package com.ruoyi.gas.service;

/**
 * H5 短信验证码服务（手机号绑定用）
 *
 * <p>规则（Dev_Plan 阶段4）：6 位、5 分钟有效、同手机号 60 秒内 1 次、
 * 每日最多 10 次、同 IP 每日限频；校验失败 5 次作废。</p>
 *
 * @author gas
 */
public interface IH5SmsService
{
    /**
     * 发送验证码
     *
     * @param phone 手机号
     * @param clientIp 调用方 IP（限频统计）
     */
    void sendCode(String phone, String clientIp);

    /**
     * 校验验证码（成功后立即作废）
     *
     * @throws com.ruoyi.common.exception.ServiceException 验证码错误/过期/超出尝试次数
     */
    void verifyCode(String phone, String code);
}
