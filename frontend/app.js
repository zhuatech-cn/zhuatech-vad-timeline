// 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
import {routingDesk} from "./routing.mjs";
import {scenariosDesk} from "./scenarios.mjs";
import { readBatch } from "./batch-import.mjs";
import { evidencePage } from "./inspection.mjs";
import { gateNames, needsException, reviewDesk } from "./review-desk.mjs";
import { canCorrect, submitEnvelope, submissionKey, formatDifference } from "./corrections.mjs";
const $ = (id) => document.getElementById(id),
  cat = await fetch("/catalog.json").then((r) => r.json());
let token = sessionStorage.getItem("workspace-token") || "",
  user = null,
  view = "",
  page = 1,
  state = "",
  query = "", scope = "all", gateFilter="", baseline=null,
  busy = false,
  currentKey = "",
  lastBody = "";
const statusNames = { READY: "待复核", APPROVED: "已通过", REJECTED: "已退回", WITHDRAWN: "已撤回" },
  roles = {
    ADMIN: "管理员",
    OPERATOR: "业务员",
    REVIEWER: "复核员",
    VIEWER: "只读",
  };
const labels = {
  
  stage: "状态",
  created_at: "时间",
  actor: "操作人",
  action: "操作",
  note: "说明",
  subject: "对象",
  username: "账号",
  role: "角色",
  active: "启用",
  confidence: "置信度",
  predicted: "预测类别",
  review: "需人工复核",
  errorRate: "错误率",
  substitutions: "替换",
  deletions: "删除",
  insertions: "插入",
  referenceUnits: "参考单元",
  start: "开始",
  end: "结束",
  decision: "判定",
  split: "集合",
  title: "批次名称",
  selfMs: "独占毫秒",
  requiredGiB: "所需 GiB",
  marginGiB: "余量 GiB",
  minSimilarity: "最低相似度",
  representative: "代表样本",
  selected: "选入",
  needsReview: "待复核数",
  totalValue: "总价值",
  free: "剩余",
  used: "已用",
  regressions: "退步查询",
  context: "编排正文",
};
const resultLabels = {
  silentSegments: "静音段数",
  falseAlarmUnits: "静音误报单元",
  silentReference: "静音参考段",
  ...labels,
  ...{
    knownTokens: "词表命中数",
    training: "训练样本",
    predictions: "预测工单",
    classes: "类别数",
    vocabulary: "词表大小",
    documents: "文档数",
    chunks: "切片数",
    sourceUnits: "源文码点",
    part: "片段序号",
    sha256: "内容摘要",
    configurations: "候选配置",
    fits: "满足容量",
    exceeds: "超出容量",
    weightGiB: "权重 GiB",
    kvPerSequenceGiB: "单序列缓存 GiB",
    maxConcurrency: "最大并发",
    components: "关联组数",
    component: "关联组代表",
    test: "测试集",
    emptySplit: "存在空集合",
    vectors: "向量数",
    dimensions: "维度",
    groups: "保留组数",
    candidates: "疑似重复",
    baseRecall: "基线召回率",
    candidateRecall: "候选召回率",
    baseMRR: "基线 MRR",
    candidateMRR: "候选 MRR",
    baseRR: "基线倒数排名",
    candidateRR: "候选倒数排名",
    baseNDCG: "基线 nDCG",
    candidateNDCG: "候选 nDCG",
    regression: "排序退步",
    consensus: "达成一致",
    adjudicated: "已裁决",
    fleissKappa: "Fleiss κ",
    tally: "标签票数",
    votes: "票数",
    elapsedMs: "总耗时（毫秒）",
    spans: "调用数",
    hottest: "最大独占耗时节点",
    durationMs: "持续毫秒",
    childrenUnionMs: "子调用占用毫秒",
    reason: "选择依据",
    reserved: "回复预留",
    revision: "版本",
    train: "训练集",
    validation: "验证集",
    agreement: "实际一致率",
    start: "起点",
    end: "终点",
  },
};
Object.assign(labels, cat.labels); Object.assign(resultLabels, cat.labels);
const choices = {
  KEEP: "保留",
  REVIEW_DUPLICATE: "待查重",
  CONSENSUS: "一致",
  REVIEW: "待裁决",
  ADJUDICATED: "已裁决",
  REQUIRED: "必选",
  OPTIMAL: "预算内优选",
  NOT_SELECTED: "未入选",
  train: "训练集",
  validation: "验证集",
  test: "测试集",
  RUN: "运行并保存",
  "READY -> WITHDRAWN": "提交人撤回",
};
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function el(tag, text, cls) {
  const n = document.createElement(tag);
  if (text != null) n.textContent = text;
  if (cls) n.className = cls;
  return n;
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function info(msg) {
  $("notice").textContent = msg || "";
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
async function api(path, method = "GET", body, key) {
  const headers = {};
  if (token) headers.Authorization = "Bearer " + token;
  if (body != null) headers["Content-Type"] = "application/json";
  if (key) headers["Idempotency-Key"] = key;
  const r = await fetch("/api" + path, {
    method,
    headers,
    body: body == null ? undefined : JSON.stringify(body),
  });
  if (!r.ok) {
    let m = "请求失败 (" + r.status + ")";
    try {
      m = (await r.json()).message || m;
    } catch {}
    if (r.status === 401) off();
    throw Error(m);
  }
  const t = await r.text();
  return t ? JSON.parse(t) : null;
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
async function act(fn) {
  if (busy) return;
  busy = true;
  info("");
  try {
    await fn();
  } catch (e) {
    info(e.message);
  } finally {
    busy = false;
  }
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function button(text, fn, primary) {
  const b = el("button", text, primary ? "primary" : "");
  b.type = "button";
  b.onclick = () => act(fn);
  return b;
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function off() {
  routing.reset();
  page = 1; state = ""; scope = "all"; gateFilter=""; baseline=null; query = ""; currentKey = ""; lastBody = "";
  user = null;
  token = "";
  sessionStorage.removeItem("workspace-token");
  $("account").textContent = "";
  $("shell").hidden = true;
  $("login").hidden = false;
  $("body").replaceChildren();
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
async function enter() {
  user = await api("/me");
  $("login").hidden = true;
  $("shell").hidden = false;
  $("account").textContent = user.username + " · " + roles[user.role];
  $("entry").hidden = user.role === "VIEWER";
  $("routing").hidden = !["ADMIN","REVIEWER"].includes(user.role);
  $("accounts").hidden = $("adminCaption").hidden = user.role !== "ADMIN";
  await (user.role === "VIEWER" ? ledger() : entry());
}
$("signin").onsubmit = (e) => {
  e.preventDefault();
  act(async () => {
    const f = e.target;
    const x = await api("/login", "POST", Object.fromEntries(new FormData(f)));
    token = x.token;
    sessionStorage.setItem("workspace-token", token);
    f.reset();
    await enter();
  });
};
$("logout").onclick = () =>
  act(async () => {
    await api("/logout", "POST");
    off();
  });
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function head(v, t, d) {
  view = v;
  $("crumb").textContent =
    cat.title + " / " + (v === "accounts" ? "系统管理" : "业务工作区");
  $("heading").textContent = t;
  $("intro").textContent = d;
  $("body").replaceChildren();
  window.scrollTo(0,0);
  for (const k of ["entry", "ledger", "accounts", "security","policy","routing"])
    $(k).classList.toggle("active", k === v);
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function box(t) {
  const n = el("div", null, "box");
  if (t) n.append(el("h2", t));
  $("body").append(n);
  return n;
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function input(type, val = "") {
  const n = el("input");
  n.type = type;
  n.value = val;
  return n;
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function field(parent, k, n) {
  const l = el("label", labels[k] || k);
  l.append(n);
  parent.append(l);
  return n;
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function fmt(v) {
  if (v == null) return "—";
  if (typeof v === "boolean") return v ? "是" : "否";
  if (typeof v === "object") return JSON.stringify(v);
  return typeof v === "number" && !Number.isInteger(v)
    ? (Math.abs(v)<1e-5?v.toExponential(4):Number(v.toPrecision(7)).toString())
    : statusNames[v] || roles[v] || String(v);
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function table(parent, keys, rows, action) {
  const wrap = el("div", null, "scroll"),
    tb = el("table"),
    tr = tb.createTHead().insertRow();
  for (const k of keys) tr.append(el("th", resultLabels[k] || k));
  if (action) tr.append(el("th", "操作"));
  const tbody = tb.createTBody();
  for (const row of rows) {
    const line = tbody.insertRow();
    for (const k of keys)
      line.insertCell().textContent =
        (k==="修订前" || k==="修订后") ? formatDifference(row[k]) : typeof row[k] === "string" &&
        ["decision", "action", "reason", "split"].includes(k)
          ? choices[row[k]] || row[k]
          : fmt(row[k]);
    if (action) line.insertCell().append(action(row));
  }
  wrap.append(tb);
  parent.append(wrap);
  if (!rows.length) parent.append(el("p", "没有符合条件的记录。", "muted"));
  return tbody;
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function entry(seed, source=null, revisionNote="") {
  const draft = structuredClone(
    seed || {
      title: "",
      settings: cat.settings,
      rows: cat.rows.slice(0, 1).map(blank),
    },
  );
  head("entry", source ? "修订批次" : "新建分析", source ? "修订会单独编号并重新送审，原件和原处理意见不被覆盖。" : cat.description);
  let note;
  if(source) {
    const panel=box("修订依据");
    panel.append(el("p","原单："+source.title+" · "+statusNames[source.stage]+" · 版本 "+source.revision));
    const last=source.history.filter(h=>h.action==="READY -> "+source.stage).at(-1);
    if(last)panel.append(el("p","原处理意见："+last.note));
    note=field(panel,"修订说明",input("text",revisionNote));note.required=true;note.maxLength=1000;
  }
  const top = box("批次与参数"),
    bar = el("div", null, "settings");
  top.append(bar);
  const title = field(bar, "title", input("text", draft.title));
  title.placeholder = "例如：本周验收批次";
  title.maxLength = 100;
  const config = {};
  for (const [k, v] of Object.entries(cat.settings)) {
    config[k] = field(
      bar,
      k,
      input(typeof v === "number" ? "number" : "text", draft.settings[k]),
    );
    if (typeof v === "number") config[k].step = "any";
  }
  const area = box("输入明细"),
    tools = el("div", null, "bar");
  area.append(tools);
  const spec = cat.rows[0],
    keys = Object.keys(spec),
    wrap = el("div", null, "scroll"),
    tb = el("table");
  wrap.append(tb);
  area.append(wrap);
  const h = tb.createTHead().insertRow();
  for (const k of [...keys, "操作"]) h.append(el("th", labels[k] || k));
  const tbody = tb.createTBody();
  let editors = [];
  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  function append(row) {
    if (editors.length >= 200) throw Error("单批最多 200 条");
    const tr = tbody.insertRow(),
      values = {};
    for (const k of keys) {
      const base = spec[k],
        n =
          typeof base === "boolean"
            ? input("checkbox")
            : typeof base === "number"
              ? input("number")
              : el("textarea");
      n.setAttribute("aria-label", labels[k] || k);
      if (n.type === "checkbox") n.checked = row[k] === true;
      else
        n.value = Array.isArray(base)
          ? JSON.stringify(row[k] ?? [])
          : (row[k] ?? "");
      if (n.type === "number") n.step = "any";
      if (n.tagName === "TEXTAREA") n.rows = 1;
      tr.insertCell().append(n);
      values[k] = n;
    }
    editors.push(values);
    tr.insertCell().append(
      button("移除", () => {
        tr.remove();
        editors = editors.filter((a) => a !== values);
      }),
    );
  }
  for (const r of draft.rows) append(r);
  tools.append(
    button("增加一行", () => append(blank(spec))),
    button("载入示例", () => {
      if (editors.some((r) => r.id.value.trim()) && !confirm("替换当前明细？"))
        return;
      entry({
        title: title.value || cat.scene + "样例",
        settings: cat.settings,
        rows: cat.rows,
      },source,note?.value);
    }),
  );
  const picker = input("file");
  picker.accept = ".json";
  field(tools, "导入 JSON 批次", picker);
  picker.onchange = () =>
    act(async () => {
      const f = picker.files[0];
      if (!f) return;
      if (f.size > 524288) throw Error("文件上限 512 KiB");
      try {
        const batch = readBatch(await f.text(), cat);
        entry(batch,source,note?.value);
        info("已载入 " + batch.rows.length + " 行，尚未提交。");
      } finally { picker.value = ""; }
    });
  const end = el("div", null, "bar");
  area.append(end);
  end.append(
    button(
      source ? "提交修订并送审" : "运行并保存",
      async () => {
        const settings = {};
        for (const [k, n] of Object.entries(config))
          settings[k] =
            typeof cat.settings[k] === "number" ? number(n.value, k) : n.value;
        const rows = editors.map((r, i) =>
          Object.fromEntries(
            keys.map((k) => {
              const n = r[k],
                base = spec[k];
              let v = n.value;
              if (Array.isArray(base)) {
                try {
                  v = JSON.parse(v);
                } catch {
                  throw Error("第 " + (i + 1) + " 行 " + k + " 不是 JSON 数组");
                }
                if (!Array.isArray(v)) throw Error(k + " 需要数组");
              } else if (typeof base === "number") v = number(v, k);
              else if (typeof base === "boolean") v = n.checked;
              return [k, v];
            }),
          ),
        );
        const b = { title: title.value, settings, rows },
          envelope=submitEnvelope(source,note?.value || "",b), s=JSON.stringify(envelope);
        if (!currentKey || lastBody !== s) {
          currentKey = submissionKey();
          lastBody = s;
        }
        const result = await api(envelope.path, "POST", envelope.body, currentKey);
        currentKey = "";
        show(result);
      },
      true,
    ),
  );
  area.append(el("p", cat.limit, "muted"));
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function blank(spec) {
  return Object.fromEntries(
    Object.entries(spec).map(([k, v]) => [
      k,
      Array.isArray(v) ? [] : typeof v === "boolean" ? false : "",
    ]),
  );
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function number(v, k) {
  if (!v.trim() || !Number.isFinite(Number(v)))
    throw Error((labels[k] || k) + " 需要数值");
  return Number(v);
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function download(data, name) {
  const url = URL.createObjectURL(
    new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }),
  );
  const a = el("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
async function ledger() {
  const r = await api("/runs?" + new URLSearchParams({ page, state, q:query, scope, gate:gateFilter }));
  head(
    "ledger",
    "结果台账",
    "按标题、状态或办理范围查找批次；待我复核不包含自己提交的记录。",
  );
  const n = box(),
    bar = el("form", null, "bar");
  n.append(bar);
  const s = el("select");
  for (const [k, v] of Object.entries({ "": "全部状态", ...statusNames }))
    s.append(new Option(v, k));
  s.value = state;
  field(bar, "状态", s);
  const gateSelect=el("select");gateSelect.add(new Option("全部验收结论",""));for(const [k,v] of Object.entries(gateNames))gateSelect.add(new Option(v,k));
  gateSelect.value=gateFilter;field(bar,"门槛结论",gateSelect);
  const range = el("select");range.add(new Option("全部批次","all"));range.add(new Option("我提交的","mine"));
  if(["ADMIN","REVIEWER"].includes(user.role))range.add(new Option("待我复核","reviewable"));
  range.value=scope;field(bar,"业务范围",range);
  const keyword=field(bar,"批次标题",input("search",query));keyword.maxLength=100;
  bar.append(el("button","查询","primary"));
  bar.onsubmit=e=>{e.preventDefault();act(async()=>{state=s.value;scope=range.value;gateFilter=gateSelect.value;query=keyword.value.trim();page=1;await ledger();});};
  s.onchange=range.onchange=gateSelect.onchange=()=>bar.requestSubmit();
  if(baseline){const notice=el("div",null,"bar");notice.append(el("span","比较基线："+baseline.title),button("清除基线",()=>{baseline=null;return ledger();}));n.append(notice);}
  table(n, ["title", "stage", "门槛结论","revision", "created_at"], r.items.map(v=>({...v,"门槛结论":gateNames[v.gate_status]||"—"})), (v) =>{
    const tools=el("div",null,"bar");tools.append(button("查看",async()=>show(await api("/runs/"+v.id))));
    if(baseline && baseline.id!==v.id)tools.append(button("与基线对比",()=>desk.comparison(baseline.id,v.id)));
    else if(!baseline)tools.append(button("设为基线",()=>{baseline={id:v.id,title:v.title};return ledger();}));
    return tools;
  });
  const p = el("div", null, "bar");
  n.append(p);
  const prev = button("上一页", async () => {
      page--;
      await ledger();
    }),
    next = button("下一页", async () => {
      page++;
      await ledger();
    });
  prev.disabled = page === 1;
  next.disabled = page * 20 >= r.total;
  p.append(
    prev,
    el("span", "第 " + page + " 页 · 共 " + r.total + " 条"),
    next,
  );
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function show(r) {
  head(
    "ledger",
    r.title,
    "批次结果 · " + statusNames[r.stage] + " · 版本 " + r.revision,
  );
  const workbar=box();if(["REVIEWER","ADMIN"].includes(user.role))workbar.append(button("查看分派与交接",()=>routing.detail(r.id)));
  if(user.role!=="VIEWER")workbar.append(button("业务参数试算",()=>scenarios(r)));
  if(!workbar.childNodes.length)workbar.remove();
  desk.gatePanel(r);
  if(r.correction || r.correctedBy || canCorrect(r,user)){
    const panel=box("修订记录"), bar=el("div",null,"bar");panel.append(bar);
    if(r.correction){
      const c=r.correction;
      bar.append(button("查看原批次",async()=>show(await api("/runs/"+c.source_id))),button("对比原单指标",()=>desk.comparison(c.source_id,r.id)));
      panel.append(el("p","修订说明："+c.reason+" · 原单版本 "+c.source_revision));
      const types={SETTING:"参数修改",EDIT:"行内修改",REMOVE:"删除行",ADD:"新增行",ORDER:"保留行调序"};
      const changes=c.changes.map(d=>({"变更类型":types[d.kind],"行编号":d.id||"—","字段":labels[d.field]||d.field||"—","修订前":d.before,"修订后":d.after}));
      const body=el("div"),pager=el("div",null,"bar");
      panel.append(body,pager);
      const render=p=>{
        const page=evidencePage(changes,"",p);body.replaceChildren();pager.replaceChildren();
        table(body,["变更类型","行编号","字段","修订前","修订后"],page.items);
        const prev=button("上一页差异",()=>render(page.page-1)),next=button("下一页差异",()=>render(page.page+1));
        prev.disabled=page.page===1;next.disabled=page.page===page.pages;
        pager.append(prev,el("span","共 "+page.total+" 项 · 第 "+page.page+" / "+page.pages+" 页"),next);
      };render(1);
    }
    if(r.correctedBy)bar.append(button("查看后续修订",async()=>show(await api("/runs/"+r.correctedBy))));
    if(canCorrect(r,user))bar.append(button("发起修订",()=>entry(r.input,r),true));
  }
  const n = box("分析结果"),
    m = el("div", null, "metrics");
  n.append(m);
  for (const [k, v] of Object.entries(r.result.metrics).sort(([a], [b]) =>
    a.localeCompare(b),
  )) {
    const block = el("div");
    block.append(el("small", resultLabels[k] || k), el("strong", fmt(v)));
    m.append(block);
  }
  desk.diagnosticPanel(r);
  const details = box("明细");
  if(r.result.inspection){
    const report=r.result.inspection, panel=box(cat.inspectionTitle);
    details.before(panel);
    panel.append(el("p",cat.inspectionHelp,"muted"));
    const stats=el("div",null,"metrics");
    for(const [key,val] of Object.entries(report.metrics)){
      const item=el("div");item.append(el("small",resultLabels[key]||key),el("strong",fmt(val)));stats.append(item);
    }
    const toolbar=el("div",null,"bar"), search=field(toolbar,"筛选复核明细",input("search"));
    const body=el("div"),nav=el("div",null,"bar");
    panel.append(stats,toolbar,body,nav);
    const keys=Object.keys(report.rows[0]||{});
    function render(which){
      const v=evidencePage(report.rows,search.value,which);
      body.replaceChildren();nav.replaceChildren();
      if(v.items.length)table(body,keys,v.items);
      else body.append(el("p",report.rows.length?"没有匹配的复核明细。":"本批次没有可列出的复核明细。","muted"));
      const prev=button("上一页明细",()=>render(v.page-1)), next=button("下一页明细",()=>render(v.page+1));
      prev.disabled=v.page<=1;next.disabled=v.page>=v.pages;
      nav.append(prev,el("span","共 "+v.total+" 条 · 第 "+v.page+" / "+v.pages+" 页"),next);
    }
    search.oninput=()=>render(1);render(1);
  }else{
    details.before(el("p","历史快照未生成专项复核报告；如需分析，请复制为新批次。","muted"));
  }
  table(
    details,
    Object.keys(r.result.rows[0] || {}).sort((a, b) =>
      a === b ? 0 : a === "id" ? -1 : b === "id" ? 1 : a.localeCompare(b),
    ),
    r.result.rows,
  );
  if (r.result.context != null) details.append(el("pre", r.result.context));
  const bar = el("div", null, "bar");
  details.append(bar);
  bar.append(
    button("导出完整结果", () => download(r, r.id + ".json")),
    button("下载输入批次", () => download(r.input, "batch.json")),
    button("下载验收证据包",async()=>{
      const res=await fetch("/api/runs/"+encodeURIComponent(r.id)+"/export",{method:"POST",headers:{Authorization:"Bearer "+token,"Content-Type":"application/json"},body:JSON.stringify({version:r.revision})});
      if(res.status===409){
        if(!bar.querySelector("[data-refresh-evidence]")){
          const refresh=button("刷新原单并核对版本",async()=>{show(await api("/runs/"+encodeURIComponent(r.id)));info("已载入最新版本；核对后可再次下载。");});
          refresh.dataset.refreshEvidence="true";bar.append(refresh);
        }
        info("原单版本已变化，本次没有下载文件。请刷新原单并核对版本。");return;
      }
      if(!res.ok){if(res.status===401)off();let msg="导出失败（"+res.status+"）";try{msg=(await res.json()).message||msg;}catch{}throw Error(msg);}
      const digest=res.headers.get("X-Evidence-SHA256"),blob=await res.blob();
      const url=URL.createObjectURL(blob),anchor=el("a");anchor.href=url;anchor.download="zhuatech-vad-timeline-"+r.id+"-evidence.zip";anchor.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
      info("已导出本批次，SHA-256："+digest+"。可将摘要通过独立渠道交给接收人核验。");
    }),
  );
  if (user.role !== "VIEWER")
    bar.append(button("复制为新批次", () => entry(r.input)));
  if (r.stage === "READY" && r.actor_id === user.id && user.role !== "VIEWER") {
    const panel = box("撤回待复核批次");
    const form = el("form", null, "bar");
    panel.append(form);
    const reason = field(form, "撤回原因", input("text"));
    reason.required = true;
    reason.maxLength = 1000;
    form.append(el("button", "确认撤回"));
    form.onsubmit = (event) => {
      event.preventDefault();
      act(async () => {
        if (!reason.value.trim()) throw Error("请填写撤回原因");
        show(await api("/runs/" + r.id + "/withdraw", "POST", {version: r.revision, note: reason.value}));
        info("批次已撤回，原记录保留。要记录修改差异，请选择「发起修订」。");
      });
    };
  }
  const hist = box("操作记录");
  table(hist, ["actor", "action", "note", "created_at"], r.history);
  if (
    ["ADMIN", "REVIEWER"].includes(user.role) &&
    r.stage === "READY" &&
    r.actor_id !== user.id
  ) {
    const b = box("独立复核"),
      note = field(b, "复核意见", input("text"));
    const basis=needsException(r.gate?.status)?field(b,"例外放行依据（仅通过时填写）",input("text")):null;if(basis)basis.maxLength=800;
    const actions = el("div", null, "bar");
    b.append(actions);
    for (const stage of ["APPROVED", "REJECTED"])
      actions.append(
        button(
          statusNames[stage],
          async () =>
            show(
              await api("/runs/" + r.id + "/review", "POST", {
                version: r.revision,
                state: stage,
                note: note.value,
                exceptionNote: stage==="APPROVED" && basis ? basis.value : null,
              }),
            ),
          stage === "APPROVED",
        ),
      );
  }
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
async function accounts() {
  const [users, audit] = await Promise.all([api("/users"), api("/audit/page?limit=50")]);
  head(
    "accounts",
    "账号与审计",
    "按岗位授予最小权限；停用、改密或变更角色会撤销旧会话。",
  );
  const b = box("新增账号"),
    f = el("form", null, "bar");
  b.append(f);
  const name = field(f, "username", input("text")),
    pw = field(f, "初始密码", input("password")),
    role = el("select");
  for (const [k, v] of Object.entries(roles)) role.append(new Option(v, k));
  role.value = "VIEWER";
  field(f, "role", role);
  f.append(el("button", "新增账号", "primary"));
  f.onsubmit = (e) => {
    e.preventDefault();
    act(async () => {
      await api("/users", "POST", {
        username: name.value,
        password: pw.value,
        role: role.value,
      });
      await accounts();
    });
  };
  table(box("企业账号"), ["username", "role", "active", "登录状态"], users.map(row=>({
    ...row, "登录状态": !row.active ? "停用" : Number(row.login_locked) ? "暂时锁定" : "正常",
  })), (row) => {
    const g = el("div", null, "bar");
    const sw = button(row.active ? "停用" : "启用", async () => {
      await api("/users/" + row.id, "PATCH", { active: String(!row.active) });
      await accounts();
    });
    sw.disabled = row.id === user.id;
    const access=el("select");access.setAttribute("aria-label",row.username+" 的角色");
    for (const [k,v] of Object.entries(roles))access.add(new Option(v,k));access.value=row.role;
    const save=button("保存角色",async()=>{if(access.value===row.role)return;
      if(!confirm("变更 "+row.username+" 的角色并撤销旧会话？"))return;
      await api("/users/"+row.id,"PATCH",{role:access.value});await accounts();});
    access.disabled=save.disabled=row.id===user.id;
    g.append(access,save,sw);
    if (row.active && Number(row.login_locked) && row.id !== user.id) {
      const form=el("form",null,"bar");
      const reason=field(form,row.username+" 的解锁依据",input("text"));
      reason.required=true; reason.maxLength=500;
      form.append(el("button","解除登录锁定","primary"));
      form.onsubmit=e=>{ e.preventDefault(); act(async()=>{
        await api("/users/"+row.id+"/unlock","POST",{note:reason.value});
        await accounts(); info("账号已解锁，旧会话已撤销；请使用原密码重新登录。");
      }); };
      g.append(form);
    }
    return g;
  });
  const journal=box("操作审计"), controls=el("div",null,"bar"), records=el("div");
  const filters=el("form",null,"bar"),eventType=el("select");eventType.add(new Option("全部事件",""));
  for(const [code,text] of Object.entries({"PUBLISH_GATE_POLICY":"规则发布","GATE_EXCEPTION":"例外放行","ROUTE_CLAIM":"领取复核","ROUTE_ASSIGN":"管理员派单","ROUTE_HANDOFF":"交接复核","ROUTE_RELEASE":"释放待办","EXPORT_EVIDENCE":"证据导出","READY -> APPROVED":"复核通过","READY -> REJECTED":"复核退回","READY -> WITHDRAWN":"撤回","CREATE_USER":"新增账号","CHANGE_USER":"变更账号","UNLOCK_USER":"解除锁定","PASSWORD_CHANGED":"修改密码","RESTORE_LOCAL":"本地恢复"}))eventType.add(new Option(text,code));
  field(filters,"审计事件",eventType);const who=field(filters,"操作人账号",input("search")),which=field(filters,"批次编号",input("search"));
  who.maxLength=80;which.maxLength=36;let criteria={};const cursors=[""];
  const fetchPage=(before,query=criteria)=>api("/audit/page?"+new URLSearchParams({...query,limit:50,...(before?{before}:{})}));
  const searchAudit=async(next)=>{const data=await fetchPage("",next);criteria=next;cursors.splice(0,cursors.length,"");render(data);};
  filters.append(el("button","查询审计","primary"),button("清除审计筛选",async()=>{filters.reset();await searchAudit({});}));
  filters.onsubmit=e=>{e.preventDefault();act(()=>searchAudit({action:eventType.value,actor:who.value.trim(),runId:which.value.trim()}));};
  journal.append(filters,controls,records);
  const render=(data)=>{
    controls.replaceChildren(); records.replaceChildren();
    const previous=button("上一页审计",async()=>{
      const target=cursors[cursors.length-2];
      const page=await fetchPage(target);
      cursors.pop(); render(page);
    });
    const older=button("下一页审计",async()=>{
      const page=await fetchPage(data.nextBefore);
      cursors.push(data.nextBefore); render(page);
    });
    previous.disabled=cursors.length===1; older.disabled=!data.hasMore;
    controls.append(previous,el("span","第 "+cursors.length+" 页"),older);
    if(data.items.length)table(records,["actor","action","subject","note","created_at"],data.items);
    else records.append(el("p","没有符合当前条件的审计记录。","muted"));
  };
  render(audit);
}
const desk=reviewDesk({el,field,input,box,table,button,head,api,act,info,download,labels,viewRun:async id=>show(await api("/runs/"+id)),toLedger:ledger,role:()=>user?.role,cat});
const scenarios=scenariosDesk({el,field,input,box,table,button,head,api,info,labels:resultLabels,download,viewRun:async id=>show(await api("/runs/"+id)),example:{"name":"忽略不足 101 毫秒的片段","settings":{"minDurationMs":101}}});
const routing=routingDesk({el,field,input,box,table,button,head,api,act,info,viewRun:async id=>show(await api("/runs/"+id)),current:()=>user});
$("routing").onclick=()=>act(()=>routing.queue());
$("policy").onclick=()=>act(desk.policy);
$("entry").onclick = () => act(() => entry());
$("ledger").onclick = () =>
  act(() => {
    page = 1;
    return ledger();
  });
$("accounts").onclick = () => act(accounts);
$("security").onclick = () => act(() => {
  head("security","修改我的密码","修改成功后，所有设备上的旧会话均会退出。");
  const panel=box("账号安全"), f=el("form",null,"settings"); panel.append(f);
  const old=field(f,"原密码",input("password")), next=field(f,"新密码",input("password")), confirmValue=field(f,"确认新密码",input("password"));
  old.autocomplete="current-password";next.autocomplete=confirmValue.autocomplete="new-password";
  old.required=next.required=confirmValue.required=true; next.minLength=12;
  f.append(el("button","保存并重新登录","primary"));
  f.onsubmit=e=>{e.preventDefault();act(async()=>{
    if(next.value!==confirmValue.value)throw Error("两次输入的新密码不一致");
    await api("/me/password","POST",{currentPassword:old.value,newPassword:next.value});
    f.reset();off();info("密码已更新，请用新密码重新登录。");
  });};
});
if (token) await act(enter);
