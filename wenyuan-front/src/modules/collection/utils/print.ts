import { htmlPrint, safePrintTitle } from '@/components/basic/print/PrintUtils';
import { fileUrl, formatLocalDateValue } from '@/utils';
import { type as fetchDictType } from '@/modules/sys/api/dict';
import { dictText } from './dictRender';
import { formatYuan } from './moneyYuan';
import { resolveCollectionPrintMeta } from './printDocumentMeta';
import { collectionPrintApi } from '../api/print';
import { printErrorText } from './printError';
import { notification } from '@/utils/message';
import {
  buildCollectionListPrintHtml,
  collectionListPrintStyles,
  formatPrintDateTime,
  formatPrintCollectionValue,
  printCollectionFieldSpecs,
  printDictLabel,
  type PrintCollectionListOptions,
  type PrintDictCache,
  type PrintDictOption,
} from './printCollectionList';
import type {
  CollectionAttachmentVo,
  CollectionMediaVo,
  CollectionPrintVo,
  CollectionSetVo,
  CollectionVo,
  MovementVo,
} from '../typings/collection';

type PrintField = { label: string; value?: string | null; dictType?: string | null };
type DictOption = PrintDictOption;
type DictCache = PrintDictCache;

export { formatPrintDateTime } from './printCollectionList';

export const printFieldColumns = [
  { title: '字段', key: 'label', width: 160 },
  {
    title: '值',
    key: 'value',
    minWidth: 220,
    render: (row: PrintField) => (row.dictType ? dictText(row.dictType, row.value) : row.value || '--'),
  },
];

export const printCollectionColumns = [
  ...printCollectionFieldSpecs.map((spec) => ({
    title: spec.label,
    key: spec.key || spec.label,
    width: spec.width,
    minWidth: spec.minWidth,
    render: (row: CollectionVo) => {
      if (spec.dictType) {
        const raw = spec.value ? spec.value(row) : spec.key ? row[spec.key] : '';
        return dictText(spec.dictType, raw as any);
      }
      return formatPrintCollectionValue(row, spec) || '--';
    },
  })),
];

function escapeHtml(value: unknown) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function splitFileIds(value: unknown) {
  return String(value ?? '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

function isImageEvidenceField(label: string) {
  return /影像|图像|图片|照片/.test(label);
}

function isDateTimeField(label: string) {
  return /时间|日期|date|time/i.test(label);
}

export async function loadDicts(types: (string | null | undefined)[]) {
  const uniqueTypes = Array.from(new Set(types.filter((type): type is string => Boolean(type))));
  const entries = await Promise.all(
    uniqueTypes.map(async (type) => {
      try {
        const resp: any = await fetchDictType(type, true);
        const options = (resp?.data || []).map((item: any) => ({
          label: String(item.label ?? item.name ?? item.value ?? ''),
          value: String(item.value ?? ''),
        }));
        return [type, options] as const;
      } catch {
        return [type, []] as const;
      }
    }),
  );
  return Object.fromEntries(entries) as DictCache;
}

function dictLabel(dicts: DictCache, type: string | null | undefined, value: unknown) {
  return printDictLabel(dicts, type, value);
}

function renderFieldValue(field: PrintField, dicts: DictCache) {
  const text = String(field.value ?? '').trim();
  if (!text || !isImageEvidenceField(field.label)) {
    return escapeHtml(dictLabel(dicts, field.dictType, isDateTimeField(field.label) ? formatPrintDateTime(text) : text));
  }
  const ids = splitFileIds(text);
  if (!ids.length) return '';
  return `
    <div class="field-images">
      ${ids
        .map(
          (id) => `
            <figure>
              <img src="${escapeHtml(fileUrl(id))}" alt="${escapeHtml(id)}" onerror="this.style.display='none'" />
              <figcaption>${escapeHtml(id)}</figcaption>
            </figure>
          `,
        )
        .join('')}
    </div>
  `;
}

function buildFields(data: CollectionPrintVo, dicts: DictCache, documentLabel = '单号') {
  const fields: PrintField[] = [
    { label: documentLabel, value: data.documentNo || '' },
    { label: '收藏单位', value: data.orgName || '' },
    { label: '生成时间', value: formatPrintDateTime(data.generatedAt) },
    ...(data.fields || []),
  ];
  const rows: string[] = [];
  for (let i = 0; i < fields.length; i += 2) {
    const left = fields[i];
    const right = fields[i + 1];
    rows.push(`
      <tr>
        <th>${escapeHtml(left.label)}</th>
        <td>${renderFieldValue(left, dicts)}</td>
        <th>${right ? escapeHtml(right.label) : ''}</th>
        <td>${right ? renderFieldValue(right, dicts) : ''}</td>
      </tr>
    `);
  }
  return rows.join('');
}

function buildCollections(data: CollectionPrintVo, dicts: DictCache) {
  return (data.collections || [])
    .map(
      (item, index) => `
        <tr>
          <td>${index + 1}</td>
          ${printCollectionFieldSpecs
            .map((spec) => `<td>${escapeHtml(formatPrintCollectionValue(item, spec, dicts))}</td>`)
            .join('')}
        </tr>
      `,
    )
    .join('');
}

const dimensionLabels: Record<string, string> = {
  height: '通高',
  mouth: '口径',
  bottom: '底径',
  belly: '腹径',
  length: '长',
  width: '宽',
  diameter: '直径',
};

function formatDimensions(dimensions?: Record<string, any> | null) {
  const text = Object.entries(dimensions ?? {})
    .filter(([, value]) => value !== undefined && value !== null && String(value).trim() !== '')
    .map(([key, value]) => `${dimensionLabels[key] || key}：${value}`)
    .join('；');
  return text || '';
}

function formatFen(value?: number | null) {
  if (value === undefined || value === null) return '';
  return formatYuan(value);
}

function archiveRows(rows: [string, unknown, string?, unknown?][]) {
  return rows
    .map(([labelA, valueA, labelB, valueB]) => {
      if (!labelB) {
        return `<tr><th>${escapeHtml(labelA)}</th><td colspan="5">${escapeHtml(valueA || '')}</td></tr>`;
      }
      return `
        <tr>
          <th>${escapeHtml(labelA)}</th>
          <td colspan="2">${escapeHtml(valueA || '')}</td>
          <th>${escapeHtml(labelB)}</th>
          <td colspan="2">${escapeHtml(valueB || '')}</td>
        </tr>
      `;
    })
    .join('');
}

function archiveMediaRows(media: CollectionMediaVo[], dicts: DictCache) {
  if (!media.length) return '<tr><td colspan="6" class="empty">暂无影像</td></tr>';
  return media
    .map(
      (item, index) => `
        <tr>
          <td>${index + 1}</td>
          <td class="media-thumb">${item.fileId ? `<img src="${escapeHtml(fileUrl(item.fileId))}" alt="${escapeHtml(item.title || item.fileId)}" />` : ''}</td>
          <td>${escapeHtml(dictLabel(dicts, 'col_media_type', item.mediaType))}</td>
          <td>${escapeHtml(item.title || '')}</td>
          <td>${escapeHtml(item.fileId || '')}</td>
          <td>${escapeHtml(dictLabel(dicts, 'col_presence', item.isCover))}</td>
        </tr>
      `,
    )
    .join('');
}

function archiveAttachmentRows(attachments: CollectionAttachmentVo[], dicts: DictCache) {
  if (!attachments.length) return '<tr><td colspan="5" class="empty">暂无附件</td></tr>';
  return attachments
    .map(
      (item, index) => `
        <tr>
          <td>${index + 1}</td>
          <td>${escapeHtml(dictLabel(dicts, 'col_attachment_type', item.attachmentType))}</td>
          <td>${escapeHtml(item.name || '')}</td>
          <td>${escapeHtml(item.fileId || '')}</td>
          <td>${escapeHtml(item.remark || '')}</td>
        </tr>
      `,
    )
    .join('');
}

function archiveSetRows(sets: CollectionSetVo[], dicts: DictCache) {
  if (!sets.length) return '<tr><td colspan="5" class="empty">暂无套件/分件关系</td></tr>';
  return sets
    .map(
      (item, index) => `
        <tr>
          <td>${index + 1}</td>
          <td>${escapeHtml(dictLabel(dicts, 'col_set_relation', item.relation))}</td>
          <td>${escapeHtml(item.partNo || '')}</td>
          <td>${escapeHtml(item.partName || '')}</td>
          <td>${escapeHtml(item.childCollectionId || '')}</td>
        </tr>
      `,
    )
    .join('');
}

function archiveMovementRows(movements: MovementVo[], dicts: DictCache) {
  if (!movements.length) return '<tr><td colspan="8" class="empty">暂无出入库记录</td></tr>';
  return movements
    .map(
      (item, index) => `
        <tr>
          <td>${index + 1}</td>
          <td>${escapeHtml(item.documentNo || '')}</td>
          <td>${escapeHtml(dictLabel(dicts, 'col_move_type', item.type))}</td>
          <td>${escapeHtml(dictLabel(dicts, 'col_move_reason', item.reason))}</td>
          <td>${escapeHtml(item.department || '')}</td>
          <td>${escapeHtml(item.operatorName || item.operator || '')}</td>
          <td>${escapeHtml(item.reviewerName || item.reviewer || '')}</td>
          <td>${escapeHtml(formatPrintDateTime(item.operatedAt))}</td>
        </tr>
      `,
    )
    .join('');
}

export async function printCollectionDocument(data: CollectionPrintVo | null | undefined) {
  if (!data) return;
  const meta = resolveCollectionPrintMeta(data);
  const dicts = await loadDicts([
    'col_relic_level',
    'col_completeness',
    ...printCollectionFieldSpecs.map((field) => field.dictType),
    ...(data.fields || []).map((field) => field.dictType),
  ]);
  const subtitle = data.title && data.title !== meta.title ? `<div class="subtitle">${escapeHtml(data.title)}</div>` : '';
  const html = `
    <section class="collection-print">
      <div class="doc-code">${escapeHtml(meta.documentLabel)}：${escapeHtml(data.documentNo || '')}</div>
      <h1>${escapeHtml(meta.title)}</h1>
      ${subtitle}
      <table class="fields"><tbody>${buildFields(data, dicts, meta.documentLabel)}</tbody></table>
      <h2>${escapeHtml(meta.collectionTitle)}</h2>
      <table class="items">
        <thead>
          <tr>
            <th>序号</th>
            ${printCollectionFieldSpecs.map((spec) => `<th>${escapeHtml(spec.label)}</th>`).join('')}
          </tr>
        </thead>
        <tbody>${buildCollections(data, dicts)}</tbody>
      </table>
      <div class="signatures">
        ${meta.signatures.map((label) => `<span>${escapeHtml(label)}：</span>`).join('')}
      </div>
      <div class="print-note">本表用于藏品业务审批、交接、账物核对和归档，打印后由相关人员签字确认。</div>
    </section>
  `;
  htmlPrint(
    html,
    meta.title,
    `
      @page { size: A4 portrait; margin: 14mm; }
      .collection-print { font-family: SimSun, "Songti SC", serif; color: #111; padding: 8px; }
      .collection-print .doc-code { text-align: right; font-size: 12px; margin-bottom: 6px; }
      .collection-print h1 { text-align: center; font-size: 24px; letter-spacing: 0; margin: 0 0 6px; }
      .collection-print .subtitle { text-align: center; font-size: 13px; margin-bottom: 12px; }
      .collection-print h2 { font-size: 15px; margin: 18px 0 8px; }
      .collection-print table { width: 100%; border-collapse: collapse; table-layout: fixed; }
      .collection-print th, .collection-print td { border: 1px solid #222; padding: 7px 9px; font-size: 12px; line-height: 1.5; word-break: break-word; }
      .collection-print .fields th { width: 16%; background: #f6f6f6; text-align: center; }
      .collection-print .fields td { width: 34%; min-height: 30px; }
      .collection-print .field-images { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px; }
      .collection-print .field-images figure { margin: 0; text-align: center; }
      .collection-print .field-images img { max-width: 100%; max-height: 96px; object-fit: contain; border: 1px solid #ddd; }
      .collection-print .field-images figcaption { margin-top: 2px; font-size: 10px; color: #555; word-break: break-all; }
      .collection-print .items th { background: #f6f6f6; }
      .collection-print .items th, .collection-print .items td { padding: 5px 5px; font-size: 11px; line-height: 1.35; }
      .collection-print .items th:first-child, .collection-print .items td:first-child { width: 48px; text-align: center; }
      .collection-print .signatures { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; margin-top: 28px; font-size: 13px; }
      .collection-print .signatures span { min-height: 34px; border-bottom: 1px solid #222; }
      .collection-print .print-note { margin-top: 14px; font-size: 11px; color: #444; }
    `,
  );
}

export function downloadCollectionPrintWord(data: CollectionPrintVo | null | undefined) {
  const businessId = data?.businessId || data?.documentNo;
  if (!data?.type || !businessId) return;
  const meta = resolveCollectionPrintMeta(data);
  const safeNo = String(data.documentNo || businessId).replace(/[\\/:*?"<>|]/g, '_');
  return collectionPrintApi.downloadDocx(data.type, businessId, `${meta.title}_${safeNo}.docx`).catch(async (error) => {
    // 打印文件请求不走全局提示（错误体是 Blob），这里显示后端原因或中文兜底。
    const content = await printErrorText(error, '打印文件下载失败');
    if (content) notification({ type: 'warning', title: '请注意', content });
  });
}

export async function printCollectionList(collections: CollectionVo[], options: PrintCollectionListOptions = {}) {
  const dicts = await loadDicts([
    'col_texture',
    'col_relic_level',
    'col_completeness',
    'col_presence',
    ...printCollectionFieldSpecs.map((field) => field.dictType),
    ...(options.querySummary || []).map((field) => field.dictType),
  ]);
  htmlPrint(buildCollectionListPrintHtml(collections, dicts, options), options.title || '藏品清册', collectionListPrintStyles);
}

export async function printCollectionArchive(
  collection: CollectionVo | null | undefined,
  media: CollectionMediaVo[] = [],
  attachments: CollectionAttachmentVo[] = [],
  sets: CollectionSetVo[] = [],
  movements: MovementVo[] = [],
) {
  if (!collection) return;
  const dicts = await loadDicts([
    'col_texture',
    'col_relic_level',
    'col_source',
    'col_life_status',
    'col_preserve',
    'col_completeness',
    'col_media_type',
    'col_attachment_type',
    'col_set_relation',
    'col_move_type',
    'col_move_reason',
    'col_presence',
  ]);
  const d = (type: string, value: unknown) => dictLabel(dicts, type, value);
  const coverFileId = media.find((item) => item.isCover && item.fileId)?.fileId || media.find((item) => item.fileId)?.fileId || collection.coverImage;
  const coverHtml = coverFileId
    ? `<img src="${escapeHtml(fileUrl(coverFileId))}" alt="${escapeHtml(collection.name || '藏品影像')}" />`
    : '<span>暂无影像</span>';
  const html = `
    <section class="collection-archive-print">
      <h1>藏品登记表</h1>
      <table class="archive-form">
        <tbody>
          <tr>
            <th>总登记号</th>
            <td>${escapeHtml(collection.totalRegNo || '')}</td>
            <th>分类号</th>
            <td>${escapeHtml(collection.classNo || '')}</td>
            <th rowspan="6">影像</th>
            <td rowspan="6" class="photo">${coverHtml}</td>
          </tr>
          <tr>
            <th>名称</th>
            <td>${escapeHtml(collection.name || '')}</td>
            <th>文物分类</th>
            <td>${escapeHtml(collection.categoryName || '')}</td>
          </tr>
          <tr>
            <th>年代</th>
            <td>${escapeHtml(collection.era || '')}</td>
            <th>质地</th>
            <td>${escapeHtml(d('col_texture', collection.texture))}</td>
          </tr>
          <tr>
            <th>级别</th>
            <td>${escapeHtml(d('col_relic_level', collection.level))}</td>
            <th>数量</th>
            <td>${escapeHtml(`${collection.quantity || ''} ${collection.unit || ''}`.trim())}</td>
          </tr>
          <tr>
            <th>来源</th>
            <td>${escapeHtml(d('col_source', collection.source))}</td>
            <th>取得日期</th>
            <td>${escapeHtml(collection.acquireDate == null ? '' : formatLocalDateValue(collection.acquireDate))}</td>
          </tr>
          <tr>
            <th>当前库位</th>
            <td>${escapeHtml(collection.warehouseName || '')}</td>
            <th>业务状态</th>
            <td>${escapeHtml(d('col_life_status', collection.lifeStatus))}</td>
          </tr>
          ${archiveRows([
            ['辅助编号', collection.auxNo, '保存状态', d('col_preserve', collection.preserveState)],
            ['完残程度', d('col_completeness', collection.completeness), '质量/重量', collection.weight],
            ['估价', formatFen(collection.valuation), '登记时间', formatPrintDateTime(collection.registeredAt)],
            ['尺寸', formatDimensions(collection.dimensions)],
            ['来源说明', collection.sourceDesc],
            ['完残状况', collection.completeStatus],
            ['铭文款识', collection.inscription],
            ['特征描述', collection.feature],
            ['备注', collection.remark],
          ])}
        </tbody>
      </table>

      <h2>影像资料</h2>
      <table class="archive-list">
        <thead><tr><th>序号</th><th>预览</th><th>类型</th><th>标题</th><th>文件ID</th><th>封面</th></tr></thead>
        <tbody>${archiveMediaRows(media, dicts)}</tbody>
      </table>

      <h2>附件材料</h2>
      <table class="archive-list">
        <thead><tr><th>序号</th><th>类型</th><th>名称</th><th>文件ID</th><th>备注</th></tr></thead>
        <tbody>${archiveAttachmentRows(attachments, dicts)}</tbody>
      </table>

      <h2>套件/分件</h2>
      <table class="archive-list">
        <thead><tr><th>序号</th><th>关系</th><th>分件号</th><th>分件名称</th><th>关联藏品ID</th></tr></thead>
        <tbody>${archiveSetRows(sets, dicts)}</tbody>
      </table>

      <h2>出入库记录</h2>
      <table class="archive-list">
        <thead><tr><th>序号</th><th>单号</th><th>类型</th><th>事由</th><th>部门</th><th>经办人</th><th>复核人</th><th>时间</th></tr></thead>
        <tbody>${archiveMovementRows(movements, dicts)}</tbody>
      </table>

      <div class="signatures">
        <span>登记人：</span>
        <span>复核人：</span>
        <span>保管员：</span>
        <span>日期：</span>
      </div>
    </section>
  `;
  htmlPrint(
    html,
    safePrintTitle(`藏品登记表-${collection.name || collection.totalRegNo || ''}`),
    `
      @page { size: A4 portrait; margin: 14mm; }
      .collection-archive-print { font-family: SimSun, "Songti SC", serif; color: #111; padding: 8px; }
      .collection-archive-print h1 { text-align: center; font-size: 24px; letter-spacing: 0; margin: 0 0 14px; }
      .collection-archive-print h2 { font-size: 15px; margin: 16px 0 6px; }
      .collection-archive-print table { width: 100%; border-collapse: collapse; table-layout: fixed; }
      .collection-archive-print th, .collection-archive-print td { border: 1px solid #222; padding: 6px 8px; font-size: 12px; line-height: 1.5; vertical-align: middle; word-break: break-word; }
      .collection-archive-print th { background: #f6f6f6; font-weight: 700; text-align: center; }
      .collection-archive-print .archive-form th { width: 12%; }
      .collection-archive-print .archive-form td { width: 18%; }
      .collection-archive-print .photo { width: 22%; height: 190px; text-align: center; padding: 4px; }
      .collection-archive-print .photo img { max-width: 100%; max-height: 180px; object-fit: contain; }
      .collection-archive-print .photo span, .collection-archive-print .empty { color: #666; text-align: center; }
      .collection-archive-print .archive-list th:first-child, .collection-archive-print .archive-list td:first-child { width: 42px; text-align: center; }
      .collection-archive-print .media-thumb { width: 72px; text-align: center; padding: 3px; }
      .collection-archive-print .media-thumb img { max-width: 58px; max-height: 58px; object-fit: contain; }
      .collection-archive-print .signatures { display: grid; grid-template-columns: repeat(4, 1fr); gap: 18px; margin-top: 24px; font-size: 13px; }
    `,
  );
}
