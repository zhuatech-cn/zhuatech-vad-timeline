#!/usr/bin/env bash
# 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
set -euo pipefail
umask 077
cd "$(dirname "$0")/.."
mode=${1:-auto}
case "$mode" in auto|--build|--no-build) ;; *) echo "用法：bash scripts/start.sh [--build|--no-build]" >&2; exit 2;; esac
if [[ -n "${JAVA_HOME:-}" ]]; then
  [[ -x "$JAVA_HOME/bin/java" ]] || { echo 'JAVA_HOME 未指向有效 JDK。' >&2; exit 2; }
  export PATH="$JAVA_HOME/bin:$PATH"
elif [[ "$(uname -s)" == Darwin ]] && ! java -version >/dev/null 2>&1 && [[ -x /Applications/DevEco-Studio.app/Contents/jbr/Contents/Home/bin/java ]]; then
  export JAVA_HOME=/Applications/DevEco-Studio.app/Contents/jbr/Contents/Home
  export PATH="$JAVA_HOME/bin:$PATH"
fi
major=$(java -XshowSettings:properties -version 2>&1 | awk '/java.specification.version = /{print $3}') || major=''
if [[ ! "$major" =~ ^[0-9]+$ ]] || ((major < 21)); then echo '需要 Java 21 或更高版本；从源码构建需要完整 JDK。请检查 JAVA_HOME。' >&2; exit 2; fi
jar=backend/target/app.jar
if [[ "$mode" == --build || ( "$mode" == auto && ( -e .git || ! -f "$jar" ) ) ]]; then
  command -v javac >/dev/null || { echo '未发现 javac；请设置完整 JDK 的 JAVA_HOME。' >&2; exit 2; }
  # Source ZIP extractors do not always preserve the executable bit.
  sh ./mvnw -B -f backend/pom.xml clean package
fi
[[ -f "$jar" ]] || { echo '没有可运行 JAR，请使用 --build。' >&2; exit 2; }
# H2 creates the configured data directory; an existing-data-only startup must not create a database.
exec java -jar "$jar"
