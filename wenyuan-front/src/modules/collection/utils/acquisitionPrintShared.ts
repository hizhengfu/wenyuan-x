import { formatLocalDateValue } from '@/utils';
import { categoryApi } from '../api/category';
import { printDictLabel, type PrintDictCache } from './printCollectionList';

export const PRINT_EMPTY_TEXT = '—';

const dimensionLabels: Record<string, string> = {
  height: '通高',
  mouth: '口径',
  bottom: '底径',
  belly: '腹径',
  length: '长度',
  width: '宽度',
  diameter: '直径',
};

export function escapePrintHtml(value: unknown) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

export function printPlainText(value: unknown) {
  const text = value === undefined || value === null ? '' : String(value).trim();
  return text || PRINT_EMPTY_TEXT;
}

export function printCellHtml(value: unknown) {
  return escapePrintHtml(printPlainText(value));
}

export function printDateText(value: unknown) {
  if (value === undefined || value === null || value === '') return PRINT_EMPTY_TEXT;
  const text = formatLocalDateValue(value);
  return text === '--' ? printPlainText(value) : text;
}

export function printBooleanText(value: unknown, yes = '是', no = '否') {
  if (value === true || value === 'true' || value === 'YES') return yes;
  if (value === false || value === 'false' || value === 'NO') return no;
  return PRINT_EMPTY_TEXT;
}

export function printDictText(dicts: PrintDictCache, type: string, value: unknown) {
  return printPlainText(printDictLabel(dicts, type, value));
}

export function acquisitionDimensionText(dimensions?: Record<string, any> | null) {
  return printPlainText(
    Object.entries(dimensions ?? {})
      .filter(([key, value]) => dimensionLabels[key] && value !== undefined && value !== null && String(value).trim())
      .map(([key, value]) => `${dimensionLabels[key]}：${String(value).trim()}`)
      .join('；'),
  );
}

export function acquisitionQuantityText(quantity: unknown, unit: unknown) {
  const count = quantity === undefined || quantity === null ? '' : String(quantity).trim();
  if (!count) return PRINT_EMPTY_TEXT;
  return `${count}${String(unit ?? '').trim()}`;
}

export function acquisitionCompletenessText(dicts: PrintDictCache, completeness: unknown, completeStatus: unknown) {
  const level = printDictLabel(dicts, 'col_completeness', completeness);
  const status = String(completeStatus ?? '').trim();
  if (level && status) return `${level}（${status}）`;
  return printPlainText(level || status);
}

/** 持有人一栏随征集方式变化的称谓。 */
export function acquisitionHolderLabel(source?: string | null) {
  if (source === 'PURCHASE') return '卖方';
  if (source === 'DONATE') return '捐赠人';
  if (source === 'EXCHANGE') return '交换方';
  if (['ALLOCATE', 'TRANSFER', 'HANDOVER'].includes(source || '')) return '移交方';
  return '来源人/持有人';
}

export async function loadCategoryNames() {
  try {
    const resp: any = await categoryApi.list();
    const list: any[] = resp?.data?.list ?? resp?.data ?? [];
    return Object.fromEntries(list.map((item) => [String(item.id), String(item.name ?? '')])) as Record<string, string>;
  } catch {
    return {} as Record<string, string>;
  }
}

/** 分类名取不到（接口失败或分类已删）时显示占位，不把内部 categoryId 打到纸面上。 */
export function acquisitionCategoryText(
  row: { categoryId?: string; categoryName?: string },
  names: Record<string, string>,
) {
  return printPlainText(row.categoryName || (row.categoryId ? names[row.categoryId] : ''));
}

/** 按字形簇（用户感知的一个字符）切分，家庭/旗帜等组合 emoji 不会被截成半个；无 Intl.Segmenter 时退回按码点切分。 */
function printGraphemes(text: string): string[] {
  if (typeof Intl !== 'undefined' && typeof Intl.Segmenter === 'function') {
    return Array.from(new Intl.Segmenter(undefined, { granularity: 'grapheme' }).segment(text), (part) => part.segment);
  }
  return Array.from(text);
}

/** 常见文件系统（ext4、APFS、NTFS 等）单个文件名上限按 UTF-8 255 字节算，含扩展名。 */
export const PRINT_FILE_NAME_MAX_BYTES = 255;

function utf8ByteLength(text: string) {
  let bytes = 0;
  for (const char of text) {
    const code = char.codePointAt(0) ?? 0;
    // 孤立代理项按 TextEncoder 的替换字符 U+FFFD 计 3 字节
    bytes += code < 0x80 ? 1 : code < 0x800 ? 2 : code < 0x10000 ? 3 : 4;
  }
  return bytes;
}

/** 截断后文件名末尾不保留的分隔符。 */
const PRINT_FILE_NAME_TRAILING_SEPARATORS = /[\s\-_—–·:：、,，;；.。]+$/u;

/**
 * 导出文件名：清洗非法字符后按字形簇截断到 120 字，并保证「文件名 + 扩展名」UTF-8 不超过 255 字节（同样按字形簇截，不留半个字符）。
 * 传了 extension（如 '.xlsx'）时返回值已包含扩展名。
 */
export function safePrintFileName(value: unknown, extension = '') {
  const normalized = String(value ?? '')
    .replace(/[\u0000-\u001f\u007f]/g, ' ')
    .replace(/[\\/:*?"<>|]/g, '_')
    .replace(/\s+/g, ' ')
    .trim();
  const budget = PRINT_FILE_NAME_MAX_BYTES - utf8ByteLength(extension);
  let bytes = 0;
  const parts = printGraphemes(normalized);
  const kept: string[] = [];
  for (const part of parts.slice(0, 120)) {
    const size = utf8ByteLength(part);
    if (bytes + size > budget) break;
    kept.push(part);
    bytes += size;
  }
  let text = kept.join('').trim();
  // 被截断时（如名称首个字形簇就超过 255 字节），去掉「标题-名称」里残留在末尾的分隔符，避免出现「文物征集登记表-.xlsx」。
  if (kept.length < parts.length) text = text.replace(PRINT_FILE_NAME_TRAILING_SEPARATORS, '').trim();
  return `${text || '未命名'}${extension}`;
}

/**
 * 征集打印标题：只去控制字符、合并空白、按字形簇截断 120 字（与 safePrintTitle 一致；safePrintFileName 另受 255 字节限制），不替换文件名非法字符，也不做 HTML 转义。
 * 转义统一由 htmlPrint → printDocumentTitle 做一次，避免 `A&B 'q'` 被转义两次。
 * 导出 xlsx 时再对同一标题做 safePrintFileName，保证文件名与打印标题同源（标题过长时文件名是标题的字形簇前缀）。
 */
export function acquisitionPrintTitle(value: unknown, fallback = '未命名') {
  const normalized = String(value ?? '')
    .replace(/[\u0000-\u001f\u007f]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
  // 按字形簇截断 120 字，避免代理对、组合 emoji（家庭、旗帜等）被截成半个
  const text = printGraphemes(normalized).slice(0, 120).join('').trim();
  return text || fallback;
}

/** 标题里的名称为空时统一用「未命名」兜底（打印标题与导出文件名一致）。 */
export function printTitleName(value: unknown) {
  return String(value ?? '').trim() || '未命名';
}
