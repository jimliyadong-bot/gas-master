import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { pathToFileURL } from 'node:url'
import { createServer as netServer } from 'node:net'
import { mkdtemp } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { createServer } from 'vite'

// 15个实际Vue页面的响应式检查，所有API均拦截；不访问业务后端或数据库。
const enabled = !!process.env.PLAYWRIGHT_MODULE
let server, browser, origin, shots
before(async () => {
  if (!enabled) return
  const { chromium } = await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE).href)
  const socket = netServer()
  await new Promise((ok, bad) => { socket.once('error', bad); socket.listen(0, '127.0.0.1', ok) })
  const port = socket.address().port
  await new Promise(ok => socket.close(ok))
  server = await createServer({ server: { host: '127.0.0.1', port, strictPort: true }, logLevel: 'error' })
  await server.listen()
  origin = `http://127.0.0.1:${port}`
  browser = await chromium.launch({ headless: true })
  shots = await mkdtemp(join(tmpdir(), 'gas-h5-ui-'))
  console.log(`H5 UI screenshots: ${shots}`)
})
after(async () => { try { await browser?.close() } finally { await server?.close() } })

const coupon = { couponId: 1, templateName: '加气立减券', buyerName: '测试能源采购商', faceValue: '20.00', thresholdAmount: '100.00', expireAt: '2026-12-31 23:59:59' }
const guns = [{ gunId: 1, gunNo: '01', gasType: 'CNG', unitPrice: '3.5800', enabled: true }, { gunId: 2, gunNo: '02', gasType: 'LNG', unitPrice: '4.2000', enabled: true }]
const order = { orderNo: 'UI202611200001', stationName: '测试城北综合能源站', gasType: 'CNG', gunNo: '01', status: 'PAID', orderAmount: '300.00', payAmount: '280.00', discountAmount: '20.00', estVolume: '83.80', createTime: '2026-11-20 09:20:00', paidAt: '2026-11-20 09:21:00', memberPhone: '13800000001', shiftId: 7 }
const batch = { batchId: 1, batchNo: 'PB202611200001', stationName: '测试城北综合能源站', buyerName: '测试能源采购商', gasType: 'CNG', qty: '10000.00', receivedQty: '5800.00', unitPrice: '3.5800', amount: '35800.00', purchaseDate: '2026-11-20', remark: '本站采购，按实际到货分次入库' }
const inventory = [{ poolId: 1, gasType: 'CNG', available: '4200.00', totalIn: '5800.00', reserved: '300.00', consumed: '1300.00', adjusted: '0.00' }, { poolId: 2, gasType: 'LNG', available: '2300.00', totalIn: '3000.00', reserved: '100.00', consumed: '600.00', adjusted: '0.00' }]
async function fixture(width) {
  const context = await browser.newContext({ viewport: { width, height: 900 }, reducedMotion: 'reduce' })
  await context.addInitScript(() => localStorage.setItem('h5_token', 'ui-only-mock-token'))
  const state = { role: 'MEMBER', empty: false, orderStatus: 'PAID', workMode: 'mine', errors: [], unexpected: [] }
  const page = await context.newPage()
  page.setDefaultTimeout(8000)
  page.on('pageerror', e => state.errors.push(e.message))
  page.on('console', message => { if (message.type() === 'warning' && /Failed to resolve component/.test(message.text())) state.errors.push(message.text()) })
  await context.route('**/api/**', async route => {
    const req = route.request(), url = new URL(req.url()), path = url.pathname
    if (!path.startsWith('/api/')) return route.continue()
    const ok = data => route.fulfill({ json: { code: 0, data } })
    if (path === '/api/h5/c/me') return ok({ role: state.role, phone: '', phoneBound: false, memberId: 71, userId: 21, stationId: state.role === 'MEMBER' ? null : 7 })
    if (path === '/api/h5/c/station/test-station') return ok({ stationName: order.stationName, guns })
    if (path === '/api/h5/c/order/list') return route.fulfill({ json: { code: 0, rows: state.empty ? [] : ['PAID', 'WAIT_PAY', 'COMPLETED'].map((status, i) => ({ ...order, orderNo: `${order.orderNo}${i}`, status })) } })
    if (path === `/api/h5/c/order/${order.orderNo}`) return ok({ ...order, status: state.orderStatus })
    if (path === '/api/h5/c/member/token' || path.endsWith(`/${order.orderNo}/token`)) return ok({ token: 'a1b2c3d4'.repeat(4), remainSeconds: 60 })
    if (path === '/api/h5/c/coupon/list' || path === '/api/h5/c/coupon/available') return ok(state.empty ? [] : [coupon])
    if (path === '/api/h5/c/order/preview') {
      const input = req.postDataJSON()
      return ok({ ...input, gasType: 'CNG', unitPrice: '3.5800', estVolume: '83.80', payAmount: input.orderAmount, discountAmount: '0.00', coupons: [coupon] })
    }
    if (path === '/api/h5/s/workbench') return ok({ stationName: order.stationName, staffName: '测试加气员', role: state.role, myShift: state.workMode === 'mine', activeShift: state.workMode === 'idle' ? null : { shiftId: 7, staffName: '测试加气员', startedAt: '2026-11-20 08:00:00', activeFlag: 1 } })
    if (path === '/api/h5/s/redeem/inspect') return ok(order)
    if (path === '/api/h5/s/proxy/inspect') return ok({ stationName: order.stationName, memberPhone: '13800000001', shiftId: 7, guns })
    if (path === '/api/h5/m/overview') return ok({ stationName: order.stationName, buyerName: batch.buyerName, stationId: 7, inventory: state.empty ? [] : inventory })
    if (path === '/api/h5/m/batches') return ok({ rows: state.empty ? [] : [batch], total: state.empty ? 0 : 1 })
    if (path === '/api/h5/m/batches/1') return ok({ batch, inventory: inventory[0] })
    if (path === '/api/h5/m/batches/1/receipts') return ok({ rows: [{ receiptId: 1, qty: '5800.00', remain: '4200.00', receivedAt: '2026-11-20 10:00:00', createBy: '测试站管', remark: '实际到货' }], total: 1 })
    state.unexpected.push(`${req.method()} ${path}`)
    return route.fulfill({ status: 404, json: { code: 500, msg: 'UI测试未配置该API' } })
  })
  return { context, page, state }
}

async function checkLayout(page, width) {
  // Vite开发态样式通过模块注入；等待样式与字体就绪，不把瞬时FOUC当作布局结果。
  await page.waitForFunction(() => getComputedStyle(document.documentElement).getPropertyValue('--app-width').trim() === '390px')
  await page.evaluate(() => document.fonts.ready)
  const size = await page.evaluate(() => {
    const shell = document.querySelector('.app-shell').getBoundingClientRect()
    return { width: shell.width, left: shell.left, scrollWidth: document.documentElement.scrollWidth, viewport: innerWidth }
  })
  assert.ok(size.scrollWidth <= size.viewport + 1, `横向溢出: ${JSON.stringify(size)}`)
  assert.ok(Math.abs(size.width - Math.min(width, 390)) <= 2, `页面宽度: ${JSON.stringify(size)}`)
  assert.ok(Math.abs(size.left - (width - size.width) / 2) <= 2, '桌面页面须居中')
  for (const selector of ['.app-tabs', '.van-popup--bottom:visible']) {
    const locator = page.locator(selector)
    if (await locator.isVisible()) {
      const box = await locator.boundingBox()
      assert.ok(Math.abs(box.x - size.left) <= 2 && box.width <= size.width + 2, `${selector}须与页面同宽`)
    }
  }
}

async function checkServiceIcons(page) {
  const icons = page.locator('.service-tile .tile-icon > svg')
  assert.equal(await icons.count(), 4, '四个服务入口必须有真实SVG，不能只有空底色')
  const drawings = await icons.evaluateAll(nodes => nodes.map(svg => ({
    width: svg.getBoundingClientRect().width,
    height: svg.getBoundingClientRect().height,
    paths: [...svg.querySelectorAll('path')].map(path => ({
      length: path.getTotalLength(), stroke: getComputedStyle(path).stroke,
      strokeWidth: parseFloat(getComputedStyle(path).strokeWidth)
    }))
  })))
  for (const drawing of drawings) {
    assert.ok(drawing.width >= 20 && drawing.height >= 20, '图标须有可见尺寸')
    assert.ok(drawing.paths.length > 0, '图标须有绘制路径')
    assert.ok(drawing.paths.every(path => path.length > 0 && path.strokeWidth > 0 && !['none', 'transparent', 'rgba(0, 0, 0, 0)'].includes(path.stroke)), '图标须有非透明描边')
  }
}

const pages = [
  ['home', '/index', 'MEMBER', '为每一程，补足能量'],
  ['mine', '/mine', 'MEMBER', '账号与服务'],
  ['bind', '/bind', 'MEMBER', '绑定手机，便捷加气'],
  ['staff-bind', '/staff/bind', 'MEMBER', '连接你的工作账号'],
  ['member-code', '/member-token', 'MEMBER', 'a1b2c3d4'.repeat(4)],
  ['coupons', '/coupons', 'MEMBER', coupon.templateName],
  ['orders', '/orders', 'MEMBER', '查看详情'],
  ['order-detail', `/order/${order.orderNo}`, 'MEMBER', 'a1b2c3d4'.repeat(4)],
  ['station', '/station/test-station', 'MEMBER', 'CNG 01 号枪'],
  ['workbench', '/staff', 'STAFF', '你正在当班'],
  ['proxy', '/staff/proxy', 'STAFF', '识别会员，协助下单'],
  ['redeem', '/staff/redeem', 'STAFF', '先核对，再确认实际量'],
  ['manager', '/manager', 'STATION_ADMIN', batch.batchNo],
  ['batch-create', '/manager/batches/new', 'STATION_ADMIN', '采购信息'],
  ['batch', '/manager/batches/1', 'STATION_ADMIN', '实际到货']
]
for (const width of [320, 390, 768, 1440]) {
  test(`H5 UI: 15 routes, form details and coupon sheet at ${width}px`, { skip: !enabled, timeout: 120000 }, async () => {
    const { context, page, state } = await fixture(width)
    try {
      for (const [name, path, role, ready] of pages) {
        state.role = role
        await page.goto(`${origin}${path}`)
        await page.getByText(ready, { exact: false }).first().waitFor()
        if (name === 'home') {
          await page.getByRole('button', { name: '绑定手机号（下单前必做）', exact: true }).waitFor()
          assert.equal(await page.getByRole('button', { name: '本站采购 / 补货 / 库存', exact: true }).count(), 0)
        }
        if (name === 'station') {
          await page.getByPlaceholder('请输入加气金额（元）').fill('300')
          await page.getByText('预计气量', { exact: true }).waitFor()
          await page.getByText('优惠券', { exact: true }).click()
          await page.locator('.van-popup--bottom').waitFor()
          await checkLayout(page, width)
          await page.screenshot({ path: join(shots, `${width}-coupon-sheet.png`), fullPage: false })
          await page.getByText('不使用优惠券', { exact: true }).click()
          await page.locator('.van-popup--bottom').waitFor({ state: 'hidden' })
        }
        if (name === 'proxy' || name === 'redeem') {
          await page.getByPlaceholder('微信扫码或手动输入32位码').fill('b'.repeat(32))
          await page.getByRole('button', { name: name === 'proxy' ? '识别会员并选枪' : '核对订单', exact: true }).click()
          await page.getByText('13800000001', { exact: true }).waitFor()
        }
        if (name === 'batch-create') {
          await page.getByPlaceholder('数量（m³），最多两位小数').fill('100')
          await page.getByPlaceholder('最多四位小数').fill('3.58')
        }
        await checkLayout(page, width)
        if (name === 'home') await checkServiceIcons(page)
        await page.screenshot({ path: join(shots, `${width}-${name}.png`), fullPage: true, animations: 'disabled' })
      }
      assert.deepEqual(state.errors, [])
      assert.deepEqual(state.unexpected, [])
    } finally { await context.close() }
  })
}

test('H5 UI: page icons do not depend on global registration; service links still work', { skip: !enabled, timeout: 90000 }, async () => {
  const { context, page, state } = await fixture(390)
  try {
    // 模拟旧开发页面未取得新增全局组件注册；只改测试响应，不改入口文件或真实服务。
    await page.route('**/src/main.js', async route => {
      const response = await route.fetch()
      const source = await response.text()
      const registration = /app\.component\((['"])AppIcon\1,\s*AppIcon\);?/
      assert.match(source, registration)
      await route.fulfill({ response, body: source.replace(registration, '') })
    })
    const directIconPages = new Set(['home', 'mine', 'staff-bind', 'member-code', 'orders', 'order-detail', 'manager'])
    for (const [name, path, role, ready] of pages.filter(([name]) => directIconPages.has(name))) {
      state.role = role
      await page.goto(`${origin}${path}`)
      await page.getByText(ready, { exact: false }).first().waitFor()
      await checkLayout(page, 390)
      assert.equal(await page.locator('appicon').count(), 0, `${name}: 不得留下未解析的图标组件`)
      if (name === 'home') {
        await checkServiceIcons(page)
        await page.locator('.service-grid').screenshot({ path: join(shots, 'service-icons.png') })
      }
    }
    state.role = 'MEMBER'
    for (const [label, path] of [['会员码', '/member-token'], ['优惠券', '/coupons'], ['员工入口', '/staff/bind'], ['我的', '/mine']]) {
      await page.goto(`${origin}/index`)
      await page.getByRole('button', { name: label, exact: true }).click()
      await page.waitForURL(`${origin}${path}`)
    }
    assert.deepEqual(state.errors, [])
    assert.deepEqual(state.unexpected, [])
  } finally { await context.close() }
})

test('H5 UI: role entries, empty lists, idle/other shift and order actions stay visible', { skip: !enabled, timeout: 90000 }, async () => {
  const { context, page, state } = await fixture(390)
  try {
    for (const [role, label] of [['STATION_ADMIN', '本站采购 / 补货 / 库存'], ['STAFF', '员工工作台']]) {
      state.role = role
      await page.goto(`${origin}/index`)
      await page.getByRole('button', { name: label, exact: true }).waitFor()
      await page.screenshot({ path: join(shots, `390-home-${role}.png`), fullPage: true })
    }
    state.empty = true
    for (const [path, text] of [['/orders', '暂无订单'], ['/coupons', '暂无此类优惠券'], ['/manager', '尚无入库记录']]) {
      await page.goto(`${origin}${path}`)
      await page.getByText(text, { exact: false }).waitFor()
      await checkLayout(page, 390)
    }
    for (const [mode, text] of [['idle', '开始当班'], ['other', '他人当班中']]) {
      state.workMode = mode
      await page.goto(`${origin}/staff`)
      await page.getByText(text, { exact: false }).waitFor()
      assert.equal(await page.getByRole('button', { name: '扫码核销', exact: true }).count(), 0)
    }
    state.empty = false; state.role = 'MEMBER'
    for (const [status, text] of [['WAIT_CONFIRM', '确认订单并去支付'], ['WAIT_PAY', '继续支付'], ['COMPLETED', '已完成']]) {
      state.orderStatus = status
      await page.goto(`${origin}/order/${order.orderNo}`)
      await page.getByText(text, { exact: true }).first().waitFor()
      await checkLayout(page, 390)
    }
    assert.deepEqual(state.errors, [])
    assert.deepEqual(state.unexpected, [])
  } finally { await context.close() }
})
