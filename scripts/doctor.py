#!/usr/bin/env python3
"""上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
Optional preflight for a direct local launch; never initializes a database or changes persistent files.
"""
import os, re, shutil, socket, subprocess, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main(args):
    """Direct local launch preflight. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
    商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
    """
    if args not in ([], ["--build"], ["--no-build"]):
        print("用法：python3 scripts/doctor.py [--build|--no-build]")
        return 2
    jar = ROOT / "backend/target/app.jar"
    build = args == ["--build"] or (not args and ((ROOT/".git").exists() or not jar.is_file()))
    errors = []
    home = os.environ.get("JAVA_HOME")
    suffix = ".exe" if os.name == "nt" else ""
    java = str(Path(home)/"bin"/("java"+suffix)) if home else shutil.which("java")
    try:
        command = subprocess.run([java or "java", "-XshowSettings:properties", "-version"], capture_output=True, text=True, timeout=12)
        found = re.search(r"java\.specification\.version\s*=\s*(\d+)",command.stderr)
        if command.returncode or not found or int(found[1]) < 21:
            errors.append("需要 Java 21 或更新版本，请检查 JAVA_HOME。")
    except (OSError, subprocess.TimeoutExpired):
        errors.append("无法运行 Java，请先配置 JAVA_HOME。")
    if build:
        javac = str(Path(home)/"bin"/("javac"+suffix)) if home else shutil.which("javac")
        if not javac or not os.access(javac,os.X_OK):
            errors.append("源码构建需要完整 JDK，未找到 javac。")
    elif not jar.is_file():
        errors.append("没有可运行的 JAR，请先构建或使用免构建包。")
    settings=(ROOT/"backend/src/main/resources/application.properties").read_text(encoding="utf8")
    default=re.search(r"server\.port=\$\{PORT:(\d+)\}",settings)[1]
    try:
        port=int(os.environ.get("PORT",default))
        if not 1 <= port <= 65535: raise ValueError()
        address=os.environ.get("BIND_ADDRESS","127.0.0.1")
        family,kind,proto,_,endpoint=socket.getaddrinfo(address,port,type=socket.SOCK_STREAM)[0]
        with socket.socket(family,kind,proto) as probe:
            probe.bind(endpoint)
    except ValueError:
        errors.append("PORT 须为 1 至 65535 的整数。")
    except OSError:
        errors.append("无法绑定监听地址：请检查 BIND_ADDRESS，或更换已被占用的 PORT。")
    guard=os.environ.get("WORKSPACE_REQUIRE_EXISTING","FALSE").upper()
    if guard not in ("TRUE","FALSE"):
        errors.append("WORKSPACE_REQUIRE_EXISTING 只能为 TRUE 或 FALSE。")
    if not (os.environ.get("DB_URL") or os.environ.get("SPRING_DATASOURCE_URL")):
        raw=os.environ.get("WORKSPACE_DATA_DIR","./var")
        var=ROOT/Path(raw)
        if not raw or ";" in raw or not (Path(raw).is_absolute() or raw.startswith(("./",".\\"))):
            errors.append("本地数据目录请使用绝对路径或以 ./ 开头的相对路径，不能含分号。")
        if guard=="TRUE" and not (var/"workspace.mv.db").is_file():
            errors.append("已启用仅打开已有文件库，但未找到 workspace.mv.db。")
        storage=var
        while not storage.exists() and storage!=storage.parent:storage=storage.parent
        if not storage.is_dir() or not os.access(storage,os.W_OK | os.X_OK):
            errors.append("配置的数据目录不可创建或不可写，请检查路径与权限。")
    print("启动方式："+("源码构建" if build else "直接运行 JAR"))
    for reason in errors:print("未通过："+reason)
    if errors:return 1
    print("本地前置检查通过。未连接数据库或下载依赖；实际运行仍以启动与登录为准。")
    return 0


if __name__=="__main__":
    sys.exit(main(sys.argv[1:]))
