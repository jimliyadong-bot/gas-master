# Repository guide

## Boundaries and authoritative references

- `design/prototypes/` is the standalone, offline-capable HTML prototype, not the implementation frontend. Its local-storage tests are separate from backend tests. Entry points are `index.html` and `overview.html`; see its `README.md` for demo flows.
- `h5/` is the implemented Vue/Vant frontend with its own `package.json` and lockfile. It is not a root npm workspace.
- `RuoYi/` is a nested Git repository and Maven reactor. Run backend Git/build operations there. Business domain, services, and MyBatis mapper XML live in `ruoyi-gas`; HTTP controllers live in `ruoyi-admin/src/main/java/com/ruoyi/web/controller/{gas,h5}`. The management UI is server-rendered Thymeleaf under `ruoyi-admin/src/main/resources/templates/gas/`, not part of `h5/`. Business integration tests are in **`ruoyi-admin/src/test/java/com/ruoyi/gas`**, not `ruoyi-gas`.
- `docs/PRD.md` defines the current scope. Its §8 confirmed answers override conflicting earlier wording; `docs/Dev_Plan.md` §7 records implementation decisions and remaining questions. Old distribution-platform requirements are explicitly superseded: do not restore consumer wallets/top-ups, credit accounts, or B-end open-platform scope.
- Consult `docs/Dev_Plan.md` for schema and implementation phases, `docs/部署与测试手册_阶段1-5.md` for deployment/testing, and `design/specs/Design_Spec.md` for UI design. Do not silently resolve decisions still marked pending.

## Verification commands

Commands below are relative to the stated working directory. They were identified from source/configuration; this guide's creation did not execute them.

### Prototype — repository root

Uses Node's built-in test runner; no npm dependencies are needed for store tests.

```sh
node --test design/prototypes/tests/*.test.cjs
node --test design/prototypes/tests/owner-store.test.cjs
node --test --test-name-pattern="handover gates" design/prototypes/tests/gas-store.test.cjs
```

Pass explicit files or the glob rather than a directory. Tests cover persistence failures, corrupt saved data, duplicate operations, shift gates, and inventory isolation; preserve those guarantees when changing stores.

For browser verification, run `python -m http.server 8765` from `design/prototypes/` and open `http://localhost:8765/`.

### H5 — `h5/`

With Node.js 18+ (deployment guide §2), npm, and package dependencies available:

```sh
npm run build
npm run dev
```

`dev` uses port **5300** and proxies `/api` to **http://localhost:9090** (`vite.config.js`). Frontend serving alone does not supply the backend. The manifest defines only `dev`, `build`, and `preview`; there are no npm test, lint, or type-check scripts. `h5/dist/` is build output, not source.

### Backend — `RuoYi/`

Requires Maven 3.8+ and JDK 17+ (deployment guide §2; parent `pom.xml` targets Java 17).

**Database safety:** these are Spring integration tests, not isolated in-memory unit tests. They need a configured local MySQL 8.0+ schema. Inventory concurrency tests commit independent transactions and manually clean up fixtures; do not point them at production or shared valuable data. `mvn clean package` also invokes tests.

```sh
mvn clean package
mvn -pl ruoyi-admin -am -Dtest=GasInventoryServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl ruoyi-admin -am '-Dtest=GasInventoryServiceTest#testFifoCrossBatchConsume' -Dsurefire.failIfNoSpecifiedTests=false test
```

The second command focuses inventory tests while building required reactor modules; the third runs just the FIFO cross-batch test. Other test classes include `GasOrderServiceTest`, `GasStaffServiceTest`, and `GasH5AuthFlowTest`.

`RuoYi/bin/package.bat` uses `-Dmaven.test.skip=true`: it skips both test compilation and execution, so it is not a regression check.

Database setup and upgrades are under `RuoYi/sql/`. **`gas_schema.sql` drops business tables; do not rerun it on an existing database.** Follow the deployment guide §3 for initialization order versus existing-schema upgrades, and consult `docs/Dev_Plan.md` for schema design. These SQL files are not interchangeable seeds.

## Prototype assets and state constraints

- Pages load generated `design/prototypes/assets/tailwind.css`. Edit `assets/src/input.css`, the Tailwind configuration, or page/JS classes instead of hand-editing the generated CSS. From the repository root, rebuild with:

  ```sh
  npx tailwindcss@3 -c design/prototypes/assets/src/tailwind.config.js -i design/prototypes/assets/src/input.css -o design/prototypes/assets/tailwind.css --minify
  ```

  The configuration's content paths are root-relative. Preserve local vendor assets and offline operation; prototype payment and QR flows are demonstrations, not real integrations.
- `gas-store.test.cjs` and `owner-store.test.cjs` specify rollback/non-overwrite behavior for storage failures and corrupt data. Do not replace saved records as an error-recovery shortcut.
- Confirmed inventory rules are per-station isolation and FIFO by receipt; actual deduction uses staff-confirmed quantity. Reservation uses original amount divided by sale price, not discounted payment. See PRD §8 rather than copying older prototype options into implementation.

## Completion checks

For the changed boundary, report the exact verification command and result, or the missing prerequisite if it could not run. For backend tests, confirm that the selected JUnit tests actually executed, not just that packaging succeeded. A frontend build does not verify backend behavior, and prototype store tests do not verify MySQL transactions. For prototype utility-class changes, regenerate the shipped CSS and inspect the affected page through the static server.
