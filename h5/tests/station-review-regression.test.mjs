import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { pathToFileURL } from 'node:url'
import { createServer as netServer } from 'node:net'
import { createServer } from 'vite'
import { quantityUnits, formatQuantity } from '../src/utils/station-draft.js'

// 固定R1—R3回归；所有后端API拦截，不连接业务后端或数据库。
const enabled = !!process.env.PLAYWRIGHT_MODULE
const options = { skip: !enabled, timeout: 30000 }
let server, browser, origin
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
})
after(async () => { await browser?.close(); await server?.close() })
function deferred() {
  let resolve
  const promise = new Promise(done => { resolve = done })
  return { promise, resolve }
}

async function fixture(kind, large = false) {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
  await context.addInitScript(() => {
    if (!localStorage.getItem('h5_token')) localStorage.setItem('h5_token', 'm1-test-a')
  })
  const page = await context.newPage()
  page.setDefaultTimeout(7000)
  const state = { mode: 'success', calls: [], results: new Map(), stock: 0n, responseGate: null, started: deferred(), errors: [] }
  page.on('pageerror', e => state.errors.push(e.message))
  const batch = { batchId: 1, batchNo: 'M1-FIX', stationId: 1, stationName: '测试A站', buyerName: '测试采购商', gasType: 'CNG', purchaseDate: '2026-11-20', qty: large ? '10000000000.05' : '100', receivedQty: large ? '10000000000.04' : '0', unitPrice: '0.0001', amount: '1000000.00' }
  const answer = (route, data) => route.fulfill({ json: { code: 0, data } })
  await context.route('**/api/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname
    if (!path.startsWith('/api/')) return route.continue()
    const stationId = request.headers().authorization === 'Bearer m1-test-b' ? 2 : 1
    if (path === '/api/h5/c/me') return answer(route, { role: 'STATION_ADMIN', userId: stationId === 1 ? 7 : 8, stationId })
    if (path === '/api/h5/m/overview') return answer(route, { stationId, stationName: stationId === 1 ? '测试A站' : '测试B站', inventory: [] })
    if (path === '/api/h5/m/batches/1') return answer(route, { batch: { ...batch, receivedQty: formatQuantity(quantityUnits(batch.receivedQty) + state.stock) }, inventory: null })
    if (path.endsWith('/receipts') && request.method() === 'GET') return answer(route, { rows: [...state.results.values()], total: state.results.size })
    if (request.method() === 'POST' && (path === '/api/h5/m/batches' || path === '/api/h5/m/batches/1/receipts')) {
      const input = request.postDataJSON()
      state.calls.push({ input, stationId })
      state.started.resolve()
      if (state.mode === 'generic') return route.fulfill({ json: { code: 500, msg: '通用故障，原结果未查明' } })
      if (state.mode === 'rejected') return route.fulfill({ json: { code: 500, msg: '本次尝试被业务拒绝', stationWriteAttemptRejected: true, requestId: input.requestId } })
      const key = `${stationId}:${input.requestId}`
      if (!state.results.has(key)) {
        state.results.set(key, kind === 'batch' ? { ...batch, ...input, batchId: 81, stationId } : { ...input, receiptId: state.results.size + 1, remain: input.qty, createBy: '7:test' })
        if (kind === 'receipt') state.stock += quantityUnits(input.qty)
      }
      if (state.mode === 'drop') return route.abort('failed')
      if (state.mode === 'missing-code') return route.fulfill({ json: { data: state.results.get(key) } })
      if (state.mode === 'missing-id') return answer(route, {})
      if (state.responseGate) await state.responseGate.promise
      return answer(route, state.results.get(key))
    }
    return route.fulfill({ status: 404, json: { code: 500, msg: `未配置mock: ${path}` } })
  })
  const click = text => page.getByRole('button', { name: text, exact: true }).click()
  const wait = text => page.getByText(text, { exact: false }).first().waitFor()
  const submitLabel = kind === 'batch' ? '核对并建立批次' : '核对并提交入库'
  const retryLabel = kind === 'batch' ? '重试原提交' : '重试原补货提交'
  const successText = kind === 'batch' ? '采购批次已建立，尚未入库' : '补货已成功，入库记录 #1'
  const pendingText = kind === 'batch' ? '上一笔提交结果尚未确认' : '存在结果未确认的补货'
  const quantity = page.getByPlaceholder(kind === 'batch' ? '数量（m³），最多两位小数' : '请输入本次实际到货（m³）')
  await page.goto(`${origin}${kind === 'batch' ? '/manager/batches/new' : '/manager/batches/1'}`)
  await quantity.fill(large ? '0.01' : '20')
  if (kind === 'batch') await page.getByPlaceholder('最多四位小数').fill('3')
  const draft = () => page.evaluate(() => {
    const key = Object.keys(sessionStorage).find(k => k.startsWith('gas:station-'))
    return key ? { key, value: JSON.parse(sessionStorage.getItem(key)) } : null
  })
  return {
    page, context, state, quantity, click, wait, submitLabel, retryLabel, successText, pendingText, draft,
    async changeOtherTab() {
      const tab = await context.newPage()
      await tab.goto(`${origin}/index`)
      await tab.evaluate(() => localStorage.setItem('h5_token', 'm1-test-b'))
    },
    async close() { state.responseGate?.resolve(); await context.close() }
  }
}

for (const kind of ['batch', 'receipt']) {
  test(`R1 ${kind}: lost success, generic error, explicit retry rejection and reload keep original key`, options, async () => {
    const f = await fixture(kind)
    try {
      f.state.mode = 'drop'
      const failed = f.page.waitForEvent('requestfailed', r => r.method() === 'POST')
      await f.click(f.submitLabel); await f.click('确认'); await failed
      const original = f.state.calls[0].input
      assert.equal(f.state.results.size, 1)
      await f.page.reload(); await f.wait(f.pendingText)
      for (const mode of ['generic', 'rejected']) {
        f.state.mode = mode
        await f.click(f.retryLabel); await f.click('确认')
        await f.wait(mode === 'generic' ? '通用故障' : '本次尝试被业务拒绝')
        assert.equal(await f.quantity.isDisabled(), true)
        assert.equal((await f.draft()).value.pending, true)
        await f.page.reload(); await f.wait(f.pendingText)
      }
      f.state.mode = 'success'
      await f.click(f.retryLabel); await f.click('确认'); await f.wait(f.successText)
      assert.equal(f.state.results.size, 1)
      assert.equal(f.state.calls.length, 4)
      for (const call of f.state.calls) assert.deepEqual(call.input, original)
      assert.equal(await f.draft(), null)
      if (kind === 'receipt') assert.equal(f.state.stock, 2000n)
      assert.deepEqual(f.state.errors, [])
    } finally { await f.close() }
  })

  test(`R1 ${kind}: incomplete success envelope preserves original draft`, options, async () => {
    for (const mode of ['missing-code', 'missing-id']) {
      const f = await fixture(kind)
      try {
        f.state.mode = mode
        await f.click(f.submitLabel); await f.click('确认'); await f.wait('未取得有效')
        assert.equal((await f.draft()).value.pending, true)
        assert.equal(f.state.results.size, 1)
        f.state.mode = 'success'
        await f.page.reload(); await f.wait(f.pendingText)
        await f.click(f.retryLabel); await f.click('确认'); await f.wait(f.successText)
        assert.equal(f.state.results.size, 1)
        assert.deepEqual(f.state.calls[1].input, f.state.calls[0].input)
        assert.deepEqual(f.state.errors, [])
      } finally { await f.close() }
    }
  })

  test(`R1 ${kind}: first explicit rejection permits correction with a new key`, options, async () => {
    const f = await fixture(kind)
    try {
      f.state.mode = 'rejected'
      await f.click(f.submitLabel); await f.click('确认'); await f.wait('本次尝试被业务拒绝')
      await f.quantity.fill('21') // Playwright同时等候字段重新可编辑
      assert.equal(f.state.results.size, 0)
      f.state.mode = 'success'
      await f.click(f.submitLabel); await f.click('确认'); await f.wait(f.successText)
      assert.notEqual(f.state.calls[0].input.requestId, f.state.calls[1].input.requestId)
      assert.equal(f.state.results.size, 1)
    } finally { await f.close() }
  })

  for (const phase of ['before-dialog', 'during-dialog', 'same-tab', 'dispatch']) {
    test(`R2 ${kind}: identity change ${phase} prevents POST`, options, async () => {
      const f = await fixture(kind)
      try {
        if (phase === 'before-dialog') {
          await f.changeOtherTab()
        } else if (phase === 'dispatch') {
          // Axios按逆注册顺序执行请求拦截器；模拟最后一刻身份变化，必须由请求层兜底。
          await f.page.evaluate(async () => {
            const { default: request } = await import('/src/api/request.js')
            request.interceptors.request.use(config => {
              if (config.method === 'post' && config.url.startsWith('/api/h5/m/')) localStorage.setItem('h5_token', 'm1-test-b')
              return config
            })
          })
          await f.click(f.submitLabel); await f.click('确认')
        } else {
          await f.click(f.submitLabel)
          await f.page.locator('.van-dialog').getByText(/站点：测试A站/).waitFor()
          if (phase === 'same-tab') await f.page.evaluate(() => localStorage.setItem('h5_token', 'm1-test-b'))
          else await f.changeOtherTab()
          await f.click('确认')
        }
        await f.wait('登录身份已变化')
        assert.equal(f.state.calls.length, 0)
        assert.equal(f.state.results.size, 0)
        if (phase === 'dispatch') {
          const saved = await f.draft()
          assert.ok(saved.key.includes(':7:1'))
          assert.equal(saved.value.pending, true)
        }
        assert.deepEqual(f.state.errors, [])
      } finally { await f.close() }
    })
  }

  test(`R2 ${kind}: stale success after identity switch preserves A draft for same-key recovery`, options, async () => {
    const f = await fixture(kind)
    try {
      f.state.responseGate = deferred()
      await f.click(f.submitLabel); await f.click('确认'); await f.state.started.promise
      await f.changeOtherTab(); await f.wait('登录身份已变化')
      f.state.responseGate.resolve()
      assert.equal(f.state.calls[0].stationId, 1)
      assert.equal((await f.draft()).value.pending, true)
      await f.page.evaluate(() => localStorage.setItem('h5_token', 'm1-test-a'))
      await f.page.reload(); await f.wait(f.pendingText)
      await f.click(f.retryLabel); await f.click('确认'); await f.wait(f.successText)
      assert.equal(f.state.results.size, 1)
      assert.deepEqual(f.state.calls[1].input, f.state.calls[0].input)
      assert.deepEqual(f.state.errors, [])
    } finally { await f.close() }
  })
}

test('R3: large decimal remaining 0.01 rejects 0.02 but accepts exact 0.01', options, async () => {
  const f = await fixture('receipt', true)
  try {
    await f.quantity.fill('0.02')
    await f.click(f.submitLabel); await f.wait('累计到货不能超过采购量')
    assert.equal(f.state.calls.length, 0)
    await f.quantity.fill('0.01')
    await f.click(f.submitLabel); await f.click('确认'); await f.wait('本批次已无可到货额度')
    assert.equal(f.state.stock, 1n)
    assert.equal(f.state.results.size, 1)
    assert.deepEqual(f.state.errors, [])
  } finally { await f.close() }
})
