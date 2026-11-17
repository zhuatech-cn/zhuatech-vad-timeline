package cn.zhuatech.vadtimeline;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;

/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
@SuppressWarnings("unchecked")
class AlgorithmTest {
  final ObjectMapper mapper = new ObjectMapper();
  Map<String, Object> cfg;
  List<Map<String, Object>> data;

  @BeforeEach
  void load() throws Exception {
    var cat = mapper.readValue(getClass().getResourceAsStream("/static/catalog.json"), Map.class);
    cfg = (Map) cat.get("settings");
    data = (List) cat.get("rows");
  }

  Map<?, ?> metrics(List<Map<String, Object>> rows) {
    return (Map<?, ?>) Algorithm.compute(cfg, rows).get("metrics");
  }

  @Test
  void knownAnswer() throws Exception {
    var r = Algorithm.compute(cfg, data);
    var m = mapper.valueToTree(r.get("metrics"));
    var expected = mapper.readTree("{\"hitMs\":75,\"missMs\":25,\"falseMs\":25}");
    for (var e : expected.properties()) {
      var v = m.get(e.getKey());
      assertNotNull(v);
      if (e.getValue().isNumber()) assertEquals(e.getValue().asDouble(), v.asDouble(), 1e-9);
      else assertEquals(e.getValue(), v);
    }
  }

  @Test
  void malformedDomainInput() {
    var x = new HashMap<>(data.getFirst());
    x.putAll(mapper.readValue("{\"endMs\":0}", Map.class));
    var copy = new ArrayList<>(data);
    copy.set(0, x);
    assertThrows(Model.Failure.class, () -> metrics(copy));
  }

  @Test
  void duplicateIds() {
    assertThrows(Model.Failure.class, () -> metrics(List.of(data.getFirst(), data.getFirst())));
  }

  @Test
  void domainBoundary() {
    var r = Algorithm.compute(Map.of("minDurationMs", 1000), data);
    assertEquals(3, ((Map<?, ?>) r.get("metrics")).get("ignored"));
  }
}
