package com.ruoyi.web.controller.h5;

import java.util.function.Supplier;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.gas.h5.H5StationRequests.CreateBatch;
import com.ruoyi.gas.h5.H5StationRequests.CreateReceipt;
import com.ruoyi.gas.h5.H5UserContext;
import com.ruoyi.gas.service.IH5StationService;

/** H5 JWT 站管入口。每个服务方法实时校验管理员身份；不使用后台登录 Cookie。 */
@Anonymous
@RestController
@RequestMapping("/api/h5/m")
public class H5StationController extends BaseController
{
    private final IH5StationService stationService;

    public H5StationController(IH5StationService stationService) { this.stationService = stationService; }

    @GetMapping("/overview")
    public AjaxResult overview() { return success(stationService.overview(H5UserContext.require())); }

    @GetMapping("/batches")
    public AjaxResult batches(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String gasType, @RequestParam(required = false) String batchNo)
    {
        return success(stationService.batches(H5UserContext.require(), page, size, gasType, batchNo));
    }

    @GetMapping("/batches/{batchId}")
    public AjaxResult detail(@PathVariable Long batchId)
    {
        return success(stationService.batchDetail(H5UserContext.require(), batchId));
    }

    @GetMapping("/batches/{batchId}/receipts")
    public AjaxResult receipts(@PathVariable Long batchId, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size)
    {
        return success(stationService.receipts(H5UserContext.require(), batchId, page, size));
    }

    @PostMapping("/batches")
    public AjaxResult create(@Valid @RequestBody CreateBatch input)
    {
        return writeAttempt(input.requestId(), () -> stationService.createBatch(H5UserContext.require(), input));
    }

    @PostMapping("/batches/{batchId}/receipts")
    public AjaxResult receive(@PathVariable Long batchId, @Valid @RequestBody CreateReceipt input)
    {
        return writeAttempt(input.requestId(), () -> stationService.receive(H5UserContext.require(), batchId, input));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public AjaxResult invalidWrite(MethodArgumentNotValidException e)
    {
        Object input = e.getBindingResult().getTarget();
        String requestId = input instanceof CreateBatch batch ? batch.requestId()
                : input instanceof CreateReceipt receipt ? receipt.requestId() : null;
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        // 已成功绑定DTO但校验失败，尚未调用业务服务；仍只确认本次尝试。
        return requestId == null ? error(message) : rejectedAttempt(requestId, message);
    }

    private AjaxResult writeAttempt(String requestId, Supplier<?> action)
    {
        try
        {
            return success(action.get());
        }
        catch (ServiceException e)
        {
            // 仅表示本次业务尝试被拒绝（事务代理抛出业务异常后已回滚）。
            // 不能表示此前同请求号的未知尝试未提交；客户端重试仍须保留原号。
            // 数据库/提交/响应序列化等通用异常不打此标记，由全局处理器处理。
            return rejectedAttempt(requestId, e.getMessage());
        }
    }

    private AjaxResult rejectedAttempt(String requestId, String message)
    {
        AjaxResult result = error(message);
        result.put("stationWriteAttemptRejected", true);
        result.put("requestId", requestId);
        return result;
    }
}
