import type { EvidenceField } from './evidenceFields';
import {
  institutionInvestigationFields,
  institutionInvestigationPayload,
  institutionInvestigationTitles,
} from './institutionInvestigationFields';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
});
const bool = (key: string, label: string): EvidenceField => ({
  ...f(key, label, 'select'),
  options: [
    { value: 'YES', label: '是，已按原件核实' },
    { value: 'NO', label: '否' },
  ],
});
const pieces: EvidenceField[] = [
  f('pieceNo', '本条逐件稳定编号'),
  f('identification', '本件身份及识别特征'),
  f('condition', '本件保存状况'),
  f('photoFileIds', '本阶段逐件照片', 'file'),
];
export const institutionItemTitles: Record<string, string> = {
  ...institutionInvestigationTitles,
  'item-proposal': '分类及承接方案',
  'item-keeper': '保管负责人核对分类方案',
  'item-authorization': '依法负责人员确认分类方案',
  'item-handover': '登记实际交接批次',
};
export const institutionRouteOptions = [
  { value: 'RECORD_LINK', label: '关联已核实的业务来源，沿原业务办理' },
  { value: 'NON_COLLECTION_SUCCESSION', label: '其他物品依据实际公益方案承接' },
  { value: 'RETURN_NON_COLLECTION_CUSTODY', label: '暂存等他人非藏品物品交还实际权利人' },
];
export function institutionItemFields(
  kind: string,
  context: Record<string, any> = {},
  route?: string,
): EvidenceField[] {
  if (kind === 'item-proposal') {
    const common = [
      { ...f('route', '经实际核实的办理路径', 'select'), options: institutionRouteOptions },
      f('proposedAt', '实际方案形成时间', 'datetime'),
      f('operatorUserId', '实际经办人本人', 'user'),
      f('identificationConclusion', '专业鉴别、身份关系及分类结论', 'textarea'),
      f('identificationFileIds', '专业鉴别与身份核实原件', 'file'),
      f('revisionReason', '追加或订正原因（已有方案时必填）', 'textarea', !!context.hasPrevious),
      f('revisionFileIds', '追加或订正原件（已有方案时必填）', 'file', !!context.hasPrevious),
    ];
    if (route === 'RECORD_LINK')
      return [
        ...common,
        {
          ...f('linkedItemKey', '本案已核实的实际对应来源', 'select'),
          options: context.linkableItems || [],
          filterable: true,
        },
      ];
    if (!route) return common;
    const property = [
      bool('nonCollectionConfirmed', '已依据专业原件确认属于其他物品，非文物或正式藏品'),
      {
        ...f('ownership', '实际权属', 'select'),
        options: [
          { value: 'STATE', label: '国家所有' },
          { value: 'NON_STATE', label: '非国家所有' },
        ],
      },
      f('propertyBasisFileIds', '实际权属依据', 'file'),
      f('ownerName', '实际所有人正式名称'),
      f('ownerIdentity', '实际所有人稳定身份标识'),
      {
        ...f('recipientKind', '实际接收方类型', 'select'),
        options:
          route === 'RETURN_NON_COLLECTION_CUSTODY'
            ? [{ value: 'LAWFUL_OWNER', label: '原关系中的真实权利人' }]
            : [
                { value: 'PUBLIC_BENEFIT_ORGANIZATION', label: '具有相应资格的公益机构' },
                { value: 'STATE_TEACHING_RESEARCH', label: '国有教学或科研单位保管使用' },
              ],
      },
      f('recipientName', '拟实际接收方正式名称'),
      f('recipientIdentity', '拟实际接收方稳定身份标识'),
      f('recipientQualificationFileIds', '接收资格或权利人身份证明', 'file'),
      f('dispositionBasisFileIds', '实际处置或交还依据', 'file'),
      bool('thirdPartyRightsResolved', '已依法依约处理第三方权利'),
      f('thirdPartyRightsFileIds', '第三方权利处理原件', 'file'),
      ...(route === 'RETURN_NON_COLLECTION_CUSTODY'
        ? [
            f('custodyAgreementFileIds', '真实寄存等保管关系原件', 'file'),
            f('returnAuthorizationFileIds', '实际交还授权及依据', 'file'),
          ]
        : [
            f('publicBenefitPurpose', '继续保管使用的实际公益用途', 'textarea'),
            bool('noPrivateDistribution', '本馆财产未分配给出资人或成员等'),
          ]),
      { ...f('pieces', '本条全部对象的逐件方案清册', 'records'), children: pieces },
    ];
    return [...common, ...property];
  }
  if (kind === 'item-keeper' || kind === 'item-authorization')
    return [
      f('confirmedAt', '本次实际确认时间', 'datetime'),
      f('confirmerUserId', '实际确认人本人', 'user'),
      f('conclusion', '本人核对分类、身份、去向及依据的意见', 'textarea'),
      f('signedFileIds', '本人实际签署原件', 'file'),
    ];
  if (kind === 'item-handover')
    return [
      f('handedAt', '本批次实际交接时间', 'datetime'),
      f('operatorUserId', '实际登记人本人', 'user'),
      f('recipientName', '实际接收方正式名称'),
      f('recipientIdentity', '实际接收方稳定身份标识'),
      f('actualReceivingLocation', '本批次实际接收地点'),
      { ...f('senderNames', '本批次实际交出人员', 'textarea'), hint: '每行一名实际人员，注明姓名及单位' },
      { ...f('receiverNames', '本批次实际点收人员', 'textarea'), hint: '每行一名实际人员，注明姓名及单位' },
      { ...f('pieces', '本批次真实交接对象（可以分批）', 'records'), children: pieces },
      bool('identityMatches', '本批次身份与方案清册一致'),
      bool('conditionMatches', '本批次保存状况与原件一致'),
      f('discrepancy', '实际差异（不一致时必填）', 'textarea', false),
      f('protectiveMeasures', '已经采取的防护及核查措施（有差异时必填）', 'textarea', false),
      f('signedReceiptFileIds', '本批次点交双方真实签署回执', 'file'),
      f('archiveHandoverFileIds', '本批次随物资料和档案交接原件', 'file'),
    ];
  return institutionInvestigationFields(kind, context);
}
const facts = [
  'nonCollectionConfirmed',
  'thirdPartyRightsResolved',
  'noPrivateDistribution',
  'identityMatches',
  'conditionMatches',
];
export function institutionItemValues(v: Record<string, any>): Record<string, any> {
  const result = { ...v };
  for (const key of facts)
    if (key in result && result[key] !== null && result[key] !== undefined) {
      if (![true, false, 'YES', 'NO'].includes(result[key])) throw new Error('请按真实原件选择核实结论');
      result[key] = result[key] === true || result[key] === 'YES';
    }
  for (const key of ['senderNames', 'receiverNames'])
    if (typeof result[key] === 'string')
      result[key] = result[key]
        .split(/\r?\n/)
        .map((s: string) => s.trim())
        .filter(Boolean);
  return result;
}
export function institutionItemPayload(kind: string, value: Record<string, any>): Record<string, any> {
  if (!kind.startsWith('item-')) return value;
  if (kind.startsWith('item-investigation')) return institutionInvestigationPayload(kind, value);
  const fields = institutionItemFields(kind, {}, value.route);
  return institutionItemValues(
    Object.fromEntries(fields.filter((f) => f.key in value).map((f) => [f.key, value[f.key]])),
  );
}
export function institutionItemInitial(
  kind: string,
  proposal: Record<string, any> | undefined,
  userId?: string,
  handed: string[] = [],
): Record<string, any> {
  if (kind === 'item-proposal') {
    const value: Record<string, any> = { ...proposal, operatorUserId: userId };
    delete value.itemKey;
    delete value.proposedAt;
    delete value.previousProposalId;
    delete value.revisionReason;
    delete value.revisionFileIds;
    for (const key of facts) delete value[key];
    return value;
  }
  if (kind === 'item-keeper' || kind === 'item-authorization') return { confirmerUserId: userId };
  if (kind === 'item-handover')
    return {
      operatorUserId: userId,
      recipientName: proposal?.recipientName,
      recipientIdentity: proposal?.recipientIdentity,
      pieces: (proposal?.pieces || [])
        .filter((p: any) => !handed.includes(p.pieceNo.trim()))
        .map((p: any) => ({ pieceNo: p.pieceNo })),
    };
  return {};
}
