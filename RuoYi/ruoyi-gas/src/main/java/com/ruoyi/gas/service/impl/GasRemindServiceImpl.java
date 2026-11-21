package com.ruoyi.gas.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.gas.domain.GasBuyer;
import com.ruoyi.gas.domain.GasInventoryPool;
import com.ruoyi.gas.domain.GasPurchaseBatch;
import com.ruoyi.gas.domain.GasPurchaseReceipt;
import com.ruoyi.gas.domain.GasRemind;
import com.ruoyi.gas.mapper.GasBuyerMapper;
import com.ruoyi.gas.mapper.GasInventoryPoolMapper;
import com.ruoyi.gas.mapper.GasPurchaseBatchMapper;
import com.ruoyi.gas.mapper.GasPurchaseReceiptMapper;
import com.ruoyi.gas.mapper.GasRemindMapper;
import com.ruoyi.gas.service.IGasConfigService;
import com.ruoyi.gas.service.IGasRemindService;
import com.ruoyi.gas.sms.ISmsSender;

/**
 * 提醒服务实现（Dev_Plan §3.7）
 *
 * <p>规则：即将用完/已用完各 1 次；用完未开票每 N 天 1 次最多 M 次；
 * 库存预警跌破触发一次、回升后重新武装；免打扰时段（21:00–8:00）推迟到 8:00 发送。</p>
 *
 * @author gas
 */
@Service
public class GasRemindServiceImpl implements IGasRemindService
{
    private static final Logger log = LoggerFactory.getLogger(GasRemindServiceImpl.class);

    public static final String TYPE_BATCH_NEAR_EMPTY = "BATCH_NEAR_EMPTY";
    public static final String TYPE_BATCH_EMPTY = "BATCH_EMPTY";
    public static final String TYPE_BATCH_UNINVOICED = "BATCH_UNINVOICED";
    public static final String TYPE_STOCK_ALERT = "STOCK_ALERT";

    /** 开票提醒默认参数（采购商未配置时） */
    private static final BigDecimal DEFAULT_RATIO = new BigDecimal("20");
    private static final int DEFAULT_DAYS = 7;
    private static final int DEFAULT_MAX = 3;

    @Autowired
    private GasRemindMapper remindMapper;

    @Autowired
    private GasPurchaseReceiptMapper receiptMapper;

    @Autowired
    private GasPurchaseBatchMapper batchMapper;

    @Autowired
    private GasBuyerMapper buyerMapper;

    @Autowired
    private GasInventoryPoolMapper poolMapper;

    @Autowired
    private IGasConfigService configService;

    @Autowired
    private ISmsSender smsSender;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Override
    @Transactional
    public void onBatchRemainChanged(Long stationId, String gasType)
    {
        GasPurchaseReceipt query = new GasPurchaseReceipt();
        query.setStationId(stationId);
        query.setGasType(gasType);
        // 同一批次可能有多条到货记录，按批次去重后逐批检查
        Map<Long, GasPurchaseReceipt> byBatch = new LinkedHashMap<>();
        for (GasPurchaseReceipt receipt : receiptMapper.selectReceiptList(query))
        {
            byBatch.putIfAbsent(receipt.getBatchId(), receipt);
        }
        for (GasPurchaseReceipt receipt : byBatch.values())
        {
            BigDecimal remain = receiptMapper.sumBatchRemain(receipt.getBatchId());
            if (remain.compareTo(BigDecimal.ZERO) <= 0)
            {
                // 已用完（1 次）
                if (remindMapper.countByType(TYPE_BATCH_EMPTY, receipt.getBatchId(), 0L) == 0)
                {
                    insertRemind(TYPE_BATCH_EMPTY, receipt.getBuyerId(), receipt.getStationId(),
                            receipt.getBatchId(), 0L, 1,
                            String.format("批次[%s]气量已用完，请及时安排开票", receipt.getBatchNo()));
                }
                continue;
            }
            GasPurchaseBatch batch = batchMapper.selectBatchById(receipt.getBatchId());
            if (StringUtils.isNull(batch))
            {
                continue;
            }
            BigDecimal ratio = remindRatio(batch.getBuyerId());
            BigDecimal threshold = batch.getQty().multiply(ratio).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            if (remain.compareTo(threshold) <= 0
                    && remindMapper.countByType(TYPE_BATCH_NEAR_EMPTY, receipt.getBatchId(), 0L) == 0)
            {
                insertRemind(TYPE_BATCH_NEAR_EMPTY, receipt.getBuyerId(), receipt.getStationId(),
                        receipt.getBatchId(), 0L, 1,
                        String.format("批次[%s]剩余 %s（不足采购量的%s%%），请及时安排开票",
                                receipt.getBatchNo(), remain, ratio.stripTrailingZeros().toPlainString()));
            }
        }
    }

    @Override
    @Transactional
    public void checkPoolAlert(Long stationId, String gasType)
    {
        GasInventoryPool query = new GasInventoryPool();
        query.setStationId(stationId);
        query.setGasType(gasType);
        for (GasInventoryPool pool : poolMapper.selectPoolList(query))
        {
            checkSinglePool(pool);
        }
    }

    private void checkSinglePool(GasInventoryPool pool)
    {
        if (StringUtils.isNull(pool.getAlertThreshold()))
        {
            return;
        }
        BigDecimal available = pool.getTotalIn().add(pool.getAdjusted())
                .subtract(pool.getConsumed()).subtract(pool.getReserved());
        boolean below = available.compareTo(pool.getAlertThreshold()) <= 0;
        if (below && pool.getAlertArmed() == 1)
        {
            // 跌破预警值：触发一次并解除武装；seq 递增防重复
            poolMapper.updateAlertArmed(pool.getPoolId(), 0);
            int seq = remindMapper.countByType(TYPE_STOCK_ALERT, 0L, pool.getPoolId()) + 1;
            insertRemind(TYPE_STOCK_ALERT, pool.getBuyerId(), pool.getStationId(), 0L, pool.getPoolId(), seq,
                    String.format("站点[%s]%s可用余量 %s，已低于预警值 %s",
                            pool.getStationName(), pool.getGasType(), available, pool.getAlertThreshold()));
        }
        else if (!below && pool.getAlertArmed() == 0)
        {
            // 回升到阈值以上：重新武装，下次跌破再提醒
            poolMapper.updateAlertArmed(pool.getPoolId(), 1);
        }
    }

    @Override
    public void scanDaily()
    {
        // 生成阶段：单事务快速提交，避免与外部 IO 混在同一长事务
        transactionTemplate.executeWithoutResult(status -> generateDailyReminds());

        // 发送阶段：逐条独立提交，单条失败不影响其余
        for (GasRemind remind : remindMapper.selectPendingList())
        {
            sendSingle(remind);
        }
    }

    /** 每日生成：全站库存预警补查 + 用完未开票周期提醒 */
    private void generateDailyReminds()
    {
        // 1. 全站库存预警补查
        for (GasInventoryPool pool : poolMapper.selectPoolList(new GasInventoryPool()))
        {
            checkSinglePool(pool);
        }

        // 2. 用完未开票：第 n 次提醒在用完后满 n*days 天，最多 max 次
        GasPurchaseBatch query = new GasPurchaseBatch();
        query.setInvoiceStatus("UNINVOICED");
        for (GasPurchaseBatch batch : batchMapper.selectBatchList(query))
        {
            if (receiptMapper.sumBatchRemain(batch.getBatchId()).compareTo(BigDecimal.ZERO) != 0)
            {
                continue;
            }
            Date emptyAt = findEmptyRemindTime(batch.getBatchId());
            if (StringUtils.isNull(emptyAt))
            {
                continue;
            }
            int days = remindDays(batch.getBuyerId());
            int max = remindMax(batch.getBuyerId());
            long elapsed = Duration.between(emptyAt.toInstant(), new Date().toInstant()).toDays();
            int nextSeq = remindMapper.countByType(TYPE_BATCH_UNINVOICED, batch.getBatchId(), 0L) + 1;
            if (nextSeq <= max && elapsed >= (long) nextSeq * days)
            {
                insertRemind(TYPE_BATCH_UNINVOICED, batch.getBuyerId(), batch.getStationId(),
                        batch.getBatchId(), 0L, nextSeq,
                        String.format("批次[%s]已用完 %d 天仍未开票，请及时处理", batch.getBatchNo(), elapsed));
            }
        }
    }

    /** 发送单条待发提醒（阶段3占位短信，阶段8接阿里云并写 gas_sms_log） */
    private void sendSingle(GasRemind remind)
    {
        try
        {
            GasBuyer buyer = buyerMapper.selectBuyerById(remind.getBuyerId());
            String phones = StringUtils.isNotNull(buyer) ? buyer.getNotifyPhones() : "";
            boolean allOk = true;
            if (StringUtils.isNotEmpty(phones))
            {
                for (String phone : phones.split(","))
                {
                    allOk &= smsSender.send(phone, remind.getRemindType(), remind.getContent(), remind.getRemindType());
                }
            }
            remindMapper.updateStatus(remind.getRemindId(), allOk ? "SENT" : "FAILED");
        }
        catch (Exception e)
        {
            log.error("提醒发送失败: remindId={}", remind.getRemindId(), e);
        }
    }

    @Override
    public List<GasRemind> selectRemindList(GasRemind remind)
    {
        return remindMapper.selectRemindList(remind);
    }

    /** 查批次"已用完"提醒的触发时间，作为用完时间 */
    private Date findEmptyRemindTime(Long batchId)
    {
        GasRemind query = new GasRemind();
        query.setRemindType(TYPE_BATCH_EMPTY);
        query.setBatchId(batchId);
        List<GasRemind> list = remindMapper.selectRemindList(query);
        return list.isEmpty() ? null : list.get(0).getCreateTime();
    }

    private BigDecimal remindRatio(Long buyerId)
    {
        GasBuyer buyer = buyerMapper.selectBuyerById(buyerId);
        return (StringUtils.isNotNull(buyer) && StringUtils.isNotNull(buyer.getInvoiceRemindRatio()))
                ? buyer.getInvoiceRemindRatio() : DEFAULT_RATIO;
    }

    private int remindDays(Long buyerId)
    {
        GasBuyer buyer = buyerMapper.selectBuyerById(buyerId);
        return (StringUtils.isNotNull(buyer) && StringUtils.isNotNull(buyer.getInvoiceRemindDays()))
                ? buyer.getInvoiceRemindDays() : DEFAULT_DAYS;
    }

    private int remindMax(Long buyerId)
    {
        GasBuyer buyer = buyerMapper.selectBuyerById(buyerId);
        return (StringUtils.isNotNull(buyer) && StringUtils.isNotNull(buyer.getInvoiceRemindMax()))
                ? buyer.getInvoiceRemindMax() : DEFAULT_MAX;
    }

    private void insertRemind(String type, Long buyerId, Long stationId, Long batchId, Long poolId, int seq, String content)
    {
        GasRemind remind = new GasRemind();
        remind.setRemindType(type);
        remind.setBuyerId(buyerId);
        remind.setStationId(stationId);
        remind.setBatchId(batchId);
        remind.setPoolId(poolId);
        remind.setSeq(seq);
        remind.setContent(content);
        remind.setPlanSendAt(calcPlanSendAt(new Date()));
        try
        {
            remindMapper.insertRemind(remind);
        }
        catch (DuplicateKeyException e)
        {
            // uk_remind 兜底并发防重，静默忽略
        }
    }

    /**
     * 免打扰（gas_config sms.quiet.* 默认 21:00–8:00）：区间内触发的推迟到 8:00 发送
     */
    private Date calcPlanSendAt(Date now)
    {
        LocalDateTime dt = LocalDateTime.ofInstant(now.toInstant(), java.time.ZoneId.systemDefault());
        LocalTime quietStart = parse(configService.selectConfigValue("sms.quiet.start", "21:00"));
        LocalTime quietEnd = parse(configService.selectConfigValue("sms.quiet.end", "08:00"));
        LocalTime time = dt.toLocalTime();
        boolean inQuiet = time.isAfter(quietStart) || time.isBefore(quietEnd) || time.equals(quietStart);
        if (!inQuiet)
        {
            return now;
        }
        LocalDateTime sendAt = dt.toLocalDate().atTime(quietEnd);
        if (!time.isBefore(quietEnd))
        {
            sendAt = sendAt.plusDays(1);
        }
        return Date.from(sendAt.atZone(java.time.ZoneId.systemDefault()).toInstant());
    }

    private LocalTime parse(String hhmm)
    {
        String[] parts = hhmm.split(":");
        return LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }
}
