package cn.zhuatech.vadtimeline;

import java.math.BigDecimal;
import java.util.*;

/**
 * Input-only comparison: ID identifies a line; only relative order of surviving IDs matters.
 * 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
final class CorrectionDiff {
  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static boolean equal(Object a, Object b) {
    if (a instanceof Number x && b instanceof Number y)
      return new BigDecimal(x.toString()).compareTo(new BigDecimal(y.toString())) == 0;
    if (a instanceof List<?> x && b instanceof List<?> y) {
      if (x.size() != y.size()) return false;
      for (int i = 0; i < x.size(); i++) if (!equal(x.get(i), y.get(i))) return false;
      return true;
    }
    if (a instanceof Map<?, ?> left && b instanceof Map<?, ?> right) {
      if (!left.keySet().equals(right.keySet())) return false;
      for (var key : left.keySet()) if (!equal(left.get(key), right.get(key))) return false;
      return true;
    }
    return Objects.equals(a, b);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static void fields(
      List<Map<String, Object>> out,
      String scope,
      String id,
      Map<String, Object> from,
      Map<String, Object> to) {
    var names = new TreeSet<String>(from.keySet());
    names.addAll(to.keySet());
    for (String key : names)
      if (!equal(from.get(key), to.get(key)))
        out.add(change(scope, id, key, from.get(key), to.get(key)));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static Map<String, Object> change(
      String kind, String id, String field, Object before, Object after) {
    var m = new LinkedHashMap<String, Object>();
    m.put("kind", kind);
    m.put("id", id);
    m.put("field", field);
    m.put("before", before);
    m.put("after", after);
    return m;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static LinkedHashMap<String, Map<String, Object>> byId(List<Map<String, Object>> rows) {
    var m = new LinkedHashMap<String, Map<String, Object>>();
    for (var row : rows) m.put((String) row.get("id"), row);
    return m;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static List<Map<String, Object>> compare(Api.Batch from, Api.Batch to) {
    var changes = new ArrayList<Map<String, Object>>();
    fields(changes, "SETTING", "", from.settings(), to.settings());
    var a = byId(from.rows());
    var b = byId(to.rows());
    for (var id : a.keySet()) {
      if (!b.containsKey(id)) changes.add(change("REMOVE", id, "", a.get(id), null));
      else fields(changes, "EDIT", id, a.get(id), b.get(id));
    }
    for (var id : b.keySet())
      if (!a.containsKey(id)) changes.add(change("ADD", id, "", null, b.get(id)));
    var oldOrder = a.keySet().stream().filter(b::containsKey).toList();
    var newOrder = b.keySet().stream().filter(a::containsKey).toList();
    if (!oldOrder.equals(newOrder)) changes.add(change("ORDER", "", "", oldOrder, newOrder));
    return changes;
  }
}
