-- ----------------------------------------------------------------------------
-- 加气站购销系统 · 阶段1：部门树与角色初始化
-- 依据：docs/Dev_Plan.md §2 阶段1 第5条、§1.1 组织与数据隔离
-- 前置：已依次执行 ry_20260319.sql、quartz.sql、gas_schema.sql
-- 说明：根部门暂用通用名“采购方”，之后可在 后台→系统管理→部门 改为实际公司名；
--       站点部门在阶段2实现“建站自动建部门”后自动挂到根部门下。
-- 注意：如已通过后台手工调整过组织或角色，请勿重复执行本脚本。
-- ----------------------------------------------------------------------------

-- 1. 根部门改为“采购方”，清理若依演示数据
--    （站点建为根部门的子部门，站点管理员/加气员挂在站点部门下；sys_dept 无 remark 字段）
update sys_dept set dept_name = '采购方', leader = '', phone = '', email = ''
    where dept_id = 100;

-- admin 移到根部门（原挂在演示部门 103）
update sys_user set dept_id = 100 where user_id = 1;

-- 删除演示用户 ry 及其关联
delete from sys_user_role where user_id = 2;
delete from sys_user_post where user_id = 2;
delete from sys_user where user_id = 2;

-- 删除若依演示部门（101-109：深圳总公司/长沙分公司及其子部门）
delete from sys_role_dept where dept_id between 101 and 109;
delete from sys_dept where dept_id between 101 and 109;

-- 2. 删除演示角色“普通角色”及其关联
delete from sys_role_menu where role_id = 2;
delete from sys_role_dept where role_id = 2;
delete from sys_user_role where role_id = 2;
delete from sys_role where role_id = 2;

-- 3. 删除“若依官网”演示菜单（menu_id=4），保留系统管理/监控/工具
delete from sys_menu where menu_id = 4;

-- 4. 业务角色（role_id 从 100 起，与 auto_increment=100 衔接）
--    data_scope：1=全部数据权限（运营/财务），3=本部门数据权限（站点管理员/加气员）
insert into sys_role (role_id, role_name, role_key, role_sort, data_scope, status, del_flag, create_by, create_time, remark) values
(100, '运营',       'gas_ops',           3, '1', '0', '0', 'admin', sysdate(), '采购方运营：全部数据，可按采购商筛选'),
(101, '财务',       'gas_finance',       4, '1', '0', '0', 'admin', sysdate(), '采购方财务：全部数据，可按采购商筛选'),
(102, '站点管理员', 'gas_station_admin', 5, '3', '0', '0', 'admin', sysdate(), '数据权限：本部门（本站）；主要经 H5 员工入口使用'),
(103, '加气员',     'gas_staff',         6, '3', '0', '0', 'admin', sysdate(), '数据权限：本部门（本站）；纯 H5 角色，无后台菜单');

-- 5. 角色-菜单授权（菜单目录 5000-5600 由 gas_schema.sql 创建；
--    具体页面/按钮菜单在阶段2代码生成后，可在 后台→系统管理→角色管理 中调整）
insert into sys_role_menu (role_id, menu_id) values
-- 运营：基础资料、采购与库存、销售订单、营销、班次、消息提醒
(100, 5000), (100, 5100), (100, 5200), (100, 5300), (100, 5500), (100, 5600),
-- 财务：基础资料、采购与库存、销售订单、财务、消息提醒
(101, 5000), (101, 5100), (101, 5200), (101, 5400), (101, 5600),
-- 站点管理员：本站采购与库存、销售订单、班次（强制交班）
(102, 5100), (102, 5200), (102, 5500);
-- 加气员（103）不分配后台菜单，账号仅用于 H5 员工入口绑定（gas_staff_binding）

-- 6. 显式占用 100-103 后，把自增游标推到 104，避免后台新建角色时主键冲突
alter table sys_role auto_increment = 104;
