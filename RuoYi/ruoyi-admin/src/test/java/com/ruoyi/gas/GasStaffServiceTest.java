package com.ruoyi.gas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.gas.domain.GasBuyer;
import com.ruoyi.gas.domain.GasDynamicToken;
import com.ruoyi.gas.domain.GasGun;
import com.ruoyi.gas.domain.GasGunPrice;
import com.ruoyi.gas.domain.GasInventoryPool;
import com.ruoyi.gas.domain.GasMember;
import com.ruoyi.gas.domain.GasOrder;
import com.ruoyi.gas.domain.GasPayTxn;
import com.ruoyi.gas.domain.GasPurchaseReceipt;
import com.ruoyi.gas.domain.GasShift;
import com.ruoyi.gas.domain.GasStation;
import com.ruoyi.gas.h5.H5User;
import com.ruoyi.gas.mapper.GasBuyerMapper;
import com.ruoyi.gas.mapper.GasGunMapper;
import com.ruoyi.gas.mapper.GasGunPriceMapper;
import com.ruoyi.gas.mapper.GasMemberMapper;
import com.ruoyi.gas.mapper.GasOrderMapper;
import com.ruoyi.gas.mapper.GasPayTxnMapper;
import com.ruoyi.gas.mapper.GasStationMapper;
import com.ruoyi.gas.service.IGasShiftService;
import com.ruoyi.gas.service.IH5OrderService;
import com.ruoyi.gas.service.IH5StaffService;
import com.ruoyi.gas.service.IInventoryService;

/** 阶段6集成测试：连真实 MySQL，逐用例事务回滚 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.quartz.auto-startup=false", "gas.h5.mock-mode=true" })
class GasStaffServiceTest
{
    @Autowired private IGasShiftService shiftService;
    @Autowired private IH5StaffService staffService;
    @Autowired private IH5OrderService orderService;
    @Autowired private IInventoryService inventoryService;
    @Autowired private GasBuyerMapper buyerMapper;
    @Autowired private GasStationMapper stationMapper;
    @Autowired private GasGunMapper gunMapper;
    @Autowired private GasGunPriceMapper priceMapper;
    @Autowired private GasMemberMapper memberMapper;
    @Autowired private GasOrderMapper orderMapper;
    @Autowired private GasPayTxnMapper txnMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    /** 用例1：同站唯一当班；管理员强制交班必须有原因；停用账号实时拒绝 */
    @Test
    @Transactional
    void testShiftOwnershipAndForceEnd()
    {
        Fixture f = fixture("JUNIT6-S1");
        H5User staffA = staff(f, f.staffAId, "JUNIT6-A");
        H5User adminB = staff(f, f.staffBId, "JUNIT6-B");

        GasShift first = shiftService.startShift(staffA);
        assertNotNull(first.getShiftId());
        ServiceException busy = assertThrows(ServiceException.class, () -> shiftService.startShift(adminB));
        assertTrue(busy.getMessage().contains("当班"));
        assertThrows(ServiceException.class,
                () -> shiftService.forceEndShift(first.getShiftId(), "admin", "", f.stationId));

        shiftService.requireStationAdminUser(adminB);
        GasShift forced = shiftService.forceEndShift(first.getShiftId(), "admin", "忘记交班，电话确认", f.stationId);
        assertEquals(null, forced.getActiveFlag());
        assertEquals("忘记交班，电话确认", forced.getEndReason());

        GasShift second = shiftService.startShift(adminB);
        assertThrows(ServiceException.class, () -> shiftService.startShift(staffA));
        shiftService.endShift(adminB, second.getShiftId());

        jdbcTemplate.update("update sys_user set status='1' where user_id=?", f.staffAId);
        assertThrows(ServiceException.class, () -> shiftService.startShift(staffA));
    }

    /** 用例2：会员码代下单→司机确认→支付预占→核销FIFO→班次汇总；重复核销拒绝 */
    @Test
    @Transactional
    void testProxyPayRedeemAndShiftSummary() throws Exception
    {
        Fixture f = fixture("JUNIT6-S2");
        H5User staff = staff(f, f.staffAId, "JUNIT6-C");
        H5User member = member(f.memberId);
        GasShift shift = shiftService.startShift(staff);

        GasDynamicToken memberCode = orderService.memberToken(member);
        GasOrder order = staffService.createProxyOrder(staff, memberCode.getToken(), shift.getShiftId(), f.gunId, new BigDecimal("35.00"));
        assertEquals(GasOrder.STATUS_WAIT_CONFIRM, order.getStatus());
        assertEquals(GasOrder.SOURCE_PROXY, order.getSource());
        assertEquals(0, countLedger(order.getOrderId(), "RESERVE"), "确认前不得预占");

        assertEquals(GasOrder.STATUS_WAIT_PAY, orderService.confirmProxyOrder(member, order.getOrderNo()).getStatus());
        pay(member, order);
        assertEquals(GasOrder.STATUS_PAID, orderMapper.selectOrderById(order.getOrderId()).getStatus());
        assertEquals(1, countLedger(order.getOrderId(), "RESERVE"));

        GasDynamicToken redeemCode = orderService.redeemToken(member, order.getOrderNo());
        GasOrder completed = staffService.redeem(staff, redeemCode.getToken(), shift.getShiftId(), new BigDecimal("11.66"));
        assertEquals(GasOrder.STATUS_COMPLETED, completed.getStatus());
        assertEquals(new BigDecimal("11.66"), completed.getActualVolume());
        assertEquals(1, countLedger(order.getOrderId(), "CONSUME"));
        assertEquals(0, inventoryService.selectPoolByStationGas(f.stationId, "CNG").getReserved().compareTo(BigDecimal.ZERO));
        assertThrows(ServiceException.class,
                () -> staffService.redeem(staff, redeemCode.getToken(), shift.getShiftId(), new BigDecimal("11.66")));

        GasShift ended = shiftService.endShift(staff, shift.getShiftId());
        assertEquals(1, ended.getProxyCount());
        assertEquals(1, ended.getRedeemCount());
        assertEquals(0, new BigDecimal("11.66").compareTo(ended.getCngVolume()));
        assertEquals(0, new BigDecimal("35.00").compareTo(ended.getPayAmount()));
    }

    /** 用例3：司机拒绝和待确认超时都关闭，且不产生预占 */
    @Test
    @Transactional
    void testProxyRejectAndConfirmTimeout()
    {
        Fixture f = fixture("JUNIT6-S3");
        H5User staff = staff(f, f.staffAId, "JUNIT6-D");
        H5User member = member(f.memberId);
        GasShift shift = shiftService.startShift(staff);

        GasOrder rejected = staffService.createProxyOrder(staff, orderService.memberToken(member).getToken(),
                shift.getShiftId(), f.gunId, new BigDecimal("20.00"));
        orderService.rejectProxyOrder(member, rejected.getOrderNo());
        assertEquals(GasOrder.STATUS_CLOSED, orderMapper.selectOrderById(rejected.getOrderId()).getStatus());
        assertEquals(0, countLedger(rejected.getOrderId(), "RESERVE"));

        GasOrder expired = staffService.createProxyOrder(staff, orderService.memberToken(member).getToken(),
                shift.getShiftId(), f.gunId, new BigDecimal("30.00"));
        jdbcTemplate.update("update gas_order set expire_at=date_sub(sysdate(), interval 1 minute) where order_id=?", expired.getOrderId());
        orderService.closeExpiredOrders();
        GasOrder closed = orderMapper.selectOrderById(expired.getOrderId());
        assertEquals(GasOrder.STATUS_CLOSED, closed.getStatus());
        assertTrue(closed.getCloseReason().contains("待司机确认"));
        assertEquals(0, countLedger(expired.getOrderId(), "RESERVE"));
    }

    /** 用例4：跨站核销拒绝；交班后旧班次核销拒绝 */
    @Test
    @Transactional
    void testCrossStationAndChangedShiftRejected() throws Exception
    {
        Fixture first = fixture("JUNIT6-S4A");
        Fixture second = fixture("JUNIT6-S4B");
        H5User member = member(first.memberId);
        GasOrder paid = paidSelfOrder(member, first.gunId);
        GasDynamicToken code = orderService.redeemToken(member, paid.getOrderNo());

        H5User otherStaff = staff(second, second.staffAId, "JUNIT6-E");
        GasShift otherShift = shiftService.startShift(otherStaff);
        ServiceException cross = assertThrows(ServiceException.class,
                () -> staffService.redeem(otherStaff, code.getToken(), otherShift.getShiftId(), new BigDecimal("1.00")));
        assertTrue(cross.getMessage().contains("跨站"));
        assertEquals(GasOrder.STATUS_PAID, orderMapper.selectOrderById(paid.getOrderId()).getStatus());

        H5User oldStaff = staff(first, first.staffAId, "JUNIT6-F");
        GasShift oldShift = shiftService.startShift(oldStaff);
        shiftService.endShift(oldStaff, oldShift.getShiftId());
        H5User newStaff = staff(first, first.staffBId, "JUNIT6-G");
        shiftService.startShift(newStaff);
        ServiceException changed = assertThrows(ServiceException.class,
                () -> staffService.redeem(oldStaff, code.getToken(), oldShift.getShiftId(), new BigDecimal("1.00")));
        assertTrue(changed.getMessage().contains("班次已变更"));
    }

    private GasOrder paidSelfOrder(H5User member, Long gunId) throws Exception
    {
        GasOrder order = orderService.createOrder(member, gunId, new BigDecimal("35.00"));
        pay(member, order);
        return orderMapper.selectOrderById(order.getOrderId());
    }

    private void pay(H5User member, GasOrder order) throws Exception
    {
        orderService.pay(member, order.getOrderNo());
        GasPayTxn txn = txnMapper.selectPayingByOrderId(order.getOrderId());
        orderService.handleNotify(txn.getMerchantId(), txn.getOutTradeNo(), "JUNIT6-" + txn.getTxnId(),
                "WECHAT", txn.getAmount().toPlainString(), "{}");
    }

    private int countLedger(Long orderId, String type)
    {
        return jdbcTemplate.queryForObject("select count(1) from gas_inventory_ledger where order_id=? and biz_type=?",
                Integer.class, orderId, type);
    }

    private H5User member(Long memberId)
    {
        return H5User.member("JUNIT6-MEMBER", memberId, true);
    }

    private H5User staff(Fixture f, Long userId, String ignored)
    {
        String role = userId.equals(f.staffBId) ? H5User.ROLE_STATION_ADMIN : H5User.ROLE_STAFF;
        String openid = "JUNIT6-" + f.code + (userId.equals(f.staffBId) ? "-B" : "-A");
        return H5User.staff(openid, userId, f.stationId, role);
    }

    /** 每套站点：库存100、收货100、一枪一价、一个会员、一个加气员、一个站点管理员 */
    private Fixture fixture(String code)
    {
        Fixture f = new Fixture();
        f.code = code;
        GasBuyer buyer = new GasBuyer();
        buyer.setBuyerName("JUnit6采购商-" + code);
        buyer.setStatus("0");
        buyerMapper.insertBuyer(buyer);
        f.buyerId = buyer.getBuyerId();

        jdbcTemplate.update("insert into gas_buyer_merchant (buyer_id,channel,merchant_no,status,create_by,create_time) values (?, 'LAKALA', ?, '0', 'junit6', sysdate())",
                f.buyerId, "M-" + code);
        f.merchantId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        jdbcTemplate.update("insert into sys_dept (parent_id,ancestors,dept_name,order_num,status,del_flag,create_by,create_time) values (100,'0,100',?,99,'0','0','junit6',sysdate())",
                "JUnit6部门-" + code);
        f.deptId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);

        GasStation station = new GasStation();
        station.setStationCode(code);
        station.setStationName("JUnit6站点-" + code);
        station.setBuyerId(f.buyerId);
        station.setDeptId(f.deptId);
        station.setQrToken(IdUtils.fastSimpleUUID());
        station.setStatus("0");
        stationMapper.insertStation(station);
        f.stationId = station.getStationId();

        GasGun gun = new GasGun();
        gun.setStationId(f.stationId);
        gun.setGasType("CNG");
        gun.setGunNo(1);
        gun.setStatus("0");
        gunMapper.insertGun(gun);
        f.gunId = gun.getGunId();
        GasGunPrice price = new GasGunPrice();
        price.setGunId(f.gunId);
        price.setPrice(new BigDecimal("3.0000"));
        price.setEffectiveFrom(DateUtils.addMinutes(DateUtils.getNowDate(), -1));
        priceMapper.insertPrice(price);

        jdbcTemplate.update("insert into gas_inventory_pool (station_id,buyer_id,gas_type,total_in,adjusted,consumed,reserved,alert_threshold) values (?,?,'CNG',100,0,0,0,0)",
                f.stationId, f.buyerId);
        jdbcTemplate.update("insert into gas_purchase_batch (batch_no,station_id,buyer_id,gas_type,qty,unit_price,amount,purchase_date,pay_status,invoice_status,create_by,create_time) values (?,?,?,'CNG',100,3,300,curdate(),'UNPAID','UNINVOICED','junit6',sysdate())",
                "B-" + code, f.stationId, f.buyerId);
        Long batchId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        jdbcTemplate.update("insert into gas_purchase_receipt (batch_id,buyer_id,station_id,gas_type,qty,consumed,adjusted,received_at,create_by,create_time) values (?,?,?,'CNG',100,0,0,sysdate(),'junit6',sysdate())",
                batchId, f.buyerId, f.stationId);

        GasMember member = new GasMember();
        member.setOpenid("JUNIT6-MEMBER-" + code);
        member.setPhone("139" + String.format("%08d", Math.abs(code.hashCode()) % 100000000));
        member.setPhoneBoundAt(DateUtils.getNowDate());
        member.setStatus("0");
        memberMapper.insertMember(member);
        jdbcTemplate.update("update gas_member set phone=?, phone_bound_at=sysdate() where member_id=?", member.getPhone(), member.getMemberId());
        f.memberId = member.getMemberId();

        f.staffAId = createStaff(code + "-A", f.deptId, f.stationId, 103);
        f.staffBId = createStaff(code + "-B", f.deptId, f.stationId, 102);
        return f;
    }

    private Long createStaff(String code, Long deptId, Long stationId, int roleId)
    {
        String login = ("j6" + code).toLowerCase().replaceAll("[^a-z0-9]", "");
        String openid = "JUNIT6-" + code;
        jdbcTemplate.update("insert into sys_user (login_name,user_name,password,status,del_flag,create_by,create_time,dept_id) values (?,?,'unused','0','0','junit6',sysdate(),?)",
                login, "JUnit6员工" + code, deptId);
        Long userId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        jdbcTemplate.update("insert into sys_user_role (user_id,role_id) values (?,?)", userId, roleId);
        jdbcTemplate.update("insert into gas_staff_binding (user_id,openid,station_id,bound_at) values (?,?,?,sysdate())",
                userId, openid, stationId);
        return userId;
    }

    private static final class Fixture
    {
        private String code;
        private Long buyerId;
        private Long merchantId;
        private Long deptId;
        private Long stationId;
        private Long gunId;
        private Long memberId;
        private Long staffAId;
        private Long staffBId;
    }
}
