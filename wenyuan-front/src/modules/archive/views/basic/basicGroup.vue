<template>
  <HPageDataTable
    ref="tableRef"
    title="基础档案组"
    :fetch-data="fetchGroups"
    :params="queryParams"
    :columns="columns"
    :fun-api-delete="api.del"
    :scroll-x="1660"
    :show-query-reset-button="true"
    :enable-drag="false"
    permission="CULTURAL_RECORD_BASIC"
    single-sorter
    @add="openCreate"
    @row-edit="openEdit"
    @row-view="openView"
  >
    <template #queryForm>
      <NFormItem label="公布名称"><NInput v-model:value="queryParams.name" clearable placeholder="请输入名称" /></NFormItem>
      <NFormItem label="公布编号"><NInput v-model:value="queryParams.code" clearable placeholder="请输入公布编号" /></NFormItem>
      <NFormItem label="公布级别">
        <NSelect v-model:value="queryParams.publishLevel" :options="PUBLISH_LEVEL_OPTIONS" clearable filterable placeholder="请选择公布级别" />
      </NFormItem>
      <NFormItem label="公布类别">
        <NSelect v-model:value="queryParams.publishCategory" :options="PUBLISH_CATEGORY_OPTIONS" clearable filterable placeholder="请选择公布类别" />
      </NFormItem>
      <NFormItem label="公布批次">
        <NSelect v-model:value="queryParams.publishBatch" :options="PUBLISH_BATCH_OPTIONS" clearable filterable placeholder="请选择公布批次" />
      </NFormItem>
      <NFormItem label="所属地区"><CascadeArea v-model:value="queryParams.areaId" clearable /></NFormItem>
      <NFormItem label="所属机构"><GroupSelect v-model:value="queryParams.groupId" /></NFormItem>
      <NFormItem label="关联数"><RelationCountRangeInput v-model:value="queryParams.relationCount" /></NFormItem>
    </template>

    <HPageForm
      v-model:show="formVisible"
      :model="form"
      :rules="rules"
      :title="formTitle"
      :handle-submit="submitForm"
      :ref-table="tableRef"
      width="80%"
      label-placement="top"
    >
      <NSpin :show="formLoading">
        <NGrid :cols="2" :x-gap="18">
          <NGi><NFormItem label="公布名称" path="name"><NInput v-model:value="form.name" clearable /></NFormItem></NGi>
          <NGi><NFormItem label="公布编号" path="code"><NInput v-model:value="form.code" clearable /></NFormItem></NGi>
          <NGi><NFormItem label="公布级别"><NSelect v-model:value="form.publishLevel" :options="PUBLISH_LEVEL_OPTIONS" clearable filterable /></NFormItem></NGi>
          <NGi><NFormItem label="公布类别"><NSelect v-model:value="form.publishCategory" :options="PUBLISH_CATEGORY_OPTIONS" clearable filterable /></NFormItem></NGi>
          <NGi><NFormItem label="公布批次"><NSelect v-model:value="form.publishBatch" :options="PUBLISH_BATCH_OPTIONS" clearable filterable /></NFormItem></NGi>
          <NGi><NFormItem label="所属地区"><CascadeArea v-model:value="form.areaId" clearable /></NFormItem></NGi>
          <NGi><NFormItem label="所属机构"><GroupSelect v-model:value="form.groupId" /></NFormItem></NGi>
          <NGi><NFormItem label="标签"><NInput v-model:value="form.tag" clearable /></NFormItem></NGi>
        </NGrid>
        <NFormItem label="图片"><HAvatar v-model:value="form.image" /></NFormItem>
        <NFormItem label="备注"><NInput v-model:value="form.remarks" type="textarea" :rows="4" /></NFormItem>
      </NSpin>
    </HPageForm>

    <HPageForm
      v-model:show="detailVisible"
      :model="activeRow || {}"
      :title="`查看「${activeRow?.name || '基础档案组'}」`"
      width="80%"
      :disabled="true"
      :enable-cancel="false"
    >
      <NSpin :show="detailLoading">
        <CulturalBasicGroupDetail :data="activeRow || {}" />
      </NSpin>
    </HPageForm>

    <CulturalRelationEditor v-model:show="relationVisible" :row="activeRow" @changed="refreshRelations" />
  </HPageDataTable>
</template>

<script lang="ts" setup>
import { computed, h, reactive, ref } from 'vue'
import { useEventBus } from '@vueuse/core'
import { NButton, useMessage } from 'naive-ui'
import { useRouter } from 'vue-router'
import { cloneDeep } from 'lodash-es'
import { useAuthStore } from '@/store'
import * as api from '@/modules/archive/api/basic/culturalBasicGroup'
import CascadeArea from '@/modules/sys/components/form/field/CascadeArea.vue'
import GroupSelect from '@/modules/sys/components/form/field/GroupSelect.vue'
import CulturalRelationEditor from '@/modules/archive/components/basicGroup/CulturalRelationEditor.vue'
import CulturalBasicGroupDetail from '@/modules/archive/components/basicGroup/CulturalBasicGroupDetail.vue'
import RelationCountRangeInput, { type RelationCountValue } from '@/modules/archive/components/basicGroup/RelationCountRangeInput.vue'
import { CULTURAL_BASIC_GROUP_RELATION_CHANGED, type CulturalBasicGroupRelationChangedEvent } from '@/modules/archive/components/basicGroup/relationEvents'
import {
  PUBLISH_BATCH_OPTIONS,
  PUBLISH_CATEGORY_OPTIONS,
  PUBLISH_LEVEL_OPTIONS,
} from '@/modules/archive/components/basicGroup/culturalBasicGroupOptions'

const message = useMessage()
const router = useRouter()
const authStore = useAuthStore()
const relationBus = useEventBus<CulturalBasicGroupRelationChangedEvent>(CULTURAL_BASIC_GROUP_RELATION_CHANGED)
const tableRef = ref<any>(null)
const formVisible = ref(false)
const detailVisible = ref(false)
const relationVisible = ref(false)
const formLoading = ref(false)
const detailLoading = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const activeRow = ref<Record<string, any> | null>(null)
const defaultForm = { id: '', name: '', code: '', publishLevel: null, publishCategory: null, publishBatch: null, areaId: null, groupId: null, tag: '', image: '', remarks: '' }
const form = ref<Record<string, any>>(cloneDeep(defaultForm))
const queryParams = reactive({ name: null, code: null, publishLevel: null, publishCategory: null, publishBatch: null, areaId: null, groupId: null, relationCount: { mode: null, min: null, max: null } as RelationCountValue })
const rules = { name: { required: true, message: '请输入公布名称' } }
const formTitle = computed(() => formMode.value === 'create' ? '添加基础档案组' : `编辑「${form.value.name || '基础档案组'}」`)
const canEditRelation = computed(() => authStore.hasPermission('CULTURAL_RECORD_BASIC_ALL,CULTURAL_RECORD_BASIC_EDIT'))

const sortableColumns = [
  { key: 'name', title: '公布名称', fixed: 'left', width: 200, columnType: 'title', ellipsis: { tooltip: true } },
  { key: 'code', title: '公布编号', width: 160, ellipsis: { tooltip: true } },
  { key: 'publishLevel', title: '公布级别', width: 220, dictType: 'four_cultural_relic_level', ellipsis: { tooltip: true } },
  { key: 'publishCategory', title: '公布类别', width: 180, dictType: 'four_cultural_relic_type', ellipsis: { tooltip: true } },
  { key: 'publishBatch', title: '公布批次', width: 140, dictType: 'basic_public_batch', ellipsis: { tooltip: true } },
  { key: 'areaName', title: '所属地区', width: 160, ellipsis: { tooltip: true } },
  { key: 'tag', title: '标签', width: 160, ellipsis: { tooltip: true } },
  { key: 'remarks', title: '备注', width: 220, ellipsis: { tooltip: true } },
].map((column) => ({ ...column, sorter: true }))

const columns = [
  ...sortableColumns,
  {
    key: 'relationCount', title: '关联数', width: 100, fixed: 'right', align: 'center', sorter: true,
    render(row: Record<string, any>) {
      const count = Number(row.relationCount || 0)
      if (!count) return ''
      return h(NButton, { text: true, type: 'primary', onClick: (event: MouseEvent) => { event.stopPropagation(); openRelationPoints(row) } }, { default: () => String(count) })
    },
  },
  {
    key: 'relation', title: '编辑关联', width: 100, fixed: 'right', align: 'center',
    render(row: Record<string, any>) {
      if (!canEditRelation.value) return ''
      return h(NButton, { text: true, type: 'primary', onClick: (event: MouseEvent) => { event.stopPropagation(); activeRow.value = row; relationVisible.value = true } }, { default: () => '关联' })
    },
  },
]

function fetchGroups(params: Record<string, any>) {
  const requestParams = { ...params }
  const range = requestParams.relationCount as RelationCountValue | undefined
  delete requestParams.relationCount
  if (range?.mode) {
    if (range.min !== null && range.max !== null && range.min > range.max) {
      message.warning('关联数最小值不能大于最大值')
      return Promise.reject(new Error('invalid relation count range'))
    }
    if (range.min !== null) requestParams.relationCountMin = range.min
    if (range.max !== null) requestParams.relationCountMax = range.max
  }
  return api.list(requestParams)
}

function openCreate() {
  formMode.value = 'create'
  form.value = cloneDeep(defaultForm)
  formVisible.value = true
}

async function openEdit(row: Record<string, any>) {
  formMode.value = 'edit'
  form.value = cloneDeep(defaultForm)
  formLoading.value = true
  formVisible.value = true
  try {
    const resp: any = await api.get(row.id)
    Object.assign(form.value, resp?.data || row)
  } finally {
    formLoading.value = false
  }
}

async function openView(row: Record<string, any>) {
  activeRow.value = row
  detailVisible.value = true
  detailLoading.value = true
  try {
    const resp: any = await api.get(row.id)
    activeRow.value = resp?.data || row
  } finally {
    detailLoading.value = false
  }
}

function submitForm(data: Record<string, any>) {
  return formMode.value === 'create' ? api.add(data) : api.edit(data)
}

function openRelationPoints(row: Record<string, any>) {
  void router.push({ path: '/archive/basic/basicGroupRelationPoints', query: { groupId: row.id, title: `「${row.name || '基础档案组'}」关联点信息` } })
}

function refreshRelations() {
  tableRef.value?.handleRefresh?.()
  if (activeRow.value?.id) relationBus.emit({ groupId: String(activeRow.value.id) })
}

relationBus.on(() => tableRef.value?.handleRefresh?.())
</script>

<style scoped>
:deep(.n-data-table-sorter--asc .n-base-icon) { transform: rotate(0deg) !important; }
:deep(.n-data-table-sorter--desc .n-base-icon) { transform: rotate(-180deg) !important; }
</style>
