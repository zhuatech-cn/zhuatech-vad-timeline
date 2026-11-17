-- 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
CREATE TABLE tenant_guard (tenant VARCHAR(64) PRIMARY KEY);
CREATE TABLE app_user (id VARCHAR(36) PRIMARY KEY, tenant VARCHAR(64) NOT NULL, username VARCHAR(80) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL, role VARCHAR(16) NOT NULL, active BOOLEAN NOT NULL, failures INT NOT NULL DEFAULT 0, locked_until TIMESTAMP NULL);
CREATE TABLE auth_session (token_hash VARCHAR(64) PRIMARY KEY, user_id VARCHAR(36) NOT NULL REFERENCES app_user(id), expires_at TIMESTAMP NOT NULL);
CREATE INDEX session_expiry ON auth_session(expires_at);
CREATE TABLE work_run (id VARCHAR(36) PRIMARY KEY,tenant VARCHAR(64) NOT NULL,actor_id VARCHAR(36) NOT NULL,request_key VARCHAR(80) NOT NULL,fingerprint VARCHAR(64) NOT NULL,title VARCHAR(100) NOT NULL,stage VARCHAR(16) NOT NULL,revision INT NOT NULL,input_json LONGTEXT NOT NULL,result_json LONGTEXT NOT NULL,created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL, UNIQUE(tenant,actor_id,request_key));
CREATE INDEX run_list ON work_run(tenant,stage,created_at,id);
CREATE TABLE work_audit(seq BIGINT AUTO_INCREMENT PRIMARY KEY,tenant VARCHAR(64) NOT NULL,actor VARCHAR(80) NOT NULL,subject VARCHAR(100) NOT NULL,action VARCHAR(60) NOT NULL,note VARCHAR(1024) NOT NULL,created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL);
CREATE INDEX audit_subject ON work_audit(tenant,subject,seq);
