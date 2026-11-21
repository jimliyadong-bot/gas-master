/* 原型公共脚本：移动端外壳、后台布局、Toast、弹层、Tab、ECharts 主题
 * 仅用于演示，所有数据为模拟数据。 */
(function () {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
  window.$ = $; window.$$ = $$;

  /* ---------- Toast ---------- */
  window.toast = function (msg) {
    let t = $('#__toast');
    if (!t) { t = document.createElement('div'); t.id = '__toast'; t.className = 'toast'; t.setAttribute('role', 'status'); document.body.appendChild(t); }
    t.textContent = msg; t.classList.add('show');
    clearTimeout(t._h); t._h = setTimeout(() => t.classList.remove('show'), 1600);
  };

  /* ---------- 弹层：data-open="#id" / data-close ---------- */
  function openLayer(sel) {
    const el = $(sel); if (!el) return;
    el.classList.add('show');
    const mask = $('#' + (el.dataset.mask || '__mask'));
    if (mask) mask.classList.add('show');
  }
  function closeAll() { $$('.sheet.show,.drawer.show,.sheet-mask.show').forEach(e => e.classList.remove('show')); }
  window.openLayer = openLayer; window.closeLayers = closeAll;
  document.addEventListener('click', e => {
    const o = e.target.closest('[data-open]'); if (o) { e.preventDefault(); openLayer(o.dataset.open); return; }
    if (e.target.closest('[data-close]') || e.target.classList.contains('sheet-mask')) closeAll();
    const tip = e.target.closest('[data-toast]'); if (tip) toast(tip.dataset.toast);
  });
  document.addEventListener('keydown', e => { if (e.key === 'Escape') closeAll(); });

  /* ---------- 分段 / Tab：.seg 或 [data-tabs] ---------- */
  document.addEventListener('click', e => {
    const b = e.target.closest('[data-tabs] > *, .seg > button'); if (!b) return;
    const group = b.parentElement;
    Array.from(group.children).forEach(x => { x.classList.toggle('on', x === b); x.setAttribute('aria-selected', x === b); });
    if (b.dataset.pane) {
      const scope = group.dataset.scope ? $(group.dataset.scope) : document;
      $$('[data-pane-id]', scope).forEach(p => p.hidden = p.dataset.paneId !== b.dataset.pane);
    }
  });

  /* ---------- 移动端外壳（body[data-shell=mobile]） ---------- */
  function mobileShell() {
    const b = document.body;
    const title = b.dataset.title || '';
    const back = b.dataset.back;
    // 仅嵌入原型总览时模拟系统状态栏，独立 H5 使用设备自身的状态栏。
    const status = window.self !== window.top ? `<div class="m-status" aria-hidden="true"><span>9:41</span><span class="flex gap-1.5 text-[13px]"><i class="fa-solid fa-signal"></i><i class="fa-solid fa-wifi"></i><i class="fa-solid fa-battery-full"></i></span></div>` : '';
    const nav = `<header class="m-nav">
      <div class="w-20 flex items-center gap-3 text-ink-900">${back ? `<a href="${back}" aria-label="返回" class="w-8 h-8 -ml-1 grid place-items-center"><i class="fa-solid fa-chevron-left"></i></a>` : `<a href="index.html" aria-label="返回角色选择" class="w-8 h-8 grid place-items-center"><i class="fa-solid fa-xmark"></i></a>`}</div>
      <h1 class="text-[17px] font-semibold truncate">${title}</h1>
      <div class="w-20 flex justify-end"><span class="h-8 px-2.5 rounded-full ring-1 ring-ink-100 flex items-center gap-2.5 text-ink-900 text-xs"><i class="fa-solid fa-ellipsis"></i><span class="w-px h-3.5 bg-ink-300"></span><i class="fa-regular fa-circle-dot"></i></span></div>
    </header>`;
    b.insertAdjacentHTML('afterbegin', status + nav);
    const tab = b.dataset.tabbar;
    if (tab) {
      const sets = {
        c: [['c-pay.html', 'fa-gas-pump', '加气'], ['c-order.html', 'fa-receipt', '订单'], ['c-mine.html', 'fa-user', '我的']],
        s: [['s-workbench.html', 'fa-house', '工作台'], ['s-scan.html', 'fa-qrcode', '扫码'], ['s-reconcile.html', 'fa-receipt', '班次记录']],
        m: [['m-stations.html', 'fa-boxes-stacked', '库存'], ['m-buyers.html', 'fa-users', '管理']]
      }[tab];
      const cur = location.pathname.split('/').pop();
      b.insertAdjacentHTML('beforeend', `<nav class="m-tabbar" aria-label="底部导航">${sets.map(([h, i, t]) => `<a href="${h}" class="${cur === h ? 'on' : ''}" ${cur === h ? 'aria-current="page"' : ''}><i class="fa-solid ${i}"></i>${t}</a>`).join('')}</nav>`);
    }
    if (!$('#__mask')) b.insertAdjacentHTML('beforeend', '<div id="__mask" class="sheet-mask"></div>');
  }

  /* ---------- 后台布局（body[data-shell=admin]） ---------- */
  const MENU = [
    ['经营', [['a-dashboard.html', 'fa-gauge-high', '工作台']]],
    ['采购与资源', [['a-stations.html', 'fa-gas-pump', '站点与枪号'], ['a-prepay.html', 'fa-boxes-stacked', '采购与库存'], ['a-price.html', 'fa-tags', '销售价格']]],
    ['销售', [['a-orders.html', 'fa-receipt', '销售订单'], ['a-marketing.html', 'fa-ticket', '优惠券']]],
    ['账务', [['a-finance.html', 'fa-scale-balanced', '采购付款 / 收款']]],
    ['系统', [['#', 'fa-user-shield', '人员权限'], ['#', 'fa-clock-rotate-left', '操作日志']]]
  ];
  function adminShell() {
    const b = document.body;
    const cur = location.pathname.split('/').pop();
    const main = $('#main');
    const side = `<aside class="w-[220px] shrink-0 bg-[#111C2E] text-white flex flex-col h-screen sticky top-0">
      <div class="h-14 flex items-center gap-2.5 px-5 border-b border-white/10">
        <span class="w-8 h-8 rounded-lg bg-gradient-to-br from-brand-400 to-gas-cng grid place-items-center"><i class="fa-solid fa-fire-flame-simple text-sm"></i></span>
        <div><div class="text-sm font-bold leading-4">批量采购管理系统</div><div class="text-[10px] text-slate-400">采购方管理后台</div></div>
      </div>
      <nav class="flex-1 overflow-y-auto no-scrollbar px-3 py-3 space-y-4" aria-label="主菜单">
        ${MENU.map(([g, items]) => `<div><div class="px-3 mb-1 text-[11px] text-slate-500">${g}</div>${items.map(([h, i, t]) => `<a class="side-link ${cur === h ? 'on' : ''}" href="${h}" ${h === '#' ? 'data-toast="原型未展开该页面"' : ''}><i class="fa-solid ${i}"></i>${t}</a>`).join('')}</div>`).join('')}
      </nav>
      <a href="index.html" class="side-link mx-3 mb-3"><i class="fa-solid fa-arrow-right-from-bracket"></i>返回角色选择</a>
    </aside>`;
    const crumb = b.dataset.crumb || '';
    const top = `<header class="h-14 bg-white border-b border-ink-100 flex items-center justify-between px-6 sticky top-0 z-20">
      <div class="text-sm text-ink-500">${crumb.split('/').map((c, i, a) => i === a.length - 1 ? `<span class="text-ink-900 font-semibold">${c}</span>` : `${c}<span class="mx-2 text-ink-300">/</span>`).join('')}</div>
      <div class="flex items-center gap-5 text-ink-700">
        <label class="relative hidden xl:block"><i class="fa-solid fa-magnifying-glass absolute left-3 top-1/2 -translate-y-1/2 text-ink-500 text-xs"></i><input class="field h-8 w-64 pl-8 text-xs" placeholder="搜索订单号 / 手机号 / 站点"></label>
        <button class="relative" data-toast="3 条待审批、2 条余额预警" aria-label="通知"><i class="fa-regular fa-bell"></i><span class="absolute -top-1.5 -right-2 bg-bad text-white text-[10px] rounded-full px-1 leading-4">5</span></button>
        <div class="flex items-center gap-2"><span class="w-8 h-8 rounded-full bg-brand-100 text-brand-600 grid place-items-center text-xs font-bold">运</span><div class="leading-4"><div class="text-[13px] font-semibold text-ink-900">王运营</div><div class="text-[11px] text-ink-500">运营管理员</div></div></div>
      </div>
    </header>`;
    const wrap = document.createElement('div');
    wrap.className = 'flex min-h-screen';
    wrap.innerHTML = side + `<div class="flex-1 min-w-0 flex flex-col">${top}</div>`;
    b.prepend(wrap);
    wrap.lastElementChild.appendChild(main);
    if (!$('#__mask')) b.insertAdjacentHTML('beforeend', '<div id="__mask" class="sheet-mask"></div>');
  }

  const shell = document.body.dataset.shell;
  if (shell === 'mobile') mobileShell();
  if (shell === 'admin') adminShell();

  /* ---------- ECharts 主题（dataviz 参考调色板：蓝 / 橙 / 青，固定顺序） ---------- */
  window.VIZ = {
    series: ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#6250d6', '#e34948'],
    grid: '#EEF1F5', axis: '#8A94A6', ink: '#17202E', ink2: '#475467'
  };
  window.makeChart = function (el, option) {
    if (!window.echarts || !el) return null;
    const c = echarts.init(el, null, { renderer: 'svg' });
    const base = {
      color: VIZ.series,
      textStyle: { fontFamily: '-apple-system,"PingFang SC","Microsoft YaHei",sans-serif', color: VIZ.ink2 },
      grid: { left: 8, right: 12, top: 36, bottom: 4, containLabel: true },
      tooltip: { trigger: 'axis', backgroundColor: '#fff', borderColor: '#E4E8EE', borderWidth: 1, textStyle: { color: VIZ.ink, fontSize: 12 }, axisPointer: { type: 'line', lineStyle: { color: '#C9D0DB', width: 1 } }, extraCssText: 'box-shadow:0 6px 18px rgba(16,24,40,.12);border-radius:8px;' },
      legend: { top: 0, right: 0, icon: 'roundRect', itemWidth: 10, itemHeight: 4, textStyle: { color: VIZ.ink2, fontSize: 12 } }
    };
    const axisCommon = { axisLine: { lineStyle: { color: '#E4E8EE' } }, axisTick: { show: false }, axisLabel: { color: VIZ.axis, fontSize: 11 }, splitLine: { lineStyle: { color: VIZ.grid, type: 'solid' } } };
    const opt = Object.assign({}, base, option);
    if (option.legend) opt.legend = Object.assign({}, base.legend, option.legend);
    ['xAxis', 'yAxis'].forEach(k => { if (opt[k]) opt[k] = [].concat(opt[k]).map(a => Object.assign({}, axisCommon, a)); });
    c.setOption(opt);
    window.addEventListener('resize', () => c.resize());
    return c;
  };
  /* 示意二维码（非真实可扫码，仅演示），seed 相同图案相同 */
  window.fakeQR = function (el, seed = 1, size = 25) {
    let s = seed * 9301 + 49297;
    const rnd = () => (s = (s * 9301 + 49297) % 233280) / 233280;
    const cell = 4, n = size, w = n * cell;
    let r = '';
    const finder = (x, y) => `<rect x="${x * cell}" y="${y * cell}" width="${7 * cell}" height="${7 * cell}" fill="#17202E"/><rect x="${(x + 1) * cell}" y="${(y + 1) * cell}" width="${5 * cell}" height="${5 * cell}" fill="#fff"/><rect x="${(x + 2) * cell}" y="${(y + 2) * cell}" width="${3 * cell}" height="${3 * cell}" fill="#17202E"/>`;
    const inF = (x, y) => (x < 8 && y < 8) || (x > n - 9 && y < 8) || (x < 8 && y > n - 9);
    for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) if (!inF(x, y) && rnd() > 0.52) r += `<rect x="${x * cell}" y="${y * cell}" width="${cell}" height="${cell}"/>`;
    el.innerHTML = `<svg viewBox="0 0 ${w} ${w}" class="w-full h-full" role="img" aria-label="示意二维码"><rect width="${w}" height="${w}" fill="#fff"/><g fill="#17202E">${r}</g>${finder(0, 0)}${finder(n - 7, 0)}${finder(0, n - 7)}</svg>`;
  };
  document.querySelectorAll('[data-qr]').forEach(el => fakeQR(el, Number(el.dataset.qr) || 1));

  window.fmt = (n, d = 0) => Number(n).toLocaleString('zh-CN', { minimumFractionDigits: d, maximumFractionDigits: d });
})();
