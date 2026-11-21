package com.ruoyi.gas.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.gas.domain.GasInventoryPool;

/**
 * 库存池 数据层
 *
 * @author gas
 */
public interface GasInventoryPoolMapper
{
    public List<GasInventoryPool> selectPoolList(GasInventoryPool pool);

    public GasInventoryPool selectPoolById(Long poolId);

    /** 悲观锁：SELECT ... FOR UPDATE（同站同气种操作串行化） */
    public GasInventoryPool selectPoolForUpdate(@Param("stationId") Long stationId, @Param("gasType") String gasType);

    /** 锁定单池（调整/预警用） */
    public GasInventoryPool selectPoolByIdForUpdate(Long poolId);

    public int insertPool(GasInventoryPool pool);

    /** 增量更新（行锁已由 FOR UPDATE 持有） */
    public int addTotalIn(@Param("poolId") Long poolId, @Param("qty") BigDecimal qty);

    public int addReserved(@Param("poolId") Long poolId, @Param("qty") BigDecimal qty);

    public int addConsumed(@Param("poolId") Long poolId, @Param("qty") BigDecimal qty);

    public int addAdjusted(@Param("poolId") Long poolId, @Param("qty") BigDecimal qty);

    /** 预警配置（threshold 可空=关闭；armed 由提醒逻辑维护） */
    public int updateAlert(@Param("poolId") Long poolId, @Param("alertThreshold") BigDecimal alertThreshold);

    public int updateAlertArmed(@Param("poolId") Long poolId, @Param("alertArmed") Integer alertArmed);
}
