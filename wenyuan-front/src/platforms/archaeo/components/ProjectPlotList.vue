<template>
  <h-page-data-table
    v-bind="$attrs"
    ref="refPageTable"
    :fetch-data="list"
    :params="paramsForm"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="探方名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入探方名称" clearable></n-input>
      </n-form-item>
    </template>
    <template #queryFormAppend>
      <n-button @click="handleAdd" type="success">
        <n-icon size="20">
          <IconMdiAdd />
        </n-icon>
        添加
      </n-button>
    </template>
    <h-dialog-form
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :refTable="refPageTable"
      :disabled="formType === 'view'"
      :handleSubmit="handleSubmit"
      v-model:show="formModel"
    >
      <n-form-item label="遗址名称" path="projectId" v-if="!projectId">
        <WmsProjectSelect v-model:value="form.projectId" placeholder="请选择遗址名称" clearable></WmsProjectSelect>
      </n-form-item>
      <n-form-item label="探方名称" path="name">
        <n-input v-model:value="form.name" placeholder="请输入探方名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="备注" path="remarks">
        <n-input type="textarea" v-model:value="form.remarks"></n-input>
      </n-form-item>
    </h-dialog-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { add, del, edit, get, list } from '../api/projectPlot.ts';
import { WmsProjectSelect } from './form/field';
import { ref, watch } from 'vue';
import { cloneDeep } from 'lodash-es';
import { NButton, NIcon } from 'naive-ui';

const props = defineProps({ projectId: { type: String } });

const columns = [
  {
    key: 'name',
    title: '探方名称',
    columnType: 'title',
    resizable: true,
    ellipsis: true,
  },
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
  projectId: props.projectId,
  image: '',
  code: '',
  remarks: '',
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
    message: '请输入探方名称',
  },
  projectId: {
    required: true,
    message: '请选择遗址名称',
  },
  createTime: {
    required: true,
    message: '请输入创建时间',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  projectId: props.projectId,
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

const emits = defineEmits(['update']);

/**
 * 提交
 * */
function handleSubmit(data: any) {
  if (formType.value === 'add') {
    return add(data).finally(() => {
      emits('update');
    });
  } else {
    return edit(data).finally(() => {
      emits('update');
    });
  }
}
</script>
