const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'assets', 'js', 'gas-store.js'), 'utf8');

function setup(initial = {}) {
  const saved = new Map(Object.entries(initial));
  const messages = [];
  const storage = {
    fail: false,
    getItem: key => saved.get(key) ?? null,
    setItem(key, value) {
      if (this.fail) throw new Error('Storage full');
      saved.set(key, value);
    }
  };
  const context = vm.createContext({
    window: {}, localStorage: storage, toast: message => messages.push(message),
    console: { error() {} }
  });
  vm.runInContext(source, context);
  return { store: context.window.GasStore, saved, storage, messages };
}

test('multiple proxy orders retain their own gun, driver decision and redemption', () => {
  const { store } = setup();
  const shift = store.startShift();
  const cng = store.createProxy({ gun: 3, amount: 200 });
  const lng = store.createProxy({ gun: 6, amount: 300 });
  assert.notEqual(cng.id, lng.id);
  assert.equal(cng.price, 3.9);
  assert.equal(cng.unit, 'm³');
  assert.equal(lng.price, 4.98);
  assert.equal(lng.unit, 'kg');
  store.pay(cng.id, '微信支付');
  store.reject(lng.id);
  store.redeem(cng.id, 49.5, shift.id);
  const data = store.read();
  assert.equal(data.orders.length, 2);
  assert.equal(store.getOrder(data, lng.id).status, 'rejected');
  const completed = store.getOrder(data, cng.id);
  assert.equal(completed.status, 'completed');
  assert.equal(completed.actualVolume, 49.5);
  assert.equal(completed.completedBy, shift.staff);
  assert.ok(completed.completedAt);
  assert.equal(store.totalsFor(data, shift.id).amount, 200);
  assert.equal(store.totalsFor(data, shift.id).cng, 49.5);
  assert.throws(() => store.redeem(cng.id, 49.5, shift.id), /重复核销/);
  assert.throws(() => store.pay(cng.id, '微信支付'), /重复支付/);
});

test('active driver payment preserves discounted amount and original expected volume', () => {
  const { store } = setup();
  const order = store.createPaid({ gun: 5, amount: 488, volume: 100, paymentMethod: '支付宝' });
  assert.equal(order.status, 'paid');
  assert.equal(order.amount, 488);
  assert.equal(order.volume, 100);
  assert.throws(() => store.createProxy({ gun: 3, amount: 200 }), /未当班/);
  const shift = store.startShift();
  store.redeem(order.id, 99.87, shift.id);
  const totals = store.totalsFor(store.read(), shift.id);
  assert.equal(totals.lng, 99.87);
  assert.equal(totals.cng, 0);
  assert.equal(totals.amount, 488);
});

test('unpaid, rejected, refunded, closed and foreign orders cannot be redeemed', () => {
  const { store, saved } = setup();
  const shift = store.startShift();
  const order = store.createProxy({ gun: 3, amount: 200 });
  const errors = {
    waiting_driver: /尚未确认支付/, rejected: /已拒绝/,
    refunded: /已退款/, closed: /已关闭/
  };
  for (const [status, error] of Object.entries(errors)) {
    const data = store.read();
    data.orders[0].status = status;
    saved.set(store.key, JSON.stringify(data));
    assert.throws(() => store.redeem(order.id, 50, shift.id), error);
    assert.equal(store.totalsFor(store.read(), shift.id).orders, 0);
  }
  const data = store.read();
  Object.assign(data.orders[0], { status: 'paid', stationId: 'other-station' });
  saved.set(store.key, JSON.stringify(data));
  assert.throws(() => store.redeem(order.id, 50, shift.id), /非本站订单/);
});

test('actual volume and rounded monetary values must remain finite and positive', () => {
  const { store, saved } = setup();
  const shift = store.startShift();
  const order = store.createPaid({ gun: 3, amount: 200, volume: 51.28, paymentMethod: '微信支付' });
  const before = saved.get(store.key);
  for (const value of [0, -1, NaN, Infinity, 0.001, Number.MAX_VALUE]) {
    assert.throws(() => store.redeem(order.id, value, shift.id), /实际气量/);
    assert.equal(saved.get(store.key), before);
  }
  for (const amount of [0.001, NaN, Infinity, Number.MAX_VALUE]) {
    assert.throws(() => store.createProxy({ gun: 3, amount }), /订单金额/);
  }
  assert.throws(() => store.createProxy({ gun: 9, amount: 200 }), /有效的加气枪号/);
});

test('handover gates all staff operations and retains each shift independently', () => {
  const { store } = setup();
  const first = store.startShift();
  assert.throws(() => store.startShift(), /重复开班/);
  const a = store.createPaid({ gun: 3, amount: 100, volume: 25.64 });
  const b = store.createPaid({ gun: 5, amount: 200, volume: 40.16 });
  store.redeem(a.id, 25.3, first.id);
  store.endShift(first.id);
  assert.throws(() => store.redeem(b.id, 40, first.id), /未当班/);
  assert.throws(() => store.createProxy({ gun: 3, amount: 200 }), /未当班/);
  const second = store.startShift();
  assert.throws(() => store.redeem(b.id, 40, first.id), /班次已变更/);
  assert.throws(() => store.endShift(first.id), /班次已变更/);
  store.redeem(b.id, 39.9, second.id);
  store.endShift(second.id);
  const data = store.read();
  assert.equal(data.shifts.length, 2);
  assert.ok(data.shifts.every(shift => shift.startedAt && shift.endedAt));
  assert.equal(store.totalsFor(data, first.id).cng, 25.3);
  assert.equal(store.totalsFor(data, second.id).lng, 39.9);
  assert.equal(store.totalsFor(data, second.id).orders, 1);
});

test('storage failure does not complete payment, redemption or handover', () => {
  const { store, saved, storage, messages } = setup();
  const shift = store.startShift();
  const order = store.createProxy({ gun: 3, amount: 200 });
  const beforePayment = saved.get(store.key);
  storage.fail = true;
  assert.throws(() => store.pay(order.id, '微信支付'), /操作未完成/);
  assert.equal(saved.get(store.key), beforePayment);
  storage.fail = false;
  store.pay(order.id, '微信支付');
  const beforeRedemption = saved.get(store.key);
  storage.fail = true;
  assert.equal(store.run(() => store.redeem(order.id, 50, shift.id)), null);
  assert.match(messages.at(-1), /保存失败/);
  assert.equal(saved.get(store.key), beforeRedemption);
  assert.equal(store.totalsFor(store.read(), shift.id).orders, 0);
  assert.throws(() => store.endShift(shift.id), /操作未完成/);
  assert.ok(store.activeShift(store.read()));
});

test('legacy records migrate once without double-counting old completed orders', () => {
  const { store, saved } = setup({
    gasStaffShift: 'on', gasShiftTotals: JSON.stringify({ orders: 2, amount: 390 }),
    gasShiftStartedAt: '2026-01-01T08:00:00.000Z',
    gasLastShiftSummary: JSON.stringify({ endedAt: '2025-12-31T12:00:00.000Z', completedOrders: 3, amount: 600 }),
    gasPendingDriverOrder: JSON.stringify({
      id: 'legacy-order', gun: '3', status: 'completed', amount: '190', volume: '48.72'
    })
  });
  let data = store.read();
  assert.equal(data.orders.length, 1);
  assert.equal(data.orders[0].amount, 190);
  assert.equal(data.shifts.length, 2);
  const shift = store.activeShift(data);
  assert.equal(store.totalsFor(data, shift.id).orders, 2);
  assert.equal(store.totalsFor(data, shift.id).cng, 0);
  const order = store.createPaid({ gun: 5, amount: 200, volume: 40 });
  store.redeem(order.id, 39.9, shift.id);
  data = store.read();
  assert.equal(store.totalsFor(data, shift.id).orders, 3);
  assert.equal(store.totalsFor(data, shift.id).amount, 590);
  saved.set('gasShiftTotals', JSON.stringify({ orders: 100, amount: 99999 }));
  assert.equal(store.totalsFor(store.read(), shift.id).orders, 3);
});

test('corrupt saved data is reported without replacing existing records', () => {
  const { store, saved } = setup({ gasPrototypeData: '{broken' });
  assert.throws(() => store.read(), /未覆盖原有数据/);
  assert.equal(saved.get(store.key), '{broken');
});
