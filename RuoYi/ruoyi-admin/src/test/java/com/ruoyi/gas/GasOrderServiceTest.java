package com.ruoyi.gas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.gas.domain.GasBuyer;
import com.ruoyi.gas.domain.GasDynamicToken;
import com.ruoyi.gas.domain.GasGun;
import com.ruoyi.gas.domain.GasGunPrice;
import com.ruoyi.gas.domain.GasOrder;
import com.ruoyi.gas.domain.GasPayTxn;
import com.ruoyi.gas.domain.GasStation;
import com.ruoyi.gas.h5.H5User;
import com.ruoyi.gas.mapper.GasBuyerMapper;
import com.ruoyi.gas.mapper.GasBuyerMerchantMapper;
import com.ruoyi.gas.mapper.GasDynamicTokenMapper;
import com.ruoyi.gas.mapper.GasGunMapper;
import com.ruoyi.gas.mapper.GasGunPriceMapper;
import com.ruoyi.gas.mapper.GasMemberMapper;
import com.ruoyi.gas.mapper.GasOrderLogMapper;
import com.ruoyi.gas.mapper.GasOrderMapper;
import com.ruoyi.gas.mapper.GasPayTxnMapper;
import com.ruoyi.gas.mapper.GasStationMapper;
import com.ruoyi.gas.service.IH5OrderService;
import com.ruoyi.gas.service.IInventoryService;

/**
 * H5 订单服务 JUnit 集成测试（Dev_Plan 阶段5）
 *
 * <ul>
 *   <li>下单 → 支付回调 → PAID + 库存预占，回调重放幂等</li>
 *   <li>并发回调：双线程同时通知，仅一次预占/一次状态迁移</li>
 *   <li>超时关单与关单后迟到支付（标记待退款）</li>
 *   <li>动态码：30 秒窗口复用、未支付订单无核销码</li>
 * </ul>
 *
 * <p>事务策略：常规用例走测试事务自动回滚；
 * 并发用例子线程用 TransactionTemplate 独立提交，AfterEach 手工清理。</p>
 *
 * @author gas
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.quartz.auto-startup=false", "gas.h5.mock-mode=true" })
class GasOrderServiceTest
{
    private static final String STATION_CODE = "JUNIT-ORD";
    private static final BigDecimal GUN_PRICE = new BigDecimal("3.5000");

    @Autowired
    private IH5OrderService orderService;

    @Autowired
    private IInventoryService inventoryService;

    @Autowired
    private com.ruoyi.gas.service.IGasCouponService couponService;

    @Autowired
    private GasBuyerMapper buyerMapper;

    @Autowired
    private GasStationMapper stationMapper;

    @Autowired
    private GasGunMapper gunMapper;

    @Autowired
    private GasGunPriceMapper priceMapper;

    @Autowired
    private GasBuyerMerchantMapper merchantMapper;

    @Autowired
    private GasMemberMapper memberMapper;

    @Autowired
    private GasOrderMapper orderMapper;

    @Autowired
    private GasOrderLogMapper orderLogMapper;

    @Autowired
    private GasPayTxnMapper txnMapper;

    @Autowired
    private GasDynamicTokenMapper tokenMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    /** 准备：采购商 + 站点 + 枪 + 枪价 + 商户 + 会员 + 库存池（withPool=false 模拟未开卖站点） */
    private Long[] prepare(String tag, boolean withPool)
    {
        GasBuyer buyer = new GasBuyer();
        buyer.setBuyerName("JUnit订单采购商" + tag);
        buyer.setStatus("0");
        buyerMapper.insertBuyer(buyer);

        jdbcTemplate.update("insert into sys_dept (parent_id, ancestors, dept_name, order_num, status, del_flag, create_by, create_time) "
                + "values (100, '0,100', 'JUnit订单部门" + tag + "', 99, '0', '0', 'junit', sysdate())");
        Long deptId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);

        GasStation station = new GasStation();
        station.setStationCode(STATION_CODE + tag);
        station.setStationName("JUnit订单站点" + tag);
        station.setBuyerId(buyer.getBuyerId());
        station.setDeptId(deptId);
        station.setQrToken("qr-" + tag);
        station.setStatus("0");
        stationMapper.insertStation(station);

        GasGun gun = new GasGun();
        gun.setStationId(station.getStationId());
        gun.setGunNo(1);
        gun.setGasType("CNG");
        gun.setStatus("0");
        gunMapper.insertGun(gun);

        GasGunPrice price = new GasGunPrice();
        price.setGunId(gun.getGunId());
        price.setPrice(GUN_PRICE);
        // 前移 1 分钟：避开 datetime 秒精度四舍五入把生效时间推到未来的边界
        price.setEffectiveFrom(new Date(System.currentTimeMillis() - 60_000));
        priceMapper.insertPrice(price);

        // 收款商户（采购商维度）
        jdbcTemplate.update("insert into gas_buyer_merchant (buyer_id, channel, merchant_no, status, create_by, create_time) "
                + "values (?, 'LAKALA', 'MK" + tag + "', '0', 'junit', sysdate())", buyer.getBuyerId());

        // 会员（已绑手机号）
        jdbcTemplate.update("insert into gas_member (openid, phone, phone_bound_at, status, create_time) "
                + "values (?, '1390000" + tag + "', sysdate(), '0', sysdate())", "MOCK-ORD-" + tag);
        Long memberId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);

        // 库存池：1000 可用（保供原则下预占可压负，但正常路径给足量；available 为计算值不落库）
        if (withPool)
        {
            jdbcTemplate.update("insert into gas_inventory_pool (station_id, buyer_id, gas_type, total_in, adjusted, consumed, reserved, update_time) "
                    + "values (?, ?, 'CNG', 1000, 0, 0, 0, sysdate())", station.getStationId(), buyer.getBuyerId());
        }

        return new Long[] { buyer.getBuyerId(), station.getStationId(), gun.getGunId(), memberId };
    }

    private H5User member(Long memberId)
    {
        return H5User.member("MOCK-ORD", memberId, true);
    }

    private Long newTemplate(Long buyerId, String tag, String faceValue, String threshold, int totalQty)
    {
        jdbcTemplate.update("insert into gas_coupon_template(buyer_id,name,face_value,threshold_amount,valid_type,valid_days,total_qty,status,create_by,create_time) "
                + "values(?,?,'" + faceValue + "','" + threshold + "','DAYS',30,?,'0','junit',sysdate())",
                buyerId, "JUnit券" + tag, totalQty);
        return jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
    }

    private com.ruoyi.gas.domain.GasCoupon issueCoupon(Long[] ids, String tag, String faceValue, String threshold, int totalQty)
    {
        Long templateId = newTemplate(ids[0], tag, faceValue, threshold, totalQty);
        return couponService.issue(templateId, ids[3], null, "junit");
    }

    /**
     * 用例1：下单 → 支付 → 回调 → PAID + 预占；回调重放幂等
     */
    @Test
    @Transactional
    void testCreatePayNotifyFlow()
    {
        Long[] ids = prepare("A", true);
        Long gunId = ids[2];
        H5User user = member(ids[3]);

        // 扫码进站
        Map<String, Object> entry = orderService.stationEntry("qr-A");
        assertEquals("JUNIT-ORDA", entry.get("stationCode"));
        List<?> guns = (List<?>) entry.get("guns");
        assertEquals(1, guns.size());
        assertEquals(0, GUN_PRICE.compareTo((BigDecimal) ((Map<?, ?>) guns.get(0)).get("unitPrice")));

        // 试算：35 元 ÷ 3.5 = 10 立方
        Map<String, Object> preview = orderService.preview(gunId, new BigDecimal("35.00"));
        assertEquals(0, new BigDecimal("10.00").compareTo((BigDecimal) preview.get("estVolume")));
        assertEquals(0, new BigDecimal("35.00").compareTo((BigDecimal) preview.get("payAmount")));

        // 创建订单
        GasOrder order = orderService.createOrder(user, gunId, new BigDecimal("35.00"));
        assertNotNull(order.getOrderId());
        assertTrue(order.getOrderNo().startsWith("SO"));
        assertEquals(GasOrder.STATUS_WAIT_PAY, order.getStatus());
        assertEquals(0, new BigDecimal("10.00").compareTo(order.getEstVolume()));

        // 发起支付（mock 网关）
        Map<String, Object> pay = orderService.pay(user, order.getOrderNo());
        assertNotNull(pay.get("payUrl"));
        assertTrue(String.valueOf(pay.get("payUrl")).contains("/api/pay/mock/cashier"));
        GasPayTxn txn = txnMapper.selectPayingByOrderId(order.getOrderId());
        assertNotNull(txn, "支付流水已创建");

        // 支付回调：PAID + 预占
        String answer = orderService.handleNotify(txn.getMerchantId(), txn.getOutTradeNo(), "CH1", "WECHAT", "35.00", "{}");
        assertEquals("success", answer);
        GasOrder paid = orderMapper.selectOrderByNo(order.getOrderNo());
        assertEquals(GasOrder.STATUS_PAID, paid.getStatus());
        assertNotNull(paid.getPaidAt());
        com.ruoyi.gas.domain.GasInventoryPool pool = inventoryService.selectPoolByStationGas(ids[1], "CNG");
        assertEquals(0, new BigDecimal("10.00").compareTo(pool.getReserved()), "预占 = 预计气量");

        // 回调重放：幂等
        assertEquals("success", orderService.handleNotify(txn.getMerchantId(), txn.getOutTradeNo(), "CH1", "WECHAT", "35.00", "{}"));
        pool = inventoryService.selectPoolByStationGas(ids[1], "CNG");
        assertEquals(0, new BigDecimal("10.00").compareTo(pool.getReserved()), "重放不产生二次预占");

        // 他人订单不可见
        H5User other = member(ids[3] + 999999);
        assertThrows(ServiceException.class, () -> orderService.myOrder(other, order.getOrderNo()));
    }

    /**
     * 用例2：并发回调——双线程同时通知同一交易，仅一次预占/一次状态迁移
     */
    @Test
    void testConcurrentNotify()
    {
        final String tag = "C";
        final Long[] ids = transactionTemplate.execute(status -> prepare(tag, true));
        H5User user = member(ids[3]);

        // 下单 + 发起支付（独立事务提交，子线程可见）
        GasOrder order = transactionTemplate.execute(status ->
                orderService.createOrder(user, ids[2], new BigDecimal("35.00")));
        GasPayTxn txn = transactionTemplate.execute(status -> {
            orderService.pay(user, order.getOrderNo());
            return txnMapper.selectPayingByOrderId(order.getOrderId());
        });

        // 并发回调
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger();
        for (int i = 0; i < threads; i++)
        {
            pool.submit(() -> {
                ready.countDown();
                try
                {
                    ready.await();
                    String answer = transactionTemplate.execute(status ->
                            orderService.handleNotify(txn.getMerchantId(), txn.getOutTradeNo(), "CH-C1", "WECHAT", "35.00", "{}"));
                    if ("success".equals(answer))
                    {
                        successCount.incrementAndGet();
                    }
                }
                catch (Exception ignored)
                {
                }
                finally
                {
                    done.countDown();
                }
            });
        }
        try
        {
            assertTrue(done.await(15, TimeUnit.SECONDS));
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        pool.shutdownNow();

        assertEquals(threads, successCount.get(), "两个并发回调都应成功应答（幂等）");
        GasOrder paid = orderMapper.selectOrderByNo(order.getOrderNo());
        assertEquals(GasOrder.STATUS_PAID, paid.getStatus());
        com.ruoyi.gas.domain.GasInventoryPool invPool = inventoryService.selectPoolByStationGas(ids[1], "CNG");
        assertEquals(0, new BigDecimal("10.00").compareTo(invPool.getReserved()), "仅一次预占");
        Integer payLogs = jdbcTemplate.queryForObject(
                "select count(1) from gas_order_log where order_id = ? and action = 'PAY_NOTIFY'",
                Integer.class, order.getOrderId());
        assertEquals(1, payLogs.intValue(), "仅一次状态迁移日志");
    }

    /**
     * 用例3：超时关单；关单后迟到支付 → 标记待退款
     */
    @Test
    @Transactional
    void testTimeoutCloseAndLatePay()
    {
        Long[] ids = prepare("B", true);
        H5User user = member(ids[3]);
        GasOrder order = orderService.createOrder(user, ids[2], new BigDecimal("35.00"));
        orderService.pay(user, order.getOrderNo());
        GasPayTxn txn = txnMapper.selectPayingByOrderId(order.getOrderId());

        // 推到过期
        jdbcTemplate.update("update gas_order set expire_at = date_sub(now(), interval 1 minute) where order_id = ?",
                order.getOrderId());

        int closed = orderService.closeExpiredOrders();
        assertTrue(closed >= 1);
        GasOrder closedOrder = orderMapper.selectOrderByNo(order.getOrderNo());
        assertEquals(GasOrder.STATUS_CLOSED, closedOrder.getStatus());
        assertNotNull(closedOrder.getClosedAt());
        GasPayTxn closedTxn = txnMapper.selectByOutTradeNo(txn.getOutTradeNo());
        assertEquals(GasPayTxn.STATUS_CLOSED, closedTxn.getStatus());

        // 迟到支付：订单保持 CLOSED，流水 SUCCESS，标记待退款，无预占
        assertEquals("success", orderService.handleNotify(txn.getMerchantId(), txn.getOutTradeNo(), "CH-LATE", "WECHAT", "35.00", "{}"));
        GasOrder lateOrder = orderMapper.selectOrderByNo(order.getOrderNo());
        assertEquals(GasOrder.STATUS_CLOSED, lateOrder.getStatus());
        assertTrue(lateOrder.getExceptionNote().contains("待退款"));
        assertEquals(GasPayTxn.STATUS_SUCCESS, txnMapper.selectByOutTradeNo(txn.getOutTradeNo()).getStatus());
        com.ruoyi.gas.domain.GasInventoryPool pool = inventoryService.selectPoolByStationGas(ids[1], "CNG");
        assertEquals(0, BigDecimal.ZERO.compareTo(pool.getReserved()), "关单订单支付迟到不预占");
    }

    /** 并发发起支付：订单行锁保证同一订单只创建一条 PAYING 流水 */
    @Test
    void testConcurrentPayCreatesSingleTxn() throws Exception
    {
        final String tag = "P";
        Long[] ids = transactionTemplate.execute(status -> prepare(tag, true));
        H5User user = member(ids[3]);
        GasOrder order = transactionTemplate.execute(status ->
                orderService.createOrder(user, ids[2], new BigDecimal("35.00")));

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        for (int i = 0; i < threads; i++)
        {
            executor.submit(() -> {
                ready.countDown();
                try
                {
                    ready.await();
                    transactionTemplate.executeWithoutResult(status -> orderService.pay(user, order.getOrderNo()));
                    success.incrementAndGet();
                }
                catch (Exception ignored)
                {
                }
                finally
                {
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(15, TimeUnit.SECONDS), "并发支付发起超时");
        executor.shutdownNow();
        assertEquals(threads, success.get(), "两个请求复用同一支付流水，均应成功");
        assertEquals(1, txnMapper.selectTxnsByOrderId(order.getOrderId()).size(), "只能有一条支付流水");
    }

    /**
     * 用例4：动态码——PAID 订单核销码 30 秒窗口复用；会员码；未支付订单拒绝
     */
    @Test
    @Transactional
    void testDynamicTokens()
    {
        Long[] ids = prepare("D", true);
        H5User user = member(ids[3]);

        // 未支付订单无核销码
        GasOrder order = orderService.createOrder(user, ids[2], new BigDecimal("7.00"));
        assertThrows(ServiceException.class, () -> orderService.redeemToken(user, order.getOrderNo()));

        // 支付
        orderService.pay(user, order.getOrderNo());
        GasPayTxn txn = txnMapper.selectPayingByOrderId(order.getOrderId());
        orderService.handleNotify(txn.getMerchantId(), txn.getOutTradeNo(), "CH2", "WECHAT", "7.00", "{}");

        // 核销码：30 秒窗口内两次获取返回同码
        GasDynamicToken t1 = orderService.redeemToken(user, order.getOrderNo());
        assertEquals(32, t1.getToken().length());
        GasDynamicToken t2 = orderService.redeemToken(user, order.getOrderNo());
        assertEquals(t1.getToken(), t2.getToken(), "刷新窗口内复用同码");

        // 会员码
        GasDynamicToken memberToken = orderService.memberToken(user);
        assertEquals(GasDynamicToken.TYPE_MEMBER, memberToken.getTokenType());
        assertEquals(user.getMemberId(), memberToken.getRefId());

        // 核销码可按码反查（阶段6核销入口用）
        GasDynamicToken found = tokenMapper.selectValidByToken(t1.getToken(), GasDynamicToken.TYPE_REDEEM, new Date());
        assertNotNull(found);
    }

    /**
     * 用例5：回调金额不一致拒绝；未初始化库存池的站点禁止下单
     */
    @Test
    @Transactional
    void testAmountMismatchAndNoPool()
    {
        Long[] ids = prepare("E", true);
        H5User user = member(ids[3]);
        assertThrows(ServiceException.class,
                () -> orderService.createOrder(user, ids[2], new BigDecimal("1.001")), "金额最多两位小数");
        GasOrder order = orderService.createOrder(user, ids[2], new BigDecimal("35.00"));
        orderService.pay(user, order.getOrderNo());
        GasPayTxn txn = txnMapper.selectPayingByOrderId(order.getOrderId());

        assertEquals("fail: merchant mismatch", orderService.handleNotify(txn.getMerchantId() + 1,
                txn.getOutTradeNo(), "CH-E1", "WECHAT", "35.00", "{}"));
        assertEquals("fail: amount required", orderService.handleNotify(txn.getMerchantId(),
                txn.getOutTradeNo(), "CH-E1", "WECHAT", null, "{}"));

        // 金额不一致：拒绝且订单/流水状态不变
        assertEquals("fail: amount mismatch",
                orderService.handleNotify(txn.getMerchantId(), txn.getOutTradeNo(), "CH-E1", "WECHAT", "0.01", "{}"));
        assertEquals(GasOrder.STATUS_WAIT_PAY, orderMapper.selectOrderByNo(order.getOrderNo()).getStatus());
        assertEquals(GasPayTxn.STATUS_PAYING, txnMapper.selectByOutTradeNo(txn.getOutTradeNo()).getStatus());

        // 正确金额仍可支付成功（金额错误回调不产生副作用）
        assertEquals("success",
                orderService.handleNotify(txn.getMerchantId(), txn.getOutTradeNo(), "CH-E1", "WECHAT", "35.00", "{}"));
        assertEquals(GasOrder.STATUS_PAID, orderMapper.selectOrderByNo(order.getOrderNo()).getStatus());

        // 未初始化库存池的站点：下单被拒（避免支付成功后预占失败）
        Long[] noPool = prepare("F", false);
        H5User userF = member(noPool[3]);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> orderService.createOrder(userF, noPool[2], new BigDecimal("10.00")));
        assertTrue(ex.getMessage().contains("暂未开放销售"));
    }

    @Test
    @Transactional
    void testCouponSelfPayFlow()
    {
        Long[] ids = prepare("K", true);
        H5User user = member(ids[3]);
        com.ruoyi.gas.domain.GasCoupon coupon = issueCoupon(ids, "K", "5.00", "20.00", 1);
        Map<String, Object> preview = orderService.preview(user, ids[2], new BigDecimal("35.00"), coupon.getCouponId());
        assertEquals(0, new BigDecimal("5.00").compareTo((BigDecimal) preview.get("discountAmount")));
        assertEquals(0, new BigDecimal("30.00").compareTo((BigDecimal) preview.get("payAmount")));
        assertEquals(0, new BigDecimal("10.00").compareTo((BigDecimal) preview.get("estVolume")), "优惠不改变原价气量");
        assertEquals(1, ((List<?>) preview.get("coupons")).size());

        GasOrder order = orderService.createOrder(user, ids[2], new BigDecimal("35.00"), coupon.getCouponId());
        assertEquals(coupon.getCouponId(), order.getCouponId());
        assertEquals(0, new BigDecimal("30.00").compareTo(order.getPayAmount()));
        assertEquals("USED", jdbcTemplate.queryForObject("select status from gas_coupon where coupon_id=?", String.class, coupon.getCouponId()));
        orderService.pay(user, order.getOrderNo());
        GasPayTxn txn = txnMapper.selectPayingByOrderId(order.getOrderId());
        assertEquals(0, new BigDecimal("30.00").compareTo(txn.getAmount()), "支付流水使用折后金额");
        assertEquals("success", orderService.handleNotify(txn.getMerchantId(), txn.getOutTradeNo(), "CH-K", "WECHAT", "30.00", "{}"));
        assertEquals(GasOrder.STATUS_PAID, orderMapper.selectOrderById(order.getOrderId()).getStatus());
        Integer completed = jdbcTemplate.queryForObject("select count(1) from gas_coupon c join gas_order o on c.used_order_id=o.order_id where c.coupon_id=? and o.status='COMPLETED'", Integer.class, coupon.getCouponId());
        assertEquals(0, completed, "待核销锁券不计入已核销统计");
    }

    @Test
    @Transactional
    void testCouponCloseReleaseAndExpiry()
    {
        Long[] ids = prepare("L", true);
        H5User user = member(ids[3]);
        com.ruoyi.gas.domain.GasCoupon coupon = issueCoupon(ids, "L", "5.00", "20.00", 2);
        GasOrder order = orderService.createOrder(user, ids[2], new BigDecimal("35.00"), coupon.getCouponId());
        jdbcTemplate.update("update gas_order set expire_at=date_sub(now(),interval 1 minute) where order_id=?", order.getOrderId());
        assertTrue(orderService.closeExpiredOrders() >= 1);
        Map<String, Object> released = jdbcTemplate.queryForMap("select status,used_order_id from gas_coupon where coupon_id=?", coupon.getCouponId());
        assertEquals("UNUSED", released.get("status"));
        assertEquals(null, released.get("used_order_id"));
        orderService.closeExpiredOrders();
        Map<String, Object> releasedAgain = jdbcTemplate.queryForMap("select status,used_order_id from gas_coupon where coupon_id=?", coupon.getCouponId());
        assertEquals("UNUSED", releasedAgain.get("status"));
        assertEquals(null, releasedAgain.get("used_order_id"));
        assertEquals(1, jdbcTemplate.queryForObject("select count(1) from gas_order_log where order_id=? and action='TIMEOUT_CLOSE'", Integer.class, order.getOrderId()));

        com.ruoyi.gas.domain.GasCoupon expired = couponService.issue(coupon.getTemplateId(), ids[3], null, "junit");
        GasOrder second = orderService.createOrder(user, ids[2], new BigDecimal("35.00"), expired.getCouponId());
        jdbcTemplate.update("update gas_order set expire_at=date_sub(now(),interval 1 minute) where order_id=?", second.getOrderId());
        jdbcTemplate.update("update gas_coupon set expire_at=date_sub(now(),interval 1 minute) where coupon_id=?", expired.getCouponId());
        orderService.closeExpiredOrders();
        assertEquals("EXPIRED", jdbcTemplate.queryForObject("select status from gas_coupon where coupon_id=?", String.class, expired.getCouponId()));
    }

    @Test
    @Transactional
    void testCouponOwnershipThresholdAndIssueLimit()
    {
        Long[] ids = prepare("M", true);
        com.ruoyi.gas.domain.GasCoupon coupon = issueCoupon(ids, "M", "5.00", "20.00", 1);
        jdbcTemplate.update("insert into gas_member(openid,phone,phone_bound_at,status,create_time) values('MOCK-ORD-OTHER','13800000000',sysdate(),'0',sysdate())");
        Long otherMember = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        assertThrows(ServiceException.class, () -> orderService.createOrder(member(otherMember), ids[2], new BigDecimal("35.00"), coupon.getCouponId()));
        assertThrows(ServiceException.class, () -> orderService.createOrder(member(ids[3]), ids[2], new BigDecimal("10.00"), coupon.getCouponId()));
        Long[] otherBuyer = prepare("N", true);
        assertThrows(ServiceException.class, () -> orderService.createOrder(member(ids[3]), otherBuyer[2], new BigDecimal("35.00"), coupon.getCouponId()));
        jdbcTemplate.update("update gas_coupon set expire_at=date_sub(now(),interval 1 second) where coupon_id=?", coupon.getCouponId());
        assertThrows(ServiceException.class, () -> orderService.createOrder(member(ids[3]), ids[2], new BigDecimal("35.00"), coupon.getCouponId()));

        Long blank = newTemplate(ids[0], "M-blank", "5.00", "0.00", 10);
        assertThrows(ServiceException.class, () -> couponService.issue(blank, null, "   ", "junit"));
        assertEquals(0, jdbcTemplate.queryForObject("select count(1) from gas_coupon where template_id=?", Integer.class, blank));
        assertEquals(0, jdbcTemplate.queryForObject("select issued_qty from gas_coupon_template where template_id=?", Integer.class, blank));

        Long limited = newTemplate(ids[0], "M-limit", "5.00", "0.00", 1);
        couponService.issue(limited, ids[3], null, "junit");
        assertThrows(ServiceException.class, () -> couponService.issue(limited, ids[3], null, "junit"));
        Long fixed = newTemplate(ids[0], "M-fixed", "5.00", "0.00", 1);
        jdbcTemplate.update("update gas_coupon_template set valid_type='FIXED',valid_days=null,valid_from=date_add(now(),interval 1 day),valid_to=date_add(now(),interval 2 day) where template_id=?", fixed);
        assertThrows(ServiceException.class, () -> couponService.issue(fixed, ids[3], null, "junit"));
    }

    @Test
    void testConcurrentCouponOccupyAndIssueLimit() throws Exception
    {
        Long[] ids = transactionTemplate.execute(status -> prepare("Q", true));
        com.ruoyi.gas.domain.GasCoupon coupon = transactionTemplate.execute(status -> issueCoupon(ids, "Q", "5.00", "20.00", 1));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        for (int i = 0; i < 2; i++)
        {
            executor.submit(() -> {
                ready.countDown();
                try
                {
                    ready.await();
                    transactionTemplate.executeWithoutResult(status -> orderService.createOrder(member(ids[3]), ids[2], new BigDecimal("35.00"), coupon.getCouponId()));
                    success.incrementAndGet();
                }
                catch (Exception ignored) { }
                finally { done.countDown(); }
            });
        }
        assertTrue(done.await(15, TimeUnit.SECONDS), "并发抢券超时");
        executor.shutdownNow();
        assertEquals(1, success.get(), "同一券只能有一个订单成功");
        assertEquals(1, jdbcTemplate.queryForObject("select count(1) from gas_order where coupon_id=?", Integer.class, coupon.getCouponId()));

        Long templateId = transactionTemplate.execute(status -> newTemplate(ids[0], "Q-issue", "5.00", "0.00", 2));
        ExecutorService issueExecutor = Executors.newFixedThreadPool(3);
        CountDownLatch issueDone = new CountDownLatch(3);
        AtomicInteger issued = new AtomicInteger();
        for (int i = 0; i < 3; i++)
        {
            issueExecutor.submit(() -> {
                try
                {
                    transactionTemplate.executeWithoutResult(status -> couponService.issue(templateId, ids[3], null, "junit"));
                    issued.incrementAndGet();
                }
                catch (Exception ignored) { }
                finally { issueDone.countDown(); }
            });
        }
        assertTrue(issueDone.await(15, TimeUnit.SECONDS), "并发发券超时");
        issueExecutor.shutdownNow();
        assertEquals(2, issued.get(), "三个请求竞争两张额度，只能成功两次");
        assertEquals(2, jdbcTemplate.queryForObject("select issued_qty from gas_coupon_template where template_id=?", Integer.class, templateId));
        assertEquals(2, jdbcTemplate.queryForObject("select count(1) from gas_coupon where template_id=?", Integer.class, templateId));
    }

    @Test
    @Transactional
    void testProxyConfirmWithCoupon()
    {
        Long[] ids = prepare("R", true);
        H5User user = member(ids[3]);
        com.ruoyi.gas.domain.GasCoupon coupon = issueCoupon(ids, "R", "5.00", "20.00", 1);
        GasOrder created = orderService.createOrder(user, ids[2], new BigDecimal("35.00"));
        jdbcTemplate.update("update gas_order set source='PROXY',status='WAIT_CONFIRM' where order_id=?", created.getOrderId());
        GasOrder confirmed = orderService.confirmProxyOrder(user, created.getOrderNo(), coupon.getCouponId());
        assertEquals(GasOrder.STATUS_WAIT_PAY, confirmed.getStatus());
        assertEquals(coupon.getCouponId(), confirmed.getCouponId());
        assertEquals(0, new BigDecimal("30.00").compareTo(confirmed.getPayAmount()));
        assertEquals("USED", jdbcTemplate.queryForObject("select status from gas_coupon where coupon_id=?", String.class, coupon.getCouponId()));
    }

    @Test
    @Transactional
    void testSelfOrderRequestIdempotency()
    {
        Long[] ids = prepare("S", true);
        H5User user = member(ids[3]);
        GasOrder first = orderService.createOrder(user, ids[2], new BigDecimal("35.00"), null, "junit-req-20261117");
        GasOrder retry = orderService.createOrder(user, ids[2], new BigDecimal("35.00"), null, "junit-req-20261117");
        assertEquals(first.getOrderId(), retry.getOrderId(), "相同请求号重试返回首单");
        assertEquals(1, jdbcTemplate.queryForObject("select count(1) from gas_order where request_id=?", Integer.class, "junit-req-20261117"));
        assertThrows(ServiceException.class, () -> orderService.createOrder(user, ids[2], new BigDecimal("36.00"), null, "junit-req-20261117"));
    }

    /** 清理并发用例数据（按站点编码标记；回滚用例无残留） */
    @AfterEach
    void cleanup()
    {
        String stationIds = "(select station_id from gas_station where station_code like 'JUNIT-ORD%')";
        String orderIds = "(select order_id from gas_order where station_id in " + stationIds + ")";
        jdbcTemplate.update("delete from gas_dynamic_token where token_type = 'REDEEM' and ref_id in " + orderIds);
        jdbcTemplate.update("delete from gas_dynamic_token where token_type='MEMBER' and ref_id in (select member_id from gas_member where openid like 'MOCK-ORD-%')");
        jdbcTemplate.update("delete c from gas_coupon c join gas_coupon_template t on c.template_id=t.template_id "
                + "join gas_station s on t.buyer_id=s.buyer_id where s.station_code like 'JUNIT-ORD%'");
        jdbcTemplate.update("delete t from gas_coupon_template t join gas_station s on t.buyer_id=s.buyer_id "
                + "where s.station_code like 'JUNIT-ORD%'");
        jdbcTemplate.update("delete from gas_order_log where order_id in " + orderIds);
        jdbcTemplate.update("delete from gas_pay_txn where order_id in " + orderIds);
        jdbcTemplate.update("delete from gas_order where station_id in " + stationIds);
        jdbcTemplate.update("delete from gas_inventory_ledger where pool_id in "
                + "(select pool_id from gas_inventory_pool where station_id in " + stationIds + ")");
        jdbcTemplate.update("delete from gas_inventory_pool where station_id in " + stationIds);
        jdbcTemplate.update("delete m from gas_buyer_merchant m join gas_station s on m.buyer_id=s.buyer_id "
                + "where s.station_code like 'JUNIT-ORD%'");
        jdbcTemplate.update("delete from gas_gun_price where gun_id in "
                + "(select gun_id from gas_gun where station_id in " + stationIds + ")");
        jdbcTemplate.update("delete from gas_gun where station_id in " + stationIds);
        jdbcTemplate.update("delete from sys_dept where dept_name like 'JUnit订单部门%'");
        jdbcTemplate.update("delete from gas_station where station_code like 'JUNIT-ORD%'");
        jdbcTemplate.update("delete from gas_member where openid like 'MOCK-ORD-%'");
        jdbcTemplate.update("delete from gas_buyer where buyer_name like 'JUnit订单采购商%'");
    }
}
