/* Local prototype records only; no real payments or inventory transactions. */
(function () {
  const key = 'gasPrototypeData';
  const station = { id: 'gaoxin', name: '高新区油气合建站' };
  const staff = '李师傅';
  const guns = [1, 2, 3, 4, 5, 6].map(no => ({
    no: String(no), gas: no < 5 ? 'CNG' : 'LNG',
    price: no < 5 ? 3.90 : 4.98, unit: no < 5 ? 'm³' : 'kg'
  }));
  const now = () => new Date().toISOString();
  const id = prefix => prefix + Date.now() + Math.random().toString(36).slice(2, 8);
  const round = value => Math.round(value * 100) / 100;
  const dateKey = value => {
    const date = new Date(value);
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  };
  const money = value => '¥' + Number(value).toFixed(2);
  const time = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—';
  const gunFor = no => {
    const gun = guns.find(item => item.no === String(no));
    if (!gun) throw new Error('请选择本站有效的加气枪号');
    return gun;
  };
  function write(data) {
    try { localStorage.setItem(key, JSON.stringify(data)); }
    catch (error) {
      console.error('保存原型记录失败', error);
      throw new Error('保存失败，请检查浏览器存储空间后重试；操作未完成');
    }
  }
  function read() {
    try {
      const saved = localStorage.getItem(key);
      if (saved) {
        const data = JSON.parse(saved);
        if (data.version !== 1 || !Array.isArray(data.orders) || !Array.isArray(data.shifts)) throw new Error('原型数据格式无效');
        return data;
      }
      const data = { version: 1, orders: [], shifts: [], currentShiftId: null };
      const legacy = JSON.parse(localStorage.getItem('gasPendingDriverOrder') || 'null');
      if (legacy) {
        const gun = gunFor(legacy.gun);
        data.orders.push({
          ...legacy, stationId: legacy.station && legacy.station !== station.name ? 'other' : station.id,
          driver: '138****6621', gas: gun.gas, price: gun.price, unit: gun.unit,
          amount: Number(legacy.amount), volume: Number(legacy.volume),
          createdAt: now(), paymentMethod: '旧版模拟支付', actualVolume: null
        });
      }
      const last = JSON.parse(localStorage.getItem('gasLastShiftSummary') || 'null');
      if (last) {
        data.shifts.push({
          id: id('OLD'), stationId: station.id, staff,
          startedAt: last.startedAt || last.endedAt, endedAt: last.endedAt,
          legacyTotals: { orders: Number(last.completedOrders), amount: Number(last.amount) }
        });
      }
      if (localStorage.getItem('gasStaffShift') === 'on') {
        const totals = JSON.parse(localStorage.getItem('gasShiftTotals') || '{"orders":0,"amount":0}');
        const shift = {
          id: id('SHIFT'), stationId: station.id, staff,
          startedAt: localStorage.getItem('gasShiftStartedAt') || now(), endedAt: null,
          legacyTotals: { orders: Number(totals.orders), amount: Number(totals.amount) }
        };
        data.shifts.push(shift);
        data.currentShiftId = shift.id;
      }
      write(data);
      return data;
    } catch (error) {
      console.error('读取原型记录失败', error);
      throw new Error('本地演示记录无法读取，请检查浏览器存储；未覆盖原有数据');
    }
  }
  function run(action) {
    try { return action(); }
    catch (error) {
      console.error('原型操作失败', error);
      toast(error.message);
      return null;
    }
  }
  const activeShift = data => data.shifts.find(shift => shift.id === data.currentShiftId && !shift.endedAt) || null;
  function requireShift(data) {
    const shift = activeShift(data);
    if (!shift) throw new Error('当前未当班，请先开始当班');
    return shift;
  }
  function totalsFor(data, shiftId) {
    const shift = data.shifts.find(item => item.id === shiftId);
    const legacy = shift && shift.legacyTotals;
    const orders = data.orders.filter(order => order.status === 'completed' && order.completedShiftId === shiftId);
    return {
      orders: orders.length + (legacy ? legacy.orders : 0),
      amount: round(orders.reduce((sum, order) => sum + order.amount, legacy ? legacy.amount : 0)),
      cng: round(orders.filter(order => order.gas === 'CNG').reduce((sum, order) => sum + order.actualVolume, 0)),
      lng: round(orders.filter(order => order.gas === 'LNG').reduce((sum, order) => sum + order.actualVolume, 0)),
      legacy: Boolean(legacy && legacy.orders)
    };
  }
  function startShift() {
    const data = read();
    if (activeShift(data)) throw new Error('当前已在当班，无需重复开班');
    const shift = { id: id('SHIFT'), stationId: station.id, staff, startedAt: now(), endedAt: null };
    data.shifts.push(shift);
    data.currentShiftId = shift.id;
    write(data);
    return shift;
  }
  function endShift(expectedId) {
    const data = read(), shift = requireShift(data);
    if (shift.id !== expectedId) throw new Error('班次已变更，请重新核对交班信息');
    shift.endedAt = now();
    data.currentShiftId = null;
    write(data);
    return shift;
  }
  function makeOrder(gun, amount, source) {
    if (!Number.isFinite(amount) || !Number.isFinite(round(amount)) || round(amount) <= 0) throw new Error('请输入至少 0.01 元的有效订单金额');
    return {
      id: id('GS'), stationId: station.id, station: station.name, driver: '138****6621',
      gun: gun.no, gas: gun.gas, unit: gun.unit, price: gun.price, amount: round(amount),
      volume: round(amount / gun.price), actualVolume: null, createdAt: now(), source
    };
  }
  function createProxy(input) {
    const data = read(), shift = requireShift(data);
    const order = makeOrder(gunFor(input.gun), input.amount, 'proxy');
    Object.assign(order, { status: 'waiting_driver', staff, createdShiftId: shift.id });
    data.orders.push(order);
    write(data);
    return order;
  }
  function createPaid(input) {
    const data = read(), gun = gunFor(input.gun);
    if (!Number.isFinite(input.volume) || input.volume <= 0) throw new Error('请输入有效的加气量');
    const order = makeOrder(gun, input.amount, 'driver');
    Object.assign(order, { volume: input.volume, status: 'paid', paidAt: now(), paymentMethod: input.paymentMethod });
    data.orders.push(order);
    write(data);
    return order;
  }
  function getOrder(data, orderId) {
    const order = data.orders.find(item => item.id === orderId);
    if (!order) throw new Error('未找到该订单，请重新扫码或核对订单编号');
    return order;
  }
  function pay(orderId, paymentMethod) {
    const data = read(), order = getOrder(data, orderId);
    if (order.status !== 'waiting_driver') throw new Error('订单状态已变更，不能重复支付');
    Object.assign(order, { status: 'paid', paymentMethod, paidAt: now() });
    write(data);
    return order;
  }
  function reject(orderId) {
    const data = read(), order = getOrder(data, orderId);
    if (order.status !== 'waiting_driver') throw new Error('该订单当前不能拒绝');
    Object.assign(order, { status: 'rejected', closedAt: now(), reason: '司机拒绝' });
    write(data);
    return order;
  }
  function checkRedeem(order) {
    if (order.stationId !== station.id) throw new Error('非本站订单，请前往订单所属站点核销');
    const errors = {
      waiting_driver: '司机尚未确认支付，暂不能核销',
      rejected: '司机已拒绝订单，不能核销',
      refunded: '订单已退款，不能核销',
      closed: '订单已关闭，不能核销',
      completed: '该订单已核销，动态码已失效，不能重复核销'
    };
    if (order.status !== 'paid') throw new Error(errors[order.status] || '订单状态异常，不能核销');
    return order;
  }
  function redeem(orderId, actualVolume, shiftId) {
    const data = read(), shift = requireShift(data);
    if (shift.id !== shiftId) throw new Error('班次已变更，请重新扫描订单');
    const order = checkRedeem(getOrder(data, orderId));
    if (!Number.isFinite(actualVolume) || actualVolume <= 0) throw new Error('请输入加气机显示的有效实际气量');
    Object.assign(order, {
      status: 'completed', actualVolume: round(actualVolume), completedAt: now(),
      completedBy: shift.staff, completedShiftId: shift.id
    });
    if (!Number.isFinite(order.actualVolume) || order.actualVolume <= 0) throw new Error('请输入至少 0.01 的有效实际气量');
    // Order completion and its shift attribution are saved in one snapshot.
    write(data);
    return order;
  }
  function demoOrder() {
    const data = read();
    let order = data.orders.find(item => item.id === 'GS2026111314328261');
    if (!order) {
      order = makeOrder(gunFor(3), 190, 'demo');
      Object.assign(order, {
        id: 'GS2026111314328261', status: localStorage.getItem('gasCompletedOrder') === 'GS2026111314328261' ? 'completed' : 'paid',
        volume: 51.28, paymentMethod: '微信支付 · 拉卡拉', paidAt: now()
      });
      data.orders.push(order);
      write(data);
    }
    return order;
  }
  window.GasStore = {
    key, station, staff, guns, gunFor, dateKey, money, time, read, run,
    activeShift, totalsFor, startShift, endShift, createProxy, createPaid,
    getOrder, pay, reject, checkRedeem, redeem, demoOrder
  };
})();
