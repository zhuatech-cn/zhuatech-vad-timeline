/* 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.vadtimeline;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:acceptance;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
      "app.admin-password=Integration-test-only-2026!"
    })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SuppressWarnings("unchecked")
/** 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
class WorkflowTest {
  @LocalServerPort int port;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  final HttpClient http = HttpClient.newHttpClient();
  String admin, operator, viewer, reviewer, other;
  Map<String, Object> batch;

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  record Reply(int code, Map<String, Object> value) {}

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  Reply req(String method, String path, String token, Object body, String key) throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path))
            .timeout(java.time.Duration.ofSeconds(12));
    if (token != null) b.header("Authorization", "Bearer " + token);
    if (key != null) b.header("Idempotency-Key", key);
    if (body != null) b.header("Content-Type", "application/json");
    var r =
        http.send(
            b.method(
                    method,
                    body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    var data = r.body().isBlank() ? Map.of() : json.readValue(r.body(), Object.class);
    return new Reply(r.statusCode(), data instanceof Map ? (Map) data : Map.of("items", data));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  String login(String name) throws Exception {
    return (String)
        req(
                "POST",
                "/login",
                null,
                Map.of("username", name, "password", "Integration-test-only-2026!"),
                null)
            .value()
            .get("token");
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private Map<String, Object> correctedInput() {
    var b = new LinkedHashMap<String, Object>(batch);
    var rows = new ArrayList<Map<String, Object>>();
    for (var row : (List<Map<String, Object>>) batch.get("rows"))
      rows.add(new LinkedHashMap<>(row));
    rows.get(1).put("beginMs", 30);
    b.put("rows", rows);
    return b;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private String startForCorrection(String state) throws Exception {
    String key = UUID.randomUUID().toString();
    var original = req("POST", "/runs", operator, batch, key);
    assertEquals(200, original.code());
    String path = "/runs/" + original.value().get("id");
    if ("WITHDRAWN".equals(state))
      assertEquals(
          200,
          req("POST", path + "/withdraw", operator, Map.of("version", 1, "note", "原数据需要核对"), null)
              .code());
    else if (!"READY".equals(state))
      assertEquals(
          200,
          req(
                  "POST",
                  path + "/review",
                  reviewer,
                  Map.of("version", 1, "state", state, "note", "独立复核"),
                  null)
              .code());
    return path;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void correctionLinksImmutableSnapshotsAndRequiresIndependentReview() throws Exception {
    String path = startForCorrection("REJECTED"), url = path + "/corrections";
    var old = req("GET", path, viewer, null, null).value();
    var b = Map.of("version", 2, "note", "校对录音起点", "batch", correctedInput());
    String key = "corr-valid-01";
    var r = req("POST", url, operator, b, key);
    assertEquals(200, r.code(), r.value().toString());
    var child = r.value();
    assertEquals("READY", child.get("stage"));
    assertEquals(1, child.get("revision"));
    var link = (Map<?, ?>) child.get("correction");
    assertEquals(old.get("id"), link.get("source_id"));
    assertEquals(2, link.get("source_revision"));
    var changes = (List<Map<String, Object>>) link.get("changes");
    assertEquals(1, changes.size());
    var change = changes.getFirst();
    assertEquals("EDIT", change.get("kind"));
    assertEquals("beginMs", change.get("field"));
    assertEquals("B", change.get("id"));
    assertNotEquals(change.get("before"), change.get("after"));
    var now = req("GET", path, viewer, null, null).value();
    assertEquals(((Number) old.get("revision")).intValue() + 1, now.get("revision"));
    for (String k : List.of("input", "result", "stage")) assertEquals(old.get(k), now.get(k));
    assertEquals(child.get("id"), now.get("correctedBy"));
    assertEquals(child.get("id"), req("POST", url, operator, b, key).value().get("id"));
    assertEquals(
        409,
        req(
                "POST",
                url,
                operator,
                Map.of("version", 2, "note", "其他原因", "batch", correctedInput()),
                key)
            .code());
    assertEquals(409, req("POST", url, operator, b, key + "B").code());
    var decision = Map.of("version", 1, "state", "APPROVED", "note", "业务差异已核实");
    String childPath = "/runs/" + child.get("id");
    assertEquals(403, req("POST", childPath + "/review", operator, decision, null).code());
    assertEquals(200, req("POST", childPath + "/review", reviewer, decision, null).code());
    assertEquals(404, req("GET", childPath, other, null, null).code());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void correctionRejectsWrongRoleStateStaleVersionAndNoChangeWithoutConsumingOriginal()
      throws Exception {
    String path = startForCorrection("WITHDRAWN"), url = path + "/corrections";
    String key = "corr-guard-01";
    var body = Map.of("version", 2, "note", "修正", "batch", correctedInput());
    assertEquals(401, req("POST", url, null, body, key).code());
    assertEquals(403, req("POST", url, viewer, body, key).code());
    assertEquals(403, req("POST", url, admin, body, key).code());
    assertEquals(404, req("POST", url, other, body, key).code());
    for (String state : List.of("READY", "APPROVED"))
      assertEquals(
          409, req("POST", startForCorrection(state) + "/corrections", operator, body, key).code());
    assertEquals(
        409,
        req(
                "POST",
                url,
                operator,
                Map.of("version", 1, "note", "原因", "batch", correctedInput()),
                key)
            .code());
    assertEquals(
        400,
        req(
                "POST",
                url,
                operator,
                Map.of("version", 2, "note", " ", "batch", correctedInput()),
                key)
            .code());
    var titleOnly = new LinkedHashMap<>(batch);
    titleOnly.put("title", "仅更名");
    assertEquals(
        400,
        req("POST", url, operator, Map.of("version", 2, "note", "仅更名", "batch", titleOnly), key)
            .code());
    var invalid = new LinkedHashMap<>(batch);
    invalid.put("rows", List.of(Map.of("id", "bad")));
    assertEquals(
        400,
        req("POST", url, operator, Map.of("version", 2, "note", "错误数据", "batch", invalid), key)
            .code());
    assertFalse(req("GET", path, viewer, null, null).value().containsKey("correctedBy"));
    assertEquals(200, req("POST", url, operator, body, key).code());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void concurrentCorrectionsOnlyOneWinsAndNextRoundCanProceed() throws Exception {
    String path = startForCorrection("WITHDRAWN"), url = path + "/corrections";
    var body = Map.of("version", 2, "note", "并发修正", "batch", correctedInput());
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a = pool.submit(() -> req("POST", url, operator, body, "race-correct-1"));
      var b = pool.submit(() -> req("POST", url, operator, body, "race-correct-2"));
      assertEquals(Set.of(200, 409), new HashSet<>(List.of(a.get().code(), b.get().code())));
    }
    String next = "/runs/" + req("GET", path, viewer, null, null).value().get("correctedBy");
    assertEquals(
        200,
        req(
                "POST",
                next + "/review",
                reviewer,
                Map.of("version", 1, "state", "REJECTED", "note", "补证"),
                null)
            .code());
    var last =
        req(
            "POST",
            next + "/corrections",
            operator,
            Map.of("version", 2, "note", "恢复已核实数据", "batch", batch),
            "race-correct-3");
    assertEquals(200, last.code());
    var middle = req("GET", next, viewer, null, null).value();
    assertTrue(middle.containsKey("correction"));
    assertEquals(last.value().get("id"), middle.get("correctedBy"));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void concurrentIdenticalCorrectionAdvancesParentRevisionOnceAndInvalidatesStaleExport()
      throws Exception {
    String path = startForCorrection("WITHDRAWN"),
        url = path + "/corrections",
        key = UUID.randomUUID().toString();
    var before = req("GET", path, viewer, null, null).value();
    var body = Map.of("version", 2, "note", "并行提交修订", "batch", correctedInput());
    try (var pool = Executors.newFixedThreadPool(2)) {
      var ready = new CountDownLatch(2);
      Callable<Reply> submit =
          () -> {
            ready.countDown();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            return req("POST", url, operator, body, key);
          };
      var a = pool.submit(submit);
      var b = pool.submit(submit);
      var one = a.get(15, TimeUnit.SECONDS);
      var two = b.get(15, TimeUnit.SECONDS);
      assertEquals(200, one.code());
      assertEquals(200, two.code());
      assertEquals(one.value().get("id"), two.value().get("id"));
      assertEquals(2, ((Map<?, ?>) one.value().get("correction")).get("source_revision"));
    }
    assertEquals(409, req("POST", path + "/export", viewer, Map.of("version", 2), null).code());
    var after = req("GET", path, viewer, null, null).value();
    assertEquals(3, after.get("revision"));
    assertEquals(before.get("stage"), after.get("stage"));
    assertEquals(before.get("result"), after.get("result"));
    assertEquals(
        1,
        ((List<Map<String, Object>>) after.get("history"))
            .stream().filter(x -> "CORRECTION_CREATED".equals(x.get("action"))).count());
    assertEquals(409, req("POST", url, operator, body, UUID.randomUUID().toString()).code());
    assertEquals(
        409,
        req(
                "POST",
                url,
                operator,
                Map.of("version", 3, "note", "并行提交修订", "batch", correctedInput()),
                key)
            .code());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void exportWaitsForAnInFlightRecordChangeWhileViewingRemainsReadOnly() throws Exception {
    String path = startForCorrection("READY"), id = path.substring("/runs/".length());
    try (var pool = Executors.newSingleThreadExecutor();
        var conn = Objects.requireNonNull(db.getDataSource()).getConnection()) {
      conn.setAutoCommit(false);
      try {
        try (var ps =
            conn.prepareStatement(
                "UPDATE work_run SET revision=revision+1,stage='WITHDRAWN' WHERE id=? AND"
                    + " revision=1")) {
          ps.setString(1, id);
          assertEquals(1, ps.executeUpdate());
        }
        var page = req("GET", path, viewer, null, null);
        assertEquals(200, page.code());
        assertEquals(1, page.value().get("revision"));
        var sent = new CountDownLatch(1);
        var export =
            pool.submit(
                () -> {
                  sent.countDown();
                  return req("POST", path + "/export", viewer, Map.of("version", 1), null);
                });
        assertTrue(sent.await(3, TimeUnit.SECONDS));
        assertThrows(TimeoutException.class, () -> export.get(250, TimeUnit.MILLISECONDS));
        conn.commit();
        assertEquals(409, export.get(10, TimeUnit.SECONDS).code());
        var current = req("GET", path, viewer, null, null).value();
        assertEquals(2, current.get("revision"));
        assertEquals("WITHDRAWN", current.get("stage"));
      } finally {
        conn.rollback();
      }
    }
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private String newQueueRun(String title, String actor) throws Exception {
    var b = new HashMap<String, Object>(batch);
    b.put("title", title);
    var created = req("POST", "/runs", actor, b, UUID.randomUUID().toString());
    assertEquals(200, created.code());
    return "/runs/" + created.value().get("id");
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private Reply route(
      String path, String actor, int version, String action, String target, String note, String key)
      throws Exception {
    return req(
        "POST",
        path + "/routing",
        actor,
        Map.of("expectedVersion", version, "action", action, "target", target, "note", note),
        key);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void routingRequiresIndependentReviewerAndTenantScope() throws Exception {
    String p = newQueueRun("权限分派", reviewer), key = UUID.randomUUID().toString();
    for (String token : List.of(viewer, operator)) {
      assertEquals(403, req("GET", "/review-queue", token, null, null).code());
      assertEquals(403, req("GET", p + "/routing", token, null, null).code());
      assertEquals(403, route(p, token, 0, "CLAIM", "", "", key).code());
    }
    assertEquals(401, req("GET", "/review-queue", null, null, null).code());
    assertEquals(404, req("GET", p + "/routing", other, null, null).code());
    assertEquals(404, route(p, other, 0, "CLAIM", "", "", key).code());
    assertEquals(403, route(p, reviewer, 0, "CLAIM", "", "", key).code());
    assertEquals(403, route(p, reviewer, 0, "ASSIGN", "admin", "说明", key).code());
    for (String target : List.of("other", "viewer", "operator", "reviewer", "no-such-account"))
      assertEquals(400, route(p, admin, 0, "ASSIGN", target, "核对接收人", key).code());
    assertEquals(200, route(p, admin, 0, "CLAIM", "", "", key).code());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void routingIdempotentReceiptsSurviveHandoffAndDoNotChangeEvidenceRevision() throws Exception {
    String p = newQueueRun("流转留痕", operator), key = UUID.randomUUID().toString();
    var baseline = req("GET", p, viewer, null, null).value();
    var a = route(p, reviewer, 0, "CLAIM", "", "", key);
    assertEquals(200, a.code());
    assertEquals(1, a.value().get("route_version"));
    assertEquals("reviewer", a.value().get("after_owner"));
    assertEquals(
        200,
        route(p, reviewer, 1, "HANDOFF", "admin", "跨班交接", UUID.randomUUID().toString()).code());
    var replay = route(p, reviewer, 0, "CLAIM", "", "", key);
    assertEquals(a.value(), replay.value());
    assertEquals(409, route(p, reviewer, 0, "CLAIM", "", "内容有变", key).code());
    assertEquals(
        403, route(p, reviewer, 2, "RELEASE", "", "别人持有", UUID.randomUUID().toString()).code());
    assertEquals(
        409, route(p, admin, 1, "RELEASE", "", "已过期", UUID.randomUUID().toString()).code());
    var history = req("GET", p + "/routing", admin, null, null).value();
    assertEquals(2, ((List<?>) history.get("history")).size());
    assertEquals("admin", ((Map<?, ?>) history.get("assignment")).get("owner_name"));
    assertEquals(baseline, req("GET", p, viewer, null, null).value());
    assertEquals(
        200, route(p, admin, 2, "RELEASE", "", "归还公共队列", UUID.randomUUID().toString()).code());
    assertEquals(a.value(), route(p, reviewer, 0, "CLAIM", "", "", key).value());
    String audit = "/audit/page?action=ROUTE_HANDOFF&runId=" + p.substring(6);
    assertEquals(1, ((List<?>) req("GET", audit, admin, null, null).value().get("items")).size());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void assignedWorkCannotBeApprovedByAnotherReviewerAndNoAssignmentsAfterClose() throws Exception {
    String p = newQueueRun("负责权生效", operator), key = UUID.randomUUID().toString();
    assertEquals(200, route(p, admin, 0, "ASSIGN", "reviewer", "指定独立复核", key).code());
    var review = Map.of("version", 1, "state", "APPROVED", "note", "独立判断");
    assertEquals(409, req("POST", p + "/review", admin, review, null).code());
    assertEquals(200, req("POST", p + "/review", reviewer, review, null).code());
    assertEquals(
        409, route(p, admin, 1, "RELEASE", "", "单据已结", UUID.randomUUID().toString()).code());
    assertEquals(200, route(p, admin, 0, "ASSIGN", "reviewer", "指定独立复核", key).code());
    assertEquals(
        "APPROVED",
        ((Map<?, ?>) req("GET", p + "/routing", admin, null, null).value().get("assignment"))
            .get("stage"));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void twoSimultaneousClaimantsCannotBothOwnTheSameWork() throws Exception {
    String p = newQueueRun("并发领取", operator);
    var start = new CountDownLatch(2);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var futures = new ArrayList<Future<Reply>>();
      for (String token : List.of(reviewer, admin))
        futures.add(
            pool.submit(
                () -> {
                  start.countDown();
                  assertTrue(start.await(4, TimeUnit.SECONDS));
                  return route(p, token, 0, "CLAIM", "", "", UUID.randomUUID().toString());
                }));
      assertEquals(
          Set.of(200, 409),
          new HashSet<>(List.of(futures.get(0).get().code(), futures.get(1).get().code())));
    }
    var h = req("GET", p + "/routing", reviewer, null, null).value();
    assertEquals(1, ((List<?>) h.get("history")).size());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void disabledReviewerAppearsInRecoveryQueueAndAdminCanReassignIt() throws Exception {
    String who = "queuehelper";
    assertEquals(
        200,
        req(
                "POST",
                "/users",
                admin,
                Map.of(
                    "username", who, "password", "Integration-test-only-2026!", "role", "REVIEWER"),
                null)
            .code());
    String p = newQueueRun("handoff-recovery-search", operator),
        id = db.queryForObject("SELECT id FROM app_user WHERE username=?", String.class, who);
    var old = login(who);
    assertEquals(200, route(p, old, 0, "CLAIM", "", "", UUID.randomUUID().toString()).code());
    assertEquals(200, req("PATCH", "/users/" + id, admin, Map.of("active", "false"), null).code());
    assertEquals(401, route(p, old, 1, "RELEASE", "", "停用账号", UUID.randomUUID().toString()).code());
    var stalled =
        req("GET", "/review-queue?scope=STALE&q=handoff-recovery-search", admin, null, null)
            .value();
    assertEquals(1, stalled.get("total"));
    assertEquals(
        200,
        route(p, admin, 1, "ASSIGN", "reviewer", "交由在岗复核员", UUID.randomUUID().toString()).code());
    assertEquals(
        0,
        req("GET", "/review-queue?scope=STALE&q=handoff-recovery-search", admin, null, null)
            .value()
            .get("total"));
    assertEquals(
        1,
        req("GET", "/review-queue?scope=MINE&q=handoff-recovery-search", reviewer, null, null)
            .value()
            .get("total"));
    assertEquals(
        0,
        req("GET", "/review-queue?scope=TEAM&q=handoff-recovery-search", other, null, null)
            .value()
            .get("total"));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void routingHistoryCursorDoesNotRepeatRowsAndInputErrorsDoNotConsumeVersions() throws Exception {
    String p = newQueueRun("分派分页", operator);
    int max = 24;
    assertEquals(400, route(p, reviewer, 0, "CLAIM", "", "", "short").code());
    assertEquals(
        400, route(p, admin, 0, "ASSIGN", "reviewer", " ", UUID.randomUUID().toString()).code());
    assertEquals(400, req("GET", "/review-queue?scope=unknown", admin, null, null).code());
    for (int i = 0; i < max; i++)
      assertEquals(
          200,
          route(
                  p,
                  reviewer,
                  i,
                  i % 2 == 0 ? "CLAIM" : "RELEASE",
                  "",
                  "自助轮转",
                  UUID.randomUUID().toString())
              .code());
    var top = req("GET", p + "/routing", reviewer, null, null).value();
    var rows = (List<Map<String, Object>>) top.get("history");
    assertEquals(20, rows.size());
    var earlier =
        req("GET", p + "/routing?before=" + top.get("nextBefore"), reviewer, null, null).value();
    var extra = (List<Map<String, Object>>) earlier.get("history");
    assertEquals(4, extra.size());
    assertEquals(1, extra.getLast().get("route_version"));
    assertEquals(0, earlier.get("nextBefore"));
    assertTrue(
        (int) rows.getLast().get("route_version") > (int) extra.getFirst().get("route_version"));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private Map<String, Object> scenarioOption() {
    return json.readValue(
        "{\"name\":\"忽略不足 101 毫秒的片段\",\"settings\":{\"minDurationMs\":101}}", Map.class);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void scenarioGoldenForDomainAndNoPersistedBusinessWrites() throws Exception {
    String p = newQueueRun("场景试算", operator);
    var old = req("GET", p, viewer, null, null).value();
    var preview =
        req(
            "POST",
            p + "/scenarios/preview",
            operator,
            Map.of("expectedRevision", 1, "alternatives", List.of(scenarioOption())),
            null);
    assertEquals(200, preview.code(), preview.value().toString());
    assertEquals(true, preview.value().get("previewOnly"));
    assertEquals(1, preview.value().get("sourceRevision"));
    var result = (Map<String, Object>) ((List<?>) preview.value().get("alternatives")).getFirst();
    var lines = (List<Map<String, Object>>) result.get("metrics");
    var x = lines.stream().filter(z -> "ignored".equals(z.get("metric"))).findFirst().orElseThrow();
    assertEquals(3, ((Number) x.get("alternative")).doubleValue(), 1e-10);
    assertEquals(x.get("archived"), x.get("reference"));
    if (x.get("delta") != null)
      assertEquals(
          ((Number) x.get("alternative")).doubleValue()
              - ((Number) x.get("reference")).doubleValue(),
          ((Number) x.get("delta")).doubleValue(),
          1e-10);
    assertEquals(old, req("GET", p, viewer, null, null).value());
    assertEquals(
        0,
        ((Number)
                ((Map<?, ?>)
                        req("GET", p + "/routing", reviewer, null, null).value().get("assignment"))
                    .get("route_version"))
            .intValue());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void scenarioPreviewBoundsRevisionAndTenantAreValidated() throws Exception {
    String p = newQueueRun("固定样本", operator);
    var a = scenarioOption();
    var valid = Map.of("expectedRevision", 1, "alternatives", List.of(a));
    String url = p + "/scenarios/preview";
    assertEquals(401, req("POST", url, null, valid, null).code());
    assertEquals(403, req("POST", url, viewer, valid, null).code());
    assertEquals(404, req("POST", url, other, valid, null).code());
    for (var options :
        List.of(
            List.of(),
            List.of(a, a),
            List.of(a, a, a, a),
            List.of(Map.of("name", "无变化", "settings", batch.get("settings"))),
            List.of(Map.of("name", "缺少参数", "settings", Map.of()))))
      assertEquals(
          400,
          req("POST", url, reviewer, Map.of("expectedRevision", 1, "alternatives", options), null)
              .code());
    assertEquals(
        409,
        req("POST", url, operator, Map.of("expectedRevision", 2, "alternatives", List.of(a)), null)
            .code());
    assertEquals(200, req("POST", url, reviewer, valid, null).code());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void queueCursorDoesNotSkipAfterRemovalOrIncludeLaterArrivals() throws Exception {
    String label = "seek-" + UUID.randomUUID();
    var original = new ArrayList<String>();
    for (int i = 0; i < 23; i++) original.add(newQueueRun(label, operator).substring(6));
    var tied =
        java.sql.Timestamp.from(
            java.time.Instant.now()
                .minusSeconds(7220)
                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                .plusNanos(567890000));
    for (String id : original) db.update("UPDATE work_run SET created_at=? WHERE id=?", tied, id);
    original.sort(String::compareTo);
    String url = "/review-queue?q=" + label;
    var first = req("GET", url, reviewer, null, null).value();
    var rows = (List<Map<String, Object>>) first.get("items");
    assertEquals(20, rows.size());
    assertEquals(original.subList(0, 20), rows.stream().map(x -> (String) x.get("id")).toList());
    String next = (String) first.get("nextCursor");
    assertFalse(next.isBlank());
    assertEquals(
        200,
        route(
                "/runs/" + original.getFirst(),
                reviewer,
                0,
                "CLAIM",
                "",
                "",
                UUID.randomUUID().toString())
            .code());
    String later = newQueueRun(label, operator).substring(6);
    var more = req("GET", url + "&cursor=" + next, reviewer, null, null).value();
    assertEquals(
        original.subList(20, 23),
        ((List<Map<String, Object>>) more.get("items"))
            .stream().map(x -> (String) x.get("id")).toList());
    assertEquals("", more.get("nextCursor"));
    assertEquals(22, more.get("total"));
    assertEquals(23, req("GET", url, reviewer, null, null).value().get("total"));
    assertEquals(
        22, req("GET", url + "&minWaitMinutes=120", reviewer, null, null).value().get("total"));
    assertEquals(
        0, req("GET", url + "&minWaitMinutes=121", reviewer, null, null).value().get("total"));
    assertEquals(400, req("GET", url + "&scope=TEAM&cursor=" + next, reviewer, null, null).code());
    assertEquals(400, req("GET", url + "&cursor=" + next, admin, null, null).code());
    assertEquals(400, req("GET", url + "&page=2&cursor=" + next, reviewer, null, null).code());
    assertEquals(400, req("GET", url + "&minWaitMinutes=10081", reviewer, null, null).code());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void expiredOrInvalidQueueCutoffsRejected() {
    var now = java.time.Instant.parse("2026-04-01T12:00:00.000010Z");
    String key = "scope";
    var ok = QueueWindow.open("", key, now);
    assertEquals(ok, QueueWindow.open(ok.encode(), key, now.plusSeconds(900)));
    assertEquals(
        409,
        assertThrows(
                Model.Failure.class, () -> QueueWindow.open(ok.encode(), key, now.plusSeconds(901)))
            .status);
    assertEquals(
        400, assertThrows(Model.Failure.class, () -> QueueWindow.open("bad!", key, now)).status);
    assertEquals(
        400,
        assertThrows(
                Model.Failure.class, () -> QueueWindow.open(ok.encode(), key, now.minusSeconds(1)))
            .status);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void scenarioHashesMatchExactExportMembersNotOnlyCounts() throws Exception {
    String p = newQueueRun("核对评审来源", operator);
    var options = Map.of("expectedRevision", 1, "alternatives", List.of(scenarioOption()));
    var preview = req("POST", p + "/scenarios/preview", reviewer, options, null).value();
    assertEquals("zhuatech-scenario/2", preview.get("format"));
    assertEquals(EvidenceArchive.PROJECT, preview.get("project"));
    var req =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + p + "/export"))
            .header("Authorization", "Bearer " + viewer)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"version\":1}"))
            .build();
    var packet = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
    assertEquals(200, packet.statusCode());
    var hashes = new TreeMap<String, String>();
    try (var z =
        new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(packet.body()))) {
      for (var ent = z.getNextEntry(); ent != null; ent = z.getNextEntry())
        if (Set.of("input.json", "result.json").contains(ent.getName()))
          hashes.put(
              ent.getName(),
              java.util.HexFormat.of()
                  .formatHex(
                      java.security.MessageDigest.getInstance("SHA-256").digest(z.readAllBytes())));
    }
    assertEquals(2, hashes.size());
    assertEquals(hashes, preview.get("sourceDigests"));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void correctionDiffDistinguishesStructuralChangeFromNumericSerialization() {
    var a =
        new Api.Batch(
            "旧",
            Map.of("rate", 1),
            List.of(
                Map.of("id", "A", "v", 1),
                Map.of("id", "B", "v", List.of(1, 2)),
                Map.of("id", "C", "v", 3)));
    var b =
        new Api.Batch(
            "新",
            Map.of("rate", 1.0),
            List.of(
                Map.of("id", "B", "v", List.of(1.0, 2.0)),
                Map.of("id", "A", "v", 1.0),
                Map.of("id", "D", "v", 3)));
    var diff = CorrectionDiff.compare(a, b);
    assertEquals(List.of("REMOVE", "ADD", "ORDER"), diff.stream().map(d -> d.get("kind")).toList());
    assertEquals(List.of("B", "A"), diff.getLast().get("after"));
    assertEquals(
        "SETTING",
        CorrectionDiff.compare(a, new Api.Batch("旧", Map.of("rate", 2), a.rows()))
            .getFirst()
            .get("kind"));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @BeforeAll
  void setup() throws Exception {
    admin = login("admin");
    assertNotNull(admin);
    for (String role : List.of("OPERATOR", "VIEWER", "REVIEWER")) {
      assertEquals(
          200,
          req(
                  "POST",
                  "/users",
                  admin,
                  Map.of(
                      "username",
                      role.toLowerCase(Locale.ROOT),
                      "password",
                      "Integration-test-only-2026!",
                      "role",
                      role),
                  null)
              .code());
    }
    operator = login("operator");
    viewer = login("viewer");
    reviewer = login("reviewer");
    db.update("INSERT INTO tenant_guard VALUES('separate')");
    db.update(
        "INSERT INTO app_user(id,tenant,username,password_hash,role,active) VALUES(?,?,?,?,?,true)",
        UUID.randomUUID().toString(),
        "separate",
        "other",
        new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
            .encode("Integration-test-only-2026!"),
        "ADMIN");
    other = login("other");
    var cat = json.readValue(getClass().getResourceAsStream("/static/catalog.json"), Map.class);
    batch = Map.of("title", "季度验收", "settings", cat.get("settings"), "rows", cat.get("rows"));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void passwordRotationRevokesEverySessionAndKeepsHistorySecretFree() throws Exception {
    String initial = "Integration-test-only-2026!", updated = "New-integration-only-secret";
    assertEquals(
        200,
        req(
                "POST",
                "/users",
                admin,
                Map.of("username", "rotate-test", "role", "VIEWER", "password", initial),
                null)
            .code());
    String first = login("rotate-test"), second = login("rotate-test");
    assertNotEquals(first, second);
    assertEquals(
        403,
        req(
                "POST",
                "/me/password",
                first,
                Map.of("currentPassword", "wrong", "newPassword", updated),
                null)
            .code());
    assertEquals(
        400,
        req(
                "POST",
                "/me/password",
                first,
                Map.of("currentPassword", initial, "newPassword", "            "),
                null)
            .code());
    assertEquals(200, req("GET", "/me", second, null, null).code());
    assertEquals(
        200,
        req(
                "POST",
                "/me/password",
                first,
                Map.of("currentPassword", initial, "newPassword", updated),
                null)
            .code());
    for (String t : List.of(first, second))
      assertEquals(401, req("GET", "/me", t, null, null).code());
    assertEquals(
        401,
        req("POST", "/login", null, Map.of("username", "rotate-test", "password", initial), null)
            .code());
    assertEquals(
        200,
        req("POST", "/login", null, Map.of("username", "rotate-test", "password", updated), null)
            .code());
    String log = req("GET", "/audit", admin, null, null).value().toString();
    assertTrue(log.contains("PASSWORD_CHANGED"));
    assertFalse(log.contains(initial));
    assertFalse(log.contains(updated));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void scopedQueueMatchesTitleLiterallyAndHonoursIsolation() throws Exception {
    var b = new HashMap<>(batch);
    b.put("title", "队列_%fixture");
    var r = req("POST", "/runs", operator, b, "queue-filter-fixture");
    assertEquals(200, r.code());
    String path =
        "/runs?q="
            + java.net.URLEncoder.encode("_%fixture", java.nio.charset.StandardCharsets.UTF_8);
    assertEquals(1, req("GET", path + "&scope=mine", operator, null, null).value().get("total"));
    assertEquals(0, req("GET", path + "&scope=mine", reviewer, null, null).value().get("total"));
    assertEquals(
        1, req("GET", path + "&scope=reviewable", reviewer, null, null).value().get("total"));
    assertEquals(403, req("GET", path + "&scope=reviewable", viewer, null, null).code());
    assertEquals(0, req("GET", path, other, null, null).value().get("total"));
    assertEquals(
        200,
        req(
                "POST",
                "/runs/" + r.value().get("id") + "/review",
                reviewer,
                Map.of("version", 1, "state", "REJECTED", "note", "queue test"),
                null)
            .code());
    assertEquals(
        0, req("GET", path + "&scope=reviewable", reviewer, null, null).value().get("total"));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void withdrawalKeepsHistoryAndEnforcesOwnershipAndVersion() throws Exception {
    var original = req("POST", "/runs", operator, batch, "withdraw-fixture");
    assertEquals(200, original.code());
    String path = "/runs/" + original.value().get("id");
    var valid = Map.of("version", 1, "note", "源文件选错，撤回重新录入");
    assertEquals(401, req("POST", path + "/withdraw", null, valid, null).code());
    assertEquals(404, req("POST", path + "/withdraw", other, valid, null).code());
    assertEquals(403, req("POST", path + "/withdraw", admin, valid, null).code());
    assertEquals(403, req("POST", path + "/withdraw", viewer, valid, null).code());
    assertEquals(
        400,
        req("POST", path + "/withdraw", operator, Map.of("version", 1, "note", "  "), null).code());
    assertEquals(
        409,
        req("POST", path + "/withdraw", operator, Map.of("version", 2, "note", "冲突"), null).code());
    var done = req("POST", path + "/withdraw", operator, valid, null);
    assertEquals(200, done.code());
    assertEquals("WITHDRAWN", done.value().get("stage"));
    assertEquals(2, done.value().get("revision"));
    assertEquals(original.value().get("result"), done.value().get("result"));
    assertEquals(original.value().get("input"), done.value().get("input"));
    assertTrue(done.value().get("history").toString().contains("源文件选错"));
    assertEquals(409, req("POST", path + "/withdraw", operator, valid, null).code());
    assertEquals(
        409,
        req(
                "POST",
                path + "/review",
                reviewer,
                Map.of("version", 2, "state", "REJECTED", "note", "too late"),
                null)
            .code());
    var list = req("GET", "/runs?state=WITHDRAWN&scope=mine", operator, null, null);
    assertEquals(200, list.code());
    assertTrue(
        list.value().get("items").toString().contains(original.value().get("id").toString()));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void onlyOneConcurrentWithdrawalOrReviewCanWin() throws Exception {
    var created = req("POST", "/runs", operator, batch, "workflow-race-key");
    String path = "/runs/" + created.value().get("id");
    var gate = new CyclicBarrier(2);
    try (var workers = Executors.newFixedThreadPool(2)) {
      var a =
          workers.submit(
              () -> {
                gate.await();
                return req(
                    "POST",
                    path + "/withdraw",
                    operator,
                    Map.of("version", 1, "note", "重新核对"),
                    null);
              });
      var b =
          workers.submit(
              () -> {
                gate.await();
                return req(
                    "POST",
                    path + "/review",
                    reviewer,
                    Map.of("version", 1, "state", "REJECTED", "note", "退回"),
                    null);
              });
      assertEquals(
          Set.of(200, 409),
          Set.of(a.get(15, TimeUnit.SECONDS).code(), b.get(15, TimeUnit.SECONDS).code()));
    }
    var detail = req("GET", path, viewer, null, null).value();
    assertEquals(2, detail.get("revision"));
    assertEquals(2, ((List<?>) detail.get("history")).size());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void bootstrapReadinessIsExplicit() {
    Auth cold = new Auth(db, "");
    assertEquals(503, assertThrows(Model.Failure.class, cold::requireReady).status);
    assertEquals(
        503, assertThrows(Model.Failure.class, () -> cold.login("admin", "unused")).status);
    cold.run(null);
    assertDoesNotThrow(cold::requireReady);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void loginAndRoles() throws Exception {
    assertEquals(401, req("GET", "/runs", null, null, null).code());
    assertEquals(403, req("POST", "/runs", viewer, batch, "viewer-test").code());
    assertEquals(403, req("GET", "/audit", operator, null, null).code());
    assertEquals(403, req("GET", "/users", viewer, null, null).code());
    assertEquals(
        401,
        req("POST", "/login", null, Map.of("username", "admin", "password", "incorrect"), null)
            .code());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void atomicReviewAndIsolation() throws Exception {
    var created = req("POST", "/runs", admin, batch, "review-fixture");
    assertEquals(200, created.code(), created.value().toString());
    String id = (String) created.value().get("id");
    var decision = Map.of("state", "APPROVED", "note", "核对样本口径", "version", 1);
    assertEquals(403, req("POST", "/runs/" + id + "/review", admin, decision, null).code());
    assertEquals(404, req("GET", "/runs/" + id, other, null, null).code());
    assertEquals(404, req("POST", "/runs/" + id + "/review", other, decision, null).code());
    assertEquals(0, req("GET", "/runs", other, null, null).value().get("total"));
    assertEquals(200, req("GET", "/runs/" + id, viewer, null, null).code());
    var checked = req("POST", "/runs/" + id + "/review", reviewer, decision, null);
    assertEquals(200, checked.code());
    assertEquals("APPROVED", checked.value().get("stage"));
    assertEquals(2, ((List<?>) checked.value().get("history")).size());
    assertEquals(409, req("POST", "/runs/" + id + "/review", reviewer, decision, null).code());
    assertEquals(200, req("GET", "/runs?state=APPROVED", viewer, null, null).code());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void concurrentRetryAndConflict() throws Exception {
    try (var pool = Executors.newFixedThreadPool(3)) {
      var work = new ArrayList<Future<Reply>>();
      for (int i = 0; i < 3; i++)
        work.add(pool.submit(() -> req("POST", "/runs", operator, batch, "same-concurrent-key")));
      var ids = new HashSet<>();
      for (var f : work) {
        var r = f.get(15, TimeUnit.SECONDS);
        assertEquals(200, r.code(), r.value().toString());
        ids.add(r.value().get("id"));
      }
      assertEquals(1, ids.size());
    }
    var changed = new HashMap<>(batch);
    changed.put("title", "changed");
    assertEquals(409, req("POST", "/runs", operator, changed, "same-concurrent-key").code());
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM work_run WHERE request_key='same-concurrent-key'",
            Integer.class));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void invalidBatchLeavesNoPartialRows() throws Exception {
    var bad = new HashMap<>(batch);
    bad.put("rows", List.of(Map.of("unexpected", 42)));
    assertEquals(400, req("POST", "/runs", operator, bad, "invalid-test").code());
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM work_run WHERE request_key='invalid-test'", Integer.class));
    assertEquals(400, req("GET", "/runs?page=0", viewer, null, null).code());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void expiredLockStartsANewFailureWindowAndAdminCanUnlock() throws Exception {
    String name = "locked-person", initial = "Integration-test-only-2026!";
    assertEquals(
        200,
        req(
                "POST",
                "/users",
                admin,
                Map.of("username", name, "role", "VIEWER", "password", initial),
                null)
            .code());
    String id = db.queryForObject("SELECT id FROM app_user WHERE username=?", String.class, name);
    String activeSession = login(name);
    assertEquals(200, req("PATCH", "/users/" + id, admin, Map.of("role", "VIEWER"), null).code());
    assertEquals(
        200,
        req("GET", "/me", activeSession, null, null).code(),
        "a no-op must not revoke sessions");
    assertEquals(400, req("PATCH", "/users/" + id, admin, Map.of("password", ""), null).code());
    db.update(
        "UPDATE app_user SET failures=5,locked_until=? WHERE id=?",
        java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(5)),
        id);
    var wrong = Map.of("username", name, "password", "incorrect");
    for (int i = 0; i < 5; i++)
      assertEquals(401, req("POST", "/login", null, wrong, null).code(), "attempt " + i);
    assertEquals(
        429,
        req("POST", "/login", null, Map.of("username", name, "password", initial), null).code());
    String path = "/users/" + id + "/unlock";
    assertEquals(403, req("POST", path, reviewer, Map.of("note", "核对身份"), null).code());
    assertEquals(404, req("POST", path, other, Map.of("note", "核对身份"), null).code());
    assertEquals(400, req("POST", path, admin, Map.of("note", " "), null).code());
    assertEquals(200, req("POST", path, admin, Map.of("note", "已线下核实账号本人"), null).code());
    assertEquals(409, req("POST", path, admin, Map.of("note", "重复操作"), null).code());
    assertNotNull(login(name));
    assertEquals(401, req("GET", "/me", activeSession, null, null).code());
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM work_audit WHERE subject=? AND action='UNLOCK_USER'",
            Integer.class,
            id));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void concurrentAdminHandoverPreservesAnActiveAdministrator() throws Exception {
    db.update("INSERT INTO tenant_guard VALUES('handover')");
    String pw = "Integration-test-only-2026!",
        a = UUID.randomUUID().toString(),
        b = UUID.randomUUID().toString();
    var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    for (var entry : Map.of(a, "handover-a", b, "handover-b").entrySet())
      db.update(
          "INSERT INTO app_user(id,tenant,username,password_hash,role,active)"
              + " VALUES(?,'handover',?,?,'ADMIN',true)",
          entry.getKey(),
          entry.getValue(),
          encoder.encode(pw));
    String ta = login("handover-a"), tb = login("handover-b");
    var gate = new CyclicBarrier(2);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var x =
          pool.submit(
              () -> {
                gate.await();
                return req("PATCH", "/users/" + b, ta, Map.of("active", "false"), null).code();
              });
      var y =
          pool.submit(
              () -> {
                gate.await();
                return req("PATCH", "/users/" + a, tb, Map.of("role", "VIEWER"), null).code();
              });
      var codes = List.of(x.get(15, TimeUnit.SECONDS), y.get(15, TimeUnit.SECONDS));
      assertEquals(1, codes.stream().filter(c -> c == 200).count());
      assertTrue(codes.stream().allMatch(c -> Set.of(200, 401, 403, 409).contains(c)));
    }
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM app_user WHERE tenant='handover' AND role='ADMIN' AND"
                + " active=true",
            Integer.class));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void olderAuditPagesRemainTenantScopedAndStableDuringWrites() throws Exception {
    for (int i = 0; i < 105; i++)
      db.update(
          "INSERT INTO work_audit(tenant,actor,subject,action,note)"
              + " VALUES('separate','qa','history','TEST',?)",
          "row-" + i);
    String path = "/audit/page?limit=50";
    assertEquals(403, req("GET", path, viewer, null, null).code());
    assertEquals(400, req("GET", "/audit/page?limit=101", other, null, null).code());
    var page = req("GET", path, other, null, null);
    assertEquals(200, page.code());
    var ids = new HashSet<Object>();
    int count = 0;
    db.update(
        "INSERT INTO work_audit(tenant,actor,subject,action,note)"
            + " VALUES('separate','qa','history','TEST','late')");
    while (true) {
      for (var row : (List<Map<String, Object>>) page.value().get("items")) {
        assertTrue(ids.add(row.get("seq")));
        assertNotEquals("late", row.get("note"));
        assertEquals("qa", row.get("actor"));
        count++;
      }
      if (!Boolean.TRUE.equals(page.value().get("hasMore"))) break;
      page = req("GET", path + "&before=" + page.value().get("nextBefore"), other, null, null);
      assertEquals(200, page.code());
    }
    assertEquals(105, count);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void revocationInvalidatesSessions() throws Exception {
    assertEquals(
        200,
        req(
                "POST",
                "/users",
                admin,
                Map.of(
                    "username",
                    "temp-user",
                    "password",
                    "Integration-test-only-2026!",
                    "role",
                    "VIEWER"),
                null)
            .code());
    String t = login("temp-user");
    String id =
        db.queryForObject("SELECT id FROM app_user WHERE username='temp-user'", String.class);
    assertEquals(200, req("PATCH", "/users/" + id, admin, Map.of("active", "false"), null).code());
    assertEquals(401, req("GET", "/me", t, null, null).code());
  }
}
