package com.ruoyi.gas.task;

import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.gas.mapper.GasDynamicTokenMapper;
import com.ruoyi.gas.service.IGasCouponService;
import com.ruoyi.gas.service.IH5OrderService;

/**
 * 订单定时任务（RuoYi Quartz 调度，sys_job invoke_target = gasOrderTask.closeExpired()）
 *
 * <p>每 5 分钟：待支付超时订单自动关闭（WAIT_PAY → CLOSED，支付流水联动关闭）；
 * 顺带清理一天前过期的动态码（保留近一天便于排查）。
 * 查单补偿：真实拉卡拉网关接入后在此扩展（PAYING 流水主动查单，阶段8）。</p>
 *
 * @author gas
 */
@Component("gasOrderTask")
public class GasOrderTask
{
    private static final Logger log = LoggerFactory.getLogger(GasOrderTask.class);

    @Autowired
    private IH5OrderService orderService;

    @Autowired
    private GasDynamicTokenMapper tokenMapper;

    @Autowired
    private IGasCouponService couponService;

    public void closeExpired()
    {
        try
        {
            int closed = orderService.closeExpiredOrders();
            if (closed > 0) log.info("定时关单完成：{} 单", closed);
        }
        catch (Exception e)
        {
            log.error("订单超时关单失败", e);
        }
        try
        {
            int expired = couponService.expireUnused();
            if (expired > 0) log.info("优惠券过期处理：{} 张", expired);
        }
        catch (Exception e)
        {
            log.error("优惠券过期处理失败", e);
        }
        try
        {
            tokenMapper.deleteExpiredBefore(new Date(System.currentTimeMillis() - 24 * 3600_000L));
        }
        catch (Exception e)
        {
            log.error("动态码清理失败", e);
        }
    }
}
