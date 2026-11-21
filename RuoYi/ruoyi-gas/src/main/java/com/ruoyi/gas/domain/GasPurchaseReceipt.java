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
 * 采购到货/入库对象 gas_purchase_receipt
 *
 * <p>先进先出的扣减单位：余量 = qty + adjusted - consumed。</p>
 *
 * @author gas
 */
public class GasPurchaseReceipt extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 到货ID */
    private Long receiptId;

    /** 采购批次ID */
    private Long batchId;

    /** 采购商ID */
    private Long buyerId;

    /** 站点ID */
    private Long stationId;

    /** 气种 */
    @Excel(name = "气种")
    private String gasType;

    /** 到货入库数量 */
    @Excel(name = "入库数量")
    private BigDecimal qty;

    /** 已实扣数量（先进先出） */
    @Excel(name = "已实扣")
    private BigDecimal consumed;

    /** 累计调整（可负） */
    @Excel(name = "累计调整")
    private BigDecimal adjusted;

    /** 到货时间（先进先出排序依据） */
    @Excel(name = "到货时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date receivedAt;

    /** 版本（乐观锁） */
    private Integer version;

    /** 批次号（联查） */
    @Excel(name = "批次号")
    private String batchNo;

    /** 站点名称（联查） */
    @Excel(name = "所属站点")
    private String stationName;

    /** 采购商名称（联查） */
    @Excel(name = "采购商")
    private String buyerName;

    /** 剩余量 = qty + adjusted - consumed（联查计算，展示用） */
    private BigDecimal remain;

    public Long getReceiptId() { return receiptId; }
    public void setReceiptId(Long receiptId) { this.receiptId = receiptId; }

    @NotNull(message = "采购批次不能为空")
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }

    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }

    public Long getStationId() { return stationId; }
    public void setStationId(Long stationId) { this.stationId = stationId; }

    public String getGasType() { return gasType; }
    public void setGasType(String gasType) { this.gasType = gasType; }

    @NotNull(message = "到货数量不能为空")
    @DecimalMin(value = "0.01", message = "到货数量必须大于0")
    @Digits(integer = 12, fraction = 2, message = "到货数量最多12位整数2位小数")
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }

    public BigDecimal getConsumed() { return consumed; }
    public void setConsumed(BigDecimal consumed) { this.consumed = consumed; }

    public BigDecimal getAdjusted() { return adjusted; }
    public void setAdjusted(BigDecimal adjusted) { this.adjusted = adjusted; }

    @NotNull(message = "到货时间不能为空")
    public Date getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Date receivedAt) { this.receivedAt = receivedAt; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }

    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }

    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }

    public BigDecimal getRemain() { return remain; }
    public void setRemain(BigDecimal remain) { this.remain = remain; }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("receiptId", getReceiptId())
            .append("batchId", getBatchId())
            .append("stationId", getStationId())
            .append("gasType", getGasType())
            .append("qty", getQty())
            .append("consumed", getConsumed())
            .append("adjusted", getAdjusted())
            .append("receivedAt", getReceivedAt())
            .toString();
    }
}
