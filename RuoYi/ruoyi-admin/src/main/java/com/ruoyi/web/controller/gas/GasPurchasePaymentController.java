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
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.gas.domain.GasPurchaseBatch;
import com.ruoyi.gas.domain.GasPurchasePayment;
import com.ruoyi.gas.service.IGasPurchaseBatchService;
import com.ruoyi.gas.service.IGasPurchasePaymentService;

/**
 * 采购付款登记（只登记：金额、日期、方式、凭证，无审批流程）
 *
 * @author gas
 */
@Controller
@RequestMapping("/gas/purchase/payment")
public class GasPurchasePaymentController extends BaseController
{
    private String prefix = "gas/purchase/payment";

    @Autowired
    private IGasPurchasePaymentService paymentService;

    @Autowired
    private IGasPurchaseBatchService batchService;

    @RequiresPermissions("gas:payment:list")
    @GetMapping()
    public String payment()
    {
        return prefix + "/payment";
    }

    @RequiresPermissions("gas:payment:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(GasPurchasePayment payment)
    {
        startPage();
        List<GasPurchasePayment> list = paymentService.selectPaymentList(payment);
        return getDataTable(list);
    }

    @Log(title = "采购付款", businessType = BusinessType.EXPORT)
    @RequiresPermissions("gas:payment:export")
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(GasPurchasePayment payment)
    {
        List<GasPurchasePayment> list = paymentService.selectPaymentList(payment);
        ExcelUtil<GasPurchasePayment> util = new ExcelUtil<GasPurchasePayment>(GasPurchasePayment.class);
        return util.exportExcel(list, "采购付款数据");
    }

    @RequiresPermissions("gas:payment:add")
    @GetMapping("/add")
    public String add(ModelMap mmap)
    {
        mmap.put("batches", batchService.selectBatchList(new GasPurchaseBatch()));
        return prefix + "/add";
    }

    @Log(title = "采购付款", businessType = BusinessType.INSERT)
    @RequiresPermissions("gas:payment:add")
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(@Validated GasPurchasePayment payment)
    {
        payment.setCreateBy(getLoginName());
        return toAjax(paymentService.insertPayment(payment));
    }

    @Log(title = "采购付款", businessType = BusinessType.DELETE)
    @RequiresPermissions("gas:payment:remove")
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        if (StringUtils.isEmpty(ids))
        {
            return error("请选择要删除的付款记录");
        }
        return toAjax(paymentService.deletePaymentByIds(Convert.toLongArray(ids)));
    }
}
