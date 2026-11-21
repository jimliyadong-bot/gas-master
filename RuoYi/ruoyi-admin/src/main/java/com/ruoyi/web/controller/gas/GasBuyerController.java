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
import com.ruoyi.gas.domain.GasBuyer;
import com.ruoyi.gas.domain.GasBuyerMerchant;
import com.ruoyi.gas.service.IGasBuyerService;

/**
 * 采购商管理（含拉卡拉商户配置、短信通知手机号、开票提醒参数）
 *
 * @author gas
 */
@Controller
@RequestMapping("/gas/buyer")
public class GasBuyerController extends BaseController
{
    private String prefix = "gas/buyer";

    @Autowired
    private IGasBuyerService buyerService;

    @RequiresPermissions("gas:buyer:list")
    @GetMapping()
    public String buyer()
    {
        return prefix + "/buyer";
    }

    @RequiresPermissions("gas:buyer:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(GasBuyer buyer)
    {
        startPage();
        List<GasBuyer> list = buyerService.selectBuyerList(buyer);
        return getDataTable(list);
    }

    @Log(title = "采购商管理", businessType = BusinessType.EXPORT)
    @RequiresPermissions("gas:buyer:export")
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(GasBuyer buyer)
    {
        List<GasBuyer> list = buyerService.selectBuyerList(buyer);
        ExcelUtil<GasBuyer> util = new ExcelUtil<GasBuyer>(GasBuyer.class);
        return util.exportExcel(list, "采购商数据");
    }

    @Log(title = "采购商管理", businessType = BusinessType.DELETE)
    @RequiresPermissions("gas:buyer:remove")
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        if (StringUtils.isEmpty(ids))
        {
            return error("请选择要删除的采购商");
        }
        return toAjax(buyerService.deleteBuyerByIds(Convert.toLongArray(ids)));
    }

    /**
     * 新增采购商
     */
    @RequiresPermissions("gas:buyer:add")
    @GetMapping("/add")
    public String add()
    {
        return prefix + "/add";
    }

    @Log(title = "采购商管理", businessType = BusinessType.INSERT, excludeParamNames = { "merchant.secretRef" })
    @RequiresPermissions("gas:buyer:add")
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(@Validated GasBuyer buyer)
    {
        if (!buyerService.checkBuyerNameUnique(buyer))
        {
            return error("新增采购商'" + buyer.getBuyerName() + "'失败，名称已存在");
        }
        GasBuyerMerchant merchant = buyer.getMerchant();
        if (merchant != null && !buyerService.checkMerchantNoUnique(merchant))
        {
            return error("新增采购商'" + buyer.getBuyerName() + "'失败，商户号已存在");
        }
        buyer.setCreateBy(getLoginName());
        return toAjax(buyerService.insertBuyer(buyer));
    }

    /**
     * 修改采购商
     */
    @RequiresPermissions("gas:buyer:edit")
    @GetMapping("/edit/{buyerId}")
    public String edit(@PathVariable("buyerId") Long buyerId, ModelMap mmap)
    {
        mmap.put("buyer", buyerService.selectBuyerById(buyerId));
        return prefix + "/edit";
    }

    @Log(title = "采购商管理", businessType = BusinessType.UPDATE, excludeParamNames = { "merchant.secretRef" })
    @RequiresPermissions("gas:buyer:edit")
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(@Validated GasBuyer buyer)
    {
        if (!buyerService.checkBuyerNameUnique(buyer))
        {
            return error("修改采购商'" + buyer.getBuyerName() + "'失败，名称已存在");
        }
        GasBuyerMerchant merchant = buyer.getMerchant();
        if (merchant != null && !buyerService.checkMerchantNoUnique(merchant))
        {
            return error("修改采购商'" + buyer.getBuyerName() + "'失败，商户号已存在");
        }
        buyer.setUpdateBy(getLoginName());
        return toAjax(buyerService.updateBuyer(buyer));
    }

    /**
     * 校验采购商名称
     */
    @RequiresPermissions("gas:buyer:list")
    @PostMapping("/checkBuyerNameUnique")
    @ResponseBody
    public boolean checkBuyerNameUnique(GasBuyer buyer)
    {
        return buyerService.checkBuyerNameUnique(buyer);
    }

    /**
     * 校验商户号
     */
    @RequiresPermissions("gas:buyer:list")
    @PostMapping("/checkMerchantNoUnique")
    @ResponseBody
    public boolean checkMerchantNoUnique(GasBuyerMerchant merchant)
    {
        return buyerService.checkMerchantNoUnique(merchant);
    }
}
