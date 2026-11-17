package cn.zhuatech.vadtimeline;

import java.math.BigDecimal;
import java.util.*;

/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
final class GateRules {
  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  record Metric(String key, String label, String unit, String sampleOperator, BigDecimal example) {}

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public record Rule(String metric, String operator, BigDecimal threshold) {}

  static final List<Metric> METRICS =
      List.of(
          new Metric("missMs", "漏检时长", "ms", "LE", new BigDecimal("20")),
          new Metric("falseMs", "误报时长", "ms", "LE", new BigDecimal("25")),
          new Metric("inspection.longestMs", "最长连续错段", "ms", "LE", new BigDecimal("25")),
          new Metric("inspection.errorSegments", "连续错段数", "count", "LE", new BigDecimal("2")),
          new Metric("diagnostics.timeF1", "时长微平均 F1", "ratio", "GE", new BigDecimal("0.8")));

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static Metric metric(String name) {
    return METRICS.stream()
        .filter(m -> m.key().equals(name))
        .findFirst()
        .orElseThrow(() -> new Model.Failure(400, "不支持的验收指标"));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static List<Rule> normalize(List<Rule> input) {
    Checks.must(input != null && input.size() <= 8, "每版最多 8 条门槛");
    var out = new ArrayList<Rule>();
    var bounds = new HashMap<String, Map<String, BigDecimal>>();
    for (var r : input) {
      Checks.must(
          r != null
              && r.metric() != null
              && r.operator() != null
              && Set.of("LE", "GE").contains(r.operator())
              && r.threshold() != null,
          "指标、方向和数值阈值均为必填");
      var m = metric(r.metric());
      var x = r.threshold();
      Checks.must(
          x.scale() >= -12
              && x.scale() <= 12
              && x.precision() <= 19
              && x.signum() >= 0
              && x.compareTo(new BigDecimal("1000000000000000")) <= 0,
          "阈值越界，最多 12 位小数");
      if (m.unit().equals("ratio"))
        Checks.must(x.compareTo(BigDecimal.ONE) <= 0, "比例阈值须在 0 至 1 之间");
      var clean = x.stripTrailingZeros();
      if (Set.of("count", "ms").contains(m.unit())) Checks.must(clean.scale() <= 0, "数量和毫秒阈值必须是整数");
      var pair = bounds.computeIfAbsent(m.key(), k -> new HashMap<>());
      Checks.must(pair.put(r.operator(), clean) == null, "同一指标和方向不可重复");
      if (pair.size() == 2)
        Checks.must(pair.get("GE").compareTo(pair.get("LE")) <= 0, "同一指标的下限不可大于上限");
      out.add(new Rule(m.key(), r.operator(), clean));
    }
    out.sort(Comparator.comparing(Rule::metric).thenComparing(Rule::operator));
    return List.copyOf(out);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static Object value(Map<String, Object> result, String key) {
    Map<?, ?> group = result;
    if (key.contains(".")) {
      int dot = key.indexOf('.');
      if (!(result.get(key.substring(0, dot)) instanceof Map<?, ?> sub)) return null;
      group = sub;
      key = key.substring(dot + 1);
    }
    return group.get("metrics") instanceof Map<?, ?> metrics ? metrics.get(key) : null;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static Map<String, Object> evaluate(int version, List<Rule> rules, Map<String, Object> result) {
    var checks = new ArrayList<Map<String, Object>>();
    boolean failed = false, unknown = false;
    for (var r : rules) {
      Object value = value(result, r.metric());
      String status = "UNKNOWN";
      if (value instanceof Number number) {
        int cmp = new BigDecimal(number.toString()).compareTo(r.threshold());
        status = ("LE".equals(r.operator()) ? cmp <= 0 : cmp >= 0) ? "PASS" : "FAIL";
      }
      failed |= "FAIL".equals(status);
      unknown |= "UNKNOWN".equals(status);
      var line = new LinkedHashMap<String, Object>();
      line.put("metric", r.metric());
      line.put("operator", r.operator());
      line.put("threshold", r.threshold());
      line.put("label", metric(r.metric()).label());
      line.put("actual", value);
      line.put("status", status);
      checks.add(line);
    }
    String status =
        rules.isEmpty()
            ? (version == 0 ? "UNCONFIGURED" : "DISABLED")
            : failed ? "FAIL" : unknown ? "UNKNOWN" : "PASS";
    return Map.of("version", version, "status", status, "checks", checks);
  }
}
