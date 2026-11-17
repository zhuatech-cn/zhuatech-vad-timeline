package cn.zhuatech.vadtimeline;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
class DatabaseIdentityTest {
  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private Connection fresh() throws SQLException {
    return DriverManager.getConnection(
        "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void emptySchemaOnlyAllowedForExplicitlyNewInstallAndCheckDoesNotCreateTables() throws Exception {
    try (var c = fresh()) {
      DatabaseIdentity.check(c, false);
      assertThrows(IllegalStateException.class, () -> DatabaseIdentity.check(c, true));
      try (var s = c.createStatement();
          var r =
              s.executeQuery(
                  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=SCHEMA()")) {
        assertTrue(r.next());
        assertEquals(0, r.getInt(1));
      }
    }
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void unrelatedTablesOrDifferentProjectAreLeftUntouched() throws Exception {
    try (var c = fresh();
        var s = c.createStatement()) {
      s.execute("CREATE TABLE unrelated(v INT)");
      s.execute("INSERT INTO unrelated VALUES(7)");
      assertThrows(IllegalStateException.class, () -> DatabaseIdentity.check(c, false));
      s.execute(
          "CREATE TABLE workspace_identity(singleton_id INT PRIMARY KEY,project_code VARCHAR(80))");
      s.execute("CREATE TABLE flyway_schema_history(v INT)");
      s.execute("INSERT INTO workspace_identity VALUES(1,'unrelated-project')");
      assertThrows(IllegalStateException.class, () -> DatabaseIdentity.check(c, true));
      try (var data = s.executeQuery("SELECT v FROM unrelated")) {
        assertTrue(data.next());
        assertEquals(7, data.getInt(1));
      }
      try (var q =
          c.prepareStatement("UPDATE workspace_identity SET project_code=? WHERE singleton_id=1")) {
        q.setString(1, Recovery.PROJECT);
        q.executeUpdate();
      }
      DatabaseIdentity.check(c, true);
    }
  }
}
