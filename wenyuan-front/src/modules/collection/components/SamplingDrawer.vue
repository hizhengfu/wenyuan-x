<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="有损取样方案与实际实施"
    ><NSpin :show="loading"
      ><NSpace vertical :size="16">
        <NAlert type="info"
          >有损取样限于保护或科学研究的特殊需要，应先评估非有损方法和替代藏品。共同确认后还须取得实际行政批准。批准范围内的材料取样与额外意外损伤分开登记。</NAlert
        >
        <NAlert v-if="readError" type="error">{{ readError }}</NAlert>
        <NAlert v-if="data.sourceIssue" type="warning">{{ data.sourceIssue }}；既有取样档案继续保留。</NAlert>
        <NAlert v-if="data.predecessorIssue" type="warning">{{ data.predecessorIssue }}；请核实前案关联原件。</NAlert>
        <NCard v-if="data.predecessor" title="本次变更的前案依据">
          <NAlert type="info">本申请须重新登记共同方案、本人确认和适用行政批准。前案样品仍在原案卷管理；仅调整旧样品分析从前案样品的分析变更入口办理，本申请不能代替该手续。</NAlert>
          <NButton @click="open(data.predecessor.predecessorId)">查阅前案与现存样品</NButton>
          <NAlert v-if="data.predecessorOriginalIssue" type="warning">{{ data.predecessorOriginalIssue }}</NAlert>
          <CollectionEvidenceReadView :archive-files="data.predecessorOriginal?.files" :original-url="predecessorOriginalUrl" :fields="samplingFields['continue-extraction']" :data="data.predecessor.request" />
        </NCard>
        <NAlert v-if="latest?.continuationIssue" type="error">{{ latest.continuationIssue }}；请核实前后案卷原件。</NAlert>
        <NCard v-if="latest?.successor" title="已关联的变更后续申请">
          <NText>后续申请不会改变本案实际完成或停作状态，本案样品继续在此管理。</NText>
          <NButton @click="open(latest.successor.successorId)">查阅变更后续取样申请</NButton>
          <CollectionEvidenceReadView :archive-files="stageFiles(latest, `CONTINUATION_${latest.successor.successorId}`)" :original-url="phaseOriginalUrl(latest, `CONTINUATION_${latest.successor.successorId}`)" :fields="samplingFields['continue-extraction']" :data="latest.successor.request" />
        </NCard>
        <NText
          >{{ data.title }}；批准要求：{{
            latest?.authority === 'NATIONAL'
              ? '国务院文物行政部门'
              : latest
                ? '省级文物行政部门'
                : '先核实藏品性质及等级'
          }}</NText
        >
        <NAlert
          v-if="(hasException || latest?.ended?.safeToReleaseOriginal === false) && !latest?.incidentClearance"
          type="error"
          >实际取样存在异常待处置，原件操作仍保持锁定。请保留真实报告及处理依据，不能用正常完成结论覆盖。</NAlert
        >
        <NAlert v-if="latest?.incidentClearance" type="info"
          >已留存事故处置和共同解限原件，历史异常及当时不安全结束记录继续保留。来源事故新增事实可能使原解限依据失效。</NAlert
        >
        <NSpace
          v-if="latest?.started && (hasException || latest?.ended?.safeToReleaseOriginal === false)"
          v-permission="'COLLECTION_ADMIN,COLLECTION_INCIDENT_MANAGE'"
          ><NButton @click="createIncident">登记事故报告与处置</NButton
          ><NButton
            v-if="latest.incidentClearance"
            @click="incidentRef?.open(latest.incidentClearance.record.incidentId)"
            >查阅关联事故案卷</NButton
          ></NSpace
        >
        <NAlert v-if="latest?.stoppedClosureIssue" type="error">{{ latest.stoppedClosureIssue }}；请核实停作原件，不能继续办理。</NAlert>
        <NAlert v-if="latest?.stoppedClosure?.closedAt" type="info">本方案已停作结案，尚未完成的研究保留真实状态。原方案、实际工作和结案时样品继续保留；后续保管另行追加，新的实施须办理适用方案手续。</NAlert>
        <NAlert v-else-if="latest?.stoppedClosure?.reviewSequence" type="warning">已登记实际停作，停止新增分析及研究完成登记。全部样品仍须保管；保管事实变化后重新核查，再由两人分别确认停作结案。</NAlert>
        <NAlert v-if="latest?.noSampleClosureIssue" type="error">{{ latest.noSampleClosureIssue }}；不能继续办理未取得样品结案。</NAlert>
        <NAlert v-if="latest?.noSampleClosure?.closedAt" type="info">已由两名方案责任人分别确认未取得样品结案。原开始、结束及核查原件继续保留，没有生成样品或分析完成记录；原藏品归还仍按实际手续办理。</NAlert>
        <NSpace v-if="canOperate" v-permission="'COLLECTION_ADMIN,COLLECTION_SAMPLING_MANAGE'">
          <NButton v-if="latest?.continuationBasisRevision && !latest.successor && !latest.continuationIssue" @click="edit('continue-extraction')">申请变更后续原件取样</NButton>
          <NButton v-if="canPlan" @click="edit('plan')">{{ latest ? '订正实际共同方案' : '登记实际共同方案' }}</NButton>
          <NButton v-if="latest && !latest.started" @click="edit('administrative')">登记真实行政批文</NButton>
          <NButton
            v-if="latest && !latest.started && ['DRAFT', 'REJECT', 'CANCEL'].includes(data.flowStatus)"
            @click="submit"
            >提交共同确认</NButton
          >
          <template v-if="latest && data.flowStatus === 'PASS' && !latest.started"
            ><NButton @click="requisition">办理原件提用</NButton
            ><NButton v-if="canStart" @click="edit('start')">登记实际取样开始</NButton></template
          >
          <template v-if="latest?.started && !latest.ended"
            ><NButton v-if="!hasException" @click="edit('take')">登记实际取样与样品</NButton
            ><NButton @click="edit('end-extraction')">登记实际取样结束</NButton></template
          >
          <NButton
            v-if="latest?.ended?.safeToReleaseOriginal === false && !latest?.incidentClearance && !latest?.completed"
            @click="edit('clear-incident')"
            >核对事故处置并共同解限</NButton
          >
          <template v-if="latest?.noSampleClosure?.eligible && !latest.noSampleClosure.closedAt && !latest.noSampleClosureIssue && (latest.ended?.safeToReleaseOriginal || latest.incidentClearance)">
            <NButton @click="edit('no-sample-review')">登记未取得样品结案核查</NButton>
            <NButton v-if="canConfirm('no-sample-confirm')" @click="edit('no-sample-confirm')">本人确认未取得样品结案</NButton>
          </template>
          <template v-if="latest?.stoppedClosure?.eligible && !latest.stoppedClosure.closedAt && !latest.stoppedClosureIssue && (latest.ended?.safeToReleaseOriginal || latest.incidentClearance)">
            <NButton @click="edit('stopped-review')">登记实际停作结案核查</NButton>
            <NButton v-if="canConfirm('stopped-confirm')" @click="edit('stopped-confirm')">本人确认停作结案</NButton>
          </template>
          <NButton
            v-if="
              (latest?.ended?.safeToReleaseOriginal || latest?.incidentClearance) &&
              !isClosed(latest) && !latest.stoppedClosure?.reviewSequence && !latest.stoppedClosureIssue &&
              latest.samples?.length &&
              latest.samples.every((s: any) => !s.journalIssue && (s.journal ? s.journal.readyForCompletion : s.analysis))
            "
            @click="edit('complete')"
            >登记实际共同复核</NButton
          >
        </NSpace>
        <NCard v-for="plan in [...(data.plans || [])].reverse()" :key="plan.id" :title="`第 ${plan.version} 版方案`"
          ><NAlert v-for="issue in plan.archiveIssues || []" :key="issue" type="error">{{ issue }}；请核对原件，损坏档案暂不提供下载。</NAlert><NTabs type="line">
            <NTabPane name="plan" tab="共同方案与批文"
              ><CollectionEvidenceReadView :archive-files="stageFiles(plan, 'PLAN')" :original-url="phaseOriginalUrl(plan, 'PLAN')"
                :fields="samplingFields.plan"
                :data="plan.content" /><CollectionEvidenceReadView :archive-files="stageFiles(plan, `ADMIN_${a.id}`)" :original-url="phaseOriginalUrl(plan, `ADMIN_${a.id}`)"
                v-for="a in plan.administrative"
                :key="a.id"
                :fields="samplingFields.administrative"
                :data="a"
            /></NTabPane>
            <NTabPane name="execution" tab="实物取样与结束"
              ><CollectionEvidenceReadView :archive-files="stageFiles(plan, 'STARTED')" :original-url="phaseOriginalUrl(plan, 'STARTED')"
                v-if="plan.started"
                :fields="samplingFields.start"
                :data="plan.started" /><CollectionEvidenceReadView :archive-files="stageFiles(plan, 'EXTRACTION_ENDED')" :original-url="phaseOriginalUrl(plan, 'EXTRACTION_ENDED')"
                v-if="plan.ended"
                :fields="samplingFields['end-extraction']"
                :data="plan.ended" /><CollectionEvidenceReadView :archive-files="stageFiles(plan, 'EXCEPTION_CLEARED')" :original-url="phaseOriginalUrl(plan, 'EXCEPTION_CLEARED')"
                v-if="plan.incidentClearance"
                :fields="samplingFields['clear-incident']"
                :data="plan.incidentClearance.record"
            /></NTabPane>
            <NTabPane name="samples" tab="样品与分析保管"
              ><NCard v-for="sample in displaySamples(plan)" :key="sample.id" :title="sample.content.number"
                ><CollectionEvidenceReadView :archive-files="stageFiles(plan, `SAMPLE_${sample.id}`)" :original-url="phaseOriginalUrl(plan, `SAMPLE_${sample.id}`)"
                  :fields="samplingFields.take"
                  :data="sample.content"
                /><CollectionEvidenceReadView :archive-files="stageFiles(plan, `ANALYSIS_${sample.id}`)" :original-url="phaseOriginalUrl(plan, `ANALYSIS_${sample.id}`)"
                  v-if="sample.analysis"
                  :fields="samplingFields.analysis"
                  :data="sample.analysis"
                />
                <NAlert v-if="isClosed(plan)" type="info">{{ plan.stoppedClosure?.closedAt ? '本方案已停作结案，未完成研究保留真实状态。' : '本项目已共同复核。' }}下方现存数量和保管显示后续记录，原结案依据继续保留；后续保管本身不授予分析许可；旧样品分析须另行登记并启用分析变更方案；后续差异由本次指定且当前具备职责的保护修复人员和保管负责人分别核实。</NAlert>
                <NText v-if="isClosed(plan) && sample.frozenJournal">结案时剩余：{{ sample.frozenJournal.remainingQuantity }} {{ sample.frozenJournal.unit }}；当时保管：{{ Object.keys(sample.frozenJournal.portions || {}).length ? '按结案分份分别保管' : sample.frozenJournal.custody.location }}。</NText>
                <NText v-for="p in Object.values(sample.frozenJournal?.portions || {}) as any[]" :key="p.key">结案分份 {{ p.key }}：{{ p.remainingQuantity }} {{ sample.frozenJournal.unit }}；{{ p.custody.location }}。</NText>
                <NAlert v-if="sample.journalIssue" type="error">{{ sample.journalIssue }}；请核对原件，当前不能继续办理样品分析、保管或共同完成。</NAlert>
                <template v-if="sample.journal">
                  <NAlert v-if="sample.journal.finalAccountingCount" type="info">已按实际处理依据分别核实记账 {{ sample.journal.finalAccountingCount }} 次；历史累计短缺：{{ sample.journal.accountedShortageQuantity }} {{ sample.journal.unit }}，历史累计多出：{{ sample.journal.accountedExcessQuantity }} {{ sample.journal.unit }}。记账不表示短缺已经寻回，后续发现继续登记差异；原藏品总账及事故手续不因此改变。</NAlert>
                  <NText v-if="samplingHasRemainder(sample.journal.nonAnalysisDisposedQuantity)">累计已核实非分析处置：{{ sample.journal.nonAnalysisDisposedQuantity }} {{ sample.journal.unit }}；未计为分析消耗。</NText>
                  <NAlert v-if="sample.journal.dispositionPending" type="warning">非分析处置尚待两人分别核实。账面数量暂未减记，请核对实际处置及剩余事实；正常分析、交接、分份和共同完成已暂停。登记不授予处置许可，不改变原藏品总账。</NAlert>
                  <NText>账面剩余数量：{{ sample.journal.remainingQuantity }} {{ sample.journal.unit }}；最近有依据的保管位置：{{ Object.keys(sample.journal.portions || {}).length ? '见各分份记录' : sample.journal.custody.location }}</NText>
                  <NAlert v-if="!Object.keys(sample.journal.portions || {}).length && sample.journal.custody.kind === 'EXTERNAL' && !sample.journal.readyForCompletion" type="warning">
                    样品仍由外部单位 {{ sample.journal.custody.externalUnit }} 的 {{ sample.journal.custody.externalPerson }} 保管。<template v-if="isClosed(plan)">请跟踪实际归还；原结案记录不表示后续样品已经归还。</template><template v-else>须核对实际归还、批准分析消耗或有依据且已分别核实的非分析处置，不能直接共同完成。</template>
                  </NAlert>
                  <NAlert v-if="sample.journal.openDiscrepancySequences?.length" type="error">
                    样品差异尚未消除，正常分析、交接和共同完成已暂停。账面剩余：{{ sample.journal.remainingQuantity }} {{ sample.journal.unit }}；<template v-if="!Object.keys(sample.journal.portions || {}).length">最近查见：{{ sample.journal.observedQuantity }} {{ sample.journal.unit }}。</template><template v-else>各分份查见情况分别记录。</template>请以核查原件确认实存保管，历史位置不代表未知去向已查明。
                    本次调查已记录 {{ sample.journal.confirmedResolutionUserIds?.length || 0 }} 人本人核实；确认调查事实不等于差异已解决。
                  </NAlert>
                  <NCard v-for="event in sample.journal.events" :key="`${event.period || ''}:${event.sequence}`" :title="`${event.period || ''}第 ${event.sequence} 次样品记录`">
                    <NText v-if="eventPortionKey(event)">对应分份：{{ eventPortionKey(event) }}</NText>
                    <CollectionEvidenceReadView :archive-files="sampleEventFiles(plan, sample, event)" :original-url="sampleEventOriginalUrl(plan, sample, event)" v-if="event.disposition" :fields="samplingFields.disposition" :data="event.disposition" />
                    <template v-if="event.dispositionConfirmation"><NText>本人核实人员：{{ event.dispositionConfirmation.operatorUserId }}；对应第 {{ event.dispositionConfirmation.record.dispositionSequence }} 次非分析处置</NText><CollectionEvidenceReadView :archive-files="sampleEventFiles(plan, sample, event)" :original-url="sampleEventOriginalUrl(plan, sample, event)" :fields="samplingFields['disposition-confirm']" :data="event.dispositionConfirmation.record" /></template>
                    <CollectionEvidenceReadView :archive-files="sampleEventFiles(plan, sample, event)" :original-url="sampleEventOriginalUrl(plan, sample, event)" v-if="event.split" :fields="samplingFields.split" :data="event.split" />
                    <NText>此前保管：{{ event.from.location }}；{{ event.from.externalUnit || '本馆' }} {{ event.from.externalPerson || event.from.userId }}</NText>
                    <NText v-if="event.analysisAuthorization">启用第 {{ event.analysisAuthorization.proposalSequence }} 次分析方案，样品数量未改变。</NText>
                    <CollectionEvidenceReadView :archive-files="sampleEventFiles(plan, sample, event)" :original-url="sampleEventOriginalUrl(plan, sample, event)" v-if="event.analysis" :fields="samplingFields['analysis-batch']" :data="event.analysis" />
                    <CollectionEvidenceReadView :archive-files="sampleEventFiles(plan, sample, event)" :original-url="sampleEventOriginalUrl(plan, sample, event)" v-if="event.handover" :fields="samplingFields.handover" :data="handoverDisplay(event.handover)" />
                    <CollectionEvidenceReadView :archive-files="sampleEventFiles(plan, sample, event)" :original-url="sampleEventOriginalUrl(plan, sample, event)" v-if="event.discrepancy" :fields="samplingFields.discrepancy" :data="custodyDisplay(event.discrepancy, 'observedCustody')" />
                    <CollectionEvidenceReadView :archive-files="sampleEventFiles(plan, sample, event)" :original-url="sampleEventOriginalUrl(plan, sample, event)" v-if="event.resolution" :fields="samplingFields[event.resolution.finalAccounting ? 'discrepancy-finalize' : 'discrepancy-review']" :data="samplingFinalAccountingDisplay(custodyDisplay(event.resolution, 'actualCustody'))" />
                    <template v-if="event.confirmation">
                      <NText>本人核实人员：{{ event.confirmation.operatorUserId }}；对应第 {{ event.confirmation.record.resolutionSequence }} 次调查</NText>
                      <CollectionEvidenceReadView :archive-files="sampleEventFiles(plan, sample, event)" :original-url="sampleEventOriginalUrl(plan, sample, event)" :fields="samplingFields['discrepancy-confirm']" :data="event.confirmation.record" />
                    </template>
                  </NCard>
                </template>
                <NCard v-if="isClosed(plan)" title="旧样品后续分析方案与实际结束">
                  <NAlert v-if="sample.analysisAmendmentIssue" type="error">{{ sample.analysisAmendmentIssue }}</NAlert>
                  <NText>保留原取样和结案；此处仅办理现存样品分析，不新增取样事实。适用审批情形须依据实际材料分别核实。</NText>
                  <NSpace v-if="canOperate && plan.id === latest?.id && sample.custodyJournal && !sample.custodyJournalIssue && !sample.analysisAmendmentIssue" v-permission="'COLLECTION_ADMIN,COLLECTION_SAMPLING_MANAGE'">
                    <NButton v-if="!sample.analysisAmendment?.activationSequence || sample.analysisAmendment.closedAt" @click="edit('analysis-proposal', sample.id)">登记旧样品分析变更方案</NButton>
                    <NAlert v-if="sample.analysisAmendment?.proposalSequence && !sample.analysisAmendment.activationSequence && !analysisBasisCurrent(sample)" type="warning">样品或藏品依据变化，请追加核实后的分析方案并重新确认。</NAlert>
                    <template v-if="sample.analysisAmendment?.proposalSequence && !sample.analysisAmendment.activationSequence && analysisBasisCurrent(sample)">
                      <NButton v-if="canConfirm('analysis-confirm', sample.id)" @click="edit('analysis-confirm', sample.id)">本人确认本分析方案</NButton>
                      <NButton v-if="analysisProposal(sample)?.approvalTreatment === 'NEW_APPROVAL' && sample.analysisAmendment.confirmedUserIds?.length === 2" @click="edit('analysis-administrative', sample.id)">登记本分析方案新批准</NButton>
                      <NButton v-if="sample.analysisAmendment.confirmedUserIds?.length === 2" @click="edit('analysis-activate', sample.id)">核实并启用本分析方案</NButton>
                    </template>
                    <NButton v-if="sample.analysisAmendment?.activationSequence && !sample.analysisAmendment.closedAt" @click="edit('analysis-close', sample.id)">登记本分析方案实际结束</NButton>
                    <NAlert v-if="sample.analysisAmendment?.closureSequence && !sample.analysisAmendment.closedAt && !analysisClosureCurrent(sample)" type="warning">样品保管变化，请重新登记分析结束核查；原签署继续保留。</NAlert>
                    <NButton v-if="canConfirm('analysis-confirm', sample.id) && sample.analysisAmendment?.closureSequence" @click="edit('analysis-confirm', sample.id)">本人确认本分析实际结束</NButton>
                  </NSpace>
                  <NAlert v-if="sample.analysisAmendment?.closedAt" type="info">本分析方案已分别确认结束；后续样品继续保管，原取样项目的未完成工作和结案状态不被改写。</NAlert>
                  <NCard v-for="event in sample.analysisAmendment?.events || []" :key="event.sequence" :title="`第 ${event.sequence} 次分析方案记录`">
                    <NText>实际登记或确认人员：{{ event.operatorUserId }}</NText>
                    <CollectionEvidenceReadView :archive-files="sampleAmendmentFiles(plan, sample, event)" :original-url="sampleAnalysisAmendmentOriginalUrl(plan, sample, event)" v-if="event.proposal" :fields="samplingFields['analysis-proposal']" :data="event.proposal" />
                    <CollectionEvidenceReadView :archive-files="sampleAmendmentFiles(plan, sample, event)" :original-url="sampleAnalysisAmendmentOriginalUrl(plan, sample, event)" v-if="event.confirmation" :fields="samplingFields['analysis-confirm']" :data="event.confirmation" />
                    <CollectionEvidenceReadView :archive-files="sampleAmendmentFiles(plan, sample, event)" :original-url="sampleAnalysisAmendmentOriginalUrl(plan, sample, event)" v-if="event.administrative" :fields="samplingFields['analysis-administrative']" :data="event.administrative.evidence" />
                    <CollectionEvidenceReadView :archive-files="sampleAmendmentFiles(plan, sample, event)" :original-url="sampleAnalysisAmendmentOriginalUrl(plan, sample, event)" v-if="event.activation" :fields="samplingFields['analysis-activate']" :data="event.activation" />
                    <CollectionEvidenceReadView :archive-files="sampleAmendmentFiles(plan, sample, event)" :original-url="sampleAnalysisAmendmentOriginalUrl(plan, sample, event)" v-if="event.closure" :fields="samplingFields['analysis-close']" :data="event.closure" />
                  </NCard>
                </NCard>
                <NCard v-for="target in actionTargets(sample)" :key="target.key || 'whole'" :title="target.key ? `分份 ${target.key}` : '整份样品办理'">
                  <template v-if="target.key && target.state">
                    <NText>本份剩余：{{ target.state.remainingQuantity }} {{ sample.journal.unit }}；最近有依据的保管：{{ target.state.custody.location }}；来源：{{ target.state.parentKey || '原样品' }}。</NText>
                    <NAlert v-if="target.state.custody.kind === 'EXTERNAL' && samplingHasRemainder(target.state.remainingQuantity)" type="warning">本份仍在外部 {{ target.state.custody.externalUnit }}，请跟踪实际归还；其他分份已归还不代表本份已归还。</NAlert>
                    <NAlert v-if="target.state.openDiscrepancySequences?.length" type="error">本份最近查见：{{ target.state.observedQuantity }} {{ sample.journal.unit }}；本次已有 {{ target.state.confirmedResolutionUserIds?.length || 0 }} 人本人核实，确认调查不等于问题已恢复。</NAlert>
                  </template>
                  <NSpace v-if="canOperate && plan.id === latest?.id && target.state && !sample.journalIssue && !plan.stoppedClosureIssue"
                    v-permission="'COLLECTION_ADMIN,COLLECTION_SAMPLING_MANAGE'">
                    <NText v-if="target.state.pendingDispositionSequence">第 {{ target.state.pendingDispositionSequence }} 次非分析处置：已有 {{ target.state.confirmedDispositionUserIds?.length || 0 }} 人本人核实，账面数量暂未减记。</NText>
                    <NButton v-if="canConfirm('disposition-confirm', sample.id, target.key)" @click="edit('disposition-confirm', sample.id, target.key)">本人核实非分析处置</NButton>
                    <NButton @click="edit('discrepancy', sample.id, target.key)">{{ target.state.openDiscrepancySequences?.length ? '追加实际核查与保管' : '登记样品差异' }}</NButton>
                    <template v-if="target.state.openDiscrepancySequences?.length">
                      <NButton @click="edit('discrepancy-review', sample.id, target.key)">登记差异调查与处置</NButton>
                      <NButton v-if="plan.ended?.safeToReleaseOriginal || plan.incidentClearance" @click="edit('discrepancy-finalize', sample.id, target.key)">登记差异最终记账依据</NButton>
                      <NButton v-if="canConfirm('discrepancy-confirm', sample.id, target.key)"
                        @click="edit('discrepancy-confirm', sample.id, target.key)">本人核实本次调查</NButton>
                    </template>
                  </NSpace>
                  <NSpace v-if="!sample.journal?.openDiscrepancySequences?.length && !sample.journal?.dispositionPending && plan.id === latest?.id && (plan.ended?.safeToReleaseOriginal || plan.incidentClearance) && !sample.journalIssue && !plan.stoppedClosureIssue"
                    v-permission="'COLLECTION_ADMIN,COLLECTION_SAMPLING_MANAGE'">
                    <template v-if="target.state && samplingHasRemainder(target.state.remainingQuantity)">
                      <NButton v-if="isClosed(plan) && sample.analysisAmendment?.activationSequence && !sample.analysisAmendment.closureSequence && !sample.analysisAmendmentIssue && analysisProposal(sample)?.targets?.some((t: any) => (t.portionKey || undefined) === target.key)" @click="edit('analysis-existing', sample.id, target.key)">按变更方案登记实际分析</NButton>
                      <NButton v-if="!isClosed(plan) && !plan.stoppedClosure?.reviewSequence" @click="edit('analysis-batch', sample.id, target.key)">追加本批实际分析</NButton>
                      <NButton @click="edit('handover', sample.id, target.key)">{{ isClosed(plan) ? '登记结案后样品交接或归还' : '登记样品实际交接或归还' }}</NButton>
                      <NButton @click="edit('split', sample.id, target.key)">登记实际拆分与分份保管</NButton>
                      <NButton @click="edit('disposition', sample.id, target.key)">登记样品实际非分析处置</NButton>
                    </template>
                    <NButton v-else-if="!isClosed(plan) && !plan.stoppedClosure?.reviewSequence && !sample.journal && !sample.analysis" @click="edit('analysis', sample.id)">登记实际分析与剩余保管</NButton>
                  </NSpace>
                </NCard>
              </NCard
              ></NTabPane
            >
            <NTabPane name="complete" tab="共同复核与原件">
              <template v-if="plan.stoppedClosure?.events?.length">
                <NText>停作结案核查：已有 {{ plan.stoppedClosure.confirmedUserIds?.length || 0 }} 人本人确认，停作不代表研究完成。</NText>
                <NAlert v-if="!plan.stoppedClosure.reviewCurrent" type="warning">样品或结束依据已变化，请重新核查；原签署继续保留。</NAlert>
                <NCard v-for="event in plan.stoppedClosure.events" :key="event.sequence" :title="`第 ${event.sequence} 次停作记录`">
                  <NText>实际登记或确认人员：{{ event.operatorUserId }}</NText>
                  <CollectionEvidenceReadView :archive-files="stageFiles(plan, `STOPPEDV${event.sequence}`)" :original-url="phaseOriginalUrl(plan, `STOPPEDV${event.sequence}`)" v-if="event.review" :fields="samplingFields['stopped-review']" :data="event.review" />
                  <template v-if="event.confirmation"><NText>对应第 {{ event.confirmation.reviewSequence }} 次停作核查</NText><CollectionEvidenceReadView :archive-files="stageFiles(plan, `STOPPEDV${event.sequence}`)" :original-url="phaseOriginalUrl(plan, `STOPPEDV${event.sequence}`)" :fields="samplingFields['stopped-confirm']" :data="event.confirmation" /></template>
                </NCard>
              </template>
              <template v-if="plan.noSampleClosure?.events?.length">
                <NText>未取得样品结案核查；本轮已有 {{ plan.noSampleClosure.confirmedUserIds?.length || 0 }} 人本人确认。</NText>
                <NAlert v-if="!plan.noSampleClosure.reviewCurrent" type="warning">结束或处置依据已变化，请重新核实；历史签署继续保留。</NAlert>
                <NCard v-for="event in plan.noSampleClosure.events" :key="event.sequence" :title="`第 ${event.sequence} 次未取得样品结案记录`">
                  <NText>实际登记或确认人员：{{ event.operatorUserId }}</NText>
                  <CollectionEvidenceReadView :archive-files="stageFiles(plan, `NOSAMPLEV${event.sequence}`)" :original-url="phaseOriginalUrl(plan, `NOSAMPLEV${event.sequence}`)" v-if="event.review" :fields="samplingFields['no-sample-review']" :data="event.review" />
                  <template v-if="event.confirmation">
                    <NText>对应第 {{ event.confirmation.reviewSequence }} 次核查</NText>
                    <CollectionEvidenceReadView :archive-files="stageFiles(plan, `NOSAMPLEV${event.sequence}`)" :original-url="phaseOriginalUrl(plan, `NOSAMPLEV${event.sequence}`)" :fields="samplingFields['no-sample-confirm']" :data="event.confirmation" />
                  </template>
                </NCard>
              </template>
              <NText depth="3">同一文件若归入多个阶段，请在下方按永久档案阶段下载对应原件。</NText>
              <CollectionEvidenceReadView :archive-files="stageFiles(plan, 'COMPLETED')" :original-url="phaseOriginalUrl(plan, 'COMPLETED')"
                v-if="plan.completed"
                :fields="samplingFields.complete"
                :data="plan.completed" /><NCollapse
                ><NCollapseItem v-for="a in plan.archives" :key="a.id" :name="a.id" :title="a.phase"
                  ><HFileList :files="a.files" :original-url="(fileId: string) => archiveOriginalUrl(a.id, fileId)" :revision="[a?.id, a?.digest]" show-download-btn /></NCollapseItem></NCollapse
            ></NTabPane> </NTabs
        ></NCard> </NSpace></NSpin
  ></HPageForm>
  <CollectionEvidenceForm
    :key="`CollectionEvidenceForm-${formEpoch}`"
    ref="formRef"
    :title="(samplingTitles[kind] || '') + formContext"
    :fields="fields"
    :save="saveForCurrentForm"
    @saved="saved"
  />
  <CollectionRequisitionCaseDrawer :key="`CollectionRequisitionCaseDrawer-${formEpoch}`" ref="requisitionRef" @saved="saved" />
  <IncidentDrawer :key="`IncidentDrawer-${formEpoch}`" ref="incidentRef" @saved="saved" />
</template>
<script setup lang="ts">
const stageFiles = (plan: any, phase: string) =>
  (plan?.archives || []).filter((archive: any) => archive.phase === phase).flatMap((archive: any) => archive.files || []);
const sequenceFiles = (plan: any, prefix: unknown, sequence: unknown) =>
  typeof prefix === 'string' && prefix && Number.isSafeInteger(sequence) && (sequence as number) > 0
    ? stageFiles(plan, `${prefix}${sequence}`) : [];
const sampleEventFiles = (plan: any, sample: any, event: any) =>
  sequenceFiles(plan, sample.archivePhasePrefixes?.[event.period === '结案后' ? 'custody' : 'journal'], event.sequence);
const sampleAmendmentFiles = (plan: any, sample: any, event: any) =>
  sequenceFiles(plan, sample.archivePhasePrefixes?.analysisAmendment, event.sequence);
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { useAuthStore } from '@/store/modules/auth';
import IncidentDrawer from './IncidentDrawer.vue';
import { cloneDeep } from 'lodash-es';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import CollectionRequisitionCaseDrawer from './CollectionRequisitionCaseDrawer.vue';
import { samplingApi as api } from '../api/sampling';
import { samplingFields, samplingTitles, samplingValues, samplingHandoverValues, samplingHasRemainder, samplingDiscrepancyValues, samplingFinalAccountingValues, samplingFinalAccountingDisplay } from '../utils/samplingFields';
import type { EvidenceField } from '../utils/evidenceFields';
const incidentRef = ref<any>();
const auth = useAuthStore();
const emit = defineEmits<{ (e: 'saved'): void }>();
const show = ref(false),
  loading = ref(false),
  readError = ref(''),
  formEpoch = ref(0),
  data = ref<any>({}),
  fields = ref<EvidenceField[]>([]),
  kind = ref(''),
  formContext = ref(''),
  formRef = ref<any>(),
  requisitionRef = ref<any>();
const latest = computed(() => data.value.plans?.at(-1));
function samplingArchiveOriginalUrl(samplingId: string, archiveId: string, fileId: string): string {
  if (!samplingId || !archiveId || !/^\d{15,20}$/.test(fileId)) return '';
  return `/collection/sampling/${encodeURIComponent(samplingId)}/original/${encodeURIComponent(archiveId)}/${encodeURIComponent(fileId)}`;
}
function archiveOriginalUrl(archiveId: string, fileId: string): string {
  return samplingArchiveOriginalUrl(id, archiveId, fileId);
}
function predecessorOriginalUrl(fileId: string): string {
  const link = data.value.predecessor;
  const archive = data.value.predecessorOriginal;
  if (!link || link.successorId !== id || archive?.phase !== `CONTINUATION_${id}` ||
      !(archive.files || []).some((file: any) => file.fileId === fileId)) return '';
  return samplingArchiveOriginalUrl(link.predecessorId, archive.id, fileId);
}
function phaseOriginalUrl(plan: any, phase: string) {
  return (fileId: string): string => {
    const matches = (plan.archives || []).filter(
      (archive: any) =>
        archive.phase === phase && (archive.files || []).some((file: any) => file.fileId === fileId),
    );
    return matches.length === 1 ? archiveOriginalUrl(matches[0].id, fileId) : '';
  };
}
function sequenceOriginalUrl(plan: any, prefix: unknown, sequence: unknown) {
  if (typeof prefix !== 'string' || !prefix || !Number.isSafeInteger(sequence) || (sequence as number) <= 0)
    return originalUrl;
  return phaseOriginalUrl(plan, `${prefix}${sequence}`);
}
function sampleEventOriginalUrl(plan: any, sample: any, event: any) {
  const period = event.period === '结案后' ? 'custody' : 'journal';
  return sequenceOriginalUrl(plan, sample.archivePhasePrefixes?.[period], event.sequence);
}
function sampleAnalysisAmendmentOriginalUrl(plan: any, sample: any, event: any) {
  return sequenceOriginalUrl(plan, sample.archivePhasePrefixes?.analysisAmendment, event.sequence);
}
function originalUrl(fileId: string): string {
  const matches = (data.value.plans || []).flatMap((plan: any) =>
    (plan.archives || []).filter((archive: any) =>
      (archive.files || []).some((file: any) => file.fileId === fileId),
    ),
  );
  // A reused file ID has no single phase. The permanent-archive list below names each phase.
  return matches.length === 1 ? archiveOriginalUrl(matches[0].id, fileId) : '';
}
const canStart = computed(() =>
  latest.value?.administrative?.some(
    (record: any) => record.level === latest.value.authority && record.action === 'APPROVAL',
  ) === true,
);
const canPlan = computed(
  () =>
    !!data.value.sourceRevision &&
    data.value.flowStatus === 'DRAFT' &&
    !data.value.flowInstanceId &&
    !latest.value?.started,
);
const hasException = computed(() =>
  (latest.value?.samples || []).some(
    (s: any) => s.content.withinApprovedScope === false || s.content.unexpectedDamage === true,
  ),
);
const emptyEditing = { id: '', plan: '', source: '', kind: '', sample: '', revision: '', sampleRevisions: {} as Record<string, string>, currentReview: false, resolutionSequence: undefined as number | undefined, dispositionSequence: undefined as number | undefined, analysisRevision: '', analysisProposalSequence: undefined as number | undefined, analysisSubjectSequence: undefined as number | undefined, continuationBasis: '', stoppedBasis: '', stoppedReviewSequence: undefined as number | undefined, noSampleBasis: '', noSampleReviewSequence: undefined as number | undefined, afterCompletion: false, portion: undefined as string | undefined };
let id = '', request = 0, editing = cloneDeep(emptyEditing);
const canOperate = computed(() => show.value && !loading.value && !readError.value && !!data.value.id &&
  data.value.canOperate !== false && auth.hasPermission('COLLECTION_ADMIN,COLLECTION_SAMPLING_MANAGE'));
const saveForCurrentForm = computed(() => {
  const epoch = formEpoch.value, actor = auth.userInfo?.id, target = cloneDeep(editing);
  return (value: Record<string, any>) => epoch === formEpoch.value && actor === auth.userInfo?.id
    ? save(value, target) : Promise.resolve(false);
});
function canConfirm(action: string, sampleId = '', portion?: string): boolean {
  const actor = auth.userInfo?.id, p = latest.value;
  if (!canOperate.value || !actor || !p) return false;
  let people: any, confirmed: string[] = [];
  if (action === 'no-sample-confirm' || action === 'stopped-confirm') {
    const state = action === 'no-sample-confirm' ? p.noSampleClosure : p.stoppedClosure;
    const issue = action === 'no-sample-confirm' ? p.noSampleClosureIssue : p.stoppedClosureIssue;
    if (issue || !state?.eligible || state.closedAt || !state.reviewCurrent ||
        !(p.ended?.safeToReleaseOriginal || p.incidentClearance)) return false;
    people = state.events?.find((e: any) => e.sequence === state.reviewSequence)?.review;
    confirmed = state.confirmedUserIds || [];
  } else {
    const sample = p.samples?.find((s: any) => s.id === sampleId);
    if (!sample || sample.journalIssue || sample.custodyJournalIssue || p.stoppedClosureIssue) return false;
    if (action === 'analysis-confirm') {
      const state = sample.analysisAmendment;
      if (!isClosed(p) || !sample.custodyJournal || sample.analysisAmendmentIssue || !state || state.closedAt) return false;
      const current = { ...sample, journal: sample.custodyJournal };
      if (state.closureSequence) {
        if (!analysisClosureCurrent(current)) return false;
        people = state.events?.find((e: any) => e.sequence === state.closureSequence)?.closure;
        confirmed = state.closureConfirmedUserIds || [];
      } else {
        if (state.activationSequence || !analysisBasisCurrent(current)) return false;
        people = state.events?.find((e: any) => e.sequence === state.proposalSequence)?.proposal;
        confirmed = state.confirmedUserIds || [];
      }
    } else {
      const journal = currentJournal(sampleId), target = currentTarget(sampleId, portion);
      if (action === 'discrepancy-confirm') {
        if (!target?.openDiscrepancySequences?.length) return false;
        people = journal?.events?.find((e: any) => e.sequence === target.pendingResolutionSequence)?.resolution;
        confirmed = target.confirmedResolutionUserIds || [];
      } else if (action === 'disposition-confirm') {
        if (journal?.openDiscrepancySequences?.length) return false;
        people = journal?.events?.find((e: any) => e.sequence === target?.pendingDispositionSequence)?.disposition;
        confirmed = target?.confirmedDispositionUserIds || [];
      } else return false;
    }
  }
  return !!people && [people.conservationUserId, people.keeperUserId].includes(actor) && !confirmed.includes(actor);
}
function clearForms() {
  editing = cloneDeep(emptyEditing);
  kind.value = '';
  fields.value = [];
  formContext.value = '';
  formEpoch.value++;
}
function close() {
  request++;
  data.value = {};
  readError.value = '';
  loading.value = false;
  clearForms();
}
watch(show, visible => { if (!visible) close(); }, { flush: 'sync' });
watch(() => auth.userInfo?.id, () => { show.value = false; id = ''; close(); }, { flush: 'sync' });
onBeforeUnmount(close);
async function open(value: string) {
  id = value;
  data.value = {};
  show.value = true;
  await reload();
}
async function reload() {
  const token = ++request,
    target = id;
  loading.value = true;
  data.value = {};
  readError.value = '';
  try {
    const r: any = await api.detail(target);
    if (token !== request || !show.value) return false;
    if (!r?.data) throw new Error('missing detail');
    data.value = r.data;
    return true;
  } catch {
    if (token === request && show.value) {
      data.value = {};
      readError.value = '取样资料读取失败，请核对当前授权和原件。';
      clearForms();
    }
    return false;
  } finally {
    if (token === request) loading.value = false;
  }
}
async function edit(action: string, sample = '', portion?: string) {
  if (!canOperate.value || (action.endsWith('-confirm') && !canConfirm(action, sample, portion))) return;
  const token = request;
  formEpoch.value++;
  editing = { id, plan: latest.value?.id, source: data.value.sourceRevision, kind: action, sample, portion,
    revision: action.startsWith('stopped-') ? latest.value?.stoppedClosure?.revision : action.startsWith('no-sample-') ? latest.value?.noSampleClosure?.revision : currentJournal(sample)?.revision || '',
    sampleRevisions: Object.fromEntries((latest.value?.samples || []).map((s: any) => [s.id, s.journal?.revision])),
    currentReview: (latest.value?.samples || []).every((s: any) => s.journal?.revision),
    resolutionSequence: currentTarget(sample, portion)?.pendingResolutionSequence,
    dispositionSequence: currentTarget(sample, portion)?.pendingDispositionSequence,
    afterCompletion: isClosed(latest.value),
    analysisRevision: latest.value?.samples?.find((s: any) => s.id === sample)?.analysisAmendment?.revision || '',
    analysisProposalSequence: latest.value?.samples?.find((s: any) => s.id === sample)?.analysisAmendment?.proposalSequence,
    analysisSubjectSequence: latest.value?.samples?.find((s: any) => s.id === sample)?.analysisAmendment?.closureSequence || latest.value?.samples?.find((s: any) => s.id === sample)?.analysisAmendment?.proposalSequence,
    continuationBasis: latest.value?.continuationBasisRevision,
    stoppedBasis: latest.value?.stoppedClosure?.basisRevision,
    stoppedReviewSequence: latest.value?.stoppedClosure?.reviewSequence,
    noSampleBasis: latest.value?.noSampleClosure?.basisRevision,
    noSampleReviewSequence: latest.value?.noSampleClosure?.reviewSequence,
  };
  kind.value = action;
  formContext.value = portion ? ` — 分份 ${portion}` : '';
  fields.value = cloneDeep(samplingFields[action]);
  fields.value = fields.value.map((f) =>
    f.key === 'sampleIds'
      ? { ...f, options: (latest.value?.samples || []).map((s: any) => ({ value: s.id, label: s.content.number })) }
      : f.key === 'discrepancySequences'
        ? { ...f, options: (currentTarget(sample, portion)?.openDiscrepancySequences || []).map((n: number) => ({ value: String(n), label: `第 ${n} 次差异核查` })) }
        : f,
  );
  if (action === 'analysis-proposal') fields.value = fields.value.map((f) =>
    f.key === 'existingApprovalId' ? { ...f, options: (latest.value?.administrative || []).map((a: any) => ({ value: a.id, label: `${a.authorityName} ${a.documentNo}` })) }
      : f.key === 'targets' ? { ...f, children: f.children?.map((child) => child.key === 'portionKey' ? { ...child, options: actionTargets({ journal: currentJournal(sample) }).filter((t: any) => t.key).map((t: any) => ({ value: t.key, label: `分份 ${t.key}` })) } : child) } : f);
  const target = editing;
  await nextTick();
  if (token !== request || target !== editing || !show.value) return;
  formRef.value?.open({});
}
async function save(value: Record<string, any>, target: typeof editing) {
  if (!canOperate.value || !target.id || !target.kind) return false;
  const v: Record<string, any> = { ...samplingValues(value), ...(target.portion ? { portionKey: target.portion } : {}) },
    sampleAction = target.afterCompletion ? `custody-${target.kind}` : target.kind;
  if (target.kind === 'analysis-proposal') return api.sampleRecord(target.id, target.sample, target.kind, { ...v, previousRevision: target.analysisRevision, sampleRevision: target.revision, sourceRevision: target.source, targets: (v.targets || []).map((t: any) => ({ ...t, portionKey: t.portionKey || null })) });
  if (target.kind === 'analysis-confirm') return api.sampleRecord(target.id, target.sample, target.kind, { ...v, previousRevision: target.analysisRevision, subjectSequence: target.analysisSubjectSequence });
  if (target.kind === 'analysis-administrative') return api.sampleRecord(target.id, target.sample, target.kind, { previousRevision: target.analysisRevision, proposalSequence: target.analysisProposalSequence, evidence: { ...v, action: 'APPROVAL' } });
  if (['analysis-activate', 'analysis-close'].includes(target.kind)) return api.sampleRecord(target.id, target.sample, target.kind, { ...v, previousRevision: target.analysisRevision, sampleRevision: target.revision, proposalSequence: target.analysisProposalSequence });
  if (target.kind === 'analysis-existing') return api.sampleRecord(target.id, target.sample, target.kind, { ...v, previousRevision: target.revision, analysisPlanSequence: target.analysisProposalSequence });
  if (target.kind === 'continue-extraction') return api.record(target.id, target.kind, { ...v, predecessorPlanId: target.plan, basisRevision: target.continuationBasis });
  if (target.kind === 'plan')
    return api.record(target.id, 'plan', { ...v, sourceRevision: target.source, alternatives: v.alternatives || [] });
  if (target.kind === 'administrative')
    return api.record(target.id, 'administrative', { planId: target.plan, evidence: { ...v, action: 'APPROVAL' } });
  if (target.kind === 'start') return api.record(target.id, 'start', { ...v, planId: target.plan });
  if (target.kind === 'stopped-review') return api.record(target.id, target.kind, { ...v, planId: target.plan, previousRevision: target.revision, basisRevision: target.stoppedBasis });
  if (target.kind === 'stopped-confirm') return api.record(target.id, target.kind, { ...v, previousRevision: target.revision, reviewSequence: target.stoppedReviewSequence });
  if (target.kind === 'no-sample-review') return api.record(target.id, target.kind, { ...v, planId: target.plan, previousRevision: target.revision, basisRevision: target.noSampleBasis });
  if (target.kind === 'no-sample-confirm') return api.record(target.id, target.kind, { ...v, previousRevision: target.revision, reviewSequence: target.noSampleReviewSequence });
  if (target.kind === 'analysis') return api.analysis(target.id, target.sample, v);
  if (target.kind === 'discrepancy-finalize') return api.sampleRecord(target.id, target.sample, target.afterCompletion ? 'custody-discrepancy-review' : 'discrepancy-review', samplingFinalAccountingValues(v, target.revision));
  if (['discrepancy', 'discrepancy-review'].includes(target.kind)) return api.sampleRecord(target.id, target.sample, sampleAction, samplingDiscrepancyValues(v, target.revision, target.kind));
  if (target.kind === 'discrepancy-confirm') return api.sampleRecord(target.id, target.sample, sampleAction, { ...v, previousRevision: target.revision, resolutionSequence: target.resolutionSequence });
  if (target.kind === 'disposition-confirm') return api.sampleRecord(target.id, target.sample, sampleAction, { ...v, previousRevision: target.revision, dispositionSequence: target.dispositionSequence });
  if (['split', 'disposition'].includes(target.kind)) return api.sampleRecord(target.id, target.sample, sampleAction, { ...v, previousRevision: target.revision });
  if (target.kind === 'analysis-batch') return api.sampleRecord(target.id, target.sample, target.kind, { ...v, previousRevision: target.revision });
  if (target.kind === 'handover') return api.sampleRecord(target.id, target.sample, sampleAction, samplingHandoverValues(v, target.revision));
  if (target.kind === 'complete' && target.currentReview) return api.record(target.id, 'complete-current', { completion: { ...v, sampleIds: v.sampleIds || [] }, sampleRevisions: target.sampleRevisions });
  if (['end-extraction', 'complete'].includes(target.kind))
    return api.record(target.id, target.kind, { ...v, sampleIds: v.sampleIds || [] });
  return api.record(target.id, target.kind, v);
}
async function submit() {
  if (!canOperate.value) return;
  const target = id, token = request;
  await api.submit(target);
  if (token !== request || target !== id || !show.value) return;
  if (await reload()) emit('saved');
}
function analysisBasisCurrent(sample: any) { const p = analysisProposal(sample); return p?.sampleRevision === sample?.journal?.revision && p?.sourceRevision === data.value.sourceRevision; }
function analysisClosureCurrent(sample: any) { return sample?.analysisAmendment?.events?.find((e: any) => e.sequence === sample.analysisAmendment.closureSequence)?.closure?.sampleRevision === sample?.journal?.revision; }
function analysisProposal(sample: any) { return sample?.analysisAmendment?.events?.find((e: any) => e.sequence === sample.analysisAmendment.proposalSequence)?.proposal; }
function currentJournal(sampleId: string) {
  const sample = latest.value?.samples?.find((s: any) => s.id === sampleId);
  return isClosed(latest.value) ? sample?.custodyJournal : sample?.journal;
}
function currentTarget(sampleId: string, portion?: string) {
  const journal = currentJournal(sampleId);
  return portion ? journal?.portions?.[portion] : journal;
}
function actionTargets(sample: any) {
  const portions = Object.values(sample.journal?.portions || {}) as any[];
  return portions.length ? portions.map((p) => ({ key: p.key as string | undefined, state: p })) : [{ key: undefined, state: sample.journal }];
}
function eventPortionKey(event: any) {
  return event.analysis?.portionKey || event.handover?.portionKey || event.discrepancy?.portionKey || event.resolution?.portionKey || event.confirmation?.record?.portionKey || event.split?.portionKey || event.disposition?.portionKey || event.dispositionConfirmation?.record?.portionKey;
}
function isClosed(plan: any): boolean { return !!(plan?.completed || plan?.stoppedClosure?.closedAt); }
function displaySamples(plan: any) {
  if (!isClosed(plan)) return plan.samples || [];
  return (plan.samples || []).map((sample: any) => ({
    ...sample,
    frozenJournal: sample.journal,
    journalIssue: sample.custodyJournalIssue || sample.journalIssue,
    journal: sample.custodyJournal && { ...sample.custodyJournal, events: [
      ...(sample.journal?.events || []).map((e: any) => ({ ...e, period: '结案前' })),
      ...(sample.custodyJournal.events || []).map((e: any) => ({ ...e, period: '结案后' })),
    ] },
  }));
}
function custodyDisplay(v: any, field: string) {
  return { ...v, ...(v[field] || {}), destinationKind: v[field]?.kind || 'UNKNOWN' };
}
function handoverDisplay(v: any) {
  return { ...v, ...v.destination, destinationKind: v.destination.kind };
}
function createIncident() {
  if (!show.value || loading.value || readError.value || !data.value.id || !latest.value ||
      !auth.hasPermission('COLLECTION_ADMIN,COLLECTION_INCIDENT_MANAGE')) return;
  incidentRef.value?.create({
    source: { bizType: 'COLLECTION_SAMPLING', bizId: id, recordId: latest.value.id },
    items: [
      {
        collectionId: data.value.collectionId,
        damaged: latest.value.samples?.some((s: any) => s.content.unexpectedDamage),
      },
    ],
  });
}
async function requisition() {
  if (!canOperate.value) return;
  await requisitionRef.value?.open(undefined, {
    collectionIds: data.value.collectionId,
    purpose: undefined,
    sourceBizType: 'COLLECTION_SAMPLING',
    sourceBizId: id,
  });
}
async function saved() {
  if (!show.value || readError.value) return;
  clearForms();
  if (await reload()) emit('saved');
}
defineExpose({ open });
</script>
