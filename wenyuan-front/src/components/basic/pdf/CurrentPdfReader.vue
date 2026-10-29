<template>
  <div class="current-pdf-reader">
    <div class="pdf-tools" v-if="count">
      <button :disabled="currentPage <= 1" @click="go(currentPage - 1)">上一页</button>
      <span role="status">第 {{ currentPage }} / {{ count }} 页</span>
      <button :disabled="currentPage >= count" @click="go(currentPage + 1)">下一页</button>
      <button :disabled="zoom <= 0.5" @click="zoom = Math.max(0.5, zoom - 0.25)">缩小</button>
      <span>{{ Math.round(zoom * 100) }}%</span>
      <button :disabled="zoom >= 2" @click="zoom = Math.min(2, zoom + 0.25)">放大</button>
    </div>
    <div ref="scroller" class="pdf-scroll" tabindex="0" aria-label="PDF 文档页面" @scroll="updateCurrent">
      <div v-if="loading" role="status">正在加载 PDF…</div>
      <div
        v-for="page in count"
        :key="`${generation}-${page}`"
        :ref="(node) => bindPage(page, node)"
        :data-page="page"
        class="pdf-page"
        :style="{ width: `${pageWidth}px`, height: `${heights[page] || estimateHeight}px` }"
        role="img"
        :aria-label="`第 ${page} 页`"
      >
        <span class="pdf-page-text">{{ texts[page] || `第 ${page} 页` }}</span>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type { PDFDocumentProxy, PDFPageProxy, RenderTask } from 'pdfjs-dist';
import { openCurrentPdf } from './currentPdfEngine';
const props = defineProps<{ src: string }>();
const emit = defineEmits<{ (event: 'error', error: unknown): void; (event: 'rendered'): void }>();
const scroller = ref<HTMLElement>();
const count = ref(0),
  currentPage = ref(1),
  zoom = ref(1),
  pageWidth = ref(800),
  estimateHeight = ref(1100),
  loading = ref(false);
const heights = ref<Record<number, number>>({}),
  texts = ref<Record<number, string>>({});
let generation = 0,
  mounted = false,
  rendered = false;
let doc: PDFDocumentProxy | undefined, engine: Awaited<ReturnType<typeof openCurrentPdf>> | undefined;
let abort: AbortController | undefined, observer: IntersectionObserver | undefined, resize: ResizeObserver | undefined;
const nodes = new Map<number, HTMLElement>(),
  visible = new Set<number>();
type PageWork = {
  cancelled: boolean;
  page?: PDFPageProxy;
  task?: RenderTask;
  canvas?: HTMLCanvasElement;
  done?: boolean;
};
const work = new Map<number, PageWork>();
let active = 0;
function release(page: number) {
  const item = work.get(page);
  if (!item) return;
  item.cancelled = true;
  item.task?.cancel();
  if (item.canvas) {
    item.canvas.width = 0;
    item.canvas.height = 0;
    item.canvas.remove();
  }
  if (item.done) item.page?.cleanup();
  work.delete(page);
}
function clear() {
  generation++;
  abort?.abort();
  abort = undefined;
  observer?.disconnect();
  observer = undefined;
  resize?.disconnect();
  resize = undefined;
  for (const page of work.keys()) release(page);
  visible.clear();
  nodes.clear();
  active = 0;
  void engine?.close();
  engine = undefined;
  doc = undefined;
  count.value = 0;
  currentPage.value = 1;
  heights.value = {};
  texts.value = {};
  loading.value = false;
  rendered = false;
}
function fail(error: unknown, ticket: number) {
  if (ticket !== generation) return;
  clear();
  emit('error', error);
}
function bindPage(page: number, value: unknown) {
  if (!(value instanceof HTMLElement)) {
    const old = nodes.get(page);
    if (old) observer?.unobserve(old);
    nodes.delete(page);
    return;
  }
  if (nodes.get(page) === value) return;
  nodes.set(page, value);
  observer?.observe(value);
}
function pump() {
  if (!doc) return;
  for (const page of visible) {
    if (active >= 2) break;
    if (!work.has(page) && nodes.has(page)) void renderPage(page);
  }
}
async function renderPage(number: number) {
  const current = doc!,
    ticket = generation,
    item: PageWork = { cancelled: false };
  work.set(number, item);
  active++;
  const valid = () => ticket === generation && !item.cancelled && current === doc;
  try {
    const page = await current.getPage(number);
    item.page = page;
    if (!valid()) return;
    const base = page.getViewport({ scale: 1 });
    const viewport = page.getViewport({ scale: pageWidth.value / base.width });
    heights.value[number] = viewport.height;
    const canvas = document.createElement('canvas');
    item.canvas = canvas;
    const ratio = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = Math.ceil(viewport.width * ratio);
    canvas.height = Math.ceil(viewport.height * ratio);
    canvas.style.width = '100%';
    canvas.style.height = '100%';
    canvas.setAttribute('aria-hidden', 'true');
    nodes.get(number)?.appendChild(canvas);
    item.task = page.render({ canvas, viewport, transform: ratio === 1 ? undefined : [ratio, 0, 0, ratio, 0, 0] });
    await item.task.promise;
    if (!valid()) return;
    const text = await page.getTextContent();
    if (!valid()) return;
    texts.value[number] = text.items.map((part) => ('str' in part ? part.str : '')).join(' ');
    item.done = true;
    if (!rendered) {
      rendered = true;
      emit('rendered');
    }
  } catch (error) {
    if (valid()) fail(error, ticket);
  } finally {
    if (!valid()) item.page?.cleanup();
    if (ticket === generation) {
      active--;
      pump();
    }
  }
}
function updateCurrent() {
  const view = scroller.value?.getBoundingClientRect();
  if (!view) return;
  let closest = Infinity;
  for (const page of visible) {
    const rect = nodes.get(page)?.getBoundingClientRect();
    if (!rect || rect.bottom <= view.top || rect.top >= view.bottom) continue;
    const distance = Math.abs(rect.top - view.top);
    if (distance < closest) {
      closest = distance;
      currentPage.value = page;
    }
  }
}
function go(page: number) {
  const node = nodes.get(page),
    root = scroller.value;
  if (!node || !root) return;
  root.scrollTop += node.getBoundingClientRect().top - root.getBoundingClientRect().top;
  currentPage.value = page;
}
function fit() {
  const width = Math.max(100, (scroller.value?.clientWidth || 832) - 32) * zoom.value;
  if (Math.abs(width - pageWidth.value) < 1) return;
  const ratio = width / pageWidth.value;
  pageWidth.value = width;
  estimateHeight.value *= ratio;
  heights.value = Object.fromEntries(Object.entries(heights.value).map(([page, height]) => [page, height * ratio]));
  for (const page of work.keys()) release(page);
  pump();
}
async function load() {
  clear();
  if (!mounted || !props.src) return;
  const ticket = generation,
    controller = new AbortController();
  abort = controller;
  loading.value = true;
  try {
    const session = await openCurrentPdf(props.src, controller.signal);
    if (ticket !== generation) {
      void session.close();
      return;
    }
    engine = session;
    const pdf = await session.promise;
    if (ticket !== generation) return;
    doc = pdf;
    const first = await pdf.getPage(1);
    if (ticket !== generation) return;
    const base = first.getViewport({ scale: 1 });
    pageWidth.value = Math.max(100, (scroller.value?.clientWidth || 832) - 32) * zoom.value;
    estimateHeight.value = (base.height / base.width) * pageWidth.value;
    observer = new IntersectionObserver(
      (entries) => {
        if (ticket !== generation) return;
        for (const entry of entries) {
          const page = Number((entry.target as HTMLElement).dataset.page);
          if (entry.isIntersecting) visible.add(page);
          else {
            visible.delete(page);
            release(page);
          }
        }
        updateCurrent();
        pump();
      },
      { root: scroller.value, rootMargin: '400px 0px' },
    );
    count.value = pdf.numPages;
    loading.value = false;
    await nextTick();
    if (ticket !== generation) return;
    resize = new ResizeObserver(fit);
    if (scroller.value) resize.observe(scroller.value);
  } catch (error) {
    if (!controller.signal.aborted) fail(error, ticket);
  }
}
watch(
  () => props.src,
  () => {
    if (mounted) void load();
  },
  { flush: 'sync' },
);
watch(zoom, fit, { flush: 'post' });
onMounted(() => {
  mounted = true;
  void load();
});
onBeforeUnmount(() => {
  mounted = false;
  clear();
});
</script>
<style scoped>
.current-pdf-reader {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.pdf-tools {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 6px;
  background: #fff;
}
.pdf-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 12px 16px;
  background: #858585;
}
.pdf-page {
  position: relative;
  margin: 0 auto 12px;
  background: white;
}
.pdf-page-text {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}
</style>
