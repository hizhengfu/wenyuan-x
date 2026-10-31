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
      <n-form-item label="纬度" path="lat">
        <n-input v-model:value="paramsForm.lat" placeholder="请输入纬度" clearable></n-input>
      </n-form-item>
      <n-form-item label="经度" path="lng">
        <n-input v-model:value="paramsForm.lng" placeholder="请输入经度" clearable></n-input>
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
      <n-form-item label="编号" path="serialNumber">
        <n-input v-model:value="form.serialNumber" placeholder="请输入编号" clearable></n-input>
      </n-form-item>
      <n-descriptions bordered label-placement="top" style="margin-bottom: 20px">
        <n-descriptions-item label="坐标">
          <n-grid x-gap="10">
            <n-gi span="8">
              <n-form-item label="纬度" path="lat">
                <n-input v-model:value="form.lat" placeholder="请输入纬度" clearable></n-input>
              </n-form-item>
            </n-gi>
            <n-gi span="8">
              <n-form-item label="经度" path="lng">
                <n-input v-model:value="form.lng" placeholder="请输入经度" clearable></n-input>
              </n-form-item>
            </n-gi>
            <n-gi span="8">
              <n-form-item label="海拔高度" path="altitude">
                <n-input v-model:value="form.altitude" placeholder="请输入海拔高度" clearable></n-input>
              </n-form-item>
            </n-gi>
          </n-grid>
        </n-descriptions-item>
      </n-descriptions>
      <n-form-item label="测点说明" path="description">
        <n-input v-model:value="form.description" placeholder="请输入测点说明" clearable type="textarea"></n-input>
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
          <td colspan="2">{{ form.serialNumber || '--' }}</td>
          <td>纬度</td>
          <td colspan="2">{{ form.lat || '--' }}</td>
          <td>经度</td>
          <td colspan="2">{{ form.lng || '--' }}</td>
        </tr>
        <tr>
          <td>测点说明</td>
          <td colspan="8">{{ form.description || '--' }}</td>
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
import { list, edit, add, del, get } from '../../../api/third/thirdCulturalRelicGps';
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import CulturalRelicSelect from '../CulturalRelicSelect.vue';

const columns = [
  // { key: 'thirdCulturalRelicCoverVo.name', title: '不可移动文物', align: 'center' },
  {
    key: 'serialNumber',
    title: '编号',
    align: 'center',
    width: '200px',
    fixed: 'left',
    ellipsis: true,
    resizable: true,
  },
  {
    key: 'location',
    title: '坐标',
    titleAlign: 'center',
    children: [
      { key: 'lat', title: '纬度', titleAlign: 'center' },
      { key: 'lng', title: '经度', titleAlign: 'center' },
      { key: 'altitude', title: '海拔高度', titleAlign: 'center' },
    ],
  },
  { key: 'description', title: '测点说明', titleAlign: 'center', ellipsis: { tooltip: true } },
  { key: 'remark', title: '备注', titleAlign: 'center', ellipsis: { tooltip: true } },
];

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
const detailModel = ref(false);

const props = defineProps({
  queryShow: {
    type: Boolean,
    default: true,
  },
  thirdCulturalRecordId: { type: String, required: true },
});

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
  lat: '',
  lng: '',
  altitude: '',
  description: '',
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
  culturalRelicId: props.thirdCulturalRecordId,
  lat: '',
  lng: '',
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
  formTitle.value = `编辑「${row.serialNumber}」`;
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
  formTitle.value = `查看「${row.serialNumber}」`;
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
