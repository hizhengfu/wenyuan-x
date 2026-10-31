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
    v-bind="$attrs"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="类别" path="category">
        <n-input v-model:value="paramsForm.category" placeholder="请输入类别" clearable></n-input>
      </n-form-item>
      <n-form-item label="成立时间" path="clsj">
        <n-input v-model:value="paramsForm.clsj" placeholder="请输入成立时间" clearable></n-input>
      </n-form-item>
      <n-form-item label="负责人" path="zzr">
        <n-input v-model:value="paramsForm.zzr" placeholder="请输入负责人" clearable></n-input>
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
        <n-gi span="12">
          <n-form-item label="类别" path="category">
            <n-input v-model:value="form.category" placeholder="请输入类别" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="成立时间" path="clsj">
            <n-input v-model:value="form.clsj" placeholder="请输入成立时间" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="人数" path="rs">
            <n-input v-model:value="form.rs" placeholder="请输入人数" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="负责人" path="zzr">
            <n-input v-model:value="form.zzr" placeholder="请输入负责人" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="详细内容" path="content">
        <n-input v-model:value="form.content" type="textarea" rows="7"></n-input>
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
        <h2 class="text-center">保护机构情况</h2>
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
            <td>类别</td>
            <td colSpan="3">{{ form.category || '--' }}</td>
            <td>成立时间</td>
            <td colSpan="4">{{ form.clsj || '--' }}</td>
          </tr>
          <tr>
            <td>人数</td>
            <td colSpan="3">{{ form.rs || '--' }}</td>
            <td>负责人</td>
            <td colSpan="4">{{ form.zzr || '--' }}</td>
          </tr>
          <tr>
            <td>详细内容</td>
            <td colSpan="8">{{ form.content || '--' }}</td>
          </tr>
        </n-table>
      </div>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../../api/four/fourCulturalRecordMainText11';
import { onMounted, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import CulturalRecordSelect from '../CulturalRecordSelect.vue';
import Print from '@/components/basic/print/Print.vue';
import dayjs from 'dayjs';

const columns = [
  {
    key: 'name',
    title: '名称',
    columnType: 'title',
    ellipsis: { tooltip: { style: { maxWidth: '600px' } } },
    titleAlign: 'center',
  },
  { key: 'category', title: '类别', titleAlign: 'center', width: '150px', ellipsis: { tooltip: true } },
  { key: 'zzr', title: '负责人', titleAlign: 'center', width: '150px', ellipsis: { tooltip: true } },
  { key: 'rs', title: '人数', titleAlign: 'center', width: '100px', ellipsis: { tooltip: true } },
  { key: 'clsj', title: '成立时间', align: 'center', width: '170px', ellipsis: { tooltip: true } },
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
  name: '',
  category: '',
  zzr: '',
  rs: '',
  clsj: '',
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
    message: '请输入名称',
  },
};

/**
 * 查询表单
 * */
const paramsForm = ref({
  fourCulturalRecordId: props.fourCulturalRecordId,
  name: '',
  category: '',
  zzr: '',
  clsj: '',
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
