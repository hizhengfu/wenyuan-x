// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { defineComponent, h, markRaw } from 'vue';
import type { FlowTask, FlowFormTaskActionResult, TaskAction } from '../../typings/flow';

const mocks = vi.hoisted(() => ({
  form: null as any,
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn(),
  completeTask: vi.fn(),
  getInstance: vi.fn(),
}));

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock('naive-ui', async (importOriginal) => ({
  ...await importOriginal<typeof import('naive-ui')>(),
  useMessage: () => ({ success: mocks.success, error: mocks.error, warning: mocks.warning }),
  useDialog: () => ({}),
}));
vi.mock('@/store', () => ({
  useAuthStore: () => ({ userInfo: { id: 'user-1' }, permissions: [], hasPermission: () => false }),
}));
vi.mock('../../formRegistry', () => ({ getFlowForm: () => mocks.form }));
vi.mock('../../composables/useAssigneeProvider', () => ({
  useAssigneeProvider: () => ({ searchUsers: vi.fn().mockResolvedValue([]) }),
}));
vi.mock('../../api/instance', () => ({
  getInstance: mocks.getInstance,
  getDiagramData: vi.fn().mockResolvedValue({ data: {} }),
  getHistory: vi.fn().mockResolvedValue({ data: [] }),
  getOperationNodes: vi.fn(), cancelInstance: vi.fn(), jumpInstance: vi.fn(), reactivateInstance: vi.fn(),
}));
vi.mock('../../api/task', () => ({
  completeTask: mocks.completeTask,
  batchForceComplete: vi.fn(), previewBatchForceComplete: vi.fn(),
}));
vi.mock('../viewer/FlowViewer.vue', () => ({ default: { template: '<div />' } }));
vi.mock('../viewer/FlowStatusTag.vue', () => ({ default: { template: '<span />' } }));
vi.mock('../task/TaskHistory.vue', () => ({ default: { template: '<div />' } }));
vi.mock('../task/TaskHistoryTimeline.vue', () => ({ default: { template: '<div />' } }));
vi.mock('../task/SequentialReviewNotice.vue', () => ({ default: { template: '<div />' } }));
vi.mock('../task/BusinessFormNotice.vue', () => ({ default: { template: '<div />' } }));
vi.mock('../task/TaskActionNotice.vue', () => ({ default: { template: '<div />' } }));
vi.mock('@/components/form/form/DrawerPageForm.vue', () => ({ default: { template: '<div />' } }));
vi.mock('@/components/basic/DynamicIcon.vue', () => ({ default: { template: '<span />' } }));

import FlowFormDrawer from '../FlowFormDrawer.vue';

const Content = defineComponent({
  setup(_, { slots }) {
    return () => h('div', [slots.header?.(), slots.default?.(), slots.footer?.()]);
  },
});
const Drawer = defineComponent({
  props: ['show'],
  setup(props, { slots }) {
    return () => props.show ? h('section', { 'data-testid': 'drawer' }, slots.default?.()) : null;
  },
});
const Button = defineComponent({
  props: ['loading', 'disabled'],
  emits: ['click'],
  setup(props, { slots, emit }) {
    return () => h('button', { disabled: props.disabled, onClick: () => emit('click') }, slots.default?.());
  },
});
const Input = defineComponent({
  props: ['value'],
  emits: ['update:value'],
  setup(props, { emit }) {
    return () => h('textarea', {
      value: props.value,
      onInput: (event: Event) => emit('update:value', (event.target as HTMLTextAreaElement).value),
    });
  },
});
const wrappers: VueWrapper[] = [];
const task = (overrides: Partial<FlowTask> = {}): FlowTask => ({
  id: 'task-1', instanceId: 'instance-1', businessKey: 'check-1',
  businessType: 'SATELLITE_REMOTE_CHECK', nodeKey: 'fill', type: 'HANDLE', status: 'PENDING',
  availableActions: ['APPROVE'], ...overrides,
});

function businessForm(options: { allowSaveDraft?: boolean; withDraftSaver?: boolean; withDraftConfig?: boolean; requiredCommentActions?: TaskAction[] } = {}) {
  const validate = vi.fn().mockRejectedValue(new Error('必填项未填写'));
  const saveTaskDraft = vi.fn<(input?: { comment?: string }) => Promise<void | FlowFormTaskActionResult>>()
    .mockResolvedValue({ successMessage: '草稿已保存' });
  const runTaskAction = vi.fn().mockResolvedValue({ close: false });
  mocks.form = markRaw(defineComponent({
    setup(_, { expose }) {
      expose({
        validate,
        getTaskActionConfig: () => ({
          ...(options.withDraftConfig === false ? {} : { allowSaveDraft: options.allowSaveDraft ?? true }),
          actions: ['SAVE', 'APPROVE'], labels: { SAVE: '暂存', APPROVE: '提交' },
          requiredCommentActions: options.requiredCommentActions ?? ['SAVE'],
        }),
        ...(options.withDraftSaver === false ? {} : { saveTaskDraft }),
        runTaskAction,
      });
      return () => h('div', { 'data-testid': 'business-form' });
    },
  }));
  return { validate, saveTaskDraft, runTaskAction };
}

async function openDrawer(currentTask = task(), viewOnly = false) {
  const wrapper = mount(FlowFormDrawer, {
    global: { stubs: {
      NDrawer: Drawer, Drawer, NDrawerContent: Content, DrawerContent: Content, NButton: Button, Button,
      NSpace: Content, Space: Content, NFlex: Content, Flex: Content, NSpin: Content, Spin: Content, NScrollbar: Content, Scrollbar: Content,
      NAlert: Content, Alert: Content, NInput: Input, Input, NTabs: Content, Tabs: Content, NTab: Content, Tab: Content, NTooltip: true, Tooltip: true,
      NModal: true, Modal: true, NForm: true, Form: true, NFormItem: true, FormItem: true, NSelect: true, Select: true, NEmpty: true, Empty: true,
    } },
  });
  wrappers.push(wrapper);
  await (wrapper.vm as any).openTask(currentTask, viewOnly);
  await flushPromises();
  return wrapper;
}
const buttons = (wrapper: VueWrapper, label: string) => wrapper.findAll('button').filter(button => button.text() === label);

beforeEach(() => {
  vi.clearAllMocks();
  mocks.getInstance.mockResolvedValue({ data: { id: 'instance-1', businessKey: 'check-1', status: 'RUNNING' } });
});
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()));

describe('task business draft save', () => {
  it.each<{ availableActions: TaskAction[] }>([
    { availableActions: ['APPROVE'] },
    { availableActions: ['APPROVE', 'SAVE'] },
    { availableActions: ['SAVE'] },
  ])(
    'saves with server actions $availableActions without validation, comments, or workflow completion',
    async ({ availableActions }) => {
      const form = businessForm();
      const wrapper = await openDrawer(task({ availableActions }));
      expect(buttons(wrapper, '暂存')).toHaveLength(1);
      await buttons(wrapper, '暂存')[0].trigger('click');
      await flushPromises();
      expect(form.saveTaskDraft).toHaveBeenCalledTimes(1);
      expect(form.saveTaskDraft).toHaveBeenCalledWith({ comment: '' });
      expect(form.validate).not.toHaveBeenCalled();
      expect(form.runTaskAction).not.toHaveBeenCalled();
      expect(mocks.completeTask).not.toHaveBeenCalled();
      expect(mocks.warning).not.toHaveBeenCalled();
      expect(mocks.success).toHaveBeenCalledWith('草稿已保存');
      expect(wrapper.emitted('success')).toHaveLength(1);
      expect(wrapper.find('[data-testid="drawer"]').exists()).toBe(true);
    },
  );

  it.each<{ name: string; change: Partial<FlowTask>; viewOnly?: boolean }>([
    { name: 'explicit empty server actions', change: { availableActions: [] } },
    { name: 'transfer only', change: { availableActions: ['TRANSFER'] } },
    { name: 'reject only', change: { availableActions: ['REJECT'] } },
    { name: 'completed task', change: { status: 'COMPLETED', availableActions: ['APPROVE', 'SAVE'] } },
    { name: 'canceled task', change: { status: 'CANCELED' } },
    { name: 'unknown task status', change: { status: undefined } },
    { name: 'view-only task', change: {}, viewOnly: true },
  ])('hides business draft saving for $name', async ({ change, viewOnly }) => {
    const form = businessForm();
    const wrapper = await openDrawer(task(change), viewOnly);
    expect(buttons(wrapper, '暂存')).toHaveLength(0);
    expect(form.saveTaskDraft).not.toHaveBeenCalled();
  });

  it('passes the current review comment unchanged without submission validation', async () => {
    const form = businessForm();
    const wrapper = await openDrawer(task({ nodeKey: 'center_review', type: 'APPROVE' }));
    const comment = '  外业点位待补\n继续核对  ';
    await wrapper.get('textarea').setValue(comment);
    await buttons(wrapper, '暂存')[0].trigger('click');
    await flushPromises();
    expect(form.saveTaskDraft).toHaveBeenCalledWith({ comment });
    expect(form.validate).not.toHaveBeenCalled();
    expect(form.runTaskAction).not.toHaveBeenCalled();
    expect(mocks.completeTask).not.toHaveBeenCalled();
    expect(wrapper.find('[data-testid="drawer"]').exists()).toBe(true);
  });

  it('restores saved review comments after reopening and preserves an intentional clear', async () => {
    const form = businessForm();
    let savedComment = '';
    form.saveTaskDraft.mockImplementation(async (input) => {
      savedComment = input?.comment || '';
      return { close: false };
    });
    const reviewTask = task({ nodeKey: 'center_review', type: 'APPROVE' });
    const wrapper = await openDrawer(reviewTask);
    await wrapper.get('textarea').setValue('已核查东侧点位，西侧待补充');
    await buttons(wrapper, '暂存')[0].trigger('click');
    await flushPromises();
    await buttons(wrapper, '关闭')[0].trigger('click');
    await (wrapper.vm as any).openTask({ ...reviewTask, comment: savedComment });
    await flushPromises();
    expect(wrapper.get<HTMLTextAreaElement>('textarea').element.value).toBe('已核查东侧点位，西侧待补充');
    await buttons(wrapper, '暂存')[0].trigger('click');
    await flushPromises();
    expect(form.saveTaskDraft).toHaveBeenLastCalledWith({ comment: '已核查东侧点位，西侧待补充' });
    await wrapper.get('textarea').setValue('');
    await buttons(wrapper, '暂存')[0].trigger('click');
    await flushPromises();
    expect(savedComment).toBe('');
    expect(form.saveTaskDraft).toHaveBeenLastCalledWith({ comment: '' });
    expect(wrapper.get<HTMLTextAreaElement>('textarea').element.value).toBe('');
    expect(form.validate).not.toHaveBeenCalled();
    expect(mocks.completeTask).not.toHaveBeenCalled();
  });

  it('retains the existing empty comment behavior for forms without the draft protocol', async () => {
    businessForm({ withDraftConfig: false });
    const wrapper = await openDrawer(task({ comment: '既有任务意见', availableActions: ['APPROVE', 'SAVE'] }));
    expect(wrapper.get<HTMLTextAreaElement>('textarea').element.value).toBe('');
  });

  it('respects business opt-out even when the server advertises SAVE', async () => {
    businessForm({ allowSaveDraft: false });
    const wrapper = await openDrawer(task({ availableActions: ['SAVE', 'APPROVE'] }));
    expect(buttons(wrapper, '暂存')).toHaveLength(0);
  });

  it.each([
    { name: 'without a draft method', withDraftSaver: false },
    { name: 'with a draft method but no draft configuration', withDraftConfig: false },
  ])('keeps validation and the existing SAVE action $name', async (options) => {
    const form = businessForm({ ...options, requiredCommentActions: [] });
    form.validate.mockResolvedValue({ incomplete: true });
    const wrapper = await openDrawer(task({ availableActions: ['SAVE', 'APPROVE'] }));
    await buttons(wrapper, '暂存')[0].trigger('click');
    await flushPromises();
    expect(form.validate).toHaveBeenCalledTimes(1);
    expect(form.runTaskAction).toHaveBeenCalledWith(expect.objectContaining({ action: 'SAVE', formData: { incomplete: true } }));
    expect(form.saveTaskDraft).not.toHaveBeenCalled();
  });

  it('still validates normal submission when draft saving is enabled', async () => {
    const form = businessForm();
    const wrapper = await openDrawer();
    await buttons(wrapper, '提交')[0].trigger('click');
    await flushPromises();
    expect(form.validate).toHaveBeenCalledTimes(1);
    expect(form.saveTaskDraft).not.toHaveBeenCalled();
    expect(form.runTaskAction).not.toHaveBeenCalled();
    expect(mocks.completeTask).not.toHaveBeenCalled();
  });

  it('does not offer draft saving when task details fail to load', async () => {
    const form = businessForm();
    mocks.getInstance.mockRejectedValueOnce(new Error('无法读取任务'));
    const wrapper = await openDrawer();
    expect(buttons(wrapper, '暂存')).toHaveLength(0);
    expect(form.saveTaskDraft).not.toHaveBeenCalled();
    expect(mocks.error).toHaveBeenCalledWith('无法读取任务');
  });

  it('blocks duplicate saves and submit while saving', async () => {
    const form = businessForm();
    let finish!: () => void;
    form.saveTaskDraft.mockReturnValue(new Promise<void>(resolve => { finish = resolve; }));
    const wrapper = await openDrawer();
    await buttons(wrapper, '暂存')[0].trigger('click');
    await buttons(wrapper, '暂存')[0].trigger('click');
    await buttons(wrapper, '提交')[0].trigger('click');
    expect(form.saveTaskDraft).toHaveBeenCalledTimes(1);
    expect(form.validate).not.toHaveBeenCalled();
    expect(form.runTaskAction).not.toHaveBeenCalled();
    finish();
    await flushPromises();
    expect(mocks.success).toHaveBeenCalledWith('暂存成功');
  });

  it('shows save errors, keeps the form open, and allows retry', async () => {
    const form = businessForm();
    form.saveTaskDraft.mockRejectedValueOnce(new Error('网络错误'));
    const wrapper = await openDrawer();
    await buttons(wrapper, '暂存')[0].trigger('click');
    await flushPromises();
    expect(mocks.error).toHaveBeenCalledWith('网络错误');
    expect(wrapper.emitted('success')).toBeUndefined();
    expect(wrapper.find('[data-testid="drawer"]').exists()).toBe(true);
    await buttons(wrapper, '暂存')[0].trigger('click');
    await flushPromises();
    expect(form.saveTaskDraft).toHaveBeenCalledTimes(2);
    expect(mocks.success).toHaveBeenCalledTimes(1);
  });

  it('ignores a stale save response after switching tasks', async () => {
    const form = businessForm();
    let finish!: (value: FlowFormTaskActionResult) => void;
    form.saveTaskDraft.mockReturnValue(new Promise(resolve => { finish = resolve; }));
    const wrapper = await openDrawer();
    await buttons(wrapper, '暂存')[0].trigger('click');
    await (wrapper.vm as any).openTask(task({ id: 'task-2' }));
    await flushPromises();
    finish({ close: true, successMessage: '旧任务保存成功' });
    await flushPromises();
    expect(mocks.success).not.toHaveBeenCalled();
    expect(wrapper.emitted('success')).toBeUndefined();
    expect(wrapper.find('[data-testid="drawer"]').exists()).toBe(true);
    expect(buttons(wrapper, '暂存')).toHaveLength(1);
  });
});
