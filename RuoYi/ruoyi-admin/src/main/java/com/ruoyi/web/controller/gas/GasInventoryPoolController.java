package com.ruoyi.web.controller.gas;

import java.math.BigDecimal;
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
import com.ruoyi.gas.domain.GasInventoryLedger;
import com.ruoyi.gas.domain.GasInventoryPool;
import com.ruoyi.gas.domain.GasPurchaseReceipt;
import com.ruoyi.gas.service.IInventoryService;

/**
 * 库存台账（站点 → 到货批次余量 → 流水；库存调整；余量预警设置）
 *
 * @author gas
 */
@Controller
@RequestMapping("/gas/inventory")
public class GasInventoryPoolController extends BaseController
{
    private String prefix = "gas/inventory";

    @Autowired
    private IInventoryService inventoryService;

    @Autowired
    private GasStationAccess stationAccess;

    @RequiresPermissions("gas:inventory:list")
    @GetMapping()
    public String pool()
    {
        return prefix + "/pool";
    }

    @RequiresPermissions("gas:inventory:list")
    @PostMapping("/pool/list")
    @ResponseBody
    public TableDataInfo poolList(GasInventoryPool pool)
    {
        Long scoped = stationAccess.scopedStationId();
        if (scoped != null)
        {
            pool.setStationId(scoped);
        }
        startPage();
        List<GasInventoryPool> list = inventoryService.selectPoolList(pool);
        return getDataTable(list);
    }

    /**
     * 某库存池的到货批次余量（台账批次视图）
     */
    @RequiresPermissions("gas:inventory:list")
    @GetMapping("/pool/receipts/{poolId}")
    @ResponseBody
    public AjaxResult receipts(@PathVariable("poolId") Long poolId)
    {
        GasInventoryPool pool = inventoryService.selectPoolById(poolId);
        if (pool == null)
        {
            return error("库存池不存在");
        }
        stationAccess.requireStation(pool.getStationId());
        List<GasPurchaseReceipt> list = inventoryService.selectReceiptListOfPool(pool.getStationId(), pool.getGasType());
        return success(list);
    }

    @RequiresPermissions("gas:inventory:list")
    @GetMapping("/ledger")
    public String ledger()
    {
        return prefix + "/ledger";
    }

    @RequiresPermissions("gas:inventory:list")
    @PostMapping("/ledger/list")
    @ResponseBody
    public TableDataInfo ledgerList(GasInventoryLedger ledger)
    {
        Long scoped = stationAccess.scopedStationId();
        if (scoped != null)
        {
            ledger.setStationId(scoped);
        }
        startPage();
        List<GasInventoryLedger> list = inventoryService.selectLedgerList(ledger);
        return getDataTable(list);
    }

    @RequiresPermissions("gas:inventory:adjust")
    @GetMapping("/adjust/{poolId}")
    public String adjust(@PathVariable("poolId") Long poolId, ModelMap mmap)
    {
        GasInventoryPool pool = inventoryService.selectPoolById(poolId);
        if (pool == null)
        {
            throw new com.ruoyi.common.exception.ServiceException("库存池不存在");
        }
        stationAccess.requireStation(pool.getStationId());
        mmap.put("pool", pool);
        mmap.put("receipts", inventoryService.selectReceiptListOfPool(pool.getStationId(), pool.getGasType()));
        return prefix + "/adjust";
    }

    /**
     * 人工调整：必须指定到货批次并填写原因（Dev_Plan 阶段3-3）
     */
    @Log(title = "库存调整", businessType = BusinessType.UPDATE)
    @RequiresPermissions("gas:inventory:adjust")
    @PostMapping("/adjust")
    @ResponseBody
    public AjaxResult adjustSave(Long poolId, Long receiptId, BigDecimal qty, String reason)
    {
        GasInventoryPool pool = inventoryService.selectPoolById(poolId);
        if (pool == null)
        {
            return error("库存池不存在");
        }
        stationAccess.requireStation(pool.getStationId());
        inventoryService.adjust(pool.getStationId(), pool.getGasType(), receiptId, qty, reason, getLoginName());
        return success();
    }

    @RequiresPermissions("gas:inventory:alert")
    @GetMapping("/alert/{poolId}")
    public String alert(@PathVariable("poolId") Long poolId, ModelMap mmap)
    {
        GasInventoryPool pool = inventoryService.selectPoolById(poolId);
        if (pool == null)
        {
            throw new com.ruoyi.common.exception.ServiceException("库存池不存在");
        }
        stationAccess.requireStation(pool.getStationId());
        mmap.put("pool", pool);
        return prefix + "/alert";
    }

    /**
     * 余量预警设置（阈值 null=关闭）
     */
    @Log(title = "库存预警设置", businessType = BusinessType.UPDATE)
    @RequiresPermissions("gas:inventory:alert")
    @PostMapping("/alert")
    @ResponseBody
    public AjaxResult alertSave(Long poolId, BigDecimal alertThreshold)
    {
        GasInventoryPool pool = inventoryService.selectPoolById(poolId);
        if (pool == null)
        {
            return error("库存池不存在");
        }
        stationAccess.requireStation(pool.getStationId());
        return toAjax(inventoryService.updateAlertThreshold(poolId, alertThreshold));
    }
}
