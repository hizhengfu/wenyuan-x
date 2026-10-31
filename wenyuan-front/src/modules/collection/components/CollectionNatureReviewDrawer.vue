<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="法定性质核查">
    <div class="nature-review">
      <div class="nature-review__header">
        <NAlert type="info">{{ collectionNatureHint }} 核查只提供线索，不会自动修改主档或历史原件。</NAlert>
        <NAlert v-if="error" type="error">{{ error }}</NAlert>
        <NAlert v-if="result.unresolvedArchives" type="warning">
          当前可见范围有 {{ result.unresolvedArchives }} 份借用或归还档案无法核对，核实原件前不能将本次清单视为完整。
        </NAlert>
        <NSpace align="center">
          <NButton :loading="busy" @click="load(page)">重新核对</NButton>
          <NText v-if="busy">正在核对…</NText>
          <NText v-else-if="error">核查未完成</NText>
          <NText v-else>待核对 {{ result.total }} 件正式藏品</NText>
        </NSpace>
      </div>
      <NDataTable
        class="nature-review__table"
        flex-height
        :columns="columns"
        :data="result.items"
        :loading="busy"
        :pagination="false"
        :scroll-x="910"
      />
      <NPagination
        class="nature-review__pagination"
        :page="page"
        :page-size="30"
        :item-count="result.total"
        :disabled="busy"
        @update:page="load"
      />
    </div>
  </HPageForm>
</template>
<script setup lang="ts">
import { h, onBeforeUnmount, ref, watch } from 'vue';
import { NButton } from 'naive-ui';
import { natureReviewApi } from '../api/natureReview';
import { collectionNatureHint, collectionNatureLabel, type CollectionNature } from '../utils/collectionNature';
type Item = {
  collectionId: string;
  orgId: string;
  totalRegNo: string;
  name: string;
  nature?: CollectionNature;
  reasons: string[];
};
const emit = defineEmits<{ view: [id: string] }>();
const show = ref(false),
  busy = ref(false),
  error = ref(''),
  page = ref(1);
const result = ref<{ items: Item[]; total: number; unresolvedArchives: number }>({
  items: [],
  total: 0,
  unresolvedArchives: 0,
});
let generation = 0;
const columns = [
  { title: '总登记号', key: 'totalRegNo', minWidth: 120, resizable: true, ellipsis: { tooltip: true }, width: 150 },
  { title: '名称', key: 'name', minWidth: 130, resizable: true, ellipsis: { tooltip: true }, width: 180 },
  {
    title: '当前正式性质',
    key: 'nature',
    minWidth: 160,
    resizable: true,
    ellipsis: { tooltip: true },
    width: 220,
    render: (row: Item) => collectionNatureLabel(row.nature),
  },
  {
    title: '待核对事项',
    key: 'reasons',
    width: 260,
    minWidth: 180,
    resizable: true,
    ellipsis: { tooltip: true },
    render: (row: Item) => row.reasons.join('；'),
  },
  {
    title: '办理',
    key: 'actions',
    width: 100,
    render: (row: Item) =>
      h(
        NButton,
        {
          size: 'small',
          disabled: busy.value,
          onClick: () => {
            show.value = false;
            emit('view', row.collectionId);
          },
        },
        () => '查看主档',
      ),
  },
];
async function load(target = 1) {
  const version = ++generation;
  page.value = target;
  busy.value = true;
  error.value = '';
  result.value = { items: [], total: 0, unresolvedArchives: 0 };
  try {
    const response: any = await natureReviewApi.page(target, 30);
    if (version === generation && show.value) result.value = response.data;
  } catch (e: any) {
    if (version === generation && show.value)
      error.value = `${e?.message || '核查失败，请重试'}；核查未完成，不能据空清单认定已核实`;
  } finally {
    if (version === generation) busy.value = false;
  }
}
async function open() {
  show.value = true;
  await load(1);
}
watch(
  show,
  (value) => {
    if (!value) {
      ++generation;
      busy.value = false;
    }
  },
  { flush: 'sync' },
);
onBeforeUnmount(() => {
  ++generation;
});
defineExpose({ open });
</script>

<style scoped>
.nature-review {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  min-height: 0;
  height: 100%;
}

.nature-review__header {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: 12px;
}

.nature-review__table {
  flex: 1;
  min-width: 0;
  min-height: 0;
}

.nature-review__pagination {
  flex: none;
  align-self: flex-end;
}
</style>
