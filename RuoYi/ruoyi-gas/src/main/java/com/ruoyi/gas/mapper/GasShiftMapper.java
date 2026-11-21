package com.ruoyi.gas.mapper;

import java.util.List;
import com.ruoyi.gas.domain.GasShift;

/** 班次数据层 */
public interface GasShiftMapper
{
    GasShift selectShiftById(Long shiftId);

    GasShift selectShiftByIdForUpdate(Long shiftId);

    GasShift selectActiveByStation(Long stationId);

    GasShift selectActiveByStationForUpdate(Long stationId);

    GasShift selectActiveByUser(Long userId);

    List<GasShift> selectShiftList(GasShift query);

    GasShift selectShiftSummary(Long shiftId);

    int insertShift(GasShift shift);

    int endShift(GasShift shift);
}
