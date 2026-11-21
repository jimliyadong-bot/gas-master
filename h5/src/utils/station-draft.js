// 提交前持久化请求号与完整内容：网络超时、页面刷新后只能重试同一笔，不能换号重复入库。
function newRequestId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  const bytes = globalThis.crypto.getRandomValues(new Uint8Array(16))
  return Array.from(bytes, n => n.toString(16).padStart(2, '0')).join('')
}

export function createStationDraft(key, defaults, storage, makeId = newRequestId) {
  const validFields = fields => fields && typeof fields === 'object'
    && Object.keys(fields).length === Object.keys(defaults).length
    && Object.keys(defaults).every(name => typeof fields[name] === 'string')
  let current = null, activeAttempt = null
  const raw = storage.getItem(key)
  if (raw !== null) {
    try { current = JSON.parse(raw) } catch { throw new Error('提交记录损坏，请先核查；未覆盖原记录') }
    if (!current || ![1, 2].includes(current.version) || !validFields(current.fields)
      || typeof current.pending !== 'boolean' || typeof current.requestId !== 'string'
      || !/^[A-Za-z0-9_-]{8,64}$/.test(current.requestId)) {
      throw new Error('提交记录格式异常，请先核查；未覆盖原记录')
    }
    // v1曾把通用错误视作明确失败；恢复旧记录时不能相信其pending=false。
    // 只改变内存中的保护状态，不覆盖原保存内容。
    if (current.version === 1) current = { ...current, pending: true }
  }
  const snapshot = () => current ? { ...current, fields: { ...current.fields } } : null
  const save = value => {
    storage.setItem(key, JSON.stringify(value)) // 保存失败不能继续向服务器发起新提交
    current = value
    return snapshot()
  }
  return {
    read: snapshot,
    begin(fields) {
      const retry = !!current?.pending
      if (!retry) {
        if (!validFields(fields)) throw new Error('提交字段格式错误')
        const same = current && Object.keys(defaults).every(name => current.fields[name] === fields[name])
        save({ version: 2, requestId: same ? current.requestId : makeId(), fields: { ...fields }, pending: true })
      }
      const value = snapshot()
      activeAttempt = { value, mayReject: !retry }
      return value
    },
    failed(attempt, error) {
      if (activeAttempt?.value !== attempt) return snapshot()
      const mayReject = activeAttempt.mayReject
      activeAttempt = null
      // 标记仅证明“本次尝试”被业务事务拒绝，不证明更早的未知尝试未提交。
      // 通用code、刷新恢复、未知结果后的任何失败都不能解锁或换请求号。
      if (mayReject && error?.stationWriteAttemptRejected === true
        && error.requestId === current?.requestId) return save({ ...current, pending: false })
      return snapshot()
    },
    complete() {
      storage.removeItem(key)
      current = null; activeAttempt = null
    }
  }
}

export function positiveDecimal(value, integers, fractions) {
  const pattern = new RegExp(`^(?:0|[1-9]\\d{0,${integers - 1}})(?:\\.\\d{1,${fractions}})?$`)
  return pattern.test(value) && /[1-9]/.test(value)
}

// 十进制整数计算采购金额，避免浮点舍入把本人确认金额显示成另一数值。
export function purchaseAmount(qty, price) {
  if (!positiveDecimal(qty, 12, 2) || !positiveDecimal(price, 6, 4)) return null
  const scaled = (value, digits) => {
    const [whole, fraction = ''] = value.split('.')
    return BigInt(whole + fraction.padEnd(digits, '0'))
  }
  const cents = (scaled(qty, 2) * scaled(price, 4) + 5000n) / 10000n
  if (cents < 1n || cents > 999999999999n) return null
  return `${cents / 100n}.${String(cents % 100n).padStart(2, '0')}`
}

// 数量统一转换成百分之一单位；不使用Number相减或浮点容差判断额度。
export function quantityUnits(value) {
  const text = String(value)
  if (text.length > 64 || !/^(?:0|[1-9]\d*)(?:\.\d{1,2})?$/.test(text)) throw new Error('库存数量格式异常，请刷新核查')
  const [whole, fraction = ''] = text.split('.')
  return BigInt(whole + fraction.padEnd(2, '0'))
}

export function remainingQuantity(qty, received) {
  const remaining = quantityUnits(qty) - quantityUnits(received)
  return remaining > 0n ? remaining : 0n
}

export function formatQuantity(units) {
  return `${units / 100n}.${String(units % 100n).padStart(2, '0')}`
}

export function localDateTime() {
  const now = new Date()
  const pad = value => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}
