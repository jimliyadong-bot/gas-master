import test from 'node:test'
import assert from 'node:assert/strict'
import { pathToFileURL } from 'node:url'
import { mkdtemp } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { createServer } from 'vite'
import { createServer as createNetServer } from 'node:net'

async function freePort() {
  const socket = createNetServer()
  await new Promise((resolve, reject) => { socket.once('error', reject); socket.listen(0, '127.0.0.1', resolve) })
  const port = socket.address().port
  await new Promise(resolve => socket.close(resolve))
  return port
}

// 使用已安装的 playwright-core，不自动下载依赖或浏览器。所有 /api 请求均拦截，不连接后端／数据库。
test('station H5: entry, cancel, create, uncertain receipt/reload/retry, rejection, exact quota', {
  skip: !process.env.PLAYWRIGHT_MODULE,
  timeout: 90000
}, async () => {
  const { chromium } = await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE).href)
  const server = await createServer({ server: { host: '127.0.0.1', port: await freePort(), strictPort: true }, logLevel: 'error' })
  let browser, page
  const errors = []
  try {
    await server.listen()
    const origin = `http://127.0.0.1:${server.httpServer.address().port}`
    browser = await chromium.launch({ headless: true })
    const context = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1 })
    await context.addInitScript(() => localStorage.setItem('h5_token', 'station-ui-mock'))
    page = await context.newPage()
    page.setDefaultTimeout(10000)
    page.on('pageerror', error => errors.push(error.message))
    let role = 'STATION_ADMIN', batch = null, received = 0, rejectNext = false, abortNext = true
    const receipts = [], posts = [], results = new Map()
    const inventory = () => received ? [{ poolId: 1, gasType: 'CNG', available: received, totalIn: received, reserved: 0, consumed: 0, adjusted: 0 }] : []
    const answer = (route, data) => route.fulfill({ json: { code: 0, data } })
    await page.route('**/api/**', async route => {
      const request = route.request(), url = new URL(request.url()), path = url.pathname
      if (!path.startsWith('/api/')) return route.continue() // Vite 的 /src/api/*.js 不是后端请求
      if (path === '/api/h5/c/me') return answer(route, { role, userId: 7, stationId: 1 })
      if (role !== 'STATION_ADMIN') return route.fulfill({ json: { code: 500, msg: '仅站点管理员' } })
      if (path === '/api/h5/m/overview') return answer(route, { stationId: 1, stationName: '测试一站', buyerName: '测试采购商', inventory: inventory() })
      if (path === '/api/h5/m/batches' && request.method() === 'GET') return answer(route, { rows: batch ? [batch] : [], total: batch ? 1 : 0 })
      if (path === '/api/h5/m/batches' && request.method() === 'POST') {
        const input = request.postDataJSON(); posts.push({ kind: 'batch', input })
        batch = { ...input, batchId: 1, batchNo: 'PB-M1-UI', stationName: '测试一站', buyerName: '测试采购商', amount: '300.00', receivedQty: 0 }
        return answer(route, batch)
      }
      if (path === '/api/h5/m/batches/1') return answer(route, { batch, inventory: inventory()[0] || null })
      if (path === '/api/h5/m/batches/1/receipts' && request.method() === 'GET') return answer(route, { rows: [...receipts].reverse(), total: receipts.length })
      if (path === '/api/h5/m/batches/1/receipts' && request.method() === 'POST') {
        const input = request.postDataJSON(); posts.push({ kind: 'receipt', input })
        if (rejectNext) { rejectNext = false; return route.fulfill({ json: { code: 500, msg: '模拟明确拒绝：请核对数量', stationWriteAttemptRejected: true, requestId: input.requestId } }) }
        if (!results.has(input.requestId)) {
          const receipt = { ...input, receiptId: receipts.length + 1, createBy: '7:test-admin', remain: input.qty }
          receipts.push(receipt); received += Number(input.qty); batch.receivedQty = received
          results.set(input.requestId, receipt)
        }
        if (abortNext) { abortNext = false; return route.abort('failed') }
        return answer(route, results.get(input.requestId))
      }
      return route.fulfill({ status: 404, json: { code: 500, msg: `Unexpected mock route: ${path}` } })
    })
    const click = text => page.getByRole('button', { name: text, exact: true }).click()
    const waitText = text => page.getByText(text, { exact: false }).first().waitFor()
    const shots = await mkdtemp(join(tmpdir(), 'gas-m1-ui-'))
    await page.goto(`${origin}/index`)
    await click('本站采购 / 补货 / 库存')
    await waitText('测试一站')
    await page.screenshot({ path: join(shots, 'manager.png'), fullPage: true })
    await click('新建采购批次')
    await page.getByPlaceholder('数量（m³），最多两位小数').fill('100')
    await page.getByPlaceholder('最多四位小数').fill('3')
    await click('核对并建立批次')
    await waitText('确认建立批次？')
    await page.waitForTimeout(400) // 仅截图等候Vant入场动画，不作为业务断言条件
    await page.screenshot({ path: join(shots, 'batch-confirm.png'), fullPage: true, animations: 'disabled' })
    await click('取消')
    assert.equal(posts.length, 0, '取消确认不发请求')
    await click('核对并建立批次'); await click('确认')
    await waitText('采购批次已建立，尚未入库')
    assert.equal(posts.filter(p => p.kind === 'batch').length, 1)
    await click('查看批次并补货')
    const quantity = page.getByPlaceholder('请输入本次实际到货（m³）')
    await quantity.fill('20')
    await click('核对并提交入库'); await click('取消')
    assert.equal(receipts.length, 0)
    const lostResponse = page.waitForEvent('requestfailed', request => request.method() === 'POST' && request.url().includes('/receipts'))
    await click('核对并提交入库'); await click('确认')
    await lostResponse
    await waitText('存在结果未确认的补货')
    assert.equal(received, 20, 'mock模拟入库成功但响应丢失')
    assert.equal(await quantity.isDisabled(), true)
    const first = posts.at(-1).input
    await page.reload()
    await waitText('存在结果未确认的补货')
    assert.equal(await quantity.inputValue(), '20')
    await click('重试原补货提交'); await click('确认')
    await waitText('补货已成功，入库记录 #1')
    assert.deepEqual(posts.at(-1).input, first, '刷新重试保持原请求号及原内容')
    assert.equal(received, 20, '重试未重复增加库存')
    await page.locator('.van-dialog').waitFor({ state: 'hidden' })
    await page.waitForTimeout(400)
    await page.screenshot({ path: join(shots, 'receipt-success.png'), fullPage: true, animations: 'disabled' })
    await quantity.fill('81')
    const previousPosts = posts.length
    await click('核对并提交入库')
    await waitText('累计到货不能超过采购量')
    assert.equal(posts.length, previousPosts, '明显超量不提交')
    rejectNext = true
    await quantity.fill('10')
    await click('核对并提交入库'); await click('确认')
    await waitText('模拟明确拒绝')
    assert.equal(await quantity.isDisabled(), false, '明确失败后允许更正')
    assert.equal(received, 20)
    await quantity.fill('80')
    await click('核对并提交入库'); await click('确认')
    await waitText('本批次已无可到货额度')
    assert.equal(received, 100)
    assert.equal(receipts.length, 2)
    role = 'STAFF'
    await page.goto(`${origin}/manager/batches/new`)
    await waitText('仅站点管理员可使用本站采购功能')
    assert.deepEqual(errors, [])
    console.log(`UI screenshots: ${shots}`)
  } catch (error) {
    console.error('Mock UI diagnostics:', errors, page ? await page.locator('body').innerText().catch(() => '') : '')
    throw error
  } finally {
    await browser?.close()
    await server.close()
  }
})
