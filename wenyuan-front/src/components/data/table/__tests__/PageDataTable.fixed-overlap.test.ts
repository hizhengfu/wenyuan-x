/** @vitest-environment happy-dom */
import { readFileSync } from 'node:fs';
import { parse } from '@vue/compiler-sfc';
import { flushPromises, mount } from '@vue/test-utils';
import { NButton, NCard, NConfigProvider, NDataTable } from 'naive-ui';
import { defineComponent, h, nextTick } from 'vue';
import { afterEach, expect, it, vi } from 'vitest';
import PageDataTable from '../PageDataTable.vue';

vi.mock('sortablejs', () => ({ default: { create: vi.fn() } }));
vi.mock('@/store', () => ({ useAppStore: () => ({ tableSize: 'small' }), useAuthStore: () => ({ hasPermission: () => true, hasPermissionByCode: () => true }) }));
vi.mock('vue-router', () => ({ useRoute: () => ({ path: '/collection-restoration/project', query: {} }) }));
vi.mock('@/modules/sys/api/dict', () => ({ type: vi.fn().mockResolvedValue({ data: [] }) }));
vi.mock('@/utils', () => ({ confirm: vi.fn(), exportNaive2Excel: vi.fn(), fileUrl: (v: unknown) => v, formatDateTime: (v: unknown) => String(v ?? ''), formatDateValue: (v: unknown) => String(v ?? ''), transformToArrayFormat: (v: unknown) => v, transformToTreeFormat: (v: unknown) => v }));
vi.mock('@/utils/request.ts', () => ({ default: vi.fn() }));
vi.mock('@/components/basic/DynamicIcon.vue', () => ({ default: { template: '<span />' } }));
vi.mock('../../../form/form/AdvanceQueryForm.vue', () => ({ default: { template: '<div />' } }));
vi.mock('@/components/basic/print/TemplatePrint.vue', () => ({ default: { template: '<span />' } }));

const cleanups: Array<() => void> = [];
afterEach(() => {
  cleanups.splice(0).reverse().forEach(fn => fn());
  (window as typeof window & { happyDOM: { setWindowSize: (size: { width: number; height: number }) => void } }).happyDOM.setWindowSize({ width: 1024, height: 768 });
});

// Same public media-rule selection used by the existing toolbar check. DOM here
// proves the actual Naive cell style/click contract, not browser pixel geometry.
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

it('keeps every column and the real aftercare action while releasing left sticky cells only on narrow screens', async () => {
  const source = readFileSync('src/components/data/table/PageDataTable.vue', 'utf8');
  const css = parse(source).descriptor.styles.map(s => s.content).join('\n');
  const style = document.createElement('style'); document.head.append(style); cleanups.push(() => style.remove());
  const aftercare = vi.fn();
  const props = {
      title: '修复项目', showHeader: false, showToolbar: false, initQuery: false,
      isAdd: false, isEdit: false, isDelete: false, isCopy: false, isExport: false, showOperate: false,
      columns: [
        { title: '总登记号', key: 'regNo', width: 150, fixed: 'left' },
        { title: '藏品名称', key: 'name', minWidth: 160, fixed: 'left' },
        { title: '修复机构', key: 'org', width: 150 },
        { title: '操作', key: 'operate', width: 250, fixed: 'right', render: () => h(NButton, { text: true, onClick: aftercare }, () => '后续养护') },
      ],
      data: [{ id: 'own', regNo: '000008', name: 'DEV 合成藏品', org: 'DEV 修复机构' }],
  };
  const Host = defineComponent({ setup: () => () => h(NConfigProvider, null, { default: () => h(PageDataTable, props) }) });
  const wrapper = mount(Host, { attachTo: document.body, global: { components: { NCard, NDataTable }, stubs: { HFileDialogPreview: true } } });
  cleanups.push(() => wrapper.unmount());
  await flushPromises(); await nextTick();
  expect(wrapper.findComponent(NCard).exists()).toBe(true);
  expect(wrapper.findComponent(NDataTable).exists()).toBe(true);
  expect(wrapper.findAll('.n-data-table-tbody > tr > td')).toHaveLength(4);
  const left = wrapper.findAll('.n-data-table-th--fixed-left, .n-data-table-td--fixed-left');
  const right = wrapper.findAll('.n-data-table-th--fixed-right, .n-data-table-td--fixed-right');
  expect(left.length).toBeGreaterThanOrEqual(4); expect(right.length).toBeGreaterThanOrEqual(2);
  for (const width of [612, 1280, 612]) {
    (window as typeof window & { happyDOM: { setWindowSize: (size: { width: number; height: number }) => void } }).happyDOM.setWindowSize({ width, height: 979 });
    style.textContent = activeCss(css); await nextTick();
    for (const cell of left) expect.soft(getComputedStyle(cell.element).position).toBe(width < 768 ? 'static' : 'sticky');
    for (const cell of right) expect(getComputedStyle(cell.element).position).toBe('sticky');
    expect(wrapper.text()).toContain('000008'); expect(wrapper.text()).toContain('DEV 合成藏品');
    const action = wrapper.findAll('button').find(b => b.text() === '后续养护')!;
    expect(action.isVisible()).toBe(true); await action.trigger('click');
  }
  expect(aftercare).toHaveBeenCalledTimes(3);
  expect(wrapper.findComponent(NDataTable).props('columns')!.map((c: unknown) => (c as { key?: string | number }).key)).toEqual(['regNo', 'name', 'org', 'operate']);
});
