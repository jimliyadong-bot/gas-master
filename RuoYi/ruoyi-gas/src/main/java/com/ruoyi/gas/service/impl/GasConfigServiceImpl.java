package com.ruoyi.gas.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.gas.domain.GasConfig;
import com.ruoyi.gas.mapper.GasConfigMapper;
import com.ruoyi.gas.service.IGasConfigService;

/**
 * 业务参数 服务实现
 *
 * @author gas
 */
@Service
public class GasConfigServiceImpl implements IGasConfigService
{
    @Autowired
    private GasConfigMapper configMapper;

    @Override
    public List<GasConfig> selectConfigList(GasConfig config)
    {
        return configMapper.selectConfigList(config);
    }

    @Override
    public GasConfig selectConfigByKey(String configKey)
    {
        return configMapper.selectConfigByKey(configKey);
    }

    @Override
    public String selectConfigValue(String configKey, String defaultValue)
    {
        GasConfig config = configMapper.selectConfigByKey(configKey);
        if (StringUtils.isNull(config) || StringUtils.isEmpty(config.getConfigValue()))
        {
            return defaultValue;
        }
        return config.getConfigValue();
    }

    @Override
    public int insertConfig(GasConfig config)
    {
        return configMapper.insertConfig(config);
    }

    @Override
    public int updateConfig(GasConfig config)
    {
        return configMapper.updateConfig(config);
    }

    @Override
    public int deleteConfigByIds(String[] configKeys)
    {
        return configMapper.deleteConfigByIds(configKeys);
    }

    @Override
    public boolean checkConfigKeyUnique(GasConfig config)
    {
        GasConfig info = configMapper.selectConfigByKey(config.getConfigKey());
        if (StringUtils.isNotNull(info))
        {
            return false;
        }
        return true;
    }
}
