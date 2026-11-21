package com.ruoyi.gas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.gas.domain.GasBuyer;
import com.ruoyi.gas.domain.GasInventoryPool;
import com.ruoyi.gas.domain.GasPurchaseBatch;
import com.ruoyi.gas.domain.GasPurchaseReceipt;
import com.ruoyi.gas.domain.GasStation;
import com.ruoyi.gas.mapper.GasBuyerMapper;
import com.ruoyi.gas.mapper.GasInventoryLedgerMapper;
import com.ruoyi.gas.mapper.GasPurchaseBatchMapper;
import com.ruoyi.gas.mapper.GasPurchaseReceiptMapper;
import com.ruoyi.gas.mapper.GasStationMapper;
import com.ruoyi.gas.service.IInventoryService;

/**
 * 库存服务 JUnit 集成测试（Dev_Plan §2 阶段3-6）
 *
 * <ul>
 *   <li>先进先出跨批次扣减</li>
 *   <li>并发核销不超扣（FOR UPDATE 悲观锁）</li>
 *   <li>调整后池余额等于按流水重算的结果</li>
 * </ul>
 *
 * <p>事务策略：FIFO 与重算用例走测试事务自动回滚；
 * 并发用例子线程用 TransactionTemplate 独立提交，AfterEach 手工清理（按 JUNIT 标记）。</p>
 *
 * @author gas
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = { "spring.quartz.auto-startup=false" })
class GasInventoryServiceTest
{
    private static final String STATION_CODE = "JUNIT-ST";

    @Autowired
    private IInventoryService inventoryService;

    @Autowired
    private GasBuyerMapper buyerMapper;

    @Autowired
    private GasStationMapper stationMapper;

    @Autowired
    private GasPurchaseBatchMapper batchMapper;

    @Autowired
    private GasPurchaseReceiptMapper receiptMapper;

    @Autowired
    private GasInventoryLedgerMapper ledgerMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    /** 准备基础数据：采购商 + 测试部门 + 站点（直插 mapper，绕开业务建站逻辑）+ 批次 */
    private Long[] prepare(String batchNo, String gasType, BigDecimal qty, BigDecimal unitPrice)
    {
        GasBuyer buyer = new GasBuyer();
        buyer.setBuyerName("JUnit测试采购商");
        buyer.setStatus("0");
        buyerMapper.insertBuyer(buyer);

        // gas_station.dept_id 非空：为测试站点配一个一次性部门
        jdbcTemplate.update("insert into sys_dept (parent_id, ancestors, dept_name, order_num, status, del_flag, create_by, create_time) "
                + "values (100, '0,100', 'JUnit测试部门', 99, '0', '0', 'junit', sysdate())");
        Long deptId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);

        GasStation station = new GasStation();
        station.setStationCode(STATION_CODE);
        station.setStationName("JUnit测试站点");
        station.setBuyerId(buyer.getBuyerId());
        station.setDeptId(deptId);
        station.setQrToken(IdUtils.fastSimpleUUID());
        station.setStatus("0");
        stationMapper.insertStation(station);

        GasPurchaseBatch batch = new GasPurchaseBatch();
        batch.setBatchNo(batchNo);
        batch.setBuyerId(buyer.getBuyerId());
        batch.setStationId(station.getStationId());
        batch.setGasType(gasType);
        batch.setPurchaseDate(new Date());
        batch.setQty(qty);
        batch.setUnitPrice(unitPrice);
        batch.setAmount(qty.multiply(unitPrice));
        batchMapper.insertBatch(batch);

        return new Long[] { buyer.getBuyerId(), station.getStationId(), batch.getBatchId() };
    }

    private GasPurchaseReceipt receive(Long batchId, String qty, String receivedAt) throws Exception
    {
        GasPurchaseReceipt receipt = new GasPurchaseReceipt();
        receipt.setBatchId(batchId);
        receipt.setQty(new BigDecimal(qty));
        receipt.setReceivedAt(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(receivedAt));
        receipt.setCreateBy("junit");
        return inventoryService.receive(receipt);
    }

    /**
     * 用例1：先进先出跨批次扣减 —— 先到的批次先扣完，再扣下一批
     */
    @Test
    @Transactional
    void testFifoCrossBatchConsume() throws Exception
    {
        Long[] ids = prepare("JUNIT-FIFO-1", "CNG", new BigDecimal("150"), new BigDecimal("3.0000"));
        Long stationId = ids[1];
        receive(ids[2], "100", "2026-11-01 08:00:00");
        receive(ids[2], "50",  "2026-11-10 08:00:00");

        // 核销 120：应扣完批次1（100）+ 批次2（20）
        inventoryService.consume(9001L, stationId, "CNG", new BigDecimal("120"), BigDecimal.ZERO, "junit");

        List<GasPurchaseReceipt> receipts = receiptMapper.selectFifoForUpdate(stationId, "CNG");
        // 批次1 已扣完（不在有余量列表），批次2 剩 30
        assertEquals(1, receipts.size());
        assertEquals(0, new BigDecimal("30").compareTo(receipts.get(0).getRemain()));
        assertEquals(0, new BigDecimal("50").compareTo(receipts.get(0).getQty()));

        GasInventoryPool pool = inventoryService.selectPoolByStationGas(stationId, "CNG");
        assertEquals(new BigDecimal("150.00"), pool.getTotalIn());
        assertEquals(new BigDecimal("120.00"), pool.getConsumed());
        // 可用 = 150 - 120 - 0
        BigDecimal available = pool.getTotalIn().add(pool.getAdjusted()).subtract(pool.getConsumed()).subtract(pool.getReserved());
        assertEquals(new BigDecimal("30.00"), available);
    }

    /**
     * 用例2：并发核销不超扣 —— 可用 100，两个线程各扣 60，仅一个成功
     */
    @Test
    void testConcurrentNoOverConsume() throws Exception
    {
        Long[] ids = prepare("JUNIT-CONC-1", "LNG", new BigDecimal("100"), new BigDecimal("4.0000"));
        Long stationId = ids[1];
        receive(ids[2], "100", "2026-11-01 08:00:00");

        final int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        try
        {
            for (int i = 0; i < threads; i++)
            {
                final long orderId = 9100L + i;
                pool.submit(() -> {
                    ready.countDown();
                    try
                    {
                        ready.await();
                        transactionTemplate.executeWithoutResult(tx -> {
                            try
                            {
                                inventoryService.consume(orderId, stationId, "LNG",
                                        new BigDecimal("60"), BigDecimal.ZERO, "junit");
                                success.incrementAndGet();
                            }
                            catch (Exception e)
                            {
                                // 可用量不足被拒，事务回滚
                                failed.incrementAndGet();
                                throw new RuntimeException(e);
                            }
                        });
                    }
                    catch (Exception ignored)
                    {
                    }
                    finally
                    {
                        done.countDown();
                    }
                });
            }
            assertTrue(done.await(30, TimeUnit.SECONDS), "并发线程超时");

            assertEquals(1, success.get(), "可用100、各扣60：只应成功一个");
            assertEquals(1, failed.get(), "另一个应被拒绝");
            GasInventoryPool p = inventoryService.selectPoolByStationGas(stationId, "LNG");
            assertEquals(new BigDecimal("60.00"), p.getConsumed(), "总实扣不能超过可用量");
            assertEquals(new BigDecimal("40.00"),
                    p.getTotalIn().add(p.getAdjusted()).subtract(p.getConsumed()).subtract(p.getReserved()),
                    "剩余可用应为40");
        }
        finally
        {
            pool.shutdownNow();
        }
    }

    /**
     * 用例3：调整后池余额等于按流水重算的结果（IN + ADJUST - CONSUME）
     */
    @Test
    @Transactional
    void testAdjustThenRecalcByLedger() throws Exception
    {
        Long[] ids = prepare("JUNIT-ADJ-1", "CNG", new BigDecimal("200"), new BigDecimal("3.0000"));
        Long stationId = ids[1];
        GasPurchaseReceipt r1 = receive(ids[2], "120", "2026-11-01 08:00:00");
        receive(ids[2], "80",  "2026-11-05 08:00:00");

        inventoryService.reserve(9201L, stationId, "CNG", new BigDecimal("30"), "junit");
        inventoryService.adjust(stationId, "CNG", r1.getReceiptId(), new BigDecimal("10"), "盘盈", "junit");
        inventoryService.adjust(stationId, "CNG", r1.getReceiptId(), new BigDecimal("-5"), "计量误差", "junit");
        inventoryService.consume(9201L, stationId, "CNG", new BigDecimal("30"), new BigDecimal("30"), "junit");

        GasInventoryPool pool = inventoryService.selectPoolByStationGas(stationId, "CNG");
        BigDecimal poolBalance = pool.getTotalIn().add(pool.getAdjusted()).subtract(pool.getConsumed());
        BigDecimal ledgerBalance = ledgerMapper.sumByPool(pool.getPoolId());
        assertEquals(ledgerBalance, poolBalance, "池余额必须等于按流水重算（IN+ADJUST-CONSUME）");
        assertEquals(new BigDecimal("175.00"), poolBalance); // 200 + 10 - 5 - 30
        assertEquals(new BigDecimal("0.00"), pool.getReserved()); // 核销释放全部预占
    }

    /**
     * 用例4：释放量不能超过该订单自身的有效预占（订单维度校验，防误释放他单预占导致超卖）
     */
    @Test
    @Transactional
    void testReleaseExceedsOrderReservedRejected() throws Exception
    {
        Long[] ids = prepare("JUNIT-REL-1", "CNG", new BigDecimal("200"), new BigDecimal("3.0000"));
        Long stationId = ids[1];
        receive(ids[2], "200", "2026-11-01 08:00:00");

        // 订单A预占30、订单B预占70，池内共预占100
        inventoryService.reserve(9301L, stationId, "CNG", new BigDecimal("30"), "junit");
        inventoryService.reserve(9302L, stationId, "CNG", new BigDecimal("70"), "junit");

        // 订单A释放30（等于自身有效预占）：成功，池剩余预占70
        inventoryService.release(9301L, stationId, "CNG", new BigDecimal("30"), "junit");
        GasInventoryPool pool = inventoryService.selectPoolByStationGas(stationId, "CNG");
        assertEquals(new BigDecimal("70.00"), pool.getReserved(), "订单A释放后池预占应只剩订单B的70");

        // 订单C无预占但池级预占余量（70）足够：池级校验会放行，订单维度校验必须拒绝
        ServiceException ex = assertThrows(ServiceException.class,
                () -> inventoryService.release(9303L, stationId, "CNG", new BigDecimal("50"), "junit"));
        assertTrue(ex.getMessage().contains("有效预占"), "提示语应指明订单有效预占不足");

        // 拒绝后池预占不受影响
        pool = inventoryService.selectPoolByStationGas(stationId, "CNG");
        assertEquals(new BigDecimal("70.00"), pool.getReserved());
    }

    /** 用例5：核销预占量只信订单流水；负预占/负释放直接拒绝 */
    @Test
    @Transactional
    void testConsumeUsesServerSideReservedQty() throws Exception
    {
        Long[] ids = prepare("JUNIT-SEC-1", "CNG", new BigDecimal("100"), new BigDecimal("3.0000"));
        Long stationId = ids[1];
        receive(ids[2], "100", "2026-11-01 08:00:00");
        inventoryService.reserve(9401L, stationId, "CNG", new BigDecimal("30"), "junit");

        // 调用方故意传 1000：服务端仍按订单实际预占30计算，池预占不得被冲成负数
        inventoryService.consume(9401L, stationId, "CNG", new BigDecimal("40"), new BigDecimal("1000"), "junit");
        GasInventoryPool pool = inventoryService.selectPoolByStationGas(stationId, "CNG");
        assertEquals(new BigDecimal("0.00"), pool.getReserved());
        assertEquals(new BigDecimal("40.00"), pool.getConsumed());

        assertThrows(ServiceException.class,
                () -> inventoryService.reserve(9402L, stationId, "CNG", new BigDecimal("-1"), "junit"));
        assertThrows(ServiceException.class,
                () -> inventoryService.release(9402L, stationId, "CNG", new BigDecimal("-1"), "junit"));
        pool = inventoryService.selectPoolByStationGas(stationId, "CNG");
        assertEquals(new BigDecimal("0.00"), pool.getReserved(), "非法数量不得制造或冲减预占");
    }

    /** 用例6：负调整不得把实物库存压到订单有效预占之下 */
    @Test
    @Transactional
    void testNegativeAdjustProtectsReservedStock() throws Exception
    {
        Long[] ids = prepare("JUNIT-SEC-2", "CNG", new BigDecimal("100"), new BigDecimal("3.0000"));
        Long stationId = ids[1];
        GasPurchaseReceipt receipt = receive(ids[2], "100", "2026-11-01 08:00:00");
        inventoryService.reserve(9501L, stationId, "CNG", new BigDecimal("80"), "junit");

        ServiceException ex = assertThrows(ServiceException.class, () ->
                inventoryService.adjust(stationId, "CNG", receipt.getReceiptId(), new BigDecimal("-30"), "盘点", "junit"));
        assertTrue(ex.getMessage().contains("有效预占"));
        GasInventoryPool pool = inventoryService.selectPoolByStationGas(stationId, "CNG");
        assertEquals(new BigDecimal("0.00"), pool.getAdjusted());
        assertEquals(new BigDecimal("80.00"), pool.getReserved());
    }

    /** 清理并发用例残留（按 JUNIT 标记，与回滚用例重叠也无害） */
    @AfterEach
    void cleanup()
    {
        jdbcTemplate.update("delete from gas_remind where buyer_id in (select buyer_id from gas_buyer where buyer_name = 'JUnit测试采购商')");
        jdbcTemplate.update("delete from gas_inventory_ledger where pool_id in (select pool_id from gas_inventory_pool where station_id in (select station_id from gas_station where station_code = ?))", STATION_CODE);
        jdbcTemplate.update("delete from gas_purchase_receipt where station_id in (select station_id from gas_station where station_code = ?)", STATION_CODE);
        jdbcTemplate.update("delete from gas_inventory_pool where station_id in (select station_id from gas_station where station_code = ?)", STATION_CODE);
        jdbcTemplate.update("delete from gas_purchase_batch where buyer_id in (select buyer_id from gas_buyer where buyer_name = 'JUnit测试采购商')");
        jdbcTemplate.update("delete from gas_station where station_code = ?", STATION_CODE);
        jdbcTemplate.update("delete from gas_buyer where buyer_name = 'JUnit测试采购商'");
        jdbcTemplate.update("delete from sys_dept where dept_name = 'JUnit测试部门'");
    }
}
