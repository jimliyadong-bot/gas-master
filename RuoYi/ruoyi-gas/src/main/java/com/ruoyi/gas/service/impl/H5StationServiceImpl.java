package com.ruoyi.gas.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Supplier;
import jakarta.validation.Validator;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.gas.domain.*;
import com.ruoyi.gas.h5.H5StationRequests.CreateBatch;
import com.ruoyi.gas.h5.H5StationRequests.CreateReceipt;
import com.ruoyi.gas.h5.H5User;
import com.ruoyi.gas.mapper.*;
import com.ruoyi.gas.service.*;

/** 本站站管业务。权限每次查库，管理操作不依赖当班；无付款、票据或跨站档案写入口。 */
@Service
public class H5StationServiceImpl implements IH5StationService
{
    private final IGasShiftService identityService;
    private final IGasPurchaseBatchService batchService;
    private final IInventoryService inventoryService;
    private final GasStationMapper stationMapper;
    private final GasBuyerMapper buyerMapper;
    private final GasPurchaseBatchMapper batchMapper;
    private final GasPurchaseReceiptMapper receiptMapper;
    private final GasStationWriteRequestMapper requestMapper;
    private final Validator validator;

    public H5StationServiceImpl(IGasShiftService identityService, IGasPurchaseBatchService batchService,
            IInventoryService inventoryService, GasStationMapper stationMapper, GasBuyerMapper buyerMapper,
            GasPurchaseBatchMapper batchMapper, GasPurchaseReceiptMapper receiptMapper,
            GasStationWriteRequestMapper requestMapper, Validator validator)
    {
        this.identityService = identityService;
        this.batchService = batchService;
        this.inventoryService = inventoryService;
        this.stationMapper = stationMapper;
        this.buyerMapper = buyerMapper;
        this.batchMapper = batchMapper;
        this.receiptMapper = receiptMapper;
        this.requestMapper = requestMapper;
        this.validator = validator;
    }

    @Override
    public Map<String, Object> overview(H5User user)
    {
        identityService.requireStationAdminUser(user);
        GasStation station = stationMapper.selectStationById(user.getStationId());
        GasInventoryPool query = new GasInventoryPool();
        query.setStationId(user.getStationId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stationId", station.getStationId());
        result.put("stationName", station.getStationName());
        result.put("buyerName", station.getBuyerName());
        result.put("inventory", inventoryService.selectPoolList(query));
        return result;
    }

    @Override
    public Map<String, Object> batches(H5User user, int page, int size, String gasType, String batchNo)
    {
        identityService.requireStationAdminUser(user);
        if (gasType != null && !gasType.isEmpty() && !"CNG".equals(gasType) && !"LNG".equals(gasType))
            throw new ServiceException("气种只能为CNG或LNG");
        if (batchNo != null && batchNo.length() > 32) throw new ServiceException("批次号最多32个字符");
        GasPurchaseBatch query = new GasPurchaseBatch();
        query.setStationId(user.getStationId());
        query.setGasType(gasType);
        query.setBatchNo(batchNo == null ? null : batchNo.trim());
        return page(page, size, () -> batchMapper.selectBatchList(query));
    }

    @Override
    public Map<String, Object> batchDetail(H5User user, Long batchId)
    {
        identityService.requireStationAdminUser(user);
        GasPurchaseBatch batch = requireBatch(user, batchId, false);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batch", batch);
        result.put("inventory", inventoryService.selectPoolByStationGas(user.getStationId(), batch.getGasType()));
        return result;
    }

    @Override
    public Map<String, Object> receipts(H5User user, Long batchId, int page, int size)
    {
        identityService.requireStationAdminUser(user);
        requireBatch(user, batchId, false);
        GasPurchaseReceipt query = new GasPurchaseReceipt();
        query.setStationId(user.getStationId());
        query.setBatchId(batchId);
        return page(page, size, () -> receiptMapper.selectReceiptList(query));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GasPurchaseBatch createBatch(H5User user, CreateBatch input)
    {
        SysUser admin = identityService.requireStationAdminUser(user);
        validate(input);
        BigDecimal amount = input.qty().multiply(input.unitPrice()).setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(new BigDecimal("0.01")) < 0 || amount.compareTo(new BigDecimal("9999999999.99")) > 0)
            throw new ServiceException("采购金额须为0.01至9999999999.99元，请调整数量或单价");
        String purchaseDate = date(input.purchaseDate(), "yyyy-MM-dd");
        GasStationWriteRequest request = claim(user, "CREATE_BATCH", input.requestId(),
                fingerprint(input.gasType(), purchaseDate, number(input.qty()), number(input.unitPrice()), text(input.remark())));
        if (request.getResultId() != null) return requireBatch(user, request.getResultId(), false);

        GasStation station = stationMapper.selectStationById(user.getStationId());
        GasBuyer buyer = buyerMapper.selectBuyerById(station.getBuyerId());
        if (buyer == null || !"0".equals(buyer.getStatus())) throw new ServiceException("本站采购商不存在或已停用");
        GasPurchaseBatch batch = new GasPurchaseBatch();
        batch.setBatchNo("PB" + IdUtils.fastSimpleUUID().substring(0, 30));
        batch.setStationId(user.getStationId());
        batch.setGasType(input.gasType());
        batch.setPurchaseDate(input.purchaseDate());
        batch.setQty(input.qty());
        batch.setUnitPrice(input.unitPrice());
        batch.setRemark(text(input.remark()));
        batch.setCreateBy(operator(admin));
        batchService.insertBatch(batch);
        complete(request, batch.getBatchId());
        return requireBatch(user, batch.getBatchId(), false);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GasPurchaseReceipt receive(H5User user, Long batchId, CreateReceipt input)
    {
        SysUser admin = identityService.requireStationAdminUser(user);
        validate(input);
        if (batchId == null || batchId <= 0) throw new ServiceException("采购批次不存在或无权访问");
        GasStationWriteRequest request = claim(user, "CREATE_RECEIPT", input.requestId(),
                fingerprint(batchId.toString(), number(input.qty()), date(input.receivedAt(), "yyyy-MM-dd HH:mm:ss"), text(input.remark())));
        // 锁住批次后检查本站，避免后台并发更改未到货批次归属的检查／写入间隙。
        requireBatch(user, batchId, true);
        if (request.getResultId() != null)
        {
            GasPurchaseReceipt existing = receiptMapper.selectReceiptById(request.getResultId());
            if (existing == null || !user.getStationId().equals(existing.getStationId()) || !batchId.equals(existing.getBatchId()))
                throw new ServiceException("原入库记录不存在或归属已变更，请核查，不能重复提交");
            return existing;
        }
        GasPurchaseReceipt receipt = new GasPurchaseReceipt();
        receipt.setBatchId(batchId);
        receipt.setQty(input.qty());
        // 数据库为秒精度，重试摘要和实际保存采用同一精度。
        receipt.setReceivedAt(new Date(input.receivedAt().getTime() / 1000 * 1000));
        receipt.setRemark(text(input.remark()));
        receipt.setCreateBy(operator(admin));
        inventoryService.receive(receipt);
        complete(request, receipt.getReceiptId());
        return receiptMapper.selectReceiptById(receipt.getReceiptId());
    }

    private GasPurchaseBatch requireBatch(H5User user, Long batchId, boolean lock)
    {
        if (batchId == null || batchId <= 0) throw new ServiceException("采购批次不存在或无权访问");
        GasPurchaseBatch batch = lock ? batchMapper.selectBatchByIdForUpdate(batchId) : batchMapper.selectBatchById(batchId);
        if (batch == null || !user.getStationId().equals(batch.getStationId()))
            throw new ServiceException("采购批次不存在或无权访问");
        return batch;
    }

    private GasStationWriteRequest claim(H5User user, String operation, String requestId, String hash)
    {
        GasStationWriteRequest key = new GasStationWriteRequest();
        key.setStationId(user.getStationId());
        key.setUserId(user.getUserId());
        key.setOperation(operation);
        key.setRequestId(requestId);
        key.setPayloadHash(hash);
        requestMapper.claim(key);
        GasStationWriteRequest saved = requestMapper.selectForUpdate(key);
        if (saved == null) throw new ServiceException("提交记录不存在，请重试");
        if (!hash.equals(saved.getPayloadHash())) throw new ServiceException("请求号已用于不同内容，请勿修改重试数据");
        return saved;
    }

    private void complete(GasStationWriteRequest request, Long resultId)
    {
        request.setResultId(resultId);
        if (requestMapper.complete(request) != 1) throw new ServiceException("提交结果已变更，请刷新核对");
    }

    private void validate(Object input)
    {
        if (input == null) throw new ServiceException("提交内容不能为空");
        validator.validate(input).stream().findFirst().ifPresent(v -> { throw new ServiceException(v.getMessage()); });
    }

    private <T> Map<String, Object> page(int page, int size, Supplier<List<T>> select)
    {
        // 身份和归属查询必须在开启 PageHelper 之前完成，否则会错误分页身份查询。
        PageHelper.startPage(Math.min(Math.max(page, 1), 1_000_000), Math.min(Math.max(size, 1), 50));
        try
        {
            List<T> rows = select.get();
            return Map.of("rows", rows, "total", new PageInfo<>(rows).getTotal());
        }
        finally { PageHelper.clearPage(); }
    }

    private static String operator(SysUser admin) { return admin.getUserId() + ":" + admin.getLoginName(); }
    private static String text(String value) { return value == null ? "" : value.trim(); }
    private static String number(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    private static String date(Date value, String pattern)
    {
        SimpleDateFormat format = new SimpleDateFormat(pattern);
        format.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        return format.format(value);
    }
    private static String fingerprint(String... values)
    {
        StringBuilder payload = new StringBuilder();
        for (String value : values) payload.append(value.length()).append(':').append(value);
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.toString().getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
