-- 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
-- Policy rows and recorded decisions are append-only. No backfill of pre-1.3 snapshots.
CREATE TABLE gate_policy (
  tenant VARCHAR(64) NOT NULL, policy_version INT NOT NULL,
  author_id VARCHAR(36) NOT NULL, author VARCHAR(80) NOT NULL, reason VARCHAR(500) NOT NULL,
  rules_json LONGTEXT NOT NULL, request_key VARCHAR(80) NOT NULL, request_hash VARCHAR(64) NOT NULL,
  created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (tenant, policy_version), UNIQUE (tenant,author_id,request_key)
);
CREATE TABLE work_run_gate (
  tenant VARCHAR(64) NOT NULL, run_id VARCHAR(36) NOT NULL, policy_version INT NOT NULL,
  gate_status VARCHAR(20) NOT NULL, decision_json LONGTEXT NOT NULL,
  PRIMARY KEY (tenant,run_id), FOREIGN KEY (run_id) REFERENCES work_run(id)
);
CREATE INDEX run_gate_status ON work_run_gate(tenant,gate_status,run_id);
