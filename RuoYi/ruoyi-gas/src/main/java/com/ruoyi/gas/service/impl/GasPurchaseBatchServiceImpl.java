package com.ruoyi.gas.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.gas.domain.GasBuyer;
import com.ruoyi.gas.domain.GasPurchaseBatch;
import com.ruoyi.gas.domain.GasStation;
import com.ruoyi.gas.mapper.GasBuyerMapper;
import com.ruoyi.gas.mapper.GasPurchaseBatchMapper;
import com.ruoyi.gas.mapper.GasPurchasePaymentMapper;
import com.ruoyi.gas.mapper.GasPurchaseReceiptMapper;
import com.ruoyi.gas.mapper.GasStationMapper;
import com.ruoyi.gas.service.IGasPurchaseBatchService;

/**
 * 采购批次 服务实现
 *
 * @author gas
 */
@Service
public class GasPurchaseBatchServiceImpl implements IGasPurchaseBatchService
{
    @Autowired
    private GasPurchaseBatchMapper batchMapper;

    @Autowired
    private GasPurchaseReceiptMapper receiptMapper;

    @Autowired
    private GasPurchasePaymentMapper paymentMapper;

    @Autowired
    private GasStationMapper stationMapper;

    @Autowired
    private GasBuyerMapper buyerMapper;

    @Override
    public List<GasPurchaseBatch> selectBatchList(GasPurchaseBatch batch)
    {
        return batchMapper.selectBatchList(batch);
    }

    @Override
    public GasPurchaseBatch selectBatchById(Long batchId)
    {
        return batchMapper.selectBatchById(batchId);
    }

    @Override
    public String generateBatchNo()
    {
        return "PB" + DateUtils.dateTimeNow();
    }

    @Override
    public boolean checkBatchNoUnique(GasPurchaseBatch batch)
    {
        Long batchId = StringUtils.isNull(batch.getBatchId()) ? -1L : batch.getBatchId();
        GasPurchaseBatch info = batchMapper.checkBatchNoUnique(batch.getBatchNo());
        return !(StringUtils.isNotNull(info) && info.getBatchId().longValue() != batchId.longValue());
    }

    @Override
    @Transactional
    public int insertBatch(GasPurchaseBatch batch)
    {
        checkStationValid(batch.getStationId());
        GasStation station = stationMapper.selectStationById(batch.getStationId());
        batch.setBuyerId(station.getBuyerId());
        batch.setAmount(calculateAmount(batch.getQty(), batch.getUnitPrice()));
        return batchMapper.insertBatch(batch);
    }

    @Override
    @Transactional
    public int updateBatch(GasPurchaseBatch batch)
    {
        GasPurchaseBatch old = batchMapper.selectBatchByIdForUpdate(batch.getBatchId());
        if (StringUtils.isNull(old))
        {
            throw new ServiceException("采购批次不存在或已被删除");
        }
        batch.setBuyerId(old.getBuyerId());
        batch.setQty(batch.getQty() == null ? old.getQty() : batch.getQty());
        batch.setUnitPrice(batch.getUnitPrice() == null ? old.getUnitPrice() : batch.getUnitPrice());
        batch.setAmount(calculateAmount(batch.getQty(), batch.getUnitPrice()));
        if (batch.getQty().compareTo(old.getQty()) != 0
                && batch.getQty().compareTo(receiptMapper.sumReceivedQtyForUpdate(batch.getBatchId())) < 0)
        {
            throw new ServiceException("采购数量不能小于累计到货数量");
        }
        boolean stationChanged = StringUtils.isNotNull(batch.getStationId())
                && batch.getStationId().longValue() != old.getStationId().longValue();
        boolean gasChanged = StringUtils.isNotEmpty(batch.getGasType()) && !batch.getGasType().equals(old.getGasType());
        if ((stationChanged || gasChanged) && receiptMapper.countByBatchIds(new Long[] { batch.getBatchId() }) > 0)
        {
            throw new ServiceException("批次已有到货入库记录，不允许变更站点或气种");
        }
        if (stationChanged)
        {
            checkStationValid(batch.getStationId());
            GasStation station = stationMapper.selectStationById(batch.getStationId());
            batch.setBuyerId(station.getBuyerId());
        }
        int rows = batchMapper.updateBatch(batch);
        if (rows == 0)
        {
            throw new ServiceException("批次已被他人修改，请刷新后重试");
        }
        BigDecimal paid = paymentMapper.sumPaidByBatch(batch.getBatchId());
        String payStatus = paid.compareTo(BigDecimal.ZERO) == 0 ? "UNPAID"
                : paid.compareTo(batch.getAmount()) >= 0 ? "PAID" : "PARTIAL";
        if (!payStatus.equals(old.getPayStatus()))
        {
            batchMapper.updateBatchPayStatus(batch.getBatchId(), payStatus);
        }
        return rows;
    }

    @Override
    @Transactional
    public int deleteBatchByIds(Long[] batchIds)
    {
        // 与到货入库使用同一把批次行锁，堵住“检查无到货后并发入库”的竞态
        for (Long batchId : batchIds)
        {
            if (batchMapper.selectBatchByIdForUpdate(batchId) == null)
            {
                throw new ServiceException("采购批次不存在或已被删除");
            }
        }
        if (receiptMapper.countByBatchIds(batchIds) > 0)
        {
            throw new ServiceException("所选批次存在到货入库记录，无法删除");
        }
        if (paymentMapper.countPaymentByBatchIds(batchIds) > 0)
        {
            throw new ServiceException("所选批次存在付款登记记录，无法删除");
        }
        return batchMapper.deleteBatchByIds(batchIds);
    }

    @Override
    @Transactional
    public int updateBatchInvoice(Long batchId, String invoiceStatus, String invoiceNo, String operator)
    {
        GasPurchaseBatch batch = batchMapper.selectBatchById(batchId);
        if (StringUtils.isNull(batch))
        {
            throw new ServiceException("采购批次不存在或已被删除");
        }
        if ("INVOICED".equals(invoiceStatus) && StringUtils.isEmpty(invoiceNo))
        {
            throw new ServiceException("标记为已开票时必须填写发票号");
        }
        return batchMapper.updateBatchInvoice(batchId, invoiceStatus, StringUtils.isEmpty(invoiceNo) ? null : invoiceNo);
    }

    private BigDecimal calculateAmount(BigDecimal qty, BigDecimal unitPrice)
    {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0
                || unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new ServiceException("采购数量和采购单价必须大于0");
        }
        return qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    private void checkStationValid(Long stationId)
    {
        GasStation station = stationMapper.selectStationById(stationId);
        if (StringUtils.isNull(station))
        {
            throw new ServiceException("所属站点不存在或已被删除");
        }
        GasBuyer buyer = buyerMapper.selectBuyerById(station.getBuyerId());
        if (StringUtils.isNull(buyer))
        {
            throw new ServiceException("站点归属的采购商不存在或已被删除");
        }
    }
}
