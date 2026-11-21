package com.ruoyi.gas.mapper;

import java.util.List;
import com.ruoyi.gas.domain.GasCouponTemplate;

public interface GasCouponTemplateMapper
{
    List<GasCouponTemplate> selectTemplateList(GasCouponTemplate template);
    GasCouponTemplate selectTemplateById(Long templateId);
    GasCouponTemplate selectTemplateByIdForUpdate(Long templateId);
    int insertTemplate(GasCouponTemplate template);
    int updateTemplate(GasCouponTemplate template);
    int increaseIssued(Long templateId);
}
