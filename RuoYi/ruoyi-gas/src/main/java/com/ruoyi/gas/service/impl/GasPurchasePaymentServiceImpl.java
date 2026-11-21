package com.ruoyi.gas.service.impl;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.gas.domain.GasPurchaseBatch;
import com.ruoyi.gas.domain.GasPurchasePayment;
import com.ruoyi.gas.mapper.GasPurchaseBatchMapper;
import com.ruoyi.gas.mapper.GasPurchasePaymentMapper;
import com.ruoyi.gas.service.IGasPurchasePaymentService;

/**
 * 采购付款登记 服务实现（只登记；登记/删除后联动批次付款状态）
 *
 * @author gas
 */
@Service
public class GasPurchasePaymentServiceImpl implements IGasPurchasePaymentService
{
    @Autowired
    private GasPurchasePaymentMapper paymentMapper;

    @Autowired
    private GasPurchaseBatchMapper batchMapper;

    @Override
    public List<GasPurchasePayment> selectPaymentList(GasPurchasePayment payment)
    {
        return paymentMapper.selectPaymentList(payment);
    }

    @Override
    @Transactional
    public int insertPayment(GasPurchasePayment payment)
    {
        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0
                || payment.getAmount().stripTrailingZeros().scale() > 2)
        {
            throw new ServiceException("付款金额必须大于0且最多保留两位小数");
        }
        // 先锁批次再登记：同批次并发付款串行化，并避免 RR 旧快照漏算已提交付款
        GasPurchaseBatch batch = batchMapper.selectBatchByIdForUpdate(payment.getBatchId());
        if (StringUtils.isNull(batch))
        {
            throw new ServiceException("采购批次不存在或已被删除");
        }
        int rows = paymentMapper.insertPayment(payment);
        refreshPayStatus(batch);
        return rows;
    }

    @Override
    @Transactional
    public int deletePaymentByIds(Long[] paymentIds)
    {
        // 删除前收集受影响批次（物理删除后无法回查归属）
        java.util.Set<Long> batchIds = new java.util.LinkedHashSet<>();
        for (Long paymentId : paymentIds)
        {
            GasPurchasePayment p = paymentMapper.selectPaymentById(paymentId);
            if (StringUtils.isNotNull(p))
            {
                batchIds.add(p.getBatchId());
            }
        }
        int rows = paymentMapper.deletePaymentByIds(paymentIds);
        for (Long batchId : batchIds)
        {
            GasPurchaseBatch batch = batchMapper.selectBatchByIdForUpdate(batchId);
            if (StringUtils.isNotNull(batch))
            {
                refreshPayStatus(batch);
            }
        }
        return rows;
    }

    /** 按累计付款重算批次付款状态：0=UNPAID，>=amount=PAID，否则 PARTIAL */
    private void refreshPayStatus(GasPurchaseBatch batch)
    {
        BigDecimal paid = paymentMapper.sumPaidByBatch(batch.getBatchId());
        String status;
        if (paid.compareTo(BigDecimal.ZERO) == 0)
        {
            status = "UNPAID";
        }
        else if (paid.compareTo(batch.getAmount()) >= 0)
        {
            status = "PAID";
        }
        else
        {
            status = "PARTIAL";
        }
        batchMapper.updateBatchPayStatus(batch.getBatchId(), status);
    }
}
