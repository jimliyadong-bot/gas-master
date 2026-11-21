package com.ruoyi.gas.service.impl;

import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.gas.domain.GasInventoryLedger;
import com.ruoyi.gas.domain.GasInventoryPool;
import com.ruoyi.gas.domain.GasPurchaseBatch;
import com.ruoyi.gas.domain.GasPurchaseReceipt;
import com.ruoyi.gas.mapper.GasInventoryLedgerMapper;
import com.ruoyi.gas.mapper.GasInventoryPoolMapper;
import com.ruoyi.gas.mapper.GasPurchaseBatchMapper;
import com.ruoyi.gas.mapper.GasPurchaseReceiptMapper;
import com.ruoyi.gas.service.IGasRemindService;
import com.ruoyi.gas.service.IInventoryService;

/**
 * 库存服务实现（Dev_Plan §3.3）
 *
 * <p>站点池 + 到货批次 + 流水三层数据在一次事务内同步变更；
 * 池行 FOR UPDATE 锁保证同站同气种操作串行；流水表唯一键兜底订单动作幂等。</p>
 *
 * @author gas
 */
@Service
public class InventoryServiceImpl implements IInventoryService
{
    private static final Logger log = LoggerFactory.getLogger(InventoryServiceImpl.class);

    @Autowired
    private GasInventoryPoolMapper poolMapper;

    @Autowired
    private GasInventoryLedgerMapper ledgerMapper;

    @Autowired
    private GasPurchaseReceiptMapper receiptMapper;

    @Autowired
    private GasPurchaseBatchMapper batchMapper;

    @Autowired
    private IGasRemindService remindService;

    @Override
    @Transactional
    public GasPurchaseReceipt receive(GasPurchaseReceipt receipt)
    {
        requirePositiveQty(receipt.getQty(), "到货数量");
        GasPurchaseBatch batch = batchMapper.selectBatchByIdForUpdate(receipt.getBatchId());
        if (StringUtils.isNull(batch))
        {
            throw new ServiceException("采购批次不存在或已被删除");
        }
        receipt.setBuyerId(batch.getBuyerId());
        receipt.setStationId(batch.getStationId());
        receipt.setGasType(batch.getGasType());

        if (receipt.getReceivedAt() == null) { throw new ServiceException("到货时间不能为空"); }
        // 锁顺序：采购批次 → 库存池 → 到货记录，与核销的池→到货顺序一致。
        GasInventoryPool pool = lockOrCreatePool(batch.getStationId(), batch.getGasType(), batch.getBuyerId());
        // 方案A：累计到货毛量不能超过采购量；销售消耗／调整不恢复到货额度。
        // 当前读避免事务先前查询产生的RR旧快照漏掉刚提交的另一笔到货。
        BigDecimal received = receiptMapper.sumReceivedQtyForUpdate(receipt.getBatchId());
        if (batch.getQty() == null || received.add(receipt.getQty()).compareTo(batch.getQty()) > 0)
        {
            throw new ServiceException("累计到货不能超过采购数量，超出部分请另建采购批次");
        }
        receiptMapper.insertReceipt(receipt);
        poolMapper.addTotalIn(pool.getPoolId(), receipt.getQty());
        insertLedger(pool.getPoolId(), receipt.getReceiptId(), 0L, LEDGER_IN, receipt.getQty(), null, receipt.getCreateBy());
        return receipt;
    }

    @Override
    @Transactional
    public void reserve(Long orderId, Long stationId, String gasType, BigDecimal qty, String operator)
    {
        requirePositiveQty(qty, "预占数量");
        GasInventoryPool pool = requirePoolForUpdate(stationId, gasType);
        // 幂等：支付回调重放时直接视为成功
        if (ledgerMapper.countByOrder(orderId, LEDGER_RESERVE) > 0)
        {
            return;
        }
        try
        {
            // 先写幂等流水，唯一键冲突时不得再改池，保证池始终能由流水重算
            insertLedger(pool.getPoolId(), 0L, orderId, LEDGER_RESERVE, qty, null, operator);
        }
        catch (DuplicateKeyException e)
        {
            return;
        }
        // 保供原则：支付成功订单必须预占，可用量允许压负，由运营补货消化
        poolMapper.addReserved(pool.getPoolId(), qty);
    }

    @Override
    @Transactional
    public void release(Long orderId, Long stationId, String gasType, BigDecimal qty, String operator)
    {
        requirePositiveQty(qty, "释放数量");
        GasInventoryPool pool = requirePoolForUpdate(stationId, gasType);
        if (ledgerMapper.countByOrder(orderId, LEDGER_RELEASE) > 0)
        {
            return;
        }
        // 订单维度校验：有效预占 = RESERVE - RELEASE - CONSUME，防止误释放其他订单的预占
        BigDecimal orderReserved = ledgerMapper.sumOrderReserved(orderId, pool.getPoolId());
        if (orderReserved.compareTo(qty) < 0)
        {
            throw new ServiceException(String.format("该订单有效预占(%s)不足以释放(%s)", orderReserved, qty));
        }
        try
        {
            insertLedger(pool.getPoolId(), 0L, orderId, LEDGER_RELEASE, qty, null, operator);
        }
        catch (DuplicateKeyException e)
        {
            return;
        }
        poolMapper.addReserved(pool.getPoolId(), qty.negate());
    }

    @Override
    @Transactional
    public void consume(Long orderId, Long stationId, String gasType, BigDecimal actualQty, BigDecimal reservedQty, String operator)
    {
        requirePositiveQty(actualQty, "实际加气量");
        GasInventoryPool pool = requirePoolForUpdate(stationId, gasType);
        if (ledgerMapper.countByOrder(orderId, LEDGER_CONSUME) > 0)
        {
            throw new ServiceException("订单已核销，请勿重复操作");
        }
        // 预占量只能由本订单流水计算，绝不信任调用方传值
        BigDecimal reservedQtySafe = ledgerMapper.sumOrderReserved(orderId, pool.getPoolId()).max(BigDecimal.ZERO);
        // 超出自身预占的部分需要池可用量支持
        BigDecimal extra = actualQty.subtract(reservedQtySafe);
        if (extra.compareTo(BigDecimal.ZERO) > 0)
        {
            checkAvailable(pool, extra);
        }

        // 先进先出：锁定有余量的到货批次，按 received_at, receipt_id 依次扣减
        List<GasPurchaseReceipt> fifo = receiptMapper.selectFifoForUpdate(stationId, gasType);
        BigDecimal need = actualQty;
        for (GasPurchaseReceipt r : fifo)
        {
            if (need.compareTo(BigDecimal.ZERO) <= 0)
            {
                break;
            }
            BigDecimal take = r.getRemain().min(need);
            receiptMapper.addConsumed(r.getReceiptId(), take);
            insertLedger(pool.getPoolId(), r.getReceiptId(), orderId, LEDGER_CONSUME, take, null, operator);
            need = need.subtract(take);
        }
        if (need.compareTo(BigDecimal.ZERO) > 0)
        {
            throw new ServiceException("到货批次余量不足，无法核销");
        }

        poolMapper.addConsumed(pool.getPoolId(), actualQty);
        poolMapper.addReserved(pool.getPoolId(), reservedQtySafe.negate());
        // 剩余预占随核销释放（实际量小于预占量时）
        BigDecimal rest = reservedQtySafe.subtract(actualQty);
        if (rest.compareTo(BigDecimal.ZERO) > 0)
        {
            insertLedger(pool.getPoolId(), 0L, orderId, LEDGER_RELEASE, rest, null, operator);
        }
        scheduleReminders(stationId, gasType, "核销");
    }

    @Override
    @Transactional
    public void adjust(Long stationId, String gasType, Long receiptId, BigDecimal qty, String reason, String operator)
    {
        if (StringUtils.isNull(qty) || qty.compareTo(BigDecimal.ZERO) == 0)
        {
            throw new ServiceException("调整数量不能为0");
        }
        if (StringUtils.isEmpty(reason))
        {
            throw new ServiceException("调整原因不能为空");
        }
        GasInventoryPool pool = requirePoolForUpdate(stationId, gasType);
        GasPurchaseReceipt receipt = receiptMapper.selectReceiptByIdForUpdate(receiptId);
        if (StringUtils.isNull(receipt) || receipt.getStationId().longValue() != stationId.longValue()
                || !gasType.equals(receipt.getGasType()))
        {
            throw new ServiceException("到货批次不存在或不属于该站点气种");
        }
        if (qty.stripTrailingZeros().scale() > 2)
        {
            throw new ServiceException("调整数量最多保留两位小数");
        }
        // 负调整不能超过批次剩余量
        if (qty.compareTo(BigDecimal.ZERO) < 0 && receipt.getRemain().compareTo(qty.negate()) < 0)
        {
            throw new ServiceException(String.format("调整量(%s)不能超过批次剩余量(%s)", qty, receipt.getRemain()));
        }
        BigDecimal physicalAfter = pool.getTotalIn().add(pool.getAdjusted()).add(qty).subtract(pool.getConsumed());
        if (physicalAfter.compareTo(BigDecimal.ZERO) < 0 || physicalAfter.compareTo(pool.getReserved()) < 0)
        {
            throw new ServiceException("调整后实物库存不能为负，且必须覆盖已支付订单的有效预占");
        }
        receiptMapper.addAdjusted(receiptId, qty);
        poolMapper.addAdjusted(pool.getPoolId(), qty);
        insertLedger(pool.getPoolId(), receiptId, 0L, LEDGER_ADJUST, qty, reason, operator);
        scheduleReminders(stationId, gasType, "调整");
    }

    @Override
    public List<GasInventoryPool> selectPoolList(GasInventoryPool pool)
    {
        return poolMapper.selectPoolList(pool);
    }

    @Override
    public GasInventoryPool selectPoolById(Long poolId)
    {
        return poolMapper.selectPoolById(poolId);
    }

    @Override
    public GasInventoryPool selectPoolByStationGas(Long stationId, String gasType)
    {
        GasInventoryPool query = new GasInventoryPool();
        query.setStationId(stationId);
        query.setGasType(gasType);
        List<GasInventoryPool> list = poolMapper.selectPoolList(query);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public int updateAlertThreshold(Long poolId, BigDecimal threshold)
    {
        if (StringUtils.isNotNull(threshold) && threshold.compareTo(BigDecimal.ZERO) < 0)
        {
            throw new ServiceException("预警值不能为负数");
        }
        return poolMapper.updateAlert(poolId, threshold);
    }

    @Override
    public List<GasPurchaseReceipt> selectReceiptListOfPool(Long stationId, String gasType)
    {
        GasPurchaseReceipt query = new GasPurchaseReceipt();
        query.setStationId(stationId);
        query.setGasType(gasType);
        return receiptMapper.selectReceiptList(query);
    }

    @Override
    public List<GasInventoryLedger> selectLedgerList(GasInventoryLedger ledger)
    {
        return ledgerMapper.selectLedgerList(ledger);
    }

    /**
     * 锁定站点池（入库时池可能不存在，先建后锁，uk_station_gas 兜底并发）
     */
    private GasInventoryPool lockOrCreatePool(Long stationId, String gasType, Long buyerId)
    {
        GasInventoryPool pool = poolMapper.selectPoolForUpdate(stationId, gasType);
        if (StringUtils.isNotNull(pool))
        {
            requireSameBuyer(pool, buyerId);
            return pool;
        }
        GasInventoryPool create = new GasInventoryPool();
        create.setStationId(stationId);
        create.setBuyerId(buyerId);
        create.setGasType(gasType);
        try
        {
            poolMapper.insertPool(create);
            return create;
        }
        catch (DuplicateKeyException e)
        {
            // 并发建池：回读并加锁
            pool = poolMapper.selectPoolForUpdate(stationId, gasType);
            if (StringUtils.isNull(pool))
            {
                throw new ServiceException("库存池创建失败，请重试");
            }
            requireSameBuyer(pool, buyerId);
            return pool;
        }
    }

    private void requireSameBuyer(GasInventoryPool pool, Long buyerId)
    {
        if (!pool.getBuyerId().equals(buyerId))
        {
            throw new ServiceException("库存池与采购批次归属不一致，请先核查站点采购商变更");
        }
    }

    private GasInventoryPool requirePoolForUpdate(Long stationId, String gasType)
    {
        GasInventoryPool pool = poolMapper.selectPoolForUpdate(stationId, gasType);
        if (StringUtils.isNull(pool))
        {
            throw new ServiceException("该站点尚无库存（请先登记采购到货）");
        }
        return pool;
    }

    private void checkAvailable(GasInventoryPool pool, BigDecimal qty)
    {
        BigDecimal available = pool.getTotalIn().add(pool.getAdjusted())
                .subtract(pool.getConsumed()).subtract(pool.getReserved());
        if (available.compareTo(qty) < 0)
        {
            throw new ServiceException(String.format("站点可用库存不足：可用 %s，需要 %s", available, qty));
        }
    }

    private void requirePositiveQty(BigDecimal qty, String name)
    {
        if (StringUtils.isNull(qty) || qty.compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new ServiceException(name + "必须大于0");
        }
        if (qty.stripTrailingZeros().scale() > 2)
        {
            throw new ServiceException(name + "最多保留两位小数");
        }
    }

    /** 提醒在核心库存事务提交后执行，异常不得影响库存事实 */
    private void scheduleReminders(Long stationId, String gasType, String action)
    {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            runReminders(stationId, gasType, action);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCommit()
            {
                runReminders(stationId, gasType, action);
            }
        });
    }

    private void runReminders(Long stationId, String gasType, String action)
    {
        try
        {
            remindService.onBatchRemainChanged(stationId, gasType);
            remindService.checkPoolAlert(stationId, gasType);
        }
        catch (Exception e)
        {
            log.error("{}后库存提醒检查失败: stationId={}, gasType={}", action, stationId, gasType, e);
        }
    }

    private void insertLedger(Long poolId, Long receiptId, Long orderId, String bizType, BigDecimal qty, String reason, String operator)
    {
        GasInventoryLedger ledger = new GasInventoryLedger();
        ledger.setPoolId(poolId);
        ledger.setReceiptId(StringUtils.isNull(receiptId) ? 0L : receiptId);
        ledger.setOrderId(StringUtils.isNull(orderId) ? 0L : orderId);
        ledger.setBizType(bizType);
        ledger.setQty(qty);
        ledger.setReason(reason);
        ledger.setOperator(StringUtils.isEmpty(operator) ? "system" : operator);
        ledgerMapper.insertLedger(ledger);
    }
}
