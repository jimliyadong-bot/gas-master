import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createServer as socketServer } from 'node:net'
import { mkdtemp } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { pathToFileURL } from 'node:url'

// 由GasH5StationHttpTest启动。真实业务API/MySQL；只丢弃已提交的首次响应，不伪造响应数据。
// 一次性身份通过stdin传入，不写入命令行、环境变量、日志或截图文件名。
assert.equal(process.env.GAS_ALLOW_DB_TESTS, 'true', 'requires approved local database writes')
assert.ok(process.env.PLAYWRIGHT_MODULE, 'requires installed Playwright')
let input = ''
for await (const chunk of process.stdin) input += chunk
const settings = JSON.parse(input)
const backend = new URL(settings.backend)
assert.equal(backend.hostname, '127.0.0.1')
assert.equal(backend.protocol, 'http:')
assert.ok(backend.port && !['9090', '5300'].includes(backend.port), 'use test-owned random backend port')
const socket = socketServer()
await new Promise((ok, fail) => { socket.once('error', fail); socket.listen(0, '127.0.0.1', ok) })
const port = socket.address().port
await new Promise(ok => socket.close(ok))
const origin = `http://127.0.0.1:${port}`
const server = await createServer({ server: { host: '127.0.0.1', port, strictPort: true, hmr: false, watch: null, proxy: { '/api': { target: backend.origin, changeOrigin: true, autoRewrite: true } } }, logLevel: 'error' })
let browser
try {
  await server.listen()
  const { chromium } = await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE).href)
  browser = await chromium.launch({ headless: true })
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, reducedMotion: 'reduce' })
  await context.addInitScript(token => localStorage.setItem('h5_token', token), settings.token)
  const page = await context.newPage()
  page.setDefaultTimeout(12000)
  const errors = [], submissions = { batch: [], receipt: [] }
  const shots = await mkdtemp(join(tmpdir(), 'gas-m1-live-'))
  page.on('pageerror', () => errors.push('pageerror'))
  await page.route('**/*', async route => {
    const url = new URL(route.request().url())
    if (url.origin !== origin) { errors.push('unexpected origin'); return route.abort() }
    const kind = url.pathname === '/api/h5/m/batches' ? 'batch'
      : /^\/api\/h5\/m\/batches\/\d+\/receipts$/.test(url.pathname) ? 'receipt' : null
    if (!kind || route.request().method() !== 'POST') return route.continue()
    submissions[kind].push(route.request().postDataJSON())
    const response = await route.fetch({ maxRedirects: 0 })
    assert.equal(response.status(), 200)
    assert.equal((await response.json()).code, 0, `${kind}: real server must commit successfully before response loss`)
    if (submissions[kind].length === 1) return route.abort('failed')
    return route.fulfill({ response })
  })
  const click = name => page.getByRole('button', { name, exact: true }).click()
  const waitText = text => page.getByText(text, { exact: false }).first().waitFor()
  await page.goto(`${origin}/manager`)
  await click('新建采购批次')
  await page.getByPlaceholder('数量（m³），最多两位小数').fill('100')
  await page.getByPlaceholder('最多四位小数').fill('3')
  await click('核对并建立批次'); await click('取消')
  assert.equal(submissions.batch.length, 0)
  await click('核对并建立批次'); await click('确认')
  await waitText('上一笔提交结果尚未确认')
  await page.reload()
  await waitText('上一笔提交结果尚未确认')
  assert.equal(await page.getByPlaceholder('数量（m³），最多两位小数').isDisabled(), true)
  await click('重试原提交'); await click('确认')
  await waitText('采购批次已建立，尚未入库')
  assert.equal(submissions.batch.length, 2)
  assert.deepEqual(submissions.batch[0], submissions.batch[1])
  await click('查看批次并补货')
  await page.getByPlaceholder('请输入本次实际到货（m³）').fill('100')
  await click('核对并提交入库'); await click('取消')
  assert.equal(submissions.receipt.length, 0)
  await click('核对并提交入库')
  await page.locator('.van-dialog').screenshot({ path: join(shots, 'live-receipt-confirm.png') })
  await click('确认')
  await waitText('存在结果未确认的补货')
  await page.reload()
  await waitText('存在结果未确认的补货')
  assert.equal(await page.getByPlaceholder('请输入本次实际到货（m³）').isDisabled(), true)
  await click('重试原补货提交'); await click('确认')
  await waitText('补货已成功，入库记录')
  await waitText('本批次已无可到货额度')
  assert.equal(submissions.receipt.length, 2)
  assert.deepEqual(submissions.receipt[0], submissions.receipt[1])
  assert.deepEqual(errors, [])
  await page.locator('.van-dialog').waitFor({ state: 'hidden' })
  await page.screenshot({ path: join(shots, 'live-receipt-success.png'), fullPage: true })
  console.log(`PASS: real H5 -> HTTP -> MySQL, cancel and post-commit response-loss retries; screenshots: ${shots}`)
} catch (error) {
  // 不将HTTP上下文或凭据展开到JUnit日志。
  console.error(`Live H5 acceptance failed: ${error.name}`)
  process.exitCode = 1
} finally {
  try { await browser?.close() } finally { await server.close() }
}
