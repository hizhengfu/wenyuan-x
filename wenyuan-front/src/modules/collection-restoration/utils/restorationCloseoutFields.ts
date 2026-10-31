import type { EvidenceField } from '@/modules/collection/utils/evidenceFields';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
});
const levels = [
  { value: 'COMPETENT', label: '主管文物行政部门' },
  { value: 'PROVINCE', label: '省级文物行政部门' },
  { value: 'NATIONAL', label: '国务院文物行政部门' },
];
export function restorationAcceptanceFields(first = false, revision = false): EvidenceField[] {
  return [
    f('acceptedAt', '实际验收时间', 'datetime'),
    {
      ...f('acceptanceType', '实际验收类型', 'select'),
      options: [
        { value: 'INTERNAL', label: '馆内验收' },
        { value: 'EXPERT_PANEL', label: '专家组评审' },
        { value: 'SUPERIOR', label: '行政部门组织验收' },
      ],
    },
    { ...f('organizerLevel', '实际组织验收机关层级', 'select', first), options: levels },
    f('organizerName', '组织验收单位或机关全称'),
    f('organizationFileIds', '实际组织验收依据', 'file'),
    f('participants', '实际验收人员、单位及职责', 'textarea'),
    f('participantFileIds', '实际参加人员签署原件', 'file'),
    {
      ...f('conclusion', '实际验收结论', 'select'),
      options: [
        { value: 'PASS', label: '通过' },
        { value: 'REVISION', label: '需整改复验' },
        { value: 'REJECT', label: '不通过' },
      ],
    },
    f('opinion', '验收意见及遗留问题', 'textarea'),
    f('reportFileId', '本次签署验收报告', 'file'),
    f('conditionAssessment', '修复效果及保存状况评估', 'textarea'),
    f('preventiveMeasures', '后续预防保护要求', 'textarea'),
    f('followUpIntervalDays', '后续检查间隔（天）', 'number'),
    ...(revision
      ? [
          f('revisionReason', '前次问题处置、复验或订正原因', 'textarea'),
          f('revisionFileIds', '本次处置、复验或订正原件', 'file'),
        ]
      : [
          f('lateReason', '超过竣工后三个月的实际迟办原因', 'textarea', false),
          f('lateFileIds', '迟办报告或处理依据', 'file', false),
        ]),
  ];
}
export const restorationAcceptanceFilingFields: EvidenceField[] = [
  { ...f('level', '实际备案机关层级', 'select'), options: levels },
  f('authorityName', '实际备案机关全称'),
  f('documentNo', '实际备案文号或回执编号'),
  f('issuedAt', '实际备案文书日期', 'date'),
  f('fileIds', '本次验收结果备案原件', 'file'),
];
export const restorationArchiveFields: EvidenceField[] = [
  f('archivedAt', '实际归档时间', 'datetime'),
  f('archivistUserId', '实际归档人员', 'user'),
  f('collectionArchiveReference', '归入本件藏品档案的卷号及索引'),
  f('archivalLocation', '纸质或电子档案实际保管位置'),
  f('inventoryFileIds', '修复全部资料归档目录', 'file'),
  f('signedArchiveFileIds', '实际归档签署原件', 'file'),
  f('archiveFileIds', '完整修复档案文件或资料包', 'file'),
  f('aftercareResponsibleUserId', '后续保护实际责任人员', 'user'),
  f('aftercareHandedAt', '实际养护交接时间', 'datetime'),
  f('aftercareHandoverFileIds', '后续养护与监测交接原件', 'file'),
];
