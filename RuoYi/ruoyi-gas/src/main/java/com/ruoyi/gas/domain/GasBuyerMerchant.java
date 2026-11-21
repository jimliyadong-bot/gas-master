package com.ruoyi.gas.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 采购商收款商户对象 gas_buyer_merchant
 *
 * <p>每个采购商一个拉卡拉商户号（Dev_Plan §7.1 A-7）；密钥不存明文，只存引用。</p>
 *
 * @author gas
 */
public class GasBuyerMerchant extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 商户配置ID */
    private Long merchantId;

    /** 采购商ID */
    private Long buyerId;

    /** 支付通道（默认 LAKALA） */
    private String channel;

    /** 接入方式（CASHIER收银台 / JSAPI，待定 §7.3 C-2） */
    private String payMode;

    /** 商户号 */
    private String merchantNo;

    /** 终端号 */
    private String termNo;

    /** 开放平台应用ID */
    private String appId;

    /** 密钥引用（配置项名或加密串，不存明文） */
    private String secretRef;

    /** 状态（0正常 1停用） */
    private String status;

    public Long getMerchantId()
    {
        return merchantId;
    }

    public void setMerchantId(Long merchantId)
    {
        this.merchantId = merchantId;
    }

    public Long getBuyerId()
    {
        return buyerId;
    }

    public void setBuyerId(Long buyerId)
    {
        this.buyerId = buyerId;
    }

    public String getChannel()
    {
        return channel;
    }

    public void setChannel(String channel)
    {
        this.channel = channel;
    }

    public String getPayMode()
    {
        return payMode;
    }

    public void setPayMode(String payMode)
    {
        this.payMode = payMode;
    }

    public String getMerchantNo()
    {
        return merchantNo;
    }

    public void setMerchantNo(String merchantNo)
    {
        this.merchantNo = merchantNo;
    }

    public String getTermNo()
    {
        return termNo;
    }

    public void setTermNo(String termNo)
    {
        this.termNo = termNo;
    }

    public String getAppId()
    {
        return appId;
    }

    public void setAppId(String appId)
    {
        this.appId = appId;
    }

    public String getSecretRef()
    {
        return secretRef;
    }

    public void setSecretRef(String secretRef)
    {
        this.secretRef = secretRef;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("merchantId", getMerchantId())
            .append("buyerId", getBuyerId())
            .append("channel", getChannel())
            .append("payMode", getPayMode())
            .append("merchantNo", getMerchantNo())
            .append("status", getStatus())
            .toString();
    }
}
