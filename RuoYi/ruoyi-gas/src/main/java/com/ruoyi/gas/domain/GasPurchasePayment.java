package com.ruoyi.gas.domain;

import java.math.BigDecimal;
import java.util.Date;
import jakarta.validation.constraints.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 采购付款登记对象 gas_purchase_payment
 *
 * <p>只登记（金额、日期、方式、凭证），无审批流程。</p>
 *
 * @author gas
 */
public class GasPurchasePayment extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 付款ID */
    private Long paymentId;

    /** 采购批次ID */
    private Long batchId;

    /** 付款金额 */
    @Excel(name = "付款金额")
    private BigDecimal amount;

    /** 付款日期 */
    @Excel(name = "付款日期", dateFormat = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date paidAt;

    /** 付款方式（BANK/ACCEPTANCE/CASH/OTHER） */
    @Excel(name = "付款方式", readConverterExp = "BANK=银行转账,ACCEPTANCE=承兑汇票,CASH=现金,OTHER=其他")
    private String payMethod;

    /** 付款凭证（URL） */
    @Excel(name = "付款凭证")
    private String voucherUrl;

    /** 批次号（联查） */
    @Excel(name = "批次号")
    private String batchNo;

    /** 站点名称（联查） */
    @Excel(name = "所属站点")
    private String stationName;

    /** 采购商名称（联查） */
    @Excel(name = "采购商")
    private String buyerName;

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }

    @NotNull(message = "采购批次不能为空")
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }

    @NotNull(message = "付款金额不能为空")
    @DecimalMin(value = "0.01", message = "付款金额必须大于0")
    @Digits(integer = 10, fraction = 2, message = "付款金额最多10位整数2位小数")
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    @NotNull(message = "付款日期不能为空")
    public Date getPaidAt() { return paidAt; }
    public void setPaidAt(Date paidAt) { this.paidAt = paidAt; }

    @NotBlank(message = "付款方式不能为空")
    public String getPayMethod() { return payMethod; }
    public void setPayMethod(String payMethod) { this.payMethod = payMethod; }

    @Size(min = 0, max = 500, message = "付款凭证长度不能超过500个字符")
    public String getVoucherUrl() { return voucherUrl; }
    public void setVoucherUrl(String voucherUrl) { this.voucherUrl = voucherUrl; }

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }

    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }

    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("paymentId", getPaymentId())
            .append("batchId", getBatchId())
            .append("amount", getAmount())
            .append("paidAt", getPaidAt())
            .append("payMethod", getPayMethod())
            .toString();
    }
}
