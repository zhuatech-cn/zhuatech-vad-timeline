/* 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.vadtimeline;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.HexFormat;

/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
final class Checks {
  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static void must(boolean ok, String reason) {
    if (!ok) throw new Model.Failure(400, reason);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static String str(Map<String, Object> r, String key, boolean empty) {
    Object v = r.get(key);
    must(v instanceof String, key + " 需要文本");
    String s = (String) v;
    must((empty || !s.isBlank()) && s.length() <= 32000, key + " 为空或超过 32000 字符");
    return s;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static double num(Map<String, Object> r, String key, double min, double max) {
    Object v = r.get(key);
    must(v instanceof Number, key + " 需要数值");
    double d = ((Number) v).doubleValue();
    must(Double.isFinite(d) && d >= min && d <= max, key + " 超出范围 [" + min + ", " + max + "]");
    return d;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static int integer(Map<String, Object> r, String k, int lo, int hi) {
    double d = num(r, k, lo, hi);
    must(d == Math.rint(d), k + " 必须是整数");
    return (int) d;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static String hash(String s) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static List<String> strings(Map<String, Object> r, String k) {
    Object v = r.get(k);
    must(v instanceof List<?> && ((List<?>) v).size() <= 1000, k + " 需要数组，最多 1000 项");
    var out = new ArrayList<String>();
    var seen = new HashSet<String>();
    for (Object x : (List<?>) v) {
      must(
          x instanceof String && !((String) x).isBlank() && seen.add((String) x),
          k + " 包含空值、重复值或非文本");
      out.add((String) x);
    }
    return out;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static void unique(List<Map<String, Object>> rows) {
    var seen = new HashSet<String>();
    for (var r : rows) must(seen.add(str(r, "id", false)), "id 不可重复");
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static Map<String, Object> result(Map<String, ?> metrics, List<?> rows) {
    return Map.of("metrics", metrics, "rows", rows);
  }
}
