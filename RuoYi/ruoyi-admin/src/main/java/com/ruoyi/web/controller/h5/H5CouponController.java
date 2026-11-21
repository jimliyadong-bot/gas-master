package com.ruoyi.web.controller.h5;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.gas.domain.GasMember;
import com.ruoyi.gas.domain.GasOrder;
import com.ruoyi.gas.h5.H5User;
import com.ruoyi.gas.h5.H5UserContext;
import com.ruoyi.gas.mapper.GasMemberMapper;
import com.ruoyi.gas.service.IGasCouponService;
import com.ruoyi.gas.service.IH5OrderService;

/** H5 本人券包及代下单确认时的可用券。 */
@Anonymous
@RestController
@RequestMapping("/api/h5/c/coupon")
public class H5CouponController extends BaseController
{
    @Autowired
    private IGasCouponService couponService;
    @Autowired
    private IH5OrderService orderService;
    @Autowired
    private GasMemberMapper memberMapper;

    @GetMapping("/list")
    public AjaxResult list(@RequestParam(value = "status", required = false) String status)
    {
        H5User user = H5UserContext.require();
        requireActiveMember(user);
        return success(couponService.memberCoupons(user.getMemberId(), status));
    }

    @GetMapping("/available")
    public AjaxResult available(@RequestParam("orderNo") String orderNo)
    {
        H5User user = H5UserContext.require();
        GasOrder order = orderService.myOrder(user, orderNo);
        if (!GasOrder.STATUS_WAIT_CONFIRM.equals(order.getStatus()))
            throw new ServiceException("仅待确认的代下单可以选择优惠券");
        return success(couponService.available(user.getMemberId(), order.getBuyerId(), order.getOrderAmount()));
    }

    private void requireActiveMember(H5User user)
    {
        if (user.getMemberId() == null) throw new ServiceException("非会员身份");
        GasMember member = memberMapper.selectMemberById(user.getMemberId());
        if (member == null || !"0".equals(member.getStatus()) || member.getPhone() == null || member.getPhone().isBlank())
            throw new ServiceException("请先绑定手机号");
    }
}
