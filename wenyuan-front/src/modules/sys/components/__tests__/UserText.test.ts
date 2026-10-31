// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { reactive } from 'vue';
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils';
import UserText from '../UserText.vue';
import { createUserNameResolver } from '../../utils/userDisplayNames';
const m = vi.hoisted(() => ({ displayNames: vi.fn() }));
vi.mock('../../api/user', () => ({ displayNames: m.displayNames }));
let auth: any;
vi.mock('@/store/modules/auth', () => ({ useAuthStore: () => auth }));
const wrappers: VueWrapper[] = [];
function open(value?: any, extra = {}) {
  const w = mount(UserText, { props: { value, ...extra } });
  wrappers.push(w);
  return w;
}
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => (resolve = done));
  return { promise, resolve };
}
beforeEach(() => {
  vi.clearAllMocks();
  auth = reactive({ token: 'session-A', userInfo: { id: 'viewer', groupId: 'org-A' }, permissions: [] });
  m.displayNames.mockImplementation(async (ids: string[]) => ({ data: ids.map((id) => ({ id, name: `姓名${id}` })) }));
});
afterEach(() => wrappers.splice(0).forEach((w) => w.unmount()));
describe('read-only user labels', () => {
  it('shows plain text and batches duplicated IDs across multiple users and instances', async () => {
    const a = open('1'),
      b = open(['1', '2', '2']);
    await flushPromises();
    expect(a.text()).toBe('姓名1');
    expect(b.text()).toBe('姓名1、姓名2');
    expect(m.displayNames).toHaveBeenCalledTimes(1);
    expect(m.displayNames).toHaveBeenCalledWith(['1', '2']);
    expect(a.find('input,select,button').exists()).toBe(false);
    expect(a.get('span').attributes('title')).toBe('1');
  });
  it('preserves supplied historical names and empty placeholders without a request', async () => {
    expect(open('1', { name: '当时登记姓名' }).text()).toBe('当时登记姓名');
    expect(open(null, { placeholder: '未指派' }).text()).toBe('未指派');
    await flushPromises();
    expect(m.displayNames).not.toHaveBeenCalled();
  });
  it('retains unresolved personnel IDs and permits retry after a read failure', async () => {
    m.displayNames.mockRejectedValueOnce(new Error('读取失败'));
    const first = open('missing');
    await flushPromises();
    expect(first.text()).toBe('人员编号 missing');
    first.unmount();
    const second = open('missing');
    await flushPromises();
    expect(second.text()).toBe('姓名missing');
    expect(m.displayNames).toHaveBeenCalledTimes(2);
  });
  it('ignores a late name when the referenced person changes', async () => {
    const old = deferred<any>();
    m.displayNames.mockReturnValueOnce(old.promise);
    const w = open('old');
    await flushPromises();
    await w.setProps({ value: 'new' });
    await flushPromises();
    old.resolve({ data: [{ id: 'old', name: '旧姓名' }] });
    await flushPromises();
    expect(w.text()).toBe('姓名new');
  });
  it('clears the cache on account/scope changes and hides old-session responses', async () => {
    const old = deferred<any>();
    m.displayNames.mockReturnValueOnce(old.promise);
    const w = open('1');
    await flushPromises();
    auth.token = 'session-B';
    auth.userInfo = { id: 'other', groupId: 'org-B' };
    m.displayNames.mockResolvedValue({ data: [] });
    await flushPromises();
    old.resolve({ data: [{ id: '1', name: '原机构姓名' }] });
    await flushPromises();
    expect(w.text()).toBe('人员编号 1');
    expect(m.displayNames).toHaveBeenCalledTimes(2);
    auth.token = '';
    await flushPromises();
    expect(w.text()).toBe('人员编号 1');
    expect(m.displayNames).toHaveBeenCalledTimes(2);
  });
  it('handles comma-separated IDs and optional login names', async () => {
    m.displayNames.mockResolvedValue({
      data: [
        { id: '1', name: '甲', loginName: 'a' },
        { id: '2', loginName: 'b' },
      ],
    });
    const w = open('1,2；1', { showLoginName: true });
    await flushPromises();
    expect(w.text()).toBe('甲（a）、b');
  });
});
it('splits requests at the backend bound while preserving every result', async () => {
  const load = vi.fn(async (ids: string[]) => ids.map((id) => ({ id, name: id })));
  const resolver = createUserNameResolver(load);
  const names = await Promise.all(Array.from({ length: 205 }, (_, i) => resolver.resolve(String(i))));
  expect(load.mock.calls.map(([ids]) => ids.length)).toEqual([100, 100, 5]);
  expect(names).toHaveLength(205);
  expect(names[204]?.id).toBe('204');
});
