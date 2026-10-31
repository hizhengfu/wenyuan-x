<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1200"
    :params="paramsForm"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
    @loadData="handleLoad"
    :funApiDelete="del"
    v-bind="$attrs"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="序号" path="serialNumber">
        <n-input v-model:value="paramsForm.serialNumber" placeholder="请输入序号" clearable></n-input>
      </n-form-item>
      <n-form-item label="总登记号" path="no">
        <n-input v-model:value="paramsForm.no" placeholder="请输入总登记号" clearable></n-input>
      </n-form-item>
      <n-form-item label="名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="年代" path="year">
        <n-input v-model:value="paramsForm.year" placeholder="请输入年代" clearable></n-input>
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
      <n-form-item label="四有档案" path="fourCulturalRecordId">
        <CulturalRecordSelect
          disabled
          v-model:value="form.fourCulturalRecordId"
          placeholder="请选择四有档案"
          clearable
        ></CulturalRecordSelect>
      </n-form-item>
      <n-form-item label="名称" path="name">
        <n-input v-model:value="form.name" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
      <n-grid x-gap="10">
        <n-gi span="8">
          <n-form-item label="序号" path="serialNumber">
            <n-input-number v-model:value="form.serialNumber" placeholder="请输入序号" clearable></n-input-number>
          </n-form-item>
          <n-form-item label="数量" path="quantity">
            <n-input v-model:value="form.quantity" placeholder="请输入数量" clearable></n-input>
          </n-form-item>
          <n-form-item label="完残状况" path="wccd">
            <n-input v-model:value="form.wccd" placeholder="请输入完残状况" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="8">
          <n-form-item label="总登记号" path="no">
            <n-input v-model:value="form.no" placeholder="请输入总登记号" clearable></n-input>
          </n-form-item>
          <n-form-item label="质地" path="grain">
            <n-input v-model:value="form.grain" placeholder="请输入质地" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="8">
          <n-form-item label="年代" path="year">
            <n-input v-model:value="form.year" placeholder="请输入年代" clearable></n-input>
          </n-form-item>
          <n-form-item label="来源" path="orgin">
            <n-input v-model:value="form.orgin" placeholder="请输入来源" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="备注" path="remarks">
        <n-input v-model:value="form.remarks" type="textarea"></n-input>
      </n-form-item>
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
      <div class="page-detail" ref="refDetail">
        <h2 class="text-center">重要文物登记表</h2>
        <div style="position: absolute; right: 10px; top: 10px">
          <Print :printable="refDetail" size="large"></Print>
        </div>
        <n-table class="info-table" :single-line="false">
          <tr>
            <td>四有档案</td>
            <td colSpan="8">{{ form.fourCulturalRecordFourCulturalRecord.name || '--' }}</td>
          </tr>
          <tr>
            <td>名称</td>
            <td colSpan="8">{{ form.name || '--' }}</td>
          </tr>
          <tr>
            <td>序号</td>
            <td colSpan="3">{{ form.serialNumber || '--' }}</td>
            <td>总登记号</td>
            <td colSpan="4">{{ form.no || '--' }}</td>
          </tr>
          <tr>
            <td>年代</td>
            <td colSpan="3">{{ form.year || '--' }}</td>
            <td>质地</td>
            <td colSpan="4">{{ form.grain || '--' }}</td>
          </tr>
          <tr>
            <td>来源</td>
            <td colSpan="3">{{ form.orgin || '--' }}</td>
            <td>数量</td>
            <td colSpan="4">{{ form.quantity || '--' }}</td>
          </tr>
          <tr>
            <td>完残状况</td>
            <td colSpan="8">{{ form.wccd || '--' }}</td>
          </tr>
          <tr>
            <td>备注</td>
            <td colSpan="8">{{ form.remarks || '--' }}</td>
          </tr>
        </n-table>
      </div>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../../api/four/fourCulturalRecordMainText14';
import { onMounted, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import CulturalRecordSelect from '../CulturalRecordSelect.vue';
import Print from '@/components/basic/print/Print.vue';

const columns = [
  { key: 'serialNumber', title: '序号', titleAlign: 'center', width: '120px', ellipsis: { tooltip: true } },
  { key: 'no', title: '总登记号', titleAlign: 'center', width: '120px', ellipsis: { tooltip: true } },
  {
    key: 'name',
    title: '名称',
    columnType: 'title',
    ellipsis: { tooltip: { style: { maxWidth: '600px' } } },
    width: '200px',
    titleAlign: 'center',
  },
  { key: 'year', title: '年代', titleAlign: 'center', ellipsis: { tooltip: true } },
  { key: 'grain', title: '质地', titleAlign: 'center', ellipsis: { tooltip: true } },
  { key: 'orgin', title: '来源', titleAlign: 'center', ellipsis: { tooltip: true } },
  { key: 'quantity', title: '数量', titleAlign: 'center', ellipsis: { tooltip: true } },
  { key: 'wccd', title: '完残状况', titleAlign: 'center', ellipsis: { tooltip: true } },
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
const props = defineProps({ fourCulturalRecordId: { type: String, required: true } });

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  fourCulturalRecordId: props.fourCulturalRecordId,
  serialNumber: null,
  no: '',
  name: '',
  year: '',
  grain: '',
  orgin: '',
  quantity: '',
  address: '',
  wccd: '',
  remarks: '',
  createBy: '',
  createTime: null,
  updateBy: '',
  updateTime: null,
  fourCulturalRecordFourCulturalRecord: { name: '' },
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {
  fourCulturalRecordId: {
    required: true,
    message: '请选择四有档案',
  },
  name: {
    required: true,
    message: '请输入名称',
  },
};

/**
 * 查询表单
 * */
const paramsForm = ref({
  fourCulturalRecordId: props.fourCulturalRecordId,
  serialNumber: '',
  no: '',
  year: '',
  name: '',
});

const formType = ref('add');
const refDetail = ref();
const detailModel = ref(false);

function handleLoad(resp: any) {
  if (resp.data && resp.data.total === 1) {
    handleView(resp.data.list[0]);
  }
}

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
<style lang="scss" scoped>
.page-detail {
  table {
    table-layout: fixed;

    th {
      white-space: normal;
    }
  }
}
</style>
