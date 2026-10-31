<template>
  <HPageDataTable auto-scroll-x
    :columns="columns"
    :fetch-data="fetchCategories"
    :params="queryParams"
    :pagination="false"
    :is-add="false"
    :is-edit="false"
    :is-copy="false"
    :is-delete="false"
    :enable-drag="false"
    row-key-field="id"
    @row-view="onView"
  >
    <template #queryForm>
      <NFormItem label="检索内容">
        <NInput v-model:value="queryParams.keyword" placeholder="分类名称 / 规范序号" clearable />
      </NFormItem>
    </template>
    <HPageForm content-only :enable-cancel="false" v-model:show="viewShow" title="分类详情">
      <NDescriptions v-if="viewRow" label-placement="left" :column="1" bordered size="small">
        <NDescriptionsItem label="分类名称">{{ viewRow.name }}</NDescriptionsItem>
        <NDescriptionsItem label="规范序号">{{ viewRow.code }}</NDescriptionsItem>
        <NDescriptionsItem label="依据">WW/T 0017—2013 附录 D 表 D.1</NDescriptionsItem>
      </NDescriptions>
    </HPageForm>
  </HPageDataTable>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { categoryApi } from '../api/category';
import type { CategoryVo } from '../typings/collection';

const queryParams = reactive({ keyword: null as string | null });
/** 只读：取 /collection/category 全量列表（COLLECTION_SELECT 即可），检索在前端按名称/规范序号过滤。 */
async function fetchCategories(params: { keyword?: string | null }) {
  const resp: any = await categoryApi.list();
  const list: CategoryVo[] = resp?.data?.list ?? resp?.data ?? [];
  const keyword = String(params?.keyword ?? '').trim().toLowerCase();
  const rows = keyword
    ? list.filter((row) => [row.name, row.code].some((value) => String(value ?? '').toLowerCase().includes(keyword)))
    : list;
  return { data: { list: rows, total: rows.length } };
}
const viewShow = ref(false);
const viewRow = ref<CategoryVo | null>(null);
const columns = [
  { title: '规范序号', key: 'code', width: 110 },
  { resizable: true, title: '分类名称', key: 'name', minWidth: 240, columnType: 'title' },
];
function onView(row: CategoryVo) {
  viewRow.value = row;
  viewShow.value = true;
}
</script>
