package com.ruoyi.gas.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.gas.domain.GasGun;
import com.ruoyi.gas.domain.GasStation;
import com.ruoyi.gas.mapper.GasGunMapper;
import com.ruoyi.gas.mapper.GasGunPriceMapper;
import com.ruoyi.gas.mapper.GasOrderMapper;
import com.ruoyi.gas.mapper.GasStationMapper;
import com.ruoyi.gas.service.IGasGunService;

/**
 * 加气枪 服务实现
 *
 * @author gas
 */
@Service
public class GasGunServiceImpl implements IGasGunService
{
    @Autowired
    private GasGunMapper gunMapper;

    @Autowired
    private GasGunPriceMapper priceMapper;

    @Autowired
    private GasStationMapper stationMapper;

    @Autowired
    private GasOrderMapper orderMapper;

    @Override
    public List<GasGun> selectGunList(GasGun gun)
    {
        return gunMapper.selectGunList(gun);
    }

    @Override
    public GasGun selectGunById(Long gunId)
    {
        return gunMapper.selectGunById(gunId);
    }

    @Override
    public int insertGun(GasGun gun)
    {
        checkStationValid(gun.getStationId());
        checkGunUniqueThrow(gun);
        return gunMapper.insertGun(gun);
    }

    @Override
    public int updateGun(GasGun gun)
    {
        GasGun old = gunMapper.selectGunById(gun.getGunId());
        if (StringUtils.isNull(old))
        {
            throw new ServiceException("枪不存在或已被删除");
        }
        // 枪与价格历史绑定，不允许更换所属站点；如需调整请新建枪号
        if (StringUtils.isNotNull(gun.getStationId()) && gun.getStationId().longValue() != old.getStationId().longValue())
        {
            throw new ServiceException("不允许更换枪的所属站点，如需调整请新建枪号");
        }
        checkStationValid(old.getStationId());
        checkGunUniqueThrow(gun);
        return gunMapper.updateGun(gun);
    }

    @Override
    @Transactional
    public int deleteGunByIds(Long[] gunIds)
    {
        if (orderMapper.countByGunIds(gunIds) > 0)
        {
            throw new ServiceException("所选枪号已存在销售订单，只能停用，不能删除或清除价格历史");
        }
        priceMapper.deletePriceByGunIds(gunIds);
        return gunMapper.deleteGunByIds(gunIds);
    }

    @Override
    public boolean checkGunUnique(GasGun gun)
    {
        Long gunId = StringUtils.isNull(gun.getGunId()) ? -1L : gun.getGunId();
        GasGun info = gunMapper.checkGunUnique(gun.getStationId(), gun.getGunNo());
        if (StringUtils.isNotNull(info) && info.getGunId().longValue() != gunId.longValue())
        {
            return false;
        }
        return true;
    }

    /**
     * 校验所属站点存在且未停用（无数据库外键，服务层保证引用有效）
     */
    private void checkStationValid(Long stationId)
    {
        if (StringUtils.isNull(stationId) || stationId.longValue() == 0L)
        {
            throw new ServiceException("所属站点不能为空");
        }
        GasStation station = stationMapper.selectStationById(stationId);
        if (StringUtils.isNull(station))
        {
            throw new ServiceException("所属站点不存在或已被删除");
        }
        if (!"0".equals(station.getStatus()))
        {
            throw new ServiceException(String.format("所属站点[%s]已停用，请先启用", station.getStationName()));
        }
    }

    /**
     * 校验站点下枪号唯一，重复则抛业务异常
     */
    private void checkGunUniqueThrow(GasGun gun)
    {
        if (!checkGunUnique(gun))
        {
            throw new ServiceException(String.format("站点下已存在枪号[%s]", gun.getGunNo()));
        }
    }
}
