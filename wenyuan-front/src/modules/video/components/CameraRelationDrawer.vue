<template>
  <n-modal
    v-model:show="dialog"
    preset="card"
    :title="title"
    style="width: 920px"
    :segmented="{ content: true }"
  >
    <n-space vertical :size="12">
      <n-space justify="space-between" align="center">
        <n-space :size="8">
          <n-tag type="info" size="small">{{ camera?.name || camera?.code }}</n-tag>
          <n-tag v-if="camera?.group?.name" size="small">{{ camera.group.name }}</n-tag>
        </n-space>
        <n-space :size="8">
          <n-button size="small" @click="loadRelations">
            <template #icon>
              <IconMdiRefresh />
            </template>
          </n-button>
          <n-button size="small" type="primary" @click="handleAdd">
            <template #icon>
              <IconMdiAdd />
            </template>
          </n-button>
        </n-space>
      </n-space>

      <n-data-table
        :columns="columns"
        :data="relationList"
        :loading="loading"
        :pagination="{ pageSize: 8 }"
        :row-key="(row: any) => row.id"
      />
    </n-space>

    <h-dialog-form
      v-model:show="formModel"
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :handle-submit="handleSubmit"
      width="560px"
      height="520px"
      @form:submit-success="handleChanged"
    >
      <slot name="sourceId" :form="form">
        <n-form-item label="关联对象ID" path="sourceId">
          <n-input v-model:value="form.sourceId" placeholder="请输入关联对象ID" clearable></n-input>
        </n-form-item>
      </slot>
      <n-form-item label="对象名称" path="sourceName">
        <n-input v-model:value="form.sourceName" placeholder="请输入对象名称" clearable></n-input>
      </n-form-item>
      <n-grid x-gap="6">
        <n-grid-item :span="12">
          <n-form-item label="主关联" path="isPrimary">
            <n-switch v-model:value="form.isPrimary"></n-switch>
          </n-form-item>
        </n-grid-item>
        <n-grid-item :span="12">
          <n-form-item label="排序" path="sort">
            <n-input-number v-model:value="form.sort" style="width: 100%"></n-input-number>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-form-item label="状态" path="status">
        <h-dict-radio v-model:value="form.status" type="content_status" clearable filterable></h-dict-radio>
      </n-form-item>
      <n-form-item label="备注" path="content">
        <n-input v-model:value="form.content" type="textarea" placeholder="请输入备注"></n-input>
      </n-form-item>
    </h-dialog-form>
  </n-modal>
</template>

<script lang="ts" setup>
import { computed, h, ref, watch } from 'vue';
import { cloneDeep } from 'lodash-es';
import { NButton, NTag, useDialog } from 'naive-ui';
import { add, del, edit, listByCamera } from '../api/relation';

const props = defineProps({
  show: { type: Boolean, default: false },
  camera: { type: Object, default: null },
});

const emits = defineEmits(['update:show', 'changed']);
const confirmDialog = useDialog();

const dialog = computed<boolean>({
  get() {
    return props.show;
  },
  set(value) {
    emits('update:show', value);
  },
});

const title = computed(() => `维护关联「${props.camera?.name || props.camera?.code || ''}」`);
const relationList = ref<any[]>([]);
const loading = ref(false);

const statusMap: Record<string, string> = {
  NORMAL: '正常',
  DELETE: '删除',
  AUDIT: '审核',
};

const columns: any[] = [
  {
    key: 'sourceName',
    title: '关联对象',
    ellipsis: { tooltip: true },
    render(row: any) {
      return row.sourceName || row.sourceId;
    },
  },
  { key: 'sourceId', title: '对象ID', width: 180, ellipsis: { tooltip: true } },
  {
    key: 'isPrimary',
    title: '主关联',
    width: 90,
    align: 'center',
    render(row: any) {
      return h(NTag, { size: 'small', type: row.isPrimary ? 'success' : 'default' }, () =>
        row.isPrimary ? '是' : '否',
      );
    },
  },
  {
    key: 'status',
    title: '状态',
    width: 90,
    align: 'center',
    render(row: any) {
      return statusMap[row.status] || row.status;
    },
  },
  {
    key: 'actions',
    title: '操作',
    width: 150,
    align: 'center',
    render(row: any) {
      return [
        h(
          NButton,
          {
            size: 'tiny',
            type: 'primary',
            class: 'mr-2',
            onClick: () => handleEdit(row),
          },
          { default: () => '编辑' },
        ),
        h(
          NButton,
          {
            size: 'tiny',
            type: 'error',
            onClick: () => handleDelete(row),
          },
          { default: () => '删除' },
        ),
      ];
    },
  },
];

const defaultForm = {
  id: '',
  cameraId: '',
  cameraCode: '',
  sourceType: null,
  sourceId: '',
  sourceName: '',
  relationType: 'DEFAULT',
  isPrimary: false,
  sort: 0,
  content: '',
  status: 'NORMAL',
};

const form = ref<any>(cloneDeep(defaultForm));
const formModel = ref(false);
const formType = ref<'add' | 'edit'>('add');
const formTitle = ref('新增关联');

const rules = {
  sourceId: {
    required: true,
    message: '请选择关联对象',
  },
  status: {
    required: true,
    message: '请选择状态',
  },
};

watch(
  () => props.show,
  (show) => {
    if (show) {
      loadRelations();
    }
  },
);

async function loadRelations() {
  if (!props.camera?.id) {
    relationList.value = [];
    return;
  }
  loading.value = true;
  try {
    const resp: any = await listByCamera(props.camera.id);
    relationList.value = resp.data || [];
  } finally {
    loading.value = false;
  }
}

function buildDefaultForm() {
  return {
    ...cloneDeep(defaultForm),
    cameraId: props.camera?.id || '',
    cameraCode: props.camera?.code || '',
    isPrimary: relationList.value.length === 0,
  };
}

function handleAdd() {
  formType.value = 'add';
  form.value = buildDefaultForm();
  formTitle.value = '新增关联';
  formModel.value = true;
}

function handleEdit(row: any) {
  formType.value = 'edit';
  form.value = {
    ...buildDefaultForm(),
    ...cloneDeep(row),
    cameraId: props.camera?.id || row.cameraId,
    cameraCode: props.camera?.code || row.cameraCode,
  };
  formTitle.value = `编辑「${row.sourceName || row.sourceId}」`;
  formModel.value = true;
}

function handleDelete(row: any) {
  confirmDialog.warning({
    title: '删除关联',
    content: `确认删除「${row.sourceName || row.sourceId}」？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: () => {
      return del(row.id).then(handleChanged);
    },
  });
}

async function handleChanged() {
  await loadRelations();
  emits('changed');
}

function handleSubmit(data: any) {
  const payload = {
    ...data,
    cameraId: props.camera?.id || data.cameraId,
    cameraCode: props.camera?.code || data.cameraCode,
  };
  if (formType.value === 'add') {
    return add(payload);
  }
  return edit(payload);
}
</script>
