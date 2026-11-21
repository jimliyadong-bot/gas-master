package com.ruoyi.web.controller.gas;

import java.io.IOException;
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
import com.ruoyi.gas.domain.GasBuyer;
import com.ruoyi.gas.domain.GasStation;
import com.ruoyi.gas.service.IGasBuyerService;
import com.ruoyi.gas.service.IGasStationService;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 站点管理（归属采购商，建站自动建部门，站点二维码下载）
 *
 * @author gas
 */
@Controller
@RequestMapping("/gas/station")
public class GasStationController extends BaseController
{
    private String prefix = "gas/station";

    @Autowired
    private IGasStationService stationService;

    @Autowired
    private IGasBuyerService buyerService;

    @RequiresPermissions("gas:station:list")
    @GetMapping()
    public String station()
    {
        return prefix + "/station";
    }

    @RequiresPermissions("gas:station:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(GasStation station)
    {
        startPage();
        List<GasStation> list = stationService.selectStationList(station);
        return getDataTable(list);
    }

    @Log(title = "站点管理", businessType = BusinessType.EXPORT)
    @RequiresPermissions("gas:station:export")
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(GasStation station)
    {
        List<GasStation> list = stationService.selectStationList(station);
        ExcelUtil<GasStation> util = new ExcelUtil<GasStation>(GasStation.class);
        return util.exportExcel(list, "站点数据");
    }

    @Log(title = "站点管理", businessType = BusinessType.DELETE)
    @RequiresPermissions("gas:station:remove")
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        if (StringUtils.isEmpty(ids))
        {
            return error("请选择要删除的站点");
        }
        return toAjax(stationService.deleteStationByIds(Convert.toLongArray(ids)));
    }

    /**
     * 新增站点
     */
    @RequiresPermissions("gas:station:add")
    @GetMapping("/add")
    public String add(ModelMap mmap)
    {
        mmap.put("buyers", activeBuyers());
        return prefix + "/add";
    }

    @Log(title = "站点管理", businessType = BusinessType.INSERT)
    @RequiresPermissions("gas:station:add")
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(@Validated GasStation station)
    {
        if (!stationService.checkStationCodeUnique(station))
        {
            return error("新增站点'" + station.getStationName() + "'失败，站点编码已存在");
        }
        station.setCreateBy(getLoginName());
        return toAjax(stationService.insertStation(station));
    }

    /**
     * 修改站点
     */
    @RequiresPermissions("gas:station:edit")
    @GetMapping("/edit/{stationId}")
    public String edit(@PathVariable("stationId") Long stationId, ModelMap mmap)
    {
        mmap.put("station", stationService.selectStationById(stationId));
        mmap.put("buyers", activeBuyers());
        return prefix + "/edit";
    }

    @Log(title = "站点管理", businessType = BusinessType.UPDATE)
    @RequiresPermissions("gas:station:edit")
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(@Validated GasStation station)
    {
        if (!stationService.checkStationCodeUnique(station))
        {
            return error("修改站点'" + station.getStationName() + "'失败，站点编码已存在");
        }
        station.setUpdateBy(getLoginName());
        return toAjax(stationService.updateStation(station));
    }

    /**
     * 下载站点二维码 PNG（内容 = h5.base.url + ?station=二维码标识）
     */
    @RequiresPermissions("gas:station:list")
    @GetMapping("/qrcode/{stationId}")
    public void qrcode(@PathVariable("stationId") Long stationId, HttpServletResponse response) throws IOException
    {
        GasStation station = stationService.selectStationById(stationId);
        if (station == null)
        {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "站点不存在");
            return;
        }
        byte[] png = stationService.buildQrPng(station);
        response.setContentType("image/png");
        response.setHeader("Content-Disposition", "attachment; filename=" + station.getStationCode() + "-qrcode.png");
        response.setContentLength(png.length);
        response.getOutputStream().write(png);
        response.getOutputStream().flush();
    }

    /**
     * 校验站点编码
     */
    @RequiresPermissions("gas:station:list")
    @PostMapping("/checkStationCodeUnique")
    @ResponseBody
    public boolean checkStationCodeUnique(GasStation station)
    {
        return stationService.checkStationCodeUnique(station);
    }

    private List<GasBuyer> activeBuyers()
    {
        GasBuyer query = new GasBuyer();
        query.setStatus("0");
        return buyerService.selectBuyerList(query);
    }
}
