package com.ruoyi.web.controller.gas;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.gas.domain.GasStation;
import com.ruoyi.gas.mapper.GasStationMapper;

/** 站点管理员数据权限：本部门对应站点；拥有任一全量数据角色时按全量处理 */
@Component
public class GasStationAccess
{
    @Autowired
    private GasStationMapper stationMapper;

    /** null=不限制；-1=本站配置缺失（查不到任何数据）；其余=允许访问的站点 */
    public Long scopedStationId()
    {
        SysUser user = ShiroUtils.getSysUser();
        if (user == null || user.isAdmin())
        {
            return null;
        }
        boolean stationRestricted = false;
        if (user.getRoles() == null)
        {
            return -1L;
        }
        for (SysRole role : user.getRoles())
        {
            if ("1".equals(role.getDataScope()))
            {
                return null;
            }
            if ("gas_station_admin".equals(role.getRoleKey()))
            {
                stationRestricted = true;
            }
        }
        if (!stationRestricted)
        {
            return null;
        }
        GasStation station = stationMapper.selectStationByDeptId(user.getDeptId());
        return station == null ? -1L : station.getStationId();
    }

    public void requireStation(Long stationId)
    {
        Long scoped = scopedStationId();
        if (scoped != null && !scoped.equals(stationId))
        {
            throw new ServiceException("无权访问其他站点的数据");
        }
    }
}
