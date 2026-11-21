package com.ruoyi.gas.h5;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.gas.service.IGasConfigService;

/**
 * H5 端 JWT 签发与校验（密钥/有效期读 gas_config，启动后首次调用时加载）
 *
 * @author gas
 */
@Service
public class H5TokenService
{
    private static final String CLAIM_MEMBER_ID = "mid";
    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_STATION_ID = "sid";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_PHONE_BOUND = "pb";

    @Autowired
    private IGasConfigService configService;

    @Value("${gas.h5.jwt-secret:}")
    private String envSecret;

    @Value("${gas.h5.mock-mode:false}")
    private boolean mockMode;

    private volatile Algorithm algorithm;
    private volatile long expireMillis = -1;

    /** 签发：会员或员工身份 */
    public String issue(H5User user)
    {
        return JWT.create()
                .withIssuer("gas-h5")
                .withSubject(user.getOpenid())
                .withClaim(CLAIM_MEMBER_ID, user.getMemberId() == null ? 0L : user.getMemberId())
                .withClaim(CLAIM_USER_ID, user.getUserId() == null ? 0L : user.getUserId())
                .withClaim(CLAIM_STATION_ID, user.getStationId() == null ? 0L : user.getStationId())
                .withClaim(CLAIM_ROLE, user.getRole())
                .withClaim(CLAIM_PHONE_BOUND, user.isPhoneBound())
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + expireMillis()))
                .sign(algorithm());
    }

    /** 校验并还原登录身份；签名错误/过期抛 JWTVerificationException */
    public H5User verify(String token)
    {
        JWTVerifier verifier = JWT.require(algorithm()).withIssuer("gas-h5").build();
        DecodedJWT jwt = verifier.verify(token);
        Long memberId = jwt.getClaim(CLAIM_MEMBER_ID).asLong();
        Long userId = jwt.getClaim(CLAIM_USER_ID).asLong();
        Long stationId = jwt.getClaim(CLAIM_STATION_ID).asLong();
        String role = jwt.getClaim(CLAIM_ROLE).asString();
        Boolean phoneBound = jwt.getClaim(CLAIM_PHONE_BOUND).asBoolean();
        if (StringUtils.isEmpty(jwt.getSubject()) || memberId == null || userId == null
                || stationId == null || phoneBound == null)
        {
            throw new JWTVerificationException("缺少必需身份 claims");
        }
        if (H5User.ROLE_MEMBER.equals(role) && memberId > 0)
        {
            return H5User.member(jwt.getSubject(), memberId, phoneBound);
        }
        if ((H5User.ROLE_STAFF.equals(role) || H5User.ROLE_STATION_ADMIN.equals(role))
                && userId > 0 && stationId > 0)
        {
            return H5User.staff(jwt.getSubject(), userId, stationId, role);
        }
        throw new JWTVerificationException("非法角色或身份 claims");
    }

    private Algorithm algorithm()
    {
        if (algorithm == null)
        {
            synchronized (this)
            {
                if (algorithm == null)
                {
                    String secret = StringUtils.isNotEmpty(envSecret) ? envSecret
                            : configService.selectConfigValue("h5.jwt.secret", "");
                    if ("gas-h5-jwt-secret-please-change-in-production".equals(secret))
                    {
                        // 即使存量库尚未执行升级脚本，也拒绝使用历史公开密钥
                        secret = "";
                    }
                    if (StringUtils.isEmpty(secret) && mockMode)
                    {
                        // 仅限本地 mock：随机密钥重启即失效，不使用任何公开默认值
                        secret = IdUtils.fastSimpleUUID() + IdUtils.fastSimpleUUID();
                    }
                    if (StringUtils.isEmpty(secret) || secret.getBytes(StandardCharsets.UTF_8).length < 32)
                    {
                        throw new ServiceException("H5 JWT 密钥未配置或强度不足（环境变量 GAS_H5_JWT_SECRET，至少32字节）");
                    }
                    algorithm = Algorithm.HMAC256(secret);
                }
            }
        }
        return algorithm;
    }

    private long expireMillis()
    {
        if (expireMillis < 0)
        {
            synchronized (this)
            {
                if (expireMillis < 0)
                {
                    long hours = Long.parseLong(configService.selectConfigValue("h5.jwt.expire.hours", "168"));
                    expireMillis = hours * 3600_000L;
                }
            }
        }
        return expireMillis;
    }
}
