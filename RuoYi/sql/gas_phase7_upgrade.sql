-- 阶段7：订单幂等、优惠券过期索引、后台菜单与运营授权，可重复执行
drop procedure if exists gas_phase7_ddl;
delimiter $$
create procedure gas_phase7_ddl()
begin
    if not exists (select 1 from information_schema.columns where table_schema=database() and table_name='gas_order' and column_name='request_id') then
        alter table gas_order add column request_id varchar(64) default null comment '主动下单客户端幂等请求号' after member_id;
    end if;
    if not exists (select 1 from information_schema.statistics where table_schema=database() and table_name='gas_order' and index_name='uk_member_request') then
        alter table gas_order add unique key uk_member_request(member_id,request_id);
    end if;
    if not exists (select 1 from information_schema.statistics where table_schema=database() and table_name='gas_coupon' and index_name='idx_status_expire') then
        alter table gas_coupon add key idx_status_expire(status,expire_at);
    end if;
end$$
delimiter ;
call gas_phase7_ddl();
drop procedure gas_phase7_ddl;

insert into sys_menu (menu_id,menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
select 5301,'优惠券管理',5300,1,'gas/coupon','','C','0','1','gas:coupon:list','fa fa-gift','phase7',sysdate(),'券模板、定向发放与核销统计'
from dual where not exists (select 1 from sys_menu where menu_id=5301);
insert into sys_menu (menu_id,menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
select 5311,'模板新增',5301,1,'#','','F','0','1','gas:coupon:add','#','phase7',sysdate(),''
from dual where not exists (select 1 from sys_menu where menu_id=5311);
insert into sys_menu (menu_id,menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
select 5312,'模板编辑',5301,2,'#','','F','0','1','gas:coupon:edit','#','phase7',sysdate(),''
from dual where not exists (select 1 from sys_menu where menu_id=5312);
insert into sys_menu (menu_id,menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
select 5313,'定向发券',5301,3,'#','','F','0','1','gas:coupon:grant','#','phase7',sysdate(),''
from dual where not exists (select 1 from sys_menu where menu_id=5313);

insert into sys_role_menu (role_id,menu_id)
select r.role_id,m.menu_id from sys_role r join sys_menu m on m.menu_id in (5301,5311,5312,5313)
where r.role_key='gas_ops' and not exists (select 1 from sys_role_menu rm where rm.role_id=r.role_id and rm.menu_id=m.menu_id);
