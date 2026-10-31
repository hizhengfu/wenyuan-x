import type { EvidenceField } from './evidenceFields';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
});
const yesNo = [
  { label: '是', value: 'YES' },
  { label: '否', value: 'NO' },
];
export const receiptFields: EvidenceField[] = [
  f('checkedAt', '本次实际核收时间', 'datetime'),
  { ...f('keeperUserIds', '实际接收保管人员', 'user'), multiple: true },
  { ...f('accepted', '本次是否已完成入藏核收', 'select'), options: yesNo },
  f('signedReceiptFileIds', '双方实际签署的逐件凭证或清册', 'file'),
];
export const pieceFields: EvidenceField[] = [
  f('pieceNo', '清册临时标识'),
  f('receivedAt', '本件实际接收时间', 'datetime'),
  f('actualQuantity', '本件或本套内实际数量', 'number', false),
  f('actualQuantityUnit', '实际数量单位（件、册等，不填写套）', 'text', false),
  f('identification', '本件实物识别、尺寸及重量等核对情况', 'textarea'),
  f('condition', '实际保存状况', 'textarea'),
  { ...f('identityMatches', '身份与取得依据一致', 'select'), options: yesNo },
  { ...f('conditionMatches', '保存状况与取得依据一致', 'select'), options: yesNo },
  f('photoFileIds', '本件实际照片', 'file'),
  f('discrepancy', '不符的具体情况', 'textarea', false),
];
export const receiptPhotoCorrectionFields: EvidenceField[] = [
  f('photoFileIds', '本件订正照片（未更正可保留原照片）', 'file'),
];
export const pendingReceiptFields: EvidenceField[] = [
  f('issueDescription', '未完成或差异说明', 'textarea'),
  f('protectiveMeasures', '已采取的防护措施', 'textarea'),
  f('followUpAt', '后续核查时间', 'datetime'),
];
export const lateReceiptFields: EvidenceField[] = [
  f('lateReason', '迟办原因', 'textarea'),
  f('lateHandlingFileIds', '迟办处理原件', 'file'),
];
export const correctionReceiptFields: EvidenceField[] = [
  f('replacementReason', '补充实收或订正原因', 'textarea'),
  f('correctionFileIds', '本次补充或订正的签署原件', 'file'),
];
export const receiptStatus = (s?: string) =>
  ({ DRAFT: '待核收', RECEIVED: '已核收待登记', REGISTERED: '已正式登账' })[s || ''] || '状态待核实';
export function receiptSeed(item: any) {
  return {
    acquisitionId: item.acquisitionId,
    warehouseId: item.current?.content?.warehouseId,
    keeperUserIds: [],
    handoverNames: [],
    pieces: (item.current?.content?.pieces || []).map((p: any) => ({
      ...p,
      identityMatches: null,
      conditionMatches: null,
    })),
    accepted: null,
  };
}
export function receiptSignatureCorrectionSeed(item: any) {
  return {
    ...item.current.content,
    acquisitionId: item.acquisitionId,
    signedReceiptFileIds: '',
    replacementReason: '',
    correctionFileIds: '',
  };
}
export function receiptPayload(value: any) {
  const bool = (v: any) => (v === 'YES' ? true : v === 'NO' ? false : typeof v === 'boolean' ? v : null);
  return {
    ...value,
    accepted: bool(value.accepted),
    pieces: (value.pieces || []).map((p: any) => ({
      ...p,
      identityMatches: bool(p.identityMatches),
      conditionMatches: bool(p.conditionMatches),
    })),
  };
}
export function receiptError(value: any, correction: boolean, late: boolean): string | undefined {
  const required = (fields: EvidenceField[], data: any) =>
    fields.find(
      (f) =>
        f.required &&
        (data[f.key] === null ||
          data[f.key] === undefined ||
          data[f.key] === '' ||
          (Array.isArray(data[f.key]) && !data[f.key].length)),
    );
  const missing = required(receiptFields, value);
  if (missing) return `请填写${missing.label}`;
  if (!value.warehouseId || !value.handoverNames?.length) return '请填写实际接收库位及移交人员姓名、单位';
  if (!value.pieces?.length) return '请逐件填写实际收到的物品';
  for (const [i, p] of value.pieces.entries()) {
    const field = required(pieceFields, p);
    if (field) return `第${i + 1}件：请填写${field.label}`;
    if ((p.identityMatches !== true || p.conditionMatches !== true) && !p.discrepancy?.trim())
      return `第${i + 1}件：请如实说明不符情况`;
  }
  if (value.accepted === true) {
    if (value.pieces.some((p: any) => !Number.isSafeInteger(p.actualQuantity) || p.actualQuantity <= 0 || !p.actualQuantityUnit?.trim() || p.actualQuantityUnit.trim() === '套'))
      return '核收完成前须凭原件记录每件或每套内的正整数实际数量和单位，不能以套数替代';
    if (new Set(value.pieces.map((p: any) => p.actualQuantityUnit.trim())).size !== 1) return '同一登记对象的实际数量须使用一致单位';
  }
  const conditional = [
    ...(value.accepted === false ? pendingReceiptFields : []),
    ...(value.accepted === true && late ? lateReceiptFields : []),
    ...(correction ? correctionReceiptFields : []),
  ];
  const field = required(conditional, value);
  if (field) return `请填写${field.label}`;
}
