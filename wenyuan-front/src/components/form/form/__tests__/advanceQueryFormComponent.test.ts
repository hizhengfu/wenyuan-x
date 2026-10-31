/**
 * @vitest-environment happy-dom
 */
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { defineComponent, nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import AdvanceQueryForm from '../AdvanceQueryForm.vue';

const FormStub = defineComponent({
  name: 'NForm',
  template: '<form><slot /></form>',
});

const FormItemStub = defineComponent({
  name: 'NFormItem',
  template: '<div><slot /></div>',
});

const ButtonStub = defineComponent({
  name: 'NButton',
  template: '<button><slot name="icon" /><slot /></button>',
});

const IconStub = defineComponent({
  name: 'NIcon',
  template: '<span><slot /></span>',
});

class ResizeObserverStub {
  observe() {}

  unobserve() {}

  disconnect() {}
}

class MutationObserverStub {
  observe() {}

  disconnect() {}
}

async function mountQueryForm(queryItems: string) {
  const wrapper = mount(AdvanceQueryForm, {
    slots: {
      default: queryItems,
      queryFormOperate: '<button type="button">查询</button>',
    },
    global: {
      stubs: {
        NButton: ButtonStub,
        NForm: FormStub,
        NFormItem: FormItemStub,
        NIcon: IconStub,
        IconMdiMenuDown: true,
        IconMdiMenuUp: true,
      },
    },
  });

  await flushPromises();
  await nextTick();
  return wrapper;
}

describe('AdvanceQueryForm operation layout', () => {
  let wrapper: VueWrapper | undefined;

  beforeEach(() => {
    localStorage.clear();
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    vi.stubGlobal('MutationObserver', MutationObserverStub);
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
      callback(0);
      return 1;
    });
    vi.stubGlobal('cancelAnimationFrame', vi.fn());
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (this: HTMLElement) {
      const isForm = this.classList.contains('page-query-form');
      const isOperate = this.classList.contains('page-query-form-operate');
      const width = isOperate ? 120 : 960;
      const right = this.dataset.right ? Number(this.dataset.right) : isForm || isOperate ? 960 : 600;
      return {
        bottom: 40,
        height: 40,
        left: right - width,
        right,
        top: 0,
        width,
        x: right - width,
        y: 0,
        toJSON: () => ({}),
      };
    });
    vi.spyOn(HTMLElement.prototype, 'getClientRects').mockReturnValue({ length: 1 } as DOMRectList);
    vi.spyOn(HTMLElement.prototype, 'offsetTop', 'get').mockImplementation(function (this: HTMLElement) {
      return this.dataset.row === 'second' ? 48 : 0;
    });
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = undefined;
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('places operations directly after query fields when everything fits on one row', async () => {
    wrapper = await mountQueryForm('<div data-row="first" /><div data-row="first" />');

    expect(wrapper.get('.page-query-form-operate').classes()).toContain('page-query-form-operate-inline');
  });

  it('keeps operations right-aligned when query fields wrap to another row', async () => {
    wrapper = await mountQueryForm('<div data-row="first" /><div data-row="second" />');

    expect(wrapper.get('.page-query-form-operate').classes()).not.toContain('page-query-form-operate-inline');
  });

  it('keeps operations right-aligned without showing more when the 12px inline gap does not fit', async () => {
    wrapper = await mountQueryForm(
      '<div data-row="first" data-right="700" /><div data-row="first" data-right="837" />',
    );

    expect(wrapper.get('.page-query-form-operate').classes()).not.toContain('page-query-form-operate-inline');
    expect(wrapper.get('.page-query-form').classes()).toContain('page-query-form-single');
    expect(wrapper.get('.page-query-form-more-toggle').classes()).toContain('page-query-form-more-toggle-hidden');
  });

  it('shows more when the operation area cannot fit on the first row even without the inline gap', async () => {
    wrapper = await mountQueryForm(
      '<div data-row="first" data-right="700" /><div data-row="first" data-right="850" />',
    );

    expect(wrapper.get('.page-query-form-operate').classes()).not.toContain('page-query-form-operate-inline');
    expect(wrapper.get('.page-query-form').classes()).not.toContain('page-query-form-single');
    expect(wrapper.get('.page-query-form-more-toggle').classes()).not.toContain('page-query-form-more-toggle-hidden');
  });
});
