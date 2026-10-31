import { htmlPrint } from '@/components/basic/print/PrintUtils';
import { collectionNatureOptions } from './collectionNature';
import { formatYuan } from './moneyYuan';
import { loadDicts } from './print';
import {
  acquisitionCategoryText,
  acquisitionCompletenessText,
  acquisitionDimensionText,
  acquisitionHolderLabel,
  acquisitionQuantityText,
  escapePrintHtml,
  loadCategoryNames,
  printBooleanText,
  printCellHtml,
  printDateText,
  printDictText,
  printPlainText,
  acquisitionPrintTitle,
  printTitleName,
} from './acquisitionPrintShared';

export interface AcquisitionFlowHistoryItem {
  nodeKey?: string | null;
  nodeName?: string | null;
  action?: string | null;
  comment?: string | null;
  operatorName?: string | null;
  createTime?: unknown;
}

export interface AcquisitionExpertOpinionPrintOptions {
  /** 专家估价属于保密信息，默认不打印。 */
  includePrice?: boolean;
  /** 征集审批流程历史；取 appraise（鉴定委员会）节点最后一条办理意见作为鉴定委员会意见。 */
  flowHistory?: AcquisitionFlowHistoryItem[] | null;
  /** 流程记录读取失败：鉴定委员会意见栏注明，鉴定结论留空。 */
  flowLoadFailed?: boolean;
}

export const ACQUISITION_FLOW_LOAD_FAILED_TEXT = '流程记录读取失败';

export const ACQUISITION_EXPERT_OPINION_TITLE = '专家鉴定意见表';

/** 鉴定委员会意见栏名：由鉴定委员会节点办理人代表填写，集体决策文件另附。 */
export const ACQUISITION_COMMITTEE_OPINION_LABEL = '鉴定委员会意见（代表办理，附集体决策文件）';

/** 征集流程中由鉴定委员会办理的节点。 */
export const ACQUISITION_APPRAISE_NODE_KEY = 'appraise';

const approveActions = ['APPROVE', 'AGREE', 'PASS'];
const rejectActions = ['REJECT', 'REFUSE', 'DISAGREE', 'TERMINATE'];

function natureText(value: unknown) {
  return printPlainText(collectionNatureOptions.find((option) => option.value === value)?.label);
}

function priceText(value: unknown) {
  return value === undefined || value === null || value === '' ? '—' : formatYuan(Number(value));
}

function fieldRows(fields: [string, string][]) {
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

function opinionRow(label: string, value: unknown, blank = false) {
  const text = blank && !String(value ?? '').trim() ? '' : printCellHtml(value);
  return `<tr><th>${escapePrintHtml(label)}</th><td colspan="3" class="opinion">${text}</td></tr>`;
}

function expertSection(expert: Record<string, any>, index: number, includePrice: boolean) {
  return `
    <section class="expert">
      <h2>专家${index + 1}：${printCellHtml(expert.expertName)}</h2>
      <table><tbody>
        ${fieldRows([
          ['姓名', printPlainText(expert.expertName)],
          ['任职机构', printPlainText(expert.organization)],
          ['职称', printPlainText(expert.professionalTitle)],
          ['研究方向', printPlainText(expert.specialty)],
          ['评估日期', printDateText(expert.assessedOn)],
          ['是否同意征集', printBooleanText(expert.agree, '同意征集', '不同意征集')],
          ...(includePrice ? ([['独立评估价', priceText(expert.proposedPriceCents)]] as [string, string][]) : []),
        ])}
        ${opinionRow('真伪鉴定意见', expert.authenticityOpinion)}
        ${opinionRow('历史艺术科学价值意见', expert.valueOpinion)}
        ${opinionRow('流传经历评估意见', expert.provenanceOpinion)}
        <tr><th>专家签字</th><td class="sign"></td><th>日期</th><td class="sign"></td></tr>
      </tbody></table>
    </section>
  `;
}

/** 取鉴定委员会节点最后一次实际办理（排除系统建任务等记录）。 */
export function latestAppraiseDecision(history?: AcquisitionFlowHistoryItem[] | null) {
  const handled = (history ?? []).filter(
    (item) =>
      item?.nodeKey === ACQUISITION_APPRAISE_NODE_KEY &&
      [...approveActions, ...rejectActions].includes(String(item.action ?? '').toUpperCase()),
  );
  return handled.length ? handled[handled.length - 1] : null;
}

/** 鉴定结论：以鉴定委员会办理结果为准；尚未办理时，若已有专家否决则写明，否则留空待填。 */
export function appraisalConclusionText(decision: AcquisitionFlowHistoryItem | null, vetoed: boolean) {
  const action = String(decision?.action ?? '').toUpperCase();
  if (approveActions.includes(action)) return '同意征集';
  if (rejectActions.includes(action)) return '不同意征集';
  if (vetoed) return '不同意征集（有专家否决）';
  return '';
}

export interface AcquisitionExpertSummary {
  expertCount: number;
  agreedCount: number;
  vetoed: boolean;
}

export function summarizeAcquisitionExperts(data: Record<string, any>): AcquisitionExpertSummary {
  const experts: Record<string, any>[] = Array.isArray(data?.experts) ? data.experts : [];
  return {
    expertCount: experts.length,
    agreedCount: experts.filter((expert) => expert?.agree === true).length,
    vetoed: !!data?.vetoOn || experts.some((expert) => expert?.agree === false),
  };
}

export async function buildAcquisitionExpertOpinionHtml(
  data: Record<string, any>,
  options: AcquisitionExpertOpinionPrintOptions = {},
) {
  const acquisition: Record<string, any> = data.acquisition || {};
  const dossier: Record<string, any> = data.dossier || {};
  const completion: Record<string, any> = data.completion || {};
  const experts: Record<string, any>[] = Array.isArray(data.experts) ? data.experts : [];
  const includePrice = !!options.includePrice;
  const [dicts, categoryNames] = await Promise.all([
    loadDicts(['col_texture', 'col_completeness', 'col_source', 'col_relic_level']),
    acquisition.categoryName ? Promise.resolve({} as Record<string, string>) : loadCategoryNames(),
  ]);
  const { expertCount, agreedCount, vetoed } = summarizeAcquisitionExperts(data);
  const flowLoadFailed = !!options.flowLoadFailed;
  const decision = flowLoadFailed ? null : latestAppraiseDecision(options.flowHistory);
  const conclusion = flowLoadFailed ? '' : appraisalConclusionText(decision, vetoed);
  const summary: [string, string][] = [
    ['专家人数', `${expertCount}人`],
    ['同意征集人数', `${agreedCount}人`],
    ['是否有专家否决', vetoed ? '是' : '否'],
    ['否决日期', data.vetoOn ? printDateText(data.vetoOn) : '—'],
    ...(includePrice ? ([['专家估价均值', priceText(data.expertAveragePriceCents)]] as [string, string][]) : []),
  ];
  const committee: [string, string][] = [
    ['办理人', decision?.operatorName ? printPlainText(decision.operatorName) : ''],
    ['办理日期', decision?.createTime ? printDateText(decision.createTime) : ''],
    ['集体决策日期', completion.decidedOn ? printDateText(completion.decidedOn) : ''],
    ['定级建议', acquisition.proposedLevel ? printDictText(dicts, 'col_relic_level', acquisition.proposedLevel) : ''],
  ];
  return `
    <section class="expert-opinion-print">
      <h1>${ACQUISITION_EXPERT_OPINION_TITLE}</h1>
      <table><tbody>
        ${fieldRows([
          ['征集单号', printPlainText(acquisition.id)],
          ['藏品名称', printPlainText(acquisition.name)],
          ['分类', acquisitionCategoryText(acquisition, categoryNames)],
          ['年代', printPlainText(acquisition.era)],
          ['质地', printDictText(dicts, 'col_texture', acquisition.texture)],
          ['数量', acquisitionQuantityText(acquisition.quantity, acquisition.unit)],
          ['尺寸', acquisitionDimensionText(acquisition.dimensions)],
          ['完残', acquisitionCompletenessText(dicts, acquisition.completeness, acquisition.completeStatus)],
          ['征集方式', printDictText(dicts, 'col_source', acquisition.source)],
          [acquisitionHolderLabel(acquisition.source), printPlainText(acquisition.holder)],
          ['拟入藏性质', natureText(dossier.nature)],
          ['调查日期', printDateText(dossier.investigatedOn)],
          ['来源是否合法', printBooleanText(dossier.lawfulSource)],
        ])}
      </tbody></table>
      ${experts.map((expert, index) => expertSection(expert, index, includePrice)).join('')}
      <h2>结论汇总</h2>
      <table><tbody>${fieldRows(summary)}</tbody></table>
      <h2>鉴定委员会意见</h2>
      <table class="committee"><tbody>
        ${opinionRow(ACQUISITION_COMMITTEE_OPINION_LABEL, flowLoadFailed ? ACQUISITION_FLOW_LOAD_FAILED_TEXT : decision?.comment, true)}
        ${opinionRow('鉴定结论', conclusion, true)}
        ${fieldRows(committee)}
        <tr><th>负责人签字</th><td class="sign"></td><th>日期</th><td class="sign"></td></tr>
      </tbody></table>
    </section>
  `;
}

export function acquisitionExpertOpinionPrintTitle(data: Record<string, any>) {
  return acquisitionPrintTitle(`${ACQUISITION_EXPERT_OPINION_TITLE}_${printTitleName(data?.acquisition?.name)}`);
}

export async function printAcquisitionExpertOpinion(
  data: Record<string, any>,
  options: AcquisitionExpertOpinionPrintOptions = {},
) {
  const html = await buildAcquisitionExpertOpinionHtml(data, options);
  htmlPrint(
    html,
    acquisitionExpertOpinionPrintTitle(data),
    `
      @page { size: A4 portrait; margin: 14mm; }
      .expert-opinion-print { font-family: SimSun, "Songti SC", serif; color: #111; padding: 8px; }
      .expert-opinion-print h1 { text-align: center; font-size: 24px; margin: 0 0 14px; }
      .expert-opinion-print h2 { font-size: 15px; margin: 18px 0 8px; }
      .expert-opinion-print .expert, .expert-opinion-print .committee { page-break-inside: avoid; }
      .expert-opinion-print table { width: 100%; border-collapse: collapse; table-layout: fixed; }
      .expert-opinion-print th, .expert-opinion-print td { border: 1px solid #222; padding: 7px 9px; font-size: 12px; line-height: 1.5; word-break: break-word; white-space: pre-wrap; }
      .expert-opinion-print th { width: 16%; background: #f6f6f6; text-align: center; font-weight: normal; }
      .expert-opinion-print td { width: 34%; }
      .expert-opinion-print td.opinion { min-height: 60px; height: 60px; vertical-align: top; }
      .expert-opinion-print td.sign { height: 36px; }
    `,
  );
}
