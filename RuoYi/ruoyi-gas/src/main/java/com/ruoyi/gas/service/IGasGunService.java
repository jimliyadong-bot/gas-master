package com.ruoyi.gas.service;

import java.util.List;
import com.ruoyi.gas.domain.GasGun;

/**
 * 加气枪 服务层
 *
 * @author gas
 */
public interface IGasGunService
{
    /**
     * 查询枪列表
     */
    public List<GasGun> selectGunList(GasGun gun);

    /**
     * 根据ID查询枪
     */
    public GasGun selectGunById(Long gunId);

    /**
     * 新增枪
     */
    public int insertGun(GasGun gun);

    /**
     * 修改枪
     */
    public int updateGun(GasGun gun);

    /**
     * 批量删除枪（联动删除其价格记录；后续阶段订单引用后需加守卫）
     */
    public int deleteGunByIds(Long[] gunIds);

    /**
     * 校验站点下枪号是否唯一
     */
    public boolean checkGunUnique(GasGun gun);
}
