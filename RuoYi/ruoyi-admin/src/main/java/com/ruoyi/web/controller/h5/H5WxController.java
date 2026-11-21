package com.ruoyi.web.controller.h5;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.gas.h5.H5TokenService;
import com.ruoyi.gas.h5.H5User;
import com.ruoyi.gas.service.IGasConfigService;
import com.ruoyi.gas.service.IH5AuthService;
import com.ruoyi.gas.service.IWxApiService;

/**
 * H5 微信授权：随机 state 防登录 CSRF，一次性登录码换 JWT，JWT 不进入 URL。
 *
 * @author gas
 */
@Anonymous
@Controller
@RequestMapping("/api/h5/wx")
public class H5WxController extends BaseController
{
    private static final long STATE_TTL_MILLIS = 5 * 60_000L;
    private static final long LOGIN_CODE_TTL_MILLIS = 60_000L;

    @Autowired
    private IWxApiService wxApiService;

    @Autowired
    private IH5AuthService authService;

    @Autowired
    private H5TokenService tokenService;

    @Autowired
    private IGasConfigService configService;

    private final Map<String, ExpiringValue> oauthStates = new ConcurrentHashMap<>();
    private final Map<String, ExpiringValue> loginCodes = new ConcurrentHashMap<>();

    @GetMapping("/authorize")
    public String authorize(@RequestParam("redirect") String redirect, HttpServletRequest request)
    {
        removeExpired();
        String state = randomCode();
        oauthStates.put(state, new ExpiringValue(sanitizeRedirect(redirect), STATE_TTL_MILLIS));
        if (wxApiService.isMockMode())
        {
            return "redirect:/api/h5/wx/callback?code=MOCK&state=" + state;
        }
        String callbackUrl = request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() == 80 || request.getServerPort() == 443 ? "" : ":" + request.getServerPort())
                + "/api/h5/wx/callback";
        return "redirect:" + wxApiService.buildAuthorizeUrl(callbackUrl, state);
    }

    @GetMapping("/callback")
    public String callback(@RequestParam("code") String code, @RequestParam(value = "state", required = false) String state)
    {
        String redirect = consume(oauthStates, state);
        if (redirect == null)
        {
            return "redirect:/index?oauth_error=invalid_state";
        }
        H5User user = authService.loginByCode(code);
        String loginCode = randomCode();
        loginCodes.put(loginCode, new ExpiringValue(tokenService.issue(user), LOGIN_CODE_TTL_MILLIS));
        String separator = redirect.contains("?") ? "&" : "?";
        return "redirect:" + redirect + separator + "h5_code=" + loginCode;
    }

    /** 一次性、60 秒有效的登录码换 JWT；前端通过 XHR 获取，避免令牌进入 URL/代理日志 */
    @PostMapping("/exchange")
    @ResponseBody
    public AjaxResult exchange(@RequestParam("code") String code)
    {
        String token = consume(loginCodes, code);
        return token == null ? error("登录码无效或已过期，请重新授权") : AjaxResult.success("兑换成功", token);
    }

    @GetMapping("/jssdk-config")
    @ResponseBody
    public AjaxResult jssdkConfig(@RequestParam("url") String url)
    {
        Map<String, String> config = wxApiService.getJsapiConfig(url);
        return success(config);
    }

    private String randomCode()
    {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String consume(Map<String, ExpiringValue> store, String code)
    {
        if (StringUtils.isEmpty(code))
        {
            return null;
        }
        ExpiringValue value = store.remove(code);
        return value == null || value.expireAt < System.currentTimeMillis() ? null : value.value;
    }

    private void removeExpired()
    {
        long now = System.currentTimeMillis();
        oauthStates.values().removeIf(value -> value.expireAt < now);
        loginCodes.values().removeIf(value -> value.expireAt < now);
    }

    /** 只放行站内相对路径，或与配置 base 完全同源且路径位于 base 下的地址 */
    private String sanitizeRedirect(String redirect)
    {
        String base = configService.selectConfigValue("h5.base.url", "");
        String fallback = StringUtils.isNotEmpty(base) ? base : "/index";
        if (StringUtils.isEmpty(redirect))
        {
            return fallback;
        }
        if (redirect.indexOf('\\') >= 0 || redirect.indexOf('#') >= 0 || redirect.indexOf('\r') >= 0 || redirect.indexOf('\n') >= 0)
        {
            return fallback;
        }
        if (redirect.startsWith("/") && !redirect.startsWith("//"))
        {
            return redirect;
        }
        try
        {
            URI baseUri = URI.create(base);
            URI target = URI.create(redirect);
            if (baseUri.isAbsolute() && target.isAbsolute() && target.getUserInfo() == null
                    && equalsIgnoreCase(baseUri.getScheme(), target.getScheme())
                    && equalsIgnoreCase(baseUri.getHost(), target.getHost())
                    && effectivePort(baseUri) == effectivePort(target)
                    && pathWithin(target.getPath(), baseUri.getPath()))
            {
                return redirect;
            }
        }
        catch (IllegalArgumentException ignored)
        {
            // 非法 URI 统一回退到站内首页
        }
        return fallback;
    }

    private static boolean pathWithin(String targetPath, String basePath)
    {
        String base = StringUtils.isEmpty(basePath) || "/".equals(basePath) ? "/" : basePath.replaceAll("/+$", "");
        String target = StringUtils.isEmpty(targetPath) ? "/" : targetPath;
        return "/".equals(base) || target.equals(base) || target.startsWith(base + "/");
    }

    private static int effectivePort(URI uri)
    {
        if (uri.getPort() >= 0)
        {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static boolean equalsIgnoreCase(String left, String right)
    {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static final class ExpiringValue
    {
        private final String value;
        private final long expireAt;

        private ExpiringValue(String value, long ttlMillis)
        {
            this.value = value;
            this.expireAt = System.currentTimeMillis() + ttlMillis;
        }
    }
}
