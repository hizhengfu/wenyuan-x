<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :params="paramsForm"
    v-bind="$attrs"
    @updateSort="handleUpdateSort"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
    @loadData="handleLoad"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="文件名" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入文件名" clearable></n-input>
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
      <n-form-item label="四有档案" path="fourCulturalRecordId">
        <CulturalRecordSelect
          disabled
          v-model:value="form.fourCulturalRecordId"
          placeholder="请选择四有档案"
          clearable
        ></CulturalRecordSelect>
      </n-form-item>
      <n-form-item label="文件名" path="name">
        <n-input v-model:value="form.name" placeholder="请输入文件名" clearable></n-input>
      </n-form-item>
      <n-form-item label="文件路径" path="path">
        <HUploadInput
          v-model:value="form.path"
          @uploadFinish="uploadFinish"
         
        ></HUploadInput>
      </n-form-item>
      <n-form-item label="备注" path="content">
        <n-input v-model:value="form.content" type="textarea"></n-input>
      </n-form-item>
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
        <h2 class="text-center">{{ form.name }}</h2>
        <template v-if="form.dir === true">
          <h-file-list :contentId="form.id"></h-file-list>
        </template>
        <ApiPdfPreview v-else :path="'/file/preview?fileId=' + form.fileId"></ApiPdfPreview>
      </div>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { add, del, edit, get, list, updateSort } from '../../api/four/fourCulturalRecordFile';
import { ref, watch } from 'vue';
import { cloneDeep } from 'lodash-es';
import CulturalRecordSelect from './CulturalRecordSelect.vue';
import ApiPdfPreview from '../../../../components/basic/ApiPdfPreview.vue';
import { useRoute } from 'vue-router';
import { notification } from '@/utils';

const route = useRoute();
const props = defineProps({
  fourCulturalRecordId: { type: String, required: true },
  title: { type: String },
  menuCode: { type: String, required: true },
  fourMenuId: { type: String },
});

const columns = [
  {
    key: 'name',
    title: '文件名',
    columnType: 'title',
    rowDrag: true,
    ellipsis: { tooltip: true },
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

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  fourCulturalRecordId: props.fourCulturalRecordId,
  name: '',
  fourMenuId: '',
  fileId: '',
  path: '',
  dir: false,
  sheetsNumber: null,
  content: '',
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {
  fourCulturalRecordId: {
    required: true,
    message: '请选择四有档案',
  },
  name: {
    required: true,
    message: '请输入文件名称',
  },
  fileId: {
    required: true,
    message: '请选择文件id',
  },
  fourMenuId: {
    required: true,
    message: '请选择档案目录',
  },
};

/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  fourCulturalRecordId: props.fourCulturalRecordId,
  fourMenuCode: props.menuCode,
});

const formType = ref('add');
const refDetail = ref();
const detailModel = ref(false);

function handleLoad(resp: any) {
  if (resp.data && resp.data.total === 1) {
    // handleView(resp.data.list[0]);
  }
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
  formTitle.value = `编辑${form.value.name}`;
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
  formTitle.value = `查看${form.value.name}`;
}

function handleUpdateSort(items: any) {
  let sorts = items.map((item: any, index: number) => {
    return { id: item.id, sort: index };
  });
  updateSort(sorts).finally(() => {
    refPageTable.value.handleRefresh();
  });
}

function uploadFinish(data: any) {
  if (data && data.id) {
    form.value.fileId = data.id;
  } else {
    form.value.fileId = '';
  }
}

/**
 * 提交
 * */
function handleSubmit(data: any) {
  if (formType.value === 'add') {
    data.fourMenuId = props.fourMenuId ?? route.query.fourMenuId;
    if (!data.fourMenuId) {
      notification({ type: 'error', content: '无四有菜单，不能提交' });
      return Promise.reject(false);
    }
    return add(data);
  } else {
    return edit(data);
  }
}

watch(
  props,
  (n) => {
    paramsForm.value.fourCulturalRecordId = n.fourCulturalRecordId;
    paramsForm.value.fourMenuCode = n.menuCode;
    refPageTable.value?.handleQuery();
    detailModel.value = false;
    formModel.value = false;
  },
  { immediate: true, deep: true },
);
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
