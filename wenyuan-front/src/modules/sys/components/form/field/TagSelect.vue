<template>
  <n-select
    v-bind="$attrs"
    :value="props.value"
    :options="groupedOptions"
    :loading="loading"
    multiple
    filterable
    clearable
    label-field="name"
    value-field="id"
    placeholder="请选择标签"
    :render-label="renderLabel"
    :render-tag="renderTag"
    @update:value="(v: string[]) => emit('update:value', v)"
  />
</template>

<script lang="ts" setup>
import { ref, h, watch, computed, VNodeChild, onMounted } from 'vue';
import { NTag, SelectOption, type SelectRenderTag } from 'naive-ui';
import { list as fetchTagList, getTagsOf } from '../../../api/tag';

const props = defineProps<{
  /** v-model:value 标签 ID 数组 */
  value?: string[];
  /** 业务命名空间，约定大写（如 CMS_ARTICLE / USER） */
  type: string;
  /** 可选：传入 contentId 时启动后自动加载该对象已选标签（用于详情/编辑回显） */
  contentId?: string;
  /** 可选：按标签分类 ID 过滤候选项；不传则全部加载并按 categoryName 自动分组显示 */
  categoryId?: string;
}>();

const emit = defineEmits<{
  (e: 'update:value', value: string[]): void;
}>();

const rawOptions = ref<any[]>([]);
const loading = ref(false);

function extractList(data: any): any[] {
  if (Array.isArray(data)) return data;
  return Array.isArray(data?.list) ? data.list : [];
}

/**
 * 当传入 categoryId 时返回扁平选项；否则按 categoryName 字段分组显示。
 * 「未分类」作为兜底分组。
 * */
const groupedOptions = computed<SelectOption[]>(() => {
  if (props.categoryId) {
    return rawOptions.value;
  }
  const groups = new Map<string, any[]>();
  rawOptions.value.forEach((t) => {
    const key = t.categoryName?.trim() || '未分类';
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key)!.push(t);
  });
  return Array.from(groups.entries()).map(([cat, items]) => ({
    type: 'group',
    label: cat,
    key: `__group_${cat}`,
    children: items,
  }));
});

function loadOptions() {
  loading.value = true;
  fetchTagList({
    pageSize: -1,
    categoryId: props.categoryId || undefined,
  })
    .then((res) => {
      rawOptions.value = extractList(res.data);
    })
    .finally(() => {
      loading.value = false;
    });
}

function loadSelected() {
  if (!props.contentId) return;
  getTagsOf({ contentId: props.contentId, type: props.type }).then((res) => {
    const ids = extractList(res.data)
      .map((t: any) => t.id)
      .filter(Boolean);
    emit('update:value', ids);
  });
}

function renderLabel(option: SelectOption): VNodeChild {
  if ((option as any).type === 'group') return option.label as string;
  const color = (option as any).color as string | undefined;
  return h('div', { style: 'display:flex;align-items:center;gap:6px' }, [
    color
      ? h('span', {
          style: `display:inline-block;width:8px;height:8px;border-radius:50%;background:${color}`,
        })
      : null,
    h('span', null, option.name as string),
  ]);
}

const renderTag: SelectRenderTag = ({ option, handleClose }) => {
  const color = (option as any).color as string | undefined;
  return h(
    NTag,
    {
      closable: true,
      color: color ? { color, textColor: '#fff', borderColor: color } : undefined,
      style: 'margin: 2px 4px 2px 0',
      onClose: (e: MouseEvent) => {
        e.stopPropagation();
        handleClose();
      },
    },
    { default: () => option.name as string },
  );
};

onMounted(() => {
  loadOptions();
  loadSelected();
});

watch(
  () => props.categoryId,
  () => loadOptions(),
);
watch(
  () => props.contentId,
  () => loadSelected(),
);
</script>
