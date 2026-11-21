package com.ruoyi.gas.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.gas.domain.GasBuyer;
import com.ruoyi.gas.domain.GasBuyerMerchant;
import com.ruoyi.gas.mapper.GasBuyerMapper;
import com.ruoyi.gas.mapper.GasBuyerMerchantMapper;
import com.ruoyi.gas.mapper.GasStationMapper;
import com.ruoyi.gas.service.IGasBuyerService;

/**
 * 采购商 服务实现
 *
 * @author gas
 */
@Service
public class GasBuyerServiceImpl implements IGasBuyerService
{
    /** 默认支付通道：拉卡拉（Dev_Plan §7.1 A-7） */
    private static final String DEFAULT_CHANNEL = "LAKALA";

    @Autowired
    private GasBuyerMapper buyerMapper;

    @Autowired
    private GasBuyerMerchantMapper merchantMapper;

    @Autowired
    private GasStationMapper stationMapper;

    @Override
    public List<GasBuyer> selectBuyerList(GasBuyer buyer)
    {
        return buyerMapper.selectBuyerList(buyer);
    }

    @Override
    public GasBuyer selectBuyerById(Long buyerId)
    {
        GasBuyer buyer = buyerMapper.selectBuyerById(buyerId);
        if (StringUtils.isNotNull(buyer))
        {
            buyer.setMerchant(merchantMapper.selectMerchantByBuyerId(buyerId));
        }
        return buyer;
    }

    @Override
    @Transactional
    public int insertBuyer(GasBuyer buyer)
    {
        int rows = buyerMapper.insertBuyer(buyer);
        saveMerchant(buyer, null);
        return rows;
    }

    @Override
    @Transactional
    public int updateBuyer(GasBuyer buyer)
    {
        int rows = buyerMapper.updateBuyer(buyer);
        saveMerchant(buyer, merchantMapper.selectMerchantByBuyerId(buyer.getBuyerId()));
        return rows;
    }

    /**
     * 保存采购商的拉卡拉商户配置（1:1，无商户号则不保存）
     */
    private void saveMerchant(GasBuyer buyer, GasBuyerMerchant existing)
    {
        GasBuyerMerchant merchant = buyer.getMerchant();
        if (StringUtils.isNull(merchant) || StringUtils.isEmpty(merchant.getMerchantNo()))
        {
            return;
        }
        merchant.setBuyerId(buyer.getBuyerId());
        if (StringUtils.isEmpty(merchant.getChannel()))
        {
            merchant.setChannel(DEFAULT_CHANNEL);
        }
        if (StringUtils.isEmpty(merchant.getStatus()))
        {
            merchant.setStatus("0");
        }
        if (StringUtils.isNull(existing))
        {
            merchantMapper.insertMerchant(merchant);
        }
        else
        {
            merchant.setMerchantId(existing.getMerchantId());
            merchantMapper.updateMerchant(merchant);
        }
    }

    @Override
    @Transactional
    public int deleteBuyerByIds(Long[] buyerIds)
    {
        for (Long buyerId : buyerIds)
        {
            GasBuyer buyer = buyerMapper.selectBuyerById(buyerId);
            if (StringUtils.isNull(buyer))
            {
                continue;
            }
            if (stationMapper.countStationByBuyerId(buyerId) > 0)
            {
                throw new ServiceException(String.format("采购商[%s]名下存在站点，无法删除", buyer.getBuyerName()));
            }
        }
        merchantMapper.deleteMerchantByBuyerIds(buyerIds);
        return buyerMapper.deleteBuyerByIds(buyerIds);
    }

    @Override
    public boolean checkBuyerNameUnique(GasBuyer buyer)
    {
        Long buyerId = StringUtils.isNull(buyer.getBuyerId()) ? -1L : buyer.getBuyerId();
        GasBuyer info = buyerMapper.checkBuyerNameUnique(buyer.getBuyerName());
        if (StringUtils.isNotNull(info) && info.getBuyerId().longValue() != buyerId.longValue())
        {
            return false;
        }
        return true;
    }

    @Override
    public boolean checkMerchantNoUnique(GasBuyerMerchant merchant)
    {
        Long merchantId = StringUtils.isNull(merchant.getMerchantId()) ? -1L : merchant.getMerchantId();
        if (StringUtils.isEmpty(merchant.getChannel()))
        {
            merchant.setChannel(DEFAULT_CHANNEL);
        }
        GasBuyerMerchant info = merchantMapper.checkMerchantNoUnique(merchant);
        if (StringUtils.isNotNull(info) && info.getMerchantId().longValue() != merchantId.longValue())
        {
            return false;
        }
        return true;
    }
}
