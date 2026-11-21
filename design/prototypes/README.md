# 加气站批量采购与销售记录系统 · 可交互原型

打开 `index.html` 选择消费者、加气员、管理员或采购方管理后台，直接进入对应首个业务页面，通过底部导航或后台菜单继续体验。移动端顶部关闭按钮、后台侧栏“返回角色选择”可返回首页。

项目说明、演示路径和多页面预览集中在 `overview.html`，可从首页“项目说明”进入。建议用本地静态服务器，例如在本目录执行 `python -m http.server 8765`，再访问 http://localhost:8765/ 。所有资源均已本地化，无需联网。

## 页面清单（15 个流程页，另含入口与总览）

| 端 | 文件 |
|---|---|
| 入口与说明 | `index.html` 角色选择 · `overview.html` 项目说明与原型总览 |
| 消费者 H5 | `c-pay.html` 主动下单 · `c-order.html` 订单确认 / 动态核销码 · `c-mine.html` 个人会员码 / 我的 |
| 加气员 H5 | `s-workbench.html` 当班与待办 · `s-scan.html` 扫码核销 / 代下单 · `s-reconcile.html` 班次记录 |
| 管理员 H5 | `m-stations.html` 库存 · `m-buyers.html` 管理 |
| 采购方管理后台 | `a-dashboard.html` 工作台 · `a-stations.html` 站点与枪号 · `a-prepay.html` 采购与库存 · `a-price.html` 销售价格 · `a-orders.html` 销售订单 · `a-marketing.html` 优惠券 · `a-finance.html` 采购付款 / 收款 |

消费者电子账户、充值、B 端开放 API 和授信管理不属于当前需求，不在入口和演示流程中提供。

## 目录

```
assets/
  tailwind.css          编译产物（页面引用）
  src/input.css         组件类源文件
  src/tailwind.config.js 设计令牌
  js/common.js          外壳布局、弹层、Tab、Toast、图表主题、示意二维码
  vendor/               ECharts、FontAwesome（本地）
  img/truck.jpg         Unsplash 图片
```

## 重新编译 CSS

修改 HTML 中的 Tailwind 类或 `input.css` 后，在项目根目录（`gas/`）执行：

```
npx tailwindcss@3 -c design/prototypes/assets/src/tailwind.config.js -i design/prototypes/assets/src/input.css -o design/prototypes/assets/tailwind.css --minify
```

所有数据为演示用模拟数据；二维码为示意图，不可扫码。扫码核销、拉卡拉支付与班次数据均为本地交互演示，不产生真实交易。
