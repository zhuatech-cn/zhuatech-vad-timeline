/* 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.vadtimeline;

import static cn.zhuatech.vadtimeline.Model.*;

import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
@RestController
@RequestMapping("/api")
/** 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
public class Api {
  private final Auth auth;
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final GateStore gates;
  private final BatchComparison comparisons;
  private final ReviewRouting routing;

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public Api(
      Auth auth,
      JdbcTemplate db,
      ObjectMapper json,
      GateStore gates,
      BatchComparison comparisons,
      ReviewRouting routing) {
    this.routing = routing;
    this.gates = gates;
    this.comparisons = comparisons;
    this.auth = auth;
    this.db = db;
    this.json = json;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public record Login(String username, String password) {}

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public record Batch(String title, Map<String, Object> settings, List<Map<String, Object>> rows) {}

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public record Review(int version, String state, String note, String exceptionNote) {}

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @GetMapping("/policy")
  public Object policy(@RequestHeader(value = "Authorization", required = false) String h) {
    var u = auth.current(h);
    return Map.of("current", gates.read(u.tenant()), "catalog", GateRules.METRICS);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @GetMapping("/policy/history")
  public Object policyHistory(
      @RequestHeader(value = "Authorization", required = false) String h,
      @RequestParam(defaultValue = "0") int before,
      @RequestParam(defaultValue = "20") int limit) {
    var u = auth.current(h);
    return gates.history(u.tenant(), before, limit);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @PostMapping("/policy/preview")
  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public Object previewPolicy(
      @RequestHeader(value = "Authorization", required = false) String h,
      @RequestBody GateStore.Preview request) {
    var u = auth.current(h);
    Auth.role(u, "ADMIN");
    return gates.preview(u, request);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @PostMapping("/policy")
  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Object publishPolicy(
      @RequestHeader(value = "Authorization", required = false) String h,
      @RequestHeader(value = "Idempotency-Key", defaultValue = "") String key,
      @RequestBody GateStore.Change change) {
    return gates.publish(auth.managementUser(h), change, key);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @GetMapping("/runs/{id}/compare")
  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public Object compareRuns(
      @RequestHeader(value = "Authorization", required = false) String h,
      @PathVariable String id,
      @RequestParam String baseline) {
    return comparisons.read(auth.current(h).tenant(), baseline, id);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @GetMapping("/runs/{id}/compare/evidence")
  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public Object compareEvidence(
      @RequestHeader(value = "Authorization", required = false) String h,
      @PathVariable String id,
      @RequestParam String baseline,
      @RequestParam(defaultValue = "main") String section,
      @RequestParam(defaultValue = "DIFFERENCES") String filter,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return comparisons.evidence(
        auth.current(h).tenant(), baseline, id, section, filter, q, page, size);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @GetMapping("/health")
  public Map<String, Object> health() {
    auth.requireReady();
    db.queryForObject("SELECT 1", Integer.class);
    return Map.of("status", "UP", "project", "vad-timeline");
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @PostMapping("/login")
  public Object login(@RequestBody Login login) {
    return auth.login(login.username(), login.password());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @GetMapping("/me")
  public Object me(@RequestHeader(value = "Authorization", required = false) String h) {
    return auth.current(h);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @PostMapping("/logout")
  public void logout(@RequestHeader(value = "Authorization", required = false) String h) {
    auth.current(h);
    auth.logout(h);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @PostMapping("/me/password")
  @Transactional(noRollbackFor = Failure.class)
  public void rotate(
      @RequestHeader(value = "Authorization", required = false) String h,
      @RequestBody Map<String, String> data) {
    var u = auth.current(h);
    if (!data.keySet().equals(Set.of("currentPassword", "newPassword")))
      throw new Failure(400, "请提供原密码和新密码");
    auth.rotatePassword(u, data.get("currentPassword"), data.get("newPassword"));
    audit(u, u.id(), "PASSWORD_CHANGED", "自助改密，撤销全部会话");
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @GetMapping("/users")
  public Object users(@RequestHeader(value = "Authorization", required = false) String h) {
    return auth.users(auth.current(h));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @PostMapping("/users")
  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public void add(
      @RequestHeader(value = "Authorization", required = false) String h,
      @RequestBody Map<String, String> p) {
    var u = auth.managementUser(h);
    auth.createUser(u, p);
    audit(u, p.get("username"), "CREATE_USER", p.get("role"));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @PatchMapping("/users/{id}")
  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public void userEdit(
      @RequestHeader(value = "Authorization", required = false) String h,
      @PathVariable String id,
      @RequestBody Map<String, String> p) {
    var u = auth.managementUser(h);
    String change = auth.changeUser(u, id, p);
    if (!change.isEmpty()) audit(u, id, "CHANGE_USER", change);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @GetMapping("/audit/page")
  public Object auditPage(
      @RequestHeader(value = "Authorization", required = false) String header,
      @RequestParam(required = false) Long before,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(defaultValue = "") String action,
      @RequestParam(defaultValue = "") String actor,
      @RequestParam(defaultValue = "") String runId) {
    var user = auth.current(header);
    Auth.role(user, "ADMIN");
    Checks.must(limit >= 1 && limit <= 100 && (before == null || before > 0), "无效的审计游标或条数");
    String event = action.strip(),
        name = actor.strip(),
        run = runId.strip().toLowerCase(Locale.ROOT);
    Checks.must(event.length() <= 60 && name.length() <= 80, "审计动作或操作人过长");
    Checks.must(
        run.isEmpty()
            || run.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
        "请输入完整的批次编号");
    var sql =
        new StringBuilder(
            "SELECT seq,actor,subject,action,note,created_at FROM work_audit WHERE tenant=?");
    var args = new ArrayList<Object>();
    args.add(user.tenant());
    if (!event.isEmpty()) {
      sql.append(" AND action=?");
      args.add(event);
    }
    if (!name.isEmpty()) {
      sql.append(" AND actor=?");
      args.add(name);
    }
    if (!run.isEmpty()) {
      sql.append(" AND subject IN (?,?,?)");
      args.add(run);
      args.add("export:" + run);
      args.add("route:" + run);
    }
    if (before != null) {
      sql.append(" AND seq<?");
      args.add(before);
    }
    sql.append(" ORDER BY seq DESC LIMIT ?");
    args.add(limit + 1);
    var rows = db.queryForList(sql.toString(), args.toArray());
    boolean more = rows.size() > limit;
    var items = more ? rows.subList(0, limit) : rows;
    return Map.of(
        "items",
        items,
        "hasMore",
        more,
        "nextBefore",
        more ? items.getLast().get("seq").toString() : "");
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @PostMapping("/users/{id}/unlock")
  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public void unlock(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable String id,
      @RequestBody Map<String, String> data) {
    var user = auth.managementUser(header);
    String note = data.get("note");
    Checks.must(
        data.size() == 1 && note != null && !note.isBlank() && note.length() <= 500,
        "请提供 1–500 字解锁依据");
    auth.unlock(user, id);
    audit(user, id, "UNLOCK_USER", note.strip());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @GetMapping("/audit")
  public Object auditLog(@RequestHeader(value = "Authorization", required = false) String h) {
    var u = auth.current(h);
    Auth.role(u, "ADMIN");
    return db.queryForList(
        "SELECT actor,subject,action,note,created_at FROM work_audit WHERE tenant=? ORDER BY seq"
            + " DESC LIMIT 100",
        u.tenant());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  void audit(User u, String subject, String action, String note) {
    db.update(
        "INSERT INTO work_audit(tenant,actor,subject,action,note) VALUES(?,?,?,?,?)",
        u.tenant(),
        u.username(),
        subject,
        action,
        note);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @PostMapping("/runs")
  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Map<String, Object> run(
      @RequestHeader(value = "Authorization", required = false) String h,
      @RequestHeader(value = "Idempotency-Key", defaultValue = "") String key,
      @RequestBody Batch batch) {
    var u = auth.current(h);
    Auth.role(u, "OPERATOR");
    Checks.must(key.matches("[a-zA-Z0-9_-]{8,80}"), "需要 8–80 位 Idempotency-Key");
    return save(u, h, key, batch, null, 0, "");
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public record Correction(int version, String note, Batch batch) {}

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public record ExportRequest(int version) {}

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @PostMapping("/runs/{id}/export")
  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public ResponseEntity<byte[]> exportEvidence(
      @RequestHeader(value = "Authorization", required = false) String h,
      @PathVariable String id,
      @RequestBody ExportRequest request) {
    var u = auth.current(h);
    // Every change to this dossier also updates its parent row. Hold that one row while exporting:
    // H2 repeatable-read alone does not exclude newly inserted history or correction links.
    var locked =
        db.queryForList(
            "SELECT revision FROM work_run WHERE tenant=? AND id=? FOR UPDATE",
            Integer.class,
            u.tenant(),
            id);
    if (locked.isEmpty()) throw new Failure(404, "记录不存在");
    u = auth.current(h);
    var data = detail(u, id);
    require(
        request != null && request.version() == ((Number) data.get("revision")).intValue(),
        "批次版本已变化，请刷新详情再导出");
    var bundle = EvidenceArchive.pack(data, json);
    audit(
        u,
        "export:" + data.get("id"),
        "EXPORT_EVIDENCE",
        "批次版本 " + request.version() + "；SHA-256 " + bundle.digest());
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("application/zip"))
        .contentLength(bundle.zip().length)
        .header(
            "Content-Disposition",
            "attachment; filename=\""
                + EvidenceArchive.PROJECT
                + "-"
                + data.get("id")
                + "-evidence.zip\"")
        .header("X-Evidence-SHA256", bundle.digest())
        .body(bundle.zip());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @PostMapping("/runs/{source}/corrections")
  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Map<String, Object> correct(
      @RequestHeader(value = "Authorization", required = false) String h,
      @RequestHeader(value = "Idempotency-Key", defaultValue = "") String key,
      @PathVariable String source,
      @RequestBody Correction c) {
    var u = auth.current(h);
    Auth.role(u, "OPERATOR");
    Checks.must(key.matches("[a-zA-Z0-9_-]{8,80}"), "需要 8–80 位 Idempotency-Key");
    Checks.must(
        c != null
            && c.batch() != null
            && c.version() > 0
            && c.note() != null
            && !c.note().isBlank()
            && c.note().length() <= 1000,
        "需要原单版本、修订说明（1–1000 字）和完整批次");
    return save(u, h, "fix:" + Checks.hash(key), c.batch(), source, c.version(), c.note().strip());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private Map<String, Object> save(
      User u, String header, String key, Batch batch, String source, int version, String note) {
    Checks.must(
        batch.title() != null && !batch.title().isBlank() && batch.title().length() <= 100,
        "标题须为 1–100 字");
    Checks.must(
        batch.settings() != null
            && batch.rows() != null
            && !batch.rows().isEmpty()
            && batch.rows().size() <= 200
            && !batch.rows().contains(null),
        "每批 1–200 条完整记录");
    Checks.must(batch.settings().keySet().equals(Set.of("minDurationMs")), "参数缺失或包含未知字段");
    int line = 0;
    for (var row : batch.rows()) {
      line++;
      Checks.must(
          row.keySet().equals(Set.of("id", "recording", "track", "beginMs", "endMs")),
          "第 " + line + " 行：字段缺失或包含未知列");
    }
    String input = json.writeValueAsString(batch);
    Checks.must(input.length() <= 250000, "输入过大，请拆分批次");
    String fingerprint =
        source == null
            ? Checks.hash(input)
            : Checks.hash(json.writeValueAsString(List.of(source, version, note, input)));
    // Serialize writes for this workspace so a concurrent retry cannot compute or post twice.
    db.queryForObject(
        "SELECT tenant FROM tenant_guard WHERE tenant=? FOR UPDATE", String.class, u.tenant());
    // In READ_COMMITTED, account disable/downgrade can have completed while this writer was queued.
    var confirmed = auth.current(header);
    Auth.role(confirmed, "OPERATOR");
    if (!u.id().equals(confirmed.id()) || !u.tenant().equals(confirmed.tenant()))
      throw new Failure(403, "账号身份已变化，请重新登录");
    Batch original = null;
    boolean currentSource = false;
    if (source != null) {
      var src =
          db.queryForList(
              "SELECT actor_id,stage,revision,input_json FROM work_run WHERE tenant=? AND id=?",
              u.tenant(),
              source);
      if (src.isEmpty()) throw new Failure(404, "原批次不存在");
      var row = src.getFirst();
      if (!u.id().equals(row.get("actor_id"))) throw new Failure(403, "仅原提交人可修订");
      // Check ownership even on retries; version must match only when creating a new child.
      currentSource =
          Set.of("REJECTED", "WITHDRAWN").contains(row.get("stage"))
              && ((Number) row.get("revision")).intValue() == version;
      original = json.readValue((String) row.get("input_json"), Batch.class);
    }
    var prior =
        db.queryForList(
            "SELECT id,fingerprint FROM work_run WHERE tenant=? AND actor_id=? AND request_key=?",
            u.tenant(),
            u.id(),
            key);
    if (!prior.isEmpty()) {
      var r = prior.getFirst();
      var saved = detail(u, (String) r.get("id"));
      if (!fingerprint.equals(r.get("fingerprint"))) {
        // Older clients may serialize object keys or equivalent numbers differently. Keep array
        // order
        // and every business value, title and correction origin as part of the request identity.
        boolean equal = CorrectionDiff.equal(saved.get("input"), json.readValue(input, Map.class));
        if (source != null) {
          equal =
              equal
                  && saved.get("correction") instanceof Map<?, ?> origin
                  && source.equals(origin.get("source_id"))
                  && ((Number) origin.get("source_revision")).intValue() == version
                  && note.equals(origin.get("reason"));
        } else equal = equal && !saved.containsKey("correction");
        require(equal, "同一提交键的输入已改变");
      }
      return saved;
    }
    if (source != null) require(currentSource, "仅可修订已退回或已撤回的当前版本");
    if (source != null)
      require(
          db.queryForObject(
                  "SELECT COUNT(*) FROM work_correction WHERE tenant=? AND source_id=?",
                  Long.class,
                  u.tenant(),
                  source)
              == 0,
          "此原单已有修订批次，请打开后续记录");
    var output = Algorithm.compute(batch.settings(), batch.rows());
    var changes = source == null ? List.of() : CorrectionDiff.compare(original, batch);
    if (source != null) Checks.must(!changes.isEmpty(), "至少修改一项业务参数、明细或顺序，仅更名或填说明不可提交");
    String encoded = ResultStorage.encode(output, json);
    String id = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO"
            + " work_run(id,tenant,actor_id,request_key,fingerprint,title,stage,revision,input_json,result_json)"
            + " VALUES(?,?,?,?,?,?,'READY',1,?,?)",
        id,
        u.tenant(),
        u.id(),
        key,
        fingerprint,
        batch.title().strip(),
        input,
        encoded);
    if (source != null) {
      // Linking a successor changes the source dossier, but never rewrites its archived input or
      // result.
      require(
          db.update(
                  "UPDATE work_run SET revision=revision+1 WHERE tenant=? AND id=? AND revision=?"
                      + " AND stage IN ('REJECTED','WITHDRAWN')",
                  u.tenant(),
                  source,
                  version)
              == 1,
          "原单版本已变化，请刷新再修订");
      db.update(
          "INSERT INTO"
              + " work_correction(tenant,source_id,child_id,source_revision,reason,changes_json)"
              + " VALUES(?,?,?,?,?,?)",
          u.tenant(),
          source,
          id,
          version,
          note,
          json.writeValueAsString(changes));
      audit(u, id, "CORRECTION_FROM", source);
      audit(u, source, "CORRECTION_CREATED", id);
    }
    gates.freeze(u.tenant(), id, output);
    audit(u, id, "RUN", "input sha256=" + Checks.hash(input));
    return detail(u, id);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @GetMapping("/runs")
  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public Object list(
      @RequestHeader(value = "Authorization", required = false) String h,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "") String state,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "all") String scope,
      @RequestParam(defaultValue = "") String gate) {
    var u = auth.current(h);
    Checks.must(
        page >= 1
            && page <= 100000
            && Set.of("", "READY", "APPROVED", "REJECTED", "WITHDRAWN").contains(state),
        "筛选或页码错误");
    Checks.must(
        q.length() <= 100 && Set.of("all", "mine", "reviewable").contains(scope), "标题或业务范围错误");
    if ("reviewable".equals(scope)) Auth.role(u, "REVIEWER");
    String where =
        " FROM work_run wr LEFT JOIN work_run_gate g ON wr.id=g.run_id AND wr.tenant=g.tenant WHERE"
            + " wr.tenant=? AND (?='' OR stage=?) AND LOCATE(?,title)>0"
            + ("mine".equals(scope)
                ? " AND actor_id=?"
                : "reviewable".equals(scope) ? " AND stage='READY' AND actor_id<>?" : "");
    var args = new ArrayList<Object>(List.of(u.tenant(), state, state, q.strip()));
    if (!"all".equals(scope)) args.add(u.id());
    Checks.must(
        Set.of("", "PASS", "FAIL", "UNKNOWN", "DISABLED", "UNCONFIGURED", "LEGACY").contains(gate),
        "门槛状态错误");
    if (!gate.isEmpty()) {
      where += " AND COALESCE(g.gate_status,'LEGACY')=?";
      args.add(gate);
    }
    long total = db.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
    args.add((page - 1) * 20);
    return Map.of(
        "total",
        total,
        "items",
        db.queryForList(
            "SELECT id,title,stage,revision,created_at,COALESCE(g.gate_status,'LEGACY') AS"
                + " gate_status"
                + where
                + " ORDER BY created_at DESC,id DESC LIMIT 20 OFFSET ?",
            args.toArray()));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @GetMapping("/runs/{id}")
  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Object get(
      @RequestHeader(value = "Authorization", required = false) String h, @PathVariable String id) {
    var u = auth.current(h);
    // Read-only screens never block a reviewer: recheck the version after assembling all related
    // rows.
    for (int attempt = 0; attempt < 3; attempt++) {
      var snapshot = detail(u, id);
      int current =
          db.queryForObject(
              "SELECT revision FROM work_run WHERE tenant=? AND id=?",
              Integer.class,
              u.tenant(),
              id);
      if (((Number) snapshot.get("revision")).intValue() == current) return snapshot;
    }
    throw new Failure(409, "记录正在更新，请重新打开详情");
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  Map<String, Object> detail(User u, String id) {
    var records =
        db.queryForList(
            "SELECT id,title,actor_id,stage,revision,input_json,result_json,created_at FROM"
                + " work_run WHERE tenant=? AND id=?",
            u.tenant(),
            id);
    if (records.isEmpty()) throw new Failure(404, "记录不存在");
    var r = new LinkedHashMap<>(records.getFirst());
    r.put("input", json.readValue((String) r.remove("input_json"), Map.class));
    r.put("result", json.readValue((String) r.remove("result_json"), Map.class));
    r.put(
        "history",
        db.queryForList(
            "SELECT actor,action,note,created_at FROM work_audit WHERE tenant=? AND subject=? ORDER"
                + " BY seq",
            u.tenant(),
            id));
    var origins =
        db.queryForList(
            "SELECT source_id,source_revision,reason,changes_json FROM work_correction WHERE"
                + " tenant=? AND child_id=?",
            u.tenant(),
            id);
    if (!origins.isEmpty()) {
      var origin = new LinkedHashMap<>(origins.getFirst());
      origin.put("changes", json.readValue((String) origin.remove("changes_json"), List.class));
      r.put("correction", origin);
    }
    var next =
        db.queryForList(
            "SELECT child_id FROM work_correction WHERE tenant=? AND source_id=?",
            String.class,
            u.tenant(),
            id);
    if (!next.isEmpty()) r.put("correctedBy", next.getFirst());
    var gate = gates.snapshot(u.tenant(), id);
    if (gate != null) r.put("gate", gate);
    return r;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @PostMapping("/runs/{id}/withdraw")
  @Transactional
  public Map<String, Object> withdraw(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @PathVariable String id,
      @RequestBody Withdrawal request) {
    var user = auth.current(authorization);
    Auth.role(user, "OPERATOR");
    var run = detail(user, id);
    if (!user.id().equals(run.get("actor_id"))) throw new Failure(403, "仅提交人可撤回自己的批次");
    Checks.must(
        request.note() != null && !request.note().isBlank() && request.note().length() <= 1000,
        "请填写撤回原因（最多 1000 字）");
    require(
        db.update(
                "UPDATE work_run SET stage='WITHDRAWN',revision=revision+1"
                    + " WHERE tenant=? AND id=? AND actor_id=? AND stage='READY' AND revision=?",
                user.tenant(),
                id,
                user.id(),
                request.version())
            == 1,
        "批次已办理或版本变化，请刷新");
    audit(user, id, "READY -> WITHDRAWN", request.note().strip());
    return detail(user, id);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public record Withdrawal(int version, String note) {}

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @PostMapping("/runs/{id}/review")
  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Object review(
      @RequestHeader(value = "Authorization", required = false) String h,
      @PathVariable String id,
      @RequestBody Review req) {
    var u = routing.lockedReviewer(h, id);
    routing.allowDecision(u, id);
    var r = detail(u, id);
    Checks.must(
        req.state() != null
            && Set.of("APPROVED", "REJECTED").contains(req.state())
            && req.note() != null
            && !req.note().isBlank()
            && req.note().length() <= 1000,
        "需要有效决定与复核意见");
    if (u.id().equals(r.get("actor_id"))) throw new Failure(403, "禁止自审");
    boolean exception =
        "APPROVED".equals(req.state())
            && r.get("gate") instanceof Map<?, ?> g
            && Set.of("FAIL", "UNKNOWN").contains(g.get("status"));
    if (exception)
      Checks.must(
          req.exceptionNote() != null
              && !req.exceptionNote().isBlank()
              && req.exceptionNote().length() <= 800,
          "未达标或无法判定，放行须另填 1–800 字例外依据");
    else
      Checks.must(
          req.exceptionNote() == null || req.exceptionNote().isBlank(), "只有未达标或无法判定的批次审批通过时可填例外依据");
    require(
        db.update(
                "UPDATE work_run SET stage=?,revision=revision+1 WHERE tenant=? AND id=? AND"
                    + " stage='READY' AND revision=?",
                req.state(),
                u.tenant(),
                id,
                req.version())
            == 1,
        "状态或版本已变化");
    if (exception) audit(u, id, "GATE_EXCEPTION", req.exceptionNote().strip());
    audit(u, id, "READY -> " + req.state(), req.note());
    return detail(u, id);
  }
}
