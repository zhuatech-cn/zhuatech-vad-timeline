# 语音活动区间验收验收

## 1.3 复核流程验收

1.3.2 并发验收：两人分别打开原单。若数据另有修订进行中，导出须得到完整的旧版本，或提示版本冲突；刷新原单能看到已提交的修订链。只读查看不会抢占复核人的写锁。本项在 WorkflowTest 中同时验证。

部署边界回归：`python3 scripts/test-mysql-snapshot.py` 检查 MySQL 库身份、表范围和不导出会话；完整交付验收另需用实际 MySQL 工具导出到文件，恢复到独立空库并重新登录。

1. 载入[验收批次](../examples/acceptance.json)，按[独立预期](../examples/diagnostics-expected.json)检查「按语音时长评估」。数值允许绝对误差 1e-10；缺失指标不等于 0。
2. 先以管理员试算新规则，核对没有新增版本，随后发布；以业务员再次提交。核对新单有版本快照、原单不变。用另一账号通过/退回；未达标时先试不填例外，再填理由。
3. 从台账设置基线，查指标和逐行证据。下载证据包，用独立记录的摘要核验；`python3 scripts/test-verify-evidence.py` 覆盖内容变动、身份不符和残缺包。不要提交真实业务包。
4. `python3 scripts/smoke.py` 在临时本机数据库完成建单、发布前试算、规则快照、逐条对照、下载后离线校验、重启和修订。


## 修订闭环验收（1.2）

在独立测试库启动，按内置样例生成原单并撤回，依照 [correction.json](../examples/correction.json) 更改指定行字段（`row` 从 0 起），填修订说明并提交。期望新单待复核、详情显示准确的前后值、可双向查看、原结果不变；另一名复核人可以审批。运行 `python3 scripts/smoke.py` 可在临时本地库重复验证，并覆盖重启后关联留存。后端集成用例另覆盖已退回单、跨企业隔离、只读账号、禁止代修、自审、无改动、相同提交键重放、不同键竞争以及第二轮修订。

上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。

1. `sh mvnw -f backend/pom.xml clean package`：运行业务边界与已知结果测试、HTTP 身份认证/角色及租户隔离、并发幂等、审批冲突和备份恢复回归。
2. `node --test scripts/import.test.mjs`：验证导入格式及错误输入。
3. `python3 scripts/smoke.py`：在临时数据库与随机端口启动实际 JAR，检查登录、批次结果、幂等、停机后重启读取、撤回及专项证据。不连接现有业务库。
4. 手工验收：导入 [输入](../examples/acceptance.json)，按[关键指标和结果行](../examples/expected.json)比对，再用独立复核账号处理。

实际生产容量、数据代表性、网络接入及容灾目标由部署方验收。

1.1 专项：运行 InspectionTest 验证报告指标、定位明细和业务边界。启动验收另对照 [复核结果](../examples/inspection-expected.json)，覆盖完整 HTTP 提交和重启读取。原 1.0 数据不需迁移；请在切换前备份，勿将不同项目指向同一库。
