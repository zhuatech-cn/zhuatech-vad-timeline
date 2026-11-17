# 部署、使用与验收

<a id="acceptance"></a>

## 1.3 验收与交接

**本项目的判定口径：按语音时长评估。** 按时间并集而不是片段数量计算微平均精确率、召回率、F1 和语音交并比。每段录音独立给出结果，分母为零时显示无法估计，不把静音时长算作正确命中。 把 [验收输入](../examples/acceptance.json) 提交后，可用[预期诊断指标](../examples/diagnostics-expected.json)核对。

| 使用场景 | 方法、范围和结果 |
|---|---|
| 读规则 | `GET /api/policy` 获取本企业版本和该项目允许的指标；`GET /api/policy/history?before=0&limit=20` 按版本倒序，后续页用 nextBefore。 |
| 发布前试算（管理员） | `POST /api/policy/preview`，传 expectedVersion、rules、limit（1–10）。用本企业最近批次的已保存指标算草案结果，不重算模型，不创建版本，不改变历史。小样本不能视为全量验收。 |
| 发布/停用（管理员） | `POST /api/policy`，必须带 Idempotency-Key 和 [JSON 示例](../examples/acceptance-policy.json)。发布时按最新版本填写 expectedVersion；最多 8 条，比例为 0–1，空规则表示停用。版本冲突返回 409，核对后重试。|
| 对比历史 | `GET /api/runs/{target}/compare?baseline={id}`；先核对样本、顺序、参数，再读差值（本次减基线）。`.../compare/evidence?baseline={id}&section=diagnostics&filter=DIFFERENCES&page=1&size=20` 按业务键查行，可选 main、inspection、diagnostics，q 检索行标识。缺失、被截断或历史业务键不唯一时明确提示不可对照。|
| 复核和例外 | 新单冻结提交时规则与结论。台账可按 gate 筛选 PASS、FAIL、UNKNOWN、DISABLED、UNCONFIGURED、LEGACY。复核人通过 FAIL/UNKNOWN 时必须另填 exceptionNote；退回和正常通过不要求例外。未发布规则不会导致系统自动通过。|
| 导出证据 | `POST /api/runs/{id}/export` 传 `{"version":1}`（当前批次 revision）。仅能导出本人有权读取的单据；404 不透露其他企业记录。返回 application/zip、X-Evidence-SHA256；版本过时返回 409。管理员审计可查 EXPORT_EVIDENCE，并与业务审批历史区分。|

**接收人离线校验：**把 ZIP 和本项目的 `scripts/verify-evidence.py` 放在本地。在工程根目录运行 `python3 scripts/verify-evidence.py evidence.zip --sha256 <独立取得的64位十六进制摘要> --run <批次UUID>`。也可只给文件名检查包内一致性。成功输出项目、批次、版本、状态和整包摘要，失败返回非零。脚本不联网、不解压落盘。包中含原始输入、结果及复核记录；只向获准接收者传递。摘要不是数字签名，也不加密数据。

在「账号与审计」可按事件、操作人账号或完整批次编号组合查询；例如 `GET /api/audit/page?action=EXPORT_EVIDENCE&runId={id}&limit=20`。账号与动作按数据库比较规则精确匹配，不支持通配符。批次编号同时检索审批和独立导出事件；翻页沿用相同条件。

**从 1.2 升级：**停机备份、在新位置试恢复后切换。V5 增加门槛版本与批次快照表，V6 增加近期批次和审计检索索引，不回写旧结果；旧记录在台账标为历史无快照。需要新诊断请另建批次，不能替历史单补判。H2 离线恢复支持同项目 V3～V7；降级应还原升级前备份，不要让旧服务连接已升级生产库。

**避免接错库：**首次安装使用专用空 schema；迁移前会核对其中的表及项目标识。非空但无本项目身份记录的库、其他项目的库均拒绝启动，不执行迁移。已有 H2/MySQL 升级或演练恢复时，在启动环境设置 `WORKSPACE_REQUIRE_EXISTING=TRUE`（Compose 也支持）：即使目标 schema 是空的也拒绝初始化。不要通过关闭身份检查或手工伪造标识跳过报错。若首次启动曾中断且只留下部分表，先备份并由 DBA 核查，不要删除有数据的库。项目只支持通过 JDBC URL 选库，不使用额外的 Flyway schema 映射。

<a id="mysql-recovery"></a>

### MySQL：逻辑备份与隔离恢复

1. 仅适用于此版原生 V7 schema（InnoDB），不支持自定义业务表、触发器、存储过程、用户授权或服务器配置备份。准备 MySQL 8.4 自带的 `mysql` 与 `mysqldump`。在备份窗口暂停升级/DDL；普通 InnoDB 业务写入可继续。各数据表来自同一次单事务逻辑快照，建表定义单独读取。
2. 由 DBA 在工程以外提供私有 MySQL `[client]` 选项文件，设置主机、用户、密码和所需 TLS 选项；Unix 权限 0600。账号使用该 schema 的最小读取权限。不要将凭据作为命令行参数、随示例提交，或把备份放在代码仓库里。
3. `python3 scripts/mysql-snapshot.py backup --defaults-file /安全路径/reader.cnf --schema workspace --output /安全路径/新的备份.sql`（将示意参数替换为实际值；schema 名支持 ASCII 字母起始及字母、数字、下划线）。成功打印整份 SQL 的 SHA-256。脚本只读源库，不覆盖已有文件，目标文件系统须支持硬链接。备份含业务数据和口令哈希，**不加密**；保存到企业管控位置，加密离机留存。结构中保留 auth_session 表，但不备份会话行，恢复后必须重新登录。
4. 在独立渠道保管摘要。恢复前执行 `python3 scripts/mysql-snapshot.py verify /安全路径/备份.sql --sha256 独立保存的摘要`；此检查只验证字节，不验证来源或可恢复性。**不要将来源不明的 SQL 导入任何数据库。**
5. DBA 准备全新隔离空 schema 和仅能操作该目标库的专用账号；确认目标为空、未连接源应用后，仅将可信快照导入该目标（mysql 客户端的 `--defaults-file` 指向目标账号，数据库参数明确写目标，输入重定向到 SQL）。不要在源库或非空库恢复。以 `WORKSPACE_REQUIRE_EXISTING=TRUE` 启动隔离副本，用原账号重新登录，核对批次编号、原始结果、审批例外、规则版本与审计。核对通过后再依企业发布流程决定是否切换。该工具不替代增量日志、时间点恢复或高可用。
上海如静知华信息科技有限公司 · https://www.zhuatech.cn/

## 本地与生产的区别

本地启动脚本在工程根目录运行 JAR，H2 数据保存在 `var/workspace.mv.db`。只需一个进程，默认仅监听环回地址。账号和业务记录不会随刷新、退出或普通重启丢失。首次须自设管理员密码，随后把管理员、业务员、复核员、只读人员分开；没有硬编码演示账号。示例是页面内的虚构输入，只有点击「运行并保存」才入库。

## MySQL 8.4

使用 Docker Compose 时，将 `.env.example` 复制成 `.env`，分别填写独立的 `ADMIN_PASSWORD`、`DB_ROOT_PASSWORD`、`DB_PASSWORD`；运行 `docker compose up -d --build`。数据库不映射主机端口。Compose 使用项目独立数据卷，**不要使用 down -v 清理生产数据**。首次下载镜像和 Maven 依赖需联网；本地运行已打包的 JAR 不需要联网或模型密钥。

已有数据库可用 `DB_URL`、`DB_USER`、`DB_PASSWORD` 指向本项目专用的空 schema，字符集 utf8mb4。不要让十个项目共用同一 schema。Flyway 在首次运行时建表并追踪迁移，禁止改写已经执行的 V1；新增迁移按版本追加。在正式环境为客户端启用验证证书的 MySQL TLS，不要把 Compose 内网样例连接串直接用于跨网段部署。为每企业部署独立 schema 和实例；无在线自助开租户入口。

<a id="corrections"></a>

## 1.2 修订与再次送审

1. 原提交人打开「已退回」或「已撤回」的详情，选择「发起修订」。核对原处理意见，更正参数或明细并填说明。只改标题、说明或数值书写形式（如 1 改为 1.0）不算业务修改。
2. 「提交修订并送审」会重新运行业务算法，用新编号进入待复核；仅显示产生差异的参数、增删行、变更字段及保留行的相对调序。按行 ID 匹配，改 ID 显示为删除旧行并新增新行。相邻版本可双向查看，源批次保留原结论并追加关联审计。
3. 一张原单只允许生成一个直接后续版本。重复点击不生成多张，另一人不能代修。若后续还需修正，先由独立复核人退回（或由提交人撤回），再从最新一单修订。一般的「复制为新批次」不建立原单关系。
4. `POST /api/runs/{sourceId}/corrections`：Bearer 认证、必传 `Idempotency-Key`。请求体 `{"version":2,"note":"原因","batch":{"title":"标题","settings":{},"rows":[]}}`（结构示意；参数和明细须使用本项目的有效数据）。相同操作者、键和内容的重试返回同一批次。400：说明或输入无效/无实质变化；403：非提交人或无权限；404：原单不可见；409：状态、版本或提交键冲突。
   - 一张原单首次关联修订单时，原单版本加一，输入、结果和业务状态不变。修订单记录的是提交时的原单版本。同一请求键和原始内容可以安全重试；其他旧页面导出会收到 409，重新打开原单即可看到后续修订单和当前版本。升级不会批量重写已有历史证据。
5. 详情 `GET /api/runs/{id}` 可含 `correctedBy`；修订单还包含 `correction.source_id/source_revision/reason/changes`。每项差异含 `kind/id/field/before/after`。可在业务详情或完整 JSON 中查阅。原提交人不能审批自己的修订。
6. **由 1.1 升级：**先停机、备份并验证可恢复。用 1.2 指向本项目原库，Flyway V4 仅新建关联表，不重算旧数据或猜测旧复制单的关系。H2 可用本版工具备份/恢复 V3、V4；如须回退，请停机，用升级前备份恢复到独立目录或 schema 再启旧程序，不要让 1.1 直连已迁移的库。

同一账号、同一提交键的网络重试，若仅 JSON 对象字段顺序或等值数的写法不同（如 `1` 和 `1.0`），仍返回原记录。改变数组顺序、业务值、标题、修订原单或原因不属于相同请求，返回冲突且不会覆盖原记录。

## 日常操作

1. 管理员登录「账号与审计」，建立业务员与复核员，个人账号不共享。
2. 业务端打开「新建分析」。用表格逐行编辑或导入 UTF-8 JSON 批次；可先「载入示例」查看字段格式。数组字段必须是 JSON 数组。全批提交失败不落半笔。
3. 检查结果和限制说明。从台账查看历史输入、明细和操作记录。点「下载输入批次」可重新导入；点「导出完整结果」只导出这一个已选批次，不会下载不相关记录。
4. 第二个账号进详情填写意见，通过或退回；原作者不能自审。完成后历史结果不可覆盖，需更正时复制为新批次。
5. 账号停用或权限变更会使旧会话失效。管理员不能停用或降级自己。管理密码重置用 API；初始环境变量不会覆盖现有密码。

## 接口与权限

除静态页面、健康及登录以外，业务和管理接口均需 `Authorization: Bearer <token>`。同源 H5 页面使用相对路径，不需要跨域白名单。POST/PATCH 用 JSON 和明确的 Content-Length，上限 512 KiB，批次序列化后上限 250,000 字符；每批 1–200 行。

| 路径 | 行为 |
|---|---|
| GET /api/health | 数据库可读且账号初始化完成后才返回 UP |
| POST /api/login | 输入 username、password；返回 token 和 user。连续失败受限 |
| GET /api/me、POST /api/logout | 读取当前身份、使会话失效 |
| POST /api/runs | body: title、settings、rows；请求头 Idempotency-Key（8–80 位）。同人同键同内容返回原记录；同键异内容 409 |
| GET /api/runs?page=1&state=READY | 每页 20 条，本企业可见；可选 READY、APPROVED、REJECTED |
| GET /api/runs/{id} | 查看该企业的输入、结果、版本和审计；其他企业记录返回 404 |
| POST /api/runs/{id}/review | 独立复核人，传 version、state、note；状态/版本冲突 409 |
| GET/POST /api/users、PATCH /api/users/{id} | 管理员列账号、创建账号、修改 role/active/password；PATCH 采用字符串值 |
| GET /api/audit | 管理员查看本企业分页审计记录 |

计算人和复核人须为不同账号。服务端校验角色；VIEWER 不能发起计算，OPERATOR 不能审批，REVIEWER 可计算及复核，ADMIN 可管理。初始工作区为 default，用户输入不能指定租户。401 先重新登录，403 核对角色与制单人，409 刷新版本，503 等初始化完成。不要在日志、问题单或 URL 中记录密码和令牌。

<a id="local-recovery"></a>

## 本地库：停机备份与恢复演练

**先确认使用的是哪个库。** 下列操作只适用于本项目内置 H2 文件库，`workspace.mv.db` 是实际数据。默认路径为项目根目录下的 `var/`。不同项目不要共用数据目录。直接启动的环境变量须在终端或服务管理器中设置；把值写入 Compose 的 `.env` 并不会自动导入当前终端。使用自定义 `DB_URL` 或 MySQL 时，本节的维护命令不适用，使用数据库原生备份，在独立库做恢复演练。

2. **生成和校验备份：** 先正常停止运行中的服务。以下命令在工程根目录执行，示例的 `./var` 要替换为真正的数据目录；备份文件要位于数据目录外。使用自定义 H2 凭据时，提供原 `DB_USER` 和 `DB_PASSWORD`，不要把密码写入命令、日志或工单。
3. **恢复到新位置：** 目标的父目录须已存在，目标数据目录须不存在（即使空目录也不覆盖）。校验不通过或项目不符会拒绝。备份和恢复都需要支持硬链接的本地文件系统及足够临时磁盘空间，不支持超过 8 GiB 的单个数据库文件。命令失败返回非零，不要跳过报错启动新库。

```bash
java -jar backend/target/app.jar maintenance backup ./var ./before-upgrade.zhbk
java -jar backend/target/app.jar maintenance verify ./before-upgrade.zhbk
java -jar backend/target/app.jar maintenance restore ./before-upgrade.zhbk ./restored-data
export WORKSPACE_DATA_DIR="$PWD/restored-data"
export WORKSPACE_REQUIRE_EXISTING=TRUE
bash scripts/start.sh --no-build
```

Windows PowerShell 中，上述三个 Java 命令相同；随后执行：

```powershell
$env:WORKSPACE_DATA_DIR = (Resolve-Path .\restored-data).Path
$env:WORKSPACE_REQUIRE_EXISTING = 'TRUE'
.\scripts\start.cmd --no-build
```

4. **业务验收：** 用原账号重新登录，按备份前的编号核对业务结果、审批与历史；管理员在审计中核对 `RESTORE_LOCAL`，再用可识别的测试批次验证写入。恢复会撤销副本中的全部旧会话，不改密码，不改原库。确认无误后再切换日常启动配置。仅打开已有库的开关不用于全新安装；目录名请勿包含分号。
5. **保管：** 备份含业务明细及账号密码哈希，**没有加密或数字签名**。SHA-256 用于发现意外损坏，不能证明发送者身份。只恢复来自可信来源的备份；按企业策略限制访问、加密离机保存、设置保留期并定期演练。备份不能代替 MySQL 高可用、异地灾备或容量规划。

## 运维与故障处理

- 生产入口使用 HTTPS 反向代理；转发同源的 / 和 /api，不要缓存 /api，不要直接暴露应用端口。仅在可信内网按需设置 BIND_ADDRESS。默认数据库为空，不要把测试数据库或 .env 加入仓库。
- 数据模型：app_user / auth_session 保存哈希密码、会话摘要和有效期；tenant_guard 在同企业内协调提交；work_run 保存不可覆盖的输入与计算快照；work_audit 保存审批和管理事件。按企业、状态、时间和业务标识建索引。不包含硬删除 API。
- 本地 H2：停机后复制整个 var 目录到受控存储；恢复到独立测试目录，再启动同版本核对批次 ID、总数及历史。MySQL：定期一致性备份（例如 mysqldump --single-transaction），用全新隔离 schema 演练恢复，禁止直接覆盖活动库。账号密码及数据库备份纳入权限和加密管理。
- 源码修改后重新运行 `./mvnw -f backend/pom.xml package`，并重启旧进程。前端在 frontend/，构建时打入 JAR，无须 Node、外部 CDN 或跨域配置。Windows 可在 PowerShell 设置环境变量后运行 `java -jar backend/target/app.jar`。

## 管理员交接与账号恢复

- 交接前先建立第二位实名管理员，让对方独立登录，再由其调整原账号。本企业的账号管理操作串行提交，获得锁后重新验证当前会话；禁止自行停用或降级。未改变角色或状态时不撤销会话，不接受空密码重置。
- 连续 5 次失败锁定 15 分钟。到期后重新计数；也可由另一名管理员线下核实身份后，在「账号与审计」的锁定账号行填原因并解锁。解锁只适用于启用账号，不改密码，撤销该账号的旧会话。只有一个管理员时可等待锁定到期。不要在解锁依据中填写密码、验证码或证件号。
- POST /api/users/{id}/unlock：JSON 示例 {"note":"已与业务负责人核实"}。空依据 400，跨企业目标 404，已解锁或停用 409。GET /api/users 增加 login_locked（1/0）。
- GET /api/audit/page?limit=50：按序号倒序，hasMore 为真时使用 nextBefore 作为下一页的 before 参数，每页 1–100 条；向历史翻页不会因新事件重复读到旧事件。兼容旧 GET /api/audit（最近 100 条）。变更日志记录角色及启用状态前后值，密码重置仅记动作。
- 升级前备份。Flyway V2 仅新增 work_audit(tenant,seq) 索引，不改旧记录。大库请安排维护窗口；确认运行账号有建索引权限，迁移失败不要清库。
- 启动前用 scripts/doctor.py，启动后用 scripts/check-service.py。后者仅访问 127.0.0.1 和本项目端口（可用 PORT 覆盖），不登录、不写库、不跟随重定向。不能替代业务流程测试。

## 业务纠错与重复提交

- 导入 UTF-8 JSON（可带 BOM）时先校验整个文件；顶层仅允许 title/settings/rows，字段、数组元素和数值类型按本工程样本校验。格式不符时显示行或参数名，不替换已有编辑内容。文件载入并不意味着入库，仍需点击「运行并保存」；业务约束由后端再次检查。
- 提交人在自己待复核的批次详情填写原因（1–1000 字），点「确认撤回」。其他人（包括管理员）不能代撤回；只读人员不可操作。撤回不删除、修改原输入或计算结果；需要改正时点「复制为新批次」。新批次独立编号，不会覆盖历史。
- 接口：POST /api/runs/{id}/withdraw，JSON 示例：{"version":1,"note":"原始样本选错"}。成功后状态 WITHDRAWN、版本 +1，记录操作人、时间和原因。GET /api/runs?state=WITHDRAWN 可查询；不再出现在待复核队列。撤回与复核使用同一版本检查，如遇 409 请重新打开详情确认实际状态。
- 调用方网络超时可沿用原 Idempotency-Key 和完全相同的请求体重试。多人并发时相同键以当前提交人和企业为范围返回同一笔；更换内容而不换键返回 409。不得把同一键用于另一笔业务。
- 从源码 ZIP 解压时也可用 bash scripts/start.sh，初始化脚本不依赖 mvnw 文件拥有可执行权限。本地启动不需要 Node；维护者可选用 Node 18+ 运行 node --test scripts/import.test.mjs。

## 日常使用与升级

- macOS/Linux 在根目录运行 `bash scripts/start.sh`；Windows 在 cmd 或 PowerShell 运行 `.\scripts\start.cmd`。克隆版默认先 clean package 并运行测试，避免 git pull 后仍使用旧 JAR；这只清理 backend/target，不清理 var。已测试构建后可显式加 `--no-build` 快速启动，需重新构建时用 `--build`。
- 源码需要完整 JDK 21，Maven Wrapper 固定 3.9.11 并校验分发包 SHA-256，首次需能访问 Maven Central。运行包已有 JAR，可用 Java 21 离线运行。`.env` 只供 Compose；直接运行 JAR 请用系统环境变量。管理员初始化根据数据库账号记录判定，而非根据文件是否存在。没有交互终端时务必为首次启动设置 ADMIN_PASSWORD；密码无效时修正并重启，**不要删除数据库**。
- 改密：登录后点「修改我的密码」。新密码至少 12 个 Unicode 字符、最多 72 个 UTF-8 字节，不能全空格或与原密码相同。成功后包括当前窗口在内的所有会话都失效。改密接口 `POST /api/me/password` 仅接受 currentPassword 和 newPassword；审计不记录密码。登录及改密的错误原密码共享账号锁定策略。
- 台账：标题支持字面量子串查询（% 和 _ 不是通配符）；按「我提交的」过滤，复核人可选「待我复核」。接口 `GET /api/runs?q=标题&scope=all|mine|reviewable&state=READY&page=1`；reviewable 只包含本企业 READY 且非本人提交的批次。
- 性能边界：同步小批计算，单企业写入串行，同实例可服务多个用户；不是海量离线训练集群。未认证生产 SLA、异地容灾、SSO、峰值并发或针对客户数据的模型精度。生产上线前须按实际负载、数据保留、备份恢复、HTTPS 和账号制度验收。
