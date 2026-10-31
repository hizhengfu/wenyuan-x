<template>
  <div class="relation-points-page">
    <HPageDataTable
      ref="tableRef"
      :title="pageTitle"
      :columns="columns"
      :fetch-data="fetchRelations"
      :params="{}"
      row-key-field="id"
      :scroll-x="1420"
      :is-add="false"
      :is-export="false"
      :show-operate="false"
      :enable-drag="false"
    />
    <CulturalBasicDetailDrawer
      v-model:show="detailVisible"
      :basic-id="String(activePoint?.id || '')"
      :title="`查看「${activePoint?.publishName || '基础档案'}」`"
    />
  </div>
</template>

<script lang="ts" setup>
import { computed, h, ref, watch } from 'vue'
import { useEventBus } from '@vueuse/core'
import { NButton, useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { useRoute } from 'vue-router'
import { confirm } from '@/utils'
import { useAuthStore } from '@/store'
import * as api from '@/modules/archive/api/basic/culturalBasicGroup'
import CulturalBasicDetailDrawer from '@/modules/archive/components/basic/CulturalBasicDetailDrawer.vue'
import { CULTURAL_BASIC_GROUP_RELATION_CHANGED, type CulturalBasicGroupRelationChangedEvent } from '@/modules/archive/components/basicGroup/relationEvents'

const route = useRoute()
const message = useMessage()
const authStore = useAuthStore()
const relationBus = useEventBus<CulturalBasicGroupRelationChangedEvent>(CULTURAL_BASIC_GROUP_RELATION_CHANGED)
const tableRef = ref<any>(null)
const detailVisible = ref(false)
const activePoint = ref<Record<string, any> | null>(null)
const groupId = computed(() => String(route.query.groupId || ''))
const pageTitle = computed(() => String(route.query.title || '关联点信息'))
const canEditRelation = computed(() => authStore.hasPermission('CULTURAL_RECORD_BASIC_ALL,CULTURAL_RECORD_BASIC_EDIT'))

const columns: DataTableColumns<Record<string, any>> = [
  {
    key: 'publishName', title: '公布名称', fixed: 'left', width: 220, ellipsis: { tooltip: true },
    render(row) {
      return h(
        'a',
        {
          class: ['data-table-link', 'relation-point-name-link'],
          onClick: () => { activePoint.value = row; detailVisible.value = true },
        },
        display(row.publishName),
      )
    },
  },
  { key: 'publishNo', title: '公布编号', width: 180, ellipsis: { tooltip: true } },
  { key: 'publishLevel', title: '公布级别', width: 220, dictType: 'four_cultural_relic_level', ellipsis: { tooltip: true } } as any,
  { key: 'publishCategory', title: '公布类别', width: 190, dictType: 'four_cultural_relic_type', ellipsis: { tooltip: true } } as any,
  { key: 'publishAddress', title: '公布地址', width: 360, ellipsis: { tooltip: true } },
  { key: 'publishBatch', title: '公布批次', width: 160, dictType: 'basic_public_batch', ellipsis: { tooltip: true } } as any,
  {
    key: 'operate', title: '操作', fixed: 'right', width: 100, align: 'center',
    render(row) {
      if (!canEditRelation.value) return ''
      return h(NButton, { size: 'tiny', type: 'error', onClick: () => unlink(row) }, { default: () => '解除' })
    },
  },
]

watch(groupId, () => tableRef.value?.handleRefresh?.())

function fetchRelations(params: Record<string, any>) {
  if (!groupId.value) return Promise.resolve({ data: { list: [], total: 0, pages: 1 } })
  return api.relations(groupId.value, params)
}

function unlink(row: Record<string, any>) {
  if (!groupId.value || !row.id) return
  confirm({
    title: '确认解除关联',
    content: '是否解除当前组关联',
    onPositiveClick: () => api.deleteRelation(groupId.value, row.id).then((resp: any) => {
      if (resp?.data?.success === false) {
        message.warning('关联状态已变化，请刷新后重试')
        return
      }
      message.success('关联已解除')
      tableRef.value?.handleRefresh?.()
      relationBus.emit({ groupId: groupId.value, basicId: String(row.id) })
    }),
  })
}

function display(value: unknown) {
  return String(value ?? '').trim() || '--'
}
</script>

<style scoped>
.relation-points-page { display: flex; min-height: 100%; flex: 1; flex-direction: column; }
.relation-point-name-link { color: #2080f0; cursor: pointer; text-decoration: none; }
.relation-point-name-link:hover { color: #4098fc; }
</style>
