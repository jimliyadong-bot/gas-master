package com.ruoyi.gas.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.gas.service.IGasConfigService;
import com.ruoyi.gas.service.IWxApiService;

/**
 * 微信公众号 API 服务实现
 *
 * <p>mock 模式（yml 配置 gas.h5.mock-mode=true，仅本地开发）：openid = "MOCK-" + code
 * （code 即模拟身份标识，本地/测试可用不同 code 模拟多个用户），JS-SDK 返回占位签名。
 * 开关固化在配置文件而非 gas_config——数据库误配不会在生产打开免授权后门。</p>
 *
 * <p>真实模式：access_token / jsapi_ticket 服务端缓存（提前 200 秒过期防临界）。</p>
 *
 * @author gas
 */
@Service
public class WxApiServiceImpl implements IWxApiService
{
    private static final Logger log = LoggerFactory.getLogger(WxApiServiceImpl.class);

    private static final String OAUTH_URL = "https://open.weixin.qq.com/connect/oauth2/authorize"
            + "?appid=%s&redirect_uri=%s&response_type=code&scope=snsapi_base&state=%s#wechat_redirect";
    private static final String OAUTH_TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token"
            + "?appid=%s&secret=%s&code=%s&grant_type=authorization_code";
    private static final String ACCESS_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token"
            + "?grant_type=client_credential&appid=%s&secret=%s";
    private static final String JSAPI_TICKET_URL = "https://api.weixin.qq.com/cgi-bin/ticket/getticket"
            + "?access_token=%s&type=jsapi";

    /** 本地开发 mock 开关（生产环境不配置即关闭） */
    @Value("${gas.h5.mock-mode:false}")
    private boolean mockMode;

    @Value("${gas.wx.secret:}")
    private String envSecret;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    @Autowired
    private IGasConfigService configService;

    /** 微信 API 超时：连接 3s / 读 5s，防止外部接口故障占满容器线程 */
    private final RestTemplate restTemplate = buildRestTemplate();

    private static RestTemplate buildRestTemplate()
    {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(factory);
    }

    /** access_token 缓存（volatile 双检 + synchronized 刷新） */
    private volatile String accessToken;
    private volatile long accessTokenExpireAt = 0;

    private volatile String jsapiTicket;
    private volatile long jsapiTicketExpireAt = 0;

    @jakarta.annotation.PostConstruct
    public void guardMockMode()
    {
        if (mockMode && (activeProfiles.contains("prod") || activeProfiles.contains("production")))
        {
            throw new IllegalStateException("生产 Profile 禁止开启 gas.h5.mock-mode");
        }
    }

    @Override
    public boolean isMockMode()
    {
        return mockMode;
    }

    @Override
    public String buildAuthorizeUrl(String redirectUri, String state)
    {
        return String.format(OAUTH_URL, appid(), urlEncode(redirectUri), urlEncode(StringUtils.isEmpty(state) ? "1" : state));
    }

    @Override
    public String getOpenidByCode(String code)
    {
        if (mockMode)
        {
            // code 即模拟身份：不同 code 模拟不同用户
            return "MOCK-" + (StringUtils.isEmpty(code) ? "DEFAULT" : code);
        }
        String url = String.format(OAUTH_TOKEN_URL, appid(), secret(), code);
        JSONObject resp = JSONObject.parseObject(restTemplate.getForObject(url, String.class));
        requireWxSuccess(resp, "OAuth code 换 openid");
        return resp.getString("openid");
    }

    @Override
    public Map<String, String> getJsapiConfig(String url)
    {
        Map<String, String> config = new HashMap<>();
        String nonceStr = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        // mock 模式不要求真实 AppID
        config.put("appId", mockMode ? configService.selectConfigValue("wx.appid", "MOCK-APPID") : appid());
        config.put("timestamp", timestamp);
        config.put("nonceStr", nonceStr);
        if (mockMode)
        {
            config.put("signature", "MOCK-MODE");
            return config;
        }
        String ticket = jsapiTicket();
        String sign = sha1("jsapi_ticket=" + ticket + "&noncestr=" + nonceStr
                + "&timestamp=" + timestamp + "&url=" + url);
        config.put("signature", sign);
        return config;
    }

    private synchronized String accessToken()
    {
        if (accessToken == null || System.currentTimeMillis() > accessTokenExpireAt)
        {
            String url = String.format(ACCESS_TOKEN_URL, appid(), secret());
            JSONObject resp = JSONObject.parseObject(restTemplate.getForObject(url, String.class));
            requireWxSuccess(resp, "获取 access_token");
            accessToken = resp.getString("access_token");
            // 微信有效期 7200 秒，提前 200 秒刷新
            accessTokenExpireAt = System.currentTimeMillis() + (resp.getLongValue("expires_in") - 200) * 1000;
        }
        return accessToken;
    }

    private synchronized String jsapiTicket()
    {
        if (jsapiTicket == null || System.currentTimeMillis() > jsapiTicketExpireAt)
        {
            String url = String.format(JSAPI_TICKET_URL, accessToken());
            JSONObject resp = JSONObject.parseObject(restTemplate.getForObject(url, String.class));
            requireWxSuccess(resp, "获取 jsapi_ticket");
            jsapiTicket = resp.getString("ticket");
            jsapiTicketExpireAt = System.currentTimeMillis() + (resp.getLongValue("expires_in") - 200) * 1000;
        }
        return jsapiTicket;
    }

    private void requireWxSuccess(JSONObject resp, String action)
    {
        if (resp == null)
        {
            log.error("{}失败：微信接口无响应", action);
            throw new ServiceException("微信接口调用失败，请稍后重试");
        }
        if (resp.containsKey("errcode") && resp.getIntValue("errcode") != 0)
        {
            log.error("{}失败：errcode={}, errmsg={}", action, resp.getIntValue("errcode"), resp.getString("errmsg"));
            throw new ServiceException("微信接口调用失败（" + resp.getIntValue("errcode") + "）");
        }
    }

    private String appid()
    {
        String appid = configService.selectConfigValue("wx.appid", "");
        if (StringUtils.isEmpty(appid))
        {
            throw new ServiceException("公众号 AppID 未配置（gas_config wx.appid）");
        }
        return appid;
    }

    private String secret()
    {
        String secret = StringUtils.isNotEmpty(envSecret) ? envSecret : configService.selectConfigValue("wx.secret", "");
        if (StringUtils.isEmpty(secret))
        {
            throw new ServiceException("公众号 AppSecret 未配置（环境变量 GAS_WX_SECRET）");
        }
        return secret;
    }

    private static String urlEncode(String value)
    {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String sha1(String value)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes)
            {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
        catch (Exception e)
        {
            throw new ServiceException("JS-SDK 签名失败");
        }
    }
}
