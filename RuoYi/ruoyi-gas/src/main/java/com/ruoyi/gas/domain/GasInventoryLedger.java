package com.ruoyi.gas.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 库存流水对象 gas_inventory_ledger
 *
 * <p>只追加不修改；uk_order_biz(order_ref, biz_type, receipt_id) 保证订单动作幂等。</p>
 *
 * @author gas
 */
public class GasInventoryLedger extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 流水ID */
    private Long ledgerId;

    /** 库存池ID */
    private Long poolId;

    /** 到货ID（IN/CONSUME/ADJUST 填；RESERVE/RELEASE 为0） */
    private Long receiptId;

    /** 订单ID（非订单流水为0） */
    private Long orderId;

    /** 类型（IN/RESERVE/RELEASE/CONSUME/ADJUST） */
    @Excel(name = "类型", readConverterExp = "IN=入库,RESERVE=预占,RELEASE=释放,CONSUME=实扣,ADJUST=调整")
    private String bizType;

    /** 数量（ADJUST 可负） */
    @Excel(name = "数量")
    private BigDecimal qty;

    /** 原因（ADJUST 必填） */
    @Excel(name = "原因")
    private String reason;

    /** 操作人 */
    @Excel(name = "操作人")
    private String operator;

    /** 发生时间 */
    @Excel(name = "发生时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** 站点ID（联查/筛选） */
    private Long stationId;

    /** 采购商ID（列表筛选用，联查 gas_inventory_pool.buyer_id） */
    private Long buyerId;

    /** 站点名称（联查） */
    @Excel(name = "所属站点")
    private String stationName;

    /** 气种（联查） */
    @Excel(name = "气种")
    private String gasType;

    /** 批次号（联查，receipt → batch） */
    @Excel(name = "批次号")
    private String batchNo;

    public Long getLedgerId() { return ledgerId; }
    public void setLedgerId(Long ledgerId) { this.ledgerId = ledgerId; }

    public Long getPoolId() { return poolId; }
    public void setPoolId(Long poolId) { this.poolId = poolId; }

    public Long getReceiptId() { return receiptId; }
    public void setReceiptId(Long receiptId) { this.receiptId = receiptId; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }

    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    @Override
    public Date getCreateTime() { return createTime; }
    @Override
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Long getStationId() { return stationId; }
    public void setStationId(Long stationId) { this.stationId = stationId; }

    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }

    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }

    public String getGasType() { return gasType; }
    public void setGasType(String gasType) { this.gasType = gasType; }

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("ledgerId", getLedgerId())
            .append("poolId", getPoolId())
            .append("receiptId", getReceiptId())
            .append("orderId", getOrderId())
            .append("bizType", getBizType())
            .append("qty", getQty())
            .append("operator", getOperator())
            .toString();
    }
}
