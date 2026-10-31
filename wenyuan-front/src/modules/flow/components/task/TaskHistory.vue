<template>
  <n-data-table
    v-if="logs.length > 0"
    :columns="columns"
    :data="logs"
    :pagination="false"
    size="small"
    striped
  />
  <n-empty v-else description="暂无操作记录" size="small" />
</template>

<script setup lang="ts">
import { type DataTableColumns } from 'naive-ui'
import dayjs from 'dayjs'
import type { FlowInstanceLog } from '../../typings/flow'
import { useDict } from '@/utils/dict'
import { flowLogActionLabel } from '../../utils/flowLogActionDisplay'

defineProps<{ logs: FlowInstanceLog[] }>()

const { options: actionDict } = useDict('flow_instance_log_action')

const columns: DataTableColumns<FlowInstanceLog> = [
  {
    title: '节点',
    key: 'nodeName',
    width: 100,
    render: (row) => row.nodeName || '-',
  },
  {
    title: '操作人',
    key: 'operatorName',
    width: 120,
    render: (row) => row.operatorName || row.operator || '-',
  },
  {
    title: '操作',
    key: 'action',
    width: 100,
    align: 'center',
    render: (row) => {
      if (!row.action) return row.actionLabel || '-'
      return flowLogActionLabel(row, actionDict.value as any[])
    },
  },
  {
    title: '备注',
    key: 'comment',
    ellipsis:{tooltip: { width: 600 }},
    render: (row) => row.comment || '-',
  },
  {
    title: '时间',
    key: 'createTime',
    width: 200,
    render: (row) => row.createTime ? dayjs(row.createTime).format('YYYY-MM-DD HH:mm:ss') : '-',
  },
]
</script>
