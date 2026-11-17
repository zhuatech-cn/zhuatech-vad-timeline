package cn.zhuatech.vadtimeline;

import java.sql.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Check the selected schema before allowing any migration DDL. 上海如静知华信息科技有限公司 ·
 * https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
@Configuration(proxyBeanMethods = false)
class DatabaseIdentity {
  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Bean
  FlywayMigrationStrategy guardedMigrations(
      @Value("${app.require-existing-workspace:false}") boolean requireExisting) {
    return flyway -> {
      var cfg = flyway.getConfiguration();
      if (cfg.getSchemas().length > 0 || cfg.getDefaultSchema() != null)
        throw new IllegalStateException("请通过 JDBC URL 选择本项目独立 schema；不支持另行指定 Flyway schemas");
      try (var c = cfg.getDataSource().getConnection()) {
        check(c, requireExisting);
      } catch (SQLException ex) {
        throw new IllegalStateException("无法核对数据库身份；未执行迁移。SQLState=" + ex.getSQLState());
      }
      flyway.migrate();
    };
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static void check(Connection c, boolean requireExisting) throws SQLException {
    int count = 0;
    boolean identity = false, history = false;
    try (var s =
            c.prepareStatement(
                "SELECT table_name FROM information_schema.tables WHERE table_schema=SCHEMA()");
        var rows = s.executeQuery()) {
      while (rows.next()) {
        count++;
        String name = rows.getString(1);
        identity |= "workspace_identity".equalsIgnoreCase(name);
        history |= "flyway_schema_history".equalsIgnoreCase(name);
      }
    }
    if (count == 0) {
      if (requireExisting) throw new IllegalStateException("仅打开已有库：目标 schema 为空，未初始化；请核对库名和恢复位置");
      return;
    }
    if (!identity || !history) throw new IllegalStateException("目标 schema 非空但不是本项目完整工作库，未执行迁移");
    try (var s =
            c.prepareStatement("SELECT project_code FROM workspace_identity WHERE singleton_id=1");
        var rows = s.executeQuery()) {
      if (!rows.next() || !Recovery.PROJECT.equals(rows.getString(1)) || rows.next())
        throw new IllegalStateException("数据库项目标识不符，未执行迁移；请核对本项目独立 schema");
    }
  }
}
