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
    v-bind="$attrs"
    :columns="columns"
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
      <h-more :height="'860px'">
        <n-form-item label="基础档案" path="culturalRecordId">
          <AssociationIdSelect
            disabled
            v-model:value="form.culturalRecordId"
            placeholder="请选择基础档案"
            clearable
            :fetchdata="fetchList"
            labelField="publishName"
          ></AssociationIdSelect>
        </n-form-item>
        <n-form-item label="名称" path="name">
          <n-input v-model:value="form.name" placeholder="请输入名称" clearable></n-input>
        </n-form-item>
        <n-form-item label="保护范围" path="bhfw">
          <n-input type="textarea" v-model:value="form.bhfw" rows="5" placeholder="请输入保护范围"></n-input>
        </n-form-item>
        <n-form-item label="保护工程" path="bhgc">
          <n-input type="textarea" v-model:value="form.bhgc" rows="5" placeholder="请输入保护工程"></n-input>
        </n-form-item>
        <n-form-item label="建设控制地带" path="jskzdz">
          <n-input type="textarea" v-model:value="form.jskzdz" rows="5" placeholder="请输入建设控制地带"></n-input>
        </n-form-item>
        <n-form-item label="建设项目控制情况" path="jsxmkzqk">
          <n-input
            type="textarea"
            v-model:value="form.jsxmkzqk"
            rows="5"
            placeholder="请输入建设项目控制情况"
          ></n-input>
        </n-form-item>
        <n-form-item label="本体范围Geojson" path="btfwData">
          <n-input type="textarea" v-model:value="form.btfwData" rows="5" placeholder="请输入本体范围Geojson"></n-input>
        </n-form-item>
        <n-form-item label="保护范围Geojson" path="bhfwData">
          <n-input type="textarea" v-model:value="form.bhfwData" rows="5" placeholder="请输入保护范围Geojson"></n-input>
        </n-form-item>
        <n-form-item label="建设控制地带Geojson" path="jskzdzData">
          <n-input
            type="textarea"
            v-model:value="form.jskzdzData"
            rows="5"
            placeholder="请输入建设控制地带Geojson"
          ></n-input>
        </n-form-item>
        <n-form-item label="矢量文件上传" path="btfwPath">
          <h-upload-input v-model:value="form.btfwPath" accept=".zip,application/zip,.shp;" placeholder="请输入本体范围文件，支持shp格式的zip压缩包" clearable>上传文件</h-upload-input>
        </n-form-item>
        <!-- <n-form-item label="保护范围矢量文件" path="bhfwPath">
          <h-upload-input v-model:value="form.bhfwPath" accept=".zip,application/zip,.shp;" placeholder="请输入保护范围文件，支持shp格式的zip压缩包" clearable>上传文件</h-upload-input>
        </n-form-item>
        <n-form-item label="建设控制地带矢量文件" path="jskzdzPath">
          <h-upload-input v-model:value="form.jskzdzPath" accept=".zip,application/zip,.shp;" placeholder="请输入建设控制地带文件，支持shp格式的zip压缩包" clearable>上传文件</h-upload-input>
        </n-form-item> -->
      </h-more>
      <HUpload :contentId="form.id" :form="form" label="上传附件"></HUpload>
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
        <h2 class="text-center">保护范围、建设控制地带及建设项目控制情况</h2>
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
            <td>保护范围</td>
            <td colSpan="8">{{ form.bhfw || '--' }}</td>
          </tr>
          <tr>
            <td>保护工程</td>
            <td colSpan="8">{{ form.bhgc || '--' }}</td>
          </tr>
          <tr>
            <td>建设控制地带</td>
            <td colSpan="8">{{ form.jskzdz || '--' }}</td>
          </tr>
          <tr>
            <td>建设项目控制情况</td>
            <td colSpan="8">{{ form.jsxmkzqk || '--' }}</td>
          </tr>
          <tr>
            <td colSpan="9">
              <LeafletTianditu
                :center="center"
                :zoom="zoom"
                style="height: 360px"
                @ready="handleMapLoad"
              ></LeafletTianditu>
            </td>
          </tr>
        </n-table>
      </div>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../api/basic/twowire.ts';
import {list as fetchList} from  '../../api/basic/culturalRecordBasic.ts'
import { onMounted, ref, watch } from 'vue';
import { cloneDeep } from 'lodash-es';
import AssociationIdSelect from './AssociationIdSelect.vue';
import Print from '@/components/basic/print/Print.vue';
import { useLeafletStore } from '@/components/basic/leaflet/useLeaflet';
import LeafletTianditu from '@/components/basic/leaflet/LeafletTianditu.vue';


const useLeaflet = useLeafletStore();

const center = ref([34.33, 108.96]);
const zoom = ref(5);

const columns = [
  {
    key: 'name',
    title: '名称',
    columnType: 'title',
    ellipsis: { tooltip: { style: { maxWidth: '600px' } } },
    titleAlign: 'center',
  },
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
const props = defineProps({ basicId: { type: String, required: true } });

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  culturalRecordId: props.basicId,
  name: '',
  bhfw: '',
  jskzdz: '',
  bhgc: '',
  jsxmkzqk: '',
  btfwData: '',
  btfwDataJson: null,
  bhfwData: '',
  bhfwDataJson: null,
  jskzdzData: '',
  jskzdzDataJson: null,
  createBy: '',
  createTime: null,
  updateBy: '',
  updateTime: null,
  fourCulturalRecordFourCulturalRecord: { name: '' },
  btfwPath:'',
  // bhfwPath:'',
  // jskzdzPath:''
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {
  basicId: {
    required: true,
    message: '请选择基础档案',
  },
};

/**
 * 查询表单
 * */
const paramsForm = ref({
  culturalRecordId: props.basicId,
  name: '',
});

watch(
  () => props.basicId,
  (value) => {
    if (value) {
      paramsForm.value.culturalRecordId = value;
      refPageTable.value?.handleQuery();
    }
  },
);

const formType = ref('add');
const refDetail = ref();
const detailModel = ref(false);

function handleLoad(resp: any) {
  // if (resp.data && resp.data.total === 1) {
  //   handleView(resp.data.list[0]);
  // }
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
  formTitle.value = `编辑`;
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
  formTitle.value = `查看`;
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

function handleMapLoad() {
  useLeaflet.addWgs84GeoJsonLhx(form.value.btfwDataJson, form.value.bhfwDataJson, form.value.jskzdzDataJson);
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
