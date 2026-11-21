package com.ruoyi.web.controller.gas;

import java.util.List;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.gas.domain.GasRemind;
import com.ruoyi.gas.service.IGasRemindService;

/**
 * 提醒记录（开票提醒、库存预警）
 *
 * @author gas
 */
@Controller
@RequestMapping("/gas/remind")
public class GasRemindController extends BaseController
{
    private String prefix = "gas/remind";

    @Autowired
    private IGasRemindService remindService;

    @RequiresPermissions("gas:remind:list")
    @GetMapping()
    public String remind()
    {
        return prefix + "/remind";
    }

    @RequiresPermissions("gas:remind:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(GasRemind remind)
    {
        startPage();
        List<GasRemind> list = remindService.selectRemindList(remind);
        return getDataTable(list);
    }
}
