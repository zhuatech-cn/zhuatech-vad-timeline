package cn.zhuatech.vadtimeline;

import static cn.zhuatech.vadtimeline.Model.*;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
record QueueWindow(String scopeHash, Instant asOf, Instant after, String id) {
  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  String encode() {
    var value =
        String.join("|", scopeHash, asOf.toString(), after == null ? "-" : after.toString(), id);
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.US_ASCII));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static QueueWindow open(String value, String hash, Instant now) {
    if (value.isEmpty()) return new QueueWindow(hash, now, null, "-");
    Checks.must(value.length() <= 360 && value.matches("[A-Za-z0-9_-]+"), "队列定位无效，请重新筛选");
    QueueWindow c;
    try {
      var fields =
          new String(Base64.getUrlDecoder().decode(value), StandardCharsets.US_ASCII)
              .split("\\|", -1);
      if (fields.length != 4) throw new IllegalArgumentException();
      var after = fields[2].equals("-") ? null : Instant.parse(fields[2]);
      if (after == null
          ? !fields[3].equals("-")
          : !UUID.fromString(fields[3]).toString().equals(fields[3]))
        throw new IllegalArgumentException();
      c = new QueueWindow(fields[0], Instant.parse(fields[1]), after, fields[3]);
    } catch (RuntimeException e) {
      throw new Failure(400, "队列定位无效，请重新筛选");
    }
    Checks.must(
        c.encode().equals(value)
            && hash.equals(c.scopeHash)
            && !c.asOf.isAfter(now)
            && (c.after == null || !c.after.isAfter(c.asOf)),
        "定位与当前筛选不符，请重新筛选");
    if (c.asOf.isBefore(now.minusSeconds(900))) throw new Failure(409, "队列定位超过 15 分钟，请重新筛选");
    return c;
  }
}
