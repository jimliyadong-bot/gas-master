package com.ruoyi.gas.h5;

/**
 * H5 端登录用户（JWT payload 载体）
 *
 * <p>身份二选一：会员（memberId）或员工（userId + stationId）；
 * 员工身份 role 为 STATION_ADMIN（站点管理员）/ STAFF（加气员）。</p>
 *
 * @author gas
 */
public class H5User
{
    /** 会员角色 */
    public static final String ROLE_MEMBER = "MEMBER";
    /** 加气员角色 */
    public static final String ROLE_STAFF = "STAFF";
    /** 站点管理员角色 */
    public static final String ROLE_STATION_ADMIN = "STATION_ADMIN";

    private String openid;
    private Long memberId;
    private Long userId;
    private Long stationId;
    private String role;
    private boolean phoneBound;

    public static H5User member(String openid, Long memberId, boolean phoneBound)
    {
        H5User u = new H5User();
        u.openid = openid;
        u.memberId = memberId;
        u.role = ROLE_MEMBER;
        u.phoneBound = phoneBound;
        return u;
    }

    public static H5User staff(String openid, Long userId, Long stationId, String role)
    {
        H5User u = new H5User();
        u.openid = openid;
        u.userId = userId;
        u.stationId = stationId;
        u.role = role;
        return u;
    }

    public boolean isMember()
    {
        return ROLE_MEMBER.equals(role);
    }

    public String getOpenid()
    {
        return openid;
    }

    public Long getMemberId()
    {
        return memberId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public Long getStationId()
    {
        return stationId;
    }

    public String getRole()
    {
        return role;
    }

    public boolean isPhoneBound()
    {
        return phoneBound;
    }
}
