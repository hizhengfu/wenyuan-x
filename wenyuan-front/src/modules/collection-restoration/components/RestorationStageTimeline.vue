<template>
  <div>
    <NEmpty v-if="!rows.length" description="暂无工序记录" />
    <template v-else>
      <NTimeline>
        <NTimelineItem
          v-for="(row, index) in rows"
          :key="row.id || index"
          :type="row.endTime ? 'success' : row.startTime ? 'info' : 'default'"
          :title="`${index + 1}. ${row.stageName || '未命名工序'}${row.methodName ? ` · ${row.methodName}` : ''}`"
          :time="`${formatDateTime(row.startTime, 'YYYY-MM-DD HH:mm')} ~ ${row.endTime ? formatDateTime(row.endTime, 'YYYY-MM-DD HH:mm') : '进行中'}　${hoursText(processHours(row))}`"
        >
          <NDescriptions label-placement="left" :column="2" size="small">
            <NDescriptionsItem label="主操作人">{{ processPeople(row).lead?.name || '--' }}</NDescriptionsItem>
            <NDescriptionsItem label="协助人员">{{ personNames(processPeople(row).assistants) || '--' }}</NDescriptionsItem>
            <NDescriptionsItem label="用料">{{ materialsText(row.materials) || '--' }}</NDescriptionsItem>
            <NDescriptionsItem label="工具">{{ row.tools || '--' }}</NDescriptionsItem>
          </NDescriptions>
        </NTimelineItem>
      </NTimeline>
      <NDivider title-placement="left">人员分工</NDivider>
      <NDataTable :columns="divisionColumns" :data="division" :bordered="false" size="small" :row-key="(r: DivisionRow) => r.key" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { NDataTable, NDescriptions, NDescriptionsItem, NDivider, NEmpty, NTimeline, NTimelineItem } from 'naive-ui';
import type { RestorationProcessVo } from '../typings';
import { formatDateTime } from '@/utils/datetime';
import {
  buildDivision, hoursText, materialsText, personNames, processHours, processPeople, sortProcessesByTime, type DivisionRow,
} from '../utils/restorationReport';

const props = defineProps<{ processes: RestorationProcessVo[] }>();
const rows = computed(() => sortProcessesByTime(props.processes));
const division = computed(() => buildDivision(props.processes));
const divisionColumns = [
  { title: '人员', key: 'name', width: 120 },
  { title: '主操作工序', key: 'leadStages', ellipsis: { tooltip: true }, render: (r: DivisionRow) => r.leadStages.join('、') || '--' },
  { title: '协助工序', key: 'assistStages', ellipsis: { tooltip: true }, render: (r: DivisionRow) => r.assistStages.join('、') || '--' },
  { title: '主操作用时', key: 'hours', width: 120, render: (r: DivisionRow) => (r.leadStages.length ? hoursText(r.hours) : '--') },
];
</script>
