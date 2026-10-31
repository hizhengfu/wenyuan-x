<template>
  <h-page-data-table ref="refPageTable" :fetch-data="list" :params="paramsForm" @add="handleAdd" @rowEdit="handleEdit"
    @rowView="handleView" :funApiDelete="del" :columns="columns">
    <!-- <template #queryForm>
      <n-form-item label="名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="序号" path="serialNumber">
        <n-input v-model:value="paramsForm.serialNumber" placeholder="请输入序号" clearable></n-input>
      </n-form-item>
      <n-form-item label="开始页号" path="startPageNumber">
        <n-input v-model:value="paramsForm.startPageNumber" placeholder="请输入开始页号" clearable></n-input>
      </n-form-item>
      <n-form-item label="结束页号" path="endPageNumber">
        <n-input v-model:value="paramsForm.endPageNumber" placeholder="请输入结束页号" clearable></n-input>
      </n-form-item>
    </template> -->
    <h-page-form :rules="rules" :model="form" label-width="auto" :title="formTitle" :refTable="refPageTable"
      :disabled="formType === 'view'" :handleSubmit="handleSubmit" v-model:show="formModel">
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="四有档案" path="fourCulturalRecordId">
            <CulturalRecordSelect disabled v-model:value="form.fourCulturalRecordId" placeholder="请选择四有档案" clearable>
            </CulturalRecordSelect>
          </n-form-item>
          <n-form-item label="开始页号" path="startPageNumber">
            <n-input-number v-model:value="form.startPageNumber" placeholder="请输入开始页号" clearable></n-input-number>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入名称" clearable></n-input>
          </n-form-item>
          <n-form-item label="结束页号" path="endPageNumber">
            <n-input-number v-model:value="form.endPageNumber" placeholder="请输入结束页号" clearable></n-input-number>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="内容" path="content">
        <n-input v-model:value="form.content" type="textarea" rows="10"></n-input>
      </n-form-item>
    </h-page-form>
    <h-page-form v-model:show="detailModel" class="project" :rules="rules" :model="form" label-width="auto"
      :title="formTitle" :ref-table="refPageTable" :disabled="formType === 'view'" :enable-cancel="false">
      <div class="page-detail" ref="refDetail">
        <n-table class="info-table" :single-line="false">
          <tr>
            <td>四有档案</td>
            <td colSpan="3">{{ form.fourCulturalRecordFourCulturalRecord.name || '--' }}</td>
            <td>名称</td>
            <td colSpan="3">{{ form.name || '--' }}</td>
          </tr>
          <tr>
            <td>开始页号</td>
            <td colSpan="3">{{ form.startPageNumber || '--' }}</td>
            <td>结束页号</td>
            <td colSpan="3">{{ form.endPageNumber || '--' }}</td>
          </tr>
          <tr>
            <td>内容</td>
            <td colSpan="7">{{ form.content || '--' }}</td>
          </tr>
        </n-table>
      </div>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../../api/four/culturalRecordDirectory';
import { ref, h } from 'vue';
import { cloneDeep } from 'lodash-es';
import CulturalRecordSelect from '../CulturalRecordSelect.vue';
import { RouterLink } from 'vue-router';

const columns = [
  { key: 'serialNumber', title: '序号', align: 'center', width: '80px' },
  {
    key: 'name',
    title: '名称',
    columnType: 'title'

    // render(row: any) {
    //   return h(
    //     RouterLink,
    //     {
    //       to: {
    //         path: '/archive/four/detail',
    //         query: { fourCulturalRecordId: row.fourCulturalRecordId, title: row.name, code: row.code },
    //       },
    //     },
    //     { default: () => row.name },
    //   );
    // },
  },
  { key: 'count', title: '数量', width: '200px' },
];

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
const detailModel = ref(false)
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
  serialNumber: '',
  name: '',
  startPageNumber: null,
  endPageNumber: null,
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
  serialNumber: '',
  name: '',
  startPageNumber: '',
  endPageNumber: '',
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
