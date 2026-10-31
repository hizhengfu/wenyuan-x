const camelizeRE = /-(\w)/g;

const cacheStringFunction = (fn: Function) => {
  const cache = Object.create(null);
  return (str: string): string => {
    const hit = cache[str];
    return hit || (cache[str] = fn(str));
  };
};

export const camelize = cacheStringFunction((str: string) => {
  return str.replace(camelizeRE, (_, c) => (c ? c.toUpperCase() : ''));
});

/**
 * 转换富文本为纯字符串
 * */
export function strip_html(html: string) {
  const tmp = document.createElement('div');
  tmp.innerHTML = html;
  let r = tmp.textContent || tmp.innerText;
  tmp.remove();
  r = r.replace(/(?:(\r\n\t)+|\r+|\n+｜\s+)/g, '');
  return r;
}

/**
 * 转换为16进制
 * */
export function hex(value: string, padding: number = 2) {
  //16进制字符串无需转换
  if (/[a-fA-F-_]/.test(value)) {
    return '';
  }
  let hex = '';
  //数字模式
  if (/^\d+$/.test(value)) {
    hex = Number(value).toString(16);
  } else {
    if (value) {
      hex = value.replace(/[^\da-fA-F]/g, '');
    }
  }
  while (hex.length < padding) {
    hex = '0' + hex;
  }

  if (!/^0x/.test(value)) {
    hex = '0x' + hex.toUpperCase();
  }
  return hex;
}

/**
 * 字符宽度，估算法
 * */
export function estimateTextWidth(text: string, fontSize: number = 16) {
  let width = 0;
  for (let i = 0; i < text.length; i++) {
    const char = text.charCodeAt(i);
    if (char >= 0 && char <= 128) {
      width += fontSize * 0.6;
    } else {
      width += fontSize;
    }
  }
  return width;
}
