package com.ruoyi.web.controller.h5;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.IpUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.gas.h5.H5TokenService;
import com.ruoyi.gas.h5.H5User;
import com.ruoyi.gas.h5.H5UserContext;
import com.ruoyi.gas.service.IH5AuthService;
import com.ruoyi.gas.service.IH5SmsService;

/**
 * H5 短信验证码（Dev_Plan §5 /api/h5/sms/*，JWT 但未绑定手机号可调用）
 *
 * <p>Shiro 层匿名放行，认证由 H5AuthInterceptor 承担。</p>
 *
 * @author gas
 */
@Anonymous
@RestController
@RequestMapping("/api/h5/sms")
public class H5SmsController extends BaseController
{
    @Autowired
    private IH5SmsService smsService;

    @Autowired
    private IH5AuthService authService;

    @Autowired
    private H5TokenService tokenService;

    /**
     * 发送验证码（60 秒 1 次/每日上限/同 IP 限频）
     */
    @PostMapping("/send-code")
    public AjaxResult sendCode(@RequestBody Map<String, String> body, HttpServletRequest request)
    {
        String phone = body == null ? null : body.get("phone");
        // 未带 X-Forwarded-For 时取直连地址（Nginx 反代层补头）
        smsService.sendCode(phone, IpUtils.getIpAddr(request));
        return success("验证码已发送");
    }

    /**
     * 绑定手机号：校验验证码后写入会员档案，返回新 JWT（phoneBound=true）
     */
    @PostMapping("/bind-phone")
    public AjaxResult bindPhone(@RequestBody Map<String, String> body)
    {
        String phone = body == null ? null : body.get("phone");
        String code = body == null ? null : body.get("code");
        if (StringUtils.isEmpty(phone) || StringUtils.isEmpty(code))
        {
            return error("请输入手机号和验证码");
        }
        H5User current = H5UserContext.require();
        smsService.verifyCode(phone, code);
        H5User bound = authService.bindPhone(current, phone);
        AjaxResult result = success("绑定成功");
        result.put("token", tokenService.issue(bound));
        result.put("phoneBound", true);
        return result;
    }
}
