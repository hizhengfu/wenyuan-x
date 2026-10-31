import * as XLSX from 'xlsx';
import { saveAs } from 'file-saver';
import { htmlPrint, safePrintTitle } from '@/components/basic/print/PrintUtils';
import { fileUrl } from '@/utils';
import type { CollectionVo } from '../typings/collection';
import { collectionNatureOptions } from './collectionNature';
import { eraMeaningLabel, eraNotationLabel } from './collectionDescription';
import { loadDicts } from './print';
import { normalizeRepeatedMeasureUnit, withCollectionDictFallback } from './collectionDictFallback';
import type { PrintDictCache } from './printCollectionList';
import {
  PRINT_EMPTY_TEXT,
  acquisitionCategoryText,
  acquisitionCompletenessText,
  acquisitionDimensionText,
  acquisitionQuantityText,
  escapePrintHtml,
  loadCategoryNames,
  printDateText,
  printDictText as sharedPrintDictText,
  printPlainText,
  safePrintFileName,
} from './acquisitionPrintShared';

/** 2-03 藏品登记「导出评审鉴定表」前端过渡版：只用藏品已有字段，不含估价等价格类信息。 */
export const COLLECTION_APPRAISAL_FORM_TITLE = '藏品评审鉴定表';

const DICT_TYPES = ['col_texture', 'col_relic_level', 'col_source', 'col_completeness', 'col_preserve'];

type FieldPair = [string, string];

export interface CollectionAppraisalFormData {
  collection: CollectionVo;
  dicts: PrintDictCache;
  categoryNames: Record<string, string>;
}

export async function loadCollectionAppraisalFormData(collection: CollectionVo): Promise<CollectionAppraisalFormData> {
  const [dicts, categoryNames] = await Promise.all([
    loadDicts(DICT_TYPES),
    collection.categoryName ? Promise.resolve({} as Record<string, string>) : loadCategoryNames(),
  ]);
  return { collection, dicts, categoryNames };
}

function printDictText(dicts: PrintDictCache, type: string, value: unknown) {
  return withCollectionDictFallback(type, value, sharedPrintDictText(dicts, type, value));
}

function completenessText(dicts: PrintDictCache, value: unknown) {
  return withCollectionDictFallback('col_completeness', value, acquisitionCompletenessText(dicts, value, undefined));
}

function natureText(value: unknown) {
  return printPlainText(collectionNatureOptions.find((option) => option.value === value)?.label);
}

function eraFullText(collection: CollectionVo) {
  const details = collection.registrationDetails;
  const kinds = [
    details?.eraMeaning ? eraMeaningLabel(details.eraMeaning) : '',
    details?.eraNotation ? eraNotationLabel(details.eraNotation) : '',
  ].filter(Boolean);
  const era = String(collection.era ?? '').trim();
  if (era && kinds.length) return `${era}（${kinds.join('，')}）`;
  return printPlainText(era || kinds.join('，'));
}

function textureText(dicts: PrintDictCache, collection: CollectionVo) {
  const texture = printDictText(dicts, 'col_texture', collection.texture);
  const kinds = [collection.textureCategory1, collection.textureCategory2]
    .map((value) => String(value ?? '').trim())
    .filter(Boolean)
    .join(' / ');
  if (kinds && texture !== PRINT_EMPTY_TEXT) return `${texture}（${kinds}）`;
  return kinds || texture;
}

function dimensionText(collection: CollectionVo) {
  const base = acquisitionDimensionText(collection.dimensions);
  const unit = String(collection.dimensionUnit ?? '').trim();
  const extra = String(collection.dimensions?.extra?.description ?? '').trim();
  const parts = [base === PRINT_EMPTY_TEXT ? '' : unit ? `${base}（${unit}）` : base, extra].filter(Boolean);
  if (parts.length) return normalizeRepeatedMeasureUnit(parts.join('；'));
  return printPlainText(collection.registrationDetails?.dimensionUnknownReason);
}

function weightText(collection: CollectionVo) {
  const weight = String(collection.weight ?? '').trim();
  const value = weight ? `${weight}${String(collection.weightUnit ?? '').trim()}` : '';
  const range = String(collection.weightRange ?? '').trim();
  if (value && range) return `${value}（${range}）`;
  return printPlainText(value || range || collection.registrationDetails?.weightUnknownReason);
}

function sourceText(dicts: PrintDictCache, collection: CollectionVo) {
  const source = printDictText(dicts, 'col_source', collection.source);
  const desc = String(collection.sourceDesc ?? '').trim();
  if (desc && source !== PRINT_EMPTY_TEXT) return `${source}（${desc}）`;
  return desc || source;
}

function actualQuantityText(collection: CollectionVo) {
  if (collection.actualQuantity === undefined || collection.actualQuantity === null) return PRINT_EMPTY_TEXT;
  return acquisitionQuantityText(collection.actualQuantity, collection.actualQuantityUnit || collection.unit);
}

/** 字段口径：设计方案 4.3.1.1.3 藏品登记「信息录入」所列基础信息（不含估价）。 */
export function collectionAppraisalBaseFields(data: CollectionAppraisalFormData): FieldPair[] {
  const { collection: row, dicts, categoryNames } = data;
  return [
    ['总登记号', printPlainText(row.totalRegNo)],
    ['分类号', printPlainText(row.classNo)],
    ['名称', printPlainText(row.name)],
    ['原名', printPlainText(row.originalName)],
    ['类别', acquisitionCategoryText(row, categoryNames)],
    ['藏品性质', natureText(row.nature)],
    ['年代', eraFullText(row)],
    ['质地', textureText(dicts, row)],
    ['数量', acquisitionQuantityText(row.quantity, row.unit)],
    ['实际数量', actualQuantityText(row)],
    ['尺寸', dimensionText(row)],
    ['质量', weightText(row)],
    ['文物级别', printDictText(dicts, 'col_relic_level', row.level)],
    ['文物来源', sourceText(dicts, row)],
    ['完残程度', completenessText(dicts, row.completeness)],
    ['保存状态', printDictText(dicts, 'col_preserve', row.preserveState)],
    ['入藏时间范围', printPlainText(row.acquireDateRange)],
    ['入藏年度', printPlainText(row.acquireYear)],
  ];
}

export function collectionAppraisalTextFields(data: CollectionAppraisalFormData): FieldPair[] {
  const row = data.collection;
  return [
    ['完残状况', printPlainText(row.completeStatus)],
    ['铭文款识', printPlainText(row.inscription)],
    ['特征描述', printPlainText(row.feature)],
  ];
}

export function collectionAppraisalInfoFields(data: CollectionAppraisalFormData): FieldPair[] {
  const details = data.collection.registrationDetails;
  return [
    ['鉴定机构', printPlainText(details?.appraisalInstitution)],
    ['鉴定人员', printPlainText(details?.appraisers)],
    ['鉴定日期', printDateText(details?.appraisedOn)],
  ];
}

const reviewOpinionLabels = ['真伪鉴定意见', '历史、艺术、科学价值评估', '定级建议', '评审结论'];
const signatureLabels = ['鉴定专家（签字）', '登记人', '部门负责人', '分管馆长'];

function pairRows(fields: FieldPair[]) {
  const rows: string[] = [];
  for (let i = 0; i < fields.length; i += 2) {
    const [leftLabel, leftValue] = fields[i];
    const right = fields[i + 1];
    rows.push(
      `<tr><th>${escapePrintHtml(leftLabel)}</th><td${right ? '' : ' colspan="3"'}>${escapePrintHtml(leftValue)}</td>${
        right ? `<th>${escapePrintHtml(right[0])}</th><td>${escapePrintHtml(right[1])}</td>` : ''
      }</tr>`,
    );
  }
  return rows.join('');
}

function wideRows(fields: FieldPair[], className = '') {
  return fields
    .map(
      ([label, value]) =>
        `<tr><th>${escapePrintHtml(label)}</th><td colspan="3"${className ? ` class="${className}"` : ''}>${escapePrintHtml(
          value,
        )}</td></tr>`,
    )
    .join('');
}

function coverFileId(data: CollectionAppraisalFormData) {
  return String(data.collection.coverImage ?? '').trim();
}

/** 打印版显示图片；xlsx 写同一图片的访问链接，无影像时两边都显示「—」。 */
function coverLinkText(data: CollectionAppraisalFormData) {
  const cover = coverFileId(data);
  if (!cover) return PRINT_EMPTY_TEXT;
  const url = fileUrl(cover);
  try {
    return new URL(url, window.location.origin).href;
  } catch {
    return url;
  }
}

function appraisalTitle(data: CollectionAppraisalFormData) {
  const name = String(data.collection.name || data.collection.totalRegNo || '').trim() || '未命名';
  return safePrintTitle(`${COLLECTION_APPRAISAL_FORM_TITLE}-${name}`);
}

export function buildCollectionAppraisalFormHtml(data: CollectionAppraisalFormData) {
  const cover = coverFileId(data);
  const photo = cover
    ? `<img src="${escapePrintHtml(fileUrl(cover))}" alt="${escapePrintHtml(printPlainText(data.collection.name))}" />`
    : `<span>${PRINT_EMPTY_TEXT}</span>`;
  return `
    <section class="appraisal-print">
      <h1>${COLLECTION_APPRAISAL_FORM_TITLE}</h1>
      <table><tbody>
        ${pairRows(collectionAppraisalBaseFields(data))}
        ${wideRows(collectionAppraisalTextFields(data))}
        <tr><th>藏品影像</th><td colspan="3" class="photo">${photo}</td></tr>
      </tbody></table>
      <h2>鉴定信息</h2>
      <table><tbody>${pairRows(collectionAppraisalInfoFields(data))}</tbody></table>
      <h2>评审意见</h2>
      <table><tbody>
        ${wideRows(
          reviewOpinionLabels.map((label) => [label, '']),
          'opinion',
        )}
      </tbody></table>
      <div class="signatures">${signatureLabels
        .map((label) => `<span>${escapePrintHtml(label)}：</span>`)
        .join('')}<span>日期：</span></div>
    </section>
  `;
}

const appraisalPrintStyle = `
  @page { size: A4 portrait; margin: 14mm; }
  .appraisal-print { font-family: SimSun, "Songti SC", serif; color: #111; padding: 8px; }
  .appraisal-print h1 { text-align: center; font-size: 24px; margin: 0 0 14px; }
  .appraisal-print h2 { font-size: 15px; margin: 16px 0 8px; }
  .appraisal-print table { width: 100%; border-collapse: collapse; table-layout: fixed; }
  .appraisal-print th, .appraisal-print td { border: 1px solid #222; padding: 7px 9px; font-size: 12px; line-height: 1.5; word-break: break-word; white-space: pre-wrap; }
  .appraisal-print th { width: 16%; background: #f6f6f6; text-align: center; font-weight: normal; }
  .appraisal-print td { width: 34%; }
  .appraisal-print td.opinion { height: 64px; vertical-align: top; }
  .appraisal-print td.photo { height: 180px; text-align: center; padding: 4px; }
  .appraisal-print td.photo img { max-width: 100%; max-height: 170px; object-fit: contain; }
  .appraisal-print .signatures { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; margin-top: 28px; font-size: 13px; line-height: 2; }
`;

export function printCollectionAppraisalForm(data: CollectionAppraisalFormData) {
  htmlPrint(buildCollectionAppraisalFormHtml(data), appraisalTitle(data), appraisalPrintStyle);
}

export function exportCollectionAppraisalForm(data: CollectionAppraisalFormData) {
  const pairs = (fields: FieldPair[]) => {
    const rows: string[][] = [];
    for (let i = 0; i < fields.length; i += 2) rows.push([...fields[i], ...(fields[i + 1] ?? [])]);
    return rows;
  };
  const wideStart: number[] = [];
  const aoa: string[][] = [[COLLECTION_APPRAISAL_FORM_TITLE]];
  aoa.push(...pairs(collectionAppraisalBaseFields(data)));
  [...collectionAppraisalTextFields(data), ['藏品影像', coverLinkText(data)] as FieldPair].forEach(([label, value]) => {
    wideStart.push(aoa.length);
    aoa.push([label, value]);
  });
  aoa.push([], ['鉴定信息']);
  aoa.push(...pairs(collectionAppraisalInfoFields(data)));
  aoa.push([], ['评审意见']);
  reviewOpinionLabels.forEach((label) => {
    wideStart.push(aoa.length);
    aoa.push([label, '']);
  });
  aoa.push([], [...signatureLabels.map((label) => `${label}：`), '日期：']);
  const sheet = XLSX.utils.aoa_to_sheet(aoa);
  const merges: XLSX.Range[] = [{ s: { r: 0, c: 0 }, e: { r: 0, c: 3 } }];
  wideStart.forEach((r) => merges.push({ s: { r, c: 1 }, e: { r, c: 3 } }));
  sheet['!merges'] = merges;
  sheet['!cols'] = [14, 34, 14, 34].map((wch) => ({ wch }));
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, sheet, COLLECTION_APPRAISAL_FORM_TITLE);
  const buffer = XLSX.write(workbook, { bookType: 'xlsx', type: 'array' });
  saveAs(
    new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }),
    safePrintFileName(appraisalTitle(data), '.xlsx'),
  );
}
