<template>
  <NSpace vertical :size="18">
    <NDescriptions bordered :column="2">
      <NDescriptionsItem label="提用单号">{{ data.documentNo || '未编制' }}</NDescriptionsItem>
      <NDescriptionsItem label="提用部门">{{ data.department }}</NDescriptionsItem>
      <NDescriptionsItem label="提用事由" :span="2" content-style="white-space: pre-wrap; overflow-wrap: anywhere;">{{
        requisitionPurposeOptions.find((o) => o.value === data.purpose)?.label || data.purpose
      }}</NDescriptionsItem>
      <NDescriptionsItem label="提用依据" :span="2" content-style="white-space: pre-wrap; overflow-wrap: anywhere;">{{ data.basis }}</NDescriptionsItem>
      <NDescriptionsItem label="归库位置"
        ><WarehouseTreeSelect :value="data.toWarehouseId" disabled
      /></NDescriptionsItem>
      <NDescriptionsItem label="实际出库时间">{{
        data.outExecuted ? formatLocalDateValue(data.outExecutedTime, 'YYYY-MM-DD HH:mm:ss') : '尚未执行出库'
      }}</NDescriptionsItem>
    </NDescriptions>
    <CollectionEvidenceReadView :fields="baseFields" :data="data" :archive-files="archiveFiles" :original-url="originalUrl" />
    <CollectionEvidenceReadView v-if="data.dossier" :fields="requisitionDossierFields" :data="data.dossier" :archive-files="archiveFiles" :original-url="originalUrl" />
    <NAlert v-else :type="data.flowStatus === 'DRAFT' ? 'info' : 'warning'">{{
      data.flowStatus === 'DRAFT'
        ? '尚未填写完整使用及安全方案。请在办理页点击“填写完整使用及安全方案”，补充使用安排、安全措施、出归库时间和责任人后，再提交三方确认。'
        : '本份记录未包含完整使用及安全方案，请核对原方案和实际责任人确认记录；审批状态本身不能证明这些材料齐全。'
    }}</NAlert>
    <NCard
      v-for="item in data.baseline?.items || []"
      :key="item.collectionId"
      size="small"
      :title="item.content?.formalName || item.collectionId"
    >
      <NText depth="3"
        >总登记号：{{ summary(item.collectionId)?.totalRegNo || '未随本次方案留存' }}；原登记状况：{{
          item.content?.completeStatus || '未填写'
        }}</NText
      >
      <CollectionEvidenceReadView
        v-if="condition(item.collectionId)"
        :fields="conditionFields"
        :data="condition(item.collectionId)"
        :archive-files="archiveFiles" :original-url="originalUrl"
      />
    </NCard>
    <template v-if="data.handover"
      ><NText strong>已登记的实际点交原件</NText
      ><CollectionEvidenceReadView :fields="requisitionHandoverFields" :data="data.handover.content" :archive-files="archiveFiles" :original-url="originalUrl"
    /></template>
  </NSpace>
</template>
<script setup lang="ts">
import { type CollectionReadonlyFile } from '../utils/readonlyFiles';
import { formatLocalDateValue } from '@/utils';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import WarehouseTreeSelect from './WarehouseTreeSelect.vue';
import {
  requisitionDossierFields,
  requisitionHandoverFields,
  requisitionPurposeOptions,
  requisitionYesNo,
} from '../utils/requisitionEvidenceFields';
import type { EvidenceField } from '../utils/evidenceFields';
const props = defineProps<{ data: Record<string, any>; originalUrl?: (fileId: string) => string; archiveFiles?: CollectionReadonlyFile[] }>();
const baseFields: EvidenceField[] = [
  { key: 'operator', label: '申请经办人', type: 'user' },
  { key: 'reviewer', label: '一级文物复核人', type: 'user' },
  { key: 'attachmentIds', label: '提用依据附件', type: 'file' },
];
const conditionFields: EvidenceField[] = [
  { key: 'condition', label: '出库点交时的保存现状', type: 'textarea' },
  { key: 'textNeedsImages', label: '是否需要影像补充', type: 'select', options: requisitionYesNo },
  { key: 'imageFileIds', label: '本件状况影像', type: 'file' },
];
const condition = (id: string) => props.data.handover?.content?.items?.find((item: any) => item.collectionId === id);
const summary = (id: string) => props.data.collectionSummaries?.find((item: any) => item.id === id);
</script>
