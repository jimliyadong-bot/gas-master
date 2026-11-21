package com.ruoyi.gas.service;

import java.util.Map;
import com.ruoyi.gas.domain.GasPurchaseBatch;
import com.ruoyi.gas.domain.GasPurchaseReceipt;
import com.ruoyi.gas.h5.H5StationRequests.CreateBatch;
import com.ruoyi.gas.h5.H5StationRequests.CreateReceipt;
import com.ruoyi.gas.h5.H5User;

public interface IH5StationService
{
    Map<String, Object> overview(H5User user);
    Map<String, Object> batches(H5User user, int page, int size, String gasType, String batchNo);
    Map<String, Object> batchDetail(H5User user, Long batchId);
    Map<String, Object> receipts(H5User user, Long batchId, int page, int size);
    GasPurchaseBatch createBatch(H5User user, CreateBatch request);
    GasPurchaseReceipt receive(H5User user, Long batchId, CreateReceipt request);
}
