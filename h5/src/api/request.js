import axios from 'axios'
import { showDialog, showToast } from 'vant'
import router from '../router'

const request = axios.create({
  baseURL: '/',
  timeout: 15000
})

request.interceptors.request.use(config => {
  // M1表单必须使用初始化时绑定的身份；绝不静默换用另一标签页的新账号。
  const token = config.stationIdentity ? config.stationIdentity.authorize() : localStorage.getItem('h5_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // RuoYi GlobalExceptionHandler 依据 XHR/Accept 头决定 JSON 响应
  config.headers['X-Requested-With'] = 'XMLHttpRequest'
  config.headers.Accept = 'application/json'
  return config
})

request.interceptors.response.use(
  response => {
    response.config.stationIdentity?.assertCurrent()
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body
      }
      showToast(body.msg || '操作失败')
      const error = new Error(body.msg || '操作失败')
      error.businessCode = body.code
      // 普通code=500不是“原提交未生效”的证据。此标记仅说明本次业务尝试被拒绝。
      error.stationWriteAttemptRejected = body.stationWriteAttemptRejected === true
      error.requestId = body.requestId
      return Promise.reject(error)
    }
    return body
  },
  error => {
    try { error.config?.stationIdentity?.assertCurrent() }
    catch (identityError) { return Promise.reject(identityError) }
    if (error.code === 'STATION_IDENTITY_CHANGED') return Promise.reject(error)
    if (error.response && error.response.status === 401) {
      // 登录失效：清凭证后重新授权，回来时还能到当前页
      localStorage.removeItem('h5_token')
      goAuth(router.currentRoute.value.fullPath)
      return Promise.reject(error)
    }
    const msg = (error.response && error.response.data && error.response.data.msg) || '网络异常，请稍后重试'
    showToast(msg)
    return Promise.reject(error)
  }
)

/** 跳微信授权（后端 mock 模式下直接回跳，无需真实公众号） */
export function goAuth(redirect = '/index') {
  const url = `/api/h5/wx/authorize?redirect=${encodeURIComponent(redirect)}`
  window.location.href = url
}

/** 一次性 OAuth 登录码换 JWT（令牌不经过 URL） */
export function exchangeWxCode(code) {
  return request.post('/api/h5/wx/exchange', null, { params: { code } })
}

/** 确认对话框 */
export function confirmDialog(message) {
  return new Promise(resolve => {
    showDialog({
      title: '提示',
      message,
      showCancelButton: true
    }).then(() => resolve(true)).catch(() => resolve(false))
  })
}

export default request
