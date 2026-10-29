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
                <IconHRk />
              </n-icon>
            </template>
            入库({{ checkedRowKeys.length }})
          </n-button>
        </template>
        批量入库
      </n-tooltip>
      <h-page-form
        :rules="rules"
        :model="form"
        label-width="auto"
        title="批量入库"
        autoReload
        :refTable="refPageTable"
        :handleSubmit="handleSubmit"
        v-model:show="batchFormModel"
      >
        <n-form-item label="库房位置" path="locationId">
          <WmsLocationSelect v-model:value="form.locationId" placeholder="请选择库房位置"></WmsLocationSelect>
        </n-form-item>
        <n-grid x-gap="15" cols="24">
          <n-grid-item span="12">
            <n-form-item label="接收人" path="receiver">
              <n-input v-model:value="form.receiver"></n-input>
            </n-form-item>
          </n-grid-item>
          <n-grid-item span="12">
            <n-form-item label="移交人" path="transfer">
              <n-input v-model:value="form.transfer"></n-input>
            </n-form-item>
          </n-grid-item>
        </n-grid>
        <n-form-item label="入库原因" path="remarks">
          <n-input type="textarea" v-model:value="form.remarks"></n-input>
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
        <n-input v-model:value="paramsForm.code" placeholder="编号" clearable></n-input>
      </n-form-item>
      <n-form-item label="遗址名称" path="projectId">
        <WmsProjectSelect
          v-model:value="paramsForm.projectId"
          placeholder="请输入遗址名称"
          clearable
        ></WmsProjectSelect>
      </n-form-item>
      <n-form-item label="出土单位" path="projectCellId" v-if="paramsForm.projectId">
        <WmsProjectCellSelect
          v-model:value="paramsForm.projectCellId"
          :projectId="paramsForm.projectId"
          placeholder="请选择出土单位"
          showAdd
          clearable
        ></WmsProjectCellSelect>
      </n-form-item>
      <n-form-item label="出土日期" path="unearthDateList">
        <n-date-picker
          clearable
          v-model:value="paramsForm.unearthDateList"
          type="daterange"
          placeholder="请输入出土日期"
        />
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
      autoReload
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
      <n-form-item label="库房位置" path="locationId">
        <WmsLocationSelect v-model:value="form.locationId" placeholder="请选择库房位置"></WmsLocationSelect>
      </n-form-item>
      <n-form-item label="接收人" path="receiver">
        <n-input v-model:value="form.receiver"></n-input>
      </n-form-item>
      <n-form-item label="移交人" path="transfer">
        <n-input v-model:value="form.transfer"></n-input>
      </n-form-item>
      <n-form-item label="入库原因" path="remarks">
        <n-input type="textarea" v-model:value="form.remarks"></n-input>
      </n-form-item>
    </h-page-form>

    <h-page-view v-model:show="formViewModel" :title="formTitle">
      <ArtifactRegisterView v-if="activeArtifactId" :id="activeArtifactId"></ArtifactRegisterView>
    </h-page-view>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { get, list } from '../../../api/artifact';
import { inbound } from '../../../api/operation.ts';
import { h, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import {
  WmsArtifactSelect,
  WmsLocationSelect,
  WmsProjectCellSelect,
  WmsProjectSelect,
} from '../../../components/form/field';
import ArtifactRegisterView from '../../../components/ArtifactRegisterView.vue';
import ArtifactOperationList from '../../../components/ArtifactOperationList.vue';
import { NButton, NIcon } from 'naive-ui';

const columns = [
  {
    type: 'selection',
    fixed: 'left',
  },
  { key: 'code', title: '编号', width: '160px', fixed: 'left', resizable: true, ellipsis: true, columnType: 'title' },
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
  { key: 'unearthDate', title: '出土日期', columnType: 'date', width: '120px' },
  { key: 'coordinate', title: '出土坐标', width: '160px', resizable: true, ellipsis: true },
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
          type: 'primary',
          onClick() {
            handleEdit(row);
          },
        },
        { default: () => '入库' },
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

const artifactIdList = ref([]);
/**
 * 表单默认值
 * */
const defaultForm = {
  artifactId: undefined,
  locationId: undefined,
  artifactList: [],
  receiver: '',
  quantity: 1,
  transfer: '',
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
    message: '请选择入库文物',
  },
  artifactIdList: {
    required: true,
    message: '请选择入库文物',
  },
  locationId: {
    required: true,
    message: '请选择库房位置',
  },
  receiver: {
    required: true,
    message: '请输入接收人',
  },
  transfer: {
    required: true,
    message: '请输入移交人',
  },
};

const activeArtifactId = ref<string>();
/**
 * 查询表单
 * */
const paramsForm = ref({
  status: '0',
  name: '',
  code: '',
  unearthDateList: undefined,
  material: undefined,
  projectId: undefined,
  projectCellId: undefined,
});

/**
 * 编辑事件
 * */
function handleEdit(row: any) {
  form.value = cloneDeep(defaultForm);
  form.value.artifactId = row.id;
  form.value.quantity = row.quantity;
  formModel.value = true;
  formTitle.value = `「${row.name}」入库`;
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
  //单选情况
  if (data.artifactId) {
    data.artifactList = [{ artifactId: data.artifactId, quantity: data.quantity }];
  }
  return inbound(data);
}

//批量入库逻辑
const batchFormModel = ref();

function handleBatch() {
  form.value = cloneDeep(defaultForm);
  batchFormModel.value = true;
}
</script>
