package com.ruoyi.gas.service;

import java.util.List;
import com.ruoyi.gas.domain.GasPurchaseBatch;

/**
 * 采购批次 服务层
 *
 * @author gas
 */
public interface IGasPurchaseBatchService
{
    public List<GasPurchaseBatch> selectBatchList(GasPurchaseBatch batch);

    public GasPurchaseBatch selectBatchById(Long batchId);

    /** 生成下一个批次号（PB+yyyyMMddHHmmss） */
    public String generateBatchNo();

    public boolean checkBatchNoUnique(GasPurchaseBatch batch);

    public int insertBatch(GasPurchaseBatch batch);

    /** 乐观锁更新；有到货记录后不允许变更站点与气种 */
    public int updateBatch(GasPurchaseBatch batch);

    /** 删除守卫：有到货/付款记录不可删 */
    public int deleteBatchByIds(Long[] batchIds);

    /** 维护票据状态（INVOICED 必填发票号；改回未开票清空发票信息） */
    public int updateBatchInvoice(Long batchId, String invoiceStatus, String invoiceNo, String operator);
}
