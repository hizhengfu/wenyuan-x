import { institutionSuccessionFields } from './institutionSuccessionFields';
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
export const transferKindOptions = [
  { label: '国有馆藏文物交换', value: 'STATE_EXCHANGE' },
  { label: '国有馆藏文物调拨', value: 'STATE_ALLOCATION' },
  { label: '其他依法转移', value: 'OTHER_LAWFUL' },
  { label: '博物馆终止或合并承接', value: 'INSTITUTION_SUCCESSION' },
];
export function transferDossierFields(
  kind?: string,
  source?: string,
  locations: { label: string; value: string }[] = [],
): EvidenceField[] {
  const fields: EvidenceField[] = [
    f('sourceKeeperUserId', '源馆保管负责人', 'user'),
    f(
      'sourceLeaderUserId',
      kind === 'INSTITUTION_SUCCESSION' ? '源馆清算或合并依法授权人员' : '源馆主要负责人',
      'user',
    ),
    f('targetKeeperUserId', '接收馆保管负责人', 'user'),
    f('targetLeaderUserId', '接收馆主要负责人', 'user'),
    { ...f('targetWarehouseId', '接收馆库位', 'select'), options: locations },
    f('archiveHandoverFileIds', '逐件档案交接资料', 'file'),
    f('sourcePaperLedgerRef', '源馆纸质账册和页码'),
    f('targetPaperLedgerRef', '接收馆纸质账册和页码'),
    f('transportStandardReference', '适用包装运输标准及现行版本'),
    f('transportStandardApplicability', '本件标准适用范围与核对依据', 'textarea'),
    f('transportCarrierName', '承担运输的博物馆或有能力的机构'),
    f('transportCarrierCapabilityFileIds', '承运馆指派或机构资信能力原件', 'file'),
    f('transportPlanFileIds', '包装运输方案及标准核对签署原件', 'file'),
  ];
  if (kind === 'STATE_EXCHANGE')
    fields.push(
      {
        ...f('exchangeTransferIds', '整组交换申请编号', 'textarea'),
        hint: '双方先分别保存逐件申请，再填写完整申请编号（含本单），用逗号或换行分隔。每件应关联同一组编号。',
      },
      f('exchangeAgreementFileIds', '双方共同交换协议', 'file'),
    );
  if (kind === 'STATE_ALLOCATION')
    fields.push({
      ...f('provincialJurisdictionFileIds', '省级主管及区域范围依据', 'file', false),
      hint: '省级调拨决定须证明双方均为其行政区域内主管的国有馆；国家调拨决定不需要此项。',
    });
  if (kind === 'OTHER_LAWFUL') fields.push(f('legalBasisFileIds', '适用法律法规及处置依据', 'file'));
  if (kind === 'INSTITUTION_SUCCESSION') fields.push(...institutionSuccessionFields());
  if (source === 'DONATE' && kind !== 'INSTITUTION_SUCCESSION')
    fields.push(
      { ...f('noDonationAgreement', '原捐赠是否无约定协议', 'select'), options: yesNo },
      f('donationAgreementFileIds', '原捐赠约定协议', 'file', false),
      f('donationTransferClause', '关于交换调拨的具体约定', 'textarea', false),
      f('donorNoticeFileIds', '事先向捐赠人说明的凭据', 'file', false),
      f('donorWrittenConsentFileIds', '捐赠人书面同意', 'file', false),
    );
  return fields;
}
export const transferAdministrativeFields: EvidenceField[] = [
  {
    ...f('level', '行政机关层级', 'select'),
    options: [
      { label: '主管文物行政部门', value: 'COMPETENT' },
      { label: '省级文物行政部门', value: 'PROVINCE' },
      { label: '国务院文物行政部门', value: 'NATIONAL' },
    ],
  },
  {
    ...f('action', '文书性质', 'select'),
    options: [
      { label: '批准或调拨决定', value: 'APPROVAL' },
      { label: '备案凭证', value: 'FILING' },
    ],
  },
  f('authorityName', '行政机关全称'),
  f('documentNo', '文号或备案编号'),
  f('issuedAt', '实际批准或备案日期', 'date'),
  f('fileIds', '行政文书原件', 'file'),
];
export const transferExecutionFields: EvidenceField[] = [
  f('handedOn', '实际交接日期', 'date'),
  f('sourceCustodianUserId', '源馆实物点交保管员', 'user'),
  f('targetCustodianUserId', '接收馆实物点交保管员', 'user'),
  f('signedHandoverFileIds', '双方签署的逐件交接单', 'file'),
  f('conditionDescription', '逐件保存状态核对', 'textarea'),
  f('conditionImageFileIds', '交接时逐件影像', 'file'),
  { ...f('authenticityVerified', '已核实藏品真实性', 'select'), options: yesNo },
  f('appliedTransportStandardReference', '实际采用包装运输标准及版本'),
  { ...f('packingStandardConfirmed', '逐件按批准方案和标准核对包装', 'select'), options: yesNo },
  f('packingCheckFileIds', '逐件包装运输核验签署原件', 'file'),
  f('signedLedgerChangeFileIds', '签署的总账信息变更表', 'file'),
  f('receivingLedgerReceiptFileIds', '接收馆核收及总账凭据', 'file'),
];
export const transferNoticeFields: EvidenceField[] = [
  {
    ...f('channel', '结果公告渠道', 'select'),
    options: [
      { label: '博物馆现场', value: 'MUSEUM_ONSITE' },
      { label: '博物馆官方网站', value: 'MUSEUM_WEBSITE' },
    ],
  },
  f('startedOn', '实际公告日期', 'date'),
  f('urlOrLocation', '实发网址或现场地点'),
  f('publicContentFileIds', '退出结果公告内容', 'file'),
  f('publicationEvidenceFileIds', '实发凭据或现场照片', 'file'),
];
const factKeys = [
  'noDonationAgreement',
  'authenticityVerified',
  'packingStandardConfirmed',
  'succession.movable',
  'succession.noPrivateDistribution',
  'succession.thirdPartyRightsResolved',
];
export function transferEvidencePayload(value: Record<string, any>) {
  const result: Record<string, any> = {};
  for (const [key, original] of Object.entries(value)) {
    let v = original;
    if (factKeys.includes(key)) {
      if (v === 'YES') v = true;
      else if (v === 'NO') v = false;
    }
    if (key.startsWith('succession.')) {
      const parts = key.split('.');
      let target = result;
      for (const part of parts.slice(0, -1)) target = target[part] ||= {};
      target[parts.at(-1)!] = v;
    } else result[key] = v;
  }
  if (typeof result.exchangeTransferIds === 'string')
    result.exchangeTransferIds = [
      ...new Set(
        result.exchangeTransferIds
          .split(/[,，;；\n]/)
          .map((s: string) => s.trim())
          .filter(Boolean),
      ),
    ];
  return result;
}
export function transferEvidenceInitial(value: Record<string, any>) {
  const result: Record<string, any> = {};
  const add = (key: string, v: any) => {
    if (key.startsWith('succession') && v && typeof v === 'object' && !Array.isArray(v))
      for (const [child, nested] of Object.entries(v)) add(`${key}.${child}`, nested);
    else result[key] = factKeys.includes(key) && typeof v === 'boolean' ? (v ? 'YES' : 'NO') : v;
  };
  for (const [key, v] of Object.entries(value)) add(key, v);
  if (Array.isArray(result.exchangeTransferIds)) result.exchangeTransferIds = result.exchangeTransferIds.join('\n');
  return result;
}
