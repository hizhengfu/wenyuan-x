import type { EvidenceField } from './evidenceFields';
import { incidentWorkFields } from './incidentWorkFields';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
});
const people = (key: string, label: string): EvidenceField => ({ ...f(key, label, 'user'), multiple: true });
const flag = (key: string, label: string): EvidenceField => ({
  ...f(key, label, 'select'),
  options: [
    { value: 'YES', label: '是' },
    { value: 'NO', label: '否' },
  ],
});
const place = (key: string, label: string): EvidenceField => f(key, label, 'select');
const departure: EvidenceField[] = [
  f('departedAt', '实际搬出时间', 'datetime'),
  place('fromLocationId', '实际搬出地点'),
  people('releasingUserIds', '实际交出和复核人员'),
  f('transportSupervisorUserId', '实际运输监督人员', 'user'),
  flag('physicallyPresent', '已核对实物实际在场并点交搬出'),
  flag('routeSafe', '搬出时路线及运输条件安全'),
  f('condition', '实际搬出前现状', 'textarea'),
  f('actualPackaging', '实际包装及支撑', 'textarea'),
  f('appliedTransportStandardReference', '实际采用包装运输标准及版本'),
  flag('packingStandardConfirmed', '逐件包装按批准方案及适用标准核对'),
  f('packingCheckFileIds', '逐件包装运输核验签署原件', 'file'),
  f('accessRecordFileIds', '实际人员进出及陪同登记原件', 'file'),
  f('signedFileIds', '实际搬出点交签署原件', 'file'),
];
export const incidentRelocationFields: Record<string, EvidenceField[]> = {
  plan: [
    f('preparedAt', '实际方案形成时间', 'datetime'),
    f('plannedDeparture', '计划搬出时间', 'datetime'),
    f('plannedArrival', '最迟接收时间', 'datetime'),
    place('fromLocationId', '实际来源地点'),
    place('toLocationId', '拟接收地点'),
    f('necessity', '原地措施不足及搬移必要性', 'textarea'),
    f('conditionBefore', '实物当前保存状况', 'textarea'),
    f('handlingMethod', '依据质地现状的取放方法', 'textarea'),
    f('packingMethod', '包装支撑方法', 'textarea'),
    f('transportStandardReference', '适用包装运输标准及现行版本'),
    f('transportStandardApplicability', '本件标准适用范围与核对依据', 'textarea'),
    f('route', '具体运输路线', 'textarea'),
    f('safetyRequirements', '现场及全程安全要求', 'textarea'),
    f('stopConditions', '停止搬移条件', 'textarea'),
    f('destinationCheckedAt', '目的地实际安全检查时间', 'datetime'),
    flag('destinationSafe', '已核实目的地具备安全保管条件'),
    f('departmentId', '保护修复责任部门', 'group'),
    f('conservationUserId', '保护修复责任人员', 'user'),
    f('departmentHeadUserId', '该部门负责人', 'user'),
    f('keeperUserId', '保管部门负责人', 'user'),
    f('leaderUserId', '法定代表人或主要负责人', 'user'),
    f('fromCustodianUserId', '原地点实际保管员', 'user'),
    f('toCustodianUserId', '目的地实际保管员', 'user'),
    f('securityUserId', '现场安全责任人员', 'user'),
    f('carrierName', '实际运输承担单位'),
    f('transportSupervisorUserId', '运输监督人员', 'user'),
    f('sourceLocationFileIds', '实际来源保管及场所依据', 'file'),
    f('destinationSafetyFileIds', '目的地安全条件及指派原件', 'file'),
    f('carrierCapabilityFileIds', '运输资信能力及指派原件', 'file'),
    f('signedPlanFileIds', '完整搬移方案原件', 'file'),
  ],
  emergency: [
    f('reason', '紧急先行搬移的真实原因', 'textarea'),
    f('actualInstruction', '实际指挥、经过及当时手续情况', 'textarea'),
    f('instructionFileIds', '指挥及实际经过原件', 'file'),
    f('conservationUserId', '本次事实复核保护修复人员', 'user'),
    f('keeperUserId', '本次事实复核保管负责人', 'user'),
    ...departure.map((x) =>
      x.key === 'releasingUserIds'
        ? f(x.key, '当时实际交出人员标识或姓名（逐行；未知可留空）', 'textarea', false)
        : x.key === 'transportSupervisorUserId'
          ? f(x.key, '当时实际运输监督人员（未知可留空）', 'text', false)
          : ['appliedTransportStandardReference', 'packingStandardConfirmed', 'packingCheckFileIds'].includes(x.key)
            ? { ...x, required: false, label: `当时${x.label}（如实记录，未知可留空）` }
          : x,
    ),
  ],
  depart: departure,
  receive: [
    f('receivedAt', '实际接收时间', 'datetime'),
    place('toLocationId', '实际接收地点'),
    { ...people('receivingUserIds', '实际接收和复核人员（未知可留空）'), required: false },
    f('custodianUserId', '实际接收保管人（未明确可留空）', 'user', false),
    flag('physicallyPresent', '已核对本件实物实际接收到场'),
    flag('authenticityConfirmed', '真实性已核实'),
    flag('conditionMatchesDeparture', '保存现状与搬出一致'),
    flag('unexpectedDamage', '是否发现额外损伤'),
    flag('safeForCustody', '当前具备安全保管条件'),
    f('condition', '接收时真实现状', 'textarea'),
    f('actualMeasures', '实际已采取措施', 'textarea'),
    f('followUp', '后续核查保管及报告安排', 'textarea'),
    f('accessRecordFileIds', '实际人员进出及陪同登记原件', 'file'),
    f('signedFileIds', '实际接收点交原件', 'file'),
  ],
  observation: incidentWorkFields.observation,
  confirm: incidentWorkFields.confirm,
  cancel: incidentWorkFields.cancel,
};
export const incidentRelocationStates: Record<string, string> = {
  PLAN: '方案待本人确认',
  APPROVED: '已批准，尚未实际搬出',
  DEPARTED: '已实际搬出，待接收',
  RECEIVED: '已实际接收，待共同核对',
  COMPLETED: '本次实际搬移已共同核对',
  CANCELED: '未实施方案已取消',
};
const flags = new Set([
  'physicallyPresent',
  'routeSafe',
  'packingStandardConfirmed',
  'destinationSafe',
  'authenticityConfirmed',
  'conditionMatchesDeparture',
  'unexpectedDamage',
  'safeForCustody',
  'unsafe',
]);
export function incidentRelocationValues(action: string, value: Record<string, any>) {
  const v = Object.fromEntries(
    Object.entries(value).map(([k, x]) => [k, flags.has(k) ? (x === 'YES' ? true : x === 'NO' ? false : x) : x]),
  );
  if (action === 'emergency') {
    const keys = new Set(departure.map((f) => f.key));
    const d = Object.fromEntries(Object.entries(v).filter(([k]) => keys.has(k)));
    d.releasingUserIds =
      typeof d.releasingUserIds === 'string'
        ? [
            ...new Set(
              d.releasingUserIds
                .split(/[,\n]/)
                .map((x: string) => x.trim())
                .filter(Boolean),
            ),
          ]
        : d.releasingUserIds || [];
    d.transportSupervisorUserId ??= '';
    return { ...Object.fromEntries(Object.entries(v).filter(([k]) => !keys.has(k))), departure: d };
  }
  if (action === 'receive') {
    v.receivingUserIds =
      typeof v.receivingUserIds === 'string'
        ? [
            ...new Set(
              v.receivingUserIds
                .split(/[,\n]/)
                .map((x: string) => x.trim())
                .filter(Boolean),
            ),
          ]
        : v.receivingUserIds || [];
    v.custodianUserId ??= '';
  }
  return v;
}
