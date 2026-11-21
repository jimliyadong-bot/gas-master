package com.ruoyi.gas.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 动态码对象 gas_dynamic_token（30秒刷新、60秒有效、单次使用）
 *
 * @author gas
 */
public class GasDynamicToken
{
    private static final long serialVersionUID = 1L;

    /** 类型：核销码（ref = 订单ID） */
    public static final String TYPE_REDEEM = "REDEEM";

    /** 类型：会员码（ref = 会员ID） */
    public static final String TYPE_MEMBER = "MEMBER";

    /** ID */
    private Long tokenId;

    /** 类型（REDEEM/MEMBER） */
    private String tokenType;

    /** 订单ID 或 会员ID */
    private Long refId;

    /** 随机令牌 */
    private String token;

    /** 过期时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expireAt;

    /** 使用时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date usedAt;

    /** 生成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public Long getTokenId()
    {
        return tokenId;
    }

    public void setTokenId(Long tokenId)
    {
        this.tokenId = tokenId;
    }

    public String getTokenType()
    {
        return tokenType;
    }

    public void setTokenType(String tokenType)
    {
        this.tokenType = tokenType;
    }

    public Long getRefId()
    {
        return refId;
    }

    public void setRefId(Long refId)
    {
        this.refId = refId;
    }

    public String getToken()
    {
        return token;
    }

    public void setToken(String token)
    {
        this.token = token;
    }

    public Date getExpireAt()
    {
        return expireAt;
    }

    public void setExpireAt(Date expireAt)
    {
        this.expireAt = expireAt;
    }

    public Date getUsedAt()
    {
        return usedAt;
    }

    public void setUsedAt(Date usedAt)
    {
        this.usedAt = usedAt;
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
