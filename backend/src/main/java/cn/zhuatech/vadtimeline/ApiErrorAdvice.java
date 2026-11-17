/* 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
package cn.zhuatech.vadtimeline;

import cn.zhuatech.vadtimeline.Model.Failure;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
@RestControllerAdvice(assignableTypes = {Api.class, ReviewRouting.class, ScenarioLab.class})
public class ApiErrorAdvice {
  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @ExceptionHandler(Failure.class)
  public ResponseEntity<?> domainError(Failure e) {
    return ResponseEntity.status(e.status).body(Map.of("message", e.getMessage()));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<?> badInput() {
    return ResponseEntity.badRequest().body(Map.of("message", "请求 JSON 格式或字段类型错误"));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<?> conflict() {
    return ResponseEntity.status(409).body(Map.of("message", "记录冲突，请刷新后重试"));
  }
}
