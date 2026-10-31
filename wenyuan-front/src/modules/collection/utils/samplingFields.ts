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
const unit = {
  ...f('unit', '计量单位', 'select'),
  options: [
    { value: 'MG', label: '毫克 mg' },
    { value: 'G', label: '克 g' },
    { value: 'MM3', label: '立方毫米 mm³' },
    { value: 'CM3', label: '立方厘米 cm³' },
    { value: 'UL', label: '微升 μL' },
    { value: 'ML', label: '毫升 mL' },
  ],
};
const observedCustodyFields: EvidenceField[] = [
  { ...f('destinationKind', '本次查明的保管身份', 'select'), options: [
    { value: 'UNKNOWN', label: '尚未查明，保留未知' },
    { value: 'INTERNAL', label: '本馆实际或历史人员' },
    { value: 'EXTERNAL', label: '已查明的外部单位和人员' },
  ] },
  f('location', '查明的实际保管位置', 'text', false),
  f('userId', '据原件填写查明的馆内人员编号（可为历史人员）', 'text', false),
  f('externalUnit', '查明的外部保管单位', 'text', false),
  f('externalPerson', '查明的外部保管人员姓名', 'text', false),
  f('expectedReturnAt', '有依据的外部归还安排时间', 'datetime', false),
];
export const samplingFields: Record<string, EvidenceField[]> = {
  'continue-extraction': [
    f('requestedAt', '实际登记变更依据时间', 'datetime'),
    f('recorderUserId', '本次实际登记人员', 'user'),
    f('title', '变更后续取样项目名称'),
    f('changeReason', '实际变更原因', 'textarea'),
    f('changedScope', '后续原件取样的变化范围', 'textarea'),
    f('previousWorkAndSamplesAssessment', '既有工作及现存样品能否满足本次需要', 'textarea'),
    f('cumulativeInterventionAssessment', '累计取样数量、位置及原件干预评估', 'textarea'),
    f('changeBasisFileIds', '实际变更依据', 'file'),
    f('assessmentFileIds', '既有样品及累计干预评估原件', 'file'),
  ],
  'analysis-proposal': [
    f('preparedAt', '旧样品分析方案实际形成时间', 'datetime'),
    f('recorderUserId', '实际登记人员', 'user'),
    f('conservationDepartmentId', '保护修复部门', 'group'),
    f('conservationUserId', '本方案保护修复人员', 'user'),
    f('keeperUserId', '本方案保管负责人', 'user'),
    f('changeReason', '实际变更原因', 'textarea'),
    f('researchNeedAndPriorWork', '研究需要及既有工作评估', 'textarea'),
    f('plannedStart', '本方案分析起始时间', 'datetime'),
    f('plannedEnd', '本方案分析截止时间', 'datetime'),
    { ...f('targets', '本方案实际分析范围', 'records'), multiple: true, children: [
      { ...f('portionKey', '分析对象（未分份时留空）', 'select', false), options: [] },
      f('laboratoryName', '批准或核实的分析单位'), f('method', '分析方法', 'textarea'),
      f('maximumConsumption', '本方案累计最多消耗量（使用样品原单位，非消耗填0）'),
      f('remainingSamplePlan', '剩余样品安排', 'textarea'),
    ] },
    { ...f('approvalTreatment', '新增取样批准的实际适用情形', 'select'), options: [
      { value: 'NEW_APPROVAL', label: '须办理新批准' },
      { value: 'EXISTING_APPROVAL_COVERS', label: '原批准覆盖本次分析' },
      { value: 'NOT_APPLICABLE', label: '仅分析已有样品，不适用新增取样批准' },
    ] },
    { ...f('existingApprovalId', '原批准记录（仅原批准覆盖时填写）', 'select', false), options: [] },
    f('applicabilityExplanation', '审批适用情形及覆盖范围的实际依据', 'textarea'),
    f('applicabilityFileIds', '适用依据原件', 'file'),
    f('assessmentFileIds', '样品及研究评估原件', 'file'),
    f('signedPlanFileIds', '共同方案签署原件', 'file'),
  ],
  'analysis-confirm': [f('confirmedAt', '本人实际确认时间', 'datetime'), f('opinion', '本人核实意见', 'textarea'), f('signedFileIds', '本人签署原件', 'file')],
  'analysis-activate': [f('activatedAt', '实际核实并启用时间', 'datetime'), f('verification', '共同确认、审批适用及当前样品核实情况', 'textarea'), f('signedFileIds', '启用核实签署原件', 'file')],
  'analysis-close': [
    f('endedAt', '本方案分析实际结束或停止时间', 'datetime'), f('reviewedAt', '实际结束核查时间', 'datetime'),
    f('recorderUserId', '实际登记人员', 'user'), f('conservationUserId', '本次核查保护修复人员', 'user'), f('keeperUserId', '本次核查保管负责人', 'user'),
    f('completedAsPlanned', '本方案分析是否按计划完成', 'select'),
    f('actualWorkAndResults', '已实际实施的分析与成果', 'textarea'), f('unfinishedWorkAndReason', '未完成工作及原因（确无未完成工作时据实说明）', 'textarea'),
    f('remainingCustodyAndFollowUp', '全部剩余样品保管与后续安排', 'textarea'), f('resultFileIds', '实际成果与现状原件', 'file'), f('signedReviewFileIds', '结束核查签署原件', 'file'),
  ],
  create: [f('collectionId', '取样藏品', 'collection'), f('title', '取样项目名称')],
  plan: [
    f('preparedAt', '共同方案实际形成时间', 'datetime'),
    f('conservationDepartmentId', '保护修复部门', 'group'),
    f('conservationUserId', '保护修复责任人员', 'user'),
    f('keeperUserId', '保管负责人', 'user'),
    {
      ...f('purpose', '保护或研究特殊需要', 'select'),
      options: [
        { value: 'CONSERVATION', label: '藏品保护' },
        { value: 'RESEARCH', label: '科学研究' },
      ],
    },
    f('specialNeed', '本次特殊需要及具体问题', 'textarea'),
    f('nonDestructiveAlternatives', '非有损分析方法评估', 'textarea'),
    f('alternativesFileIds', '非有损方法评估依据', 'file'),
    f('substituteSearchScope', '替代藏品实际调查范围', 'textarea'),
    f('substituteSearchFileIds', '替代调查依据', 'file'),
    {
      ...f('alternatives', '实际替代对象及评估（一级文物必填）', 'records', false),
      multiple: true,
      children: [
        f('description', '候选藏品或未找到对象的具体调查情况', 'textarea'),
        f('samePeriod', '是否同时代', 'select'),
        f('sameType', '是否同类型', 'select'),
        f('sameMaterial', '是否同质地', 'select'),
        f('canReplace', '是否可以替代原件', 'select'),
        f('assessment', '实际评估与不能替代的理由', 'textarea'),
        f('fileIds', '评估签署原件', 'file'),
      ],
    },
    f('originalIndispensable', '本件原件取样是否确实不可替代', 'select'),
    f('indispensabilityReason', '必须取样的实际理由', 'textarea'),
    f('plannedStart', '商定开始时间', 'datetime'),
    f('plannedEnd', '商定结束时间', 'datetime'),
    f('location', '取样场所'),
    {
      ...f('points', '逐部位取样范围及上限', 'records'),
      multiple: true,
      children: [
        f('key', '部位标识'),
        f('location', '批准取样位置'),
        f('locationFileIds', '部位定位影像', 'file'),
        f('material', '取样材料'),
        f('method', '实际计划方法与工具', 'textarea'),
        f('minimumInterventionReason', '最小必要干预理由', 'textarea'),
        f('maximumQuantity', '本部位累计数量上限', 'decimal'),
        unit,
        f('maximumCount', '本部位份数上限', 'number'),
        f('protectionMeasures', '防护措施', 'textarea'),
      ],
    },
    f('laboratoryName', '方案确定的分析单位'),
    f('analysisMethod', '分析方法', 'textarea'),
    f('analysisMayConsumeSample', '分析是否允许消耗样品', 'select'),
    f('remainingSamplePlan', '剩余样品处置及保管方案', 'textarea'),
    f('safetyAndAftercarePlan', '安全与取样后养护方案', 'textarea'),
    f('conditionFileIds', '取样前现状原件', 'file'),
    f('signedJointPlanFileIds', '共同签署方案原件', 'file'),
    f('replacementReason', '订正原因', 'textarea', false),
    f('replacementFileIds', '订正原件', 'file', false),
  ],
  administrative: [
    {
      ...f('level', '实际批准机关层级', 'select'),
      options: [
        { value: 'NATIONAL', label: '国务院文物行政部门' },
        { value: 'PROVINCE', label: '省级文物行政部门' },
      ],
    },
    f('authorityName', '实际批准机关名称'),
    f('documentNo', '批准文号'),
    f('issuedAt', '实际批准日期', 'date'),
    f('fileIds', '行政批准原件', 'file'),
  ],
  start: [
    f('startedAt', '实际开始时间', 'datetime'),
    f('operatorUserId', '实际经办人员', 'user'),
    f('requisitionId', '实际提用单编号（与在库日记二选一）', 'text', false),
    f('onSiteWarehouseLogId', '实际在库日记编号', 'text', false),
    f('originalSuitable', '原件是否适宜', 'select'),
    f('siteSafe', '场所是否安全', 'select'),
    f('safetyChecks', '实际安全核查', 'textarea'),
    f('safetyFileIds', '安全核查原件', 'file'),
    f('signedStartFileIds', '实际开始签署原件', 'file'),
  ],
  take: [
    f('number', '本馆唯一的样品编号'),
    f('pointKey', '本方案部位标识（异常取样据实填写）'),
    f('takenAt', '实际取样时间', 'datetime'),
    f('operatorUserId', '实际经办人员', 'user'),
    f('actualLocation', '实际取样位置'),
    f('quantity', '实际取样数量', 'decimal'),
    unit,
    f('withinApprovedScope', '实际取样是否在批准范围内', 'select'),
    f('unexpectedDamage', '是否发生额外意外损伤', 'select'),
    f('conditionAfter', '取样后真实状况', 'textarea'),
    f('actualMeasures', '实际采取的措施', 'textarea'),
    f('sampleLocation', '实际样品保管位置'),
    f('custodianUserId', '实际样品保管人员', 'user'),
    f('markingFileIds', '实际样品标识影像', 'file'),
    f('conditionFileIds', '取样现状影像原件', 'file'),
    f('signedTakeFileIds', '实际取样签署原件', 'file'),
    f('exceptionExplanation', '范围异常或意外损伤说明', 'textarea', false),
    f('exceptionFileIds', '异常事实及处理依据', 'file', false),
  ],
  'end-extraction': [
    f('endedAt', '实际取样结束时间', 'datetime'),
    f('conservationUserId', '本方案保护修复责任人员', 'user'),
    f('keeperUserId', '本方案保管负责人', 'user'),
    { ...f('sampleIds', '本轮已逐件核对的全部实际样品', 'select', false), multiple: true, options: [] },
    f('conditionAfter', '原件实际现状', 'textarea'),
    f('actualAftercare', '实际取样后养护', 'textarea'),
    f('safeToReleaseOriginal', '是否可按正常结束释放原件操作', 'select'),
    f('conditionFileIds', '原件现状与养护依据', 'file'),
    f('signedEndFileIds', '共同签署结束原件', 'file'),
  ],
  analysis: [
    f('analyzedAt', '实际分析及保管确认时间', 'datetime'),
    f('laboratoryName', '实际分析单位'),
    f('operatorUserId', '实际经办人员', 'user'),
    f('methodAndResult', '实际分析方法和结果', 'textarea'),
    f('consumedQuantity', '实际消耗数量（沿用样品单位）', 'decimal'),
    f('remainingQuantity', '实际剩余数量（沿用样品单位）', 'decimal'),
    f('remainingLocation', '剩余样品实际保管位置', 'text', false),
    f('remainingCustodianUserId', '剩余样品实际保管人员', 'user', false),
    f('reportFileIds', '分析报告和结果原件', 'file'),
    f('signedAnalysisAndCustodyFileIds', '分析及剩余样品保管签署原件', 'file'),
  ],
  'analysis-batch': [
    f('batchNumber', '本份样品唯一的本批分析编号'),
    f('analyzedAt', '本批实际分析时间', 'datetime'),
    f('laboratoryName', '批准方案对应的实际分析单位'),
    f('analystName', '实际分析人姓名（按报告填写）'),
    f('recorderUserId', '本次实际登记人员（本人）', 'user'),
    f('methodAndResult', '本批分析方法与结果', 'textarea'),
    f('consumedQuantity', '本批实际消耗数量（沿用样品单位）', 'decimal'),
    f('remainingQuantity', '本批后剩余数量（前次剩余减本批消耗）', 'decimal'),
    f('remainingLocation', '剩余样品实际位置（保管人员变更另办交接）', 'text', false),
    f('reportFileIds', '本批实际分析报告', 'file'),
    f('signedAnalysisAndCustodyFileIds', '分析及剩余保管签署原件', 'file'),
  ],
  handover: [
    f('handedAt', '实际完成点交时间', 'datetime'),
    f('recorderUserId', '本次实际登记人员（本人）', 'user'),
    f('quantity', '实际交接全部剩余样品数量（沿用样品单位）', 'decimal'),
    { ...f('destinationKind', '实际接收方', 'select'), options: [
      { value: 'INTERNAL', label: '本馆人员接收或收回' },
      { value: 'EXTERNAL', label: '批准分析单位外部人员接收' },
    ] },
    f('location', '实际接收保管位置'),
    f('userId', '本馆接收人员（馆内接收填写）', 'user', false),
    f('externalUnit', '外部实际接收单位（外部接收填写）', 'text', false),
    f('externalPerson', '外部实际接收人姓名', 'text', false),
    f('expectedReturnAt', '外部保管约定归还时间', 'datetime', false),
    f('condition', '交接双方核对的现状及标识', 'textarea'),
    f('purposeAndArrangements', '依据本方案的交接用途、安全与返还安排', 'textarea'),
    f('basisFileIds', '交接安排依据原件', 'file'),
    f('markingFileIds', '本次标识与现状影像', 'file'),
    f('signedBothPartiesFileIds', '交出及接收双方实际签署原件', 'file'),
  ],
  discrepancy: [
    f('observedAt', '实际发现或本次核查时间', 'datetime'),
    f('recorderUserId', '本次实际登记人员（本人）', 'user'),
    f('observedQuantity', '本次实际查见数量（未找到填真实零值，沿用样品单位）', 'decimal'),
    f('identityConfirmed', '样品真实性是否已核实', 'select'),
    f('conditionSafe', '样品现状是否安全', 'select'),
    ...observedCustodyFields,
    f('findings', '实际数量、标识和现状差异或后续核查结果', 'textarea'),
    f('immediateMeasures', '已经采取的停止、保护或查找措施', 'textarea'),
    f('reportingAndFollowUp', '实际报告、后续责任及核查安排', 'textarea'),
    f('evidenceFileIds', '发现或核查的实际证据', 'file'),
    f('signedObservationFileIds', '本次发现或核查签署原件', 'file'),
  ],
  disposition: [
    f('disposedAt', '实际完成非分析处置时间', 'datetime'),
    f('recorderUserId', '本次实际登记人员（本人）', 'user'),
    f('conservationUserId', '本次核实保护修复责任人员', 'user'),
    f('keeperUserId', '本次核实保管负责人', 'user'),
    f('disposedQuantity', '实际非分析处置数量（不计分析消耗）', 'decimal'),
    f('remainingQuantity', '处置后实际剩余数量（沿用样品单位）', 'decimal'),
    f('remainingLocation', '剩余样品实际位置（保管身份变更另办交接）', 'text', false),
    f('methodAndReason', '实际处置方法与原因', 'textarea'),
    f('authorityAndBasis', '适用权限、制度及批准依据（登记不授予许可）', 'textarea'),
    f('actualResultAndDestination', '实际处置结果、去向及相应记录索引', 'textarea'),
    f('remainingConditionAndArrangements', '剩余物现状及后续保管安排（无剩余据实说明）', 'textarea'),
    f('authorizationFileIds', '适用权限与批准依据原件', 'file'),
    f('quantityAndResultFileIds', '数量、现状及实际结果证据', 'file'),
    f('signedDispositionFileIds', '实际处置签署原件', 'file'),
  ],
  'disposition-confirm': [
    f('confirmedAt', '本人实际核实时间', 'datetime'),
    f('opinion', '本人对处置依据、数量、实际去向及剩余保管的意见', 'textarea'),
    f('signedFileIds', '本人签署原件', 'file'),
  ],
  split: [
    f('splitAt', '实际拆分时间', 'datetime'),
    f('recorderUserId', '本次实际登记人员（本人）', 'user'),
    { ...f('portions', '拆分后全部分份（合计等于拆分前数量，单位沿用原样品）', 'records'), multiple: true, children: [
      f('key', '本原样品内未使用过的分份编号（字母、数字、下划线或连字符）'),
      f('quantity', '本份实际数量', 'decimal'),
      f('location', '本份实际保管位置'),
    ] },
    f('reasonAndMethod', '实际拆分理由和方法', 'textarea'),
    f('condition', '拆分后标识与现状核对', 'textarea'),
    f('basisFileIds', '实际拆分依据', 'file'),
    f('markingFileIds', '逐份标识与数量原件', 'file'),
    f('signedSplitAndCustodyFileIds', '实际拆分及保管签署原件', 'file'),
  ],
  'discrepancy-review': [
    { ...f('discrepancySequences', '本次逐项核对的全部未结差异', 'select'), multiple: true, options: [] },
    f('reviewedAt', '实际完成本次调查的时间', 'datetime'),
    f('recorderUserId', '本次实际登记人员（本人）', 'user'),
    f('conservationUserId', '本次核查保护修复责任人员', 'user'),
    f('keeperUserId', '本次核查保管负责人', 'user'),
    f('actualQuantity', '调查核实的实际数量（不自动调整账面量）', 'decimal'),
    f('identityConfirmed', '真实性是否已经核实', 'select'),
    f('safeToContinue', '实际保存状态是否允许继续正常分析', 'select'),
    ...observedCustodyFields,
    f('investigationAndCause', '实际调查过程、原因与逐项核查结论', 'textarea'),
    f('actualMeasures', '已经实际完成的处置措施', 'textarea'),
    f('reportingAndHandling', '实际报告及适用的核查处理结果', 'textarea'),
    f('followUp', '未解决事项及后续保护、查找和复查安排', 'textarea'),
    f('investigationFileIds', '调查与核实证据', 'file'),
    f('reportingAndHandlingFileIds', '报告及核查处理依据', 'file'),
    f('signedReviewFileIds', '本次调查签署原件', 'file'),
    { ...f('custodyHandoverFileIds', '差异期间实际交接及双方签署原件（保管人员或单位变化时填写）', 'file'), required: false },
  ],
  'stopped-review': [
    f('stoppedAt', '实际停止本方案剩余工作的时间', 'datetime'),
    f('reviewedAt', '本次实际核查时间', 'datetime'),
    f('recorderUserId', '本次实际登记人员（本人）', 'user'),
    f('conservationUserId', '本次指定的当前保护修复责任人员', 'user'),
    f('keeperUserId', '本次指定的当前保管负责人', 'user'),
    f('reason', '实际停止原因', 'textarea'),
    f('actualWork', '已实际完成的取样、分析及其他工作', 'textarea'),
    f('unfinishedWork', '尚未实施或未完成的工作（不填为研究完成）', 'textarea'),
    f('originalConditionAndCare', '原藏品实际现状与保护养护', 'textarea'),
    f('sampleCustodyAndFollowUp', '全部样品及分份保管与后续安排', 'textarea'),
    f('authorityAndFutureWork', '停止依据及后续工作应办理的手续', 'textarea'),
    f('collectionArchiveIndex', '停作结案档案索引'),
    f('stoppingBasisFileIds', '实际停止依据原件', 'file'),
    f('actualWorkAndCustodyFileIds', '实际工作与全部样品保管原件', 'file'),
    f('signedReviewFileIds', '本次核查签署原件', 'file'),
  ],
  'stopped-confirm': [
    f('confirmedAt', '本人实际确认时间', 'datetime'),
    f('opinion', '本人对停作、未完成工作及全部样品保管的意见', 'textarea'),
    f('signedFileIds', '本人签署原件', 'file'),
  ],
  'no-sample-review': [
    f('reviewedAt', '实际核查时间', 'datetime'),
    f('recorderUserId', '本次实际登记人员（本人）', 'user'),
    f('conservationUserId', '本方案保护修复责任人员', 'user'),
    f('keeperUserId', '本方案保管负责人', 'user'),
    f('reason', '实际未取得样品及停止原因', 'textarea'),
    f('actualOperations', '已经实际进行的准备与操作', 'textarea'),
    f('conditionAfter', '原藏品实际现状', 'textarea'),
    f('actualAftercare', '已经完成的保护养护', 'textarea'),
    f('followUp', '后续保护、归还及适用手续安排', 'textarea'),
    f('collectionArchiveIndex', '本次藏品档案索引'),
    f('noSampleEvidenceFileIds', '确未取得样品的核查依据', 'file'),
    f('conditionFileIds', '原藏品现状原件', 'file'),
    f('signedReviewFileIds', '本次核查签署原件', 'file'),
  ],
  'no-sample-confirm': [
    f('confirmedAt', '本人实际确认时间', 'datetime'),
    f('opinion', '本人对未取得样品及结案依据的核实意见', 'textarea'),
    f('signedFileIds', '本人签署原件', 'file'),
  ],
  'discrepancy-confirm': [
    f('confirmedAt', '本人实际核实时间', 'datetime'),
    f('opinion', '本人对调查事实、处置及仍存问题的意见', 'textarea'),
    f('signedFileIds', '本人签署原件', 'file'),
  ],
  'clear-incident': [
    f('incidentId', '已完成实际处置的事故案卷编号'),
    f('confirmedAt', '实际共同核实解限时间', 'datetime'),
    f('conservationUserId', '本方案保护修复责任人员', 'user'),
    f('keeperUserId', '本方案保管负责人', 'user'),
    f('condition', '处置后原件真实现状', 'textarea'),
    f('actualMeasures', '实际保护及后续措施', 'textarea'),
    f('signedFileIds', '共同核实解限签署原件', 'file'),
  ],
  complete: [
    f('completedAt', '实际共同复核时间', 'datetime'),
    f('conservationUserId', '本方案保护修复责任人员', 'user'),
    f('keeperUserId', '本方案保管负责人', 'user'),
    { ...f('sampleIds', '本轮已逐件核对的全部样品', 'select'), multiple: true, options: [] },
    f('review', '原件、分析、非分析处置或差异处理及剩余保管共同复核', 'textarea'),
    f('collectionArchiveIndex', '本件藏品档案索引'),
    f('signedReviewFileIds', '共同复核签署原件', 'file'),
  ],
};
export const samplingFinalAccountingFields: EvidenceField[] = [
  { ...f('accountingKind', '本次实际处理记账类别', 'select'), options: [
    { value: 'SHORTAGE', label: '短缺按实际处理依据记账（不表示已寻回）' },
    { value: 'EXCESS', label: '多出且已查明属于本份样品的数量记账' },
    { value: 'NON_ANALYSIS_DISPOSITION', label: '差异调查查明的实际非分析处置' },
  ] },
  f('accountingDecidedAt', '实际处理决定时间', 'datetime'),
  f('accountingAuthorityAndBasis', '实际决定单位、适用权限和制度依据', 'textarea'),
  f('accountingDecisionNo', '实际处理决定编号或档案索引'),
  f('accountingDecisionFileIds', '实际处理决定及权限依据原件', 'file'),
  f('accountingFollowUpResponsibility', '仍存问题、持续查找或保护责任及复查安排', 'textarea'),
  f('accountingFollowUpFileIds', '后续责任和跟进依据原件', 'file'),
];
samplingFields['analysis-existing'] = samplingFields['analysis-batch'];
samplingFields['analysis-administrative'] = samplingFields.administrative;
samplingFields['discrepancy-finalize'] = [...samplingFields['discrepancy-review'], ...samplingFinalAccountingFields];
export const samplingTitles: Record<string, string> = {
  'analysis-proposal': '登记旧样品分析变更方案', 'analysis-confirm': '本人核实分析方案或结束',
  'analysis-administrative': '登记本分析方案新批准', 'analysis-activate': '核实并启用旧样品分析方案',
  'analysis-existing': '按变更方案登记实际分析', 'analysis-close': '登记本分析方案实际结束', 
  'continue-extraction': '关联前案并申请变更后续取样',
  disposition: '登记样品实际非分析处置',
  'disposition-confirm': '本人核实非分析处置',
  split: '登记样品实际拆分与分份保管',
  discrepancy: '登记样品差异或追加实际核查',
  'discrepancy-review': '登记差异调查与处置',
  'discrepancy-finalize': '登记差异最终记账依据',
  'discrepancy-confirm': '本人核实本次调查',
  'stopped-review': '登记实际停作结案核查',
  'stopped-confirm': '本人确认停作结案',
  'no-sample-review': '登记未取得样品结案核查',
  'no-sample-confirm': '本人确认未取得样品结案',
  'analysis-batch': '追加本批实际分析',
  handover: '登记样品实际交接或归还',
  'clear-incident': '核对实际事故处置并共同解限',
  create: '新增有损取样申请',
  plan: '登记实际共同方案',
  administrative: '登记真实行政批文',
  start: '登记实际取样开始',
  take: '登记实际取样与样品',
  'end-extraction': '登记实际取样结束',
  analysis: '登记实际分析与剩余保管',
  complete: '登记实际共同复核',
};
const booleanKeys = [
  'completedAsPlanned',
  'identityConfirmed',
  'conditionSafe',
  'safeToContinue',
  'samePeriod',
  'sameType',
  'sameMaterial',
  'canReplace',
  'originalIndispensable',
  'analysisMayConsumeSample',
  'originalSuitable',
  'siteSafe',
  'withinApprovedScope',
  'unexpectedDamage',
  'safeToReleaseOriginal',
];
export function samplingValues(value: Record<string, any>): Record<string, any> {
  const out = { ...value };
  for (const key of booleanKeys) {
    if (out[key] === 'YES') out[key] = true;
    else if (out[key] === 'NO') out[key] = false;
  }
  if (Array.isArray(out.alternatives)) out.alternatives = out.alternatives.map(samplingValues);
  if (Array.isArray(out.points)) out.points = out.points.map((p) => ({ ...p }));
  return out;
}

export function samplingHandoverValues(value: Record<string, any>, previousRevision: string) {
  const { destinationKind, location, userId, externalUnit, externalPerson, expectedReturnAt, ...rest } = value;
  return { ...rest, previousRevision, destination: { kind: destinationKind, location,
    ...(destinationKind === 'INTERNAL' ? { userId } : { externalUnit, externalPerson, expectedReturnAt }),
  } };
}

// 服务端数量非负；用十进制尾数判断剩余，避免微量样品转成 JS Number 后下溢为零。
export function samplingHasRemainder(value: unknown): boolean {
  const text = String(value ?? '');
  return /^(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[eE][+-]?[0-9]+)?$/.test(text)
    && /[1-9]/.test(text.split(/[eE]/)[0]);
}

export function samplingDiscrepancyValues(value: Record<string, any>, previousRevision: string, kind: string) {
  const { destination, ...rest } = samplingHandoverValues(value, previousRevision);
  const custody = value.destinationKind === 'UNKNOWN' ? null : destination;
  return kind === 'discrepancy'
    ? { ...rest, observedCustody: custody }
    : { ...rest, discrepancySequences: (value.discrepancySequences || []).map(Number), actualCustody: custody };
}


export function samplingFinalAccountingValues(value: Record<string, any>, previousRevision: string) {
  const { accountingKind, accountingDecidedAt, accountingAuthorityAndBasis, accountingDecisionNo,
    accountingDecisionFileIds, accountingFollowUpResponsibility, accountingFollowUpFileIds, ...rest } = value;
  return { ...samplingDiscrepancyValues(rest, previousRevision, 'discrepancy-review'), finalAccounting: {
    kind: accountingKind, decidedAt: accountingDecidedAt, authorityAndBasis: accountingAuthorityAndBasis,
    decisionNo: accountingDecisionNo, decisionFileIds: accountingDecisionFileIds,
    followUpResponsibility: accountingFollowUpResponsibility, followUpFileIds: accountingFollowUpFileIds,
  } };
}
export function samplingFinalAccountingDisplay(value: Record<string, any>) {
  const v = value.finalAccounting;
  return v ? { ...value, accountingKind: v.kind, accountingDecidedAt: v.decidedAt,
    accountingAuthorityAndBasis: v.authorityAndBasis, accountingDecisionNo: v.decisionNo,
    accountingDecisionFileIds: v.decisionFileIds, accountingFollowUpResponsibility: v.followUpResponsibility,
    accountingFollowUpFileIds: v.followUpFileIds } : value;
}
