<template>
  <div>
    <n-space style="margin-bottom: 8px">
      <n-tag type="success" round>成功 {{ summary.success }}</n-tag>
      <n-tag type="error" round>失败 {{ summary.fail }}</n-tag>
      <n-tag type="warning" round>跳过 {{ summary.skipped }}</n-tag>
      <n-tag type="info" round>待发 {{ summary.wait }}</n-tag>
      <n-tag type="info" round>重试中 {{ summary.retrying }}</n-tag>
      <n-tag round>合计 {{ summary.total }}</n-tag>
    </n-space>
    <n-data-table
      size="small"
      :columns="columns"
      :data="dispatches"
      :max-height="360"
      :scroll-x="800"
    />
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue';
import { type DataTableColumns } from 'naive-ui';
import { useDict, getDictLabel } from '@/utils/dict';
import { renderTimeCell } from './timeCell';
import type {
  MessageNotifyDispatchRow,
  MessageNotifyDispatchSummary,
} from '../typings/message';

defineProps<{
  dispatches: MessageNotifyDispatchRow[];
  summary: MessageNotifyDispatchSummary;
}>();

const { options: channelDict } = useDict('notify_channel');
const { options: statusDict } = useDict('notify_dispatch_status');

const columns = computed<DataTableColumns<MessageNotifyDispatchRow>>(() => [
  {
    key: 'channel',
    title: '通道',
    width: 110,
    render: (row) => getDictLabel(channelDict.value as any[], row.channel),
  },
  { key: 'userName', title: '接收人', width: 140, ellipsis: { tooltip: true } },
  { key: 'target', title: '投递地址', width: 180, ellipsis: { tooltip: true } },
  {
    key: 'dispatchStatus',
    title: '状态',
    width: 90,
    render: (row) => getDictLabel(statusDict.value as any[], row.dispatchStatus),
  },
  { key: 'retryCount', title: '重试', width: 60, align: 'right' },
  { key: 'message', title: '说明', minWidth: 220, ellipsis: { tooltip: true } },
  {
    key: 'updateTime',
    title: '更新时间',
    width: 180,
    render: (row: MessageNotifyDispatchRow) => renderTimeCell(row.updateTime || row.createTime),
  },
]);
</script>
