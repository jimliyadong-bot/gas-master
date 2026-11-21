package com.ruoyi.gas.service;

import java.util.List;
import com.ruoyi.gas.domain.GasCouponTemplate;

public interface IGasCouponTemplateService
{
    List<GasCouponTemplate> selectTemplateList(GasCouponTemplate template);
    GasCouponTemplate selectTemplateById(Long templateId);
    int insertTemplate(GasCouponTemplate template);
    int updateTemplate(GasCouponTemplate template);
}
