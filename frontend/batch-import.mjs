// 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function fields(value, expected, where) {
  if (!value || Array.isArray(value) || typeof value !== "object") throw Error(where + "：需要对象");
  for (const k of Object.keys(expected)) if (!Object.hasOwn(value, k)) throw Error(where + "：缺少字段 " + k);
  for (const k of Object.keys(value)) if (!Object.hasOwn(expected, k)) throw Error(where + "：未知字段 " + k);
}

/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function typed(value, sample, where) {
  if (Array.isArray(sample)) {
    if (!Array.isArray(value)) throw Error(where + "：需要数组");
    if (sample.length) value.forEach((v, i) => typed(v, sample[0], where + "[" + (i + 1) + "]"));
  } else if (typeof value !== typeof sample || (typeof sample === "number" && !Number.isFinite(value))) {
    throw Error(where + "：类型不符，预期 " + ({number:"有限数值",string:"文本",boolean:"布尔值"}[typeof sample] || typeof sample));
  }
}

/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
export function readBatch(source, catalog) {
  let batch;
  try { batch = JSON.parse(source.replace(/^\uFEFF/, "")); }
  catch { throw Error("无法解析 JSON，请检查 UTF-8 编码、引号和逗号"); }
  fields(batch, {title:0,settings:0,rows:0}, "批次");
  if (typeof batch.title !== "string" || !batch.title.trim() || batch.title.length > 100) throw Error("批次标题须为 1–100 字");
  fields(batch.settings, catalog.settings, "参数");
  for (const [k,v] of Object.entries(catalog.settings)) typed(batch.settings[k],v,"参数 " + k);
  if (!Array.isArray(batch.rows) || batch.rows.length < 1 || batch.rows.length > 200) throw Error("每批需要 1–200 行");
  const spec = catalog.rows[0];
  batch.rows.forEach((r, i) => {
    const where = "第 " + (i + 1) + " 行";
    fields(r,spec,where);
    for (const [k,v] of Object.entries(spec)) typed(r[k],v,where + " " + k);
  });
  return batch;
}
