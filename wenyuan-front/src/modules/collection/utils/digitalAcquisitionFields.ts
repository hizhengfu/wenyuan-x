import type { EvidenceField } from './evidenceFields';
const yesNo = [
  { label: '是', value: 'YES' },
  { label: '否', value: 'NO' },
];
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
  ...(type === 'select' ? { options: yesNo } : {}),
});
const files = (key: string, label: string): EvidenceField => ({ ...f(key, label, 'file'), multiple: true });
const resource = [
  f('occurredAt', '实际完成本阶段时间', 'datetime'),
  f('operatorUserId', '实际经办人员', 'user'),
  { ...f('parentResourceIds', '实际来源资源批次', 'select'), multiple: true, options: [] },
  { ...files('fileIds', '本阶段实际成果文件'), packageFilesKey: 'packageFiles' },
  f('description', '实际工作内容', 'textarea'),
  f('technicalMetadata', '实际格式、精度、单位及质量参数', 'textarea'),
  f('signedRecordFileIds', '本阶段签署原件', 'file'),
];
export const digitalFields: Record<string, EvidenceField[]> = {
  plan: [
    f('preparedAt', '共同方案实际形成时间', 'datetime'),
    f('informationDepartmentId', '实际信息化部门', 'group'),
    f('informationUserId', '信息化责任人员', 'user'),
    f('keeperUserId', '保管负责人', 'user'),
    f('acquisitionMethod', '采集技术与方法', 'textarea'),
    f('workLocation', '采集场所'),
    f('plannedStart', '商定采集开始时间', 'datetime'),
    f('plannedEnd', '商定采集结束时间', 'datetime'),
    f('environmentAndExposureControls', '环境与暴露时间控制', 'textarea'),
    f('handlingControls', '支撑、提取及搬运防护', 'textarea'),
    f('processingStandards', '加工标准', 'textarea'),
    f('organizationRules', '命名、分类及元数据规则', 'textarea'),
    f('storageAndBackupPlan', '主存储、独立备份与恢复验证方案', 'textarea'),
    f('resourceSecurityPlan', '授权、保留及安全检查方案', 'textarea'),
    f('rightsAndConfidentiality', '权利与保密核查', 'textarea'),
    f('conditionFileIds', '原件现状与影像', 'file'),
    f('operationPlanFileIds', '操作方案原件', 'file'),
    f('safetyPlanFileIds', '安全方案原件', 'file'),
    f('securityEvidenceFileIds', '数字资源安全核查依据', 'file'),
    f('signedJointPlanFileIds', '共同签署方案原件', 'file'),
    f('replacementReason', '订正原因', 'textarea', false),
    f('replacementFileIds', '订正依据原件', 'file', false),
  ],
  start: [
    f('startedAt', '实际开始时间', 'datetime'),
    f('operatorUserId', '实际经办人员', 'user'),
    f('requisitionId', '实际提用单编号（与在库日记二选一）', 'text', false),
    f('onSiteWarehouseLogId', '实际在库日记编号', 'text', false),
    f('conditionSafe', '原件状况是否适宜', 'select'),
    f('siteSafe', '场所是否符合安全条件', 'select'),
    f('actualControls', '实际采取的防护措施', 'textarea'),
    f('safetyCheckFileIds', '安全核查原件', 'file'),
    f('signedStartFileIds', '实际开始签署原件', 'file'),
  ],
  capture: [
    f('finishedAt', '实物采集实际结束时间', 'datetime'),
    f('operatorUserId', '实际经办人员', 'user'),
    f('acquisitionParameters', '实际设备、精度、曝光及采集参数', 'textarea'),
    f('originalUndamaged', '原件是否未受损', 'select'),
    f('conditionAfter', '原件实际保存状况', 'textarea'),
    f('conditionFileIds', '采集后现状及影像', 'file'),
    { ...files('rawFileIds', '实际采集的原始文件'), packageFilesKey: 'rawPackageFiles' },
    f('signedCaptureFileIds', '采集结束签署原件', 'file'),
  ],
  PROCESSED: resource,
  ORGANIZED: resource,
  STORED: [
    ...resource,
    f('storageLocation', '实际主存储位置'),
    f('backupLocation', '实际独立备份位置'),
    f('restorationVerifiedAt', '实际恢复验证时间', 'datetime'),
    f('accessControlsVerified', '是否已核实授权安全措施', 'select'),
    f('securityCheckFileIds', '备份恢复与安全核查原件', 'file'),
  ],
  complete: [
    f('completedAt', '实际共同复核时间', 'datetime'),
    f('informationUserId', '本方案信息化责任人员', 'user'),
    f('keeperUserId', '本方案保管负责人', 'user'),
    { ...f('storedResourceIds', '已逐批核对的全部储存成果', 'select'), multiple: true, options: [] },
    f('qualityAndSecurityReview', '质量、来源链与安全共同复核意见', 'textarea'),
    f('signedJointReviewFileIds', '共同复核签署原件', 'file'),
  ],
};
export const digitalTitles: Record<string, string> = {
  plan: '登记共同方案',
  start: '登记实际开始',
  capture: '登记采集结束与原始数据',
  PROCESSED: '登记实际加工',
  ORGANIZED: '登记实际整理',
  STORED: '登记实际储存与验证',
  complete: '登记实际共同复核',
};
export const digitalStageLabels: Record<string, string> = {
  RAW: '原始采集',
  PROCESSED: '加工',
  ORGANIZED: '整理',
  STORED: '储存',
};
export function digitalValues(value: Record<string, any>) {
  const out = { ...value };
  for (const key of ['conditionSafe', 'siteSafe', 'originalUndamaged', 'accessControlsVerified'])
    if (out[key] === 'YES') out[key] = true;
    else if (out[key] === 'NO') out[key] = false;
  return out;
}
export function digitalFileIds(value: unknown): string[] {
  return [
    ...new Set(
      (Array.isArray(value) ? value : [value])
        .flatMap((v) => (typeof v === 'string' ? v.split(/[,，;\n]/) : []))
        .map((v) => v.trim())
        .filter(Boolean),
    ),
  ];
}
