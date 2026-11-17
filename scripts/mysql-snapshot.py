#!/usr/bin/env python3
"""Read-only V7 MySQL logical backup. No restore or overwrite operation is provided.
上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
"""
import argparse
import hashlib
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import tempfile

PROJECT = "zhuatech-vad-timeline"
TABLES = frozenset(("app_user", "auth_session", "flyway_schema_history", "gate_policy", "tenant_guard",
                    "review_route", "review_route_event", "work_audit", "work_correction", "work_run", "work_run_gate", "workspace_identity"))
SYSTEM = {"mysql", "sys", "information_schema", "performance_schema"}


def ensure(condition, msg):
    """Enforce a precondition. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
    if not condition:
        raise ValueError(msg)


def layout(schema, inventory, identity, version):
    """Require the known V7 table set and supported engine. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
    ensure(re.fullmatch(r"[A-Za-z][A-Za-z0-9_]{0,63}", schema) and schema.lower() not in SYSTEM,
           "specify a dedicated application schema")
    ensure(identity == PROJECT and version == "7", "wrong project or unsupported schema version (V7 required)")
    values = [tuple(line.split("\t")) for line in inventory.splitlines()]
    ensure(len(values) == len(TABLES) and {row[0] for row in values} == TABLES and
           all(len(row) == 3 and row[1] == "BASE TABLE" and row[2].lower() == "innodb" for row in values),
           "unexpected tables, views or non-InnoDB engine; use a DBA-managed backup instead")


def checksum(path):
    """Hash a file using bounded memory. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
    value = hashlib.sha256()
    with Path(path).open("rb") as f:
        for part in iter(lambda: f.read(1024*1024), b""):
            value.update(part)
    return value.hexdigest()


def dump_args(client, option, schema, definitions):
    """Build a read-only MySQL command for definitions or rows. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
    args = [client, option, "--no-login-paths", "--no-tablespaces", "--set-gtid-purged=OFF", "--skip-column-statistics",
            "--skip-triggers", "--skip-lock-tables", "--skip-add-locks", "--skip-add-drop-table",
            "--skip-disable-keys", "--default-character-set=utf8mb4", "--hex-blob", "--skip-comments"]
    if definitions:
        return args + ["--no-data", schema, *sorted(TABLES)]
    return args + ["--single-transaction", "--quick", "--no-create-info", schema, *sorted(TABLES - {"auth_session"})]


def backup(args):
    """Read one project database and publish a new, private SQL file. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
    cfg = Path(args.defaults_file).resolve(strict=True)
    ensure(cfg.is_file(), "client config must be a regular file")
    if os.name == "posix":
        ensure(not stat.S_IMODE(cfg.stat().st_mode) & 0o077, "client option file must be private (chmod 600)")
    output = Path(args.output).absolute()
    ensure(output.parent.is_dir() and not os.path.lexists(output), "output must be a new file in an existing directory")
    schema = args.schema
    ensure(re.fullmatch(r"[A-Za-z][A-Za-z0-9_]{0,63}", schema) and schema.lower() not in SYSTEM,
           "specify a dedicated application schema")
    suffix = ".exe" if os.name == "nt" else ""
    def binary(name):
        """Resolve the explicitly selected native client. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
        path = str(Path(args.client_bin) / (name+suffix)) if args.client_bin else shutil.which(name)
        ensure(path is not None and Path(path).is_file(), "MySQL 8.4 client tools not found")
        return path
    mysql, dump = binary("mysql"), binary("mysqldump")
    option = "--defaults-file=" + str(cfg)
    def query(statement):
        """Read a bounded metadata query. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
        proc = subprocess.run([mysql, option, "--no-login-paths", "--batch", "--raw", "--skip-column-names", schema, "-e", statement],
                              check=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=30)
        return proc.stdout.decode("utf-8").strip()
    def validate():
        """Read and verify storage identity. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
        layout(schema, query("SELECT table_name,table_type,COALESCE(engine,'') FROM information_schema.tables "
                             "WHERE table_schema=DATABASE()"),
               query("SELECT project_code FROM workspace_identity WHERE singleton_id=1"),
               query("SELECT version FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL "
                     "ORDER BY installed_rank DESC LIMIT 1"))
    validate()
    # Create in the same destination filesystem. Link publishes without replacing an existing path.
    with tempfile.TemporaryDirectory(prefix=".zh-mysql-snapshot-", dir=output.parent) as tmp:
        staging = Path(tmp)/"snapshot.sql"
        fd = os.open(staging, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        with os.fdopen(fd, "wb") as file:
            file.write(("-- " + PROJECT + " MySQL V7 logical snapshot; session rows intentionally excluded.\n").encode())
            file.flush()
            for definitions in (True, False):
                subprocess.run(dump_args(dump, option, schema, definitions), check=True, stdout=file,
                               stderr=subprocess.DEVNULL, timeout=args.timeout)
            file.flush()
            os.fsync(file.fileno())
        validate()
        digest = checksum(staging)
        os.link(staging, output)
    return digest


def main():
    """Run the local CLI. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    write = commands.add_parser("backup", help="create a new plain SQL backup, never include session rows")
    write.add_argument("--defaults-file", required=True, help="local MySQL [client] option file")
    write.add_argument("--schema", required=True)
    write.add_argument("--output", required=True)
    write.add_argument("--client-bin", help="directory containing mysql and mysqldump")
    write.add_argument("--timeout", type=int, default=3600, help="max seconds per dump phase")
    read = commands.add_parser("verify", help="offline integrity check against an independently retained SHA-256")
    read.add_argument("snapshot")
    read.add_argument("--sha256", required=True)
    args = parser.parse_args()
    try:
        if args.command == "verify":
            ensure(re.fullmatch("[0-9a-fA-F]{64}", args.sha256), "provide a full SHA-256")
            actual = checksum(args.snapshot)
            ensure(actual == args.sha256.lower(), "backup checksum mismatch")
        else:
            ensure(1 <= args.timeout <= 86400, "timeout must be between 1 and 86400 seconds per phase")
            actual = backup(args)
        print(actual)
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        # Never echo connection options, a subprocess command line or an option file.
        print("Backup failed: " + (str(error) if isinstance(error, ValueError) else type(error).__name__),
              file=sys.stderr)
        return 1
    return 0

if __name__ == "__main__":
    sys.exit(main())
