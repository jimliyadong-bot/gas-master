package com.ruoyi.gas.service;

import java.util.List;
import com.ruoyi.gas.domain.GasStation;

/**
 * 加气站 服务层
 *
 * @author gas
 */
public interface IGasStationService
{
    /**
     * 查询站点列表
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
     * 新增站点（自动在根部门下创建 sys_dept，生成二维码标识）
     */
    public int insertStation(GasStation station);

    /**
     * 修改站点（站点改名同步部门名）
     */
    public int updateStation(GasStation station);

    /**
     * 批量删除站点（名下有枪或部门下有用户时拒绝，联动删除部门）
     */
    public int deleteStationByIds(Long[] stationIds);

    /**
     * 校验站点编码是否唯一
     */
    public boolean checkStationCodeUnique(GasStation station);

    /**
     * 生成站点二维码 PNG（内容 = h5.base.url 参数 + ?station=二维码标识）
     */
    public byte[] buildQrPng(GasStation station);
}
