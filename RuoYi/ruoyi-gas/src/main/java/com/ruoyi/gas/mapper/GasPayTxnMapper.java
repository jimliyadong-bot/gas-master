package com.ruoyi.gas.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.gas.domain.GasPayTxn;

/**
 * 支付流水 数据层（回调幂等：uk_out_trade_no + 条件更新）
 *
 * @author gas
 */
public interface GasPayTxnMapper
{
    public GasPayTxn selectByOutTradeNo(String outTradeNo);

    public GasPayTxn selectPayingByOrderId(Long orderId);

    public List<GasPayTxn> selectTxnsByOrderId(Long orderId);

    /**
     * 条件更新为支付成功（幂等核心：fromStatus='PAYING' 不匹配则 0 行）
     */
    public int markSuccess(@Param("txnId") Long txnId,
            @Param("channelTradeNo") String channelTradeNo, @Param("payChannel") String payChannel,
            @Param("rawNotify") String rawNotify);

    /** 订单关单联动：PAYING → CLOSED */
    public int closePayingByOrderId(@Param("orderId") Long orderId);

    public int insertTxn(GasPayTxn txn);
}
