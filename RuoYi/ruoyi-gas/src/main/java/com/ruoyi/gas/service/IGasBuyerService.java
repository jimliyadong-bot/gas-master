package com.ruoyi.gas.service;

import java.util.List;
import com.ruoyi.gas.domain.GasBuyer;
import com.ruoyi.gas.domain.GasBuyerMerchant;

/**
 * 采购商 服务层
 *
 * @author gas
 */
public interface IGasBuyerService
{
    /**
     * 查询采购商列表
     */
    public List<GasBuyer> selectBuyerList(GasBuyer buyer);

    /**
     * 根据ID查询采购商（含拉卡拉商户配置）
     */
    public GasBuyer selectBuyerById(Long buyerId);

    /**
     * 新增采购商（含商户配置）
     */
    public int insertBuyer(GasBuyer buyer);

    /**
     * 修改采购商（含商户配置）
     */
    public int updateBuyer(GasBuyer buyer);

    /**
     * 批量删除采购商（名下有站点时拒绝）
     */
    public int deleteBuyerByIds(Long[] buyerIds);

    /**
     * 校验采购商名称是否唯一
     */
    public boolean checkBuyerNameUnique(GasBuyer buyer);

    /**
     * 校验商户号是否唯一（同通道）
     */
    public boolean checkMerchantNoUnique(GasBuyerMerchant merchant);
}
