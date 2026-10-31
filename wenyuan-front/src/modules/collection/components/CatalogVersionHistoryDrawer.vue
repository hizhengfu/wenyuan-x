<template>
  <div class="catalog-version-history">
    <HPageForm content-only :enable-cancel="false" v-model:show="visible" title="编目版本正文">
      <NSpace v-if="visible" vertical :size="16">
        <NAlert v-if="currentCatalog" type="info">
          当前收藏单位：{{ currentCatalog.orgName }}；总登记号：{{ currentCatalog.totalRegNo || '--' }}。
          选择已提交版本查看当时的完整著录正文，当前草稿不会覆盖历史内容。
        </NAlert>
        <NSpin :show="busy">
          <NDataTable :columns="columns" :data="versions" :row-key="versionKey" :bordered="false" size="small" :scroll-x="730" />
          <NEmpty v-if="!busy && !versions.length" description="尚未提交过编目版本" />
          <template v-if="historyCatalog && selectedVersion">
            <NAlert type="info" class="version-notice">
              固定查阅 V{{ selectedVersion.version }}（{{ statusLabel(selectedVersion.status) }}）提交正文，仅供查阅。
            </NAlert>
            <CatalogDetail :key="selectedVersion.id" :catalog="historyCatalog" historical />
          </template>
        </NSpin>
        <NSpace justify="end"><NButton @click="close">关闭</NButton></NSpace>
      </NSpace>
    </HPageForm>
  </div>
</template>

<script setup lang="ts">
import { computed, h, ref, watch } from 'vue';
import { NButton, useMessage } from 'naive-ui';
import { cloneDeep } from 'lodash-es';
import CatalogDetail from './CatalogDetail.vue';
import { catalogApi } from '../api/catalog';
import { readCatalog, readCatalogVersions } from '../utils/catalogResponses';
import { useCatalogOperation } from '../composables/useCatalogOperation';
import { formatDateTime } from '@/utils';
import type { CatalogStatus, CollectionCatalogVo, CollectionCatalogVersionVo } from '../typings/collection';

type Target = { collectionId: string; catalogId: string; orgId: string };
const message = useMessage();
const visible = ref(false);
const target = ref<Target | null>(null);
const currentCatalog = ref<CollectionCatalogVo | null>(null);
const versions = ref<CollectionCatalogVersionVo[]>([]);
const selectedVersion = ref<CollectionCatalogVersionVo | null>(null);
const session = useCatalogOperation(() => {
  visible.value = false;
  target.value = null;
  currentCatalog.value = null;
  versions.value = [];
  selectedVersion.value = null;
});
const busy = session.busy;
watch(visible, show => { if (!show) session.reset(); }, { flush: 'sync' });
const statusLabel = (status?: CatalogStatus) => ({ DRAFT: '草稿', SUBMITTED: '待审核', PENDING: '待审核', APPROVED: '已通过', REJECTED: '已退回' })[status || 'DRAFT'];
const versionKey = (row: CollectionCatalogVersionVo) => row.id!;
const columns = [
  { title: '版次', key: 'version', width: 70, render: (row: CollectionCatalogVersionVo) => `V${row.version}` },
  { title: '状态', key: 'status', minWidth: 90, resizable: true, ellipsis: { tooltip: true }, width: 100, render: (row: CollectionCatalogVersionVo) => statusLabel(row.status) },
  { title: '提交人', key: 'submittedBy', minWidth: 100, resizable: true, ellipsis: { tooltip: true }, width: 140 },
  { title: '提交时间', key: 'submittedAt', minWidth: 160, resizable: true, ellipsis: { tooltip: true }, width: 180, render: (row: CollectionCatalogVersionVo) => formatDateTime(row.submittedAt) },
  { title: '审核人', key: 'auditBy', minWidth: 100, resizable: true, ellipsis: { tooltip: true }, width: 140 },
  { title: '操作', key: 'action', width: 100, render: (row: CollectionCatalogVersionVo) => h(NButton, { size: 'small', secondary: true, disabled: busy.value, onClick: () => void selectVersion(row) }, { default: () => '查看正文' }) },
];

// Only immutable version content/metadata is displayed; current data supplies identity and authorization.
const historyCatalog = computed<CollectionCatalogVo | null>(() => {
  const row = selectedVersion.value, identity = currentCatalog.value;
  if (!row || !identity || !target.value) return null;
  return {
    id: row.catalogId, collectionId: row.collectionId,
    orgId: identity.orgId, orgName: identity.orgName, totalRegNo: identity.totalRegNo,
    status: row.status, version: row.version, submittedVersion: row.version, approvedVersion: null,
    submittedBy: row.submittedBy, submittedAt: row.submittedAt,
    auditBy: row.auditBy, auditTime: row.auditTime, auditOpinion: row.auditOpinion,
    content: cloneDeep(row.content), canEdit: false, canReview: false,
    completeness: { percent: 0, complete: false, missingFields: [] },
    stateHash: identity.stateHash, updateTime: row.auditTime || row.submittedAt,
  };
});
function close() { session.reset(); }
function checkedVersions(response: unknown, expected: Target) {
  const rows = readCatalogVersions(response, expected.collectionId);
  const ids = new Set<string>(), numbers = new Set<number>();
  for (const row of rows) {
    if (row.catalogId !== expected.catalogId || row.version < 1 || ids.has(row.id!) || numbers.has(row.version))
      throw new Error('编目版本归属或版次不完整，请重新核对');
    ids.add(row.id!); numbers.add(row.version);
  }
  return rows;
}
async function readCurrent(expected: Target) {
  const row = readCatalog(await catalogApi.get(expected.collectionId), expected.collectionId);
  if (row.id !== expected.catalogId || row.orgId !== expected.orgId) throw new Error('编目所属馆或记录已变化，请重新读取后查阅');
  return row;
}
async function open(catalog: CollectionCatalogVo) {
  if (!session.active.value || !catalog.collectionId || !catalog.id || !catalog.orgId) return;
  const expected: Target = { collectionId: catalog.collectionId, catalogId: catalog.id, orgId: catalog.orgId };
  close(); target.value = expected; visible.value = true;
  const ticket = session.begin('history'); if (ticket === null) return;
  try {
    const identity = await readCurrent(expected);
    if (!session.current(ticket) || !visible.value) return;
    const rows = checkedVersions(await catalogApi.versions(expected.collectionId), expected);
    if (!session.current(ticket) || !visible.value) return;
    currentCatalog.value = identity; versions.value = rows;
  } catch (error: any) {
    if (session.current(ticket)) { close(); message.error(error?.message || '编目版本读取失败'); }
  } finally { session.finish(ticket); }
}
async function selectVersion(version: CollectionCatalogVersionVo) {
  if (!visible.value || !target.value || !versions.value.some(row => row.id === version.id && row.version === version.version)) return;
  const expected = { ...target.value }, selectedId = version.id, selectedNumber = version.version;
  const ticket = session.begin('version'); if (ticket === null) return;
  selectedVersion.value = null;
  try {
    const identity = await readCurrent(expected);
    if (!session.current(ticket) || !visible.value) return;
    const rows = checkedVersions(await catalogApi.versions(expected.collectionId), expected);
    if (!session.current(ticket) || !visible.value) return;
    const row = rows.find(item => item.id === selectedId && item.version === selectedNumber);
    if (!row) throw new Error('所选编目版本已不可读取，请重新核对');
    currentCatalog.value = identity; versions.value = rows; selectedVersion.value = cloneDeep(row);
  } catch (error: any) {
    if (session.current(ticket)) { close(); message.error(error?.message || '编目版本正文读取失败'); }
  } finally { session.finish(ticket); }
}
defineExpose({ open, close });
</script>

<style scoped>
.version-notice { margin: 16px 0; }
</style>
