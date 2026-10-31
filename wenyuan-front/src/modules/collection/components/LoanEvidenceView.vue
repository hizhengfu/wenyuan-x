<template>
  <NSpace vertical :size="18">
    <NDescriptions bordered :column="2"
      ><NDescriptionsItem label="借用事由" :span="2" content-style="white-space: pre-wrap; overflow-wrap: anywhere;">{{ data.title }}</NDescriptionsItem
      ><NDescriptionsItem label="借用单位">{{ data.borrower }}</NDescriptionsItem
      ><NDescriptionsItem label="协议编号">{{ data.protocolNo }}</NDescriptionsItem
      ><NDescriptionsItem label="实际出库">{{
        data.outExecuted ? formatLocalDateValue(data.outExecutedTime, 'YYYY-MM-DD HH:mm:ss') : data.returned ? '已归还，历史出库待核实' : '尚未出库'
      }}</NDescriptionsItem></NDescriptions
    >
    <CollectionEvidenceReadView :archive-files="archiveFiles" :original-url="originalUrl"
      v-if="data.dossier"
      :fields="loanDossierFields(data.dossier)"
      :data="{ ...data.dossier, ...data.dossier.overseasExhibition }"
    /><NAlert v-else type="warning">旧单尚未核实完整新规借用方案，旧批准状态不能代替法定原件。</NAlert>
    <NCard
      v-for="item in data.baseline?.items || []"
      :key="item.collectionId"
      :title="item.content?.formalName || item.collectionId"
      size="small"
      ><NSpace vertical>
        <CollectionEvidenceReadView :archive-files="archiveFiles" :original-url="originalUrl"
          v-if="assessment(item.collectionId)"
          :fields="loanAssessmentFields(!!data.dossier?.exhibitionOverseas)"
          :data="assessment(item.collectionId)"
        />
        <CollectionEvidenceReadView :archive-files="archiveFiles" :original-url="originalUrl"
          v-if="condition(item.collectionId)"
          :fields="conditionFields"
          :data="condition(item.collectionId)"
        /> </NSpace
    ></NCard>
    <NCard v-for="record in data.administrativeEvidence || []" :key="record.id" title="行政报批或备案原件" size="small"
      ><CollectionEvidenceReadView :archive-files="archiveFiles" :original-url="originalUrl" :fields="loanAdministrativeFields" :data="record.content"
    /></NCard>
    <CollectionEvidenceReadView v-if="data.handover" :fields="loanHandoverFields" :data="data.handover.content" :archive-files="archiveFiles" :original-url="originalUrl" />
    <NAlert
      v-for="f in data.obligations?.filings || []"
      :key="f.level"
      :type="f.overdue || f.onTime === false ? 'warning' : 'info'"
      >{{ loanAuthorityOptions.find((o) => o.value === f.level)?.label }}备案：报送期限
      {{ formatLocalDateValue(f.dueOn) }}；{{
        f.completedOn
          ? `已登记，实际报送 ${formatLocalDateValue(f.submittedOn)}（${f.onTime ? '按期' : '逾期报送'}）`
          : f.overdue
            ? '已逾期，须办理并说明情况'
            : '待登记实际报送及备案凭据'
      }}</NAlert
    >
    <NAlert v-if="data.obligations?.simultaneousFirstLevelFiling === false" type="warning"
      >一级文物两级备案报送日期不一致，请如实核实未同时报送的情况并按规定处理。</NAlert
    >
    <NAlert v-if="data.obligations?.heldForReviewCollectionIds?.length" type="warning">{{data.obligations.heldForReviewCollectionIds.length}} 件实物已接收但仍待归还真实性或状况核查，请在归还单办理。</NAlert>
    <NAlert v-if="data.obligations?.returnOverdue" type="warning"
      >藏品已超过协议或催还要求的归还期限，请督促催还，并根据情况履行报告程序。</NAlert
    >
    <NAlert v-for="due in recalledReturns" :key="due.collectionId" :type="due.overdue ? 'warning' : 'info'">
      {{ itemName(due.collectionId) }}：催还要求 {{ formatLocalDateValue(due.recallDueOn) }}；当前逾期界点
      {{ due.dueAt ? formatLocalDateValue(due.dueAt, 'YYYY-MM-DD HH:mm:ss') : formatLocalDateValue(due.dueOn) }}{{ due.overdue ? '（已逾期）' : '' }}。
    </NAlert>
    <NText
      v-for="check in data.obligations?.inspections || []"
      :key="check.collectionId"
      :type="check.overdue ? 'warning' : 'default'"
      >{{ itemName(check.collectionId) }}：下次双方安全检查 {{ formatLocalDateValue(check.nextDueOn)
      }}{{ check.overdue ? '（已逾期）' : '' }}</NText
    >
    <NAlert v-if="data.obligations?.pendingMajorReportIds?.length" type="error"
      >存在 {{ data.obligations.pendingMajorReportIds.length }} 项重大情况待登记实际主管部门报告。</NAlert
    >
    <NAlert v-if="data.obligations?.unresolvedIssueIds?.length" type="warning"
      >{{ data.obligations.unresolvedIssueIds.length }} 项问题或催还尚未归档处理结果。</NAlert
    >
    <NCard v-for="record in data.exhibitionEnds || []" :key="record.id" title="展览实际结束原件" size="small">
      <CollectionEvidenceReadView :fields="loanExhibitionEndFields()" :data="record.content.request" :archive-files="archiveFiles" :original-url="originalUrl" />
    </NCard>
    <NAlert v-for="filing in data.obligations?.overseasFilings || []" :key="filing.kind" :type="filing.overdue || filing.lateHistoryIds?.length ? 'warning' : 'info'">{{loanOverseasFilingSummary(filing)}}</NAlert>
    <NCard v-for="record in data.overseasFilings || []" :key="record.id" title="出境备案或结项报送原件" size="small">
      <NText>{{record.content.late?'逾期报送；':''}}当次报送期限：{{formatLocalDateValue(record.content.dueOn)}}</NText>
      <CollectionEvidenceReadView :fields="loanOverseasFilingFields([],record.content.request.kind)" :data="record.content.request" :archive-files="archiveFiles" :original-url="originalUrl" />
    </NCard>
    <NAlert v-if="data.obligations?.missingExitClearanceCollectionIds?.length" type="warning">{{data.obligations.missingExitClearanceCollectionIds.length}} 件尚缺出境审核及海关放行原件。</NAlert>
    <NAlert v-if="data.obligations?.pendingReentryCollectionIds?.length" type="info">{{data.obligations.pendingReentryCollectionIds.length}} 件待登记原审核机构复进境查验及海关结项。</NAlert>
    <NAlert v-if="data.obligations?.returnedWithoutReentryCollectionIds?.length" type="warning">已归库藏品仍缺复进境手续，实物接收不代替海关结项。</NAlert>
    <NCard v-for="record in data.overseasPorts || []" :key="record.id" :title="record.content.request.stage==='EXIT'?'出境口岸原件':'复进境结项原件'" size="small">
      <CollectionEvidenceReadView :fields="loanPortFields(data.administrativeEvidence,data.overseasPorts,data.baseline?.items,record.content.request.stage)" :data="loanEvidenceInitial(record.content.request)" :archive-files="archiveFiles" :original-url="originalUrl" />
    </NCard>
    <NCard v-for="record in data.overseasExtensions || []" :key="record.id" title="出境展览延期原件" size="small">
      <NAlert :type="record.content.approvalLate ? 'warning' : 'info'">原展期截至 {{ formatLocalDateValue(record.content.previousExhibitionEndsOn) }}，原归还时间 {{ formatLocalDateValue(record.content.previousReturnAt, 'YYYY-MM-DD HH:mm:ss') }}。{{ record.content.approvalLate ? '本次批准晚于原展期，迟办事实保留。' : '延期批准与原方案分别留存。' }}</NAlert>
      <CollectionEvidenceReadView :fields="loanExtensionFields(data.administrativeEvidence)" :data="record.content.request" :archive-files="archiveFiles" :original-url="originalUrl" />
    </NCard>
    <NCard
      v-for="record in data.monitoringRecords || []"
      :key="record.id"
      :title="`监管记录：${record.id}`"
      size="small"
      ><CollectionEvidenceReadView :archive-files="archiveFiles" :original-url="originalUrl"
        :fields="loanMonitoringFields(data.monitoringRecords, data.baseline?.items)"
        :data="record.content"
    /></NCard>
  </NSpace>
</template>
<script setup lang="ts">
import { type CollectionReadonlyFile } from '../utils/readonlyFiles';
import { computed } from 'vue';
import { formatLocalDateValue } from '@/utils';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import {
  loanDossierFields,
  loanExtensionFields,
  loanPortFields,
  loanOverseasFilingFields,
  loanExhibitionEndFields,
  loanOverseasFilingSummary,
  loanEvidenceInitial,
  loanAssessmentFields,
  loanAdministrativeFields,
  loanHandoverFields,
  loanMonitoringFields,
  loanAuthorityOptions,
  loanYesNo,
} from '../utils/loanEvidenceFields';
import type { EvidenceField } from '../utils/evidenceFields';
const props = defineProps<{ data: Record<string, any>; originalUrl?: (fileId: string) => string; archiveFiles?: CollectionReadonlyFile[] }>();
const recalledReturns = computed(() => props.data.obligations?.returnDeadlines?.filter((item: any) => item.recallDueOn) || []);
const assessment = (id: string) => {
  const a = props.data.dossier?.assessments?.find((i: any) => i.collectionId === id);
  return a
    ? { ...a, ...props.data.dossier?.overseasExhibition?.objects?.find((i: any) => i.collectionId === id) }
    : null;
};
const condition = (id: string) => props.data.handover?.content?.items?.find((i: any) => i.collectionId === id);
const itemName = (id: string) =>
  props.data.baseline?.items?.find((i: any) => i.collectionId === id)?.content?.formalName || id;
const conditionFields: EvidenceField[] = [
  { key: 'condition', label: '实际点交时逐件现状', type: 'textarea' },
  { key: 'textNeedsImages', label: '文字是否需影像补充', type: 'select', options: loanYesNo },
  { key: 'imageFileIds', label: '本件点交影像', type: 'file' },
];
</script>
