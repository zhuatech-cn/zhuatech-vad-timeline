// 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
import {submissionKey} from "./corrections.mjs";
export const names={CLAIM:"领取",ASSIGN:"管理员派单",HANDOFF:"交接",RELEASE:"释放"};
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
export function waitText(m){if(!Number.isSafeInteger(m)||m<0)return "—";if(m<60)return m+" 分钟";const h=Math.floor(m/60),r=m%60;return h+" 小时"+(r?" "+r+" 分钟":"");}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
export function queueTime(value,zone){const d=new Date(value);if(typeof value!=="string"||!value||!Number.isFinite(d.getTime()))return "—";return new Intl.DateTimeFormat("zh-CN",{year:"numeric",month:"2-digit",day:"2-digit",hour:"2-digit",minute:"2-digit",second:"2-digit",hourCycle:"h23",...(zone?{timeZone:zone}:{})}).format(d);}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
export function routeActions(a,user){
  if(!a||!user||a.stage!=="READY"||!["ADMIN","REVIEWER"].includes(user.role))return [];
  const own=a.owner_id===user.id,admin=user.role==="ADMIN",actions=[];
  if(!a.owner_id && a.actor_id!==user.id)actions.push("CLAIM");
  if(admin)actions.push("ASSIGN");
  if(a.owner_id && (own||admin))actions.push("HANDOFF","RELEASE");
  return actions;
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
export function routePayload(version,action,target,note){
  const recipient=String(target||"").trim(),why=String(note||"").trim(),needsTarget=["ASSIGN","HANDOFF"].includes(action);
  if(!Number.isInteger(version)||version<0||!Object.hasOwn(names,action))throw Error("请先重新读取分派状态");
  if(needsTarget&&(!recipient||recipient.length>80))throw Error("请填写接收人账号（最多 80 字）");
  if(why.length>500 || (action!=="CLAIM"&&!why))throw Error("请填写办理原因（最多 500 字）");
  return {expectedVersion:version,action,target:needsTarget?recipient:"",note:why};
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
export function routingDesk({el,field,input,box,table,button,head,api,act,info,viewRun,current}){
  let savedScope="OPEN",savedQ="",savedAge=0,position=0,trail=[""];
  async function queue(scope=savedScope,q=savedQ,age=savedAge,index=0,cursors=[""]){
    const params=new URLSearchParams({scope,q,minWaitMinutes:String(age)});
    if(cursors[index])params.set("cursor",cursors[index]);
    const report=await api("/review-queue?"+params);savedScope=scope;savedQ=q;savedAge=age;position=index;trail=[...cursors];
    if(index===0)trail[0]=report.firstCursor;
    head("routing","复核队列","按提交时间从早到晚办理。等待时间从提交起算；领取不代表复核通过。");
    const panel=box(),form=el("form",null,"bar");panel.append(form);
    const scopes=el("select");for(const [key,label] of Object.entries({OPEN:"公共待领",MINE:"我负责的",TEAM:"全部待复核",STALE:"负责人已停用或转岗"}))scopes.add(new Option(label,key));
    scopes.value=scope;field(form,"队列范围",scopes);
    const waits=el("select");for(const [k,l] of [[0,"不限"],[30,"至少 30 分钟"],[120,"至少 2 小时"],[480,"至少 8 小时"],[1440,"至少 1 天"]])waits.add(new Option(l,String(k)));
    waits.value=String(age);field(form,"等待时间",waits);
    const keyword=field(form,"查找批次",input("search",q));keyword.maxLength=100;
    form.append(el("button","筛选队列","primary"));form.onsubmit=e=>{e.preventDefault();act(()=>queue(scopes.value,keyword.value.trim(),Number(waits.value)));};
    scopes.onchange=waits.onchange=()=>form.requestSubmit();
    panel.append(el("p","提交截止于 "+queueTime(report.asOf)+"（"+Intl.DateTimeFormat().resolvedOptions().timeZone+"）；负责人实时更新。重新筛选可看到新提交。","muted"));
    if(report.items.length)table(panel,["批次标题","当前负责人","办理提示","已等待","提交时间"],report.items.map(v=>({...v,"批次标题":v.title,"当前负责人":v.owner_name||"未分派","办理提示":v.needs_handoff?"需要交接":(v.actor_id===current().id?"自己提交，不可自审":v.owner_id?"已领取或派单":"可领取"),"已等待":waitText(v.waitMinutes),"提交时间":queueTime(v.created_at)})),v=>button("办理分派",()=>detail(v.id)));
    else panel.append(el("p","当前筛选没有待办。可以调整等待时间、关键词或队列范围。","muted"));
    const nav=el("div",null,"bar");panel.append(nav);
    const prev=button("上一页待办",()=>queue(scope,q,age,position-1,trail)),next=button("下一页待办",()=>queue(scope,q,age,position+1,[...trail.slice(0,position+1),report.nextCursor]));
    prev.disabled=index===0;next.disabled=!report.nextCursor;nav.append(prev,el("span","当前符合 "+report.total+" 条 · 第 "+(index+1)+" 页"),next);
  }
  async function detail(id){
    const path="/runs/"+encodeURIComponent(id)+"/routing",data=await api(path),a=data.assignment;
    head("routing",a.title,"复核分派 · 当前负责人："+(a.owner_name||"未分派")+" · 分派版本 "+a.route_version);
    const control=box("办理分派");
    const nav=el("div",null,"bar");control.append(nav);nav.append(button("返回复核队列",()=>queue()),button("查看批次结果",()=>viewRun(id)),button("刷新分派状态",()=>detail(id)));
    if(a.needs_handoff)control.append(el("p","原负责人已停用或不再具备复核角色，请管理员重新派单。","muted"));
    const actions=routeActions(a,current());
    if(actions.length){
      const form=el("form",null,"bar");control.append(form);const kind=el("select");
      for(const action of actions)kind.add(new Option(names[action],action));field(form,"办理动作",kind);
      const rec=field(form,"接收人账号",input("text"));rec.maxLength=80;
      const why=field(form,"办理原因",input("text"));why.maxLength=500;
      function fields(){const target=["ASSIGN","HANDOFF"].includes(kind.value);rec.disabled=!target;rec.required=target;why.required=kind.value!=="CLAIM";}
      kind.onchange=fields;fields();form.append(el("button","确认办理","primary"));let key="",last="";
      form.onsubmit=e=>{e.preventDefault();act(async()=>{
        const body=routePayload(a.route_version,kind.value,rec.value,why.value),fingerprint=JSON.stringify(body);
        if(fingerprint!==last){key=submissionKey();last=fingerprint;}
        await api(path,"POST",body,key);await detail(id);info("分派已记录。");
      });};
    }else control.append(el("p",a.stage==="READY"?"当前没有可执行的分派动作。":"该批次已结束办理，分派信息仅供查阅。","muted"));
    const history=box("交接记录"),body=el("div"),pager=el("div",null,"bar");history.append(body,pager);
    let cutoff=a.route_version;
    function render(v,append){
      if(!append)body.replaceChildren();
      if(v.history.length)table(body,["版本","动作","从","到","办理人","原因","时间"],v.history.map(x=>({"版本":x.route_version,"动作":names[x.action],"从":x.before_owner||"公共队列","到":x.after_owner||"公共队列","办理人":x.actor,"原因":x.note,"时间":queueTime(x.created_at)})));
      else if(!append)body.append(el("p","尚无分派记录。","muted"));
      pager.replaceChildren();
      if(v.nextBefore && v.nextBefore<=cutoff){cutoff=v.nextBefore;pager.append(button("加载更早交接",async()=>render(await api(path+"?before="+cutoff),true)));}
    }
    render(data,false);
  }
  return {queue,detail,reset:()=>{savedScope="OPEN";savedQ="";savedAge=0;position=0;trail=[""];}};
}
