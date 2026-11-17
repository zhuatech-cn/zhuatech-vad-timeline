-- 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
CREATE TABLE work_correction (
  tenant VARCHAR(64) NOT NULL,
  source_id VARCHAR(36) NOT NULL,
  child_id VARCHAR(36) NOT NULL,
  source_revision INT NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  changes_json LONGTEXT NOT NULL,
  PRIMARY KEY (tenant,source_id),
  UNIQUE (tenant,child_id),
  FOREIGN KEY (source_id) REFERENCES work_run(id),
  FOREIGN KEY (child_id) REFERENCES work_run(id)
);
