package com.ruoyi.gas.mapper;

import org.apache.ibatis.annotations.Param;
import com.ruoyi.gas.domain.GasMember;

/**
 * 消费者会员 数据层
 *
 * @author gas
 */
public interface GasMemberMapper
{
    public GasMember selectMemberById(Long memberId);

    /** 主动下单按会员串行化幂等请求 */
    public GasMember selectMemberByIdForUpdate(Long memberId);

    public GasMember selectMemberByOpenid(String openid);

    /** 手机号已被绑定的会员（换绑校验：排除自身） */
    public GasMember selectMemberByPhone(@Param("phone") String phone, @Param("excludeMemberId") Long excludeMemberId);

    public int insertMember(GasMember member);

    /** 绑定/换绑手机号 */
    public int bindPhone(@Param("memberId") Long memberId, @Param("phone") String phone);

    public int updateMember(GasMember member);
}
