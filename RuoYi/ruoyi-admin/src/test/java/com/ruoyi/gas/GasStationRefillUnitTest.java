package com.ruoyi.gas;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.gas.domain.*;
import com.ruoyi.gas.h5.H5StationRequests.CreateBatch;
import com.ruoyi.gas.h5.H5StationRequests.CreateReceipt;
import com.ruoyi.gas.h5.H5User;
import com.ruoyi.gas.h5.H5UserContext;
import com.ruoyi.web.controller.h5.H5StationController;
import com.ruoyi.gas.mapper.*;
import com.ruoyi.gas.service.*;
import com.ruoyi.gas.service.impl.*;

/** M1 纯单元测试：不启动 Spring、不加载应用配置、不连接数据库。不能替代 MySQL 并发／回滚验收。 */
class GasStationRefillUnitTest
{
    private final H5User user = H5User.staff("unit-station-admin", 21L, 31L, H5User.ROLE_STATION_ADMIN);
    private IGasShiftService identity;
    private IGasPurchaseBatchService purchases;
    private IInventoryService inventory;
    private GasStationMapper stations;
    private GasBuyerMapper buyers;
    private GasPurchaseBatchMapper batches;
    private GasPurchaseReceiptMapper receipts;
    private GasStationWriteRequestMapper requests;
    private H5StationServiceImpl service;
    private ValidatorFactory validation;
    private AtomicReference<GasStationWriteRequest> saved;

    @BeforeEach
    void setup()
    {
        identity = mock(IGasShiftService.class); purchases = mock(IGasPurchaseBatchService.class);
        inventory = mock(IInventoryService.class); stations = mock(GasStationMapper.class);
        buyers = mock(GasBuyerMapper.class); batches = mock(GasPurchaseBatchMapper.class);
        receipts = mock(GasPurchaseReceiptMapper.class); requests = mock(GasStationWriteRequestMapper.class);
        validation = Validation.buildDefaultValidatorFactory();
        service = new H5StationServiceImpl(identity, purchases, inventory, stations, buyers, batches, receipts, requests, validation.getValidator());
        SysUser admin = new SysUser(); admin.setUserId(21L); admin.setLoginName("station-admin");
        when(identity.requireStationAdminUser(user)).thenReturn(admin);
        GasStation station = new GasStation(); station.setStationId(31L); station.setBuyerId(41L);
        when(stations.selectStationById(31L)).thenReturn(station);
        GasBuyer buyer = new GasBuyer(); buyer.setBuyerId(41L); buyer.setStatus("0");
        when(buyers.selectBuyerById(41L)).thenReturn(buyer);
        saved = new AtomicReference<>();
        when(requests.claim(any())).thenAnswer(call -> { saved.compareAndSet(null, call.getArgument(0)); return 1; });
        when(requests.selectForUpdate(any())).thenAnswer(call -> saved.get());
        when(requests.complete(any())).thenReturn(1);
    }

    @AfterEach void closeValidation() { validation.close(); H5UserContext.clear(); }

    @Test void rejectsNonAdminBeforeAnyBusinessAccess()
    {
        when(identity.requireStationAdminUser(user)).thenThrow(new ServiceException("仅站管"));
        assertThrows(ServiceException.class, () -> service.overview(user));
        assertThrows(ServiceException.class, () -> service.batches(user, 1, 10, null, null));
        assertThrows(ServiceException.class, () -> service.batchDetail(user, 51L));
        assertThrows(ServiceException.class, () -> service.receipts(user, 51L, 1, 10));
        assertThrows(ServiceException.class, () -> service.createBatch(user, batchInput("100")));
        assertThrows(ServiceException.class, () -> service.receive(user, 51L, receiptInput("10")));
        verifyNoInteractions(requests, purchases, inventory, batches, receipts, stations);
    }

    @Test void rejectsCrossStationDetailAndReceipt()
    {
        GasPurchaseBatch other = batch(99L);
        when(batches.selectBatchById(51L)).thenReturn(other);
        when(batches.selectBatchByIdForUpdate(51L)).thenReturn(other);
        assertThrows(ServiceException.class, () -> service.batchDetail(user, 51L));
        assertThrows(ServiceException.class, () -> service.receipts(user, 51L, 1, 10));
        assertThrows(ServiceException.class, () -> service.receive(user, 51L, receiptInput("10")));
        verify(inventory, never()).receive(any());
    }

    @Test void createsBatchWithServerStationOperatorAndNoInventoryOrShift()
    {
        when(purchases.insertBatch(any())).thenAnswer(call -> {
            GasPurchaseBatch created = call.getArgument(0);
            assertEquals(31L, created.getStationId());
            assertEquals("21:station-admin", created.getCreateBy());
            assertNull(created.getBuyerId(), "归属由共用采购服务从站点生成");
            assertNull(created.getPayStatus());
            created.setBatchId(51L);
            return 1;
        });
        when(batches.selectBatchById(51L)).thenReturn(batch(31L));
        assertEquals(51L, service.createBatch(user, batchInput("100")).getBatchId());
        verifyNoInteractions(inventory);
        verify(identity, never()).requireOwnActiveShift(any(), any());
        verify(identity, never()).startShift(any());
    }

    @Test void repeatedCreateReturnsOriginalAndDifferentPayloadIsRejected()
    {
        when(purchases.insertBatch(any())).thenAnswer(call -> { ((GasPurchaseBatch) call.getArgument(0)).setBatchId(51L); return 1; });
        when(batches.selectBatchById(51L)).thenReturn(batch(31L));
        service.createBatch(user, batchInput("100"));
        assertEquals(51L, service.createBatch(user, batchInput("100.00")).getBatchId());
        assertThrows(ServiceException.class, () -> service.createBatch(user, batchInput("101")));
        verify(purchases, times(1)).insertBatch(any());
    }

    @Test void repeatedReceiptReturnsOriginalWithoutSecondInventoryChange()
    {
        when(batches.selectBatchByIdForUpdate(51L)).thenReturn(batch(31L));
        GasPurchaseReceipt persisted = new GasPurchaseReceipt();
        persisted.setReceiptId(61L); persisted.setStationId(31L); persisted.setBatchId(51L);
        when(receipts.selectReceiptById(61L)).thenReturn(persisted);
        when(inventory.receive(any())).thenAnswer(call -> { GasPurchaseReceipt r = call.getArgument(0); r.setReceiptId(61L); return r; });
        assertEquals(61L, service.receive(user, 51L, receiptInput("10")).getReceiptId());
        assertEquals(61L, service.receive(user, 51L, receiptInput("10.00")).getReceiptId());
        assertThrows(ServiceException.class, () -> service.receive(user, 51L, receiptInput("11")));
        verify(inventory, times(1)).receive(any());
    }

    @Test void invalidInputCannotClaimOrWrite()
    {
        assertThrows(ServiceException.class, () -> service.createBatch(user, batchInput("-1")));
        assertThrows(ServiceException.class, () -> service.createBatch(user, batchInput("1.001")));
        assertThrows(ServiceException.class, () -> service.createBatch(user,
                new CreateBatch("valid-key", "OTHER", new Date(), BigDecimal.ONE, BigDecimal.ONE, null)));
        assertThrows(ServiceException.class, () -> service.receive(user, 51L,
                new CreateReceipt("valid-key", BigDecimal.ONE, null, null)));
        verifyNoInteractions(requests, inventory, purchases);
    }

    @Test void rejectsAmountOverflowAndRoundedZero()
    {
        assertThrows(ServiceException.class, () -> service.createBatch(user,
                new CreateBatch("valid-key", "CNG", new Date(), new BigDecimal("999999999999.99"), new BigDecimal("999999.9999"), null)));
        assertThrows(ServiceException.class, () -> service.createBatch(user,
                new CreateBatch("valid-key", "CNG", new Date(), new BigDecimal("0.01"), new BigDecimal("0.0001"), null)));
        verifyNoInteractions(requests, purchases);
    }

    @Test void sharedReceiveAllowsExactlyRemainingQuota()
    {
        InventoryHarness h = inventoryHarness("60");
        GasPurchaseReceipt input = incoming("40");
        h.service.receive(input);
        verify(h.poolMapper).addTotalIn(71L, new BigDecimal("40"));
        verify(h.receiptMapper).insertReceipt(input);
        verify(h.ledgerMapper).insertLedger(argThat(l -> "IN".equals(l.getBizType()) && l.getQty().compareTo(new BigDecimal("40")) == 0));
    }

    @Test void sharedReceiveRejectsOverQuotaBeforeAnyBalanceWrite()
    {
        InventoryHarness h = inventoryHarness("60");
        ServiceException error = assertThrows(ServiceException.class, () -> h.service.receive(incoming("40.01")));
        assertTrue(error.getMessage().contains("另建采购批次"));
        verify(h.receiptMapper, never()).insertReceipt(any());
        verify(h.poolMapper, never()).addTotalIn(anyLong(), any());
        verifyNoInteractions(h.ledgerMapper);
    }

    @Test void salesDoNotRestoreReceiptQuota()
    {
        InventoryHarness h = inventoryHarness("100");
        assertThrows(ServiceException.class, () -> h.service.receive(incoming("0.01")));
        verify(h.receiptMapper).sumReceivedQtyForUpdate(51L);
        verify(h.receiptMapper, never()).sumBatchRemain(anyLong());
        verify(h.receiptMapper, never()).insertReceipt(any());
    }

    @Test void batchCannotShrinkBelowReceivedAmount()
    {
        GasPurchaseBatchServiceImpl edit = new GasPurchaseBatchServiceImpl();
        ReflectionTestUtils.setField(edit, "batchMapper", batches);
        ReflectionTestUtils.setField(edit, "receiptMapper", receipts);
        GasPurchaseBatch existing = batch(31L); existing.setQty(new BigDecimal("100")); existing.setUnitPrice(BigDecimal.ONE);
        when(batches.selectBatchByIdForUpdate(51L)).thenReturn(existing);
        when(receipts.sumReceivedQtyForUpdate(51L)).thenReturn(new BigDecimal("60"));
        GasPurchaseBatch input = new GasPurchaseBatch(); input.setBatchId(51L); input.setQty(new BigDecimal("59.99"));
        assertThrows(ServiceException.class, () -> edit.updateBatch(input));
        verify(batches, never()).updateBatch(any());
    }

    @Test void mybatisRequestMappingParsesWithoutDatabase() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAlias("GasStationWriteRequest", GasStationWriteRequest.class);
        String resource = "mapper/gas/GasStationWriteRequestMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource))
        {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        assertTrue(configuration.hasStatement(GasStationWriteRequestMapper.class.getName() + ".claim"));
        assertTrue(configuration.hasStatement(GasStationWriteRequestMapper.class.getName() + ".complete"));
    }

    @Test void controllerMarksBusinessRejectionForThisAttemptAndRequestOnly()
    {
        IH5StationService target = mock(IH5StationService.class);
        H5StationController controller = new H5StationController(target);
        H5UserContext.set(user);
        CreateBatch batch = batchInput("100");
        CreateReceipt receipt = receiptInput("10");
        when(target.createBatch(user, batch)).thenThrow(new ServiceException("采购金额不合法"));
        when(target.receive(user, 51L, receipt)).thenThrow(new ServiceException("累计到货不能超过采购数量"));
        var batchError = controller.create(batch);
        var receiptError = controller.receive(51L, receipt);
        assertEquals(500, batchError.get("code"));
        assertEquals(true, batchError.get("stationWriteAttemptRejected"));
        assertEquals(batch.requestId(), batchError.get("requestId"));
        assertEquals(true, receiptError.get("stationWriteAttemptRejected"));
        assertEquals(receipt.requestId(), receiptError.get("requestId"));
    }

    @Test void controllerDoesNotAcknowledgeDatabaseOrCommitFailuresAsRejections()
    {
        IH5StationService target = mock(IH5StationService.class);
        H5StationController controller = new H5StationController(target);
        H5UserContext.set(user);
        when(target.createBatch(eq(user), any())).thenThrow(new IllegalStateException("模拟未知提交结果"));
        when(target.receive(eq(user), eq(51L), any())).thenThrow(
                new org.springframework.dao.DataAccessResourceFailureException("模拟查库失败"));
        assertThrows(IllegalStateException.class, () -> controller.create(batchInput("100")));
        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,
                () -> controller.receive(51L, receiptInput("10")));
    }

    @Test void controllerValidationRejectionCarriesRequestIdWithoutCallingService() throws Exception
    {
        IH5StationService target = mock(IH5StationService.class);
        H5StationController controller = new H5StationController(target);
        CreateBatch input = batchInput("-1");
        var errors = new org.springframework.validation.BeanPropertyBindingResult(input, "input");
        errors.rejectValue("qty", "invalid", "数量必须大于0");
        var parameter = new org.springframework.core.MethodParameter(
                H5StationController.class.getMethod("create", CreateBatch.class), 0);
        var response = controller.invalidWrite(new org.springframework.web.bind.MethodArgumentNotValidException(parameter, errors));
        assertEquals(500, response.get("code"));
        assertEquals(true, response.get("stationWriteAttemptRejected"));
        assertEquals(input.requestId(), response.get("requestId"));
        verifyNoInteractions(target);
    }

    @Test void controllerSuccessKeepsExistingResponseContract()
    {
        IH5StationService target = mock(IH5StationService.class);
        H5StationController controller = new H5StationController(target);
        H5UserContext.set(user);
        GasPurchaseBatch result = batch(31L);
        when(target.createBatch(eq(user), any())).thenReturn(result);
        var response = controller.create(batchInput("100"));
        assertEquals(0, response.get("code"));
        assertSame(result, response.get("data"));
        assertFalse(response.containsKey("stationWriteAttemptRejected"));
    }

    private CreateBatch batchInput(String qty)
    {
        return new CreateBatch("unit-batch-0001", "CNG", new Date(1700000000000L), new BigDecimal(qty), new BigDecimal("3.0000"), "备注");
    }
    private CreateReceipt receiptInput(String qty)
    {
        return new CreateReceipt("unit-receipt-0001", new BigDecimal(qty), new Date(1700000000000L), "备注");
    }
    private GasPurchaseBatch batch(Long stationId)
    {
        GasPurchaseBatch batch = new GasPurchaseBatch(); batch.setBatchId(51L); batch.setStationId(stationId);
        batch.setBuyerId(41L); batch.setGasType("CNG"); batch.setQty(new BigDecimal("100"));
        return batch;
    }
    private GasPurchaseReceipt incoming(String qty)
    {
        GasPurchaseReceipt receipt = new GasPurchaseReceipt(); receipt.setBatchId(51L);
        receipt.setQty(new BigDecimal(qty)); receipt.setReceivedAt(new Date()); receipt.setCreateBy("unit");
        return receipt;
    }
    private record InventoryHarness(InventoryServiceImpl service, GasInventoryPoolMapper poolMapper,
            GasPurchaseReceiptMapper receiptMapper, GasInventoryLedgerMapper ledgerMapper) { }
    private InventoryHarness inventoryHarness(String received)
    {
        InventoryServiceImpl core = new InventoryServiceImpl();
        GasInventoryPoolMapper pools = mock(GasInventoryPoolMapper.class);
        GasPurchaseReceiptMapper rs = mock(GasPurchaseReceiptMapper.class);
        GasInventoryLedgerMapper ledger = mock(GasInventoryLedgerMapper.class);
        ReflectionTestUtils.setField(core, "batchMapper", batches); ReflectionTestUtils.setField(core, "poolMapper", pools);
        ReflectionTestUtils.setField(core, "receiptMapper", rs); ReflectionTestUtils.setField(core, "ledgerMapper", ledger);
        when(batches.selectBatchByIdForUpdate(51L)).thenReturn(batch(31L));
        GasInventoryPool pool = new GasInventoryPool(); pool.setPoolId(71L); pool.setBuyerId(41L);
        when(pools.selectPoolForUpdate(31L, "CNG")).thenReturn(pool);
        when(rs.sumReceivedQtyForUpdate(51L)).thenReturn(new BigDecimal(received));
        when(rs.insertReceipt(any())).thenAnswer(call -> { ((GasPurchaseReceipt) call.getArgument(0)).setReceiptId(61L); return 1; });
        return new InventoryHarness(core, pools, rs, ledger);
    }
}
