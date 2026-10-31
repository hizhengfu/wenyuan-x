<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1500"
    :params="paramsForm"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
    :funApiCopy="copy"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="名称" path="name">
        <n-input v-model:value="paramsForm.name" clearable placeholder="名称"></n-input>
      </n-form-item>
      <n-form-item label="编码" path="code">
        <n-input v-model:value="paramsForm.code" clearable placeholder="编码"></n-input>
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
      <n-grid x-gap="5">
        <n-form-item-gi label="名称" path="name" :span="12">
          <n-input v-model:value="form.name" placeholder="请输入名称"></n-input>
        </n-form-item-gi>
        <n-form-item-gi label="编号" path="code" :span="12">
          <n-input v-model:value="form.code" placeholder="请输入编号"></n-input>
        </n-form-item-gi>
      </n-grid>
      <n-grid x-gap="5">
        <n-form-item-gi label="图表类型" path="chartType" :span="8">
          <h-dict-select
            v-model:value="form.chartType"
            clearable
            type="analyse_chart_type"
            placeholder="请选择图表类型"
          ></h-dict-select>
        </n-form-item-gi>
        <n-form-item-gi label="坐标轴类型" path="type" :span="8">
          <h-dict-select
            v-model:value="form.type"
            clearable
            type="analyse_axis_type"
            placeholder="请选择类型"
          ></h-dict-select>
        </n-form-item-gi>
        <n-form-item-gi label="单位" path="unit" :span="8">
          <n-input v-model:value="form.unit" placeholder="请输入单位"></n-input>
        </n-form-item-gi>
      </n-grid>
      <n-form-item label="内容" path="content">
        <n-input type="textarea" v-model:value="form.content" placeholder="请输入内容"></n-input>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { add, copy, del, edit, list } from '../../api/analyseDefine';
import { h, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { RouterLink } from 'vue-router';

const columns = [
  {
    key: 'name',
    title: '名称',
    columnType: 'title',
    width: '260px',
    fixed: 'left',
    resizable: true,
    ellipsis: { tooltip: true },
  },
  { key: 'code', title: '编码', width: '160px', resizable: true, ellipsis: true },
  { key: 'chartType', title: '图表类型', width: '120px', dictType: 'analyse_chart_type' },
  { key: 'type', title: '坐标轴类型', width: '120px', dictType: 'analyse_axis_type' },
  { key: 'unit', title: '数据单位', width: '120px' },
  {
    key: 'createTime',
    title: '数据列表',
    render(row: any, index: number) {
      return [
        h(
          RouterLink,
          { class: 'mr-3', to: { path: './analyseData', query: { code: row.code } } },
          { default: () => '编辑查看数据' },
        ),
      ];
    },
    columnType: 'date',
    width: '220px',
    align: 'center',
  },
  { key: 'content', title: '描述', ellipsis: { tooltip: true, width: '30%' } },
];

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
/**
 * 表格ref
 * */
const refPageTable = ref(null);

const formTitle = ref();

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  name: '',
  code: '',
  type: null,
  chartType: null,
  unit: '',
  content: '',
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
  code: {
    required: true,
    message: '请输入编号',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  code: '',
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
