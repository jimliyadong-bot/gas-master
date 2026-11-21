package com.ruoyi.gas.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 销售订单对象 gas_order（§3.4）
 *
 * <p>状态机：WAIT_PAY → PAID → WAIT_REDEEM？——本系统 PAID 即待核销，
 * 由加气员扫码核销（阶段6）转 COMPLETED；WAIT_PAY 超时/取消 → CLOSED。</p>
 *
 * @author gas
 */
public class GasOrder extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 状态：代下单待司机确认 */
    public static final String STATUS_WAIT_CONFIRM = "WAIT_CONFIRM";
    /** 状态：待支付 */
    public static final String STATUS_WAIT_PAY = "WAIT_PAY";
    /** 状态：已支付/待核销 */
    public static final String STATUS_PAID = "PAID";
    /** 状态：已完成（核销） */
    public static final String STATUS_COMPLETED = "COMPLETED";
    /** 状态：已关闭（超时/取消） */
    public static final String STATUS_CLOSED = "CLOSED";
    /** 状态：退款中/已退款（阶段8） */
    public static final String STATUS_REFUNDING = "REFUNDING";
    public static final String STATUS_REFUNDED = "REFUNDED";

    /** 来源：主动下单 */
    public static final String SOURCE_SELF = "SELF";
    /** 来源：加气员代下单 */
    public static final String SOURCE_PROXY = "PROXY";

    /** 订单ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** 来源（SELF/PROXY） */
    private String source;

    /** 状态 */
    private String status;

    /** 采购商ID */
    private Long buyerId;

    /** 站点ID */
    private Long stationId;

    /** 枪ID */
    private Long gunId;

    /** 枪号快照 */
    private Integer gunNo;

    /** 气种 */
    private String gasType;

    /** 会员ID */
    private Long memberId;

    /** 主动下单客户端幂等请求号 */
    private String requestId;

    /** 枪价快照 */
    private BigDecimal unitPrice;

    /** 原价金额 */
    private BigDecimal orderAmount;

    /** 优惠金额 */
    private BigDecimal discountAmount;

    /** 实付金额 */
    private BigDecimal payAmount;

    /** 累计已退金额 */
    private BigDecimal refundedAmount;

    /** 使用的券 */
    private Long couponId;

    /** 预计气量=原价金额÷枪价（亦为预占量） */
    private BigDecimal estVolume;

    /** 加气员确认量（实扣量） */
    private BigDecimal actualVolume;

    /** 收款商户配置ID */
    private Long merchantId;

    /** 支付渠道（微信/支付宝/云闪付） */
    private String payChannel;

    /** 代下单所在班次 */
    private Long createdShiftId;

    /** 核销所在班次 */
    private Long completedShiftId;

    /** 代下单加气员 */
    private Long proxyStaffId;

    /** 核销加气员 */
    private Long redeemStaffId;

    /** 待支付截止时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expireAt;

    /** 支付时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date paidAt;

    /** 完成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date completedAt;

    /** 关闭时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date closedAt;

    /** 关闭/拒绝原因 */
    private String closeReason;

    /** 核销异常说明 */
    private String exceptionNote;

    // ---- 以下为联查展示字段 ----

    /** 站点名称 */
    private String stationName;

    /** 站点编码 */
    private String stationCode;

    /** 会员手机号 */
    private String memberPhone;

    public Long getOrderId()
    {
        return orderId;
    }

    public void setOrderId(Long orderId)
    {
        this.orderId = orderId;
    }

    public String getOrderNo()
    {
        return orderNo;
    }

    public void setOrderNo(String orderNo)
    {
        this.orderNo = orderNo;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Long getBuyerId()
    {
        return buyerId;
    }

    public void setBuyerId(Long buyerId)
    {
        this.buyerId = buyerId;
    }

    public Long getStationId()
    {
        return stationId;
    }

    public void setStationId(Long stationId)
    {
        this.stationId = stationId;
    }

    public Long getGunId()
    {
        return gunId;
    }

    public void setGunId(Long gunId)
    {
        this.gunId = gunId;
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

    public Long getMemberId()
    {
        return memberId;
    }

    public void setMemberId(Long memberId)
    {
        this.memberId = memberId;
    }

    public String getRequestId()
    {
        return requestId;
    }

    public void setRequestId(String requestId)
    {
        this.requestId = requestId;
    }

    public BigDecimal getUnitPrice()
    {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice)
    {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getOrderAmount()
    {
        return orderAmount;
    }

    public void setOrderAmount(BigDecimal orderAmount)
    {
        this.orderAmount = orderAmount;
    }

    public BigDecimal getDiscountAmount()
    {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount)
    {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getPayAmount()
    {
        return payAmount;
    }

    public void setPayAmount(BigDecimal payAmount)
    {
        this.payAmount = payAmount;
    }

    public BigDecimal getRefundedAmount()
    {
        return refundedAmount;
    }

    public void setRefundedAmount(BigDecimal refundedAmount)
    {
        this.refundedAmount = refundedAmount;
    }

    public Long getCouponId()
    {
        return couponId;
    }

    public void setCouponId(Long couponId)
    {
        this.couponId = couponId;
    }

    public BigDecimal getEstVolume()
    {
        return estVolume;
    }

    public void setEstVolume(BigDecimal estVolume)
    {
        this.estVolume = estVolume;
    }

    public BigDecimal getActualVolume()
    {
        return actualVolume;
    }

    public void setActualVolume(BigDecimal actualVolume)
    {
        this.actualVolume = actualVolume;
    }

    public Long getMerchantId()
    {
        return merchantId;
    }

    public void setMerchantId(Long merchantId)
    {
        this.merchantId = merchantId;
    }

    public String getPayChannel()
    {
        return payChannel;
    }

    public void setPayChannel(String payChannel)
    {
        this.payChannel = payChannel;
    }

    public Long getCreatedShiftId()
    {
        return createdShiftId;
    }

    public void setCreatedShiftId(Long createdShiftId)
    {
        this.createdShiftId = createdShiftId;
    }

    public Long getCompletedShiftId()
    {
        return completedShiftId;
    }

    public void setCompletedShiftId(Long completedShiftId)
    {
        this.completedShiftId = completedShiftId;
    }

    public Long getProxyStaffId()
    {
        return proxyStaffId;
    }

    public void setProxyStaffId(Long proxyStaffId)
    {
        this.proxyStaffId = proxyStaffId;
    }

    public Long getRedeemStaffId()
    {
        return redeemStaffId;
    }

    public void setRedeemStaffId(Long redeemStaffId)
    {
        this.redeemStaffId = redeemStaffId;
    }

    public Date getExpireAt()
    {
        return expireAt;
    }

    public void setExpireAt(Date expireAt)
    {
        this.expireAt = expireAt;
    }

    public Date getPaidAt()
    {
        return paidAt;
    }

    public void setPaidAt(Date paidAt)
    {
        this.paidAt = paidAt;
    }

    public Date getCompletedAt()
    {
        return completedAt;
    }

    public void setCompletedAt(Date completedAt)
    {
        this.completedAt = completedAt;
    }

    public Date getClosedAt()
    {
        return closedAt;
    }

    public void setClosedAt(Date closedAt)
    {
        this.closedAt = closedAt;
    }

    public String getCloseReason()
    {
        return closeReason;
    }

    public void setCloseReason(String closeReason)
    {
        this.closeReason = closeReason;
    }

    public String getExceptionNote()
    {
        return exceptionNote;
    }

    public void setExceptionNote(String exceptionNote)
    {
        this.exceptionNote = exceptionNote;
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

    public String getMemberPhone()
    {
        return memberPhone;
    }

    public void setMemberPhone(String memberPhone)
    {
        this.memberPhone = memberPhone;
    }
}
