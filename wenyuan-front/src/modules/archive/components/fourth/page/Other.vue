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
      <n-form-item label="名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入名称" clearable></n-input>
      </n-form-item>

      <n-form-item label="类别" path="type">
        <h-dict-select
          v-model:value="paramsForm.type"
          type="fourth_other_information"
          placeholder="请输入类别"
          clearable
        ></h-dict-select>
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
          <n-form-item label="编号" path="no">
            <n-input v-model:value="form.no" placeholder="请输入编号" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入名称" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>

      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="类别" path="type">
            <h-dict-select v-model:value="form.type" type="fourth_other_information"></h-dict-select>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="数量" path="number">
            <n-input v-model:value="form.number" placeholder="请输入数量" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="保存地点" path="address">
            <n-input v-model:value="form.address" placeholder="请输入保存地点" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="备注" path="remark">
        <n-input type="textarea" v-model:value="form.remark" placeholder="请输入备注" clearable></n-input>
      </n-form-item>
      <h-upload :contentId="form.id" label="上传文件" :disabled="formType === 'view'"></h-upload>
    </h-page-form>
    <h-page-form
      v-model:show="detailModel"
      class="project"
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :ref-table="refPageTable"
      :disabled="formType === 'view'"
      :enable-cancel="false"
    >
      <n-table class="info-table" :single-line="false">
        <tr>
          <td>编号</td>
          <td colspan="2">{{ form.no || '--' }}</td>
          <td>名称</td>
          <td colspan="2">{{ form.name || '--' }}</td>
          <td>类别</td>
          <td colspan="2">
            <h-dict-text type="fourth_other_information" v-model:value="form.type"></h-dict-text>
          </td>
        </tr>
        <tr>
          <td>数量</td>
          <td colspan="3">{{ form.number || '--' }}</td>

          <td>保存地点</td>
          <td colspan="4">{{ form.address || '--' }}</td>
        </tr>
        <tr>
          <td>备注</td>
          <td colspan="8">{{ form.remark || '--' }}</td>
        </tr>
        <tr>
          <td>文件</td>
          <td colspan="8">
            <h-file-list :contentId="form.id"></h-file-list>
          </td>
        </tr>
      </n-table>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../../api/fourth/fourthCulturalRelicOther';
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { useRoute } from 'vue-router';

const route = useRoute();

const props = defineProps({
  fourthCulturalRecordId: {
    type: String,
    required: true,
  },
  title: {
    type: String,
  },
  culturalType: {
    type: Number,
  },
});
const columns = [
  {
    title: '序号',
    key: 'index',
    columnType: 'index',
    width: '80px',
    align: 'center',
    fixed: 'left',
    ellipsis: true,
    resizable: true,
  },
  {
    key: 'no',
    title: '编号',
    ellipsis: true,
    resizable: true,
  },
  { key: 'name', title: '名称', width: '200px', resizable: true, ellipsis: { tooltip: true } },

  { key: 'type', title: '类别' },
  { key: 'number', title: '数量' },
  { key: 'address', title: '保存地点' },
  { key: 'remark', title: '备注' },
];

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
const detailModel = ref(false);
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
  culturalRelicId: route.query.fourthCulturalRecordId || props.fourthCulturalRecordId,
  serialNumber: '',
  name: '',
  no: '',
  type: '',
  number: '',
  address: '',
  remark: '',
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {
  culturalRelicId: {
    required: true,
    message: '请输入cultural_relic_id',
  },
  name: {
    required: true,
    message: '请输入名称',
  },
  no: {
    required: true,
    message: '请输入编号',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  type: '',
  culturalRelicId: route.query.fourthCulturalRecordId || props.fourthCulturalRecordId,
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
    detailModel.value = true;
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
