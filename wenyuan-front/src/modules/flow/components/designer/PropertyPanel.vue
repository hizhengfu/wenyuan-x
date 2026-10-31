<template>
  <div class="property-panel">
    <n-tabs v-model:value="activeTab" type="line" size="small" :tab-style="{ padding: '8px 14px', fontSize: '13px' }">

      <!-- ── 节点属性 ── -->
      <n-tab-pane name="node" tab="节点属性" display-directive="show">
        <div class="tab-content">
          <template v-if="!selected">
            <div class="empty-tip">
              <n-empty description="选择节点或连线" size="small" />
            </div>
          </template>

          <template v-else-if="selected.type === 'edge'">
            <div class="panel-title">
              <span>连线</span>
              <n-button v-if="!readonly" size="tiny" type="error" quaternary style="margin-left:auto" @click="emit('delete')">删除</n-button>
            </div>
            <n-form :disabled="readonly" :model="edgeForm" label-placement="top" size="small">
              <n-form-item label="连线名称">
                <n-input v-model:value="edgeForm.name" placeholder="如：同意、条件1" @change="onEdgeChange" />
              </n-form-item>
              <n-form-item label="条件类型">
                <HDictSelect v-model:value="edgeForm.conditionType" type="flow_condition_type" @update:value="onEdgeChange" />
              </n-form-item>
              <n-form-item v-if="edgeForm.conditionType === 'EXPRESSION'" label="条件表达式 (SpEL)">
                <n-input
                  v-model:value="edgeForm.condition"
                  type="textarea"
                  placeholder="#amount > 10000"
                  :rows="3"
                  @change="onEdgeChange"
                />
              </n-form-item>
              <n-form-item v-if="edgeForm.conditionType === 'EXPRESSION'" label="优先级">
                <n-input-number v-model:value="edgeForm.priority" :min="0" @update:value="onEdgeChange" />
              </n-form-item>
            </n-form>
          </template>

          <template v-else>
            <div class="panel-title">
              <span>{{ nodeTypeLabel }}</span>
              <!-- START/END 不允许删除 -->
              <n-button
                v-if="!readonly && nodeType !== 'START' && nodeType !== 'END'"
                size="tiny"
                type="error"
                quaternary
                style="margin-left:auto"
                @click="emit('delete')"
              >
                删除
              </n-button>
            </div>

            <n-form :disabled="readonly" :model="nodeForm" label-placement="top" size="small" class="node-form">

              <n-form-item label="节点名称" required>
                <n-input v-model:value="nodeForm.name" placeholder="节点名称" @change="onNodeChange" />
              </n-form-item>

              <n-form-item label="节点标识">
                <n-input
                  v-model:value="nodeForm.nodeKey"
                  placeholder="如: dept_approve"
                  :disabled="nodeType === 'START' || nodeType === 'END'"
                  @change="onNodeChange"
                />
              </n-form-item>

              <!-- 审批/经办/抄送节点 -->
              <template v-if="isUserTask">
                <n-divider />

                <n-form-item label="处理人类型">
                  <HDictSelect
                    v-model:value="nodeForm.assigneeType"
                    type="flow_assignee_type"
                    @update:value="onNodeChange"
                  />
                </n-form-item>
                <n-form-item v-if="showAssigneeConfig" :label="assigneeLabel">
                  <!-- 固定用户：远程搜索 -->
                  <n-select
                    v-if="nodeForm.assigneeType === 'FIXED_USER'"
                    v-model:value="nodeForm.assignees"
                    multiple
                    filterable
                    remote
                    :loading="assigneeLoading"
                    :options="assigneeOptions"
                    placeholder="搜索用户姓名 / 账号"
                    @search="onSearchUsers"
                    @update:value="onAssigneesChange"
                  />
                  <!-- 角色：预加载平铺列表 -->
                  <n-select
                    v-else-if="nodeForm.assigneeType === 'ROLE'"
                    v-model:value="nodeForm.assignees"
                    multiple
                    filterable
                    :loading="assigneeLoading"
                    :options="assigneeOptions"
                    placeholder="选择角色"
                    @update:value="onAssigneesChange"
                  />
                  <!-- 部门：树形多选 -->
                  <n-tree-select
                    v-else-if="nodeForm.assigneeType === 'DEPT'"
                    v-model:value="nodeForm.assignees"
                    multiple
                    checkable
                    :cascade="false"
                    :loading="assigneeLoading"
                    :options="assigneeTreeOptions"
                    label-field="label"
                    key-field="value"
                    children-field="children"
                    placeholder="选择部门"
                    filterable
                    @update:value="onAssigneesChange"
                  />
                </n-form-item>

                <n-form-item
                  label="多人审批"
                  v-if="nodeForm.nodeType !== 'CC' && nodeForm.assigneeType !== 'INITIATOR' && nodeForm.assigneeType !== 'LEADER'"
                >
                  <n-switch v-model:value="nodeForm.multiInstance" @update:value="onNodeChange" />
                </n-form-item>

                <n-form-item v-if="nodeForm.multiInstance" label="审批方式">
                  <n-radio-group v-model:value="nodeForm.multiInstanceType" @update:value="onNodeChange">
                    <n-radio-button value="ALL">会签</n-radio-button>
                    <n-radio-button value="ANY">或签</n-radio-button>
                    <n-radio-button value="SEQUENTIAL">顺签</n-radio-button>
                  </n-radio-group>
                </n-form-item>

                <n-form-item label="驳回方式" v-if="nodeForm.nodeType === 'APPROVE'">
                  <HDictSelect
                    v-model:value="nodeForm.rejectType"
                    type="flow_reject_type"
                    @update:value="onNodeChange"
                  />
                </n-form-item>

                <n-form-item v-if="nodeForm.rejectType === 'BACK_TO_NODE'" label="驳回目标节点">
                  <n-select
                    v-model:value="nodeForm.rejectNodeKey"
                    :options="allNodeOptions"
                    @update:value="onNodeChange"
                  />
                </n-form-item>

                <n-form-item label="超时（小时）">
                  <n-input-number
                    v-model:value="nodeForm.timeoutHours"
                    :min="0"
                    placeholder="不填表示不限制"
                    style="width: 100%"
                    @update:value="onNodeChange"
                  />
                </n-form-item>

                <n-form-item label="表单标识">
                  <n-input
                    v-model:value="nodeForm.formKey"
                    placeholder="使用流程表单"
                    @change="onNodeChange"
                  />
                </n-form-item>

                <n-divider>操作按钮</n-divider>
                <n-form-item>
                  <n-checkbox-group v-model:value="nodeForm.actions" @update:value="onNodeChange">
                    <n-space>
                      <n-checkbox
                        v-for="option in actionOptions"
                        :key="option.value"
                        :value="option.value"
                      >
                        {{ option.label }}
                      </n-checkbox>
                    </n-space>
                  </n-checkbox-group>
                </n-form-item>

                <n-divider>字段权限</n-divider>
                <div class="field-perm-list">
                  <div
                    v-for="(item, index) in nodeForm.fieldPerms"
                    :key="index"
                    class="field-perm-item"
                  >
                    <n-input
                      v-model:value="item.field"
                      size="small"
                      placeholder="字段名"
                      style="width: 110px"
                      @change="onNodeChange"
                    />
                    <n-input
                      v-model:value="item.label"
                      size="small"
                      placeholder="标签"
                      style="width: 80px; margin: 0 4px"
                      @change="onNodeChange"
                    />
                    <HDictSelect
                      v-model:value="item.perm"
                      size="small"
                      type="flow_field_perm"
                      style="width: 80px"
                      @update:value="onNodeChange"
                    />
                    <n-button v-if="!readonly" size="tiny" quaternary type="error" @click="removeFieldPerm(index)" style="margin-left: 4px">
                      删
                    </n-button>
                  </div>
                  <n-button v-if="!readonly" size="small" dashed @click="addFieldPerm" style="width: 100%; margin-top: 8px">
                    + 添加字段权限
                  </n-button>
                </div>
              </template>

              <!-- 子流程节点 -->
              <template v-if="nodeForm.nodeType === 'SUBPROCESS'">
                <n-divider />
                <n-form-item label="子流程" required>
                  <FlowDefinitionSelect
                    v-model:value="nodeForm.subDefinitionKey"
                    :exclude-key="currentDefKey"
                    group-by-category
                    placeholder="选择已发布的子流程"
                    @update:value="onNodeChange"
                  />
                </n-form-item>
              </template>

              <n-form-item label="节点描述">
                <n-input
                  v-model:value="nodeForm.description"
                  type="textarea"
                  :rows="2"
                  placeholder="备注说明"
                  @change="onNodeChange"
                />
              </n-form-item>
            </n-form>
          </template>
        </div>
      </n-tab-pane>

      <!-- ── 流程属性 ── -->
      <n-tab-pane name="flow" tab="流程属性" display-directive="show">
        <div class="tab-content">
          <div class="flow-switches">
            <div class="setting-row">
              <span>允许发起人撤回</span>
              <n-switch v-model:value="localFlowProps.allowInitiatorCancel" :disabled="readonly" aria-label="允许发起人撤回" @update:value="onFlowPropsChange" />
            </div>
            <div class="setting-row">
              <span>重复审批人自动通过</span>
              <n-switch v-model:value="localFlowProps.autoPassRepeat" :disabled="readonly" aria-label="重复审批人自动通过" @update:value="onFlowPropsChange" />
            </div>
          </div>
          <n-form :model="localFlowProps" :disabled="readonly" label-placement="top" size="small" :show-feedback="false" class="flow-settings">
            <n-form-item label="无人处理时">
              <n-radio-group v-model:value="localFlowProps.autoPassEmptyAssignee" aria-label="无人处理时" @update:value="onFlowPropsChange">
                <n-radio-button :value="false" title="优先分配给 admin，否则选择首位可用管理员">交给管理员</n-radio-button>
                <n-radio-button :value="true">自动跳过</n-radio-button>
              </n-radio-group>
            </n-form-item>
            <n-form-item label="流程说明">
              <n-input v-model:value="localFlowProps.description" type="textarea" :rows="3" placeholder="选填" @change="onFlowPropsChange" />
            </n-form-item>
          </n-form>
        </div>
      </n-tab-pane>

    </n-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import type { FlowNodeProperties, FlowEdgeProperties, FlowProperties, FieldPermItem, NodeType } from '../../typings/flow'
import { NODE_TYPE_LABELS } from './nodes'
import { useAssigneeProvider, type AssigneeOption, type AssigneeTreeOption } from '../../composables/useAssigneeProvider'
import FlowDefinitionSelect from '../FlowDefinitionSelect.vue'

interface Selected {
  type: 'node' | 'edge'
  id: string
  properties: FlowNodeProperties | FlowEdgeProperties
  nodeType?: string
}

const props = defineProps<{
  selected: Selected | null
  allNodes?: Array<{ nodeKey: string; name: string; nodeType: string }>
  flowProps?: FlowProperties
  readonly?: boolean
  /** 当前正在编辑的流程定义 key，用于子流程下拉过滤掉自身，避免循环引用 */
  currentDefKey?: string
}>()

const emit = defineEmits<{
  (e: 'update:node', id: string, properties: FlowNodeProperties): void
  (e: 'update:edge', id: string, properties: FlowEdgeProperties): void
  (e: 'update:flow-props', fp: FlowProperties): void
  (e: 'delete'): void
}>()

// ─── Tab 状态 ─────────────────────────────────────────────────

const activeTab = ref<'node' | 'flow'>('node')

// 选中节点/边时自动切到节点属性 Tab
watch(() => props.selected, (sel) => {
  if (sel) activeTab.value = 'node'
})

// ─── 节点表单 ─────────────────────────────────────────────────

const nodeForm = ref<FlowNodeProperties & { nodeType?: NodeType; name?: string }>({})
const edgeForm = ref<FlowEdgeProperties>({})

watch(() => props.selected, (sel) => {
  if (!sel) return
  if (sel.type === 'node') {
    nodeForm.value = { ...sel.properties as FlowNodeProperties }
    sanitizeActionsForNode()
  } else {
    edgeForm.value = { ...sel.properties as FlowEdgeProperties }
  }
}, { immediate: true, deep: true })

// ─── 流程属性表单 ─────────────────────────────────────────────

const defaultFlowProps: Required<Pick<FlowProperties, 'allowInitiatorCancel' | 'autoPassRepeat' | 'autoPassEmptyAssignee'>> = {
  allowInitiatorCancel: true,
  autoPassRepeat: false,
  autoPassEmptyAssignee: false,
}
const normalizeFlowProps = (fp?: FlowProperties | null): FlowProperties => ({
  ...defaultFlowProps,
  ...(fp || {}),
  autoPassEmptyAssignee: fp?.autoPassEmptyAssignee === true,
})
const localFlowProps = ref<FlowProperties>(normalizeFlowProps())

watch(() => props.flowProps, (fp) => {
  localFlowProps.value = normalizeFlowProps(fp)
}, { immediate: true, deep: true })

// ─── 节点计算属性 ─────────────────────────────────────────────

const nodeType = computed(() => props.selected?.nodeType || (props.selected?.properties as FlowNodeProperties)?.nodeType || '')
const nodeTypeLabel = computed(() => NODE_TYPE_LABELS[nodeType.value] || nodeType.value)
const isUserTask = computed(() => ['APPROVE', 'HANDLE', 'CC'].includes(nodeType.value))
const showAssigneeConfig = computed(() => !['INITIATOR', 'LEADER', 'SELF_SELECT'].includes(nodeForm.value.assigneeType || ''))
const actionOptions = computed(() => {
  const options = [
    { label: '同意', value: 'APPROVE' },
    { label: '驳回', value: 'REJECT' },
    { label: '转交', value: 'TRANSFER' },
    { label: '委托', value: 'DELEGATE' },
    { label: '暂存', value: 'SAVE' },
  ]
  return nodeType.value === 'HANDLE'
    ? options.filter((option) => option.value !== 'REJECT')
    : options
})

const assigneeLabel = computed(() => {
  const map: Record<string, string> = { FIXED_USER: '固定用户', ROLE: '角色', DEPT: '部门' }
  return map[nodeForm.value.assigneeType || ''] || '处理人'
})

const assigneePlaceholder = computed(() => {
  const map: Record<string, string> = { FIXED_USER: '选择用户', ROLE: '选择角色', DEPT: '选择部门' }
  return map[nodeForm.value.assigneeType || ''] || '请选择'
})

// ─── 处理人提供者 ───────────────────────────────────────────────
const assigneeProvider = useAssigneeProvider()

const assigneeLoading = ref(false)
const assigneeOptions = ref<AssigneeOption[]>([])
const assigneeTreeOptions = ref<AssigneeTreeOption[]>([])

/** 按类型加载处理人候选项 */
async function loadAssigneeOptions(type: string, existingIds: string[]) {
  assigneeLoading.value = true
  try {
    if (type === 'FIXED_USER') {
      // 同时获取初始列表 + 已选用户信息（确保回显正确）
      const [searchResults, existingUsers] = await Promise.all([
        assigneeProvider.searchUsers(''),
        existingIds.length ? assigneeProvider.loadUsersByIds(existingIds) : Promise.resolve([]),
      ])
      // 合并，已选用户优先，去重
      const map = new Map<string, AssigneeOption>()
      for (const u of [...existingUsers, ...searchResults]) map.set(u.value, u)
      assigneeOptions.value = [...map.values()]
    } else if (type === 'ROLE') {
      assigneeOptions.value = await assigneeProvider.loadRoles()
    } else if (type === 'DEPT') {
      assigneeTreeOptions.value = await assigneeProvider.loadDepts()
    }
  } finally {
    assigneeLoading.value = false
  }
}

// 用户切换 assigneeType（用户手动操作）→ 重新加载候选项
watch(
  () => nodeForm.value.assigneeType,
  async (type) => {
    assigneeOptions.value = []
    assigneeTreeOptions.value = []
    if (!type || ['INITIATOR', 'LEADER', 'SELF_SELECT'].includes(type)) return
    await loadAssigneeOptions(type, nodeForm.value.assignees || [])
  },
)

// 切换到不同节点（即使 assigneeType 不变，已选列表可能不同）
watch(
  () => props.selected?.id,
  async () => {
    const type = nodeForm.value.assigneeType
    if (!type || ['INITIATOR', 'LEADER', 'SELF_SELECT'].includes(type)) return
    await nextTick() // 等待 nodeForm 由上面的 watch 更新完成
    assigneeOptions.value = []
    assigneeTreeOptions.value = []
    await loadAssigneeOptions(type, nodeForm.value.assignees || [])
  },
)

/** FIXED_USER 远程搜索回调 */
async function onSearchUsers(keyword: string) {
  assigneeLoading.value = true
  try {
    const results = await assigneeProvider.searchUsers(keyword)
    // 保留已选项，避免合并后丢失选中状态
    const selectedIds = new Set(nodeForm.value.assignees || [])
    const selectedOpts = assigneeOptions.value.filter((o) => selectedIds.has(o.value))
    const map = new Map<string, AssigneeOption>()
    for (const u of [...selectedOpts, ...results]) map.set(u.value, u)
    assigneeOptions.value = [...map.values()]
  } finally {
    assigneeLoading.value = false
  }
}

const allNodeOptions = computed(() =>
  (props.allNodes || [])
    .filter(n => n.nodeKey && n.nodeKey !== nodeForm.value.nodeKey)
    .map(n => ({ label: `${n.name}（${n.nodeKey}）`, value: n.nodeKey }))
)

// ─── 事件处理 ─────────────────────────────────────────────────

function onNodeChange() {
  if (props.readonly) return
  if (props.selected?.type === 'node') {
    sanitizeActionsForNode()
    emit('update:node', props.selected.id, { ...nodeForm.value })
  }
}

function sanitizeActionsForNode() {
  if (nodeForm.value.nodeType === 'HANDLE' && nodeForm.value.actions?.includes('REJECT' as any)) {
    nodeForm.value.actions = nodeForm.value.actions.filter((action) => action !== 'REJECT')
  }
}

function onAssigneesChange(_values: string[]) {
  onNodeChange()
}

function onEdgeChange() {
  if (props.readonly) return
  if (props.selected?.type === 'edge') {
    emit('update:edge', props.selected.id, { ...edgeForm.value })
  }
}

function onFlowPropsChange() {
  if (props.readonly) return
  emit('update:flow-props', { ...localFlowProps.value })
}

function addFieldPerm() {
  if (props.readonly) return
  if (!nodeForm.value.fieldPerms) nodeForm.value.fieldPerms = []
  nodeForm.value.fieldPerms.push({ field: '', label: '', perm: 'EDITABLE' })
}

function removeFieldPerm(index: number) {
  if (props.readonly) return
  nodeForm.value.fieldPerms?.splice(index, 1)
  onNodeChange()
}
</script>

<style scoped>
.property-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
}

/* 让 n-tabs 撑满高度 */
:deep(.n-tabs) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

:deep(.n-tabs-nav) {
  flex-shrink: 0;
  border-bottom: 1px solid #e4e7ed;
  padding: 0 4px;
}

:deep(.n-tab-pane) {
  flex: 1;
  overflow: hidden;
  padding: 0 !important;
}

.tab-content {
  height: 100%;
  overflow-y: auto;
  padding: 12px;
}

.panel-title {
  font-size: 14px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
}

.empty-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
}

.node-form {
  font-size: 13px;
}

.field-perm-list {
  margin-top: 4px;
}

.field-perm-item {
  display: flex;
  align-items: center;
  margin-bottom: 6px;
}

.flow-switches {
  display: grid;
  gap: 18px;
  padding: 4px 0 20px;
  margin-bottom: 20px;
  border-bottom: 1px solid #f0f0f0;
}
.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
}
.flow-settings {
  display: grid;
  gap: 20px;
}
</style>
