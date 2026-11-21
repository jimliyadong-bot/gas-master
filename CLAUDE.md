# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

A static, dependency-free interactive prototype for a gas-station bulk-purchase and sales-record system (加气站批量采购与销售记录系统). There is no backend, no framework, no bundler, and no real payment integration — 拉卡拉 references are simulated. All records live in `localStorage`; QR codes are decorative.

The repo is a requirements/design deliverable, not production software. UI copy and documents are in Chinese. Business logic exists only to make the prototype believable and testable.

**Authoritative requirements:** `docs/PRD.md` v1.0. It explicitly replaces the older v0.9 "distribution platform" scope. Do not reintroduce the old scope (B-end open API, consumer e-wallets/top-up, credit accounts, multi-tenant platform). Material under `doc/` (PDF/PNG) is reference material from that earlier phase — historical context, not current requirements. PRD §8 now records the business answers (confirmed 2026-11-16); those override "可配置/待确认" wording elsewhere in the PRD and the prototype (e.g. the prototype still offers a shared-inventory mode — the confirmed rule is per-station inventory with FIFO by receipt). Anything still marked 待定 there must not be decided silently.

**Real implementation (in progress):** `docs/Dev_Plan.md` is the implementation plan: architecture, phases, schema design, UI, and the decision log in §7. `RuoYi/` is the RuoYi 4.8.3 monolith (Spring Boot 4 + Shiro + Thymeleaf + MyBatis + MySQL 8, JDK 17; its own nested git repo) used as the admin backend. Business code goes in a new `ruoyi-gas` module; business tables, dictionaries and menu folders are in `RuoYi/sql/gas_schema.sql` (run after `ry_20260319.sql` and `quartz.sql`). The 公众号 H5 is planned as a separate `h5/` project (Vue 3 + Vant 4). The `design/prototypes/` stores remain the behavioural reference for state rules.

## Source-of-truth files

| File | Purpose |
|---|---|
| `docs/PRD.md` | Product positioning, roles, rules, page flows, open questions |
| `design/Flowchart.md` | Mermaid business flows (purchase/inventory, active & proxy ordering, shifts, owner records) |
| `design/specs/Design_Spec.md` | Color tokens, components, mobile/admin layout, copy principles |
| `design/prototypes/` | The runnable prototype (`index.html` entry, `overview.html` project summary/embedded previews) |
| `design/prototypes/assets/js/gas-store.js` | Order/shift state machine (consumer + staff) |
| `design/prototypes/assets/js/owner-store.js` | Procurement/inventory state (buyer-side / admin) |
| `design/prototypes/tests/` | Node `node:test` suites for both stores |

## Commands

Serve the prototype (run from `design/prototypes/`; a static server is required because pages are cross-linked and `overview.html` embeds them in iframes):

```
python -m http.server 8765
# then open http://localhost:8765/
```

Run all store tests (from repo root; Node ≥ 22, no dependencies):

```
node --test design/prototypes/tests/*.test.cjs
```

Run one test by name:

```
node --test --test-name-pattern="handover gates" design/prototypes/tests/gas-store.test.cjs
```

Note: `node --test design/prototypes/tests/` (directory argument) fails on this Node 24 setup — pass the glob or explicit files.

Rebuild the checked-in Tailwind output after adding/changing utility classes in any `*.html` or in `assets/src/input.css` (run from repo root):

```
npx tailwindcss@3 -c design/prototypes/assets/src/tailwind.config.js -i design/prototypes/assets/src/input.css -o design/prototypes/assets/tailwind.css --minify
```

Pages load only the prebuilt `assets/tailwind.css`; never switch to a Tailwind CDN or start a second build pipeline.

## Architecture

**Static multi-page app.** Each role has its own HTML pages with page-specific inline `<script>`; shared behavior comes from `assets/js/common.js`. There is no router — navigation is plain links/`location.href`.

**Shells are injected at load time** by `common.js`, driven by `body[data-shell]`:
- `data-shell="mobile"` injects the phone nav/tabbar shell using `data-title`, `data-back`, `data-tabbar="c|s|m"`. The three tab sets are hardcoded in `mobileShell()`.
- `data-shell="admin"` injects the 220px sidebar + breadcrumb from `MENU` and moves the existing `<main id="main">` into the layout.
- A new page therefore needs a matching entry in `MENU` or the tab sets in `common.js` (and, for overview/preview pages, usually an entry in `overview.html`).

**Declarative interaction contract** (delegated listeners in `common.js`): `data-open="#id"` / `data-close` for sheets and drawers, `data-toast="..."`, `.seg` / `[data-tabs]` toggling with `data-pane` + `data-pane-id`, `data-qr` for placeholder QR SVGs. Globals provided: `$`, `$$`, `toast`, `openLayer`, `closeLayers`, `VIZ` (fixed chart palette), `makeChart` (ECharts registered with the shared theme), `fakeQR`, `fmt`.

**State lives in exactly two stores**, each a browser IIFE exposing one global:
- `GasStore` (`localStorage` key `gasPrototypeData`) — orders and staff shifts for one demo station (`gaoxin`, guns 1–4 CNG, 5–6 LNG). Order lifecycle: `waiting_driver → paid → completed`, plus `rejected`/`refunded`/`closed`. `createProxy` and `redeem` require an active shift; `totalsFor` derives shift totals from completed orders; legacy localStorage keys are migrated once in its `read()`.
- `OwnerStore` (`localStorage` key `gasOwnerProcurement`) — buyers, procurement batches, adjustments, outflows. `inventory()` derives balances from received batches only, isolated by buyer + gas + batch; `confirmReceipt`, `adjust`, `assignLegacy` require an "expected" copy of the record to detect concurrent edits.

**Persistence pattern to preserve.** Every mutation reads the full snapshot, validates, mutates in memory, then writes once. Storage failure must leave prior data untouched and throw a user-facing error; corrupt JSON must throw "未覆盖原有数据" without overwriting. Business handlers are wrapped in `GasStore.run()` (catches, toasts, returns `null`) or explicit `try/catch` for OwnerStore. Do not add partial writes or direct field mutation of saved data. `gas-store.test.cjs` explicitly tests rollback on storage failure, duplicate redemption/payment, shift changes, and legacy migration.

**Cross-page sync** uses `window` `storage` events plus `pageshow` to re-read and re-render; pages embedded in `overview.html` are iframes sized 390×844 (scaled `.65`) and 1440×900, so keep pages independent and re-entrant.

**Domain invariants:** CNG is measured in m³ and LNG in kg — never sum across gas types. Estimated volume is `amount ÷ price` and must always be labelled "仅供参考", never treated as measured. Money uses ¥, two decimals, tabular numerals; tables right-align amounts. Procurement/inventory and orders are scoped per station, and the staff shift is the permission gate for redemption and proxy ordering.

## Working conventions

- Roles: 消费者/司机 (`c-*`), 加气员 (`s-*`), 管理员 (`m-*`), 采购方管理后台 (`a-*`). `m-dashboard.html` and `m-funds.html` are redirect stubs.
- Before writing new requirements documents or prototype flows, confirm the open questions/scope with the user rather than inventing rules — this is a standing instruction for this project.
- Keep the prototype offline-capable: vendor assets are local (`assets/vendor/`), and no page should depend on network access.
