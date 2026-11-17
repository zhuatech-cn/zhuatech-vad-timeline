package cn.zhuatech.vadtimeline;

import java.math.BigDecimal;
import java.util.*;

/**
 * Stable business identities, not visual row numbers or truncated preview text. 上海如静知华信息科技有限公司 ·
 * https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
final class EvidenceDiff {
  static final List<String> MAIN = List.of("recording"),
      INSPECTION = List.of("recording", "errorType", "beginMs");

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  record Key(List<String> parts, Map<String, Object> display) {
    @Override
    public boolean equals(Object other) {
      return other instanceof Key k && parts.equals(k.parts);
    }

    @Override
    public int hashCode() {
      return parts.hashCode();
    }
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> rows(Map<String, Object> result, String section) {
    Object part = "main".equals(section) ? result : result.get(section);
    if (!(part instanceof Map<?, ?> p) || !(p.get("rows") instanceof List<?> rows)) return null;
    return (List<Map<String, Object>>) (List<?>) rows;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private static Map<Key, Map<String, Object>> index(
      List<Map<String, Object>> rows, List<String> keys) {
    var out = new LinkedHashMap<Key, Map<String, Object>>();
    for (var row : rows) {
      var values = new ArrayList<String>();
      var display = new LinkedHashMap<String, Object>();
      for (var field : keys) {
        Object v = row.get(field);
        if (v == null) return null;
        if (v instanceof Number n)
          values.add("n:" + new BigDecimal(n.toString()).stripTrailingZeros().toPlainString());
        else if (v instanceof String text) values.add("s:" + text);
        else return null;
        display.put(field, v);
      }
      if (out.put(new Key(values, display), row) != null) return null;
    }
    return out;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private static List<String> differentFields(Map<String, Object> left, Map<String, Object> right) {
    if (left == null || right == null) return List.of();
    var names = new TreeSet<>(left.keySet());
    names.addAll(right.keySet());
    return names.stream()
        .filter(
            k ->
                left.containsKey(k) != right.containsKey(k)
                    || !CorrectionDiff.equal(left.get(k), right.get(k)))
        .toList();
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static Map<String, Object> compare(
      Map<String, Object> left,
      Map<String, Object> right,
      String section,
      String filter,
      String needle,
      int page,
      int size) {
    Checks.must(
        Set.of("main", "inspection", "diagnostics").contains(section)
            && Set.of("DIFFERENCES", "ALL", "ADDED", "REMOVED", "CHANGED", "SAME").contains(filter),
        "无效对照范围");
    Checks.must(
        needle != null
            && needle.length() <= 100
            && page >= 1
            && page <= 100000
            && size >= 1
            && size <= 50,
        "页码、条数或检索词无效");
    var keys =
        section.equals("main")
            ? MAIN
            : section.equals("inspection") ? INSPECTION : List.of("recording");
    var old = rows(left, section);
    var next = rows(right, section);
    var report = new LinkedHashMap<String, Object>();
    report.put("section", section);
    report.put("keyFields", keys);
    report.put("page", page);
    report.put("size", size);
    var counts = new LinkedHashMap<String, Integer>();
    for (String k : List.of("ADDED", "REMOVED", "CHANGED", "SAME")) counts.put(k, 0);
    report.put("counts", counts);
    report.put("total", 0);
    report.put("items", List.of());
    if (old == null || next == null) {
      report.put("availability", "MISSING_SECTION");
      return report;
    }
    if ("diagnostics".equals(section)
        && (left.get(section) instanceof Map<?, ?> a && Boolean.TRUE.equals(a.get("truncated"))
            || right.get(section) instanceof Map<?, ?> b
                && Boolean.TRUE.equals(b.get("truncated")))) {
      report.put("availability", "PARTIAL_SECTION");
      return report;
    }
    var a = index(old, keys);
    var b = index(next, keys);
    if (a == null || b == null) {
      report.put("availability", "AMBIGUOUS_IDENTITY");
      return report;
    }
    report.put("availability", "AVAILABLE");
    var ids = new LinkedHashSet<>(a.keySet());
    ids.addAll(b.keySet());
    var matches = new ArrayList<Map<String, Object>>();
    int total = 0, start = (page - 1) * size;
    String q = needle.strip().toLowerCase(Locale.ROOT);
    for (var id : ids) {
      var x = a.get(id);
      var y = b.get(id);
      var fields = differentFields(x, y);
      var kind =
          x == null ? "ADDED" : y == null ? "REMOVED" : fields.isEmpty() ? "SAME" : "CHANGED";
      counts.merge(kind, 1, Integer::sum);
      if (!filter.equals("ALL")
          && !(filter.equals("DIFFERENCES") && !kind.equals("SAME"))
          && !filter.equals(kind)) continue;
      if (!q.isEmpty()
          && id.display().values().stream()
              .noneMatch(v -> v.toString().toLowerCase(Locale.ROOT).contains(q))) continue;
      if (total++ < start || matches.size() >= size) continue;
      var item = new LinkedHashMap<String, Object>();
      item.put("key", id.display());
      item.put("state", kind);
      item.put("fields", fields);
      item.put("baseline", x);
      item.put("target", y);
      matches.add(item);
    }
    report.put("total", total);
    report.put("items", matches);
    return report;
  }
}
