package com.ruoyi.gas.service;

import java.util.Map;

/**
 * 微信公众号 API 服务（OAuth 网页授权、JS-SDK 签名）
 *
 * <p>gas_config wx.mock-openid 非空时走 mock 模式（本地开发绕过微信）。</p>
 *
 * @author gas
 */
public interface IWxApiService
{
    /** mock 模式（wx.mock-openid 非空） */
    boolean isMockMode();

    /** 网页授权页跳转 URL（snsapi_base，state 透传回调） */
    String buildAuthorizeUrl(String redirectUri, String state);

    /** OAuth code 换 openid（mock 模式直接返回配置的 mock openid） */
    String getOpenidByCode(String code);

    /**
     * JS-SDK wx.config 所需参数（appId/timestamp/nonceStr/signature）
     *
     * @param url 调用 JS-SDK 的页面 URL（不含 # 之后部分）
     */
    Map<String, String> getJsapiConfig(String url);
}
