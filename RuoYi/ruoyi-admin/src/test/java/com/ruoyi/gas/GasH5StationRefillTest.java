package com.ruoyi.gas;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.gas.domain.*;
import com.ruoyi.gas.h5.H5StationRequests.CreateBatch;
import com.ruoyi.gas.h5.H5StationRequests.CreateReceipt;
import com.ruoyi.gas.h5.H5User;
import com.ruoyi.gas.mapper.GasBuyerMapper;
import com.ruoyi.gas.mapper.GasStationMapper;
import com.ruoyi.gas.service.*;

/**
 * M1 MySQL集成测试。仅隔离库，需先执行gas_m1_station_refill_upgrade.sql。
 * 显式设置GAS_ALLOW_DB_TESTS=true后才执行；并发用例独立提交，按本类创建的ID清理。
 * 不要指向生产／共享有价值数据。此类不是默认可安全执行的内存测试。
 */
@EnabledIfEnvironmentVariable(named = "GAS_ALLOW_DB_TESTS", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = { "spring.quartz.auto-startup=false" })
class GasH5StationRefillTest
{
    @Autowired private IH5StationService service;
    @Autowired private IGasPurchaseBatchService batchService;
    @Autowired private IInventoryService inventory;
    @Autowired private GasBuyerMapper buyerMapper;
    @Autowired private GasStationMapper stationMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate tx;
    private final List<Fixture> fixtures = new ArrayList<>();

    @Test void createsBatchWithoutStockOrShiftAndReplaysSameRequest()
    {
        Fixture f = fixture();
        CreateBatch input = batchInput("100", "create-batch-0001");
        GasPurchaseBatch batch = service.createBatch(f.admin(), input);
        assertEquals(f.stationId, batch.getStationId());
        assertEquals(f.buyerId, batch.getBuyerId());
        assertEquals(new BigDecimal("300.00"), batch.getAmount());
        assertEquals(0, count("gas_inventory_pool", "station_id", f.stationId));
        assertEquals(0, count("gas_shift", "station_id", f.stationId));
        assertEquals(batch.getBatchId(), service.createBatch(f.admin(), input).getBatchId());
        assertThrows(ServiceException.class, () -> service.createBatch(f.admin(), batchInput("101", input.requestId())));
        assertEquals(1, count("gas_purchase_batch", "station_id", f.stationId));
    }

    @Test void exactQuotaAndDuplicateReceiptOnlyIncreaseOnce()
    {
        Fixture f = fixture();
        GasPurchaseBatch batch = createBatch(f);
        service.receive(f.admin(), batch.getBatchId(), receipt("60", "receipt-first-0001"));
        CreateReceipt last = receipt("40", "receipt-last-0001");
        GasPurchaseReceipt result = service.receive(f.admin(), batch.getBatchId(), last);
        assertEquals(result.getReceiptId(), service.receive(f.admin(), batch.getBatchId(), last).getReceiptId());
        assertThrows(ServiceException.class, () -> service.receive(f.admin(), batch.getBatchId(), receipt("41", last.requestId())));
        assertThrows(ServiceException.class, () -> service.receive(f.admin(), batch.getBatchId(), receipt("0.01", "receipt-over-0001")));
        assertStock(f, "100", 2);
        assertEquals("" + f.adminId + ":" + f.login, result.getCreateBy());
    }

    @Test void overLimitRollsBackPoolReceiptLedgerAndRequest()
    {
        Fixture f = fixture();
        GasPurchaseBatch batch = createBatch(f);
        assertThrows(ServiceException.class, () -> service.receive(f.admin(), batch.getBatchId(), receipt("100.01", "over-limit-0001")));
        assertEquals(0, count("gas_purchase_receipt", "station_id", f.stationId));
        assertEquals(0, count("gas_inventory_pool", "station_id", f.stationId));
        assertEquals(0, requestCount(f, "over-limit-0001"));
    }

    @Test void enclosingFailureRollsBackAllAndSameRequestCanRetry()
    {
        Fixture f = fixture();
        GasPurchaseBatch batch = createBatch(f);
        CreateReceipt input = receipt("30", "rollback-receipt-0001");
        assertThrows(ServiceException.class, () -> tx.executeWithoutResult(status -> {
            service.receive(f.admin(), batch.getBatchId(), input);
            throw new ServiceException("模拟提交前失败");
        }));
        assertEquals(0, count("gas_purchase_receipt", "station_id", f.stationId));
        assertEquals(0, count("gas_inventory_pool", "station_id", f.stationId));
        assertEquals(0, requestCount(f, input.requestId()));
        service.receive(f.admin(), batch.getBatchId(), input);
        assertStock(f, "30", 1);
    }

    @Test void scopesQueriesAndRejectsOtherStationMemberStaffAndRevokedAdmin()
    {
        Fixture a = fixture(), b = fixture();
        GasPurchaseBatch first = createBatch(a), other = createBatch(b);
        Map<String, Object> list = service.batches(a.admin(), 1, 10, null, null);
        List<?> rows = (List<?>) list.get("rows");
        assertEquals(1, rows.size());
        assertEquals(first.getBatchId(), ((GasPurchaseBatch) rows.get(0)).getBatchId());
        assertThrows(ServiceException.class, () -> service.batchDetail(a.admin(), other.getBatchId()));
        assertThrows(ServiceException.class, () -> service.receipts(a.admin(), other.getBatchId(), 1, 10));
        assertThrows(ServiceException.class, () -> service.receive(a.admin(), other.getBatchId(), receipt("10", "cross-station-0001")));
        assertEquals(0, requestCount(a, "cross-station-0001"));
        assertThrows(ServiceException.class, () -> service.overview(H5User.member("member", 1L, true)));
        H5User staff = H5User.staff(a.staffOpenid, a.staffId, a.stationId, H5User.ROLE_STAFF);
        assertThrows(ServiceException.class, () -> service.overview(staff));
        jdbc.update("delete from sys_user_role where user_id=?", a.adminId);
        assertThrows(ServiceException.class, () -> service.createBatch(a.admin(), batchInput("10", "revoked-admin-0001")));
        assertEquals(0, count("gas_purchase_receipt", "station_id", b.stationId));
    }

    @Test void concurrentSameRequestProducesOneReceipt() throws Exception
    {
        Fixture f = fixture();
        GasPurchaseBatch batch = createBatch(f);
        CreateReceipt input = receipt("60", "concurrent-same-0001");
        List<Future<Long>> results = concurrently(
                () -> service.receive(f.admin(), batch.getBatchId(), input).getReceiptId(),
                () -> service.receive(f.admin(), batch.getBatchId(), input).getReceiptId());
        assertEquals(results.get(0).get(10, TimeUnit.SECONDS), results.get(1).get(10, TimeUnit.SECONDS));
        assertStock(f, "60", 1);
    }

    @Test void concurrentDifferentRequestsCannotExceedQuota() throws Exception
    {
        Fixture f = fixture();
        GasPurchaseBatch batch = createBatch(f);
        List<Future<Long>> results = concurrently(
                () -> service.receive(f.admin(), batch.getBatchId(), receipt("60", "concurrent-first-0001")).getReceiptId(),
                () -> service.receive(f.admin(), batch.getBatchId(), receipt("60", "concurrent-second-0001")).getReceiptId());
        int success = 0, rejected = 0;
        for (Future<Long> result : results)
        {
            try { result.get(10, TimeUnit.SECONDS); success++; }
            catch (ExecutionException e)
            {
                assertInstanceOf(ServiceException.class, e.getCause());
                assertTrue(e.getCause().getMessage().contains("累计到货"));
                rejected++;
            }
        }
        assertEquals(1, success); assertEquals(1, rejected);
        assertStock(f, "60", 1);
    }

    @Test void consumedStockDoesNotRestoreQuotaAndBatchCannotShrink()
    {
        Fixture f = fixture();
        GasPurchaseBatch batch = createBatch(f);
        service.receive(f.admin(), batch.getBatchId(), receipt("100", "full-receipt-0001"));
        inventory.consume(8_000_000_000L + f.stationId, f.stationId, "CNG", new BigDecimal("60"), BigDecimal.ZERO, "m1-test");
        assertThrows(ServiceException.class, () -> service.receive(f.admin(), batch.getBatchId(), receipt("1", "after-consume-0001")));
        GasPurchaseBatch edit = batchService.selectBatchById(batch.getBatchId());
        edit.setQty(new BigDecimal("99"));
        assertThrows(ServiceException.class, () -> batchService.updateBatch(edit));
        assertEquals(new BigDecimal("100.00"), batchService.selectBatchById(batch.getBatchId()).getQty());
        assertStock(f, "100", 1);
    }

    @Test void existingPoolRollbackLeavesEveryRowUnchangedAndNoOrphanLedger()
    {
        Fixture f = fixture();
        GasPurchaseBatch batch = createBatch(f);
        service.receive(f.admin(), batch.getBatchId(), receipt("20", "existing-first-0001"));
        Map<String, Object> beforePool = jdbc.queryForMap("select * from gas_inventory_pool where station_id=?", f.stationId);
        List<Map<String, Object>> beforeLedger = jdbc.queryForList("select * from gas_inventory_ledger where pool_id=? order by ledger_id", beforePool.get("pool_id"));
        long[] rolledBackReceipt = {0};
        CreateReceipt input = receipt("30", "existing-rollback-0001");
        assertThrows(ServiceException.class, () -> tx.executeWithoutResult(status -> {
            rolledBackReceipt[0] = service.receive(f.admin(), batch.getBatchId(), input).getReceiptId();
            throw new ServiceException("模拟已有池入库后事务失败");
        }));
        assertTrue(rolledBackReceipt[0] > 0);
        assertEquals(beforePool, jdbc.queryForMap("select * from gas_inventory_pool where station_id=?", f.stationId));
        assertEquals(beforeLedger, jdbc.queryForList("select * from gas_inventory_ledger where pool_id=? order by ledger_id", beforePool.get("pool_id")));
        assertEquals(0, count("gas_inventory_ledger", "receipt_id", rolledBackReceipt[0]));
        assertEquals(0, requestCount(f, input.requestId()));
        assertStock(f, "20", 1);
        assertThrows(ServiceException.class, () -> service.receive(f.admin(), batch.getBatchId(), receipt("81", "existing-over-0001")));
        assertEquals(beforePool, jdbc.queryForMap("select * from gas_inventory_pool where station_id=?", f.stationId));
        assertEquals(0, requestCount(f, "existing-over-0001"));
        service.receive(f.admin(), batch.getBatchId(), input);
        assertStock(f, "50", 2);
    }

    @Test void twoConcurrentReceiptsCanExactlyFillRemainingQuota() throws Exception
    {
        Fixture f = fixture();
        GasPurchaseBatch batch = createBatch(f);
        service.receive(f.admin(), batch.getBatchId(), receipt("60", "exact-start-0001"));
        List<Future<Long>> results = concurrently(
                () -> service.receive(f.admin(), batch.getBatchId(), receipt("20", "exact-first-0001")).getReceiptId(),
                () -> service.receive(f.admin(), batch.getBatchId(), receipt("20", "exact-second-0001")).getReceiptId());
        assertNotEquals(results.get(0).get(), results.get(1).get());
        assertStock(f, "100", 3);
    }

    @Test void backendAndH5ShareTheSameConcurrentQuotaGuard() throws Exception
    {
        Fixture f = fixture();
        GasPurchaseBatch batch = createBatch(f);
        List<Future<Long>> results = concurrently(
                () -> service.receive(f.admin(), batch.getBatchId(), receipt("60", "mixed-h5-0001")).getReceiptId(),
                () -> {
                    GasPurchaseReceipt input = new GasPurchaseReceipt();
                    input.setBatchId(batch.getBatchId()); input.setQty(new BigDecimal("60"));
                    input.setReceivedAt(new Date()); input.setCreateBy("m1-backend-test");
                    return inventory.receive(input).getReceiptId();
                });
        assertOneSuccessOneBusinessRejection(results);
        assertStock(f, "60", 1);
    }

    @Test void concurrentReceiptAndBatchShrinkCannotBothViolateQuota() throws Exception
    {
        Fixture f = fixture();
        GasPurchaseBatch batch = createBatch(f);
        GasPurchaseBatch edit = batchService.selectBatchById(batch.getBatchId());
        edit.setQty(new BigDecimal("50"));
        List<Future<Long>> results = concurrently(
                () -> service.receive(f.admin(), batch.getBatchId(), receipt("60", "shrink-race-0001")).getReceiptId(),
                () -> (long) batchService.updateBatch(edit));
        assertOneSuccessOneBusinessRejection(results);
        BigDecimal received = jdbc.queryForObject("select coalesce(sum(qty),0) from gas_purchase_receipt where batch_id=?", BigDecimal.class, batch.getBatchId());
        assertTrue(received.compareTo(batchService.selectBatchById(batch.getBatchId()).getQty()) <= 0);
        if (received.signum() == 0) {
            assertEquals(0, count("gas_inventory_pool", "station_id", f.stationId));
            assertEquals(0, requestCount(f, "shrink-race-0001"));
        } else assertStock(f, "60", 1);
    }

    @Test void concurrentFirstReceiptsAcrossBatchesShareOnePoolAndStationsStayIsolated() throws Exception
    {
        Fixture f = fixture(), other = fixture();
        GasPurchaseBatch first = createBatch(f);
        GasPurchaseBatch second = service.createBatch(f.admin(), batchInput("100", "second-batch-0001"));
        List<Future<Long>> results = concurrently(
                () -> service.receive(f.admin(), first.getBatchId(), receipt("20", "first-pool-0001")).getReceiptId(),
                () -> service.receive(f.admin(), second.getBatchId(), receipt("30", "second-pool-0001")).getReceiptId());
        for (Future<Long> result : results) assertTrue(result.get() > 0);
        assertEquals(1, count("gas_inventory_pool", "station_id", f.stationId));
        assertStock(f, "50", 2);
        GasPurchaseBatch otherBatch = createBatch(other);
        results = concurrently(
                () -> service.receive(f.admin(), first.getBatchId(), receipt("10", "station-a-0001")).getReceiptId(),
                () -> service.receive(other.admin(), otherBatch.getBatchId(), receipt("40", "station-b-0001")).getReceiptId());
        for (Future<Long> result : results) assertTrue(result.get() > 0);
        assertStock(f, "60", 3);
        assertStock(other, "40", 1);
    }

    @Test void negativeAdjustmentDoesNotRestoreGrossReceiptQuota()
    {
        Fixture f = fixture();
        GasPurchaseBatch batch = createBatch(f);
        GasPurchaseReceipt full = service.receive(f.admin(), batch.getBatchId(), receipt("100", "adjust-full-0001"));
        inventory.adjust(f.stationId, "CNG", full.getReceiptId(), new BigDecimal("-10"), "M1测试盘亏", "m1-test");
        assertThrows(ServiceException.class, () -> service.receive(f.admin(), batch.getBatchId(), receipt("0.01", "after-adjust-0001")));
        assertEquals(new BigDecimal("-10.00"), inventory.selectPoolByStationGas(f.stationId, "CNG").getAdjusted());
        assertEquals(0, requestCount(f, "after-adjust-0001"));
        assertStock(f, "100", 1);
    }

    private void assertOneSuccessOneBusinessRejection(List<Future<Long>> results) throws Exception
    {
        int success = 0, rejected = 0;
        for (Future<Long> result : results) {
            try { result.get(); success++; }
            catch (ExecutionException e) { assertInstanceOf(ServiceException.class, e.getCause()); rejected++; }
        }
        assertEquals(1, success);
        assertEquals(1, rejected);
    }

    private List<Future<Long>> concurrently(Callable<Long> first, Callable<Long> second) throws Exception
    {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try
        {
            List<Future<Long>> results = new ArrayList<>();
            for (Callable<Long> work : List.of(first, second)) results.add(workers.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("并发启动超时");
                return work.call();
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            workers.shutdown();
            assertTrue(workers.awaitTermination(30, TimeUnit.SECONDS), "并发操作超时");
            return results;
        }
        finally { start.countDown(); workers.shutdownNow(); }
    }

    private GasPurchaseBatch createBatch(Fixture f) { return service.createBatch(f.admin(), batchInput("100", "create-batch-0001")); }
    private CreateBatch batchInput(String qty, String requestId)
    {
        return new CreateBatch(requestId, "CNG", new Date(), new BigDecimal(qty), new BigDecimal("3.0000"), "M1集成测试");
    }
    private CreateReceipt receipt(String qty, String requestId)
    {
        return new CreateReceipt(requestId, new BigDecimal(qty), new Date(1700000000000L), "M1集成测试");
    }
    private int count(String table, String field, Long id)
    {
        // 表名／字段仅来自本类常量，不接收外部请求。
        return jdbc.queryForObject("select count(*) from " + table + " where " + field + "=?", Integer.class, id);
    }
    private int requestCount(Fixture f, String requestId)
    {
        return jdbc.queryForObject("select count(*) from gas_station_write_request where station_id=? and request_id=?", Integer.class, f.stationId, requestId);
    }
    private void assertStock(Fixture f, String qty, int receiptCount)
    {
        assertEquals(0, new BigDecimal(qty).compareTo(inventory.selectPoolByStationGas(f.stationId, "CNG").getTotalIn()));
        assertEquals(receiptCount, count("gas_purchase_receipt", "station_id", f.stationId));
        BigDecimal ledger = jdbc.queryForObject("select coalesce(sum(l.qty),0) from gas_inventory_ledger l join gas_inventory_pool p on l.pool_id=p.pool_id where p.station_id=? and l.biz_type='IN'", BigDecimal.class, f.stationId);
        assertEquals(0, new BigDecimal(qty).compareTo(ledger));
        Long poolId = inventory.selectPoolByStationGas(f.stationId, "CNG").getPoolId();
        assertEquals(receiptCount, jdbc.queryForObject("select count(*) from gas_inventory_ledger where pool_id=? and biz_type='IN'", Integer.class, poolId));
        assertEquals(0, jdbc.queryForObject("select count(*) from gas_purchase_receipt r left join gas_inventory_ledger l on l.receipt_id=r.receipt_id and l.biz_type='IN' where r.station_id=? and (l.ledger_id is null or l.pool_id<>? or l.qty<>r.qty or l.operator<>r.create_by)", Integer.class, f.stationId, poolId));
    }
    private Fixture fixture()
    {
        Fixture f = tx.execute(status -> {
            Fixture result = new Fixture();
            String suffix = IdUtils.fastSimpleUUID().substring(0, 12);
            GasBuyer buyer = new GasBuyer(); buyer.setBuyerName("M1测试-" + suffix); buyer.setStatus("0");
            buyerMapper.insertBuyer(buyer); result.buyerId = buyer.getBuyerId();
            jdbc.update("insert into sys_dept(parent_id,ancestors,dept_name,order_num,status,del_flag) values(100,'0,100',?,99,'0','0')", "M1测试-" + suffix);
            result.deptId = jdbc.queryForObject("select last_insert_id()", Long.class);
            GasStation station = new GasStation(); station.setStationCode("M1-" + suffix); station.setStationName("M1测试站" + suffix);
            station.setBuyerId(result.buyerId); station.setDeptId(result.deptId); station.setQrToken(IdUtils.fastSimpleUUID()); station.setStatus("0");
            stationMapper.insertStation(station); result.stationId = station.getStationId();
            result.login = "m1a" + suffix; result.adminOpenid = "m1a-" + suffix; result.staffOpenid = "m1s-" + suffix;
            result.adminId = employee(result, result.login, result.adminOpenid, 102);
            result.staffId = employee(result, "m1s" + suffix, result.staffOpenid, 103);
            return result;
        });
        fixtures.add(f);
        return f;
    }
    private Long employee(Fixture f, String login, String openid, int roleId)
    {
        jdbc.update("insert into sys_user(login_name,user_name,password,status,del_flag,dept_id) values(?,?,'unused','0','0',?)", login, login, f.deptId);
        Long id = jdbc.queryForObject("select last_insert_id()", Long.class);
        jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)", id, roleId);
        jdbc.update("insert into gas_staff_binding(user_id,openid,station_id,bound_at) values(?,?,?,sysdate())", id, openid, f.stationId);
        return id;
    }
    private static class Fixture
    {
        Long stationId, buyerId, deptId, adminId, staffId;
        String login, adminOpenid, staffOpenid;
        H5User admin() { return H5User.staff(adminOpenid, adminId, stationId, H5User.ROLE_STATION_ADMIN); }
    }
    @AfterEach void cleanup()
    {
        for (Fixture f : fixtures) tx.executeWithoutResult(status -> {
            jdbc.update("delete from gas_station_write_request where station_id=?", f.stationId);
            jdbc.update("delete from gas_remind where station_id=?", f.stationId);
            jdbc.update("delete from gas_inventory_ledger where pool_id in (select pool_id from gas_inventory_pool where station_id=?)", f.stationId);
            jdbc.update("delete from gas_purchase_receipt where station_id=?", f.stationId);
            jdbc.update("delete from gas_purchase_batch where station_id=?", f.stationId);
            jdbc.update("delete from gas_inventory_pool where station_id=?", f.stationId);
            jdbc.update("delete from gas_staff_binding where user_id in (?,?)", f.adminId, f.staffId);
            jdbc.update("delete from sys_user_role where user_id in (?,?)", f.adminId, f.staffId);
            jdbc.update("delete from sys_user where user_id in (?,?)", f.adminId, f.staffId);
            jdbc.update("delete from gas_station where station_id=?", f.stationId);
            jdbc.update("delete from sys_dept where dept_id=?", f.deptId);
            jdbc.update("delete from gas_buyer where buyer_id=?", f.buyerId);
        });
    }
}
