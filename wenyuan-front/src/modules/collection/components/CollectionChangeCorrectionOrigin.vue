<template>
  <NDescriptions v-if="origin" title="补正来由" bordered :column="1">
    <NDescriptionsItem label="原申请">{{ origin.previousChangeId }}</NDescriptionsItem>
    <NDescriptionsItem label="原审批">{{ origin.previousInstanceId }}</NDescriptionsItem>
    <NDescriptionsItem label="补正原因" content-style="white-space: pre-wrap; overflow-wrap: anywhere;">{{ origin.reason }}</NDescriptionsItem>
    <NDescriptionsItem label="本次补正依据"><CollectionCaseOriginalFiles v-if="archiveId" case-type="change" :archive-id="archiveId" :case-id="caseId" :archive-files="archiveFiles" :value="origin.evidenceFileIds" /><NText v-else depth="3">归档原件读取后显示</NText></NDescriptionsItem>
    <NDescriptionsItem label="记录时间">{{ formatDateTime(origin.recordedAt) }}</NDescriptionsItem>
  </NDescriptions>
</template>
<script setup lang="ts">
import { formatDateTime } from '@/utils';
import type { CollectionReadonlyFile } from '../utils/readonlyFiles';
import type { ChangeCorrectionOrigin } from '../api/change';
import CollectionCaseOriginalFiles from './CollectionCaseOriginalFiles.vue';
defineProps<{ origin?: ChangeCorrectionOrigin | null; caseId?: string | null; archiveFiles?:CollectionReadonlyFile[]; archiveId?:string }>();
</script>
