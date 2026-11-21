package com.ruoyi.gas.service;

import java.util.List;
import com.ruoyi.gas.domain.GasRemind;

/**
 * 提醒服务（Dev_Plan §3.7 开票提醒与库存预警）
 *
 * <p>提醒对象为采购商 notify_phones；21:00–8:00 触发的推迟到次日 8:00 发送；
 * gas_remind 唯一键防重复。</p>
 *
 * @author gas
 */
public interface IGasRemindService
{
    /** 批次余量变化检查：即将用完（≤采购量×比例）/ 已用完，各提醒 1 次 */
    void onBatchRemainChanged(Long stationId, String gasType);

    /** 库存池预警检查：可用量 ≤ 预警值触发一次，回升后重新武装 */
    void checkPoolAlert(Long stationId, String gasType);

    /** 每日 9:00 定时任务：用完未开票（每 N 天 1 次最多 M 次）、全站库存预警补查、发送到点提醒 */
    void scanDaily();

    /** 提醒记录查询（后台消息页） */
    List<GasRemind> selectRemindList(GasRemind remind);
}
