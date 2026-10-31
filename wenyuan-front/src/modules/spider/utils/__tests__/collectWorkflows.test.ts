/** @vitest-environment happy-dom */
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils';
import { defineComponent, h, KeepAlive, nextTick, reactive, ref } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import CollectPage from '../../components/WechatAutomaticCollect.vue';
import Workbench from '../../views/wechatCollect.vue';
import LegacyEntry from '../../views/collect.vue';
import LinkPage from '../../components/WechatLinkCollect.vue';
import CollectMethodNav from '../../components/CollectMethodNav.vue';
import * as api from '../../api/collect';
import * as links from '../../api/wechat';

const mocks = vi.hoisted(() => ({
  replace: vi.fn(),
  removeTab: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
  info: vi.fn(),
  auth: {} as any,
  route: {} as any,
}));
vi.mock('@/store', () => ({ useAuthStore: () => mocks.auth, useTabStore: () => ({ removeTab: mocks.removeTab }) }));
vi.mock('vue-router', () => ({ useRouter: () => ({ replace: mocks.replace }), useRoute: () => mocks.route }));
vi.mock('@/components/form/form/index', () => ({}));
vi.mock('naive-ui', async (original) => ({
  ...(await original<typeof import('naive-ui')>()),
  useMessage: () => mocks,
  useDialog: () => ({ warning: vi.fn() }),
}));
vi.mock('../../api/collect', () => ({
  collectConnections: vi.fn(),
  collectCategories: vi.fn(),
  collectPlans: vi.fn(),
  createCollectPlan: vi.fn(),
  runCollectPlan: vi.fn(),
  configureCollectPlan: vi.fn(),
  deleteCollectPlan: vi.fn(),
  searchCollectSources: vi.fn(),
  previewCollectSource: vi.fn(),
  startCollectAuthorization: vi.fn(),
  pollCollectAuthorization: vi.fn(),
  cancelCollectAuthorization: vi.fn(),
}));
vi.mock('../../api/wechat', () => ({
  capabilities: vi.fn(),
  list: vi.fn(),
  add: vi.fn(),
  edit: vi.fn(),
  del: vi.fn(),
  preview: vi.fn(),
  collect: vi.fn(),
}));
vi.mock('@/modules/sys/api/asyncTask', () => ({
  listAsyncTasks: vi.fn().mockResolvedValue({ data: { list: [] } }),
  cancelAsyncTask: vi.fn(),
}));

const wrappers: VueWrapper[] = [];
const slot = {
  inheritAttrs: false,
  emits: ['click'],
  template: '<div @click="$emit(\'click\')"><slot /><slot name="header-extra" /></div>',
};
const stubs = Object.fromEntries(
  [
    'NCard',
    'NSpace',
    'NForm',
    'NGrid',
    'NGi',
    'NFormItemGi',
    'NFormItem',
    'NAlert',
    'NButton',
    'NText',
    'HPageDataTable',
    'HPageForm',
  ].flatMap((name) => [
    [name, slot],
    [name.replace(/^N/, ''), slot],
  ]),
);
const global = { stubs, renderStubDefaultSlot: true };
function mount(component: any, options: any = {}) {
  const wrapper = shallowMount(component, { global, ...options });
  wrappers.push(wrapper);
  return wrapper;
}
function form(wrapper: VueWrapper) {
  return (wrapper.vm as any).$?.setupState ?? wrapper.vm;
}
const menu = (key: 'links' | 'automatic') =>
  key === 'links' ? { id: '50003', path: '/spider/wechatCollect' } : { id: 'cms-collect', path: '/spider/collect' };
function ready(wrapper: VueWrapper, mode: 'once' | 'scheduled') {
  Object.assign(form(wrapper), {
    connectionId: 'connection',
    sourceKey: 'source',
    categoryId: 'category',
    name: '行业资讯',
    executionMode: mode,
  });
}
beforeEach(() => {
  vi.useFakeTimers();
  vi.clearAllMocks();
  mocks.auth = reactive({
    menus: [{ path: '/spider', children: [menu('links')] }],
    permissions: ['ADMIN'],
    hasPermission(codes: string[]) {
      return this.permissions.includes('ADMIN') || codes.some((code) => this.permissions.includes(code));
    },
  });
  mocks.route = reactive({ path: '/spider/wechatCollect', query: { menuId: '50003', title: '公众号采集' } });
  mocks.replace.mockImplementation(async (target) => {
    mocks.route.path = target.path;
    mocks.route.query = target.query;
  });
  vi.mocked(links.capabilities).mockResolvedValue({ data: { automatic: true } });
  vi.mocked(api.collectConnections).mockResolvedValue([
    { id: 'connection', name: '连接', kind: 'SESSION', status: 'CONNECTED' },
  ]);
  vi.mocked(api.collectCategories).mockResolvedValue([{ id: 'category', name: '测试 / 文章' }]);
  vi.mocked(api.collectPlans).mockResolvedValue([]);
  vi.mocked(api.createCollectPlan).mockResolvedValue('saved-plan');
  vi.mocked(api.runCollectPlan).mockResolvedValue('task');
  vi.mocked(links.add).mockResolvedValue({} as any);
  vi.mocked(links.edit).mockResolvedValue({} as any);
});
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount());
  vi.clearAllTimers();
  vi.useRealTimers();
});

describe('single collection workbench', () => {
  it('navigation switches modes inside the page', async () => {
    const wrapper = mount(CollectMethodNav, { props: { method: 'links', methods: ['links', 'automatic'] } });
    const target = wrapper.findAll('div').find((node) => node.text().trim() === '扫码 / 定时');
    await target!.trigger('click');
    expect(wrapper.emitted('select')).toEqual([['automatic']]);
    expect(mocks.replace).not.toHaveBeenCalled();
  });
  it('both modes use one menu and route without depending on the removed menu', async () => {
    const wrapper = mount(Workbench);
    await flushPromises();
    expect(form(wrapper).methods).toEqual(['links', 'automatic']);
    expect(wrapper.findComponent(CollectPage).exists()).toBe(true);
    form(wrapper).selectMethod('links');
    await nextTick();
    expect(wrapper.findComponent(LinkPage).exists()).toBe(true);
    expect(mocks.replace).toHaveBeenLastCalledWith({
      path: '/spider/wechatCollect',
      query: { menuId: '50003', title: '公众号采集', method: 'links' },
    });
    form(wrapper).selectMethod('automatic');
    await nextTick();
    expect(mocks.route.path).toBe('/spider/wechatCollect');
    expect(mocks.route.query.menuId).toBe('50003');
  });
  it('a cached workbench does not redirect navigation to another page', async () => {
    mount(Workbench);
    await flushPromises();
    mocks.replace.mockClear();
    mocks.route.path = '/cms/article';
    mocks.route.query = { menuId: 'article' };
    await nextTick();
    expect(mocks.replace).not.toHaveBeenCalled();
    mocks.route.path = '/spider/wechatCollect';
    mocks.route.query = { menuId: '50003', method: 'links' };
    await nextTick();
    expect(mocks.replace).toHaveBeenCalledWith(
      expect.objectContaining({
        path: '/spider/wechatCollect',
        query: expect.objectContaining({ method: 'links' }),
      }),
    );
  });
  it('without CMS even ADMIN sees only link and RSS collection', async () => {
    vi.mocked(links.capabilities).mockResolvedValueOnce({ data: { automatic: false } });
    const wrapper = mount(Workbench);
    await flushPromises();
    expect(form(wrapper).methods).toEqual(['links']);
    expect(wrapper.findComponent(CollectPage).exists()).toBe(false);
    expect(wrapper.findComponent(LinkPage).exists()).toBe(true);
    expect(api.collectConnections).not.toHaveBeenCalled();
  });
  it('a role with only automatic collection permission does not load link configuration', async () => {
    mocks.auth.permissions = ['CMS_COLLECT_ALL'];
    const wrapper = mount(Workbench);
    await flushPromises();
    expect(form(wrapper).methods).toEqual(['automatic']);
    expect(wrapper.findComponent(LinkPage).exists()).toBe(false);
    expect(links.list).not.toHaveBeenCalled();
  });
  it('a role without automatic permission falls back from an automatic bookmark', async () => {
    mocks.auth.permissions = ['SPIDER_RULE_SELECT'];
    mocks.route.query.method = 'automatic';
    const wrapper = mount(Workbench);
    await flushPromises();
    expect(form(wrapper).methods).toEqual(['links']);
    expect(form(wrapper).method).toBe('links');
  });
  it('capability failure is recoverable and does not silently hide the automatic mode', async () => {
    vi.mocked(links.capabilities).mockRejectedValueOnce({ response: { status: 500 } });
    const wrapper = mount(Workbench);
    await flushPromises();
    expect(form(wrapper).loadError).toBe(true);
    expect(wrapper.findComponent(LinkPage).exists()).toBe(false);
    await form(wrapper).loadCapabilities();
    await flushPromises();
    expect(form(wrapper).loadError).toBe(false);
    expect(wrapper.findComponent(CollectPage).exists()).toBe(true);
  });
  it('an older backend can use the legacy menu until it is restarted', async () => {
    mocks.auth.menus[0].children.push(menu('automatic'));
    vi.mocked(links.capabilities).mockRejectedValueOnce({ response: { status: 404 } });
    const wrapper = mount(Workbench);
    await flushPromises();
    expect(form(wrapper).methods).toEqual(['links', 'automatic']);
  });
  it('the legacy address redirects to the automatic mode and removes its old tab', async () => {
    mocks.route.path = '/spider/collect';
    mocks.route.query.menuId = 'cms-collect';
    mount(LegacyEntry);
    await flushPromises();
    expect(mocks.replace).toHaveBeenCalledTimes(1);
    expect(mocks.replace).toHaveBeenCalledWith({
      path: '/spider/wechatCollect',
      query: { menuId: '50003', title: '公众号采集', method: 'automatic' },
    });
    expect(mocks.removeTab).toHaveBeenCalledWith('/spider/collect');
  });
  it('creates only link or RSS configurations with draft default while preserving legacy editing', async () => {
    const wrapper = mount(LinkPage);
    const state = form(wrapper);
    state.handleAdd();
    await nextTick();
    expect(state.sourceOptions.map((option: any) => option.value)).toEqual(['URL_LIST', 'RSS']);
    expect(state.form.status).toBe(2);
    await state.handleSubmit({ ...state.form, title: '新采集' });
    expect(links.add).toHaveBeenCalledWith(expect.objectContaining({ status: 2, source: 'URL_LIST' }));
    state.handleEdit({ id: 'old', title: '旧配置', source: 'MP_BACKEND', mpCookie: 'existing', status: 0 });
    await nextTick();
    expect(state.sourceOptions.find((option: any) => option.value === 'MP_BACKEND').disabled).toBe(true);
    await state.handleSubmit(state.form);
    expect(links.edit).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'old', mpCookie: 'existing', source: 'MP_BACKEND', status: 0 }),
    );
  });
});

describe('QR collection execution', () => {
  it('one-shot collection saves scheduling disabled before queuing the task', async () => {
    const wrapper = mount(CollectPage);
    await flushPromises();
    ready(wrapper, 'once');
    await form(wrapper).createPlan();
    expect(api.createCollectPlan).toHaveBeenCalledWith(expect.objectContaining({ enabled: false, autoPublish: false }));
    expect(api.runCollectPlan).toHaveBeenCalledWith('saved-plan');
    expect(vi.mocked(api.createCollectPlan).mock.invocationCallOrder[0]).toBeLessThan(
      vi.mocked(api.runCollectPlan).mock.invocationCallOrder[0],
    );
  });
  it('scheduled mode leaves initial dispatch to the scheduler', async () => {
    const wrapper = mount(CollectPage);
    await flushPromises();
    ready(wrapper, 'scheduled');
    await form(wrapper).createPlan();
    expect(api.createCollectPlan).toHaveBeenCalledWith(expect.objectContaining({ enabled: true, autoPublish: false }));
    expect(api.runCollectPlan).not.toHaveBeenCalled();
  });
  it('a failed enqueue keeps the saved manual configuration visible for retry', async () => {
    const wrapper = mount(CollectPage);
    await flushPromises();
    ready(wrapper, 'once');
    vi.mocked(api.runCollectPlan).mockRejectedValueOnce(new Error('queue unavailable'));
    await form(wrapper).createPlan();
    expect(api.createCollectPlan).toHaveBeenCalledTimes(1);
    expect(api.deleteCollectPlan).not.toHaveBeenCalled();
    expect(api.collectPlans).toHaveBeenCalledTimes(2);
    expect(form(wrapper).name).toBe('');
    expect(mocks.warning).toHaveBeenCalledWith(expect.stringContaining('配置已保存'));
  });
  it('stops background polling on KeepAlive leave and refreshes categories on return', async () => {
    const visible = ref(true);
    const Host = defineComponent({
      setup: () => () => h(KeepAlive, null, { default: () => (visible.value ? h(CollectPage) : null) }),
    });
    const wrapper = mount(Host, {
      global: { ...global, stubs: { ...stubs, WechatAutomaticCollect: false, KeepAlive: false } },
    });
    await flushPromises();
    expect(api.collectCategories).toHaveBeenCalledTimes(1);
    visible.value = false;
    await nextTick();
    await vi.advanceTimersByTimeAsync(30000);
    expect(api.collectPlans).toHaveBeenCalledTimes(1);
    visible.value = true;
    await nextTick();
    await flushPromises();
    expect(api.collectCategories).toHaveBeenCalledTimes(2);
  });
});
