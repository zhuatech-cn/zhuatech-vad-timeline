-- 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
-- Work allocation is independent of the scientific evidence snapshot.
CREATE TABLE review_route (
  tenant VARCHAR(64) NOT NULL, run_id VARCHAR(36) NOT NULL, route_version INT NOT NULL,
  owner_id VARCHAR(36), owner_name VARCHAR(80), changed_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  PRIMARY KEY(tenant,run_id), FOREIGN KEY(run_id) REFERENCES work_run(id),
  FOREIGN KEY(owner_id) REFERENCES app_user(id)
);
CREATE INDEX route_owner_queue ON review_route(tenant,owner_id,run_id);
CREATE TABLE review_route_event (
  tenant VARCHAR(64) NOT NULL, run_id VARCHAR(36) NOT NULL, route_version INT NOT NULL,
  actor_id VARCHAR(36) NOT NULL, actor VARCHAR(80) NOT NULL, action VARCHAR(16) NOT NULL,
  before_owner VARCHAR(80) NOT NULL, after_owner VARCHAR(80) NOT NULL, note VARCHAR(500) NOT NULL,
  request_key VARCHAR(80) NOT NULL, request_hash VARCHAR(64) NOT NULL, created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  PRIMARY KEY(tenant,run_id,route_version), UNIQUE(tenant,actor_id,request_key), FOREIGN KEY(run_id) REFERENCES work_run(id)
);
