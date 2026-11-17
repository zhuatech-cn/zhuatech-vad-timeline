package cn.zhuatech.vadtimeline;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.util.*;
import java.util.zip.ZipFile;

/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
final class Recovery {
  static final String PROJECT = "zhuatech-vad-timeline";
  static final long MAX = 8L << 30;
  static final String FILE = "workspace.mv.db";
  private final String user, password;

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  Recovery(String user, String password) {
    this.user = user;
    this.password = password;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private static void privateFile(Path path) throws IOException {
    if (Files.getFileStore(path.getParent()).supportsFileAttributeView("posix"))
      Files.createFile(
          path,
          java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
              java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
    else Files.createFile(path); // On Windows inherits the operator-controlled directory ACL.
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static void cli(String[] args) throws Exception {
    if (args.length < 2
        || System.getenv("DB_URL") != null
        || System.getenv("SPRING_DATASOURCE_URL") != null)
      throw new IOException("仅支持显式目录中的本地 H2。使用 MySQL 或自定义 JDBC URL 时请采用数据库原生备份。");
    var runner =
        new Recovery(
            System.getenv().getOrDefault("DB_USER", "sa"),
            System.getenv().getOrDefault("DB_PASSWORD", ""));
    switch (args[1]) {
      case "backup" -> {
        if (args.length != 4) throw new IOException("maintenance backup <数据目录> <新备份文件>");
        runner.backup(Path.of(args[2]), Path.of(args[3]));
      }
      case "verify" -> {
        if (args.length != 3) throw new IOException("maintenance verify <备份文件>");
        read(Path.of(args[2]), OutputStream.nullOutputStream());
      }
      case "restore" -> {
        if (args.length != 4) throw new IOException("maintenance restore <备份文件> <不存在的新数据目录>");
        runner.restore(Path.of(args[2]), Path.of(args[3]));
      }
      default -> throw new IOException("支持的离线操作：backup、verify、restore。");
    }
    System.out.println(PROJECT + "：" + args[1] + " 完成");
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private Connection connect(Path dir) throws SQLException, IOException {
    String base = dir.toRealPath().resolve("workspace").toString();
    if (base.contains(";")) throw new IOException("H2 目录路径不可含分号");
    return DriverManager.getConnection(
        "jdbc:h2:file:" + base + ";IFEXISTS=TRUE;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        user,
        password);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private static void identify(Connection c) throws SQLException, IOException {
    try (var stmt = c.createStatement();
        var r =
            stmt.executeQuery("SELECT project_code FROM workspace_identity WHERE singleton_id=1")) {
      if (!r.next() || !PROJECT.equals(r.getString(1))) throw new IOException("数据库不属于本项目");
    }
    try (var stmt = c.createStatement();
        var r =
            stmt.executeQuery(
                "SELECT version FROM flyway_schema_history WHERE success=TRUE AND version IS NOT"
                    + " NULL ORDER BY installed_rank DESC LIMIT 1")) {
      if (!r.next() || !Set.of("3", "4", "5", "6", "7").contains(r.getString(1)))
        throw new IOException("当前工具仅支持已迁移到 V3–V7 的文件库");
    }
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  void backup(Path source, Path dest) throws Exception {
    Path dir = source.toRealPath();
    if (!Files.isRegularFile(dir.resolve(FILE), LinkOption.NOFOLLOW_LINKS))
      throw new IOException("找不到本地文件库");
    dest = dest.toAbsolutePath().normalize();
    Path parent = dest.getParent().toRealPath();
    if (Files.exists(dest, LinkOption.NOFOLLOW_LINKS) || parent.startsWith(dir))
      throw new IOException("备份必须是数据目录以外的全新文件");
    Path stage = Files.createTempDirectory(parent, ".zh-backup-");
    Path snapshot = stage.resolve("snapshot.zip"), packed = stage.resolve("result.tmp");
    try {
      try (Connection c = connect(dir)) {
        identify(c);
        try (var stmt = c.prepareStatement("BACKUP TO ?")) {
          stmt.setString(1, snapshot.toString());
          stmt.execute();
        }
      }
      privateFile(packed);
      try (ZipFile zip = new ZipFile(snapshot.toFile());
          var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(packed)))) {
        var entry = zip.getEntry(FILE);
        if (entry == null || zip.size() != 1 || entry.getSize() <= 0 || entry.getSize() > MAX)
          throw new IOException("不支持的 H2 快照内容或大小");
        out.writeUTF("ZH-H2-1");
        out.writeUTF(PROJECT);
        out.writeLong(entry.getSize());
        try (var in = zip.getInputStream(entry)) {
          out.write(copy(in, out, entry.getSize()));
        }
      }
      try (var ch = java.nio.channels.FileChannel.open(packed, StandardOpenOption.WRITE)) {
        ch.force(true);
      }
      // Same-filesystem, atomic publication with no overwrite, including competing backup commands.
      Files.createLink(dest, packed);
    } finally {
      Files.deleteIfExists(packed);
      Files.deleteIfExists(snapshot);
      Files.deleteIfExists(stage);
    }
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  void restore(Path backup, Path destination) throws Exception {
    Path dest = destination.toAbsolutePath().normalize();
    if (Files.exists(dest, LinkOption.NOFOLLOW_LINKS)) throw new IOException("恢复目标已存在，不会覆盖");
    Path stage = Files.createTempDirectory(dest.getParent().toRealPath(), ".zh-restore-");
    Path db = stage.resolve(FILE);
    try {
      privateFile(db);
      try (var sink = new BufferedOutputStream(Files.newOutputStream(db))) {
        read(backup, sink);
      }
      try (Connection c = connect(stage)) {
        identify(c);
        c.setAutoCommit(false);
        try (var s = c.createStatement()) {
          s.executeUpdate("DELETE FROM auth_session");
          s.executeUpdate(
              "INSERT INTO work_audit(tenant,actor,subject,action,note) SELECT"
                  + " tenant,'@restore','workspace','RESTORE_LOCAL','旧会话已撤销；从离线备份恢复' FROM"
                  + " tenant_guard");
        }
        c.commit();
      }
      try (var ch = java.nio.channels.FileChannel.open(db, StandardOpenOption.WRITE)) {
        ch.force(true);
      }
      Files.createDirectory(dest);
      try {
        Files.createLink(dest.resolve(FILE), db);
      } catch (IOException | UnsupportedOperationException e) {
        Files.deleteIfExists(dest);
        throw e;
      }
    } finally {
      Files.deleteIfExists(db);
      Files.deleteIfExists(stage.resolve("workspace.trace.db"));
      Files.deleteIfExists(stage);
    }
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static void read(Path backup, OutputStream target) throws Exception {
    try (var in = new DataInputStream(new BufferedInputStream(Files.newInputStream(backup)))) {
      if (!"ZH-H2-1".equals(in.readUTF()) || !PROJECT.equals(in.readUTF()))
        throw new IOException("备份格式或项目标识不匹配");
      long size = in.readLong();
      if (size <= 0 || size > MAX || size > Files.size(backup)) throw new IOException("备份大小不合法");
      byte[] hash = copy(in, target, size);
      if (!MessageDigest.isEqual(hash, in.readNBytes(32)) || in.read() != -1)
        throw new IOException("备份校验不通过");
    }
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private static byte[] copy(InputStream in, OutputStream out, long left) throws Exception {
    var digest = MessageDigest.getInstance("SHA-256");
    byte[] buf = new byte[64 * 1024];
    while (left > 0) {
      int n = in.read(buf, 0, (int) Math.min(left, buf.length));
      if (n < 0) throw new EOFException("备份文件不完整");
      digest.update(buf, 0, n);
      out.write(buf, 0, n);
      left -= n;
    }
    return digest.digest();
  }
}
