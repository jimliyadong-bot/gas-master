package com.ruoyi.gas.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.gas.domain.GasBuyer;
import com.ruoyi.gas.domain.GasCouponTemplate;
import com.ruoyi.gas.mapper.GasBuyerMapper;
import com.ruoyi.gas.mapper.GasCouponTemplateMapper;
import com.ruoyi.gas.service.IGasCouponTemplateService;

@Service
public class GasCouponTemplateServiceImpl implements IGasCouponTemplateService
{
    @Autowired
    private GasCouponTemplateMapper templateMapper;
    @Autowired
    private GasBuyerMapper buyerMapper;

    @Override
    public List<GasCouponTemplate> selectTemplateList(GasCouponTemplate template)
    {
        return templateMapper.selectTemplateList(template);
    }

    @Override
    public GasCouponTemplate selectTemplateById(Long templateId)
    {
        return templateMapper.selectTemplateById(templateId);
    }

    @Override
    @Transactional
    public int insertTemplate(GasCouponTemplate template)
    {
        validate(template, null);
        template.setIssuedQty(0);
        return templateMapper.insertTemplate(template);
    }

    @Override
    @Transactional
    public int updateTemplate(GasCouponTemplate template)
    {
        if (template.getTemplateId() == null) throw new ServiceException("模板ID不能为空");
        GasCouponTemplate old = templateMapper.selectTemplateByIdForUpdate(template.getTemplateId());
        if (old == null) throw new ServiceException("优惠券模板不存在");
        validate(template, old);
        return templateMapper.updateTemplate(template);
    }

    private void validate(GasCouponTemplate template, GasCouponTemplate old)
    {
        if (template.getBuyerId() == null) throw new ServiceException("请选择发券采购商");
        if (old == null || !old.getBuyerId().equals(template.getBuyerId()))
        {
            GasBuyer buyer = buyerMapper.selectBuyerById(template.getBuyerId());
            if (buyer == null || !"0".equals(buyer.getStatus())) throw new ServiceException("采购商不存在或已停用");
        }
        if (old != null && old.getIssuedQty() > 0 && !old.getBuyerId().equals(template.getBuyerId()))
            throw new ServiceException("已发券模板不能变更采购商");
        if (StringUtils.isEmpty(template.getName()) || template.getName().trim().length() > 64)
            throw new ServiceException("券名称不能为空且不超过64字");
        template.setName(template.getName().trim());
        template.setFaceValue(money(template.getFaceValue(), "面额"));
        template.setThresholdAmount(money(template.getThresholdAmount(), "使用门槛"));
        if (template.getFaceValue().signum() <= 0) throw new ServiceException("面额必须大于0");
        if (template.getThresholdAmount().signum() < 0) throw new ServiceException("使用门槛不能小于0");
        if (template.getTotalQty() == null || template.getTotalQty() <= 0 || template.getTotalQty() > 1000000)
            throw new ServiceException("发放总量须为1至1000000");
        if (old != null && template.getTotalQty() < old.getIssuedQty())
            throw new ServiceException("发放总量不能小于已发数量");
        if (template.getStatus() == null) template.setStatus("0");
        if (!"0".equals(template.getStatus()) && !"1".equals(template.getStatus()))
            throw new ServiceException("模板状态无效");
        if ("FIXED".equals(template.getValidType()))
        {
            if (template.getValidFrom() == null || template.getValidTo() == null
                    || !template.getValidTo().after(template.getValidFrom()))
                throw new ServiceException("固定有效期开始时间必须早于结束时间");
            template.setValidDays(null);
        }
        else if ("DAYS".equals(template.getValidType()))
        {
            if (template.getValidDays() == null || template.getValidDays() <= 0 || template.getValidDays() > 3650)
                throw new ServiceException("领取后有效天数须为1至3650天");
            template.setValidFrom(null);
            template.setValidTo(null);
        }
        else throw new ServiceException("有效期类型无效");
    }

    private BigDecimal money(BigDecimal value, String label)
    {
        if (value == null) throw new ServiceException(label + "不能为空");
        try { return value.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw new ServiceException(label + "最多两位小数"); }
    }
}
