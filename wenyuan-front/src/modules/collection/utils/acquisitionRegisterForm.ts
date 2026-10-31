import * as XLSX from 'xlsx';
import { saveAs } from 'file-saver';
import { htmlPrint } from '@/components/basic/print/PrintUtils';
import { acquisitionApi } from '../api/acquisition';
import { acquisitionPlanApi } from '../api/acquisitionPlan';
import type { AcquisitionPlanVo, AcquisitionVo } from '../typings/collection';
import { formatYuan } from './moneyYuan';
import { loadDicts } from './print';
import type { PrintDictCache } from './printCollectionList';
import {
  acquisitionCategoryText,
  acquisitionCompletenessText,
  acquisitionDimensionText,
  acquisitionHolderLabel,
  acquisitionQuantityText,
  escapePrintHtml,
  loadCategoryNames,
  printDateText,
  printDictText,
  printPlainText,
  safePrintFileName,
  acquisitionPrintTitle,
  printTitleName,
} from './acquisitionPrintShared';

export const ACQUISITION_REGISTER_TITLE = '文物征集登记表';
export const ACQUISITION_ITEM_REGISTER_SUFFIX = '（逐件）';

const PAGE_SIZE = 200;
const MAX_PAGES = 50;
const REGISTER_DICT_TYPES = [
  'col_plan_period',
  'col_texture',
  'col_completeness',
  'col_source',
  'col_relic_level',
  'flow_biz_status',
];
const XLSX_MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

export interface AcquisitionRegisterData {
  plan: AcquisitionPlanVo;
  acquisitions: AcquisitionVo[];
  dicts: PrintDictCache;
  categoryNames: Record<string, string>;
}

export interface AcquisitionItemRegisterData {
  acquisition: AcquisitionVo;
  dicts: PrintDictCache;
  categoryNames: Record<string, string>;
}

/** 按 planId 分页拉全本计划下的征集单；后端已按 planId 过滤，前端再核对一次。 */
export async function fetchPlanAcquisitions(planId: string) {
  const rows: AcquisitionVo[] = [];
  for (let pageNum = 1; pageNum <= MAX_PAGES; pageNum++) {
    const resp: any = await acquisitionApi.page({ planId, pageNum, pageSize: PAGE_SIZE });
    const list: AcquisitionVo[] = resp?.data?.list ?? resp?.data ?? [];
    const total = Number(resp?.data?.total ?? 0);
    rows.push(...list);
    if (list.length < PAGE_SIZE || (total > 0 && rows.length >= total)) break;
  }
  const seen = new Set<string>();
  return rows.filter((row) => {
    if (row.planId && row.planId !== planId) return false;
    const key = row.id || '';
    if (!key) return true;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

export async function loadAcquisitionRegisterData(planId: string): Promise<AcquisitionRegisterData> {
  const { data } = await acquisitionPlanApi.get(planId);
  if (!data || data.id !== planId) throw new Error('计划读取结果不一致');
  const [acquisitions, dicts, categoryNames] = await Promise.all([
    fetchPlanAcquisitions(planId),
    loadDicts(REGISTER_DICT_TYPES),
    loadCategoryNames(),
  ]);
  return { plan: data as AcquisitionPlanVo, acquisitions, dicts, categoryNames };
}

export async function loadAcquisitionItemRegisterData(acquisitionId: string): Promise<AcquisitionItemRegisterData> {
  const { data } = await acquisitionApi.get(acquisitionId);
  if (!data || data.id !== acquisitionId) throw new Error('征集单读取结果不一致');
  const [dicts, categoryNames] = await Promise.all([loadDicts(REGISTER_DICT_TYPES), loadCategoryNames()]);
  return { acquisition: data as AcquisitionVo, dicts, categoryNames };
}

function planHeader(data: AcquisitionRegisterData): [string, string][] {
  const { plan, dicts } = data;
  return [
    ['计划名称', printPlainText(plan.title)],
    ['计划周期', printDictText(dicts, 'col_plan_period', plan.planType)],
    ['计划年度', printPlainText(plan.year)],
    ['计划月份', plan.planType === 'MONTH' ? printPlainText(plan.month) : '—'],
    ['计划预算', plan.budget === undefined || plan.budget === null ? '—' : formatYuan(plan.budget)],
    ['征集方向', printPlainText(plan.content)],
  ];
}

/** 系统里没有对应字段、需要手填的栏目，打印和导出都留空白格。 */
const HAND_FILL = '';

export const registerColumns = [
  '序号',
  '征集单号',
  '名称',
  '分类',
  '年代',
  '质地',
  '件数单位',
  '尺寸',
  '重量',
  '完残',
  '拟定级别',
  '征集方式',
  '来源人/持有人',
  '征集日期',
  '征集理由',
  '经手人',
  '状态',
  '备注',
];

const registerColumnWidths = [6, 20, 20, 12, 10, 8, 9, 26, 9, 20, 10, 10, 16, 12, 28, 10, 10, 14];

function registerRows(data: AcquisitionRegisterData) {
  const { dicts, categoryNames } = data;
  return data.acquisitions.map((row, index) => [
    String(index + 1),
    printPlainText(row.id),
    printPlainText(row.name),
    acquisitionCategoryText(row, categoryNames),
    printPlainText(row.era),
    printDictText(dicts, 'col_texture', row.texture),
    acquisitionQuantityText(row.quantity, row.unit),
    acquisitionDimensionText(row.dimensions),
    printPlainText(row.weight),
    acquisitionCompletenessText(dicts, row.completeness, row.completeStatus),
    printDictText(dicts, 'col_relic_level', row.proposedLevel),
    printDictText(dicts, 'col_source', row.source),
    printPlainText(row.holder),
    printDateText(row.acquireDate),
    printPlainText(row.reason),
    HAND_FILL,
    printDictText(dicts, 'flow_biz_status', row.flowStatus),
    HAND_FILL,
  ]);
}

const signatureLabels = ['编制人', '专家评审', '分管馆长'];
const itemSignatureLabels = ['经手人', '保管部门负责人', '分管馆长'];

function signatureHtml(labels: string[]) {
  return `<div class="signatures">${labels.map((label) => `<span>${label}：________</span>`).join('')}</div>`;
}

export function buildAcquisitionRegisterHtml(data: AcquisitionRegisterData) {
  const header = planHeader(data);
  const rows = registerRows(data);
  const headerRows = [
    `<tr><th>${header[0][0]}</th><td colspan="3">${escapePrintHtml(header[0][1])}</td></tr>`,
    `<tr><th>${header[1][0]}</th><td>${escapePrintHtml(header[1][1])}</td><th>${header[2][0]}</th><td>${escapePrintHtml(
      header[2][1],
    )}</td></tr>`,
    `<tr><th>${header[3][0]}</th><td>${escapePrintHtml(header[3][1])}</td><th>${header[4][0]}</th><td>${escapePrintHtml(
      header[4][1],
    )}</td></tr>`,
    `<tr><th>${header[5][0]}</th><td colspan="3">${escapePrintHtml(header[5][1])}</td></tr>`,
  ].join('');
  const bodyRows = rows.length
    ? rows.map((cells) => `<tr>${cells.map((cell) => `<td>${escapePrintHtml(cell)}</td>`).join('')}</tr>`).join('')
    : `<tr><td colspan="${registerColumns.length}" class="empty">—</td></tr>`;
  return `
    <article class="register-print">
      <h1>${ACQUISITION_REGISTER_TITLE}</h1>
      <table class="plan"><tbody>${headerRows}</tbody></table>
      <table class="items">
        <thead><tr>${registerColumns.map((label) => `<th>${label}</th>`).join('')}</tr></thead>
        <tbody>${bodyRows}</tbody>
      </table>
      ${signatureHtml(signatureLabels)}
    </article>
  `;
}

const registerStyle = `
  @page { size: A4 landscape; margin: 10mm; }
  .register-print { font-family: SimSun, "Songti SC", serif; color: #111; padding: 8px; }
  .register-print h1 { text-align: center; font-size: 24px; margin: 0 0 14px; }
  .register-print table { width: 100%; border-collapse: collapse; table-layout: fixed; }
  .register-print th, .register-print td { border: 1px solid #222; padding: 6px 8px; font-size: 12px; line-height: 1.5; word-break: break-word; }
  .register-print th { background: #f6f6f6; text-align: center; font-weight: normal; }
  .register-print .plan th { width: 12%; }
  .register-print .plan td { width: 38%; white-space: pre-wrap; }
  .register-print .items { margin-top: 14px; }
  .register-print .items th, .register-print .items td { padding: 4px 3px; font-size: 10px; }
  .register-print .items th:first-child, .register-print .items td:first-child { width: 28px; text-align: center; }
  .register-print .items td.empty { text-align: center; }
  .register-print .signatures { display: flex; justify-content: space-between; margin-top: 36px; font-size: 13px; line-height: 2; }
`;

export function acquisitionRegisterPrintTitle(data: AcquisitionRegisterData) {
  return acquisitionPrintTitle(`${ACQUISITION_REGISTER_TITLE}-${printTitleName(data.plan?.title)}`);
}

/** 导出文件名由打印标题再做文件名清洗得到。 */
export function acquisitionRegisterFileName(data: AcquisitionRegisterData) {
  return safePrintFileName(acquisitionRegisterPrintTitle(data), '.xlsx');
}

export function printAcquisitionRegister(data: AcquisitionRegisterData) {
  htmlPrint(buildAcquisitionRegisterHtml(data), acquisitionRegisterPrintTitle(data), registerStyle);
}

export function buildAcquisitionRegisterSheet(data: AcquisitionRegisterData) {
  const header = planHeader(data);
  const rows = registerRows(data);
  const width = registerColumns.length;
  const aoa: string[][] = [
    [ACQUISITION_REGISTER_TITLE],
    ...header.map(([label, value]) => [label, value]),
    [],
    registerColumns,
    ...(rows.length ? rows : [['—']]),
    [],
    signatureLabels.map((label) => `${label}：________`),
  ];
  const sheet = XLSX.utils.aoa_to_sheet(aoa);
  const merges: XLSX.Range[] = [{ s: { r: 0, c: 0 }, e: { r: 0, c: width - 1 } }];
  header.forEach((_, index) => merges.push({ s: { r: index + 1, c: 1 }, e: { r: index + 1, c: width - 1 } }));
  if (!rows.length) {
    const emptyRow = header.length + 3;
    merges.push({ s: { r: emptyRow, c: 0 }, e: { r: emptyRow, c: width - 1 } });
  }
  sheet['!merges'] = merges;
  sheet['!cols'] = registerColumnWidths.map((wch) => ({ wch }));
  return sheet;
}

function saveSheet(sheet: XLSX.WorkSheet, fileName: string) {
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, sheet, ACQUISITION_REGISTER_TITLE);
  const buffer = XLSX.write(workbook, { bookType: 'xlsx', type: 'array' });
  saveAs(new Blob([buffer], { type: XLSX_MIME }), fileName);
}

export function exportAcquisitionRegister(data: AcquisitionRegisterData) {
  saveSheet(buildAcquisitionRegisterSheet(data), acquisitionRegisterFileName(data));
}

/** 逐件登记版：一张征集单（一件/一套征集对象）一页。[标签, 值, 是否整行] */
export function acquisitionItemRegisterFields(data: AcquisitionItemRegisterData): [string, string, boolean][] {
  const { acquisition: row, dicts, categoryNames } = data;
  return [
    ['征集单号', printPlainText(row.id), false],
    ['所属计划', printPlainText(row.planTitle), false],
    ['名称', printPlainText(row.name), false],
    ['分类', acquisitionCategoryText(row, categoryNames), false],
    ['年代', printPlainText(row.era), false],
    ['质地', printDictText(dicts, 'col_texture', row.texture), false],
    ['件数单位', acquisitionQuantityText(row.quantity, row.unit), false],
    ['重量', printPlainText(row.weight), false],
    ['尺寸', acquisitionDimensionText(row.dimensions), true],
    ['完残', acquisitionCompletenessText(dicts, row.completeness, row.completeStatus), false],
    ['拟定级别', printDictText(dicts, 'col_relic_level', row.proposedLevel), false],
    ['征集方式', printDictText(dicts, 'col_source', row.source), false],
    [acquisitionHolderLabel(row.source), printPlainText(row.holder), false],
    ['征集日期', printDateText(row.acquireDate), false],
    ['状态', printDictText(dicts, 'flow_biz_status', row.flowStatus), false],
    ['征集理由', printPlainText(row.reason), true],
    ['经手人', HAND_FILL, true],
    ['备注', HAND_FILL, true],
  ];
}

/** 把字段排成 4 列（标签/值/标签/值）；整行字段单独一行，值跨 3 列。 */
function itemFieldLayout(fields: [string, string, boolean][]) {
  const lines: [string, string, boolean][][] = [];
  let pending: [string, string, boolean] | null = null;
  for (const field of fields) {
    if (field[2]) {
      if (pending) lines.push([pending]);
      pending = null;
      lines.push([field]);
    } else if (pending) {
      lines.push([pending, field]);
      pending = null;
    } else {
      pending = field;
    }
  }
  if (pending) lines.push([pending]);
  return lines;
}

export function buildAcquisitionItemRegisterHtml(data: AcquisitionItemRegisterData) {
  const lines = itemFieldLayout(acquisitionItemRegisterFields(data));
  const body = lines
    .map((line) => {
      const [first, second] = line;
      const cls = first[2] ? ' class="wide"' : '';
      return `<tr><th>${escapePrintHtml(first[0])}</th><td${second ? '' : ' colspan="3"'}${cls}>${escapePrintHtml(
        first[1],
      )}</td>${second ? `<th>${escapePrintHtml(second[0])}</th><td>${escapePrintHtml(second[1])}</td>` : ''}</tr>`;
    })
    .join('');
  return `
    <article class="register-item-print">
      <h1>${ACQUISITION_REGISTER_TITLE}</h1>
      <table><tbody>${body}</tbody></table>
      ${signatureHtml(itemSignatureLabels)}
    </article>
  `;
}

const itemRegisterStyle = `
  @page { size: A4 portrait; margin: 14mm; }
  .register-item-print { font-family: SimSun, "Songti SC", serif; color: #111; padding: 8px; }
  .register-item-print h1 { text-align: center; font-size: 24px; margin: 0 0 14px; }
  .register-item-print table { width: 100%; border-collapse: collapse; table-layout: fixed; }
  .register-item-print th, .register-item-print td { border: 1px solid #222; padding: 8px 9px; font-size: 12px; line-height: 1.6; word-break: break-word; white-space: pre-wrap; }
  .register-item-print th { width: 16%; background: #f6f6f6; text-align: center; font-weight: normal; }
  .register-item-print td { width: 34%; height: 22px; }
  .register-item-print td.wide { height: 48px; vertical-align: top; }
  .register-item-print .signatures { display: flex; justify-content: space-between; margin-top: 36px; font-size: 13px; line-height: 2; }
`;

export function acquisitionItemRegisterPrintTitle(data: AcquisitionItemRegisterData) {
  return acquisitionPrintTitle(
    `${ACQUISITION_REGISTER_TITLE}${ACQUISITION_ITEM_REGISTER_SUFFIX}-${printTitleName(data.acquisition?.name)}`,
  );
}

export function acquisitionItemRegisterFileName(data: AcquisitionItemRegisterData) {
  return safePrintFileName(acquisitionItemRegisterPrintTitle(data), '.xlsx');
}

export function printAcquisitionItemRegister(data: AcquisitionItemRegisterData) {
  htmlPrint(buildAcquisitionItemRegisterHtml(data), acquisitionItemRegisterPrintTitle(data), itemRegisterStyle);
}

export function buildAcquisitionItemRegisterSheet(data: AcquisitionItemRegisterData) {
  const lines = itemFieldLayout(acquisitionItemRegisterFields(data));
  const aoa: string[][] = [[ACQUISITION_REGISTER_TITLE]];
  const merges: XLSX.Range[] = [{ s: { r: 0, c: 0 }, e: { r: 0, c: 3 } }];
  lines.forEach((line) => {
    const r = aoa.length;
    const [first, second] = line;
    if (second) {
      aoa.push([first[0], first[1], second[0], second[1]]);
    } else {
      aoa.push([first[0], first[1]]);
      merges.push({ s: { r, c: 1 }, e: { r, c: 3 } });
    }
  });
  aoa.push([]);
  const signRow = aoa.length;
  aoa.push(itemSignatureLabels.map((label) => `${label}：________`));
  const sheet = XLSX.utils.aoa_to_sheet(aoa);
  sheet['!merges'] = merges;
  sheet['!cols'] = [14, 30, 14, 30].map((wch) => ({ wch }));
  sheet['!rows'] = aoa.map((_, index) => (index === signRow ? { hpt: 28 } : {}));
  return sheet;
}

export function exportAcquisitionItemRegister(data: AcquisitionItemRegisterData) {
  saveSheet(buildAcquisitionItemRegisterSheet(data), acquisitionItemRegisterFileName(data));
}
