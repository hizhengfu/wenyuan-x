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
      <n-form-item label="编制单位" path="bzdw">
        <n-input v-model:value="paramsForm.bzdw" placeholder="请输入编制单位" clearable></n-input>
      </n-form-item>
      <n-form-item label="题名" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入题名" clearable></n-input>
      </n-form-item>
      <n-form-item label="批准单位" path="pzdw">
        <n-input v-model:value="paramsForm.pzdw" placeholder="请输入批准单位" clearable></n-input>
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
            ></CulturalRecordSelect>
          </n-form-item>
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
        <n-gi span="12">
          <n-form-item label="编制单位" path="bzdw">
            <n-input v-model:value="form.bzdw" placeholder="请输入编制单位" clearable></n-input>
          </n-form-item>
          <n-form-item label="批准单位" path="pzdw">
            <n-input v-model:value="form.pzdw" placeholder="请输入批准单位" clearable></n-input>
          </n-form-item>
          <n-form-item label="类型" path="type">
            <h-dict-select
              disabled
              v-model:value="form.type"
              type="four_cultural_relic_bhgcfa_type"
              placeholder="请选择类型"
            ></h-dict-select>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="编制时间" path="bzsj">
            <n-date-picker clearable v-model:value="form.bzsj" placeholder="请选择编制时间" />
          </n-form-item>
          <n-form-item label="批准时间" path="pzsj">
            <n-date-picker clearable v-model:value="form.pzsj" placeholder="请选择批准时间" />
          </n-form-item>
          <n-form-item label="张数" path="sheetsNumber">
            <n-input-number v-model:value="form.sheetsNumber" placeholder="请输入张数" clearable></n-input-number>
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
        <h2 class="text-center">保护工程方案</h2>
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
            <td>编制单位</td>
            <td colSpan="3">{{ form.bzdw || '--' }}</td>
            <td>编制时间</td>
            <td colSpan="4">{{ form.bzsj ? dayjs(form.bzsj).format('YYYY-MM-DD') : '--' }}</td>
          </tr>
          <tr>
            <td>批准单位</td>
            <td colSpan="3">{{ form.pzdw || '--' }}</td>
            <td>批准时间</td>
            <td colSpan="4">{{ form.pzsj ? dayjs(form.pzsj).format('YYYY-MM-DD') : '--' }}</td>
          </tr>
          <tr>
            <td>张数</td>
            <td colSpan="3">{{ form.sheetsNumber || '--' }}</td>
            <td>类型</td>
            <td colSpan="4">
              <h-dict-text v-model:value="form.type" type="four_cultural_relic_bhgcfa_type"></h-dict-text>
            </td>
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
import { list, edit, add, del, get } from '../../../api/four/culturalRecordMainBhghBhfa';
import { onMounted, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import CulturalRecordSelect from '../CulturalRecordSelect.vue';
import Print from '@/components/basic/print/Print.vue';
import dayjs from 'dayjs';

const props = defineProps({ fourCulturalRecordId: { type: String, required: true } });

const columns = [
  { key: 'serialNumber', title: '序号', titleAlign: 'center', ellipsis: { tooltip: true }, width: '80px' },
  { key: 'bzdw', title: '编制单位', titleAlign: 'center', ellipsis: { tooltip: true }, width: '100px' },
  {
    key: 'name',
    title: '题名',
    columnType: 'title',
    ellipsis: { tooltip: true },
    titleAlign: 'center',
    width: '150px',
  },
  {
    key: 'bzsj',
    title: '编制时间',
    align: 'center',
    ellipsis: { tooltip: true },
    width: '100px',
    columnType: 'date',
  },
  { key: 'pzdw', title: '批准单位', titleAlign: 'center', ellipsis: { tooltip: true }, width: '100px' },
  {
    key: 'pzsj',
    title: '批准时间',
    align: 'center',
    ellipsis: { tooltip: true },
    width: '100px',
    columnType: 'date',
  },
  { key: 'sheetsNumber', title: '张数', titleAlign: 'center', ellipsis: { tooltip: true }, width: '80px' },
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
/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  fourCulturalRecordId: props.fourCulturalRecordId,
  name: '',
  serialNumber: null,
  bzdw: '',
  bzsj: null,
  pzdw: '',
  pzsj: null,
  sheetsNumber: null,
  remarks: '',
  createBy: '',
  createTime: null,
  updateBy: '',
  updateTime: null,
  type: '1',
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
  bzdw: {
    required: true,
    message: '请输入编制单位',
  },
  pzdw: {
    required: true,
    message: '请输入批准单位',
  },
};

/**
 * 查询表单
 * */
const paramsForm = ref({
  fourCulturalRecordId: props.fourCulturalRecordId,
  serialNumber: '',
  bzdw: '',
  name: '',
  pzdw: '',
  type: '1',
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
  formTitle.value = `编辑保护工程方案`;
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
  formTitle.value = `查看保护工程方案`;
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
