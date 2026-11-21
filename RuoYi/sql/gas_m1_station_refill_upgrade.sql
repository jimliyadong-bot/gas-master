-- M1：H5 站管批次／补货请求幂等。适用于已有业务库；新环境也需在基础建表后执行本脚本。
-- 仅新增表，不清理、修改或重算历史业务数据。执行前确认隔离测试库／目标及备份。
-- 重复执行安全；本脚本不处理已超采购量的历史数据，历史超量需人工核查。
-- 权限：复用既有 gas_station_admin 角色，H5 服务逐次查库校验本站。
-- 不给站管额外授予后台批次编辑／删除、付款、票据、商户等按钮权限；不新增审核菜单。
-- 可在获准目标上单独执行以下只读历史超量核查；只列出，不自动更改采购量或到货：
-- select b.batch_id, b.batch_no, b.station_id, b.qty as purchase_qty, sum(r.qty) as received_qty
-- from gas_purchase_batch b join gas_purchase_receipt r on r.batch_id = b.batch_id
-- group by b.batch_id, b.batch_no, b.station_id, b.qty having sum(r.qty) > b.qty;
create table if not exists gas_station_write_request (
    station_id bigint not null comment '服务端身份确定的站点',
    user_id bigint not null comment '操作人后台账号',
    operation varchar(16) character set ascii collate ascii_bin not null comment 'CREATE_BATCH/CREATE_RECEIPT',
    request_id varchar(64) character set ascii collate ascii_bin not null comment '客户端同次提交重试标识',
    payload_hash char(64) character set ascii collate ascii_bin not null comment '规范化业务字段SHA256',
    result_id bigint null comment '创建的批次ID或到货ID',
    create_time datetime not null,
    completed_at datetime null,
    primary key (station_id, user_id, operation, request_id),
    key idx_station_request_time (station_id, create_time)
) engine=InnoDB default charset=utf8mb4 comment='站管写入幂等记录，与业务事务一起提交；不可随意清理';
