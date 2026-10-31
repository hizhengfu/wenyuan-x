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
      <n-form-item label="编号" path="no">
        <n-input v-model:value="paramsForm.no" placeholder="请输入编号" clearable></n-input>
      </n-form-item>
      <n-form-item label="树名" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入树名" clearable></n-input>
      </n-form-item>
      <n-form-item label="学名" path="scientificName">
        <n-input v-model:value="paramsForm.scientificName" placeholder="请输入学名" clearable></n-input>
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
      <n-form-item label="树名" path="name">
        <n-input v-model:value="form.name" placeholder="请输入树名" clearable></n-input>
      </n-form-item>
      <n-form-item label="学名" path="scientificName">
        <n-input v-model:value="form.scientificName" placeholder="请输入学名" clearable></n-input>
      </n-form-item>
      <n-grid x-gap="10">
        <n-gi span="8">
          <n-form-item label="序号" path="serialNumber">
            <n-input-number v-model:value="form.serialNumber" placeholder="请输入序号" clearable></n-input-number>
          </n-form-item>
          <n-form-item label="位置" path="address">
            <n-input v-model:value="form.address" placeholder="请输入位置" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="8">
          <n-form-item label="编号" path="no">
            <n-input v-model:value="form.no" placeholder="请输入编号" clearable></n-input>
          </n-form-item>
          <n-form-item label="树高" path="height">
            <n-input-number v-model:value="form.height" placeholder="请输入树高" clearable></n-input-number>
          </n-form-item>
        </n-gi>
        <n-gi span="8">
          <n-form-item label="树龄" path="age">
            <n-input-number v-model:value="form.age" placeholder="请输入树龄" clearable></n-input-number>
          </n-form-item>
          <n-form-item label="冠幅" path="gf">
            <n-input-number v-model:value="form.gf" placeholder="请输入冠幅" clearable></n-input-number>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="特点" path="characteristic">
        <n-input v-model:value="form.characteristic" placeholder="请输入特点"></n-input>
      </n-form-item>
      <n-form-item label="备注" path="remarks">
        <n-input type="textarea" v-model:value="form.remarks" placeholder="请输入备注"></n-input>
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
        <h2 class="text-center">古树名木登记表</h2>
        <div style="position: absolute; right: 10px; top: 10px">
          <Print :printable="refDetail" size="large"></Print>
        </div>
        <n-table class="info-table" :single-line="false">
          <tr>
            <td>四有档案</td>
            <td colSpan="8">{{ form.fourCulturalRecordFourCulturalRecord.name || '--' }}</td>
          </tr>
          <tr>
            <td>树名</td>
            <td colSpan="8">{{ form.name || '--' }}</td>
          </tr>
          <tr>
            <td>学名</td>
            <td colSpan="8">{{ form.scientificName || '--' }}</td>
          </tr>
          <tr>
            <td>序号</td>
            <td colSpan="2">{{ form.serialNumber || '--' }}</td>
            <td>编号</td>
            <td colSpan="2">{{ form.no || '--' }}</td>
            <td>树龄</td>
            <td colSpan="2">{{ form.age || '--' }}</td>
          </tr>
          <tr>
            <td>位置</td>
            <td colSpan="2">{{ form.address || '--' }}</td>
            <td>树高</td>
            <td colSpan="2">{{ form.height || '--' }}</td>
            <td>冠幅</td>
            <td colSpan="2">{{ form.gf || '--' }}</td>
          </tr>
          <tr>
            <td>特点</td>
            <td colSpan="8">{{ form.characteristic || '--' }}</td>
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
import { list, edit, add, del, get } from '../../../api/four/fourCulturalRecordMainText15';
import { onMounted, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import CulturalRecordSelect from '../CulturalRecordSelect.vue';
import Print from '@/components/basic/print/Print.vue';

const columns = [
  { key: 'serialNumber', title: '序号', titleAlign: 'center', width: '80px', ellipsis: { tooltip: true } },
  { key: 'no', title: '编号', titleAlign: 'center', width: '100px', ellipsis: { tooltip: true } },
  {
    key: 'name',
    title: '树名',
    columnType: 'title',
    ellipsis: { tooltip: { style: { maxWidth: '600px' } } },
    titleAlign: 'center',
  },
  { key: 'scientificName', title: '学名', titleAlign: 'center', width: '200px', ellipsis: { tooltip: true } },
  { key: 'age', title: '树龄', titleAlign: 'center', width: '80px', ellipsis: { tooltip: true } },
  { key: 'address', title: '位置', titleAlign: 'center', width: '200px', ellipsis: { tooltip: true } },
  { key: 'height', title: '树高', titleAlign: 'center', width: '80px', ellipsis: { tooltip: true } },
  { key: 'gf', title: '冠幅', titleAlign: 'center', width: '80px', ellipsis: { tooltip: true } },
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
  scientificName: '',
  age: null,
  height: null,
  gf: null,
  address: '',
  remarks: '',
  characteristic: '',
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
    message: '请输入树名',
  },
};

/**
 * 查询表单
 * */
const paramsForm = ref({
  fourCulturalRecordId: props.fourCulturalRecordId,
  name: '',
  serialNumber: '',
  no: '',
  scientificName: '',
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
