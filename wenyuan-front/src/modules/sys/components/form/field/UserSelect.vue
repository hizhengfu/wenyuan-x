<template>
  <n-select
    v-bind="$attrs"
    :options="options"
    remote
    filterable
    :loading="loading"
    :render-label="renderLabel"
    :fallback-option="preserveUnlistedValues ? unlistedOption : false"
    @search="handleSearch"
    value-field="id"
  />
</template>
<script lang="ts" setup>
import { ref, VNodeChild } from 'vue';
import { search } from '../../../api/user';
import { SelectOption } from 'naive-ui';

defineProps<{ preserveUnlistedValues?: boolean }>();

// 仅展示表单已保留的值，不把历史编号加入搜索候选或赋予人员资格。
function unlistedOption(value: string | number): SelectOption {
  return { id: value, value, label: `人员编号 ${value}（请核对原件）` };
}

const options = ref<any[]>([]);
const loading = ref(false);

function renderLabel(option: SelectOption): VNodeChild {
  // 防御：n-select 在 value 未命中 options 时会构造仅含 value 的 ghost option，
  // 这种情况下 name / loginName 都是 undefined，直接拼字符串会得到 "undefined(undefined)"
  const name = (option as any).name;
  const loginName = (option as any).loginName;
  if (name && loginName) return `${name}(${loginName})`;
  if (name) return String(name);
  if (loginName) return String(loginName);
  return (option.label as string) ?? String(option.value ?? '');
}

function handleSearch(query: string) {
  loading.value = true;
  search({ keywords: query })
    .then((res) => {
      // 过滤掉 name / loginName 都为空的脏数据，避免下拉里出现空白行
      const list = Array.isArray(res?.data) ? res.data : [];
      options.value = list.filter((u: any) => u && u.id && (u.name || u.loginName));
    })
    .finally(() => {
      loading.value = false;
    });
}

handleSearch('');
</script>
