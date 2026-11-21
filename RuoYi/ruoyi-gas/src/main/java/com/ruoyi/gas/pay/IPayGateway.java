package com.ruoyi.gas.pay;

import java.util.Map;
import com.ruoyi.gas.domain.GasBuyerMerchant;
import com.ruoyi.gas.domain.GasOrder;
import com.ruoyi.gas.domain.GasPayTxn;

/**
 * 支付网关抽象（拉卡拉收银台 / JSAPI，最终接入方式待定——Dev_Plan 阶段5）
 *
 * <p>阶段5以 {@link MockPayGateway} 走通全链路；真实拉卡拉实现（收银台与 JSAPI）
 * 待商户号/证书具备后补齐，接口保持不变。</p>
 *
 * @author gas
 */
public interface IPayGateway
{
    /**
     * 发起支付：返回前端可用的支付入口
     *
     * @return payUrl：跳转型（收银台/mock 收银台）；payParams：JSAPI 调起参数（可空）
     */
    PayLaunch launch(GasPayTxn txn, GasBuyerMerchant merchant, GasOrder order);

    /**
     * 主动查单（超时补偿用）：返回 null 表示网关不支持/无结果
     */
    PayQueryResult query(GasBuyerMerchant merchant, String outTradeNo);

    /** 支付发起结果 */
    class PayLaunch
    {
        private final String payUrl;
        private final Map<String, String> payParams;

        public PayLaunch(String payUrl, Map<String, String> payParams)
        {
            this.payUrl = payUrl;
            this.payParams = payParams;
        }

        public String getPayUrl()
        {
            return payUrl;
        }

        public Map<String, String> getPayParams()
        {
            return payParams;
        }
    }

    /** 查单结果 */
    class PayQueryResult
    {
        public static final String TRADE_SUCCESS = "SUCCESS";
        public static final String TRADE_PAYING = "PAYING";
        public static final String TRADE_CLOSED = "CLOSED";

        private final String tradeStatus;
        private final String channelTradeNo;
        private final String payChannel;

        public PayQueryResult(String tradeStatus, String channelTradeNo, String payChannel)
        {
            this.tradeStatus = tradeStatus;
            this.channelTradeNo = channelTradeNo;
            this.payChannel = payChannel;
        }

        public String getTradeStatus()
        {
            return tradeStatus;
        }

        public String getChannelTradeNo()
        {
            return channelTradeNo;
        }

        public String getPayChannel()
        {
            return payChannel;
        }
    }
}
