// 站管表单的身份快照只留内存，不把token写入提交凭据或错误消息。
export function createStationIdentity(storage, onInvalidated = () => {}) {
  const token = storage.getItem('h5_token')
  if (!token) throw new Error('登录身份不可用，请重新授权')
  let invalid = false
  const fail = () => {
    const error = new Error('登录身份已变化，请重新加载并核对站点；原提交凭据已保留')
    error.code = 'STATION_IDENTITY_CHANGED'
    if (!invalid) { invalid = true; onInvalidated(error) }
    return error
  }
  const assertCurrent = () => {
    if (invalid) throw fail()
    let current
    try { current = storage.getItem('h5_token') } catch { throw fail() }
    if (current !== token) throw fail()
  }
  return {
    assertCurrent,
    authorize() { assertCurrent(); return token },
    storageChanged(event) {
      // 即使A→B→A发生在异步检查之间，也不能重新放行已经失效的旧表单。
      if ((event.key === 'h5_token' && event.newValue !== token) || event.key === null) fail()
    }
  }
}
