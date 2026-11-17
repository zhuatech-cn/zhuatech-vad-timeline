/* 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.vadtimeline;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
@Component
public class HttpGuard extends OncePerRequestFilter {
  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader("Referrer-Policy", "no-referrer");
    res.setHeader(
        "Content-Security-Policy",
        "default-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'none';"
            + " form-action 'self'");
    if (req.getRequestURI().startsWith("/api/")) res.setHeader("Cache-Control", "no-store");
    if (SetHolder.METHODS.contains(req.getMethod()) && req.getRequestURI().startsWith("/api/")) {
      long size = req.getContentLengthLong();
      if (size < 0 || size > 524288) {
        res.sendError(size < 0 ? 411 : 413);
        return;
      }
      if (size > 0
          && (req.getContentType() == null
              || !req.getContentType()
                  .toLowerCase(java.util.Locale.ROOT)
                  .startsWith("application/json"))) {
        res.sendError(415);
        return;
      }
    }
    chain.doFilter(req, res);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private static class SetHolder {
    static final java.util.Set<String> METHODS = java.util.Set.of("POST", "PATCH", "PUT");
  }
}
