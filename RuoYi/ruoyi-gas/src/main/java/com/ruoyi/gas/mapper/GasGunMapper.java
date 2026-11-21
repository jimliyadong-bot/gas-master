package com.ruoyi.gas.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.gas.domain.GasGun;

/**
 * 加气枪 数据层
 *
 * @author gas
 */
public interface GasGunMapper
{
    /**
     * 查询枪列表（联查站点/采购商）
     */
    public List<GasGun> selectGunList(GasGun gun);

    /**
     * 根据ID查询枪
     */
    public GasGun selectGunById(Long gunId);

    /**
     * 校验站点下枪号唯一
     */
    public GasGun checkGunUnique(@Param("stationId") Long stationId, @Param("gunNo") Integer gunNo);

    /**
     * 统计站点名下枪数（删站点守卫）
     */
    public int countGunByStationId(Long stationId);

    /**
     * 新增枪
     */
    public int insertGun(GasGun gun);

    /**
     * 修改枪
     */
    public int updateGun(GasGun gun);

    /**
     * 批量删除枪（物理删除）
     */
    public int deleteGunByIds(Long[] gunIds);
}
