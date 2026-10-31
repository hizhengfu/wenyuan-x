// @vitest-environment happy-dom
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { defineComponent, h } from 'vue';
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils';
import Panel from '../MuseumBackfillImportPanel.vue';
import type { BackfillTask } from '../../api/dataMaintenance';
const m = vi.hoisted(() => ({
  upload: vi.fn(),
  template: vi.fn(),
  tasks: vi.fn(),
  task: vi.fn(),
  rows: vi.fn(),
  confirm: vi.fn(),
  retry: vi.fn(),
  warning: vi.fn(),
  success: vi.fn(),
  saveAs: vi.fn(),
}));
vi.mock('@/utils', () => ({ formatDateTime: (value: unknown) => String(value) }));
vi.mock('file-saver', () => ({ saveAs: m.saveAs }));
vi.mock('naive-ui', async (original) => ({
  ...(await original<typeof import('naive-ui')>()),
  useDialog: () => ({ warning: m.warning }),
  useMessage: () => ({ success: m.success }),
}));
vi.mock('../../api/dataMaintenance', () => ({
  maintenanceApi: {
    upload: m.upload,
    template: m.template,
    tasks: m.tasks,
    task: m.task,
    taskRows: m.rows,
    confirm: m.confirm,
    retry: m.retry,
  },
}));
const Button = defineComponent({
  props: ['disabled'],
  setup:
    (props, { slots }) =>
    () =>
      h('button', { disabled: props.disabled }, slots.default?.()),
});
const Select = defineComponent({
  props: ['value', 'options', 'disabled'],
  emits: ['update:value'],
  setup:
    (props, { emit }) =>
    () =>
      h(
        'select',
        {
          value: props.value,
          disabled: props.disabled,
          onChange: (e: Event) => emit('update:value', (e.target as HTMLSelectElement).value),
        },
        (props.options || []).map((o: { value: string; label: string }) => h('option', { value: o.value }, o.label)),
      ),
});
const Input = defineComponent({
  props: ['value', 'placeholder', 'disabled'],
  emits: ['update:value'],
  setup:
    (props, { emit }) =>
    () =>
      h('input', {
        value: props.value,
        disabled: props.disabled,
        placeholder: props.placeholder,
        onInput: (e: Event) => emit('update:value', (e.target as HTMLInputElement).value),
      }),
});
const wrappers: VueWrapper[] = [];
const task = (values: Partial<BackfillTask> = {}): BackfillTask => ({
  id: 'TASK',
  orgId: 'ORG',
  fileName: '历史.xlsx',
  fileSize: 123,
  timeZone: 'Asia/Shanghai',
  status: 'READY',
  phase: 'VALIDATING',
  total: 1201,
  processed: 1201,
  insertable: 1200,
  duplicates: 1,
  errors: 0,
  affected: 0,
  skipped: 0,
  reason: '',
  errorMessage: null,
  createdAt: 1,
  updatedAt: 1,
  expiresAt: Date.now() + 86400000,
  purged: false,
  ...values,
});
function list(value: BackfillTask) {
  m.tasks.mockResolvedValue({ data: { list: [value], total: 1 } });
  m.task.mockResolvedValue({ data: value });
}
async function open() {
  const w = shallowMount(Panel, {
    props: { orgId: 'ORG' },
    global: {
      renderStubDefaultSlot: true,
      stubs: { NButton: Button, Button, NSelect: Select, Select, NInput: Input, Input },
    },
  });
  wrappers.push(w);
  await flushPromises();
  return w;
}
const button = (w: VueWrapper, label: string) => w.findAll('button').find((item) => item.text() === label)!;
beforeEach(() => {
  vi.clearAllMocks();
  list(task());
  m.rows.mockResolvedValue({ data: { list: [], total: 1201 } });
  m.template.mockResolvedValue(new Blob(['template']));
  m.confirm.mockResolvedValue({ data: task({ status: 'IMPORT_QUEUED', phase: 'IMPORTING', processed: 0 }) });
});
afterEach(() => {
  wrappers.splice(0).forEach((w) => w.unmount());
  vi.useRealTimers();
});

it('offers the downloadable template and accepts files above the old 10MB limit for background upload', async () => {
  m.tasks.mockResolvedValue({ data: { list: [], total: 0 } });
  const w = await open();
  await button(w, '下载补录模板（Excel）').trigger('click');
  await flushPromises();
  expect(m.template).toHaveBeenCalledWith('ORG');
  expect(m.saveAs).toHaveBeenCalledWith(expect.any(Blob), '采集数据补录模板.xlsx');
  const file = new File(['xlsx'], '大文件.xlsx');
  Object.defineProperty(file, 'size', { value: 10 * 1024 * 1024 + 1 });
  const input = w.find('input[type="file"]');
  Object.defineProperty(input.element, 'files', { configurable: true, value: [file] });
  await input.trigger('change');
  const pending = task({ status: 'QUEUED', phase: 'PARSING', total: 0, processed: 0 });
  m.upload.mockImplementation(async (_org, _zone, _file, progress) => {
    progress(100);
    list(pending);
    return { data: pending };
  });
  await button(w, '上传并后台校验').trigger('click');
  await flushPromises();
  expect(m.upload).toHaveBeenCalledWith('ORG', 'Asia/Shanghai', file, expect.any(Function), expect.any(AbortSignal));
  expect(w.text()).toContain('等待校验');
  expect(m.confirm).not.toHaveBeenCalled();
  expect(w.text()).not.toContain('每次最多');
});

it('restores a persisted task and confirms that task without reuploading or changing its saved timezone', async () => {
  const w = await open();
  expect(w.text()).toContain('可导入 1200 行，重复 1 行');
  expect(button(w, '确认后台导入').attributes('disabled')).toBeDefined();
  await w.find('input[placeholder="填写补录原因，如设备离线期间人工记录补录"]').setValue('设备离线补录');
  await w.find('select').setValue('UTC');
  await button(w, '确认后台导入').trigger('click');
  list(task({ status: 'IMPORT_QUEUED', phase: 'IMPORTING', processed: 0 }));
  await m.warning.mock.calls[0][0].onPositiveClick();
  await flushPromises();
  expect(m.confirm).toHaveBeenCalledWith('TASK', '设备离线补录');
  expect(m.upload).not.toHaveBeenCalled();
  expect(w.text()).toContain('等待导入');
});

it('blocks invalid tasks and fetches filtered row results using server pagination', async () => {
  list(task({ status: 'INVALID', errors: 2 }));
  const w = await open();
  expect(button(w, '确认后台导入')).toBeUndefined();
  expect(w.text()).toContain('尚未导入');
  await w.findAll('select')[1].setValue('CONFLICT');
  await flushPromises();
  expect(m.rows).toHaveBeenLastCalledWith('TASK', 1, 20, 'CONFLICT');
});

it('polls a running import to completion and stops polling after unmount', async () => {
  vi.useFakeTimers();
  list(task({ status: 'IMPORTING', phase: 'IMPORTING', processed: 250, affected: 250 }));
  const w = await open();
  expect(w.text()).toContain('已导入 250 行');
  list(task({ status: 'SUCCEEDED', phase: 'IMPORTING', processed: 1201, affected: 1200, skipped: 1, updatedAt: 2 }));
  await vi.advanceTimersByTimeAsync(3000);
  await flushPromises();
  expect(w.text()).toContain('已导入 1200 行，已跳过 1 行');
  expect(w.emitted('completed')).toHaveLength(1);
  const requests = m.tasks.mock.calls.length;
  w.unmount();
  await vi.advanceTimersByTimeAsync(9000);
  expect(m.tasks).toHaveBeenCalledTimes(requests);
});

it('reports partial completion and queues retry without promising a whole-file rollback', async () => {
  list(task({ status: 'FAILED', phase: 'IMPORTING', processed: 250, affected: 250, errorMessage: '数据库连接中断' }));
  const w = await open();
  expect(w.text()).toContain('已导入 250 行');
  expect(w.text()).toContain('重试将从断点继续');
  const pending = task({ status: 'IMPORT_QUEUED', phase: 'IMPORTING', processed: 250, affected: 250 });
  m.retry.mockImplementation(async () => {
    list(pending);
    return { data: pending };
  });
  await button(w, '重试任务').trigger('click');
  await flushPromises();
  expect(m.retry).toHaveBeenCalledWith('TASK');
  expect(w.text()).toContain('等待导入');
});

it('ignores a late response from the previously selected venue', async () => {
  let release!: (value: unknown) => void;
  m.tasks.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        release = resolve;
      }),
  );
  const w = await open();
  m.tasks.mockResolvedValue({ data: { list: [], total: 0 } });
  await w.setProps({ orgId: 'OTHER' });
  await flushPromises();
  release({ data: { list: [task()], total: 1 } });
  await flushPromises();
  expect(m.task).not.toHaveBeenCalled();
  expect(w.text()).not.toContain('历史.xlsx');
});
