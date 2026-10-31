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
export const incidentTargets = [
  { value: 'KEEPER', label: '保管部门负责人' },
  { value: 'DIRECTOR', label: '法定代表人或主要负责人' },
  { value: 'COMPETENT', label: '主管文物行政部门' },
  { value: 'PROVINCE', label: '省级文物行政部门' },
  { value: 'NATIONAL', label: '国务院文物行政部门' },
  { value: 'POLICE', label: '公安机关' },
];
export const incidentReportKinds = [
  { value: 'INCIDENT', label: '实际情况报告或报案' },
  { value: 'RESULT_FILING', label: '核查处理结果备案' },
];
export const incidentDispositions = [
  { value: 'PRESENT_SAFE', label: '实物已核实并恢复安全' },
  { value: 'PRESENT_RESTRICTED', label: '实物仍须限制使用' },
  { value: 'NOT_RECOVERED', label: '尚未找回' },
  { value: 'DESTROYED', label: '已经毁损灭失' },
];
export const incidentFields: Record<string, EvidenceField[]> = {
  source: [
    {
      ...f('sourceBizType', '实际来源业务', 'select'),
      options: [
        { value: 'COLLECTION_SAMPLING', label: '有损取样' },
        { value: 'COLLECTION_CARE', label: '日常检查与养护异常' },
        { value: 'COLLECTION_FRAGILITY', label: '脆弱藏品实际使用期间监测异常' },
        { value: 'COLLECTION_RESTORATION', label: '修复实际异常或停止' },
        { value: 'REPRODUCTION_COMPLIANCE_2026', label: '复制拓印停止异常' },
        { value: 'COLLECTION_RETURN', label: '归还验收' },
        { value: 'COLLECTION_INVENTORY_BATCH', label: '清点批次' },
      ],
    },
    f('sourceBizId', '实际来源业务编号'),
    f('sourceRecordId', '实际记录或方案编号（检查养护、取样、修复必填）', 'text', false),
    f('reason', '本次核对并补充关联的依据', 'textarea'),
    f('fileIds', '实际来源核对原件', 'file'),
  ],
  facts: [
    f('title', '事故案卷名称'),
    f('discoveredAt', '实际发现时间', 'datetime'),
    f('occurredAt', '实际发生时间（不明可留空）', 'datetime', false),
    f('location', '实际地点'),
    f('reporterUserId', '实际发现或报告人员', 'user'),
    f('description', '已知事实及发现经过', 'textarea'),
    f('immediateMeasures', '即时防护、现场保护及已采取措施', 'textarea'),
    {
      ...f('items', '逐件受影响藏品', 'records'),
      multiple: true,
      children: [
        f('collectionId', '受影响藏品', 'collection'),
        f('damaged', '是否发现损伤', 'select'),
        {
          ...f('security', '被盗、被抢或丢失情况', 'select'),
          options: [
            { value: 'NONE', label: '未发现被盗、被抢或丢失' },
            { value: 'THEFT', label: '被盗' },
            { value: 'ROBBERY', label: '被抢' },
            { value: 'LOSS', label: '丢失' },
          ],
        },
        f('major', '是否重大情况', 'select'),
        f('findings', '本件实际发现及现状', 'textarea'),
        f('imageFileIds', '现有影像原件（可后续补充）', 'file', false),
      ],
    },
    f('relatedWorkIds', '关联现场防护作业编号（逐行填写）', 'textarea', false),
    f('relatedRelocationIds', '关联应急搬移编号（逐行填写）', 'textarea', false),
    f('relatedRestorationPlanIds', '关联实际停止或完成的修复方案编号（逐行填写）', 'textarea', false),
    f('evidenceFileIds', '现有发现及现场原件', 'file', false),
    f('replacementReason', '补充或订正原因', 'textarea', false),
    f('replacementFileIds', '补充或订正原件', 'file', false),
  ],
  report: [
    { ...f('collectionIds', '本次实际报告涉及的藏品', 'select'), multiple: true, options: [] },
    { ...f('target', '实际报告接收方', 'select'), options: incidentTargets },
    { ...f('kind', '实际报告类型', 'select'), options: incidentReportKinds },
    f('authorityName', '实际接收机关或部门全称'),
    f('recipientUserId', '馆内报告实际接收人（馆内报告必填）', 'user', false),
    f('reportNo', '实际报告或受理编号'),
    f('reportedAt', '实际报案、报告或送达时间', 'datetime'),
    f('description', '实际报告内容与方式', 'textarea'),
    f('fileIds', '报告及送达、受理原件', 'file'),
  ],
  handling: [
    f('completedAt', '实际核查处理完成时间', 'datetime'),
    f('investigatorUserId', '实际调查经办人员', 'user'),
    f('cause', '实际原因调查', 'textarea'),
    f('actions', '实际处理措施及结果', 'textarea'),
    f('investigationFileIds', '调查原件', 'file'),
    {
      ...f('items', '逐件核查处理结果', 'records'),
      multiple: true,
      children: [
        { ...f('collectionId', '本次受影响藏品', 'select'), options: [] },
        { ...f('disposition', '当前实物实际处置状态', 'select'), options: incidentDispositions },
        f('authenticityConfirmed', '是否已核实实物真实性', 'select'),
        f('condition', '处理后真实现状', 'textarea'),
        f('actualLocation', '实际位置或未找到的说明'),
        f('custodianUserId', '当前实际保管人员', 'user', false),
        f('protectiveMeasures', '实际防护及限制措施', 'textarea'),
        f('followUp', '后续养护、安全检查或查找安排', 'textarea'),
        f('fileIds', '逐件核查及现状原件', 'file'),
      ],
    },
    {
      ...f('authorityResults', '行政或公安核查处理结果', 'records', false),
      multiple: true,
      children: [
        {
          ...f('target', '实际处理机关类别', 'select'),
          options: incidentTargets.filter((t) => !['KEEPER', 'DIRECTOR'].includes(t.value)),
        },
        f('authorityName', '实际处理机关全称'),
        { ...f('collectionIds', '本份结果涉及的藏品', 'select'), multiple: true, options: [] },
        f('completedAt', '实际核查处理时间', 'datetime'),
        { ...f('reportIds', '对应的实际情况报告', 'select'), multiple: true, options: [] },
        f('result', '实际核查处理结果', 'textarea'),
        f('fileIds', '行政或公安处理原件', 'file'),
      ],
    },
    f('keeperUserId', '本案保管负责人', 'user'),
    f('leaderUserId', '本案法定代表人或主要负责人', 'user'),
    f('archiveIndex', '本件藏品档案及事故案卷索引'),
    f('signedHandlingFileIds', '实际处理签署原件', 'file'),
    f('replacementReason', '处理结果订正原因', 'textarea', false),
    f('replacementFileIds', '处理订正原件', 'file', false),
  ],
  confirm: [
    f('signedAt', '本人实际签署时间', 'datetime'),
    f('opinion', '本人核对完整案卷的意见', 'textarea'),
    f('fileIds', '本人签署原件', 'file'),
  ],
};
export function incidentValues(v: Record<string, any>) {
  const result = { ...v };
  for (const key of ['relatedWorkIds', 'relatedRelocationIds', 'relatedRestorationPlanIds']) {
    if (typeof v[key] === 'string')
      result[key] = [
        ...new Set(
          v[key]
            .split(/[,，;；\n]/)
            .map((x: string) => x.trim())
            .filter(Boolean),
        ),
      ];
  }
  if (v.items)
    result.items = v.items.map((row: any) => {
      const value = { ...row };
      for (const key of ['damaged', 'major', 'authenticityConfirmed'])
        if (key in value) value[key] = value[key] === 'YES' ? true : value[key] === 'NO' ? false : value[key];
      return value;
    });
  if (v.authorityResults)
    result.authorityResults = v.authorityResults.map((row: any) => ({
      ...row,
      collectionIds: row.collectionIds ? [...row.collectionIds] : row.collectionIds,
      reportIds: row.reportIds ? [...row.reportIds] : row.reportIds,
    }));
  return result;
}
export function incidentFormValues(v: Record<string, any>) {
  return {
    ...v,
    ...(Array.isArray(v.relatedWorkIds) ? { relatedWorkIds: v.relatedWorkIds.join('\n') } : {}),
    ...(Array.isArray(v.relatedRelocationIds) ? { relatedRelocationIds: v.relatedRelocationIds.join('\n') } : {}),
    ...(Array.isArray(v.relatedRestorationPlanIds)
      ? { relatedRestorationPlanIds: v.relatedRestorationPlanIds.join('\n') }
      : {}),
    ...(v.items
      ? {
          items: v.items.map((row: any) => {
            const value = { ...row };
            for (const key of ['damaged', 'major', 'authenticityConfirmed'])
              if (typeof value[key] === 'boolean') value[key] = value[key] ? 'YES' : 'NO';
            return value;
          }),
        }
      : {}),
  };
}
export function incidentLabel(value: string, options = incidentTargets) {
  return options.find((o) => o.value === value)?.label || value;
}
