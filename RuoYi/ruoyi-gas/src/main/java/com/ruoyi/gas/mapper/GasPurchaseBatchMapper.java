package com.ruoyi.gas.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.gas.domain.GasPurchaseBatch;

/**
 * 采购批次 数据层
 *
 * @author gas
 */
public interface GasPurchaseBatchMapper
{
    public List<GasPurchaseBatch> selectBatchList(GasPurchaseBatch batch);

    public GasPurchaseBatch selectBatchById(Long batchId);

    /** 锁定批次行（付款/票据状态联动重算时串行化同批次并发操作） */
    public GasPurchaseBatch selectBatchByIdForUpdate(Long batchId);

    public GasPurchaseBatch checkBatchNoUnique(String batchNo);

    public int insertBatch(GasPurchaseBatch batch);

    /** 乐观锁更新（where version = #{version}） */
    public int updateBatch(GasPurchaseBatch batch);

    public int updateBatchInvoice(@Param("batchId") Long batchId, @Param("invoiceStatus") String invoiceStatus,
            @Param("invoiceNo") String invoiceNo);

    public int updateBatchPayStatus(@Param("batchId") Long batchId, @Param("payStatus") String payStatus);

    public int deleteBatchByIds(Long[] batchIds);

    /** 到货数量合计（删除守卫） */
    public int countReceiptByBatchIds(Long[] batchIds);
}
