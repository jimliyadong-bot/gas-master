package com.ruoyi.gas.mapper;

import com.ruoyi.gas.domain.GasStaffBinding;

/**
 * 员工公众号绑定 数据层
 *
 * @author gas
 */
public interface GasStaffBindingMapper
{
    public GasStaffBinding selectByOpenid(String openid);

    public GasStaffBinding selectByUserId(Long userId);

    /** 同一账号换绑微信（uk_user 更新） */
    public int updateOpenid(@org.apache.ibatis.annotations.Param("userId") Long userId,
            @org.apache.ibatis.annotations.Param("openid") String openid,
            @org.apache.ibatis.annotations.Param("stationId") Long stationId);

    public int insertBinding(GasStaffBinding binding);
}
