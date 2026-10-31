<template>
  <n-spin :show="loading">
    <HDetailTable :rows="rows" show-print empty-description="暂无数字成果详情">
      <template #image>
        <HImage :src="form.image" :size="180" :width="180" :height="240" type="f" class="page-detail__image"></HImage>
      </template>

      <template #mainType>
        <h-dict-text :value="form.mainType" type="archive_digital_category"></h-dict-text>
      </template>

      <template #type>
        <h-dict-text :value="formatMultipleValue(form.type)" :type="typeDictType"></h-dict-text>
      </template>

      <template #fileType>
        <h-dict-text :value="form.fileType" type="archive_digital_file_type"></h-dict-text>
      </template>

      <template #sourceType>
        <h-dict-text :value="form.sourceType" type="archive_digital_source_type"></h-dict-text>
      </template>

      <template #status>
        <h-dict-text :value="form.status" type="content_status"></h-dict-text>
      </template>

      <template #parameter>
        <div class="page-detail__content">
          {{ displayValue(form.parameter) }}
        </div>
      </template>

      <template #content>
        <div class="page-detail__rich-text" v-html="form.content || '--'"></div>
      </template>

      <template #files>
        <HFileList :contentId="form.id"></HFileList>
      </template>
    </HDetailTable>
  </n-spin>
</template>

<script lang="ts" setup>
import { get } from '../../api/digital';
import { cloneDeep } from 'lodash-es';
import dayjs from 'dayjs';
import { computed, ref, watch } from 'vue';

const props = defineProps({
  id: {
    type: String,
    default: '',
  },
});

const loading = ref(false);

const defaultForm = {
  id: '',
  sourceId: '',
  sourceName: '',
  sourceType: '',
  type: null as string | string[] | null,
  mainType: '',
  fileType: '',
  name: '',
  image: '',
  content: '',
  dateTime: null as string | number | null,
  path: '',
  areaId: '',
  area: {
    name: '',
  },
  year: '',
  size: '',
  groupName: '',
  manufacturer: '',
  parameter: '',
  status: '',
  createBy: '',
  createTime: null as string | number | null,
  updateBy: '',
  updateTime: null as string | number | null,
};

const form = ref(cloneDeep(defaultForm));

const typeDictType = computed(() => {
  return form.value.mainType !== '1' ? 'archive_digital_material_type' : 'archive_digital_type';
});

const imageRowspan = computed(() => {
  return 4;
});

const rows = computed(() => {
  if (!form.value.id) {
    return [];
  }

  const detailRows: any[] = [
    {
      cells: [
        { content: '成果名称', class: 'w-120px' },
        { content: displayValue(form.value.name), colspan: 3 },
        { slot: 'image', rowspan: imageRowspan.value, class: 'page-detail__image-cell' },
      ],
    },
    {
      cells: [
        { content: '归属单位', class: 'w-120px' },
        { content: displayValue(form.value.sourceName) },
        { content: '成果类型', class: 'w-120px' },
        { slot: 'fileType' },
      ],
    },
    {
      cells: [
        { content: '藏品分类', class: 'w-120px' },
        { slot: 'mainType' },
        { content: form.value.mainType !== '1' ? '藏品类型' : '建筑类别', class: 'w-120px' },
        { slot: 'type' },
      ],
    },
    {
      cells: [
        { content: '所属地区', class: 'w-120px' },
        { content: displayValue(form.value.area?.name || form.value.areaId) },
        { content: '藏品年代', class: 'w-120px' },
        { content: displayValue(form.value.year) },
      ],
    },
  ];

  if (form.value.mainType !== '1') {
    detailRows.push({
      cells: [
        { content: '藏品尺寸', class: 'w-120px' },
        { content: displayValue(form.value.size) },
        { content: '收藏单位', class: 'w-120px' },
        { content: displayValue(form.value.groupName), colspan: 2 },
      ],
    });
  }

  detailRows.push(
    {
      cells: [
        { content: '采集时间', class: 'w-120px' },
        { content: formatDate(form.value.dateTime) },
        { content: '设备型号', class: 'w-120px' },
        { content: displayValue(form.value.manufacturer), colspan: 2 },
      ],
    },
    {
      cells: [
        { content: '关联类型', class: 'w-120px' },
        { slot: 'sourceType' },
        { content: '数据状态', class: 'w-120px' },
        { slot: 'status', colspan: 2 },
      ],
    },
    {
      cells: [
        { content: '文件路径', class: 'w-120px' },
        { content: displayValue(form.value.path), colspan: 4 },
      ],
    },
    {
      cells: [{ content: '制作参数', colspan: 5 }],
    },
    {
      cells: [{ slot: 'parameter', colspan: 5 }],
    },
    {
      cells: [{ content: '成果内容', colspan: 5 }],
    },
    {
      cells: [{ slot: 'content', colspan: 5 }],
    },
    {
      cells: [
        { content: '查看附件', class: 'w-120px' },
        { slot: 'files', colspan: 4 },
      ],
    },
  );

  return detailRows;
});

function displayValue(value: unknown) {
  if (value === null || value === undefined || value === '') {
    return '--';
  }

  return String(value);
}

function formatDate(value: string | number | null) {
  if (!value) {
    return '--';
  }

  const date = dayjs(value);
  return date.isValid() ? date.format('YYYY-MM-DD') : displayValue(value);
}

function formatMultipleValue(value: string | string[] | null) {
  if (Array.isArray(value)) {
    return value.join(',');
  }

  return value || undefined;
}

async function loadDetail(id: string) {
  form.value = cloneDeep(defaultForm);

  if (!id) {
    return;
  }

  loading.value = true;

  try {
    const resp = await get(id);
    Object.assign(form.value, resp?.data || {});
  } finally {
    loading.value = false;
  }
}

watch(
  () => props.id,
  (id) => {
    loadDetail(id);
  },
  { immediate: true },
);
</script>

<style lang="scss" scoped>
.page-detail__image-cell {
  width: 220px;
  text-align: center;
  vertical-align: top;
}

.page-detail__image {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  min-height: 200px;
  padding: 8px 0 0;
}

.page-detail__image :deep(.n-image) {
  display: flex;
  justify-content: center;
  width: 100%;
}

.page-detail__image :deep(img) {
  width: 100%;
  max-width: 180px;
  max-height: 240px;
  object-fit: contain;
}

.page-detail__content {
  min-height: 72px;
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.8;
  padding: 4px 0;
}

.page-detail__rich-text {
  min-height: 120px;
  line-height: 1.8;
  word-break: break-all;
}
</style>
