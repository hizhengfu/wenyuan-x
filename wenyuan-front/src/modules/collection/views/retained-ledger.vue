<template>
  <HPageDataTable auto-scroll-x
    :columns="columns"
    :fetch-data="retainedLedgerApi.page"
    :params="params"
    :is-add="false"
    :is-edit="false"
    :is-delete="false"
    @row-view="open"
  >
    <template #queryForm
      ><NFormItem label="检索内容"><NInput v-model:value="params.keyword" placeholder="原总登记号或名称" clearable /></NFormItem
    ></template>
    <template #toolbar-extra
      ><NText depth="3">本页永久保留跨馆转移时的原馆总账及档案。藏品以后变更不会覆盖此记录。</NText></template
    >
  </HPageDataTable>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="原馆总账与档案留存">
    <NAlert v-if="readError" type="error">{{ readError }}</NAlert>
    <NText v-if="loading">正在读取原馆留存账……</NText>
    <NSpace v-if="record" vertical :size="20">
      <NAlert type="info"
        >本记录为跨馆转移时保存的历史原账，原总登记号继续保留。相关调拨申请：{{ record.transferId }}。</NAlert
      >
      <NAlert v-if="record.evidenceIssue" type="warning"><CollectionFieldLabel label="原件暂不可用，仍可查阅留存记录。" :help="record.evidenceIssue" /></NAlert>
      <NAlert v-else-if="record.bindingVerified !== true" type="warning">{{
        record.bindingVerified === false
          ? '旧记录未冻结调拨编号及接收馆关联，请核对原始交接凭证。'
          : '服务器未提供调拨关联核验状态，请核对原始交接凭证。'
      }}</NAlert>
      <NButton
        v-permission="'COLLECTION_ADMIN,COLLECTION_ARCHIVE_MANAGE'"
        @click="archivePacketRef?.open({ orgId: record.orgId, collectionId: record.collectionId })"
        >原馆实际建档与备案原件</NButton
      >
      <NButton
        v-permission="'COLLECTION_ADMIN,COLLECTION_CARE_MANAGE'"
        @click="careRef?.openHistory({ orgId: record.orgId, collectionId: record.collectionId })"
        >原馆检查与养护历史</NButton
      >
      <NDescriptions bordered :column="2">
        <NDescriptionsItem v-for="field in fields" :key="field.key" :label="field.label" :span="['completeStatus', 'remark'].includes(field.key) ? 2 : 1" content-style="white-space: pre-wrap; overflow-wrap: anywhere;">
          <HDictText v-if="field.dict" :type="field.dict" :value="original[field.key]" />
          <span v-else-if="field.date">{{
            original[field.key] ? formatLocalDateValue(original[field.key]) : '未登记'
          }}</span>
          <span v-else>{{ original[field.key] ?? '未登记' }}</span>
        </NDescriptionsItem>
        <NDescriptionsItem label="尺寸">{{ dimensions }}</NDescriptionsItem>
        <NDescriptionsItem label="原库位">{{
          original.warehouseName || original.warehouseId || '未登记'
        }}</NDescriptionsItem>
      </NDescriptions>
      <NText strong>转移时保存的影像及附件</NText>
      <NText v-if="record.evidenceIssue && !files.length" depth="3">原件恢复并核验通过后可预览、下载。</NText>
      <NDescriptions bordered :column="1"
        ><NDescriptionsItem v-for="file in files" :key="file.key" :label="file.name"
          ><HFileList :files="[file]" :original-url="originalUrl" :revision="[record.id, record.archiveId]" show-download-btn /></NDescriptionsItem
      ></NDescriptions>
      <NCollapse
        ><NCollapseItem
          v-for="version in record.snapshot.catalogVersions"
          :key="version.id"
          :name="version.id"
          :title="`原馆编目第 ${version.version} 版`"
          ><CollectionChangeDiff :before="{}" :after="version.content" /></NCollapseItem
      ></NCollapse>
    </NSpace>
  </HPageForm>
  <CollectionArchivePacketDrawer v-if="show && !readError" :key="`CollectionArchivePacketDrawer-${historyEpoch}`" ref="archivePacketRef" />
  <CollectionCareDrawer v-if="show && !readError" :key="`CollectionCareDrawer-${historyEpoch}`" ref="careRef" />
</template>
<script setup lang="ts">
import { computed, h, onBeforeUnmount, reactive, ref, watch } from 'vue';
import { NButton, NText } from 'naive-ui';
import CollectionFieldLabel from '../components/CollectionFieldLabel.vue';
import { formatLocalDateValue } from '@/utils';
import { retainedLedgerApi } from '../api/retainedLedger';
import CollectionChangeDiff from '../components/CollectionChangeDiff.vue';
import CollectionArchivePacketDrawer from '../components/CollectionArchivePacketDrawer.vue';
import CollectionCareDrawer from '../components/CollectionCareDrawer.vue';
const archivePacketRef = ref<any>(),
  careRef = ref<any>(),
  readError = ref(''),
  loading = ref(false),
  historyEpoch = ref(0);
let requestVersion = 0;
const params = reactive({ keyword: '' }),
  show = ref(false),
  record = ref<Record<string, any>>();
const original = computed<Record<string, any>>(() => record.value?.snapshot?.collection || {});
const fields: { key: string; label: string; dict?: string; date?: boolean }[] = [
  { key: 'registeredAt', label: '原登记日期', date: true },
  { key: 'totalRegNo', label: '永久保留总登记号' },
  { key: 'classNo', label: '分类号' },
  { key: 'name', label: '原登记名称' },
  { key: 'era', label: '时代' },
  { key: 'quantity', label: '数量' },
  { key: 'unit', label: '单位' },
  { key: 'weight', label: '重量（质量）' },
  { key: 'texture', label: '质地', dict: 'col_texture' },
  { key: 'completeStatus', label: '完残情况' },
  { key: 'source', label: '来源', dict: 'col_source' },
  { key: 'entryVoucherNo', label: '入馆凭证号' },
  { key: 'deregVoucherNo', label: '原退出凭证号' },
  { key: 'level', label: '原登记级别', dict: 'col_relic_level' },
  { key: 'remark', label: '原账备注' },
];
const dimensions = computed(
  () =>
    Object.entries(original.value.dimensions || {})
      .filter(([, v]) => v != null && v !== '')
      .map(
        ([k, v]) =>
          `${({ height: '通高', mouth: '口径', bottom: '底径', belly: '腹径', length: '长', width: '宽', diameter: '直径' } as Record<string, string>)[k] || k}：${v}`,
      )
      .join('；') || '未登记',
);
const files = computed(() => {
  const snapshot = record.value?.snapshot;
  const named = [
  ...(snapshot?.collection?.coverImage ? [{ key: 'cover', name: '原馆封面影像', fileId: snapshot.collection.coverImage }] : []),
  ...(snapshot?.media || []).map((f: any) => ({
    key: `media-${f.id}`,
    name: f.title || '原馆影像',
    fileId: f.fileId,
  })),
  ...(snapshot?.attachments || []).map((f: any) => ({
    key: `attachment-${f.id}`,
    name: f.name || '原馆附件',
    fileId: f.fileId,
  })),
  ].filter((f) => !!f.fileId);
  const archive = (record.value?.archives || []).find(
    (a: any) => a.id === record.value?.archiveId && a.phase === 'TRANSFERRED',
  );
  return (archive?.files || []).map((file: any) => ({
    ...file,
    key: file.fileId,
    name: named.find((item) => item.fileId === file.fileId)?.name || '原馆留存原件',
    fileId: file.fileId,
  }));
});
const originalUrl = (fileId: string) =>
  `/collection/retained-ledger/${record.value?.id}/original/${record.value?.archiveId}/${fileId}`;
const columns = [
  { ellipsis: { tooltip: true }, resizable: true, width: 160, title: '原总登记号', key: 'totalRegNo' },
  { ellipsis: { tooltip: true }, resizable: true, width: 220, title: '原馆登记名称', key: 'name' },
  { ellipsis: { tooltip: true }, resizable: true, width: 210, title: '跨馆转移申请', key: 'transferId' },
  { ellipsis: { tooltip: true }, resizable: true, width: 190,
    title: '原件及关联核验',
    key: 'bindingVerified',
    render: (row: any) =>
      row.evidenceIssue
        ? h(NText, { type: 'warning' }, () => h(CollectionFieldLabel, {
            label: '原件核验异常', help: row.evidenceIssue,
          }))
        : row.bindingVerified === true
        ? '关联字段已冻结'
        : row.bindingVerified === false
          ? '历史关联待核对'
          : '核验状态未提供',
  },
  { width: 190, title: '留存时间', key: 'retainedAt', columnType: 'dateTime' },
  { width: 190,
    title: '原始账档',
    key: 'actions',
    render: (row: any) => h(NButton, { text: true, type: 'primary', onClick: () => open(row) }, () => '查看原账及附件'),
  },
];
watch(
  show,
  (value) => {
    if (!value) {
      requestVersion++;
      record.value = undefined;
      readError.value = '';
      loading.value = false;
      historyEpoch.value++;
    }
  },
  { flush: 'sync' },
);
onBeforeUnmount(() => {
  requestVersion++;
});
async function open(row: { id: string }) {
  const current = ++requestVersion;
  record.value = undefined;
  readError.value = '';
  loading.value = true;
  historyEpoch.value++;
  show.value = true;
  try {
    const response: any = await retainedLedgerApi.get(row.id);
    if (current !== requestVersion || !show.value) return;
    if (!response?.data) throw new Error('missing');
    record.value = response.data;
  } catch {
    if (current === requestVersion && show.value) {
      record.value = undefined;
      readError.value = '原馆留存账读取失败，请核对当前授权及原件后重新读取。';
      historyEpoch.value++;
    }
  } finally {
    if (current === requestVersion) loading.value = false;
  }
}
</script>
