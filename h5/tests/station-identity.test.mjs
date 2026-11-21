import test from 'node:test'
import assert from 'node:assert/strict'
import { createStationIdentity } from '../src/utils/station-identity.js'

function setup() {
  let token = 'mock-original', notifications = 0
  const storage = { getItem: () => token }
  const guard = createStationIdentity(storage, () => notifications++)
  return { guard, storage, set: value => { token = value }, notifications: () => notifications }
}

test('unchanged form uses its captured identity', () => {
  const { guard } = setup()
  assert.equal(guard.authorize(), 'mock-original')
  assert.doesNotThrow(() => guard.assertCurrent())
})

test('same-tab changes block dispatch even without storage event', () => {
  const fixture = setup()
  fixture.set('mock-replacement')
  assert.throws(() => fixture.guard.authorize(), e => e.code === 'STATION_IDENTITY_CHANGED' && !e.message.includes('mock-'))
  fixture.set('mock-original')
  assert.throws(() => fixture.guard.authorize(), /身份已变化/)
  assert.equal(fixture.notifications(), 1)
})

test('cross-tab A to B to A event invalidates old form permanently', () => {
  const fixture = setup()
  fixture.guard.storageChanged({ key: 'h5_token', newValue: 'mock-replacement' })
  fixture.guard.storageChanged({ key: 'h5_token', newValue: 'mock-original' })
  assert.throws(() => fixture.guard.assertCurrent(), /身份已变化/)
  assert.equal(fixture.notifications(), 1)
})

test('unrelated storage event does not invalidate form', () => {
  const { guard } = setup()
  guard.storageChanged({ key: 'other', newValue: 'x' })
  guard.storageChanged({ key: 'h5_token', newValue: 'mock-original' })
  assert.equal(guard.authorize(), 'mock-original')
})

test('logout, clear and unavailable storage fail closed', () => {
  const logout = setup(); logout.set(null)
  assert.throws(() => logout.guard.authorize(), /身份已变化/)
  const cleared = setup(); cleared.guard.storageChanged({ key: null })
  assert.throws(() => cleared.guard.authorize(), /身份已变化/)
  const denied = setup(); denied.storage.getItem = () => { throw new Error('denied') }
  assert.throws(() => denied.guard.authorize(), /身份已变化/)
  assert.throws(() => createStationIdentity({ getItem: () => null }), /重新授权/)
})
