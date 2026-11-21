package com.ruoyi.gas.service.impl;

import java.security.SecureRandom;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.gas.domain.GasSmsLog;
import com.ruoyi.gas.mapper.GasSmsLogMapper;
import com.ruoyi.gas.service.IGasConfigService;
import com.ruoyi.gas.service.IH5SmsService;
import com.ruoyi.gas.sms.ISmsSender;

/**
 * H5 短信验证码服务实现
 *
 * <p>验证码存内存（单实例部署，重启后用户重新发码即可）；限频统计走
 * gas_sms_log（idx_phone_time / idx_ip_time 索引支撑），表驱动可审计。</p>
 *
 * @author gas
 */
@Service
public class H5SmsServiceImpl implements IH5SmsService
{
    private static final String BIZ_VERIFY_CODE = "VERIFY_CODE";
    private static final String TEMPLATE_CODE = "H5_BIND_PHONE";

    /** 手机号格式：1 开头 11 位 */
    private static final String PHONE_PATTERN = "^1[3-9]\\d{9}$";

    private final SecureRandom random = new SecureRandom();

    /** 内存验证码缓存：phone → entry */
    private final Map<String, CodeEntry> codeCache = new ConcurrentHashMap<>();

    @Autowired
    private GasSmsLogMapper smsLogMapper;

    @Autowired
    private IGasConfigService configService;

    @Autowired
    private ISmsSender smsSender;

    @Override
    public synchronized void sendCode(String phone, String clientIp)
    {
        if (StringUtils.isEmpty(phone) || !phone.matches(PHONE_PATTERN))
        {
            throw new ServiceException("请输入正确的手机号");
        }
        long intervalSeconds = longConfig("sms.code.interval.seconds", 60);
        Date last = smsLogMapper.selectLastSendTime(phone, BIZ_VERIFY_CODE);
        if (last != null && System.currentTimeMillis() - last.getTime() < intervalSeconds * 1000)
        {
            throw new ServiceException(String.format("发送太频繁，请 %d 秒后再试", intervalSeconds));
        }
        int phoneDailyMax = intConfig("sms.code.daily.max", 10);
        if (smsLogMapper.countTodayByPhone(phone, BIZ_VERIFY_CODE) >= phoneDailyMax)
        {
            throw new ServiceException("该手机号今日验证码已达上限，请明天再试");
        }
        int ipDailyMax = intConfig("sms.code.ip.daily.max", 30);
        if (StringUtils.isNotEmpty(clientIp)
                && smsLogMapper.countTodayByIp(clientIp, BIZ_VERIFY_CODE) >= ipDailyMax)
        {
            throw new ServiceException("当前网络今日验证码已达上限，请明天再试");
        }

        String code = String.format("%06d", random.nextInt(1000000));
        boolean ok = smsSender.send(phone, TEMPLATE_CODE, "{\"code\":\"******\"}", BIZ_VERIFY_CODE);

        // 无论占位实现成功与否都留痕（阶段8接阿里云后 status 反映真实结果）
        GasSmsLog smsLog = new GasSmsLog();
        smsLog.setPhone(phone);
        smsLog.setTemplateCode(TEMPLATE_CODE);
        smsLog.setParams("{\"code\":\"******\"}");
        smsLog.setBizType(BIZ_VERIFY_CODE);
        smsLog.setClientIp(clientIp);
        smsLog.setStatus(ok ? "SUCCESS" : "FAILED");
        if (!ok)
        {
            smsLog.setErrorMsg("短信通道返回失败");
        }
        smsLogMapper.insertSmsLog(smsLog);

        if (!ok)
        {
            throw new ServiceException("短信发送失败，请稍后重试");
        }
        codeCache.put(phone, new CodeEntry(code,
                System.currentTimeMillis() + longConfig("sms.code.ttl.minutes", 5) * 60_000));
        // 顺带清理过期条目，防止向大量不同手机号发码导致内存缓慢增长
        codeCache.values().removeIf(entry -> System.currentTimeMillis() > entry.expireAt);
    }

    @Override
    public synchronized void verifyCode(String phone, String code)
    {
        CodeEntry entry = codeCache.get(phone);
        if (entry == null)
        {
            throw new ServiceException("验证码已过期，请重新获取");
        }
        if (System.currentTimeMillis() > entry.expireAt)
        {
            codeCache.remove(phone);
            throw new ServiceException("验证码已过期，请重新获取");
        }
        int maxTry = intConfig("sms.code.max.try", 5);
        if (!entry.code.equals(code))
        {
            entry.failedTries++;
            if (entry.failedTries >= maxTry)
            {
                codeCache.remove(phone);
                throw new ServiceException("验证码错误次数过多，请重新获取");
            }
            throw new ServiceException("验证码错误");
        }
        // 验证通过即作废（一次性）
        codeCache.remove(phone);
    }

    private int intConfig(String key, int defaultValue)
    {
        return Integer.parseInt(configService.selectConfigValue(key, String.valueOf(defaultValue)));
    }

    private long longConfig(String key, long defaultValue)
    {
        return Long.parseLong(configService.selectConfigValue(key, String.valueOf(defaultValue)));
    }

    /** 验证码缓存条目 */
    private static final class CodeEntry
    {
        private final String code;
        private final long expireAt;
        private int failedTries;

        private CodeEntry(String code, long expireAt)
        {
            this.code = code;
            this.expireAt = expireAt;
        }
    }
}
