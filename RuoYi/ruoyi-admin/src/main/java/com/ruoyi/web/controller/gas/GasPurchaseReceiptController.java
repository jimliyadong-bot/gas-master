package com.ruoyi.web.controller.gas;

import java.util.List;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.gas.domain.GasPurchaseBatch;
import com.ruoyi.gas.domain.GasPurchaseReceipt;
import com.ruoyi.gas.service.IGasPurchaseBatchService;
import com.ruoyi.gas.service.IGasPurchaseReceiptService;
import com.ruoyi.gas.service.IInventoryService;

/**
 * 到货入库（登记后经 InventoryService 入库：池累计 + IN 流水）
 *
 * @author gas
 */
@Controller
@RequestMapping("/gas/purchase/receipt")
public class GasPurchaseReceiptController extends BaseController
{
    private String prefix = "gas/purchase/receipt";

    @Autowired
    private IGasPurchaseReceiptService receiptService;

    @Autowired
    private IGasPurchaseBatchService batchService;

    @Autowired
    private IInventoryService inventoryService;

    @Autowired
    private GasStationAccess stationAccess;

    @RequiresPermissions("gas:receipt:list")
    @GetMapping()
    public String receipt()
    {
        return prefix + "/receipt";
    }

    @RequiresPermissions("gas:receipt:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(GasPurchaseReceipt receipt)
    {
        Long scoped = stationAccess.scopedStationId();
        if (scoped != null)
        {
            receipt.setStationId(scoped);
        }
        startPage();
        List<GasPurchaseReceipt> list = receiptService.selectReceiptList(receipt);
        return getDataTable(list);
    }

    @Log(title = "到货入库", businessType = BusinessType.EXPORT)
    @RequiresPermissions("gas:receipt:export")
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(GasPurchaseReceipt receipt)
    {
        Long scoped = stationAccess.scopedStationId();
        if (scoped != null)
        {
            receipt.setStationId(scoped);
        }
        List<GasPurchaseReceipt> list = receiptService.selectReceiptList(receipt);
        ExcelUtil<GasPurchaseReceipt> util = new ExcelUtil<GasPurchaseReceipt>(GasPurchaseReceipt.class);
        return util.exportExcel(list, "到货入库数据");
    }

    @RequiresPermissions("gas:receipt:add")
    @GetMapping("/add")
    public String add(ModelMap mmap)
    {
        GasPurchaseBatch query = new GasPurchaseBatch();
        Long scoped = stationAccess.scopedStationId();
        if (scoped != null)
        {
            query.setStationId(scoped);
        }
        mmap.put("batches", batchService.selectBatchList(query));
        return prefix + "/add";
    }

    /**
     * 到货登记 = 新增到货记录 + 入库（池累计/流水），同一事务
     */
    @Log(title = "到货入库", businessType = BusinessType.INSERT)
    @RequiresPermissions("gas:receipt:add")
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(@Validated GasPurchaseReceipt receipt)
    {
        GasPurchaseBatch batch = batchService.selectBatchById(receipt.getBatchId());
        if (batch == null)
        {
            return error("采购批次不存在");
        }
        stationAccess.requireStation(batch.getStationId());
        receipt.setCreateBy(getLoginName());
        inventoryService.receive(receipt);
        return success(String.format("入库成功：站点库存池累计入库已增加 %s", receipt.getQty()));
    }
}
