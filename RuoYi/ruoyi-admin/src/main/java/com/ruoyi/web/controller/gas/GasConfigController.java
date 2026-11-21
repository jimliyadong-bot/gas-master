package com.ruoyi.web.controller.gas;

import java.util.List;
import java.util.Set;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.gas.domain.GasConfig;
import com.ruoyi.gas.service.IGasConfigService;

/**
 * 业务参数管理（订单超时、码有效期、提醒配置等）
 *
 * @author gas
 */
@Controller
@RequestMapping("/gas/config")
public class GasConfigController extends BaseController
{
    private String prefix = "gas/config";

    /** 认证密钥只允许通过环境变量维护，通用配置页面不回显、不修改 */
    private static final Set<String> SENSITIVE_KEYS = Set.of("h5.jwt.secret", "wx.secret");

    @Autowired
    private IGasConfigService configService;

    @RequiresPermissions("gas:config:list")
    @GetMapping()
    public String config()
    {
        return prefix + "/config";
    }

    @RequiresPermissions("gas:config:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(GasConfig config)
    {
        startPage();
        List<GasConfig> list = configService.selectConfigList(config);
        list.forEach(this::maskSensitive);
        return getDataTable(list);
    }

    @Log(title = "业务参数", businessType = BusinessType.DELETE)
    @RequiresPermissions("gas:config:remove")
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        if (StringUtils.isEmpty(ids))
        {
            return error("请选择要删除的参数");
        }
        String[] keys = ids.split(",");
        for (String key : keys)
        {
            if (SENSITIVE_KEYS.contains(key))
            {
                return error("认证密钥只能通过环境变量维护");
            }
        }
        return toAjax(configService.deleteConfigByIds(keys));
    }

    /**
     * 新增参数
     */
    @RequiresPermissions("gas:config:add")
    @GetMapping("/add")
    public String add()
    {
        return prefix + "/add";
    }

    @Log(title = "业务参数", businessType = BusinessType.INSERT, excludeParamNames = { "configValue" })
    @RequiresPermissions("gas:config:add")
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(GasConfig config)
    {
        if (!isEditableKey(config.getConfigKey()))
        {
            return error("参数键格式无效，或该认证密钥只能通过环境变量维护");
        }
        if (!configService.checkConfigKeyUnique(config))
        {
            return error("新增参数'" + config.getConfigKey() + "'失败，参数键已存在");
        }
        config.setUpdateBy(getLoginName());
        return toAjax(configService.insertConfig(config));
    }

    /**
     * 修改参数
     */
    @RequiresPermissions("gas:config:edit")
    @GetMapping("/edit/{configKey}")
    public String edit(@PathVariable("configKey") String configKey, ModelMap mmap)
    {
        GasConfig config = configService.selectConfigByKey(configKey);
        if (config != null)
        {
            maskSensitive(config);
        }
        mmap.put("config", config);
        return prefix + "/edit";
    }

    @Log(title = "业务参数", businessType = BusinessType.UPDATE, excludeParamNames = { "configValue" })
    @RequiresPermissions("gas:config:edit")
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(GasConfig config)
    {
        if (!isEditableKey(config.getConfigKey()))
        {
            return error("参数键格式无效，或该认证密钥只能通过环境变量维护");
        }
        config.setUpdateBy(getLoginName());
        return toAjax(configService.updateConfig(config));
    }

    /**
     * 校验参数键
     */
    @RequiresPermissions("gas:config:list")
    @PostMapping("/checkConfigKeyUnique")
    @ResponseBody
    public boolean checkConfigKeyUnique(GasConfig config)
    {
        return configService.checkConfigKeyUnique(config);
    }

    private boolean isEditableKey(String key)
    {
        return key != null && key.matches("[A-Za-z0-9_.-]{1,64}") && !SENSITIVE_KEYS.contains(key);
    }

    private void maskSensitive(GasConfig config)
    {
        if (SENSITIVE_KEYS.contains(config.getConfigKey()))
        {
            config.setConfigValue("******");
        }
    }
}
