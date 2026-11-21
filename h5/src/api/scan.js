import { showToast } from 'vant'
import { jsSdkConfig } from './h5'

let sdkPromise

function loadSdk() {
  if (window.wx) return Promise.resolve(window.wx)
  if (sdkPromise) return sdkPromise
  sdkPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = 'https://res.wx.qq.com/open/js/jweixin-1.3.2.js'
    script.onload = () => window.wx ? resolve(window.wx) : reject(new Error('微信扫一扫组件不可用'))
    script.onerror = () => reject(new Error('微信扫一扫组件加载失败'))
    document.head.appendChild(script)
  }).catch(error => {
    sdkPromise = undefined
    document.querySelector('script[src="https://res.wx.qq.com/open/js/jweixin-1.3.2.js"]')?.remove()
    throw error
  })
  return sdkPromise
}

/** 微信内调用原生扫一扫；开发/mock 或非微信环境返回 null，页面保留手动输码 */
export async function scanQrCode() {
  try {
    const wx = await loadSdk()
    if (!wx) return null
    const isIos = /iP(?:hone|ad|od)/.test(navigator.userAgent)
    const url = isIos
      ? (window.__GAS_WX_ENTRY_URL || window.location.href.split('#')[0])
      : window.location.href.split('#')[0]
    const res = await jsSdkConfig(url)
    if (res.data.signature === 'MOCK-MODE') {
      showToast('Mock 环境请手动输入动态码')
      return null
    }
    await new Promise((resolve, reject) => {
      wx.config({ debug: false, ...res.data, jsApiList: ['scanQRCode'] })
      wx.ready(resolve)
      wx.error(reject)
    })
    return await new Promise((resolve, reject) => {
      wx.scanQRCode({
        needResult: 1,
        scanType: ['qrCode'],
        success: result => resolve(result.resultStr),
        cancel: () => resolve(null),
        fail: reject
      })
    })
  } catch (e) {
    showToast('无法调用扫一扫，请手动输入动态码')
    return null
  }
}
