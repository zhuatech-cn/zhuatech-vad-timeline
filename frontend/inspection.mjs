// 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
/** 对已保存的复核证据作字面筛选，不修改或重算记录。商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
export function evidencePage(rows, needle, requestedPage, size=20) {
  const query=needle.trim().toLowerCase();
  const found=query ? rows.filter(row=>Object.values(row).some(v=>v!=null && (Array.isArray(v)?v.join(" "):String(v)).toLowerCase().includes(query))) : rows;
  const pages=Math.max(1,Math.ceil(found.length/size)), page=Math.min(pages,Math.max(1,requestedPage));
  return {total:found.length,pages,page,items:found.slice((page-1)*size,page*size)};
}
