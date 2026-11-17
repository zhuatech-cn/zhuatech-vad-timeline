#!/usr/bin/env python3
"""Render a standalone, offline evaluation report from a verified batch dossier and a scenario preview.
上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
"""
import argparse,decimal,hashlib,html,io,json,os,re,sys,tempfile,zipfile
from pathlib import Path
import importlib.util
_spec=importlib.util.spec_from_file_location("evidence_validator",Path(__file__).with_name("verify-evidence.py"))
handoff=importlib.util.module_from_spec(_spec);_spec.loader.exec_module(handoff)

Decimal=decimal.Decimal
LIMIT=2*1024*1024
RELEASES={"1.4.0"}
def require(ok,text):
    """上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
    if not ok:raise ValueError(text)
def num(token):
    """上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
    require(len(token)<=128,"numeric representation too long")
    n=Decimal(token);require(n.is_finite() and abs(n.adjusted())<=512,"unsupported numeric scale");return n
def obj(raw):
    """上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
    def unique(pairs):
        """上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
        row={}
        for k,v in pairs:
            require(k not in row,"duplicate JSON property");row[k]=v
        return row
    o=json.loads(raw.decode("utf-8"),object_pairs_hook=unique,parse_float=num,parse_int=num,parse_constant=lambda _:(_ for _ in ()).throw(ValueError("non-finite number")))
    require(isinstance(o,dict),"expected JSON object");return o
def exact(x,y):
    """上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
    if type(x) is not type(y):return False
    if isinstance(x,dict):return x.keys()==y.keys() and all(exact(x[k],y[k]) for k in x)
    if isinstance(x,list):return len(x)==len(y) and all(exact(a,b) for a,b in zip(x,y))
    return x==y
def read(path,n):
    """上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
    p=Path(path);require(p.is_file(),"file is missing")
    with p.open("rb") as f:v=f.read(n+1)
    require(0<len(v)<=n,"file outside supported size");return v
def checksum(raw):return hashlib.sha256(raw).hexdigest()
def pinned(raw,pin):
    """上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
    require(isinstance(pin,str) and re.fullmatch("[a-fA-F0-9]{64}",pin) and checksum(raw)==pin.lower(),"digest does not match independent record")
def archived_metrics(r):
    """上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
    flat=dict(r["metrics"])
    for section in ("inspection","diagnostics"):
        if isinstance(r.get(section),dict) and isinstance(r[section].get("metrics"),dict):
            flat.update({section+"."+k:v for k,v in r[section]["metrics"].items()})
    return flat
def scalar(v):return v is None or type(v) in (bool,str,Decimal)
def validate(evidence,scenario,epin,spin):
    """上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
    # Verify all ZIP checksums first. Re-read bounded bytes and pin to the same digest before inspecting.
    proof=handoff.verify(evidence,expected=epin)
    raw=read(evidence,handoff.LIMIT+65536);pinned(raw,proof["sha256"])
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        source={k:z.read(k) for k in ("input.json","result.json")}
        inp=obj(source["input.json"]);result=obj(source["result.json"])
    data=read(scenario,LIMIT)
    if spin:pinned(data,spin)
    p=obj(data)
    require(p.get("previewOnly") is True and p.get("algorithmVersion") in RELEASES,"not a supported preview")
    require(p.get("format")=="zhuatech-scenario/2" and p.get("project")==proof["project"],"scenario is not bound to the same project; re-export on 1.4.1+")
    require(p.get("sourceDigests")=={k:checksum(v) for k,v in source.items()},"exact input/result bytes differ from the evidence; export a matching pair")
    require(p.get("runId")==proof["runId"] and type(p.get("sourceRevision")) is Decimal and p["sourceRevision"]==proof["revision"],"batch or revision mismatch")
    require(type(p.get("sampleCount")) is Decimal and p["sampleCount"]==len(inp["rows"]) and exact(p.get("referenceSettings"),inp["settings"]),"source settings or sample size mismatch")
    flat=archived_metrics(result)
    alts=p.get("alternatives");require(isinstance(alts,list) and 1<=len(alts)<=3,"expected 1-3 alternatives")
    baseline=None;names=set();prior=[]
    for a in alts:
        require(isinstance(a,dict) and isinstance(a.get("name"),str) and a["name"]==a["name"].strip() and 1<=len(a["name"])<=60 and a["name"] not in names,"invalid alternative name")
        names.add(a["name"]);cfg=a.get("settings")
        require(isinstance(cfg,dict) and cfg.keys()==inp["settings"].keys() and not exact(cfg,inp["settings"]) and not any(exact(cfg,old) for old in prior),"invalid or repeated alternative settings")
        require(all(type(v) is type(inp["settings"][k]) and scalar(v) for k,v in cfg.items()),"invalid setting type");prior.append(cfg)
        records=a.get("metrics");require(isinstance(records,list) and 1<=len(records)<=500,"invalid metric count")
        refs={};changes=0
        for x in records:
            require(isinstance(x,dict) and set(x)=={"metric","archived","reference","alternative","state","delta"},"invalid metric fields")
            k=x["metric"];require(isinstance(k,str) and 1<=len(k)<=160 and k not in refs,"unknown or repeated metric")
            require(all(scalar(x[a]) for a in ("reference","alternative","archived","delta")),"invalid metric form")
            require(exact(flat.get(k),x["archived"]),"archived metric does not match dossier")
            l,r=x["reference"],x["alternative"];refs[k]=l
            state="UNAVAILABLE" if l is None or r is None else "SAME" if exact(l,r) else "CHANGED"
            require(x["state"]==state,"inconsistent change state")
            changes+=int(state=="CHANGED")
            with decimal.localcontext() as ctx:
                ctx.prec=1300
                diff=r-l if type(l) is Decimal and type(r) is Decimal else None
            require(exact(diff,x["delta"]),"inconsistent numeric delta")
        require(set(flat)<=set(refs) and type(a.get("changed")) is Decimal and a["changed"]==changes,"missing metric or inconsistent summary")
        if baseline is None:baseline=refs
        else:require(exact(refs,baseline),"alternatives do not share the same recomputed reference")
    return proof,p,checksum(data)
def render(proof,p,ssha,anchored,show_settings=False,include_same=False,lang="zh"):
    """Escape all customer fields and create a stand-alone report with no executable content.
    上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
    """
    require(lang in ("zh","en"),"invalid report language")
    def t(zh,en):return en if lang=="en" else zh
    def e(v):
        if v is None:v="—"
        elif type(v) is bool:v=t("是","Yes") if v else t("否","No")
        return html.escape(str(v),quote=True)
    css="html{font:15px/1.55 system-ui,sans-serif;color:#142d42;background:#f3f6f8}body{max-width:1080px;margin:auto;padding:24px}h1{font-size:25px}h2{font-size:19px}article{background:white;border:1px solid #cbd6df;padding:18px;margin:16px 0}.scroll{overflow:auto}table{border-collapse:collapse;width:100%}th,td{text-align:left;padding:7px 9px;border-bottom:1px solid #d5dfe6}th{background:#edf2f5}code{overflow-wrap:anywhere}small{color:#4c6475}@media print{html{background:white}body{padding:0}thead{display:table-header-group}tr{break-inside:avoid}.scroll{overflow:visible}article{border:0;padding:0;margin:12px 0}}"
    name=t("参数试算评审记录","Fixed-input scenario review")
    title=e(name);state={"CHANGED":t("变化","Changed"),"SAME":t("不变","Same"),"UNAVAILABLE":t("不可比较","Unavailable")}
    out=['<!doctype html><html lang="'+t('zh-CN','en')+'"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src \'none\'; style-src \'unsafe-inline\'; base-uri \'none\'; form-action \'none\'"><title>'+title+'</title><style>'+css+'</style></head><body><header><small>'+t("知华科技 / 业务参数试算","ZhuaTech / Evaluation workbench")+'</small><h1>'+title+'</h1><p>'+t("本页是固定样本试算，不是正式验收单或审批决定。","This is a fixed-input simulation, not a signed acceptance or approval.")+'</p></header>',
      '<p>'+t("项目","Project")+' <b>'+e(proof["project"])+'</b> · '+t("样本条数","Rows")+' '+e(p["sampleCount"])+' · '+t("算法","Algorithm")+' '+e(p["algorithmVersion"])+'</p>',
      '<p>'+t("原单","Source")+' <code>'+e(proof["runId"])+'</code> · '+t("版本","Revision")+' '+e(proof["revision"])+' · '+t("归档状态","Archived state")+' '+e(proof["stage"])+'</p>']
    for i,alt in enumerate(p["alternatives"],1):
        out.append('<article><h2>'+t("方案","Scenario")+' '+str(i)+': '+e(alt["name"])+'</h2><p>'+t("可比指标中有变化的项数：","Comparable metrics that changed: ")+e(alt["changed"])+'</p>')
        if show_settings:
            out.append('<h3>'+t("参数设置（含业务字段）","Settings (may include business identifiers)")+'</h3><dl>')
            for k,v in sorted(alt["settings"].items()):out.append('<dt>'+e(k)+'</dt><dd>'+e(p["referenceSettings"][k])+' → '+e(v)+'</dd>')
            out.append('</dl>')
        rows=[x for x in alt["metrics"] if include_same or x["state"]!="SAME"]
        if rows:
            cols=t(["指标","归档","原参重算","方案","差值","结果"],["Metric","Archived","Reference","Scenario","Delta","Status"])
            out.append('<div class="scroll"><table><thead><tr>'+''.join('<th scope="col">'+v+'</th>' for v in cols)+'</tr></thead><tbody>')
            for x in rows:out.append('<tr>'+''.join('<td>'+e(v)+'</td>' for v in (x["metric"],x["archived"],x["reference"],x["alternative"],x["delta"],state[x["state"]]))+'</tr>')
            out.append('</tbody></table></div>')
        else:out.append('<p>'+t('无变化；使用 --include-same 可列出全部指标。','No changes. Use --include-same to include unchanged metrics.')+'</p>')
        out.append('</article>')
    note=t("已核对独立摘要","Matched independently supplied digest") if anchored else t("仅记录本次文件摘要，未提供独立摘要","Local file digest only; no independent scenario digest supplied")
    out.extend(['<footer><h2>'+t("文件核对信息","File verification")+'</h2><p>'+t("证据摘要已匹配独立记录。试算文件：","The evidence matched its independently supplied digest. Scenario: ")+note+'.</p>',
      '<p>Evidence SHA-256: <code>'+e(proof["sha256"])+'</code></p><p>Scenario SHA-256: <code>'+e(ssha)+'</code></p>',
      '<small>'+t("仅核对文件、来源、数值关系和版本。未重新执行模型，不证明分析方法或审批决定正确。上海如静知华信息科技有限公司 · https://www.zhuatech.cn/","File, source, version and arithmetic checks only. The model is not re-executed; this report does not certify business decisions. Shanghai Rujing Zhihua Information Technology Co., Ltd. · https://www.zhuatech.cn/en.html")+'</small></footer></body></html>'])
    return '\n'.join(out)+'\n'
def publish(path,content):
    """上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
    dst=Path(path)
    require(dst.parent.is_dir() and not dst.exists() and not dst.is_symlink(),"select a new output path")
    with tempfile.TemporaryDirectory(prefix='.scenario-report-',dir=dst.parent) as tmp:
        partial=Path(tmp)/'report.html'
        with partial.open('xb') as f:
            os.chmod(partial,0o600);f.write(content.encode('utf8'));f.flush();os.fsync(f.fileno())
        os.link(partial,dst)
def main(argv=None):
    """上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--evidence',required=True);ap.add_argument('--evidence-sha256',required=True)
    ap.add_argument('--scenario',required=True);ap.add_argument('--scenario-sha256');ap.add_argument('--out',required=True)
    ap.add_argument('--show-settings',action='store_true');ap.add_argument('--include-same',action='store_true')
    ap.add_argument('--lang',choices=('zh','en'),default='zh')
    args=ap.parse_args(argv)
    try:
        info,p,s=validate(args.evidence,args.scenario,args.evidence_sha256,args.scenario_sha256)
        publish(args.out,render(info,p,s,bool(args.scenario_sha256),args.show_settings,args.include_same,args.lang))
    except (ValueError,TypeError,KeyError,OSError,OverflowError,EOFError,RecursionError,decimal.DecimalException,zipfile.BadZipFile,handoff.struct.error,handoff.zlib.error):
        print("未生成：请核对来源、版本、文件摘要及新输出路径。",file=sys.stderr);return 1
    print("已生成本地离线评审页；未上传。");return 0
if __name__=="__main__":sys.exit(main())
