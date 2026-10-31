<template>
  <n-drawer v-model:show="show" :width="640" placement="right" :on-after-leave="onClose">
    <n-drawer-content :title="title" closable>
      <n-spin :show="loading">
        <!-- 同 key 所有版本 -->
        <div class="vh-section">
          <div class="vh-section-title">所有版本</div>
          <n-empty v-if="versions.length === 0 && !loading" description="暂无版本数据" />
          <n-list v-else hoverable bordered>
            <n-list-item v-for="v in versions" :key="v.id">
              <div class="vh-version-row">
                <div class="vh-version-main">
                  <span v-if="v.status !== 'DRAFT'" class="vh-version-number">v{{ v.version }}</span>
                  <n-tag size="small" :type="statusTagType(v.status)" :bordered="false">
                    {{ statusLabel(v.status) }}
                  </n-tag>
                  <span v-if="v.name !== currentName" class="vh-version-name">{{ v.name }}</span>
                </div>
                <div class="vh-version-meta">
                  <span class="vh-version-time">{{ formatTime(v.updateTime || v.createTime) }}</span>
                  <n-button size="tiny" @click="onDesign(v)">
                    {{ v.status === 'DRAFT' ? '设计' : '查看' }}
                  </n-button>
                </div>
              </div>
            </n-list-item>
          </n-list>
        </div>

        <!-- 完整审计时间线 -->
        <div class="vh-section">
          <div class="vh-section-title">
            操作日志
            <n-button size="tiny" text @click="loadAuditLogs">
              <template #icon>
                <n-icon><HIcon icon="mdi:refresh" /></n-icon>
              </template>
              刷新
            </n-button>
          </div>
          <n-empty v-if="auditLogs.length === 0 && !loading" description="暂无操作日志" />
          <n-timeline v-else>
            <n-timeline-item
              v-for="log in auditLogs"
              :key="log.id"
              :type="actionTimelineType(log.action)"
              :title="`${log.actionLabel || log.action} · v${log.version}`"
              :content="log.changeSummary || ''"
              :time="formatTime(log.operateTime)"
              :line-type="'default'"
            >
              <template #icon>
                <n-icon>
                  <HIcon :icon="actionIcon(log.action)" />
                </n-icon>
              </template>
              <template #footer>
                <span class="vh-operator">{{ log.operatorName || log.operatorId }}</span>
              </template>
            </n-timeline-item>
          </n-timeline>
        </div>
      </n-spin>
    </n-drawer-content>
  </n-drawer>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import HIcon from '@/components/basic/DynamicIcon.vue'
import { listDefinitions, getDefinitionAuditLog } from '../api/definition'
import type { FlowDefinition, FlowDefinitionAuditLog, DefinitionStatus, DefinitionAuditAction } from '../typings/flow'

const router = useRouter()

const show = ref(false)
const loading = ref(false)
const currentKey = ref<string>('')
const currentName = ref<string>('')
const versions = ref<FlowDefinition[]>([])
const auditLogs = ref<FlowDefinitionAuditLog[]>([])

const title = computed(() => `${currentName.value} · 版本历史`)

function statusTagType(status?: DefinitionStatus): 'default' | 'success' | 'error' | 'warning' {
  if (status === 'ACTIVE') return 'success'
  if (status === 'DISABLED') return 'error'
  if (status === 'DRAFT') return 'warning'
  return 'default'
}

function statusLabel(status?: DefinitionStatus): string {
  if (status === 'ACTIVE') return '已发布'
  if (status === 'DISABLED') return '已禁用'
  if (status === 'DRAFT') return '草稿'
  return '-'
}

function actionTimelineType(action: DefinitionAuditAction): 'default' | 'success' | 'info' | 'warning' | 'error' {
  switch (action) {
    case 'PUBLISH':
      return 'success'
    case 'DISABLE':
      return 'error'
    case 'CREATE':
    case 'COPY':
      return 'info'
    case 'DELETE':
      return 'error'
    case 'SAVE_DRAFT':
    case 'UPDATE_META':
      return 'default'
    default:
      return 'default'
  }
}

function actionIcon(action: DefinitionAuditAction): string {
  switch (action) {
    case 'CREATE':
      return 'mdi:plus-circle-outline'
    case 'UPDATE_META':
      return 'mdi:pencil-outline'
    case 'SAVE_DRAFT':
      return 'mdi:content-save-outline'
    case 'PUBLISH':
      return 'mdi:rocket-launch-outline'
    case 'DISABLE':
      return 'mdi:cancel'
    case 'COPY':
      return 'mdi:content-copy'
    case 'DELETE':
      return 'mdi:trash-can-outline'
    default:
      return 'mdi:circle-outline'
  }
}

function formatTime(t?: string): string {
  return t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-'
}

/**
 * 从分页响应里抽取列表数据。
 *
 * 关键防御点：当列表为空时，后端 Jackson 配置 `JsonInclude.Include.NON_EMPTY`
 * 会把空数组从 JSON 中剔除（[JsonUtils.kt]），所以 `page.list` 可能直接 undefined。
 * 此时必须返回 `[]`，否则 fallback 到整个 page 对象会让 v-for 错误地遍历对象键。
 */
function extractList<T>(res: any): T[] {
  const page = res?.data?.data ?? res?.data ?? res
  const raw = page?.list ?? page?.data
  return Array.isArray(raw) ? (raw as T[]) : Array.isArray(page) ? (page as T[]) : []
}

async function loadVersions() {
  const res = await listDefinitions({ key: currentKey.value, pageSize: 0 })
  versions.value = extractList<FlowDefinition>(res)
}

async function loadAuditLogs() {
  const res = await getDefinitionAuditLog(currentKey.value, { pageSize: 0 })
  auditLogs.value = extractList<FlowDefinitionAuditLog>(res)
}

async function loadAll() {
  loading.value = true
  try {
    await Promise.all([loadVersions(), loadAuditLogs()])
  } finally {
    loading.value = false
  }
}

function onDesign(v: FlowDefinition) {
  show.value = false
  router.push({ path: '/flow/definition/design', query: { id: v.id, title: v.name } })
}

function onClose() {
  // 清理状态，避免下次打开时闪现旧数据
  versions.value = []
  auditLogs.value = []
}

/** 外部调用：打开抽屉并加载指定 key 的版本与审计数据 */
function open(payload: { key: string; name: string }) {
  currentKey.value = payload.key
  currentName.value = payload.name
  show.value = true
  loadAll()
}

defineExpose({ open })
</script>

<style scoped lang="scss">
.vh-section {
  & + & {
    margin-top: 20px;
  }
}

.vh-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.vh-version-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 12px;
  flex-wrap: wrap;
}

.vh-version-main {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.vh-version-number {
  font-size: 14px;
  font-weight: 600;
  min-width: 32px;
}

.vh-version-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: #555;
}

.vh-version-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.vh-version-time {
  font-size: 12px;
  color: #999;
}

.vh-operator {
  font-size: 12px;
  color: #999;
}
</style>
