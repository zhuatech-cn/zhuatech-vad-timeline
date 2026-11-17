package cn.zhuatech.vadtimeline;

import static cn.zhuatech.vadtimeline.Checks.*;

import java.util.*;

/**
 * 把同一录音中连续的漏检或误报合并为可回放区间，按时长降序列出起止点，避免重复轨道或相邻事件拆碎同一错段。 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ ·
 * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
final class Inspection {
  /**
   * 有序结果，可表达无法估计的数值。上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ ·
   * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
   */
  private static Map<String, Object> fields(Object... pairs) {
    var row = new LinkedHashMap<String, Object>();
    for (int i = 0; i < pairs.length; i += 2) row.put((String) pairs[i], pairs[i + 1]);
    return row;
  }

  /**
   * 对已校验的输入生成复核证据。上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ ·
   * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
   */
  @SuppressWarnings("unchecked")
  static Map<String, Object> evaluate(
      Map<String, Object> cfg, List<Map<String, Object>> rows, Map<String, Object> base) {
    var details = new ArrayList<Map<String, Object>>();

    var clips = new TreeMap<String, TreeMap<Integer, int[]>>();
    int min = ((Number) cfg.get("minDurationMs")).intValue();
    for (var r : rows) {
      int a = ((Number) r.get("beginMs")).intValue(), b = ((Number) r.get("endMs")).intValue();
      if (b - a < min) continue;
      var e = clips.computeIfAbsent((String) r.get("recording"), k -> new TreeMap<>());
      int t = r.get("track").equals("reference") ? 0 : 1;
      e.computeIfAbsent(a, k -> new int[2])[t]++;
      e.computeIfAbsent(b, k -> new int[2])[t]--;
    }
    long longest = 0;
    for (var clip : clips.entrySet()) {
      int ref = 0, det = 0, start = 0;
      String open = "";
      for (var e : clip.getValue().entrySet()) {
        int at = e.getKey();
        ref += e.getValue()[0];
        det += e.getValue()[1];
        String next = ref > 0 && det == 0 ? "MISS" : ref == 0 && det > 0 ? "FALSE_ALARM" : "";
        if (next.equals(open)) continue;
        if (!open.isEmpty()) {
          long d = at - start;
          longest = Math.max(longest, d);
          details.add(
              fields(
                  "recording",
                  clip.getKey(),
                  "errorType",
                  open,
                  "beginMs",
                  start,
                  "endMs",
                  at,
                  "durationMs",
                  d));
        }
        open = next;
        start = at;
      }
    }
    details.sort(
        Comparator.<Map<String, Object>>comparingLong(
                v -> ((Number) v.get("durationMs")).longValue())
            .reversed());
    return result(fields("errorSegments", details.size(), "longestMs", longest), details);
  }
}
