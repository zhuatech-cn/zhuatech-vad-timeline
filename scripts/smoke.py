#!/usr/bin/env python3
"""上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
Launch only a disposable instance with private data and verify the packaged delivery.
商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
"""
import json, math, os, secrets, socket, subprocess, tempfile, time, sys
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

ROOT = Path(__file__).resolve().parents[1]
def smoke():
    jar = ROOT / "backend/target/app.jar"
    if not jar.is_file():
        raise SystemExit("Build first: ./mvnw -f backend/pom.xml package (Windows: mvnw.cmd -f backend/pom.xml package)")
    with tempfile.TemporaryDirectory(prefix="zh-workspace-") as tmp:
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            port = sock.getsockname()[1]
        base = "http://127.0.0.1:" + str(port)
        password = secrets.token_urlsafe(24)
        # Inherit only the runtime, never Spring/JDBC overrides or production JVM options.
        runtime={k:v for k,v in os.environ.items() if k.upper() in {
            "JAVA_HOME","PATH","HOME","USERPROFILE","SYSTEMROOT","WINDIR","COMSPEC","PATHEXT",
            "TMPDIR","TEMP","TMP","LANG","LC_ALL","LC_CTYPE","TZ"}}
        env = dict(runtime, SPRING_CONFIG_LOCATION="classpath:/application.properties",
                   PORT=str(port), BIND_ADDRESS="127.0.0.1", ADMIN_PASSWORD=password,
                   DB_USER="sa", DB_PASSWORD="", DB_URL="jdbc:h2:file:"+str(Path(tmp)/"db")+";MODE=MySQL;DATABASE_TO_LOWER=TRUE")
        def call(path, method="GET", data=None, token=None, key=None):
            headers = {"Content-Type":"application/json"}
            if token: headers["Authorization"]="Bearer "+token
            if key: headers["Idempotency-Key"]=key
            raw = json.dumps(data,ensure_ascii=False).encode() if data is not None else None
            with urlopen(Request(base+path,data=raw,method=method,headers=headers),timeout=10) as res:
                v=res.read()
                return json.loads(v) if v else None
        def boot():
            command = ["java","-jar",str(jar)] if os.name=="nt" else ["bash",str(ROOT/"scripts/start.sh"),"--no-build"]
            process=subprocess.Popen(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
            try:
                for _ in range(200):
                    if process.poll() is not None: raise RuntimeError("startup failed")
                    try:
                        call("/api/health"); return process
                    except (URLError,TimeoutError):time.sleep(.15)
                raise RuntimeError("startup timeout")
            except BaseException:
                stop(process); raise
        def stop(p):
            if p is not None and p.poll() is None:
                p.terminate()
                try:p.wait(timeout=12)
                except subprocess.TimeoutExpired:p.kill();p.wait()
        with (Path(tmp)/"server.log").open("w+") as log:
            p=None
            try:
                p=boot()
                subprocess.run([sys.executable,str(ROOT/'scripts/check-service.py')],cwd=ROOT,env=env,check=True,timeout=10)
                for path in ("/","/app.js","/style.css"):
                    with urlopen(base+path,timeout=5) as res:assert res.status==200 and res.read()
                cat=call("/catalog.json")
                batch={"title":"离线启动自检","settings":cat["settings"],"rows":cat["rows"]}
                auth=lambda:call("/api/login","POST",{"username":"admin","password":password})["token"]
                token=auth();one=call("/api/runs","POST",batch,token,"startup-test")
                assert one["result"]["rows"] and call("/api/runs",token=token)["total"]==1
                assert call("/api/runs","POST",batch,token,"startup-test")["id"]==one["id"]
                stop(p);env.pop("ADMIN_PASSWORD");p=boot()
                again=call("/api/runs/"+one["id"],token=auth());assert one["result"]==again["result"]
                closed=call("/api/runs/"+one["id"]+"/withdraw","POST",{"version":again["revision"],"note":"自检完成，保留记录"},auth())
                assert closed["stage"]=="WITHDRAWN" and closed["result"]==one["result"]
                assert call("/api/runs?state=WITHDRAWN&scope=mine",token=auth())["total"]==1
                fix=json.loads((ROOT/"examples/correction.json").read_text(encoding="utf8"))
                updated=json.loads(json.dumps(batch));updated["rows"][fix["row"]][fix["field"]]=fix["after"]
                successor=call("/api/runs/"+one["id"]+"/corrections","POST",{"version":closed["revision"],"note":fix["reason"],"batch":updated},auth(),"correction-smoke")
                delta=successor["correction"]["changes"];assert delta==[{"kind":"EDIT","id":fix["id"],"field":fix["field"],"before":fix["before"],"after":fix["after"]}]
                stop(p);p=boot()
                original=call("/api/runs/"+one["id"],token=auth())
                assert original["correctedBy"]==successor["id"] and original["result"]==one["result"]
                assert original["revision"]==closed["revision"]+1 and successor["correction"]["source_revision"]==closed["revision"]
                assert call("/api/runs/"+successor["id"],token=auth())["correction"]==successor["correction"]
                fixture=json.loads((ROOT/"examples/acceptance.json").read_text(encoding="utf8"))
                policy=json.loads((ROOT/"examples/acceptance-policy.json").read_text(encoding="utf8"))
                preview=call("/api/policy/preview","POST",{"expectedVersion":0,"rules":policy["rules"],"limit":5},auth())
                assert preview["previewOnly"] and len(preview["rows"])==2 and sum(preview["counts"].values())==2
                assert call("/api/policy",token=auth())["current"]["version"]==0
                assert call("/api/policy","POST",policy,auth(),"smoke-rule-v1")["version"]==1
                saved=call("/api/runs","POST",fixture,auth(),"domain-acceptance")
                result=saved["result"];gate=saved["gate"];assert gate["version"]==1 and gate["status"] in ("PASS","FAIL","UNKNOWN")
                worked=json.loads((ROOT/"examples/scenario.json").read_text(encoding="utf8"))
                option={"name":worked["name"],"settings":worked["settings"]}
                scenario=call("/api/runs/"+saved["id"]+"/scenarios/preview","POST",{"expectedRevision":saved["revision"],"alternatives":[option]},auth())
                point=worked["checkpoint"]
                assert scenario["previewOnly"] and math.isclose(next(x["alternative"] for x in scenario["alternatives"][0]["metrics"] if x["metric"]==point["metric"]),point["expected"],rel_tol=0,abs_tol=point["absoluteTolerance"])
                call("/api/users","POST",{"username":"shiftreview","role":"REVIEWER","password":password},auth())
                receipt=call("/api/runs/"+saved["id"]+"/routing","POST",{"expectedVersion":0,"action":"ASSIGN","note":"交班","target":"shiftreview"},auth(),"smoke-assign-review")
                assert receipt["route_version"]==1 and receipt["after_owner"]=="shiftreview"
                assert call("/api/runs/"+one["id"],token=auth())["gate"]["status"]=="UNCONFIGURED"
                comp=call("/api/runs/"+saved["id"]+"/compare?baseline="+one["id"],token=auth())
                assert comp["cohort"]["sameInputs"] and len(comp["metrics"])>0
                for group in ("main","inspection","diagnostics"):
                    ev=call("/api/runs/"+saved["id"]+"/compare/evidence?baseline="+one["id"]+"&section="+group+"&filter=ALL&size=2",token=auth())
                    assert ev["availability"]=="AVAILABLE" and sum(ev["counts"].values())==ev["counts"]["SAME"]
                target=Path(tmp)/"snapshot.zip"
                req=Request(base+"/api/runs/"+saved["id"]+"/export",data=json.dumps({"version":saved["revision"]}).encode(),
                    headers={"Content-Type":"application/json","Authorization":"Bearer "+auth()},method="POST")
                with urlopen(req,timeout=10) as proof:
                    assert proof.headers.get_content_type()=="application/zip"
                    expected_hash=proof.headers["X-Evidence-SHA256"];target.write_bytes(proof.read())
                check_cmd=[sys.executable,str(ROOT/"scripts/verify-evidence.py"),str(target),"--run",saved["id"],"--sha256",expected_hash]
                checked=json.loads(subprocess.check_output(check_cmd,cwd=tmp,text=True,timeout=12))
                assert checked["sha256"]==expected_hash and checked["stage"]==saved["stage"]
                golden=json.loads((ROOT/"examples/diagnostics-expected.json").read_text(encoding="utf8"))
                assert all(math.isclose(result["diagnostics"]["metrics"][k],v,abs_tol=golden["tolerance"],rel_tol=0) for k,v in golden["metrics"].items()),result["diagnostics"]
                stop(p);p=boot()
                assert call("/api/runs/"+saved["id"],token=auth())["gate"]==gate
                assert call("/api/policy",token=auth())["current"]["version"]==1
                state=call("/api/runs/"+saved["id"]+"/routing",token=auth())
                assert state["assignment"]["owner_name"]=="shiftreview" and state["history"][0]["action"]=="ASSIGN"
                req.add_header("Authorization","Bearer "+auth())
                with urlopen(req,timeout=10) as again_zip:
                    assert again_zip.headers["X-Evidence-SHA256"]==expected_hash and again_zip.read()==target.read_bytes()
                audit=call("/api/audit/page?action=EXPORT_EVIDENCE&actor=admin&runId="+saved["id"],token=auth())
                assert len(audit["items"])==2 and all(expected_hash in e["note"] for e in audit["items"])
                expected=json.loads((ROOT/"examples/expected.json").read_text(encoding="utf8"))["metrics"]
                assert all(k in result["metrics"] and result["metrics"][k]==v for k,v in expected.items()),result["metrics"]
                evidence=json.loads((ROOT/"examples/expected.json").read_text(encoding="utf8"))["row"]
                assert any(all(k in row and row[k]==v for k,v in evidence.items()) for row in result["rows"]),result["rows"]
                check=json.loads((ROOT/"examples/inspection-expected.json").read_text(encoding="utf8"))
                report=result["inspection"]
                assert all(k in report["metrics"] and report["metrics"][k]==v for k,v in check["metrics"].items()),report
                assert any(all(k in item and item[k]==v for k,v in check["row"].items()) for item in report["rows"]),report
                print("PASS: packaged startup, gate preview/publication/persistence, keyed evidence, independent diagnostics and offline archive validation")
            except BaseException:
                log.flush();log.seek(0);print(log.read()[-3500:],file=sys.stderr);raise
            finally:stop(p)
if __name__=="__main__":smoke()
