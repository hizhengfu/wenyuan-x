/** 数字超过一万转换为万
 * @param { Number } value 数值
 * @param { Number } decimal 保留几位小数
 * @returns { String }
 */
export function friendlyNum(value: number, decimal: number = 0) {
  const k = 10000;
  const sizes = ['', '万', '亿', '万亿'];
  let i = undefined;
  let str = '';
  if (value < k) {
    str = value + '';
  } else {
    i = Math.floor(Math.log(value) / Math.log(k));
    str = (value / Math.pow(k, i)).toFixed(decimal) + sizes[i];
  }
  return str;
}
