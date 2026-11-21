package com.ruoyi.gas.mapper;

import java.util.List;
import com.ruoyi.gas.domain.GasOrderLog;

/**
 * 订单状态日志 数据层
 *
 * @author gas
 */
public interface GasOrderLogMapper
{
    public List<GasOrderLog> selectLogsByOrderId(Long orderId);

    public int insertLog(GasOrderLog log);
}
