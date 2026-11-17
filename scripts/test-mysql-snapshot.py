#!/usr/bin/env python3
"""Checks backup boundaries without connecting to a database. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/"""
import hashlib
import runpy
import tempfile
import unittest
from pathlib import Path
M = runpy.run_path(str(Path(__file__).with_name("mysql-snapshot.py")))
INV = "\n".join(t+"\tBASE TABLE\tInnoDB" for t in M["TABLES"])

class MySQLSnapshotTest(unittest.TestCase):
    """MySQLSnapshotTest. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
    def test_identity_and_storage_engine(self):
        """Verify identity and storage engine. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
        M["layout"]("production_copy", INV, M["PROJECT"], "7")
        for data in ((INV, "other", "7"), (INV, M["PROJECT"], "5"),
                     (INV.replace("InnoDB", "OTHER", 1), M["PROJECT"], "7"),
                     (INV+"\nunknown\tBASE TABLE\tInnoDB", M["PROJECT"], "7")):
            with self.assertRaises(ValueError):
                M["layout"]("production_copy", *data)
    def test_rejects_invalid_or_system_schemas(self):
        """Verify rejects invalid or system schemas. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
        for name in ("sys", "MYSQL", "information_schema", "-invalid", "a/b", "z"*65, ""):
            with self.assertRaises(ValueError):
                M["layout"](name, INV, M["PROJECT"], "7")
    def test_session_schema_is_retained_but_no_active_token_rows_are_dumped(self):
        """Verify session schema is retained but no active token rows are dumped. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
        ddl = M["dump_args"]("mysqldump", "--defaults-file=/unused", "isolated", True)
        rows = M["dump_args"]("mysqldump", "--defaults-file=/unused", "isolated", False)
        self.assertIn("auth_session", ddl)
        self.assertNotIn("auth_session", rows)
        self.assertIn("--single-transaction", rows)
        for args in (ddl, rows):
            self.assertIn("--skip-add-drop-table", args)
            self.assertIn("--no-login-paths", args)
            self.assertNotIn("--databases", args)
    def test_streamed_checksum(self):
        """Verify streamed checksum. 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/"sample"; data=(b"demo row\n"*190000); p.write_bytes(data)
            self.assertEqual(M["checksum"](p),hashlib.sha256(data).hexdigest())

if __name__ == "__main__":
    unittest.main()
