-- ============================================================================
-- 加气站购销系统 · SQL 导入完整性核验（MySQL 8，只核验，不修改业务数据）
-- 用法：在目标库执行本文件，例如 mysql ... ry -e "source .../gas_import_verify.sql"
-- 标记：[OK]=通过；[MISSING]=关键项缺失；[WARN]=非阻断警告/可能被后台定制
-- 说明：临时存储过程和临时表在会话结束后消失，存储过程也在脚本末尾主动删除。
-- ============================================================================

SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS gas_verify_import;
DELIMITER $$

CREATE PROCEDURE gas_verify_import()
BEGIN
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_id BIGINT;
    DECLARE v_phase VARCHAR(32);
    DECLARE v_type VARCHAR(32);
    DECLARE v_object VARCHAR(255);
    DECLARE v_expected VARCHAR(255);
    DECLARE v_level VARCHAR(16);
    DECLARE v_desc VARCHAR(255);
    DECLARE v_actual BIGINT DEFAULT 0;
    DECLARE v_status VARCHAR(16);

    DECLARE cur CURSOR FOR
        SELECT check_id, phase, check_type, object_name, expected_value, fail_level, item_desc
        FROM tmp_gas_verify_expected ORDER BY check_id;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    DROP TEMPORARY TABLE IF EXISTS tmp_gas_verify_expected;
    DROP TEMPORARY TABLE IF EXISTS tmp_gas_verify_result;
    CREATE TEMPORARY TABLE tmp_gas_verify_expected (
        check_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
        phase VARCHAR(32) NOT NULL,
        check_type VARCHAR(32) NOT NULL,
        object_name VARCHAR(255) NOT NULL,
        expected_value VARCHAR(255) NOT NULL DEFAULT '',
        fail_level VARCHAR(16) NOT NULL DEFAULT '[MISSING]',
        item_desc VARCHAR(255) NOT NULL
    );
    CREATE TEMPORARY TABLE tmp_gas_verify_result (
        check_id BIGINT NOT NULL,
        phase VARCHAR(32) NOT NULL,
        item_desc VARCHAR(255) NOT NULL,
        object_name VARCHAR(255) NOT NULL,
        expected_value VARCHAR(255) NOT NULL,
        actual_count BIGINT NOT NULL,
        status VARCHAR(16) NOT NULL
    );

    -- 1. 若依基础表（ry_20260319.sql）
    INSERT INTO tmp_gas_verify_expected (phase, check_type, object_name, item_desc) VALUES
    ('若依基础','TABLE','sys_dept','基础表'),('若依基础','TABLE','sys_user','基础表'),
    ('若依基础','TABLE','sys_post','基础表'),('若依基础','TABLE','sys_role','基础表'),
    ('若依基础','TABLE','sys_menu','基础表'),('若依基础','TABLE','sys_user_role','基础表'),
    ('若依基础','TABLE','sys_role_menu','基础表'),('若依基础','TABLE','sys_role_dept','基础表'),
    ('若依基础','TABLE','sys_user_post','基础表'),('若依基础','TABLE','sys_oper_log','基础表'),
    ('若依基础','TABLE','sys_dict_type','基础表'),('若依基础','TABLE','sys_dict_data','基础表'),
    ('若依基础','TABLE','sys_config','基础表'),('若依基础','TABLE','sys_logininfor','基础表'),
    ('若依基础','TABLE','sys_user_online','基础表'),('若依基础','TABLE','sys_job','基础表'),
    ('若依基础','TABLE','sys_job_log','基础表'),('若依基础','TABLE','sys_notice','基础表'),
    ('若依基础','TABLE','sys_notice_read','基础表'),('若依基础','TABLE','gen_table','基础表'),
    ('若依基础','TABLE','gen_table_column','基础表');
    INSERT INTO tmp_gas_verify_expected (phase, check_type, object_name, expected_value, item_desc) VALUES
    ('若依基础','USER_ID','1','admin','admin 初始账号'),
    ('若依基础','ROLE','1','admin','admin 角色'),
    ('若依基础','MENU','1','','系统管理菜单'),
    ('若依基础','MENU','2','','系统监控菜单'),
    ('若依基础','MENU','3','','系统工具菜单');

    -- 2. Quartz 表（quartz.sql；大小写按脚本原样核验）
    INSERT INTO tmp_gas_verify_expected (phase, check_type, object_name, item_desc)
    SELECT 'Quartz','TABLE',t.name,'Quartz 表' FROM (
        SELECT 'QRTZ_JOB_DETAILS' AS name UNION ALL SELECT 'QRTZ_TRIGGERS'
        UNION ALL SELECT 'QRTZ_SIMPLE_TRIGGERS' UNION ALL SELECT 'QRTZ_CRON_TRIGGERS'
        UNION ALL SELECT 'QRTZ_BLOB_TRIGGERS' UNION ALL SELECT 'QRTZ_CALENDARS'
        UNION ALL SELECT 'QRTZ_PAUSED_TRIGGER_GRPS' UNION ALL SELECT 'QRTZ_FIRED_TRIGGERS'
        UNION ALL SELECT 'QRTZ_SCHEDULER_STATE' UNION ALL SELECT 'QRTZ_LOCKS'
        UNION ALL SELECT 'QRTZ_SIMPROP_TRIGGERS'
    ) t;

    -- 3. 业务表（gas_schema.sql）
    INSERT INTO tmp_gas_verify_expected (phase, check_type, object_name, item_desc)
    SELECT '业务表','TABLE',t.name,'gas_schema 业务表' FROM (
        SELECT 'gas_buyer' AS name UNION ALL SELECT 'gas_buyer_merchant'
        UNION ALL SELECT 'gas_station' UNION ALL SELECT 'gas_gun'
        UNION ALL SELECT 'gas_gun_price' UNION ALL SELECT 'gas_config'
        UNION ALL SELECT 'gas_purchase_batch' UNION ALL SELECT 'gas_purchase_receipt'
        UNION ALL SELECT 'gas_purchase_payment' UNION ALL SELECT 'gas_inventory_pool'
        UNION ALL SELECT 'gas_inventory_ledger' UNION ALL SELECT 'gas_member'
        UNION ALL SELECT 'gas_staff_binding' UNION ALL SELECT 'gas_order'
        UNION ALL SELECT 'gas_order_log' UNION ALL SELECT 'gas_dynamic_token'
        UNION ALL SELECT 'gas_shift' UNION ALL SELECT 'gas_pay_txn'
        UNION ALL SELECT 'gas_refund' UNION ALL SELECT 'gas_settle_bill'
        UNION ALL SELECT 'gas_reconcile_diff' UNION ALL SELECT 'gas_coupon_template'
        UNION ALL SELECT 'gas_coupon' UNION ALL SELECT 'gas_remind'
        UNION ALL SELECT 'gas_sms_log'
    ) t;

    -- 4. 关键列：能识别旧版表结构/漏升级
    INSERT INTO tmp_gas_verify_expected (phase, check_type, object_name, item_desc) VALUES
    ('关键列','COLUMN','gas_station|code_flag','软删编码唯一辅助列'),
    ('关键列','COLUMN','gas_gun_price|cur_flag','开口价唯一辅助列'),
    ('关键列','COLUMN','gas_inventory_ledger|order_ref','订单流水唯一辅助列'),
    ('关键列','COLUMN','gas_inventory_pool|buyer_id','库存池采购商'),
    ('关键列','COLUMN','gas_inventory_pool|alert_armed','库存预警状态'),
    ('关键列','COLUMN','gas_purchase_batch|version','批次乐观锁'),
    ('关键列','COLUMN','gas_purchase_receipt|adjusted','批次调整量'),
    ('关键列','COLUMN','gas_purchase_receipt|version','到货版本'),
    ('关键列','COLUMN','gas_order|closed_at','订单关闭时间'),
    ('关键列','COLUMN','gas_order|close_reason','订单关闭原因'),
    ('关键列','COLUMN','gas_order|exception_note','订单异常说明'),
    ('关键列','COLUMN','gas_pay_txn|update_time','支付流水更新时间'),
    ('关键列','COLUMN','gas_member|phone_bound_at','手机号绑定时间'),
    ('关键列','COLUMN','gas_sms_log|client_ip','短信限频 IP'),
    ('关键列','COLUMN','gas_order|created_shift_id','代下单班次'),
    ('关键列','COLUMN','gas_order|completed_shift_id','核销班次'),
    ('关键列','COLUMN','gas_order|proxy_staff_id','代下单员工'),
    ('关键列','COLUMN','gas_order|redeem_staff_id','核销员工'),
    ('关键列','COLUMN','gas_order|request_id','主动下单幂等请求号'),
    ('关键列','COLUMN','gas_order|coupon_id','订单用券'),
    ('关键列','COLUMN','gas_order|discount_amount','订单优惠金额'),
    ('关键列','COLUMN','gas_coupon_template|buyer_id','发券采购商'),
    ('关键列','COLUMN','gas_coupon_template|valid_type','券有效期类型'),
    ('关键列','COLUMN','gas_coupon_template|valid_days','相对有效天数'),
    ('关键列','COLUMN','gas_coupon_template|valid_from','固定有效期开始'),
    ('关键列','COLUMN','gas_coupon_template|valid_to','固定有效期结束'),
    ('关键列','COLUMN','gas_coupon_template|total_qty','发放总量'),
    ('关键列','COLUMN','gas_coupon_template|issued_qty','已发放量'),
    ('关键列','COLUMN','gas_coupon|buyer_id','券归属采购商'),
    ('关键列','COLUMN','gas_coupon|used_order_id','用券订单'),
    ('关键列','COLUMN','gas_coupon|expire_at','券过期时间'),
    ('关键列','COLUMN','gas_shift|ended_by','交班操作人'),
    ('关键列','COLUMN','gas_shift|end_reason','强制交班原因'),
    ('关键列','COLUMN','gas_shift|proxy_count','班次代下单汇总'),
    ('关键列','COLUMN','gas_shift|redeem_count','班次核销汇总');

    -- 5. 关键唯一索引/普通索引
    INSERT INTO tmp_gas_verify_expected (phase, check_type, object_name, item_desc) VALUES
    ('关键索引','INDEX','gas_buyer_merchant|uk_buyer_channel','采购商通道唯一'),
    ('关键索引','INDEX','gas_buyer_merchant|uk_merchant_no','商户号唯一'),
    ('关键索引','INDEX','gas_station|uk_station_code','站点编码唯一'),
    ('关键索引','INDEX','gas_station|uk_qr_token','二维码标识唯一'),
    ('关键索引','INDEX','gas_station|uk_dept','站点部门唯一'),
    ('关键索引','INDEX','gas_gun|uk_station_gun','站内枪号唯一'),
    ('关键索引','INDEX','gas_gun_price|uk_gun_current','每枪一个开口价'),
    ('关键索引','INDEX','gas_purchase_batch|uk_batch_no','采购批次号唯一'),
    ('关键索引','INDEX','gas_inventory_pool|uk_station_gas','站点气种池唯一'),
    ('关键索引','INDEX','gas_inventory_ledger|uk_order_biz','订单流水幂等'),
    ('关键索引','INDEX','gas_member|uk_openid','会员 openid 唯一'),
    ('关键索引','INDEX','gas_member|uk_phone','会员手机号唯一'),
    ('关键索引','INDEX','gas_staff_binding|uk_user','员工绑定唯一'),
    ('关键索引','INDEX','gas_staff_binding|uk_openid','员工 openid 唯一'),
    ('关键索引','INDEX','gas_order|uk_order_no','订单号唯一'),
    ('关键索引','INDEX','gas_dynamic_token|uk_token','动态码唯一'),
    ('关键索引','INDEX','gas_shift|uk_station_active','每站一个当班'),
    ('关键索引','INDEX','gas_pay_txn|uk_out_trade_no','支付交易号唯一'),
    ('关键索引','INDEX','gas_pay_txn|uk_channel_trade_no','渠道交易号唯一'),
    ('关键索引','INDEX','gas_refund|uk_refund_no','退款单号唯一'),
    ('关键索引','INDEX','gas_settle_bill|uk_bill','账单明细唯一'),
    ('关键索引','INDEX','gas_remind|uk_remind','提醒防重唯一'),
    ('关键索引','INDEX','gas_purchase_receipt|idx_fifo','FIFO 索引'),
    ('关键索引','INDEX','gas_order|idx_status_expire','超时关单索引'),
    ('关键索引','INDEX','gas_order|idx_created_shift','代下单班次汇总索引'),
    ('关键索引','INDEX','gas_order|idx_completed_shift','核销班次汇总索引'),
    ('关键索引','INDEX','gas_order|uk_member_request','主动下单请求号唯一'),
    ('关键索引','INDEX','gas_coupon|idx_status_expire','券状态过期索引');

    -- 6. 业务参数键；值允许后台修改，键必须存在
    INSERT INTO tmp_gas_verify_expected (phase, check_type, object_name, item_desc)
    SELECT '业务参数','CONFIG',t.name,'参数键' FROM (
        SELECT 'order.waitPay.minutes' AS name UNION ALL SELECT 'order.waitConfirm.minutes'
        UNION ALL SELECT 'order.amount.max' UNION ALL SELECT 'token.refresh.seconds'
        UNION ALL SELECT 'token.ttl.seconds' UNION ALL SELECT 'sms.quiet.start'
        UNION ALL SELECT 'sms.quiet.end' UNION ALL SELECT 'sms.code.ttl.minutes'
        UNION ALL SELECT 'sms.code.interval.seconds' UNION ALL SELECT 'sms.code.daily.max'
        UNION ALL SELECT 'sms.code.ip.daily.max' UNION ALL SELECT 'sms.code.max.try'
        UNION ALL SELECT 'wx.appid' UNION ALL SELECT 'wx.secret'
        UNION ALL SELECT 'h5.jwt.secret' UNION ALL SELECT 'h5.jwt.expire.hours'
        UNION ALL SELECT 'h5.base.url'
    ) t;
    INSERT INTO tmp_gas_verify_expected (phase, check_type, object_name, fail_level, item_desc) VALUES
    ('升级清理','ABSENT_CONFIG','wx.mock-openid','[WARN]','旧 mock 开关应移除');

    -- 7. 字典类型与字典值
    INSERT INTO tmp_gas_verify_expected (phase, check_type, object_name, item_desc)
    SELECT '字典','DICT_TYPE',t.name,'业务字典类型' FROM (
        SELECT 'gas_type' AS name UNION ALL SELECT 'gas_order_status'
        UNION ALL SELECT 'gas_order_source' UNION ALL SELECT 'gas_pay_status'
        UNION ALL SELECT 'gas_invoice_status' UNION ALL SELECT 'gas_purchase_pay_method'
        UNION ALL SELECT 'gas_ledger_type' UNION ALL SELECT 'gas_refund_status'
        UNION ALL SELECT 'gas_coupon_status' UNION ALL SELECT 'gas_remind_type'
    ) t;
    INSERT INTO tmp_gas_verify_expected (phase, check_type, object_name, item_desc)
    SELECT '字典','DICT_VALUE',t.name,'业务字典值' FROM (
        SELECT 'gas_type|CNG' AS name UNION ALL SELECT 'gas_type|LNG'
        UNION ALL SELECT 'gas_order_status|WAIT_CONFIRM' UNION ALL SELECT 'gas_order_status|WAIT_PAY'
        UNION ALL SELECT 'gas_order_status|PAID' UNION ALL SELECT 'gas_order_status|COMPLETED'
        UNION ALL SELECT 'gas_order_status|REJECTED' UNION ALL SELECT 'gas_order_status|CLOSED'
        UNION ALL SELECT 'gas_order_status|REFUNDING' UNION ALL SELECT 'gas_order_status|REFUNDED'
        UNION ALL SELECT 'gas_order_source|SELF' UNION ALL SELECT 'gas_order_source|PROXY'
        UNION ALL SELECT 'gas_pay_status|UNPAID' UNION ALL SELECT 'gas_pay_status|PARTIAL'
        UNION ALL SELECT 'gas_pay_status|PAID'
        UNION ALL SELECT 'gas_invoice_status|UNINVOICED' UNION ALL SELECT 'gas_invoice_status|INVOICED'
        UNION ALL SELECT 'gas_purchase_pay_method|BANK' UNION ALL SELECT 'gas_purchase_pay_method|ACCEPTANCE'
        UNION ALL SELECT 'gas_purchase_pay_method|CASH' UNION ALL SELECT 'gas_purchase_pay_method|OTHER'
        UNION ALL SELECT 'gas_ledger_type|IN' UNION ALL SELECT 'gas_ledger_type|RESERVE'
        UNION ALL SELECT 'gas_ledger_type|RELEASE' UNION ALL SELECT 'gas_ledger_type|CONSUME'
        UNION ALL SELECT 'gas_ledger_type|ADJUST'
        UNION ALL SELECT 'gas_refund_status|PROCESSING' UNION ALL SELECT 'gas_refund_status|SUCCESS'
        UNION ALL SELECT 'gas_refund_status|FAILED'
        UNION ALL SELECT 'gas_coupon_status|UNUSED' UNION ALL SELECT 'gas_coupon_status|USED'
        UNION ALL SELECT 'gas_coupon_status|EXPIRED'
        UNION ALL SELECT 'gas_remind_type|BATCH_NEAR_EMPTY' UNION ALL SELECT 'gas_remind_type|BATCH_EMPTY'
        UNION ALL SELECT 'gas_remind_type|BATCH_UNINVOICED' UNION ALL SELECT 'gas_remind_type|STOCK_ALERT'
    ) t;
    INSERT INTO tmp_gas_verify_expected (phase, check_type, object_name, fail_level, item_desc)
    SELECT '升级清理','ABSENT_DICT',t.name,'[WARN]','旧字典值应修正' FROM (
        SELECT 'gas_remind_type|INVOICE_NEAR' AS name UNION ALL SELECT 'gas_remind_type|INVOICE_DONE'
        UNION ALL SELECT 'gas_remind_type|INVOICE_OVERDUE' UNION ALL SELECT 'gas_remind_type|STOCK_LOW'
    ) t;

    -- 8. 菜单目录与阶段2/3页面、按钮（object=menu_id，expected=perms）
    INSERT INTO tmp_gas_verify_expected (phase, check_type, object_name, expected_value, item_desc) VALUES
    ('菜单目录','MENU','5000','','基础资料'),('菜单目录','MENU','5100','','采购与库存'),
    ('菜单目录','MENU','5200','','销售订单'),('菜单目录','MENU','5300','','营销'),
    ('菜单目录','MENU','5400','','财务'),('菜单目录','MENU','5500','','班次'),
    ('菜单目录','MENU','5600','','消息提醒'),
    ('阶段2菜单','MENU','5001','gas:buyer:list','采购商'),('阶段2菜单','MENU','5002','gas:station:list','站点'),
    ('阶段2菜单','MENU','5003','gas:gun:list','枪号'),('阶段2菜单','MENU','5004','gas:price:list','枪价'),
    ('阶段2菜单','MENU','5005','gas:config:list','业务参数'),
    ('阶段2菜单','MENU','5011','gas:buyer:query','采购商查询'),('阶段2菜单','MENU','5012','gas:buyer:add','采购商新增'),
    ('阶段2菜单','MENU','5013','gas:buyer:edit','采购商修改'),('阶段2菜单','MENU','5014','gas:buyer:remove','采购商删除'),
    ('阶段2菜单','MENU','5015','gas:buyer:export','采购商导出'),
    ('阶段2菜单','MENU','5021','gas:station:query','站点查询'),('阶段2菜单','MENU','5022','gas:station:add','站点新增'),
    ('阶段2菜单','MENU','5023','gas:station:edit','站点修改'),('阶段2菜单','MENU','5024','gas:station:remove','站点删除'),
    ('阶段2菜单','MENU','5025','gas:station:export','站点导出'),
    ('阶段2菜单','MENU','5031','gas:gun:query','枪号查询'),('阶段2菜单','MENU','5032','gas:gun:add','枪号新增'),
    ('阶段2菜单','MENU','5033','gas:gun:edit','枪号修改'),('阶段2菜单','MENU','5034','gas:gun:remove','枪号删除'),
    ('阶段2菜单','MENU','5035','gas:gun:export','枪号导出'),
    ('阶段2菜单','MENU','5041','gas:price:query','枪价查询'),('阶段2菜单','MENU','5042','gas:price:add','枪价新增'),
    ('阶段2菜单','MENU','5043','gas:price:remove','枪价删除'),
    ('阶段2菜单','MENU','5051','gas:config:query','参数查询'),('阶段2菜单','MENU','5052','gas:config:add','参数新增'),
    ('阶段2菜单','MENU','5053','gas:config:edit','参数修改'),('阶段2菜单','MENU','5054','gas:config:remove','参数删除'),
    ('阶段3菜单','MENU','5101','gas:batch:list','采购批次'),('阶段3菜单','MENU','5102','gas:receipt:list','到货入库'),
    ('阶段3菜单','MENU','5103','gas:payment:list','付款登记'),('阶段3菜单','MENU','5104','gas:inventory:list','库存台账'),
    ('阶段3菜单','MENU','5601','gas:remind:list','提醒记录'),
    ('阶段3菜单','MENU','5111','gas:batch:query','批次查询'),('阶段3菜单','MENU','5112','gas:batch:add','批次新增'),
    ('阶段3菜单','MENU','5113','gas:batch:edit','批次修改'),('阶段3菜单','MENU','5114','gas:batch:remove','批次删除'),
    ('阶段3菜单','MENU','5115','gas:batch:export','批次导出'),('阶段3菜单','MENU','5116','gas:batch:invoice','票据状态'),
    ('阶段3菜单','MENU','5121','gas:receipt:query','到货查询'),('阶段3菜单','MENU','5122','gas:receipt:add','到货登记'),
    ('阶段3菜单','MENU','5123','gas:receipt:export','到货导出'),
    ('阶段3菜单','MENU','5131','gas:payment:query','付款查询'),('阶段3菜单','MENU','5132','gas:payment:add','付款登记'),
    ('阶段3菜单','MENU','5133','gas:payment:remove','付款删除'),('阶段3菜单','MENU','5134','gas:payment:export','付款导出'),
    ('阶段3菜单','MENU','5141','gas:inventory:query','台账查询'),('阶段3菜单','MENU','5142','gas:inventory:adjust','库存调整'),
    ('阶段3菜单','MENU','5143','gas:inventory:alert','预警设置'),('阶段3菜单','MENU','5611','gas:remind:query','提醒查询'),
    ('阶段6菜单','MENU','5501','gas:shift:list','班次记录'),
    ('阶段6菜单','MENU','5511','gas:shift:query','班次查询'),
    ('阶段6菜单','MENU','5512','gas:shift:force','强制交班'),
    ('阶段7菜单','MENU','5301','gas:coupon:list','优惠券管理'),
    ('阶段7菜单','MENU','5311','gas:coupon:add','模板新增'),
    ('阶段7菜单','MENU','5312','gas:coupon:edit','模板编辑'),
    ('阶段7菜单','MENU','5313','gas:coupon:grant','定向发券');

    -- 9. 角色、关键授权与角色授权数量（数量被后台定制时只给 WARN）
    INSERT INTO tmp_gas_verify_expected (phase, check_type, object_name, expected_value, item_desc) VALUES
    ('阶段1角色','ROLE','100','gas_ops','运营'),('阶段1角色','ROLE','101','gas_finance','财务'),
    ('阶段1角色','ROLE','102','gas_station_admin','站点管理员'),('阶段1角色','ROLE','103','gas_staff','加气员'),
    ('角色授权','ROLE_MENU','100|5000','','运营-基础资料'),
    ('角色授权','ROLE_MENU','100|5100','','运营-采购库存'),
    ('角色授权','ROLE_MENU','100|5035','','运营-枪号导出'),
    ('角色授权','ROLE_MENU','102|5104','','站点管理员-库存台账'),
    ('角色授权','ROLE_MENU','100|5501','','运营-班次记录'),('角色授权','ROLE_MENU','100|5511','','运营-班次查询'),
    ('角色授权','ROLE_MENU','100|5512','','运营-强制交班'),('角色授权','ROLE_MENU','102|5501','','站管-班次记录'),
    ('角色授权','ROLE_MENU','102|5511','','站管-班次查询'),('角色授权','ROLE_MENU','102|5512','','站管-强制交班'),
    ('角色授权','ROLE_MENU','100|5301','','运营-优惠券管理'),('角色授权','ROLE_MENU','100|5311','','运营-模板新增'),
    ('角色授权','ROLE_MENU','100|5312','','运营-模板编辑'),('角色授权','ROLE_MENU','100|5313','','运营-定向发券');
    INSERT INTO tmp_gas_verify_expected (phase, check_type, object_name, expected_value, fail_level, item_desc) VALUES
    ('角色授权','ROLE_MENU_COUNT','100','62','[WARN]','运营授权总数'),
    ('角色授权','ROLE_MENU_COUNT','101','23','[WARN]','财务授权总数'),
    ('角色授权','ROLE_MENU_COUNT','102','12','[WARN]','站点管理员授权总数'),
    ('角色授权','ROLE_MENU_COUNT','103','0','[WARN]','加气员后台授权总数');

    -- 10. 定时任务
    INSERT INTO tmp_gas_verify_expected (phase, check_type, object_name, item_desc) VALUES
    ('定时任务','JOB','gasRemindTask.scanDaily()','每日提醒扫描'),
    ('定时任务','JOB','gasOrderTask.closeExpired()','订单超时关单');

    OPEN cur;
    verify_loop: LOOP
        FETCH cur INTO v_id, v_phase, v_type, v_object, v_expected, v_level, v_desc;
        IF v_done = 1 THEN LEAVE verify_loop; END IF;
        SET v_actual = 0;

        IF v_type = 'TABLE' THEN
            SELECT COUNT(*) INTO v_actual FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_object;
        ELSEIF v_type = 'COLUMN' THEN
            SELECT COUNT(*) INTO v_actual FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = SUBSTRING_INDEX(v_object, '|', 1)
              AND COLUMN_NAME = SUBSTRING_INDEX(v_object, '|', -1);
        ELSEIF v_type = 'INDEX' THEN
            SELECT COUNT(DISTINCT INDEX_NAME) INTO v_actual FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = SUBSTRING_INDEX(v_object, '|', 1)
              AND INDEX_NAME = SUBSTRING_INDEX(v_object, '|', -1);
        ELSEIF v_type = 'CONFIG' THEN
            SET @verify_sql = CONCAT('SELECT COUNT(*) INTO @verify_count FROM gas_config WHERE config_key = ''', v_object, '''');
            PREPARE stmt FROM @verify_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
            SET v_actual = COALESCE(@verify_count, 0);
        ELSEIF v_type = 'ABSENT_CONFIG' THEN
            SET @verify_sql = CONCAT('SELECT COUNT(*) INTO @verify_count FROM gas_config WHERE config_key = ''', v_object, '''');
            PREPARE stmt FROM @verify_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
            SET v_actual = COALESCE(@verify_count, 0);
        ELSEIF v_type = 'DICT_TYPE' THEN
            SET @verify_sql = CONCAT('SELECT COUNT(*) INTO @verify_count FROM sys_dict_type WHERE dict_type = ''', v_object, '''');
            PREPARE stmt FROM @verify_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
            SET v_actual = COALESCE(@verify_count, 0);
        ELSEIF v_type IN ('DICT_VALUE', 'ABSENT_DICT') THEN
            SET @verify_sql = CONCAT('SELECT COUNT(*) INTO @verify_count FROM sys_dict_data WHERE dict_type = ''',
                SUBSTRING_INDEX(v_object, '|', 1), ''' AND dict_value = ''', SUBSTRING_INDEX(v_object, '|', -1), '''');
            PREPARE stmt FROM @verify_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
            SET v_actual = COALESCE(@verify_count, 0);
        ELSEIF v_type = 'MENU' THEN
            SET @verify_sql = CONCAT('SELECT COUNT(*) INTO @verify_count FROM sys_menu WHERE menu_id = ', CAST(v_object AS UNSIGNED),
                ' AND perms = ''', v_expected, '''');
            PREPARE stmt FROM @verify_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
            SET v_actual = COALESCE(@verify_count, 0);
        ELSEIF v_type = 'USER_ID' THEN
            SET @verify_sql = CONCAT('SELECT COUNT(*) INTO @verify_count FROM sys_user WHERE user_id = ', CAST(v_object AS UNSIGNED),
                ' AND login_name = ''', v_expected, '''');
            PREPARE stmt FROM @verify_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
            SET v_actual = COALESCE(@verify_count, 0);
        ELSEIF v_type = 'ROLE' THEN
            IF v_object REGEXP '^[0-9]+$' THEN
                SET @verify_sql = CONCAT('SELECT COUNT(*) INTO @verify_count FROM sys_role WHERE role_id = ', CAST(v_object AS UNSIGNED),
                    ' AND role_key = ''', v_expected, '''');
            ELSE
                SET @verify_sql = CONCAT('SELECT COUNT(*) INTO @verify_count FROM sys_role WHERE role_key = ''', v_object, '''');
            END IF;
            PREPARE stmt FROM @verify_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
            SET v_actual = COALESCE(@verify_count, 0);
        ELSEIF v_type = 'ROLE_MENU' THEN
            SET @verify_sql = CONCAT('SELECT COUNT(*) INTO @verify_count FROM sys_role_menu WHERE role_id = ',
                CAST(SUBSTRING_INDEX(v_object, '|', 1) AS UNSIGNED), ' AND menu_id = ',
                CAST(SUBSTRING_INDEX(v_object, '|', -1) AS UNSIGNED));
            PREPARE stmt FROM @verify_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
            SET v_actual = COALESCE(@verify_count, 0);
        ELSEIF v_type = 'ROLE_MENU_COUNT' THEN
            SET @verify_sql = CONCAT('SELECT COUNT(*) INTO @verify_count FROM sys_role_menu WHERE role_id = ', CAST(v_object AS UNSIGNED));
            PREPARE stmt FROM @verify_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
            SET v_actual = COALESCE(@verify_count, 0);
        ELSEIF v_type = 'JOB' THEN
            SET @verify_sql = CONCAT('SELECT COUNT(*) INTO @verify_count FROM sys_job WHERE invoke_target = ''', v_object, '''');
            PREPARE stmt FROM @verify_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
            SET v_actual = COALESCE(@verify_count, 0);
        END IF;

        IF v_type IN ('ABSENT_CONFIG', 'ABSENT_DICT') THEN
            SET v_status = IF(v_actual = 0, '[OK]', '[WARN]');
        ELSEIF v_type = 'ROLE_MENU_COUNT' THEN
            SET v_status = IF(v_actual = CAST(v_expected AS UNSIGNED), '[OK]', '[WARN]');
        ELSE
            SET v_status = IF(v_actual >= 1, '[OK]', v_level);
        END IF;
        INSERT INTO tmp_gas_verify_result VALUES
            (v_id, v_phase, v_desc, v_object, v_expected, v_actual, v_status);
    END LOOP;
    CLOSE cur;

    -- 第一张结果：总结。缺失数=0 即关键导入项完整；警告不阻断运行。
    SELECT IF(SUM(status = '[MISSING]') = 0,
              '[OK] 所有关键导入项均已存在',
              '[MISSING] 存在关键缺失，请查看下方明细') AS 核验结论,
           COUNT(*) AS 核验总数,
           SUM(status = '[OK]') AS 通过数,
           SUM(status = '[MISSING]') AS 缺失数,
           SUM(status = '[WARN]') AS 警告数,
           DATABASE() AS 当前数据库
    FROM tmp_gas_verify_result;

    -- 第二张结果：逐项明细。重点筛选 [MISSING]；actual_count=0 表示未匹配到。
    SELECT check_id AS 序号, phase AS 阶段, item_desc AS 核验项,
           object_name AS 对象, expected_value AS 期望值,
           actual_count AS 匹配数, status AS 状态
    FROM tmp_gas_verify_result ORDER BY check_id;

    DROP TEMPORARY TABLE tmp_gas_verify_expected;
    DROP TEMPORARY TABLE tmp_gas_verify_result;
END$$

DELIMITER ;
CALL gas_verify_import();
DROP PROCEDURE IF EXISTS gas_verify_import;
