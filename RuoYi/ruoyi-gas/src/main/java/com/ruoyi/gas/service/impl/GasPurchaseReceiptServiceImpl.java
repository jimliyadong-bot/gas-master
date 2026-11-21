package com.ruoyi.gas.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.gas.domain.GasPurchaseReceipt;
import com.ruoyi.gas.mapper.GasPurchaseReceiptMapper;
import com.ruoyi.gas.service.IGasPurchaseReceiptService;

/**
 * 采购到货查询 服务实现
 *
 * @author gas
 */
@Service
public class GasPurchaseReceiptServiceImpl implements IGasPurchaseReceiptService
{
    @Autowired
    private GasPurchaseReceiptMapper receiptMapper;

    @Override
    public List<GasPurchaseReceipt> selectReceiptList(GasPurchaseReceipt receipt)
    {
        return receiptMapper.selectReceiptList(receipt);
    }

    @Override
    public GasPurchaseReceipt selectReceiptById(Long receiptId)
    {
        return receiptMapper.selectReceiptById(receiptId);
    }
}
