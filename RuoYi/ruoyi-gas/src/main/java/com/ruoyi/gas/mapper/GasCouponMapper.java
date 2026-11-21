package com.ruoyi.gas.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.gas.domain.GasCoupon;

public interface GasCouponMapper
{
    List<GasCoupon> selectCouponList(GasCoupon coupon);
    List<GasCoupon> selectMemberCoupons(@Param("memberId") Long memberId, @Param("status") String status);
    List<GasCoupon> selectAvailable(@Param("memberId") Long memberId, @Param("buyerId") Long buyerId,
            @Param("orderAmount") BigDecimal orderAmount);
    GasCoupon selectCouponByIdForUpdate(Long couponId);
    int insertCoupon(GasCoupon coupon);
    int occupy(@Param("couponId") Long couponId, @Param("orderId") Long orderId);
    int release(@Param("couponId") Long couponId, @Param("orderId") Long orderId);
    int expireUnused();
}
