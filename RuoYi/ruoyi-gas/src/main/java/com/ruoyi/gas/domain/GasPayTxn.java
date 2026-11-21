package com.ruoyi.gas.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 支付流水对象 gas_pay_txn
 *
 * <p>回调幂等：uk_out_trade_no + 条件更新非 SUCCESS → SUCCESS（含 CLOSED 迟到支付）。</p>
 *
 * @author gas
 */
public class GasPayTxn
{
    private static final long serialVersionUID = 1L;

    /** 状态：支付中 */
    public static final String STATUS_PAYING = "PAYING";
    /** 状态：成功 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 状态：失败 */
    public static final String STATUS_FAILED = "FAILED";
    /** 状态：已关闭（订单超时关单联动） */
    public static final String STATUS_CLOSED = "CLOSED";

    /** ID */
    private Long txnId;

    /** 订单ID */
    private Long orderId;

    /** 收款商户配置ID */
    private Long merchantId;

    /** 我方交易号 */
    private String outTradeNo;

    /** 拉卡拉交易号 */
    private String channelTradeNo;

    /** 微信/支付宝/云闪付 */
    private String payChannel;

    /** 金额 */
    private BigDecimal amount;

    /** 状态（PAYING/SUCCESS/FAILED/CLOSED） */
    private String status;

    /** 支付成功时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date paidAt;

    /** 回调原文 */
    private String rawNotify;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 订单号（联查） */
    private String orderNo;

    public Long getTxnId()
    {
        return txnId;
    }

    public void setTxnId(Long txnId)
    {
        this.txnId = txnId;
    }

    public Long getOrderId()
    {
        return orderId;
    }

    public void setOrderId(Long orderId)
    {
        this.orderId = orderId;
    }

    public Long getMerchantId()
    {
        return merchantId;
    }

    public void setMerchantId(Long merchantId)
    {
        this.merchantId = merchantId;
    }

    public String getOutTradeNo()
    {
        return outTradeNo;
    }

    public void setOutTradeNo(String outTradeNo)
    {
        this.outTradeNo = outTradeNo;
    }

    public String getChannelTradeNo()
    {
        return channelTradeNo;
    }

    public void setChannelTradeNo(String channelTradeNo)
    {
        this.channelTradeNo = channelTradeNo;
    }

    public String getPayChannel()
    {
        return payChannel;
    }

    public void setPayChannel(String payChannel)
    {
        this.payChannel = payChannel;
    }

    public BigDecimal getAmount()
    {
        return amount;
    }

    public void setAmount(BigDecimal amount)
    {
        this.amount = amount;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Date getPaidAt()
    {
        return paidAt;
    }

    public void setPaidAt(Date paidAt)
    {
        this.paidAt = paidAt;
    }

    public String getRawNotify()
    {
        return rawNotify;
    }

    public void setRawNotify(String rawNotify)
    {
        this.rawNotify = rawNotify;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public String getOrderNo()
    {
        return orderNo;
    }

    public void setOrderNo(String orderNo)
    {
        this.orderNo = orderNo;
    }
}
