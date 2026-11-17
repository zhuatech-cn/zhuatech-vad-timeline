package cn.zhuatech.vadtimeline;

import java.util.*;

/**
 * 按时间并集而不是片段数量计算微平均精确率、召回率、F1 和语音交并比。每段录音独立给出结果，分母为零时显示无法估计，不把静音时长算作正确命中。 上海如静知华信息科技有限公司 ·
 * https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
final class Diagnostics {
  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private static Map<String, Object> map(Object... pairs) {
    var m = new LinkedHashMap<String, Object>();
    for (int i = 0; i < pairs.length; i += 2) m.put((String) pairs[i], pairs[i + 1]);
    return m;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private static double num(Map<String, Object> m, String k) {
    return ((Number) m.get(k)).doubleValue();
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private static Double ratio(double a, double b) {
    return b == 0 ? null : a / b;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @SuppressWarnings("unchecked")
  static Map<String, Object> evaluate(
      Map<String, Object> cfg, List<Map<String, Object>> rows, Map<String, Object> base) {
    var metrics = (Map<String, Object>) base.get("metrics");
    var evidence = (List<Map<String, Object>>) base.get("rows");
    var details = new ArrayList<Map<String, Object>>();
    long h = 0, m = 0, f = 0;
    double macro = 0;
    int evaluated = 0;
    for (var r : evidence) {
      long hit = (long) num(r, "hitMs"),
          miss = (long) num(r, "missMs"),
          fp = (long) num(r, "falseMs");
      h += hit;
      m += miss;
      f += fp;
      Double f1 = ratio(2 * hit, 2 * hit + miss + fp);
      if (f1 != null) {
        macro += f1;
        evaluated++;
      }
      details.add(
          map(
              "recording",
              r.get("recording"),
              "timePrecision",
              ratio(hit, hit + fp),
              "timeRecall",
              ratio(hit, hit + miss),
              "timeF1",
              f1,
              "activityIoU",
              ratio(hit, hit + miss + fp)));
    }
    return Checks.result(
        map(
            "timePrecision",
            ratio(h, h + f),
            "timeRecall",
            ratio(h, h + m),
            "timeF1",
            ratio(2 * h, 2 * h + m + f),
            "activityIoU",
            ratio(h, h + m + f),
            "macroF1",
            ratio(macro, evaluated),
            "evaluableRecordings",
            evaluated),
        details);
  }
}
