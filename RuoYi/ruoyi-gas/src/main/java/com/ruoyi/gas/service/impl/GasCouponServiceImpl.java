package com.ruoyi.gas.service.impl;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.gas.domain.GasCoupon;
import com.ruoyi.gas.domain.GasCouponTemplate;
import com.ruoyi.gas.domain.GasMember;
import com.ruoyi.gas.mapper.GasCouponMapper;
import com.ruoyi.gas.mapper.GasCouponTemplateMapper;
import com.ruoyi.gas.mapper.GasMemberMapper;
import com.ruoyi.gas.service.IGasCouponService;

@Service
public class GasCouponServiceImpl implements IGasCouponService
{
    @Autowired
    private GasCouponMapper couponMapper;
    @Autowired
    private GasCouponTemplateMapper templateMapper;
    @Autowired
    private GasMemberMapper memberMapper;

    @Override
    public List<GasCoupon> selectCouponList(GasCoupon coupon)
    {
        return couponMapper.selectCouponList(coupon);
    }

    @Override
    @Transactional
    public GasCoupon issue(Long templateId, Long memberId, String phone, String operator)
    {
        if (memberId == null && StringUtils.isEmpty(StringUtils.trim(phone)))
            throw new ServiceException("会员ID和手机号至少填写一项");
        // 行锁同时固定面额/采购商快照，避免RR下模板编辑与发券交错产生旧版本券
        GasCouponTemplate template = templateMapper.selectTemplateByIdForUpdate(templateId);
        if (template == null) throw new ServiceException("优惠券模板不存在");
        GasMember member = memberId != null ? memberMapper.selectMemberById(memberId)
                : memberMapper.selectMemberByPhone(StringUtils.trim(phone), null);
        if (member == null || !"0".equals(member.getStatus()) || StringUtils.isEmpty(member.getPhone()))
            throw new ServiceException("会员不存在、未绑定手机号或已停用");
        if (memberId != null && StringUtils.isNotEmpty(phone) && !member.getPhone().equals(phone.trim()))
            throw new ServiceException("会员ID与手机号不一致");
        if (templateMapper.increaseIssued(templateId) != 1)
            throw new ServiceException("模板已停用、不在发放期或已发完");

        Date now = new Date();
        GasCoupon coupon = new GasCoupon();
        coupon.setTemplateId(templateId);
        coupon.setBuyerId(template.getBuyerId());
        coupon.setMemberId(member.getMemberId());
        coupon.setFaceValue(template.getFaceValue());
        coupon.setThresholdAmount(template.getThresholdAmount());
        coupon.setExpireAt("FIXED".equals(template.getValidType()) ? template.getValidTo()
                : new Date(now.getTime() + TimeUnit.DAYS.toMillis(template.getValidDays())));
        coupon.setIssuedBy(StringUtils.isEmpty(operator) ? "system" : operator);
        couponMapper.insertCoupon(coupon);
        return coupon;
    }

    @Override
    public List<GasCoupon> memberCoupons(Long memberId, String status)
    {
        if (status != null && !status.isEmpty() && !GasCoupon.STATUS_UNUSED.equals(status)
                && !GasCoupon.STATUS_USED.equals(status) && !GasCoupon.STATUS_EXPIRED.equals(status))
            throw new ServiceException("优惠券状态无效");
        return couponMapper.selectMemberCoupons(memberId, status);
    }

    @Override
    public List<GasCoupon> available(Long memberId, Long buyerId, BigDecimal orderAmount)
    {
        if (memberId == null || buyerId == null || orderAmount == null) return List.of();
        return couponMapper.selectAvailable(memberId, buyerId, orderAmount);
    }

    @Override
    public GasCoupon lockUsable(Long couponId, Long memberId, Long buyerId, BigDecimal orderAmount)
    {
        if (couponId == null) return null;
        GasCoupon coupon = couponMapper.selectCouponByIdForUpdate(couponId);
        if (coupon == null || !coupon.getMemberId().equals(memberId) || !coupon.getBuyerId().equals(buyerId)
                || !GasCoupon.STATUS_UNUSED.equals(coupon.getStatus()) || !coupon.getExpireAt().after(new Date())
                || coupon.getThresholdAmount().compareTo(orderAmount) > 0
                || coupon.getFaceValue().compareTo(orderAmount) >= 0)
            throw new ServiceException("优惠券不可用、已过期、未达门槛或折后金额须至少0.01元");
        return coupon;
    }

    @Override
    public int occupy(Long couponId, Long orderId)
    {
        return couponMapper.occupy(couponId, orderId);
    }

    @Override
    public int release(Long couponId, Long orderId)
    {
        if (couponId == null || orderId == null) return 0;
        return couponMapper.release(couponId, orderId);
    }

    @Override
    public int expireUnused()
    {
        return couponMapper.expireUnused();
    }
}
