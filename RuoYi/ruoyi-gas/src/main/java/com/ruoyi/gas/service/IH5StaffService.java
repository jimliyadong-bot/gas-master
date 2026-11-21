package com.ruoyi.gas.service;

import java.math.BigDecimal;
import java.util.Map;
import com.ruoyi.gas.domain.GasOrder;
import com.ruoyi.gas.h5.H5User;

/** H5 加气员业务：动态码核销与会员码代下单，全部在线完成 */
public interface IH5StaffService
{
    Map<String, Object> workbench(H5User user);

    Map<String, Object> inspectRedeemToken(H5User user, String token);

    GasOrder redeem(H5User user, String token, Long shiftId, BigDecimal actualVolume);

    Map<String, Object> inspectMemberToken(H5User user, String token);

    GasOrder createProxyOrder(H5User user, String token, Long shiftId, Long gunId, BigDecimal orderAmount);
}
