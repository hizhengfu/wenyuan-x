<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="2000"
    :isAdd="false"
    :isEdit="false"
    :isBatchDelete="false"
    :params="paramsForm"
    @rowView="handleView"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="文物名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入文物名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="编号" path="code">
        <n-input v-model:value="paramsForm.code" placeholder="输入编号" clearable></n-input>
      </n-form-item>
      <n-form-item label="遗址" path="projectId">
        <WmsProjectSelect v-model:value="paramsForm.projectId" clearable filterable></WmsProjectSelect>
      </n-form-item>
      <n-form-item v-if="paramsForm.projectId" label="遗迹单位" path="projectCellId">
        <WmsProjectCellSelect
          v-model:value="paramsForm.projectCellId"
          :projectId="paramsForm.projectId"
          clearable
          filterable
        ></WmsProjectCellSelect>
      </n-form-item>
      <n-form-item label="质地" path="material">
        <HDictSelect
          v-model:value="paramsForm.material"
          multiple
          clearable
          max-tag-count="responsive"
          type="wms_artifact_material_type"
        ></HDictSelect>
      </n-form-item>
      <n-form-item label="文化属性" path="culturalAttribute">
        <HDictSelect
          v-model:value="paramsForm.culturalAttribute"
          type="wms_artifact_cultural_attribute"
          multiple
          clearable
          max-tag-count="responsive"
        ></HDictSelect>
      </n-form-item>
      <n-form-item label="年代" path="timePeriod">
        <HDictTreeSelect
          v-model:value="paramsForm.timePeriod"
          type="wms_artifact_year"
          cascade
          check-strategy="all"
          multiple
          checkable
          filterable
          clearable
          max-tag-count="responsive"
        ></HDictTreeSelect>
      </n-form-item>
      <n-form-item label="用途" path="purpose">
        <n-input v-model:value="paramsForm.purpose" clearable />
      </n-form-item>
      <n-form-item label="型式" path="artifactType">
        <n-input v-model:value="paramsForm.artifactType" clearable />
      </n-form-item>
    </template>

    <template #activeBtn="{ checkedTableData }">
      <n-button
        type="primary"
        size="small"
        :disabled="!checkedTableData?.length"
        @click="handleExportWord(checkedTableData)"
      >
        <template #icon>
          <n-icon><IconMdiFileWordBox /></n-icon>
        </template>
        按模板导出 Word（{{ checkedTableData?.length || 0 }}）
      </n-button>
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

    <ArtifactExportDialog v-model:show="exportShow" :rows="selectedRows"></ArtifactExportDialog>
  </h-page-data-table>
</template>

<script lang="ts" setup>
import { getExtend, saveExtend } from '../../../api/artifact';
import { list } from '../../../api/artifactStock';
import { h, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { NButton, useMessage } from 'naive-ui';
import { WmsProjectCellSelect, WmsProjectSelect } from '../../../components/form/field';
import ArtifactExtendForm from '../../../components/ArtifactExtendForm.vue';
import ArtifactBookDetail from '../../../components/ArtifactBookDetail.vue';
import ArtifactImagesForm from '../../../components/form/ArtifactImagesForm.vue';
import ArtifactLineImagesForm from '../../../components/form/ArtifactLineImagesForm.vue';
import ArtifactExportDialog from '../../../components/ArtifactExportDialog.vue';
import { uuid } from '@/utils';

const message = useMessage();
const rightWrapId = 'wrap_' + uuid();

const refPageTable = ref();
const formModel = ref(false);
const formViewModel = ref(false);
const formTitle = ref();
const exportShow = ref(false);
const selectedRows = ref<any[]>([]);

const columns = [
  { type: 'selection', width: 40, fixed: 'left' },
  { key: 'name', title: '文物名称', columnType: 'title', width: '220px', fixed: 'left', resizable: true, ellipsis: true },
  { key: 'image', title: '缩略图', columnType: 'image', width: '80px' },
  { key: 'project.name', title: '遗址', width: '160px', resizable: true, ellipsis: true },
  { key: 'projectCellName', title: '遗迹单位', width: '140px', resizable: true, ellipsis: true },
  { key: 'code', title: '编号', width: '160px', resizable: true, ellipsis: true },
  {
    key: 'material',
    title: '质地',
    width: '140px',
    dictType: 'wms_artifact_material_type',
    resizable: true,
    ellipsis: true,
  },
  { key: 'extend.timePeriod', title: '时代', width: '120px', resizable: true, ellipsis: true },
  { key: 'extend.culturalAttribute', title: '文化属性', width: '140px', resizable: true, ellipsis: true },
  { key: 'extend.purpose', title: '用途', width: '120px', resizable: true, ellipsis: true },
  { key: 'extend.artifactType', title: '器型', width: '120px', resizable: true, ellipsis: true },
  { key: 'extend.shape', title: '形制', width: '200px', resizable: true, ellipsis: true },
  {
    key: 'operate',
    type: 'operate',
    fixed: 'right',
    title: '操作',
    align: 'center',
    width: '140px',
    render(row: any) {
      return [
        h(
          NButton,
          { size: 'tiny', type: 'warning', onClick: () => handleEdit(row) },
          { default: () => '编辑卡片' },
        ),
      ];
    },
  },
];

const defaultForm = {
  artifactId: '',
  timePeriod: undefined,
  culturalAttribute: '',
  purpose: '',
  artifactType: '',
  artifactStyle: '',
  artifactSubStyle: '',
  artifactStage: '',
  phaseName: '',
  sectionName: '',
  shape: '',
  decoration: '',
  craftingTechnique: '',
  lineDiagram: '',
  dimensions: '',
  weight: 0,
};
const form = ref(cloneDeep(defaultForm));
const rules = { timePeriod: { required: true, message: '请选择时代' } };

const paramsForm = ref({
  name: '',
  code: '',
  projectId: undefined,
  projectCellId: undefined,
  material: undefined,
  culturalAttribute: undefined,
  timePeriod: undefined,
  purpose: '',
  artifactType: '',
});

function handleEdit(row: any) {
  form.value = cloneDeep(defaultForm);
  form.value.artifactId = row.id;
  getExtend(row.id).then((resp) => {
    Object.assign(form.value, resp.data);
    formModel.value = true;
  });
  formTitle.value = `编辑卡片「${row.name}」`;
}

const activeArtifactId = ref<string>();
function handleView(row: any) {
  activeArtifactId.value = row.id;
  formViewModel.value = true;
  formTitle.value = `查看「${row.name}」`;
}

function handleExportWord(checkedTableData: any[]) {
  if (!checkedTableData?.length) {
    message.warning('请先勾选要导出的文物');
    return;
  }
  selectedRows.value = checkedTableData;
  exportShow.value = true;
}
</script>
