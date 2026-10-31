import type { FlowTask, TaskAction, FlowFormTaskActionConfig } from '../typings/flow';

/** 明确的空列表就是无可用动作；业务表单只能缩小服务端允许范围。 */
export function resolveTaskActions(task?: FlowTask | null, custom?: FlowFormTaskActionConfig | null): TaskAction[] {
  if (Array.isArray(task?.availableActions)) {
    const server = [...new Set(task.availableActions)];
    return Array.isArray(custom?.actions) ? server.filter(action => custom.actions!.includes(action)) : server;
  }
  const defaults: TaskAction[] = task?.type === 'HANDLE' ? ['APPROVE'] : ['APPROVE', 'REJECT'];
  const source = custom?.actions?.length ? custom.actions : defaults;
  return task?.type === 'HANDLE' ? source.filter(action => action !== 'REJECT') : source;
}
