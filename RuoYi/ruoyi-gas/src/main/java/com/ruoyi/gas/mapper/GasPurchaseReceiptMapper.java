package com.ruoyi.gas.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.gas.domain.GasPurchaseReceipt;

/**
 * 采购到货 数据层
 *
 * @author gas
 */
public interface GasPurchaseReceiptMapper
{
    public List<GasPurchaseReceipt> selectReceiptList(GasPurchaseReceipt receipt);

    public GasPurchaseReceipt selectReceiptById(Long receiptId);

    /** 锁定单条到货（人工调整时用） */
    public GasPurchaseReceipt selectReceiptByIdForUpdate(Long receiptId);

    /**
     * 先进先出扣减候选：锁定该站该气种所有有余量的到货批次，按 received_at, receipt_id 排序
     */
    public List<GasPurchaseReceipt> selectFifoForUpdate(@Param("stationId") Long stationId, @Param("gasType") String gasType);

    public int insertReceipt(GasPurchaseReceipt receipt);

    /** 累加已实扣（行锁由 FIFO 查询持有） */
    public int addConsumed(@Param("receiptId") Long receiptId, @Param("qty") BigDecimal qty);

    /** 累加调整量（可负） */
    public int addAdjusted(@Param("receiptId") Long receiptId, @Param("qty") BigDecimal qty);

    /** 批次剩余量合计 = sum(qty + adjusted - consumed) */
    public BigDecimal sumBatchRemain(Long batchId);

    /** 批次累计到货毛量 = sum(qty) */
    public BigDecimal sumReceivedQty(Long batchId);

    /** 当前读；调用方须先锁采购批次，入库时还须先锁池，避免RR旧快照与锁序反转。 */
    public BigDecimal sumReceivedQtyForUpdate(Long batchId);

    public int countByBatchIds(Long[] batchIds);
}
