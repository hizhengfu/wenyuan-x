<template>
  <NDescriptions label-placement="left" :column="2" bordered size="small">
    <NDescriptionsItem label="修后现状" :span="2"><span style="white-space: pre-wrap">{{ archive?.afterDescription || '--' }}</span></NDescriptionsItem>
    <NDescriptionsItem label="档案材料清单" :span="2">{{ archiveMaterials || '--' }}</NDescriptionsItem>
    <NDescriptionsItem label="工序用料汇总" :span="2">{{ processMaterials || '--' }}</NDescriptionsItem>
    <NDescriptionsItem label="验收结论">{{ acceptance?.conclusion ? conclusionLabels[acceptance.conclusion] || acceptance.conclusion : '--' }}</NDescriptionsItem>
    <NDescriptionsItem label="验收时间">{{ acceptance?.acceptedTime ? formatDateTime(acceptance.acceptedTime) : '--' }}</NDescriptionsItem>
    <NDescriptionsItem label="验收意见" :span="2"><span style="white-space: pre-wrap">{{ acceptance?.opinion || '--' }}</span></NDescriptionsItem>
    <NDescriptionsItem label="工序用时合计">{{ hoursText(hours.total) }}（{{ hours.counted }} 道{{ hours.missing ? `，${hours.missing} 道未登记` : '' }}）</NDescriptionsItem>
    <NDescriptionsItem label="合同金额">{{ contract ? fenToYuan(contract.contractAmount) : '--' }}</NDescriptionsItem>
  </NDescriptions>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { NDescriptions, NDescriptionsItem } from 'naive-ui';
import type { RestorationAcceptanceVo, RestorationArchiveVo, RestorationContractVo, RestorationProcessVo } from '../typings';
import { formatDateTime } from '@/utils/datetime';
import { conclusionLabels, fenToYuan, hoursText, materialText, materialsText, mergeMaterials, sumProcessHours } from '../utils/restorationReport';

const props = defineProps<{
  processes: RestorationProcessVo[];
  archive?: RestorationArchiveVo | null;
  acceptance?: RestorationAcceptanceVo | null;
  contract?: RestorationContractVo | null;
}>();
const hours = computed(() => sumProcessHours(props.processes));
const archiveMaterials = computed(() => materialsText(props.archive?.materialsUsed) || props.archive?.materialsUsed || '');
const processMaterials = computed(() => mergeMaterials(props.processes.map((r) => r.materials)).map(materialText).join('；'));
</script>
