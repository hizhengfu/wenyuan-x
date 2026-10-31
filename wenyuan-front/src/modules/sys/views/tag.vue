<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1200"
    :params="paramsForm"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="标签名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="标签名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="标签分类" path="categoryId">
        <n-select
          v-model:value="paramsForm.categoryId"
          :options="categoryOptions"
          label-field="name"
          value-field="id"
          placeholder="全部分类"
          clearable
          filterable
        />
      </n-form-item>
    </template>
    <h-page-form
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :disabled="formType === 'view'"
      :refTable="refPageTable"
      :handleSubmit="handleSubmit"
      v-model:show="formModel"
    >
      <n-form-item label="名称" path="name">
        <n-input v-model:value="form.name" placeholder="请输入名称"></n-input>
      </n-form-item>
      <n-form-item label="分类" path="categoryId">
        <n-select
          v-model:value="form.categoryId"
          :options="categoryOptions"
          label-field="name"
          value-field="id"
          placeholder="请选择分类"
          clearable
          filterable
        />
      </n-form-item>
      <n-form-item label="颜色" path="color">
        <n-color-picker v-model:value="form.color"></n-color-picker>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { add, del, edit, list } from '../api/tag';
import { all as fetchCategories } from '../api/tagCategory';
import { onMounted, ref } from 'vue';
import { cloneDeep } from 'lodash-es';

const columns = [
  {
    key: 'name',
    title: '名称',
    columnType: 'title',
    width: '180px',
    fixed: 'left',
    resizable: true,
    ellipsis: true,
  },
  { key: 'categoryName', title: '分类', width: '160px', resizable: true, ellipsis: true },
  { key: 'createTime', title: '创建时间', columnType: 'dateTime', width: '220px', align: 'center' },
];

/** 编辑表单相关 */
const formModel = ref(false);
/** 表格 ref */
const refPageTable = ref(null);

const formTitle = ref();

/** 表单默认值 */
const defaultForm = {
  id: '',
  name: '',
  categoryId: null as string | null,
  color: '',
};

/** 表单 */
const form = ref(cloneDeep(defaultForm));

/** 验证规则 */
const rules = {
  name: {
    required: true,
    message: '请输入名称',
  },
};

/** 查询表单 */
const paramsForm = ref({
  name: '',
  categoryId: null as string | null,
});

/** 分类候选项 */
const categoryOptions = ref<any[]>([]);

const formType = ref('add');

function loadCategories() {
  fetchCategories().then((res) => {
    categoryOptions.value = res.data || [];
  });
}

function handleAdd() {
  formType.value = 'add';
  form.value = cloneDeep(defaultForm);
  formTitle.value = '添加';
  formModel.value = true;
}

function handleEdit(row: any) {
  formType.value = 'edit';
  form.value = cloneDeep(defaultForm);
  Object.assign(form.value, row);
  formTitle.value = `编辑「${row.name}」`;
  formModel.value = true;
}

function handleView(row: any) {
  formType.value = 'view';
  Object.assign(form.value, row);
  formTitle.value = `查看「${row.name}」`;
  formModel.value = true;
}

function handleSubmit(data: any) {
  if (formType.value === 'add') {
    return add(data);
  } else {
    return edit(data);
  }
}

onMounted(() => loadCategories());
</script>
