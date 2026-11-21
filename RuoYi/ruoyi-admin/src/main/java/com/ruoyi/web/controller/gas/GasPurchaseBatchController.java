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
import com.ruoyi.gas.domain.GasPurchaseBatch;
import com.ruoyi.gas.domain.GasStation;
import com.ruoyi.gas.service.IGasPurchaseBatchService;
import com.ruoyi.gas.service.IGasStationService;

/**
 * 采购批次管理（采购商 + 站点 + 气种，票据状态维护）
 *
 * @author gas
 */
@Controller
@RequestMapping("/gas/purchase/batch")
public class GasPurchaseBatchController extends BaseController
{
    private String prefix = "gas/purchase/batch";

    @Autowired
    private IGasPurchaseBatchService batchService;

    @Autowired
    private IGasStationService stationService;

    @Autowired
    private GasStationAccess stationAccess;

    @RequiresPermissions("gas:batch:list")
    @GetMapping()
    public String batch(ModelMap mmap)
    {
        mmap.put("stations", activeStations());
        return prefix + "/batch";
    }

    @RequiresPermissions("gas:batch:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(GasPurchaseBatch batch)
    {
        scopeBatch(batch);
        startPage();
        List<GasPurchaseBatch> list = batchService.selectBatchList(batch);
        return getDataTable(list);
    }

    @Log(title = "采购批次", businessType = BusinessType.EXPORT)
    @RequiresPermissions("gas:batch:export")
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(GasPurchaseBatch batch)
    {
        scopeBatch(batch);
        List<GasPurchaseBatch> list = batchService.selectBatchList(batch);
        ExcelUtil<GasPurchaseBatch> util = new ExcelUtil<GasPurchaseBatch>(GasPurchaseBatch.class);
        return util.exportExcel(list, "采购批次数据");
    }

    @RequiresPermissions("gas:batch:add")
    @GetMapping("/add")
    public String add(ModelMap mmap)
    {
        mmap.put("stations", activeStations());
        mmap.put("nextBatchNo", batchService.generateBatchNo());
        return prefix + "/add";
    }

    @Log(title = "采购批次", businessType = BusinessType.INSERT)
    @RequiresPermissions("gas:batch:add")
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(@Validated GasPurchaseBatch batch)
    {
        stationAccess.requireStation(batch.getStationId());
        if (!batchService.checkBatchNoUnique(batch))
        {
            return error("新增采购批次'" + batch.getBatchNo() + "'失败，批次号已存在");
        }
        batch.setCreateBy(getLoginName());
        return toAjax(batchService.insertBatch(batch));
    }

    @RequiresPermissions("gas:batch:edit")
    @GetMapping("/edit/{batchId}")
    public String edit(@PathVariable("batchId") Long batchId, ModelMap mmap)
    {
        mmap.put("batch", batchService.selectBatchById(batchId));
        mmap.put("stations", activeStations());
        return prefix + "/edit";
    }

    @Log(title = "采购批次", businessType = BusinessType.UPDATE)
    @RequiresPermissions("gas:batch:edit")
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(@Validated GasPurchaseBatch batch)
    {
        checkBatchAccess(batch.getBatchId());
        stationAccess.requireStation(batch.getStationId());
        if (!batchService.checkBatchNoUnique(batch))
        {
            return error("修改采购批次'" + batch.getBatchNo() + "'失败，批次号已存在");
        }
        batch.setUpdateBy(getLoginName());
        return toAjax(batchService.updateBatch(batch));
    }

    @Log(title = "采购批次", businessType = BusinessType.DELETE)
    @RequiresPermissions("gas:batch:remove")
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        if (StringUtils.isEmpty(ids))
        {
            return error("请选择要删除的采购批次");
        }
        Long[] batchIds = Convert.toLongArray(ids);
        for (Long batchId : batchIds)
        {
            checkBatchAccess(batchId);
        }
        return toAjax(batchService.deleteBatchByIds(batchIds));
    }

    /**
     * 维护票据状态（改为已开票需填发票号；改回未开票停止提醒）
     */
    @RequiresPermissions("gas:batch:invoice")
    @GetMapping("/invoice/{batchId}")
    public String invoice(@PathVariable("batchId") Long batchId, ModelMap mmap)
    {
        mmap.put("batch", batchService.selectBatchById(batchId));
        return prefix + "/invoice";
    }

    @Log(title = "采购批次-票据状态", businessType = BusinessType.UPDATE)
    @RequiresPermissions("gas:batch:invoice")
    @PostMapping("/invoice")
    @ResponseBody
    public AjaxResult invoice(Long batchId, String invoiceStatus, String invoiceNo)
    {
        checkBatchAccess(batchId);
        return toAjax(batchService.updateBatchInvoice(batchId, invoiceStatus, invoiceNo, getLoginName()));
    }

    @RequiresPermissions("gas:batch:list")
    @PostMapping("/checkBatchNoUnique")
    @ResponseBody
    public boolean checkBatchNoUnique(GasPurchaseBatch batch)
    {
        return batchService.checkBatchNoUnique(batch);
    }

    private void checkBatchAccess(Long batchId)
    {
        GasPurchaseBatch old = batchService.selectBatchById(batchId);
        if (old == null)
        {
            throw new com.ruoyi.common.exception.ServiceException("采购批次不存在");
        }
        stationAccess.requireStation(old.getStationId());
    }

    private void scopeBatch(GasPurchaseBatch batch)
    {
        Long scoped = stationAccess.scopedStationId();
        if (scoped != null)
        {
            batch.setStationId(scoped);
        }
    }

    private List<GasStation> activeStations()
    {
        GasStation query = new GasStation();
        query.setStatus("0");
        Long scoped = stationAccess.scopedStationId();
        if (scoped != null)
        {
            query.setStationId(scoped);
        }
        return stationService.selectStationList(query);
    }
}
