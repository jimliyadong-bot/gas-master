package com.ruoyi.web.controller.gas;

import java.util.List;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.gas.domain.GasGun;
import com.ruoyi.gas.domain.GasStation;
import com.ruoyi.gas.service.IGasGunService;
import com.ruoyi.gas.service.IGasStationService;

/**
 * 枪号管理
 *
 * @author gas
 */
@Controller
@RequestMapping("/gas/gun")
public class GasGunController extends BaseController
{
    private String prefix = "gas/gun";

    @Autowired
    private IGasGunService gunService;

    @Autowired
    private IGasStationService stationService;

    @RequiresPermissions("gas:gun:list")
    @GetMapping()
    public String gun()
    {
        return prefix + "/gun";
    }

    @RequiresPermissions("gas:gun:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(GasGun gun)
    {
        startPage();
        List<GasGun> list = gunService.selectGunList(gun);
        return getDataTable(list);
    }

    @Log(title = "枪号管理", businessType = BusinessType.EXPORT)
    @RequiresPermissions("gas:gun:export")
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(GasGun gun)
    {
        List<GasGun> list = gunService.selectGunList(gun);
        ExcelUtil<GasGun> util = new ExcelUtil<GasGun>(GasGun.class);
        return util.exportExcel(list, "枪号数据");
    }

    @Log(title = "枪号管理", businessType = BusinessType.DELETE)
    @RequiresPermissions("gas:gun:remove")
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        if (StringUtils.isEmpty(ids))
        {
            return error("请选择要删除的枪号");
        }
        return toAjax(gunService.deleteGunByIds(Convert.toLongArray(ids)));
    }

    /**
     * 新增枪
     */
    @RequiresPermissions("gas:gun:add")
    @GetMapping("/add")
    public String add(ModelMap mmap)
    {
        mmap.put("stations", activeStations());
        return prefix + "/add";
    }

    @Log(title = "枪号管理", businessType = BusinessType.INSERT)
    @RequiresPermissions("gas:gun:add")
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(@Validated GasGun gun)
    {
        if (!gunService.checkGunUnique(gun))
        {
            return error("新增失败，该站点下已存在枪号[" + gun.getGunNo() + "]");
        }
        gun.setCreateBy(getLoginName());
        return toAjax(gunService.insertGun(gun));
    }

    /**
     * 修改枪
     */
    @RequiresPermissions("gas:gun:edit")
    @GetMapping("/edit/{gunId}")
    public String edit(@PathVariable("gunId") Long gunId, ModelMap mmap)
    {
        mmap.put("gun", gunService.selectGunById(gunId));
        mmap.put("stations", activeStations());
        return prefix + "/edit";
    }

    @Log(title = "枪号管理", businessType = BusinessType.UPDATE)
    @RequiresPermissions("gas:gun:edit")
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(@Validated GasGun gun)
    {
        if (!gunService.checkGunUnique(gun))
        {
            return error("修改失败，该站点下已存在枪号[" + gun.getGunNo() + "]");
        }
        gun.setUpdateBy(getLoginName());
        return toAjax(gunService.updateGun(gun));
    }

    /**
     * 校验站点下枪号唯一
     */
    @RequiresPermissions("gas:gun:list")
    @PostMapping("/checkGunUnique")
    @ResponseBody
    public boolean checkGunUnique(GasGun gun)
    {
        return gunService.checkGunUnique(gun);
    }

    private List<GasStation> activeStations()
    {
        GasStation query = new GasStation();
        query.setStatus("0");
        return stationService.selectStationList(query);
    }
}
