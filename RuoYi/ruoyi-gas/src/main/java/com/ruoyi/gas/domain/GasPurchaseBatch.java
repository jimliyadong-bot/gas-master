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
 * 采购批次对象 gas_purchase_batch
 *
 * @author gas
 */
public class GasPurchaseBatch extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 批次ID */
    private Long batchId;

    /** 批次号 */
    @Excel(name = "批次号")
    private String batchNo;

    /** 采购商ID */
    private Long buyerId;

    /** 站点ID */
    private Long stationId;

    /** 气种（CNG/LNG） */
    @Excel(name = "气种")
    private String gasType;

    /** 采购日期 */
    @Excel(name = "采购日期", dateFormat = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date purchaseDate;

    /** 采购数量（m³/kg） */
    @Excel(name = "采购数量")
    private BigDecimal qty;

    /** 采购单价 */
    @Excel(name = "采购单价")
    private BigDecimal unitPrice;

    /** 采购金额 */
    @Excel(name = "采购金额")
    private BigDecimal amount;

    /** 付款状态（UNPAID/PARTIAL/PAID） */
    @Excel(name = "付款状态", readConverterExp = "UNPAID=未付款,PARTIAL=部分付款,PAID=已付清")
    private String payStatus;

    /** 票据状态（UNINVOICED/INVOICED） */
    @Excel(name = "票据状态", readConverterExp = "UNINVOICED=未开票,INVOICED=已开票")
    private String invoiceStatus;

    /** 发票号 */
    @Excel(name = "发票号")
    private String invoiceNo;

    /** 开票时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date invoicedAt;

    /** 乐观锁版本 */
    private Integer version;

    /** 站点编码（联查） */
    @Excel(name = "站点编码")
    private String stationCode;

    /** 站点名称（联查） */
    @Excel(name = "所属站点")
    private String stationName;

    /** 采购商名称（联查） */
    @Excel(name = "采购商")
    private String buyerName;

    /** 累计到货数量（联查） */
    private BigDecimal receivedQty;

    /** 累计付款金额（联查） */
    private BigDecimal paidAmount;

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }

    @NotBlank(message = "批次号不能为空")
    @Size(min = 0, max = 32, message = "批次号长度不能超过32个字符")
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }

    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }

    @NotNull(message = "所属站点不能为空")
    public Long getStationId() { return stationId; }
    public void setStationId(Long stationId) { this.stationId = stationId; }

    @NotBlank(message = "气种不能为空")
    public String getGasType() { return gasType; }
    public void setGasType(String gasType) { this.gasType = gasType; }

    @NotNull(message = "采购日期不能为空")
    public Date getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(Date purchaseDate) { this.purchaseDate = purchaseDate; }

    @NotNull(message = "采购数量不能为空")
    @DecimalMin(value = "0.01", message = "采购数量必须大于0")
    @Digits(integer = 12, fraction = 2, message = "采购数量最多12位整数2位小数")
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }

    @NotNull(message = "采购单价不能为空")
    @DecimalMin(value = "0.0001", message = "采购单价必须大于0")
    @Digits(integer = 6, fraction = 4, message = "采购单价最多6位整数4位小数")
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    @NotNull(message = "采购金额不能为空")
    @DecimalMin(value = "0.01", message = "采购金额必须大于0")
    @Digits(integer = 10, fraction = 2, message = "采购金额最多10位整数2位小数")
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getPayStatus() { return payStatus; }
    public void setPayStatus(String payStatus) { this.payStatus = payStatus; }

    public String getInvoiceStatus() { return invoiceStatus; }
    public void setInvoiceStatus(String invoiceStatus) { this.invoiceStatus = invoiceStatus; }

    @Size(min = 0, max = 64, message = "发票号长度不能超过64个字符")
    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }

    public Date getInvoicedAt() { return invoicedAt; }
    public void setInvoicedAt(Date invoicedAt) { this.invoicedAt = invoicedAt; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }

    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }

    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }

    public BigDecimal getReceivedQty() { return receivedQty; }
    public void setReceivedQty(BigDecimal receivedQty) { this.receivedQty = receivedQty; }

    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("batchId", getBatchId())
            .append("batchNo", getBatchNo())
            .append("stationId", getStationId())
            .append("gasType", getGasType())
            .append("purchaseDate", getPurchaseDate())
            .append("qty", getQty())
            .append("amount", getAmount())
            .append("payStatus", getPayStatus())
            .append("invoiceStatus", getInvoiceStatus())
            .toString();
    }
}
