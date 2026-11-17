/* 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.vadtimeline;

import static cn.zhuatech.vadtimeline.Model.*;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
 *
 * <p>商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
@Service
@org.springframework.core.annotation.Order(0)
public class Auth implements ApplicationRunner {
  final JdbcTemplate db;
  final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
  final String bootstrap;
  private volatile boolean ready;

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public void requireReady() {
    if (!ready) throw new Failure(503, "账号仍在初始化，请稍后重试");
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public Auth(JdbcTemplate db, @Value("${app.admin-password:}") String bootstrap) {
    this.db = db;
    this.bootstrap = bootstrap;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public static String hash(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
   *
   * <p>商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
   */
  public void run(org.springframework.boot.ApplicationArguments args) {
    if (!Recovery.PROJECT.equals(
        db.queryForObject(
            "SELECT project_code FROM workspace_identity WHERE singleton_id=1", String.class)))
      throw new IllegalStateException("数据库项目标识不符，服务不会就绪");
    if (db.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class) > 0) {
      ready = true;
      return;
    }
    String initial = bootstrap;
    if (initial.isEmpty() && System.console() != null) {
      char[] entered = System.console().readPassword("初始化管理员 admin，请设置密码（至少 12 个字符）：");
      if (entered != null) {
        try {
          initial = new String(entered);
        } finally {
          Arrays.fill(entered, '\0');
        }
      }
    }
    if (!validNewPassword(initial))
      throw new IllegalStateException("未初始化：请设置 ADMIN_PASSWORD 后重新启动，无需删除已有数据库");
    if (db.queryForObject("SELECT COUNT(*) FROM tenant_guard WHERE tenant='default'", Integer.class)
        == 0) db.update("INSERT INTO tenant_guard VALUES('default')");
    db.update(
        "INSERT INTO app_user(id,tenant,username,password_hash,role,active) VALUES(?,?,?,?,?,true)",
        UUID.randomUUID().toString(),
        "default",
        "admin",
        encoder.encode(initial),
        "ADMIN");
    ready = true;
  }

  /**
   * 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
   *
   * <p>商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
   */
  @org.springframework.transaction.annotation.Transactional(noRollbackFor = Failure.class)
  public Map<String, Object> login(String name, String password) {
    requireReady();
    if (name == null
        || password == null
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new Failure(401, "账号或密码错误");
    var rows =
        db.queryForList(
            "SELECT *, CASE WHEN locked_until>CURRENT_TIMESTAMP THEN 1 ELSE 0 END AS lock_live FROM"
                + " app_user WHERE username=? FOR UPDATE",
            name);
    if (rows.isEmpty()) {
      encoder.matches(password, "$2a$10$abcdefghijklmnopqrstuu8K.f.a7NEsLoHdLF0EmUeZTGV7r.LJgW");
      throw new Failure(401, "账号或密码错误");
    }
    var r = rows.getFirst();
    String id = r.get("id").toString();
    verifyPassword(r, id, password, 401);
    byte[] random = new byte[32];
    new SecureRandom().nextBytes(random);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    db.update("DELETE FROM auth_session WHERE expires_at<?", Timestamp.from(Instant.now()));
    db.update(
        "INSERT INTO auth_session VALUES(?,?,?)",
        hash(token),
        id,
        Timestamp.from(Instant.now().plusSeconds(28800)));
    return Map.of(
        "token",
        token,
        "user",
        new User(id, r.get("tenant").toString(), name, r.get("role").toString()));
  }

  /**
   * 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
   *
   * <p>商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
   */
  private void verifyPassword(Map<String, Object> r, String id, String password, int status) {
    if (!Boolean.TRUE.equals(r.get("active"))) throw new Failure(status, "账号或密码错误");
    Object lock = r.get("locked_until");
    if (((Number) r.get("lock_live")).intValue() != 0) throw new Failure(429, "登录尝试过多，请稍后重试");
    if (lock != null) {
      // The timed lock has expired: the next attempt starts a fresh window.
      db.update("UPDATE app_user SET failures=0,locked_until=NULL WHERE id=?", id);
    }
    if (!encoder.matches(password, r.get("password_hash").toString())) {
      db.update("UPDATE app_user SET failures=failures+1 WHERE id=?", id);
      if (db.queryForObject("SELECT failures FROM app_user WHERE id=?", Integer.class, id) >= 5)
        db.update(
            "UPDATE app_user SET locked_until={fn"
                + " TIMESTAMPADD(SQL_TSI_SECOND,900,CURRENT_TIMESTAMP)} WHERE id=?",
            id);
      throw new Failure(status, "账号或密码错误");
    }
    db.update("UPDATE app_user SET failures=0,locked_until=NULL WHERE id=?", id);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private static boolean validNewPassword(String p) {
    return p != null
        && !p.isBlank()
        && p.codePointCount(0, p.length()) >= 12
        && p.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public void rotatePassword(User user, String current, String next) {
    if (current == null
        || current.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
        || !validNewPassword(next)
        || next.equals(current)) throw new Failure(400, "新密码须与原密码不同，至少 12 个字符且最多 72 字节");
    var rows =
        db.queryForList(
            "SELECT *, CASE WHEN locked_until>CURRENT_TIMESTAMP THEN 1 ELSE 0 END AS lock_live FROM"
                + " app_user WHERE tenant=? AND id=? FOR UPDATE",
            user.tenant(),
            user.id());
    if (rows.isEmpty()) throw new Failure(401, "账号已不可用");
    verifyPassword(rows.getFirst(), user.id(), current, 403);
    db.update(
        "UPDATE app_user SET password_hash=? WHERE tenant=? AND id=?",
        encoder.encode(next),
        user.tenant(),
        user.id());
    db.update("DELETE FROM auth_session WHERE user_id=?", user.id());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public User current(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer "))
      throw new Failure(401, "请先登录");
    var users =
        db.query(
            "SELECT u.id,u.tenant,u.username,u.role FROM auth_session s JOIN app_user u ON"
                + " s.user_id=u.id WHERE s.token_hash=? AND s.expires_at>? AND u.active=true",
            (r, n) -> new User(r.getString(1), r.getString(2), r.getString(3), r.getString(4)),
            hash(authorization.substring(7)),
            Timestamp.from(Instant.now()));
    if (users.isEmpty()) throw new Failure(401, "登录已失效");
    return users.getFirst();
  }

  /**
   * 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
   *
   * <p>商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
   */
  public void logout(String authorization) {
    if (authorization != null && authorization.startsWith("Bearer "))
      db.update("DELETE FROM auth_session WHERE token_hash=?", hash(authorization.substring(7)));
  }

  /**
   * 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
   *
   * <p>商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
   */
  public List<Map<String, Object>> users(User u) {
    role(u, "ADMIN");
    return db.queryForList(
        "SELECT id,username,role,active,CASE WHEN locked_until>CURRENT_TIMESTAMP THEN 1 ELSE 0 END"
            + " AS login_locked FROM app_user WHERE tenant=? ORDER BY username",
        u.tenant());
  }

  /**
   * 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
   *
   * <p>商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
   */
  public void createUser(User u, Map<String, String> input) {
    if (input.containsValue(null) || !Set.of("username", "password", "role").equals(input.keySet()))
      throw new Failure(400, "需要账号、密码及角色");
    role(u, "ADMIN");
    String name = input.getOrDefault("username", ""),
        pass = input.getOrDefault("password", ""),
        role = input.getOrDefault("role", "");
    if (!name.matches("[a-zA-Z0-9_.-]{3,50}")
        || !validNewPassword(pass)
        || !Set.of("ADMIN", "REVIEWER", "OPERATOR", "VIEWER").contains(role))
      throw new Failure(400, "账号、密码或角色格式错误");
    db.update(
        "INSERT INTO app_user(id,tenant,username,password_hash,role,active) VALUES(?,?,?,?,?,true)",
        UUID.randomUUID().toString(),
        u.tenant(),
        name,
        encoder.encode(pass),
        role);
  }

  /**
   * 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
   *
   * <p>商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
   */
  public User managementUser(String header) {
    User first = current(header);
    role(first, "ADMIN");
    db.queryForObject(
        "SELECT tenant FROM tenant_guard WHERE tenant=? FOR UPDATE", String.class, first.tenant());
    // Authorization may have changed while waiting for the tenant lock.
    User confirmed = current(header);
    role(confirmed, "ADMIN");
    return confirmed;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public void unlock(User user, String id) {
    role(user, "ADMIN");
    require(!user.id().equals(id), "请由另一位管理员核实后解锁");
    var matches =
        db.queryForList(
            "SELECT id FROM app_user WHERE tenant=? AND id=? FOR UPDATE", user.tenant(), id);
    if (matches.isEmpty()) throw new Failure(404, "账号不存在");
    require(
        db.update(
                "UPDATE app_user SET failures=0,locked_until=NULL WHERE tenant=? AND id=? AND"
                    + " active=true AND locked_until>CURRENT_TIMESTAMP",
                user.tenant(),
                id)
            == 1,
        "账号未处于启用且锁定状态，请刷新");
    db.update("DELETE FROM auth_session WHERE user_id=?", id);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public String changeUser(User u, String id, Map<String, String> input) {
    if (input.isEmpty()
        || input.containsValue(null)
        || !Set.of("role", "active", "password").containsAll(input.keySet()))
      throw new Failure(400, "账号变更字段错误");
    role(u, "ADMIN");
    var users =
        db.queryForList(
            "SELECT role,active FROM app_user WHERE tenant=? AND id=? FOR UPDATE", u.tenant(), id);
    if (users.isEmpty()) throw new Failure(404, "账号不存在");
    if (input.containsKey("active") && !Set.of("true", "false").contains(input.get("active")))
      throw new Failure(400, "active 格式错误");
    String nextRole = input.getOrDefault("role", users.getFirst().get("role").toString());
    if (!Set.of("ADMIN", "REVIEWER", "OPERATOR", "VIEWER").contains(nextRole))
      throw new Failure(400, "角色错误");
    boolean active =
        input.containsKey("active")
            ? Boolean.parseBoolean(input.get("active"))
            : Boolean.TRUE.equals(users.getFirst().get("active"));
    require(!id.equals(u.id()) || (active && nextRole.equals("ADMIN")), "不可停用或降级自己的管理账号");
    String previousRole = users.getFirst().get("role").toString();
    boolean previousActive = Boolean.TRUE.equals(users.getFirst().get("active"));
    if (input.containsKey("password") && !validNewPassword(input.get("password")))
      throw new Failure(400, "密码须至少 12 位且不超过 72 字节");
    if (previousRole.equals(nextRole) && active == previousActive && !input.containsKey("password"))
      return "";
    if (previousActive && previousRole.equals("ADMIN") && (!active || !nextRole.equals("ADMIN")))
      require(
          db.queryForObject(
                  "SELECT COUNT(*) FROM app_user WHERE tenant=? AND active=true AND role='ADMIN'",
                  Integer.class,
                  u.tenant())
              > 1,
          "必须保留至少一个启用的管理员");
    db.update(
        "UPDATE app_user SET role=?,active=? WHERE tenant=? AND id=?",
        nextRole,
        active,
        u.tenant(),
        id);
    String password = input.getOrDefault("password", "");
    if (!password.isEmpty()) {
      if (!validNewPassword(password)) throw new Failure(400, "密码须至少 12 位且不超过 72 字节");
      db.update(
          "UPDATE app_user SET password_hash=?,failures=0,locked_until=NULL WHERE id=?",
          encoder.encode(password),
          id);
    }
    db.update("DELETE FROM auth_session WHERE user_id=?", id);
    return "role="
        + previousRole
        + " -> "
        + nextRole
        + "; active="
        + previousActive
        + " -> "
        + active
        + (input.containsKey("password") ? "; password reset" : "");
  }

  /**
   * 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
   *
   * <p>商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
   */
  public static void role(User user, String role) {
    if (role.equals("OPERATOR") && !user.role().equals("VIEWER")) return;
    if (user.role().equals("ADMIN") || user.role().equals(role)) return;
    throw new Failure(403, "当前角色没有此操作权限");
  }
}
