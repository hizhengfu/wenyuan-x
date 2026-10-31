<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :params="paramsForm"
    :scroll-x="1800"
    :isAdd="false"
    :isEdit="false"
    @rowView="handleView"
    v-model:checked-row-keys="artifactIdList"
    :columns="columns"
  >
    <template #activeBtn="{ checkedRowKeys, checkedTableData }">
      <n-tooltip trigger="hover" v-if="checkedRowKeys?.length">
        <template #trigger>
          <n-button type="primary" @click="handleBatch">
            <template #icon>
              <n-icon size="20px">
                <IconHYk />
              </n-icon>
            </template>
            归库({{ checkedRowKeys.length }})
          </n-button>
        </template>
        批量移库
      </n-tooltip>
      <h-page-form
        :rules="rules"
        :model="form"
        label-width="auto"
        title="批量移库"
        autoReload
        :refTable="refPageTable"
        :handleSubmit="handleSubmit"
        v-model:show="batchFormModel"
      >
        <n-form-item label="移库原因" path="reason">
          <HDictRadio
            v-model:value="form.reason"
            type="wms_artifact_transfer_type"
            placeholder="请选择移库原因"
          ></HDictRadio>
        </n-form-item>

        <n-grid x-gap="15" cols="2">
          <n-grid-item>
            <n-form-item label="出库位置" path="fromLocationId">
              <WmsLocationSelect v-model:value="form.fromLocationId" placeholder="请选择出库位置"></WmsLocationSelect>
            </n-form-item>
          </n-grid-item>
          <n-grid-item>
            <n-form-item label="移库位置" path="toLocationId">
              <WmsLocationSelect v-model:value="form.toLocationId" placeholder="请选择移库位置"></WmsLocationSelect>
            </n-form-item>
          </n-grid-item>
        </n-grid>

        <n-form-item label="移库备注" path="remarks">
          <n-input type="textarea" v-model:value="form.remarks" placeholder="请输入移库备注"></n-input>
        </n-form-item>
        <ArtifactOperationList
          v-model:checked-row-keys="artifactIdList"
          :data="checkedTableData"
          v-model:select-data="form.artifactList"
        ></ArtifactOperationList>
      </h-page-form>
    </template>
    <template #queryForm>
      <n-form-item label="文物名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入文物名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="编号" path="code">
        <n-input v-model:value="paramsForm.code" placeholder="输入编号" clearable></n-input>
      </n-form-item>
      <n-form-item label="遗址名称" path="projectId">
        <WmsProjectSelect
          v-model:value="paramsForm.projectId"
          placeholder="请输入遗址名称"
          clearable
        ></WmsProjectSelect>
      </n-form-item>
      <n-form-item label="材质分类" path="material">
        <HDictSelect
          v-model:value="paramsForm.material"
          multiple
          clearable
          type="wms_artifact_save_status"
          placeholder="请选择材质类型"
        ></HDictSelect>
      </n-form-item>
    </template>
    <h-page-form
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :refTable="refPageTable"
      :handleSubmit="handleSubmit"
      v-model:show="formModel"
    >
      <n-form-item label="选择文物" path="artifactId">
        <WmsArtifactSelect
          :disabled="true"
          v-model:value="form.artifactId"
          placeholder="请选择文物"
        ></WmsArtifactSelect>
      </n-form-item>
      <n-form-item label="移库原因" path="reason">
        <HDictRadio
          v-model:value="form.reason"
          type="wms_artifact_transfer_type"
          placeholder="请选择移库原因"
        ></HDictRadio>
      </n-form-item>
      <n-grid x-gap="15" cols="2">
        <n-grid-item>
          <n-form-item label="出库位置" path="fromLocationId">
            <WmsLocationSelect v-model:value="form.fromLocationId" placeholder="请选择出库位置"></WmsLocationSelect>
          </n-form-item>
        </n-grid-item>
        <n-grid-item>
          <n-form-item label="移库位置" path="toLocationId">
            <WmsLocationSelect v-model:value="form.toLocationId" placeholder="请选择移库位置"></WmsLocationSelect>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-form-item label="移库备注" path="remarks">
        <n-input type="textarea" v-model:value="form.remarks" placeholder="请输入移库备注"></n-input>
      </n-form-item>
    </h-page-form>
    <h-page-view v-model:show="formViewModel" :title="formTitle">
      <ArtifactRecordDetail v-if="activeArtifactId" :id="activeArtifactId"></ArtifactRecordDetail>
    </h-page-view>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list } from '../../../api/artifactStock.ts';
import { transfer } from '../../../api/operation.ts';
import { h, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { WmsArtifactSelect, WmsProjectSelect, WmsLocationSelect } from '../../../components/form/field';
import ArtifactRecordDetail from '../../../components/ArtifactRecordDetail.vue';
import { NButton, NIcon } from 'naive-ui';
import ArtifactOperationList from '@/platforms/asa_wms/components/ArtifactOperationList.vue';

const columns = [
  {
    type: 'selection',
    fixed: 'left',
  },
  { key: 'code', title: '编号',fixed: 'left', width: '160px', resizable: true, ellipsis: true, columnType: 'title' },
  { key: 'image', title: '缩略图', columnType: 'image', width: '80px' },
  {
    key: 'name',
    title: '文物名称',
    width: '220px',
    resizable: true,
    ellipsis: true,
  },
  {
    key: 'material',
    title: '质地',
    width: '160px',
    dictType: 'wms_artifact_material_type',
    resizable: true,
    ellipsis: true,
  },
  { key: 'project.name', title: '遗址名称', width: '160px', resizable: true, ellipsis: true },
  { key: 'projectCellName', title: '遗迹单位', width: '160px', resizable: true, ellipsis: true },
  {
    key: 'quantity',
    title: '数量',
    width: '100px',
    align: 'right',
    render: (row: any) => {
      return `${row.quantity}${row.quantityUnit}`;
    },
  },
  { key: 'preservation', title: '保存状况', width: '200px', resizable: true, ellipsis: true },
  { key: 'storageTime', title: '入库日期', columnType: 'date', width: '120px' },
  { key: 'recorderBy', title: '记录人', width: '100px', resizable: true, ellipsis: true },
  {
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
        { default: () => '移库' },
      );
    },
  },
];

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
const formViewModel = ref(false);
/**
 * 表格ref
 * */
const refPageTable = ref();

const formTitle = ref();
const artifactIdList = ref();

/**
 * 表单默认值
 * */
const defaultForm = {
  artifactId: undefined,
  artifactList: [],
  reason: undefined,
  fromLocationId: undefined,
  toLocationId: undefined,
  remarks: '',
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {
  artifactId: {
    required: true,
    message: '请选择出库文物',
  },
  reason: {
    required: true,
    message: '请选择移库类型',
  },
  toLocationId: {
    required: true,
    message: '请选择移库位置',
  },
  fromLocationId: {
    required: true,
    message: '请选择出库位置',
  },
};

const activeArtifactId = ref();
/**
 * 查询表单
 * */
const paramsForm = ref({
  status: '1',
  name: '',
  code: '',
  material: undefined,
  projectId: undefined,
});

/**
 * 编辑事件
 * */
function handleEdit(row: any) {
  form.value = cloneDeep(defaultForm);
  form.value.artifactId = row.id;
  formModel.value = true;
  formTitle.value = `「${row.name}」移库`;
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  activeArtifactId.value = row.id;
  formViewModel.value = true;
  formTitle.value = `查看「${row.name}」`;
}

/**
 * 提交
 * */
function handleSubmit(data: any) {
  if (data.artifactId) {
    data.artifactIdList = [data.artifactId];
  }
  return transfer(data);
}

const batchFormModel = ref();

function handleBatch() {
  form.value = cloneDeep(defaultForm);
  batchFormModel.value = true;
}
</script>
