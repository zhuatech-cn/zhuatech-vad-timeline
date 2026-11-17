/* 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
package cn.zhuatech.vadtimeline;

import static cn.zhuatech.vadtimeline.Model.*;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

/**
 * Tenant-scoped review ownership and append-only routing history. 上海如静知华信息科技有限公司 ·
 * https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
@RestController
@RequestMapping("/api")
public class ReviewRouting {
  private final JdbcTemplate db;
  private final Auth auth;
  private final ObjectMapper json;
  private static final String JOIN =
      " FROM work_run w LEFT JOIN review_route a ON a.tenant=w.tenant AND a.run_id=w.id "
          + " LEFT JOIN app_user u ON u.tenant=w.tenant AND u.id=a.owner_id ";
  private static final String STALE =
      " (a.owner_id IS NOT NULL AND (u.id IS NULL OR u.active=FALSE OR u.role NOT IN"
          + " ('REVIEWER','ADMIN'))) ";

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public ReviewRouting(JdbcTemplate db, Auth auth, ObjectMapper json) {
    this.db = db;
    this.auth = auth;
    this.json = json;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public record Change(Integer expectedVersion, String action, String target, String note) {}

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private User reviewer(String h) {
    var user = auth.current(h);
    Auth.role(user, "REVIEWER");
    return user;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  User lockedReviewer(String h, String id) {
    var user = reviewer(h);
    db.queryForObject(
        "SELECT tenant FROM tenant_guard WHERE tenant=? FOR UPDATE", String.class, user.tenant());
    var verified = reviewer(h);
    if (!user.id().equals(verified.id()) || !user.tenant().equals(verified.tenant()))
      throw new Failure(403, "账号已发生变化");
    if (db.queryForList(
            "SELECT id FROM work_run WHERE tenant=? AND id=? FOR UPDATE",
            String.class,
            verified.tenant(),
            id)
        .isEmpty()) throw new Failure(404, "批次不存在");
    return verified;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  void allowDecision(User user, String id) {
    var assignee =
        db.queryForList(
            "SELECT owner_id FROM review_route WHERE tenant=? AND run_id=? AND owner_id IS NOT"
                + " NULL",
            String.class,
            user.tenant(),
            id);
    if (!assignee.isEmpty() && !assignee.getFirst().equals(user.id()))
      throw new Failure(409, "本批次已由其他人负责，请通过复核队列交接后办理");
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @GetMapping("/review-queue")
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Object queue(
      @RequestHeader(value = "Authorization", required = false) String h,
      @RequestParam(defaultValue = "OPEN") String scope,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "") String cursor,
      @RequestParam(defaultValue = "0") int minWaitMinutes) {
    var user = reviewer(h);
    Checks.must(
        Set.of("OPEN", "MINE", "TEAM", "STALE").contains(scope)
            && q.length() <= 100
            && page >= 1
            && page <= 100000
            && (cursor.isEmpty() || page == 1)
            && minWaitMinutes >= 0
            && minWaitMinutes <= 10080,
        "查询范围或页码无效");
    String criteria = JOIN + " WHERE w.tenant=? AND w.stage='READY' AND LOCATE(?,w.title)>0";
    var params = new ArrayList<Object>(List.of(user.tenant(), q.strip()));
    switch (scope) {
      case "OPEN" -> {
        criteria += " AND a.owner_id IS NULL AND w.actor_id<>?";
        params.add(user.id());
      }
      case "MINE" -> {
        criteria += " AND a.owner_id=?";
        params.add(user.id());
      }
      case "STALE" -> criteria += " AND " + STALE;
      default -> {}
    }
    var now =
        db.queryForObject("SELECT CURRENT_TIMESTAMP(6)", (rs, n) -> rs.getTimestamp(1).toInstant());
    var hash =
        Checks.hash(
            json.writeValueAsString(
                List.of(user.tenant(), user.id(), scope, q.strip(), minWaitMinutes)));
    var window = QueueWindow.open(cursor, hash, now);
    criteria += " AND w.created_at<=?";
    params.add(java.sql.Timestamp.from(window.asOf().minusSeconds(minWaitMinutes * 60L)));
    long total = db.queryForObject("SELECT COUNT(*)" + criteria, Long.class, params.toArray());
    if (window.after() != null) {
      criteria += " AND (w.created_at>? OR (w.created_at=? AND w.id>?))";
      var t = java.sql.Timestamp.from(window.after());
      params.add(t);
      params.add(t);
      params.add(window.id());
    }
    params.add(cursor.isEmpty() ? (page - 1) * 20 : 0);
    var columns = new org.springframework.jdbc.core.ColumnMapRowMapper();
    var rows =
        db.query(
            "SELECT w.id,w.title,w.revision,w.actor_id,w.created_at,COALESCE(a.route_version,0) AS"
                + " route_version,a.owner_id,a.owner_name,CASE WHEN "
                + STALE
                + " THEN 1 ELSE 0 END AS needs_handoff"
                + criteria
                + " ORDER BY w.created_at,w.id LIMIT 21 OFFSET ?",
            (rs, n) -> {
              var item = columns.mapRow(rs, n);
              var created = rs.getTimestamp("created_at").toInstant();
              item.put("created_at", created.toString());
              item.put(
                  "waitMinutes", java.time.Duration.between(created, window.asOf()).toMinutes());
              return item;
            },
            params.toArray());
    boolean more = rows.size() > 20;
    var items = more ? rows.subList(0, 20) : rows;
    String next = "";
    if (more) {
      var last = items.getLast();
      next =
          new QueueWindow(
                  hash,
                  window.asOf(),
                  java.time.Instant.parse((String) last.get("created_at")),
                  (String) last.get("id"))
              .encode();
    }
    return Map.of(
        "items",
        items,
        "page",
        page,
        "total",
        total,
        "asOf",
        window.asOf().toString(),
        "minWaitMinutes",
        minWaitMinutes,
        "firstCursor",
        new QueueWindow(hash, window.asOf(), null, "-").encode(),
        "nextCursor",
        next);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @GetMapping("/runs/{id}/routing")
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Object read(
      @RequestHeader(value = "Authorization", required = false) String h,
      @PathVariable String id,
      @RequestParam(defaultValue = "0") int before) {
    var user = reviewer(h);
    Checks.must(before >= 0, "交接游标无效");
    var rows =
        db.queryForList(
            "SELECT w.id,w.title,w.stage,w.actor_id,COALESCE(a.route_version,0) AS"
                + " route_version,a.owner_id,a.owner_name,CASE WHEN "
                + STALE
                + " THEN 1 ELSE 0 END AS needs_handoff"
                + JOIN
                + " WHERE w.tenant=? AND w.id=?",
            user.tenant(),
            id);
    if (rows.isEmpty()) throw new Failure(404, "批次不存在");
    int ceiling =
        ((Number) rows.getFirst().get("route_version"))
            .intValue(); // keep history aligned even when another event is committed concurrently
    var history =
        db.queryForList(
            "SELECT route_version,actor,action,before_owner,after_owner,note,created_at FROM"
                + " review_route_event WHERE tenant=? AND run_id=? AND route_version<=? AND (?=0 OR"
                + " route_version<?) ORDER BY route_version DESC LIMIT 21",
            user.tenant(),
            id,
            ceiling,
            before,
            before);
    boolean more = history.size() > 20;
    var items = more ? history.subList(0, 20) : history;
    return Map.of(
        "assignment",
        rows.getFirst(),
        "history",
        items,
        "nextBefore",
        more ? items.getLast().get("route_version") : 0);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @PostMapping("/runs/{id}/routing")
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Object change(
      @RequestHeader(value = "Authorization", required = false) String h,
      @PathVariable String id,
      @RequestHeader(value = "Idempotency-Key", defaultValue = "") String key,
      @RequestBody Change body) {
    var u = lockedReviewer(h, id);
    Checks.must(key.matches("[a-zA-Z0-9_-]{8,80}"), "需要有效的提交键");
    Checks.must(
        body != null
            && body.expectedVersion() != null
            && body.expectedVersion() >= 0
            && body.action() != null
            && Set.of("CLAIM", "ASSIGN", "HANDOFF", "RELEASE").contains(body.action()),
        "需要有效的动作及当前分派版本");
    String action = body.action(),
        target = Objects.toString(body.target(), "").strip(),
        note = Objects.toString(body.note(), "").strip();
    if (action.equals("ASSIGN")) Auth.role(u, "ADMIN");
    boolean targeted = action.equals("ASSIGN") || action.equals("HANDOFF");
    Checks.must(
        targeted ? (!target.isEmpty() && target.length() <= 80) : target.isEmpty(), "请核对接收人账号");
    Checks.must(
        note.length() <= 500 && (action.equals("CLAIM") || !note.isEmpty()),
        "交接或释放须记录原因（最多 500 字）");
    if (note.isEmpty()) note = "主动领取";
    String fingerprint =
        Checks.hash(
            json.writeValueAsString(List.of(id, body.expectedVersion(), action, target, note)));
    var prior =
        db.queryForList(
            "SELECT run_id,route_version,action,before_owner,after_owner,request_hash FROM"
                + " review_route_event WHERE tenant=? AND actor_id=? AND request_key=?",
            u.tenant(),
            u.id(),
            key);
    if (!prior.isEmpty()) {
      var rec = new LinkedHashMap<>(prior.getFirst());
      require(fingerprint.equals(rec.remove("request_hash")), "提交键已用于不同的交接请求");
      return rec;
    }
    var run =
        db.queryForMap(
            "SELECT actor_id,stage FROM work_run WHERE tenant=? AND id=?", u.tenant(), id);
    require("READY".equals(run.get("stage")), "只有待复核批次可更改分派");
    var owners =
        db.queryForList(
            "SELECT owner_id,owner_name,route_version FROM review_route WHERE tenant=? AND"
                + " run_id=?",
            u.tenant(),
            id);
    String previous =
        owners.isEmpty() ? "" : Objects.toString(owners.getFirst().get("owner_id"), "");
    String previousName =
        owners.isEmpty() ? "" : Objects.toString(owners.getFirst().get("owner_name"), "");
    int version =
        owners.isEmpty() ? 0 : ((Number) owners.getFirst().get("route_version")).intValue();
    require(body.expectedVersion() == version, "分派已变化，请刷新队列");
    String nextId = null, nextName = null;
    if (action.equals("CLAIM")) {
      if (u.id().equals(run.get("actor_id"))) throw new Failure(403, "不能领取自己提交的批次");
      require(previous.isEmpty(), "批次不在公共队列");
      nextId = u.id();
      nextName = u.username();
    } else {
      if (!action.equals("ASSIGN")) {
        require(!previous.isEmpty(), "当前无人负责");
        if (!previous.equals(u.id()) && !u.role().equals("ADMIN"))
          throw new Failure(403, "仅负责人或管理员可交接或释放");
      }
      if (targeted) {
        var candidates =
            db.queryForList(
                "SELECT id,username FROM app_user WHERE tenant=? AND username=? AND active=TRUE AND"
                    + " role IN ('ADMIN','REVIEWER')",
                u.tenant(),
                target);
        Checks.must(candidates.size() == 1, "接收人不是本企业有效复核员");
        nextId = (String) candidates.getFirst().get("id");
        nextName = (String) candidates.getFirst().get("username");
        Checks.must(!nextId.equals(run.get("actor_id")), "接收人不能是批次提交人");
        require(!nextId.equals(previous), "接收人与当前负责人相同");
      }
    }
    int next = version + 1;
    if (owners.isEmpty())
      db.update(
          "INSERT INTO review_route(tenant,run_id,route_version,owner_id,owner_name)"
              + " VALUES(?,?,?,?,?)",
          u.tenant(),
          id,
          next,
          nextId,
          nextName);
    else
      require(
          db.update(
                  "UPDATE review_route SET"
                      + " route_version=?,owner_id=?,owner_name=?,changed_at=CURRENT_TIMESTAMP(6)"
                      + " WHERE tenant=? AND run_id=? AND route_version=?",
                  next,
                  nextId,
                  nextName,
                  u.tenant(),
                  id,
                  version)
              == 1,
          "分派版本冲突");
    String after = Objects.toString(nextName, "");
    db.update(
        "INSERT INTO"
            + " review_route_event(tenant,run_id,route_version,actor_id,actor,action,before_owner,after_owner,note,request_key,request_hash)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        u.tenant(),
        id,
        next,
        u.id(),
        u.username(),
        action,
        previousName,
        after,
        note,
        key,
        fingerprint);
    db.update(
        "INSERT INTO work_audit(tenant,actor,subject,action,note) VALUES(?,?,?,?,?)",
        u.tenant(),
        u.username(),
        "route:" + id,
        "ROUTE_" + action,
        "v" + next + " · " + previousName + " -> " + after + " · " + note);
    return Map.of(
        "run_id",
        id,
        "route_version",
        next,
        "action",
        action,
        "before_owner",
        previousName,
        "after_owner",
        after);
  }
}
