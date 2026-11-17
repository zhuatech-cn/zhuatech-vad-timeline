/* 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
package cn.zhuatech.vadtimeline;

import static cn.zhuatech.vadtimeline.Model.*;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

/**
 * Fixed-input, non-persistent scenario execution; sources and official decisions never change.
 * 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
@RestController
@RequestMapping("/api")
public final class ScenarioLab {
  final JdbcTemplate db;
  final ObjectMapper json;
  final Auth auth;

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public ScenarioLab(JdbcTemplate db, ObjectMapper json, Auth auth) {
    this.db = db;
    this.json = json;
    this.auth = auth;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public record Option(String name, Map<String, Object> settings) {}

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public record Preview(Integer expectedRevision, List<Option> alternatives) {}

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @PostMapping("/runs/{id}/scenarios/preview")
  public Object preview(
      @RequestHeader(value = "Authorization", required = false) String h,
      @PathVariable String id,
      @RequestBody Preview p) {
    var user = auth.current(h);
    Auth.role(user, "OPERATOR");
    Checks.must(
        p != null
            && p.expectedRevision() != null
            && p.expectedRevision() > 0
            && p.alternatives() != null
            && !p.alternatives().isEmpty()
            && p.alternatives().size() <= 3,
        "提供当前批次版本及 1–3 个方案");
    // One SELECT fixes the source version, input and archived result together. Release the DB
    // connection before computing.
    var rows =
        db.queryForList(
            "SELECT id,revision,input_json,result_json FROM work_run WHERE tenant=? AND id=?",
            user.tenant(),
            id);
    if (rows.isEmpty()) throw new Failure(404, "批次不存在");
    var stored = rows.getFirst();
    int revision = ((Number) stored.get("revision")).intValue();
    require(p.expectedRevision() == revision, "批次版本已变化，请先刷新详情");
    var source = json.readValue((String) stored.get("input_json"), Api.Batch.class);
    var options = new ArrayList<Option>();
    var names = new HashSet<String>();
    for (var candidate : p.alternatives()) {
      Checks.must(
          candidate != null
              && candidate.name() != null
              && !candidate.name().isBlank()
              && candidate.name().strip().length() <= 60
              && names.add(candidate.name().strip()),
          "方案名称不能为空、重复或超过 60 字");
      Checks.must(
          candidate.settings() != null
              && candidate.settings().keySet().equals(source.settings().keySet()),
          "每个方案须提供与原批次相同的参数字段");
      Checks.must(!CorrectionDiff.equal(candidate.settings(), source.settings()), "方案参数不能与原参数完全相同");
      for (var prev : options)
        Checks.must(!CorrectionDiff.equal(prev.settings(), candidate.settings()), "不同方案的参数不能完全相同");
      options.add(new Option(candidate.name().strip(), candidate.settings()));
    }
    var original = BatchComparison.flatten(Algorithm.compute(source.settings(), source.rows()));
    var inputDoc = json.readValue((String) stored.get("input_json"), Map.class);
    var resultDoc = json.readValue((String) stored.get("result_json"), Map.class);
    var archive = BatchComparison.flatten(resultDoc);
    var output = new ArrayList<Map<String, Object>>();
    for (var option : options) {
      Map<String, Object> outcome;
      try {
        outcome = BatchComparison.flatten(Algorithm.compute(option.settings(), source.rows()));
      } catch (Failure err) {
        throw new Failure(err.status, "方案「" + option.name() + "」：" + err.getMessage());
      }
      var keys = new TreeSet<>(original.keySet());
      keys.addAll(outcome.keySet());
      keys.addAll(archive.keySet());
      var items = new ArrayList<Map<String, Object>>();
      int changed = 0;
      for (String k : keys) {
        Object a = original.get(k), b = outcome.get(k);
        var v = new LinkedHashMap<String, Object>();
        String state =
            a == null || b == null
                ? "UNAVAILABLE"
                : CorrectionDiff.equal(a, b) ? "SAME" : "CHANGED";
        if (state.equals("CHANGED")) changed++;
        v.put("metric", k);
        v.put("archived", archive.get(k));
        v.put("reference", a);
        v.put("alternative", b);
        v.put("state", state);
        v.put(
            "delta",
            a instanceof Number x && b instanceof Number y
                ? new BigDecimal(y.toString()).subtract(new BigDecimal(x.toString()))
                : null);
        items.add(v);
      }
      output.add(
          Map.of(
              "name",
              option.name(),
              "settings",
              option.settings(),
              "changed",
              changed,
              "metrics",
              items));
    }
    var response = new LinkedHashMap<String, Object>();
    response.put("format", "zhuatech-scenario/2");
    response.put("project", EvidenceArchive.PROJECT);
    response.put(
        "sourceDigests",
        Map.of(
            "input.json",
            EvidenceArchive.sourceDigest(inputDoc, json),
            "result.json",
            EvidenceArchive.sourceDigest(resultDoc, json)));
    response.put("previewOnly", true);
    response.put("algorithmVersion", "1.4.0");
    response.put("producerVersion", "1.4.1");
    response.put("runId", id);
    response.put("sourceRevision", revision);
    response.put("sampleCount", source.rows().size());
    response.put("referenceSettings", source.settings());
    response.put("alternatives", output);
    return response;
  }
}
