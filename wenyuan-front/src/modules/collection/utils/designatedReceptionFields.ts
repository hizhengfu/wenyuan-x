import { collectionNatureOptions, collectionNatureHint } from './collectionNature';
import type { EvidenceField } from './evidenceFields';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
});
const options = (items: [string, string][]) => items.map(([value, label]) => ({ value, label }));
const yesNo = options([
  ['YES', '是'],
  ['NO', '否'],
]);
const bool = (key: string, label: string): EvidenceField => ({ ...f(key, label, 'select'), options: yesNo });
const correction = [
  f('correctionReason', '追加或订正说明（已有原件时必填）', 'textarea', false),
  f('correctionFileIds', '追加或订正签署原件', 'file', false),
];
export const designatedStates: Record<string, string> = {
  AWAIT_ASSESSMENT: '待专业鉴定评估',
  MUST_RECEIVE: '符合标准，应接收',
  MUST_REFER: '不符合标准，应报请另行指定',
  AWAIT_AUTHORITY: '已报请，等待行政决定',
  AWAIT_HANDOVER: '已另行指定，等待实际承接',
  REASSIGNED_RECEIVED: '已记录另行承接结果',
  READY_FOR_ACCESSION: '接收准备已核对，待入馆核收',
};
export const designatedTitles: Record<string, string> = {
  notice: '登记行政指定接收',
  assessment: '登记专业鉴定评估',
  custody: '登记实际保管原件',
  acceptance: '本人核对接收及实际取得',
  referral: '登记已报请另行指定',
  reassignment: '登记另行指定行政决定',
  settlement: '登记实际承接结果',
};
const pieces: EvidenceField[] = [
  f('pieceNo', '原件逐件编号'),
  f('receivedAt', '实际接收时间', 'datetime'),
  f('identification', '身份和识别特征'),
  f('condition', '实际保存状况'),
  bool('identityMatches', '身份与指定清单一致'),
  bool('conditionMatches', '保存状况与依据一致'),
  f('photoFileIds', '逐件照片原件', 'file'),
  f('discrepancy', '实际差异说明', 'textarea', false),
];
const people = (key: string, label: string): EvidenceField => ({
  ...f(key, label, 'textarea'),
  hint: '每行一名实际人员，填写姓名及单位',
});
export function designatedFields(kind: string, context: Record<string, any> = {}, orgId?: string): EvidenceField[] {
  const item: EvidenceField[] = [
    f('item.sourceItemNo', '原移交物稳定编号'),
    f('item.name', '指定对象名称'),
    {
      ...f('item.nature', '对象性质', 'select'),
      options: collectionNatureOptions,
      hint: collectionNatureHint,
    },
    f('item.quantity', '指定数量', 'number'),
    f('item.unit', '计量单位'),
    {
      ...f('item.completeness', '完残程度', 'select'),
      options: options([
        ['COMPLETE', '完整'],
        ['BASIC', '基本完整'],
        ['DAMAGED', '残缺'],
        ['SEVERE', '严重残缺'],
      ]),
    },
    f('item.condition', '指定依据中的保存状况', 'textarea'),
    f('item.imageFileIds', '对象影像', 'file'),
    f('item.era', '年代', 'text', false),
    f('item.texture', '质地', 'text', false),
    f('item.weight', '重量', 'text', false),
  ];
  const fields: Record<string, EvidenceField[]> = {
    notice: [
      { ...f('orgId', '被指定的本馆单位', 'select'), options: context.orgs || [] },
      {
        ...f('authorityLevel', '指定部门级别', 'select'),
        options: options([
          ['NATIONAL', '国务院文物行政部门'],
          ['PROVINCE', '省级文物行政部门'],
        ]),
      },
      f('authorityCode', '指定部门稳定标识'),
      f('authorityName', '指定部门正式名称'),
      f('documentNo', '指定决定文号'),
      f('issuedOn', '决定日期', 'date'),
      f('notifiedOn', '实际收到通知日期', 'date'),
      f('designatedReceiverName', '决定中的接收馆正式名称'),
      {
        ...f('senderKind', '原移交单位类别', 'select'),
        options: options([
          ['ARCHAEOLOGY', '考古发掘单位'],
          ['LAW_ENFORCEMENT', '执法部门'],
          ['JUDICIAL', '司法部门'],
          ['SUPERVISORY', '监察部门'],
          ['OTHER_LAWFUL', '其他依法移交部门'],
        ]),
      },
      f('senderName', '原移交单位正式名称'),
      f('senderIdentity', '原单位稳定身份标识'),
      f('transferLegalBasis', '依法移交依据', 'textarea'),
      f('documentFileIds', '行政指定及移交依据原件', 'file'),
      ...item,
    ],
    assessment: [
      f('assessedAt', '实际评估时间', 'datetime'),
      f('operatorUserId', '实际登记人员本人', 'user'),
      f('standardsFileIds', '本馆入藏标准原件', 'file'),
      f('standardsConclusion', '逐项标准及结论说明', 'textarea'),
      bool('meetsStandards', '是否符合本馆入藏标准'),
      {
        ...f('opinions', '实际专业人员书面意见', 'records'),
        children: [
          f('name', '姓名'),
          f('organization', '所属单位'),
          f('specialty', '专业方向'),
          f('qualificationFileIds', '专业资格依据', 'file'),
          f('authenticityOpinion', '真伪鉴定意见', 'textarea'),
          f('valueOpinion', '价值评估意见', 'textarea'),
          bool('meetsStandards', '是否符合本馆标准'),
          f('signedOpinionFileIds', '本人签署意见原件', 'file'),
        ],
      },
      f('signedFileIds', '评估结论签署原件', 'file'),
      ...correction,
    ],
    custody: [
      f('checkedAt', '实际保管核验时间', 'datetime'),
      f('operatorUserId', '实际登记人员本人', 'user'),
      {
        ...f('warehouseId', '本馆实际保管库位', 'select'),
        options: (context.locations || []).filter((v: any) => v.orgId === orgId),
      },
      f('custodianUserId', '实际保管人员', 'user'),
      people('handoverNames', '实际移交人员'),
      { ...f('pieces', '实际收到的逐件清册', 'records'), children: pieces },
      f('signedFileIds', '双方签署保管交接原件', 'file'),
      ...correction,
    ],
    acceptance: [
      f('actualAcquiredOn', '依法实际取得日期', 'date'),
      f('acquisitionBasisFileIds', '实际取得日期及依据原件', 'file'),
      f('confirmedAt', '本人实际核对时间', 'datetime'),
      f('keeperUserId', '保管负责人本人', 'user'),
      f('conclusion', '实际取得与接收准备核对结论', 'textarea'),
      f('signedFileIds', '本人签署核对原件', 'file'),
    ],
    referral: [
      f('reportedAt', '实际报请时间', 'datetime'),
      f('operatorUserId', '实际办理人员本人', 'user'),
      f('authorityCode', '原指定部门稳定标识'),
      f('referenceNo', '报请文号'),
      f('requestSummary', '不符合标准及另行指定请求', 'textarea'),
      f('requestFileIds', '实际报请原件', 'file'),
      f('deliveryReceiptFileIds', '实际送达或签收凭据', 'file'),
    ],
    reassignment: [
      f('decidedOn', '另行指定决定日期', 'date'),
      f('recordedByUserId', '据原件登记人员本人', 'user'),
      f('authorityCode', '原指定部门稳定标识'),
      f('authorityName', '原指定部门正式名称'),
      f('documentNo', '另行指定文号'),
      f('recipientName', '另行指定接收单位名称'),
      f('recipientIdentity', '接收单位稳定身份标识'),
      f('recipientKind', '接收单位类别'),
      f('sourceItemNo', '决定对应的原移交物编号'),
      f('documentFileIds', '另行指定行政原件', 'file'),
    ],
    settlement: [
      f('handedAt', '实际承接时间', 'datetime'),
      f('recordedByUserId', '据实登记人员本人', 'user'),
      {
        ...f('mode', '真实承接路径', 'select'),
        options: options([
          ['FROM_THIS_MUSEUM', '由本馆实际交出'],
          ['DIRECT_FROM_SENDER', '原移交单位直接交新单位'],
        ]),
      },
      f('recipientName', '实际接收单位名称'),
      f('recipientIdentity', '实际接收单位稳定身份标识'),
      people('senderNames', '实际交出人员'),
      people('receiverNames', '实际接收人员'),
      { ...f('pieces', '实际承接逐件原件', 'records'), children: pieces },
      f('actualResult', '实际承接结果', 'textarea'),
      f('signedReceiptFileIds', '双方签署承接原件', 'file'),
    ],
  };
  return fields[kind] || [];
}
export function designatedValues(value: Record<string, any>): Record<string, any> {
  const out: Record<string, any> = {};
  const boolean = (v: any) => (v === 'YES' ? true : v === 'NO' ? false : v);
  for (const [key, v] of Object.entries(value)) {
    if (key.startsWith('item.')) (out.item ||= {})[key.slice(5)] = v;
    else if (key === 'meetsStandards') out[key] = boolean(v);
    else if (key === 'opinions') out[key] = v?.map((r: any) => ({ ...r, meetsStandards: boolean(r.meetsStandards) }));
    else if (key === 'pieces')
      out[key] = v?.map((r: any) => ({
        ...r,
        identityMatches: boolean(r.identityMatches),
        conditionMatches: boolean(r.conditionMatches),
      }));
    else if (['handoverNames', 'senderNames', 'receiverNames'].includes(key))
      out[key] = Array.isArray(v)
        ? v
        : typeof v === 'string'
          ? v
              .split(/\n/)
              .map((s) => s.trim())
              .filter(Boolean)
          : v;
    else out[key] = v;
  }
  return out;
}
