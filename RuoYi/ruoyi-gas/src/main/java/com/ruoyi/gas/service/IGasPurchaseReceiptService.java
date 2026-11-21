package com.ruoyi.gas.service;

import java.util.List;
import com.ruoyi.gas.domain.GasPurchaseReceipt;

/**
 * 采购到货查询 服务层（入库动作走 IInventoryService.receive）
 *
 * @author gas
 */
public interface IGasPurchaseReceiptService
{
    public List<GasPurchaseReceipt> selectReceiptList(GasPurchaseReceipt receipt);

    public GasPurchaseReceipt selectReceiptById(Long receiptId);
}
