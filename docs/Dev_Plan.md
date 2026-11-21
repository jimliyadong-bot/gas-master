# 加气站批量采购与销售记录系统 · 开发方案

> 版本：v0.3｜日期：2026-11-16（v0.2：并入 PRD §8 答复；v0.3：明确后台账号体系、确定阿里云短信，新增 `RuoYi/sql/gas_schema.sql`）  
> 依据：`docs/PRD.md` v1.0、`design/Flowchart.md`、`design/specs/Design_Spec.md`、原型 `design/prototypes/`（含 `gas-store.js` / `owner-store.js` 的状态规则）。  
> 本文是技术方案。规则以 §7 已确认事项为准；仍未确认的事项在 §7.3 列出，方案中对应的部分做成可配置项。

---

## 1. 总体架构

> 最新环境确认：用户同意继续使用现有 Java 17，取消此前拟议的 Java 8 兼容迁移；不降级框架或依赖。按已确认的 M0—M6 顺序推进，M1 超采购量到货采用方案 A。实际改动和验证结果见 `docs/模块开发进度.md`，不以历史验证替代本轮验收。

```
            ┌────────────── 微信公众号（已认证） ──────────────┐
            │  菜单 / 站点二维码 → 网页授权(OAuth2) → H5          │
            └──────────────────────┬───────────────────────────┘
                                   │ HTTPS  /api/h5/**  (JWT)
┌──────────────────┐     ┌─────────▼──────────────────────────────┐     ┌──────────────┐
│ H5 前端（新项目） │────▶│ RuoYi 4.8.3 单体（Spring Boot 4 + Shiro）│────▶│ MySQL 8      │
│ Vue3+Vite+Vant4  │     │  ├ ruoyi-admin   后台页面 + H5 API 控制器  │     └──────────────┘
│ 消费者/加气员/站管 │     │  ├ ruoyi-gas     【新增】业务领域模块      │◀───▶ 拉卡拉（按采购商商户号路由）
└──────────────────┘     │  ├ ruoyi-quartz  定时任务                 │◀───▶ 短信服务（验证码、开票/库存提醒）
后台（浏览器）──────────▶│  /gas/**  Thymeleaf + Bootstrap（Shiro）  │
                         └────────────────────────────────────────┘
```

### 1.1 组织与数据隔离

已确认：**采购方就是采购商，是同一业务主体；每个采购商对应多个加气站，一个站点只属于一个采购商。** 每个站点有独立库存，每个采购批次只给指定站点库存补货；没有采购商公共库存或跨站共享库存。所有司机均从交易对应站点出气。

账号安排仍沿用现方案：后台由采购商使用，用一套后台账号体系；可以录入多个采购商业务档案，`gas_buyer` 不是独立登录租户，不为每个档案单独开账号。采购商档案是采购方资料的系统表达，不是采购方之下的另一个业务主体；本轮身份澄清不自动改变现有账号隔离方案。

**系统不设置车队实体、账户、库存或专属结算流程；车队人员一律按散户下单、付款。每个站点同一时间只允许一名当班加气员进行核验、核销。**

**补货职责已确认：** 站点管理员在管理员端按采购批次录入本站到货补货，由本人核对确认弹窗后提交入库，不需要第二人审核。管理员端原型参考 `design/prototypes/m-buyers.html`；后台保留查看、调整能力，以查看和调整为主，不作为每笔补货的必经审批端。此为最新需求，不表示现有 H5 和默认授权已同步实现。

```
采购商／采购方（根部门）── 超级管理员 / 运营 / 财务：看全部数据，可按采购商筛选
 ├─ 站点 A1（部门）── 站点管理员、加气员：数据权限“本部门”
 ├─ 站点 A2
 └─ 站点 B1
采购商 A、B…：gas_buyer 档案，通过 gas_station.buyer_id 关联站点
```

- 新建站点时自动在根部门下创建对应的 `sys_dept`，写入 `gas_station.dept_id`；站点管理员、加气员挂在站点部门下，业务查询加 `@DataScope` 限定本站。
- 业务表都冗余 `buyer_id`、`station_id`，所有列表和报表都能按采购商、站点筛选和汇总。
- 拉卡拉商户号、优惠券成本、开票和库存短信提醒都以采购商为单位。

| 端 | 技术 | 认证 | 使用者 |
|---|---|---|---|
| 管理后台 | RuoYi 原生 Thymeleaf 页面 | Shiro 会话 + 角色/菜单/数据权限 | 超级管理员、运营、财务、站点管理员 |
| 公众号 H5 | Vue 3 + Vite + Vant 4，独立部署（Nginx 静态） | OAuth 换 openid → 短信验证码绑定手机号 → 后端签发 JWT | 消费者/司机、加气员、站点管理员 |

**几点取舍：**

- **后台不改造成前后端分离。** RuoYi 4.x 的代码生成器直接产出增删改查、Excel 导出、数据权限和操作日志，一期最快。原型 `a-*.html` 用作字段和交互参考。
- **H5 单独建项目**，配合微信 JS-SDK（扫一扫）、OAuth 跳转和拉卡拉支付跳转。
- **H5 接口与后台共用一个进程，但认证分开**：`/api/h5/**` 在 Shiro 中设为 `anon`，再挂自定义 JWT 过滤器，不依赖 Shiro 会话 Cookie。
- **业务代码集中在新模块 `ruoyi-gas`**，不改 RuoYi 原有模块，方便日后升级。

---

## 2. 开发步骤

每个阶段都以“可演示、可测试”收尾。原型 `node:test` 已覆盖的规则（重复支付/核销、班次变更、写入失败回滚等）在对应阶段改写为 JUnit 集成测试。

### 阶段 0 · 准备（与阶段 1 并行）

1. **外部资源**（办理周期长，尽早开始）：
   - 公众号后台配置“网页授权域名”“JS 接口安全域名”，准备已备案域名和 HTTPS 证书；
   - **阿里云短信**（账号已有）：确认短信签名已审核通过；申请 3 个模板：验证码（验证码类）、开票提醒、库存预警（通知类），模板变量与 §3.7 保持一致；创建只授予短信发送权限的 RAM 子账号 AccessKey，通过环境变量配置，不提交到代码库；
   - **拉卡拉**：按采购商分别准备商户号、开放平台应用和测试环境密钥；确定用收银台还是 JSAPI（§7.3）。
2. 开发环境：JDK 17、Maven 3.9+、MySQL 8、Node 20+。

### 阶段 1 · 后台底座

1. 导入 `RuoYi/sql/ry_20260319.sql`、`quartz.sql`，修改 `application-druid.yml` 数据库连接，启动验证。
2. 基础配置调整：`demoEnabled: false`；固定 Shiro `cipherKey`；端口改 8080（Nginx 反代）；上传目录 `profile`；系统名称和 Logo。（执行记录 2026-11-16：端口实际用 9090——本机 Windows 动态保留端口段 8022-8121 覆盖了 8080，Nginx 反代目标相应调整；系统名称登录页用全称、侧边栏用“加气站购销系统”，Logo 图片沿用待替换）
3. 新建 Maven 模块 `ruoyi-gas`（包 `com.ruoyi.gas`），在 `ruoyi-admin` 中引用。
4. 执行 `RuoYi/sql/gas_schema.sql`：建业务表、业务参数默认值、字典和菜单目录。
5. 按 §1.1 调整部门树（根部门改为采购方名称，删除若依演示部门），建角色：超级管理员 / 运营 / 财务（数据权限：全部）、站点管理员 / 加气员（数据权限：本部门）。

### 阶段 2 · 基础资料（代码生成为主）

采购商（含拉卡拉商户配置、短信通知手机号）、站点（归属采购商，生成站点二维码）、枪号、**按枪维护的销售价格**（带生效时间，后台维护）、系统参数（订单超时、码有效期、提醒比例等）。

> **执行记录 2026-11-16：阶段 2 已完成并通过端到端冒烟验证。**
> - **交付**：`ruoyi-gas` 五组 Controller/Service/Mapper/模板——采购商（`GasBuyerController`，含 `gas_buyer_merchant` 拉卡拉配置 1:1 内嵌维护、商户号渠道内唯一、删除级联删商户配置）、站点（建站自动建 `sys_dept` 并回填 `dept_id`、`qr_token` + zxing 二维码 PNG 下载、改名同步部门名、有枪/部门有用户不可删、删站联动删部门）、枪号（站点内 (station_id, gun_no) 唯一、CNG/LNG 字典）、枪价（**只增不改**，新价生效自动结旧价 `effective_to`、历史保留、只能删每枪最新价、删当前价自动重开上一条）、业务参数（`gas_config`，已初始化 `h5.base.url`）。
> - **菜单/权限**：`RuoYi/sql/gas_phase2_menu.sql` 已执行——5 个页面菜单（5001–5005 挂“基础资料”目录 5000）+ 22 个按钮权限；角色授权：运营(100) 全量、财务(101) 采购商/站点只读。
> - **技术要点**：`SysDeptMapper.insertDept` 不回填自增主键，建站后用 `checkDeptNameUnique` 反查 `dept_id`（不改 RuoYi 原模块）；删除守卫抛 `ServiceException` 由 `GlobalExceptionHandler` 返回 JSON（需 `X-Requested-With: XMLHttpRequest` 头）。
> - **验证**：`mvn clean package` 8 模块全部 SUCCESS；HTTP 冒烟（admin 登录）全流程通过——采购商/站点/枪/两次枪价 CRUD、枪号重复与站点-枪删除守卫返回 JSON、自动结旧价（3.5 → effectiveTo=12:00）与删除重开旧价（3.5 恢复当前有效）、二维码 583 字节 PNG、页面渲染 200；验证后数据已清理（gas 五表清零、无残留部门）。
> - **Review 修复（2026-11-16 第二轮）**：
>   1. **未来调价语义**：`selectCurrentPriceByGunId` 与 `onlyCurrent` 筛选统一为区间判定（`from<=now<to 或 to 为空`），调价校验改用链尾开口价（`selectLatestPriceByGunId`），列表标签增加“待生效”三态；
>   2. **并发与软删的 DB 兜底**：`gas_gun_price` 加生成列 `cur_flag` + `uk_gun_current`（每枪至多一条开口价，删除时**先删链尾再重开上一条**避免撞键）；`gas_station` 的 `uk_station_code` 改生成列 `code_flag`（软删后站点编码可复用）——两处存量库均已 ALTER；
>   3. **引用有效性**：建站/改归属校验采购商存在且未停用，建枪校验站点存在且未停用；枪禁改所属站点（后端校验 + 编辑页只读）；
>   4. **校验与安全**：`notifyPhones` 手机号格式（逗号分隔、允许空）、开票提醒参数范围（比例 0–100 等）校验；5 个 `remove` 端点 `ids` 判空；`checkXxxUnique` 端点补 `@RequiresPermissions`；删除无调用方的枪价 detail 死代码。
>   回归冒烟全部通过（含预置未来价后 onlyCurrent 仍返回旧价、早于链尾调价被拒、软删站点 SMK01 编码复用、空 ids 拒删等 17 项断言）。

### 阶段 3 · 采购与库存（后端核心之一）

1. 采购批次（采购商 + 指定站点 + 气种）表示向该站库存补货。最新确认流程：站点管理员在管理员端按批次录入本次到货／补货 → 本人确认弹窗 → 提交成功后生成入库记录、增加本站对应气种库存；取消不入库，不需要他人审核。参考 `design/prototypes/m-buyers.html`，现有采购批次与分次进气记录结构不因增加确认弹窗而合并。
2. **库存池 + 流水 + 到货批次余量**（§3.3）：所有变动只能通过 `InventoryService` 完成（入库 / 预占 / 释放 / 先进先出实扣 / 调整）。
3. 人工调整：必须指定到货批次并填写原因。
4. 采购付款**只登记**（金额、日期、方式、凭证），没有审批流程；票据状态维护。
5. 库存余量预警、开票提醒（§3.7）。
6. 现有 JUnit 覆盖先进先出跨批次扣减、并发核销不超扣、调整后余额等于按流水重算。它们反映当前实现；最新需求改为“下单校验库存、实际加气／核销不因库存不足拦截”，后续需同步实现和对应回归，本轮未修改测试。

> **最新需求与历史实现的差异：** 以下保留当时执行记录。当前默认菜单授权允许后台运营登记到货，站点管理员只读；实际 Vue H5 尚未实现采购补货页面。后续应按最新已确认的“站管日常录入、自确认入库；后台主要查看调整”补齐页面、接口和权限，不应把历史“站管只读”继续作为目标权限。
>
> **执行记录 2026-11-16：阶段 3 已完成并通过 JUnit 与端到端冒烟验证。**
> - **核心 `InventoryService`**（`ruoyi-gas`）：入库（receive：到货 + 池累计 + IN 流水）/ 预占（reserve）/ 释放（release）/ 先进先出实扣（consume：锁批次按 `received_at` 分摊、释放剩余预占）/ 人工调整（adjust：指定批次 + 必填原因）。并发控制：池行 `SELECT ... FOR UPDATE` 串行化同站同气种操作；`uk_order_biz(order_ref, biz_type, receipt_id)` 保证订单动作幂等；核销入口先查 CONSUME 流水防重复核销。
> - **页面**（`gas_phase3_menu.sql` 已执行）：采购批次（5101，票据状态维护）、到货入库（5102）、付款登记（5103，只登记；登记/删除自动联动批次付款状态 UNPAID/PARTIAL/PAID）、库存台账（5104：池列表 + 批次余量弹层 + 流水页 + 调整 + 预警设置）、提醒记录（5601）。角色授权：运营全量、财务批次/台账只读 + 付款/票据可维护、站点管理员本站只读（@DataScope 后续阶段统一接入）。
> - **提醒机制（§3.7）**：`GasRemindServiceImpl` —— 批次将用完/已用完各 1 次（比例取采购商配置默认 20%）；用完未开票每 N 天 1 次最多 M 次（以 BATCH_EMPTY 触发时间为用完时间）；库存预警跌破触发一次、回升重新武装（`alert_armed`）；免打扰 21:00–8:00 推迟到 8:00（读 `gas_config`）；`gas_remind` 唯一键防重。定时任务 `gasRemindTask.scanDaily()`（sys_job 每天 9:00）；短信用 `ISmsSender` 接口占位实现（阶段 8 换阿里云）。
> - **JUnit**（`ruoyi-admin/src/test`，连本地 MySQL）：FIFO 跨批次扣减 ✓、并发核销不超扣（可用 100 双线程各扣 60 仅成功 1 个）✓、调整后池余额等于按流水重算 ✓，3/3 通过。
> - **冒烟发现并修复**：`GasInventoryLedger` 缺 `buyerId` 筛选属性导致列表页 500（与阶段2 `GasGun` 同类 OGNL 问题）；付款删除重算需先收集批次再删（物理删除后无法回查归属）。
> - **验证**：`mvn clean package` SUCCESS；HTTP 冒烟 20 项断言全过（批次号守卫、入库池累计 200、付款状态 PARTIAL→PAID 联动、调整 ±/缺原因拒绝、流水 IN×2+ADJUST×2、预警触发 STOCK_ALERT、批次删除守卫、票据状态校验、5 页面渲染 200）；验证后数据全部清理。
>
> **Review 修复（2026-11-16 第三轮，全部 13 项完成）：**
> - **H1 超卖缺陷**：`release` 原按池级预占校验——订单A预占30/B预占70时，A误释放50会把 `reserved` 打成负数导致超卖。改为**订单维度校验**（新增 `sumOrderReserved`：有效预占 = RESERVE − RELEASE − CONSUME），释放量不得超过该订单自身有效预占；新增 JUnit 用例4（池级余量足够但订单无预占的释放必须拒绝）4/4 通过。
> - **M1 付款状态竞态**：同批次并发登记付款时 `sumPaidByBatch` 可能漏看对方未提交记录，状态被低估。`insertPayment`/`deletePaymentByIds` 重算前先 `selectBatchByIdForUpdate` 锁批次行串行化。
> - **M2 提醒隔离**：`consume`/`adjust` 中的提醒调用包 try-catch，提醒失败只记日志不回滚主流程。
> - **M3 提醒幂等**：`insertRemind` catch `DuplicateKeyException`，`uk_remind` 兜底并发防重。
> - **M4 长事务拆分**：`scanDaily` 拆为生成（TransactionTemplate 单事务快速提交）+ 发送（逐条独立提交、单条失败仅记日志不影响其余）两阶段，短信等外部 IO 不再持有长事务。
> - **L1** 批次列表两个关联子查询改 join 派生表聚合；**L2** adjust 路由 3 次重复 `selectPoolById` 去重；**L4** `onBatchRemainChanged` 按批次去重再检查；**L5** 入库累计到货超过采购量记 warn 日志（`sumReceivedQty`）；**L6** LEDGER_* 常量上移至 `IInventoryService` 接口。
> - **L3 字典不一致**：`gas_remind_type` 字典值原为 `INVOICE_NEAR/INVOICE_DONE/INVOICE_OVERDUE/STOCK_LOW`，与代码常量 `BATCH_NEAR_EMPTY/BATCH_EMPTY/BATCH_UNINVOICED/STOCK_ALERT` 不一致——schema 已修正，存量库已 UPDATE 同步（字典 + 提醒表历史数据）；`remind.html` 搜索下拉与类型列改用字典渲染。
> - **L7 决策（遗留确认）**：① 批次/到货删除策略维持**物理删除 + 守卫**（批次有到货或付款记录不可删；到货一旦入池即不可删，只能负向 adjust 冲抵）——库存流水作为审计记录永不删除、页面不提供删除入口；② `gas_remind` 保留全部历史（含 FAILED），作为短信发送审计依据，阶段8接阿里云时补重发入口。
> - **L8 部署说明**：存量库升级需执行字典修正 SQL（`update sys_dict_data set dict_value=... where dict_type='gas_remind_type'`，本地库已执行）；新环境直接用最新 `gas_schema.sql` 无需额外操作。`gas_inventory_ledger` 流水表只增不删，按年增长，建议生产运行 12 个月后评估按 `create_time` 归档（当前量级无需分区）。

### 阶段 4 · H5 工程、公众号授权、手机号绑定

1. 建 `h5/` 项目：Vue 3 + Vite + Vant 4 + Vue Router + Pinia + axios；按 §4.2 配置主题。
2. OAuth：没有 token 时跳 `/api/h5/wx/authorize?redirect=...` → 微信回调 `code` → 换 openid → 建/查 `gas_member`。
3. **手机号绑定**：公众号网页拿不到微信绑定的手机号（这个能力只有小程序有），因此授权后用**短信验证码**绑定。未绑定手机号的会员只能浏览，不能下单或展示会员码。验证码规则：6 位，5 分钟有效，同一手机号 60 秒内只能发一次、每天最多 10 次，同一 IP 也限频。
4. JS-SDK：后端提供 `/api/h5/wx/jssdk-config`（access_token、jsapi_ticket 在服务端缓存）。
5. 员工身份：加气员、站点管理员首次从“员工入口”进入时，用后台账号密码绑定 openid（`gas_staff_binding`），之后自动识别角色和所属站点。
6. 本地开发用 mock openid 开关绕过微信授权。

> **执行记录 2026-11-16：阶段 4 已完成并通过 JUnit、后端冒烟与前后端端到端联调验证。**
> - **后端（`ruoyi-gas` + `ruoyi-admin/controller/h5`）**：
>   - **认证架构**：Shiro 对 `/api/h5/**` 匿名放行（`@Anonymous` 注解，免改 framework），JWT 认证由 `H5AuthInterceptor`（`/api/h5/wx/**` 除外）承担，`H5UserContext` ThreadLocal 提供身份；密钥/有效期读 `gas_config`（`h5.jwt.secret`/`h5.jwt.expire.hours`，新增）。
>   - **OAuth**：`H5WxController` —— `authorize`（302 微信授权页，redirect_uri 从请求构造；mock 模式直接回跳本地 callback）→ `callback`（code 换 openid → 登录 → 302 前端 `?h5_token=`）；redirect 白名单（`/` 开头相对路径或 `h5.base.url` 前缀）防开放重定向；`jssdk-config`（access_token/jsapi_ticket 服务端缓存 7200s−200s，SHA1 签名；mock 返回占位）。
>   - **登录身份**：员工绑定（`uk_openid`）优先，其次会员建档（首登自动 insert）；员工角色被取消后自动回退会员流程；`WxApiServiceImpl` 真实模式 RestTemplate 调微信 API（appid/secret 未配置时明确报错）。
>   - **短信验证码**：`H5SmsServiceImpl` —— 6 位码存内存（单实例，重启重发即可），限频走 `gas_sms_log`（`selectLastSendTime`/`countTodayByPhone`/`countTodayByIp` 表驱动）：60 秒 1 次、同号日限 10、同 IP 日限 30（新配置 `sms.code.ip.daily.max`）、校验失败 5 次作废（`sms.code.max.try`）、验证通过即作废；params 恒为掩码不入明文。
>   - **手机号绑定**：`uk_phone` 唯一校验（被其他微信占用时拒绝）；绑定成功签发新 JWT（phoneBound=true）；换绑同号幂等。
>   - **员工绑定**：`H5StaffBindController` 复用 framework 的 `SysLoginService`（内部经 `SysPasswordService.validate`，与后台登录同一套重试锁定）；`H5AuthServiceImpl.staffBind` 校验角色（`gas_staff`/`gas_station_admin`，经 `SysRoleMapper` 纯 SQL 查询，避开 `@DataScope` 对 Shiro 上下文的依赖）+ 站点反查（`sys_user.dept_id → gas_station`，新 mapper 方法）+ upsert（同账号换微信走 update，同微信绑其他账号拒绝）。
>   - **修复记录**：`@RestController` 下 `redirect:` 前缀不生效 → `H5WxController` 改 `@Controller`；新档会员 insert 后内存 status 为 null 被误判停用 → 仅存量档校验状态；mock 模式 `getJsapiConfig` 不再强制要求真实 appid；三个业务 Controller 漏 `@Anonymous` 被 Shiro 拦截 → 补齐。
> - **前端（`h5/` 新工程）**：Vue 3.5 + Vite 5 + Vant 4.9 + Router 4 + Pinia 2 + axios；主题 `--van-primary-color: #2A78D6`（§4.2）；页面——Index（身份分流：未授权/未绑手机号/已绑/员工）、BindPhone（60 秒倒计时 + 校验规则）、Mine、StaffBind（员工入口）；`request.js` 统一注入 `Authorization` + `X-Requested-With`（RuoYi `ServiceException` JSON 分支判定），401 自动清 token 跳授权；路由守卫统一处理 `h5_token` 回跳（摘参存 Pinia/localStorage）与未登录拦截；vite dev 代理 `/api → 9090`。
> - **验证**：`mvn clean package` SUCCESS；`GasH5AuthFlowTest` 4/4（mock 登录幂等/JWT 篡改拒绝、发码限频、错误码拒绝、绑定后 phoneBound、手机号被占拒绝、员工绑定/非员工拒绝）；后端冒烟 `smoke4.ps1` 13/13（302 链路、401、限频、角色拒绝、jssdk mock 签名）；前后端端到端 `smoke4e2e.ps1` 8/8（vite 页面、完整授权 302 链路落到前端、代理 API、代理 me）；数据与 mock 配置已清理复原。
> - **部署注意**：本机 Windows 排除端口段覆盖 8080/8022-8321/5153-5252，前端 dev 端口用 5300；生产部署时 Nginx 托管 h5 静态并反代 `/api`，`h5.base.url` 配置为 H5 站点完整域名。
>
> **Review 修复（2026-11-16 第四轮，H×3 + M×6 + L1 已完成）：**
> - **H1 mock 后门**：`wx.mock-openid` 数据驱动开关从 `gas_config` **移除**（schema 已删、存量库已清），改为 yml/启动参数 `gas.h5.mock-mode`（生产不配置即关闭），mock 模式下 openid = `"MOCK-" + code`（code 即模拟身份，测试可用不同 code 模拟多用户）。
> - **H3 发码并发绕过**：`sendCode` 加全局锁串行化，关闭限频检查与写入间的 TOCTOU 窗口（发码 QPS 极低，全局锁代价可忽略）。
> - **H2 token 生命周期**（阶段5前决策）：JWT 7 天有效且无吊销手段——**决策：阶段 5（下单）/阶段 6（核销、代下单）的业务入口统一查库校验身份与状态**（订单/库存操作前必查，接受每单一次查询代价）；轻量版已落地——`bindPhone` 写操作前校验库内 `member.status`。
> - **M1** redirect 白名单补 `/\` 绕过拦截；**M2** `RestTemplate` 超时 3s/5s（防微信接口故障占满容器线程）；**M3** `codeCache` put 时清理过期条目；**M5** 会员建档 catch `uk_openid` 并发冲突后回读；**M7** `me` 下沉至 `IH5AuthService`，Controller 不再直用 Mapper；**L9** StaffBind.vue 副作用移入 onMounted。
> - **M4 反代**（部署决策）：`application.yml` 加 `server.forward-headers-strategy: framework`——OAuth `redirect_uri` 按外部域名构造；**Nginx 必须传递 `X-Forwarded-Host` 与 `X-Forwarded-Proto`**。
> - **验证**：JUnit 8/8（H5 4 + 库存 4，H5 测试已适配新 mock 机制）；冒烟 `smoke4.ps1` 13/13、`smoke4e2e.ps1` 8/8；前端 build OK；数据清理干净。
> - **遗留待办**（记录不动）：M6 jssdk-config 匿名刷 access_token（真实模式上线时加刷新速率限制）；M8 短信骚扰面（阶段8接阿里云时评估图形验证码）；L1 token 经 URL 下发（可演进一次性 code 换 token）；L2 员工调动需重新绑定；L3/L4 JWT secret 热加载与弱默认值（生产检查清单：改值+环境变量）；L5 Vant 按需引入；L6 domain 基类风格统一；L7 测试反射耦合（阶段8换真实通道时改从发送参数断言）；L8 发送失败占限频窗口（保留现状）。

### 阶段 5 · 主动下单与拉卡拉支付（后端核心之二）

1. 扫站点码 → 选枪 → 输入金额 → 试算（枪价、预计气量 = **原价金额** ÷ 单价、可用券）→ **下单时校验本站对应气种可用库存足够** → 创建订单（待支付）。最新需求只在下单环节做库存充足性校验，不延伸到实际加气／核销环节；当前代码尚需补齐此校验。
2. **按站点所属采购商选择拉卡拉商户号**下单；支付层定义 `PayGateway` 接口，收银台和 JSAPI 各写一个实现，最终用哪种待定。
3. 以回调或查单结果为准把订单改为“待核销”，同一事务内按预计气量预占库存。
4. 回调幂等：交易号唯一键 + 条件更新 `WHERE status='WAIT_PAY'`。
5. 定时任务：待支付超时关单；对支付中的订单主动查单补偿。

> **执行记录 2026-11-16：阶段 5 已完成并通过 JUnit、冒烟全链路验证（mock 支付网关，真实拉卡拉留接口位）。**
> - **Domain/Mapper**：`GasOrder`/`GasOrderLog`/`GasPayTxn`/`GasDynamicToken` 四组；`closeOrder`（条件更新写 closed_at/close_reason）、`markSuccess`（幂等核心：`WHERE status != 'SUCCESS'`，同时容纳 PAYING 正常回调与 CLOSED 迟到回调）、`selectReusable`（动态码 30 秒窗口复用）。
> - **PayGateway 抽象**（`com.ruoyi.gas.pay`）：`IPayGateway`（launch/query）+ `MockPayGateway`（payUrl → `/api/pay/mock/cashier`，收银台确认即走真实回调处理路径）+ `PayGatewayRouter`（mock 开关/真实实现自动切换）；真实拉卡拉收银台/JSAPI 待商户证书具备后按同一接口接入。
> - **H5OrderService**：扫码进站（含每枪当前价，未定价枪展示但价格空、前端禁用）；试算（estVolume = 金额÷枪价，券阶段7接入）；创建订单（WAIT_PAY，`order.waitPay.minutes` 复用阶段1配置键）；发起支付（按站点采购商选 `gas_buyer_merchant`，复用未完成 PAYING 流水）；**支付回调**（txn markSuccess → 订单 markPaid 条件更新 → `reserve` 按预计气量预占[保供原则，池可压负] → 订单日志全链路一个事务）；**超时关单**（sys_job 101，每 5 分钟 `gasOrderTask.closeExpired()`）；**迟到支付**（关单后渠道扣款成功 → 流水落 SUCCESS + 订单 exception_note 标记待退款，阶段8处理）；**动态码**（核销码/会员码：30 秒刷新窗口复用、60 秒有效、单次使用）。
> - **Controller**：`H5OrderController`（/api/h5/c：station/preview/order/list/detail/pay/token/member-token，全部落实第四轮 H2 决策——订单业务入口查库校验会员状态与手机号绑定）；`H5PayController`（/api/pay：`notify/{merchantId}` 拉卡拉回调[验签待真实接入] + mock 收银台）。
> - **开发中发现并修复的真缺陷**：迟到支付回调被幂等条件吞掉（`markSuccess WHERE status='PAYING'` 在关单联动 CLOSED 后 0 行 → 渠道已扣款但系统无感知）——改为 `WHERE status != 'SUCCESS'`，CLOSED 流水也能落账并触发待退款标记（JUnit 用例3 覆盖）；另修复测试中 datetime 秒精度四舍五入把枪价生效时间推到未来的边界（effective_from 前移 1 分钟）。
> - **前端**（`h5/`）：新增 Station（选枪+金额+防抖试算+提交跳收银台）、OrderList（下拉刷新/分页/状态标签）、OrderDetail（核销码倒计时自动刷新 + WAIT_PAY 轮询支付状态 + 继续支付）、MemberToken 四页；Index/Mine 入口接通。
> - **验证**：`mvn clean package` SUCCESS；JUnit 12/12（订单 4/4：下单→支付→PAID+预占+重放幂等、双线程并发回调仅一次预占、超时关单+迟到支付标记、动态码窗口复用；H5 4/4；库存 4/4 回归）；冒烟 `smoke5.ps1` 17/17（mock 授权→绑号→进站→试算 35元=10m³→下单→收银台页→确认支付→PAID→核销码/会员码→池预占=10→标准 notify 端点重放幂等→未定价枪禁用/无价枪拒单）；数据全部清理。
> - **遗留待办**：真实拉卡拉接入（验签、收银台/JSAPI 二选一[见 7.2 C-2]、`GasOrderTask` 预留查单补偿扩展点）；`/api/pay/notify` 报文结构按拉卡拉实际文档适配；退款流程（阶段8）。
>
> **Review 修复（2026-11-16 第五轮，F1-F10 全部完成）：**
> - **F1（高）回调伪造**：`/api/pay/notify` 无验签即受理——任何人可伪造支付成功白嫖。加安全守卫：**验签实现前仅 mock 模式受理**，真实接入时以验签替代。
> - **F2（高）预占失败回滚交易**：`reserve` 抛异常（库存池未初始化）会把整个回调事务回滚 → 渠道已扣款但订单永远不 PAID。改为 try-catch 保交易 + `exception_note` 留痕人工介入；配合 F5 从下单入口杜绝该场景。
> - **F3（高）金额校验缺失**：`handleNotify` 新增 amount 参数（非空时必须与流水一致，不符拒绝记日志）；mock 收银台按流水金额传参。
> - **F4（中）交易号碰撞**：`out_trade_no` 秒级时间戳同秒双击撞 `uk_out_trade_no` 500 → 改毫秒时间戳。
> - **F5（中）未开卖站点**：`createOrder` 校验库存池已初始化（无池站点提示"暂未开放销售"），避免支付成功后预占失败。
> - **F6（中）分页 total**：订单列表 `TableDataInfo.total` 原为当前页行数 → 新增 `countMemberOrders`。
> - **F7（低）参数容错**：Controller 数字解析 catch `NumberFormatException` 返回"参数错误"而非 500。
> - **F8（低）动态码清理**：`gasOrderTask` 顺带清理一天前过期码（`deleteExpiredBefore`）。
> - **F9/F10（低）**：Station.vue 防抖 timer 卸载清理；`htmlMessage` 冗余编码转换删除。
> - **验证**：JUnit 13/13（订单 5/5，新增用例5：金额不一致拒绝且无副作用、正确金额仍可支付、无池站点拒单）；`mvn clean package` SUCCESS；前端 build OK；冒烟 `smoke5.ps1` 17/17；`gas_phase45_upgrade.sql` 两次执行幂等验证通过。
> - **交付物**：`sql/gas_phase45_upgrade.sql`（存量库升级脚本，幂等）、`docs/部署与测试手册_阶段1-5.md`（SQL 顺序/配置/启动/初始化/功能测试清单/FAQ）；`gas_schema.sql` 补 `order.amount.max`。

### 第六轮：未提交代码全量 review（2026-11-17）
- 范围：`RuoYi` 仓库全部未提交文件（`ruoyi-gas`、H5/管理端 controller、模板、SQL、冒烟脚本、原生配置 diff）；另核对未纳入 git 的 `h5` 前端。
- 高危修复：核销预占量改由订单流水计算；非法负预占/释放拒绝；支付回调强制商户与金额校验、支付事实与库存预占拆事务、重复收款留痕；支付发起订单行锁且渠道调用移出事务；JWT 移除公开默认密钥并强校验 issuer/角色/claims；OAuth 一次性 state + 一次性登录码换 JWT；站点管理员强制本站数据范围。
- 重要修复：非 mock 禁止回退 Mock 网关、prod Profile 禁开 mock；预占/释放先写幂等流水再改池；负调整保护订单预占；提醒改提交后执行；采购金额服务端计算且改价重算付款状态；站点归属/删除与删枪增加业务引用守卫；敏感配置脱敏及日志排除；枪号列表 XSS；枪价秒级精度；分页与金额精度边界。
- 验证结果：`mvn clean package` SUCCESS，JUnit 16/16（库存 6、H5 认证 4、订单 6）；新增并发支付单流水、伪造预占量、负调整压占回归；H5 build OK；升级 SQL 连续两次执行成功；`smoke4` 14/14、`smoke4e2e` 9/9、`smoke5` 19/19（含错商户/缺金额回调负例）。并发测试额外定位并修复 RR 旧快照：PAYING 流水查询改 `FOR UPDATE`，付款汇总同样使用当前读。
- 保留到后续：真实拉卡拉验签与退款（阶段8）；到货请求级业务幂等号（当前用事务与引用检查保护，阶段7前后补充）；未来生效价仍需运营避免空档；阶段6新增员工业务接口时每次请求刷新员工角色/状态。

### 阶段 6 · 加气员：班次、核销、代下单

1. **每个站同一时间只允许一人当班**：有人当班时，其他人开始当班会被拒绝，并提示当班人姓名。交班不需要复核，生成班次汇总快照。忘记交班的情况由站点管理员在后台“强制交班”，并记录操作人。
2. **加气前扫动态码核验 → 实际加气 → 确认实际量 → 完成核销**：加气前核验不标记订单完成、不执行实际量实扣；完成核销时记录确认量、按本站批次追溯实扣并释放剩余预占，记入当前班次。实际加气／核销不做库存不足拦截；气量与付款差额线下处理，不新增线上补款或差额退款。**核销必须在线完成**，接口失败提示重试，不做离线核销；该时序不改变完成核销前可退款的规则。
3. 扫会员码代下单 → 订单“待司机确认”（不预占）→ 司机 H5 确认 → 待支付 → 支付 → 待核销；拒绝或超时关闭。
4. JUnit 覆盖：重复核销、跨站核销、非当班操作、班次已变更、同站重复开班。

> **执行记录 2026-11-17：阶段 6 已完成开发、两轮 Review 修复及全量回归。**
> - **员工 H5**：工作台开班/本人交班、扫码预检与实际量核销、会员码识别与选枪代下单；司机订单详情支持确认/拒绝，确认前不预占。员工账号密码绑定入口 `/api/h5/staff/bind` 已补齐并验证新 JWT 身份。
> - **班次与权限**：同站唯一活跃班次由行锁和 `uk_station_active` 双重保证；核销、代下单、交班每次实时核验员工账号、角色、绑定、站点和部门；站管可在 H5 或后台强制交班，原因必填并留痕。
> - **核销事务**：订单行锁 + 班次行锁 + 动态码原子消费 + FIFO 库存实扣同一事务；交班汇总保存代下单数、核销数、CNG/LNG 气量和实付金额快照。
> - **管理后台**：新增“班次 → 班次记录”列表、状态/站点/员工筛选及强制交班；菜单 5501、按钮 5511/5512 和运营/站管授权由 `gas_phase6_upgrade.sql` 幂等升级。
> - **Review 修复**：核销与交班改 `READ_COMMITTED` 当前读；支付回调统一 `gas_order → gas_pay_txn` 锁顺序；支付提交与预占之间禁止抢核销；动态码消费原子复核有效期；交班汇总改条件聚合并补班次索引；动态码预检改 POST 请求体；强制交班拒绝空白及非字符串原因。
> - **前端 Review 修复**：核销/代下单绑定已核对 token 快照并丢弃旧响应；金额/气量按十进制字符串提交；动态码倒计时显式重启；订单分页防旧响应和失败跳页；确认后支付失败刷新状态；微信 iOS 签名 URL、SDK 失败重试、身份切换清理及强制交班二次确认。
> - **最终验证**：JUnit 20/20（库存 6、H5 认证 4、订单 6、员工班次 4）；`smoke4` 14/14、`smoke4e2e` 9/9、`smoke5` 19/19、`smoke6` 23/23；H5 build 与后端 package 成功；阶段6升级 SQL 连续两次执行成功，冒烟数据和服务自动清理。
> - **交接**：真实微信/拉卡拉仍需测试环境联调；优惠券为下一阶段，真实拉卡拉验签、退款、对账和报表按阶段8实施。

### 阶段 7 · 优惠券

券由**采购商**创建和发放，成本由采购商承担；**每单最多用一张，不限站点和支付方式**（实际可用范围为发券采购商名下的全部站点，见 §7.3）。功能包括：券模板（面额/门槛/有效期/总量）、定向发放（按手机号或会员）、下单用券、核销统计。

> **执行记录 2026-11-17：阶段 7 已完成开发、Review 修复及全量回归。**
> - **后台**：`GasCouponTemplate/GasCoupon` 服务与 `GasCouponController`，提供模板新增/编辑/停用、定向发券（手机号或会员ID）、券记录和核销统计；菜单 5301、按钮 5311/5312/5313 由 `gas_phase7_upgrade.sql` 幂等升级。
> - **H5**：站点试算返回可用券，主动下单可选券折后支付；代下单司机确认时选券；券包按未使用/已使用/已过期展示。
> - **订单规则**：每单一张券，面额/门槛/有效期/采购商归属实时校验；锁券进入 `USED`，关单/拒绝释放回 `UNUSED`，核销完成才计入统计；FIFO 预占气量仍按原价金额 ÷ 枪价。
> - **幂等**：主动下单新增 `request_id`，H5 修改枪/金额/券时更换请求号，网络重试复用首单；服务端会员行锁 + `uk_member_request` 唯一索引，参数变化拒绝复用。
> - **Review 修复**：发券目标空白拒绝；模板编辑与发券改行锁统一当前版本；停用原采购商不再阻塞模板维护；定时任务关单/过期券/动态码清理分项容错；补 `idx_status_expire`；后台列表 XSS 转义和券记录分页修复；路由复用旧页面、确认失败刷新、二次确认服务端金额、券包失败重试、员工身份隐藏会员入口。
> - **最终验证**：JUnit 26/26（库存 6、H5 认证 4、订单 12、员工班次 4）；`smoke7` 24/24；回归 `smoke4` 14/14、E2E 9/9、`smoke5` 19/19、`smoke6` 23/23；阶段7升级 SQL 连续两次执行成功；H5 build 与后端 package 成功。
> - **交接**：真实拉卡拉退款、验签、对账和报表进入阶段8。

### 阶段 8 · 退款、对账、报表、通知

1. **退款**（按本轮简化范围，只处理完成核销前退款；不需要审批）：
   - 保留待核销全额原路退款、释放预占的方案；是否已开始现场加气不新增为退款限制。
   - 已完成订单退款、部分差额退款、补加补款等不纳入本期；加不下或补加由线下处理。原“已完成订单后台退款”方案暂缓。
   - 核销前退款仍记录发起人、金额、原因和处理结果；退款后不能继续核销，同一笔收款不得重复退。
2. 拉卡拉对账单下载（按采购商商户号分别下载）→ 与 `gas_pay_txn` 自动比对 → 差异写入差异表，人工处理并填说明。
3. 采购商工作台（ECharts，RuoYi 已内置）、站点看板、各列表的今日/历史/时间范围/站点/气种筛选和导出。
4. 短信提醒（§3.7）接入真实短信通道。

### 阶段 9 · 联调、验收、上线

- 拉卡拉测试环境全流程：支付、重复回调、超时、核销前退款、对账，多个采购商商户号各测一遍；已完成退款不纳入本期验收；
- 真机测试微信内置浏览器（iOS/Android）：授权、短信绑定、扫一扫、支付返回；
- 部署：Nginx（H5 静态 + `/api` 和后台反代）+ RuoYi jar + MySQL；每日备份；日志保留。

---

## 3. 数据库设计

### 3.1 约定

- 表前缀 `gas_`；主键 `bigint` 自增；业务单号另设唯一字段（如 `order_no`）。
- 每张表带 RuoYi 通用字段：`create_by, create_time, update_by, update_time, remark`；需要软删除的表加 `del_flag`。
- 金额 `decimal(12,2)`；气量 `decimal(14,2)`；单价 `decimal(10,4)`（页面显示两位）。
- 气种 `gas_type` 为 `CNG`/`LNG`；**数量字段不跨气种汇总**，单位由气种决定（m³ / kg）。
- 状态、类型都用字典值，代码中用枚举。

### 3.2 表清单

| 分组 | 表 | 说明 |
|---|---|---|
| 组织 | `gas_buyer` | 采购商档案（不是登录单位）：名称、联系人、联系电话、`notify_phones`（短信接收号码，可多个）、开票提醒参数 |
| | `gas_buyer_merchant` | 采购商的拉卡拉商户配置：商户号、终端号、应用 ID、密钥引用（密钥不明文入库，用配置或加密存储） |
| | `gas_station` | 站点：`buyer_id`、`dept_id`、编码、名称、二维码 token、状态 |
| | `gas_gun` | 枪号：`station_id, gun_no, gas_type`，唯一 (station_id, gun_no) |
| | `gas_gun_price` | 枪价：`gun_id, price, effective_from, effective_to, create_by`；新价生效时自动结束旧价，历史保留 |
| | `gas_config` | 系统参数（键值） |
| 采购 | `gas_purchase_batch` | 采购批次：`batch_no, buyer_id, station_id, gas_type, purchase_date, qty, unit_price, amount, pay_status, invoice_status` |
| | `gas_purchase_receipt` | 到货/入库：`batch_id, station_id, gas_type, qty, consumed, adjusted, received_at, version`；**先进先出的扣减单位** |
| | `gas_purchase_payment` | 采购付款登记：`batch_id, amount, paid_at, method, voucher_url` |
| 库存 | `gas_inventory_pool` | 每站每气种一行的余额汇总（§3.3） |
| | `gas_inventory_ledger` | 库存流水，只追加不修改 |
| 会员与员工 | `gas_member` | 消费者：`openid`(唯一)、`unionid`、昵称、**`phone`（短信验证后写入，唯一）**、`phone_bound_at`、状态 |
| | `gas_staff_binding` | 后台用户 ↔ openid：`user_id, openid, station_id` |
| 订单 | `gas_order` | 销售订单（§3.4） |
| | `gas_order_log` | 订单状态变更日志：从/到状态、操作人、原因 |
| | `gas_dynamic_token` | 动态核销码 / 会员码（§3.5） |
| 班次 | `gas_shift` | `station_id, user_id, started_at, ended_at, active_flag, ended_by`，交班时写入汇总快照字段 |
| 支付 | `gas_pay_txn` | 支付流水：`order_id, merchant_id, out_trade_no(唯一), channel_trade_no(唯一), channel, amount, status, paid_at, raw_notify` |
| | `gas_refund` | 退款：`order_id, refund_no(唯一), amount, reason, source(CUSTOMER/ADMIN), status, operator` |
| | `gas_settle_bill` / `gas_reconcile_diff` | 拉卡拉对账单明细 / 对账差异及处理说明 |
| 营销 | `gas_coupon_template` | 券模板：`buyer_id`、面额、门槛、有效期、总量 |
| | `gas_coupon` | 用户券：`template_id, member_id, status, used_order_id, expire_at` |
| 通知 | `gas_remind` | 提醒记录（开票、库存预警），兼作防重复发送（§3.7） |
| | `gas_sms_log` | 短信发送日志：手机号、模板、参数、结果、服务商回执 |
| 审计 | 复用 `sys_oper_log` | 后台操作用 `@Log` 记录；业务关键动作另记在流水表和订单日志表 |

### 3.3 库存：站点池 + 到货批次 + 流水

**业务规则：一站一独立库存。** 每个采购批次只补充一个指定站点的库存；H5 站管本人弹窗确认提交成功后直接增加库存，无后台再确认。没有公共库存、跨站共享库存或车队库存。

**库存充足性只在下单时校验。** 支付时仍按预计气量预占本站库存，核销仍记录实际确认量及本站批次消耗、释放本单预占，但实际加气／核销不能再因库存不足被拦截。该规则只简化库存量门槛，不取消本站权限、订单状态、动态码和重复操作保护。

**实现口径：** 现有 `gas_inventory_pool` 按“站点 + 气种”分别记账，是同一站点库存内的气种分项，不代表跨站库存池。CNG 与 LNG 的单位不同，仍分开统计；本轮不合并气种数据或修改表结构。最新入库要求是站点管理员录入到货补货后，本人通过确认弹窗提交，保存成功才增加本站库存；不增加他人审核节点，库存流水继续记录操作人。

```sql
-- 每站每气种一行，用于快速判断可用量和加锁
CREATE TABLE gas_inventory_pool (
  id           bigint PRIMARY KEY AUTO_INCREMENT,
  station_id   bigint NOT NULL,
  buyer_id     bigint NOT NULL,
  gas_type     varchar(8) NOT NULL,
  total_in     decimal(14,2) NOT NULL DEFAULT 0,
  reserved     decimal(14,2) NOT NULL DEFAULT 0,
  consumed     decimal(14,2) NOT NULL DEFAULT 0,
  adjusted     decimal(14,2) NOT NULL DEFAULT 0,   -- 可负
  alert_threshold decimal(14,2) NULL,             -- 库存余量预警值
  UNIQUE KEY uk_station_gas (station_id, gas_type)
  -- 可用 = total_in + adjusted - consumed - reserved
);

CREATE TABLE gas_inventory_ledger (
  id           bigint PRIMARY KEY AUTO_INCREMENT,
  pool_id      bigint NOT NULL,
  receipt_id   bigint NOT NULL DEFAULT 0,   -- IN/CONSUME/ADJUST 填到货批次；RESERVE/RELEASE 为 0
  order_id     bigint NOT NULL DEFAULT 0,
  biz_type     varchar(16) NOT NULL,        -- IN / RESERVE / RELEASE / CONSUME / ADJUST
  qty          decimal(14,2) NOT NULL,
  reason       varchar(255) NULL,           -- ADJUST 必填
  operator     varchar(64)  NOT NULL,
  create_time  datetime NOT NULL,
  -- 同一订单的同一动作在同一到货批次上只能记一次，防止重复回调/重复核销造成重复扣减；
  -- 非订单流水 order_ref 为 NULL，不受此约束（MySQL 唯一索引允许多个 NULL）
  order_ref    bigint GENERATED ALWAYS AS (IF(order_id = 0, NULL, order_id)) STORED,
  UNIQUE KEY uk_order_biz (order_ref, biz_type, receipt_id),
  KEY idx_pool_time (pool_id, create_time),
  KEY idx_receipt (receipt_id)
);
```

**并发控制：** 一个站点的交易量不大，采用**悲观锁**最简单可靠。每次库存操作先 `SELECT ... FROM gas_inventory_pool WHERE station_id=? AND gas_type=? FOR UPDATE`，同一站同一气种的操作因此串行执行；先进先出扣减时，再按 `received_at, id` 顺序锁定有余量的到货批次。

**本轮简化与现代码差异：** 当前 `createOrder` 主要检查库存池存在，而 `consume` 会检查超出预占的可用量及 FIFO 批次余量，不足即拒绝。这与“下单校验、加气／核销不做库存不足拦截”的最新需求不同，后续需同时调整入口校验、核销路径及回归，不能只改提示文案。

实际量与付款不一致不在本期系统内补差；加不下或补加线下处理，仍保留原实际确认量记录口径。充足时沿用 FIFO 追溯；不足但不阻断核销时的批次记账需在实现设计中衔接，本轮不擅自决定负库存分摊、伪造入库或静默丢弃消耗。本文只更新需求，未更改现有算法或声称这些差异已修复。

### 3.4 订单

```sql
CREATE TABLE gas_order (
  id              bigint PRIMARY KEY AUTO_INCREMENT,
  order_no        varchar(32) NOT NULL UNIQUE,
  source          varchar(8)  NOT NULL,        -- SELF 主动 / PROXY 代下单
  status          varchar(20) NOT NULL,
  buyer_id        bigint NOT NULL,
  station_id      bigint NOT NULL,
  gun_id          bigint NOT NULL,
  gas_type        varchar(8)  NOT NULL,
  member_id       bigint NOT NULL,
  unit_price      decimal(10,4) NOT NULL,      -- 下单时枪价快照
  order_amount    decimal(12,2) NOT NULL,      -- 原价金额
  discount_amount decimal(12,2) NOT NULL DEFAULT 0,
  pay_amount      decimal(12,2) NOT NULL,      -- 实付
  refunded_amount decimal(12,2) NOT NULL DEFAULT 0,
  coupon_id       bigint NULL,
  est_volume      decimal(14,2) NOT NULL,      -- 预计气量 = order_amount ÷ unit_price（仅供参考，也是预占量）
  actual_volume   decimal(14,2) NULL,          -- 加气员确认量，即实扣量
  merchant_id     bigint NULL,                 -- 收款的采购商商户
  pay_channel     varchar(16) NULL,
  created_shift_id   bigint NULL,
  completed_shift_id bigint NULL,
  proxy_staff_id  bigint NULL,
  redeem_staff_id bigint NULL,
  expire_at       datetime NULL,
  paid_at         datetime NULL,
  completed_at    datetime NULL,
  closed_at       datetime NULL,
  close_reason    varchar(255) NULL,
  exception_note  varchar(255) NULL,
  create_time     datetime NOT NULL,
  KEY idx_station_status (station_id, status),
  KEY idx_buyer_time (buyer_id, create_time),
  KEY idx_member (member_id, create_time)
);
```

状态机：

```
SELF : WAIT_PAY ──支付回调──▶ PAID(待核销) ──核销──▶ COMPLETED
PROXY: WAIT_CONFIRM ──司机确认──▶ WAIT_PAY ──▶ PAID ──▶ COMPLETED
       WAIT_CONFIRM ──拒绝/超时──▶ REJECTED / CLOSED（不收款、不占库存）
WAIT_PAY ──超时──▶ CLOSED
PAID ──全额退款──▶ REFUNDING ──▶ REFUNDED（释放预占）
COMPLETED：本期不提供已完成退款流程，原后台部分／全额退款方案暂缓
```

每次变更都是条件更新 `WHERE id=? AND status=<期望状态>`，并写 `gas_order_log`。

### 3.5 动态码（按常规设计）

| 参数 | 核销码 | 会员码 |
|---|---|---|
| 内容 | 随机 128 位 token（不含订单信息） | 随机 128 位 token（不含手机号） |
| 刷新 | 页面每 30 秒自动刷新，也可手动点击刷新 | 同左 |
| 有效期 | 60 秒（留出扫码时间差）；生成新码后旧码立即作废 | 同左 |
| 使用次数 | 一次 | 一次（成功创建代下单后作废） |
| 网络 | **只能在线核销**；扫码或确认失败时提示重试，订单保持待核销 | 同左 |

`gas_dynamic_token`：`type(REDEEM/MEMBER), ref_id, token(唯一), expire_at, used_at`。核销在**一个事务**内完成：校验 token（存在、未过期、未使用）→ 订单属于加气员当班站点 → 订单状态为 PAID → 完成订单 → 先进先出扣减 → 标记 token 已用。

### 3.6 班次

- `gas_shift.active_flag`：当班时为 1、交班后为 NULL，唯一索引 `(station_id, active_flag)`，保证**每站同时只有一个进行中的班次**。
- 交班时把汇总（代下单数、核销数、分气种气量、金额）写入快照字段，之后不再变化；未完成订单不关闭。
- 强制交班记录 `ended_by` 和原因。

### 3.7 开票提醒与库存预警方案（短信通知采购商）

**开票提醒**，以采购批次为单位，只针对票据状态不是“已开票”的批次：

| 提醒类型 | 触发条件（默认值可在采购商上配置） | 次数 |
|---|---|---|
| 即将用完 | 批次剩余量 ≤ 批次采购量 × 20% | 1 次 |
| 已用完 | 批次剩余量 = 0 | 1 次 |
| 用完未开票 | 用完后满 7 天仍未开票 | 每 7 天 1 次，最多 3 次 |

- 批次剩余量 = 该批次所有到货的入库量 + 调整量 − 已扣量（先进先出扣减保证能精确算到批次）。
- **检查时机**：核销事务提交后发出“批次余量变化”事件，异步检查前两种；“用完未开票”由每天 9:00 的定时任务扫描，这个任务同时补发此前漏发的提醒。
- **通知对象**：采购商的 `notify_phones`；同时在后台消息中心生成一条提醒，采购商和站点管理员都能看到。
- **免打扰**：21:00–8:00 之间触发的提醒推迟到 8:00 发送。
- **防重复**：`gas_remind` 唯一键 `(batch_id, remind_type, seq)`；短信失败重试 3 次，结果写 `gas_sms_log`。
- **停止条件**：后台或站点管理员 H5 把批次票据状态改为“已开票”后，不再提醒。
- 短信模板示例：`【签名】您在{站点}的采购批次{批次号}（{气种}{采购量}{单位}）已使用{比例}，剩余{余量}{单位}，请及时安排开票。`

**库存余量预警**（PRD §3.1-5）用同一套机制：站点池可用量 ≤ `alert_threshold` 时短信提醒采购商；回升到阈值以上后再次跌破才会再提醒。

---

## 4. UI 设计

### 4.1 管理后台（RuoYi Thymeleaf）

**沿用 RuoYi 的布局和组件**（左侧菜单 + 多标签页 + bootstrap-table），只做样式统一：

- 新增 `static/gas/gas-theme.css`：主色 `#2A78D6`，气种标签 CNG `#0E9F7E` / LNG `#5B4BD6`，金额色 `#E8501F`，状态色同 Design_Spec；数字列 `tabular-nums`，金额右对齐并显示 `¥` 和两位小数。
- 字段、文案、筛选项以原型 `a-*.html` 为准；预计气量处处标注“仅供参考”。
- 同一套菜单，按角色显示，按数据权限过滤：超级管理员、运营、财务看全部，站点管理员只看本站。列表、工作台和导出都提供“采购商”筛选，并显示每条记录归属的采购商。

| 一级菜单 | 页面 | 原型 |
|---|---|---|
| 工作台 | 销售额、订单数、预计/实际出气量、各站库存与预警、待核销订单、开票提醒 | `a-dashboard` |
| 基础资料 | 采购商（含商户号、通知手机号）、站点与枪号（站点二维码下载）、枪价 | `a-stations`、`a-price` |
| 采购与库存 | 后台以采购批次、到货和库存台账查看及调整为主，保留库存调整和预警设置；日常补货由站管端录入，不经后台审核 | `a-prepay`、`m-buyers`、`m-stations` |
| 销售订单 | 订单列表/详情（状态日志、支付、核销、退款记录）、**订单退款** | `a-orders` |
| 营销 | 券模板、发放、核销统计 | `a-marketing` |
| 财务 | 采购付款登记、销售收款、拉卡拉对账与差异 | `a-finance` |
| 班次 | 各站班次汇总、强制交班 | `s-reconcile` |
| 消息 | 开票提醒、库存预警、短信发送记录 | — |
| 系统管理 | RuoYi 原有：用户、角色、部门、字典、日志、定时任务 | — |

### 4.2 公众号 H5（Vue 3 + Vant 4）

在 Vant 主题变量中设置 Design_Spec 的色值（`--van-primary-color: #2A78D6` 等），金额、气种标签、状态色做成公共组件。

| 角色 | 页面 | 原型 | 要点 |
|---|---|---|---|
| 消费者 | 绑定手机号 | — | 授权后首次进入；手机号 + 短信验证码 |
| | 站点下单 | `c-pay` | 站点二维码进入；选枪、输入金额，实时显示枪价、预计气量（仅供参考）、优惠和实付 |
| | 订单确认/核销码 | `c-order` | 代下单确认；支付后展示 30 秒刷新的核销码；待核销时可申请全额退款 |
| | 我的 | `c-mine` | 会员码（30 秒刷新）、订单历史、优惠券 |
| 加气员 | 工作台 | `s-workbench` | 当班状态、开始当班/交班、待核销列表 |
| | 扫码 | `s-scan` | 微信扫一扫：核销码 → 确认实际气量；会员码 → 代下单 |
| | 班次记录 | `s-reconcile` | 本人班次与交班汇总 |
| 站点管理员 | 本站概览 | `m-stations` | 采购量、出气量、余量、订单、往来款 |
| | 采购批次与补货 | `m-buyers` | **已确认**：站点管理员录入本站采购批次及到货补货，本人确认弹窗后提交入库，不需要他人确认，全部留痕；付款、票据、档案编辑权限另行明确 |

公众号菜单建议：「加气下单」「我的订单」「员工入口」。

---

## 5. 接口划分（H5）

| 前缀 | 认证 | 示例 |
|---|---|---|
| `/api/h5/wx/*` | 无 | `authorize`、`callback`、`jssdk-config` |
| `/api/h5/sms/*` | JWT（未绑定手机号也可调用） | `send-code`、`bind-phone` |
| `/api/h5/c/*` | JWT（会员，已绑定手机号） | `station/{code}`、`order/preview`、`order`、`order/{no}/confirm`、`order/{no}/pay`、`order/{no}/refund`、`order/{no}/token`、`member/token`、`coupons` |
| `/api/h5/s/*` | JWT + 加气员 | `shift/start`、`shift/end`、`orders/pending`、`redeem/scan`、`redeem/confirm`、`proxy/order` |
| `/api/h5/m/*` | JWT + 站点管理员 | `overview`、`orders`、`batches`、`receipts`、`payments` |
| `/api/pay/notify/{merchantId}` | 拉卡拉验签 | 支付/退款回调；按商户号取密钥验签 |

`/api/h5/s/*` 的写操作在服务端校验当班状态；`/api/h5/m/*` 在服务端限定本站；前端的按钮显示控制只是辅助。

---

## 6. 目录结构

```
gas/
├─ RuoYi/
│  ├─ ruoyi-gas/                      # 新增业务模块
│  │  └─ src/main/java/com/ruoyi/gas/
│  │     ├─ domain/  mapper/  service/      # 订单、库存、班次、支付、优惠券、提醒……
│  │     ├─ pay/                            # PayGateway 接口 + 拉卡拉实现、验签、回调
│  │     ├─ sms/                            # SmsSender 接口 + 阿里云短信实现（dysmsapi SDK）
│  │     └─ wx/                             # 公众号 OAuth、JS-SDK
│  ├─ ruoyi-admin/src/main/java/com/ruoyi/web/controller/
│  │     ├─ gas/          # 后台页面控制器（Thymeleaf）
│  │     └─ api/h5/       # H5 接口 + JWT 过滤器
│  ├─ ruoyi-admin/src/main/resources/templates/gas/
│  └─ sql/gas_schema.sql                     # 业务表 + 菜单 + 字典 + 定时任务初始化
├─ h5/                                      # 新增：公众号 H5
└─ design/ docs/
```

---

## 7. 业务决策记录

### 7.1 已确认（2026-11-16 及本轮补充确认）

| # | 事项 | 结论 |
|---|---|---|
| A-0 | 采购方、采购商与本轮“供应商”称谓 | 已确认：采购方就是采购商，本轮所称“供应商”也指该采购商；统一使用“采购商”，不新增主体或关联关系（原 C-5 已关闭） |
| A-1 | 库存模式 | 本轮重申：一站一独立库存，无公共或跨站共享库存，订单只从对应站点出气 |
| A-2 | 预占量口径 | 按原价金额 ÷ 枪价 |
| A-3 | 实扣依据 | 加气员确认量 |
| A-4 | 退款 | 本轮明确：完成核销前可以退款，不需要审批；其他退款场景不考虑，原已完成订单后台退款暂缓，不新增开始加气后的退款门槛 |
| A-5 | 采购商与站点 | 一个采购商对应多个站点，一个站点只属于一个采购商 |
| A-6 | 批次扣减 | 仅在订单对应站点、对应气种内按到货批次先进先出，不跨站扣减 |
| A-7 | 拉卡拉商户号 | 归属采购商（每个采购商一个） |
| A-8 | 采购批次与入库 | 每批采购给指定站点库存补货；H5 站点管理员录入并本人弹窗确认提交成功后直接增加库存，无后台再确认（原待确认 C-4 已明确） |
| A-9 | 库存不足校验 | 本轮明确：仅在下单时校验；实际加气／核销不做库存不足拦截，不要求系统处理现场缺货 |
| A-10 | 实际量与付款差额 | 本轮明确暂缓：加不下或补加线下处理，本期不新增线上补款、差额退款或实际量重算应付 |
| A-11 | 数据误录 | 本轮明确暂缓：后续再在后台增加修改功能，本期不扩展专项纠错；现有查看、调整及留痕不删除 |
| A-12 | 超采购量到货 | 开发启动时已确认方案 A：同批次累计到货不得超过采购数量，超出部分另建批次；M1 将当前后端仅告警放行改为服务端拒绝，并覆盖并发到货 |
| B-1 | 动态码 | 按常规设计（§3.5）；不支持离线核销 |
| B-2 | 价格 | 按枪定价，后台维护 |
| B-3 | 优惠券 | 不能叠加；不限站点和支付方式；成本由采购商承担 |
| B-4 | 当班 | 本轮重申：一个站同一时间只能有一名当班加气员核验、核销；交班不需要复核 |
| B-5 | 站点管理员 | 本站采购批次、到货补货的日常录入由站管端完成，参考 `m-buyers.html`；本人确认弹窗即可，不需第二人或后台审核 |
| B-6 | 开票提醒 | 提醒采购商，短信通知；触发规则见 §3.7 |
| B-7 | 采购付款 | 只登记 |
| B-8 | 公众号与手机号 | 公众号已认证；授权后收集手机号（短信验证码绑定） |
| B-9 | 车队 | 本轮明确：系统不存在车队概念，车队人员按散户交易，无车队账户、库存或专属结算 |
| B-10 | 后台库存职责 | 后台也能调整，但主要用于查看、调整，不是每笔补货的必经审核端 |
| B-11 | 核验与核销时序 | 已确认：加气前扫码核验，实际加气后确认实际量并完成核销；加气前核验不等于订单完成或实际量实扣（原 C-6 已关闭） |

### 7.2 方案自行采用的默认做法（可改）

- 消费者在 H5 上可对**待核销**订单自助申请全额退款，后台也能操作。
- 待核销订单全额退款后，如果券仍有效则退回用户，仍作为现有方案默认；已完成退款及相关退券规则本期不实施。
- 原按“预计量与实际量差额 × 枪价”计算后台退款的方案已暂缓；气量与付款差异线下处理，不据旧方案恢复本期差额结算功能。
- 优惠券只能在发券采购商名下的站点使用（因为成本由该采购商承担，收款也进入该采购商的商户号）。
- 原“库存够才允许核销超出预计量”的条件已被本轮答复覆盖：库存充足性只在下单时校验，核销不按库存量阻断；实际量记录口径不变。
- 站点管理员维护采购批次、录入到货补货并本人弹窗确认，已提升为 §7.1 的明确需求，不再是方案默认。付款和票据状态的编辑、采购商档案是否只在后台维护仍需细化；不能因入库写权限已确认，就自动扩大到跨站档案、商户配置等权限。

| C-1 | 后台账号体系 | 后台使用方就是采购商自己，用一套账号；可录入多个采购商档案，不给每个采购商开账号，后台能看到归属并按采购商筛选即可 |
| C-3 | 短信服务 | 阿里云短信，账号已有，只需申请模板 |

### 7.3 仍待确认

| # | 问题 |
|---|---|
| C-2 | 拉卡拉用收银台还是 JSAPI（A-7 待定部分）。支付层已做接口抽象，当前 mock 流程已实现，真实接入前需确定 |
