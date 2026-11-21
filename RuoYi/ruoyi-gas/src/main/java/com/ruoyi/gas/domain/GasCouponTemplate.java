package com.ruoyi.gas.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/** 优惠券模板，已发用户券保存面额、门槛和有效期快照。 */
public class GasCouponTemplate extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long templateId;
    private Long buyerId;
    private String buyerName;
    private String name;
    private BigDecimal faceValue;
    private BigDecimal thresholdAmount;
    private String validType;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date validFrom;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date validTo;
    private Integer validDays;
    private Integer totalQty;
    private Integer issuedQty;
    private String status;
    private Integer usedQty;
    private BigDecimal discountTotal;

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }
    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getFaceValue() { return faceValue; }
    public void setFaceValue(BigDecimal faceValue) { this.faceValue = faceValue; }
    public BigDecimal getThresholdAmount() { return thresholdAmount; }
    public void setThresholdAmount(BigDecimal thresholdAmount) { this.thresholdAmount = thresholdAmount; }
    public String getValidType() { return validType; }
    public void setValidType(String validType) { this.validType = validType; }
    public Date getValidFrom() { return validFrom; }
    public void setValidFrom(Date validFrom) { this.validFrom = validFrom; }
    public Date getValidTo() { return validTo; }
    public void setValidTo(Date validTo) { this.validTo = validTo; }
    public Integer getValidDays() { return validDays; }
    public void setValidDays(Integer validDays) { this.validDays = validDays; }
    public Integer getTotalQty() { return totalQty; }
    public void setTotalQty(Integer totalQty) { this.totalQty = totalQty; }
    public Integer getIssuedQty() { return issuedQty; }
    public void setIssuedQty(Integer issuedQty) { this.issuedQty = issuedQty; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getUsedQty() { return usedQty; }
    public void setUsedQty(Integer usedQty) { this.usedQty = usedQty; }
    public BigDecimal getDiscountTotal() { return discountTotal; }
    public void setDiscountTotal(BigDecimal discountTotal) { this.discountTotal = discountTotal; }
}
