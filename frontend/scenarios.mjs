// 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
import {formatDifference} from "./corrections.mjs";
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
export function scenarioSettings(template, values){
  const result={};
  for(const [k,original] of Object.entries(template)){
    const v=String(values[k]??"").trim();
    if(!v)throw Error("参数 "+k+" 不可为空");
    if(typeof original==="number"){
      if(!/^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?$/.test(v)||!Number.isFinite(Number(v)))throw Error("参数 "+k+" 须填写有限数值");
      result[k]=Number(v);
    }else if(typeof original==="boolean"){
      if(!["true","false"].includes(v))throw Error("参数 "+k+" 须为 true 或 false");result[k]=v==="true";
    }else result[k]=v;
  }
  return result;
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
export function scenariosDesk({el,field,input,box,table,button,head,api,info,labels,download,viewRun,example}){
  return function open(run){
    head("ledger","业务参数试算","在同一份样本上比较不同参数；不会新建批次、修改历史证据或替代验收结论。");
    const editor=box("试算方案"), toolbar=el("div",null,"bar"), forms=el("div"), output=box("试算结果");
    editor.append(el("p",run.title+" · 原单版本 "+run.revision+" · "+run.input.rows.length+" 条固定明细"),forms,toolbar);
    const baseline=run.input.settings, slots=[];let outputVersion=0;
    function dirty(){outputVersion++;output.replaceChildren(el("h2","试算结果"),el("p","参数尚未试算或已修改，请重新运行。","muted"));}
    function addSlot(){
      if(slots.length>=3)return;
      const panel=el("fieldset"),legend=el("legend","参数方案"),line=el("div",null,"bar");panel.append(legend,line);forms.append(panel);
      const name=field(line,"方案名称",input("text","方案 "+(slots.length+1)));name.maxLength=60;const entries={};
      for(const [k,v] of Object.entries(baseline)){entries[k]=field(line,labels[k]||k,input("text",String(v)));entries[k].maxLength=120;}
      const slot={name,entries,panel};slots.push(slot);
      panel.addEventListener("input",dirty);
      line.append(button("载入参数示例",()=>{name.value=example.name;for(const [k,val] of Object.entries(example.settings))entries[k].value=String(val);dirty();}));
      line.append(button("移除此方案",()=>{if(slots.length===1){info("请保留至少一个方案。");return;}slots.splice(slots.indexOf(slot),1);panel.remove();dirty();}));
      dirty();
    }
    function render(res){
      output.replaceChildren(el("h2","试算结果"),el("p","算法版本 "+res.algorithmVersion+" · 基准与方案均按本次算法计算。历史归档值仅供参照；此页不改变原单。"));
      const details=el("details"),summary=el("summary","核对数据来源");details.append(summary,el("p",res.project+" · "+res.format));for(const [name,sha] of Object.entries(res.sourceDigests||{})){const p=el("p",name+" SHA-256：");p.append(el("code",sha));details.append(p);}output.append(details);
      const form=el("div",null,"bar"),filter=field(form,"只看变动",input("checkbox")),scope=el("select");filter.checked=true;
      for(const alt of res.alternatives)scope.add(new Option(alt.name,alt.name));field(form,"查看方案",scope);output.append(form);
      const grid=el("div");output.append(grid);
      function display(){
        const alt=res.alternatives.find(x=>x.name===scope.value);grid.replaceChildren();
        grid.append(el("p",alt.changed+" 项可比指标发生变化。","muted"));
        const rows=alt.metrics.filter(x=>!filter.checked||x.state!=="SAME").map(x=>({"指标":labels[x.metric.split(".").at(-1)]||x.metric,"归档值":formatDifference(x.archived),"原参重算":formatDifference(x.reference),"方案值":formatDifference(x.alternative),"差值":formatDifference(x.delta),"结果":({SAME:"无变化",CHANGED:"有变化",UNAVAILABLE:"不可比较"})[x.state]}));
        if(rows.length)table(grid,["指标","归档值","原参重算","方案值","差值","结果"],rows);else grid.append(el("p","可比指标均无变化，取消筛选可查看全部。","muted"));
      }
      filter.onchange=scope.onchange=display;display();
      output.append(button("下载试算结果",()=>download(res,"scenario-preview-"+res.runId+".json")));
    }
    toolbar.append(button("增加方案",()=>{if(slots.length>=3){info("每次最多比较三个方案。");return;}addSlot();}),button("运行参数试算",async()=>{
      const alternatives=slots.map(({name,entries})=>({name:name.value.trim(),settings:scenarioSettings(baseline,Object.fromEntries(Object.entries(entries).map(([k,node])=>[k,node.value])))}));
      const current=outputVersion,res=await api("/runs/"+encodeURIComponent(run.id)+"/scenarios/preview","POST",{expectedRevision:run.revision,alternatives});
      if(current===outputVersion && output.isConnected)render(res);
    },true),button("返回原批次",()=>viewRun(run.id)));
    addSlot();
  };
}
