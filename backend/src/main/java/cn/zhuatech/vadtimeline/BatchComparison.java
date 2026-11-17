package cn.zhuatech.vadtimeline;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Compare persisted evidence, never re-execute an old algorithm. 上海如静知华信息科技有限公司 ·
 * https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
@Service
final class BatchComparison {
  final JdbcTemplate db;
  final ObjectMapper json;

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  BatchComparison(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  record Saved(
      String id, String title, String stage, Api.Batch input, Map<String, Object> result) {}

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @SuppressWarnings("unchecked")
  private Saved load(String tenant, String id) {
    var rows =
        db.queryForList(
            "SELECT id,title,stage,input_json,result_json FROM work_run WHERE tenant=? AND id=?",
            tenant,
            id);
    if (rows.isEmpty()) throw new Model.Failure(404, "所选批次不可见或不存在");
    var row = rows.getFirst();
    return new Saved(
        (String) row.get("id"),
        (String) row.get("title"),
        (String) row.get("stage"),
        json.readValue((String) row.get("input_json"), Api.Batch.class),
        json.readValue((String) row.get("result_json"), Map.class));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  Map<String, Object> read(String tenant, String baseline, String target) {
    Checks.must(!baseline.equalsIgnoreCase(target), "请选择两个不同批次");
    var a = load(tenant, baseline);
    var b = load(tenant, target);
    Checks.must(!a.id().equals(b.id()), "请选择两个不同批次");
    return compare(a, b);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  Map<String, Object> evidence(
      String tenant,
      String base,
      String target,
      String section,
      String filter,
      String q,
      int page,
      int size) {
    Checks.must(!base.equalsIgnoreCase(target), "请选择两个不同批次");
    var a = load(tenant, base);
    var b = load(tenant, target);
    Checks.must(!a.id().equals(b.id()), "请选择两个不同批次");
    return EvidenceDiff.compare(a.result(), b.result(), section, filter, q, page, size);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static Map<String, Object> compare(Saved a, Saved b) {
    var aMetrics = flatten(a.result());
    var bMetrics = flatten(b.result());
    var names = new TreeSet<>(aMetrics.keySet());
    names.addAll(bMetrics.keySet());
    var rows = new ArrayList<Map<String, Object>>();
    for (var name : names) {
      var x = aMetrics.get(name);
      var y = bMetrics.get(name);
      var line = new LinkedHashMap<String, Object>();
      line.put("metric", name);
      line.put("baseline", x);
      line.put("target", y);
      line.put(
          "delta",
          x instanceof Number ax && y instanceof Number by
              ? new BigDecimal(by.toString()).subtract(new BigDecimal(ax.toString()))
              : null);
      String state =
          !aMetrics.containsKey(name)
              ? "ONLY_TARGET"
              : !bMetrics.containsKey(name)
                  ? "ONLY_BASELINE"
                  : x == null || y == null
                      ? "UNAVAILABLE"
                      : CorrectionDiff.equal(x, y) ? "SAME" : "CHANGED";
      line.put("state", state);
      rows.add(line);
    }
    var diff = CorrectionDiff.compare(a.input(), b.input());
    var added = new ArrayList<String>();
    var removed = new ArrayList<String>();
    var changed = new LinkedHashSet<String>();
    var settings = new ArrayList<String>();
    boolean reordered = false;
    for (var d : diff)
      switch ((String) d.get("kind")) {
        case "SETTING" -> settings.add((String) d.get("field"));
        case "ADD" -> added.add((String) d.get("id"));
        case "REMOVE" -> removed.add((String) d.get("id"));
        case "EDIT" -> changed.add((String) d.get("id"));
        case "ORDER" -> reordered = true;
        default -> throw new IllegalStateException("unknown input change");
      }
    var cohort =
        Map.of(
            "added",
            added,
            "removed",
            removed,
            "changed",
            changed,
            "settingsChanged",
            settings,
            "reordered",
            reordered,
            "sharedCount",
            a.input().rows().size() - removed.size(),
            "sameInputs",
            diff.isEmpty());
    return Map.of(
        "baseline",
        Map.of(
            "id",
            a.id(),
            "title",
            a.title(),
            "stage",
            a.stage(),
            "rowCount",
            a.input().rows().size()),
        "target",
        Map.of(
            "id",
            b.id(),
            "title",
            b.title(),
            "stage",
            b.stage(),
            "rowCount",
            b.input().rows().size()),
        "cohort",
        cohort,
        "metrics",
        rows);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static Map<String, Object> flatten(Map<String, Object> result) {
    var m = new TreeMap<String, Object>();
    if (result.get("metrics") instanceof Map<?, ?> xs) xs.forEach((k, v) -> m.put(k.toString(), v));
    for (String section : List.of("inspection", "diagnostics"))
      if (result.get(section) instanceof Map<?, ?> sub
          && sub.get("metrics") instanceof Map<?, ?> xs)
        xs.forEach((k, v) -> m.put(section + "." + k, v));
    return m;
  }
}
