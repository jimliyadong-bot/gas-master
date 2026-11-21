package com.ruoyi.gas.mapper;

import java.util.List;
import com.ruoyi.gas.domain.GasBuyer;

/**
 * 采购商 数据层
 *
 * @author gas
 */
public interface GasBuyerMapper
{
    /**
     * 查询采购商列表
     */
    public List<GasBuyer> selectBuyerList(GasBuyer buyer);

    /**
     * 根据ID查询采购商
     */
    public GasBuyer selectBuyerById(Long buyerId);

    /**
     * 校验采购商名称唯一
     */
    public GasBuyer checkBuyerNameUnique(String buyerName);

    /**
     * 新增采购商
     */
    public int insertBuyer(GasBuyer buyer);

    /**
     * 修改采购商
     */
    public int updateBuyer(GasBuyer buyer);

    /**
     * 批量逻辑删除采购商（del_flag = '2'）
     */
    public int deleteBuyerByIds(Long[] buyerIds);
}
