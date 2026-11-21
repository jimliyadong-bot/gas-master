package com.ruoyi.web.controller.gas;

import java.util.Date;
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
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.gas.domain.GasGun;
import com.ruoyi.gas.domain.GasGunPrice;
import com.ruoyi.gas.service.IGasGunPriceService;
import com.ruoyi.gas.service.IGasGunService;

/**
 * 枪价管理（按枪维护销售价格，带生效时间，新价自动结束旧价，历史保留）
 *
 * @author gas
 */
@Controller
@RequestMapping("/gas/price")
public class GasGunPriceController extends BaseController
{
    private String prefix = "gas/price";

    @Autowired
    private IGasGunPriceService priceService;

    @Autowired
    private IGasGunService gunService;

    @RequiresPermissions("gas:price:list")
    @GetMapping()
    public String price(ModelMap mmap)
    {
        // 供筛选用的正常站点与枪列表
        GasGun query = new GasGun();
        query.setStatus("0");
        mmap.put("guns", gunService.selectGunList(query));
        return prefix + "/price";
    }

    @RequiresPermissions("gas:price:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(GasGunPrice price)
    {
        startPage();
        List<GasGunPrice> list = priceService.selectPriceList(price);
        return getDataTable(list);
    }

    @Log(title = "枪价管理", businessType = BusinessType.DELETE)
    @RequiresPermissions("gas:price:remove")
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        if (StringUtils.isEmpty(ids))
        {
            return error("请选择要删除的价格记录");
        }
        return toAjax(priceService.deletePriceByIds(Convert.toLongArray(ids)));
    }

    /**
     * 新增枪价（只增不改：新价生效时自动结束旧价）
     */
    @RequiresPermissions("gas:price:add")
    @GetMapping("/add")
    public String add(ModelMap mmap)
    {
        GasGun query = new GasGun();
        query.setStatus("0");
        mmap.put("guns", gunService.selectGunList(query));
        mmap.put("now", DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, new Date()));
        return prefix + "/add";
    }

    @Log(title = "枪价管理", businessType = BusinessType.INSERT)
    @RequiresPermissions("gas:price:add")
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(@Validated GasGunPrice price)
    {
        price.setCreateBy(getLoginName());
        return toAjax(priceService.insertPrice(price));
    }
}
