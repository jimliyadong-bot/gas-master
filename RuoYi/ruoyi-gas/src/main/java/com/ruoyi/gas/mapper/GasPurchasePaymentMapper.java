package com.ruoyi.gas.mapper;

import java.math.BigDecimal;
import java.util.List;
import com.ruoyi.gas.domain.GasPurchasePayment;

/**
 * 采购付款 数据层
 *
 * @author gas
 */
public interface GasPurchasePaymentMapper
{
    public List<GasPurchasePayment> selectPaymentList(GasPurchasePayment payment);

    public GasPurchasePayment selectPaymentById(Long paymentId);

    public int insertPayment(GasPurchasePayment payment);

    public int deletePaymentByIds(Long[] paymentIds);

    /** 批次累计付款金额 */
    public BigDecimal sumPaidByBatch(Long batchId);

    /** 付款笔数（批次删除守卫） */
    public int countPaymentByBatchIds(Long[] batchIds);
}
