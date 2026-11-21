package com.ruoyi.gas.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.gas.domain.GasDynamicToken;
import com.ruoyi.gas.domain.GasGun;
import com.ruoyi.gas.domain.GasGunPrice;
import com.ruoyi.gas.domain.GasMember;
import com.ruoyi.gas.domain.GasOrder;
import com.ruoyi.gas.domain.GasOrderLog;
import com.ruoyi.gas.domain.GasShift;
import com.ruoyi.gas.domain.GasStation;
import com.ruoyi.gas.h5.H5User;
import com.ruoyi.gas.mapper.GasDynamicTokenMapper;
import com.ruoyi.gas.mapper.GasGunMapper;
import com.ruoyi.gas.mapper.GasGunPriceMapper;
import com.ruoyi.gas.mapper.GasInventoryLedgerMapper;
import com.ruoyi.gas.mapper.GasMemberMapper;
import com.ruoyi.gas.mapper.GasOrderLogMapper;
import com.ruoyi.gas.mapper.GasOrderMapper;
import com.ruoyi.gas.mapper.GasStationMapper;
import com.ruoyi.gas.service.IGasConfigService;
import com.ruoyi.gas.service.IGasShiftService;
import com.ruoyi.gas.service.IH5StaffService;
import com.ruoyi.gas.service.IInventoryService;

/** 加气员业务实现：订单行锁 + 班次行锁 + 动态码单次消费 + 库存事务 */
@Service
public class H5StaffServiceImpl implements IH5StaffService
{
    @Autowired
    private IGasShiftService shiftService;
    @Autowired
    private IInventoryService inventoryService;
    @Autowired
    private IGasConfigService configService;
    @Autowired
    private GasDynamicTokenMapper tokenMapper;
    @Autowired
    private GasOrderMapper orderMapper;
    @Autowired
    private GasOrderLogMapper orderLogMapper;
    @Autowired
    private GasInventoryLedgerMapper ledgerMapper;
    @Autowired
    private GasMemberMapper memberMapper;
    @Autowired
    private GasStationMapper stationMapper;
    @Autowired
    private GasGunMapper gunMapper;
    @Autowired
    private GasGunPriceMapper priceMapper;

    @Override
    public Map<String, Object> workbench(H5User user)
    {
        SysUser staff = shiftService.requireStaffUser(user);
        GasStation station = stationMapper.selectStationById(user.getStationId());
        GasShift active = shiftService.selectActiveByStation(user.getStationId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stationId", station.getStationId());
        result.put("stationName", station.getStationName());
        result.put("staffName", displayName(staff));
        result.put("role", user.getRole());
        result.put("activeShift", active);
        result.put("myShift", active != null && user.getUserId().equals(active.getUserId()));
        return result;
    }

    @Override
    public Map<String, Object> inspectRedeemToken(H5User user, String token)
    {
        GasShift shift = requireCurrentOwnShift(user);
        GasOrder order = orderFromRedeemToken(user, token, false);
        return orderView(order, shift.getShiftId());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GasOrder redeem(H5User user, String token, Long shiftId, BigDecimal actualVolume)
    {
        GasShift shift = shiftService.requireOwnActiveShift(user, shiftId);
        requireActualVolume(actualVolume);
        GasDynamicToken dynamicToken = validToken(token, GasDynamicToken.TYPE_REDEEM);
        GasOrder order = orderMapper.selectOrderByIdForUpdate(dynamicToken.getRefId());
        if (order == null || !GasOrder.STATUS_PAID.equals(order.getStatus()))
        {
            throw new ServiceException("订单不存在或已核销");
        }
        requireSameStation(user, order.getStationId());
        if (ledgerMapper.countByOrder(order.getOrderId(), "RESERVE") <= 0)
        {
            throw new ServiceException("支付成功，库存预占正在确认，请稍后重试");
        }
        if (tokenMapper.markUsed(dynamicToken.getTokenId()) == 0)
        {
            throw new ServiceException("核销码已使用，请让司机刷新");
        }
        if (orderMapper.completeOrder(order.getOrderId(), actualVolume, shift.getShiftId(), user.getUserId()) == 0)
        {
            throw new ServiceException("订单状态已变更，请勿重复核销");
        }
        String operator = user.getUserId() + ":" + shift.getStaffName();
        inventoryService.consume(order.getOrderId(), order.getStationId(), order.getGasType(),
                actualVolume, order.getEstVolume(), operator);
        insertLog(order.getOrderId(), GasOrder.STATUS_PAID, GasOrder.STATUS_COMPLETED,
                "REDEEM", "STAFF", operator, "实际加气量 " + actualVolume + " " + unit(order.getGasType()));
        return orderMapper.selectOrderById(order.getOrderId());
    }

    @Override
    public Map<String, Object> inspectMemberToken(H5User user, String token)
    {
        GasShift shift = requireCurrentOwnShift(user);
        GasMember member = memberFromToken(token);
        GasStation station = stationMapper.selectStationById(user.getStationId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("memberId", member.getMemberId());
        result.put("memberPhone", maskPhone(member.getPhone()));
        result.put("shiftId", shift.getShiftId());
        result.put("stationId", station.getStationId());
        result.put("stationName", station.getStationName());
        result.put("guns", stationGuns(station.getStationId()));
        return result;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GasOrder createProxyOrder(H5User user, String token, Long shiftId, Long gunId, BigDecimal orderAmount)
    {
        GasShift shift = shiftService.requireOwnActiveShift(user, shiftId);
        GasDynamicToken dynamicToken = validToken(token, GasDynamicToken.TYPE_MEMBER);
        GasMember member = memberMapper.selectMemberById(dynamicToken.getRefId());
        requireActiveMember(member);
        validateAmount(orderAmount);

        GasGun gun = gunMapper.selectGunById(gunId);
        if (gun == null || !"0".equals(gun.getStatus()) || !user.getStationId().equals(gun.getStationId()))
        {
            throw new ServiceException("加气枪不存在、已停用或不属于本站");
        }
        GasStation station = stationMapper.selectStationById(gun.getStationId());
        GasGunPrice price = priceMapper.selectCurrentPriceByGunId(gunId);
        if (price == null)
        {
            throw new ServiceException("该枪未设置当前价格，暂不能下单");
        }
        if (inventoryService.selectPoolByStationGas(station.getStationId(), gun.getGasType()) == null)
        {
            throw new ServiceException("该站点暂未开放销售（库存未初始化）");
        }
        if (tokenMapper.markUsed(dynamicToken.getTokenId()) == 0)
        {
            throw new ServiceException("会员码已使用，请让司机刷新");
        }

        GasOrder order = new GasOrder();
        order.setOrderNo(generateOrderNo());
        order.setSource(GasOrder.SOURCE_PROXY);
        order.setStatus(GasOrder.STATUS_WAIT_CONFIRM);
        order.setBuyerId(station.getBuyerId());
        order.setStationId(station.getStationId());
        order.setGunId(gun.getGunId());
        order.setGunNo(gun.getGunNo());
        order.setGasType(gun.getGasType());
        order.setMemberId(member.getMemberId());
        order.setUnitPrice(price.getPrice());
        order.setOrderAmount(orderAmount);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setPayAmount(orderAmount);
        order.setEstVolume(orderAmount.divide(price.getPrice(), 2, RoundingMode.HALF_UP));
        order.setExpireAt(plusMinutes(Integer.parseInt(configService.selectConfigValue("order.waitConfirm.minutes", "10"))));
        order.setCreatedShiftId(shift.getShiftId());
        order.setProxyStaffId(user.getUserId());
        orderMapper.insertOrder(order);
        insertLog(order.getOrderId(), null, GasOrder.STATUS_WAIT_CONFIRM, "CREATE_PROXY",
                "STAFF", user.getUserId() + ":" + shift.getStaffName(), "会员码代下单，等待司机确认");
        return order;
    }

    private GasOrder orderFromRedeemToken(H5User user, String token, boolean forUpdate)
    {
        GasDynamicToken dynamicToken = validToken(token, GasDynamicToken.TYPE_REDEEM);
        GasOrder order = forUpdate ? orderMapper.selectOrderByIdForUpdate(dynamicToken.getRefId())
                : orderMapper.selectOrderById(dynamicToken.getRefId());
        if (order == null || !GasOrder.STATUS_PAID.equals(order.getStatus()))
        {
            throw new ServiceException("订单不存在、未支付或已核销");
        }
        requireSameStation(user, order.getStationId());
        return order;
    }

    private GasMember memberFromToken(String token)
    {
        GasMember member = memberMapper.selectMemberById(validToken(token, GasDynamicToken.TYPE_MEMBER).getRefId());
        requireActiveMember(member);
        return member;
    }

    private GasDynamicToken validToken(String token, String type)
    {
        if (StringUtils.isEmpty(token) || token.trim().length() != 32)
        {
            throw new ServiceException("动态码格式不正确");
        }
        GasDynamicToken dynamicToken = tokenMapper.selectValidByToken(token.trim(), type, new Date());
        if (dynamicToken == null)
        {
            throw new ServiceException("动态码无效、已过期或已使用，请让司机刷新");
        }
        return dynamicToken;
    }

    private GasShift requireCurrentOwnShift(H5User user)
    {
        shiftService.requireStaffUser(user);
        GasShift active = shiftService.selectActiveByStation(user.getStationId());
        if (active == null || !user.getUserId().equals(active.getUserId()))
        {
            throw new ServiceException("请先开始当班；如他人当班需先完成交班");
        }
        return active;
    }

    private void requireSameStation(H5User user, Long stationId)
    {
        if (!user.getStationId().equals(stationId))
        {
            throw new ServiceException("跨站订单不可在本站核销");
        }
    }

    private void requireActiveMember(GasMember member)
    {
        if (member == null || !"0".equals(member.getStatus()) || StringUtils.isEmpty(member.getPhone()))
        {
            throw new ServiceException("会员不存在、未绑手机号或已停用");
        }
    }

    private Map<String, Object> orderView(GasOrder order, Long shiftId)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNo", order.getOrderNo());
        result.put("source", order.getSource());
        result.put("stationName", order.getStationName());
        result.put("gunNo", order.getGunNo());
        result.put("gasType", order.getGasType());
        result.put("memberPhone", maskPhone(order.getMemberPhone()));
        result.put("orderAmount", order.getOrderAmount());
        result.put("payAmount", order.getPayAmount());
        result.put("estVolume", order.getEstVolume());
        result.put("shiftId", shiftId);
        return result;
    }

    private List<Map<String, Object>> stationGuns(Long stationId)
    {
        GasGun query = new GasGun();
        query.setStationId(stationId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (GasGun gun : gunMapper.selectGunList(query))
        {
            if (!"0".equals(gun.getStatus()))
            {
                continue;
            }
            GasGunPrice price = priceMapper.selectCurrentPriceByGunId(gun.getGunId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("gunId", gun.getGunId());
            item.put("gunNo", gun.getGunNo());
            item.put("gasType", gun.getGasType());
            item.put("unitPrice", price == null ? null : price.getPrice());
            item.put("enabled", price != null);
            result.add(item);
        }
        return result;
    }

    private void validateAmount(BigDecimal amount)
    {
        if (amount == null || amount.compareTo(new BigDecimal("0.01")) < 0)
        {
            throw new ServiceException("下单金额至少 0.01 元");
        }
        if (amount.stripTrailingZeros().scale() > 2)
        {
            throw new ServiceException("下单金额最多保留两位小数");
        }
        BigDecimal max = new BigDecimal(configService.selectConfigValue("order.amount.max", "99999"));
        if (amount.compareTo(max) > 0)
        {
            throw new ServiceException("单笔下单金额不能超过 " + max.stripTrailingZeros().toPlainString() + " 元");
        }
    }

    private void requireActualVolume(BigDecimal actualVolume)
    {
        if (actualVolume == null || actualVolume.compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new ServiceException("实际加气量必须大于0");
        }
        if (actualVolume.stripTrailingZeros().scale() > 2)
        {
            throw new ServiceException("实际加气量最多保留两位小数");
        }
    }

    private void insertLog(Long orderId, String from, String to, String action, String type, String operator, String reason)
    {
        GasOrderLog entry = new GasOrderLog();
        entry.setOrderId(orderId);
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setAction(action);
        entry.setOperatorType(type);
        entry.setOperator(operator);
        entry.setReason(reason);
        orderLogMapper.insertLog(entry);
    }

    private static String maskPhone(String phone)
    {
        if (phone == null || phone.length() < 7)
        {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static String unit(String gasType)
    {
        return "CNG".equals(gasType) ? "m³" : "kg";
    }

    private static String displayName(SysUser user)
    {
        return StringUtils.isNotEmpty(user.getUserName()) ? user.getUserName() : user.getLoginName();
    }

    private static String generateOrderNo()
    {
        return "PO" + com.ruoyi.common.utils.DateUtils.dateTimeNow("yyyyMMddHHmmss")
                + String.format("%04d", new java.security.SecureRandom().nextInt(10000));
    }

    private static Date plusMinutes(int minutes)
    {
        return Date.from(LocalDateTime.now().plusMinutes(minutes).atZone(ZoneId.systemDefault()).toInstant());
    }
}
