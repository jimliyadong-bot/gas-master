package com.ruoyi.web.controller.h5;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.gas.domain.GasPayTxn;
import com.ruoyi.gas.mapper.GasPayTxnMapper;
import com.ruoyi.gas.service.IH5OrderService;

/**
 * H5 支付回调与模拟收银台（Dev_Plan §5 /api/pay/*，匿名）
 *
 * <p>/notify/{merchantId}：拉卡拉支付结果回调（真实模式验签待商户证书具备后补齐，
 * 当前解析标准报文字段并复用同一幂等处理路径）；/mock/cashier：mock 模式收银台。</p>
 *
 * @author gas
 */
@Anonymous
@RestController
@RequestMapping("/api/pay")
public class H5PayController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(H5PayController.class);

    @Value("${gas.h5.mock-mode:false}")
    private boolean mockMode;

    @Autowired
    private IH5OrderService orderService;

    @Autowired
    private GasPayTxnMapper txnMapper;

    /**
     * 拉卡拉支付结果回调（幂等：重复通知返回 success）
     *
     * <p>安全守卫：真实拉卡拉验签实现前（阶段8/商户接入），仅 mock 模式受理回调——
     * 否则任何人可伪造支付成功通知白嫖。接入时移除此守卫并以验签替代。</p>
     */
    @PostMapping(value = "/notify/{merchantId}", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public String notify(@PathVariable("merchantId") Long merchantId, @RequestBody String body)
    {
        if (!mockMode)
        {
            log.warn("支付回调已拒绝：拉卡拉验签未启用前回调端点关闭 merchantId={}", merchantId);
            return "fail: notify disabled until signature verification enabled";
        }
        JSONObject payload;
        try
        {
            payload = JSONObject.parseObject(body);
        }
        catch (Exception e)
        {
            log.warn("支付回调报文解析失败 merchantId={}", merchantId);
            return "fail: bad payload";
        }
        if (payload == null || StringUtils.isEmpty(payload.getString("out_trade_no")))
        {
            return "fail: missing out_trade_no";
        }
        log.info("支付回调：merchantId={} outTradeNo={}", merchantId, payload.getString("out_trade_no"));
        return orderService.handleNotify(merchantId, payload.getString("out_trade_no"),
                payload.getString("trade_no"), payload.getString("pay_channel"),
                payload.getString("amount"), body);
    }

    /**
     * Mock 收银台页面（仅 mock 模式）：展示订单金额，确认后触发等价回调
     */
    @GetMapping(value = "/mock/cashier", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public String cashier(@RequestParam("outTradeNo") String outTradeNo)
    {
        if (!mockMode)
        {
            return htmlMessage("mock 收银台未启用（生产环境不可用）");
        }
        GasPayTxn txn = txnMapper.selectByOutTradeNo(outTradeNo);
        if (txn == null)
        {
            return htmlMessage("交易号无效");
        }
        if (!GasPayTxn.STATUS_PAYING.equals(txn.getStatus()))
        {
            return htmlMessage("该交易已处理（状态：" + txn.getStatus() + "），可关闭本页返回");
        }
        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<title>模拟收银台</title><style>"
                + "body{font-family:system-ui,sans-serif;background:#f7f8fa;margin:0;padding:40px 16px;text-align:center}"
                + ".card{background:#fff;border-radius:12px;padding:32px 16px;max-width:420px;margin:0 auto;box-shadow:0 2px 8px rgba(0,0,0,.06)}"
                + ".amount{font-size:40px;font-weight:700;color:#e8501f;margin:16px 0}"
                + "button{background:#07c160;color:#fff;border:none;border-radius:24px;padding:12px 48px;font-size:17px;margin-top:24px}"
                + ".muted{color:#969799;font-size:13px}"
                + "</style></head><body><div class=\"card\">"
                + "<div class=\"muted\">模拟收银台（本地开发）</div>"
                + "<div class=\"amount\">¥ " + txn.getAmount().toPlainString() + "</div>"
                + "<div class=\"muted\">交易号 " + txn.getOutTradeNo() + "</div>"
                + "<form method=\"post\" action=\"/api/pay/mock/cashier/pay\">"
                + "<input type=\"hidden\" name=\"outTradeNo\" value=\"" + txn.getOutTradeNo() + "\">"
                + "<input type=\"hidden\" name=\"payChannel\" value=\"WECHAT\">"
                + "<button type=\"submit\">确认支付</button></form>"
                + "<div class=\"muted\" style=\"margin-top:16px\">支付成功后可关闭本页返回订单页</div>"
                + "</div></body></html>";
    }

    /**
     * Mock 收银台确认支付：生成模拟渠道交易号，走与真实回调相同的幂等处理
     * （金额取自流水本身，模拟渠道侧按单收款）
     */
    @PostMapping(value = "/mock/cashier/pay", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public String mockPay(@RequestParam("outTradeNo") String outTradeNo,
            @RequestParam(value = "payChannel", defaultValue = "WECHAT") String payChannel)
    {
        if (!mockMode)
        {
            return htmlMessage("mock 收银台未启用");
        }
        GasPayTxn txn = txnMapper.selectByOutTradeNo(outTradeNo);
        if (txn == null)
        {
            return htmlMessage("交易号无效");
        }
        String channelTradeNo = "MOCK" + System.currentTimeMillis();
        String answer = orderService.handleNotify(txn.getMerchantId(), outTradeNo, channelTradeNo, payChannel,
                txn.getAmount().toPlainString(),
                "{\"mock\":true,\"out_trade_no\":\"" + outTradeNo + "\"}");
        if (answer.startsWith("success"))
        {
            return htmlMessage("支付成功！可关闭本页返回订单页查看核销码");
        }
        return htmlMessage("支付失败：" + answer + "，请返回重试");
    }

    private static String htmlMessage(String message)
    {
        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<title>模拟收银台</title><style>"
                + "body{font-family:system-ui,sans-serif;background:#f7f8fa;margin:0;padding:60px 16px;text-align:center;color:#323233}"
                + "</style></head><body><div>" + message + "</div></body></html>";
    }
}
