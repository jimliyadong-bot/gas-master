package com.ruoyi.gas.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.gas.domain.GasOrder;

/**
 * 销售订单 数据层
 *
 * @author gas
 */
public interface GasOrderMapper
{
    public GasOrder selectOrderById(Long orderId);

    /** 核销/司机确认时锁定订单 */
    public GasOrder selectOrderByIdForUpdate(Long orderId);

    public GasOrder selectOrderByNo(String orderNo);

    /** 发起支付时锁定订单，串行化支付流水创建 */
    public GasOrder selectOrderByNoForUpdate(String orderNo);

    /** 主动下单幂等请求当前读 */
    public GasOrder selectOrderByRequestIdForUpdate(@Param("memberId") Long memberId, @Param("requestId") String requestId);

    /** 会员订单列表（H5 我的订单） */
    public List<GasOrder> selectMemberOrders(@Param("memberId") Long memberId,
            @Param("offset") int offset, @Param("limit") int limit);

    /** 会员订单总数（H5 分页） */
    public int countMemberOrders(@Param("memberId") Long memberId);

    /** 关单（条件更新：WAIT_PAY 不匹配则 0 行；写 closed_at 与原因） */
    public int closeOrder(@Param("orderId") Long orderId, @Param("closeReason") String closeReason);

    /** 支付成功回填（仅 WAIT_PAY → PAID） */
    public int markPaid(@Param("orderId") Long orderId, @Param("merchantId") Long merchantId,
            @Param("payChannel") String payChannel);

    /** 阶段6：司机确认、拒绝、超时、核销条件更新 */
    public int confirmProxyOrder(@Param("orderId") Long orderId, @Param("expireAt") java.util.Date expireAt);

    /** 司机确认时保存优惠券与折后金额 */
    public int updateOrderCoupon(GasOrder order);

    public int closeWaitConfirm(@Param("orderId") Long orderId, @Param("closeReason") String closeReason);

    public int completeOrder(@Param("orderId") Long orderId, @Param("actualVolume") java.math.BigDecimal actualVolume,
            @Param("shiftId") Long shiftId, @Param("staffId") Long staffId);

    public java.util.List<GasOrder> selectExpiredWaitConfirm(@Param("now") java.util.Date now);

    /** 待支付超时订单（关单任务） */
    public List<GasOrder> selectExpiredWaitPay(@Param("now") java.util.Date now);

    public int insertOrder(GasOrder order);

    /** 站点/枪基础资料删除与归属变更守卫 */
    public int countByStationId(Long stationId);

    public int countByGunIds(Long[] gunIds);

    /** 核销异常/迟到支付标记 */
    public int updateExceptionNote(@Param("orderId") Long orderId, @Param("exceptionNote") String exceptionNote);

    /** 阶段6核销用：查汇总（预留） */
    public List<Map<String, Object>> selectShiftSummary(@Param("shiftId") Long shiftId);
}
