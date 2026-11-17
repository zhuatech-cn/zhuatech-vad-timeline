// 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
export function canCorrect(run,user) {
  return Boolean(user && ["ADMIN","REVIEWER","OPERATOR"].includes(user.role) && run.actor_id===user.id &&
    ["REJECTED","WITHDRAWN"].includes(run.stage) && !run.correctedBy);
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
export function submitEnvelope(source,note,batch) {
  if(!source)return {path:"/runs",body:batch};
  const text=note.trim();
  if(!text || text.length>1000)throw Error("请填写修订说明（1–1000 字）");
  return {path:"/runs/"+encodeURIComponent(source.id)+"/corrections",body:{version:source.revision,note:text,batch}};
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
export function formatDifference(value) {
  // Preserve input precision and strings verbatim; a null side means no previous/next value.
  if(value===null || value===undefined)return "—";
  return typeof value==="object" ? JSON.stringify(value) : String(value);
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
export function submissionKey(cryptoProvider=globalThis.crypto) {
  return Array.from(cryptoProvider.getRandomValues(new Uint8Array(16)),v=>v.toString(16).padStart(2,"0")).join("");
}
