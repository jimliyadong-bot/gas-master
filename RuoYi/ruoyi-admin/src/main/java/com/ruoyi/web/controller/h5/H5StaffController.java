package com.ruoyi.web.controller.h5;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.gas.domain.GasShift;
import com.ruoyi.gas.h5.H5User;
import com.ruoyi.gas.h5.H5UserContext;
import com.ruoyi.gas.service.IGasShiftService;
import com.ruoyi.gas.service.IH5StaffService;

/** H5 加气员/站点管理员工作台（JWT 之外，每次业务调用实时刷新员工状态） */
@Anonymous
@RestController
@RequestMapping("/api/h5/s")
public class H5StaffController extends BaseController
{
    @Autowired
    private IH5StaffService staffService;
    @Autowired
    private IGasShiftService shiftService;

    @GetMapping("/workbench")
    public AjaxResult workbench()
    {
        return success(staffService.workbench(H5UserContext.require()));
    }

    @PostMapping("/shift/start")
    public AjaxResult startShift()
    {
        return success(shiftService.startShift(H5UserContext.require()));
    }

    @PostMapping("/shift/{shiftId}/end")
    public AjaxResult endShift(@PathVariable Long shiftId)
    {
        return success(shiftService.endShift(H5UserContext.require(), shiftId));
    }

    @PostMapping("/shift/{shiftId}/force-end")
    public AjaxResult forceEndShift(@PathVariable Long shiftId, @RequestBody Map<String, Object> body)
    {
        H5User user = H5UserContext.require();
        SysUser admin = shiftService.requireStationAdminUser(user);
        Object reasonValue = body == null ? null : body.get("reason");
        if (reasonValue != null && !(reasonValue instanceof String))
        {
            throw new ServiceException("强制交班原因格式错误");
        }
        String reason = (String) reasonValue;
        String operator = user.getUserId() + ":" + (StringUtils.isNotEmpty(admin.getUserName()) ? admin.getUserName() : admin.getLoginName());
        GasShift shift = shiftService.forceEndShift(shiftId, operator, reason, user.getStationId());
        return success(shift);
    }

    @PostMapping("/redeem/inspect")
    public AjaxResult inspectRedeem(@RequestBody Map<String, Object> body)
    {
        return success(staffService.inspectRedeemToken(H5UserContext.require(), text(body, "token")));
    }

    @PostMapping("/redeem")
    public AjaxResult redeem(@RequestBody Map<String, Object> body)
    {
        H5User user = H5UserContext.require();
        return success(staffService.redeem(user, text(body, "token"), longValue(body, "shiftId"),
                decimalValue(body, "actualVolume")));
    }

    @PostMapping("/proxy/inspect")
    public AjaxResult inspectMember(@RequestBody Map<String, Object> body)
    {
        return success(staffService.inspectMemberToken(H5UserContext.require(), text(body, "token")));
    }

    @PostMapping("/proxy/order")
    public AjaxResult createProxyOrder(@RequestBody Map<String, Object> body)
    {
        H5User user = H5UserContext.require();
        return success(staffService.createProxyOrder(user, text(body, "token"), longValue(body, "shiftId"),
                longValue(body, "gunId"), decimalValue(body, "orderAmount")));
    }

    private static String text(Map<String, Object> body, String key)
    {
        if (body == null || body.get(key) == null || "null".equals(String.valueOf(body.get(key))))
        {
            throw new ServiceException("缺少参数：" + key);
        }
        return String.valueOf(body.get(key));
    }

    private static Long longValue(Map<String, Object> body, String key)
    {
        try
        {
            return Long.valueOf(text(body, key));
        }
        catch (NumberFormatException e)
        {
            throw new ServiceException("参数格式错误：" + key);
        }
    }

    private static BigDecimal decimalValue(Map<String, Object> body, String key)
    {
        try
        {
            return new BigDecimal(text(body, key));
        }
        catch (NumberFormatException e)
        {
            throw new ServiceException("参数格式错误：" + key);
        }
    }
}
