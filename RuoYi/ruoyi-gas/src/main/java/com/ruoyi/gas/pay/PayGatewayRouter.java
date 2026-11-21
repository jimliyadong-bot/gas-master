package com.ruoyi.gas.pay;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.gas.domain.GasBuyerMerchant;

/**
 * 支付网关路由
 *
 * <p>gas.h5.mock-mode=true 时走 Mock；真实模式必须由拉卡拉实现接管，
 * 未装配真实实现时明确失败，绝不静默回退到 Mock。</p>
 *
 * @author gas
 */
@Component
public class PayGatewayRouter
{
    @Value("${gas.h5.mock-mode:false}")
    private boolean mockMode;

    @Autowired
    private MockPayGateway mockPayGateway;

    /** 真实拉卡拉实现（收银台或 JSAPI，二选一装配；阶段8接入后存在） */
    @Autowired(required = false)
    private IPayGateway lakalaPayGateway;

    public IPayGateway route(GasBuyerMerchant merchant)
    {
        if (!mockMode)
        {
            if (lakalaPayGateway == null || lakalaPayGateway instanceof MockPayGateway)
            {
                throw new ServiceException("真实支付通道未配置，请联系管理员");
            }
            return lakalaPayGateway;
        }
        return mockPayGateway;
    }
}
