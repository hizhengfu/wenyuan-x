<template>
  <n-form
    ref="refQueryForm"
    class="page-query-form"
    :class="{ 'page-query-form-single': !hasMore || queryFormCollapsed }"
    v-bind="$attrs"
  >
    <div ref="refQueryFormFields" class="page-query-form-fields">
      <AdvanceQueryFormFields
        :configurable="queryConfigurable"
        :visible-keys="renderedVisibleQueryFieldKeys"
        @update:options="handleQueryFieldOptions"
      >
        <slot></slot>
      </AdvanceQueryFormFields>
    </div>
    <div
      ref="refQueryFormOperate"
      class="page-query-form-operate"
      :class="{ 'page-query-form-operate-inline': operateInline }"
    >
      <slot name="queryFormOperate"></slot>
      <slot name="queryFormAppend"></slot>
      <n-form-item v-if="queryConfigurable && querySettingOptions.length > 1" class="page-query-form-settings">
        <AdvanceQueryFormSettings
          :options="querySettingOptions"
          :value="effectiveVisibleQueryFieldKeys"
          @update:value="handleVisibleQueryFieldKeys"
          @reset="resetQuerySettings"
        />
      </n-form-item>
      <n-form-item class="page-query-form-more-toggle" :class="{ 'page-query-form-more-toggle-hidden': !hasMore }">
        <n-button type="primary" class="text-btn" text :aria-expanded="!queryFormCollapsed" @click="handleToggleMore">
          <template #icon>
            <n-icon size="20px">
              <IconMdiMenuDown v-if="queryFormCollapsed" />
              <IconMdiMenuUp v-else />
            </n-icon>
          </template>
          {{ queryFormCollapsed ? '展开' : '收缩' }}
        </n-button>
      </n-form-item>
    </div>
  </n-form>
</template>
<script lang="ts" setup>
import { useStorage } from '@vueuse/core';
import { computed, nextTick, onMounted, onUnmounted, onUpdated, ref, watch } from 'vue';
import AdvanceQueryFormFields from './AdvanceQueryFormFields';
import AdvanceQueryFormSettings from './AdvanceQueryFormSettings.vue';
import { canPlaceQueryFormOperateInline, findWrappedQueryFormItems } from './advanceQueryFormLayout';
import {
  defaultVisibleQueryKeys as resolveDefaultVisibleQueryKeys,
  mergeQuerySettingOptions,
  normalizeVisibleQueryKeys,
  parseStoredVisibleQueryKeys,
  type AdvanceQueryFormConfiguredOption,
  type AdvanceQueryFormDiscoveredOption,
  type AdvanceQueryFormVisibilityChange,
} from './advanceQueryFormSettings';

const props = withDefaults(
  defineProps<{
    storageKey?: string;
    queryConfigurable?: boolean;
    queryConfigKey?: string;
    queryConfigOptions?: AdvanceQueryFormConfiguredOption[];
    defaultVisibleQueryKeys?: Array<string | number>;
    alwaysVisibleQueryKeys?: Array<string | number>;
  }>(),
  {
    queryConfigurable: false,
    queryConfigOptions: () => [],
    defaultVisibleQueryKeys: () => [],
    alwaysVisibleQueryKeys: () => [],
  },
);

const emit = defineEmits<{
  'query-field-visibility-change': [payload: AdvanceQueryFormVisibilityChange];
}>();

const COLLAPSED_ITEM_CLASS = 'page-query-form-item-collapsed';
const STORAGE_KEY_PREFIX = '__APP_ADVANCE_QUERY_FORM_TOGGLE_MORE__';

/**
 * 查询表单
 */
const refQueryForm = ref<any>();
const refQueryFormFields = ref<HTMLElement>();
const refQueryFormOperate = ref<HTMLElement>();

/**
 * 同一路由内保持相同的收缩状态，避免不同页面互相影响。
 */
function resolveStorageKey() {
  if (props.storageKey) return props.storageKey;
  if (typeof window === 'undefined') return STORAGE_KEY_PREFIX;
  const routePath = window.location.hash.replace(/^#/, '').split('?')[0] || window.location.pathname || 'default';
  return `${STORAGE_KEY_PREFIX}:${routePath}`;
}

/**
 * true 表示收缩，只展示第一行查询项。
 */
const queryFormCollapsed = useStorage(resolveStorageKey(), false);
const hasMore = ref(false);
const operateInline = ref(false);
const discoveredQueryOptions = ref<AdvanceQueryFormDiscoveredOption[]>([]);
const visibleQueryFieldKeys = ref<string[]>([]);
const queryFieldsInitialized = ref(false);
const knownQueryOptionKeys = ref<string[]>([]);

const querySettingOptions = computed(() =>
  mergeQuerySettingOptions(
    discoveredQueryOptions.value,
    props.queryConfigOptions,
    props.alwaysVisibleQueryKeys.map(String),
  ),
);
const defaultQueryFieldKeys = computed(() =>
  resolveDefaultVisibleQueryKeys(querySettingOptions.value, props.defaultVisibleQueryKeys.map(String)),
);
const effectiveVisibleQueryFieldKeys = computed(() => {
  const selected = normalizeVisibleQueryKeys(
    visibleQueryFieldKeys.value,
    querySettingOptions.value,
    defaultQueryFieldKeys.value,
  );
  const selectedSet = new Set(selected);
  return querySettingOptions.value
    .filter((option) => selectedSet.has(option.value) || option.disabled)
    .map((option) => option.value);
});
const renderedVisibleQueryFieldKeys = computed(() =>
  props.queryConfigurable && queryFieldsInitialized.value ? effectiveVisibleQueryFieldKeys.value : undefined,
);

let animationFrameId: number | undefined;
let measureScheduled = false;
let unmounted = false;
let resizeObserver: ResizeObserver | undefined;
let mutationObserver: MutationObserver | undefined;
const observedWidths = new WeakMap<Element, number>();

function readStoredQueryFieldKeys() {
  const defaults = defaultQueryFieldKeys.value;
  if (!props.queryConfigKey || typeof localStorage === 'undefined') {
    return normalizeVisibleQueryKeys(defaults, querySettingOptions.value, defaults);
  }
  return parseStoredVisibleQueryKeys(localStorage.getItem(props.queryConfigKey), querySettingOptions.value, defaults);
}

function writeStoredQueryFieldKeys() {
  if (!props.queryConfigKey || typeof localStorage === 'undefined') return;
  localStorage.setItem(props.queryConfigKey, JSON.stringify(visibleQueryFieldKeys.value));
}

function emitQueryFieldVisibility(
  reason: AdvanceQueryFormVisibilityChange['reason'],
  previousVisibleKeys: readonly string[] = [],
) {
  if (!props.queryConfigurable) return;
  const visibleKeys = effectiveVisibleQueryFieldKeys.value;
  const visible = new Set(visibleKeys);
  const previousVisible = new Set(previousVisibleKeys);
  const hiddenKeys = querySettingOptions.value
    .filter((option) => !visible.has(option.value) && (reason !== 'user' || previousVisible.has(option.value)))
    .map((option) => option.value);
  emit('query-field-visibility-change', {
    visibleKeys,
    hiddenKeys,
    options: querySettingOptions.value,
    reason,
  });
}

function syncQueryFieldSettings(reason: 'initialize' | 'sync', previousVisibleKeys: readonly string[] = []) {
  const options = querySettingOptions.value;
  const optionKeys = options.map((option) => option.value);
  const previousOptions = new Set(knownQueryOptionKeys.value);

  if (!queryFieldsInitialized.value) {
    visibleQueryFieldKeys.value = readStoredQueryFieldKeys();
    queryFieldsInitialized.value = true;
  } else {
    const defaults = new Set(defaultQueryFieldKeys.value);
    const newlyDiscoveredDefaults = optionKeys.filter((key) => !previousOptions.has(key) && defaults.has(key));
    visibleQueryFieldKeys.value = normalizeVisibleQueryKeys(
      [...visibleQueryFieldKeys.value, ...newlyDiscoveredDefaults],
      options,
      defaultQueryFieldKeys.value,
    );
  }

  knownQueryOptionKeys.value = optionKeys;
  writeStoredQueryFieldKeys();
  emitQueryFieldVisibility(reason, previousVisibleKeys);
  requestMeasure();
}

function handleQueryFieldOptions(options: AdvanceQueryFormDiscoveredOption[]) {
  const previousVisibleKeys = effectiveVisibleQueryFieldKeys.value;
  discoveredQueryOptions.value = options;
  syncQueryFieldSettings(queryFieldsInitialized.value ? 'sync' : 'initialize', previousVisibleKeys);
}

function applyVisibleQueryFieldKeys(value: Array<string | number>, preserveDisabledSelection: boolean) {
  const previousVisibleKeys = effectiveVisibleQueryFieldKeys.value;
  const incoming = new Set(value.map(String));
  const previousSelected = new Set(visibleQueryFieldKeys.value);
  const nextSelected = querySettingOptions.value
    .filter((option) =>
      option.disabled && preserveDisabledSelection ? previousSelected.has(option.value) : incoming.has(option.value),
    )
    .map((option) => option.value);

  visibleQueryFieldKeys.value = normalizeVisibleQueryKeys(
    nextSelected,
    querySettingOptions.value,
    defaultQueryFieldKeys.value,
  );
  writeStoredQueryFieldKeys();
  emitQueryFieldVisibility('user', previousVisibleKeys);
  requestMeasure();
}

function handleVisibleQueryFieldKeys(value: Array<string | number>) {
  applyVisibleQueryFieldKeys(value, true);
}

function updateVisibleQueryFieldKeys(value: Array<string | number>) {
  applyVisibleQueryFieldKeys(value, false);
}

function resetQuerySettings() {
  updateVisibleQueryFieldKeys(defaultQueryFieldKeys.value);
}

function clearCollapsedItems() {
  refQueryFormFields.value
    ?.querySelectorAll<HTMLElement>(`.${COLLAPSED_ITEM_CLASS}`)
    .forEach((element) => element.classList.remove(COLLAPSED_ITEM_CLASS));
}

/**
 * 先恢复全部查询项，再以浏览器实际排版后的 offsetTop 判断哪些项进入了下一行。
 */
function measureQueryFormMore() {
  const formElement = refQueryForm.value?.$el as HTMLElement | undefined;
  const fieldsElement = refQueryFormFields.value;
  const operateElement = refQueryFormOperate.value;
  if (!formElement || !fieldsElement || !operateElement || formElement.getBoundingClientRect().width === 0) return;

  clearCollapsedItems();
  const queryItems = Array.from(fieldsElement.children).filter(
    (element): element is HTMLElement => element instanceof HTMLElement,
  );
  const visibleItems = queryItems.filter((element) => element.getClientRects().length > 0);
  if (!visibleItems.length) {
    hasMore.value = false;
    operateInline.value = false;
    return;
  }

  const wrappedItems = findWrappedQueryFormItems(queryItems);
  const firstRowTop = visibleItems[0].offsetTop;

  // 用固定几何边界判断操作区是否能留在首行，避免操作区当前采用的
  // 12px 内联间距或自动边距反过来改变 `hasMore` 并形成循环闪烁。
  const operateFitsFirstRow = canPlaceQueryFormOperateInline(formElement, visibleItems, operateElement, 0);
  hasMore.value = wrappedItems.length > 0 || !operateFitsFirstRow;
  operateInline.value = !hasMore.value && canPlaceQueryFormOperateInline(formElement, visibleItems, operateElement);
  if (!queryFormCollapsed.value || !hasMore.value) return;

  wrappedItems.forEach((element) => element.classList.add(COLLAPSED_ITEM_CLASS));

  // 操作区在同一 flex 流的最后。如果它仍然落到下一行，逐项减少首行字段，
  // 直到“查询 / 展开”能够位于收缩行的末尾；至少保留一个查询项。
  const firstRowItems = visibleItems.filter((element) => !wrappedItems.includes(element));
  while (firstRowItems.length > 1 && operateElement.offsetTop > firstRowTop + 1) {
    firstRowItems.pop()?.classList.add(COLLAPSED_ITEM_CLASS);
  }
}

function requestMeasure() {
  if (measureScheduled || unmounted) return;
  measureScheduled = true;

  nextTick(() => {
    if (unmounted) return;
    const measure = () => {
      animationFrameId = undefined;
      measureScheduled = false;
      measureQueryFormMore();
    };
    if (typeof requestAnimationFrame === 'function') {
      animationFrameId = requestAnimationFrame(measure);
    } else {
      measure();
    }
  });
}

/**
 * 切换查询表单展开收缩
 */
function handleToggleMore() {
  queryFormCollapsed.value = !queryFormCollapsed.value;
  if (!queryFormCollapsed.value) clearCollapsedItems();
  requestMeasure();
}

function observeWidth(element: Element | undefined) {
  if (!element || !resizeObserver) return;
  observedWidths.set(element, element.getBoundingClientRect().width);
  resizeObserver.observe(element);
}

const observedQueryItems = new Set<Element>();

function syncObservedQueryItems() {
  if (!resizeObserver || !refQueryFormFields.value) return;
  const currentItems = new Set(Array.from(refQueryFormFields.value.children));

  observedQueryItems.forEach((element) => {
    if (currentItems.has(element)) return;
    resizeObserver?.unobserve(element);
    observedQueryItems.delete(element);
  });
  currentItems.forEach((element) => {
    if (observedQueryItems.has(element)) return;
    observedQueryItems.add(element);
    observeWidth(element);
  });
}

onMounted(() => {
  const formElement = refQueryForm.value?.$el as HTMLElement | undefined;

  resizeObserver = new ResizeObserver((entries) => {
    const widthChanged = entries.some((entry) => {
      const previousWidth = observedWidths.get(entry.target);
      const currentWidth = entry.contentRect.width;
      observedWidths.set(entry.target, currentWidth);
      return previousWidth === undefined || Math.abs(previousWidth - currentWidth) > 0.5;
    });
    if (widthChanged) requestMeasure();
  });
  observeWidth(formElement);
  observeWidth(refQueryFormOperate.value);
  syncObservedQueryItems();

  mutationObserver = new MutationObserver(() => {
    syncObservedQueryItems();
    requestMeasure();
  });
  if (formElement) {
    mutationObserver.observe(formElement, { childList: true, subtree: true });
  }

  requestMeasure();
});

onUpdated(() => {
  syncObservedQueryItems();
  requestMeasure();
});

watch(
  () => [
    props.queryConfigurable,
    props.queryConfigOptions,
    props.defaultVisibleQueryKeys,
    props.alwaysVisibleQueryKeys,
  ],
  () => {
    if (!queryFieldsInitialized.value) return;
    syncQueryFieldSettings('sync', effectiveVisibleQueryFieldKeys.value);
  },
  { deep: true },
);

watch(
  () => props.queryConfigKey,
  () => {
    if (!queryFieldsInitialized.value) return;
    queryFieldsInitialized.value = false;
    knownQueryOptionKeys.value = [];
    syncQueryFieldSettings('initialize');
  },
);

onUnmounted(() => {
  unmounted = true;
  resizeObserver?.disconnect();
  mutationObserver?.disconnect();
  if (animationFrameId !== undefined && typeof cancelAnimationFrame === 'function') {
    cancelAnimationFrame(animationFrameId);
  }
});

defineExpose({
  updateVisibleQueryFieldKeys,
  resetQuerySettings,
});
</script>
<style lang="scss" scoped>
.page-query-form {
  --query-control-width: 200px;
  --query-range-control-width: 240px;

  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  position: relative;

  :deep(.n-form-item-label) {
    white-space: nowrap;
    flex-shrink: 0;
    min-width: max-content;
  }

  :deep(.n-form-item-blank > .n-input),
  :deep(.n-form-item-blank > .n-radio-group),
  :deep(.n-form-item-blank > .n-tree-select),
  :deep(.n-form-item-blank > .n-cascader),
  :deep(.n-form-item-blank > .n-select),
  :deep(.n-form-item-blank > .n-input-number),
  :deep(.n-form-item-blank > .n-date-picker),
  :deep(.n-form-item-blank .n-input-group > .n-date-picker),
  :deep(.n-form-item-blank .n-input-group > .n-select) {
    width: var(--query-control-width);
    min-width: var(--query-control-width);
    margin-bottom: 15px;
  }

  :deep(.n-form-item-blank > .n-date-picker.n-date-picker--range),
  :deep(.n-form-item-blank .n-input-group > .n-date-picker.n-date-picker--range) {
    width: var(--query-range-control-width);
    min-width: var(--query-range-control-width);
  }

  // 字典选择器由输入组包裹，内层下拉框需遵循外层宽度，避免遮挡下一项标题。
  :deep(.n-form-item-blank .n-input-group > .n-select) {
    min-width: 0;
  }
}

.page-query-form-fields {
  display: contents;
}

.page-query-form-operate {
  display: flex;
  flex: 0 0 auto;
  align-items: flex-start;
  justify-content: flex-end;
  margin-left: auto;

  &.page-query-form-operate-inline {
    margin-left: 12px;
  }
}

.page-query-form-more-toggle-hidden {
  visibility: hidden;
  pointer-events: none;
}

:deep(.page-query-form-item-collapsed) {
  display: none !important;
}
</style>
