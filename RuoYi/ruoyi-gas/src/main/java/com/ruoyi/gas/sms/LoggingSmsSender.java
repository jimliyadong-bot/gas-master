package com.ruoyi.gas.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 短信发送占位实现（阶段 3）
 *
 * <p>仅记录日志，验证提醒链路；阶段 8 接入阿里云短信后替换
 * （发送结果写 gas_sms_log）。</p>
 *
 * @author gas
 */
@Component
public class LoggingSmsSender implements ISmsSender
{
    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    @Override
    public boolean send(String phone, String templateCode, String paramsJson, String bizType)
    {
        String maskedPhone = phone != null && phone.length() >= 7
                ? phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4) : "***";
        log.info("[SMS-PLACEHOLDER] phone={}, template={}, bizType={}, params={}", maskedPhone, templateCode, bizType, paramsJson);
        return true;
    }
}
