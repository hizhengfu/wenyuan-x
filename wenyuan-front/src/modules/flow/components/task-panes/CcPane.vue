<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="getCCList"
    :params="paramsForm"
    :showOperate="false"
    :isAdd="false"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="关键字" path="keyword">
        <n-input v-model:value="paramsForm.keyword" placeholder="流程名称/标题" clearable />
      </n-form-item>
      <n-form-item label="未读" path="onlyUnread">
        <n-switch v-model:value="paramsForm.onlyUnread">
          <template #checked>仅未读</template>
          <template #unchecked>全部</template>
        </n-switch>
      </n-form-item>
      <n-form-item>
        <n-button @click="onReadAll">全部标记已读</n-button>
      </n-form-item>
    </template>

    <FlowFormDrawer ref="flowModalRef" />
  </h-page-data-table>
</template>

<script setup lang="ts">
import { ref, h, inject, watch, type Ref } from 'vue'
import { useMessage, NButton, NTag, NSpace } from 'naive-ui'
import { getCCList, readCC, readAllCC } from '../../api/task'
import FlowFormDrawer from '../FlowFormDrawer.vue'

const message = useMessage()
const refPageTable = ref<any>(null)
const paramsForm = ref({ keyword: '', onlyUnread: false })
const flowModalRef = ref<InstanceType<typeof FlowFormDrawer>>()

const refreshSignal = inject<Ref<number> | null>('flowTaskRefresh', null)
if (refreshSignal) {
  watch(refreshSignal, () => refPageTable.value?.handleQuery?.())
}

/** 查看抄送的流程详情，自动标记已读 */
function viewInstance(row: any) {
  if (!row.isRead) {
    readCC(row.id).then(() => refPageTable.value?.handleQuery())
  }
  if (row.instanceId) {
    flowModalRef.value?.openInstance(row.instanceId, row.instanceTitle)
  }
}

/** 仅标记已读，不打开详情 */
async function onMarkRead(row: any) {
  if (row.isRead) return
  await readCC(row.id)
  message.success('已标记已读')
  refPageTable.value?.handleQuery()
}

async function onReadAll() {
  await readAllCC()
  message.success('已全部标记已读')
  refPageTable.value?.handleQuery()
}

const columns = [
  {
    key: 'instanceTitle',
    title: '标题',
    minWidth: '200px',
    ellipsis: { tooltip: true },
    render: (row: any) =>
      h(NButton, { text: true, type: 'primary', onClick: () => viewInstance(row) }, () => row.instanceTitle || '-'),
  },
  { key: 'definitionName', title: '流程名称', width: '150px' },
  { key: 'nodeName', title: '抄送节点', width: '120px' },
  {
    key: 'isRead',
    title: '状态',
    width: '80px',
    align: 'center',
    render: (row: any) =>
      h(NTag, { size: 'small', type: row.isRead ? 'default' : 'info' }, () => (row.isRead ? '已读' : '未读')),
  },
  { key: 'createTime', title: '时间', width: '160px', columnType: 'dateTime' },
  {
    key: 'operate',
    type: 'operate',
    title: '操作',
    width: '180px',
    align: 'center',
    fixed: 'right',
    render: (row: any) =>
      h(NSpace, { size: 4, justify: 'center' }, () =>
        [
          h(NButton, { size: 'tiny', type: 'primary', onClick: () => viewInstance(row) }, () => '详情'),
          !row.isRead &&
            h(NButton, { size: 'tiny', onClick: () => onMarkRead(row) }, () => '标记已读'),
        ].filter(Boolean),
      ),
  },
]
</script>
