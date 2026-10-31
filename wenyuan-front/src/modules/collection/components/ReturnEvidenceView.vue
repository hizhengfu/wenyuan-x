<template>
  <NSpace vertical :size="18"
    ><NDescriptions bordered :column="2"
      ><NDescriptionsItem label="来源单号">{{ data.sourceDocumentNo }}</NDescriptionsItem
      ><NDescriptionsItem label="接收与验收">{{ returnCaseStatus(data) }}</NDescriptionsItem
      ><NDescriptionsItem label="实际接收时间">{{
        formatLocalDateValue(data.executedAt, 'YYYY-MM-DD HH:mm:ss')
      }}</NDescriptionsItem></NDescriptions
    >
    <NAlert v-if="!data.sourceSnapshot" type="warning"
      >旧单尚未核实逐件来源资料，请按真实原件补录后办理；历史执行记录继续保留。</NAlert
    >
    <NCard v-if="data.sourceSnapshot" title="来源签署凭证" size="small"
      ><NSpace vertical
        ><NText strong>来源协议或提用安全方案</NText>
        <HFileList :files="sourceOriginals.files(data.sourceSnapshot.agreementFileIds)" :original-url="sourceOriginals.originalUrl" :revision="sourceOriginals.archive.value" show-download-btn />
        <NText strong>原出库双方签署</NText>
        <HFileList :files="sourceOriginals.files(data.sourceSnapshot.outSignedFileIds)" :original-url="sourceOriginals.originalUrl" :revision="sourceOriginals.archive.value" show-download-btn />
        <NText v-if="sourceOriginals.loading.value" depth="3">正在读取来源出库原件…</NText>
        <NAlert v-if="sourceOriginals.error.value" type="warning">{{ sourceOriginals.error.value }}</NAlert>
        <NButton v-if="sourceAvailable" size="small" secondary @click="openSource"
          >查看来源案卷及出库时永久原件</NButton></NSpace
    ></NCard>
    <CollectionEvidenceReadView
      v-if="data.inspection"
      :fields="returnHeaderFields(data.sourceBizType === 'COLLECTION_LOAN')"
      :data="data.inspection"
      :archive-files="archiveFiles" :original-url="originalUrl"
    />
    <NCard
      v-for="source in data.sourceSnapshot?.items || []"
      :key="source.collectionId"
      :title="source.content?.formalName || source.collectionId"
      size="small"
      ><NSpace vertical
        ><NText>原出库现状：{{ source.outboundCondition?.condition }}</NText
        ><HFileList
          v-if="source.outboundCondition?.imageFileIds"
          :files="sourceOriginals.files(source.outboundCondition.imageFileIds)"
          :original-url="sourceOriginals.originalUrl"
          :revision="sourceOriginals.archive.value"
          show-download-btn
        /><CollectionEvidenceReadView
          v-if="item(source.collectionId)"
          :fields="returnItemFields(item(source.collectionId))"
          :data="item(source.collectionId)"
          :archive-files="archiveFiles" :original-url="originalUrl"
        />
        <NAlert v-if="held(source.collectionId)" type="warning"
          >本件已接收保管，异常尚待调查处理，不能继续提用或借出。</NAlert
        ></NSpace
      ></NCard
    >
    <NAlert v-for="pending in data.pendingRequirements || []" :key="pending.collectionId" type="warning"
      >{{ pending.collectionId }}：{{
        pending.reports?.length
          ? pending.reports
              .map(
                (r: any) =>
                  `${reportTargets.find((t) => t.value === r.target)?.label} / ${reportKinds.find((k) => k.value === r.kind)?.label}`,
              )
              .join('；')
          : '按本件数量、价值及具体情形核实报告程序'
      }}。解除待核查前须核实实际原件。</NAlert
    >
    <NCard
      v-for="r in data.complianceRecords || []"
      :key="r.id"
      :title="r.kind === 'REPORT' ? '实际报告原件' : '双方说明、调查及处理结果'"
      size="small"
      ><CollectionEvidenceReadView
        :fields="
          r.kind === 'REPORT'
            ? returnReportFields(data.sourceSnapshot?.items || [])
            : returnResolutionFields(data.sourceSnapshot?.items || [])
        "
        :data="r.content"
        :archive-files="archiveFiles" :original-url="originalUrl"
    /></NCard>
  </NSpace>
</template>
<script setup lang="ts">
import { type CollectionReadonlyFile } from '../utils/readonlyFiles';
import { computed } from 'vue';
import { useReturnSourceOriginals } from '../utils/returnSourceOriginals';
import { formatLocalDateValue } from '@/utils';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import {
  returnHeaderFields,
  returnItemFields,
  returnReportFields,
  returnResolutionFields,
  returnCaseStatus,
  reportTargets,
  reportKinds,
} from '../utils/returnEvidenceFields';
const props = defineProps<{ data: Record<string, any>; originalUrl?: (fileId: string) => string; archiveFiles?: CollectionReadonlyFile[] }>();
const sourceOriginals = useReturnSourceOriginals(() => props.data);
const emit = defineEmits<{ (e: 'open-source', source: { bizType: string; bizId: string }): void }>();
const sourceAvailable = computed(() =>
  ['COLLECTION_LOAN', 'COLLECTION_REQUISITION'].includes(props.data.sourceBizType) && !!props.data.sourceBizId,
);
function openSource() {
  if (sourceAvailable.value) emit('open-source', { bizType: props.data.sourceBizType, bizId: props.data.sourceBizId });
}
const item = (id: string) => props.data.inspection?.items?.find((i: any) => i.collectionId === id);
const held = (id: string) =>
  props.data.executionState === 'EXECUTED' &&
  props.data.inspection &&
  !String(props.data.acceptedCollectionIds || '')
    .split(',')
    .includes(id);
</script>
