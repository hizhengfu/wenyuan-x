<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1200"
    :params="paramsForm"
    :initQuery="false"
    @add="handleAdd"
    @rowEdit="handleEdit"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="名称"></n-input>
      </n-form-item>
    </template>
    <h-dialog-form
      :rules="rules"
      :model="form"
      label-width="auto"
      :refTable="refPageTable"
      :handleSubmit="handleSubmit"
      v-model:show="formModel"
    >
      <n-form-item label="名称：" path="name">
        <n-input v-model:value="form.name" placeholder="请输入名称"></n-input>
      </n-form-item>
      <n-form-item label="字段：" path="columnNames">
        <h-module-column-select
          v-model:value="form.columnNames"
          :tableName="tableId"
          valueField="label"
          multiple
          placeholder="请选择字段"
        >
        </h-module-column-select>
      </n-form-item>
      <n-grid :x-gap="12">
        <n-form-item-gi label="排序：" path="sort" :span="18">
          <n-input-number v-model:value="form.sort" placeholder="排序"></n-input-number>
        </n-form-item-gi>
        <n-form-item-gi label="UNIQUE：" path="unique" :span="6">
          <n-switch v-model:value="form.unique"></n-switch>
        </n-form-item-gi>
      </n-grid>
    </h-dialog-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { HModuleColumnSelect } from '../../components/form/field';
import { list, add, edit, del } from '../../api/moduleIndex';
import { computed, h, nextTick, ref, watch, watchEffect } from 'vue';
import { NButton, NIcon } from 'naive-ui';
import { cloneDeep } from 'lodash-es';
import { RouterLink, useRoute } from 'vue-router';

const route = useRoute();
const columns = [
  { key: 'name', title: '名称' },
  { key: 'columnNames', title: '字段' },
  { key: 'unique', title: 'unique' },
  { key: 'sort', title: '顺序' },
];

const tableId = computed<string>(() => {
  return route.query.tableId as string;
});

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
/**
 * 表格ref
 * */
const refPageTable = ref(null);

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  tableId: tableId.value,
  name: '',
  columnNames: '',
  unique: true,
  sort: 0,
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {
  name: {
    required: true,
    message: '请输入名称',
  },
  columnNames: {
    required: true,
    message: '请选择字段',
  },
  sort: {
    required: true,
    message: '请输入排序序号',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  tableId: '',
});

const formType = ref('add');

/**
 * 添加
 * */
function handleAdd() {
  formType.value = 'add';
  form.value = cloneDeep(defaultForm);
  formModel.value = true;
}

/**
 * 编辑事件
 * */
function handleEdit(row: any) {
  formType.value = 'edit';
  Object.assign(form.value, row);
  formModel.value = true;
}

/**
 * 提交
 * */
function handleSubmit(data: any) {
  if (formType.value === 'add') {
    return add(data);
  } else {
    return edit(data);
  }
}

watch(
  () => route.query.tableId,
  (nTableId) => {
    if (nTableId) {
      paramsForm.value.tableId = nTableId as string;
      nextTick(() => {
        // @ts-ignore
        refPageTable.value?.handleQuery();
      });
    }
  },
  {
    immediate: true,
  },
);
</script>
