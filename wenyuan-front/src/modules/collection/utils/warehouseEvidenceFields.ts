import type { EvidenceField } from './evidenceFields';
import { loanYesNo } from './loanEvidenceFields';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
});
const flag = (key: string, label: string): EvidenceField => ({ ...f(key, label, 'select'), options: loanYesNo });
export const warehouseEntryKinds = [
  { label: '两名以上实际库管共同入库', value: 'KEEPERS' },
  { label: '非库管人员由两名库管陪同', value: 'VISITORS' },
  { label: '库管不足两人，经主要负责人指定陪同', value: 'STAFF_SHORTAGE' },
];
export const warehouseStaffingFields = [
  { ...f('keeperUserIds', '本库实际管理人员', 'user'), multiple: true },
  f('keeperUserId', '保管部门负责人', 'user'),
  f('leaderUserId', '法定代表人或主要负责人', 'user'),
  f('assignedAt', '任职实际签署时间', 'datetime'),
  f('signedAssignmentFileIds', '岗位责任及任职签署原件', 'file'),
  f('replacementReason', '任职调整或更正原因', 'textarea', false),
];
export const warehouseEntryBaseFields = [
  { ...f('entrantIds', '本馆入库人员', 'user', false), multiple: true },
  f('externalEntrants', '外来人员（姓名及单位）', 'textarea', false),
  f('purpose', '入库事由及工作内容', 'textarea'),
  f('plannedStart', '申请进入时间', 'datetime'),
  f('plannedEnd', '申请离开时间', 'datetime'),
];
export function warehouseEntryFields(value: any = {}): EvidenceField[] {
  return [
    { ...f('kind', '入库人员类别', 'select'), options: warehouseEntryKinds },
    f('keeperUserId', '本库保管部门负责人', 'user'),
    ...(value.kind !== 'KEEPERS'
      ? [
          {
            ...f(
              'escortUserIds',
              value.kind === 'VISITORS' ? '实际陪同库管（至少两人）' : '拟由主要负责人指定的馆内陪同人员',
              'user',
            ),
            multiple: true,
          },
        ]
      : []),
    ...(value.kind === 'VISITORS' ? [f('externalNames', '外来人员真实姓名及单位（每行一人）', 'textarea', false)] : []),
    f('identityFileIds', '全部人员身份核验依据', 'file'),
    ...(value.kind === 'STAFF_SHORTAGE'
      ? [
          f('leaderUserId', '实际作出指定的主要负责人', 'user'),
          f('shortageReason', '本库库管不足两人的真实情况', 'textarea'),
          f('shortageFileIds', '人员不足情况依据', 'file'),
        ]
      : []),
  ];
}
export function warehouseAttendanceFields(value: any = {}, exit = false): EvidenceField[] {
  return [
    f('occurredAt', exit ? '实际离库时间' : '实际入库时间', 'datetime'),
    { ...f('entrantUserIds', '实际到场的本馆入库人员', 'user', false), multiple: true },
    { ...f('escortUserIds', '实际到场的全部陪同人员', 'user', false), multiple: true },
    f('externalNames', '实际到场的外来人员（每行一人）', 'textarea', false),
    flag('identityChecked', '已逐人核实真实身份'),
    flag('continuousAccompaniment', exit ? '是否始终共同在库或全程陪同' : '是否全部共同到场'),
    f('signedRecordFileIds', '实际签署的人员出入库登记表', 'file'),
    f('remark', '现场核验或异常说明', 'textarea', false),
    ...(exit && (value.continuousAccompaniment === 'NO' || value.continuousAccompaniment === false)
      ? [
          f('exceptionMeasures', '陪同中断后的即时措施', 'textarea'),
          f('exceptionReportFileIds', '异常报告原件', 'file'),
        ]
      : []),
  ];
}
export function warehouseDiaryFields(value: any = {}): EvidenceField[] {
  return [
    f('inspectedAt', '当日实际巡库时间', 'datetime'),
    { ...f('inspectorUserIds', '实际巡库保管人员', 'user'), multiple: true },
    flag('entryRecordsComplete', '已完整核对当日人员进出记录'),
    f('collectionDynamics', '当日藏品动态及保存情况', 'textarea'),
    f('securitySituation', '库内安防、消防及其他安全情况', 'textarea'),
    flag('hasAnomaly', '是否发现异常'),
    ...(value.hasAnomaly === 'YES' || value.hasAnomaly === true
      ? [
          f('anomalyDescription', '发现的具体异常', 'textarea'),
          f('handlingMeasures', '已采取的有效措施', 'textarea'),
          f('keeperUserId', '实际报告的保管部门负责人', 'user'),
          f('keeperReportedAt', '实际报告保管负责人时间', 'datetime'),
          f('keeperReportFileIds', '保管负责人报告原件', 'file'),
          flag('major', '是否为重大异常'),
          f('significanceReason', '重大情况判断依据', 'textarea'),
          ...(value.major === 'YES' || value.major === true
            ? [
                f('leaderUserId', '实际报告的主要负责人', 'user'),
                f('leaderReportedAt', '实际报告主要负责人时间', 'datetime'),
                f('leaderReportFileIds', '主要负责人报告原件', 'file'),
              ]
            : []),
          flag('resolved', '是否处理完毕'),
          f('followUpAt', '后续核查时间', 'datetime', value.resolved !== 'YES' && value.resolved !== true),
        ]
      : []),
    f('signedDiaryFileIds', '实际巡库人员签署的日记原件', 'file'),
    f('replacementReason', '日记更正或补记原因', 'textarea', false),
  ];
}
export const warehouseReadingFields = [
  f('measuredAt', '实际测量时间', 'datetime'),
  f('temperature', '实测温度（℃，可为0或负数）', 'number'),
  f('humidity', '实测相对湿度（%）', 'number'),
  f('sourceDescription', '设备或人工测量来源', 'textarea'),
  f('evidenceFileIds', '实际测量记录原件', 'file'),
];
const flags = ['identityChecked', 'continuousAccompaniment', 'entryRecordsComplete', 'hasAnomaly', 'major', 'resolved'];
export function warehouseEvidenceInitial(value: any = {}): any {
  const out = { ...value };
  for (const key of flags) if (typeof out[key] === 'boolean') out[key] = out[key] ? 'YES' : 'NO';
  if (Array.isArray(out.externalNames)) out.externalNames = out.externalNames.join('\n');
  if (out.diary) out.diary = warehouseEvidenceInitial(out.diary);
  if (out.dossier) out.dossier = warehouseEvidenceInitial(out.dossier);
  return out;
}
export function warehouseEvidencePayload(value: any): any {
  const out = { ...value };
  for (const key of flags) if (out[key] === 'YES' || out[key] === 'NO') out[key] = out[key] === 'YES';
  if (typeof out.externalNames === 'string')
    out.externalNames = [
      ...new Set(
        out.externalNames
          .split(/\n/)
          .map((v: string) => v.trim())
          .filter(Boolean),
      ),
    ];
  if (out.diary) out.diary = warehouseEvidencePayload(out.diary);
  if (out.dossier) out.dossier = warehouseEvidencePayload(out.dossier);
  return out;
}
export function warehouseFieldError(fields: EvidenceField[], value: any) {
  const missing = fields.find(
    (f) =>
      f.required &&
      (value[f.key] == null || value[f.key] === '' || (Array.isArray(value[f.key]) && !value[f.key].length)),
  );
  return missing ? `请填写${missing.label}` : undefined;
}
export function warehouseVisitStatus(v: any) {
  return !v.entry
    ? '旧进出记录，原件待核实'
    : v.exit?.continuousAccompaniment === false
      ? '陪同中断，须报告处置'
      : v.exitedAt
        ? '已离库'
        : '尚未登记离库';
}
