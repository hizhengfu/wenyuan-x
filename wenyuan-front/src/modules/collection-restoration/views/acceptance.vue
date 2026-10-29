<template>
  <HPageDataTable
    ref="tableRef"
    :columns="columns"
    :fetch-data="restorationArchiveApi.acceptancePage"
    :is-delete="false"
    :is-add="canWriteAcceptance"
    :is-edit="canWriteAcceptance"
    :add-permission="RESTORATION_ACCEPTANCE_WRITE_PERMISSION"
    :edit-permission="RESTORATION_ACCEPTANCE_WRITE_PERMISSION"
    :params="queryParams"
    row-key-field="id"
    @add="onAdd"
    @row-edit="onEdit"
    @row-view="onView"
  >
    <template #toolbar-extra><OriginalHistoryButton business-type="RESTORATION_ACCEPTANCE_2026" /></template>
    <template #queryForm>
      <NFormItem label="关键字">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="验收意见" style="width: 200px" />
      </NFormItem>
      <NFormItem label="验收类型">
        <HDictSelect
          v-model:value="queryParams.acceptanceType"
          type="restoration_acceptance_type"
          clearable
          style="width: 140px"
        />
      </NFormItem>
      <NFormItem label="验收结论">
        <HDictSelect
          v-model:value="queryParams.conclusion"
          type="restoration_acceptance_conclusion"
          clearable
          style="width: 140px"
        />
      </NFormItem>
      <NFormItem label="修复项目">
        <RestorationProjectPicker v-model:value="queryParams.restorationId" style="width: 280px" />
      </NFormItem>
    </template>

    <HPageView v-model:show="chooseShow" title="选择修复项目办理实际验收" width="600px">
      <NAlert type="info">选择已登记实际竣工的项目。验收人员、时间、报告和结论均依据原件填写。</NAlert>
      <RestorationProjectPicker v-model:value="selectedProject" style="width: 360px; margin: 16px 0" />
      <NButton :disabled="!selectedProject" @click="openSelected">打开验收原件办理</NButton>
    </HPageView>
    <RestorationCloseoutDrawer ref="closeoutRef" @saved="tableRef?.handleRefresh()" />

    <HPageView v-model:show="viewShow" title="验收单详情" width="760px">
      <NButton v-if="viewRow?.restorationId" @click="closeoutRef?.open(viewRow.restorationId)"
        >查看验收历史及实际原件</NButton
      >
      <NDescriptions v-if="viewRow" label-placement="left" :column="1" bordered size="small">
        <NDescriptionsItem label="藏品">{{
          `${viewRow.collectionTotalRegNo || ''} ${viewRow.collectionName || ''}`.trim() || '--'
        }}</NDescriptionsItem>
        <NDescriptionsItem label="档案编号">{{ viewRow.restorationArchiveNo || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="修复机构">{{ viewRow.restoreOrg || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="验收类型"
          ><HDictText type="restoration_acceptance_type" :value="viewRow.acceptanceType"
        /></NDescriptionsItem>
        <NDescriptionsItem label="验收结论">
          <NTag
            size="small"
            :bordered="false"
            :type="viewRow.conclusion === 'PASS' ? 'success' : viewRow.conclusion === 'REJECT' ? 'error' : 'warning'"
            >{{
              ({ PASS: '通过', REVISION: '需整改复验', REJECT: '不通过' } as any)[viewRow.conclusion || ''] ||
              viewRow.conclusion ||
              '--'
            }}</NTag
          >
        </NDescriptionsItem>
        <NDescriptionsItem label="专家名单">
          <template v-if="viewExperts.length">
            <div v-for="(e, i) in viewExperts" :key="i">{{ e.name }} · {{ e.title }} · {{ e.unit }}</div>
          </template>
          <template v-else>--</template>
        </NDescriptionsItem>
        <NDescriptionsItem label="验收意见">{{ viewRow.opinion || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="报告附件"
          ><HFileList :value="viewRow.reportFileId" :revision="viewRow.id"
        /></NDescriptionsItem>
        <NDescriptionsItem label="验收时间">{{ formatDateTime(viewRow.acceptedTime) }}</NDescriptionsItem>
      </NDescriptions>
    </HPageView>
  </HPageDataTable>
</template>

<script setup lang="ts">
import OriginalHistoryButton from '@/modules/collection/components/OriginalHistoryButton.vue';
import { computed, h, reactive, ref, resolveComponent } from 'vue';
import { NAlert, NButton, NDescriptions, NDescriptionsItem, NFormItem, NInput, NTag } from 'naive-ui';
import { restorationArchiveApi } from '../api/archive';
import RestorationProjectPicker from '../components/RestorationProjectPicker.vue';
import RestorationCloseoutDrawer from '../components/RestorationCloseoutDrawer.vue';
import type { RestorationAcceptanceVo } from '../typings';
import { formatDateTime } from '@/utils/datetime';
import { RESTORATION_ACCEPTANCE_WRITE_CODES, RESTORATION_ACCEPTANCE_WRITE_PERMISSION } from '../utils/restorationPermissions';
import { useAuthStore } from '@/store';

const HDictText = resolveComponent('HDictText');

interface Expert {
  name?: string;
  title?: string;
  unit?: string;
}

const tableRef = ref<any>(null);
const auth = useAuthStore();
// 新增 / 编辑都是打开验收归档抽屉登记验收或备案（RestorationCloseoutController.acceptance / filing），按验收写码开关；
// 未传 permission 时表格放行，所以另用 is-add / is-edit。
const canWriteAcceptance = computed(() => auth.hasPermission(RESTORATION_ACCEPTANCE_WRITE_CODES));
const chooseShow = ref(false),
  selectedProject = ref<string | null>(null),
  closeoutRef = ref<any>();
const queryParams = reactive({
  keyword: null as string | null,
  acceptanceType: null as string | null,
  conclusion: null as string | null,
  restorationId: null as string | null,
});

const columns: any[] = [
  {
    title: '藏品',
    key: 'collection',
    minWidth: 180, ellipsis: { tooltip: true }, resizable: true, width: 180,
    render: (r: RestorationAcceptanceVo) =>
      h(
        'a',
        { class: 'data-table-link', style: { cursor: 'pointer' }, onClick: () => onView(r) },
        `${r.collectionTotalRegNo || ''} ${r.collectionName || ''}`.trim() || '—',
      ),
  },
  { title: '档案编号', key: 'restorationArchiveNo', width: 180, ellipsis: { tooltip: true }, resizable: true },
  { title: '修复机构', key: 'restoreOrg', ellipsis: { tooltip: true }, resizable: true, width: 200 },
  {
    title: '验收类型',
    key: 'acceptanceType',
    width: 120, ellipsis: { tooltip: true }, resizable: true,
    render: (r: RestorationAcceptanceVo) =>
      h(HDictText, { type: 'restoration_acceptance_type', value: r.acceptanceType }),
  },
  {
    title: '结论',
    key: 'conclusion',
    width: 130, ellipsis: { tooltip: true }, resizable: true,
    render: (r: RestorationAcceptanceVo) =>
      h(
        NTag,
        {
          size: 'small',
          bordered: false,
          type: r.conclusion === 'PASS' ? 'success' : r.conclusion === 'REJECT' ? 'error' : 'warning',
        },
        {
          default: () =>
            (({ PASS: '通过', REVISION: '需整改复验', REJECT: '不通过' }) as any)[r.conclusion || ''] ||
            r.conclusion ||
            '—',
        },
      ),
  },
  {
    title: '验收时间',
    key: 'acceptedTime',
    width: 190, ellipsis: { tooltip: true }, resizable: true,
    render: (r: RestorationAcceptanceVo) => formatDateTime(r.acceptedTime),
  },
  { title: '意见', key: 'opinion', resizable: true, width: 240, ellipsis: { tooltip: true } },
];

function parseExperts(raw?: string | null): Expert[] {
  if (!raw) return [];
  try {
    const arr = JSON.parse(raw);
    if (Array.isArray(arr)) return arr.map((e: any) => ({ name: e?.name, title: e?.title, unit: e?.unit }));
  } catch {
    /* 忽略 */
  }
  return [];
}
const onAdd = () => {
  selectedProject.value = null;
  chooseShow.value = true;
};
async function openSelected() {
  if (!selectedProject.value) return;
  const target = selectedProject.value;
  chooseShow.value = false;
  await closeoutRef.value?.open(target);
}
async function onEdit(row: RestorationAcceptanceVo) {
  if (row.restorationId) await closeoutRef.value?.open(row.restorationId);
}

const viewShow = ref(false);
const viewRow = ref<RestorationAcceptanceVo | null>(null);
const viewExperts = ref<Expert[]>([]);
const onView = async (row: RestorationAcceptanceVo) => {
  if (!row.id) return;
  const res = await restorationArchiveApi.acceptanceGetById(row.id);
  viewRow.value = (res.data as RestorationAcceptanceVo) || row;
  viewExperts.value = parseExperts(viewRow.value.experts);
  viewShow.value = true;
};
</script>

<style scoped>
.rows-block {
  width: 100%;
}
.rows-empty {
  color: #999;
  font-size: 13px;
  padding: 8px 12px;
  background: rgba(0, 0, 0, 0.02);
  border-radius: 4px;
  margin-bottom: 8px;
}
.rows-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
}
</style>
