// 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {readBatch} from "../frontend/batch-import.mjs";
import {evidencePage} from "../frontend/inspection.mjs";
import {showMetric,thresholdText,comparableSummary,needsException} from "../frontend/review-desk.mjs";
test("门槛输入保留小数精度，不把空值、指数或非法数量转为零",()=>{
  assert.equal(thresholdText("0.123456789123","ratio"),"0.123456789123");
  assert.equal(thresholdText("2.000","count"),"2");assert.equal(thresholdText("1.00","ratio"),"1");
  for(const [x,u] of [["","count"],["2.01","ms"],["1.000000000001","ratio"],["1e6","count"],["1000000000000000.01","number"],["0.1234567890123","ratio"]])assert.throws(()=>thresholdText(x,u));
});
test("比较口径与例外放行条件明确区分",()=>{
  assert.match(comparableSummary({sameInputs:true}),/均一致/);
  assert.match(comparableSummary({sameInputs:false,sharedCount:1,added:["乙"],removed:[],changed:["甲"],settingsChanged:["x"],reordered:true}),/不代表/);
  assert.equal(needsException("FAIL"),true);assert.equal(needsException("UNKNOWN"),true);
  for(const s of [null,"PASS","LEGACY","DISABLED","UNCONFIGURED"])assert.equal(needsException(s),false);
});
import {canCorrect,submitEnvelope,submissionKey,formatDifference} from "../frontend/corrections.mjs";
import {webcrypto} from "node:crypto";
import {routeActions,routePayload} from "../frontend/routing.mjs";
import {scenarioSettings} from "../frontend/scenarios.mjs";
test("参数试算不把空数值或非有限值当成零，保留文本参数",()=>{
 assert.deepEqual(scenarioSettings({slots:1,mode:"a"},{slots:" 2 ",mode:" exact "}),{slots:2,mode:"exact"});
 for(const x of [""," ","Infinity","0x22","1e999","one"])assert.throws(()=>scenarioSettings({n:1},{n:x}));
 assert.deepEqual(scenarioSettings({yes:false},{yes:"true"}),{yes:true});assert.throws(()=>scenarioSettings({yes:false},{yes:"1"}));
});
test("分派只向符合条件的复核人员显示，不允许自己领取",()=>{
  const a={stage:"READY",actor_id:"s",owner_id:null},r={id:"r",role:"REVIEWER"};
  assert.deepEqual(routeActions(a,r),["CLAIM"]);assert.deepEqual(routeActions({...a,actor_id:"r"},r),[]);
  assert.deepEqual(routeActions({...a,owner_id:"r"},r),["HANDOFF","RELEASE"]);
  assert.deepEqual(routeActions({...a,owner_id:"someone"},r),[]);
  assert.deepEqual(routeActions({...a,owner_id:"someone"},{id:"a",role:"ADMIN"}),["ASSIGN","HANDOFF","RELEASE"]);
  for(const role of ["VIEWER","OPERATOR"])assert.deepEqual(routeActions(a,{id:"s",role}),[]);
  assert.deepEqual(routeActions({...a,stage:"APPROVED"},r),[]);
});
test("交接保留分派版本并校验接收人和原因",()=>{
  assert.deepEqual(routePayload(3,"HANDOFF"," jack "," 白班确认 "),{expectedVersion:3,action:"HANDOFF",target:"jack",note:"白班确认"});
  assert.equal(routePayload(0,"CLAIM","ignored","").target,"");
  for(const args of [[-1,"CLAIM","",""],[0,"RELEASE",""," "],[0,"ASSIGN","","a"],[0,"no","",""]])assert.throws(()=>routePayload(...args));
});
test("修订仅面向原提交人，已办、只读、已有后续时不显示入口",()=>{
  const r={id:"from",actor_id:"A",stage:"REJECTED"},u={id:"A",role:"OPERATOR"};
  assert.equal(canCorrect(r,u),true);assert.equal(canCorrect({...r,stage:"WITHDRAWN"},u),true);
  for(const bad of [{...r,correctedBy:"B"},{...r,stage:"READY"},{...r,stage:"APPROVED"},{...r,actor_id:"C"}])assert.equal(canCorrect(bad,u),false);
  assert.equal(canCorrect(r,{...u,role:"VIEWER"}),false);
});
test("修订携带来源、版本和说明；普通提交保持兼容",()=>{
  const s={id:"A",revision:2},b={title:"测试"};
  assert.deepEqual(submitEnvelope(null,"",b),{path:"/runs",body:b});
  assert.deepEqual(submitEnvelope(s," 修正 ",b),{path:"/runs/A/corrections",body:{version:2,note:"修正",batch:b}});
  assert.throws(()=>submitEnvelope(s,"  ",b));assert.throws(()=>submitEnvelope(s,"a".repeat(1001),b));
  assert.equal(formatDifference("READY"),"READY");assert.equal(formatDifference(0.000001),"0.000001");assert.equal(formatDifference([1,2]),"[1,2]");assert.equal(formatDifference(null),"—");
  assert.match(submissionKey(webcrypto),/^[0-9a-f]{32}$/);assert.notEqual(submissionKey(webcrypto),submissionKey(webcrypto));
});
test("复核列表支持分页、空结果、数组内容与大小写无关的字面搜索",()=>{
  const rows=Array.from({length:23},(_,i)=>({id:i,notes:i===22?["订单","ABC*"]:[],value:null}));
  assert.deepEqual(evidencePage(rows,"",9).items,rows.slice(20));
  assert.deepEqual(evidencePage(rows,"abc*",2).items,[rows[22]]);
  assert.deepEqual(evidencePage(rows,"XYZ",4),{total:0,page:1,pages:1,items:[]});
  assert.equal(rows.length,23);
});
import {waitText,queueTime} from "../frontend/routing.mjs";
test("等待时长和浏览器时区可区分整点、跨日与空值",()=>{assert.equal(waitText(0),"0 分钟");assert.equal(waitText(60),"1 小时");assert.equal(waitText(125),"2 小时 5 分钟");assert.equal(waitText(null),"—");assert.match(queueTime("2026-11-17T17:04:22Z","Asia/Shanghai"),/18.*01:04:22/);assert.equal(queueTime("bad"),"—");});
const cat=JSON.parse(readFileSync(new URL("../frontend/catalog.json",import.meta.url),"utf8"));
const sample={title:"实测导入",settings:cat.settings,rows:cat.rows};
test("本工程样本可无损导入，接受 UTF-8 BOM",()=>assert.deepEqual(readBatch("\uFEFF"+JSON.stringify(sample),cat),sample));
test("未知参数或列不可静默丢弃",()=>{
  assert.throws(()=>readBatch(JSON.stringify({...sample,settings:{...sample.settings,unknown:"x"}}),cat),/参数.*未知字段 unknown/);
  assert.throws(()=>readBatch(JSON.stringify({...sample,rows:[cat.rows[0],{...cat.rows[0],unexpected:1}]}),cat),/第 2 行.*未知字段 unexpected/);
});
test("错误行和类型不可被转换成另一条业务记录",()=>{
  assert.throws(()=>readBatch(JSON.stringify({...sample,rows:[{...cat.rows[0],id:null}]}),cat),/第 1 行 id/);
  assert.throws(()=>readBatch(JSON.stringify({...sample,rows:[{}]}),cat),/第 1 行.*缺少/);
  assert.throws(()=>readBatch(JSON.stringify({...sample,rows:[]}),cat),/1–200/);
  assert.throws(()=>readBatch("{",cat),/无法解析/);
});

test("对比数值可在紧凑和完整精度之间切换，不舍入到零或改变原始类型含义",()=>{
  assert.equal(showMetric(1/3),"0.33333333");assert.equal(showMetric(1/3,true),String(1/3));
  assert.notEqual(showMetric(1e-60),"0");assert.equal(showMetric(null),"—");assert.equal(showMetric(45),"45");assert.equal(showMetric("0.0000"),"0.0000");
});
