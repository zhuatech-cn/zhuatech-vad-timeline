package cn.zhuatech.vadtimeline;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;

/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
@SuppressWarnings("unchecked")
class InspectionTest {
  final ObjectMapper json = new ObjectMapper();
  Map<String, Object> cfg;
  List<Map<String, Object>> data;

  @BeforeEach
  void load() throws Exception {
    var fixture = json.readValue(getClass().getResourceAsStream("/static/catalog.json"), Map.class);
    cfg = (Map) fixture.get("settings");
    data = (List) fixture.get("rows");
  }

  Map<String, Object> view(Map<String, Object> c, List<Map<String, Object>> r) {
    return (Map) Algorithm.compute(c, r).get("inspection");
  }

  Map<String, Object> summary(List<Map<String, Object>> r) {
    return (Map) view(cfg, r).get("metrics");
  }

  List<Map<String, Object>> detail(List<Map<String, Object>> r) {
    return (List) view(cfg, r).get("rows");
  }

  boolean matches(Map<?, ?> v, String fixture) {
    var actual = json.valueToTree(v);
    var expected = json.readTree(fixture);
    return expected.properties().stream()
        .allMatch(
            e -> {
              var a = actual.get(e.getKey());
              if (a == null) return false;
              return a.isNumber() && e.getValue().isNumber()
                  ? Math.abs(a.asDouble() - e.getValue().asDouble()) < 1e-9
                  : e.getValue().equals(a);
            });
  }

  @Test
  void exampleSummary() {
    assertTrue(matches(summary(data), "{\"errorSegments\":2,\"longestMs\":25}"));
  }

  @Test
  void exampleEvidence() {
    assertTrue(
        detail(data).stream()
            .anyMatch(v -> matches(v, "{\"errorType\":\"MISS\",\"beginMs\":0,\"endMs\":25}")));
  }

  @Test
  void operationalBoundary() {
    var x = new HashMap<>(data.getFirst());
    x.put("beginMs", 100);
    x.put("endMs", 110);
    x.put("id", "ext");
    assertEquals(1, summary(List.of(data.getFirst(), x)).get("errorSegments"));
    assertEquals(110L, summary(List.of(data.getFirst(), x)).get("longestMs"));
  }
}
