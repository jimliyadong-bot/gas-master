package com.ruoyi.gas.service;

import java.util.List;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.gas.domain.GasShift;
import com.ruoyi.gas.h5.H5User;

/** 班次服务：员工身份每次查库刷新，班次变更通过行锁和 active_flag 防并发 */
public interface IGasShiftService
{
    /** 刷新员工账号、角色、微信绑定和站点状态，返回当前后台用户 */
    SysUser requireStaffUser(H5User user);

    /** 仅当前仍拥有站点管理员角色的员工可执行强制交班 */
    SysUser requireStationAdminUser(H5User user);

    GasShift startShift(H5User user);

    GasShift endShift(H5User user, Long shiftId);

    /** 当前员工自己的有效班次，核销/代下单必须携带并复核 shiftId */
    GasShift requireOwnActiveShift(H5User user, Long shiftId);

    GasShift selectActiveByStation(Long stationId);

    GasShift selectShiftById(Long shiftId);

    List<GasShift> selectShiftList(GasShift query);

    /** 后台/H5 管理员强制交班；scopedStationId 非空时限制本站 */
    GasShift forceEndShift(Long shiftId, String operator, String reason, Long scopedStationId);
}
