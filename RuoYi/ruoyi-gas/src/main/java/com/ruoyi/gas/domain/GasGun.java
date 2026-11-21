package com.ruoyi.gas.domain;

import jakarta.validation.constraints.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 加气枪对象 gas_gun
 *
 * @author gas
 */
public class GasGun extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 枪ID */
    private Long gunId;

    /** 站点ID */
    private Long stationId;

    /** 采购商ID（列表筛选用，联查 gas_station.buyer_id） */
    private Long buyerId;

    /** 枪号 */
    @Excel(name = "枪号", cellType = Excel.ColumnType.NUMERIC)
    private Integer gunNo;

    /** 气种（CNG/LNG） */
    @Excel(name = "气种")
    private String gasType;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 站点名称（列表联查） */
    @Excel(name = "所属站点")
    private String stationName;

    /** 站点编码（列表联查） */
    private String stationCode;

    /** 采购商名称（列表联查） */
    @Excel(name = "采购商")
    private String buyerName;

    public Long getGunId()
    {
        return gunId;
    }

    public void setGunId(Long gunId)
    {
        this.gunId = gunId;
    }

    @NotNull(message = "所属站点不能为空")
    public Long getStationId()
    {
        return stationId;
    }

    public void setStationId(Long stationId)
    {
        this.stationId = stationId;
    }

    public Long getBuyerId()
    {
        return buyerId;
    }

    public void setBuyerId(Long buyerId)
    {
        this.buyerId = buyerId;
    }

    @NotNull(message = "枪号不能为空")
    @Min(value = 1, message = "枪号必须为正整数")
    public Integer getGunNo()
    {
        return gunNo;
    }

    public void setGunNo(Integer gunNo)
    {
        this.gunNo = gunNo;
    }

    @NotBlank(message = "气种不能为空")
    public String getGasType()
    {
        return gasType;
    }

    public void setGasType(String gasType)
    {
        this.gasType = gasType;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStationName()
    {
        return stationName;
    }

    public void setStationName(String stationName)
    {
        this.stationName = stationName;
    }

    public String getStationCode()
    {
        return stationCode;
    }

    public void setStationCode(String stationCode)
    {
        this.stationCode = stationCode;
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
            .append("gunId", getGunId())
            .append("stationId", getStationId())
            .append("gunNo", getGunNo())
            .append("gasType", getGasType())
            .append("status", getStatus())
            .toString();
    }
}
