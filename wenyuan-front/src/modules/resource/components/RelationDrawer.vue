<template>
  <n-drawer v-model:show="innerShow" :width="760" placement="right">
    <n-drawer-content :title="`关联管理 - ${resourceName || ''}`" closable>
      <n-card v-if="editable" size="small" title="添加关联" class="mb-3">
        <n-form inline :model="formAdd" label-placement="left">
          <n-form-item label="目标分类">
            <CategoryTreeSelect v-model:value="formAdd.categoryId" style="width: 200px" />
          </n-form-item>
          <n-form-item label="目标资源">
            <n-select
              v-model:value="formAdd.targetId"
              :options="targetOptions"
              filterable
              remote
              clearable
              :loading="searchLoading"
              placeholder="选择分类后搜索资源"
              @search="onSearchTarget"
              style="width: 240px"
            />
          </n-form-item>
          <n-form-item label="关系类型">
            <n-select v-model:value="formAdd.relationType" :options="RELATION_TYPE_OPTIONS" style="width: 140px" />
          </n-form-item>
          <n-form-item label="说明">
            <n-input v-model:value="formAdd.description" placeholder="可选" clearable style="width: 180px" />
          </n-form-item>
          <n-form-item>
            <n-button type="primary" :disabled="!formAdd.targetId" @click="doAdd">添加</n-button>
          </n-form-item>
        </n-form>
      </n-card>

      <n-data-table :columns="columns" :data="relations" :loading="loading" size="small" :bordered="false" />
    </n-drawer-content>
  </n-drawer>
</template>

<script lang="ts" setup>
import { h, ref, computed, watch } from 'vue';
import { NButton, NImage, useMessage, useDialog } from 'naive-ui';
import {
  relationList,
  addRelation,
  deleteRelation,
  list as resourceListApi,
  RELATION_TYPE_OPTIONS,
} from '../api/resource';
import { fileUrl } from '@/utils';
import CategoryTreeSelect from './CategoryTreeSelect.vue';

const props = defineProps({
  show: { type: Boolean, default: false },
  resourceId: { type: String, default: '' },
  resourceName: { type: String, default: '' },
  editable: { type: Boolean, default: true },
});

const emit = defineEmits(['update:show']);

const message = useMessage();
const dialog = useDialog();

const innerShow = computed({
  get: () => props.show,
  set: (v) => emit('update:show', v),
});

const relations = ref<any[]>([]);
const loading = ref(false);

const defaultAdd = {
  categoryId: '',
  targetId: '',
  relationType: 'RELATED',
  description: '',
};
const formAdd = ref({ ...defaultAdd });

const targetOptions = ref<{ label: string; value: string }[]>([]);
const searchLoading = ref(false);
let searchTimer: any = null;

watch(
  () => [props.show, props.resourceId],
  ([show, id]) => {
    if (show && id) {
      loadRelations();
      formAdd.value = { ...defaultAdd };
      targetOptions.value = [];
    }
  },
);

watch(
  () => formAdd.value.categoryId,
  () => {
    formAdd.value.targetId = '';
    loadTargetOptions('');
  },
);

async function loadRelations() {
  if (!props.resourceId) return;
  loading.value = true;
  try {
    const resp: any = await relationList(props.resourceId);
    relations.value = resp.data || [];
  } finally {
    loading.value = false;
  }
}

function onSearchTarget(q: string) {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(async () => {
    loadTargetOptions(q);
  }, 300);
}

async function loadTargetOptions(keyword: string) {
  if (!formAdd.value.categoryId && !keyword) {
    targetOptions.value = [];
    return;
  }
  searchLoading.value = true;
  try {
    const resp: any = await resourceListApi({
      keyword,
      categoryId: formAdd.value.categoryId || undefined,
      pageNum: 1,
      pageSize: 20,
    });
    const items = resp?.data?.list || resp?.data?.data || [];
    targetOptions.value = items
      .filter((it: any) => it.id !== props.resourceId)
      .map((it: any) => ({ label: `${it.name}${it.code ? `（${it.code}）` : ''}`, value: it.id }));
  } finally {
    searchLoading.value = false;
  }
}

async function doAdd() {
  if (!props.editable) {
    return;
  }
  try {
    await addRelation({
      sourceId: props.resourceId,
      targetId: formAdd.value.targetId,
      relationType: formAdd.value.relationType,
      description: formAdd.value.description,
      sort: 0,
    });
    message.success('关联添加成功');
    formAdd.value = { ...defaultAdd };
    await loadRelations();
  } catch (e: any) {
    message.error(e?.message || '添加失败');
  }
}

function doDelete(row: any) {
  if (!props.editable) {
    return;
  }
  dialog.warning({
    title: '删除关联',
    content: '确认删除该关联？',
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      await deleteRelation(row.id);
      message.success('删除成功');
      await loadRelations();
    },
  });
}

const columns = [
  {
    key: 'targetThumbnailPath',
    title: '缩略图',
    width: 70,
    render: (row: any) =>
      row.targetThumbnailPath ? h(NImage, { src: fileUrl(row.targetThumbnailPath), width: 40, height: 40 }) : '-',
  },
  { key: 'targetName', title: '目标资源', ellipsis: { tooltip: true } },
  {
    key: 'relationType',
    title: '关系类型',
    width: 120,
    render: (row: any) => RELATION_TYPE_OPTIONS.find((o) => o.value === row.relationType)?.label || row.relationType,
  },
  { key: 'description', title: '说明', ellipsis: { tooltip: true } },
  {
    key: 'actions',
    title: '操作',
    width: 80,
    align: 'center' as const,
    render: (row: any) =>
      props.editable
        ? h(
            NButton,
            { text: true, type: 'error', size: 'small', onClick: () => doDelete(row) },
            { default: () => '删除' },
          )
        : null,
  },
];
</script>
