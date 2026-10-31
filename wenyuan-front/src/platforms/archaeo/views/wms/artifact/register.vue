<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :params="paramsForm"
    permission="WMS_ARTIFACT"
    :scroll-x="1800"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="文物名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入文物名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="编号" path="code">
        <n-input v-model:value="paramsForm.code" placeholder="输入编号" clearable></n-input>
      </n-form-item>
      <n-form-item label="所属项目" path="projectId">
        <WmsProjectSelect
          v-model:value="paramsForm.projectId"
          placeholder="请输入所属项目"
          clearable
        ></WmsProjectSelect>
      </n-form-item>
      <n-form-item label="材质分类" path="material">
        <HDictSelect
          v-model:value="paramsForm.material"
          multiple
          clearable
          type="wms_artifact_material_type"
          placeholder="请选择材质类型"
        ></HDictSelect>
      </n-form-item>
    </template>
    <template #activeBtn>
      <h-dialog-import-excel-form
        ref="importExcelFormRef"
        v-if="authStore.hasPermissionByCode('CREATE', 'WMS_ARTIFACT')"
        export-template-url="wms/artifact/exportTemplate"
        uploadDesc="请根据模版内容填报，列名不能修改。"
        exportLabel="点击导出示例模版"
        exportFileName="文物登记示例模版.xlsx"
        @uploadFinish="handleImportFinish"
        file-upload-url="wms/artifact/import"
      ></h-dialog-import-excel-form>
    </template>
    <h-page-form
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :refTable="refPageTable"
      autoReload
      :disabled="formType === 'view'"
      :handleSubmit="handleSubmit"
      v-model:show="formModel"
    >
      <n-grid x-gap="15">
        <n-grid-item span="16">
          <n-form-item label="文物名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入文物名称" clearable></n-input>
          </n-form-item>
          <n-form-item label="编号" path="code">
            <n-input v-model:value="form.code" placeholder="输入编号" clearable></n-input>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="8" class="flex-y-center">
          <n-form-item label="缩略图" path="image">
            <h-avatar v-model:value="form.image" size="220"></h-avatar>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-grid x-gap="15">
        <n-grid-item span="12">
          <n-form-item label="遗址名称" path="projectId">
            <WmsProjectSelect v-model:value="form.projectId" placeholder="请输入遗址名称" clearable></WmsProjectSelect>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="12">
          <n-form-item label="出土日期" path="unearthDate">
            <n-date-picker clearable v-model:value="form.unearthDate" type="date" placeholder="请输入出土日期" />
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-grid x-gap="15">
        <n-grid-item span="12">
          <n-form-item label="质地" path="material">
            <HDictSelect
              v-model:value="form.material"
              clearable
              showAdd
              type="wms_artifact_material_type"
              placeholder="请选择质地"
            ></HDictSelect>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="12">
          <n-form-item label="数量" path="quantity">
            <n-input-group>
              <n-input-number v-model:value="form.quantity" min="1" placeholder="请输入数量" clearable></n-input-number>
              <n-input-group-label>件</n-input-group-label>
              <!--              <HDictSelect
                style="width: 80px"
                v-model:value="form.quantityUnit"
                type="wms_artifact_unit_type"
                placeholder="请选择数量单位"
              ></HDictSelect>-->
            </n-input-group>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-grid x-gap="15">
        <n-grid-item span="12">
          <n-form-item label="出土单位" path="projectCell">
            <WmsProjectCellSelect
              v-model:value="form.projectCell"
              :projectId="form.projectId"
              placeholder="请选择出土单位"
              showAdd
              clearable
            ></WmsProjectCellSelect>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="12">
          <n-form-item label="经纬度" path="latlng">
            <HFieldLatlng v-model:value="form.latlng" placeholder="请输入经纬度" clearable></HFieldLatlng>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-grid cols="3" x-gap="8">
        <n-grid-item>
          <n-form-item label="出土坐标" path="n">
            <n-input-group>
              <n-input-group-label>N</n-input-group-label>
              <n-input v-model:value="form.n" placeholder="请输入N坐标"></n-input>
            </n-input-group>
          </n-form-item>
        </n-grid-item>
        <n-grid-item>
          <n-form-item label="" path="e" required>
            <n-input-group>
              <n-input-group-label>E</n-input-group-label>
              <n-input v-model:value="form.e" placeholder="请输入E坐标"></n-input>
            </n-input-group>
          </n-form-item>
        </n-grid-item>
        <n-grid-item>
          <n-form-item label="" path="z" required>
            <n-input-group>
              <n-input-group-label>Z</n-input-group-label>
              <n-input v-model:value="form.z" placeholder="请输入Z坐标"></n-input>
            </n-input-group>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-form-item label="出土状况" path="preservation">
        <HDictRadio
          v-model:value="form.preservation"
          type="wms_artifact_preserve_status"
          placeholder="请选择出土状况"
        ></HDictRadio>
      </n-form-item>
      <n-form-item label="记录人" path="recorderBy">
        <n-input v-model:value="form.recorderBy" placeholder="请输入记录人" clearable></n-input>
      </n-form-item>
      <n-form-item label="文物备注" path="remarks">
        <n-input type="textarea" v-model:value="form.remarks"></n-input>
      </n-form-item>
      <!--      <n-form-item label="文物图像">-->
      <!--        <ArtifactImagesForm :contentId="form.id"></ArtifactImagesForm>-->
      <!--      </n-form-item>-->
      <HUpload :contentId="form.id" :form="form" label="上传附件" :disabled="formType === 'view'"></HUpload>
    </h-page-form>
    <h-page-view v-model:show="formViewModel" :title="formTitle">
      <ArtifactRegisterView :id="activeArtifactId"></ArtifactRegisterView>
    </h-page-view>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { add, del, edit, get, list } from '../../../api/artifact';
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { WmsProjectCellSelect, WmsProjectSelect } from '../../../components/form/field';
import ArtifactRegisterView from '../../../components/ArtifactRegisterView.vue';
import { useAuthStore } from '@/store';
import { clearAll } from '@/modules/sys/api/dict';

const authStore = useAuthStore();

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
  { key: 'image', title: '缩略图', columnType: 'image', width: '80px' },
  { key: 'project.name', title: '遗址名称', width: '160px', resizable: true, ellipsis: true },
  { key: 'projectCellName', title: '出土单位', width: '160px', resizable: true, ellipsis: true },
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
  { key: 'preservation', title: '出土状况', width: '200px', resizable: true, ellipsis: true },
  { key: 'unearthDate', title: '出土日期', columnType: 'date', width: '120px' },
  { key: 'coordinate', title: '出土坐标', width: '160px', resizable: true, ellipsis: true },
  { key: 'recorderBy', title: '记录人', width: '100px', resizable: true, ellipsis: true },
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
  id: '',
  image: '',
  name: '',
  projectId: undefined,
  project: { name: '' },
  projectCell: '',
  code: '',
  categoryId: '',
  material: '',
  quantity: 1,
  quantityUnit: '件',
  year: '',
  dimensions: '',
  weight: null,
  preservation: '',
  coordinate: '',
  unearthDate: undefined,
  location: '',
  lat: '',
  lng: '',
  n: '',
  e: '',
  z: '',
  latlng: '',
  remarks: '',
  recorderBy: '',
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
  name: {
    required: true,
    message: '请输入文物名称',
  },
  projectId: {
    required: true,
    message: '请输入所属考古项目',
  },
  unearthDate: {
    required: true,
    message: '请选择出土日期',
  },
  material: {
    required: true,
    message: '请输入文物材质',
  },
  quantity: {
    required: true,
    message: '请输入数量',
  },
  n: {
    required: true,
    message: '请输入N坐标',
  },
  e: {
    required: true,
    message: '请输入E坐标',
  },
  z: {
    required: true,
    message: '请输入Z坐标',
  },
  quantityUnit: {
    required: true,
    message: '请输入数量单位',
  },
  preservation: {
    required: true,
    message: '请选择文物保存状况',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  status: '0',
  name: '',
  code: '',
  material: undefined,
  projectId: undefined,
});

const formType = ref('add');

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

    let coordinate: string[] = form.value.coordinate?.split(',');
    if (coordinate && coordinate.length === 3) {
      form.value.n = coordinate[0];
      form.value.e = coordinate[1];
      form.value.z = coordinate[2];
    }

    formModel.value = true;
  });
  formTitle.value = `编辑「${row.name}」`;
}

/**
 * 提交
 * */
function handleSubmit(data: any) {
  const { n, e, z } = form.value;

  if (n && e && z) {
    form.value.coordinate = `${n},${e},${z}`;
  } else {
    form.value.coordinate = '';
  }

  if (formType.value === 'add') {
    return add(data);
  } else {
    return edit(data);
  }
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

const importExcelFormRef = ref();

function handleImportFinish() {
  importExcelFormRef.value?.handClose();
  clearAll();
  refPageTable.value?.handleRefresh();
}
</script>
