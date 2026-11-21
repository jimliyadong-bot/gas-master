package com.ruoyi.gas.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.gas.domain.GasShift;
import com.ruoyi.gas.domain.GasStaffBinding;
import com.ruoyi.gas.domain.GasStation;
import com.ruoyi.gas.h5.H5User;
import com.ruoyi.gas.mapper.GasShiftMapper;
import com.ruoyi.gas.mapper.GasStaffBindingMapper;
import com.ruoyi.gas.mapper.GasStationMapper;
import com.ruoyi.gas.service.IGasShiftService;
import com.ruoyi.system.mapper.SysRoleMapper;
import com.ruoyi.system.mapper.SysUserMapper;

/** 班次服务实现 */
@Service
public class GasShiftServiceImpl implements IGasShiftService
{
    @Autowired
    private GasShiftMapper shiftMapper;
    @Autowired
    private GasStaffBindingMapper bindingMapper;
    @Autowired
    private GasStationMapper stationMapper;
    @Autowired
    private SysUserMapper userMapper;
    @Autowired
    private SysRoleMapper roleMapper;

    @Override
    public SysUser requireStaffUser(H5User user)
    {
        return requireStaff(user, false);
    }

    @Override
    public SysUser requireStationAdminUser(H5User user)
    {
        return requireStaff(user, true);
    }

    private SysUser requireStaff(H5User user, boolean adminOnly)
    {
        if (user == null || user.isMember() || user.getUserId() == null || user.getStationId() == null)
        {
            throw new ServiceException("该操作仅限加气员或站点管理员");
        }
        SysUser staff = userMapper.selectUserById(user.getUserId());
        if (staff == null || !"0".equals(staff.getStatus()) || "2".equals(staff.getDelFlag()))
        {
            throw new ServiceException("员工账号不存在或已停用");
        }
        boolean admin = false;
        boolean staffRole = false;
        for (SysRole role : roleMapper.selectRolesByUserId(user.getUserId()))
        {
            if (!"0".equals(role.getStatus()))
            {
                continue;
            }
            admin |= "gas_station_admin".equals(role.getRoleKey());
            staffRole |= "gas_staff".equals(role.getRoleKey());
        }
        if (adminOnly ? !admin : !(admin || staffRole))
        {
            throw new ServiceException(adminOnly ? "该操作仅限站点管理员" : "员工角色已取消，请重新绑定");
        }

        GasStaffBinding binding = bindingMapper.selectByOpenid(user.getOpenid());
        if (binding == null || !user.getUserId().equals(binding.getUserId())
                || !user.getStationId().equals(binding.getStationId()))
        {
            throw new ServiceException("员工微信绑定已变更，请重新授权");
        }
        GasStation station = stationMapper.selectStationById(binding.getStationId());
        if (station == null || !"0".equals(station.getStatus()) || "2".equals(station.getDelFlag())
                || staff.getDeptId() == null || !staff.getDeptId().equals(station.getDeptId()))
        {
            throw new ServiceException("员工站点或部门绑定已失效");
        }
        return staff;
    }

    @Override
    @Transactional
    public GasShift startShift(H5User user)
    {
        SysUser staff = requireStaffUser(user);
        GasShift active = shiftMapper.selectActiveByStationForUpdate(user.getStationId());
        if (active != null)
        {
            if (active.getUserId().equals(user.getUserId()))
            {
                return active;
            }
            throw new ServiceException("当前由[" + active.getStaffName() + "]当班，请先交班");
        }
        GasShift ownOther = shiftMapper.selectActiveByUser(user.getUserId());
        if (ownOther != null)
        {
            throw new ServiceException("您在[" + ownOther.getStationName() + "]仍有未交班班次");
        }
        GasShift shift = new GasShift();
        shift.setStationId(user.getStationId());
        shift.setUserId(user.getUserId());
        shift.setStaffName(displayName(staff));
        try
        {
            shiftMapper.insertShift(shift);
        }
        catch (DuplicateKeyException e)
        {
            GasShift current = shiftMapper.selectActiveByStation(user.getStationId());
            throw new ServiceException(current == null ? "开班冲突，请刷新重试"
                    : "当前由[" + current.getStaffName() + "]当班，请先交班");
        }
        return shiftMapper.selectShiftById(shift.getShiftId());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GasShift endShift(H5User user, Long shiftId)
    {
        SysUser staff = requireStaffUser(user);
        GasShift shift = requireOwnActiveShift(user, shiftId);
        return finishShift(shift, displayName(staff), null);
    }

    @Override
    public GasShift requireOwnActiveShift(H5User user, Long shiftId)
    {
        requireStaffUser(user);
        if (shiftId == null)
        {
            throw new ServiceException("缺少班次信息，请刷新工作台");
        }
        GasShift shift = shiftMapper.selectShiftByIdForUpdate(shiftId);
        if (shift == null || !shift.isActive() || !user.getUserId().equals(shift.getUserId())
                || !user.getStationId().equals(shift.getStationId()))
        {
            throw new ServiceException("班次已变更，请刷新工作台后重试");
        }
        return shift;
    }

    @Override
    public GasShift selectActiveByStation(Long stationId)
    {
        return shiftMapper.selectActiveByStation(stationId);
    }

    @Override
    public GasShift selectShiftById(Long shiftId)
    {
        return shiftMapper.selectShiftById(shiftId);
    }

    @Override
    public List<GasShift> selectShiftList(GasShift query)
    {
        return shiftMapper.selectShiftList(query);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GasShift forceEndShift(Long shiftId, String operator, String reason, Long scopedStationId)
    {
        String trimmedReason = reason == null ? "" : reason.trim();
        if (trimmedReason.isEmpty() || trimmedReason.length() > 255)
        {
            throw new ServiceException("强制交班原因必填，且不能超过255字");
        }
        GasShift shift = shiftMapper.selectShiftByIdForUpdate(shiftId);
        if (shift == null || !shift.isActive())
        {
            throw new ServiceException("班次不存在或已交班");
        }
        if (scopedStationId != null && !scopedStationId.equals(shift.getStationId()))
        {
            throw new ServiceException("无权操作其他站点的班次");
        }
        return finishShift(shift, operator, trimmedReason);
    }

    private GasShift finishShift(GasShift shift, String operator, String reason)
    {
        GasShift summary = shiftMapper.selectShiftSummary(shift.getShiftId());
        shift.setEndedBy(operator);
        shift.setEndReason(reason);
        shift.setProxyCount(summary.getProxyCount());
        shift.setRedeemCount(summary.getRedeemCount());
        shift.setCngVolume(summary.getCngVolume());
        shift.setLngVolume(summary.getLngVolume());
        shift.setPayAmount(summary.getPayAmount());
        if (shiftMapper.endShift(shift) == 0)
        {
            throw new ServiceException("班次已变更，请刷新后重试");
        }
        return shiftMapper.selectShiftById(shift.getShiftId());
    }

    private String displayName(SysUser user)
    {
        return StringUtils.isNotEmpty(user.getUserName()) ? user.getUserName() : user.getLoginName();
    }
}
