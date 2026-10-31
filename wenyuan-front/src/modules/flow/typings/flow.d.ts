// ─── 枚举 ──────────────────────────────────────────────────────

export type NodeType = 'START' | 'END' | 'APPROVE' | 'HANDLE' | 'CC' | 'EXCLUSIVE' | 'SUBPROCESS'
export type AssigneeType = 'FIXED_USER' | 'ROLE' | 'DEPT' | 'INITIATOR' | 'LEADER' | 'SELF_SELECT'
export type MultiInstanceType = 'ALL' | 'ANY' | 'SEQUENTIAL'
export type DefinitionStatus = 'DRAFT' | 'ACTIVE' | 'DISABLED'
export type InstanceStatus = 'RUNNING' | 'COMPLETED' | 'CANCELED' | 'REJECTED'
export type TaskStatus = 'PENDING' | 'COMPLETED' | 'REJECTED' | 'CANCELED' | 'TRANSFERRED' | 'SKIPPED'
export type TaskType = 'APPROVE' | 'HANDLE' | 'CC'
export type TaskAction = 'APPROVE' | 'REJECT' | 'TRANSFER' | 'DELEGATE' | 'SAVE'
export type FieldPerm = 'HIDDEN' | 'READONLY' | 'EDITABLE' | 'REQUIRED'
export type RejectType = 'BACK_TO_START' | 'BACK_TO_PREV' | 'BACK_TO_NODE' | 'TERMINATE'
export type ConditionType = 'NONE' | 'EXPRESSION' | 'DEFAULT'

// ─── 设计时模型 ────────────────────────────────────────────────

export interface FieldPermItem {
  field: string
  label?: string
  perm: FieldPerm
}

/** 节点属性（设计时） */
export interface FlowNodeProperties {
  nodeKey?: string
  name?: string
  nodeType?: NodeType
  assigneeType?: AssigneeType
  assignees?: string[]
  assigneeNames?: string[]
  multiInstance?: boolean
  multiInstanceType?: MultiInstanceType
  rejectType?: RejectType
  rejectNodeKey?: string
  formKey?: string
  timeoutHours?: number
  subDefinitionKey?: string
  fieldPerms?: FieldPermItem[]
  actions?: TaskAction[]
  description?: string
}

/** 边属性（设计时） */
export interface FlowEdgeProperties {
  name?: string
  conditionType?: ConditionType
  condition?: string
  priority?: number
}

/** 流程图节点（LogicFlow格式） */
export interface FlowModelNode {
  id: string
  type: string
  x: number
  y: number
  properties: FlowNodeProperties
}

/** 流程图边（LogicFlow格式） */
export interface FlowModelEdge {
  id: string
  sourceNodeId: string
  targetNodeId: string
  properties: FlowEdgeProperties
}

/** 流程级别配置（保存在模型中） */
export interface FlowProperties {
  allowInitiatorCancel?: boolean  // 允许发起人撤回
  autoPassRepeat?: boolean        // 重复审批人自动通过
  autoPassEmptyAssignee?: boolean // 无处理人自动跳过，默认关闭并交由管理员办理
  description?: string            // 流程说明
}

/** 完整流程图模型 */
export interface FlowModel {
  nodes: FlowModelNode[]
  edges: FlowModelEdge[]
  properties?: FlowProperties
}

// ─── 定义 VO ──────────────────────────────────────────────────

export interface FlowDefinition {
  id?: string
  name?: string
  key?: string
  version?: number
  description?: string
  category?: string
  status?: DefinitionStatus
  formKey?: string
  modelData?: string
  createBy?: string
  createTime?: string
  updateTime?: string
}

// ─── 运行时 VO ─────────────────────────────────────────────────

export interface FlowInstance {
  id?: string
  definitionId?: string
  definitionKey?: string
  definitionName?: string
  definitionVersion?: number
  title?: string
  businessKey?: string
  businessType?: string
  formData?: Record<string, any>
  status?: InstanceStatus
  /** 当前活动节点 Key 列表（始终填充） */
  currentNodeKeys?: string[]
  /** 当前活动节点名称列表（仅分页查询时填充） */
  currentNodeNames?: string[]
  initiator?: string
  initiatorName?: string
  startTime?: string
  endTime?: string
  duration?: number
  /** 父流程实例 ID（仅子流程时存在） */
  parentInstanceId?: string
  /** 父流程占位任务 ID（仅子流程时存在） */
  parentTaskId?: string
  /** 当前用户是否具备流程运维权限 */
  canManage?: boolean
  /** 当前用户是否可将运行中流程跳转到指定节点 */
  canJump?: boolean
  /** 当前用户是否可将已结束流程重新激活到指定节点 */
  canReactivate?: boolean
  /** 可作为跳转/重新激活目标的节点列表 */
  operationNodes?: FlowOperationNode[]
}

export interface FlowOperationNode {
  nodeKey: string
  name: string
  type: string
}

export interface FlowTask {
  /** 原申请材料的订正入口说明；存在时按服务端字段保护办理。 */
  businessFormNotice?: string
  sequentialReview?: {
    position?: number
    total?: number
    originalAssigneeId?: string
    originalAssigneeName?: string
    needsReview: boolean
  }
  id?: string
  instanceId?: string
  flowInstanceId?: string
  definitionId?: string
  definitionKey?: string
  definitionName?: string
  instanceTitle?: string
  nodeKey?: string
  nodeName?: string
  type?: TaskType
  assignee?: string
  assigneeName?: string
  status?: TaskStatus
  action?: TaskAction
  comment?: string
  formData?: Record<string, any>
  createTime?: string
  claimTime?: string
  completeTime?: string
  dueTime?: string
  availableActions?: TaskAction[]
  actionRestrictions?: Partial<Record<TaskAction, string>>
  fieldPerms?: FieldPermItem[]
  /**
   * 表单 Key（按优先级解析后的最终值）：节点级 formKey > 流程级 formKey > null。
   * 前端按此 Key 优先加载自定义表单组件；为空时回退到 businessType 自动发现。
   */
  formKey?: string
  /** 业务主键，用于表单组件加载完整数据 */
  businessKey?: string
  /** 业务类型，用于 glob 自动发现对应表单组件 */
  businessType?: string
}

// ─── 表单组件协议 ─────────────────────────────────────────────

/**
 * 所有业务 FlowForm.vue 必须遵守的 props 协议。
 * - view    : 审批进度查看，全部只读
 * - approve : 审批节点，按 fieldPerms 控制读写
 * - handle  : 经办节点，按 fieldPerms 控制读写
 */
export interface FlowFormProps {
  formData: Record<string, any>
  fieldPerms?: FieldPermItem[]
  mode: 'view' | 'approve' | 'handle'
  task?: FlowTask | null
  businessType?: string | null
  businessKey?: string | null
}

export interface FlowFormTaskActionConfig {
  /** 可选：启用仅保存业务数据的暂存入口，不代表流程动作授权。 */
  allowSaveDraft?: boolean
  actions?: TaskAction[]
  labels?: Partial<Record<TaskAction, string>>
  showComment?: boolean
  commentPlaceholder?: string
  closeLabel?: string
  requiredCommentActions?: TaskAction[]
}

export interface FlowFormTaskActionInput {
  action: TaskAction
  task?: FlowTask | null
  formData: Record<string, any>
  comment?: string
  transferTo?: string
  transferToName?: string
}

export interface FlowFormTaskActionResult {
  close?: boolean
  successMessage?: string
}

/** FlowForm.vue 必须通过 defineExpose 暴露的方法 */
export interface FlowFormExpose {
  /** 校验并返回最新表单数据，仅 approve/handle 模式下调用 */
  validate: (options?: { submit?: boolean }) => Promise<Record<string, any>>
  /** 可选：覆盖任务动作区的按钮、文案与意见区显示 */
  getTaskActionConfig?: () => FlowFormTaskActionConfig | null | undefined
  /** 可选：直接暂存业务数据，由业务接口校验办理权限，不进行提交校验或推进流程。 */
  saveTaskDraft?: (input?: { comment?: string }) => Promise<void | FlowFormTaskActionResult>
  /** 可选：覆盖默认 completeTask 流程，直接走业务自定义接口 */
  runTaskAction?: (input: FlowFormTaskActionInput) => Promise<void | FlowFormTaskActionResult>
}

export interface FlowCC {
  id?: string
  instanceId?: string
  instanceTitle?: string
  definitionName?: string
  taskId?: string
  nodeName?: string
  userId?: string
  isRead?: boolean
  readTime?: string
  createTime?: string
}

export interface FlowInstanceLog {
  id?: string
  instanceId?: string
  taskId?: string
  nodeKey?: string
  nodeName?: string
  action?: string
  actionLabel?: string
  operator?: string
  operatorName?: string
  comment?: string
  createTime?: string
}

/** 流程图高亮状态数据 */
export interface FlowDiagramData {
  model: FlowModel
  currentNodeKeys: string[]
  completedNodeKeys: string[]
  rejectedNodeKeys: string[]
  /** 实际待处理任务，旧服务未返回时不推测处理人。 */
  pendingTasks?: Array<{
    id: string
    nodeKey: string
    nodeName?: string | null
    assignee?: string | null
    assigneeName?: string | null
  }>
}

// ─── 请求参数 ──────────────────────────────────────────────────

export interface StartFlowRequest {
  definitionKey: string
  title: string
  businessKey?: string
  businessType?: string
  formData?: Record<string, any>
  selfSelectAssignees?: Record<string, string[]>
  /** 父流程实例 ID（仅子流程内部使用，业务一般不传） */
  parentInstanceId?: string
  /** 父流程占位任务 ID（仅子流程内部使用，业务一般不传） */
  parentTaskId?: string
}

export interface ReactivateFlowRequest {
  targetNodeKey: string
  comment?: string
}

export interface JumpFlowRequest {
  targetNodeKey: string
  comment?: string
}

export interface CompleteTaskRequest {
  taskId: string
  action: TaskAction
  comment?: string
  formData?: Record<string, any>
  transferTo?: string
  transferToName?: string
}

// ─── 超时 / 催办 ───────────────────────────────────────────────

/** 超时任务（dueTime < now 且 PENDING） */
export interface FlowOverdueTask {
  taskId: string
  instanceId: string
  instanceTitle?: string
  definitionKey: string
  definitionName?: string
  businessKey?: string
  businessType?: string
  nodeKey: string
  nodeName?: string
  type: TaskType
  assignee?: string
  assigneeName?: string
  initiator: string
  initiatorName: string
  dueTime: string
  /** 已逾期分钟数 */
  overdueMinutes: number
  /** 是否已被自动扫描标记过（true 表示自动通知已发送一次） */
  timeoutNotified: boolean
  createTime: string
}

/** 超时列表查询范围 */
export type OverdueScope = 'mine' | 'assigned' | 'all'

/** 人工催办请求 */
export interface UrgeTaskRequest {
  taskId: string
  comment?: string
}

/** 流程定义审计动作 */
export type DefinitionAuditAction =
  | 'CREATE'
  | 'UPDATE_META'
  | 'SAVE_DRAFT'
  | 'PUBLISH'
  | 'DISABLE'
  | 'COPY'
  | 'DELETE'

/** 流程定义审计日志条目（对应后端 FlowDefinitionAuditLogVo） */
export interface FlowDefinitionAuditLog {
  id: string
  definitionId: string
  definitionKey: string
  version: number
  action: DefinitionAuditAction
  actionLabel: string
  changeSummary?: string
  hasSnapshot: boolean
  operatorId: string
  operatorName?: string
  operateTime: string
}

/**
 * 任务中心首页：按业务类型分组的待办聚合卡片。
 *
 * 由后端 `GET /flow/task/todo/group-by-business-type` 返回。
 * 每张卡片对应一个 businessType（如 CASE / PROJECT_PLAN），点击后可跳转到
 * 对应模块的专属待办页（routePath）或按 businessType 筛选的统一待办列表。
 *
 * count = 0 的业务类型不返回；按 latestTaskTime 倒序。
 */
export interface TodoBusinessTypeGroupVo {
  /** 业务类型代号（FlowService.businessType()） */
  businessType: string
  /** 业务类型中文名（FlowService.businessTypeName()） */
  businessTypeName: string
  /** 图标标识（FlowService.businessTypeIcon()），通常是 mdi:xxx */
  icon?: string
  /** 卡片跳转的前端路由（FlowService.todoListRoute()）；为空时回退到 /flow/task?tab=todo&businessType=xxx */
  routePath?: string
  /** 该业务类型下当前用户的 PENDING 待办数 */
  count: number
  /** 最近一条待办的创建时间，ISO 8601 字符串 */
  latestTaskTime?: string
}

// ─── 统计 ──────────────────────────────────────────────────────

/** 流程统计概览 */
export interface FlowStatisticsOverview {
  /** 我的待办数量 */
  todoCount: number
  /** 我的已办数量 */
  doneCount: number
  /** 我发起的-进行中 */
  myRunningCount: number
  /** 我发起的-已完成 */
  myCompletedCount: number
  /** 我发起的-已撤销 */
  myCanceledCount: number
  /** 我发起的-已驳回 */
  myRejectedCount: number
  /** 抄送给我-未读 */
  ccUnreadCount: number
  /** 抄送给我-总数 */
  ccTotalCount: number
  /** 当前已超时的我的处理任务数 */
  myOverdueCount: number

  /** 全局：流程定义总数（仅 ADMIN） */
  totalDefinitions?: number
  activeDefinitions?: number
  totalInstances?: number
  runningInstances?: number
  todayInstances?: number
  pendingTasks?: number
  overdueTasks?: number
}

/** 状态计数项 */
export interface FlowStatusCount {
  status: string
  label: string
  count: number
}

/** 流程定义维度统计 */
export interface FlowDefinitionStat {
  definitionKey: string
  definitionName: string
  total: number
  running: number
  completed: number
  canceled: number
  rejected: number
}

/** 趋势点 */
export interface FlowTrendPoint {
  date: string
  started: number
  finished: number
}
