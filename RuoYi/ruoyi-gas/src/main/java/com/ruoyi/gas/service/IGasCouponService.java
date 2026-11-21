package com.ruoyi.gas.service;

import java.math.BigDecimal;
import java.util.List;
import com.ruoyi.gas.domain.GasCoupon;

public interface IGasCouponService
{
    List<GasCoupon> selectCouponList(GasCoupon coupon);
    GasCoupon issue(Long templateId, Long memberId, String phone, String operator);
    List<GasCoupon> memberCoupons(Long memberId, String status);
    List<GasCoupon> available(Long memberId, Long buyerId, BigDecimal orderAmount);
    GasCoupon lockUsable(Long couponId, Long memberId, Long buyerId, BigDecimal orderAmount);
    int occupy(Long couponId, Long orderId);
    int release(Long couponId, Long orderId);
    int expireUnused();
}
