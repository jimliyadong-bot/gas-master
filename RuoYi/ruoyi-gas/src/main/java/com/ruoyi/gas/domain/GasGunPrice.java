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
 * 枪价对象 gas_gun_price
 *
 * <p>只增不改：新价生效时自动结束旧价（effective_to = 新价 effective_from），历史保留。</p>
 *
 * @author gas
 */
public class GasGunPrice extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 价格ID */
    private Long priceId;

    /** 枪ID */
    private Long gunId;

    /** 销售单价（元/m³ 或 元/kg） */
    @Excel(name = "销售单价")
    private BigDecimal price;

    /** 生效时间 */
    @Excel(name = "生效时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date effectiveFrom;

    /** 失效时间（空=当前有效） */
    @Excel(name = "失效时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date effectiveTo;

    /** 枪号（列表联查） */
    @Excel(name = "枪号", cellType = Excel.ColumnType.NUMERIC)
    private Integer gunNo;

    /** 气种（列表联查） */
    @Excel(name = "气种")
    private String gasType;

    /** 站点ID（列表联查，也用于列表筛选） */
    private Long stationId;

    /** 站点名称（列表联查） */
    @Excel(name = "所属站点")
    private String stationName;

    /** 采购商名称（列表联查） */
    @Excel(name = "采购商")
    private String buyerName;

    public Long getPriceId()
    {
        return priceId;
    }

    public void setPriceId(Long priceId)
    {
        this.priceId = priceId;
    }

    @NotNull(message = "枪不能为空")
    public Long getGunId()
    {
        return gunId;
    }

    public void setGunId(Long gunId)
    {
        this.gunId = gunId;
    }

    @NotNull(message = "销售单价不能为空")
    @DecimalMin(value = "0.0001", message = "销售单价必须大于0")
    @Digits(integer = 6, fraction = 4, message = "销售单价最多6位整数4位小数")
    public BigDecimal getPrice()
    {
        return price;
    }

    public void setPrice(BigDecimal price)
    {
        this.price = price;
    }

    @NotNull(message = "生效时间不能为空")
    public Date getEffectiveFrom()
    {
        return effectiveFrom;
    }

    public void setEffectiveFrom(Date effectiveFrom)
    {
        this.effectiveFrom = effectiveFrom;
    }

    public Date getEffectiveTo()
    {
        return effectiveTo;
    }

    public void setEffectiveTo(Date effectiveTo)
    {
        this.effectiveTo = effectiveTo;
    }

    public Integer getGunNo()
    {
        return gunNo;
    }

    public void setGunNo(Integer gunNo)
    {
        this.gunNo = gunNo;
    }

    public String getGasType()
    {
        return gasType;
    }

    public void setGasType(String gasType)
    {
        this.gasType = gasType;
    }

    public Long getStationId()
    {
        return stationId;
    }

    public void setStationId(Long stationId)
    {
        this.stationId = stationId;
    }

    public String getStationName()
    {
        return stationName;
    }

    public void setStationName(String stationName)
    {
        this.stationName = stationName;
    }

    public String getBuyerName()
    {
        return buyerName;
    }

    public void setBuyerName(String buyerName)
    {
        this.buyerName = buyerName;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("priceId", getPriceId())
            .append("gunId", getGunId())
            .append("price", getPrice())
            .append("effectiveFrom", getEffectiveFrom())
            .append("effectiveTo", getEffectiveTo())
            .toString();
    }
}
