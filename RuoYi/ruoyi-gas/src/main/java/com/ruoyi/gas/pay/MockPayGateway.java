package com.ruoyi.gas.pay;

import java.util.Collections;
import org.springframework.stereotype.Component;
import com.ruoyi.gas.domain.GasBuyerMerchant;
import com.ruoyi.gas.domain.GasOrder;
import com.ruoyi.gas.domain.GasPayTxn;

/**
 * Mock 支付网关（gas.h5.mock-mode=true 时启用，本地/测试走通全链路）
 *
 * <p>payUrl 指向后端模拟收银台页面（/api/pay/mock/cashier?outTradeNo=xx），
 * 页面上的"确认支付"即触发与真实网关等价的回调处理路径（幂等、预占）。</p>
 *
 * @author gas
 */
@Component
public class MockPayGateway implements IPayGateway
{
    @Override
    public PayLaunch launch(GasPayTxn txn, GasBuyerMerchant merchant, GasOrder order)
    {
        return new PayLaunch("/api/pay/mock/cashier?outTradeNo=" + txn.getOutTradeNo(), Collections.emptyMap());
    }

    @Override
    public PayQueryResult query(GasBuyerMerchant merchant, String outTradeNo)
    {
        // mock 模式无网关可查：返回"支付中"交由超时关单兜底
        return new PayQueryResult(PayQueryResult.TRADE_PAYING, null, null);
    }
}
