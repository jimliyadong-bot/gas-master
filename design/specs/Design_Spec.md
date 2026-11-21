# 设计规范（批量采购系统原型 v1.0）

令牌源文件：`design/prototypes/assets/src/tailwind.config.js`；组件类：`design/prototypes/assets/src/input.css`。

## 1. 颜色

| 用途 | 令牌 | 色值 | 说明 |
|---|---|---|---|
| 品牌主色 | `brand-500` | `#2A78D6` | 按钮、链接、选中态；`brand-50…900` 为完整色阶 |
| CNG | `gas-cng` / `gas-cngbg` | `#0E9F7E` / `#E3F6F0` | 气种标签 `.tag-cng`，单位 m³ |
| LNG | `gas-lng` / `gas-lngbg` | `#5B4BD6` / `#ECEAFD` | 气种标签 `.tag-lng`，单位 kg |
| 金额强调 | `money` | `#E8501F` | 实付、优惠金额 |
| 状态 | `ok` / `warn` / `bad` | `#14A06F` / `#E49B0F` / `#D93B3B` | 成功、预警、异常 |
| 文字 | `ink-900/700/500/300/100` | `#17202E` … `#EEF1F5` | 标题 → 次要 → 辅助 → 线条 → 底色 |
| 页面底色 | `page` | `#F4F6F9` | |

图表配色（ECharts，固定顺序、不循环）：`#2a78d6 #eb6834 #1baf7a #eda100 #e87ba4 #008300 #6250d6 #e34948`，见 `common.js` 中 `VIZ.series`。

## 2. 字体与数字

- 字体：系统字体栈（PingFang SC / Microsoft YaHei）。
- 所有数字使用等宽数字（`tabular-nums`），表格金额右对齐（`.num`）。
- 金额统一 `¥` 前缀、千分位；气量保留 2 位小数并带单位。

## 3. 组件

| 类名 | 用途 |
|---|---|
| `.card` | 白底圆角 14px 卡片 |
| `.btn-primary / -ghost / -soft / -danger`，`.btn-lg / -sm` | 按钮 |
| `.tag-*` | 状态 / 气种标签 |
| `.kpi-label / .kpi-value / .delta-up / .delta-down` | 指标卡 |
| `.seg` | 分段切换 |
| `.field / .label` | 表单 |
| `.tbl` | 后台表格 |
| `.sheet`（移动端底部弹层）、`.drawer`（后台右侧抽屉）、`.toast` | 弹层 |

交互约定（`common.js`）：`data-open="#id"` 打开弹层，`data-close` 关闭；`data-toast="文案"` 弹出提示；`[data-tabs]` / `.seg` 子元素点击切换，配合 `data-pane` / `data-pane-id` 切换内容。

## 4. 布局

- **移动 H5**：`body[data-shell=mobile].m-page`，最大宽 390px，居中显示，小屏自适应，内容区左右留白 16px；底部导航、支付栏和弹层与页面同宽，底部适配设备安全区。独立访问不模拟系统状态栏，仅嵌入原型总览时保留。`data-title` 标题、`data-back` 返回、`data-tabbar=c|s|m` 底部导航。
- **管理后台**：`body[data-shell=admin]`，按 1440 宽设计；左侧 220px 深色菜单 + 顶栏面包屑（`data-crumb="一级/二级"`），内容放 `<main id="main">`。

## 5. 文案原则

面向采购方、管理员和一线加气员，优先使用业务表达：“采购批次”“剩余库存”“预占气量”“加气员确认量”“采购付款 / 销售收款”；预计气量必须标注“仅供参考”，不得混同于实际计量。待确认规则集中列于当前 PRD。

## 6. 实现H5视觉迭代（2026-11-20）

本节对应实际Vue/Vant前端 `h5/`，不是原型生成CSS修改。

- 实现令牌及通用样式：`h5/src/styles/theme.css`；延续品牌蓝、CNG绿、LNG紫、金额橙，新增低对比背景、深色正文及统一状态展示。
- 页面最大宽390px居中；移动端自适应；底部导航与弹层同宽并适配安全区，表单／详情页保留原业务操作。
- 主按钮高48px、圆角12px；卡片圆角16—20px；正文采用系统字体，数字使用tabular-nums。
- `AppIcon`为本地内联SVG；`PageIntro`统一标题与说明；`AppTabs`按已知角色展示入口，不为补导航而修改身份请求逻辑。
- 首页采用主操作卡与快捷服务，订单突出状态／金额，库存按气种分别展示；不增加虚构统计、钱包或充值。
- 15个页面覆盖与验证命令见 [H5 UI优化说明](../../docs/H5_UI优化说明.md)。UI检查不替代业务／数据库验收。
