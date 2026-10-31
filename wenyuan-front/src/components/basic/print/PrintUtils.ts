import defaultStyle from './printStyle.css?raw';
import printJS from 'print-js';
import { template, isString, get, set, isArray, cloneDeep, forEach } from 'lodash-es';
import { isVNode } from 'vue';
import { fileUrl } from '@/utils';

/**
 * 根据表格列定义生成数据数组
 * */
export function tableColumnData(columns: any[], data: any): any[] {
  let printData = data?.map((row: any, index: number) => {
    let nRow: any = cloneDeep(row);
    columns.map((column: any, index: number) => {
      let value = get(row, column.key);
      if (column.columnType == 'title') {
        // 直接使用对应的字段
      } else if (column.columnType == 'index') {
        value = `${index + 1}`;
      } else if (column.columnType == 'image') {
        value = fileUrl(value);
      } else if (column.render) {
        //有自定义函数的特殊处理，其中可能会产生图标等类型，故只处理字符串
        let text = getAllTextFromRender(column.render(row));
        if (isString(text)) {
          value = text;
        }
      }
      if (isArray(value)) {
        value = value.join('');
      }
      set(nRow, column.key, value || '');
    });
    return nRow;
  });
  return printData || [];
}

/**
 * 获取全部的文本内容
 * */
function getAllTextFromRender(node: any) {
  let text: any[] = [];

  if (isArray(node)) {
    node.forEach((n) => {
      text.push(getAllTextFromRender(n));
    });
    return text.join('\r\n');
  }

  if (isVNode(node)) {
    if (typeof node.children === 'string') {
      // 处理文本节点
      text.push(node.children);
    } else if (Array.isArray(node.children)) {
      // 处理数组子节点
      node.children.forEach((child) => {
        if (typeof child === 'string') {
          text.push(child);
        } else if (isVNode(child)) {
          text.push(getAllTextFromRender(child));
        }
      });
    } else if (typeof node.children === 'object' && node.children !== null) {
      // 处理对象类型的子节点（如插槽）
      Object.values(node.children).forEach((child) => {
        if (typeof child === 'function') {
          // 如果是渲染函数，调用它并递归处理结果
          const renderResult = child();
          text.push(getAllTextFromRender(renderResult));
        } else if (isVNode(child)) {
          text.push(getAllTextFromRender(child));
        }
      });
    } else {
      text.push(node);
    }
  } else {
    return node;
  }

  return text.join('\r\n');
}

/**
 * 根据模版编译内容
 * */
export function compiledTemplate(htmlTemplate: string | undefined | null, data: any[] | undefined): string {
  if (!htmlTemplate || !data) return '';
  let list: string[] = [];

  let compiled = template(htmlTemplate || '<h1 class="text-center">请配置打印模版后，再尝试打印。</h1>', {
    imports: { forEach, get, set },
  });

  data?.forEach((item: any) => {
    list.push(compiled({ data: item }));
  });
  return list.join("<div class='page-break'></div>");
}

/** 按字形簇（用户感知的一个字符）切分，家庭/旗帜等组合 emoji 不会被截成半个；无 Intl.Segmenter 时退回按码点切分。 */
function printGraphemes(text: string): string[] {
  if (typeof Intl !== 'undefined' && typeof Intl.Segmenter === 'function') {
    return Array.from(new Intl.Segmenter(undefined, { granularity: 'grapheme' }).segment(text), (part) => part.segment);
  }
  return Array.from(text);
}

/**
 * 打印标题整理（只整理不转义）：去掉控制字符、合并空白、按字形簇截断到 120 个字（无 Intl.Segmenter 时按码点）；
 * 不替换 / : * ? " < > | 等字符（如「2026/10/22」保持原样），文件名清洗只在导出/下载时做。
 * HTML 转义只在 printDocumentTitle（htmlPrint）里做一次。
 * */
export function safePrintTitle(title: unknown, fallback = '打印') {
  const normalized = String(title ?? '')
    .replace(/[\u0000-\u001f\u007f]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
  // 按字形簇截断 120 字，避免代理对、组合 emoji（家庭、旗帜等）被截成半个
  const text = printGraphemes(normalized).slice(0, 120).join('').trim();
  return text || fallback;
}

/**
 * print-js 会把 documentTitle 原样拼进 <title>，这里统一整理并做一次 HTML 转义，防止业务数据注入标签。
 * 调用方传入未转义的原始标题即可。
 * */
export function printDocumentTitle(title: unknown) {
  return safePrintTitle(title)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/**
 * 点开开始打印
 * */
export function htmlPrint(html: string, title: string, style: string | undefined | null = '') {
  if (html) {
    printJS({
      printable: html,
      scanStyles: false,
      documentTitle: printDocumentTitle(title),
      style: defaultStyle + (style || ''),
      type: 'raw-html',
      showModal: true,
      modalMessage: '准备打印',
    });
  }
}
