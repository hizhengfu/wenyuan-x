/** 修复日志 / 阶段分工 / 结果评估 / 总结报告共用的纯函数（不发请求、不依赖 Vue）。 */
import type {
  RestorationAcceptanceVo, RestorationArchiveVo, RestorationContractVo, RestorationProcessVo, RestorationVo,
} from '../typings';

const DAY = 86400000;
const HOUR = 3600000;

function num(value: unknown): number | null {
  if (value == null || value === '') return null;
  const n = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(n) ? n : null;
}

function time(value: unknown): number | null {
  const n = num(value);
  if (n != null) return n;
  if (typeof value === 'string' && value.trim()) {
    const parsed = Date.parse(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

function startOfDay(ts: number) {
  const d = new Date(ts);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

/** 两个时间点相差的自然日（b - a），按本地日期取整。 */
export function dayDiff(a: unknown, b: unknown): number | null {
  const x = time(a), y = time(b);
  if (x == null || y == null) return null;
  return Math.round((startOfDay(y) - startOfDay(x)) / DAY);
}

/** 工序用时（小时）：优先取登记用时，缺失时按起止时间折算。 */
export function processHours(row: RestorationProcessVo): number | null {
  const d = num(row.duration);
  if (d != null && d >= 0) return d;
  const s = time(row.startTime), e = time(row.endTime);
  if (s == null || e == null || e < s) return null;
  return (e - s) / HOUR;
}

/** 工序用时合计；missing 为既无用时也无完整起止的工序数。 */
export function sumProcessHours(rows: RestorationProcessVo[] | null | undefined) {
  let total = 0, counted = 0, missing = 0;
  for (const row of rows || []) {
    const h = processHours(row);
    if (h == null) missing++;
    else { total += h; counted++; }
  }
  return { total: Math.round(total * 100) / 100, counted, missing };
}

/** 按时间先后排列工序（无开始时间的按工序号排在最后）。 */
export function sortProcessesByTime(rows: RestorationProcessVo[] | null | undefined): RestorationProcessVo[] {
  return [...(rows || [])].sort((a, b) => {
    const x = time(a.startTime), y = time(b.startTime);
    if (x != null && y != null && x !== y) return x - y;
    if (x == null && y != null) return 1;
    if (x != null && y == null) return -1;
    return (num(a.sort) ?? 0) - (num(b.sort) ?? 0);
  });
}

export function nextProcessSort(rows: RestorationProcessVo[] | null | undefined) {
  return (rows || []).reduce((max, row) => Math.max(max, num(row.sort) ?? 0), 0) + 1;
}

export interface MaterialItem { name: string; qty?: string; unit?: string; spec?: string }

export const UNNAMED_MATERIAL = '未命名用料';

/** 解析工序/档案用料：JSON 数组（name/qty/unit/spec）或自由文本。 */
export function parseMaterials(raw: unknown): MaterialItem[] {
  if (typeof raw !== 'string' || !raw.trim()) return [];
  const text = raw.trim();
  try {
    const value: unknown = JSON.parse(text);
    const list = Array.isArray(value) ? value : [value];
    const items: MaterialItem[] = [];
    for (const item of list) {
      if (item && typeof item === 'object') {
        const r = item as Record<string, unknown>;
        const filled = Object.values(r).some((v) => v != null && String(v).trim() !== '');
        if (!filled) continue;
        // 没有 name 的项不丢：依次取其他名称字段，仍缺时显示「未命名用料」。
        const name = [r.name, r.material, r.materialName, r.title, r.label]
          .map((v) => (v == null ? '' : String(v).trim())).find(Boolean) || UNNAMED_MATERIAL;
        items.push({
          name,
          qty: r.qty == null && r.quantity == null ? undefined : String(r.qty ?? r.quantity),
          unit: r.unit == null ? undefined : String(r.unit),
          spec: r.spec == null ? undefined : String(r.spec),
        });
      } else if (item != null && String(item).trim()) items.push({ name: String(item).trim() });
    }
    return items;
  } catch {
    // 自由文本：按换行、中文分号/逗号/顿号、英文分号切分；英文逗号仅在两侧不同时为数字时切分，保留「1,000ml」等千分位。
    return text.split(/[\r\n；;，、]|(?<!\d),|,(?!\d)/).map((s) => s.trim()).filter(Boolean).map((name) => ({ name }));
  }
}

export function materialText(item: MaterialItem) {
  return [item.name, item.spec, [item.qty, item.unit].filter(Boolean).join('')].filter(Boolean).join(' ');
}

export function materialsText(raw: unknown) {
  const items = parseMaterials(raw);
  return items.length ? items.map(materialText).join('；') : '';
}

/** 汇总多道工序的用料，同名同单位的数量相加（数量非数字时并列）。 */
export function mergeMaterials(raws: unknown[]): MaterialItem[] {
  const map = new Map<string, MaterialItem>();
  for (const raw of raws) {
    for (const item of parseMaterials(raw)) {
      const key = `${item.name}\u0000${item.spec || ''}\u0000${item.unit || ''}`;
      const old = map.get(key);
      if (!old) { map.set(key, { ...item }); continue; }
      const a = num(old.qty), b = num(item.qty);
      if (a != null && b != null) old.qty = String(Math.round((a + b) * 1000) / 1000);
      else if (item.qty) old.qty = [old.qty, item.qty].filter(Boolean).join('+');
    }
  }
  return [...map.values()];
}

/** 人员名单切分：只按逗号、分号、顿号、换行切，不按空格切（「Anna Li」等带空格的姓名保持完整）。 */
export function splitNames(value: unknown): string[] {
  return typeof value === 'string' ? value.split(/[,，;；、\r\n]+/).map((s) => s.trim()).filter(Boolean) : [];
}

export const UNKNOWN_PERSON = '未知人员';

/** key 为分组依据：有人员编号按编号，否则按完整姓名字符串；name 为展示名，缺姓名时为「未知人员」，不展示编号。 */
export interface PersonRef { key: string; name: string }

export function processPeople(p: RestorationProcessVo | null | undefined): { lead: PersonRef | null; assistants: PersonRef[] } {
  const leadId = String(p?.operatorId ?? '').trim(), leadName = String(p?.operatorName ?? '').trim();
  const lead = leadId || leadName ? { key: leadId ? `id:${leadId}` : `name:${leadName}`, name: leadName || UNKNOWN_PERSON } : null;
  const ids = splitNames(p?.assistantIds), names = splitNames(p?.assistantNames);
  let assistants: PersonRef[] = [];
  if (ids.length && (!names.length || names.length === ids.length)) {
    assistants = ids.map((id, i) => ({ key: `id:${id}`, name: names[i] || UNKNOWN_PERSON }));
  } else if (names.length) {
    assistants = names.map((name) => ({ key: `name:${name}`, name }));
  }
  return { lead, assistants };
}

export function personNames(refs: (PersonRef | null | undefined)[]): string {
  return refs.filter((r): r is PersonRef => !!r).map((r) => r.name).join('、');
}

export interface DivisionRow { key: string; name: string; leadStages: string[]; assistStages: string[]; hours: number }

/**
 * 人员分工：主操作人计入用时，协助人员只列参与工序。
 * 分组：有人员编号按编号；只有姓名、没有编号的按完整姓名字符串单独成行，不并入同名带编号的人员。
 * 缺姓名：同一编号在别处有姓名则用该姓名；始终无姓名的不同人员按编号区分，出现 2 人及以上时依首次出现顺序
 * 显示为「未知人员1」「未知人员2」…（只有 1 人时仍为「未知人员」），不展示人员编号。
 */
export function buildDivision(rows: RestorationProcessVo[] | null | undefined): DivisionRow[] {
  const list = sortProcessesByTime(rows);
  const map = new Map<string, DivisionRow>();
  const get = (ref: PersonRef) => {
    let row = map.get(ref.key);
    if (!row) { row = { key: ref.key, name: ref.name, leadStages: [], assistStages: [], hours: 0 }; map.set(ref.key, row); }
    else if (row.name === UNKNOWN_PERSON && ref.name !== UNKNOWN_PERSON) row.name = ref.name;
    return row;
  };
  for (const p of list) {
    const stage = p.stageName || `工序 ${p.sort ?? ''}`.trim();
    const { lead, assistants } = processPeople(p);
    if (lead) {
      const row = get(lead);
      row.leadStages.push(stage);
      row.hours = Math.round((row.hours + (processHours(p) ?? 0)) * 100) / 100;
    }
    for (const ref of assistants) {
      if (ref.key !== lead?.key) get(ref).assistStages.push(stage);
    }
  }
  const result = [...map.values()];
  const unknown = result.filter((row) => row.name === UNKNOWN_PERSON);
  if (unknown.length > 1) unknown.forEach((row, i) => { row.name = `${UNKNOWN_PERSON}${i + 1}`; });
  return result;
}

/**
 * 实际起止：仅在已开工（state 为 ONGOING / DONE）后，才把项目 startDate / endDate 当作实际工期。
 * - 草稿等未开工状态里填的开始/完成日期是计划，不能冒充实际工期（显示「未开工」）；
 * - 项目未竣工（无竣工日期）时实际完工一律为空，按「开工日 ~ 进行中」展示，不用工序结束时间代替；
 * - 仅当项目既无开工也无竣工日期、且状态已竣工（DONE）时，才用工序推断：
 *   开工取工序最早开始，完工取工序最晚结束（须全部工序均已结束，否则仍为空）。
 */
export function actualPeriod(project: RestorationVo | null | undefined, rows: RestorationProcessVo[] | null | undefined) {
  const list = rows || [];
  const started = project?.state === 'ONGOING' || project?.state === 'DONE';
  const ps = started ? time(project?.startDate) : null;
  const pe = started ? time(project?.endDate) : null;
  const infer = started && ps == null && pe == null && project?.state === 'DONE';
  const starts = list.map((r) => time(r.startTime)).filter((v): v is number => v != null);
  const ends = list.map((r) => time(r.endTime)).filter((v): v is number => v != null);
  const inferredStart = infer && starts.length ? Math.min(...starts) : null;
  const inferredEnd = infer && list.length && ends.length === list.length ? Math.max(...ends) : null;
  const start = ps ?? inferredStart, end = pe ?? inferredEnd;
  return {
    start,
    end,
    startSource: ps != null ? 'project' : inferredStart != null ? 'process' : null,
    endSource: pe != null ? 'project' : inferredEnd != null ? 'process' : null,
    // 已开工但尚未竣工才算「进行中」；未开工不要写成进行中。
    ongoing: started && end == null,
  } as { start: number | null; end: number | null; startSource: 'project' | 'process' | null; endSource: 'project' | 'process' | null; ongoing: boolean };
}

/** 实际工期文字：未开工显示「未开工」；已开工未竣工显示「开工日 ~ 进行中」。 */
export function actualPeriodText(
  period: { start: number | null; end: number | null; ongoing?: boolean },
  formatDate: (v: unknown) => string,
) {
  if (period.start == null && period.end == null) return period.ongoing ? '-- ~ 进行中' : '未开工';
  return `${period.start == null ? '--' : formatDate(period.start)} ~ ${period.end == null ? '进行中' : formatDate(period.end)}`;
}

/** 计划工期与实际工期偏差（天）。正数表示晚于/长于计划。 */
export function periodDeviation(
  plannedStart: unknown, plannedEnd: unknown, actualStart: unknown, actualEnd: unknown,
) {
  const plannedDays = dayDiff(plannedStart, plannedEnd);
  const actualDays = dayDiff(actualStart, actualEnd);
  return {
    plannedDays: plannedDays == null ? null : plannedDays + 1,
    actualDays: actualDays == null ? null : actualDays + 1,
    startDelay: dayDiff(plannedStart, actualStart),
    endDelay: dayDiff(plannedEnd, actualEnd),
    durationDelta: plannedDays == null || actualDays == null ? null : actualDays - plannedDays,
  };
}

export function deviationText(days: number | null | undefined, late = '晚', early = '早') {
  if (days == null) return '--';
  if (days === 0) return '一致';
  return days > 0 ? `${late} ${days} 天` : `${early} ${-days} 天`;
}

export function fenToYuan(value: unknown): string {
  const n = num(value);
  return n == null ? '--' : `¥${(n / 100).toFixed(2)}`;
}

export function hoursText(value: unknown): string {
  const n = num(value);
  return n == null ? '--' : `${(Math.round(n * 100) / 100).toFixed(2)} 小时`;
}

export function escapeHtml(value: unknown): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

export const conclusionLabels: Record<string, string> = { PASS: '通过', REVISION: '需整改复验', REJECT: '不通过' };

export interface ProcessSummary { processCount?: number; totalHours?: number; stages?: { sort?: number; stage?: string; duration?: number | null }[]; materialsRaw?: string[] }

export interface SummaryReportInput {
  project: RestorationVo;
  processes: RestorationProcessVo[];
  summary?: ProcessSummary | null;
  archive?: RestorationArchiveVo | null;
  acceptance?: RestorationAcceptanceVo | null;
  contract?: RestorationContractVo | null;
  formatDate: (v: unknown) => string;
  formatDateTime: (v: unknown) => string;
  stateText?: string;
}

/**
 * 总结报告打印标题（未转义的原始文本）。
 * 必须交给 PrintUtils.htmlPrint：其中 printDocumentTitle 统一整理并只做一次 HTML 转义，此处不要再转义，避免双重转义。
 */
export function summaryReportTitle(project: RestorationVo | null | undefined) {
  const name = project?.collectionName || project?.collectionTotalRegNo || '文物';
  return `${name}修复项目总结报告`;
}

/** 生成总结报告 HTML 正文；所有业务数据在此转义，标题由 htmlPrint 统一处理。 */
export function buildSummaryReportHtml(input: SummaryReportInput): string {
  const { project: p, processes, summary, archive, acceptance, contract, formatDate, formatDateTime } = input;
  const e = escapeHtml;
  const v = (value: unknown) => e(value == null || value === '' ? '--' : value);
  const row = (cells: [string, unknown][]) => `<tr>${cells.map(([k, val]) => `<th>${e(k)}</th><td>${v(val)}</td>`).join('')}</tr>`;
  const wide = (k: string, val: unknown) => `<tr><th>${e(k)}</th><td colspan="3" class="pre">${v(val)}</td></tr>`;
  const local = sumProcessHours(processes);
  const period = actualPeriod(p, processes);
  const dev = contract ? periodDeviation(contract.plannedStart, contract.plannedEnd, period.start, period.end) : null;
  const materials = mergeMaterials(summary?.materialsRaw?.length ? summary.materialsRaw : processes.map((r) => r.materials));
  const stages = sortProcessesByTime(processes);
  const name = `${p.collectionTotalRegNo || ''} ${p.collectionName || ''}`.trim();
  const parts: string[] = [];
  parts.push(`<h2 class="text-center">${e(name || '文物')}修复项目总结报告</h2>`);
  parts.push('<h3>一、项目概况</h3><table class="report">');
  parts.push(row([['藏品', name], ['修复机构', p.restoreOrg]]));
  parts.push(row([['经办人', p.operatorName || p.operator], ['复核人', p.reviewerName || p.reviewer]]));
  const startEndText = period.start == null && period.end == null
    ? (period.ongoing ? '-- ~ 未竣工' : '未开工')
    : `${period.start == null ? '--' : formatDate(period.start)} ~ ${period.end == null ? '未竣工' : formatDate(period.end)}`;
  parts.push(row([['项目状态', input.stateText || p.state], ['开工 / 竣工', startEndText]]));
  if (contract && dev) {
    parts.push(row([['合同编号', contract.contractNo], ['合同金额', fenToYuan(contract.contractAmount)]]));
    parts.push(row([['计划工期', `${formatDate(contract.plannedStart)} ~ ${formatDate(contract.plannedEnd)}（${dev.plannedDays ?? '--'} 天）`], ['实际工期', `${actualPeriodText(period, formatDate)}（${dev.actualDays ?? '--'} 天）`]]));
    parts.push(row([['开工偏差', deviationText(dev.startDelay)], ['完工偏差', deviationText(dev.endDelay)]]));
    parts.push(wide('工期长度偏差', deviationText(dev.durationDelta, '超出', '缩短')));
  }
  parts.push(wide('修复方案', p.plan));
  parts.push('</table>');
  parts.push('<h3>二、修复过程</h3><table class="report list"><thead><tr><th>序号</th><th>工序</th><th>方法</th><th>主操作人</th><th>协助人员</th><th>起止时间</th><th>用时(小时)</th></tr></thead><tbody>');
  if (!stages.length) parts.push('<tr><td colspan="7">--</td></tr>');
  stages.forEach((r, i) => {
    const h = processHours(r), people = processPeople(r);
    parts.push(`<tr><td>${i + 1}</td><td>${v(r.stageName)}</td><td>${v(r.methodName)}</td><td>${v(people.lead?.name)}</td><td>${v(personNames(people.assistants))}</td><td>${e(formatDateTime(r.startTime))} ~ ${e(formatDateTime(r.endTime))}</td><td>${h == null ? '--' : h.toFixed(2)}</td></tr>`);
  });
  parts.push('</tbody></table>');
  // 用时口径与「结果评估」一致：统一用前端 sumProcessHours 按本页工序列表合计；服务端汇总不同时只并列注明来源，不替换。
  const serverTotal = num(summary?.totalHours);
  const serverNote = serverTotal != null && Math.abs(serverTotal - local.total) >= 0.01 ? `；服务端工序汇总为 ${hoursText(serverTotal)}，与本页口径不同，仅供参考` : '';
  parts.push(`<p>工序 ${e(processes.length)} 道，用时合计 ${e(hoursText(local.total))}（按本页工序列表计算：优先登记用时，缺失按起止折算${local.missing ? `，${e(local.missing)} 道未登记用时` : ''}${e(serverNote)}）。</p>`);
  parts.push('<h3>三、用料汇总</h3>');
  parts.push(`<p class="pre">${materials.length ? materials.map((m) => e(materialText(m))).join('；') : v(materialsText(archive?.materialsUsed) || archive?.materialsUsed)}</p>`);
  parts.push('<h3>四、修复前后现状</h3><table class="report">');
  parts.push(wide('修复前现状', archive?.beforeDescription));
  parts.push(wide('修复后现状', archive?.afterDescription));
  parts.push(row([['档案编号', archive?.archiveNo], ['归档时间', archive?.approvedTime ? formatDateTime(archive.approvedTime) : '未归档']]));
  parts.push('</table>');
  parts.push('<h3>五、验收结论</h3><table class="report">');
  parts.push(row([['验收类型', acceptance?.acceptanceType], ['验收结论', acceptance?.conclusion ? conclusionLabels[acceptance.conclusion] || acceptance.conclusion : '--']]));
  parts.push(row([['验收专家', acceptance?.experts], ['验收时间', acceptance?.acceptedTime ? formatDateTime(acceptance.acceptedTime) : '--']]));
  parts.push(wide('验收意见', acceptance?.opinion));
  parts.push('</table>');
  return parts.join('');
}

export const summaryReportStyle = `
.report{width:100%;border-collapse:collapse;margin:6px 0 12px;font-size:12px}
.report th,.report td{border:1px solid #333;padding:4px 6px;vertical-align:top}
.report th{background:#f5f5f5;font-weight:normal;white-space:nowrap;width:12%}
.report.list th{width:auto}
.pre{white-space:pre-wrap}
h2{margin:0 0 12px}h3{font-size:14px;margin:12px 0 4px}
`;

/** 项目状态文字：停止状态优先，其余使用字典标签。 */
export function projectStateText(project: RestorationVo | null | undefined, dictLabel?: string) {
  if (!project) return '--';
  if (project.actualStoppedAt != null) return '开工后已停止';
  if (project.sentStoppedAt != null) return '未开工已停止';
  return dictLabel && dictLabel !== '--' ? dictLabel : project.state || '--';
}

/** 工序进度：已结束道数 / 总道数。 */
export function processProgress(rows: RestorationProcessVo[] | null | undefined) {
  const list = rows || [];
  const done = list.filter((r) => time(r.endTime) != null).length;
  return { total: list.length, done, percent: list.length ? Math.round((done / list.length) * 100) : 0 };
}
