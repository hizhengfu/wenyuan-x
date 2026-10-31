import { Ref, unref, computed } from 'vue';
import { useAsyncState } from '@vueuse/core';
import { frontType as fetchDictType } from '@/modules/sys/api/dict';

/** 缓存，防止重复请求 */
const dictCache = new Map<string, any[]>();

/** 通用异步获取字典 */
async function loadDict(type: string) {
  if (dictCache.has(type)) {
    return dictCache.get(type)!;
  }
  const res: any = await fetchDictType(type, true);
  dictCache.set(type, res.data);
  return res.data;
}

/**
 * useDict(type) - 自动加载并缓存字典项（响应式）
 */
export function useDict(type: string) {
  const { state: options, isLoading } = useAsyncState(
    async () => await loadDict(type),
    [], // 初始值
    { immediate: true, shallow: false },
  );
  return { options, isLoading };
}

/**
 * useDictLabel(type, value) - 获取响应式字典标签（基于 useDict）
 */
export function useDictLabel(type: string, value: Ref<string | boolean | undefined> | string | boolean | undefined) {
  const { options } = useDict(type);

  const label = computed(() => {
    const opts = unref(options);
    const val = unref(value);

    if (!opts?.length || val === undefined || val === null) return '--';
    if (typeof val === 'boolean') return val ? '是' : '否';

    const values = String(val).split(',');
    const labels = values.map((v) => opts.find((item: any) => item.value == v)?.label).filter(Boolean);
    return labels.length ? labels.join(',') : '--';
  });

  return { label, options };
}

/**
 * 根据字典选项获取 label
 * @param options 字典数组 [{ value, label }]
 * @param value 字符串、数字、布尔值 或 多值逗号分隔
 * @returns 对应的 label 字符串
 */
export function getDictLabel(options: any[], value: string | number | boolean | undefined | null): string {
  if (!options?.length || value === undefined || value === null) return '--';

  if (typeof value === 'boolean') return value ? '是' : '否';

  const values = String(value).split(',');
  const labels = values.map((v) => options.find((item) => item.value == v)?.label).filter(Boolean);

  return labels.length ? labels.join(',') : '--';
}
