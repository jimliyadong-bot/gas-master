package com.ruoyi.gas.h5;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * H5 端 MVC 配置：注册 JWT 拦截器
 *
 * <p>/api/h5/wx/** 匿名（授权入口、回调、JS-SDK 配置），其余 /api/h5/** 需 JWT。</p>
 *
 * @author gas
 */
@Configuration
public class H5WebConfig implements WebMvcConfigurer
{
    @Autowired
    private H5AuthInterceptor h5AuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry)
    {
        registry.addInterceptor(h5AuthInterceptor)
                .addPathPatterns("/api/h5/**")
                .excludePathPatterns("/api/h5/wx/**");
    }
}
