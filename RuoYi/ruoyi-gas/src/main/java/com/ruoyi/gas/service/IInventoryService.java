package com.ruoyi.gas.service;

import java.math.BigDecimal;
import java.util.List;
import com.ruoyi.gas.domain.GasInventoryLedger;
import com.ruoyi.gas.domain.GasInventoryPool;
import com.ruoyi.gas.domain.GasPurchaseReceipt;

/**
 * 库存服务（Dev_Plan §3.3）
 *
 * <p>站点库存池 + 到货批次 + 流水：所有库存变动只能通过本服务完成
 * （入库 / 预占 / 释放 / 先进先出实扣 / 调整）。</p>
 *
 * <p>并发控制：每次操作先 SELECT ... FOR UPDATE 锁定站点池行（同站同气种串行化），
 * FIFO 扣减时再按 received_at, receipt_id 顺序锁定有余量的到货批次。</p>
 *
 * @author gas
 */
public interface IInventoryService
{
    /** 流水类型（gas_ledger_type 字典值） */
    String LEDGER_IN = "IN";
    String LEDGER_RESERVE = "RESERVE";
    String LEDGER_RELEASE = "RELEASE";
    String LEDGER_CONSUME = "CONSUME";
    String LEDGER_ADJUST = "ADJUST";

    /**
     * 入库：新增到货记录 + 池累计入库 + IN 流水
     *
     * @param receipt 到货信息（batchId、qty、receivedAt 必填；buyer/station/gasType 取自批次）
     * @return 含 receiptId 的到货记录
     */
    GasPurchaseReceipt receive(GasPurchaseReceipt receipt);

    /**
     * 预占（订单支付成功后按预计气量预占）
     *
     * @param orderId 订单ID（幂等：同一订单重复预占直接成功返回）
     * @param qty 预占量
     */
    void reserve(Long orderId, Long stationId, String gasType, BigDecimal qty, String operator);

    /**
     * 释放预占（订单关闭/全额退款）
     *
     * @param orderId 订单ID（幂等：同一订单重复释放直接成功返回）
     * @param qty 释放量
     */
    void release(Long orderId, Long stationId, String gasType, BigDecimal qty, String operator);

    /**
     * 先进先出实扣（核销）：按到货时间顺序扣减批次，同时释放该订单全部预占
     *
     * @param orderId 订单ID（重复核销抛业务异常）
     * @param actualQty 加气员确认的实际量（可大于预占量，只要池可用量足够）
     * @param reservedQty 该订单此前的预占量
     */
    void consume(Long orderId, Long stationId, String gasType, BigDecimal actualQty, BigDecimal reservedQty, String operator);

    /**
     * 人工调整：必须指定到货批次并填写原因；调整池与批次的累计调整量（可负）
     */
    void adjust(Long stationId, String gasType, Long receiptId, BigDecimal qty, String reason, String operator);

    /** 库存池列表（台账页） */
    List<GasInventoryPool> selectPoolList(GasInventoryPool pool);

    GasInventoryPool selectPoolById(Long poolId);

    GasInventoryPool selectPoolByStationGas(Long stationId, String gasType);

    /** 维护余量预警值（null=关闭预警） */
    int updateAlertThreshold(Long poolId, BigDecimal threshold);

    /** 某站某气种的到货批次余量（台账页批次视图） */
    List<GasPurchaseReceipt> selectReceiptListOfPool(Long stationId, String gasType);

    /** 库存流水查询（台账页流水视图） */
    List<GasInventoryLedger> selectLedgerList(GasInventoryLedger ledger);
}
