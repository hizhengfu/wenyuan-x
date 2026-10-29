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
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="名称/类别" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入名称/类别" clearable></n-input>
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
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入名称" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="类别" path="type">
            <n-input v-model:value="form.type" placeholder="请输入类别" clearable></n-input>
            <!-- <h-dict-select
              v-if="culturalType"
              v-model:value="form.type"
              :type="`fourth_cultural_${culturalType}`"
            ></h-dict-select> -->
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="面积/数量" path="mjSl">
            <template #label>
              面积/数量
              <n-popover trigger="hover">
                <template #trigger>
                  <n-icon size="16px">
                    <icon-mdi-help-circle-outline></icon-mdi-help-circle-outline>
                  </n-icon>
                </template>
                <span>请填写数据时携带单位</span>
              </n-popover>
            </template>

            <n-input v-model:value="form.mjSl" placeholder="请输入面积/数量" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
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
      <n-table class="info-table" :single-line="false">
        <tr>
          <td>名称</td>
          <td colspan="2">{{ form.name || '--' }}</td>
          <td>类别</td>
          <td colspan="2">{{ form.type || '--' }}</td>
        </tr>
        <tr>
          <td>面积/数量</td>
          <td colspan="8">{{ form.mjSl || '--' }}</td>
        </tr>
      </n-table>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../../api/fourth/fourthCulturalRelicMonomer';
import { get as coverGet } from '../../../api/fourth/fourthCulturalRelicCover';

import { ref, onMounted } from 'vue';
import { cloneDeep } from 'lodash-es';

const props = defineProps({
  fourthCulturalRecordId: {
    type: String,
    required: true,
  },
  title: {
    type: String,
  },
  culturalType: {
    type: Number,
    required: true,
  },
});
const columns = [
  {
    title: '序号',
    key: 'index',
    columnType: 'index',
    width: '80px',
    align: 'center',
    fixed: 'left',
    ellipsis: true,
    resizable: true,
  },
  {
    key: 'name',
    title: '名称',

    width: '200px',
    resizable: true,
    ellipsis: { tooltip: true },
  },
  { key: 'type', title: '类别' },
  { key: 'mjSl', title: '面积/数量' },
];

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
const detailModel = ref(false);
/**
 * 表格ref
 * */
const refPageTable = ref();

const formTitle = ref();

/**
 * 表单默认值
 * */
const defaultForm = {
  culturalRelicId: props.fourthCulturalRecordId,
  name: '',
  mjSl: '',
  type: '',
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {
  culturalRelicId: {
    required: true,
    message: '请输入cultural_relic_id',
  },
  name: {
    required: true,
    message: '请输入名称',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  culturalRelicId: props.fourthCulturalRecordId,
});

const formType = ref('add');
const culturalType = ref();
const handleCoverData = async () => {
  if (!props.fourthCulturalRecordId) return;
  let culturalRelicId = props.fourthCulturalRecordId;
  const coverRes = await coverGet(culturalRelicId);
  if (coverRes && coverRes.data && coverRes.data.type) {
    culturalType.value = coverRes.data.type;
  }
};
onMounted(async () => {
  await handleCoverData();
});

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
  formType.value = 'view';
  Object.assign(form.value, row);
  get(row.id).then((resp) => {
    Object.assign(form.value, resp.data);
    detailModel.value = true;
  });
  formTitle.value = `查看「${row.name}」`;
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
</script>
