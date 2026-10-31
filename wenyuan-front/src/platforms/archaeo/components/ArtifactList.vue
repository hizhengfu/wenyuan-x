<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1800"
    :isAdd="false"
    :isEdit="false"
    :params="paramsForm"
    @rowView="handleView"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="文物名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入文物名称" clearable></n-input>
      </n-form-item>
      <n-form-item label-width="68" label="编号" path="code">
        <n-input v-model:value="paramsForm.code" placeholder="输入编号" clearable></n-input>
      </n-form-item>
      <template v-if="!queryHidden">
        <n-form-item label="遗址名称" path="projectId">
          <WmsProjectSelect
            v-model:value="paramsForm.projectId"
            placeholder="请输入遗址名称"
            clearable
          ></WmsProjectSelect>
        </n-form-item>
        <n-form-item v-if="paramsForm.projectId" label="遗迹单位" path="projectCellId">
          <WmsProjectCellSelect
            v-model:value="paramsForm.projectCellId"
            :projectId="paramsForm.projectId"
            placeholder="请选择遗迹单位"
            clearable
          ></WmsProjectCellSelect>
        </n-form-item>
        <n-form-item label="质地" label-width="68" path="material">
          <HDictSelect
            v-model:value="paramsForm.material"
            multiple
            clearable
            max-tag-count="responsive"
            type="wms_artifact_material_type"
            placeholder="请选择质地"
          ></HDictSelect>
        </n-form-item>
      </template>
      <n-form-item label="库存状态" path="status">
        <HDictSelect
          v-model:value="paramsForm.status"
          clearable
          type="wms_artifact_storage_status"
          placeholder="请选择库存状态"
        ></HDictSelect>
      </n-form-item>
      <n-form-item label="出库原因" path="storageType" v-if="paramsForm.status === '2'">
        <HDictSelect
          v-model:value="paramsForm.storageType"
          multiple
          clearable
          max-tag-count="responsive"
          type="wms_artifact_outbound_type"
          placeholder="请选择出库原因"
        ></HDictSelect>
      </n-form-item>
      <n-form-item label="保存状态" path="artifactStatus">
        <HDictSelect
          v-model:value="paramsForm.artifactStatus"
          type="wms_artifact_save_status"
          clearable
          placeholder="请选择文物保存状态"
        ></HDictSelect>
      </n-form-item>
      <n-form-item label="出土日期" path="unearthDateList">
        <n-date-picker
          clearable
          v-model:value="paramsForm.unearthDateList"
          type="daterange"
          placeholder="请输入出土日期"
        />
      </n-form-item>
    </template>
    <h-page-form
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :refTable="refPageTable"
      :handleSubmit="saveExtend"
      v-model:show="formModel"
    >
      <n-grid x-gap="15" cols="2">
        <n-grid-item>
          <ArtifactExtendForm :form="form"></ArtifactExtendForm>
        </n-grid-item>
        <n-grid-item :id="rightWrapId" style="transform: translate(0, 0)">
          <n-form-item label="文物图像">
            <ArtifactImagesForm
              placement="left"
              :contentId="form.artifactId"
              :modelWrapId="'#' + rightWrapId"
            ></ArtifactImagesForm>
          </n-form-item>
          <n-divider></n-divider>
          <n-form-item label="文物线图" path="lineDiagram">
            <ArtifactLineImagesForm
              :contentId="form.artifactId"
              :modelWrapId="'#' + rightWrapId"
            ></ArtifactLineImagesForm>
          </n-form-item>
        </n-grid-item>
      </n-grid>
    </h-page-form>
    <h-page-view v-model:show="formViewModel" :title="formTitle">
      <ArtifactBookDetail v-if="activeArtifactId" :id="activeArtifactId"></ArtifactBookDetail>
    </h-page-view>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { getExtend, saveExtend } from '../api/artifact';
import { list } from '../api/artifactStock.ts';
import { h, ref, watch, watchEffect } from 'vue';
import { cloneDeep } from 'lodash-es';
import { WmsProjectCellSelect, WmsProjectSelect } from './form/field';
import ArtifactExtendForm from './ArtifactExtendForm.vue';
import ArtifactBookDetail from './ArtifactBookDetail.vue';
import { NButton } from 'naive-ui';
import ArtifactImagesForm from './form/ArtifactImagesForm.vue';
import ArtifactLineImagesForm from './form/ArtifactLineImagesForm.vue';
import { uuid } from '@/utils';

const props = defineProps({
  queryHidden: { type: Boolean, default: false }, //主要是分账情况下隐藏
  projectId: { type: String },
  projectCellId: { type: String },
  projectPlotId: { type: String },
  projectCellType: { type: String },
});

const rightWrapId = 'wrap_' + uuid();

const columns = [
  {
    key: 'name',
    title: '文物名称',
    columnType: 'title',
    width: '220px',
    fixed: 'left',
    resizable: true,
    ellipsis: true,
  },
  { key: 'status', title: '库房状态', dictType: 'wms_artifact_storage_status', width: '100px', align: 'center' },
  { key: 'image', title: '缩略图', columnType: 'image', width: '80px' },
  { key: 'project.name', title: '遗址名称', width: '160px', resizable: true, ellipsis: true },
  { key: 'code', title: '编号', width: '160px', resizable: true, ellipsis: true },
  {
    key: 'material',
    title: '质地',
    width: '160px',
    dictType: 'wms_artifact_material_type',
    resizable: true,
    ellipsis: true,
  },
  {
    key: 'quantity',
    title: '数量',
    width: '100px',
    align: 'right',
    render: (row: any) => {
      return `${row.quantity}${row.quantityUnit}`;
    },
  },
  { key: 'saveStatus', title: '保存状况', width: '200px', resizable: true, ellipsis: true },
  { key: 'unearthDate', title: '出土日期', columnType: 'date', width: '120px' },
  { key: 'coordinate', title: '出土坐标', width: '160px', resizable: true, ellipsis: true },
  { key: 'recorderBy', title: '记录人', width: '100px', resizable: true, ellipsis: true },
  /*{
    key: 'operate',
    type: 'operate',
    fixed: 'right',
    title: '操作',
    align: 'center',
    width: '100px',
    render(row: any) {
      return h(
        NButton,
        {
          size: 'tiny',
          type: 'warning',
          onClick() {
            handleEdit(row);
          },
        },
        { default: () => '文物研究' },
      );
    },
  },*/
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
  artifactId: '',
  timePeriod: undefined, //时代
  culturalAttribute: '', //文化属性
  purpose: '', //用途
  shape: '', //形制
  decoration: '', //纹饰
  craftingTechnique: '', //制作工艺
  lineDiagram: '', //文物线图
  dimensions: '', //尺寸（长×宽×高/cm）
  weight: 0, //重量（kg）
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {
  timePeriod: {
    required: true,
    message: '请选择时代',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  status: '',
  name: '',
  code: '',
  preservation: '',
  unearthDateList: undefined,
  material: undefined,
  artifactStatus: undefined,
  storageType: undefined,
  projectId: undefined,
  projectPlotId: undefined,
  projectCellId: undefined,
  projectCellType: undefined,
});

const formType = ref('add');

/**
 * 编辑事件
 * */
function handleEdit(row: any) {
  formType.value = 'edit';
  form.value = cloneDeep(defaultForm);
  form.value.artifactId = row.id;
  getExtend(row.id).then((resp) => {
    Object.assign(form.value, resp.data);
    formModel.value = true;
  });
  formTitle.value = `编辑「${row.name}」`;
}

/**
 * 数据查看
 * */
const activeArtifactId = ref<string>();
const formViewModel = ref(false);

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view';
  activeArtifactId.value = row.id;
  formViewModel.value = true;
  formTitle.value = `查看「${row.name}」`;
}

watch(
  () => props,
  (newProps) => {
    const { projectId, projectPlotId, projectCellId, projectCellType } = newProps;
    Object.assign(paramsForm.value, {
      projectId: projectId,
      projectPlotId: projectPlotId,
      projectCellId: projectCellId,
      projectCellType: projectCellType,
    });

    refPageTable.value?.handleRefresh();
  },
  {
    deep: true,
  },
);
</script>
<style scoped>
:deep(.n-upload-file-list.n-upload-file-list--grid) {
  grid-template-columns: repeat(auto-fill, 200px);
}

:deep(.n-upload-trigger.n-upload-trigger--image-card),
:deep(.n-upload-file-list .n-upload-file.n-upload-file--image-card-type) {
  width: 200px; /* 设置你期望的宽度 */
  height: 160px; /* 设置你期望的高度 */
}
</style>
