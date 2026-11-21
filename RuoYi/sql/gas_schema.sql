-- =====================================================================
-- 加气站批量采购与销售记录系统 · 业务表初始化
-- 适用：RuoYi 4.8.3，MySQL 8.0+
-- 执行顺序：ry_20260319.sql → quartz.sql → 本文件
-- 设计说明见 docs/Dev_Plan.md §3
-- 约定：金额 decimal(12,2)；气量 decimal(14,2)；单价 decimal(10,4)
--       气种 CNG(m³) / LNG(kg)，数量不得跨气种汇总
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 组织与基础资料
-- ---------------------------------------------------------------------

drop table if exists gas_buyer;
create table gas_buyer (
  buyer_id              bigint        not null auto_increment comment '采购商ID',
  buyer_name            varchar(100)  not null                comment '采购商名称',
  short_name            varchar(30)   default null            comment '简称',
  contact_name          varchar(30)   default null            comment '联系人',
  contact_phone         varchar(20)   default null            comment '联系电话',
  notify_phones         varchar(255)  default null            comment '短信通知手机号，多个用英文逗号分隔',
  invoice_remind_ratio  decimal(5,2)  not null default 20.00  comment '开票提醒：批次剩余比例(%)',
  invoice_remind_days   int           not null default 7      comment '开票提醒：用完未开票的提醒间隔(天)',
  invoice_remind_max    int           not null default 3      comment '开票提醒：用完未开票最多提醒次数',
  status                char(1)       not null default '0'    comment '状态（0正常 1停用）',
  del_flag              char(1)       not null default '0'    comment '删除标志（0存在 2删除）',
  create_by             varchar(64)   default ''              comment '创建者',
  create_time           datetime                              comment '创建时间',
  update_by             varchar(64)   default ''              comment '更新者',
  update_time           datetime                              comment '更新时间',
  remark                varchar(500)  default null            comment '备注',
  primary key (buyer_id)
) engine=innodb default charset=utf8mb4 comment='采购商';

drop table if exists gas_buyer_merchant;
create table gas_buyer_merchant (
  merchant_id   bigint        not null auto_increment comment '商户配置ID',
  buyer_id      bigint        not null                comment '采购商ID',
  channel       varchar(16)   not null default 'LAKALA' comment '支付通道',
  pay_mode      varchar(16)   default null            comment '接入方式（CASHIER收银台 / JSAPI，待定）',
  merchant_no   varchar(64)   not null                comment '商户号',
  term_no       varchar(64)   default null            comment '终端号',
  app_id        varchar(64)   default null            comment '开放平台应用ID',
  secret_ref    varchar(128)  default null            comment '密钥引用（配置项名或加密串，不存明文）',
  status        char(1)       not null default '0'    comment '状态（0正常 1停用）',
  create_by     varchar(64)   default ''              comment '创建者',
  create_time   datetime                              comment '创建时间',
  update_by     varchar(64)   default ''              comment '更新者',
  update_time   datetime                              comment '更新时间',
  remark        varchar(500)  default null            comment '备注',
  primary key (merchant_id),
  unique key uk_buyer_channel (buyer_id, channel),
  unique key uk_merchant_no (channel, merchant_no)
) engine=innodb default charset=utf8mb4 comment='采购商收款商户';

drop table if exists gas_station;
create table gas_station (
  station_id    bigint        not null auto_increment comment '站点ID',
  station_code  varchar(32)   not null                comment '站点编码',
  station_name  varchar(100)  not null                comment '站点名称',
  buyer_id      bigint        not null                comment '所属采购商ID（一站只属于一个采购商）',
  dept_id       bigint        not null                comment '对应 sys_dept（用于站点管理员数据权限）',
  address       varchar(255)  default null            comment '地址',
  qr_token      varchar(64)   not null                comment '站点二维码标识',
  status        char(1)       not null default '0'    comment '状态（0正常 1停用）',
  del_flag      char(1)       not null default '0'    comment '删除标志（0存在 2删除）',
  create_by     varchar(64)   default ''              comment '创建者',
  create_time   datetime                              comment '创建时间',
  update_by     varchar(64)   default ''              comment '更新者',
  update_time   datetime                              comment '更新时间',
  remark        varchar(500)  default null            comment '备注',
  primary key (station_id),
  -- 生成列：未删除的站点取 station_code，软删后为 NULL；配合唯一键允许编码复用（MySQL 唯一索引允许多个 NULL）
  code_flag      varchar(32)   generated always as (if(del_flag = '0', station_code, null)) stored comment '站点编码唯一约束辅助列',
  unique key uk_station_code (code_flag),
  unique key uk_qr_token (qr_token),
  unique key uk_dept (dept_id),
  key idx_buyer (buyer_id)
) engine=innodb default charset=utf8mb4 comment='加气站';

drop table if exists gas_gun;
create table gas_gun (
  gun_id        bigint        not null auto_increment comment '枪ID',
  station_id    bigint        not null                comment '站点ID',
  gun_no        int           not null                comment '枪号',
  gas_type      varchar(8)    not null                comment '气种（CNG/LNG）',
  status        char(1)       not null default '0'    comment '状态（0正常 1停用）',
  create_by     varchar(64)   default ''              comment '创建者',
  create_time   datetime                              comment '创建时间',
  update_by     varchar(64)   default ''              comment '更新者',
  update_time   datetime                              comment '更新时间',
  remark        varchar(500)  default null            comment '备注',
  primary key (gun_id),
  unique key uk_station_gun (station_id, gun_no)
) engine=innodb default charset=utf8mb4 comment='加气枪';

drop table if exists gas_gun_price;
create table gas_gun_price (
  price_id        bigint        not null auto_increment comment '价格ID',
  gun_id          bigint        not null                comment '枪ID',
  price           decimal(10,4) not null                comment '销售单价（元/m³ 或 元/kg）',
  effective_from  datetime      not null                comment '生效时间',
  effective_to    datetime      default null            comment '失效时间（空=价格链开口；当前有效=from<=now<to或to为空）',
  create_by       varchar(64)   default ''              comment '创建者',
  create_time     datetime                              comment '创建时间',
  remark          varchar(500)  default null            comment '调价说明',
  primary key (price_id),
  key idx_gun_time (gun_id, effective_from),
  -- 生成列：开口价（to 为空）取 gun_id，否则为 NULL；配合唯一键保证每枪至多一条开口价（MySQL 唯一索引允许多个 NULL）
  cur_flag        bigint        generated always as (if(effective_to is null, gun_id, null)) stored comment '开口价唯一约束辅助列',
  unique key uk_gun_current (cur_flag)
) engine=innodb default charset=utf8mb4 comment='枪价（历史可追溯，只增不改）';

drop table if exists gas_config;
create table gas_config (
  config_key    varchar(64)   not null comment '参数键',
  config_value  varchar(255)  not null comment '参数值',
  update_by     varchar(64)   default '' comment '更新者',
  update_time   datetime                 comment '更新时间',
  remark        varchar(500)  default null comment '说明',
  primary key (config_key)
) engine=innodb default charset=utf8mb4 comment='业务参数';

-- ---------------------------------------------------------------------
-- 2. 采购
-- ---------------------------------------------------------------------

drop table if exists gas_purchase_batch;
create table gas_purchase_batch (
  batch_id        bigint        not null auto_increment comment '批次ID',
  batch_no        varchar(32)   not null                comment '批次号',
  buyer_id        bigint        not null                comment '采购商ID',
  station_id      bigint        not null                comment '站点ID',
  gas_type        varchar(8)    not null                comment '气种',
  purchase_date   date          not null                comment '采购日期',
  qty             decimal(14,2) not null                comment '采购数量',
  unit_price      decimal(10,4) not null                comment '采购单价',
  amount          decimal(12,2) not null                comment '采购金额',
  pay_status      varchar(16)   not null default 'UNPAID'    comment '付款状态（字典 gas_pay_status）',
  invoice_status  varchar(16)   not null default 'UNINVOICED' comment '票据状态（字典 gas_invoice_status）',
  invoice_no      varchar(64)   default null            comment '发票号',
  invoiced_at     datetime      default null            comment '开票时间',
  version         int           not null default 0      comment '乐观锁版本（防并发覆盖编辑）',
  create_by       varchar(64)   default ''              comment '创建者',
  create_time     datetime                              comment '创建时间',
  update_by       varchar(64)   default ''              comment '更新者',
  update_time     datetime                              comment '更新时间',
  remark          varchar(500)  default null            comment '备注',
  primary key (batch_id),
  unique key uk_batch_no (batch_no),
  key idx_station_gas (station_id, gas_type, purchase_date),
  key idx_buyer (buyer_id, purchase_date)
) engine=innodb default charset=utf8mb4 comment='采购批次';

drop table if exists gas_purchase_receipt;
create table gas_purchase_receipt (
  receipt_id    bigint        not null auto_increment comment '到货ID',
  batch_id      bigint        not null                comment '采购批次ID',
  buyer_id      bigint        not null                comment '采购商ID',
  station_id    bigint        not null                comment '站点ID',
  gas_type      varchar(8)    not null                comment '气种',
  qty           decimal(14,2) not null                comment '到货入库数量',
  consumed      decimal(14,2) not null default 0      comment '已实扣数量（先进先出）',
  adjusted      decimal(14,2) not null default 0      comment '累计调整（可负）',
  received_at   datetime      not null                comment '到货时间（先进先出排序依据）',
  version       int           not null default 0      comment '版本',
  create_by     varchar(64)   default ''              comment '创建者',
  create_time   datetime                              comment '创建时间',
  update_by     varchar(64)   default ''              comment '更新者',
  update_time   datetime                              comment '更新时间',
  remark        varchar(500)  default null            comment '备注',
  primary key (receipt_id),
  key idx_fifo (station_id, gas_type, received_at, receipt_id),
  key idx_batch (batch_id)
) engine=innodb default charset=utf8mb4 comment='采购到货/入库（先进先出扣减单位）';

drop table if exists gas_purchase_payment;
create table gas_purchase_payment (
  payment_id    bigint        not null auto_increment comment '付款ID',
  batch_id      bigint        not null                comment '采购批次ID',
  amount        decimal(12,2) not null                comment '付款金额',
  paid_at       date          not null                comment '付款日期',
  pay_method    varchar(16)   not null                comment '付款方式（字典 gas_purchase_pay_method）',
  voucher_url   varchar(500)  default null            comment '付款凭证',
  create_by     varchar(64)   default ''              comment '登记人',
  create_time   datetime                              comment '登记时间',
  remark        varchar(500)  default null            comment '备注',
  primary key (payment_id),
  key idx_batch (batch_id)
) engine=innodb default charset=utf8mb4 comment='采购付款登记（只登记，无审批）';

-- ---------------------------------------------------------------------
-- 3. 库存
-- ---------------------------------------------------------------------

drop table if exists gas_inventory_pool;
create table gas_inventory_pool (
  pool_id          bigint        not null auto_increment comment '库存池ID',
  station_id       bigint        not null                comment '站点ID',
  buyer_id         bigint        not null                comment '采购商ID',
  gas_type         varchar(8)    not null                comment '气种',
  total_in         decimal(14,2) not null default 0      comment '累计入库',
  reserved         decimal(14,2) not null default 0      comment '当前预占',
  consumed         decimal(14,2) not null default 0      comment '累计实扣',
  adjusted         decimal(14,2) not null default 0      comment '累计调整（可负）',
  alert_threshold  decimal(14,2) default null            comment '余量预警值',
  alert_armed      tinyint       not null default 1      comment '预警是否待触发（触发后置0，回升到阈值以上再置1）',
  update_time      datetime                              comment '更新时间',
  primary key (pool_id),
  unique key uk_station_gas (station_id, gas_type)
) engine=innodb default charset=utf8mb4 comment='站点库存池（可用 = total_in + adjusted - consumed - reserved）';

drop table if exists gas_inventory_ledger;
create table gas_inventory_ledger (
  ledger_id    bigint        not null auto_increment comment '流水ID',
  pool_id      bigint        not null                comment '库存池ID',
  receipt_id   bigint        not null default 0      comment '到货ID（IN/CONSUME/ADJUST 必填；RESERVE/RELEASE 为0）',
  order_id     bigint        not null default 0      comment '订单ID（非订单流水为0）',
  biz_type     varchar(16)   not null                comment '类型（字典 gas_ledger_type）',
  qty          decimal(14,2) not null                comment '数量（ADJUST 可负）',
  reason       varchar(255)  default null            comment '原因（ADJUST 必填）',
  operator     varchar(64)   not null                comment '操作人',
  create_time  datetime      not null                comment '发生时间',
  order_ref    bigint generated always as (if(order_id = 0, null, order_id)) stored comment '唯一约束辅助列',
  primary key (ledger_id),
  unique key uk_order_biz (order_ref, biz_type, receipt_id),
  key idx_pool_time (pool_id, create_time),
  key idx_receipt (receipt_id)
) engine=innodb default charset=utf8mb4 comment='库存流水（只追加不修改）';

-- ---------------------------------------------------------------------
-- 4. 会员与员工
-- ---------------------------------------------------------------------

drop table if exists gas_member;
create table gas_member (
  member_id       bigint        not null auto_increment comment '会员ID',
  openid          varchar(64)   not null                comment '公众号 openid',
  unionid         varchar(64)   default null            comment 'unionid',
  nickname        varchar(64)   default null            comment '昵称',
  avatar          varchar(500)  default null            comment '头像',
  phone           varchar(20)   default null            comment '手机号（短信验证后写入）',
  phone_bound_at  datetime      default null            comment '手机号绑定时间',
  status          char(1)       not null default '0'    comment '状态（0正常 1停用）',
  create_time     datetime                              comment '创建时间',
  update_time     datetime                              comment '更新时间',
  primary key (member_id),
  unique key uk_openid (openid),
  unique key uk_phone (phone)
) engine=innodb default charset=utf8mb4 comment='消费者会员';

drop table if exists gas_staff_binding;
create table gas_staff_binding (
  binding_id   bigint        not null auto_increment comment 'ID',
  user_id      bigint        not null                comment 'sys_user.user_id',
  openid       varchar(64)   not null                comment '公众号 openid',
  station_id   bigint        not null                comment '所属站点',
  bound_at     datetime      not null                comment '绑定时间',
  primary key (binding_id),
  unique key uk_user (user_id),
  unique key uk_openid (openid)
) engine=innodb default charset=utf8mb4 comment='员工公众号绑定（加气员/站点管理员）';

-- ---------------------------------------------------------------------
-- 5. 订单、动态码、班次
-- ---------------------------------------------------------------------

drop table if exists gas_order;
create table gas_order (
  order_id            bigint        not null auto_increment comment '订单ID',
  order_no            varchar(32)   not null                comment '订单号',
  source              varchar(8)    not null                comment '来源（SELF主动 / PROXY代下单）',
  status              varchar(20)   not null                comment '状态（字典 gas_order_status）',
  buyer_id            bigint        not null                comment '采购商ID',
  station_id          bigint        not null                comment '站点ID',
  gun_id              bigint        not null                comment '枪ID',
  gun_no              int           not null                comment '枪号快照',
  gas_type            varchar(8)    not null                comment '气种',
  member_id           bigint        not null                comment '会员ID',
  request_id          varchar(64)   default null            comment '主动下单客户端幂等请求号',
  unit_price          decimal(10,4) not null                comment '枪价快照',
  order_amount        decimal(12,2) not null                comment '原价金额',
  discount_amount     decimal(12,2) not null default 0      comment '优惠金额',
  pay_amount          decimal(12,2) not null                comment '实付金额',
  refunded_amount     decimal(12,2) not null default 0      comment '累计已退金额',
  coupon_id           bigint        default null            comment '使用的券',
  est_volume          decimal(14,2) not null                comment '预计气量=原价金额÷枪价（仅供参考，亦为预占量）',
  actual_volume       decimal(14,2) default null            comment '加气员确认量（实扣量）',
  merchant_id         bigint        default null            comment '收款商户配置ID',
  pay_channel         varchar(16)   default null            comment '支付渠道（微信/支付宝/云闪付）',
  created_shift_id    bigint        default null            comment '代下单所在班次',
  completed_shift_id  bigint        default null            comment '核销所在班次',
  proxy_staff_id      bigint        default null            comment '代下单加气员',
  redeem_staff_id     bigint        default null            comment '核销加气员',
  expire_at           datetime      default null            comment '待确认/待支付截止时间',
  paid_at             datetime      default null            comment '支付时间',
  completed_at        datetime      default null            comment '完成时间',
  closed_at           datetime      default null            comment '关闭时间',
  close_reason        varchar(255)  default null            comment '关闭/拒绝原因',
  exception_note      varchar(255)  default null            comment '核销异常说明',
  create_time         datetime      not null                comment '创建时间',
  update_time         datetime                              comment '更新时间',
  primary key (order_id),
  unique key uk_order_no (order_no),
  unique key uk_member_request (member_id, request_id),
  key idx_station_status (station_id, status),
  key idx_buyer_time (buyer_id, create_time),
  key idx_member_time (member_id, create_time),
  key idx_status_expire (status, expire_at),
  key idx_created_shift (created_shift_id),
  key idx_completed_shift (completed_shift_id, status, gas_type)
) engine=innodb default charset=utf8mb4 comment='销售订单';

drop table if exists gas_order_log;
create table gas_order_log (
  log_id         bigint        not null auto_increment comment 'ID',
  order_id       bigint        not null                comment '订单ID',
  from_status    varchar(20)   default null            comment '原状态',
  to_status      varchar(20)   not null                comment '新状态',
  action         varchar(32)   not null                comment '动作',
  operator_type  varchar(16)   not null                comment '操作方（MEMBER/STAFF/ADMIN/SYSTEM）',
  operator       varchar(64)   not null                comment '操作人',
  reason         varchar(255)  default null            comment '原因/说明',
  create_time    datetime      not null                comment '时间',
  primary key (log_id),
  key idx_order (order_id)
) engine=innodb default charset=utf8mb4 comment='订单状态日志';

drop table if exists gas_dynamic_token;
create table gas_dynamic_token (
  token_id     bigint        not null auto_increment comment 'ID',
  token_type   varchar(8)    not null                comment '类型（REDEEM核销码 / MEMBER会员码）',
  ref_id       bigint        not null                comment '订单ID 或 会员ID',
  token        char(32)      not null                comment '随机令牌',
  expire_at    datetime      not null                comment '过期时间',
  used_at      datetime      default null            comment '使用时间',
  create_time  datetime      not null                comment '生成时间',
  primary key (token_id),
  unique key uk_token (token),
  key idx_ref (token_type, ref_id)
) engine=innodb default charset=utf8mb4 comment='动态码（30秒刷新、60秒有效、单次使用；可定期清理过期数据）';

drop table if exists gas_shift;
create table gas_shift (
  shift_id      bigint        not null auto_increment comment '班次ID',
  station_id    bigint        not null                comment '站点ID',
  user_id       bigint        not null                comment '加气员 sys_user.user_id',
  staff_name    varchar(30)   not null                comment '加气员姓名快照',
  started_at    datetime      not null                comment '开始当班',
  ended_at      datetime      default null            comment '交班时间',
  active_flag   tinyint       default 1               comment '当班中=1，交班后=NULL（每站只允许一个进行中班次）',
  ended_by      varchar(64)   default null            comment '交班操作人（本人或强制交班的管理员）',
  end_reason    varchar(255)  default null            comment '强制交班原因',
  proxy_count   int           default null            comment '汇总：代下单数',
  redeem_count  int           default null            comment '汇总：核销数',
  cng_volume    decimal(14,2) default null            comment '汇总：CNG 确认量(m³)',
  lng_volume    decimal(14,2) default null            comment '汇总：LNG 确认量(kg)',
  pay_amount    decimal(12,2) default null            comment '汇总：核销订单实付金额',
  primary key (shift_id),
  unique key uk_station_active (station_id, active_flag),
  key idx_user (user_id, started_at)
) engine=innodb default charset=utf8mb4 comment='加气员班次';

-- ---------------------------------------------------------------------
-- 6. 支付、退款、对账
-- ---------------------------------------------------------------------

drop table if exists gas_pay_txn;
create table gas_pay_txn (
  txn_id            bigint        not null auto_increment comment 'ID',
  order_id          bigint        not null                comment '订单ID',
  merchant_id       bigint        not null                comment '收款商户配置ID',
  out_trade_no      varchar(64)   not null                comment '我方交易号',
  channel_trade_no  varchar(64)   default null            comment '拉卡拉交易号',
  pay_channel       varchar(16)   default null            comment '微信/支付宝/云闪付',
  amount            decimal(12,2) not null                comment '金额',
  status            varchar(16)   not null                comment '状态（PAYING/SUCCESS/FAILED/CLOSED）',
  paid_at           datetime      default null            comment '支付成功时间',
  raw_notify        text                                  comment '回调原文',
  create_time       datetime      not null                comment '创建时间',
  update_time       datetime                              comment '更新时间',
  primary key (txn_id),
  unique key uk_out_trade_no (out_trade_no),
  unique key uk_channel_trade_no (channel_trade_no),
  key idx_order (order_id)
) engine=innodb default charset=utf8mb4 comment='支付流水';

drop table if exists gas_refund;
create table gas_refund (
  refund_id          bigint        not null auto_increment comment 'ID',
  refund_no          varchar(64)   not null                comment '退款单号',
  order_id           bigint        not null                comment '订单ID',
  txn_id             bigint        not null                comment '原支付流水ID',
  amount             decimal(12,2) not null                comment '退款金额',
  reason             varchar(255)  not null                comment '退款原因',
  source             varchar(16)   not null                comment '发起方（CUSTOMER 待核销自助 / ADMIN 后台）',
  status             varchar(16)   not null                comment '状态（字典 gas_refund_status）',
  channel_refund_no  varchar(64)   default null            comment '拉卡拉退款单号',
  operator           varchar(64)   not null                comment '发起人',
  refunded_at        datetime      default null            comment '退款成功时间',
  create_time        datetime      not null                comment '创建时间',
  update_time        datetime                              comment '更新时间',
  primary key (refund_id),
  unique key uk_refund_no (refund_no),
  key idx_order (order_id)
) engine=innodb default charset=utf8mb4 comment='退款（无审批）';

drop table if exists gas_settle_bill;
create table gas_settle_bill (
  bill_id           bigint        not null auto_increment comment 'ID',
  merchant_id       bigint        not null                comment '商户配置ID',
  bill_date         date          not null                comment '账单日期',
  channel_trade_no  varchar(64)   not null                comment '拉卡拉交易号',
  trade_type        varchar(16)   not null                comment '交易类型（PAY/REFUND）',
  amount            decimal(12,2) not null                comment '交易金额',
  fee               decimal(12,2) default null            comment '手续费',
  settle_amount     decimal(12,2) default null            comment '结算金额',
  trade_time        datetime      default null            comment '交易时间',
  raw_line          varchar(2000) default null            comment '账单原始行',
  primary key (bill_id),
  unique key uk_bill (merchant_id, channel_trade_no, trade_type),
  key idx_date (merchant_id, bill_date)
) engine=innodb default charset=utf8mb4 comment='拉卡拉对账单明细';

drop table if exists gas_reconcile_diff;
create table gas_reconcile_diff (
  diff_id           bigint        not null auto_increment comment 'ID',
  merchant_id       bigint        not null                comment '商户配置ID',
  bill_date         date          not null                comment '账单日期',
  diff_type         varchar(16)   not null                comment '差异类型（LOCAL_ONLY/BILL_ONLY/AMOUNT_MISMATCH）',
  order_no          varchar(32)   default null            comment '订单号',
  channel_trade_no  varchar(64)   default null            comment '拉卡拉交易号',
  local_amount      decimal(12,2) default null            comment '系统金额',
  bill_amount       decimal(12,2) default null            comment '账单金额',
  status            varchar(16)   not null default 'OPEN' comment '处理状态（OPEN/RESOLVED）',
  handle_note       varchar(500)  default null            comment '处理说明',
  handled_by        varchar(64)   default null            comment '处理人',
  handled_at        datetime      default null            comment '处理时间',
  create_time       datetime      not null                comment '发现时间',
  primary key (diff_id),
  key idx_date (merchant_id, bill_date)
) engine=innodb default charset=utf8mb4 comment='对账差异';

-- ---------------------------------------------------------------------
-- 7. 优惠券（不叠加；成本由采购商承担；限发券采购商名下站点）
-- ---------------------------------------------------------------------

drop table if exists gas_coupon_template;
create table gas_coupon_template (
  template_id       bigint        not null auto_increment comment '模板ID',
  buyer_id          bigint        not null                comment '发券采购商',
  name              varchar(64)   not null                comment '券名称',
  face_value        decimal(12,2) not null                comment '面额',
  threshold_amount  decimal(12,2) not null default 0      comment '使用门槛（原价金额≥）',
  valid_type        varchar(8)    not null                comment '有效期类型（FIXED固定日期 / DAYS领取后天数）',
  valid_from        datetime      default null            comment '固定开始',
  valid_to          datetime      default null            comment '固定结束',
  valid_days        int           default null            comment '领取后有效天数',
  total_qty         int           not null                comment '发放总量',
  issued_qty        int           not null default 0      comment '已发放',
  status            char(1)       not null default '0'    comment '状态（0正常 1停用）',
  create_by         varchar(64)   default ''              comment '创建者',
  create_time       datetime                              comment '创建时间',
  update_by         varchar(64)   default ''              comment '更新者',
  update_time       datetime                              comment '更新时间',
  remark            varchar(500)  default null            comment '备注',
  primary key (template_id),
  key idx_buyer (buyer_id)
) engine=innodb default charset=utf8mb4 comment='优惠券模板';

drop table if exists gas_coupon;
create table gas_coupon (
  coupon_id         bigint        not null auto_increment comment '券ID',
  template_id       bigint        not null                comment '模板ID',
  buyer_id          bigint        not null                comment '发券采购商',
  member_id         bigint        not null                comment '会员ID',
  face_value        decimal(12,2) not null                comment '面额快照',
  threshold_amount  decimal(12,2) not null                comment '门槛快照',
  status            varchar(16)   not null                comment '状态（字典 gas_coupon_status）',
  expire_at         datetime      not null                comment '过期时间',
  used_order_id     bigint        default null            comment '使用订单',
  used_at           datetime      default null            comment '使用时间',
  issued_by         varchar(64)   not null                comment '发放人',
  create_time       datetime      not null                comment '发放时间',
  primary key (coupon_id),
  key idx_member (member_id, status),
  key idx_template (template_id),
  key idx_status_expire (status, expire_at)
) engine=innodb default charset=utf8mb4 comment='用户优惠券';

-- ---------------------------------------------------------------------
-- 8. 提醒与短信（阿里云短信）
-- ---------------------------------------------------------------------

drop table if exists gas_remind;
create table gas_remind (
  remind_id     bigint        not null auto_increment comment 'ID',
  remind_type   varchar(24)   not null                comment '类型（字典 gas_remind_type）',
  buyer_id      bigint        not null                comment '采购商ID',
  station_id    bigint        not null                comment '站点ID',
  batch_id      bigint        not null default 0      comment '采购批次ID（库存预警为0）',
  pool_id       bigint        not null default 0      comment '库存池ID（开票提醒为0）',
  seq           int           not null default 1      comment '第几次（防重复）',
  content       varchar(500)  not null                comment '提醒内容',
  plan_send_at  datetime      not null                comment '计划发送时间（避开21:00-8:00）',
  status        varchar(16)   not null default 'PENDING' comment '状态（PENDING/SENT/FAILED）',
  sent_at       datetime      default null            comment '发送时间',
  create_time   datetime      not null                comment '触发时间',
  primary key (remind_id),
  unique key uk_remind (remind_type, batch_id, pool_id, seq),
  key idx_pending (status, plan_send_at)
) engine=innodb default charset=utf8mb4 comment='开票提醒/库存预警';

drop table if exists gas_sms_log;
create table gas_sms_log (
  sms_id           bigint        not null auto_increment comment 'ID',
  phone            varchar(20)   not null                comment '手机号',
  template_code    varchar(32)   not null                comment '阿里云模板CODE',
  params           varchar(1000) default null            comment '模板参数 JSON（验证码类不记录明文）',
  biz_type         varchar(24)   not null                comment '业务类型（VERIFY_CODE/INVOICE_REMIND/STOCK_ALERT）',
  biz_id           bigint        default null            comment '关联业务ID',
  client_ip        varchar(64)   default null            comment '请求IP（验证码限频用）',
  status           varchar(16)   not null                comment 'SUCCESS/FAILED',
  provider_biz_id  varchar(64)   default null            comment '阿里云 BizId',
  error_msg        varchar(500)  default null            comment '错误信息',
  create_time      datetime      not null                comment '发送时间',
  primary key (sms_id),
  key idx_phone_time (phone, create_time),
  key idx_ip_time (client_ip, create_time)
) engine=innodb default charset=utf8mb4 comment='短信发送日志';

-- ---------------------------------------------------------------------
-- 9. 业务参数默认值
-- ---------------------------------------------------------------------

insert into gas_config (config_key, config_value, update_time, remark) values
('order.waitPay.minutes',       '15', sysdate(), '待支付超时关单（分钟）'),
('order.waitConfirm.minutes',   '10', sysdate(), '代下单待司机确认超时（分钟）'),
('order.amount.max',          '99999', sysdate(), '单笔下单金额上限（元）'),
('token.refresh.seconds',       '30', sysdate(), '核销码/会员码刷新间隔（秒）'),
('token.ttl.seconds',           '60', sysdate(), '核销码/会员码有效期（秒）'),
('sms.quiet.start',          '21:00', sysdate(), '短信免打扰开始'),
('sms.quiet.end',            '08:00', sysdate(), '短信免打扰结束'),
('sms.code.ttl.minutes',         '5', sysdate(), '验证码有效期（分钟）'),
('sms.code.interval.seconds',   '60', sysdate(), '同一手机号验证码发送间隔（秒）'),
('sms.code.daily.max',          '10', sysdate(), '同一手机号每日验证码上限'),
('sms.code.ip.daily.max',       '30', sysdate(), '同一IP每日验证码上限'),
('sms.code.max.try',             '5', sysdate(), '验证码校验失败次数上限（超限作废）'),
('wx.appid',                     '', sysdate(), '公众号 AppID（网页授权/JS-SDK）'),
('wx.secret',                    '', sysdate(), '公众号 AppSecret（建议生产改用环境变量覆盖）'),
('h5.jwt.secret',       '', sysdate(), 'H5 端 JWT 签名密钥（生产仅用环境变量 GAS_H5_JWT_SECRET，至少32字节）'),
('h5.jwt.expire.hours',        '168', sysdate(), 'H5 端 JWT 有效期（小时，默认7天）');

-- ---------------------------------------------------------------------
-- 10. 字典
-- ---------------------------------------------------------------------

insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark) values
('气种',         'gas_type',                '0', 'admin', sysdate(), 'CNG按m³、LNG按kg'),
('订单状态',     'gas_order_status',        '0', 'admin', sysdate(), null),
('订单来源',     'gas_order_source',        '0', 'admin', sysdate(), null),
('采购付款状态', 'gas_pay_status',          '0', 'admin', sysdate(), null),
('票据状态',     'gas_invoice_status',      '0', 'admin', sysdate(), null),
('采购付款方式', 'gas_purchase_pay_method', '0', 'admin', sysdate(), null),
('库存流水类型', 'gas_ledger_type',         '0', 'admin', sysdate(), null),
('退款状态',     'gas_refund_status',       '0', 'admin', sysdate(), null),
('优惠券状态',   'gas_coupon_status',       '0', 'admin', sysdate(), null),
('提醒类型',     'gas_remind_type',         '0', 'admin', sysdate(), null);

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time) values
(1, 'CNG', 'CNG', 'gas_type', '', 'success', 'Y', '0', 'admin', sysdate()),
(2, 'LNG', 'LNG', 'gas_type', '', 'primary', 'N', '0', 'admin', sysdate()),

(1, '待司机确认', 'WAIT_CONFIRM', 'gas_order_status', '', 'warning', 'N', '0', 'admin', sysdate()),
(2, '待支付',     'WAIT_PAY',     'gas_order_status', '', 'warning', 'N', '0', 'admin', sysdate()),
(3, '待核销',     'PAID',         'gas_order_status', '', 'info',    'N', '0', 'admin', sysdate()),
(4, '已完成',     'COMPLETED',    'gas_order_status', '', 'success', 'N', '0', 'admin', sysdate()),
(5, '已拒绝',     'REJECTED',     'gas_order_status', '', 'default', 'N', '0', 'admin', sysdate()),
(6, '已关闭',     'CLOSED',       'gas_order_status', '', 'default', 'N', '0', 'admin', sysdate()),
(7, '退款中',     'REFUNDING',    'gas_order_status', '', 'warning', 'N', '0', 'admin', sysdate()),
(8, '已退款',     'REFUNDED',     'gas_order_status', '', 'danger',  'N', '0', 'admin', sysdate()),

(1, '主动下单', 'SELF',  'gas_order_source', '', 'primary', 'Y', '0', 'admin', sysdate()),
(2, '代下单',   'PROXY', 'gas_order_source', '', 'info',    'N', '0', 'admin', sysdate()),

(1, '未付款',   'UNPAID',  'gas_pay_status', '', 'danger',  'Y', '0', 'admin', sysdate()),
(2, '部分付款', 'PARTIAL', 'gas_pay_status', '', 'warning', 'N', '0', 'admin', sysdate()),
(3, '已付清',   'PAID',    'gas_pay_status', '', 'success', 'N', '0', 'admin', sysdate()),

(1, '未开票', 'UNINVOICED', 'gas_invoice_status', '', 'warning', 'Y', '0', 'admin', sysdate()),
(2, '已开票', 'INVOICED',   'gas_invoice_status', '', 'success', 'N', '0', 'admin', sysdate()),

(1, '银行转账', 'BANK',       'gas_purchase_pay_method', '', '', 'Y', '0', 'admin', sysdate()),
(2, '承兑汇票', 'ACCEPTANCE', 'gas_purchase_pay_method', '', '', 'N', '0', 'admin', sysdate()),
(3, '现金',     'CASH',       'gas_purchase_pay_method', '', '', 'N', '0', 'admin', sysdate()),
(4, '其他',     'OTHER',      'gas_purchase_pay_method', '', '', 'N', '0', 'admin', sysdate()),

(1, '入库', 'IN',      'gas_ledger_type', '', 'success', 'N', '0', 'admin', sysdate()),
(2, '预占', 'RESERVE', 'gas_ledger_type', '', 'warning', 'N', '0', 'admin', sysdate()),
(3, '释放', 'RELEASE', 'gas_ledger_type', '', 'info',    'N', '0', 'admin', sysdate()),
(4, '实扣', 'CONSUME', 'gas_ledger_type', '', 'primary', 'N', '0', 'admin', sysdate()),
(5, '调整', 'ADJUST',  'gas_ledger_type', '', 'danger',  'N', '0', 'admin', sysdate()),

(1, '退款中', 'PROCESSING', 'gas_refund_status', '', 'warning', 'N', '0', 'admin', sysdate()),
(2, '已退款', 'SUCCESS',    'gas_refund_status', '', 'success', 'N', '0', 'admin', sysdate()),
(3, '失败',   'FAILED',     'gas_refund_status', '', 'danger',  'N', '0', 'admin', sysdate()),

(1, '未使用', 'UNUSED',  'gas_coupon_status', '', 'primary', 'Y', '0', 'admin', sysdate()),
(2, '已使用', 'USED',    'gas_coupon_status', '', 'success', 'N', '0', 'admin', sysdate()),
(3, '已过期', 'EXPIRED', 'gas_coupon_status', '', 'default', 'N', '0', 'admin', sysdate()),

(1, '批次即将用完', 'BATCH_NEAR_EMPTY', 'gas_remind_type', '', 'warning', 'N', '0', 'admin', sysdate()),
(2, '批次已用完',   'BATCH_EMPTY',      'gas_remind_type', '', 'danger',  'N', '0', 'admin', sysdate()),
(3, '用完未开票',   'BATCH_UNINVOICED', 'gas_remind_type', '', 'danger',  'N', '0', 'admin', sysdate()),
(4, '库存余量预警', 'STOCK_ALERT',      'gas_remind_type', '', 'warning', 'N', '0', 'admin', sysdate());

-- ---------------------------------------------------------------------
-- 11. 菜单目录（具体页面和按钮权限由代码生成器生成，生成时上级菜单选择下列目录）
-- ---------------------------------------------------------------------

insert into sys_menu (menu_id, menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, remark) values
(5000, '基础资料',   0, 5,  '#', '', 'M', '0', '1', '', 'fa fa-building',      'admin', sysdate(), '采购商、站点、枪号、枪价'),
(5100, '采购与库存', 0, 6,  '#', '', 'M', '0', '1', '', 'fa fa-database',      'admin', sysdate(), '采购批次、到货、付款、库存台账'),
(5200, '销售订单',   0, 7,  '#', '', 'M', '0', '1', '', 'fa fa-file-text-o',   'admin', sysdate(), '订单、退款'),
(5300, '营销',       0, 8,  '#', '', 'M', '0', '1', '', 'fa fa-ticket',        'admin', sysdate(), '优惠券'),
(5400, '财务',       0, 9,  '#', '', 'M', '0', '1', '', 'fa fa-jpy',           'admin', sysdate(), '销售收款、对账'),
(5500, '班次',       0, 10, '#', '', 'M', '0', '1', '', 'fa fa-clock-o',       'admin', sysdate(), '班次记录、强制交班'),
(5600, '消息提醒',   0, 11, '#', '', 'M', '0', '1', '', 'fa fa-bell-o',        'admin', sysdate(), '开票提醒、库存预警、短信记录');
