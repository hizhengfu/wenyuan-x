export const safetyDomains: Record<string, string> = {
  CARE: '检查养护与使用监测',
  JOINT: '在用共同检查',
  EMERGENCY: '预案、演练与整改',
  LOAN: '借用催还、检查与备案',
};
export const safetyStatuses: Record<string, string> = {
  ORIGINAL_ERROR: '原件需核查',
  OVERDUE: '已到期',
  PENDING: '待办理',
  SCHEDULED: '尚未到期',
  IN_PROGRESS: '实际办理中',
};
export const deliveryStatuses: Record<string, string> = {
  PENDING: '待入队',
  RETRY: '入队失败，等待重试',
  QUEUED: '站内信已入队',
  CANCELLED: '已取消旧提醒',
};
export function coordinationText(row: Record<string, any>) {
  if (row.coordinationMissing) return '缺少有效协调人员，请核对责任安排';
  if (row.coordinatorUserIds?.length) return '部分职责待核实，已转协调人员提醒';
  return '按当前责任人员提醒';
}
