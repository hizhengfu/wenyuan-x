import type { App, DirectiveBinding } from 'vue';
import { download } from '@/utils/download';
import { apiUrl, linkUrl, originalFileUrl } from '@/utils/path';

type State = {
  value: unknown;
  signature?: string;
  controller?: AbortController;
  observer?: MutationObserver;
  listener: (event: Event) => void;
};
const states = new WeakMap<HTMLElement, State>();
function spec(el: HTMLElement, value: any) {
  const data = typeof value === 'object' && value ? value : { url: value };
  const source = typeof data.url === 'string' ? data.url : '';
  let fileName =
    String(data.fileName || el.innerText || '下载')
      .split(/[\\/]/)
      .pop() || '下载';
  const segment = source.split(/[?#]/)[0].split('/').pop() || '';
  const ext = /\.([a-z\d]{1,12})$/i.exec(segment)?.[1];
  if (ext && !fileName.toLowerCase().endsWith('.' + ext.toLowerCase())) fileName += '.' + ext;
  const url = !source
    ? ''
    : data.isUserFile !== false
      ? originalFileUrl(source)
      : data.isApiPrefix !== false
        ? apiUrl(source)
        : linkUrl(source);
  return { url, fileName, params: data.params, preferServerFilename: data.preferServerFilename === true };
}
function visible(el: HTMLElement) {
  if (!el.isConnected) return false;
  for (let node: HTMLElement | null = el; node; node = node.parentElement) {
    if (
      node.hidden ||
      node.getAttribute('aria-hidden') === 'true' ||
      getComputedStyle(node).display === 'none' ||
      getComputedStyle(node).visibility === 'hidden'
    )
      return false;
  }
  return true;
}
function cancel(state: State) {
  state.controller?.abort();
  state.controller = undefined;
  state.observer?.disconnect();
  state.observer = undefined;
}
export default {
  install(app: App) {
    app.directive('download', {
      deep: true,
      mounted(el: HTMLElement, binding: DirectiveBinding) {
        const state: State = { value: binding.value, listener: () => {} };
        state.listener = (event) => {
          event.preventDefault();
          if (state.controller || !visible(el)) return;
          const captured = spec(el, state.value);
          if (!captured.url) return;
          const signature = JSON.stringify(captured),
            controller = new AbortController();
          state.controller = controller;
          state.signature = signature;
          const current = () =>
            states.get(el) === state &&
            state.controller === controller &&
            visible(el) &&
            JSON.stringify(spec(el, state.value)) === signature;
          state.observer = new MutationObserver(() => {
            if (!current()) cancel(state);
          });
          state.observer.observe(document.documentElement, {
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: ['style', 'class', 'hidden', 'aria-hidden'],
          });
          void download(captured.url, captured.fileName, captured.params, {
            signal: controller.signal,
            isCurrent: current,
            preferServerFilename: captured.preferServerFilename,
          }).finally(() => {
            if (state.controller === controller) cancel(state);
          });
        };
        states.set(el, state);
        el.addEventListener('click', state.listener);
      },
      updated(el: HTMLElement, binding: DirectiveBinding) {
        const state = states.get(el);
        if (!state) return;
        state.value = binding.value;
        if ((state.controller && JSON.stringify(spec(el, state.value)) !== state.signature) || !visible(el))
          cancel(state);
      },
      beforeUnmount(el: HTMLElement) {
        const state = states.get(el);
        if (!state) return;
        cancel(state);
        el.removeEventListener('click', state.listener);
        states.delete(el);
      },
    });
  },
};
