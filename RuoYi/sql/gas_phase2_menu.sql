-- ----------------------------------------------------------------------------
-- 加气站购销系统 · 阶段2：基础资料菜单与授权
-- 依据：docs/Dev_Plan.md §2 阶段2、§4.1 管理后台菜单
-- 前置：已执行 ry_20260319.sql、quartz.sql、gas_schema.sql、gas_phase1_org.sql
-- 注意：请勿重复执行（重复插入会主键冲突）
-- ----------------------------------------------------------------------------

-- 1. 页面菜单（挂 5000 基础资料目录下）
insert into sys_menu (menu_id, menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, remark) values
(5001, '采购商',   5000, 1, 'gas/buyer',   '', 'C', '0', '1', 'gas:buyer:list',   'fa fa-building-o', 'admin', sysdate(), '采购商档案、拉卡拉商户配置、通知手机号'),
(5002, '站点',     5000, 2, 'gas/station', '', 'C', '0', '1', 'gas:station:list', 'fa fa-map-marker', 'admin', sysdate(), '站点管理、建站自动建部门、二维码下载'),
(5003, '枪号',     5000, 3, 'gas/gun',     '', 'C', '0', '1', 'gas:gun:list',     'fa fa-bolt',       'admin', sysdate(), '站点枪号与气种'),
(5004, '枪价',     5000, 4, 'gas/price',   '', 'C', '0', '1', 'gas:price:list',   'fa fa-money',      'admin', sysdate(), '按枪销售价格（生效时间，新价自动结旧价）'),
(5005, '业务参数', 5000, 5, 'gas/config',  '', 'C', '0', '1', 'gas:config:list',  'fa fa-sliders',    'admin', sysdate(), '订单超时、码有效期等业务参数');

-- 2. 按钮权限
insert into sys_menu (menu_id, menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, remark) values
-- 采购商
(5011, '采购商查询', 5001, 1, '#', '', 'F', '0', '1', 'gas:buyer:query',  '#', 'admin', sysdate(), ''),
(5012, '采购商新增', 5001, 2, '#', '', 'F', '0', '1', 'gas:buyer:add',    '#', 'admin', sysdate(), ''),
(5013, '采购商修改', 5001, 3, '#', '', 'F', '0', '1', 'gas:buyer:edit',   '#', 'admin', sysdate(), ''),
(5014, '采购商删除', 5001, 4, '#', '', 'F', '0', '1', 'gas:buyer:remove', '#', 'admin', sysdate(), ''),
(5015, '采购商导出', 5001, 5, '#', '', 'F', '0', '1', 'gas:buyer:export', '#', 'admin', sysdate(), ''),
-- 站点
(5021, '站点查询', 5002, 1, '#', '', 'F', '0', '1', 'gas:station:query',  '#', 'admin', sysdate(), ''),
(5022, '站点新增', 5002, 2, '#', '', 'F', '0', '1', 'gas:station:add',    '#', 'admin', sysdate(), ''),
(5023, '站点修改', 5002, 3, '#', '', 'F', '0', '1', 'gas:station:edit',   '#', 'admin', sysdate(), ''),
(5024, '站点删除', 5002, 4, '#', '', 'F', '0', '1', 'gas:station:remove', '#', 'admin', sysdate(), ''),
(5025, '站点导出', 5002, 5, '#', '', 'F', '0', '1', 'gas:station:export', '#', 'admin', sysdate(), ''),
-- 枪号
(5031, '枪号查询', 5003, 1, '#', '', 'F', '0', '1', 'gas:gun:query',  '#', 'admin', sysdate(), ''),
(5032, '枪号新增', 5003, 2, '#', '', 'F', '0', '1', 'gas:gun:add',    '#', 'admin', sysdate(), ''),
(5033, '枪号修改', 5003, 3, '#', '', 'F', '0', '1', 'gas:gun:edit',   '#', 'admin', sysdate(), ''),
(5034, '枪号删除', 5003, 4, '#', '', 'F', '0', '1', 'gas:gun:remove', '#', 'admin', sysdate(), ''),
(5035, '枪号导出', 5003, 5, '#', '', 'F', '0', '1', 'gas:gun:export', '#', 'admin', sysdate(), ''),
-- 枪价
(5041, '枪价查询', 5004, 1, '#', '', 'F', '0', '1', 'gas:price:query',  '#', 'admin', sysdate(), ''),
(5042, '枪价新增', 5004, 2, '#', '', 'F', '0', '1', 'gas:price:add',    '#', 'admin', sysdate(), ''),
(5043, '枪价删除', 5004, 3, '#', '', 'F', '0', '1', 'gas:price:remove', '#', 'admin', sysdate(), ''),
-- 业务参数
(5051, '参数查询', 5005, 1, '#', '', 'F', '0', '1', 'gas:config:query',  '#', 'admin', sysdate(), ''),
(5052, '参数新增', 5005, 2, '#', '', 'F', '0', '1', 'gas:config:add',    '#', 'admin', sysdate(), ''),
(5053, '参数修改', 5005, 3, '#', '', 'F', '0', '1', 'gas:config:edit',   '#', 'admin', sysdate(), ''),
(5054, '参数删除', 5005, 4, '#', '', 'F', '0', '1', 'gas:config:remove', '#', 'admin', sysdate(), '');

-- 3. 角色授权
insert into sys_role_menu (role_id, menu_id) values
-- 运营（100）：基础资料全部页面与按钮
(100, 5001), (100, 5002), (100, 5003), (100, 5004), (100, 5005),
(100, 5011), (100, 5012), (100, 5013), (100, 5014), (100, 5015),
(100, 5021), (100, 5022), (100, 5023), (100, 5024), (100, 5025),
(100, 5031), (100, 5032), (100, 5033), (100, 5034), (100, 5035),
(100, 5041), (100, 5042), (100, 5043),
(100, 5051), (100, 5052), (100, 5053), (100, 5054),
-- 财务（101）：采购商/站点只读（对账需要归属信息）
(101, 5001), (101, 5002), (101, 5011), (101, 5021);

-- 4. H5 基础地址参数（站点二维码内容前缀，部署后改为正式域名）
insert into gas_config (config_key, config_value, update_by, update_time, remark) values
('h5.base.url', 'http://localhost:5300', 'admin', sysdate(), '公众号 H5 基础地址，站点二维码内容 = 该地址 + /station/二维码标识；部署后改为正式域名');
