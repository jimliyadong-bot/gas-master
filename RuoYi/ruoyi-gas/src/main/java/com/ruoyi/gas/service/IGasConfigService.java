package com.ruoyi.gas.service;

import java.util.List;
import com.ruoyi.gas.domain.GasConfig;

/**
 * 业务参数 服务层
 *
 * @author gas
 */
public interface IGasConfigService
{
    /**
     * 查询业务参数列表
     */
    public List<GasConfig> selectConfigList(GasConfig config);

    /**
     * 根据参数键查询参数
     */
    public GasConfig selectConfigByKey(String configKey);

    /**
     * 读取参数值，不存在时返回默认值
     */
    public String selectConfigValue(String configKey, String defaultValue);

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

    /**
     * 校验参数键是否唯一
     */
    public boolean checkConfigKeyUnique(GasConfig config);
}
