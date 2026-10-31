export interface AlarmHandlingState {
  status: 'PENDING' | 'CONFIRMED' | 'PROCESSING' | 'PROCESSED';
  sourceCode?: string;
  reason?: string;
  externalId?: string;
  flowInstanceId?: string;
  flowStatus?: 'RUNNING' | 'COMPLETED' | 'REJECTED' | 'CANCELLED';
  operatorId?: string;
  operatorName?: string;
  occurredAt?: number;
  details?: Record<string, unknown>;
}

// Both local and external results use the original handling records and PROCESSED stage.
export function alarmHandlingProgress(
  records: Array<{ action: string; occurredAt: number; id: string; sourceCode?: string; applied?: boolean }>,
  state?: AlarmHandlingState | null,
) {
  const latestRecord = [...records]
    .filter((record) => record.applied !== false && ['CONFIRMED', 'PROCESSING', 'PROCESSED'].includes(record.action))
    .sort((a, b) => a.occurredAt - b.occurredAt || a.id.localeCompare(b.id))
    .at(-1);
  const latest = state?.status ?? latestRecord?.action;
  const source = state ? state.sourceCode : latestRecord?.sourceCode;
  const externallyCompleted = latest === 'PROCESSED' && !!source && source !== 'LOCAL';
  if (externallyCompleted) {
    return { step: 4, action: 'COMMENTED', label: '补充说明', externallyCompleted };
  }
  if (state?.flowStatus === 'RUNNING') {
    return { step: 3, action: 'COMMENTED', label: '等待外部流程完成', externallyCompleted };
  }
  switch (latest) {
    case 'CONFIRMED':
      return { step: 2, action: 'PROCESSING', label: '开始处理', externallyCompleted };
    case 'PROCESSING':
      return { step: 3, action: 'PROCESSED', label: '登记处理结果', externallyCompleted };
    case 'PROCESSED':
      return { step: 4, action: 'PROCESSED', label: '补充处理结果', externallyCompleted };
    default:
      return { step: 1, action: 'CONFIRMED', label: '确认预警', externallyCompleted };
  }
}
