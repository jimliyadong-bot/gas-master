package com.ruoyi.gas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.gas.domain.GasBuyer;
import com.ruoyi.gas.domain.GasMember;
import com.ruoyi.gas.domain.GasStation;
import com.ruoyi.gas.h5.H5TokenService;
import com.ruoyi.gas.h5.H5User;
import com.ruoyi.gas.mapper.GasMemberMapper;
import com.ruoyi.gas.mapper.GasStationMapper;
import com.ruoyi.gas.mapper.GasBuyerMapper;
import com.ruoyi.gas.service.IH5AuthService;
import com.ruoyi.gas.service.IH5SmsService;
import com.ruoyi.gas.service.IWxApiService;
import com.ruoyi.system.service.ISysRoleService;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.common.core.domain.entity.SysUser;

/**
 * H5 认证流程 JUnit 集成测试（Dev_Plan 阶段4）
 *
 * <ul>
 *   <li>mock 授权登录：openid 自动建档、重复登录幂等</li>
 *   <li>短信验证码：发送限频（60 秒间隔）、错误码拒绝、绑定成功后 phoneBound</li>
 *   <li>员工绑定：加气员角色 + 站点部门 → 员工身份；非员工角色/跨站拒绝</li>
 *   <li>JWT 签发/校验往返</li>
 * </ul>
 *
 * @author gas
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.quartz.auto-startup=false",
        "gas.h5.mock-mode=true" })
@Transactional
class GasH5AuthFlowTest
{
    /** mock 模式下 openid = "MOCK-" + code，code 即模拟身份 */
    private static final String MOCK_CODE = "JUNIT-USER";
    private static final String MOCK_OPENID = "MOCK-JUNIT-USER";
    private static final String PHONE = "13800001111";

    @Autowired
    private IWxApiService wxApiService;

    @Autowired
    private IH5AuthService authService;

    @Autowired
    private IH5SmsService smsService;

    @Autowired
    private H5TokenService tokenService;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysRoleService roleService;

    @Autowired
    private GasMemberMapper memberMapper;

    @Autowired
    private GasBuyerMapper buyerMapper;

    @Autowired
    private GasStationMapper stationMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 准备：采购商 + 站点（含部门）+ 挂在站点部门的加气员账号 */
    private Long[] prepareStaffUser(String loginName)
    {
        GasBuyer buyer = new GasBuyer();
        buyer.setBuyerName("JUnitH5采购商");
        buyer.setStatus("0");
        buyerMapper.insertBuyer(buyer);

        jdbcTemplate.update("insert into sys_dept (parent_id, ancestors, dept_name, order_num, status, del_flag, create_by, create_time) "
                + "values (100, '0,100', 'JUnitH5部门', 99, '0', '0', 'junit', sysdate())");
        Long deptId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);

        GasStation station = new GasStation();
        station.setStationCode("JUNIT-H5");
        station.setStationName("JUnitH5站点");
        station.setBuyerId(buyer.getBuyerId());
        station.setDeptId(deptId);
        station.setQrToken(IdUtils.fastSimpleUUID());
        station.setStatus("0");
        stationMapper.insertStation(station);

        jdbcTemplate.update("insert into sys_user (login_name, user_name, password, status, del_flag, create_by, create_time, dept_id) "
                + "values (?, 'JUnit加气员', 'unused', '0', '0', 'junit', sysdate(), ?)", loginName, deptId);
        Long userId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        // 角色 103 = 加气员（gas_staff，阶段1初始化）
        jdbcTemplate.update("insert into sys_user_role (user_id, role_id) values (?, 103)", userId);
        return new Long[] { userId, station.getStationId(), deptId };
    }

    /**
     * 用例1：mock 授权登录 —— 自动建档、重复登录幂等、JWT 往返
     */
    @Test
    void testMockLoginAndTokenRoundtrip()
    {
        assertTrue(wxApiService.isMockMode(), "mock 属性已生效");

        H5User first = authService.loginByCode(MOCK_CODE);
        assertEquals(H5User.ROLE_MEMBER, first.getRole());
        assertNotNull(first.getMemberId(), "首次登录自动建档");
        assertEquals(MOCK_OPENID, first.getOpenid());
        assertTrue(!first.isPhoneBound(), "新会员未绑定手机号");

        // 幂等：同一 openid 再次登录返回同一会员
        H5User second = authService.loginByCode(MOCK_CODE);
        assertEquals(first.getMemberId(), second.getMemberId());

        // JWT 往返
        String token = tokenService.issue(first);
        H5User verified = tokenService.verify(token);
        assertEquals(first.getMemberId(), verified.getMemberId());
        assertEquals(H5User.ROLE_MEMBER, verified.getRole());

        // 篡改的 token 必须被拒绝
        assertThrows(Exception.class, () -> tokenService.verify(token.substring(0, token.length() - 4) + "x"));
    }

    /**
     * 用例2：验证码发送限频 + 错误码拒绝 + 绑定手机号成功
     */
    @Test
    void testSmsCodeAndBindPhone()
    {
        H5User user = authService.loginByCode(MOCK_CODE);
        smsService.sendCode(PHONE, "127.0.0.1");

        // 60 秒内重发被拒
        ServiceException ex = assertThrows(ServiceException.class, () -> smsService.sendCode(PHONE, "127.0.0.1"));
        assertTrue(ex.getMessage().contains("频繁"));

        // 错误验证码被拒
        assertThrows(ServiceException.class, () -> smsService.verifyCode(PHONE, "000000"));

        // 从内存缓存提取正确验证码（占位短信实现不外发明文）
        Map<String, ?> cache = (Map<String, ?>) ReflectionTestUtils.getField(smsService, "codeCache");
        Object entry = ((ConcurrentHashMap<?, ?>) cache).get(PHONE);
        String code = (String) ReflectionTestUtils.getField(entry, "code");

        smsService.verifyCode(PHONE, code);
        // 验证通过即作废：同码二次校验必须失败
        assertThrows(ServiceException.class, () -> smsService.verifyCode(PHONE, code));

        // 绑定手机号 → phoneBound=true，档案落库
        H5User bound = authService.bindPhone(user, PHONE);
        assertTrue(bound.isPhoneBound());
        GasMember member = memberMapper.selectMemberById(bound.getMemberId());
        assertEquals(PHONE, member.getPhone());
        assertNotNull(member.getPhoneBoundAt());

        // 绑定后 JWT 携带 phoneBound
        H5User verified = tokenService.verify(tokenService.issue(bound));
        assertTrue(verified.isPhoneBound());
    }

    /**
     * 用例3：手机号唯一 —— 已被其他微信绑定的手机号拒绝
     */
    @Test
    void testBindPhoneOccupied()
    {
        H5User a = authService.loginByCode(MOCK_CODE);

        // 另一个 openid 的会员先占用手机号（mock 模式下不同 code = 不同用户）
        H5User b = authService.loginByCode("OTHER-JUNIT-USER");
        assertNotEquals(a.getMemberId(), b.getMemberId());
        H5User bBound = authService.bindPhone(b, PHONE);
        assertTrue(bBound.isPhoneBound());

        // A 再绑同号必须被拒
        ServiceException ex = assertThrows(ServiceException.class, () -> authService.bindPhone(a, PHONE));
        assertTrue(ex.getMessage().contains("其他微信"));
    }

    /**
     * 用例4：员工绑定 —— 加气员角色 + 站点部门 → 员工身份；非员工角色拒绝
     */
    @Test
    void testStaffBind()
    {
        Long[] ids = prepareStaffUser("junith5");
        Long userId = ids[0];
        Long stationId = ids[1];

        // 先以会员身份登录（未绑定员工）
        H5User member = authService.loginByCode(MOCK_CODE);
        assertEquals(H5User.ROLE_MEMBER, member.getRole());

        // 绑定员工
        SysUser sysUser = userService.selectUserByLoginName("junith5");
        H5User staff = authService.staffBind(member, sysUser);
        assertEquals(H5User.ROLE_STAFF, staff.getRole());
        assertEquals(userId, staff.getUserId());
        assertEquals(stationId, staff.getStationId(), "站点由用户部门反查");

        // 绑定后再次登录 → 员工身份优先
        H5User relogin = authService.loginByCode(MOCK_CODE);
        assertEquals(H5User.ROLE_STAFF, relogin.getRole());

        // 员工 JWT 往返
        H5User verified = tokenService.verify(tokenService.issue(staff));
        assertEquals(H5User.ROLE_STAFF, verified.getRole());
        assertEquals(stationId, verified.getStationId());

        // 非员工角色账号（无 gas_staff/gas_station_admin）拒绝绑定
        jdbcTemplate.update("insert into sys_user (login_name, user_name, password, status, del_flag, create_by, create_time, dept_id) "
                + "values ('junith5ops', 'JUnit运营', 'unused', '0', '0', 'junit', sysdate(), 100)");
        Long opsUserId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        jdbcTemplate.update("insert into sys_user_role (user_id, role_id) values (?, 100)", opsUserId); // 运营角色
        SysUser opsUser = userService.selectUserByLoginName("junith5ops");
        ServiceException ex = assertThrows(ServiceException.class, () -> authService.staffBind(member, opsUser));
        assertTrue(ex.getMessage().contains("加气员或站点管理员"));
    }
}
