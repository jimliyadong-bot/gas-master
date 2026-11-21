package com.ruoyi.gas.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 员工公众号绑定对象 gas_staff_binding（加气员/站点管理员）
 *
 * <p>后台账号密码验证通过后建立 openid ↔ sys_user 绑定；uk_user、uk_openid 各自唯一，
 * 同一账号换绑微信走更新，同一微信换绑账号需先解绑。</p>
 *
 * @author gas
 */
public class GasStaffBinding
{
    private static final long serialVersionUID = 1L;

    /** ID */
    private Long bindingId;

    /** sys_user.user_id */
    private Long userId;

    /** 公众号 openid */
    private String openid;

    /** 所属站点 */
    private Long stationId;

    /** 绑定时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date boundAt;

    /** 站点名称（联查） */
    private String stationName;

    /** 用户昵称（联查） */
    private String userName;

    public Long getBindingId()
    {
        return bindingId;
    }

    public void setBindingId(Long bindingId)
    {
        this.bindingId = bindingId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getOpenid()
    {
        return openid;
    }

    public void setOpenid(String openid)
    {
        this.openid = openid;
    }

    public Long getStationId()
    {
        return stationId;
    }

    public void setStationId(Long stationId)
    {
        this.stationId = stationId;
    }

    public Date getBoundAt()
    {
        return boundAt;
    }

    public void setBoundAt(Date boundAt)
    {
        this.boundAt = boundAt;
    }

    public String getStationName()
    {
        return stationName;
    }

    public void setStationName(String stationName)
    {
        this.stationName = stationName;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }
}
