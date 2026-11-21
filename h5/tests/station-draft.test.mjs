import test from 'node:test'
import assert from 'node:assert/strict'
import { createStationDraft, positiveDecimal, purchaseAmount, quantityUnits, remainingQuantity, formatQuantity } from '../src/utils/station-draft.js'

const defaults = { qty: '10', receivedAt: '2026-01-01T10:00:00', remark: '' }
function memory() {
  const values = new Map()
  return { values, getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) }
}
const id = () => 'request-0001'
const rejection = attempt => ({ stationWriteAttemptRejected: true, requestId: attempt.requestId })

test('opening or cancelling without begin does not write a request', () => {
  const storage = memory()
  const draft = createStationDraft('one', defaults, storage, id)
  assert.equal(draft.read(), null)
  assert.equal(storage.values.size, 0)
})

test('unknown result survives reload and retries original fields and request ID', () => {
  const storage = memory()
  const original = createStationDraft('one', defaults, storage, id).begin(defaults)
  const reloaded = createStationDraft('one', defaults, storage, () => 'must-not-use')
  assert.equal(reloaded.read().pending, true)
  assert.deepEqual(reloaded.begin({ ...defaults, qty: '99' }), original)
})

test('snapshots cannot mutate stored pending payload', () => {
  const storage = memory()
  const draft = createStationDraft('one', defaults, storage, id)
  draft.begin(defaults)
  const view = draft.read()
  view.fields.qty = '900'
  assert.equal(draft.read().fields.qty, '10')
})

test('confirmed rejection unlocks editing and changed payload gets a new ID', () => {
  const storage = memory()
  let counter = 0
  const draft = createStationDraft('one', defaults, storage, () => `request-${++counter}`)
  const original = draft.begin(defaults)
  draft.failed(original, rejection(original))
  const next = draft.begin(defaults)
  assert.equal(next.requestId, original.requestId)
  draft.failed(next, rejection(next))
  assert.notEqual(draft.begin({ ...defaults, qty: '11' }).requestId, original.requestId)
})

test('generic errors never unlock even the first attempt', () => {
  const draft = createStationDraft('one', defaults, memory(), id)
  const attempt = draft.begin(defaults)
  assert.equal(draft.failed(attempt, { businessCode: 500 }).pending, true)
  assert.equal(draft.failed(attempt, rejection(attempt)).pending, true, '同一次回调不得事后改变判定')
})

test('unknown followed by explicit attempt rejection remains locked across reload', () => {
  const storage = memory()
  const draft = createStationDraft('one', defaults, storage, id)
  const first = draft.begin(defaults)
  draft.failed(first, new Error('lost response'))
  const restored = createStationDraft('one', defaults, storage, () => 'must-not-use')
  const retry = restored.begin({ ...defaults, qty: '99' })
  assert.equal(restored.failed(retry, rejection(retry)).pending, true)
  assert.deepEqual(restored.begin({ ...defaults, remark: 'retry' }).fields, first.fields)
  assert.equal(restored.read().requestId, first.requestId)
})

test('wrong request rejection and stale attempt cannot unlock', () => {
  const draft = createStationDraft('one', defaults, memory(), id)
  const first = draft.begin(defaults)
  assert.equal(draft.failed(first, { ...rejection(first), requestId: 'other-id' }).pending, true)
  const retry = draft.begin(defaults)
  assert.equal(draft.failed(first, rejection(first)).pending, true)
  assert.equal(draft.failed(retry, rejection(retry)).pending, true)
})

test('legacy unlocked v1 record is restored conservatively without overwrite', () => {
  const storage = memory()
  const raw = JSON.stringify({ version: 1, requestId: 'legacy-0001', fields: defaults, pending: false })
  storage.setItem('one', raw)
  const draft = createStationDraft('one', defaults, storage, () => 'must-not-use')
  assert.equal(draft.read().pending, true)
  const retry = draft.begin({ ...defaults, qty: '99' })
  assert.equal(draft.failed(retry, rejection(retry)).pending, true)
  assert.equal(storage.getItem('one'), raw)
})

test('rejection persistence failure keeps original locked record', () => {
  const storage = memory()
  const draft = createStationDraft('one', defaults, storage, id)
  const first = draft.begin(defaults)
  const raw = storage.getItem('one')
  storage.setItem = () => { throw new Error('write failed') }
  assert.throws(() => draft.failed(first, rejection(first)), /write failed/)
  assert.equal(draft.read().pending, true)
  assert.equal(storage.getItem('one'), raw)
})

test('save failure leaves state untouched, so caller cannot send without a durable key', () => {
  const storage = memory()
  storage.setItem = () => { throw new Error('storage unavailable') }
  const draft = createStationDraft('one', defaults, storage, id)
  assert.throws(() => draft.begin(defaults), /storage unavailable/)
  assert.equal(draft.read(), null)
})

test('corrupt saved data is not overwritten', () => {
  const storage = memory()
  storage.setItem('one', '{broken')
  assert.throws(() => createStationDraft('one', defaults, storage, id), /损坏/)
  assert.equal(storage.getItem('one'), '{broken')
})

test('invalid saved shape is rejected without overwrite', () => {
  const storage = memory()
  const raw = JSON.stringify({ version: 1, requestId: 'request-0001', fields: { qty: 10 }, pending: true })
  storage.setItem('one', raw)
  assert.throws(() => createStationDraft('one', defaults, storage, id), /格式异常/)
  assert.equal(storage.getItem('one'), raw)
})

test('missing or non-string persisted request ID is corrupt rather than a new request', () => {
  for (const requestId of [undefined, null, 12345678]) {
    const storage = memory()
    const raw = JSON.stringify({ version: 2, requestId, fields: defaults, pending: true })
    storage.setItem('one', raw)
    assert.throws(() => createStationDraft('one', defaults, storage, id), /格式异常/)
    assert.equal(storage.getItem('one'), raw)
  }
})

test('successful completion clears only its own account/station draft', () => {
  const storage = memory()
  const one = createStationDraft('admin1:station1', defaults, storage, id)
  const other = createStationDraft('admin2:station2', defaults, storage, id)
  one.begin(defaults); other.begin(defaults)
  one.complete()
  assert.equal(one.read(), null)
  assert.equal(storage.getItem('admin1:station1'), null)
  assert.notEqual(storage.getItem('admin2:station2'), null)
})

test('cleanup failure preserves old request so retry remains idempotent', () => {
  const storage = memory()
  const draft = createStationDraft('one', defaults, storage, id)
  draft.begin(defaults)
  storage.removeItem = () => { throw new Error('remove failed') }
  assert.throws(() => draft.complete(), /remove failed/)
  assert.equal(draft.read().requestId, 'request-0001')
  assert.equal(draft.read().pending, true)
})

test('decimal validation rejects negative, exponent, excessive scale and overflow', () => {
  for (const value of ['-1', '0', '1e3', '1.001', '1000000000000', 'Infinity', 'NaN']) assert.equal(positiveDecimal(value, 12, 2), false)
  assert.equal(positiveDecimal('0.01', 12, 2), true)
  assert.equal(positiveDecimal('999999999999.99', 12, 2), true)
})

test('quantity quota uses exact cents at large and maximum DECIMAL boundaries', () => {
  for (const base of ['0', '10000000000', '999999999999']) {
    for (let cents = 1; cents < 100; cents++) {
      const qty = `${base}.${String(cents).padStart(2, '0')}`
      const received = `${base}.${String(cents - 1).padStart(2, '0')}`
      const remaining = remainingQuantity(qty, received)
      assert.equal(remaining, 1n)
      assert.equal(formatQuantity(remaining), '0.01')
      assert.equal(quantityUnits('0.01') > remaining, false)
      assert.equal(quantityUnits('0.02') > remaining, true)
    }
  }
  assert.equal(remainingQuantity('100', '100'), 0n)
  assert.equal(remainingQuantity('100', '101'), 0n)
  assert.equal(quantityUnits(100.01), 10001n)
})

test('invalid stock quantities fail closed instead of becoming zero or NaN', () => {
  for (const value of [null, undefined, '', '-1', '1.001', '1e3', Infinity, NaN]) {
    assert.throws(() => remainingQuantity('100', value), /数量格式异常/)
  }
})

test('purchase amount uses decimal half-up and matches database amount limits', () => {
  assert.equal(purchaseAmount('3', '0.1'), '0.30')
  assert.equal(purchaseAmount('1', '0.005'), '0.01')
  assert.equal(purchaseAmount('100', '3.1234'), '312.34')
  assert.equal(purchaseAmount('0.01', '0.0001'), null)
  assert.equal(purchaseAmount('999999999999.99', '999999.9999'), null)
})
