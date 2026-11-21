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
import com.ruoyi.gas.domain.GasBuyer;
import com.ruoyi.gas.domain.GasCoupon;
import com.ruoyi.gas.domain.GasCouponTemplate;
import com.ruoyi.gas.service.IGasBuyerService;
import com.ruoyi.gas.service.IGasCouponService;
import com.ruoyi.gas.service.IGasCouponTemplateService;

/** 优惠券模板、定向发放和核销统计。 */
@Controller
@RequestMapping("/gas/coupon")
public class GasCouponController extends BaseController
{
    private static final String PREFIX = "gas/coupon";
    @Autowired
    private IGasCouponTemplateService templateService;
    @Autowired
    private IGasCouponService couponService;
    @Autowired
    private IGasBuyerService buyerService;

    @RequiresPermissions("gas:coupon:list")
    @GetMapping
    public String coupon() { return PREFIX + "/coupon"; }

    @RequiresPermissions("gas:coupon:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(GasCouponTemplate template)
    {
        startPage();
        List<GasCouponTemplate> list = templateService.selectTemplateList(template);
        return getDataTable(list);
    }

    @RequiresPermissions("gas:coupon:add")
    @GetMapping("/add")
    public String add(ModelMap mmap)
    {
        mmap.put("template", new GasCouponTemplate());
        mmap.put("buyers", activeBuyers());
        return PREFIX + "/form";
    }

    @Log(title = "优惠券模板", businessType = BusinessType.INSERT)
    @RequiresPermissions("gas:coupon:add")
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(GasCouponTemplate template)
    {
        template.setCreateBy(getLoginName());
        return toAjax(templateService.insertTemplate(template));
    }

    @RequiresPermissions("gas:coupon:edit")
    @GetMapping("/edit/{templateId}")
    public String edit(@PathVariable("templateId") Long templateId, ModelMap mmap)
    {
        GasCouponTemplate template = templateService.selectTemplateById(templateId);
        List<GasBuyer> buyers = activeBuyers();
        if (template != null && buyers.stream().noneMatch(b -> template.getBuyerId().equals(b.getBuyerId())))
        {
            GasBuyer current = buyerService.selectBuyerById(template.getBuyerId());
            if (current != null) buyers.add(current);
        }
        mmap.put("template", template);
        mmap.put("buyers", buyers);
        return PREFIX + "/form";
    }

    @Log(title = "优惠券模板", businessType = BusinessType.UPDATE)
    @RequiresPermissions("gas:coupon:edit")
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(GasCouponTemplate template)
    {
        template.setUpdateBy(getLoginName());
        return toAjax(templateService.updateTemplate(template));
    }

    @RequiresPermissions("gas:coupon:grant")
    @GetMapping("/grant/{templateId}")
    public String grant(@PathVariable("templateId") Long templateId, ModelMap mmap)
    {
        mmap.put("template", templateService.selectTemplateById(templateId));
        return PREFIX + "/grant";
    }

    @Log(title = "定向发放优惠券", businessType = BusinessType.INSERT, excludeParamNames = { "phone" })
    @RequiresPermissions("gas:coupon:grant")
    @PostMapping("/grant")
    @ResponseBody
    public AjaxResult grantSave(Long templateId, Long memberId, String phone)
    {
        GasCoupon coupon = couponService.issue(templateId, memberId, phone, getLoginName());
        AjaxResult result = success("发放成功");
        result.put("couponId", coupon.getCouponId());
        return result;
    }

    @RequiresPermissions("gas:coupon:list")
    @GetMapping("/records/{templateId}")
    public String records(@PathVariable("templateId") Long templateId, ModelMap mmap)
    {
        mmap.put("template", templateService.selectTemplateById(templateId));
        return PREFIX + "/records";
    }

    @RequiresPermissions("gas:coupon:list")
    @PostMapping("/couponList")
    @ResponseBody
    public TableDataInfo couponList(GasCoupon coupon)
    {
        startPage();
        return getDataTable(couponService.selectCouponList(coupon));
    }

    private List<GasBuyer> activeBuyers()
    {
        GasBuyer query = new GasBuyer();
        query.setStatus("0");
        return buyerService.selectBuyerList(query);
    }
}
