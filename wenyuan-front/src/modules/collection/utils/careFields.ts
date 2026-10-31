import type { EvidenceField } from './evidenceFields';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
});
const yes = (key: string, label: string): EvidenceField => ({
  ...f(key, label, 'select'),
  options: [
    { label: '是', value: 'YES' },
    { label: '否', value: 'NO' },
  ],
});
const methods = (key: string): EvidenceField => ({
  ...f(key, '养护方法', 'select'),
  multiple: true,
  options: [
    { value: 'DUST_REMOVAL', label: '除尘' },
    { value: 'PEST_CONTROL', label: '杀虫' },
    { value: 'DISINFECTION', label: '灭菌' },
    { value: 'SUPPORT', label: '支撑' },
    { value: 'PACKAGING', label: '包装' },
    { value: 'FIXING', label: '固定' },
  ],
});
const custody = [
  yes('physicallyPresent', '是否实际在场'),
  f('actualLocation', '实际保管地点'),
  f('custodianUserId', '实际保管人员（未知可留空）', 'user', false),
];
export const careFields: Record<string, EvidenceField[]> = {
  plan: [
    f('preparedAt', '实际方案制定时间', 'datetime'),
    f('conservationUserId', '保护修复责任人员', 'user'),
    f('keeperUserId', '保管部门负责人', 'user'),
    f('inspectionIntervalDays', '检查周期（天，依实际评估填写）', 'number'),
    f('careIntervalDays', '养护周期（天，依实际评估填写）', 'number'),
    methods('methods'),
    f('instructions', '分项方法、材料剂量和操作要求', 'textarea'),
    f('environmentRequirements', '保存环境要求及适用监测策略', 'textarea'),
    f('storageProtection', '科学存放及支撑包装要求', 'textarea'),
    f('safetyInstructions', '风险、防护与停止条件', 'textarea'),
    f('planFileIds', '实际评估及方案原件', 'file'),
  ],
  sign: [f('opinion', '本人核对意见', 'textarea'), f('fileIds', '本人确认原件', 'file')],
  check: [
    f('checkedAt', '实际检查时间', 'datetime'),
    f('operatorUserId', '实际检查人员（本人）', 'user'),
    ...custody,
    f('condition', '实际保存状况', 'textarea'),
    f('diseases', '实际病害及变化，无病害也应明确记录', 'textarea'),
    f('environmentObservation', '实际环境检查及监测数据依据', 'textarea'),
    yes('damaged', '是否发现损伤'),
    yes('safeForRoutineCare', '是否已核实适合普通养护'),
    f('actualMeasures', '已采取措施', 'textarea'),
    f('followUp', '后续安排', 'textarea'),
    f('conditionFileIds', '现状和检查原件', 'file'),
    f('signedFileIds', '实际检查签署原件', 'file'),
  ],
  start: [
    f('startedAt', '实际开始时间', 'datetime'),
    f('operatorUserId', '实际操作人员（本人）', 'user'),
    methods('methods'),
    f('requisitionId', '已实际点交的本件养护提用单', 'text', false),
    f('onSiteWarehouseLogId', '在库办理的当日实际库房日记（二选一）', 'text', false),
    f('actualLocation', '实际操作地点'),
    f('safetyChecks', '本次原件、环境和操作安全核对', 'textarea'),
    f('signedFileIds', '实际开始签署原件', 'file'),
  ],
  end: [
    f('endedAt', '实际完成或停止时间', 'datetime'),
    yes('completed', '是否全部正常完成'),
    methods('actualMethods'),
    f('actualWork', '实际实施方法、材料剂量、范围及停止原因', 'textarea'),
    f('condition', '实际结束后保存状况', 'textarea'),
    f('diseases', '实际结束后病害及变化', 'textarea'),
    yes('unexpectedDamage', '是否发生意外损伤'),
    yes('safeToRelease', '是否已核实可以安全结束本次占用'),
    ...custody,
    f('actualMeasures', '实际防护措施', 'textarea'),
    f('followUp', '后续检查养护安排', 'textarea'),
    f('conditionFileIds', '实际现状原件', 'file'),
    f('signedFileIds', '实际完成或停止签署原件', 'file'),
  ],
  clear: [
    f('incidentId', '已实际确认处理的事故案卷编号'),
    f('confirmedAt', '实际核对时间', 'datetime'),
    f('conservationUserId', '保护修复责任人员', 'user'),
    f('keeperUserId', '保管部门负责人（本人）', 'user'),
    f('condition', '处置后实际现状', 'textarea'),
    f('actualMeasures', '实际保护措施', 'textarea'),
    f('signedFileIds', '共同核对原件', 'file'),
  ],
};
careFields.end.find((f) => f.key === 'actualMethods')!.required = false;
export const careTitles: Record<string, string> = {
  plan: '制定检查养护方案',
  sign: '本人确认方案',
  check: '记录实际检查',
  start: '记录实际养护开始',
  end: '记录实际完成或停止',
  clear: '依据事故处置核对异常',
};
const booleans = new Set([
  'physicallyPresent',
  'damaged',
  'safeForRoutineCare',
  'completed',
  'unexpectedDamage',
  'safeToRelease',
]);
export function careValues(v: Record<string, any>) {
  return Object.fromEntries(
    Object.entries(v).map(([key, value]) => [
      key,
      booleans.has(key) && ['YES', 'NO'].includes(value) ? value === 'YES' : value,
    ]),
  );
}
