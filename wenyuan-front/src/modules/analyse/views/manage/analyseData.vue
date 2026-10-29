<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1200"
    :params="paramsForm"
    @add="handleAdd"
    @rowView="handleView"
    @rowEdit="handleEdit"
    @updateSort="handleUpdateSort"
    :funApiDelete="del"
    :funApiCopy="copy"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="编码" path="code">
        <n-input v-model:value="paramsForm.code" clearable></n-input>
      </n-form-item>
      <n-form-item label="时间类型" path="type">
        <h-dict-select
          v-model:value="paramsForm.type"
          type="analyse_time_type"
          clearable
          placeholder="请选择时间类型"
        ></h-dict-select>
      </n-form-item>
      <n-form-item label="数据时间" path="dateTime">
        <n-date-picker v-model:value="paramsForm.dateTime" clearable />
      </n-form-item>
      <n-form-item label="资源ID" path="sourceId">
        <n-input-group>
          <n-input v-model:value="paramsForm.sourceId" placeholder="请输入资源ID" clearable></n-input>
        </n-input-group>
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
      <n-grid :x-gap="6">
        <n-grid-item span="12">
          <n-form-item label="名称" path="name">
            <n-input v-model:value="form.name" placeholder="名称" />
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="12">
          <n-form-item label="时间类型" path="type">
            <h-dict-select
              v-model:value="form.type"
              type="analyse_time_type"
              placeholder="请选择时间类型"
            ></h-dict-select>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-grid :x-gap="6">
        <n-grid-item span="8">
          <n-form-item label="数据定义编码" path="code">
            <HDefineSelect v-model:value="form.code" placeholder="数据定义编码"></HDefineSelect>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="8">
          <n-form-item label="资源ID" path="sourceId">
            <n-input v-model:value="form.sourceId" placeholder="资源ID" />
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="8">
          <n-form-item label="分组" path="group">
            <n-input v-model:value="form.group" placeholder="分组" />
          </n-form-item>
        </n-grid-item>
      </n-grid>

      <n-grid :x-gap="6">
        <n-grid-item span="8">
          <n-form-item label="数据值" path="value">
            <n-input-number v-model:value="form.value" placeholder="数据值" />
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="8">
          <n-form-item label="数据值1" path="value1">
            <n-input-number v-model:value="form.value1" placeholder="数据值1" />
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="8">
          <n-form-item label="数据值2" path="value2">
            <n-input-number v-model:value="form.value2" placeholder="数据值2" />
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-grid :x-gap="6">
        <n-grid-item span="8">
          <n-form-item label="数据值3" path="value3">
            <n-input-number v-model:value="form.value3" placeholder="数据值3" />
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="8">
          <n-form-item label="数据值4" path="value4">
            <n-input-number v-model:value="form.value4" placeholder="数据值4" />
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="8">
          <n-form-item label="数据值5" path="value5">
            <n-input-number v-model:value="form.value5" placeholder="数据值5" />
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-grid :x-gap="6">
        <n-grid-item span="12">
          <n-form-item label="扩展数据" path="extend">
            <n-input v-model:value="form.extend" placeholder="扩展数据" />
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="12">
          <n-form-item label="数据时间" path="dateTime">
            <n-date-picker v-model:value="form.dateTime" clearable placeholder="数据时间" />
          </n-form-item>
        </n-grid-item>
      </n-grid>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { del, page as list, copy, add, edit, updateSort } from '../../api/analyseData';
import { onMounted, ref, watch } from 'vue';
import HDefineSelect from '../../components/form/field/DefineSelect.vue';
import { cloneDeep } from 'lodash-es';
import { useRoute } from 'vue-router';

const columns = [
  { key: 'name', title: '名称', columnType: 'title', fixed: 'left', width: '260px', rowDrag: true },
  { key: 'code', title: '编码', width: '300px' },
  { key: 'type', title: '类型', width: '100px', dictType: 'analyse_time_type', align: 'center' },
  { key: 'dateTime', title: '数据时间', columnType: 'dateTime', width: '220px', align: 'center' },
  { key: 'value', title: '数据值', width: '160px', align: 'right' },
  { key: 'value1', title: '数据值1', width: '160px', align: 'right' },
  { key: 'value2', title: '数据值2', width: '160px', align: 'right' },
  { key: 'value3', title: '数据值3', width: '160px', align: 'right' },
  { key: 'value4', title: '数据值4', width: '160px', align: 'right' },
  { key: 'value5', title: '数据值5', width: '160px', align: 'right' },
  { key: 'sourceId', title: '资源ID', width: '160px', align: 'left', ellipsis: { tooltip: true } },
  { key: 'group', title: '资源群组', width: '160px', align: 'left' },
  { key: 'createTime', title: '创建时间', columnType: 'dateTime', width: '220px', align: 'center' },
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
  type: '',
  sourceId: '',
  value: null,
  value1: null,
  value2: null,
  value3: null,
  value4: null,
  value5: null,
  dateTime: null,
};

/**
 * 表单
 * */
const form: any = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {
  name: {
    required: true,
    message: '请输入名称',
  },
  type: {
    required: true,
    message: '请选择类型',
  },
  code: {
    required: true,
    message: '请输入编码',
  },
  value: {
    required: true,
    message: '请输入数据值',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  type: null,
  code: '',
  sourceId: '',
  dateTime: null,
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

/**
 * 更新排序
 * */
function handleUpdateSort(items: any) {
  let sorts = items.map((item: any, index: number) => {
    return { id: item.id, sort: index };
  });
  updateSort(sorts).finally(() => {
    refPageTable.value.handleQuery();
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

const route = useRoute();

watch(
  () => route.query.code,
  (newValue, oldValue) => {
    if (newValue) {
      paramsForm.value.code = newValue as string;
      refPageTable.value?.handleQuery();
    }
  },
  { immediate: true },
);
</script>
