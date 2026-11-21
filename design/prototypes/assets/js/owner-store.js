(function () {
  const key = 'gasOwnerProcurement';
  const dateKey = value => {
    const date = new Date(value);
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  };
  function defaults() {
    const today = new Date();
    const past = new Date(today);
    past.setDate(past.getDate() - 5);
    const currentDate = dateKey(today), historyDate = dateKey(past);
    const dayAgo = n => dateKey(new Date(today.getTime() - n * 86400000));
    const d0 = currentDate, d1 = dayAgo(1), d2 = dayAgo(2), d3 = dayAgo(3), d4 = dayAgo(4), d5 = dayAgo(5);
    const b001BatchId = 'PO-' + currentDate.replaceAll('-', '') + '-003';
    const b002BatchId = 'PO-' + historyDate.replaceAll('-', '') + '-006';
    const b001Receipts = [
      { id: 'IN-' + d5.replaceAll('-', '') + '-003a', purchaseBatchId: b001BatchId, buyerId: 'B001', gas: 'CNG', qty: 5000, receivedAt: d5 + 'T08:00:00', demo: true },
      { id: 'IN-' + d2.replaceAll('-', '') + '-003b', purchaseBatchId: b001BatchId, buyerId: 'B001', gas: 'CNG', qty: 5000, receivedAt: d2 + 'T08:00:00', demo: true },
      { id: 'IN-' + d0.replaceAll('-', '') + '-003c', purchaseBatchId: b001BatchId, buyerId: 'B001', gas: 'CNG', qty: 2000, receivedAt: d0 + 'T08:00:00', demo: true }
    ];
    return {
      buyers: [
        { id: 'B001', name: '宏达物流有限公司', contact: '张经理', phone: '138****6621', alerts: { CNG: 5000, LNG: 500 } },
        { id: 'B002', name: '顺达运输车队', contact: '王队长', phone: '139****7812', alerts: { CNG: 3000, LNG: 300 } }
      ],
      batches: [
        { id: b001BatchId, buyerId: 'B001', gas: 'CNG', date: currentDate, qty: 12000, price: 3.12, payment: '部分付款', invoice: '待开票', demo: true },
        { id: b002BatchId, buyerId: 'B002', gas: 'CNG', date: historyDate, qty: 30000, price: 3.12, payment: '已付清', invoice: '已登记', demo: true }
      ],
      adjustments: [],
      receipts: [
        { id: 'IN-' + historyDate.replaceAll('-', '') + '-006', purchaseBatchId: b002BatchId, buyerId: 'B002', gas: 'CNG', qty: 30000, receivedAt: historyDate + 'T09:00:00', demo: true },
        ...b001Receipts
      ],
      outflows: [
        { id: 'DEMO-OUT-001', buyerId: 'B002', batchId: 'IN-' + historyDate.replaceAll('-', '') + '-006', gas: 'CNG', qty: 50, status: 'completed', occurredAt: historyDate + 'T14:00:00', demo: true },
        { id: 'DEMO-OUT-002', buyerId: 'B001', batchId: b001Receipts[0].id, gas: 'CNG', qty: 3000, status: 'completed', occurredAt: d4 + 'T16:00:00', demo: true },
        { id: 'DEMO-OUT-003', buyerId: 'B001', batchId: b001Receipts[0].id, gas: 'CNG', qty: 2000, status: 'completed', occurredAt: d3 + 'T16:00:00', demo: true },
        { id: 'DEMO-OUT-004', buyerId: 'B001', batchId: b001Receipts[1].id, gas: 'CNG', qty: 4000, status: 'completed', occurredAt: d1 + 'T16:00:00', demo: true },
        { id: 'DEMO-OUT-005', buyerId: 'B001', batchId: b001Receipts[2].id, gas: 'CNG', qty: 2000, status: 'completed', occurredAt: d0 + 'T16:00:00', demo: true }
      ]
    };
  }
  function normalize(data) {
    const receipts = Array.isArray(data.receipts) ? data.receipts.slice() : [];
    (data.batches || []).forEach(batch => {
      if (batch.receivedAt && !receipts.some(receipt => receipt.id === batch.id || receipt.purchaseBatchId === batch.id)) {
        receipts.push({
          id: batch.id,
          purchaseBatchId: batch.id,
          buyerId: batch.buyerId,
          gas: batch.gas,
          qty: Number(batch.qty),
          receivedAt: batch.receivedAt,
          demo: batch.demo
        });
      }
    });
    return { ...data, receipts, adjustments: data.adjustments || [], outflows: data.outflows || [] };
  }
  function read() {
    try {
      const saved = localStorage.getItem(key);
      if (!saved) return defaults();
      const data = JSON.parse(saved);
      if (!data || !Array.isArray(data.buyers) || !Array.isArray(data.batches) ||
          (data.adjustments != null && !Array.isArray(data.adjustments)) ||
          (data.outflows != null && !Array.isArray(data.outflows)) ||
          (data.receipts != null && !Array.isArray(data.receipts))) throw new Error('数据格式无效');
      return normalize(data);
    } catch (error) {
      console.error('读取采购记录失败', error);
      throw new Error('本地采购记录无法读取，请检查浏览器存储；未覆盖原有数据');
    }
  }
  function write(data) {
    try { localStorage.setItem(key, JSON.stringify(normalize(data))); }
    catch (error) {
      console.error('保存采购记录失败', error);
      throw new Error('采购记录保存失败，请检查浏览器存储后重试');
    }
  }
  const round = value => Math.round(value * 100) / 100;
  const purchaseReceipts = (data, batch) => data.receipts.filter(receipt => receipt.purchaseBatchId === batch.id && receipt.buyerId === batch.buyerId && receipt.gas === batch.gas);
  function receiptInventory(data, receipt) {
    const outgoing = data.outflows.filter(flow => flow.batchId === receipt.id && flow.buyerId === receipt.buyerId && flow.gas === receipt.gas && flow.status === 'completed').reduce((sum, flow) => sum + Number(flow.qty), 0);
    const adjustment = data.adjustments.filter(item => item.batchId === receipt.id && item.buyerId === receipt.buyerId && item.gas === receipt.gas).reduce((sum, item) => sum + Number(item.amount), 0);
    const incoming = Number(receipt.qty);
    return { incoming: round(incoming), outgoing: round(outgoing), adjustment: round(adjustment), balance: round(incoming - outgoing + adjustment) };
  }
  function batchInventory(data, batch) {
    const totals = purchaseReceipts(data, batch).map(receipt => receiptInventory(data, receipt));
    const sum = key => round(totals.reduce((total, item) => total + item[key], 0));
    return { incoming: sum('incoming'), outgoing: sum('outgoing'), adjustment: sum('adjustment'), balance: sum('balance') };
  }
  function receivedQty(data, batch) {
    return round(purchaseReceipts(data, batch).reduce((sum, receipt) => sum + Number(receipt.qty), 0));
  }
  function remainingQty(data, batch) {
    return round(Math.max(0, Number(batch.qty) - receivedQty(data, batch)));
  }
  function inventory(data, buyerId, gas) {
    if (!buyerId) throw new Error('请选择采购商');
    const totals = data.receipts.filter(receipt => receipt.buyerId === buyerId && receipt.gas === gas).map(receipt => receiptInventory(data, receipt));
    const sum = key => round(totals.reduce((total, item) => total + item[key], 0));
    return { incoming: sum('incoming'), outgoing: sum('outgoing'), adjustment: sum('adjustment'), balance: sum('balance') };
  }
  function alertsFor(data) {
    const result = [];
    data.buyers.forEach(buyer => {
      const alerts = buyer.alerts || {};
      ['CNG', 'LNG'].forEach(gas => {
        const threshold = Number(alerts[gas]);
        if (!Number.isFinite(threshold) || threshold <= 0) return;
        const inv = inventory(data, buyer.id, gas);
        if (inv.incoming > 0 && inv.balance <= threshold) result.push({ buyer, gas, balance: inv.balance, threshold });
      });
    });
    return result;
  }
  function confirmReceipt(batchId, qty, expected) {
    const data = read();
    const batch = data.batches.find(item => item.id === batchId);
    if (!batch) throw new Error('采购批次不存在，请刷新后重试');
    if (!expected || batch.gas !== expected.gas || Number(batch.qty) !== Number(expected.qty) || batch.buyerId !== expected.buyerId) throw new Error('采购批次已变更，请重新核对后确认');
    if (!data.buyers.some(buyer => buyer.id === batch.buyerId)) throw new Error('批次采购商不存在，请先修正采购批次');
    if (!['CNG', 'LNG'].includes(batch.gas) || !Number.isFinite(Number(batch.qty)) || Number(batch.qty) <= 0) throw new Error('采购批次气种或数量无效，请先修改');
    const remaining = remainingQty(data, batch);
    if (!Number.isFinite(qty) || qty <= 0 || !Number.isFinite(round(qty)) || round(qty) <= 0 || round(qty) > remaining) throw new Error('本次进气量必须大于 0 且不能超过待进气量');
    data.receipts.push({
      id: 'IN-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8),
      purchaseBatchId: batch.id,
      buyerId: batch.buyerId,
      gas: batch.gas,
      qty: round(qty),
      receivedAt: new Date().toISOString(),
      demo: batch.demo
    });
    write(data);
    return data;
  }
  function adjust(receiptId, direction, qty, reason, expected) {
    if (!['increase', 'decrease'].includes(direction) ||
        !Number.isFinite(qty) || qty <= 0 || !Number.isFinite(round(qty)) || round(qty) <= 0 ||
        typeof reason !== 'string' || !reason.trim()) throw new Error('请填写有效的调整数量和原因');
    const data = read(), amount = round(qty) * (direction === 'increase' ? 1 : -1);
    const receipt = data.receipts.find(item => item.id === receiptId);
    if (!receipt) throw new Error('请选择有效的进气记录');
    if (!expected || receipt.buyerId !== expected.buyerId || receipt.gas !== expected.gas || Number(receipt.qty) !== Number(expected.qty) || receipt.receivedAt !== expected.receivedAt) throw new Error('进气记录已变更，请重新核对后调整');
    if (!data.buyers.some(buyer => buyer.id === receipt.buyerId)) throw new Error('采购商不存在，请重新选择');
    if (direction === 'decrease' && round(receiptInventory(data, receipt).balance + amount) < 0) throw new Error('调减数量不能超过该进气批次的本地账面库存');
    data.adjustments.push({ id: 'ADJ-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8), buyerId: receipt.buyerId, batchId: receipt.id, gas: receipt.gas, amount, reason: reason.trim(), createdAt: new Date().toISOString() });
    write(data);
    return data;
  }
  function assignLegacy(kind, expected, batchId) {
    const data = read();
    if (!['adjustments', 'outflows'].includes(kind)) throw new Error('记录类型无效');
    const receipt = data.receipts.find(entry => entry.id === batchId);
    if (!receipt || !data.buyers.some(buyer => buyer.id === receipt.buyerId)) throw new Error('请选择有效的进气记录');
    const item = data[kind].find(entry => entry.id === expected.id);
    if (!item || item.batchId || item.buyerId !== expected.buyerId || item.gas !== expected.gas ||
        (kind === 'adjustments' ? item.amount !== expected.amount || item.reason !== expected.reason : item.qty !== expected.qty || item.status !== expected.status || item.occurredAt !== expected.occurredAt)) {
      throw new Error('记录已变更，请重新核对后操作');
    }
    if (receipt.gas !== item.gas || (item.buyerId && item.buyerId !== receipt.buyerId)) throw new Error('进气记录与历史记录的采购商或气种不一致');
    item.buyerId = receipt.buyerId;
    item.batchId = receipt.id;
    item.assignedAt = new Date().toISOString();
    write(data);
    return data;
  }
  window.OwnerStore = { key, dateKey, read, write, inventory, batchInventory, receiptInventory, receivedQty, remainingQty, confirmReceipt, adjust, assignLegacy, alertsFor };
})();
