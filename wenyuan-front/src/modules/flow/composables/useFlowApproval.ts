import { h, type Ref } from 'vue'
import { NTag } from 'naive-ui'

export const FLOW_STATUS_MAP: Record<string, { label: string; type: 'default' | 'info' | 'success' | 'warning' | 'error' }> = {
  DRAFT:  { label: '草稿',   type: 'default' },
  AUDIT:  { label: '审批中', type: 'warning' },
  PASS:   { label: '已通过', type: 'success' },
  REJECT: { label: '已驳回', type: 'error'   },
  CANCEL: { label: '已撤销', type: 'default' },
  VOID:   { label: '已作废', type: 'default' },
}

export const FLOW_STATUS_OPTIONS = Object.entries(FLOW_STATUS_MAP).map(([value, { label }]) => ({ value, label }))

export interface UseFlowApprovalOptions {
  refTable: Ref<any>
  /** FlowFormDrawer 的 ref */
  drawerRef: Ref<any>
  /** 从行数据中取显示名称，默认取 row.name */
  getLabel?: (row: any) => string
}

/**
 * 工作流审批辅助组合式函数。
 *
 * 只负责：状态列渲染 + 打开 FlowFormDrawer。
 * 提交/撤销/作废/编辑/删除等操作均在 FlowFormDrawer 内部处理。
 */
export function useFlowApproval(options: UseFlowApprovalOptions) {
  const { refTable, drawerRef, getLabel } = options

  const label = (row: any) => getLabel?.(row) ?? row.name ?? row.id

  /** 审批状态列 */
  const flowStatusColumn = {
    key: 'flowStatus',
    title: '审批状态',
    width: '100px',
    align: 'center',
    render: (row: any) => {
      const cfg = FLOW_STATUS_MAP[row.flowStatus]
      return cfg
        ? h(NTag, { type: cfg.type, size: 'small' }, () => cfg.label)
        : h(NTag, { size: 'small' }, () => '草稿')
    },
  }

  /** 点击标题/行时打开 FlowFormDrawer */
  function handleTitleClick(row: any) {
    drawerRef.value?.open(row.id, label(row))
  }

  return {
    flowStatusColumn,
    handleTitleClick,
  }
}
