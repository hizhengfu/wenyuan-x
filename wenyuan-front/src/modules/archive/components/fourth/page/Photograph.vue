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
    v-if="props.showList"
  >
    <template #queryForm>
      <n-form-item label="名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入名称" clearable></n-input>
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
          <n-form-item label="图片编号" path="code">
            <n-input v-model:value="form.code" placeholder="请输入图片编号" clearable></n-input>
          </n-form-item>
          <n-form-item label="名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入名称" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-space justify="center">
            <n-form-item label="照片路径" path="path">
              <h-avatar v-model:value="form.path"></h-avatar>
            </n-form-item>
          </n-space>
        </n-gi>
      </n-grid>
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="照片号" path="photographNo">
            <n-input v-model:value="form.photographNo" placeholder="请输入照片号" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="底片号" path="photographicPlateNo">
            <n-input v-model:value="form.photographicPlateNo" placeholder="请输入底片号" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="拍摄者" path="photographBy">
            <n-input v-model:value="form.photographBy" placeholder="请输入拍摄者" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="拍摄方位" path="photographOrientation">
            <n-input v-model:value="form.photographOrientation" placeholder="请输入拍摄方位" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="拍摄时间" path="photographTime">
            <n-date-picker clearable v-model:value="form.photographTime" type="date" placeholder="请输入拍摄时间" />
            <!-- <n-input clearable v-model:value="form.photographTime"  placeholder="请输入拍摄时间" /> -->
          </n-form-item>
        </n-gi>
      </n-grid>

      <n-form-item label="文字说明" path="remark">
        <n-input v-model:value="form.remark" type="textarea" placeholder="请输入文字说明" clearable></n-input>
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
      <n-table class="info-table" :single-line="false">
        <tr>
          <td>图片编号</td>
          <td colspan="3">{{ form.code || '--' }}</td>
          <td>名称</td>
          <td colspan="3">{{ form.name || '--' }}</td>
          <td rowspan="2">照片</td>
          <td colspan="3" rowspan="2">
            <h-image v-if="form.path" :src="form.path" size="50"></h-image>
          </td>
        </tr>
        <tr>
          <td>照片号</td>
          <td colspan="3">{{ form.photographNo || '--' }}</td>

          <td>底片号</td>
          <td colspan="3" style="border-right: 1px solid #efeff5">{{ form.photographicPlateNo || '--' }}</td>
        </tr>
        <tr>
          <td>拍摄者</td>
          <td colspan="3">{{ form.photographBy || '--' }}</td>
          <td>拍摄方位</td>
          <td colspan="7">{{ form.photographOrientation || '--' }}</td>
        </tr>
        <tr>
          <td>文字说明</td>
          <td colspan="8">{{ form.remark || '--' }}</td>
        </tr>
      </n-table>
    </h-page-form>
  </h-page-data-table>
  <photographView v-else :fourthCulturalRecordId="fourthCulturalRecordId"></photographView>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../../api/fourth/fourthCulturalRelicPhotograph';
import photographView from './PhotographView.vue';
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
  showList: {
    type: Boolean,
    default: true,
  },
});
const columns = [
  { title: '序号', key: 'index', columnType: 'index', width: '80px', align: 'center' },
  { key: 'code', title: '图片编号', resizable: true, ellipsis: { tooltip: true } },
  { key: 'name', title: '名称', width: '200px', resizable: true, ellipsis: { tooltip: true } },
  { key: 'path', title: '照片', columnType: 'image', width: '100px' },
  { key: 'photographNo', title: '照片号' },
  { key: 'photographicPlateNo', title: '底片号' },
  { key: 'photographBy', title: '拍摄者' },
  { key: 'photographTime', title: '拍摄时间', columnType: 'date' },
  { key: 'photographOrientation', title: '拍摄方位', ellipsis: { tooltip: true } },

  { key: 'remark', title: '文字说明', ellipsis: { tooltip: true } },
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
  culturalRelicId: route.query.fourthCulturalRecordId || props.fourthCulturalRecordId,

  name: '',
  code: '',
  photographNo: '',
  photographicPlateNo: '',
  photographBy: '',
  photographTime: null,
  photographOrientation: '',
  path: '',
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
  code: {
    required: true,
    message: '请输入图纸编号',
  },
  name: {
    required: true,
    message: '请输入名称',
  },
  path: {
    required: true,
    message: '请上传图纸',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
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
