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
    :isTree="true"
  >
    <template #queryForm>
      <n-form-item label="名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入name" clearable></n-input>
      </n-form-item>
    </template>
    <h-page-form
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :refTable="refPageTable"
      :disabled="formType === 'view'"
      :handleSubmit="handleSubmit"
      v-model:show="formModel"
    >
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入分类名称" clearable></n-input>
          </n-form-item>
          <n-form-item label="父级分类名称" path="parentId">
            <n-input v-model:value="form.parentId" placeholder="请输入父级分类名称" clearable></n-input>
          </n-form-item>
          <n-form-item label="排序" path="sort">
            <n-input-number v-model:value="form.sort" placeholder="请输入排序" clearable></n-input-number>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="分类代码" path="code">
            <n-input v-model:value="form.code" placeholder="请输入分类代码" clearable></n-input>
          </n-form-item>
          <n-form-item label="分类层级" path="level">
            <n-input v-model:value="form.level" placeholder="请输入分类层级" clearable></n-input>
          </n-form-item>
          <n-form-item label="是否系统分类" path="isSystem">
            <h-dict-select v-model:value="form.isSystem" placeholder="请选择是否系统分类" type="is_isRecommended" clearable></h-dict-select>
          </n-form-item>
        </n-gi>
      </n-grid>

      <n-form-item label="描述" path="description">
        <n-input v-model:value="form.description" placeholder="请输入描述" type="textarea" clearable></n-input>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get ,treeList} from '../../api/laws/regulationCategory';
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';

const columns = [
  { key: 'name', title: '分类名称', columnType: 'title', width: '200px', resizable: true, ellipsis: { tooltip: true } },
  { key: 'code', title: '分类代码',width:'200px' },
  { key: 'level', title: '分类层级',width:'200px' },



];

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
/**
 * 表格ref
 * */
const refPageTable = ref();

const formTitle = ref();

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  name: '',
  code: '',
  parentId: '',
  level: '',
  sort: null,
  isSystem: null,
  description: '',
  createTime: '',
  createBy: '',
  updateTime: '',
  updateBy: '',
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
    message: '请输入name',
  },

};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
});

const formType = ref('add');

/**
 * 添加
 * */
function handleAdd() {
  formType.value = 'add';
  form.value = cloneDeep(defaultForm);
  formTitle.value = '添加';
  formModel.value = true;
}

/**
 * 编辑事件
 * */
function handleEdit(row: any) {
  formType.value = 'edit';
  form.value = cloneDeep(defaultForm);
  get(row.id).then((resp) => {
    Object.assign(form.value, resp.data);
    formModel.value = true;
  });
  formTitle.value = `编辑「${row.name}」`;
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view';
  Object.assign(form.value, row);
  get(row.id).then((resp) => {
    Object.assign(form.value, resp.data);
    formModel.value = true;
  });
  formTitle.value = `查看「${row.name}」`;
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
</script>
