package cn.zhuatech.vadtimeline;

import static cn.zhuatech.vadtimeline.GateRules.*;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
@Service
final class GateStore {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  GateStore(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public record Change(Integer expectedVersion, String reason, List<Rule> rules) {}

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public record Policy(
      int version, List<Rule> rules, String author, String reason, String publishedAt) {}

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public record Preview(Integer expectedVersion, List<Rule> rules, int limit) {}

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  Map<String, Object> preview(Model.User u, Preview request) {
    Auth.role(u, "ADMIN");
    Checks.must(
        request != null
            && request.expectedVersion() != null
            && request.expectedVersion() >= 0
            && request.limit() >= 1
            && request.limit() <= 10,
        "需要当前规则版本，试算范围为最近 1 至 10 个批次");
    var proposed = GateRules.normalize(request.rules());
    var current = read(u.tenant());
    Model.require(current.version() == request.expectedVersion(), "规则版本已变化，请刷新后重新试算");
    var rows = new ArrayList<Map<String, Object>>();
    var counts = new LinkedHashMap<String, Integer>();
    for (var k : List.of("PASS", "FAIL", "UNKNOWN", "DISABLED")) counts.put(k, 0);
    db.query(
        "SELECT w.id,w.title,w.stage,w.result_json,COALESCE(g.gate_status,'LEGACY') stored_status"
            + " FROM work_run w LEFT JOIN work_run_gate g ON w.tenant=g.tenant AND w.id=g.run_id"
            + " WHERE w.tenant=? ORDER BY w.created_at DESC,w.id DESC LIMIT ?",
        (org.springframework.jdbc.core.RowCallbackHandler)
            rs -> {
              Map<String, Object> result = json.readValue(rs.getString("result_json"), Map.class);
              var decision =
                  GateRules.evaluate(Math.addExact(current.version(), 1), proposed, result);
              String state = (String) decision.get("status");
              counts.merge(state, 1, Integer::sum);
              rows.add(
                  Map.of(
                      "id",
                      rs.getString("id"),
                      "title",
                      rs.getString("title"),
                      "stage",
                      rs.getString("stage"),
                      "storedStatus",
                      rs.getString("stored_status"),
                      "projectedStatus",
                      state,
                      "checks",
                      decision.get("checks")));
            },
        u.tenant(),
        request.limit());
    return Map.of(
        "currentVersion",
        current.version(),
        "previewOnly",
        true,
        "counts",
        counts,
        "rows",
        rows,
        "limit",
        request.limit());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  Policy read(String tenant) {
    var rows =
        db.queryForList(
            "SELECT policy_version,rules_json,author,reason,created_at FROM gate_policy WHERE"
                + " tenant=? ORDER BY policy_version DESC LIMIT 1",
            tenant);
    return rows.isEmpty() ? new Policy(0, List.of(), "", "", "") : policy(rows.getFirst());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private Policy policy(Map<String, Object> row) {
    return new Policy(
        ((Number) row.get("policy_version")).intValue(),
        List.of(json.readValue((String) row.get("rules_json"), Rule[].class)),
        (String) row.get("author"),
        (String) row.get("reason"),
        row.get("created_at").toString());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  Object history(String tenant, int before, int limit) {
    Checks.must(before >= 0 && limit >= 1 && limit <= 50, "游标或条数无效");
    var rows =
        db.queryForList(
            "SELECT policy_version,rules_json,author,reason,created_at FROM gate_policy WHERE"
                + " tenant=? AND (?=0 OR policy_version<?) ORDER BY policy_version DESC LIMIT ?",
            tenant,
            before,
            before,
            limit + 1);
    boolean more = rows.size() > limit;
    var items = rows.stream().limit(limit).map(this::policy).toList();
    return Map.of(
        "items", items, "nextBefore", more ? items.getLast().version() : 0, "hasMore", more);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  Policy publish(Model.User u, Change change, String key) {
    Checks.must(
        change != null
            && change.expectedVersion() != null
            && change.expectedVersion() >= 0
            && change.reason() != null
            && !change.reason().isBlank()
            && change.reason().length() <= 500,
        "需要当前版本及 1–500 字变更原因");
    Checks.must(key.matches("[a-zA-Z0-9_-]{8,80}"), "需要 8–80 位 Idempotency-Key");
    var rules = normalize(change.rules());
    String reason = change.reason().strip();
    String encoded = json.writeValueAsString(rules);
    String hash =
        Checks.hash(json.writeValueAsString(List.of(change.expectedVersion(), reason, encoded)));
    db.queryForObject(
        "SELECT tenant FROM tenant_guard WHERE tenant=? FOR UPDATE", String.class, u.tenant());
    var prior =
        db.queryForList(
            "SELECT policy_version,rules_json,author,reason,created_at,request_hash FROM"
                + " gate_policy WHERE tenant=? AND author_id=? AND request_key=?",
            u.tenant(),
            u.id(),
            key);
    if (!prior.isEmpty()) {
      Model.require(hash.equals(prior.getFirst().get("request_hash")), "同一提交键的策略已变化");
      return policy(prior.getFirst());
    }
    var prev = read(u.tenant());
    Model.require(prev.version() == change.expectedVersion(), "规则版本已变化，请刷新后重新核对");
    Checks.must(!normalize(prev.rules()).equals(rules), "规则未变化");
    int next = Math.addExact(prev.version(), 1);
    db.update(
        "INSERT INTO"
            + " gate_policy(tenant,policy_version,author_id,author,reason,rules_json,request_key,request_hash)"
            + " VALUES(?,?,?,?,?,?,?,?)",
        u.tenant(),
        next,
        u.id(),
        u.username(),
        reason,
        encoded,
        key,
        hash);
    db.update(
        "INSERT INTO work_audit(tenant,actor,subject,action,note) VALUES(?,?,?,?,?)",
        u.tenant(),
        u.username(),
        "policy:" + next,
        "PUBLISH_GATE_POLICY",
        "版本 " + prev.version() + " -> " + next + "；" + reason);
    return read(u.tenant());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  void freeze(String tenant, String run, Map<String, Object> result) {
    var p = read(tenant);
    var decision = evaluate(p.version(), p.rules(), result);
    db.update(
        "INSERT INTO work_run_gate(tenant,run_id,policy_version,gate_status,decision_json)"
            + " VALUES(?,?,?,?,?)",
        tenant,
        run,
        p.version(),
        decision.get("status"),
        json.writeValueAsString(decision));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @SuppressWarnings("unchecked")
  Map<String, Object> snapshot(String tenant, String run) {
    var records =
        db.queryForList(
            "SELECT decision_json FROM work_run_gate WHERE tenant=? AND run_id=?",
            String.class,
            tenant,
            run);
    return records.isEmpty() ? null : json.readValue(records.getFirst(), Map.class);
  }
}
