package com.ruoyi.gas.mapper;

import org.apache.ibatis.annotations.Param;
import com.ruoyi.gas.domain.GasDynamicToken;

/**
 * 动态码 数据层（30秒刷新窗口内复用同码）
 *
 * @author gas
 */
public interface GasDynamicTokenMapper
{
    /** 查询刷新窗口内（30 秒）未使用未过期的同业务码，命中则复用 */
    public GasDynamicToken selectReusable(@Param("tokenType") String tokenType,
            @Param("refId") Long refId, @Param("freshWindow") java.util.Date freshWindow);

    /** 核销用：按码查有效记录（60 秒内、未使用） */
    public GasDynamicToken selectValidByToken(@Param("token") String token,
            @Param("tokenType") String tokenType, @Param("now") java.util.Date now);

    /** 核销消费：used_at 置当前时间（未使用才生效，单次使用） */
    public int markUsed(@Param("tokenId") Long tokenId);

    /** 清理指定时间之前的过期码（定时任务，保留近一天便于排查） */
    public int deleteExpiredBefore(@Param("before") java.util.Date before);

    public int insertToken(GasDynamicToken token);
}
