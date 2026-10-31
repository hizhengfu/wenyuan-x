import type { RouteLocationRaw } from 'vue-router';

export function buildTaskBusinessRoute(task: Record<string, unknown> | null | undefined): RouteLocationRaw | null {
  if (
    task?.businessType === 'PROJECT_IMMOVABLE_PLAN'
    && task.businessKey
    && task.nodeKey === 'city_review'
  ) {
    return {
      path: '/project/immovable-plan',
      query: {
        businessKey: String(task.businessKey),
        taskId: task.id ? String(task.id) : undefined,
        nodeKey: task.nodeKey ? String(task.nodeKey) : undefined,
        mode: 'approve',
      },
    };
  }
  return null;
}
