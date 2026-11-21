package com.ruoyi.gas.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.ruoyi.gas.domain.GasDynamicToken;
import com.ruoyi.gas.domain.GasOrder;
import com.ruoyi.gas.h5.H5User;

/**
 * H5 订单服务（Dev_Plan 阶段5：主动下单与支付）
 *
 * @author gas
 */
public interface IH5OrderService
{
    /** 扫码进站：站点信息 + 在线枪列表（含当前价） */
    Map<String, Object> stationEntry(String qrCode);

    /** 无券试算（保留给既有服务测试） */
    Map<String, Object> preview(Long gunId, BigDecimal orderAmount);

    /** 阶段7：会员试算、可用券列表及选券后的折后金额 */
    Map<String, Object> preview(H5User user, Long gunId, BigDecimal orderAmount, Long couponId);

    /** 创建无券订单（WAIT_PAY，15 分钟支付时限） */
    GasOrder createOrder(H5User user, Long gunId, BigDecimal orderAmount);

    /** 创建订单并可选锁一张本人优惠券 */
    GasOrder createOrder(H5User user, Long gunId, BigDecimal orderAmount, Long couponId);

    /** 带客户端请求号的主动下单幂等入口 */
    GasOrder createOrder(H5User user, Long gunId, BigDecimal orderAmount, Long couponId, String requestId);

    /** 我的订单列表 */
    List<GasOrder> myOrders(H5User user, int page, int size);

    /** 我的订单总数 */
    int countMyOrders(H5User user);

    /** 订单详情（归属校验） */
    GasOrder myOrder(H5User user, String orderNo);

    /** 无券确认代下单（保留原签名） */
    GasOrder confirmProxyOrder(H5User user, String orderNo);

    /** 阶段7：司机确认时可选锁一张本人优惠券 */
    GasOrder confirmProxyOrder(H5User user, String orderNo, Long couponId);

    GasOrder rejectProxyOrder(H5User user, String orderNo);

    /** 发起支付：按站点采购商选商户，返回支付入口（复用未完成流水） */
    Map<String, Object> pay(H5User user, String orderNo);

    /**
     * 支付回调处理（幂等：txn 条件更新 + 订单条件更新 + reserve 幂等）
     *
     * @param merchantId 回调路径商户，必须与流水商户一致
     * @param amount 渠道侧金额（必填，必须与流水一致）
     * @return 应答给渠道的报文（"success" 表示已受理）
     */
    String handleNotify(Long merchantId, String outTradeNo, String channelTradeNo, String payChannel, String amount, String rawNotify);

    /** 超时关单任务：WAIT_PAY 且 expire_at 已过 → CLOSED（PAYING 流水联动关闭） */
    int closeExpiredOrders();

    /** 订单核销码（PAID 订单，30 秒刷新窗口复用，60 秒有效） */
    GasDynamicToken redeemToken(H5User user, String orderNo);

    /** 会员码（核销入口反查会员，同刷新规则） */
    GasDynamicToken memberToken(H5User user);
}
