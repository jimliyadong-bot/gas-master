package com.ruoyi.web.controller.gas;

import java.util.List;
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
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.gas.domain.GasShift;
import com.ruoyi.gas.service.IGasShiftService;

/** 后台班次记录与忘记交班的强制交班 */
@Controller
@RequestMapping("/gas/shift")
public class GasShiftController extends BaseController
{
    private static final String PREFIX = "gas/shift";

    @Autowired
    private IGasShiftService shiftService;
    @Autowired
    private GasStationAccess stationAccess;

    @RequiresPermissions("gas:shift:list")
    @GetMapping
    public String shift()
    {
        return PREFIX + "/shift";
    }

    @RequiresPermissions("gas:shift:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(GasShift query)
    {
        Long scoped = stationAccess.scopedStationId();
        if (scoped != null)
        {
            query.setStationId(scoped);
        }
        startPage();
        List<GasShift> list = shiftService.selectShiftList(query);
        return getDataTable(list);
    }

    @RequiresPermissions("gas:shift:force")
    @GetMapping("/force/{shiftId}")
    public String force(@PathVariable Long shiftId, ModelMap mmap)
    {
        GasShift shift = shiftService.selectShiftById(shiftId);
        if (shift == null || !shift.isActive())
        {
            throw new ServiceException("班次不存在或已交班");
        }
        stationAccess.requireStation(shift.getStationId());
        mmap.put("shift", shift);
        return PREFIX + "/force";
    }

    @Log(title = "强制交班", businessType = BusinessType.UPDATE)
    @RequiresPermissions("gas:shift:force")
    @PostMapping("/force")
    @ResponseBody
    public AjaxResult forceSave(Long shiftId, String endReason)
    {
        return success(shiftService.forceEndShift(shiftId, getLoginName(), endReason, stationAccess.scopedStationId()));
    }
}
