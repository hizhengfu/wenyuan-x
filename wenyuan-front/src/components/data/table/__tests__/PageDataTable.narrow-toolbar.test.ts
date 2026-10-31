/** @vitest-environment happy-dom */
import { readFileSync } from 'node:fs';
import { parse } from '@vue/compiler-sfc';
import { flushPromises, mount } from '@vue/test-utils';
import { createGenerator } from 'unocss';
import { NButton, NCard, NSpace } from 'naive-ui';
import { defineComponent, h } from 'vue';
import { afterEach, expect, it, vi } from 'vitest';
import unoConfig from '~/uno.config';
import PageDataTable from '../PageDataTable.vue';

const auth = vi.hoisted(() => ({ allowCreate: true }));
vi.mock('sortablejs', () => ({ default: { create: vi.fn() } }));
vi.mock('@/store', () => ({ useAppStore: () => ({ tableSize: 'small' }), useAuthStore: () => ({ hasPermission: () => auth.allowCreate, hasPermissionByCode: () => auth.allowCreate }) }));
vi.mock('vue-router', () => ({ useRoute: () => ({ path: '/collection/warehouse', query: { title: '库房保管' } }) }));
vi.mock('@/modules/sys/api/dict', () => ({ type: vi.fn().mockResolvedValue({ data: [] }) }));
vi.mock('@/utils', () => ({ confirm: vi.fn(), exportNaive2Excel: vi.fn(), fileUrl: (v: unknown) => v, formatDateTime: (v: unknown) => String(v ?? ''), formatDateValue: (v: unknown) => String(v ?? ''), transformToArrayFormat: (v: unknown) => v, transformToTreeFormat: (v: unknown) => v }));
vi.mock('@/utils/request.ts', () => ({ default: vi.fn() }));
vi.mock('@/components/basic/DynamicIcon.vue', () => ({ default: { template: '<span />' } }));
vi.mock('../../../form/form/AdvanceQueryForm.vue', () => ({ default: { template: '<div><slot /></div>' } }));
vi.mock('@/components/basic/print/TemplatePrint.vue', () => ({ default: { template: '<span />' } }));

const cleanups: Array<() => void> = [];
afterEach(() => { cleanups.splice(0).reverse().forEach(fn => fn()); auth.allowCreate = true; });

// happy-dom does not apply CSS media rules consistently. Select the active rules
// from the actual Uno output and SFC CSS using the viewport's public matchMedia.
// This checks display/click contracts, not browser geometry or pixel layout.
function activeCss(css: string): string {
  const scratch = document.createElement('style'); scratch.textContent = css; document.head.append(scratch);
  const visit = (rules: CSSRuleList): string => Array.from(rules).map(rule => {
    if (rule.type === CSSRule.MEDIA_RULE) {
      const media = rule as CSSMediaRule;
      return window.matchMedia(media.conditionText).matches ? visit(media.cssRules) : '';
    }
    return rule.cssText;
  }).join('\n');
  const result = visit(scratch.sheet!.cssRules); scratch.remove(); return result;
}

it('keeps authorized header business controls visible and usable at 612px without exposing denied add actions', async () => {
  (window as typeof window & { happyDOM: { setWindowSize: (size: { width: number; height: number }) => void } }).happyDOM.setWindowSize({ width: 612, height: 979 });
  const source = readFileSync('src/components/data/table/PageDataTable.vue', 'utf8');
  const uno = await createGenerator(unoConfig);
  const generated = await uno.generate(source);
  const css = generated.css + '\n' + parse(source).descriptor.styles.map(s => s.content).join('\n');
  const style = document.createElement('style'); style.textContent = activeCss(css); document.head.append(style);
  cleanups.push(() => style.remove());
  const staffing = vi.fn();
  const Host = defineComponent({ setup: () => () => h(PageDataTable, {
    title: '库房保管', columns: [{ title: '位置名称', key: 'name' }],
    data: [{ id: 'own', name: 'DEV 库房' }], initQuery: false,
    isAdd: true, permission: 'COLLECTION_WAREHOUSE', addPermission: 'COLLECTION_WAREHOUSE_MANAGE',
    isExport: false, isEdit: false, isDelete: false, showOperate: false,
  }, { 'toolbar-extra': () => h(NSpace, { size: 'small' }, { default: () => [
    h(NButton, { onClick: staffing }, { default: () => '实际库管任职' }),
    h(NButton, null, { default: () => '库区安全值班' }),
  ] }) }) });
  const options = { attachTo: document.body, global: { stubs: {
    DataTable: { inheritAttrs: false, template: '<div data-testid="rows" />' },
    HFileDialogPreview: true, HFullScreen: { inheritAttrs: false, template: '<span />' }, 'h-full-screen': { inheritAttrs: false, template: '<span />' },
  } } };
  const wrapper = mount(Host, options); cleanups.push(() => wrapper.unmount()); await flushPromises();
  expect(wrapper.findComponent(NCard).exists()).toBe(true);
  const business = wrapper.findAll('button').find(b => b.text() === '实际库管任职')!;
  expect.soft(business.isVisible()).toBe(true);
  expect.soft(wrapper.get('[aria-label="新增记录"]').isVisible()).toBe(true);
  expect.soft(wrapper.get('[aria-label="列设置"]').isVisible()).toBe(true);
  await business.trigger('click'); expect(staffing).toHaveBeenCalledTimes(1);
  await wrapper.get('[aria-label="新增记录"]').trigger('click');
  expect(wrapper.findComponent(PageDataTable).emitted('add')).toHaveLength(1);
  auth.allowCreate = false;
  const reader = mount(Host, options); cleanups.push(() => reader.unmount()); await flushPromises();
  expect(reader.find('[aria-label="新增记录"]').exists()).toBe(false);
  expect(reader.findAll('button').find(b => b.text() === '实际库管任职')!.isVisible()).toBe(true);
});
