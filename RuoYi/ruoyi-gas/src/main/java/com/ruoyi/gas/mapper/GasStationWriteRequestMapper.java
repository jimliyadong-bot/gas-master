package com.ruoyi.gas.mapper;

import com.ruoyi.gas.domain.GasStationWriteRequest;

public interface GasStationWriteRequestMapper
{
    /** 唯一键争用会等待前一事务；绝不覆盖首次提交的内容或结果。 */
    int claim(GasStationWriteRequest request);
    GasStationWriteRequest selectForUpdate(GasStationWriteRequest request);
    int complete(GasStationWriteRequest request);
}
