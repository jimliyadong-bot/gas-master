package com.ruoyi.gas.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.gas.domain.GasGunPrice;

/**
 * 枪价 数据层
 *
 * @author gas
 */
public interface GasGunPriceMapper
{
    /**
     * 查询枪价列表（联查枪/站点/采购商）
     */
    public List<GasGunPrice> selectPriceList(GasGunPrice price);

    /**
     * 根据ID查询枪价
     */
    public GasGunPrice selectPriceById(Long priceId);

    /**
     * 查询枪当前生效价格（from <= now < to 或 to 为空）
     */
    public GasGunPrice selectCurrentPriceByGunId(Long gunId);

    /**
     * 查询枪的链尾开口价（effective_to 为空的最新一条，可能为预置的未来价）
     */
    public GasGunPrice selectLatestPriceByGunId(Long gunId);

    /**
     * 结束枪当前有效价格（effective_to = 新价生效时间）
     */
    public int closeCurrentPrice(@Param("gunId") Long gunId, @Param("effectiveTo") String effectiveTo);

    /**
     * 查询枪在指定生效时间之前最近的一条价格（删最新价时用于恢复）
     */
    public GasGunPrice selectPrevPrice(@Param("gunId") Long gunId, @Param("effectiveFrom") String effectiveFrom);

    /**
     * 统计枪上比指定生效时间更新的价格条数
     */
    public int countNewerPrices(@Param("gunId") Long gunId, @Param("effectiveFrom") String effectiveFrom);

    /**
     * 恢复价格为当前有效（effective_to 置空，删最新价时恢复上一条）
     */
    public int reopenPriceById(Long priceId);

    /**
     * 新增枪价
     */
    public int insertPrice(GasGunPrice price);

    /**
     * 批量删除枪价（物理删除，用于删枪联动或录错清理）
     */
    public int deletePriceByIds(Long[] priceIds);

    /**
     * 删除枪的全部价格记录（删枪联动）
     */
    public int deletePriceByGunIds(Long[] gunIds);
}
