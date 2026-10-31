<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :params="paramsForm"
    :scroll-x="1800"
    :isAdd="false"
    :isEdit="false"
    v-model:checked-row-keys="artifactIdList"
    @rowView="handleView"
    :columns="columns"
  >
    <template #activeBtn="{ checkedRowKeys, checkedTableData }">
      <n-tooltip trigger="hover" v-if="checkedRowKeys?.length">
        <template #trigger>
          <n-button type="primary" @click="handleBatch">
            <template #icon>
              <n-icon size="20px">
                <IconHCk />
              </n-icon>
            </template>
            出库({{ checkedRowKeys.length }})
          </n-button>
        </template>
        批量出库
      </n-tooltip>
      <h-page-form
        :rules="rules"
        :model="form"
        label-width="auto"
        title="批量出库"
        :refTable="refPageTable"
        :handleSubmit="handleSubmit"
        v-model:show="batchFormModel"
      >
        <n-form-item label="出库原因" path="outPurpose">
          <HDictRadio
            v-model:value="form.outPurpose"
            type="wms_artifact_outbound_type"
            placeholder="请选择出库原因"
          ></HDictRadio>
        </n-form-item>
        <n-form-item label="文物去向" path="artifactDest">
          <n-input type="textarea" v-model:value="form.artifactDest" placeholder="请输入文物去向"></n-input>
        </n-form-item>
        <n-flex>
          <n-form-item label="是否归还" path="needReturn">
            <n-switch v-model:value="form.needReturn" placeholder="请选择是否归还"></n-switch>
          </n-form-item>
          <n-form-item v-if="form.needReturn" label="预计归还时间" path="expectedReturn">
            <n-date-picker v-model:value="form.expectedReturn" placeholder="请选择预计归还时间"></n-date-picker>
          </n-form-item>
        </n-flex>
        <n-form-item label="接收人" path="receiver">
          <n-input v-model:value="form.receiver" placeholder="请输入接收人"></n-input>
        </n-form-item>
        <n-form-item label="出库备注" path="remarks">
          <n-input type="textarea" v-model:value="form.remarks" placeholder="请输入出库备注"></n-input>
        </n-form-item>
        <ArtifactOperationList
          v-model:checked-row-keys="artifactIdList"
          :data="checkedTableData"
          v-model:select-data="form.artifactList"
          allow-edit
        ></ArtifactOperationList>
      </h-page-form>
    </template>
    <template #queryForm>
      <n-form-item label="文物名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入文物名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="文物编号" path="code">
        <n-input v-model:value="paramsForm.code" placeholder="输入编号" clearable></n-input>
      </n-form-item>
      <n-form-item label="遗址名称" path="projectId">
        <WmsProjectSelect
          v-model:value="paramsForm.projectId"
          placeholder="请输入遗址名称"
          clearable
        ></WmsProjectSelect>
      </n-form-item>
      <n-form-item label="所属库房" path="roomId">
        <WmsRoomSelect v-model:value="paramsForm.roomId" placeholder="请选择文物库房" clearable></WmsRoomSelect>
      </n-form-item>
      <n-form-item label="存放位置" path="locationId">
        <WmsLocationSelect
          v-model:value="paramsForm.locationId"
          placeholder="请选择文物存放位置"
          clearable
        ></WmsLocationSelect>
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
      <n-form-item label="文物保存状态" path="saveStatus">
        <HDictRadio
          v-model:value="form.saveStatus"
          type="wms_artifact_save_status"
          placeholder="请选择文物保存状态"
        ></HDictRadio>
      </n-form-item>
      <n-form-item label="出库原因" path="outPurpose">
        <HDictRadio
          v-model:value="form.outPurpose"
          type="wms_artifact_outbound_type"
          placeholder="请选择出库原因"
        ></HDictRadio>
      </n-form-item>
      <n-form-item label="文物去向" path="artifactDest">
        <n-input type="textarea" v-model:value="form.artifactDest" placeholder="请输入文物去向"></n-input>
      </n-form-item>
      <n-flex>
        <n-form-item label="是否归还" path="needReturn">
          <n-switch v-model:value="form.needReturn" placeholder="请选择是否归还"></n-switch>
        </n-form-item>
        <n-form-item v-if="form.needReturn" label="预计归还时间" path="expectedReturn">
          <n-date-picker v-model:value="form.expectedReturn" placeholder="请选择预计归还时间"></n-date-picker>
        </n-form-item>
        <n-form-item label="出库件数" path="quantity">
          <n-input-number v-model:value="form.quantity" :min="1" placeholder="请输入出库件数"></n-input-number>
        </n-form-item>
      </n-flex>
      <n-form-item label="接收人" path="receiver">
        <n-input v-model:value="form.receiver" placeholder="请输入接收人"></n-input>
      </n-form-item>
      <n-form-item label="出库备注" path="remarks">
        <n-input type="textarea" v-model:value="form.remarks" placeholder="请输入出库备注"></n-input>
      </n-form-item>
    </h-page-form>
    <h-page-view
      v-model:show="formOperationOutboundViewModel"
      title="出库信息查看与打印"
      @afterLeave="handleOperationOutboundViewClose"
    >
      <ArtifactOperationOutboundView :record-id="operateRecordId"></ArtifactOperationOutboundView>
    </h-page-view>
    <h-page-view v-model:show="formViewModel" :title="formTitle">
      <ArtifactRecordDetail v-if="activeArtifactId" :id="activeArtifactId"></ArtifactRecordDetail>
    </h-page-view>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list } from '../../../api/artifactStock.ts';
import { outbound } from '../../../api/operation';
import { h, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { WmsArtifactSelect, WmsProjectSelect, WmsLocationSelect, WmsRoomSelect } from '../../../components/form/field';
import ArtifactRecordDetail from '../../../components/ArtifactRecordDetail.vue';
import { NButton, NIcon } from 'naive-ui';
import ArtifactOperationList from '../../../components/ArtifactOperationList.vue';
import ArtifactOperationOutboundView from '../../../components/ArtifactOperationOutboundView.vue';
import { useAppStore } from '@/store';

const columns = [
  {
    type: 'selection',
    fixed: 'left',
  },
  { key: 'code', title: '编号', fixed: 'left', width: '160px', resizable: true, ellipsis: true, columnType: 'title' },
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
  { key: 'storageRoom.name', title: '归属库房', width: '160px', resizable: true, ellipsis: true },
  { key: 'storageLocation.name', title: '存放位置', width: '160px', resizable: true, ellipsis: true },
  { key: 'storageTime', title: '入库时间', columnType: 'date', width: '120px' },

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
  { key: 'recorderBy', title: '记录人', width: '100px', resizable: true, ellipsis: true },
  {
    key: 'operate',
    type: 'operate',
    fixed: 'right',
    title: '操作',
    align: 'center',
    width: '120px',
    render(row: any) {
      return [
        h(
          NButton,
          {
            size: 'tiny',
            type: 'error',
            onClick() {
              handleEdit(row);
            },
          },
          { default: () => '出库' },
        ),
      ];
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
  outPurpose: undefined,
  storageType: undefined,
  receiver: '',
  quantity: 1,
  saveStatus: '',
  artifactDest: '',
  needReturn: true,
  expectedReturn: undefined,
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
  quantity: {
    required: true,
    message: '请输入文物件数',
  },
  receiver: {
    required: true,
    message: '请输入接收人',
  },
  outPurpose: {
    required: true,
    message: '请选择出库类型',
  },
  storageType: {
    required: true,
    message: '请选择出库类型',
  },
  saveStatus: {
    required: true,
    message: '请选择文物保存状态',
  },
  expectedReturn: {
    required: true,
    message: '请选择预计归还时间',
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
  locationId: undefined,
  roomId: undefined,
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
  formTitle.value = `「${row.name}」出库`;
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
 * 显示出库单以便打印
 * */
const formOperationOutboundViewModel = ref(false);
const operateRecordId = ref();

const useApp = useAppStore();

/**
 * 关闭出库单事件
 * */
function handleOperationOutboundViewClose() {
  useApp.reloadPage(100);
}

/**
 * 提交
 * */
function handleSubmit(data: any) {
  //单选情况
  if (data.artifactId) {
    data.artifactList = [{ artifactId: data.artifactId, saveStatus: data.saveStatus, quantity: data.quantity }];
  }
  return outbound(data).then((resp: any) => {
    formOperationOutboundViewModel.value = true;
    operateRecordId.value = resp.data;
  });
}

const batchFormModel = ref();

function handleBatch() {
  form.value = cloneDeep(defaultForm);
  batchFormModel.value = true;
}
</script>
