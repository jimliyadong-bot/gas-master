package com.ruoyi.gas.service.impl;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.gas.domain.GasGun;
import com.ruoyi.gas.domain.GasGunPrice;
import com.ruoyi.gas.mapper.GasGunMapper;
import com.ruoyi.gas.mapper.GasGunPriceMapper;
import com.ruoyi.gas.service.IGasGunPriceService;

/**
 * 枪价 服务实现
 *
 * <p>只增不改：新增价格时自动结束旧价（effective_to = 新价生效时间）；
 * 只允许删除每支枪最新一条价格，删除后自动恢复上一条为当前有效。</p>
 *
 * @author gas
 */
@Service
public class GasGunPriceServiceImpl implements IGasGunPriceService
{
    @Autowired
    private GasGunPriceMapper priceMapper;

    @Autowired
    private GasGunMapper gunMapper;

    @Override
    public List<GasGunPrice> selectPriceList(GasGunPrice price)
    {
        return priceMapper.selectPriceList(price);
    }

    @Override
    public GasGunPrice selectPriceById(Long priceId)
    {
        return priceMapper.selectPriceById(priceId);
    }

    @Override
    @Transactional
    public int insertPrice(GasGunPrice price)
    {
        GasGun gun = gunMapper.selectGunById(price.getGunId());
        if (StringUtils.isNull(gun))
        {
            throw new ServiceException("枪不存在或已被删除");
        }
        price.setEffectiveFrom(java.util.Date.from(price.getEffectiveFrom().toInstant()
                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS)));
        String fromStr = DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, price.getEffectiveFrom());
        // 以链尾开口价（可能为当前生效价，也可能为预置的未来价）为基准校验，保证价格链有序
        GasGunPrice latest = priceMapper.selectLatestPriceByGunId(price.getGunId());
        if (StringUtils.isNotNull(latest) && !price.getEffectiveFrom().after(latest.getEffectiveFrom()))
        {
            throw new ServiceException(String.format("新价生效时间必须晚于已录入价格的最新生效时间（%s）",
                    DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, latest.getEffectiveFrom())));
        }
        // 新价生效时自动结束旧价，历史保留
        if (StringUtils.isNotNull(latest))
        {
            priceMapper.closeCurrentPrice(price.getGunId(), fromStr);
        }
        try
        {
            return priceMapper.insertPrice(price);
        }
        catch (DuplicateKeyException e)
        {
            // uk_gun_current 生成列唯一键兜底：并发调价时后提交者事务回滚，避免出现两条当前价
            throw new ServiceException("调价保存冲突（可能有人在同时调价），请刷新后重试");
        }
    }

    @Override
    @Transactional
    public int deletePriceByIds(Long[] priceIds)
    {
        // 待重开的上一条价格：必须先删链尾再重开，否则同枪两条开口价触发 uk_gun_current 唯一键冲突
        List<Long> reopenIds = new ArrayList<>();
        for (Long priceId : priceIds)
        {
            GasGunPrice price = priceMapper.selectPriceById(priceId);
            if (StringUtils.isNull(price))
            {
                continue;
            }
            String fromStr = DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, price.getEffectiveFrom());
            if (priceMapper.countNewerPrices(price.getGunId(), fromStr) > 0)
            {
                throw new ServiceException("存在更新的价格记录，只能删除每支枪最新的一条价格");
            }
            // 删除的是链尾开口价时，恢复上一条为开口
            if (StringUtils.isNull(price.getEffectiveTo()))
            {
                GasGunPrice prev = priceMapper.selectPrevPrice(price.getGunId(), fromStr);
                if (StringUtils.isNotNull(prev))
                {
                    reopenIds.add(prev.getPriceId());
                }
            }
        }
        int rows = priceMapper.deletePriceByIds(priceIds);
        for (Long reopenId : reopenIds)
        {
            priceMapper.reopenPriceById(reopenId);
        }
        return rows;
    }

    @Override
    public GasGunPrice selectCurrentPriceByGunId(Long gunId)
    {
        return priceMapper.selectCurrentPriceByGunId(gunId);
    }
}
