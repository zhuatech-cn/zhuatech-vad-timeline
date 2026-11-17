#!/usr/bin/env python3
"""上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
Read-only loopback readiness check; no model, login credentials or database access required.
"""
import argparse
import json
import os
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.request import ProxyHandler, HTTPRedirectHandler, build_opener


class LocalOnly(HTTPRedirectHandler):
    """上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 Do not follow redirects."""
    def redirect_request(self, *args):
        """上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
        return None


def main():
    """上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 Readiness probe."""
    parser = argparse.ArgumentParser(description="检查本机服务是否就绪，且确为当前项目。")
    parser.add_argument("--wait", type=int, default=0, help="最大重试时间（秒，0–300）")
    args = parser.parse_args()
    try:
        port = int(os.environ.get("PORT", "19445"))
        if not (1 <= port <= 65535 and 0 <= args.wait <= 300):
            raise ValueError()
    except ValueError:
        parser.error("PORT 应为 1–65535，--wait 应为 0–300。")
    base = f"http://127.0.0.1:{port}"
    client = build_opener(ProxyHandler({}), LocalOnly())
    deadline = time.monotonic() + args.wait
    while True:
        try:
            with client.open(base+"/api/health", timeout=3) as r:
                health = json.loads(r.read(16384))
            if not isinstance(health,dict) or health.get("project") != "vad-timeline":
                print("端口上不是预期项目 vad-timeline，请核对 PORT。",file=sys.stderr)
                return 1
            if health.get("status") != "UP":raise ValueError("未就绪")
            with client.open(base+"/", timeout=3) as r:
                if b"/app.js" not in r.read(65536):raise ValueError("未找到工作台页面")
            try:
                with client.open(base+"/api/me", timeout=3):
                    raise ValueError("身份接口未拒绝匿名请求")
            except HTTPError as e:
                if e.code != 401:raise
            print("通过：vad-timeline 数据库与账号初始化已就绪，首页可访问，匿名请求被拒绝。")
            return 0
        except (URLError, OSError, ValueError) as exc:
            if time.monotonic() >= deadline:
                print("检查未通过：",base,"；请核对端口、服务日志及首次管理员初始化。",type(exc).__name__,file=sys.stderr)
                return 1
            time.sleep(0.4)


if __name__ == "__main__":
    sys.exit(main())
