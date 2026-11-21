package com.ruoyi.gas.domain;

import jakarta.validation.constraints.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 加气站对象 gas_station
 *
 * @author gas
 */
public class GasStation extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 站点ID */
    private Long stationId;

    /** 站点编码 */
    @Excel(name = "站点编码")
    private String stationCode;

    /** 站点名称 */
    @Excel(name = "站点名称")
    private String stationName;

    /** 所属采购商ID */
    @Excel(name = "采购商ID", cellType = Excel.ColumnType.NUMERIC)
    private Long buyerId;

    /** 对应 sys_dept（站点管理员数据权限） */
    private Long deptId;

    /** 地址 */
    @Excel(name = "地址")
    private String address;

    /** 站点二维码标识 */
    private String qrToken;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    /** 采购商名称（列表联查） */
    @Excel(name = "采购商")
    private String buyerName;

    public Long getStationId()
    {
        return stationId;
    }

    public void setStationId(Long stationId)
    {
        this.stationId = stationId;
    }

    @NotBlank(message = "站点编码不能为空")
    @Size(min = 0, max = 32, message = "站点编码长度不能超过32个字符")
    public String getStationCode()
    {
        return stationCode;
    }

    public void setStationCode(String stationCode)
    {
        this.stationCode = stationCode;
    }

    @NotBlank(message = "站点名称不能为空")
    @Size(min = 0, max = 100, message = "站点名称长度不能超过100个字符")
    public String getStationName()
    {
        return stationName;
    }

    public void setStationName(String stationName)
    {
        this.stationName = stationName;
    }

    @NotNull(message = "所属采购商不能为空")
    public Long getBuyerId()
    {
        return buyerId;
    }

    public void setBuyerId(Long buyerId)
    {
        this.buyerId = buyerId;
    }

    public Long getDeptId()
    {
        return deptId;
    }

    public void setDeptId(Long deptId)
    {
        this.deptId = deptId;
    }

    public String getAddress()
    {
        return address;
    }

    public void setAddress(String address)
    {
        this.address = address;
    }

    public String getQrToken()
    {
        return qrToken;
    }

    public void setQrToken(String qrToken)
    {
        this.qrToken = qrToken;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
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
            .append("stationId", getStationId())
            .append("stationCode", getStationCode())
            .append("stationName", getStationName())
            .append("buyerId", getBuyerId())
            .append("deptId", getDeptId())
            .append("qrToken", getQrToken())
            .append("status", getStatus())
            .toString();
    }
}
