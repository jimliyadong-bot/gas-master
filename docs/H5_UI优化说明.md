# H5 UI 优化说明（2026-11-20）

## 范围与风格

本轮是用户要求的**全部现有H5页面视觉优化**，不是M2—M6业务实现或验收。仅修改 `h5/` 的展示层；没有修改后端、数据库、接口、权限、支付／核销流程或采购提交凭据逻辑。

- 蓝白主色、浅灰蓝底色、统一圆角与留白；沿用CNG绿色、LNG紫色、金额橙色的语义。
- 内容最大宽390px；手机端自适应，桌面居中，不再把移动按钮横向拉满整屏。
- 首页面向身份展示主操作卡、身份摘要和快捷服务；不展示虚构统计、钱包或充值。
- 订单卡突出状态、金额、站点和气种；库存按气种分别展示，不跨气种求和。
- 表单增加步骤和说明，统一字段、按钮、禁用态、空态、确认弹窗及优惠券底部弹层。
- 首页等一级页面增加底部导航，按已加载身份展示对应入口；身份尚未加载时仅展示首页／我的，不猜测权限或额外请求身份。表单和详情页不增加会干扰提交的底部导航。
- 本地图标为内联SVG，系统字体，无新增依赖和外部图片／字体请求；支持键盘焦点、触控和减少动态效果偏好。

## 页面覆盖

| 页面组 | 实际文件（`h5/src/views/`） |
|---|---|
| 首页与账号 | `Index.vue`、`Mine.vue`、`BindPhone.vue`、`StaffBind.vue` |
| 消费者业务 | `Station.vue`、`OrderList.vue`、`OrderDetail.vue`、`CouponList.vue`、`MemberToken.vue` |
| 员工工作台 | `StaffWorkbench.vue`、`StaffProxy.vue`、`StaffRedeem.vue` |
| 站管采购补货 | `Manager.vue`、`ManagerBatchCreate.vue`、`ManagerBatch.vue` |

共15个页面。改动前后对这15个文件的 `<script setup>` 内容做SHA-256比对，全部一致；页面调整限模板与样式。金额、数量、时间、原请求号、身份绑定和异步回调保护仍由原逻辑处理。模板链接与按钮需与业务回归一起检查，不能只靠脚本未变判断无回归。

### 公共展示层

- `h5/src/styles/theme.css`：设计令牌、移动宽度、Vant组件样式、表单／弹层／导航安全区。
- `h5/src/components/AppIcon.vue`：本地图标。
- `h5/src/components/PageIntro.vue`：页头、图标及说明。
- `h5/src/components/AppTabs.vue`：角色相关一级导航。
- `h5/src/App.vue`、`h5/src/main.js`：统一页面容器与公共组件注册。

原型 `design/prototypes/` 与后台Thymeleaf页面不在本轮范围内，未修改生成CSS。

## 验证（UI初版）

工作目录 `D:/Project/workspace/gas/h5`，实际执行：

```sh
node --test tests/dev-proxy.test.mjs tests/station-draft.test.mjs tests/station-identity.test.mjs
PLAYWRIGHT_MODULE='C:/Users/jim/AppData/Local/npm-cache/_npx/e41f203b7505f1fb/node_modules/playwright-core/index.mjs' node --test tests/ui-pages.smoke.mjs tests/station-pages.smoke.mjs tests/station-review-regression.test.mjs
npm run build
```

- 工具／代理：**28通过，0失败，0跳过**。
- 浏览器：**23通过，0失败，0跳过**。既有18项采购／异常／身份切换回归保留，新增5项UI检查。
- 新增 `h5/tests/ui-pages.smoke.mjs`：在320、390、768、1440四种宽度遍历全部15页面，检查无页面横向溢出、居中宽度、底部导航及弹层宽度；另检查不同身份入口、空列表、空班次／他人当班、订单状态操作。覆盖真实Vue组件，全部业务API拦截为mock。
- 构建成功，396 modules transformed；没有npm test／lint／type-check脚本，不以构建替代页面和业务测试。
- 浏览器依赖使用本机既有Playwright／Chromium。其他机器需调整模块路径，未配置会跳过，跳过不算通过。
- 截图写入测试输出的本机临时目录 `gas-h5-ui-*`；人工查看首页、站管库存、订单详情、扫码核销、小屏绑定表单及桌面优惠券弹层。
- 开发态截图会等待主题CSS／字体加载，避免把Vite瞬时无样式画面误判成布局结果。

**未验证**：真实MySQL事务、真实JWT链路、微信真机、真实支付／短信。本轮没有连接或写入数据库，没有执行JUnit；原M1数据库验收缺口保持。会员码和核销码仍使用当前文本凭据展示，没有用装饰图标伪装真实二维码，也没有借UI优化实现延期业务。

## 后续修正：服务图标显示

用户反馈首页四个服务入口只有彩色底块。已为直接使用图标的7个页面显式导入 `AppIcon`，不再只依赖入口文件的全局注册；显示扫码、票券、工作包、头像图案。仅新增展示组件import，业务逻辑未改。

在独立mock测试中移除全局图标注册，修复前首页出现8个未解析组件，修复后7页正常且4个入口跳转通过。这验证了代码对注册缺失的修正，不代表已确认用户浏览器当时的具体原因。

本轮在`h5/`重新执行上方浏览器命令及`npm run build`：**24项通过、0失败、0跳过，构建成功396 modules**；其中UI新增1项，原4宽度检查增加SVG实际尺寸、路径和描边断言。工具／代理28项未复跑，保留为初版结果。截图 `C:/Users/jim/AppData/Local/Temp/gas-h5-ui-uFEFIY/service-icons.png` 已人工查看；没有访问真实业务后端或数据库。

## 如何查看

H5开发服务已启动时，刷新 `http://localhost:5300/index` 即可；若仍显示旧样式，用 `Ctrl+F5` 刷新。若前端未启动，在 `h5/` 执行 `npm run dev`。不需要重导SQL或重新打包后端，也不要为刷新样式清理采购／补货提交凭据。

桌面看到居中的窄页面是按移动H5设计的正常结果。可用浏览器设备模式预览390px手机尺寸，再检查站管／员工入口与表单。
