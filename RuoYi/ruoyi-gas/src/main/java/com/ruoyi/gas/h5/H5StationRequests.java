package com.ruoyi.gas.h5;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import jakarta.validation.constraints.*;

/** 站管写入白名单；站点、采购商、操作人、金额均由服务端确定。 */
public final class H5StationRequests
{
    private H5StationRequests() { }

    public record CreateBatch(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{8,64}", message = "请求号格式错误") String requestId,
            @NotNull @Pattern(regexp = "CNG|LNG", message = "气种只能为CNG或LNG") String gasType,
            @NotNull(message = "采购日期不能为空") @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8", lenient = OptBoolean.FALSE) Date purchaseDate,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal qty,
            @NotNull @DecimalMin("0.0001") @Digits(integer = 6, fraction = 4) BigDecimal unitPrice,
            @Size(max = 500, message = "备注最多500字") String remark) { }

    public record CreateReceipt(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{8,64}", message = "请求号格式错误") String requestId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal qty,
            @NotNull(message = "到货时间不能为空") @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8", lenient = OptBoolean.FALSE) Date receivedAt,
            @Size(max = 500, message = "备注最多500字") String remark) { }
}
