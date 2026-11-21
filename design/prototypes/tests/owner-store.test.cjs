const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'assets', 'js', 'owner-store.js'), 'utf8');

function setup(initial = {}) {
  const saved = new Map(Object.entries(initial));
  const storage = {
    fail: false,
    getItem: key => saved.get(key) ?? null,
    setItem(key, value) {
      if (this.fail) throw new Error('Storage full');
      saved.set(key, value);
    }
  };
  const context = vm.createContext({
    window: {}, localStorage: storage, GasStore: { station: { id: 'gaoxin' } },
    console: { error() {} }
  });
  vm.runInContext(source, context);
  return { store: context.window.OwnerStore, saved, storage };
}

test('inventory is isolated by buyer, gas and receipt, excluding unassigned records', () => {
  const { store } = setup();
  const data = {
    buyers: [{ id: 'A' }, { id: 'B' }],
    batches: [
      { id: 'A1', buyerId: 'A', gas: 'CNG', qty: 100 },
      { id: 'A2', buyerId: 'A', gas: 'CNG', qty: 200 },
      { id: 'A3', buyerId: 'A', gas: 'LNG', qty: 40 },
      { id: 'B1', buyerId: 'B', gas: 'CNG', qty: 1000 }
    ],
    receipts: [
      { id: 'A1', purchaseBatchId: 'A1', buyerId: 'A', gas: 'CNG', qty: 100, receivedAt: '2026-01-01' },
      { id: 'A3', purchaseBatchId: 'A3', buyerId: 'A', gas: 'LNG', qty: 40, receivedAt: '2026-01-01' },
      { id: 'B1', purchaseBatchId: 'B1', buyerId: 'B', gas: 'CNG', qty: 1000, receivedAt: '2026-01-01' }
    ],
    outflows: [
      { batchId: 'A1', buyerId: 'A', gas: 'CNG', qty: 12.5, status: 'completed' },
      { batchId: 'A1', buyerId: 'A', gas: 'CNG', qty: 30, status: 'pending' },
      { batchId: 'A3', buyerId: 'A', gas: 'LNG', qty: 7, status: 'completed' },
      { batchId: 'B1', buyerId: 'B', gas: 'CNG', qty: 30, status: 'completed' },
      { gas: 'CNG', qty: 50, status: 'completed' }
    ],
    adjustments: [
      { batchId: 'A1', buyerId: 'A', gas: 'CNG', amount: -2 },
      { batchId: 'B1', buyerId: 'B', gas: 'CNG', amount: 50 },
      { buyerId: 'A', gas: 'CNG', amount: -10 }
    ]
  };
  const cng = store.inventory(data, 'A', 'CNG');
  const lng = store.inventory(data, 'A', 'LNG');
  assert.equal(cng.incoming, 100);
  assert.equal(cng.outgoing, 12.5);
  assert.equal(cng.balance, 85.5);
  assert.equal(lng.balance, 33);
  assert.equal(store.inventory(data, 'B', 'CNG').balance, 1020);
});

test('batches stay pending until received and cannot be over-received', () => {
  const legacy = { buyers: [{ id: 'A' }], batches: [{ id: 'B1', buyerId: 'A', gas: 'CNG', qty: 100 }] };
  const { store, saved } = setup({ gasOwnerProcurement: JSON.stringify(legacy) });
  assert.equal(store.inventory(store.read(), 'A', 'CNG').balance, 0);
  assert.equal(saved.get(store.key), JSON.stringify(legacy));
  store.confirmReceipt('B1', 100, legacy.batches[0]);
  assert.equal(store.inventory(store.read(), 'A', 'CNG').balance, 100);
  const after = saved.get(store.key);
  assert.throws(() => store.confirmReceipt('B1', 1, legacy.batches[0]), /超过待进气量/);
  assert.equal(saved.get(store.key), after);
});

test('receipt rejects a batch changed after confirmation was opened', () => {
  const data = { buyers: [{ id: 'A' }], batches: [{ id: 'B1', buyerId: 'A', gas: 'CNG', qty: 100 }] };
  const { store, saved } = setup({ gasOwnerProcurement: JSON.stringify(data) });
  assert.throws(() => store.confirmReceipt('B1', 100, { buyerId: 'A', gas: 'CNG', qty: 90 }), /重新核对/);
  assert.equal(saved.get(store.key), JSON.stringify(data));
});

test('a batch without a valid buyer cannot be received into inventory', () => {
  const data = { buyers: [], batches: [{ id: 'B1', buyerId: 'missing', gas: 'CNG', qty: 100 }] };
  const { store, saved } = setup({ gasOwnerProcurement: JSON.stringify(data) });
  assert.throws(() => store.confirmReceipt('B1', 100, data.batches[0]), /采购商不存在/);
  assert.equal(saved.get(store.key), JSON.stringify(data));
});

test('adjustments record a reason, cannot exceed balance, and persist atomically', () => {
  const data = { buyers: [{ id: 'A' }, { id: 'B' }], batches: [
    { id: 'B1', buyerId: 'A', gas: 'CNG', qty: 100 },
    { id: 'B2', buyerId: 'A', gas: 'CNG', qty: 50 },
    { id: 'B3', buyerId: 'B', gas: 'CNG', qty: 20 }
  ], receipts: [
    { id: 'B1', purchaseBatchId: 'B1', buyerId: 'A', gas: 'CNG', qty: 100, receivedAt: '2026-01-01' },
    { id: 'B2', purchaseBatchId: 'B2', buyerId: 'A', gas: 'CNG', qty: 50, receivedAt: '2026-01-01' }
  ] };
  const { store, saved, storage } = setup({ gasOwnerProcurement: JSON.stringify(data) });
  store.adjust('B1', 'decrease', 10, '盘点', data.receipts[0]);
  assert.equal(store.inventory(store.read(), 'A', 'CNG').balance, 140);
  assert.equal(store.batchInventory(store.read(), store.read().batches[0]).balance, 90);
  assert.equal(store.inventory(store.read(), 'B', 'CNG').balance, 0);
  assert.equal(store.read().adjustments[0].buyerId, 'A');
  assert.equal(store.read().adjustments[0].batchId, 'B1');
  assert.equal(store.read().adjustments[0].reason, '盘点');
  const before = saved.get(store.key);
  assert.throws(() => store.adjust('B1', 'decrease', 91, '盘点', data.receipts[0]), /超过/);
  assert.throws(() => store.adjust('missing', 'increase', 1, '盘点'), /进气记录/);
  assert.throws(() => store.adjust('B1', 'increase', 1, ' ', data.receipts[0]), /原因/);
  assert.throws(() => store.adjust('B1', 'increase', 1, '盘点', { ...data.receipts[0], buyerId: 'B' }), /重新核对/);
  storage.fail = true;
  assert.throws(() => store.adjust('B1', 'increase', 10, '盘点', data.receipts[0]), /保存失败/);
  assert.equal(saved.get(store.key), before);
});

test('unassigned historical records require explicit assignment and cannot be assigned twice', () => {
  const data = {
    buyers: [{ id: 'A' }, { id: 'B' }], batches: [
      { id: 'A1', buyerId: 'A', gas: 'CNG', qty: 100, receivedAt: '2026-01-01' },
      { id: 'B1', buyerId: 'B', gas: 'CNG', qty: 40, receivedAt: '2026-01-01' },
      { id: 'A2', buyerId: 'A', gas: 'LNG', qty: 30, receivedAt: '2026-01-01' }
    ],
    adjustments: [{ id: 'old', buyerId: 'A', gas: 'CNG', amount: 15, reason: '盘点' }],
    outflows: [{ id: 'out', gas: 'CNG', qty: 5, status: 'completed' }]
  };
  const { store, saved } = setup({ gasOwnerProcurement: JSON.stringify(data) });
  assert.equal(store.inventory(store.read(), 'A', 'CNG').balance, 100);
  assert.throws(() => store.assignLegacy('adjustments', data.adjustments[0], 'B1'), /不一致/);
  assert.throws(() => store.assignLegacy('adjustments', data.adjustments[0], 'A2'), /不一致/);
  store.assignLegacy('adjustments', data.adjustments[0], 'A1');
  store.assignLegacy('outflows', data.outflows[0], 'B1');
  assert.equal(store.inventory(store.read(), 'A', 'CNG').balance, 115);
  assert.equal(store.inventory(store.read(), 'B', 'CNG').balance, 35);
  const before = saved.get(store.key);
  assert.throws(() => store.assignLegacy('adjustments', data.adjustments[0], 'A1'), /变更/);
  assert.equal(saved.get(store.key), before);
});

test('saved procurement is shared and storage errors do not overwrite records', () => {
  const { store, saved, storage } = setup();
  const data = store.read();
  data.batches[0].paidAmount = 100;
  store.write(data);
  assert.equal(store.read().batches[0].paidAmount, 100);
  const before = saved.get(store.key);
  storage.fail = true;
  assert.throws(() => store.write({ buyers: [], batches: [] }), /保存失败/);
  assert.equal(saved.get(store.key), before);
  saved.set(store.key, '{broken');
  assert.throws(() => store.read(), /未覆盖原有数据/);
  assert.equal(saved.get(store.key), '{broken');
});

test('inventory alerts flag buyers at or below their per-gas threshold', () => {
  const data = {
    buyers: [
      { id: 'A', name: '宏达', alerts: { CNG: 100, LNG: 10 } },
      { id: 'B', name: '顺达', alerts: { CNG: 500 } }
    ],
    batches: [{ id: 'P1', buyerId: 'A', gas: 'CNG', qty: 200 }],
    receipts: [{ id: 'R1', purchaseBatchId: 'P1', buyerId: 'A', gas: 'CNG', qty: 200, receivedAt: '2026-01-01' }],
    outflows: [{ batchId: 'R1', buyerId: 'A', gas: 'CNG', qty: 120, status: 'completed' }]
  };
  const { store } = setup({ gasOwnerProcurement: JSON.stringify(data) });
  const alerts = store.alertsFor(store.read());
  assert.equal(alerts.length, 1);
  assert.equal(alerts[0].buyer.id, 'A');
  assert.equal(alerts[0].gas, 'CNG');
  assert.equal(alerts[0].balance, 80);
  assert.equal(alerts[0].threshold, 100);
});
