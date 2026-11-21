package com.ruoyi.gas.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 站点库存池对象 gas_inventory_pool
 *
 * <p>每站每气种一行；可用 = total_in + adjusted - consumed - reserved；
 * 所有变动必须经 InventoryService 完成（悲观锁串行化）。</p>
 *
 * @author gas
 */
public class GasInventoryPool extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 库存池ID */
    private Long poolId;

    /** 站点ID */
    private Long stationId;

    /** 采购商ID */
    private Long buyerId;

    /** 气种 */
    @Excel(name = "气种")
    private String gasType;

    /** 累计入库 */
    @Excel(name = "累计入库")
    private BigDecimal totalIn;

    /** 当前预占 */
    @Excel(name = "当前预占")
    private BigDecimal reserved;

    /** 累计实扣 */
    @Excel(name = "累计实扣")
    private BigDecimal consumed;

    /** 累计调整（可负） */
    @Excel(name = "累计调整")
    private BigDecimal adjusted;

    /** 余量预警值 */
    @Excel(name = "预警值")
    private BigDecimal alertThreshold;

    /** 预警待触发（触发后置0，回升后置1） */
    private Integer alertArmed;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    /** 站点编码（联查） */
    @Excel(name = "站点编码")
    private String stationCode;

    /** 站点名称（联查） */
    @Excel(name = "所属站点")
    private String stationName;

    /** 采购商名称（联查） */
    @Excel(name = "采购商")
    private String buyerName;

    /** 可用量（联查计算：total_in + adjusted - consumed - reserved） */
    @Excel(name = "可用量")
    private BigDecimal available;

    public Long getPoolId() { return poolId; }
    public void setPoolId(Long poolId) { this.poolId = poolId; }

    public Long getStationId() { return stationId; }
    public void setStationId(Long stationId) { this.stationId = stationId; }

    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }

    public String getGasType() { return gasType; }
    public void setGasType(String gasType) { this.gasType = gasType; }

    public BigDecimal getTotalIn() { return totalIn; }
    public void setTotalIn(BigDecimal totalIn) { this.totalIn = totalIn; }

    public BigDecimal getReserved() { return reserved; }
    public void setReserved(BigDecimal reserved) { this.reserved = reserved; }

    public BigDecimal getConsumed() { return consumed; }
    public void setConsumed(BigDecimal consumed) { this.consumed = consumed; }

    public BigDecimal getAdjusted() { return adjusted; }
    public void setAdjusted(BigDecimal adjusted) { this.adjusted = adjusted; }

    public BigDecimal getAlertThreshold() { return alertThreshold; }
    public void setAlertThreshold(BigDecimal alertThreshold) { this.alertThreshold = alertThreshold; }

    public Integer getAlertArmed() { return alertArmed; }
    public void setAlertArmed(Integer alertArmed) { this.alertArmed = alertArmed; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }

    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }

    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }

    public BigDecimal getAvailable() { return available; }
    public void setAvailable(BigDecimal available) { this.available = available; }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("poolId", getPoolId())
            .append("stationId", getStationId())
            .append("gasType", getGasType())
            .append("totalIn", getTotalIn())
            .append("reserved", getReserved())
            .append("consumed", getConsumed())
            .append("adjusted", getAdjusted())
            .toString();
    }
}
