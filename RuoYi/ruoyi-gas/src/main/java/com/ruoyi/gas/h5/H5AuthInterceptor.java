package com.ruoyi.gas.h5;

import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.alibaba.fastjson2.JSONObject;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.ruoyi.common.utils.StringUtils;

/**
 * H5 端 JWT 认证拦截器（/api/h5/** 除 wx 匿名段）
 *
 * <p>Shiro 对 /api/h5/** 已放行（@Anonymous），认证完全由本拦截器承担；
 * 校验 Authorization: Bearer &lt;token&gt;，成功后填充 H5UserContext。</p>
 *
 * @author gas
 */
@Component
public class H5AuthInterceptor implements HandlerInterceptor
{
    @Autowired
    private H5TokenService tokenService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException
    {
        String auth = request.getHeader("Authorization");
        if (StringUtils.isEmpty(auth) || !auth.startsWith("Bearer "))
        {
            writeUnauthorized(response, "未登录或登录已失效，请重新授权");
            return false;
        }
        try
        {
            H5User user = tokenService.verify(auth.substring(7).trim());
            H5UserContext.set(user);
            return true;
        }
        catch (JWTVerificationException e)
        {
            writeUnauthorized(response, "登录凭证无效或已过期，请重新授权");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
    {
        H5UserContext.clear();
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException
    {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        JSONObject body = new JSONObject();
        body.put("code", 401);
        body.put("msg", message);
        response.getWriter().write(body.toJSONString());
    }
}
