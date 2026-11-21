package com.ruoyi.gas.sms;

/**
 * 短信发送接口
 *
 * <p>阶段 3 使用日志占位实现；阶段 8 接入阿里云短信（dysmsapi SDK）后替换为真实实现。</p>
 *
 * @author gas
 */
public interface ISmsSender
{
    /**
     * 发送短信
     *
     * @param phone 手机号
     * @param templateCode 模板编码（阿里云模板 CODE）
     * @param paramsJson 模板变量 JSON
     * @param bizType 业务类型（VERIFY_CODE/INVOICE_REMIND/STOCK_ALERT）
     * @return 是否成功
     */
    boolean send(String phone, String templateCode, String paramsJson, String bizType);
}
