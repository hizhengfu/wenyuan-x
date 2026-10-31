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
    v-bind="$attrs"
  >
    <template #queryForm>
      <n-form-item label="海拔高程" path="hbgd">
        <n-input v-model:value="paramsForm.hbgd" placeholder="请输入海拔高程" clearable></n-input>
      </n-form-item>
      <n-form-item label="行政区划" path="xzqh">
        <n-input v-model:value="paramsForm.xzqh" placeholder="请输入行政区划" clearable></n-input>
      </n-form-item>
      <n-form-item label="测点位置" path="cdwz">
        <n-input v-model:value="paramsForm.cdwz" placeholder="请输入测点位置" clearable></n-input>
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
          <n-form-item label="海拔高程" path="hbgd">
            <n-input-group>
              <n-input v-model:value="form.hbgd" placeholder="请输入海拔高程" clearable></n-input>
              <n-input-group-label>米</n-input-group-label>
            </n-input-group>
          </n-form-item>
          <n-form-item label="经纬度" path="latlng">
            <HFieldLatlng v-model:value="form.latlng"></HFieldLatlng>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="相对位置" path="address">
            <n-input v-model:value="form.address" placeholder="请输入相对位置" clearable></n-input>
          </n-form-item>
          <n-form-item label="行政区划" path="xzqh">
            <n-input v-model:value="form.xzqh" placeholder="请输入行政区划" clearable></n-input>
          </n-form-item>
          <n-form-item label="测点位置" path="cdwz">
            <n-input v-model:value="form.cdwz" placeholder="请输入测点位置" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="描述" path="remarks">
        <n-input v-model:value="form.remarks" type="textarea" rows="6"></n-input>
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
        <h2 class="text-center">地理位置</h2>
        <div style="position: absolute; right: 10px; top: 10px">
          <Print :printable="refDetail" size="large"></Print>
        </div>
        <n-table class="info-table" :single-line="false">
          <tr>
            <td>四有档案</td>
            <td colSpan="3">{{ form.fourCulturalRecordFourCulturalRecord.name || '--' }}</td>
            <td>相对位置</td>
            <td colSpan="4">{{ form.address || '--' }}</td>
          </tr>
          <tr>
            <td>海拔高程</td>
            <td colSpan="3">{{ form.hbgd || '--' }}米</td>
            <td>行政区划</td>
            <td colSpan="4">{{ form.xzqh || '--' }}</td>
          </tr>
          <tr>
            <td>经纬度</td>
            <td colSpan="3">{{ form.latlng || '--' }}</td>
            <td>测点位置</td>
            <td colSpan="4">{{ form.cdwz || '--' }}</td>
          </tr>
          <tr>
            <td>描述</td>
            <td colSpan="8">{{ form.remarks || '--' }}</td>
          </tr>
        </n-table>
      </div>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../../api/four/fourCulturalRecordMainText2';
import { ref, onMounted } from 'vue';
import { cloneDeep } from 'lodash-es';
import Print from '@/components/basic/print/Print.vue';
import { HFieldLatlng } from '@/components/form/field';
import CulturalRecordSelect from '../CulturalRecordSelect.vue';

const refDetail = ref();
const columns = [
  {
    key: 'fourCulturalRecordFourCulturalRecord.name',
    title: '四有档案',
    ellipsis: { tooltip: true },
    titleAlign: 'center',
    columnType: 'title',
    width: '120px',
  },
  { key: 'lat', title: '纬度', titleAlign: 'center', width: '120px' },
  { key: 'lng', title: '经度', titleAlign: 'center', width: '120px' },
  { key: 'hbgd', title: '海拔高程(米)', titleAlign: 'center', width: '140px' },
  { key: 'xzqh', title: '行政区划', titleAlign: 'center', ellipsis: { tooltip: true } },
  { key: 'cdwz', title: '测点位置', titleAlign: 'center', ellipsis: { tooltip: true } },
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
  lat: '',
  lng: '',
  hbgd: '',
  cdwz: '',
  xzqh: '',
  address: '',
  remarks: '',
  latlng: '',
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
};

/**
 * 查询表单
 * */
const paramsForm = ref({
  fourCulturalRecordId: props.fourCulturalRecordId,
  hbgd: '',
  xzqh: '',
  cdwz: '',
});

const formType = ref('add');
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
  get(row.id).then((resp: any) => {
    Object.assign(form.value, resp.data);
    formModel.value = true;
  });
  formTitle.value = `编辑地理位置`;
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view';
  Object.assign(form.value, row);
  get(row.id).then((resp: any) => {
    Object.assign(form.value, resp.data);
    detailModel.value = true;
  });
  formTitle.value = `查看地理位置`;
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
