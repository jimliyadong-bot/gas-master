-- ----------------------------------------------------------------------------
-- 加气站购销系统 · 阶段3：采购与库存菜单与授权
-- 依据：docs/Dev_Plan.md §2 阶段3、§4.1 管理后台菜单
-- 前置：已执行 gas_schema.sql（已建目录 5000-5600）、gas_phase2_menu.sql（已用 5001-5005、5011-5054）
-- 注意：请勿重复执行（重复插入会主键冲突）
-- ----------------------------------------------------------------------------

-- 1. 页面菜单
insert into sys_menu (menu_id, menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, remark) values
(5101, '采购批次',   5100, 1, 'gas/purchase/batch',   '', 'C', '0', '1', 'gas:batch:list',     'fa fa-shopping-cart', 'admin', sysdate(), '采购批次、票据状态维护'),
(5102, '到货入库',   5100, 2, 'gas/purchase/receipt', '', 'C', '0', '1', 'gas:receipt:list',   'fa fa-truck',         'admin', sysdate(), '到货登记，入库增加站点库存'),
(5103, '付款登记',   5100, 3, 'gas/purchase/payment', '', 'C', '0', '1', 'gas:payment:list',   'fa fa-credit-card',   'admin', sysdate(), '采购付款只登记，无审批'),
(5104, '库存台账',   5100, 4, 'gas/inventory',        '', 'C', '0', '1', 'gas:inventory:list', 'fa fa-cubes',         'admin', sysdate(), '站点库存池、批次余量、流水、调整、预警'),
(5601, '提醒记录',   5600, 1, 'gas/remind',           '', 'C', '0', '1', 'gas:remind:list',    'fa fa-bell',          'admin', sysdate(), '开票提醒、库存预警记录');

-- 2. 按钮权限
insert into sys_menu (menu_id, menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, remark) values
-- 采购批次
(5111, '批次查询', 5101, 1, '#', '', 'F', '0', '1', 'gas:batch:query',  '#', 'admin', sysdate(), ''),
(5112, '批次新增', 5101, 2, '#', '', 'F', '0', '1', 'gas:batch:add',    '#', 'admin', sysdate(), ''),
(5113, '批次修改', 5101, 3, '#', '', 'F', '0', '1', 'gas:batch:edit',   '#', 'admin', sysdate(), ''),
(5114, '批次删除', 5101, 4, '#', '', 'F', '0', '1', 'gas:batch:remove', '#', 'admin', sysdate(), ''),
(5115, '批次导出', 5101, 5, '#', '', 'F', '0', '1', 'gas:batch:export', '#', 'admin', sysdate(), ''),
(5116, '票据状态', 5101, 6, '#', '', 'F', '0', '1', 'gas:batch:invoice','#', 'admin', sysdate(), ''),
-- 到货入库
(5121, '到货查询', 5102, 1, '#', '', 'F', '0', '1', 'gas:receipt:query',  '#', 'admin', sysdate(), ''),
(5122, '到货登记', 5102, 2, '#', '', 'F', '0', '1', 'gas:receipt:add',    '#', 'admin', sysdate(), ''),
(5123, '到货导出', 5102, 3, '#', '', 'F', '0', '1', 'gas:receipt:export', '#', 'admin', sysdate(), ''),
-- 付款登记
(5131, '付款查询', 5103, 1, '#', '', 'F', '0', '1', 'gas:payment:query',  '#', 'admin', sysdate(), ''),
(5132, '付款登记', 5103, 2, '#', '', 'F', '0', '1', 'gas:payment:add',    '#', 'admin', sysdate(), ''),
(5133, '付款删除', 5103, 3, '#', '', 'F', '0', '1', 'gas:payment:remove', '#', 'admin', sysdate(), ''),
(5134, '付款导出', 5103, 4, '#', '', 'F', '0', '1', 'gas:payment:export', '#', 'admin', sysdate(), ''),
-- 库存台账
(5141, '台账查询',   5104, 1, '#', '', 'F', '0', '1', 'gas:inventory:query',  '#', 'admin', sysdate(), ''),
(5142, '库存调整',   5104, 2, '#', '', 'F', '0', '1', 'gas:inventory:adjust', '#', 'admin', sysdate(), ''),
(5143, '预警设置',   5104, 3, '#', '', 'F', '0', '1', 'gas:inventory:alert',  '#', 'admin', sysdate(), ''),
-- 提醒记录
(5611, '提醒查询', 5601, 1, '#', '', 'F', '0', '1', 'gas:remind:query', '#', 'admin', sysdate(), '');

-- 3. 角色授权（目录 5100/5600 已在 gas_phase1_org.sql 授权）
insert into sys_role_menu (role_id, menu_id) values
-- 运营（100）：采购与库存全部页面与按钮 + 提醒
(100, 5101), (100, 5102), (100, 5103), (100, 5104), (100, 5601),
(100, 5111), (100, 5112), (100, 5113), (100, 5114), (100, 5115), (100, 5116),
(100, 5121), (100, 5122), (100, 5123),
(100, 5131), (100, 5132), (100, 5133), (100, 5134),
(100, 5141), (100, 5142), (100, 5143),
(100, 5611),
-- 财务（101）：批次/到货/台账只读，付款登记可维护，票据状态可维护
(101, 5101), (101, 5102), (101, 5103), (101, 5104), (101, 5601),
(101, 5111), (101, 5116),
(101, 5121),
(101, 5131), (101, 5132), (101, 5133), (101, 5134),
(101, 5141),
(101, 5611),
-- 站点管理员（102）：本站批次/到货/台账查看（数据权限后续阶段统一接入 @DataScope）
(102, 5101), (102, 5102), (102, 5104),
(102, 5111), (102, 5121), (102, 5141);

-- 4. 提醒定时任务（每天 9:00：用完未开票、库存预警、发送待发提醒）
insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark) values
('开票提醒与库存预警扫描', 'DEFAULT', 'gasRemindTask.scanDaily()', '0 0 9 * * ?', '3', '1', '0', 'admin', sysdate(), 'Dev_Plan §3.7：用完未开票提醒、库存预警补查、发送到点提醒');
