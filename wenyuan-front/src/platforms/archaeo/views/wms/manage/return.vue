<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :params="paramsForm"
    :scroll-x="1800"
    :isAdd="false"
    :isEdit="false"
    :printTemplate="outboundPrintTemplate"
    @rowView="handleView"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="编号" path="code" label-width="80px">
        <n-input v-model:value="paramsForm.code" placeholder="输入编号" clearable></n-input>
      </n-form-item>
      <n-form-item label="出库时间" path="operationTimeList">
        <n-date-picker
          clearable
          v-model:value="paramsForm.operationTimeList"
          type="daterange"
          placeholder="请输入出库时间"
        />
      </n-form-item>
      <n-form-item label="出库原因" path="outPurpose">
        <HDictSelect
          v-model:value="paramsForm.outPurpose"
          type="wms_artifact_outbound_type"
          placeholder="请选择出库原因"
          clearable
        ></HDictSelect>
      </n-form-item>
    </template>
    <h-page-form
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      autoReload
      :refTable="refPageTable"
      :handleSubmit="handleSubmit"
      v-model:show="formModel"
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
      <n-form-item label="归还日期" path="actualReturn">
        <n-date-picker v-model:value="form.actualReturn" type="date" placeholder="请选择归还日期"></n-date-picker>
      </n-form-item>
      <n-form-item label="归库备注" path="remarks">
        <n-input type="textarea" v-model:value="form.remarks" placeholder="请输入归库备注"></n-input>
      </n-form-item>
      <ArtifactOperationReturnList
        v-model:checked-row-keys="artifactIdList"
        :data="refArtifactData"
        v-model:select-data="form.artifactList"
        allow-edit
      ></ArtifactOperationReturnList>
    </h-page-form>
    <h-page-view v-model:show="formViewModel" :title="formTitle">
      <ArtifactOperationOutboundView :record-id="activeRecordId"></ArtifactOperationOutboundView>
    </h-page-view>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { artifactList, list, returnOperation } from '../../../api/operation';
import { h, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { WmsLocationSelect } from '../../../components/form/field';
import ArtifactOperationOutboundView from '../../../components/ArtifactOperationOutboundView.vue';
import { NButton } from 'naive-ui';
import ArtifactOperationReturnList from '../../../components/ArtifactOperationReturnList.vue';
import outboundPrintTemplate from '../../../assets/templates/OutboundPrintTemplate.html?raw';

const columns = [
  {
    type: 'selection',
    fixed: 'left',
  },
  {
    key: 'code',
    fixed: 'left',
    title: '出库编号',
    columnType: 'title',
    width: '200px',
    resizable: true,
    ellipsis: true,
  },
  { key: 'operationDetail.artifactDest', title: '文物去向', width: '160px', resizable: true, ellipsis: true },
  { key: 'operationDetail.outPurpose', title: '出库原因', width: '160px', resizable: true, ellipsis: true },
  { key: 'operationDetail.receiver', title: '接收方', width: '160px', resizable: true, ellipsis: true },
  { key: 'operationDetail.needReturn', title: '是否归还', width: '80px', align: 'center', dictType: 'sys_ok' },
  { key: 'operationDetail.expectedReturn', title: '预计归还日期', columnType: 'date', width: '100px' },
  {
    key: 'sumQuantity',
    title: '总数量',
    width: '100px',
    align: 'right',
  },
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
          disabled: row.flowStatus === 1,
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

/**
 * 表单默认值
 * */
const defaultForm = {
  artifactList: [],
  locationId: undefined,
  recordId: undefined,
  receiver: undefined,
  actualReturn: undefined,
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
  remarks: {
    required: true,
    message: '请选择归库备注',
  },
  locationId: {
    required: true,
    message: '请选择归库位置',
  },
  actualReturn: {
    required: true,
    message: '请选择归还日期',
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

const activeRecordId = ref();
const activeArtifactId = ref();
/**
 * 查询表单
 * */
const paramsForm = ref({
  type: '1',
  artifactName: '',
  code: '',
  remarks: '',
  operationTime: '',
  operationTimeList: undefined,
  storageRemarks: undefined,
  material: undefined,
  outPurpose: undefined,
});

const refArtifactData = ref([]);

const artifactIdList = ref();

function fetchArtifactList(recordId: string) {
  artifactList({ recordId: recordId, type: 1 }).then((resp: any) => {
    let data = resp.data || [];
    artifactIdList.value = data
      .filter((item: any) => item.flowStatus === 0)
      .map((item: any) => {
        return item.id;
      });
    refArtifactData.value = data;
  });
}

/**
 * 编辑事件
 * */
function handleEdit(row: any) {
  form.value = cloneDeep(defaultForm);
  form.value.recordId = row.id;
  fetchArtifactList(row.id);
  formModel.value = true;
  formTitle.value = `「${row.code}」归库`;
}

const formView = ref();

/**
 * 查看事件
 * */
function handleView(row: any) {
  formView.value = row;
  activeRecordId.value = row.id;
  formViewModel.value = true;
  formTitle.value = `查看「${row.code}」`;
}

/**
 * 提交
 * */
function handleSubmit(data: any) {
  return returnOperation(data);
}


</script>
