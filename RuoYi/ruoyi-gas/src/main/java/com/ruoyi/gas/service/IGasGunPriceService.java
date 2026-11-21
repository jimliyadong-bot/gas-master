package com.ruoyi.gas.service;

import java.util.List;
import com.ruoyi.gas.domain.GasGunPrice;

/**
 * 枪价 服务层
 *
 * @author gas
 */
public interface IGasGunPriceService
{
    /**
     * 查询枪价列表（可只看当前有效价）
     */
    public List<GasGunPrice> selectPriceList(GasGunPrice price);

    /**
     * 根据ID查询枪价
     */
    public GasGunPrice selectPriceById(Long priceId);

    /**
     * 新增枪价：自动结束旧价（effective_to = 新价生效时间），历史保留
     */
    public int insertPrice(GasGunPrice price);

    /**
     * 删除枪价：只允许删除每支枪最新一条，删除后恢复上一条为当前有效
     */
    public int deletePriceByIds(Long[] priceIds);

    /**
     * 查询枪当前有效价格
     */
    public GasGunPrice selectCurrentPriceByGunId(Long gunId);
}
