/* 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.vadtimeline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
@SpringBootApplication
public class Application {
  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public static void main(String[] args) {
    if (args.length > 0 && "maintenance".equals(args[0])) {
      try {
        Recovery.cli(args);
      } catch (Exception e) {
        if (e instanceof java.sql.SQLException sql)
          System.err.println("文件库操作被拒绝，请确认已停机、项目和数据库凭据正确且版本为 V3–V6。SQLState=" + sql.getSQLState());
        else System.err.println("离线维护失败：" + e.getMessage());
        System.exit(2);
      }
      return;
    }
    SpringApplication.run(Application.class, args);
  }
}
