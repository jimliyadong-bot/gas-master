-- ----------------------------------------------------------------------------
-- 加气站购销系统 · 阶段4-5 存量库升级脚本
-- 适用：已执行过 ry_20260319.sql / quartz.sql / gas_schema.sql / gas_phase1_org.sql /
--       gas_phase2_menu.sql / gas_phase3_menu.sql 的既有环境，升级到阶段5完成态。
-- 全新安装请直接按部署手册顺序执行最新 gas_schema.sql 等文件，无需本脚本。
-- 幂等：可重复执行。
-- ----------------------------------------------------------------------------

-- 1. 业务参数（阶段4/5 新增；不存在则插入）
insert into gas_config (config_key, config_value, update_time, remark)
select 'order.amount.max', '99999', sysdate(), '单笔下单金额上限（元）' from dual
where not exists (select 1 from gas_config where config_key = 'order.amount.max');

insert into gas_config (config_key, config_value, update_time, remark)
select 'sms.code.ip.daily.max', '30', sysdate(), '同一IP每日验证码上限' from dual
where not exists (select 1 from gas_config where config_key = 'sms.code.ip.daily.max');

insert into gas_config (config_key, config_value, update_time, remark)
select 'sms.code.max.try', '5', sysdate(), '验证码校验失败次数上限（超限作废）' from dual
where not exists (select 1 from gas_config where config_key = 'sms.code.max.try');

insert into gas_config (config_key, config_value, update_time, remark)
select 'wx.appid', '', sysdate(), '公众号 AppID（网页授权/JS-SDK）' from dual
where not exists (select 1 from gas_config where config_key = 'wx.appid');

insert into gas_config (config_key, config_value, update_time, remark)
select 'wx.secret', '', sysdate(), '公众号 AppSecret（建议生产改用环境变量覆盖）' from dual
where not exists (select 1 from gas_config where config_key = 'wx.secret');

insert into gas_config (config_key, config_value, update_time, remark)
select 'h5.jwt.secret', '', sysdate(), 'H5 端 JWT 签名密钥（生产仅用环境变量 GAS_H5_JWT_SECRET，至少32字节）' from dual
where not exists (select 1 from gas_config where config_key = 'h5.jwt.secret');

-- 清除历史版本写入的公开默认密钥；已有自定义密钥保留，生产仍建议迁移到环境变量
update gas_config set config_value = '', update_time = sysdate()
where config_key = 'h5.jwt.secret' and config_value = 'gas-h5-jwt-secret-please-change-in-production';

insert into gas_config (config_key, config_value, update_time, remark)
select 'h5.jwt.expire.hours', '168', sysdate(), 'H5 端 JWT 有效期（小时，默认7天）' from dual
where not exists (select 1 from gas_config where config_key = 'h5.jwt.expire.hours');

-- 2. 移除数据驱动的 mock 开关（阶段4 review 安全修复：改用启动参数 gas.h5.mock-mode）
delete from gas_config where config_key = 'wx.mock-openid';

-- 3. 阶段3 review 字典值修正（旧值 INVOICE_*/STOCK_LOW → 代码常量 BATCH_*/STOCK_ALERT）
update sys_dict_data set dict_value = 'BATCH_NEAR_EMPTY' where dict_type = 'gas_remind_type' and dict_value = 'INVOICE_NEAR';
update sys_dict_data set dict_value = 'BATCH_EMPTY'      where dict_type = 'gas_remind_type' and dict_value = 'INVOICE_DONE';
update sys_dict_data set dict_value = 'BATCH_UNINVOICED' where dict_type = 'gas_remind_type' and dict_value = 'INVOICE_OVERDUE';
update sys_dict_data set dict_value = 'STOCK_ALERT'      where dict_type = 'gas_remind_type' and dict_value = 'STOCK_LOW';
update gas_remind set remind_type = 'BATCH_NEAR_EMPTY' where remind_type = 'INVOICE_NEAR';
update gas_remind set remind_type = 'BATCH_EMPTY'      where remind_type = 'INVOICE_DONE';
update gas_remind set remind_type = 'BATCH_UNINVOICED' where remind_type = 'INVOICE_OVERDUE';
update gas_remind set remind_type = 'STOCK_ALERT'      where remind_type = 'STOCK_LOW';

-- 4. 枪号导出权限（阶段全量 review 补齐）
insert into sys_menu (menu_id, menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, remark)
select 5035, '枪号导出', 5003, 5, '#', '', 'F', '0', '1', 'gas:gun:export', '#', 'admin', sysdate(), ''
from dual where not exists (select 1 from sys_menu where menu_id = 5035);
insert into sys_role_menu (role_id, menu_id)
select 100, 5035 from dual where not exists (select 1 from sys_role_menu where role_id = 100 and menu_id = 5035);

-- 5. 定时任务：订单超时关单（阶段5，sys_job 101）
insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select '订单超时关单', 'DEFAULT', 'gasOrderTask.closeExpired()', '0 */5 * * * ?', '3', '1', '0', 'admin', sysdate(), '待支付订单超时自动关闭'
from dual
where not exists (select 1 from sys_job where invoke_target = 'gasOrderTask.closeExpired()');
