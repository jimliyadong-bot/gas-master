package com.ruoyi.gas.mapper;

import java.util.List;
import com.ruoyi.gas.domain.GasBuyerMerchant;

/**
 * 采购商收款商户 数据层
 *
 * @author gas
 */
public interface GasBuyerMerchantMapper
{
    /**
     * 查询采购商商户配置列表
     */
    public List<GasBuyerMerchant> selectMerchantList(GasBuyerMerchant merchant);

    /**
     * 根据采购商ID查询商户配置（每采购商每通道一条）
     */
    public GasBuyerMerchant selectMerchantByBuyerId(Long buyerId);

    /**
     * 校验商户号唯一（同通道）
     */
    public GasBuyerMerchant checkMerchantNoUnique(GasBuyerMerchant merchant);

    /**
     * 新增商户配置
     */
    public int insertMerchant(GasBuyerMerchant merchant);

    /**
     * 修改商户配置
     */
    public int updateMerchant(GasBuyerMerchant merchant);

    /**
     * 删除采购商的商户配置（删采购商时联动）
     */
    public int deleteMerchantByBuyerIds(Long[] buyerIds);
}
