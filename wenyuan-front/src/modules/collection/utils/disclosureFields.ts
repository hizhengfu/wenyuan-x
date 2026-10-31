import type { EvidenceField } from './evidenceFields';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
});
export const disclosureRestrictions = [
  ['STATE_SECRET', '国家秘密'],
  ['BUSINESS_SECRET', '商业秘密'],
  ['WORK_SECRET', '工作秘密'],
  ['PERSONAL_PRIVACY', '个人隐私'],
] as const;
export const disclosureHandlings = [
  { value: 'NONE_PRESENT', label: '本公开稿不含此类内容' },
  { value: 'REDACTED', label: '此类内容已剔除或脱敏' },
  { value: 'WITHHELD', label: '适用此类整体不公开依据' },
];
export const publicFacts = [
  ['TOTAL_REG_NO', '总登记号'],
  ['NAME', '名称'],
  ['CATEGORY', '类别'],
  ['ERA', '时代'],
  ['LEVEL', '级别'],
  ['MATERIAL', '质地'],
  ['QUANTITY', '数量'],
  ['UNIT', '计量单位'],
  ['DIMENSIONS', '尺寸'],
  ['MANAGEMENT', '管理情况'],
  ['USE', '使用情况'],
  ['DISPOSITION_PLAN', '退出处置方案'],
  ['ACCOUNT_DISPOSITION', '账目处置情况'],
  ['OBJECT_DISPOSITION', '实物处置情况'],
] as const;
export const disclosureChannels = [
  { value: 'MUSEUM_SITE', label: '博物馆现场' },
  { value: 'OFFICIAL_WEBSITE', label: '官方网站' },
  { value: 'ANNUAL_REPORT', label: '年报' },
  { value: 'MEDIA', label: '媒体' },
  { value: 'AUTHORITY_WEBSITE', label: '主管文物部门官方网站' },
];
export const disclosureReviewFields = (revision = false): EvidenceField[] => [
  {
    ...f('decision', '本次实际核查结论', 'select'),
    options: [
      { value: 'PUBLIC', label: '已核查的公开稿' },
      { value: 'WITHHELD', label: '整体不公开的实际依据' },
    ],
  },
  f('reviewedAt', '实际完成核查时间', 'datetime'),
  f('reviewerUserId', '实际核查人员', 'user'),
  f('signedReviewFileIds', '实际签署核查原件', 'file'),
  f('replacementReason', '本次更新或订正原因', 'textarea', revision),
  f('replacementFileIds', '本次核实依据原件', 'file', revision),
];
export const disclosurePublicFields: EvidenceField[] = [
  f('title', '公开标题'),
  f('body', '公开正文（纯文本）', 'textarea'),
  f('feedbackInstructions', '向公众提供的监督反馈方式', 'textarea'),
  f('imageUrlsText', '已核查的公开图片地址（每行一个）', 'textarea', false),
  f('publicContentFileIds', '与文字和图片一致的实际公开稿原件', 'file'),
];
export const disclosureEventFields = (withdraw = false): EvidenceField[] => [
  { ...f('channel', '实际公开渠道', 'select'), options: disclosureChannels },
  f('location', '实际官网地址、年报页码或现场位置'),
  f('occurredAt', withdraw ? '实际撤下时间' : '实际公开时间', 'datetime'),
  f('recorderUserId', '核实该事实的实际人员', 'user'),
  f('evidenceFileIds', withdraw ? '实际撤下凭据' : '实际公开凭据', 'file'),
  ...(withdraw
    ? [f('reason', '实际撤下原因', 'textarea')]
    : [f('publicContentFileIds', '本次实际使用的已核查公开稿原件', 'file')]),
];
export const disclosureStopFields = [f('reason', '停止向CMS供稿的原因', 'textarea')];
export function disclosureStatus(row: any) {
  if (row.content.decision === 'WITHHELD') return '已留存不公开依据；已有公开内容仍需核对处置';
  if (!row.matchesCurrentSource) return '业务资料已变化，公开稿待更新';
  if (row.feedStoppedAt) return '本版已停止向CMS供稿';
  return row.feedAvailable
    ? '已向CMS提供本版公开稿；实际公开另核对'
    : row.feedEnabledAt
      ? '本版曾向CMS供稿，当前不再供稿'
      : '已核查公开稿，尚未向CMS提供本版';
}
