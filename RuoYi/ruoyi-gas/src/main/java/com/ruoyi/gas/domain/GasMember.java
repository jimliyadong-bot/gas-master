package com.ruoyi.gas.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 消费者会员对象 gas_member
 *
 * <p>公众号 OAuth 换取 openid 后自动建档；手机号经短信验证后写入（uk_phone 唯一）。</p>
 *
 * @author gas
 */
public class GasMember extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 会员ID */
    private Long memberId;

    /** 公众号 openid */
    private String openid;

    /** unionid */
    private String unionid;

    /** 昵称 */
    private String nickname;

    /** 头像 */
    private String avatar;

    /** 手机号（短信验证后写入） */
    private String phone;

    /** 手机号绑定时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date phoneBoundAt;

    /** 状态（0正常 1停用） */
    private String status;

    public Long getMemberId()
    {
        return memberId;
    }

    public void setMemberId(Long memberId)
    {
        this.memberId = memberId;
    }

    public String getOpenid()
    {
        return openid;
    }

    public void setOpenid(String openid)
    {
        this.openid = openid;
    }

    public String getUnionid()
    {
        return unionid;
    }

    public void setUnionid(String unionid)
    {
        this.unionid = unionid;
    }

    public String getNickname()
    {
        return nickname;
    }

    public void setNickname(String nickname)
    {
        this.nickname = nickname;
    }

    public String getAvatar()
    {
        return avatar;
    }

    public void setAvatar(String avatar)
    {
        this.avatar = avatar;
    }

    public String getPhone()
    {
        return phone;
    }

    public void setPhone(String phone)
    {
        this.phone = phone;
    }

    public Date getPhoneBoundAt()
    {
        return phoneBoundAt;
    }

    public void setPhoneBoundAt(Date phoneBoundAt)
    {
        this.phoneBoundAt = phoneBoundAt;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }
}
