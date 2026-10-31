import type { TaskAction } from '../typings/flow';

export function resolveTaskComment(action: TaskAction, comment?: string): string | undefined {
  if (comment?.trim()) return comment;
  if (action === 'APPROVE') return '同意';
  if (action === 'REJECT') return '不同意';
  return undefined;
}
