import type { EvidenceField } from './evidenceFields';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
});
const bool = (key: string, label: string): EvidenceField => ({
  ...f(key, label, 'select'),
  options: [
    { value: 'YES', label: '是，按实际原件核实' },
    { value: 'NO', label: '否或仍待核实' },
  ],
});
export const institutionInvestigationTitles: Record<string, string> = {
  'item-investigation': '追加交接差异调查及处理',
  'item-investigation-keeper': '保管负责人核对差异处理',
  'item-investigation-authorization': '依法负责人员复核差异处理',
};
export const institutionInvestigationStates: Record<string, string> = {
  NO_DIFFERENCE: '当前没有待核交接差异',
  NEEDS_INVESTIGATION: '交接差异或后续问题待调查',
  NEEDS_RECHECK: '交接或问题已变化，须追加核查',
  INVESTIGATING: '实际调查处理中',
  READY_FOR_REVIEW: '待保管负责人核对处理结果',
  KEEPER_CONFIRMED: '待依法负责人员复核处理结果',
  CONFIRMED: '差异处理已由两名责任人员复核',
};
const pieceFields: EvidenceField[] = [
  f('pieceNo', '已交接对象的逐件稳定编号'),
  f('identification', '本次身份查验及实际识别特征', 'textarea'),
  f('condition', '本次实际保存状况（保留损伤事实）', 'textarea'),
  bool('nonCollectionConfirmed', '经本次核实仍属其他物品，非文物或正式藏品'),
  bool('identityConfirmed', '本件实际身份已查清'),
  bool('present', '本次核查实物确实在场'),
  bool('safeCustody', '本件目前具备安全保管条件'),
  f('recipientName', '实际接收保管方正式名称', 'text', false),
  f('recipientIdentity', '实际接收保管方稳定身份标识', 'text', false),
  f('actualLocation', '本件实际保管地点', 'text', false),
  f('custodianDescription', '本件实际保管人员及单位', 'textarea', false),
  f('photoFileIds', '本次实际查验照片（在场及结案时核实）', 'file', false),
];
export function institutionInvestigationFields(kind: string, context: Record<string, any> = {}): EvidenceField[] {
  if (kind === 'item-investigation')
    return [
      f('investigatedAt', '本次实际核查时间', 'datetime'),
      f('operatorUserId', '实际经办人本人', 'user'),
      f('cause', '原因调查及当前查明情况', 'textarea'),
      f('actions', '已经实际采取的处理及保护措施', 'textarea'),
      f('senderStatementFileIds', '点交交出方的实际书面说明（结案必备）', 'file', false),
      f('receiverStatementFileIds', '点交接收方的实际书面说明（结案必备）', 'file', false),
      f('investigationFileIds', '实际调查处理原件', 'file'),
      { ...f('pieces', '全部已交接对象的本次逐件核查', 'records'), children: pieceFields },
      bool('thirdPartyRightsResolved', '本次相关第三方权利已处理'),
      f('thirdPartyRightsFileIds', '实际权利核查及处理原件', 'file'),
      f('reportingAssessment', '根据物品性质、事件及适用规定核实的报告义务', 'textarea'),
      f('reportingBasisFileIds', '报告程序核查依据原件', 'file'),
      bool('reportingRequired', '经真实依据核实需履行相关报告'),
      bool('reportingCompleted', '所需报告已经实际完成'),
      f('reportFileIds', '已完成的实际报告、送达及处理原件', 'file', false),
      bool('allIssuesResolved', '本版交接及全部已知后续问题已处理'),
      f('remainingIssues', '仍未完成或待核实事项（未结时必填）', 'textarea', false),
      f('responsibleUserId', '后续核查责任人（未结时必填）', 'user', false),
      f('nextCheckAt', '后续核查安排（未结时必填）', 'datetime', false),
      f('revisionReason', '追加核查或订正原因', 'textarea', !!context.hasPreviousInvestigation),
      f('revisionFileIds', '追加核查或订正原件', 'file', !!context.hasPreviousInvestigation),
      f('signedFileIds', '经办人本次真实签署的调查处理原件', 'file'),
    ];
  if (kind === 'item-investigation-keeper' || kind === 'item-investigation-authorization')
    return [
      f('confirmedAt', '本人实际确认时间', 'datetime'),
      f('confirmerUserId', '实际确认人本人', 'user'),
      f('conclusion', '本人对双方说明、调查、报告及逐件保管的核对意见', 'textarea'),
      f('signedFileIds', '本人实际签署原件', 'file'),
    ];
  return [];
}
const facts = [
  'thirdPartyRightsResolved',
  'reportingRequired',
  'reportingCompleted',
  'allIssuesResolved',
  'nonCollectionConfirmed',
  'identityConfirmed',
  'present',
  'safeCustody',
];
function values(value: Record<string, any>, fields: EvidenceField[]): Record<string, any> {
  const result: Record<string, any> = {};
  for (const field of fields)
    if (field.key in value) {
      const v = value[field.key];
      if (facts.includes(field.key)) {
        if (![true, false, 'YES', 'NO'].includes(v)) throw new Error('请按本次真实核查选择结论');
        result[field.key] = v === true || v === 'YES';
      } else if (field.type === 'records') {
        if (!Array.isArray(v)) throw new Error('请填写实际逐件核查记录');
        result[field.key] = v.map((row: Record<string, any>) => values(row, field.children || []));
      } else result[field.key] = v;
    }
  return result;
}
export function institutionInvestigationPayload(kind: string, value: Record<string, any>) {
  return values(value, institutionInvestigationFields(kind));
}
export function institutionInvestigationInitial(kind: string, userId?: string, batches: any[] = []) {
  if (kind === 'item-investigation')
    return {
      operatorUserId: userId,
      pieces: batches.flatMap((r) => (r.content.request.pieces || []).map((p: any) => ({ pieceNo: p.pieceNo }))),
    };
  return { confirmerUserId: userId };
}
