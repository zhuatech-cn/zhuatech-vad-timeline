// 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
import {formatDifference,submissionKey} from "./corrections.mjs";
import {evidencePage} from "./inspection.mjs";
export const gateNames={PASS:"达标",FAIL:"未达标",UNKNOWN:"无法判定",UNCONFIGURED:"未配置",DISABLED:"已停用",LEGACY:"历史无快照"};
const ops={GE:"≥",LE:"≤"}, states={ONLY_TARGET:"仅本次有",ONLY_BASELINE:"仅基线有",UNAVAILABLE:"数值缺失",SAME:"相同",CHANGED:"已变化"};
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
export function needsException(status){return status==="FAIL"||status==="UNKNOWN";}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
export function thresholdText(text,unit){
  const value=String(text).trim();
  if(!/^(?:0|[1-9]\d{0,15})(?:\.\d{1,12})?$/.test(value))throw Error("阈值须为非负十进制数，最多 12 位小数");
  const [whole,dec=""]=value.split(".");
  if(BigInt(whole)>1000000000000000n || (BigInt(whole)===1000000000000000n&&/[1-9]/.test(dec)))throw Error("阈值超出允许范围");
  if(unit==="ratio" && (BigInt(whole)>1n || (whole==="1"&&/[1-9]/.test(dec))))throw Error("比例请填写 0 至 1");
  if(["count","ms"].includes(unit) && /[1-9]/.test(dec))throw Error("数量和毫秒须为整数");
  const fraction=dec.replace(/0+$/,"");return whole+(fraction?"."+fraction:"");
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
export function showMetric(value,full=false){
  if(value==null)return "—";
  if(typeof value!=="number" || full || Number.isInteger(value))return formatDifference(value);
  return Number(value.toPrecision(8)).toString();
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
export function comparableSummary(c){
  if(c.sameInputs)return "样本编号、业务字段、参数和顺序均一致。";
  return "共同样本 "+c.sharedCount+"，新增 "+c.added.length+"，移除 "+c.removed.length+"，同编号内容改变 "+c.changed.length+"；"+
    (c.settingsChanged.length?"参数有变更":"参数一致")+"，"+(c.reordered?"共同样本顺序有变更":"共同样本顺序一致")+"。差值仅描述两次快照，不代表模型本身改善或退化。";
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
export function reviewDesk({el,field,input,box,table,button,head,api,act,info,download,labels,viewRun,toLedger,role,cat}){
  const metricLabel=key=>labels[key.split(".").at(-1)]||key;
  const lineLabel=def=>def.label||metricLabel(def.metric);
  const term=(value)=>value==null?"—":formatDifference(value);
  function gatePanel(run){
    const gate=run.gate,panel=box("门槛验收");
    if(!gate){panel.append(el("p","历史批次没有门槛快照；不会使用今天的规则补判。","muted"));return;}
    panel.append(el("p",gateNames[gate.status]+(gate.version?" · 规则 V"+gate.version:"")+" · 提交时快照，不替代独立复核。"));
    if(gate.checks.length)table(panel,["指标","条件","实测值","结论"],gate.checks.map(c=>({"指标":lineLabel(c),"条件":ops[c.operator]+" "+term(c.threshold),"实测值":term(c.actual),"结论":gateNames[c.status]})));
  }
  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  function diagnosticPanel(run) {
    const d=run.result.diagnostics;if(!d)return;
    const panel=box(cat.diagnosticTitle);panel.append(el("p",cat.diagnosticHelp,"muted"));
    const grid=el("div",null,"metrics");panel.append(grid);
    for(const [k,val] of Object.entries(d.metrics)){const cell=el("div");cell.append(el("small",metricLabel(k)),el("strong",term(val)));grid.append(cell);}
    if(d.truncated)panel.append(el("p","统计涵盖全部 "+d.totalRows+" 行；受快照大小上限影响，只保存前 "+d.rows.length+" 行诊断明细。"));
    const fold=el("details");fold.append(el("summary","展开诊断明细（"+d.rows.length+" 条）"));panel.append(fold);
    const toolbar=el("div",null,"bar"),search=field(toolbar,"筛选诊断明细",input("search")),records=el("div"),nav=el("div",null,"bar");fold.append(toolbar,records,nav);
    function render(page){const selected=evidencePage(d.rows,search.value,page);records.replaceChildren();nav.replaceChildren();
      if(selected.items.length)table(records,Object.keys(d.rows[0]),selected.items);else records.append(el("p","当前条件下没有明细。","muted"));
      const prev=button("上一页诊断",()=>render(selected.page-1)),next=button("下一页诊断",()=>render(selected.page+1));prev.disabled=selected.page===1;next.disabled=selected.page===selected.pages;
      nav.append(prev,el("span","第 "+selected.page+" / "+selected.pages+" 页 · "+selected.total+" 条"),next);}
    fold.addEventListener("toggle",()=>{if(fold.open)render(1);});search.oninput=()=>render(1);
  }
  async function comparison(baseline,target){
    const report=await api("/runs/"+encodeURIComponent(target)+"/compare?baseline="+encodeURIComponent(baseline));
    const {baseline:a,target:b,cohort:c}=report;
    head("ledger","批次对比","读取两份已保存结果；不会重新计算或改变审批状态。");
    const top=box("对比范围"),bar=el("div",null,"bar");
    top.append(el("p","基线："+a.title+"（"+a.rowCount+" 行） · 本次："+b.title+"（"+b.rowCount+" 行）"),el("p",comparableSummary(c)),bar);
    bar.append(button("打开基线",()=>viewRun(a.id)),button("打开本次",()=>viewRun(b.id)),button("交换比较方向",()=>comparison(b.id,a.id)),button("导出对比 JSON",()=>download(report,"compare-"+a.id+"-"+b.id+".json")),button("返回台账",toLedger));
    const block=box("指标差值"),changed=input("checkbox"),full=input("checkbox"),switches=el("div",null,"compare-switches"),results=el("div");
    field(switches,"只看有差异或缺值",changed);field(switches,"显示完整数值",full);block.append(switches,results);
    function render(){
      const value=v=>showMetric(v,full.checked);results.replaceChildren();table(results,["指标","基线值","本次值","差值（本次减基线）","对比状态"],report.metrics.filter(v=>!changed.checked||v.state!=="SAME").map(v=>({"指标":metricLabel(v.metric),"基线值":value(v.baseline),"本次值":value(v.target),"差值（本次减基线）":value(v.delta),"对比状态":states[v.state]})));
    }
    changed.onchange=full.onchange=render;render();
    const inventory=box("输入差异索引");let detailCount=0;
    for(const [key,name] of Object.entries({added:"新增行",removed:"移除行",changed:"内容改变行",settingsChanged:"变更参数"})){
      if(!c[key].length)continue;
      const fold=el("details"),summary=el("summary",name+"（"+c[key].length+"）");
      fold.append(summary,el("p",c[key].join("、")));inventory.append(fold);detailCount++;
    }
    if(!detailCount)inventory.append(el("p",c.reordered?"只有输入顺序改变。":"没有编号或字段变更。","muted"));
    const detail=box("逐条结果对照"), holder=el("div");detail.append(holder);
    let section="main",filter="DIFFERENCES",q="";
    async function loadPage(page){
      const path="/runs/"+encodeURIComponent(target)+"/compare/evidence?"+new URLSearchParams({baseline,section,filter,q,page,size:20});
      const data=await api(path);holder.replaceChildren();
      const form=el("form",null,"bar"),group=el("select"),state=el("select"),termInput=input("search",q);
      for(const [k,t] of Object.entries({main:"主结果明细",inspection:"专项复核明细",diagnostics:"业务诊断明细"}))group.add(new Option(t,k));
      const kinds={DIFFERENCES:"全部差异",ALL:"含相同行",ADDED:"新增",REMOVED:"退出",CHANGED:"内容变化",SAME:"相同"};
      for(const [k,t] of Object.entries(kinds))state.add(new Option(t,k));group.value=section;state.value=filter;
      field(form,"对照范围",group);field(form,"差异类型",state);field(form,"按行标识检索",termInput);
      form.append(el("button","检索对照","primary"));holder.append(form);
      form.onsubmit=e=>{e.preventDefault();act(async()=>{section=group.value;filter=state.value;q=termInput.value.trim();await loadPage(1);});};
      group.onchange=state.onchange=()=>form.requestSubmit();
      if(data.availability!=="AVAILABLE"){
        holder.append(el("p",data.availability==="MISSING_SECTION"?"至少一个快照没有这组明细，不能当作空列表对照。":data.availability==="PARTIAL_SECTION"?"至少一个快照只保留了部分诊断明细，本组不作逐条对照。":"历史明细缺少唯一业务标识，未尝试按行号配对。","muted"));return;
      }
      holder.append(el("p","按 "+data.keyFields.map(metricLabel).join(" + ")+" 匹配。全组：新增 "+data.counts.ADDED+"，退出 "+data.counts.REMOVED+"，变化 "+data.counts.CHANGED+"，相同 "+data.counts.SAME+"。"));
      const details=el("div");holder.append(details);
      if(!data.items.length)details.append(el("p","当前条件下没有匹配的明细。","muted"));
      for(const item of data.items){
        const line=el("details"),key=Object.entries(item.key).map(([k,v])=>metricLabel(k)+"="+v).join(" · ");
        line.append(el("summary",kinds[item.state]+" · "+key+(item.fields.length?" · "+item.fields.map(metricLabel).join("、"):"")));
        const fields=[...new Set([...Object.keys(item.baseline||{}),...Object.keys(item.target||{})])];
        table(line,["字段","基线值","本次值"],fields.map(f=>({"字段":metricLabel(f),"基线值":term(item.baseline?.[f]),"本次值":term(item.target?.[f])})));
        details.append(line);
      }
      const pag=el("div",null,"bar"),prev=button("上一页对照",()=>loadPage(page-1)),next=button("下一页对照",()=>loadPage(page+1));
      prev.disabled=page===1;next.disabled=page*data.size>=data.total;
      pag.append(prev,el("span","第 "+page+" 页 · 共 "+data.total+" 条"),next);holder.append(pag);
    }
    holder.append(button("加载逐条对照",()=>loadPage(1)));
  }
  async function policy(){
    const [data,history]=await Promise.all([api("/policy"),api("/policy/history?limit=10")]);
    const {current,catalog}=data;const defs=new Map(catalog.map(m=>[m.key,m]));
    head("policy","验收规则","管理员发布本项目的验收门槛。每次发布单独编号，仅影响此后新提交的批次。");
    const box1=box("当前生效版本");box1.append(el("p",current.version?("V"+current.version+" · "+(current.rules.length?"启用":"停用")+" · "+current.author+" · "+current.reason):"尚未配置：系统不会默认启用示例阈值。"));
    const showRules=(p,rules)=>table(p,["指标","判断条件"],rules.map(r=>({"指标":defs.get(r.metric)?.label||r.metric,"判断条件":ops[r.operator]+" "+r.threshold})));
    if(current.rules.length)showRules(box1,current.rules);
    if(role()==="ADMIN"){
      const editor=box("发布新版本"),form=el("form"),list=el("div"),buttons=el("div",null,"bar");
      editor.append(form);form.append(list,buttons);
      const rows=[],preview=el("div");preview.setAttribute("aria-live","polite");
      let draftRevision=0;const clearPreview=()=>{draftRevision++;preview.replaceChildren();};
      form.addEventListener("input",clearPreview);form.addEventListener("change",clearPreview);
      function append(rule){
        if(rows.length===8)throw Error("最多同时设置 8 条门槛");clearPreview();
        const line=el("div",null,"bar"),met=el("select"),op=el("select"),val=input("text");
        for(const m of catalog)met.add(new Option(m.label,m.key));
        op.add(new Option("至少（≥）","GE"));op.add(new Option("至多（≤）","LE"));
        met.value=rule?.metric||catalog[0].key;
        const fillSample=()=>{const d=defs.get(met.value);op.value=d.sampleOperator;val.value=d.example;};
        fillSample();if(rule){op.value=rule.operator;val.value=rule.threshold;}
        val.inputMode="decimal";val.required=true;val.maxLength=30;met.onchange=fillSample;
        field(line,"指标",met);field(line,"方向",op);field(line,"阈值",val);
        const row={met,op,val};rows.push(row);
        line.append(button("移除门槛",()=>{rows.splice(rows.indexOf(row),1);line.remove();clearPreview();}));
        list.append(line);
      }
      for(const r of current.rules)append(r);
      buttons.append(button("增加门槛",()=>append()));
      const draftRules=()=>rows.map(({met,op,val})=>({metric:met.value,operator:op.value,threshold:thresholdText(val.value,defs.get(met.value).unit)}));
      const sample=el("select");for(const n of [1,5,10])sample.add(new Option("最近 "+n+" 个批次",n));sample.value="5";field(buttons,"试算样本范围",sample);
      buttons.append(button("试算未发布规则",async()=>{
        const captured=draftRevision;const result=await api("/policy/preview","POST",{expectedVersion:current.version,rules:draftRules(),limit:Number(sample.value)});
        if(captured!==draftRevision)return;
        preview.replaceChildren(el("h3","试算结果（未发布）"),el("p","从最近的 "+result.rows.length+" 个批次读取历史指标。达标 "+result.counts.PASS+"，未达标 "+result.counts.FAIL+"，无法判定 "+result.counts.UNKNOWN+"，停用 "+result.counts.DISABLED+"。结果不改变原批次。"));
        if(result.rows.length)table(preview,["批次","原门槛结论","草案试算"],result.rows.map(r=>({"批次":r.title,"原门槛结论":gateNames[r.storedStatus],"草案试算":gateNames[r.projectedStatus]})));
        else preview.append(el("p","本企业还没有已保存的批次。","muted"));
      }));form.append(preview);
      const reason=field(form,"发布原因",input("text"));reason.required=true;reason.maxLength=500;
      const submit=el("button","发布规则","primary");form.append(submit);
      form.append(el("p","移除所有门槛再发布会停用规则；旧批次的结论不会改变。比例用 0 至 1 表示。","muted"));
      let last="",key="";
      form.onsubmit=e=>{e.preventDefault();act(async()=>{
        const rules=draftRules();
        if(!rules.length && !current.rules.length)throw Error("请至少添加一条门槛");
        if(!rules.length && !confirm("停用门槛，仅影响后续新批次。确定发布？"))return;
        const body={expectedVersion:current.version,reason:reason.value.trim(),rules};
        if(!body.reason)throw Error("请填写变更原因");const hash=JSON.stringify(body);if(!key||hash!==last){last=hash;key=submissionKey();}
        const saved=await api("/policy","POST",body,key);key="";await policy();info("已发布规则 V"+saved.version);
      });};
    }
    const hist=box("版本记录"),nav=el("div",null,"bar"),records=el("div"),cursors=[0];hist.append(nav,records);
    function render(p){
      nav.replaceChildren();records.replaceChildren();
      const prev=button("上一页规则",async()=>{const pos=cursors.at(-2);const page=await api("/policy/history?limit=10&before="+pos);cursors.pop();render(page);});
      const next=button("下一页规则",async()=>{const page=await api("/policy/history?limit=10&before="+p.nextBefore);cursors.push(p.nextBefore);render(page);});
      prev.disabled=cursors.length===1;next.disabled=!p.hasMore;nav.append(prev,el("span","第 "+cursors.length+" 页"),next);
      if(!p.items.length)records.append(el("p","还没有发布记录。","muted"));
      for(const policy of p.items){
        const d=el("details");d.append(el("summary","V"+policy.version+" · "+policy.author+" · "+policy.reason),el("p",policy.publishedAt));
        if(policy.rules.length)showRules(d,policy.rules);else d.append(el("p","本版停用验收门槛。"));
        records.append(d);
      }
    }render(history);
  }
  return {gatePanel,diagnosticPanel,comparison,policy};
}
