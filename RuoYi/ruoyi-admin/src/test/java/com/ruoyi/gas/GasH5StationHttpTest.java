package com.ruoyi.gas;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.time.Duration;
import java.util.*;
import javax.sql.DataSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.framework.shiro.service.SysPasswordService;
import com.ruoyi.gas.domain.GasBuyer;
import com.ruoyi.gas.domain.GasStation;
import com.ruoyi.gas.mapper.GasBuyerMapper;
import com.ruoyi.gas.mapper.GasStationMapper;

/**
 * 真HTTP/Tomcat + Shiro/H5 JWT/MVC + MySQL验收；仅微信渠道为mock。
 * 使用随机回环端口，不访问用户9090。凭据仅在测试内存，不输出到断言或日志。
 * 需要显式GAS_ALLOW_DB_TESTS=true及事先确认的本地ry测试库；按本轮ID清理。
 */
@EnabledIfEnvironmentVariable(named = "GAS_ALLOW_DB_TESTS", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "spring.quartz.auto-startup=false", "spring.quartz.job-store-type=memory",
        "gas.h5.mock-mode=true", "spring.datasource.druid.slave.enabled=false", "ruoyi.addressEnabled=false" })
class GasH5StationHttpTest
{
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource datasource;
    @Autowired private TransactionTemplate tx;
    @Autowired private GasBuyerMapper buyers;
    @Autowired private GasStationMapper stations;
    @Autowired private SysPasswordService passwords;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final List<Fixture> fixtures = new ArrayList<>();
    private final Set<Long> buyerIds = new LinkedHashSet<>();
    private final List<String> openids = new ArrayList<>();

    @BeforeEach void approvedTargetOnly() throws Exception
    {
        try (var connection = datasource.getConnection()) {
            URI uri = URI.create(connection.getMetaData().getURL().substring(5));
            assertTrue(Set.of("127.0.0.1", "localhost").contains(uri.getHost()), "only approved loopback DB");
            assertEquals("/ry", uri.getPath());
            assertEquals(3306, uri.getPort() < 0 ? 3306 : uri.getPort());
            assertEquals("ry", connection.getCatalog());
        }
    }

    @Test void oauthBindCreateReceiveReplayAndLedgerThroughRealHttp() throws Exception
    {
        Fixture f = fixture(null, 102);
        String token = bind(f);
        JsonNode profile = ok(get("/api/h5/c/me", token)).path("data");
        assertEquals("STATION_ADMIN", profile.path("role").asText());
        assertEquals(f.stationId, profile.path("stationId").asLong());
        assertEquals(0, count("gas_shift", f.stationId));
        Map<String, Object> input = batchInput("create-http-0001", "CNG", "100", "3.0000");
        input.put("stationId", 999999999L); input.put("buyerId", 999999999L);
        input.put("amount", "0.01"); input.put("createBy", "untrusted-client");
        JsonNode batch = ok(post("/api/h5/m/batches", token, input)).path("data");
        long batchId = batch.path("batchId").asLong();
        assertTrue(batchId > 0);
        assertEquals(f.stationId, batch.path("stationId").asLong());
        assertEquals(f.buyerId, batch.path("buyerId").asLong());
        assertEquals(f.userId + ":" + f.login, batch.path("createBy").asText());
        decimal("300", batch.path("amount"));
        assertEquals(batchId, ok(post("/api/h5/m/batches", token, input)).path("data").path("batchId").asLong());
        assertEquals(1, count("gas_purchase_batch", f.stationId));
        assertEquals(0, count("gas_inventory_pool", f.stationId));
        String path = "/api/h5/m/batches/" + batchId + "/receipts";
        ok(post(path, token, receiptInput("receive-http-0001", "60")));
        Map<String, Object> finalReceipt = receiptInput("receive-http-0002", "40");
        long receiptId = ok(post(path, token, finalReceipt)).path("data").path("receiptId").asLong();
        assertEquals(receiptId, ok(post(path, token, finalReceipt)).path("data").path("receiptId").asLong());
        JsonNode rejected = reject(post(path, token, receiptInput("receive-over-0001", "0.01")));
        assertAttemptRejected(rejected, "receive-over-0001");
        rejected = reject(post(path, token, receiptInput("receive-http-0002", "39")));
        assertAttemptRejected(rejected, "receive-http-0002");
        assertLedger(f, "CNG", "100", 2);
        long lngId = ok(post("/api/h5/m/batches", token, batchInput("create-lng-0001", "LNG", "200", "2")))
                .path("data").path("batchId").asLong();
        ok(post("/api/h5/m/batches/" + lngId + "/receipts", token, receiptInput("receive-lng-0001", "50")));
        assertLedger(f, "CNG", "100", 2);
        assertLedger(f, "LNG", "50", 1);
        assertEquals(2, ok(get("/api/h5/m/overview", token)).path("data").path("inventory").size());
        assertEquals(5, count("gas_station_write_request", f.stationId));
        assertEquals(0, count("gas_shift", f.stationId));
    }

    @Test void missingInvalidMemberStaffAndOtherStationCannotUseManagerApis() throws Exception
    {
        Fixture a = fixture(null, 102), b = fixture(a.buyerId, 102), staff = fixture(null, 103);
        String adminA = bind(a), adminB = bind(b), staffToken = bind(staff), member = login();
        long batchId = ok(post("/api/h5/m/batches", adminA, batchInput("guard-batch-0001", "CNG", "100", "3")))
                .path("data").path("batchId").asLong();
        String detail = "/api/h5/m/batches/" + batchId;
        List<String> reads = List.of("/api/h5/m/overview", "/api/h5/m/batches", detail, detail + "/receipts");
        for (String identity : Arrays.asList(null, "not-a-valid-jwt")) {
            for (String path : reads) assertEquals(401, send("GET", path, identity, null).statusCode());
            assertEquals(401, send("POST", "/api/h5/m/batches", identity, batchInput("no-token-batch", "CNG", "10", "3")).statusCode());
            assertEquals(401, send("POST", detail + "/receipts", identity, receiptInput("no-token-receipt", "10")).statusCode());
        }
        for (String identity : List.of(member, staffToken)) {
            for (String path : reads) reject(get(path, identity));
            reject(post("/api/h5/m/batches", identity, batchInput("wrong-role-batch", "CNG", "10", "3")));
            reject(post(detail + "/receipts", identity, receiptInput("wrong-role-receipt", "10")));
        }
        reject(get(detail, adminB)); reject(get(detail + "/receipts", adminB));
        reject(post(detail + "/receipts", adminB, receiptInput("wrong-station-receipt", "10")));
        assertEquals(0, ok(get("/api/h5/m/batches", adminB)).path("data").path("rows").size());
        assertEquals(0, count("gas_purchase_receipt", a.stationId));
        assertEquals(0, count("gas_station_write_request", b.stationId));
        assertEquals(0, count("gas_station_write_request", staff.stationId));
    }

    @Test void oldJwtCannotBypassLiveAccountRoleStationDepartmentOrBindingChanges() throws Exception
    {
        Fixture f = fixture(null, 102);
        String token = bind(f);
        jdbc.update("delete from sys_user_role where user_id=?", f.userId);
        assertOldIdentityRejected(f, token, "revoked-role-0001");
        jdbc.update("insert into sys_user_role(user_id,role_id) values(?,102)", f.userId);
        jdbc.update("update sys_user set status='1' where user_id=?", f.userId);
        assertOldIdentityRejected(f, token, "disabled-user-0001");
        jdbc.update("update sys_user set status='0' where user_id=?", f.userId);
        jdbc.update("update gas_station set status='1' where station_id=?", f.stationId);
        assertOldIdentityRejected(f, token, "disabled-station-0001");
        jdbc.update("update gas_station set status='0' where station_id=?", f.stationId);
        jdbc.update("update sys_user set dept_id=100 where user_id=?", f.userId);
        assertOldIdentityRejected(f, token, "changed-dept-0001");
        jdbc.update("update sys_user set dept_id=? where user_id=?", f.deptId, f.userId);
        jdbc.update("delete from gas_staff_binding where user_id=?", f.userId);
        assertOldIdentityRejected(f, token, "removed-binding-0001");
        assertEquals(0, count("gas_purchase_batch", f.stationId));
    }

    @Test void mvcRejectsInvalidFieldsAndPreservesAmountAndResponseContract() throws Exception
    {
        Fixture f = fixture(null, 102);
        String token = bind(f);
        List<Map<String, Object>> invalid = List.of(
                Map.of("qty", "0"), Map.of("qty", "-1"), Map.of("qty", "1.001"), Map.of("qty", "1000000000000.00"),
                Map.of("unitPrice", "0"), Map.of("unitPrice", "1.00001"), Map.of("gasType", "OTHER"),
                Map.of("remark", "x".repeat(501)), Map.of("requestId", "bad"),
                Map.of("qty", "0.01", "unitPrice", "0.0001"), Map.of("qty", "100000000000.00", "unitPrice", "1"));
        int i = 0;
        for (Map<String, Object> values : invalid) {
            Map<String, Object> input = batchInput("invalid-field-" + (++i), "CNG", "100", "3");
            input.putAll(values);
            JsonNode result = reject(post("/api/h5/m/batches", token, input));
            assertAttemptRejected(result, (String) input.get("requestId"));
        }
        for (String date : List.of("2026-02-30", "not-a-date")) {
            Map<String, Object> input = batchInput("invalid-date-0001", "CNG", "100", "3");
            input.put("purchaseDate", date);
            reject(post("/api/h5/m/batches", token, input));
        }
        assertEquals(0, count("gas_purchase_batch", f.stationId));
        assertEquals(0, count("gas_station_write_request", f.stationId));
        JsonNode batch = ok(post("/api/h5/m/batches", token, batchInput("half-up-batch-0001", "CNG", "0.03", "3.5000"))).path("data");
        decimal("0.11", batch.path("amount"));
        String receipts = "/api/h5/m/batches/" + batch.path("batchId").asLong() + "/receipts";
        JsonNode rejected = reject(post(receipts, token, receiptInput("invalid-qty-0001", "0.001")));
        assertAttemptRejected(rejected, "invalid-qty-0001");
        Map<String, Object> invalidTime = new LinkedHashMap<>(receiptInput("invalid-time-0001", "0.01"));
        invalidTime.put("receivedAt", "2026-02-30 10:00:00");
        reject(post(receipts, token, invalidTime));
        JsonNode receipt = ok(post(receipts, token, receiptInput("precise-time-0001", "0.03"))).path("data");
        assertEquals("2026-11-20 10:00:01", receipt.path("receivedAt").asText());
        assertLedger(f, "CNG", "0.03", 1);
        // R3大数量边界：真实DECIMAL剩0.01时拒绝0.02，接受恰好补满0.01。
        long largeId = ok(post("/api/h5/m/batches", token, batchInput("large-batch-0001", "LNG", "10000000000.05", "0.0001")))
                .path("data").path("batchId").asLong();
        String largeReceipts = "/api/h5/m/batches/" + largeId + "/receipts";
        ok(post(largeReceipts, token, receiptInput("large-first-0001", "10000000000.04")));
        assertAttemptRejected(reject(post(largeReceipts, token, receiptInput("large-over-0001", "0.02"))), "large-over-0001");
        ok(post(largeReceipts, token, receiptInput("large-exact-0001", "0.01")));
        assertLedger(f, "LNG", "10000000000.05", 2);
    }

    @Test void paginationFiltersAndSameBuyerStationsStayIsolated() throws Exception
    {
        Fixture a = fixture(null, 102), b = fixture(a.buyerId, 102);
        String token = bind(a), other = bind(b);
        long receiptBatch = 0;
        String batchNo = "";
        for (int i = 0; i < 12; i++) {
            JsonNode batch = ok(post("/api/h5/m/batches", token, batchInput("page-batch-" + i, i == 11 ? "LNG" : "CNG", "100", "3"))).path("data");
            if (i == 0) { receiptBatch = batch.path("batchId").asLong(); batchNo = batch.path("batchNo").asText(); }
        }
        ok(post("/api/h5/m/batches", other, batchInput("other-batch-0001", "CNG", "100", "3")));
        JsonNode first = ok(get("/api/h5/m/batches?page=1&size=10", token)).path("data");
        JsonNode second = ok(get("/api/h5/m/batches?page=2&size=10", token)).path("data");
        assertPages(first, second, "batchId", 12);
        assertEquals(11, ok(get("/api/h5/m/batches?gasType=CNG", token)).path("data").path("total").asInt());
        assertEquals(1, ok(get("/api/h5/m/batches?gasType=LNG", token)).path("data").path("total").asInt());
        assertEquals(1, ok(get("/api/h5/m/batches?batchNo=" + batchNo, token)).path("data").path("total").asInt());
        assertEquals(1, ok(get("/api/h5/m/batches", other)).path("data").path("total").asInt());
        for (int i = 0; i < 11; i++) ok(post("/api/h5/m/batches/" + receiptBatch + "/receipts", token, receiptInput("page-receipt-" + i, "1")));
        String path = "/api/h5/m/batches/" + receiptBatch + "/receipts";
        assertPages(ok(get(path + "?page=1&size=10", token)).path("data"), ok(get(path + "?page=2&size=10", token)).path("data"), "receiptId", 11);
        assertLedger(a, "CNG", "11", 11);
        assertEquals(0, count("gas_inventory_pool", b.stationId));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "PLAYWRIGHT_MODULE", matches = ".+")
    void realH5BrowserRetriesCommittedWritesAfterResponseLoss() throws Exception
    {
        Fixture f = fixture(null, 102);
        String token = bind(f);
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("h5/package.json"))) root = root.getParent();
        assertNotNull(root, "H5 project directory required");
        Path log = Files.createTempFile("gas-m1-live-browser-", ".log");
        Process child = new ProcessBuilder("node", "tests/station-live.acceptance.mjs")
                .directory(root.resolve("h5").toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            try (var input = child.getOutputStream()) {
                input.write(json.writeValueAsBytes(Map.of("backend", "http://127.0.0.1:" + port, "token", token)));
            }
            assertTrue(child.waitFor(120, TimeUnit.SECONDS), "live browser timeout");
            // 子进程只输出断言状态／截图路径；不输出stdin身份、API响应或浏览器存储。
            String output = Files.readString(log);
            System.out.print(output);
            assertEquals(0, child.exitValue(), "live browser failed; see local sanitized log");
            assertEquals(1, count("gas_purchase_batch", f.stationId));
            assertEquals(1, count("gas_purchase_receipt", f.stationId));
            assertEquals(2, count("gas_station_write_request", f.stationId));
            assertEquals(0, count("gas_shift", f.stationId));
            assertLedger(f, "CNG", "100", 1);
        } finally {
            if (child.isAlive()) {
                child.descendants().forEach(ProcessHandle::destroy);
                child.destroyForcibly();
                child.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    private void assertPages(JsonNode first, JsonNode second, String key, int total)
    {
        assertEquals(total, first.path("total").asInt()); assertEquals(total, second.path("total").asInt());
        assertEquals(10, first.path("rows").size()); assertEquals(total - 10, second.path("rows").size());
        Set<Long> ids = new HashSet<>();
        for (JsonNode page : List.of(first, second)) for (JsonNode row : page.path("rows")) assertTrue(ids.add(row.path(key).asLong()));
        assertEquals(total, ids.size());
    }
    private void assertOldIdentityRejected(Fixture f, String token, String requestId) throws Exception
    {
        reject(get("/api/h5/m/overview", token));
        assertAttemptRejected(reject(post("/api/h5/m/batches", token, batchInput(requestId, "CNG", "100", "3"))), requestId);
        assertEquals(0, count("gas_station_write_request", f.stationId));
    }
    private void assertAttemptRejected(JsonNode response, String requestId)
    {
        assertTrue(response.path("stationWriteAttemptRejected").asBoolean());
        assertEquals(requestId, response.path("requestId").asText());
    }
    private void decimal(String expected, JsonNode value) { assertEquals(0, new BigDecimal(expected).compareTo(value.decimalValue())); }
    private int count(String table, long stationId) { return jdbc.queryForObject("select count(*) from " + table + " where station_id=?", Integer.class, stationId); }
    private void assertLedger(Fixture f, String gasType, String quantity, int receipts)
    {
        Map<String, Object> pool = jdbc.queryForMap("select * from gas_inventory_pool where station_id=? and gas_type=?", f.stationId, gasType);
        assertEquals(0, new BigDecimal(quantity).compareTo((BigDecimal) pool.get("total_in")));
        assertEquals(0, ((BigDecimal) pool.get("reserved")).signum());
        assertEquals(0, ((BigDecimal) pool.get("consumed")).signum());
        assertEquals(0, ((BigDecimal) pool.get("adjusted")).signum());
        assertEquals(receipts, jdbc.queryForObject("select count(*) from gas_purchase_receipt where station_id=? and gas_type=?", Integer.class, f.stationId, gasType));
        assertEquals(receipts, jdbc.queryForObject("select count(*) from gas_inventory_ledger where pool_id=? and biz_type='IN'", Integer.class, pool.get("pool_id")));
        BigDecimal ledger = jdbc.queryForObject("select sum(qty) from gas_inventory_ledger where pool_id=? and biz_type='IN'", BigDecimal.class, pool.get("pool_id"));
        assertEquals(0, new BigDecimal(quantity).compareTo(ledger));
        assertEquals(0, jdbc.queryForObject("select count(*) from gas_purchase_receipt r left join gas_inventory_ledger l on l.receipt_id=r.receipt_id and l.biz_type='IN' where r.station_id=? and r.gas_type=? and (l.ledger_id is null or l.pool_id<>? or l.qty<>r.qty or l.operator<>r.create_by)", Integer.class, f.stationId, gasType, pool.get("pool_id")));
    }
    private Map<String, Object> batchInput(String id, String gasType, String quantity, String price)
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", id); body.put("gasType", gasType); body.put("qty", quantity);
        body.put("unitPrice", price); body.put("purchaseDate", "2026-11-20"); body.put("remark", "M1真实HTTP验收");
        return body;
    }
    private Map<String, Object> receiptInput(String id, String quantity)
    {
        return Map.of("requestId", id, "qty", quantity, "receivedAt", "2026-11-20 10:00:01", "remark", "M1真实HTTP验收");
    }
    private HttpResponse<String> send(String method, String path, String token, Object body) throws Exception
    {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15)).header("Accept", "application/json").header("X-Requested-With", "XMLHttpRequest");
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    private JsonNode get(String path, String token) throws Exception { return json.readTree(send("GET", path, token, null).body()); }
    private JsonNode post(String path, String token, Object body) throws Exception { return json.readTree(send("POST", path, token, body).body()); }
    private JsonNode ok(JsonNode value) { assertTrue(value.has("code"), "expected JSON envelope"); assertEquals(0, value.path("code").asInt()); return value; }
    private JsonNode reject(JsonNode value) { assertTrue(value.has("code"), "expected JSON rejection"); assertNotEquals(0, value.path("code").asInt()); assertTrue(value.path("data").isMissingNode() || value.path("data").isNull()); return value; }
    private String param(String location, String key)
    {
        String query = URI.create(location).getRawQuery();
        assertNotNull(query, "expected redirect query");
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair[0].equals(key)) return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
        }
        fail("expected redirect parameter: " + key); return null;
    }
    private String login() throws Exception
    {
        String code = "M1HTTP-" + IdUtils.fastSimpleUUID();
        openids.add("MOCK-" + code);
        HttpResponse<String> authorize = send("GET", "/api/h5/wx/authorize?redirect=/index", null, null);
        assertEquals(302, authorize.statusCode());
        String state = param(authorize.headers().firstValue("Location").orElseThrow(), "state");
        HttpResponse<String> callback = send("GET", "/api/h5/wx/callback?code=" + code + "&state=" + state, null, null);
        assertEquals(302, callback.statusCode());
        String loginCode = param(callback.headers().firstValue("Location").orElseThrow(), "h5_code");
        String token = ok(post("/api/h5/wx/exchange?code=" + loginCode, null, null)).path("data").asText();
        assertFalse(token.isBlank());
        reject(post("/api/h5/wx/exchange?code=" + loginCode, null, null));
        HttpResponse<String> reusedState = send("GET", "/api/h5/wx/callback?code=" + code + "&state=" + state, null, null);
        assertEquals("invalid_state", param(reusedState.headers().firstValue("Location").orElseThrow(), "oauth_error"));
        return token;
    }
    private String bind(Fixture f) throws Exception
    {
        String member = login();
        JsonNode bound = ok(post("/api/h5/staff/bind", member, Map.of("loginName", f.login, "password", f.password)));
        assertEquals(f.stationId, bound.path("stationId").asLong());
        // 等待本次异步登录审计落库，避免清理后任务又留下本轮日志。
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (jdbc.queryForObject("select count(*) from sys_logininfor where login_name=?", Integer.class, f.login) == 0 && System.nanoTime() < deadline) Thread.sleep(20);
        assertEquals(1, jdbc.queryForObject("select count(*) from sys_logininfor where login_name=?", Integer.class, f.login));
        return bound.path("token").asText();
    }
    private Fixture fixture(Long sharedBuyer, int role)
    {
        Fixture f = tx.execute(status -> {
            Fixture item = new Fixture();
            String suffix = IdUtils.fastSimpleUUID().substring(0, 12);
            if (sharedBuyer == null) {
                GasBuyer buyer = new GasBuyer(); buyer.setBuyerName("M1HTTP-" + suffix); buyer.setStatus("0");
                buyers.insertBuyer(buyer); item.buyerId = buyer.getBuyerId();
            } else item.buyerId = sharedBuyer;
            jdbc.update("insert into sys_dept(parent_id,ancestors,dept_name,order_num,status,del_flag) values(100,'0,100',?,99,'0','0')", "M1HTTP-" + suffix);
            item.deptId = jdbc.queryForObject("select last_insert_id()", Long.class);
            GasStation station = new GasStation(); station.setStationCode("M1HTTP-" + suffix); station.setStationName("M1HTTP测试站-" + suffix);
            station.setBuyerId(item.buyerId); station.setDeptId(item.deptId); station.setQrToken(IdUtils.fastSimpleUUID()); station.setStatus("0");
            stations.insertStation(station); item.stationId = station.getStationId();
            item.login = "m1http" + suffix; item.password = IdUtils.fastSimpleUUID().substring(0, 16);
            String salt = IdUtils.fastSimpleUUID().substring(0, 6);
            jdbc.update("insert into sys_user(login_name,user_name,password,salt,status,del_flag,dept_id) values(?,?,?,?,'0','0',?)", item.login, item.login, passwords.encryptPassword(item.login, item.password, salt), salt, item.deptId);
            item.userId = jdbc.queryForObject("select last_insert_id()", Long.class);
            jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)", item.userId, role);
            return item;
        });
        fixtures.add(f); buyerIds.add(f.buyerId); return f;
    }
    private static class Fixture { long stationId, buyerId, deptId, userId; String login, password; }

    @AfterEach void cleanup()
    {
        tx.executeWithoutResult(status -> {
            for (Fixture f : fixtures) {
                jdbc.update("delete from gas_station_write_request where station_id=?", f.stationId);
                jdbc.update("delete from gas_remind where station_id=?", f.stationId);
                jdbc.update("delete from gas_inventory_ledger where pool_id in (select pool_id from gas_inventory_pool where station_id=?)", f.stationId);
                jdbc.update("delete from gas_purchase_receipt where station_id=?", f.stationId);
                jdbc.update("delete from gas_purchase_batch where station_id=?", f.stationId);
                jdbc.update("delete from gas_inventory_pool where station_id=?", f.stationId);
                jdbc.update("delete from gas_staff_binding where user_id=?", f.userId);
                jdbc.update("delete from sys_user_role where user_id=?", f.userId);
                jdbc.update("delete from sys_logininfor where login_name=?", f.login);
                jdbc.update("delete from sys_user where user_id=?", f.userId);
                jdbc.update("delete from gas_station where station_id=?", f.stationId);
                jdbc.update("delete from sys_dept where dept_id=?", f.deptId);
            }
            for (long buyerId : buyerIds) jdbc.update("delete from gas_buyer where buyer_id=?", buyerId);
            for (String openid : openids) jdbc.update("delete from gas_member where openid=?", openid);
        });
    }
}
