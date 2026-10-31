<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    isTree
    :scroll-x="1200"
    :enableDrag="!!paramsForm.type"
    :params="paramsForm"
    @updateSort="handleUpdateSort"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item v-if="!type" label="字典类型" path="type" style="width: 300px">
        <dict-type-select
          v-model:value="paramsForm.type"
          filterable
          clearable
          placeholder="请选择字典类型"
        ></dict-type-select>
      </n-form-item>
      <n-form-item label="LABEL" path="label">
        <n-input v-model:value="paramsForm.label" clearable placeholder="请输入名称"></n-input>
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
    <h-page-form
      :rules="rules"
      :model="form"
      label-width="auto"
      autocomplete="off"
      :title="formTitle"
      :disabled="formType === 'view'"
      :refTable="refPageTable"
      :handleSubmit="handleSubmit"
      v-model:show="formModel"
    >
      <n-form-item label="字典类型" v-if="!type" path="type">
        <dict-type-select
          v-model:value="form.type"
          filterable
          clearable
          placeholder="请选择字典类型"
        ></dict-type-select>
      </n-form-item>
      <n-form-item v-if="isTree && form.type" label="父亲字典" path="parentId">
        <DictTreeSelect
          v-model:value="form.parentId"
          :type="form.type"
          keyField="id"
          filterable
          clearable
          placeholder="请选择父级别字典"
        ></DictTreeSelect>
      </n-form-item>
      <n-form-item label="标签" path="label">
        <n-input v-model:value="form.label" placeholder="请输入名称"></n-input>
      </n-form-item>
      <n-form-item label="值" path="value">
        <n-input v-model:value="form.value" placeholder="请输入值"></n-input>
      </n-form-item>
      <n-form-item label="备注" path="remarks">
        <n-input type="textarea" v-model:value="form.remarks"></n-input>
      </n-form-item>
      <n-form-item label="启用状态" path="enabled">
        <n-switch v-model:value="form.enabled"></n-switch>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script setup lang="ts">
import { add, del, edit, list, updateSort } from '@/modules/sys/api/dict.ts';
import { DictTreeSelect, DictTypeSelect } from './index';
import { NButton, NIcon, NSwitch } from 'naive-ui';
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';

const props = defineProps({ type: { type: String }, isTree: { type: Boolean, default: false } });

const columns = [
  {
    key: 'label',
    title: 'LABEL',
    columnType: 'title',
    rowDrag: true,
    fixed: 'left',
    width: '160px',
    resizable: true,
    ellipsis: true,
  },
  { key: 'value', title: 'VALUE', width: '80px', resizable: true, ellipsis: true },
  { key: 'type', title: '类型', width: '120px', resizable: true, ellipsis: true },
  { key: 'sort', title: '排序', width: '100px', align: 'right' },
  { key: 'isSys', title: '系统字典', dictType: 'sys_ok', width: '100px', align: 'center' },
  {
    key: 'operate',
    type: 'operate',
    fixed: 'right',
    title: '操作',
    align: 'center',
    width: '100px',
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

const formType = ref('add');

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  parentId: '',
  type: '',
  label: '',
  value: '',
  enabled: true,
  sort: 0,
  remarks: '',
};
/**
 * 验证规则
 * */
const rules = {
  type: {
    required: true,
    message: '请选择类型',
  },
  label: {
    required: true,
    message: '请输入标签',
  },
  value: {
    required: true,
    message: '请输入值',
  },
  enabled: {
    required: true,
    message: '请选择状态',
  },
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 查询表单
 * */
const paramsForm = ref({
  type: props.type,
  label: '',
});

/**
 * 添加
 * */
function handleAdd() {
  formType.value = 'add';
  form.value = cloneDeep(defaultForm);
  if (paramsForm.value.type) {
    form.value.type = paramsForm.value.type;
  }
  formTitle.value = '添加';
  formModel.value = true;
}

/**
 * 编辑事件
 * */
function handleEdit(row: any) {
  formType.value = 'edit';
  form.value = cloneDeep(defaultForm);
  Object.assign(form.value, row);
  formTitle.value = `编辑「${row.label}」`;
  formModel.value = true;
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view';
  Object.assign(form.value, row);
  formTitle.value = `查看「${row.label}」`;
  formModel.value = true;
}

function handleUpdateSort(items: any) {
  let sorts = items.map((item: any, index: number) => {
    return { id: item.id, sort: index };
  });
  updateSort(sorts).finally(() => {
    refPageTable.value.handleRefresh();
  });
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
