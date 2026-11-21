package com.ruoyi.gas.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.gas.domain.GasInventoryLedger;

/**
 * 库存流水 数据层（只追加）
 *
 * @author gas
 */
public interface GasInventoryLedgerMapper
{
    public List<GasInventoryLedger> selectLedgerList(GasInventoryLedger ledger);

    public int insertLedger(GasInventoryLedger ledger);

    /** 订单某类型流水计数（核销幂等检查） */
    public int countByOrder(@Param("orderId") Long orderId, @Param("bizType") String bizType);

    /** 订单在指定池的有效预占 = sum(RESERVE) - sum(RELEASE) - sum(CONSUME)，禁止跨池挪用 */
    public BigDecimal sumOrderReserved(@Param("orderId") Long orderId, @Param("poolId") Long poolId);

    /** 按池重算余额：sum(各类型数量) —— IN/RESERVE/RELEASE/CONSUME/ADJUST 符号由 SQL 处理 */
    public BigDecimal sumByPool(Long poolId);
}
