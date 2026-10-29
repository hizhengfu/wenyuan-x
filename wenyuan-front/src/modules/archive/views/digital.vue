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
    :funApiCopy="copy"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="关联类型" path="sourceType">
        <h-dict-select
          v-model:value="paramsForm.sourceType"
          type="archive_digital_source_type"
          clearable
          filterable
        ></h-dict-select>
      </n-form-item>
      <template v-if="paramsForm.sourceType">
        <n-form-item v-if="paramsForm.sourceType == 'CULTURAL_MUSEUM'" label="博物馆" label-width="68px" path="sourceId">
          <HCulturalMuseumSelect
            v-model:value="paramsForm.sourceId"
            placeholder="请选择关联博物馆"
            clearable
            filterable
          >
          </HCulturalMuseumSelect>
        </n-form-item>
        <n-form-item v-else label="文保单位" path="sourceId">
          <HCulturalBasicSelect
            v-model:value="paramsForm.sourceId"
            placeholder="请选择关联文保单位"
            clearable
            filterable
          >
          </HCulturalBasicSelect>
        </n-form-item>
      </template>
      <n-form-item label="采集时间" path="dateTimeList">
        <n-date-picker clearable v-model:value="paramsForm.dateTimeList" type="daterange" placeholder="请输入时间" />
      </n-form-item>
      <n-form-item label="成果类型" path="fileType">
        <h-dict-select
          v-model:value="paramsForm.fileType"
          type="archive_digital_file_type"
          clearable
          filterable
        ></h-dict-select>
      </n-form-item>
      <n-form-item label="数据状态" path="status">
        <h-dict-select v-model:value="paramsForm.status" type="content_status" clearable filterable></h-dict-select>
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
      <n-grid x-gap="6">
        <n-grid-item span="18">
          <n-form-item label="成果名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入成果名称" clearable></n-input>
          </n-form-item>
          <n-grid x-gap="6">
            <n-grid-item span="12">
              <n-form-item label="藏品分类" path="mainType">
                <h-dict-select
                  v-model:value="form.mainType"
                  type="archive_digital_category"
                  clearable
                  placeholder="请选择藏品分类"
                ></h-dict-select>
              </n-form-item>
            </n-grid-item>
            <n-grid-item span="12">
              <n-form-item label="所属地区" path="areaId">
                <HAreaSelect v-model:value="form.areaId" placeholder="请选择区域" clearable filterable></HAreaSelect>
              </n-form-item>
            </n-grid-item>
          </n-grid>
        </n-grid-item>
        <n-grid-item span="6" class="flex-center">
          <n-form-item path="image">
            <HAvatar v-model:value="form.image"></HAvatar>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-grid x-gap="6">
        <n-grid-item span="12">
          <n-form-item :label="form.mainType != '1' ? '藏品类型' : '建筑类别'" path="type">
            <h-dict-select
              v-model:value="form.type"
              :type="form.mainType != '1' ? 'archive_digital_material_type' : 'archive_digital_type'"
              multiple
              clearable
              filterable
              placeholder="请选择藏品分类"
            ></h-dict-select>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="12">
          <n-form-item label="藏品年代" path="year">
            <n-input v-model:value="form.year" placeholder="请输入藏品年代"></n-input>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-grid x-gap="6" v-if="form.mainType != '1'">
        <n-grid-item span="12">
          <n-form-item label="藏品尺寸" path="size">
            <n-input v-model:value="form.size" placeholder="请输入尺寸"></n-input>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="12">
          <n-form-item label="收藏单位" path="groupName">
            <n-input v-model:value="form.groupName" placeholder="请输入收藏单位"></n-input>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-grid x-gap="6">
        <n-grid-item span="12">
          <n-form-item label="采集时间" path="dateTime">
            <n-date-picker clearable v-model:value="form.dateTime" type="date" placeholder="请输入采集时间" />
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="12">
          <n-form-item label="成果类型" path="fileType">
            <h-dict-select
              v-model:value="form.fileType"
              type="archive_digital_file_type"
              clearable
              filterable
            ></h-dict-select>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-form-item label="文件路径" path="path" feedback="如果为模型文件最好为.gltf或.glb">
        <HUploadInput v-model:value="form.path" placeholder="请上传文件路径" clearable></HUploadInput>
      </n-form-item>
      <n-form-item label="设备型号" path="manufacturer">
        <n-input v-model:value="form.manufacturer" placeholder="请填写设备型号"></n-input>
      </n-form-item>
      <n-form-item label="制作参数" path="parameter">
        <n-input type="textarea" v-model:value="form.parameter" placeholder="请填写制作参数"></n-input>
      </n-form-item>
      <n-grid x-gap="6">
        <n-grid-item span="12">
          <n-form-item label="关联类型" path="sourceType">
            <h-dict-select
              v-model:value="form.sourceType"
              type="archive_digital_source_type"
              clearable
              filterable
            ></h-dict-select>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="12">
          <template v-if="form.sourceType">
            <n-form-item v-if="form.sourceType == 'CULTURAL_MUSEUM'" label="关联博物馆" path="sourceId">
              <HCulturalMuseumSelect v-model:value="form.sourceId" placeholder="请选择关联博物馆" clearable filterable>
              </HCulturalMuseumSelect>
            </n-form-item>
            <n-form-item v-else label="关联文保单位" path="sourceId">
              <HCulturalBasicSelect v-model:value="form.sourceId" placeholder="请选择关联文保单位" clearable filterable>
              </HCulturalBasicSelect>
            </n-form-item>
          </template>
        </n-grid-item>
      </n-grid>
      <n-form-item label="成果内容" path="content">
        <n-input type="textarea" rows="6" v-model:value="form.content"></n-input>
      </n-form-item>
      <n-form-item label="数据状态" path="status">
        <h-dict-radio v-model:value="form.status" type="content_status" clearable filterable></h-dict-radio>
      </n-form-item>
      <HUpload :contentId="form.id" label="上传附件" :disabled="formType === 'view'"></HUpload>
    </h-page-form>
    <h-page-form v-model:show="detailModel" :title="detailTitle" width="84%" :enable-cancel="false">
      <DigitalDetailPanel v-if="detailModel && currentDetailId" :id="currentDetailId" />
    </h-page-form>
    <HFileDialogPreview
      v-model:show="dialogFilePreview"
      :title="filePreviewTitle"
      :path="filePreviewPath"
      :previewType="filePreviewType"
    >
      <template #footer v-if="filePreviewType == 'DRAWING' || filePreviewType == 'IMAGE' || filePreviewType == 'MODEL'">
        <div>
          {{ filePreviewContent }}
        </div>
      </template>
    </HFileDialogPreview>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get, copy } from '../api/digital.ts';
import { h, ref, watch } from 'vue';
import { cloneDeep } from 'lodash-es';
import HAreaSelect from '@/modules/sys/components/form/field/AreaSelect.vue';
import { list as museumList } from '@/modules/archive/api/museum/cultural.ts';
import { list as basicList } from '@/modules/archive/api/basic/culturalRecordBasic.ts';
import HCulturalBasicSelect from '../components/field/CulturalBasicSelect.vue';
import HCulturalMuseumSelect from '../components/field/CulturalMuseumSelect.vue';
import { NA } from 'naive-ui';
import DigitalDetailPanel from '../components/digital/DigitalDetailPanel.vue';
// import { StationSelect } from '../../components/form/field';

const props = defineProps({
  queryType: { type: String },
  sourceId: { type: String },
});

const columns = [
  {
    key: 'name',
    title: '成果名称',
    columnType: 'title',
    width: '320px',
    resizable: true,
    ellipsis: { tooltip: { style: { maxWidth: '600px' } } },
  },
  {
    key: 'sourceName',
    title: '归属单位',
    width: '200px',
    resizable: true,
    ellipsis: true,
  },
  { key: 'image', title: '缩略图', width: '80px', columnType: 'image' },
  {
    key: 'fileType',
    title: '成果类型',
    dictType: 'archive_digital_file_type',
    align: 'center',
    resizable: true,
    ellipsis: true,
    width: '160px',
  },
  { key: 'dateTime', title: '采集时间', columnType: 'date', align: 'center', width: '200px' },
  {
    key: 'path',
    title: '成果查看',
    width: '120px',
    render(row: any) {
      return h(
        NA,
        {
          type: 'default',
          onClick: () => {
            handleFilePreview(row);
          },
        },
        { default: () => '内容预览' },
      );
    },
  },
  { key: 'status', title: '数据状态', dictType: 'content_status', align: 'center', width: '100px' },
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
const detailModel = ref(false);
const detailTitle = ref('');
const currentDetailId = ref('');

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  sourceId: '',
  sourceName: '',
  sourceType: 'CULTURAL_RECORD_BASIC',
  type: null,
  mainType: '1',
  fileType: '',
  name: '',
  image: '',
  content: '',
  dateTime: null,
  path: '',
  areaId: '',
  year: '',
  size: '',
  groupName: '',
  manufacturer: '',
  parameter: '',
  status: '0',
  createBy: '',
  createTime: null,
  updateBy: '',
  updateTime: null,
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {
  mainType: {
    required: true,
    message: '清选择藏品分类',
  },
  type: {
    required: true,
    message: '请选择成果类型',
  },
  name: {
    required: true,
    message: '请输入名称',
  },
  content: {
    required: true,
    message: '请输入描述内容',
  },
  fileType: {
    required: true,
    message: '请选择成果类型',
  },
  path: {
    required: true,
    message: '请输入路径',
  },
  dateTime: {
    required: true,
    message: '请输入时间',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  stationId: '',
  type: '',
  fileType: props.queryType ? props.queryType : '',
  sourceId: props.sourceId ? props.sourceId : '',
  sourceType: 'CULTURAL_RECORD_BASIC',
  name: '',
  dateTimeList: null,
  status: '',
});

const formType = ref('add');

const dialogFilePreview = ref(false);
const filePreviewTitle = ref();
const filePreviewPath = ref();
const filePreviewType = ref();
const filePreviewContent = ref();

watch(
  () => form.value.sourceId,
  (newVal) => {
    console.log(newVal, form.value.sourceType);

    const { apiCall, nameField } =
      form.value.sourceType == 'CULTURAL_MUSEUM'
        ? { apiCall: museumList, nameField: 'wdmc' }
        : { apiCall: basicList, nameField: 'publishName' };
    apiCall({ id: newVal }).then((resp) => {
      if (resp.data.list && resp.data.list[0]) {
        form.value.sourceName = resp.data.list[0][nameField];
      }
    });
  },
);

function handleFilePreview(row: any) {
  dialogFilePreview.value = true;
  filePreviewTitle.value = row.name;
  filePreviewPath.value = row.path;
  filePreviewType.value = row.fileType;
  filePreviewContent.value = row.content;
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
  formTitle.value = `编辑「${row.name}」`;
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  currentDetailId.value = row.id ? String(row.id) : '';
  detailTitle.value = `查看「${row.name || '--'}」`;
  detailModel.value = true;
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

watch(detailModel, (flag) => {
  if (!flag) {
    currentDetailId.value = '';
  }
});
</script>
