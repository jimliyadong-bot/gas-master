package com.ruoyi.gas.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.gas.domain.GasMember;
import com.ruoyi.gas.domain.GasStaffBinding;
import com.ruoyi.gas.domain.GasStation;
import com.ruoyi.gas.h5.H5User;
import com.ruoyi.gas.mapper.GasMemberMapper;
import com.ruoyi.gas.mapper.GasStaffBindingMapper;
import com.ruoyi.gas.mapper.GasStationMapper;
import com.ruoyi.gas.service.IH5AuthService;
import com.ruoyi.gas.service.IWxApiService;
import com.ruoyi.system.mapper.SysRoleMapper;
import com.ruoyi.system.mapper.SysUserMapper;

/**
 * H5 端认证服务实现
 *
 * <p>登录身份优先级：员工绑定（uk_openid）> 会员档案（首次授权自动建档）。</p>
 *
 * @author gas
 */
@Service
public class H5AuthServiceImpl implements IH5AuthService
{
    @Autowired
    private IWxApiService wxApiService;

    @Autowired
    private GasMemberMapper memberMapper;

    @Autowired
    private GasStaffBindingMapper staffBindingMapper;

    @Autowired
    private GasStationMapper stationMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Override
    @Transactional
    public H5User loginByCode(String code)
    {
        String openid = wxApiService.getOpenidByCode(code);

        // 员工绑定优先：加气员/站点管理员以员工身份进入
        GasStaffBinding binding = staffBindingMapper.selectByOpenid(openid);
        if (binding != null)
        {
            SysUser staffUser = userMapper.selectUserById(binding.getUserId());
            GasStation staffStation = stationMapper.selectStationById(binding.getStationId());
            if (staffUser == null || !"0".equals(staffUser.getStatus()))
            {
                throw new ServiceException("员工账号不存在或已停用");
            }
            if (staffStation == null || !"0".equals(staffStation.getStatus())
                    || staffUser.getDeptId() == null || !staffUser.getDeptId().equals(staffStation.getDeptId()))
            {
                throw new ServiceException("员工站点绑定已失效，请联系管理员重新绑定");
            }
            String role = resolveStaffRole(binding.getUserId());
            if (role != null)
            {
                return H5User.staff(openid, binding.getUserId(), binding.getStationId(), role);
            }
            // 用户角色被调整后不再是员工：忽略绑定，走会员流程
        }

        GasMember member = memberMapper.selectMemberByOpenid(openid);
        if (member == null)
        {
            member = new GasMember();
            member.setOpenid(openid);
            try
            {
                memberMapper.insertMember(member);
            }
            catch (DuplicateKeyException e)
            {
                // 并发首登撞 uk_openid：回读已建档案
                member = memberMapper.selectMemberByOpenid(openid);
                if (member == null)
                {
                    throw new ServiceException("登录失败，请重试");
                }
            }
            // 新档状态库默认 '0'（正常），内存对象未回读
        }
        else if (!"0".equals(member.getStatus()))
        {
            throw new ServiceException("会员账号已停用");
        }
        return H5User.member(openid, member.getMemberId(), StringUtils.isNotEmpty(member.getPhone()));
    }

    @Override
    @Transactional
    public H5User staffBind(H5User current, SysUser sysUser)
    {
        if (sysUser == null || !"0".equals(sysUser.getStatus()))
        {
            throw new ServiceException("员工账号不存在或已停用");
        }
        List<String> roleKeys = roleMapper.selectRolesByUserId(sysUser.getUserId()).stream()
                .map(r -> r.getRoleKey()).filter(StringUtils::isNotEmpty).collect(Collectors.toList());
        boolean stationAdmin = roleKeys.contains("gas_station_admin");
        boolean staff = roleKeys.contains("gas_staff");
        if (!stationAdmin && !staff)
        {
            throw new ServiceException("该账号不是加气员或站点管理员，无法绑定员工身份");
        }

        // 员工挂在站点部门下：sys_user.dept_id → 所属站点
        GasStation station = stationMapper.selectStationByDeptId(sysUser.getDeptId());
        if (station == null)
        {
            throw new ServiceException("该账号未挂到站点部门，请联系管理员调整部门归属");
        }

        // 同一微信不能绑定其他账号
        GasStaffBinding byOpenid = staffBindingMapper.selectByOpenid(current.getOpenid());
        if (byOpenid != null && byOpenid.getUserId().longValue() != sysUser.getUserId().longValue())
        {
            throw new ServiceException("该微信已绑定账号[" + (byOpenid.getUserName() == null ? byOpenid.getUserId() : byOpenid.getUserName())
                    + "]，如需更换请先联系管理员解绑");
        }

        // 同一账号换绑微信（uk_user 更新）
        GasStaffBinding byUser = staffBindingMapper.selectByUserId(sysUser.getUserId());
        if (byUser != null)
        {
            staffBindingMapper.updateOpenid(sysUser.getUserId(), current.getOpenid(), station.getStationId());
        }
        else
        {
            GasStaffBinding binding = new GasStaffBinding();
            binding.setUserId(sysUser.getUserId());
            binding.setOpenid(current.getOpenid());
            binding.setStationId(station.getStationId());
            staffBindingMapper.insertBinding(binding);
        }
        return H5User.staff(current.getOpenid(), sysUser.getUserId(), station.getStationId(),
                stationAdmin ? H5User.ROLE_STATION_ADMIN : H5User.ROLE_STAFF);
    }

    @Override
    @Transactional
    public H5User bindPhone(H5User current, String phone)
    {
        if (!current.isMember())
        {
            throw new ServiceException("仅消费者会员需要绑定手机号");
        }
        GasMember member = memberMapper.selectMemberById(current.getMemberId());
        if (member == null)
        {
            throw new ServiceException("会员不存在，请重新授权");
        }
        // JWT 有效期内会员可能被停用：写操作前以库内状态为准
        if (!"0".equals(member.getStatus()))
        {
            throw new ServiceException("会员账号已停用");
        }
        // 换绑同号幂等成功
        if (phone.equals(member.getPhone()))
        {
            return H5User.member(current.getOpenid(), current.getMemberId(), true);
        }
        // uk_phone：手机号不能绑定多个微信
        if (memberMapper.selectMemberByPhone(phone, current.getMemberId()) != null)
        {
            throw new ServiceException("该手机号已绑定其他微信，如有疑问请联系客服");
        }
        memberMapper.bindPhone(current.getMemberId(), phone);
        return H5User.member(current.getOpenid(), current.getMemberId(), true);
    }

    @Override
    public Map<String, Object> me(H5User user)
    {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("role", user.getRole());
        info.put("phoneBound", user.isPhoneBound());
        if (user.isMember())
        {
            GasMember member = memberMapper.selectMemberById(user.getMemberId());
            info.put("memberId", user.getMemberId());
            info.put("nickname", member == null ? null : member.getNickname());
            info.put("phone", member == null ? null : maskPhone(member.getPhone()));
        }
        else
        {
            info.put("userId", user.getUserId());
            info.put("stationId", user.getStationId());
        }
        return info;
    }

    private String maskPhone(String phone)
    {
        if (phone == null || phone.length() != 11)
        {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /** 解析员工角色：站点管理员 > 加气员（纯 SQL，绕开 @DataScope 对 Shiro 上下文的依赖） */
    private String resolveStaffRole(Long userId)
    {
        List<String> roleKeys = roleMapper.selectRolesByUserId(userId).stream()
                .map(r -> r.getRoleKey()).filter(StringUtils::isNotEmpty).collect(Collectors.toList());
        if (roleKeys.contains("gas_station_admin"))
        {
            return H5User.ROLE_STATION_ADMIN;
        }
        if (roleKeys.contains("gas_staff"))
        {
            return H5User.ROLE_STAFF;
        }
        // 用户角色被调整后不再是员工：按未绑定处理（下次走会员流程）
        return null;
    }
}
