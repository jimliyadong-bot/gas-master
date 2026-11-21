package com.ruoyi.gas.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 提醒记录对象 gas_remind（开票提醒/库存预警，兼作防重复发送）
 *
 * @author gas
 */
public class GasRemind extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 提醒ID */
    private Long remindId;

    /** 类型（BATCH_NEAR_EMPTY/BATCH_EMPTY/BATCH_UNINVOICED/STOCK_ALERT） */
    @Excel(name = "类型", readConverterExp = "BATCH_NEAR_EMPTY=批次将用完,BATCH_EMPTY=批次已用完,BATCH_UNINVOICED=用完未开票,STOCK_ALERT=库存预警")
    private String remindType;

    /** 采购商ID */
    private Long buyerId;

    /** 站点ID */
    private Long stationId;

    /** 采购批次ID（库存预警为0） */
    private Long batchId;

    /** 库存池ID（开票提醒为0） */
    private Long poolId;

    /** 第几次（防重复） */
    @Excel(name = "序号")
    private Integer seq;

    /** 提醒内容 */
    @Excel(name = "提醒内容")
    private String content;

    /** 计划发送时间（避开21:00-8:00） */
    @Excel(name = "计划发送", dateFormat = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date planSendAt;

    /** 状态（PENDING/SENT/FAILED） */
    @Excel(name = "状态", readConverterExp = "PENDING=待发送,SENT=已发送,FAILED=发送失败")
    private String status;

    /** 发送时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date sentAt;

    /** 触发时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** 采购商名称（联查） */
    @Excel(name = "采购商")
    private String buyerName;

    /** 站点名称（联查） */
    @Excel(name = "所属站点")
    private String stationName;

    /** 批次号（联查） */
    @Excel(name = "批次号")
    private String batchNo;

    public Long getRemindId() { return remindId; }
    public void setRemindId(Long remindId) { this.remindId = remindId; }

    public String getRemindType() { return remindType; }
    public void setRemindType(String remindType) { this.remindType = remindType; }

    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }

    public Long getStationId() { return stationId; }
    public void setStationId(Long stationId) { this.stationId = stationId; }

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }

    public Long getPoolId() { return poolId; }
    public void setPoolId(Long poolId) { this.poolId = poolId; }

    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Date getPlanSendAt() { return planSendAt; }
    public void setPlanSendAt(Date planSendAt) { this.planSendAt = planSendAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getSentAt() { return sentAt; }
    public void setSentAt(Date sentAt) { this.sentAt = sentAt; }

    @Override
    public Date getCreateTime() { return createTime; }
    @Override
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }

    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("remindId", getRemindId())
            .append("remindType", getRemindType())
            .append("buyerId", getBuyerId())
            .append("batchId", getBatchId())
            .append("seq", getSeq())
            .append("status", getStatus())
            .toString();
    }
}
