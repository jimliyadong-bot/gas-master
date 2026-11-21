package com.ruoyi.gas.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.gas.domain.GasRemind;

/**
 * 提醒记录 数据层
 *
 * @author gas
 */
public interface GasRemindMapper
{
    public List<GasRemind> selectRemindList(GasRemind remind);

    /** 已发送/待发送的同类提醒次数（防重复） */
    public int countByType(@Param("remindType") String remindType, @Param("batchId") Long batchId, @Param("poolId") Long poolId);

    public int insertRemind(GasRemind remind);

    /** 待发送列表（到点未发） */
    public List<GasRemind> selectPendingList();

    public int updateStatus(@Param("remindId") Long remindId, @Param("status") String status);
}
