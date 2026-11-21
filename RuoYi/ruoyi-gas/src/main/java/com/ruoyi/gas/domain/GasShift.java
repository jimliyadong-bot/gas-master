package com.ruoyi.gas.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/** 加气员班次（每站至多一个进行中班次，交班写入汇总快照） */
public class GasShift extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long shiftId;
    private Long stationId;
    private Long userId;
    private String staffName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endedAt;

    private Integer activeFlag;
    private String endedBy;
    private String endReason;
    private Integer proxyCount;
    private Integer redeemCount;
    private BigDecimal cngVolume;
    private BigDecimal lngVolume;
    private BigDecimal payAmount;
    private String stationName;
    /** 查询条件：ACTIVE/ENDED */
    private String shiftStatus;

    public boolean isActive()
    {
        return Integer.valueOf(1).equals(activeFlag);
    }

    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
    public Long getStationId() { return stationId; }
    public void setStationId(Long stationId) { this.stationId = stationId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getStaffName() { return staffName; }
    public void setStaffName(String staffName) { this.staffName = staffName; }
    public Date getStartedAt() { return startedAt; }
    public void setStartedAt(Date startedAt) { this.startedAt = startedAt; }
    public Date getEndedAt() { return endedAt; }
    public void setEndedAt(Date endedAt) { this.endedAt = endedAt; }
    public Integer getActiveFlag() { return activeFlag; }
    public void setActiveFlag(Integer activeFlag) { this.activeFlag = activeFlag; }
    public String getEndedBy() { return endedBy; }
    public void setEndedBy(String endedBy) { this.endedBy = endedBy; }
    public String getEndReason() { return endReason; }
    public void setEndReason(String endReason) { this.endReason = endReason; }
    public Integer getProxyCount() { return proxyCount; }
    public void setProxyCount(Integer proxyCount) { this.proxyCount = proxyCount; }
    public Integer getRedeemCount() { return redeemCount; }
    public void setRedeemCount(Integer redeemCount) { this.redeemCount = redeemCount; }
    public BigDecimal getCngVolume() { return cngVolume; }
    public void setCngVolume(BigDecimal cngVolume) { this.cngVolume = cngVolume; }
    public BigDecimal getLngVolume() { return lngVolume; }
    public void setLngVolume(BigDecimal lngVolume) { this.lngVolume = lngVolume; }
    public BigDecimal getPayAmount() { return payAmount; }
    public void setPayAmount(BigDecimal payAmount) { this.payAmount = payAmount; }
    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }
    public String getShiftStatus() { return shiftStatus; }
    public void setShiftStatus(String shiftStatus) { this.shiftStatus = shiftStatus; }
}
