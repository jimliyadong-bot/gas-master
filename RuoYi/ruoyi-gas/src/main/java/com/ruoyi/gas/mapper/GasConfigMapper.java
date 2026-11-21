package com.ruoyi.gas.mapper;

import java.util.List;
import com.ruoyi.gas.domain.GasConfig;

/**
 * 业务参数 数据层
 *
 * @author gas
 */
public interface GasConfigMapper
{
    /**
     * 查询业务参数列表
     */
    public List<GasConfig> selectConfigList(GasConfig config);

    /**
     * 根据参数键查询参数值
     */
    public GasConfig selectConfigByKey(String configKey);

    /**
     * 新增业务参数
     */
    public int insertConfig(GasConfig config);

    /**
     * 修改业务参数
     */
    public int updateConfig(GasConfig config);

    /**
     * 批量删除业务参数
     */
    public int deleteConfigByIds(String[] configKeys);
}
