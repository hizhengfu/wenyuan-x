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
      <n-form-item label="光盘号" path="gph">
        <n-input v-model:value="paramsForm.gph" placeholder="请输入光盘号" clearable></n-input>
      </n-form-item>
      <n-form-item label="题名" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入题名" clearable></n-input>
      </n-form-item>
      <n-form-item label="形成时间" path="dateTime">
        <n-date-picker clearable v-model:value="paramsForm.dateTimeList" placeholder="请选择形成时间" type="daterange" />
      </n-form-item>
      <n-form-item label="类别" path="type">
        <h-dict-select
          v-model:value="paramsForm.type"
          type="four_cultural_relic_dzwj_type"
          placeholder="请选择类别"
          clearable
        ></h-dict-select>
      </n-form-item>
      <n-form-item label="套别" path="tb">
        <h-dict-select
          v-model:value="paramsForm.tb"
          type="four_cultural_relic_dzwj_tb"
          placeholder="请选择套别"
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
          <n-form-item label="四有档案" path="fourCulturalRecordId">
            <CulturalRecordSelect
              disabled
              v-model:value="form.fourCulturalRecordId"
              placeholder="请选择四有档案"
              clearable
            ></CulturalRecordSelect
          ></n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="序号" path="serialNumber">
            <n-input-number v-model:value="form.serialNumber" placeholder="请输入序号" clearable></n-input-number>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="题名" path="name">
        <n-input v-model:value="form.name" placeholder="请输入题名" clearable></n-input>
      </n-form-item>
      <n-grid x-gap="10">
        <n-gi span="8">
          <n-form-item label="光盘号" path="gph">
            <n-input v-model:value="form.gph" placeholder="请输入光盘号" clearable></n-input>
          </n-form-item>
          <n-form-item label="套别" path="tb">
            <h-dict-select
              v-model:value="form.tb"
              type="four_cultural_relic_dzwj_tb"
              placeholder="请选择套别"
              clearable
            ></h-dict-select>
          </n-form-item>
        </n-gi>
        <n-gi span="8">
          <n-form-item label="形成时间" path="dateTime">
            <n-date-picker clearable v-model:value="form.dateTime" placeholder="请选择形成时间" />
          </n-form-item>
          <n-form-item label="保管期限" path="bgqx">
            <n-input v-model:value="form.bgqx" placeholder="请输入保管期限" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="8">
          <n-form-item label="类别" path="type">
            <h-dict-select
              v-model:value="form.type"
              type="four_cultural_relic_dzwj_type"
              placeholder="请选择类别"
              clearable
            ></h-dict-select>
          </n-form-item>
          <n-form-item label="密级" path="mj">
            <h-dict-select
              v-model:value="form.mj"
              type="four_cultural_relic_dzwj_mj"
              placeholder="请选择密级"
              clearable
            ></h-dict-select>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="软件环境" path="rjhj">
            <n-input v-model:value="form.rjhj" placeholder="请输入软件环境" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="光盘标签" path="gpbq">
            <n-input v-model:value="form.gpbq" placeholder="请输入光盘标签" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="备注" path="remarks">
        <n-input v-model:value="form.remarks" type="textarea"></n-input>
      </n-form-item>
      <HUpload :contentId="form.id" :form="form" label="上传文件"></HUpload>
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
        <h2 class="text-center">电子文件</h2>
        <div style="position: absolute; right: 10px; top: 10px">
          <Print :printable="refDetail" size="large"></Print>
        </div>
        <n-table class="info-table" :single-line="false">
          <tr>
            <td>四有档案</td>
            <td colSpan="3">{{ form.fourCulturalRecordFourCulturalRecord.name || '--' }}</td>
            <td>序号</td>
            <td colSpan="4">{{ form.serialNumber || '--' }}</td>
          </tr>
          <tr>
            <td>题名</td>
            <td colSpan="8">{{ form.name || '--' }}</td>
          </tr>
          <tr>
            <td>光盘号</td>
            <td colSpan="2">{{ form.gph || '--' }}</td>
            <td>形成时间</td>
            <td colSpan="2">{{ form.dateTime ? dayjs(form.dateTime).format('YYYY-MM-DD') : '--' }}</td>
            <td>类别</td>
            <td colSpan="2">
              <h-dict-text v-model:value="form.type" type="four_cultural_relic_dzwj_type"></h-dict-text>
            </td>
          </tr>
          <tr>
            <td>套别</td>
            <td colSpan="2"><h-dict-text v-model:value="form.tb" type="four_cultural_relic_dzwj_tb"></h-dict-text></td>
            <td>保管期限</td>
            <td colSpan="2">{{ form.bgqx || '--' }}</td>
            <td>密级</td>
            <td colSpan="2"><h-dict-text v-model:value="form.mj" type="four_cultural_relic_dzwj_mj"></h-dict-text></td>
          </tr>
          <tr>
            <td>软件环境</td>
            <td colSpan="8">{{ form.rjhj || '--' }}</td>
          </tr>
          <tr>
            <td>光盘标签</td>
            <td colSpan="8">{{ form.gpbq || '--' }}</td>
          </tr>
          <tr>
            <td>备注</td>
            <td colSpan="8">{{ form.remarks || '--' }}</td>
          </tr>
          <tr>
            <td>上传文件</td>
            <td colSpan="8"><h-file-list :contentId="form.id"></h-file-list></td>
          </tr>
        </n-table>
      </div>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../../api/four/culturalRecordMainDzwj';
import { onMounted, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import CulturalRecordSelect from '../CulturalRecordSelect.vue';
import Print from '../../../../../components/basic/print/Print.vue';
import dayjs from 'dayjs';

const props = defineProps({ fourCulturalRecordId: { type: String, required: true } });

const columns = [
  { key: 'serialNumber', title: '序号', titleAlign: 'center', ellipsis: { tooltip: true }, width: '80px' },
  { key: 'gph', title: '光盘号', titleAlign: 'center', ellipsis: { tooltip: true }, width: '100px' },
  {
    key: 'name',
    title: '题名',
    columnType: 'title',
    ellipsis: { tooltip: true },
    titleAlign: 'center',
    width: '120px',
  },
  {
    key: 'dateTime',
    title: '形成时间',
    align: 'center',
    ellipsis: { tooltip: true },
    width: '90px',
    columnType: 'date',
  },
  {
    key: 'type',
    title: '类别',
    align: 'center',
    ellipsis: { tooltip: true },
    width: '100px',
    dictType: 'four_cultural_relic_dzwj_type',
  },
  {
    key: 'tb',
    title: '套别',
    align: 'center',
    ellipsis: { tooltip: true },
    width: '100px',
    dictType: 'four_cultural_relic_dzwj_tb',
  },
  { key: 'bgqx', title: '保管期限', titleAlign: 'center', ellipsis: { tooltip: true }, width: '100px' },
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
  fourCulturalRecordId: props.fourCulturalRecordId,
  serialNumber: null,
  gph: '',
  name: '',
  dateTime: null,
  type: '',
  tb: '',
  bgqx: '',
  gpbq: '',
  mj: '',
  rjhj: '',
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
    message: '请输入题名',
  },
  serialNumber: {
    required: true,
    message: '请输入序号',
  },
  gph: {
    required: true,
    message: '请输入光盘号',
  },
  type: {
    required: true,
    message: '请选择类别',
  },
};

/**
 * 查询表单
 * */
const paramsForm = ref({
  fourCulturalRecordId: props.fourCulturalRecordId,
  serialNumber: '',
  gph: '',
  name: '',
  dateTimeList: null,
  type: '',
  tb: '',
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
  formTitle.value = `编辑电子文件`;
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
  formTitle.value = `查看电子文件`;
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
