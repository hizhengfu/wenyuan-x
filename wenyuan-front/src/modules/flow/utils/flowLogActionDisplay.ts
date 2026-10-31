import type { FlowInstanceLog } from '../typings/flow';

export type FlowLogActionDictItem = {
  value?: string | number | boolean | null;
  label?: string | null;
};

export function flowLogActionLabel(log: Pick<FlowInstanceLog, 'action' | 'actionLabel'>, dict: FlowLogActionDictItem[] = []) {
  if (log.actionLabel && log.actionLabel !== log.action) return log.actionLabel;
  if (!log.action) return '-';
  return dict.find((item) => item.value == log.action)?.label || log.action;
}

export function flowLogTimelineType(action?: string): 'default' | 'success' | 'info' | 'warning' | 'error' {
  if (!action) return 'default';
  if (['APPROVE', 'COMPLETE', 'CC'].includes(action)) return 'success';
  if (['REJECT', 'CANCEL', 'TERMINATE'].includes(action)) return 'error';
  if (['TRANSFER', 'DELEGATE'].includes(action)) return 'warning';
  if (['START', 'SUBPROCESS_START', 'SKIP'].includes(action)) return 'info';
  return 'default';
}

export function flowLogActionTagType(action?: string): 'default' | 'primary' | 'info' | 'success' | 'warning' | 'error' {
  if (!action) return 'default';
  if (['APPROVE', 'COMPLETE'].includes(action)) return 'success';
  if (['REJECT', 'CANCEL', 'TERMINATE'].includes(action)) return 'error';
  if (['TRANSFER', 'DELEGATE'].includes(action)) return 'warning';
  if (['START', 'SUBPROCESS_START', 'SKIP', 'CC'].includes(action)) return 'info';
  return 'default';
}
