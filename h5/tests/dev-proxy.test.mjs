import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { createServer as createHttpServer } from 'node:http'
import { fileURLToPath } from 'node:url'
import { createServer as createViteServer } from 'vite'
import config from '../vite.config.js'

// 使用真实Vite代理和随机端口HTTP桩；不访问9090、不连接业务后端或数据库。
let backend, vite, backendOrigin, frontendOrigin
const backendPaths = []
async function freePort() {
  const socket = createHttpServer()
  await new Promise((resolve, reject) => { socket.once('error', reject); socket.listen(0, '127.0.0.1', resolve) })
  const port = socket.address().port
  await new Promise(resolve => socket.close(resolve))
  return port
}
const external = 'https://open.weixin.qq.com/connect/oauth2/authorize?appid=test-only&state=test-state'
before(async () => {
  backend = createHttpServer((req, res) => {
    const url = new URL(req.url, backendOrigin)
    backendPaths.push(url.pathname)
    const redirect = location => { res.writeHead(302, { Location: location }); res.end() }
    if (url.pathname === '/api/h5/wx/authorize') return redirect(`${backendOrigin}/api/h5/wx/callback?code=MOCK&state=test-state`)
    if (url.pathname === '/api/h5/wx/callback') return redirect(`${backendOrigin}/index?h5_code=test-login-code`)
    if (url.pathname === '/api/test/relative') return redirect('/index?oauth_error=invalid_state')
    if (url.pathname === '/api/test/external') return redirect(external)
    if (url.pathname === '/api/test/echo') {
      res.writeHead(200, { 'Content-Type': 'application/json' })
      return res.end(JSON.stringify({ code: 0, host: req.headers.host, query: url.searchParams.get('value') }))
    }
    res.writeHead(404); res.end('Unexpected backend route')
  })
  await new Promise((resolve, reject) => { backend.once('error', reject); backend.listen(0, '127.0.0.1', resolve) })
  backendOrigin = `http://127.0.0.1:${backend.address().port}`
  vite = await createViteServer({
    ...config,
    configFile: false,
    envFile: false,
    root: fileURLToPath(new URL('../', import.meta.url)),
    logLevel: 'error',
    server: {
      ...config.server, host: '127.0.0.1', port: await freePort(), strictPort: true, hmr: false, watch: null,
      proxy: { '/api': { ...config.server.proxy['/api'], target: backendOrigin } }
    }
  })
  await vite.listen()
  frontendOrigin = `http://127.0.0.1:${vite.httpServer.address().port}`
})
after(async () => {
  try { await vite?.close() }
  finally {
    backend?.closeAllConnections()
    if (backend?.listening) await new Promise(resolve => backend.close(resolve))
  }
})

const get = path => fetch(new URL(path, frontendOrigin), { redirect: 'manual', signal: AbortSignal.timeout(10000) })

test('absolute mock authorization redirects stay on frontend through callback and return', async () => {
  const authorize = await get('/api/h5/wx/authorize?redirect=%2Findex')
  assert.equal(authorize.status, 302)
  const callback = authorize.headers.get('location')
  assert.equal(callback, `${frontendOrigin}/api/h5/wx/callback?code=MOCK&state=test-state`)
  const result = await get(callback)
  assert.equal(result.status, 302)
  const landing = result.headers.get('location')
  assert.equal(landing, `${frontendOrigin}/index?h5_code=test-login-code`)
  const page = await get(landing)
  assert.equal(page.status, 200)
  assert.match(await page.text(), /<div id="app"><\/div>/)
  assert.equal(backendPaths.includes('/index'), false, 'H5首页不能发到后台触发Shiro登录')
  assert.equal(backendPaths.includes('/login'), false)
})

test('relative redirect remains relative and resolves on frontend', async () => {
  const response = await get('/api/test/relative')
  assert.equal(response.status, 302)
  assert.equal(response.headers.get('location'), '/index?oauth_error=invalid_state')
  assert.equal(new URL(response.headers.get('location'), frontendOrigin).origin, frontendOrigin)
})

test('external authorization Location is not rewritten or followed', async () => {
  const response = await get('/api/test/external')
  assert.equal(response.status, 302)
  assert.equal(response.headers.get('location'), external)
})

test('ordinary API requests retain backend Host and query parameters', async () => {
  const response = await get('/api/test/echo?value=proxy-test')
  assert.equal(response.status, 200)
  assert.deepEqual(await response.json(), { code: 0, host: new URL(backendOrigin).host, query: 'proxy-test' })
})
