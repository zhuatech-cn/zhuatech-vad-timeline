package cn.zhuatech.vadtimeline;

import java.util.*;
import tools.jackson.databind.ObjectMapper;

/**
 * Keep optional evidence bounded without silently truncating core output. 上海如静知华信息科技有限公司 ·
 * https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
final class ResultStorage {
  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @SuppressWarnings("unchecked")
  static String encode(Map<String, Object> result, ObjectMapper json) {
    var base = new LinkedHashMap<>(result);
    var d = (Map<String, Object>) base.remove("diagnostics");
    String core = json.writeValueAsString(base);
    Checks.must(core.length() <= 2000000, "结果过大，请拆分批次");
    if (d == null) return core;
    var rows = (List<Map<String, Object>>) d.get("rows");
    var kept = new ArrayList<Map<String, Object>>();
    int used = 0;
    for (var row : rows) {
      int length = json.writeValueAsString(row).length() + 1;
      if (used + length > 350000) break;
      kept.add(row);
      used += length;
    }
    base.put(
        "diagnostics",
        Map.of(
            "metrics",
            d.get("metrics"),
            "rows",
            kept,
            "totalRows",
            rows.size(),
            "truncated",
            kept.size() != rows.size()));
    return json.writeValueAsString(base);
  }
}
