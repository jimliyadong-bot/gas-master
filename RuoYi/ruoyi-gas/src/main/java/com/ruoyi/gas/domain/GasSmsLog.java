package com.ruoyi.gas.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 短信发送日志对象 gas_sms_log
 *
 * <p>验证码类 params 不记明文（掩码存储）；(phone, create_time)/(client_ip, create_time)
 * 索引支撑发送限频统计。</p>
 *
 * @author gas
 */
public class GasSmsLog
{
    private static final long serialVersionUID = 1L;

    /** ID */
    private Long smsId;

    /** 手机号 */
    private String phone;

    /** 阿里云模板CODE */
    private String templateCode;

    /** 模板参数 JSON（验证码类不记录明文） */
    private String params;

    /** 业务类型（VERIFY_CODE/INVOICE_REMIND/STOCK_ALERT） */
    private String bizType;

    /** 关联业务ID */
    private Long bizId;

    /** 请求IP（验证码限频用） */
    private String clientIp;

    /** SUCCESS/FAILED */
    private String status;

    /** 阿里云 BizId */
    private String providerBizId;

    /** 错误信息 */
    private String errorMsg;

    /** 发送时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public Long getSmsId()
    {
        return smsId;
    }

    public void setSmsId(Long smsId)
    {
        this.smsId = smsId;
    }

    public String getPhone()
    {
        return phone;
    }

    public void setPhone(String phone)
    {
        this.phone = phone;
    }

    public String getTemplateCode()
    {
        return templateCode;
    }

    public void setTemplateCode(String templateCode)
    {
        this.templateCode = templateCode;
    }

    public String getParams()
    {
        return params;
    }

    public void setParams(String params)
    {
        this.params = params;
    }

    public String getBizType()
    {
        return bizType;
    }

    public void setBizType(String bizType)
    {
        this.bizType = bizType;
    }

    public Long getBizId()
    {
        return bizId;
    }

    public void setBizId(Long bizId)
    {
        this.bizId = bizId;
    }

    public String getClientIp()
    {
        return clientIp;
    }

    public void setClientIp(String clientIp)
    {
        this.clientIp = clientIp;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getProviderBizId()
    {
        return providerBizId;
    }

    public void setProviderBizId(String providerBizId)
    {
        this.providerBizId = providerBizId;
    }

    public String getErrorMsg()
    {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg)
    {
        this.errorMsg = errorMsg;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }
}
