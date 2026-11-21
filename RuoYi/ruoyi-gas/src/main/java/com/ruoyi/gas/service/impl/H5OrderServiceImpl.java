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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.gas.domain.*;
import com.ruoyi.gas.h5.H5User;
import com.ruoyi.gas.mapper.*;
import com.ruoyi.gas.pay.IPayGateway;
import com.ruoyi.gas.pay.PayGatewayRouter;
import com.ruoyi.gas.service.IGasConfigService;
import com.ruoyi.gas.service.IH5OrderService;
import com.ruoyi.gas.service.IInventoryService;

/** H5 订单服务：主动下单、代下单确认、支付、超时关单和动态码 */
@Service
public class H5OrderServiceImpl implements IH5OrderService
{
    private static final Logger log = LoggerFactory.getLogger(H5OrderServiceImpl.class);
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();
    @Autowired private GasStationMapper stationMapper;
    @Autowired private GasGunMapper gunMapper;
    @Autowired private GasGunPriceMapper priceMapper;
    @Autowired private GasMemberMapper memberMapper;
    @Autowired private GasBuyerMerchantMapper merchantMapper;
    @Autowired private GasOrderMapper orderMapper;
    @Autowired private GasOrderLogMapper orderLogMapper;
    @Autowired private GasPayTxnMapper txnMapper;
    @Autowired private GasDynamicTokenMapper tokenMapper;
    @Autowired private IInventoryService inventoryService;
    @Autowired private PayGatewayRouter gatewayRouter;
    @Autowired private IGasConfigService configService;
    @Autowired private com.ruoyi.gas.service.IGasCouponService couponService;
    @Autowired private TransactionTemplate transactionTemplate;
    private final Object[] payLocks = new Object[256];

    public H5OrderServiceImpl()
    {
        for (int i = 0; i < payLocks.length; i++) { payLocks[i] = new Object(); }
    }

    @Override
    public Map<String, Object> stationEntry(String qrCode)
    {
        GasStation station = stationMapper.selectStationByQrToken(qrCode);
        if (station == null) { throw new ServiceException("站点码无效或站点已停用"); }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stationId", station.getStationId());
        result.put("stationCode", station.getStationCode());
        result.put("stationName", station.getStationName());
        result.put("buyerId", station.getBuyerId());
        GasGun query = new GasGun();
        query.setStationId(station.getStationId());
        List<Map<String, Object>> guns = new ArrayList<>();
        for (GasGun gun : gunMapper.selectGunList(query))
        {
            if (!"0".equals(gun.getStatus())) { continue; }
            GasGunPrice price = priceMapper.selectCurrentPriceByGunId(gun.getGunId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("gunId", gun.getGunId());
            item.put("gunNo", gun.getGunNo());
            item.put("gasType", gun.getGasType());
            item.put("unitPrice", price == null ? null : price.getPrice());
            guns.add(item);
        }
        result.put("guns", guns);
        return result;
    }

    @Override
    public Map<String, Object> preview(Long gunId, BigDecimal orderAmount)
    {
        return previewInternal(null, gunId, orderAmount, null);
    }

    @Override
    public Map<String, Object> preview(H5User user, Long gunId, BigDecimal orderAmount, Long couponId)
    {
        requireActiveMember(user);
        return previewInternal(user, gunId, orderAmount, couponId);
    }

    private Map<String, Object> previewInternal(H5User user, Long gunId, BigDecimal orderAmount, Long couponId)
    {
        GasGunPrice price = requireCurrentPrice(gunId);
        validateAmount(orderAmount);
        GasStation station = stationMapper.selectStationById(price.getStationId());
        if (station == null || !"0".equals(station.getStatus())) { throw new ServiceException("站点已停用，无法试算"); }
        List<GasCoupon> coupons = user == null ? List.of()
                : couponService.available(user.getMemberId(), station.getBuyerId(), orderAmount);
        GasCoupon selected = null;
        if (couponId != null)
        {
            Long selectedId = couponId;
            selected = coupons.stream().filter(c -> selectedId.equals(c.getCouponId())).findFirst()
                    .orElseThrow(() -> new ServiceException("优惠券不可用、未达门槛或折后金额须至少0.01元"));
        }
        BigDecimal discount = selected == null ? BigDecimal.ZERO : selected.getFaceValue();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gunId", price.getGunId());
        result.put("gunNo", price.getGunNo());
        result.put("gasType", price.getGasType());
        result.put("unitPrice", price.getPrice());
        result.put("orderAmount", orderAmount);
        result.put("estVolume", orderAmount.divide(price.getPrice(), 2, RoundingMode.HALF_UP));
        result.put("discountAmount", discount);
        result.put("payAmount", orderAmount.subtract(discount));
        result.put("couponId", selected == null ? null : selected.getCouponId());
        result.put("coupons", coupons);
        return result;
    }

    @Override
    @Transactional
    public GasOrder createOrder(H5User user, Long gunId, BigDecimal orderAmount)
    {
        return createOrder(user, gunId, orderAmount, null);
    }

    @Override
    @Transactional
    public GasOrder createOrder(H5User user, Long gunId, BigDecimal orderAmount, Long couponId)
    {
        return createOrder(user, gunId, orderAmount, couponId, null);
    }

    @Override
    @Transactional
    public GasOrder createOrder(H5User user, Long gunId, BigDecimal orderAmount, Long couponId, String requestId)
    {
        requireActiveMember(user);
        validateAmount(orderAmount);
        String normalizedRequestId = requestId == null || requestId.trim().isEmpty() ? null : requestId.trim();
        if (normalizedRequestId != null && !normalizedRequestId.matches("[A-Za-z0-9_-]{8,64}"))
            throw new ServiceException("下单请求号格式错误");
        if (normalizedRequestId != null)
        {
            memberMapper.selectMemberByIdForUpdate(user.getMemberId());
            GasOrder existing = orderMapper.selectOrderByRequestIdForUpdate(user.getMemberId(), normalizedRequestId);
            if (existing != null)
            {
                if (!existing.getGunId().equals(gunId) || existing.getOrderAmount().compareTo(orderAmount) != 0
                        || !java.util.Objects.equals(existing.getCouponId(), couponId))
                    throw new ServiceException("下单请求号已用于其他订单");
                return existing;
            }
        }
        GasGunPrice price = requireCurrentPrice(gunId);
        GasGun gun = gunMapper.selectGunById(gunId);
        GasStation station = stationMapper.selectStationById(gun.getStationId());
        if (station == null || !"0".equals(station.getStatus())) { throw new ServiceException("站点已停用，无法下单"); }
        if (inventoryService.selectPoolByStationGas(station.getStationId(), gun.getGasType()) == null)
        {
            throw new ServiceException("该站点暂未开放销售（库存未初始化）");
        }
        GasCoupon coupon = couponService.lockUsable(couponId, user.getMemberId(), station.getBuyerId(), orderAmount);
        GasOrder order = newOrder(user.getMemberId(), gun, station, price, orderAmount);
        order.setRequestId(normalizedRequestId);
        applyCoupon(order, coupon);
        order.setStatus(GasOrder.STATUS_WAIT_PAY);
        order.setExpireAt(plusMinutes(payTimeoutMinutes()));
        orderMapper.insertOrder(order);
        if (coupon != null && couponService.occupy(coupon.getCouponId(), order.getOrderId()) != 1)
        {
            throw new ServiceException("优惠券已被使用，请重新选择");
        }
        insertLog(order.getOrderId(), null, order.getStatus(), "CREATE", "MEMBER", order.getOrderNo(), null);
        return order;
    }

    private void applyCoupon(GasOrder order, GasCoupon coupon)
    {
        order.setCouponId(coupon == null ? null : coupon.getCouponId());
        order.setDiscountAmount(coupon == null ? BigDecimal.ZERO : coupon.getFaceValue());
        order.setPayAmount(order.getOrderAmount().subtract(order.getDiscountAmount()));
    }

    private GasOrder newOrder(Long memberId, GasGun gun, GasStation station, GasGunPrice price, BigDecimal amount)
    {
        GasOrder order = new GasOrder();
        order.setOrderNo(generateOrderNo());
        order.setSource(GasOrder.SOURCE_SELF);
        order.setBuyerId(station.getBuyerId());
        order.setStationId(station.getStationId());
        order.setGunId(gun.getGunId());
        order.setGunNo(gun.getGunNo());
        order.setGasType(gun.getGasType());
        order.setMemberId(memberId);
        order.setUnitPrice(price.getPrice());
        order.setOrderAmount(amount);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setPayAmount(amount);
        order.setEstVolume(amount.divide(price.getPrice(), 2, RoundingMode.HALF_UP));
        return order;
    }

    @Override
    public List<GasOrder> myOrders(H5User user, int page, int size)
    {
        requireActiveMember(user);
        int safePage = Math.min(Math.max(page, 1), 1_000_000);
        int safeSize = Math.min(Math.max(size, 1), 50);
        return orderMapper.selectMemberOrders(user.getMemberId(), (safePage - 1) * safeSize, safeSize);
    }

    @Override
    public int countMyOrders(H5User user)
    {
        requireActiveMember(user);
        return orderMapper.countMemberOrders(user.getMemberId());
    }

    @Override
    public GasOrder myOrder(H5User user, String orderNo)
    {
        requireActiveMember(user);
        return requireOwnedOrder(user, orderNo);
    }

    @Override
    @Transactional
    public GasOrder confirmProxyOrder(H5User user, String orderNo)
    {
        return confirmProxyOrder(user, orderNo, null);
    }

    @Override
    @Transactional
    public GasOrder confirmProxyOrder(H5User user, String orderNo, Long couponId)
    {
        requireActiveMember(user);
        GasOrder order = orderMapper.selectOrderByNoForUpdate(orderNo);
        requireOrderOwner(user, order);
        if (!GasOrder.STATUS_WAIT_CONFIRM.equals(order.getStatus())) { throw new ServiceException("订单当前状态无需确认"); }
        if (order.getExpireAt() != null && order.getExpireAt().before(new Date()))
        {
            throw new ServiceException("代下单确认已超时，请让加气员重新下单");
        }
        GasCoupon coupon = couponService.lockUsable(couponId, user.getMemberId(), order.getBuyerId(), order.getOrderAmount());
        if (coupon != null)
        {
            applyCoupon(order, coupon);
            if (orderMapper.updateOrderCoupon(order) != 1 || couponService.occupy(couponId, order.getOrderId()) != 1)
            {
                throw new ServiceException("优惠券或订单状态已变更，请刷新重试");
            }
        }
        if (orderMapper.confirmProxyOrder(order.getOrderId(), plusMinutes(payTimeoutMinutes())) == 0)
        {
            throw new ServiceException("订单状态已变更，请刷新");
        }
        insertLog(order.getOrderId(), GasOrder.STATUS_WAIT_CONFIRM, GasOrder.STATUS_WAIT_PAY,
                "DRIVER_CONFIRM", "MEMBER", order.getOrderNo(),
                coupon == null ? "司机确认代下单" : "司机确认代下单，使用优惠券 " + coupon.getCouponId());
        return orderMapper.selectOrderById(order.getOrderId());
    }

    @Override
    @Transactional
    public GasOrder rejectProxyOrder(H5User user, String orderNo)
    {
        requireActiveMember(user);
        GasOrder order = orderMapper.selectOrderByNoForUpdate(orderNo);
        requireOrderOwner(user, order);
        if (!GasOrder.STATUS_WAIT_CONFIRM.equals(order.getStatus())) { throw new ServiceException("订单当前状态不可拒绝"); }
        String reason = "司机拒绝代下单";
        if (orderMapper.closeWaitConfirm(order.getOrderId(), reason) == 0) { throw new ServiceException("订单状态已变更，请刷新"); }
        couponService.release(order.getCouponId(), order.getOrderId());
        insertLog(order.getOrderId(), GasOrder.STATUS_WAIT_CONFIRM, GasOrder.STATUS_CLOSED,
                "DRIVER_REJECT", "MEMBER", order.getOrderNo(), reason);
        return orderMapper.selectOrderById(order.getOrderId());
    }

    @Override
    public Map<String, Object> pay(H5User user, String orderNo)
    {
        PayLaunchRequest request;
        synchronized (payLocks[Math.floorMod(orderNo.hashCode(), payLocks.length)])
        {
            request = transactionTemplate.execute(status -> {
                requireActiveMember(user);
                GasOrder order = orderMapper.selectOrderByNoForUpdate(orderNo);
                requireOrderOwner(user, order);
                if (!GasOrder.STATUS_WAIT_PAY.equals(order.getStatus())) { throw new ServiceException("订单当前状态不可支付"); }
                if (order.getExpireAt() != null && order.getExpireAt().before(new Date())) { throw new ServiceException("订单已超时，请重新下单"); }
                GasBuyerMerchant merchant = merchantMapper.selectMerchantByBuyerId(order.getBuyerId());
                if (merchant == null || !"0".equals(merchant.getStatus())) { throw new ServiceException("站点收款商户未配置或已停用，请联系站点"); }
                GasPayTxn txn = txnMapper.selectPayingByOrderId(order.getOrderId());
                if (txn == null)
                {
                    txn = new GasPayTxn();
                    txn.setOrderId(order.getOrderId());
                    txn.setMerchantId(merchant.getMerchantId());
                    txn.setOutTradeNo(order.getOrderNo() + "T" + System.currentTimeMillis());
                    txn.setAmount(order.getPayAmount());
                    txnMapper.insertTxn(txn);
                }
                return new PayLaunchRequest(order, merchant, txn);
            });
        }
        IPayGateway.PayLaunch launch = gatewayRouter.route(request.merchant).launch(request.txn, request.merchant, request.order);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNo", request.order.getOrderNo());
        result.put("payAmount", request.order.getPayAmount());
        result.put("payUrl", launch.getPayUrl());
        result.put("payParams", launch.getPayParams());
        return result;
    }

    @Override
    public String handleNotify(Long merchantId, String outTradeNo, String channelTradeNo, String payChannel, String amount, String rawNotify)
    {
        GasPayTxn txn = txnMapper.selectByOutTradeNo(outTradeNo);
        if (txn == null) { log.warn("支付回调：交易号不存在 outTradeNo={}", outTradeNo); return "fail: txn not found"; }
        if (merchantId == null || !merchantId.equals(txn.getMerchantId()))
        {
            log.error("支付回调商户不一致 outTradeNo={} pathMerchant={} txnMerchant={}", outTradeNo, merchantId, txn.getMerchantId());
            return "fail: merchant mismatch";
        }
        if (StringUtils.isEmpty(amount)) { return "fail: amount required"; }
        try
        {
            if (txn.getAmount().compareTo(new BigDecimal(amount)) != 0)
            {
                log.error("支付回调金额不一致 outTradeNo={} expect={} actual={}", outTradeNo, txn.getAmount(), amount);
                return "fail: amount mismatch";
            }
        }
        catch (NumberFormatException e) { return "fail: bad amount format"; }
        NotifyResult result = transactionTemplate.execute(status -> processNotify(txn, channelTradeNo, payChannel, rawNotify));
        if (result.reserveOrder != null)
        {
            GasOrder order = result.reserveOrder;
            try
            {
                GasOrder current = orderMapper.selectOrderById(order.getOrderId());
                if (current == null || !GasOrder.STATUS_PAID.equals(current.getStatus()))
                {
                    log.warn("订单状态已变化，跳过库存预占 orderNo={} status={}", order.getOrderNo(), current == null ? null : current.getStatus());
                    return result.answer;
                }
                inventoryService.reserve(order.getOrderId(), order.getStationId(), order.getGasType(), order.getEstVolume(), order.getOrderNo());
            }
            catch (Exception e)
            {
                log.error("支付成功但库存预占失败 orderNo={}", order.getOrderNo(), e);
                orderMapper.updateExceptionNote(order.getOrderId(), "支付成功但库存预占失败，请人工核查");
            }
        }
        return result.answer;
    }

    private NotifyResult processNotify(GasPayTxn txn, String channelTradeNo, String payChannel, String rawNotify)
    {
        // 与支付发起、超时关单统一 gas_order → gas_pay_txn 加锁顺序，避免死锁
        GasOrder order = orderMapper.selectOrderByIdForUpdate(txn.getOrderId());
        if (order == null) { log.error("支付回调：流水对应订单不存在 txnId={} orderId={}", txn.getTxnId(), txn.getOrderId()); return new NotifyResult("fail: order not found", null); }
        int updated = txnMapper.markSuccess(txn.getTxnId(), channelTradeNo, payChannel, rawNotify);
        if (updated == 0) { log.info("支付回调幂等命中 outTradeNo={} status={}", txn.getOutTradeNo(), txn.getStatus()); return new NotifyResult("success", null); }
        for (GasPayTxn other : txnMapper.selectTxnsByOrderId(order.getOrderId()))
        {
            if (!other.getTxnId().equals(txn.getTxnId()) && GasPayTxn.STATUS_SUCCESS.equals(other.getStatus()))
            {
                orderMapper.updateExceptionNote(order.getOrderId(), "检测到同一订单重复收款，待退款/对账处理");
                insertLog(order.getOrderId(), order.getStatus(), order.getStatus(), "DUPLICATE_PAY_NOTIFY", "SYSTEM", channelTradeNo, "同一订单第二笔支付成功，待退款/对账");
                log.error("重复收款：orderNo={} txnId={}", order.getOrderNo(), txn.getTxnId());
                return new NotifyResult("success", null);
            }
        }
        if (GasOrder.STATUS_WAIT_PAY.equals(order.getStatus()))
        {
            if (orderMapper.markPaid(order.getOrderId(), txn.getMerchantId(), payChannel) == 0)
            {
                markLatePaid(order, channelTradeNo);
                return new NotifyResult("success", null);
            }
            insertLog(order.getOrderId(), GasOrder.STATUS_WAIT_PAY, GasOrder.STATUS_PAID, "PAY_NOTIFY", "SYSTEM", channelTradeNo, "支付成功，预占库存 " + order.getEstVolume());
            log.info("支付成功：orderNo={} amount={} txn={}", order.getOrderNo(), txn.getAmount(), txn.getOutTradeNo());
            return new NotifyResult("success", order);
        }
        if (GasOrder.STATUS_CLOSED.equals(order.getStatus()))
        {
            markLatePaid(order, channelTradeNo);
            return new NotifyResult("success", null);
        }
        log.info("支付回调：订单状态无变化 orderNo={} status={}", order.getOrderNo(), order.getStatus());
        return new NotifyResult("success", null);
    }

    @Override
    @Transactional
    public int closeExpiredOrders()
    {
        Date now = new Date();
        int closed = 0;
        for (GasOrder order : orderMapper.selectExpiredWaitPay(now))
        {
            if (orderMapper.closeOrder(order.getOrderId(), "待支付超时自动关闭") == 0) { continue; }
            couponService.release(order.getCouponId(), order.getOrderId());
            txnMapper.closePayingByOrderId(order.getOrderId());
            insertLog(order.getOrderId(), GasOrder.STATUS_WAIT_PAY, GasOrder.STATUS_CLOSED, "TIMEOUT_CLOSE", "SYSTEM", "system", "待支付超时自动关闭");
            closed++;
        }
        for (GasOrder order : orderMapper.selectExpiredWaitConfirm(now))
        {
            String reason = "代下单待司机确认超时自动关闭";
            if (orderMapper.closeWaitConfirm(order.getOrderId(), reason) == 0) { continue; }
            insertLog(order.getOrderId(), GasOrder.STATUS_WAIT_CONFIRM, GasOrder.STATUS_CLOSED, "TIMEOUT_CLOSE", "SYSTEM", "system", reason);
            closed++;
        }
        if (closed > 0) { log.info("超时关单：本次关闭 {} 单", closed); }
        return closed;
    }

    @Override
    public GasDynamicToken redeemToken(H5User user, String orderNo)
    {
        requireActiveMember(user);
        GasOrder order = requireOwnedOrder(user, orderNo);
        if (!GasOrder.STATUS_PAID.equals(order.getStatus())) { throw new ServiceException("订单未支付，无核销码"); }
        return currentToken(GasDynamicToken.TYPE_REDEEM, order.getOrderId());
    }

    @Override
    public GasDynamicToken memberToken(H5User user)
    {
        requireActiveMember(user);
        return currentToken(GasDynamicToken.TYPE_MEMBER, user.getMemberId());
    }

    private static final class PayLaunchRequest
    {
        private final GasOrder order;
        private final GasBuyerMerchant merchant;
        private final GasPayTxn txn;
        private PayLaunchRequest(GasOrder order, GasBuyerMerchant merchant, GasPayTxn txn) { this.order = order; this.merchant = merchant; this.txn = txn; }
    }

    private static final class NotifyResult
    {
        private final String answer;
        private final GasOrder reserveOrder;
        private NotifyResult(String answer, GasOrder reserveOrder) { this.answer = answer; this.reserveOrder = reserveOrder; }
    }

    private GasDynamicToken currentToken(String tokenType, Long refId)
    {
        GasDynamicToken reusable = tokenMapper.selectReusable(tokenType, refId, minusSeconds(30));
        if (reusable != null) { return reusable; }
        GasDynamicToken token = new GasDynamicToken();
        token.setTokenType(tokenType);
        token.setRefId(refId);
        token.setToken(IdUtils.fastSimpleUUID());
        token.setExpireAt(plusSeconds(60));
        tokenMapper.insertToken(token);
        return token;
    }

    private void markLatePaid(GasOrder order, String channelTradeNo)
    {
        orderMapper.updateExceptionNote(order.getOrderId(), "订单已关闭后收到支付成功回调（" + channelTradeNo + "），待退款处理");
        insertLog(order.getOrderId(), GasOrder.STATUS_CLOSED, GasOrder.STATUS_CLOSED, "LATE_PAY_NOTIFY", "SYSTEM", channelTradeNo, "关单后支付成功，待退款（阶段8）");
        log.warn("迟到支付：orderNo={} txn={} 已标记待退款", order.getOrderNo(), channelTradeNo);
    }

    private void requireActiveMember(H5User user)
    {
        if (!user.isMember()) { throw new ServiceException("该操作仅限消费者会员"); }
        GasMember member = memberMapper.selectMemberById(user.getMemberId());
        if (member == null || !"0".equals(member.getStatus())) { throw new ServiceException("会员不存在或已停用"); }
        if (StringUtils.isEmpty(member.getPhone())) { throw new ServiceException("请先绑定手机号"); }
    }

    private GasOrder requireOwnedOrder(H5User user, String orderNo)
    {
        GasOrder order = orderMapper.selectOrderByNo(orderNo);
        requireOrderOwner(user, order);
        return order;
    }

    private void requireOrderOwner(H5User user, GasOrder order)
    {
        if (!user.isMember() || user.getMemberId() == null || order == null || !user.getMemberId().equals(order.getMemberId()))
        {
            throw new ServiceException("订单不存在");
        }
    }

    private GasGunPrice requireCurrentPrice(Long gunId)
    {
        GasGun gun = gunMapper.selectGunById(gunId);
        if (gun == null || !"0".equals(gun.getStatus())) { throw new ServiceException("加气枪不存在或已停用"); }
        GasGunPrice price = priceMapper.selectCurrentPriceByGunId(gunId);
        if (price == null) { throw new ServiceException("该枪未设置当前价格，暂不能下单"); }
        return price;
    }

    private void validateAmount(BigDecimal orderAmount)
    {
        if (orderAmount == null || orderAmount.compareTo(new BigDecimal("0.01")) < 0) { throw new ServiceException("下单金额至少 0.01 元"); }
        if (orderAmount.stripTrailingZeros().scale() > 2) { throw new ServiceException("下单金额最多保留两位小数"); }
        BigDecimal max = new BigDecimal(configService.selectConfigValue("order.amount.max", "99999"));
        if (orderAmount.compareTo(max) > 0) { throw new ServiceException("单笔下单金额不能超过 " + max.stripTrailingZeros().toPlainString() + " 元"); }
    }

    private int payTimeoutMinutes() { return Integer.parseInt(configService.selectConfigValue("order.waitPay.minutes", "15")); }

    private void insertLog(Long orderId, String fromStatus, String toStatus, String action, String operatorType, String operator, String reason)
    {
        GasOrderLog entry = new GasOrderLog();
        entry.setOrderId(orderId);
        entry.setFromStatus(fromStatus);
        entry.setToStatus(toStatus);
        entry.setAction(action);
        entry.setOperatorType(operatorType);
        entry.setOperator(operator);
        entry.setReason(reason);
        orderLogMapper.insertLog(entry);
    }

    private static String generateOrderNo() { return "SO" + com.ruoyi.common.utils.DateUtils.dateTimeNow("yyyyMMddHHmmss") + String.format("%04d", RANDOM.nextInt(10000)); }
    private static Date plusMinutes(int minutes) { return Date.from(LocalDateTime.now().plusMinutes(minutes).atZone(ZoneId.systemDefault()).toInstant()); }
    private static Date plusSeconds(int seconds) { return Date.from(LocalDateTime.now().plusSeconds(seconds).atZone(ZoneId.systemDefault()).toInstant()); }
    private static Date minusSeconds(int seconds) { return Date.from(LocalDateTime.now().minusSeconds(seconds).atZone(ZoneId.systemDefault()).toInstant()); }
}
