-- 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
-- Stable newest-first sampling; indexed cursor lookup for evidence and identity audits.
CREATE INDEX run_recent_key ON work_run(tenant,created_at,id);
CREATE INDEX audit_action_cursor ON work_audit(tenant,action,seq);
CREATE INDEX audit_actor_cursor ON work_audit(tenant,actor,seq);
