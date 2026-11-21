package com.ruoyi.gas.mapper;

import java.util.Date;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.gas.domain.GasSmsLog;

/**
 * 短信发送日志 数据层（限频统计 + 发送留痕）
 *
 * @author gas
 */
public interface GasSmsLogMapper
{
    public int insertSmsLog(GasSmsLog smsLog);

    /** 指定手机号某业务类型最近一次发送时间（60 秒间隔限频用） */
    public Date selectLastSendTime(@Param("phone") String phone, @Param("bizType") String bizType);

    /** 指定手机号某业务类型当日发送量 */
    public int countTodayByPhone(@Param("phone") String phone, @Param("bizType") String bizType);

    /** 指定 IP 当日验证码发送量 */
    public int countTodayByIp(@Param("clientIp") String clientIp, @Param("bizType") String bizType);
}
