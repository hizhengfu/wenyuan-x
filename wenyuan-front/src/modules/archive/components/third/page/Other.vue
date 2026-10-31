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
    <template #queryForm v-if="props.queryShow">
      <!-- <n-form-item label="不可移动文物" path="culturalRelicId">
        <CulturalRelicSelect
          v-model:value="paramsForm.culturalRelicId"
          placeholder="请选择不可移动文物"
          clearable
        ></CulturalRelicSelect>
      </n-form-item> -->
      <n-form-item label="名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="类别" path="type">
        <n-input v-model:value="paramsForm.type" placeholder="请输入类别" clearable></n-input>
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
          <n-form-item label="序号" path="serialNumber">
            <n-input v-model:value="form.serialNumber" placeholder="请输入序号" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入名称" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-grid x-gap="10">
        <n-gi span="8">
          <n-form-item label="编号" path="no">
            <n-input v-model:value="form.no" placeholder="请输入编号" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="8">
          <n-form-item label="类别" path="type">
            <h-dict-select
              v-model:value="form.type"
              placeholder="请选择类别"
              type="third_cultural_relic_other_category"
              clearable
            ></h-dict-select>
          </n-form-item>
        </n-gi>
        <n-gi span="8">
          <n-form-item label="数量" path="number">
            <n-input-number v-model:value="form.number" placeholder="请输入数量" clearable></n-input-number>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="保存地点" path="address">
        <n-input v-model:value="form.address" placeholder="请输入保存地点" clearable></n-input>
      </n-form-item>
      <n-form-item label="备注" path="remark">
        <n-input v-model:value="form.remark" placeholder="请输入备注" clearable type="textarea"></n-input>
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
          <td>编号</td>
          <td colspan="2">{{ form.no || '--' }}</td>
          <td>名称</td>
          <td colspan="2">{{ form.name || '--' }}</td>
          <td>序号</td>
          <td colspan="2">{{ form.serialNumber || '--' }}</td>
        </tr>
        <tr>
          <td>类别</td>
          <td colspan="2">
            <h-dict-text type="third_cultural_relic_other_category" v-model:value="form.type"></h-dict-text>
          </td>
          <td>数量</td>
          <td colspan="2">{{ form.number || '--' }}</td>
          <td>保存地点</td>
          <td colspan="2">{{ form.address || '--' }}</td>
        </tr>
        <tr>
          <td>备注</td>
          <td colspan="8">{{ form.remark || '--' }}</td>
        </tr>
      </n-table>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../../api/third/thirdCulturalRelicOther';
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import CulturalRelicSelect from '../CulturalRelicSelect.vue';

const columns = [
  // { key: 'thirdCulturalRelicCoverVo.name', title: '不可移动文物', align: 'center' },
  { key: 'serialNumber', title: '序号', align: 'center', width: '80px' },
  {
    key: 'name',
    title: '名称',
    fixed: 'left',
    ellipsis: { tooltip: { style: { maxWidth: '600px' } } },
    titleAlign: 'center',
  },
  { key: 'no', title: '编号', align: 'center', width: '80px' },
  { key: 'type', title: '类别', align: 'center' },
  { key: 'number', title: '数量', align: 'right' },
  { key: 'address', title: '保存地点', titleAlign: 'center', ellipsis: { tooltip: true } },
  { key: 'remark', title: '备注', titleAlign: 'center', ellipsis: { tooltip: true } },
];

const props = defineProps({
  queryShow: {
    type: Boolean,
    default: true,
  },
  thirdCulturalRecordId: { type: String, required: true },
});

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
  culturalRelicId: props.thirdCulturalRecordId,
  serialNumber: '',
  name: '',
  no: '',
  type: '',
  number: null,
  address: '',
  remark: '',
  createTime: null,
  createBy: '',
  updateTime: null,
  updateBy: '',
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  type: '',
  culturalRelicId: props.thirdCulturalRecordId,
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
