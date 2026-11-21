package com.ruoyi.gas.service;

import java.util.List;
import com.ruoyi.gas.domain.GasPurchasePayment;

/**
 * 采购付款登记 服务层（只登记，无审批；登记/删除后联动批次付款状态）
 *
 * @author gas
 */
public interface IGasPurchasePaymentService
{
    public List<GasPurchasePayment> selectPaymentList(GasPurchasePayment payment);

    public int insertPayment(GasPurchasePayment payment);

    /** 删除登记后重算批次付款状态 */
    public int deletePaymentByIds(Long[] paymentIds);
}
