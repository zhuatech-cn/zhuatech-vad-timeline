package cn.zhuatech.vadtimeline;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.*;
import tools.jackson.databind.ObjectMapper;

/**
 * Single-record packet; a digest detects corruption but does not authenticate an issuer.
 * 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
final class EvidenceArchive {
  static final String PROJECT = "zhuatech-vad-timeline";
  static final int MAX_RAW = 16 * 1024 * 1024;

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  record Bundle(byte[] zip, String digest) {}

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private static String sha(byte[] input) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private static Object canonical(Object v) {
    if (v instanceof Map<?, ?> m) {
      var sorted = new TreeMap<String, Object>();
      for (var e : m.entrySet()) sorted.put((String) e.getKey(), canonical(e.getValue()));
      return sorted;
    }
    if (v instanceof List<?> items) return items.stream().map(EvidenceArchive::canonical).toList();
    return v;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static byte[] canonicalBytes(Object value, ObjectMapper json) {
    return json.writeValueAsBytes(canonical(value));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static String sourceDigest(Object value, ObjectMapper json) {
    return sha(canonicalBytes(value, json));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static Bundle pack(Map<String, Object> run, ObjectMapper json) {
    var files = new LinkedHashMap<String, byte[]>();
    var review = new LinkedHashMap<String, Object>();
    for (var k :
        List.of(
            "id",
            "title",
            "actor_id",
            "stage",
            "revision",
            "created_at",
            "history",
            "gate",
            "correction",
            "correctedBy")) if (run.containsKey(k)) review.put(k, run.get(k));
    files.put("input.json", canonicalBytes(run.get("input"), json));
    files.put("result.json", canonicalBytes(run.get("result"), json));
    files.put("review.json", canonicalBytes(review, json));
    var entries = new ArrayList<Object>();
    for (var f : files.entrySet())
      entries.add(
          Map.of("name", f.getKey(), "bytes", f.getValue().length, "sha256", sha(f.getValue())));
    var manifest = new LinkedHashMap<String, Object>();
    manifest.put("format", "zhuatech-evidence/1");
    manifest.put("project", PROJECT);
    manifest.put("runId", run.get("id"));
    manifest.put("revision", run.get("revision"));
    manifest.put("stage", run.get("stage"));
    manifest.put("entries", entries);
    files.put("manifest.json", canonicalBytes(manifest, json));
    long total = files.values().stream().mapToLong(a -> a.length).sum();
    if (total > MAX_RAW) throw new Model.Failure(413, "证据包超过 16 MiB，请在台账内查阅");
    try (var bytes = new ByteArrayOutputStream();
        var z = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
      for (var f : files.entrySet()) {
        var e = new ZipEntry(f.getKey());
        e.setTimeLocal(LocalDateTime.of(2000, 1, 1, 0, 0));
        z.putNextEntry(e);
        z.write(f.getValue());
        z.closeEntry();
      }
      z.finish();
      var data = bytes.toByteArray();
      return new Bundle(data, sha(data));
    } catch (IOException ex) {
      throw new java.io.UncheckedIOException(ex);
    }
  }
}
