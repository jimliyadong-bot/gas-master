package com.ruoyi.gas.service;

import java.util.Map;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.gas.h5.H5User;

/**
 * H5 端认证服务（OAuth 登录、员工账号绑定、手机号绑定）
 *
 * @author gas
 */
public interface IH5AuthService
{
    /**
     * OAuth 回调登录：openid → 员工绑定优先，其次建档/查询会员
     *
     * @return 登录身份（含角色与手机号绑定标记）
     */
    H5User loginByCode(String code);

    /**
     * 员工绑定：后台账号（密码已由调用方校验通过）绑定当前 openid
     *
     * @param current 当前 JWT 身份（openid 来自授权）
     * @param sysUser 密码校验通过的后台用户
     * @return 绑定后的员工身份
     */
    H5User staffBind(H5User current, SysUser sysUser);

    /**
     * 会员绑定手机号（验证码已由调用方校验通过），返回新身份（phoneBound=true）
     */
    H5User bindPhone(H5User current, String phone);

    /**
     * 当前登录身份信息（角色、手机号掩码、站点等，供前端分流展示）
     */
    Map<String, Object> me(H5User user);
}
