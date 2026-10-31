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
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="序号" path="serialNumber">
        <n-input v-model:value="paramsForm.serialNumber" placeholder="请输入序号" clearable></n-input>
      </n-form-item>
      <n-form-item label="责任者" path="zrz">
        <n-input v-model:value="paramsForm.zrz" placeholder="请输入责任者" clearable></n-input>
      </n-form-item>
      <n-form-item label="题名" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入题名" clearable></n-input>
      </n-form-item>
      <n-form-item label="页号" path="pageNumber">
        <n-input v-model:value="paramsForm.pageNumber" placeholder="请输入页号" clearable></n-input>
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
        <n-gi span="8">
          <n-form-item label="四有档案" path="fourCulturalRecordId">
            <CulturalRecordSelect
              disabled
              v-model:value="form.fourCulturalRecordId"
              placeholder="请选择四有档案"
              clearable
            ></CulturalRecordSelect>
          </n-form-item>
        </n-gi>
        <n-gi span="8">
          <n-form-item label="序号" path="serialNumber">
            <n-input v-model:value="form.serialNumber" placeholder="请输入序号" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="8">
          <n-form-item label="责任者" path="zrz">
            <n-input v-model:value="form.zrz" placeholder="请输入责任者" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="题名" path="name">
        <n-input v-model:value="form.name" placeholder="请输入题名" clearable></n-input>
      </n-form-item>
      <n-grid x-gap="10">
        <n-gi span="8">
          <n-form-item label="时间" path="dateTime">
            <n-input v-model:value="form.dateTime" placeholder="请输入时间" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="8">
          <n-form-item label="页号" path="pageNumber">
            <n-input-number v-model:value="form.pageNumber" placeholder="请输入页号" clearable></n-input-number>
          </n-form-item>
        </n-gi>
        <n-gi span="8">
          <n-form-item label="张数" path="sheetsNum">
            <n-input-number v-model:value="form.sheetsNum" placeholder="请输入张数" clearable></n-input-number>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="备注" path="content">
        <n-input v-model:value="form.content" type="textarea"></n-input>
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
        <h2 class="text-center">大事记</h2>
        <div style="position: absolute; right: 10px; top: 10px">
          <Print :printable="refDetail" size="large"></Print>
        </div>
        <n-table class="info-table" :single-line="false">
          <tr>
            <td>四有档案</td>
            <td colSpan="2">{{ form.fourCulturalRecordFourCulturalRecord.name || '--' }}</td>
            <td>序号</td>
            <td colSpan="2">{{ form.serialNumber || '--' }}</td>
            <td>责任者</td>
            <td colSpan="2">{{ form.zrz || '--' }}</td>
          </tr>
          <tr>
            <td>题名</td>
            <td colSpan="8">{{ form.name || '--' }}</td>
          </tr>
          <tr>
            <td>时间</td>
            <td colSpan="2">{{ form.dateTime || '--' }}</td>
            <td>页号</td>
            <td colSpan="2">{{ form.pageNumber || '--' }}</td>
            <td>张数</td>
            <td colSpan="2">{{ form.sheetsNum || '--' }}</td>
          </tr>
          <tr>
            <td>备注</td>
            <td colSpan="8">{{ form.content || '--' }}</td>
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
import { list, edit, add, del, get } from '../../../api/four/culturalRecordAssistantDsj';
import { ref, onMounted } from 'vue';
import { cloneDeep } from 'lodash-es';
import CulturalRecordSelect from '../CulturalRecordSelect.vue';
import Print from '../../../../../components/basic/print/Print.vue';
import dayjs from 'dayjs';

const props = defineProps({ fourCulturalRecordId: { type: String, required: true } });

const columns = [
  { key: 'serialNumber', title: '序号', titleAlign: 'center', ellipsis: { tooltip: true }, width: '80px' },
  { key: 'zrz', title: '责任者', titleAlign: 'center', ellipsis: { tooltip: true } },
  {
    key: 'name',
    title: '题名',
    columnType: 'title',
    ellipsis: { tooltip: { style: { maxWidth: '600px' } } },
    titleAlign: 'center',
  },
  {
    key: 'dateTime',
    title: '时间',
    align: 'center',
    ellipsis: { tooltip: true },
    width: '150px',
  },
  { key: 'pageNumber', title: '页号', titleAlign: 'center', ellipsis: { tooltip: true }, width: '80px' },
  { key: 'sheetsNum', title: '张数', titleAlign: 'center', ellipsis: { tooltip: true }, width: '80px' },
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
  serialNumber: '',
  zrz: '',
  name: '',
  dateTime: '',
  pageNumber: null,
  sheetsNum: null,
  content: '',
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
  code: {
    required: true,
    message: '请输入文号',
  },
  zrz: {
    required: true,
    message: '请输入责任者',
  },
};

/**
 * 查询表单
 * */
const paramsForm = ref({
  fourCulturalRecordId: props.fourCulturalRecordId,
  serialNumber: '',
  zrz: '',
  name: '',
  pageNumber: '',
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
