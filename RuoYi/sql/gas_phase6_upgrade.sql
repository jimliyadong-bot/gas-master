-- 阶段6：班次菜单与授权（gas_shift 表已包含在 gas_schema.sql）
-- 幂等，可在存量库重复执行；全新安装在 gas_phase3_menu.sql 后执行。
insert into sys_menu (menu_id, menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, remark)
select 5501, '班次记录', 5500, 1, 'gas/shift', '', 'C', '0', '1', 'gas:shift:list', 'fa fa-clock-o', 'admin', sysdate(), '班次汇总与强制交班'
from dual where not exists (select 1 from sys_menu where menu_id = 5501);
insert into sys_menu (menu_id, menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, remark)
select 5511, '班次查询', 5501, 1, '#', '', 'F', '0', '1', 'gas:shift:query', '#', 'admin', sysdate(), ''
from dual where not exists (select 1 from sys_menu where menu_id = 5511);
insert into sys_menu (menu_id, menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, remark)
select 5512, '强制交班', 5501, 2, '#', '', 'F', '0', '1', 'gas:shift:force', '#', 'admin', sysdate(), '忘记交班时由管理员强制交班并记录原因'
from dual where not exists (select 1 from sys_menu where menu_id = 5512);

insert into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id from sys_role r join sys_menu m on m.menu_id in (5501, 5511, 5512)
where r.role_key = 'gas_ops' and not exists (select 1 from sys_role_menu rm where rm.role_id = r.role_id and rm.menu_id = m.menu_id);
insert into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id from sys_role r join sys_menu m on m.menu_id in (5501, 5511, 5512)
where r.role_key = 'gas_station_admin' and not exists (select 1 from sys_role_menu rm where rm.role_id = r.role_id and rm.menu_id = m.menu_id);

-- 班次汇总索引（幂等）
set @idx_sql = (select if(count(*) = 0,
    'alter table gas_order add key idx_created_shift (created_shift_id)', 'select 1')
    from information_schema.statistics where table_schema = database() and table_name = 'gas_order' and index_name = 'idx_created_shift');
prepare idx_stmt from @idx_sql; execute idx_stmt; deallocate prepare idx_stmt;
set @idx_sql = (select if(count(*) = 0,
    'alter table gas_order add key idx_completed_shift (completed_shift_id, status, gas_type)', 'select 1')
    from information_schema.statistics where table_schema = database() and table_name = 'gas_order' and index_name = 'idx_completed_shift');
prepare idx_stmt from @idx_sql; execute idx_stmt; deallocate prepare idx_stmt;
