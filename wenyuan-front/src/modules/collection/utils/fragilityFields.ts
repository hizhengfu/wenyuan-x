import type { EvidenceField } from './evidenceFields';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
});
const kinds = [
  { value: 'DISPLAY', label: '实际展出' },
  { value: 'TRANSPORT', label: '实际运输' },
];
export const fragilityFields: Record<string, EvidenceField[]> = {
  assessment: [
    f('assessedAt', '实际评估时间', 'datetime'),
    {
      ...f('fragileMaterial', '是否属于材质脆弱藏品', 'select'),
      options: [
        { value: 'YES', label: '是' },
        { value: 'NO', label: '否（须有实际评估依据）' },
      ],
    },
    f('materialAndCondition', '实际材质、保存状况及判断依据', 'textarea'),
    f('environmentRequirements', '严格保存环境要求与监测依据', 'textarea'),
    {
      ...f('environmentCriteria', '逐类数值环境要求（脆弱材质按实际专业评估填写）', 'records', false),
      multiple: true,
      children: [
        { ...f('kind', '使用类型', 'select'), options: kinds },
        f('metricCode', '指标标识（联网时与监测指标一致）'),
        f('metricName', '指标名称'),
        f('unit', '评估单位'),
        f('minimum', '允许下限（可仅填上限）', 'decimal', false),
        f('maximum', '允许上限（可仅填下限）', 'decimal', false),
        f('maximumSampleAgeSeconds', '采样最多有效秒数', 'number'),
        f('basis', '本件限值、有效期及指标覆盖的专业依据', 'textarea'),
      ],
    },
    f('storageProtection', '科学存放及有效防护措施', 'textarea'),
    f('useRiskAssessment', '使用风险、减少使用及替代方案评估', 'textarea'),
    f('conservationUserId', '保护修复责任人员', 'user'),
    f('keeperUserId', '保管部门负责人', 'user'),
    f('historyVerifiedFrom', '已核对使用历史的起始时间', 'datetime'),
    f('historyReviewFileIds', '完整窗口历史使用核对原件', 'file'),
    f('assessmentFileIds', '实际材质与保护评估原件', 'file'),
    f('windowDays', '滚动统计窗口（天，仅脆弱材质填写）', 'number', false),
    f('maximumExtractions', '窗口内最多提取次数，0为禁止', 'number', false),
    f('maximumTransports', '窗口内最多运输次数，0为禁止', 'number', false),
    f('maximumDisplayHours', '窗口内累计最多展出小时，0为禁止', 'number', false),
    f('minimumRestHours', '两次展出之间至少间隔小时', 'number', false),
  ],
  plan: [
    f('displayFrom', '本次计划实际展出开始（展览用途必填）', 'datetime', false),
    f('displayTo', '本次计划实际展出结束（展览用途必填）', 'datetime', false),
    {
      ...f('transports', '逐段运输安排（外借须含出借和返回）', 'records', false),
      multiple: true,
      children: [
        f('departsAt', '计划出发时间', 'datetime'),
        f('arrivesAt', '计划到达时间', 'datetime'),
        f('route', '实际计划路线'),
        f('protection', '本段保护措施', 'textarea'),
      ],
    },
    f('environmentAndProtection', '本次环境控制、支撑防护与监测安排', 'textarea'),
    {
      ...f('environmentSources', '每段各项测量的指定来源', 'records'),
      multiple: true,
      children: [
        { ...f('kind', '使用类型', 'select'), options: kinds },
        f('segmentIndex', '安排顺序（展出为0，运输从0依次填写）', 'number'),
        f('metricCode', '本件评估指标', 'select'),
        {
          ...f('type', '测量来源', 'select'),
          options: [
            { value: 'REGISTERED_DEVICE', label: '固定展出位置的联网监测' },
            { value: 'SIGNED_ON_SITE', label: '现场签署测量（含运输及馆外）' },
          ],
        },
        f('context', '指定实际场所或路线（运输须与本段路线一致）'),
        f('instrumentAndMethod', '指定仪器标识与测量方法', 'textarea'),
        f('representativeness', '测点对本件展柜或包装环境的代表性及现场核对', 'textarea'),
        f('qualificationFileIds', '实际仪器校准、测量能力及位置核对原件', 'file'),
        f('deviceId', '已确认监测设备标识', 'text', false),
        f('locationId', '已确认监测位置标识', 'text', false),
      ],
    },
    f('necessityAndAlternatives', '必要性、替代方式与减少使用措施', 'textarea'),
    f('signedPlanFileIds', '本次使用保护方案原件', 'file'),
  ],
  adjustment: [
    f('reviewedAt', '实际变更核查时间', 'datetime'),
    f('checkId', '本件最新实际现状检查', 'select'),
    f('reason', '实际变更原因及适用手续核对', 'textarea'),
    f('actualUnstartedEvidence', '所选段从未实际开始的核查经过及依据', 'textarea'),
    {
      ...f('adjustments', '逐段变更内容', 'records'),
      multiple: true,
      children: [
        { ...f('kind', '原安排类型', 'select'), options: kinds },
        f('segmentIndex', '原安排顺序', 'number'),
        {
          ...f('action', '本段实际处理', 'select'),
          options: [
            { value: 'CANCEL', label: '核实未实施并取消' },
            { value: 'RESCHEDULE', label: '在原批准范围内重新安排' },
          ],
        },
        f('reason', '本段真实原因', 'textarea'),
        f('from', '新的计划开始（重新安排时填写）', 'datetime', false),
        f('to', '新的计划结束（重新安排时填写）', 'datetime', false),
        f('route', '新的运输路线（重新安排运输时填写）', 'text', false),
        f('protection', '新的保护措施（重新安排时填写）', 'textarea', false),
      ],
    },
    f('signedFileIds', '实际核查及变更方案原件', 'file'),
    f('borrowerAgreementFileIds', '借用双方实际签署变更依据（外借重新安排必填）', 'file', false),
  ],
  adjustmentSign: [f('opinion', '本人对冻结核查及变更方案的意见', 'textarea'), f('fileIds', '本人确认原件', 'file')],
  adjustmentWithdraw: [
    f('withdrawnAt', '实际撤回时间', 'datetime'),
    f('reason', '尚未生效变更的真实撤回原因', 'textarea'),
    f('signedFileIds', '本人撤回依据', 'file'),
  ],

  sign: [f('opinion', '本人核对意见', 'textarea'), f('fileIds', '本人确认原件', 'file')],
  start: [
    { ...f('kind', '实际开始类型', 'select'), options: kinds },
    f('segmentIndex', '本次安排顺序', 'number'),
    f('startedAt', '实际开始时间', 'datetime'),
    f('operatorUserId', '馆方实际操作或监督记录人员（本人）', 'user'),
    f('actualLocationOrRoute', '实际位置或路线'),
    f('actualEnvironmentAndProtection', '实际环境、防护和安全核对', 'textarea'),
    {
      ...f('environmentReadings', '本段现场签署测量（联网指标由服务器核验）', 'records', false),
      multiple: true,
      children: [
        f('metricCode', '已评估指标'),
        f('value', '真实测值', 'decimal'),
        f('unit', '实际单位'),
        f('measuredAt', '实际测量时间（须在开始前）', 'datetime'),
        f('recordedAt', '原始签署测量记录形成时间（按原件填写）', 'datetime', false),
        f('instrumentAndMethod', '实际使用的已指定仪器及方法'),
        f('context', '实际测量场所或路线'),
        f('measuredBy', '原件载明的实际测量人'),
        f('signedFileIds', '本项真实测量签署原件', 'file'),
      ],
    },
    f('signedFileIds', '实际开始原件', 'file'),
  ],
  end: [
    f('endedAt', '实际结束时间（如实记录，超时也须保留）', 'datetime'),
    f('actualLocation', '实际结束后位置'),
    f('actualResult', '实际结果、偏差及已采取措施', 'textarea'),
    f('signedFileIds', '实际结束原件', 'file'),
  ],
  review: [f('checkId', '本件实际结束后的日常检查记录编号'), f('signedFileIds', '保管负责人核对原件', 'file')],
  history: [
    { ...f('kind', '既有事实类型', 'select'), options: kinds },
    f('startedAt', '实际开始时间', 'datetime'),
    f('endedAt', '实际结束时间', 'datetime'),
    f('reference', '既有记录编号及原件索引'),
    f('actualDetails', '实际经过、时长或路线', 'textarea'),
    f('fileIds', '实际既有事实原件', 'file'),
  ],
};
fragilityFields.monitor = [
  f('checkedAt', '实际核验时间', 'datetime'),
  f('operatorUserId', '实际核验人员（本人）', 'user'),
  {
    ...f('physicallyPresent', '是否实际核实实物在场', 'select'),
    options: [
      { value: 'YES', label: '是' },
      { value: 'NO', label: '否' },
    ],
  },
  f('custodianUserId', '实际保管人员（在场时必填）', 'user', false),
  f('actualCustodyLocation', '核验时实际保管位置'),
  f('condition', '核验时实际保存现状', 'textarea'),
  {
    ...f('damaged', '是否实际发现损伤', 'select'),
    options: [
      { value: 'YES', label: '是' },
      { value: 'NO', label: '否' },
    ],
  },
  f('actualMeasures', '实际采取的措施及原始记录矛盾说明', 'textarea'),
  f('followUp', '持续监测及后续处理安排', 'textarea'),
  {
    ...f('readings', '本次新增的现场测量原件（联网来源由服务器读取）', 'records', false),
    multiple: true,
    children: [
      f('metricCode', '本段批准指标', 'select'),
      f('value', '真实测值', 'decimal'),
      f('unit', '实际单位'),
      f('measuredAt', '实际测量时间', 'datetime'),
      f('recordedAt', '原始测量记录形成时间（无依据留空，按核验时才可用）', 'datetime', false),
      f('instrumentAndMethod', '实际仪器及方法', 'select'),
      f('context', '实际测量环境或路线', 'select'),
      f('measuredBy', '原件测量人'),
      f('signedFileIds', '本项真实测量原件', 'file'),
    ],
  },
  f('signedFileIds', '本次核验及现状签署原件', 'file'),
];
export const fragileMonitoringEvidenceFields: EvidenceField[] = [
  f('from', '本次核验区间开始', 'datetime'),
  f('to', '本次核验区间结束', 'datetime'),
  f('scope', '证据范围'),
  f('issues', '需核实的问题'),
  {
    ...f('metrics', '逐项采样证据及缺口', 'records'),
    multiple: true,
    children: [
      f('metricName', '指标'),
      f('unit', '单位'),
      f('issues', '异常及缺失依据'),
      {
        ...f('spans', '证据时间段', 'records'),
        multiple: true,
        children: [
          f('from', '起始', 'datetime'),
          f('to', '结束', 'datetime'),
          {
            ...f('state', '证据状态', 'select'),
            options: [
              { value: 'AVAILABLE', label: '当时已有有效采样证据' },
              { value: 'GAP', label: '监测证据缺口' },
              { value: 'EXCEEDED', label: '超出本件数值要求' },
              { value: 'UNVERIFIED', label: '证据无法核实' },
            ],
          },
          f('reason', '依据'),
        ],
      },
    ],
  },
];
export const fragilityTitles: Record<string, string> = {
  monitor: '登记使用期间环境及实物核验',
  monitorClear: '依据事故处置核对本次使用监测异常',
  assessment: '材质脆弱性与使用限值评估',
  plan: '本次使用保护方案',
  sign: '本人确认实际评估或使用方案',
  start: '记录实际展出或运输开始',
  end: '记录实际结束',
  review: '核对使用后实际检查',
  history: '据实补录既有展出或运输',
  adjustment: '核实未实施段并办理实际使用变更',
  adjustmentSign: '本人确认实际使用变更',
  adjustmentWithdraw: '撤回尚未生效的使用变更',
};
export function fragilityValues(v: Record<string, any>): Record<string, any> {
  return {
    ...v,
    ...Object.fromEntries(
      ['physicallyPresent', 'damaged'].filter((k) => ['YES', 'NO'].includes(v[k])).map((k) => [k, v[k] === 'YES']),
    ),
    ...(v.readings ? { readings: v.readings.map((r: any) => ({ ...r, value: decimal(r.value, true) })) } : {}),
    ...(v.environmentCriteria
      ? {
          environmentCriteria: v.environmentCriteria.map((r: any) => ({
            ...r,
            metricCode: String(r.metricCode || '')
              .trim()
              .toUpperCase(),
            minimum: decimal(r.minimum, false),
            maximum: decimal(r.maximum, false),
          })),
        }
      : {}),
    ...(v.environmentReadings
      ? { environmentReadings: v.environmentReadings.map((r: any) => ({ ...r, value: decimal(r.value, true) })) }
      : {}),
    ...(['YES', 'NO'].includes(v.fragileMaterial) ? { fragileMaterial: v.fragileMaterial === 'YES' } : {}),
  };
}
export const limitKeys = [
  'windowDays',
  'maximumExtractions',
  'maximumTransports',
  'maximumDisplayHours',
  'minimumRestHours',
];

function decimal(value: unknown, required: boolean): number | undefined {
  if (value === undefined || value === null || value === '') {
    if (required) throw new Error('请填写真实数值，空白不能作为0');
    return undefined;
  }
  if ((typeof value !== 'string' && typeof value !== 'number') || String(value).trim() === '')
    throw new Error('请填写有效数值');
  const n = Number(value);
  if (!Number.isFinite(n)) throw new Error('测值与上下限须为有效有限数值');
  return n;
}
export const fragileEnvironmentVerificationFields: EvidenceField[] = [
  f('evaluatedAt', '服务器实际核验时间', 'datetime'),
  f('scope', '核验范围'),
  {
    ...f('metrics', '逐项冻结核验依据', 'records'),
    multiple: true,
    children: [
      f('criterion.metricName', '评估指标'),
      f('criterion.minimum', '本件允许下限'),
      f('criterion.maximum', '本件允许上限'),
      f('criterion.maximumSampleAgeSeconds', '本件采样有效秒数'),
      f('value', '开始前核验测值'),
      f('unit', '单位'),
      f('measuredAt', '开始前实际采样时间', 'datetime'),
      f('source.context', '批准测量环境'),
      f('source.instrumentAndMethod', '批准仪器及方法'),
      f('source.representativeness', '本件环境代表性依据'),
      f('source.qualificationFileIds', '已封存来源资质及位置原件', 'file'),
      f('evidence.signedOnSite.measuredBy', '现场原件测量人'),
      f('evidence.signedOnSite.signedFileIds', '现场测量原件', 'file'),
      f('evidence.startSample.fact.id', '开始前联网事实编号'),
      f('evidence.currentSample.fact.id', '登记时联网事实编号'),
      f('evidence.currentSample.fact.numeric_value_0', '登记时联网测值'),
      f('evidence.currentSample.fact.occurred_at', '登记时联网采样时间（UTC）'),
      f('evidence.currentSample.threshold.sourceIds', '冻结的生效规则及版本依据'),
    ],
  },
];
