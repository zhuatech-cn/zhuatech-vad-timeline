package cn.zhuatech.vadtimeline;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.ObjectMapper;

/**
 * Real HTTP, a fresh tenant per case, independent reviewers and immutable saved evidence.
 * 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:desk;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
      "app.admin-password=Review-desk-fixture-2026!"
    })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SuppressWarnings("unchecked")
class ReviewDeskTest {
  @LocalServerPort int port;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  final HttpClient net = HttpClient.newHttpClient();
  final String password = UUID.randomUUID().toString();
  final String hash = new BCryptPasswordEncoder().encode(password);
  final String metric = "missMs";
  String tenant, admin, worker, reviewer, viewer, foreign;
  Map<String, Object> batch;

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  record Response(int status, Map<String, Object> data) {}

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  Response call(String verb, String path, String token, Object body, String key) throws Exception {
    var req =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path))
            .timeout(Duration.ofSeconds(15));
    if (token != null) req.header("Authorization", "Bearer " + token);
    if (key != null) req.header("Idempotency-Key", key);
    if (body != null) req.header("Content-Type", "application/json");
    var r =
        net.send(
            req.method(
                    verb,
                    body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    return new Response(
        r.statusCode(), r.body().isBlank() ? Map.of() : json.readValue(r.body(), Map.class));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  String actor(String name, String role, String t) throws Exception {
    db.update(
        "INSERT INTO app_user(id,tenant,username,password_hash,role,active) VALUES(?,?,?,?,?,true)",
        UUID.randomUUID().toString(),
        t,
        name,
        hash,
        role);
    var r = call("POST", "/login", null, Map.of("username", name, "password", password), null);
    assertEquals(200, r.status());
    return (String) r.data().get("token");
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @BeforeEach
  void setup() throws Exception {
    tenant = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    String ft = "f" + tenant;
    db.update("INSERT INTO tenant_guard VALUES(?)", tenant);
    db.update("INSERT INTO tenant_guard VALUES(?)", ft);
    admin = actor("a" + tenant, "ADMIN", tenant);
    worker = actor("o" + tenant, "OPERATOR", tenant);
    reviewer = actor("r" + tenant, "REVIEWER", tenant);
    viewer = actor("v" + tenant, "VIEWER", tenant);
    foreign = actor("x" + tenant, "ADMIN", ft);
    var spec = json.readValue(getClass().getResourceAsStream("/static/catalog.json"), Map.class);
    batch = Map.of("title", "门槛验收", "settings", spec.get("settings"), "rows", spec.get("rows"));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  Map<String, Object> rule(String op, Object val) {
    return Map.of("metric", metric, "operator", op, "threshold", val);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  Map<String, Object> config(int version, List<?> rules) {
    return Map.of("expectedVersion", version, "reason", "企业验收口径", "rules", rules);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  String randomKey() {
    return UUID.randomUUID().toString();
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  Map<String, Object> run(Map<String, Object> input) throws Exception {
    var r = call("POST", "/runs", worker, input, randomKey());
    assertEquals(200, r.status(), r.data().toString());
    return r.data();
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void policyPublicationIsScopedVersionedImmutableAndIdempotent() throws Exception {
    var b = config(0, List.of(rule("GE", 0)));
    var key = randomKey();
    for (var who : List.of(viewer, worker, reviewer))
      assertEquals(403, call("POST", "/policy", who, b, key).status());
    assertEquals(401, call("GET", "/policy", null, null, null).status());
    var v = call("POST", "/policy", admin, b, key);
    assertEquals(200, v.status());
    assertEquals(1, v.data().get("version"));
    assertEquals(v.data(), call("POST", "/policy", admin, b, key).data());
    assertEquals(
        409, call("POST", "/policy", admin, config(0, List.of(rule("LE", 0))), key).status());
    assertEquals(409, call("POST", "/policy", admin, b, randomKey()).status());
    assertEquals(
        400,
        call("POST", "/policy", admin, config(1, List.of(rule("GE", 0.0))), randomKey()).status());
    assertEquals(
        0,
        ((Map<?, ?>) call("GET", "/policy", foreign, null, null).data().get("current"))
            .get("version"));
    assertEquals(200, call("POST", "/policy", admin, config(1, List.of()), randomKey()).status());
    var newer = call("GET", "/policy/history?limit=1", viewer, null, null).data();
    assertEquals(2, newer.get("nextBefore"));
    assertEquals(true, newer.get("hasMore"));
    var older = call("GET", "/policy/history?limit=1&before=2", viewer, null, null).data();
    assertEquals(1, ((List<Map<String, Object>>) older.get("items")).getFirst().get("version"));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM gate_policy WHERE tenant=?", Integer.class, "f" + tenant));
    assertEquals(
        2,
        db.queryForObject(
            "SELECT COUNT(*) FROM work_audit WHERE tenant=? AND action='PUBLISH_GATE_POLICY'",
            Integer.class,
            tenant));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void concurrentPolicyPublicationRequiresRefreshingLosingDraft() throws Exception {
    try (var pool = Executors.newFixedThreadPool(2)) {
      var one =
          pool.submit(
              () -> call("POST", "/policy", admin, config(0, List.of(rule("LE", 0))), randomKey()));
      var two =
          pool.submit(
              () -> call("POST", "/policy", admin, config(0, List.of(rule("GE", 0))), randomKey()));
      assertEquals(
          Set.of(200, 409), new HashSet<>(List.of(one.get().status(), two.get().status())));
    }
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM gate_policy WHERE tenant=?", Integer.class, tenant));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void snapshotNeverRejudgesAndExceptionNeedsIndependentReason() throws Exception {
    assertEquals("UNCONFIGURED", ((Map<?, ?>) run(batch).get("gate")).get("status"));
    assertEquals(
        200,
        call("POST", "/policy", admin, config(0, List.of(rule("LE", 0))), randomKey()).status());
    var first = run(batch);
    String path = "/runs/" + first.get("id");
    var saved = (Map<?, ?>) first.get("gate");
    assertEquals("FAIL", saved.get("status"), saved.toString());
    assertEquals(1, saved.get("version"));
    assertEquals(
        200,
        call("POST", "/policy", admin, config(1, List.of(rule("GE", 0))), randomKey()).status());
    assertEquals("PASS", ((Map<?, ?>) run(batch).get("gate")).get("status"));
    assertEquals(saved, call("GET", path, viewer, null, null).data().get("gate"));
    var approve = Map.of("version", 1, "state", "APPROVED", "note", "独立复核");
    assertEquals(400, call("POST", path + "/review", reviewer, approve, null).status());
    var exception =
        Map.of("version", 1, "state", "APPROVED", "note", "独立复核", "exceptionNote", "已人工核实差异");
    assertEquals(403, call("POST", path + "/review", worker, exception, null).status());
    var accepted = call("POST", path + "/review", reviewer, exception, null);
    assertEquals(200, accepted.status());
    assertEquals("APPROVED", accepted.data().get("stage"));
    assertEquals(saved, accepted.data().get("gate"));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM work_audit WHERE tenant=? AND subject=? AND"
                + " action='GATE_EXCEPTION'",
            Integer.class,
            tenant,
            first.get("id")));
    assertEquals(1, call("GET", "/runs?gate=FAIL", viewer, null, null).data().get("total"));
    assertEquals(0, call("GET", "/runs?gate=FAIL", foreign, null, null).data().get("total"));
    assertEquals(400, call("GET", "/runs?gate=anything", viewer, null, null).status());
    assertEquals(200, call("POST", "/policy", admin, config(2, List.of()), randomKey()).status());
    assertEquals("DISABLED", ((Map<?, ?>) run(batch).get("gate")).get("status"));
    assertEquals(saved, call("GET", path, viewer, null, null).data().get("gate"));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void comparisonSeparatesCohortAndMetricsAndNeverWritesHistory() throws Exception {
    var a = run(batch);
    var rows = new ArrayList<Map<String, Object>>();
    for (var row : (List<Map<String, Object>>) batch.get("rows"))
      rows.add(new LinkedHashMap<>(row));
    rows.getFirst().put("id", "new-isolated-id");
    var b = run(Map.of("title", "新一轮", "settings", batch.get("settings"), "rows", rows));
    String path = "/runs/" + b.get("id") + "/compare?baseline=" + a.get("id");
    var res = call("GET", path, viewer, null, null);
    assertEquals(200, res.status());
    var c = (Map<?, ?>) res.data().get("cohort");
    assertEquals(List.of("new-isolated-id"), c.get("added"));
    assertEquals(1, ((List<?>) c.get("removed")).size());
    assertEquals(false, c.get("sameInputs"));
    assertTrue(((List<?>) res.data().get("metrics")).size() > 0);
    assertEquals(404, call("GET", path, foreign, null, null).status());
    String ev =
        "/runs/"
            + b.get("id")
            + "/compare/evidence?baseline="
            + a.get("id")
            + "&section=main&filter=ALL&size=1";
    assertEquals("AVAILABLE", call("GET", ev, viewer, null, null).data().get("availability"));
    assertEquals(404, call("GET", ev, foreign, null, null).status());
    assertEquals(401, call("GET", ev, null, null, null).status());
    assertEquals(
        404,
        call(
                "GET",
                "/runs/" + b.get("id") + "/compare?baseline=" + UUID.randomUUID(),
                viewer,
                null,
                null)
            .status());
    assertEquals(
        400,
        call("GET", "/runs/" + a.get("id") + "/compare?baseline=" + a.get("id"), viewer, null, null)
            .status());
    assertEquals(
        a.get("history"),
        call("GET", "/runs/" + a.get("id"), viewer, null, null).data().get("history"));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void rulesRejectUnknownUnboundedDuplicateOrImpossibleConditions() throws Exception {
    for (Object rules :
        List.of(
            List.of(rule("EQ", 0)),
            List.of(rule("GE", -1)),
            List.of(rule("GE", 1), rule("GE", 1)),
            List.of(rule("GE", 1), rule("LE", 0)),
            List.of(rule("LE", "1e10000")),
            List.of(Map.of("metric", "not-a-metric", "operator", "LE", "threshold", 1)))) {
      assertEquals(
          400,
          call(
                  "POST",
                  "/policy",
                  admin,
                  Map.of("expectedVersion", 0, "reason", "校验", "rules", rules),
                  randomKey())
              .status(),
          rules.toString());
    }
    var r = List.of(new GateRules.Rule(metric, "GE", BigDecimal.ONE));
    var loaded = GateRules.normalize(r);
    assertEquals("UNKNOWN", GateRules.evaluate(3, loaded, Map.of()).get("status"));
    assertEquals("UNCONFIGURED", GateRules.evaluate(0, List.of(), Map.of()).get("status"));
    assertEquals("DISABLED", GateRules.evaluate(2, List.of(), Map.of()).get("status"));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void equivalentJsonSerializationReplaysButDifferentContentDoesNot() throws Exception {
    String key = randomKey();
    var first = call("POST", "/runs", worker, batch, key);
    assertEquals(200, first.status());
    var rows = new ArrayList<Map<String, Object>>();
    for (var item : (List<Map<String, Object>>) batch.get("rows")) {
      var row = new LinkedHashMap<String, Object>();
      for (var field : new ArrayList<>(item.keySet()).reversed()) {
        var value = item.get(field);
        row.put(field, value instanceof Integer n ? n.doubleValue() : value);
      }
      rows.add(row);
    }
    var changed = new LinkedHashMap<>(batch);
    changed.put("rows", rows);
    var replay = call("POST", "/runs", worker, changed, key);
    assertEquals(200, replay.status());
    assertEquals(first.data().get("id"), replay.data().get("id"));
    var altered = new LinkedHashMap<>(changed);
    altered.put("title", "另一张业务单");
    assertEquals(409, call("POST", "/runs", worker, altered, key).status());
    assertEquals(
        1,
        db.queryForObject("SELECT COUNT(*) FROM work_run WHERE tenant=?", Integer.class, tenant));
    assertEquals(first.data().get("history"), replay.data().get("history"));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void queuedWriterRevalidatesAccountAfterAnAdminCommitsDeactivation() throws Exception {
    var pool = Executors.newSingleThreadExecutor();
    try (var c = db.getDataSource().getConnection()) {
      c.setAutoCommit(false);
      try (var st =
          c.prepareStatement("SELECT tenant FROM tenant_guard WHERE tenant=? FOR UPDATE")) {
        st.setString(1, tenant);
        try (var rows = st.executeQuery()) {
          assertTrue(rows.next());
        }
      }
      var pending = pool.submit(() -> call("POST", "/runs", worker, batch, randomKey()));
      long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
      boolean waiting = false;
      while (System.nanoTime() < until) {
        if (db.queryForObject(
                "SELECT COUNT(*) FROM information_schema.sessions WHERE blocker_id IS NOT NULL AND"
                    + " UPPER(executing_statement) LIKE '%TENANT_GUARD%'",
                Integer.class)
            > 0) {
          waiting = true;
          break;
        }
        Thread.sleep(15);
      }
      assertTrue(waiting, "an authenticated write should be waiting for this tenant's lock");
      try (var revoke =
          c.prepareStatement("UPDATE app_user SET active=false WHERE tenant=? AND username=?")) {
        revoke.setString(1, tenant);
        revoke.setString(2, "o" + tenant);
        assertEquals(1, revoke.executeUpdate());
      }
      c.commit();
      assertEquals(401, pending.get(8, TimeUnit.SECONDS).status());
      assertEquals(
          0,
          db.queryForObject("SELECT COUNT(*) FROM work_run WHERE tenant=?", Integer.class, tenant));
    } finally {
      pool.shutdownNow();
    }
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void auditFiltersCombineAndKeepCursorBoundariesWithinEnterprise() throws Exception {
    String id = (String) run(batch).get("id");
    String who = "a" + tenant;
    for (int i = 0; i < 3; i++)
      db.update(
          "INSERT INTO work_audit(tenant,actor,subject,action,note) VALUES(?,?,?,?,?)",
          tenant,
          who,
          "export:" + id,
          "EXPORT_EVIDENCE",
          "fixture " + i);
    db.update(
        "INSERT INTO work_audit(tenant,actor,subject,action,note) VALUES(?,?,?,?,?)",
        "f" + tenant,
        who,
        "export:" + id,
        "EXPORT_EVIDENCE",
        "other tenant");
    String path = "/audit/page?limit=2&runId=" + id + "&actor=" + who + "&action=EXPORT_EVIDENCE";
    var first = call("GET", path, admin, null, null);
    assertEquals(200, first.status());
    assertEquals(true, first.data().get("hasMore"));
    var top = (List<Map<String, Object>>) first.data().get("items");
    assertEquals(2, top.size());
    assertEquals("fixture 2", top.getFirst().get("note"));
    var second =
        call("GET", path + "&before=" + first.data().get("nextBefore"), admin, null, null).data();
    assertEquals(false, second.get("hasMore"));
    var tail = (List<Map<String, Object>>) second.get("items");
    assertEquals(1, tail.size());
    assertEquals("fixture 0", tail.getFirst().get("note"));
    assertTrue(
        ((List<?>) call("GET", path + "&before=1", admin, null, null).data().get("items"))
            .isEmpty());
    assertEquals(403, call("GET", path, viewer, null, null).status());
    assertEquals(400, call("GET", "/audit/page?runId=not-a-uuid", admin, null, null).status());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void previewIsAnAdminReadOnlyBoundedIndependentProjection() throws Exception {
    var saved = run(batch);
    var rules = List.of(rule("LE", 0));
    var request = Map.of("expectedVersion", 0, "rules", rules, "limit", 1);
    for (var who : List.of(worker, viewer, reviewer))
      assertEquals(403, call("POST", "/policy/preview", who, request, null).status());
    assertEquals(401, call("POST", "/policy/preview", null, request, null).status());
    assertEquals(
        List.of(), call("POST", "/policy/preview", foreign, request, null).data().get("rows"));
    var before = call("GET", "/runs/" + saved.get("id"), viewer, null, null).data();
    var preview = call("POST", "/policy/preview", admin, request, null);
    assertEquals(200, preview.status());
    assertEquals(true, preview.data().get("previewOnly"));
    var row = ((List<Map<String, Object>>) preview.data().get("rows")).getFirst();
    assertEquals(saved.get("id"), row.get("id"));
    assertEquals("FAIL", row.get("projectedStatus"));
    assertEquals("UNCONFIGURED", row.get("storedStatus"));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM gate_policy WHERE tenant=?", Integer.class, tenant));
    assertEquals(before, call("GET", "/runs/" + saved.get("id"), viewer, null, null).data());
    assertEquals(
        400,
        call(
                "POST",
                "/policy/preview",
                admin,
                Map.of("expectedVersion", 0, "rules", rules, "limit", 11),
                null)
            .status());
    assertEquals(200, call("POST", "/policy", admin, config(0, rules), randomKey()).status());
    assertEquals(409, call("POST", "/policy/preview", admin, request, null).status());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void evidencePacketPinsRevisionAndDoesNotModifyReviewHistory() throws Exception {
    var record = run(batch);
    String id = (String) record.get("id"), path = "/runs/" + id + "/export";
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path))
            .header("Authorization", "Bearer " + viewer)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"version\":1}"))
            .build();
    var downloaded = net.send(request, HttpResponse.BodyHandlers.ofByteArray());
    assertEquals(200, downloaded.statusCode());
    assertEquals("application/zip", downloaded.headers().firstValue("Content-Type").orElseThrow());
    var hex =
        HexFormat.of()
            .formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(downloaded.body()));
    assertEquals(hex, downloaded.headers().firstValue("X-Evidence-SHA256").orElseThrow());
    var parts = new LinkedHashMap<String, byte[]>();
    try (var stream =
        new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(downloaded.body()))) {
      for (var entry = stream.getNextEntry(); entry != null; entry = stream.getNextEntry())
        assertNull(parts.put(entry.getName(), stream.readAllBytes()));
    }
    assertEquals(
        Set.of("input.json", "review.json", "result.json", "manifest.json"), parts.keySet());
    var manifest = json.readValue(parts.get("manifest.json"), Map.class);
    assertEquals(EvidenceArchive.PROJECT, manifest.get("project"));
    assertEquals(id, manifest.get("runId"));
    for (var entry : (List<Map<String, Object>>) manifest.get("entries")) {
      var bytes = parts.get(entry.get("name"));
      assertEquals(bytes.length, entry.get("bytes"));
      assertEquals(
          entry.get("sha256"),
          HexFormat.of()
              .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
    }
    var review = json.readValue(parts.get("review.json"), Map.class);
    assertEquals(record.get("history"), review.get("history"));
    assertEquals(record.get("result"), json.readValue(parts.get("result.json"), Map.class));
    assertEquals(record.get("input"), json.readValue(parts.get("input.json"), Map.class));
    assertArrayEquals(
        downloaded.body(), net.send(request, HttpResponse.BodyHandlers.ofByteArray()).body());
    assertEquals(
        record.get("history"),
        call("GET", "/runs/" + id, viewer, null, null).data().get("history"));
    assertEquals(
        2,
        db.queryForObject(
            "SELECT COUNT(*) FROM work_audit WHERE tenant=? AND subject=? AND"
                + " action='EXPORT_EVIDENCE'",
            Integer.class,
            tenant,
            "export:" + id));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void evidenceExportRejectsUnknownForeignAndStaleSnapshots() throws Exception {
    var record = run(batch);
    String id = (String) record.get("id"), path = "/runs/" + id + "/export";
    var v = Map.of("version", 1);
    assertEquals(401, call("POST", path, null, v, null).status());
    assertEquals(404, call("POST", path, foreign, v, null).status());
    assertEquals(
        404, call("POST", "/runs/" + UUID.randomUUID() + "/export", viewer, v, null).status());
    assertEquals(
        200,
        call("POST", "/runs/" + id + "/withdraw", worker, Map.of("version", 1, "note", "重核"), null)
            .status());
    assertEquals(409, call("POST", path, viewer, v, null).status());
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM work_audit WHERE tenant=? AND action='EXPORT_EVIDENCE'",
            Integer.class,
            tenant));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void oldSnapshotsMissingMetricsAndNullsNeverBecomeZero() {
    var sample = new Api.Batch("例", Map.of(), List.of(Map.of("id", "A")));
    var oldMetrics = new LinkedHashMap<String, Object>();
    oldMetrics.put("stat", null);
    oldMetrics.put("n", 2.01);
    var old = new BatchComparison.Saved("a", "甲", "READY", sample, Map.of("metrics", oldMetrics));
    var now =
        new BatchComparison.Saved(
            "b",
            "乙",
            "READY",
            sample,
            Map.of(
                "metrics",
                Map.of("stat", 0, "n", 2.02),
                "inspection",
                Map.of("metrics", Map.of("k", 7))));
    var report = BatchComparison.compare(old, now);
    var rows = (List<Map<String, Object>>) report.get("metrics");
    var only =
        rows.stream().filter(v -> v.get("metric").equals("inspection.k")).findFirst().orElseThrow();
    assertEquals("ONLY_TARGET", only.get("state"));
    assertNull(only.get("delta"));
    assertTrue(
        rows.stream()
            .anyMatch(
                v ->
                    v.get("metric").equals("stat")
                        && v.get("state").equals("UNAVAILABLE")
                        && v.get("delta") == null));
    assertTrue(
        rows.stream()
            .anyMatch(
                v -> v.get("metric").equals("n") && new BigDecimal("0.01").equals(v.get("delta"))));
    assertEquals(true, ((Map<?, ?>) report.get("cohort")).get("sameInputs"));
  }
}
