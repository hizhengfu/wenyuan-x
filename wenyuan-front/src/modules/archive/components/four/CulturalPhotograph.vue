<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1200"
    :params="paramsForm"
    v-bind="$attrs"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
    @loadData="handleLoad"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="序号" path="serialNumber">
        <n-input v-model:value="paramsForm.serialNumber" placeholder="请输入序号" clearable></n-input>
      </n-form-item>
      <n-form-item label="题名" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入题名" clearable></n-input>
      </n-form-item>
      <n-form-item label="底片号" path="filmNo">
        <n-input v-model:value="paramsForm.filmNo" placeholder="请输入底片号" clearable></n-input>
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
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="照片类型" path="type">
            <h-dict-select
              disabled
              v-model:value="form.type"
              type="four_cultural_relic_photograph_type"
              placeholder="请选择照片类型"
            ></h-dict-select>
          </n-form-item>
          <n-form-item label="底片号" path="filmNo">
            <n-input v-model:value="form.filmNo" placeholder="请输入底片号" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="序号" path="serialNumber">
            <n-input-number v-model:value="form.serialNumber" placeholder="请输入序号" clearable></n-input-number>
          </n-form-item>
          <n-form-item label="参见号" path="seeNo">
            <n-input v-model:value="form.seeNo" placeholder="请输入参见号" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="题名" path="name">
        <n-input v-model:value="form.name" placeholder="请输入题名" clearable></n-input>
      </n-form-item>
      <n-form-item label="文字说明" path="description">
        <n-input v-model:value="form.description" type="textarea" placeholder="请输入文字说明"></n-input>
      </n-form-item>
      <n-grid x-gap="10">
        <n-gi span="8">
          <n-form-item label="拍摄时间" path="shootingTime">
            <n-date-picker clearable v-model:value="form.shootingTime" placeholder="请选择拍摄时间" />
          </n-form-item>
        </n-gi>
        <n-gi span="8">
          <n-form-item label="拍摄方位" path="shootingDirection">
            <n-input v-model:value="form.shootingDirection" placeholder="请输入拍摄方位" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="8">
          <n-form-item label="拍摄者" path="shootingName">
            <n-input v-model:value="form.shootingName" placeholder="请输入拍摄者" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="备注" path="remark">
        <n-input v-model:value="form.remark" type="textarea" placeholder="请输入备注"></n-input>
      </n-form-item>
      <HUpload :contentId="form.id" :form="form" label="上传照片"></HUpload>
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
        <h2 class="text-center">{{ props.title }}</h2>
        <div style="position: absolute; right: 10px; top: 10px">
          <Print :printable="refDetail" size="large"></Print>
        </div>
        <n-table class="info-table" :single-line="false">
          <tr>
            <td>四有档案</td>
            <td colSpan="4">{{ form.fourCulturalRecordFourCulturalRecord.name || '--' }}</td>
            <td>序号</td>
            <td colSpan="3">{{ form.serialNumber || '--' }}</td>
          </tr>
          <tr>
            <td>底片号</td>
            <td colSpan="4">{{ form.filmNo || '--' }}</td>
            <td>参见号</td>
            <td colSpan="3">{{ form.seeNo || '--' }}</td>
          </tr>
          <tr>
            <td>题名</td>
            <td colSpan="8">{{ form.name || '--' }}</td>
          </tr>
          <tr>
            <td rowspan="4" style="writing-mode: vertical-rl; letter-spacing: 10px; padding-top: 25px">文字说明</td>
            <td rowspan="4" colSpan="4" style="border-right: solid 1px #efeff5">{{ form.description || '--' }}</td>
          </tr>
          <tr>
            <td>拍摄时间</td>
            <td colSpan="3">{{ form.shootingTime ? dayjs(form.shootingTime).format('YYYY-MM-DD') : '--' }}</td>
          </tr>
          <tr>
            <td>拍摄方位</td>
            <td colSpan="3">{{ form.shootingDirection || '--' }}</td>
          </tr>
          <tr>
            <td>拍摄者</td>
            <td colSpan="3">{{ form.shootingName || '--' }}</td>
          </tr>
          <tr>
            <td>备注</td>
            <td colSpan="8">{{ form.remark || '--' }}</td>
          </tr>
          <tr>
            <td>上传照片</td>
            <td colSpan="8"><h-file-list :contentId="form.id"></h-file-list></td>
          </tr>
        </n-table>
      </div>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../api/four/culturalRecordMainPhotograph';
import { onMounted, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import CulturalRecordSelect from './CulturalRecordSelect.vue';
import Print from '../../../../components/basic/print/Print.vue';
import dayjs from 'dayjs';

const columns = [
  { key: 'serialNumber', title: '序号', titleAlign: 'center', ellipsis: { tooltip: true }, width: '80px' },
  {
    key: 'name',
    title: '题名',
    columnType: 'title',
    ellipsis: { tooltip: true },
    titleAlign: 'center',
  },
  {
    key: 'shootingTime',
    title: '拍摄时间',
    align: 'center',
    ellipsis: { tooltip: true },
    width: '110px',
    columnType: 'date',
  },
  { key: 'seeNo', title: '参见号', titleAlign: 'center', ellipsis: { tooltip: true }, width: '170px' },
  { key: 'filmNo', title: '底片号', titleAlign: 'center', ellipsis: { tooltip: true }, width: '170px' },
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
const props = defineProps({
  fourCulturalRecordId: { type: String, required: true },
  title: { type: String, required: true },
  type: { type: Number, required: true },
});

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  fourCulturalRecordId: props.fourCulturalRecordId,
  type: props.type,
  serialNumber: null,
  filmNo: '',
  seeNo: '',
  name: '',
  description: '',
  shootingTime: null,
  shootingDirection: '',
  shootingName: '',
  remark: '',
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
    message: '请输入题名',
  },
  serialNumber: {
    required: true,
    message: '请输入序号',
  },
};

/**
 * 查询表单
 * */
const paramsForm = ref({
  fourCulturalRecordId: props.fourCulturalRecordId,
  serialNumber: '',
  name: '',
  filmNo: '',
  type: props.type,
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
  formTitle.value = `编辑${props.title}`;
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
  formTitle.value = `查看${props.title}`;
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
