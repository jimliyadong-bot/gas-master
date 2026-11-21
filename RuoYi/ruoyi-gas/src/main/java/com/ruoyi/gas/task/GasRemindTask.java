package com.ruoyi.gas.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.gas.service.IGasRemindService;

/**
 * 提醒定时任务（RuoYi Quartz 调度，sys_job invoke_target = gasRemindTask.scanDaily()）
 *
 * <p>每天 9:00：用完未开票提醒、全站库存预警补查、发送到点的待发提醒（§3.7）。</p>
 *
 * @author gas
 */
@Component("gasRemindTask")
public class GasRemindTask
{
    @Autowired
    private IGasRemindService remindService;

    public void scanDaily()
    {
        remindService.scanDaily();
    }
}
