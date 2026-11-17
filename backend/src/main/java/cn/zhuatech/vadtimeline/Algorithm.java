package cn.zhuatech.vadtimeline;

import static cn.zhuatech.vadtimeline.Checks.*;

import java.util.*;

/**
 * 分录音扫描标注与检测区间，合并重叠后累计命中、漏检和误报毫秒。 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ ·
 * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
final class Algorithm {
  /**
   * 接收时间戳，不上传或识别音频；不评价说话人身份。短于阈值的区间不参与计分。 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ ·
   * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
   */
  static Map<String, Object> compute(Map<String, Object> cfg, List<Map<String, Object>> rows) {
    var base = calculate(cfg, rows);
    var enriched = new LinkedHashMap<String, Object>(base);
    enriched.put("inspection", Inspection.evaluate(cfg, rows, base));
    enriched.put("diagnostics", Diagnostics.evaluate(cfg, rows, base));
    return enriched;
  }

  /**
   * 保留原始计算口径。上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
   */
  private static Map<String, Object> calculate(
      Map<String, Object> cfg, List<Map<String, Object>> rows) {
    must(rows != null && !rows.isEmpty() && rows.size() <= 200, "需要 1–200 行");
    unique(rows);
    var evidence = new ArrayList<Map<String, Object>>();
    int min = integer(cfg, "minDurationMs", 1, 60000);
    var rec = new TreeMap<String, TreeMap<Integer, int[]>>();
    long H = 0, M = 0, F = 0;
    int ignored = 0;
    for (var r : rows) {
      String clip = str(r, "recording", false), t = str(r, "track", false);
      must(Set.of("reference", "detected").contains(t), "track 无效");
      int a = integer(r, "beginMs", 0, 2000000000), b = integer(r, "endMs", 0, 2000000000);
      must(b > a, "区间终点须晚于起点");
      var events = rec.computeIfAbsent(clip, k -> new TreeMap<>());
      if (b - a < min) {
        ignored++;
        continue;
      }
      int i = t.equals("reference") ? 0 : 1;
      events.computeIfAbsent(a, k -> new int[2])[i]++;
      events.computeIfAbsent(b, k -> new int[2])[i]--;
    }
    for (var c : rec.entrySet()) {
      int last = 0, x = 0, y = 0;
      long h = 0, m = 0, f = 0;
      for (var e : c.getValue().entrySet()) {
        long d = e.getKey() - last;
        if (x > 0 && y > 0) h += d;
        else if (x > 0) m += d;
        else if (y > 0) f += d;
        x += e.getValue()[0];
        y += e.getValue()[1];
        last = e.getKey();
      }
      H += h;
      M += m;
      F += f;
      evidence.add(Map.of("recording", c.getKey(), "hitMs", h, "missMs", m, "falseMs", f));
    }
    return result(Map.of("hitMs", H, "missMs", M, "falseMs", F, "ignored", ignored), evidence);
  }
}
