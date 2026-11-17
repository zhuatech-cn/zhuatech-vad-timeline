"""Golden acceptance for the offline scenario reviewer (no customer data). 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。"""
import copy,contextlib,io,json,os,tempfile,unittest,uuid,zipfile
from html.parser import HTMLParser
from pathlib import Path
import render_scenario as tool
class Elements(HTMLParser):
    def __init__(self):super().__init__();self.tags=[];self.attrs=[];self.content=[]
    def handle_starttag(self,tag,attrs):self.tags.append(tag);self.attrs.extend(attrs)
    def handle_data(self,value):self.content.append(value)
class ReportTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
        self.id=str(uuid.uuid4());self.ev=self.root/"evidence.zip";self.sc=self.root/"preview.json";self.out=self.root/"report.html"
        self.input={"title":"Private customer material","settings":{"slots":1},"rows":[{"id":"private-sample","text":"private raw sample"}]}
        self.result={"rows":[],"metrics":{"wait":2,"stable":True,"none":None}}
        self.review={"id":self.id,"revision":1,"stage":"READY","history":[{"note":"private reviewer context"}]}
        self.makezip()
        self.preview=dict(previewOnly=True,algorithmVersion="1.4.0",runId=self.id,sourceRevision=1,sampleCount=1,referenceSettings={"slots":1},
            alternatives=[dict(name="研发 <甲 & 乙>",settings={"slots":2},changed=1,metrics=[
            dict(metric="wait",archived=2,reference=2,alternative=0.125,state="CHANGED",delta=-1.875),
            dict(metric="stable",archived=True,reference=True,alternative=True,state="SAME",delta=None),
            dict(metric="none",archived=None,reference=None,alternative=0,state="UNAVAILABLE",delta=None)])])
        self.preview.update(format="zhuatech-scenario/2",project="zhuatech-vad-timeline",sourceDigests=self.ds.copy());self.save(self.preview)
    def makezip(self):
        data={k:json.dumps(v).encode() for k,v in [("input.json",self.input),("result.json",self.result),("review.json",self.review)]}
        manifest=dict(format="zhuatech-evidence/1",project="zhuatech-vad-timeline",runId=self.id,revision=1,stage="READY",
              entries=[dict(name=k,bytes=len(v),sha256=tool.checksum(v)) for k,v in data.items()])
        with zipfile.ZipFile(self.ev,"w",zipfile.ZIP_DEFLATED) as z:
            for k,v in data.items():z.writestr(k,v)
            z.writestr("manifest.json",json.dumps(manifest))
        self.epin=tool.checksum(self.ev.read_bytes())
        self.ds={k:tool.checksum(data[k]) for k in ("input.json","result.json")}
    def save(self,value):
        self.sc.write_text(json.dumps(value,ensure_ascii=False));self.spin=tool.checksum(self.sc.read_bytes())
    def call(self,*extra):
        with contextlib.redirect_stdout(io.StringIO()),contextlib.redirect_stderr(io.StringIO()):
            return tool.main(["--evidence",str(self.ev),"--evidence-sha256",self.epin,"--scenario",str(self.sc),"--out",str(self.out),*extra])
    def test_both_digest_pins_and_default_redaction(self):
        self.assertEqual(self.call("--scenario-sha256",self.spin),0);page=self.out.read_text();p=Elements();p.feed(page)
        self.assertIn("研发 <甲 & 乙>","".join(p.content));self.assertIn("-1.875",page)
        for text in ("private raw sample","private reviewer context","Private customer material","private-sample","slots"):self.assertNotIn(text,page)
        self.assertNotIn("script",p.tags);self.assertNotIn("img",p.tags)
        self.assertFalse(any(k in ("src","href") for k,v in p.attrs));self.assertNotIn("stable",page)
    def test_english_edition_and_explicit_settings(self):
        self.assertEqual(self.call("--lang","en","--show-settings","--include-same"),0);html=self.out.read_text()
        for text in ('lang="en"',"Archived","slots","stable","no independent scenario digest supplied"):self.assertIn(text,html)
    def test_different_record_or_revision_or_row_count_fails(self):
        for k,v in (("runId",str(uuid.uuid4())),("sourceRevision",2),("sourceRevision",True),("sampleCount",0),("referenceSettings",{"slots":2})):
            with self.subTest(k=k):
                p=dict(self.preview);p[k]=v;self.save(p);self.assertEqual(self.call(),1);self.assertFalse(self.out.exists())
    def test_unsupported_preview_and_digest_refusal(self):
        self.assertEqual(self.call("--scenario-sha256","0"*64),1)
        for k,v in (("previewOnly",False),("algorithmVersion","unrecognized")):
            p=dict(self.preview);p[k]=v;self.save(p);self.assertEqual(self.call(),1)
        self.epin="0"*64;self.assertEqual(self.call(),1)
    def test_no_arithmetic_or_archived_value_rewrite(self):
        for field,val in (("archived",3),("delta",1.875),("state","SAME"),("alternative",False)):
            p=copy.deepcopy(self.preview);p["alternatives"][0]["metrics"][0][field]=val;self.save(p);self.assertEqual(self.call(),1)
    def test_no_duplicate_metric_or_improper_count(self):
        p=copy.deepcopy(self.preview);p["alternatives"][0]["metrics"].append(p["alternatives"][0]["metrics"][0]);self.save(p);self.assertEqual(self.call(),1)
        p=copy.deepcopy(self.preview);p["alternatives"][0]["changed"]=2;self.save(p);self.assertEqual(self.call(),1)
    def test_all_alternatives_use_same_reference(self):
        p=copy.deepcopy(self.preview);a=copy.deepcopy(p["alternatives"][0]);a.update(name="alternate",settings={"slots":3});a["metrics"][0].update(reference=3,delta=-2.875);p["alternatives"].append(a);self.save(p);self.assertEqual(self.call(),1)
    def test_no_writing_over_another_file_or_symlink(self):
        self.out.write_text("prior output");self.assertEqual(self.call(),1);self.assertEqual(self.out.read_text(),"prior output")
        self.out.unlink();self.out.symlink_to(self.root/"missing");self.assertEqual(self.call(),1);self.assertFalse((self.root/"missing").exists())
    def test_bounded_inputs_and_no_partial_output(self):
        self.sc.write_bytes(b" "*(tool.LIMIT+1));self.assertEqual(self.call(),1);self.assertFalse(self.out.exists())
    def test_exact_decimal_difference_including_small_p_values(self):
        self.result["metrics"]["tiny"]=1e-50;self.makezip()
        p=copy.deepcopy(self.preview);p["sourceDigests"]=self.ds.copy();a=p["alternatives"][0];a["metrics"].append(dict(metric="tiny",archived=1e-50,reference=1e-50,alternative=0,state="CHANGED",delta=-1e-50));a["changed"]+=1;self.save(p)
        self.assertEqual(self.call(),0);self.assertIn("-1E-50",self.out.read_text())
    def test_wrong_setting_type_or_same_configuration_refused(self):
        for v in (True,1,"two"):
            p=copy.deepcopy(self.preview);p["alternatives"][0]["settings"]["slots"]=v;self.save(p);self.assertEqual(self.call(),1)
    def test_no_manifest_filename_reads(self):
        self.sc.write_text('{"previewOnly":true,"previewOnly":false}');self.assertEqual(self.call(),1)
    def test_different_input_same_record_count_and_metrics_refused(self):
        self.input["rows"][0]["text"]="independent input copy";self.makezip()
        self.assertEqual(self.call(),1);self.assertFalse(self.out.exists())
    def test_nonmetric_output_change_refused(self):
        self.result["rows"]=[{"note":"another historical result"}];self.makezip()
        self.assertEqual(self.call(),1);self.assertFalse(self.out.exists())
    def test_project_and_format_bound_to_corresponding_file(self):
        for k,v in (("project","zhuatech-unrelated"),("format","zhuatech-scenario/1")):
            self.save(dict(self.preview,**{k:v}));self.assertEqual(self.call(),1)
    def test_legacy_preview_refused_instead_of_inferring_provenance(self):
        p=dict(self.preview);p.pop("sourceDigests");self.save(p);self.assertEqual(self.call(),1)
if __name__=="__main__":unittest.main()
