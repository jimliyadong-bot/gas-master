package com.ruoyi.gas.mapper;

import java.util.List;
import com.ruoyi.gas.domain.GasStation;

/**
 * 加气站 数据层
 *
 * @author gas
 */
public interface GasStationMapper
{
    /**
     * 查询站点列表（联查采购商名称）
     */
    public List<GasStation> selectStationList(GasStation station);

    /**
     * 根据ID查询站点
     */
    public GasStation selectStationById(Long stationId);

    /**
     * 根据二维码标识查询站点（H5 扫码入口用）
     */
    public GasStation selectStationByQrToken(String qrToken);

    /**
     * 根据部门ID查询站点（H5 员工绑定：sys_user.dept_id → 所属站点）
     */
    public GasStation selectStationByDeptId(Long deptId);

    /**
     * 校验站点编码唯一
     */
    public GasStation checkStationCodeUnique(String stationCode);

    /**
     * 统计采购商名下正常站点数（删采购商守卫）
     */
    public int countStationByBuyerId(Long buyerId);

    /**
     * 新增站点
     */
    public int insertStation(GasStation station);

    /**
     * 修改站点
     */
    public int updateStation(GasStation station);

    /**
     * 批量逻辑删除站点（del_flag = '2'）
     */
    public int deleteStationByIds(Long[] stationIds);
}
