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
          <n-button type="primary" @click="batchFormModel = true">
            <template #icon>
              <n-icon size="20px">
                <IconHGk />
              </n-icon>
            </template>
            归库({{ checkedRowKeys.length }})
          </n-button>
        </template>
        批量归库
      </n-tooltip>
      <h-page-form
        :rules="rules"
        :model="form"
        label-width="auto"
        title="批量归库"
        :refTable="refPageTable"
        :handleSubmit="handleSubmit"
        v-model:show="batchFormModel"
      >
        <n-grid x-gap="15" cols="2">
          <n-grid-item>
            <n-form-item label="归库位置" path="locationId">
              <WmsLocationSelect v-model:value="form.locationId" placeholder="请选择归库位置"></WmsLocationSelect>
            </n-form-item>
          </n-grid-item>
          <n-grid-item>
            <n-form-item label="归还人" path="returnBy">
              <n-input v-model:value="form.returnBy" placeholder="请输入接收人"></n-input>
            </n-form-item>
          </n-grid-item>
        </n-grid>
        <n-grid x-gap="15" cols="2">
          <n-grid-item>
            <n-form-item label="接收人" path="receiver">
              <n-input v-model:value="form.receiver" placeholder="请输入接收人"></n-input>
            </n-form-item>
          </n-grid-item>
          <n-grid-item>
            <n-form-item label="核对人员" path="checkBy">
              <n-input v-model:value="form.checkBy" placeholder="请输入信息"></n-input>
            </n-form-item>
          </n-grid-item>
        </n-grid>
        <n-form-item label="归库备注" path="remarks">
          <n-input type="textarea" v-model:value="form.remarks" placeholder="请输入归库备注"></n-input>
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
      <n-form-item label="编号" path="code" label-width="80px">
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
      <n-form-item label="出库时间" path="storageTimeList">
        <n-date-picker
          clearable
          v-model:value="paramsForm.storageTimeList"
          type="daterange"
          placeholder="请输入出库时间"
        />
      </n-form-item>
      <n-form-item label="出库原因" path="remarks">
        <n-input v-model:value="paramsForm.storageRemarks" placeholder="请输入出库原因" clearable></n-input>
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
      <n-grid x-gap="15" cols="2">
        <n-grid-item>
          <n-form-item label="归库位置" path="locationId">
            <WmsLocationSelect v-model:value="form.locationId" placeholder="请选择归库位置"></WmsLocationSelect>
          </n-form-item>
        </n-grid-item>
        <n-grid-item>
          <n-form-item label="归还人" path="returnBy">
            <n-input v-model:value="form.returnBy" placeholder="请输入接收人"></n-input>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-grid x-gap="15" cols="2">
        <n-grid-item>
          <n-form-item label="接收人" path="receiver">
            <n-input v-model:value="form.receiver" placeholder="请输入接收人"></n-input>
          </n-form-item>
        </n-grid-item>
        <n-grid-item>
          <n-form-item label="核对人员" path="checkBy">
            <n-input v-model:value="form.checkBy" placeholder="请输入信息"></n-input>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-form-item label="归库备注" path="remarks">
        <n-input type="textarea" v-model:value="form.remarks" placeholder="请输入归库备注"></n-input>
      </n-form-item>
    </h-page-form>
    <h-page-view v-model:show="formViewModel" :title="formTitle">
      <ArtifactRecordDetail v-if="activeArtifactId" :id="activeArtifactId"></ArtifactRecordDetail>
    </h-page-view>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { returnOperation, list } from '../../../api/operation';
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
  { key: 'project.name', title: '遗址名称', width: '160px', resizable: true, ellipsis: true },
  { key: 'code', title: '编号', width: '160px', resizable: true, ellipsis: true },
  {
    key: 'sumQuantity',
    title: '总数量',
    width: '100px',
    align: 'right',
  },
  { key: 'preservation', title: '保存状况', width: '200px', resizable: true, ellipsis: true },
  { key: 'operationTime', title: '操作时间', columnType: 'dateTime', width: '160px' },
  { key: 'operatorBy', title: '操作人', width: '160px', resizable: true, ellipsis: true },
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
          type: 'success',
          onClick() {
            handleEdit(row);
          },
        },
        { default: () => '归库' },
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
  locationId: undefined,
  receiver: undefined,
  checkBy: '',
  returnBy: '',
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
    message: '请选择归库文物',
  },
  remarks: {
    required: true,
    message: '请选择归库备注',
  },
  locationId: {
    required: true,
    message: '请选择归库位置',
  },
  receiver: {
    required: true,
    message: '请输入接收人',
  },
  returnBy: {
    required: true,
    message: '请输入归还人',
  },
  checkBy: {
    required: true,
    message: '请输入检查人',
  },
};

const activeArtifactId = ref();
/**
 * 查询表单
 * */
const paramsForm = ref({
  status: '2',
  name: '',
  code: '',
  remarks: '',
  operationTime: '',
  storageTimeList: undefined,
  storageRemarks: undefined,
  material: undefined,
  projectId: undefined,
});

/**
 * 编辑事件
 * */
function handleEdit(row: any) {
  form.value = cloneDeep(defaultForm);
  form.value.artifactId = row.id;
  form.value.locationId = row.locationId;
  formModel.value = true;
  formTitle.value = `「${row.name}」归库`;
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
    data.artifactList = [{ artifactId: data.artifactId, quantity: data.quantity }];
  }
  return returnOperation(data);
}

const batchFormModel = ref();
</script>
