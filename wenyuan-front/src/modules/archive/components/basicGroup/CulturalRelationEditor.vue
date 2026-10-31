<template>
  <HPageForm
    v-model:show="innerShow"
    :model="filters"
    :title="`编辑关联「${row?.name || '基础档案组'}」`"
    width="80%"
    :handle-submit="submitRelation"
    submit-text="提交"
    label-placement="top"
    size="small"
  >
    <div class="relation-drawer">
      <section class="relation-section relation-group-summary">
        <div class="relation-section-title">当前数据组</div>
        <NSpin :show="contextLoading">
          <div class="relation-group-summary-grid">
            <SummaryItem label="公布名称" :value="summary.name" />
            <SummaryItem label="公布编号" :value="summary.code" />
            <SummaryItem label="公布级别" :value="summary.publishLevel" />
            <SummaryItem label="公布类别" :value="summary.publishCategory" />
            <SummaryItem label="公布批次" :value="summary.publishBatch" />
            <SummaryItem label="所属地区" :value="summary.regionName || summary.areaName" />
            <SummaryItem label="备注" :value="summary.remarks" wide />
          </div>
        </NSpin>
      </section>

      <section class="relation-section">
        <div class="relation-section-title">高级筛选</div>
        <div class="relation-filter-search-grid">
          <NFormItem label="名称/别名" :show-feedback="false">
            <NInput v-model:value="filters.publishName" clearable placeholder="请输入名称或别名" />
          </NFormItem>
          <NFormItem label="编号/代码" :show-feedback="false">
            <NInput v-model:value="filters.publishNo" clearable placeholder="请输入公布编号或代码" />
          </NFormItem>
          <NFormItem label="地址关键词" :show-feedback="false">
            <NInput v-model:value="filters.addressKeyword" clearable placeholder="请输入地名、街道或门牌号" />
          </NFormItem>
        </div>
        <div class="relation-filter-option-grid">
          <RelationFilterOptionGroup v-model:value="filters.publishLevel" label="公布级别" :options="options.publishLevel" />
          <RelationFilterOptionGroup v-model:value="filters.publishCategory" label="公布类别" :options="options.publishCategory" />
          <RelationFilterOptionGroup v-model:value="filters.city" label="公布地域（市级）" :options="cityOptions" />
          <RelationCountyTree v-model:value="filters.county" :cities="visibleCountyCities" />
          <RelationFilterOptionGroup v-model:value="filters.tjYear" label="统计年代" :options="options.tjYear" />
          <RelationFilterOptionGroup v-model:value="filters.syq" label="所有权" :options="options.syq" />
        </div>
        <NSpace justify="end" :size="8">
          <NButton size="small" @click="clearFilters">重置</NButton>
          <NButton size="small" type="primary" @click="searchCandidates">查询</NButton>
        </NSpace>
      </section>

      <section class="relation-section relation-section--points">
        <div class="relation-section-title relation-section-title--split">
          <span>关联文物点</span>
          <span class="relation-selected-count">筛选结果 {{ pager.total }} 个，待关联 {{ selectedIds.length }} 个</span>
        </div>
        <NSpin :show="loading">
          <div v-if="candidates.length" class="relation-point-grid">
            <article
              v-for="item in candidates"
              :key="item.id"
              :class="['relation-point-option', isSelected(item) && 'is-checked', item.relationStatus === 'current' && 'is-current', item.relationStatus === 'other' && 'is-other']"
            >
              <NCheckbox
                :checked="isSelected(item) || item.relationStatus === 'current'"
                :disabled="item.relationStatus !== 'none'"
                aria-label="选择文物点"
                @update:checked="toggleCandidate(item)"
              />
              <div class="relation-point-content">
                <strong class="relation-point-name">{{ display(item.publishName) }}</strong>
                <span class="relation-point-meta">{{ pointMeta(item) }}</span>
                <span class="relation-point-address">{{ item.publishAddress || item.currentAddress || '暂无地址' }}</span>
                <small v-if="item.relationStatus === 'other'" class="relation-owner-name">已关联{{ displayRelationGroup(item) }}</small>
              </div>
              <div v-if="item.relationStatus !== 'none'" class="relation-point-actions">
                <span class="relation-point-status">{{ item.relationStatus === 'current' ? '当前组' : '已关联其他组' }}</span>
                <NButton size="tiny" tertiary type="error" @click="unlink(item)">解除</NButton>
              </div>
            </article>
          </div>
          <NEmpty v-else description="暂无关联文物点" />
        </NSpin>
        <NPagination
          v-if="pager.total > pager.pageSize"
          v-model:page="pager.pageNum"
          v-model:page-size="pager.pageSize"
          :item-count="pager.total"
          :page-sizes="[20, 30, 50]"
          show-size-picker
          @update:page="loadCandidates"
          @update:page-size="changePageSize"
        />
      </section>
    </div>
  </HPageForm>
</template>

<script lang="ts" setup>
import { computed, defineComponent, h, reactive, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { confirm } from '@/utils'
import * as api from '@/modules/archive/api/basic/culturalBasicGroup'
import RelationFilterOptionGroup, { type OptionItem } from './RelationFilterOptionGroup.vue'
import RelationCountyTree, { type LocationTreeNode } from './RelationCountyTree.vue'

const props = defineProps<{ show: boolean; row: Record<string, any> | null }>()
const emit = defineEmits<{ 'update:show': [value: boolean]; changed: [] }>()
const message = useMessage()

const SummaryItem = defineComponent({
  props: { label: { type: String, required: true }, value: { type: [String, Number], default: '' }, wide: { type: Boolean, default: false } },
  setup(itemProps) {
    return () => h('div', { class: ['relation-group-summary-item', itemProps.wide && 'relation-group-summary-item--wide'] }, [
      h('span', itemProps.label), h('strong', display(itemProps.value)),
    ])
  },
})

const contextLoading = ref(false)
const loading = ref(false)
const summary = reactive<Record<string, any>>({})
const filters = reactive({ publishName: null as string | null, publishNo: null as string | null, addressKeyword: null as string | null, publishLevel: [] as string[], publishCategory: [] as string[], city: [] as string[], county: [] as string[], tjYear: [] as string[], syq: [] as string[] })
const options = reactive<Record<'publishLevel' | 'publishCategory' | 'tjYear' | 'syq', OptionItem[]>>({ publishLevel: [], publishCategory: [], tjYear: [], syq: [] })
const cityTree = ref<LocationTreeNode[]>([])
const candidates = ref<Record<string, any>[]>([])
const selectedIds = ref<string[]>([])
const pager = reactive({ pageNum: 1, pageSize: 30, total: 0 })
const innerShow = computed({ get: () => props.show, set: (value) => emit('update:show', value) })
const cityOptions = computed(() => cityTree.value.map(({ label, value }) => ({ label, value })))
const visibleCountyCities = computed(() => cityTree.value.filter((city) => filters.city.includes(city.value)))

watch(() => props.show, (show) => { if (show && props.row?.id) void initialize() })
watch(() => [...filters.city], (cities) => {
  const allowed = new Set(cityTree.value.filter((city) => cities.includes(city.value)).flatMap((city) => city.children.map((county) => county.value)))
  filters.county = filters.county.filter((county) => allowed.has(county))
})

async function initialize() {
  contextLoading.value = true
  Object.keys(summary).forEach((key) => delete summary[key])
  Object.assign(summary, props.row || {})
  candidates.value = []
  selectedIds.value = []
  pager.pageNum = 1
  try {
    const resp: any = await api.relationContext(props.row!.id)
    const data = resp?.data || {}
    Object.assign(summary, props.row || {}, data.group || {})
    ;(['publishLevel', 'publishCategory', 'tjYear', 'syq'] as const).forEach((key) => { options[key] = toOptions(data.options?.[key]) })
    cityTree.value = Array.isArray(data.options?.cityTree) ? data.options.cityTree : []
    applyDefaults(data.defaults || {})
    await loadCandidates()
  } catch {
    message.error('关联信息读取失败')
  } finally {
    contextLoading.value = false
  }
}

function applyDefaults(defaults: Record<string, any>) {
  clearFilterValues()
  Object.entries(defaults).forEach(([key, value]) => {
    if (key in filters) (filters as any)[key] = Array.isArray((filters as any)[key]) ? [...(Array.isArray(value) ? value : [])] : value ?? null
  })
}
function clearFilterValues() {
  Object.keys(filters).forEach((key) => { (filters as any)[key] = Array.isArray((filters as any)[key]) ? [] : null })
  pager.pageNum = 1
}
function clearFilters() { clearFilterValues(); void loadCandidates() }
function searchCandidates() { pager.pageNum = 1; void loadCandidates() }

async function loadCandidates() {
  if (!props.row?.id) return
  loading.value = true
  try {
    const resp: any = await api.relationCandidates(props.row.id, { ...queryParams(), pageNum: pager.pageNum, pageSize: pager.pageSize })
    const data = resp?.data || {}
    candidates.value = data.list || []
    pager.total = Number(data.total || 0)
    pager.pageNum = Number(data.pageNum || pager.pageNum)
    pager.pageSize = Number(data.pageSize || pager.pageSize)
  } catch { message.error('关联候选读取失败') } finally { loading.value = false }
}
function changePageSize(pageSize: number) { pager.pageSize = pageSize; pager.pageNum = 1; void loadCandidates() }
function queryParams() {
  return Object.fromEntries(Object.entries(filters).map(([key, value]) => [key, Array.isArray(value) ? value.join(',') : String(value || '').trim()]).filter(([, value]) => Boolean(value)))
}
function isSelected(row: Record<string, any>) { return selectedIds.value.includes(String(row.id)) }
function toggleCandidate(row: Record<string, any>) {
  if (row.relationStatus !== 'none') return
  const id = String(row.id || '')
  if (!id) return
  selectedIds.value = isSelected(row) ? selectedIds.value.filter((item) => item !== id) : [...selectedIds.value, id]
}
async function submitRelation() {
  if (!props.row?.id || !selectedIds.value.length) {
    message.warning('请选择需要关联的文物点')
    return Promise.reject(new Error('missing relation ids'))
  }
  await api.addRelations(props.row.id, Array.from(new Set(selectedIds.value)))
  message.success('关联已保存')
  selectedIds.value = []
  emit('changed')
  await loadCandidates()
}
function unlink(row: Record<string, any>) {
  const ownerGroupId = String(row.relationGroupId || row.basicGroupId || '').trim()
  if (!ownerGroupId || !row.id) return
  const content = row.relationStatus === 'other' ? `已关联${displayRelationGroup(row)}，是否解除关联` : '是否解除当前组关联'
  confirm({ title: '确认解除关联', content, onPositiveClick: () => api.deleteRelation(ownerGroupId, row.id).then(() => { selectedIds.value = selectedIds.value.filter((id) => id !== String(row.id)); message.success('关联已解除'); emit('changed'); return loadCandidates() }) })
}
function displayRelationGroup(row: Record<string, any>) { return row.relationGroupName ? `「${row.relationGroupName}」组` : '其他组' }
function pointMeta(row: Record<string, any>) { return [row.publishNo, row.publishLevel, row.publishCategory, row.publishBatch].filter(Boolean).join(' / ') || '暂无编号、级别、类别信息' }
function display(value: unknown) { return String(value ?? '').trim() || '--' }
function toOptions(values: unknown): OptionItem[] { return Array.isArray(values) ? values.filter(Boolean).map((value) => ({ label: String(value), value: String(value) })) : [] }
</script>

<style scoped>
.relation-drawer { display: flex; flex-direction: column; gap: 16px; color: #1f2937; }
.relation-section { display: flex; flex-direction: column; gap: 14px; padding: 0 14px 14px; border: 1px solid #f0f2f6; background: #fff; }
.relation-section-title { height: 38px; margin: 0 -14px; padding: 0 14px; background: #f7f8fb; color: #344054; font-size: 13px; font-weight: 600; line-height: 38px; }
.relation-section-title--split { display: flex; align-items: center; justify-content: space-between; }
.relation-selected-count { color: #667085; font-size: 12px; font-weight: 400; }
.relation-group-summary { gap: 0; }
.relation-group-summary-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); border: 1px solid #edf1f5; border-bottom: 0; }
:deep(.relation-group-summary-item) { display: grid; min-width: 0; grid-template-columns: 92px minmax(0, 1fr); min-height: 36px; border-right: 1px solid #edf1f5; border-bottom: 1px solid #edf1f5; }
:deep(.relation-group-summary-item:nth-child(3n)) { border-right: 0; }
:deep(.relation-group-summary-item > span) { display: flex; align-items: center; padding: 7px 10px; border-right: 1px solid #edf1f5; background: #fbfcfe; color: #667085; font-size: 12px; }
:deep(.relation-group-summary-item > strong) { overflow: hidden; padding: 7px 10px; color: #344054; font-size: 13px; font-weight: 500; line-height: 20px; text-overflow: ellipsis; white-space: nowrap; }
:deep(.relation-group-summary-item--wide) { grid-column: span 3; border-right: 0; }
.relation-filter-search-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px 20px; }
.relation-filter-search-grid :deep(.n-form-item) { margin: 0; }
.relation-filter-option-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.relation-section--points { min-height: 360px; }
.relation-point-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.relation-point-option { display: grid; min-height: 96px; grid-template-columns: 20px minmax(0, 1fr) auto; gap: 10px; align-items: start; padding: 12px; border: 1px solid #e3e8ef; border-radius: 3px; background: #f8fafc; }
.relation-point-option.is-checked { border-color: #6f8499; background: #f5f7fa; }
.relation-point-option.is-other { background: #fafafa; }
.relation-point-content { display: flex; min-width: 0; flex-direction: column; gap: 6px; user-select: text; }
.relation-point-name { color: #344054; line-height: 1.4; }
.relation-point-meta, .relation-point-address, .relation-owner-name { color: #667085; font-size: 12px; line-height: 1.4; }
.relation-point-address { overflow-wrap: anywhere; }
.relation-owner-name { color: #8a5a18; }
.relation-point-actions { display: flex; align-items: flex-end; gap: 8px; flex-direction: column; }
.relation-point-status { padding: 3px 6px; border: 1px solid #d8e0e8; background: #f7f9fb; color: #667085; font-size: 12px; line-height: 16px; white-space: nowrap; }
.relation-drawer :deep(.n-pagination) { justify-content: flex-end; margin-top: 12px; }
@media (max-width: 1200px) { .relation-group-summary-grid, .relation-filter-option-grid, .relation-point-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } :deep(.relation-group-summary-item--wide) { grid-column: span 2; } }
@media (max-width: 760px) { .relation-group-summary-grid, .relation-filter-search-grid, .relation-filter-option-grid, .relation-point-grid { grid-template-columns: 1fr; } :deep(.relation-group-summary-item), :deep(.relation-group-summary-item--wide) { grid-column: auto; border-right: 0; } }
</style>
