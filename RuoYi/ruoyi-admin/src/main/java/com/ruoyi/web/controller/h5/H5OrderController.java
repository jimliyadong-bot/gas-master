package com.ruoyi.web.controller.h5;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.gas.domain.GasOrder;
import com.ruoyi.gas.h5.H5User;
import com.ruoyi.gas.h5.H5UserContext;
import com.ruoyi.gas.service.IH5OrderService;

/**
 * H5 会员订单（Dev_Plan §5 /api/h5/c/*，JWT + 已绑手机号会员）
 *
 * <p>Shiro 层匿名放行，认证由 H5AuthInterceptor 承担。</p>
 *
 * @author gas
 */
@Anonymous
@RestController
@RequestMapping("/api/h5/c")
public class H5OrderController extends BaseController
{
    @Autowired
    private IH5OrderService orderService;

    /**
     * 扫码进站：站点信息 + 在线枪列表（含当前价）
     */
    @GetMapping("/station/{code}")
    public AjaxResult station(@PathVariable("code") String code)
    {
        return success(orderService.stationEntry(code));
    }

    /**
     * 试算：枪价 / 预计气量 / 应付金额（券阶段7接入）
     */
    @PostMapping("/order/preview")
    public AjaxResult preview(@RequestBody Map<String, Object> body)
    {
        Object[] args = parseGunAndAmount(body);
        if (args == null)
        {
            return error("参数错误");
        }
        Long couponId = parseCouponId(body);
        if (couponId != null && couponId <= 0) return error("优惠券参数错误");
        return success(orderService.preview(H5UserContext.require(), (Long) args[0], (BigDecimal) args[1], couponId));
    }

    /**
     * 创建订单（WAIT_PAY）
     */
    @PostMapping("/order")
    public AjaxResult create(@RequestBody Map<String, Object> body)
    {
        Object[] args = parseGunAndAmount(body);
        if (args == null)
        {
            return error("参数错误");
        }
        Long couponId = parseCouponId(body);
        if (couponId != null && couponId <= 0) return error("优惠券参数错误");
        H5User user = H5UserContext.require();
        String requestId = body.get("requestId") == null ? null : String.valueOf(body.get("requestId"));
        GasOrder order = orderService.createOrder(user, (Long) args[0], (BigDecimal) args[1], couponId, requestId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNo", order.getOrderNo());
        result.put("orderAmount", order.getOrderAmount());
        result.put("discountAmount", order.getDiscountAmount());
        result.put("couponId", order.getCouponId());
        result.put("payAmount", order.getPayAmount());
        result.put("estVolume", order.getEstVolume());
        result.put("expireAt", order.getExpireAt());
        return success(result);
    }

    /**
     * 我的订单列表
     */
    @GetMapping("/order/list")
    public TableDataInfo list(@RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size)
    {
        H5User user = H5UserContext.require();
        TableDataInfo rspData = getDataTable(orderService.myOrders(user, page, size));
        rspData.setTotal(orderService.countMyOrders(user));
        return rspData;
    }

    /** 解析 gunId/orderAmount（容错非法数字） */
    private Object[] parseGunAndAmount(Map<String, Object> body)
    {
        if (body == null || body.get("gunId") == null || body.get("orderAmount") == null)
        {
            return null;
        }
        try
        {
            return new Object[] {
                    Long.valueOf(String.valueOf(body.get("gunId"))),
                    new BigDecimal(String.valueOf(body.get("orderAmount"))) };
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private Long parseCouponId(Map<String, Object> body)
    {
        if (body == null || body.get("couponId") == null || String.valueOf(body.get("couponId")).isEmpty()) return null;
        try { return Long.valueOf(String.valueOf(body.get("couponId"))); }
        catch (NumberFormatException e) { return -1L; }
    }

    /**
     * 订单详情
     */
    @GetMapping("/order/{orderNo}")
    public AjaxResult detail(@PathVariable("orderNo") String orderNo)
    {
        H5User user = H5UserContext.require();
        return success(orderService.myOrder(user, orderNo));
    }

    /** 阶段6：司机确认/拒绝加气员代下单 */
    @PostMapping("/order/{orderNo}/confirm")
    public AjaxResult confirmProxy(@PathVariable("orderNo") String orderNo,
            @RequestBody(required = false) Map<String, Object> body)
    {
        Long couponId = parseCouponId(body);
        if (couponId != null && couponId <= 0) return error("优惠券参数错误");
        return success(orderService.confirmProxyOrder(H5UserContext.require(), orderNo, couponId));
    }

    @PostMapping("/order/{orderNo}/reject")
    public AjaxResult rejectProxy(@PathVariable("orderNo") String orderNo)
    {
        return success(orderService.rejectProxyOrder(H5UserContext.require(), orderNo));
    }

    /**
     * 发起支付：返回支付入口（收银台 URL / JSAPI 参数）
     */
    @PostMapping("/order/{orderNo}/pay")
    public AjaxResult pay(@PathVariable("orderNo") String orderNo)
    {
        H5User user = H5UserContext.require();
        return success(orderService.pay(user, orderNo));
    }

    /**
     * 订单核销码（PAID 订单；30 秒刷新、60 秒有效、单次使用）
     */
    @GetMapping("/order/{orderNo}/token")
    public AjaxResult redeemToken(@PathVariable("orderNo") String orderNo)
    {
        H5User user = H5UserContext.require();
        return success(tokenView(orderService.redeemToken(user, orderNo)));
    }

    /**
     * 会员码（核销入口反查会员）
     */
    @GetMapping("/member/token")
    public AjaxResult memberToken()
    {
        H5User user = H5UserContext.require();
        return success(tokenView(orderService.memberToken(user)));
    }

    private Map<String, Object> tokenView(com.ruoyi.gas.domain.GasDynamicToken token)
    {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("token", token.getToken());
        view.put("expireAt", token.getExpireAt());
        long remainSeconds = Math.max(0, (token.getExpireAt().getTime() - System.currentTimeMillis()) / 1000);
        view.put("remainSeconds", remainSeconds);
        return view;
    }
}
