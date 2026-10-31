<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1200"
    :params="paramsForm"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
    @updateSort="handleUpdateSort"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="所属Portal" path="portalId">
        <h-field-portal-define
          v-model:value="paramsForm.portalId"
          placeholder="请选择所属所属Portal"
        ></h-field-portal-define>
      </n-form-item>
      <n-form-item label="名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="名称"></n-input>
      </n-form-item>
    </template>
    <h-dialog-form
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :disabled="formType === 'view'"
      :refTable="refPageTable"
      :handleSubmit="handleSubmit"
      v-model:show="formModel"
    >
      <n-form-item label="所属Portal" path="portalId">
        <h-field-portal-define v-model:value="form.portalId" placeholder="请选择所属所属Portal"></h-field-portal-define>
      </n-form-item>
      <n-grid x-gap="12">
        <n-form-item-gi label="字段Label" path="name" :span="12">
          <n-input v-model:value="form.name" placeholder="请输入名称"></n-input>
        </n-form-item-gi>
        <n-form-item-gi label="字段Value" path="value" :span="12">
          <n-input v-model:value="form.value" placeholder="请输入值"></n-input>
        </n-form-item-gi>
      </n-grid>
      <n-grid x-gap="12">
        <n-form-item-gi label="字段类型" path="type" :span="12">
          <h-dict-select v-model:value="form.type" placeholder="请选择类型" type="portal_field_type"></h-dict-select>
        </n-form-item-gi>
        <n-form-item-gi v-if="form.type === '11'" label="字典类型" path="dictType" :span="12">
          <HDictTypeSelect v-model:value="form.dictType"></HDictTypeSelect>
        </n-form-item-gi>
      </n-grid>
      <div class="flex justify-start">
        <n-form-item label-align="left" label="查询" path="isQuery" class="mr-3">
          <n-switch v-model:value="form.isQuery"></n-switch>
        </n-form-item>
        <n-form-item label-align="left" label="隐藏" path="isHidden">
          <n-switch v-model:value="form.isHidden"></n-switch>
        </n-form-item>
      </div>
    </h-dialog-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { add, del, edit, list, updateSort } from '../../api/portalfield';
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { HFieldPortalDefine } from '../../components/form/field';

const columns = [
  { key: 'name', title: '字段名称', columnType: 'title', fixed: 'left', rowDrag: true, resizable: true },
  { key: 'portal.name', title: '归属Portal', resizable: true },
  { key: 'type', title: '类型', dictType: 'portal_field_type' },
  { key: 'value', title: '字段Field' },
  { key: 'dictType', title: '字段字典' },
  { key: 'isQuery', title: '是否查询', dictType: 'sys_ok', width: '100px' },
  { key: 'isHidden', title: '是否隐藏', dictType: 'sys_ok', width: '100px' },
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
  portalId: '',
  name: '',
  value: '',
  type: '',
  dictType: '',
  isQuery: false,
  isHidden: false,
  sort: 10,
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
  value: {
    required: true,
    message: '请输入值',
  },
  portalId: {
    required: true,
    message: '请选择Portal',
  },
  type: {
    required: true,
    message: '请输入类型',
  },
  sort: {
    required: true,
    message: '请输入排序',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  portalId: '',
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
  Object.assign(form.value, row);
  formTitle.value = `编辑「${row.name}」`;
  formModel.value = true;
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view';
  Object.assign(form.value, row);
  formTitle.value = `查看「${row.name}」`;
  formModel.value = true;
}

function handleUpdateSort(items: any) {
  let sorts = items.map((item: any, index: number) => {
    return { id: item.id, sort: index };
  });
  updateSort(sorts).finally(() => {
    refPageTable.value?.handleRefresh();
  });
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
