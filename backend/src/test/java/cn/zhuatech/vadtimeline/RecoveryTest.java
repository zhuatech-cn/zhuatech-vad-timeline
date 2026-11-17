package cn.zhuatech.vadtimeline;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
class RecoveryTest {
  @TempDir Path temp;
  final Recovery tool = new Recovery("sa", "");

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  String url(Path dir) {
    return "jdbc:h2:file:" + dir.resolve("workspace") + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  Path fixture() throws Exception {
    var dir = Files.createDirectory(temp.resolve("original"));
    Flyway.configure().dataSource(url(dir), "sa", "").load().migrate();
    try (var c = DriverManager.getConnection(url(dir), "sa", "");
        var s = c.createStatement()) {
      s.executeUpdate("INSERT INTO tenant_guard VALUES('sample')");
      s.executeUpdate(
          "INSERT INTO app_user(id,tenant,username,password_hash,role,active)"
              + " VALUES('u','sample','test','not-a-real-hash','ADMIN',true)");
      s.executeUpdate("INSERT INTO auth_session VALUES('placeholder','u',CURRENT_TIMESTAMP)");
      s.executeUpdate(
          "INSERT INTO work_audit(tenant,actor,subject,action,note)"
              + " VALUES('sample','test','x','OLD','旧记录')");
    }
    return dir;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  int count(Path dir, String query) throws Exception {
    try (var c = DriverManager.getConnection(url(dir) + ";IFEXISTS=TRUE", "sa", "");
        var s = c.createStatement();
        var r = s.executeQuery(query)) {
      assertTrue(r.next());
      return r.getInt(1);
    }
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void independentRestoreKeepsAccountsAndHistoryButRevokesRestoredSessions() throws Exception {
    var source = fixture();
    var archive = temp.resolve("safe.zhbk");
    var dest = temp.resolve("新 副本");
    tool.backup(source, archive);
    Recovery.read(archive, OutputStream.nullOutputStream());
    tool.restore(archive, dest);
    assertEquals(1, count(dest, "SELECT COUNT(*) FROM app_user"));
    assertEquals(0, count(dest, "SELECT COUNT(*) FROM auth_session"));
    assertEquals(1, count(source, "SELECT COUNT(*) FROM auth_session"));
    assertEquals(2, count(dest, "SELECT COUNT(*) FROM work_audit"));
    assertEquals(
        1,
        count(
            dest,
            "SELECT COUNT(*) FROM work_audit WHERE action='RESTORE_LOCAL' AND tenant='sample'"));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void existingFilesAndWrongProjectsCannotBeOverwritten() throws Exception {
    var source = fixture();
    var snapshot = temp.resolve("safe.zhbk");
    tool.backup(source, snapshot);
    assertThrows(IOException.class, () -> tool.backup(source, snapshot));
    assertThrows(IOException.class, () -> tool.restore(snapshot, source));
    assertThrows(IOException.class, () -> tool.backup(source, source.resolve("internal.zhbk")));
    try (var c = DriverManager.getConnection(url(source), "sa", "");
        var s = c.createStatement()) {
      s.executeUpdate("UPDATE workspace_identity SET project_code='foreign-project'");
    }
    var foreign = temp.resolve("other.zhbk");
    assertThrows(IOException.class, () -> tool.backup(source, foreign));
    assertFalse(Files.exists(foreign));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void damagedOrIncompleteBackupIsRejectedBeforePublication() throws Exception {
    var source = fixture();
    var snapshot = temp.resolve("sample.zhbk");
    tool.backup(source, snapshot);
    var bytes = Files.readAllBytes(snapshot);
    bytes[bytes.length - 1] ^= 1;
    Files.write(snapshot, bytes);
    var dest = temp.resolve("restored");
    assertThrows(IOException.class, () -> tool.restore(snapshot, dest));
    assertFalse(Files.exists(dest));
    Files.write(snapshot, java.util.Arrays.copyOf(bytes, bytes.length / 2));
    assertThrows(IOException.class, () -> Recovery.read(snapshot, OutputStream.nullOutputStream()));
    assertFalse(Files.exists(dest));
  }
}
