<template>
  <NCard
    class="auxiliary-ledger-page"
    title="辅助账册"
    content-style="display: flex; flex-direction: column; gap: 18px; min-height: 0"
  >
    <div class="auxiliary-ledger-toolbar">
      <NAlert type="info"
        >不符合入藏标准的资料，请先选择下方对应类别，再登记辅助账编号、数量、保管位置及判定依据，并上传原始凭证。已登入藏品总登记账的藏品须先依法办理退出，不得直接转入辅助账。</NAlert
      >
      <NSpace align="center"
        ><NSelect v-model:value="orgId" :options="orgOptions" placeholder="收藏单位" style="width: 260px" /><NButton
          v-if="activeKind !== 'LAWFUL_EXIT'"
          v-permission="'COLLECTION_ADMIN,COLLECTION_LEDGER_CORRECT'"
          :disabled="!orgId || loading || !!readError"
          @click="openRegistration"
          >登记{{ activeKindLabel }}</NButton
        ><NButton :loading="loading" @click="load">刷新</NButton></NSpace
      >
      <NAlert v-if="readError" type="error">{{ readError }}</NAlert>
      <NTabs v-model:value="activeKind" type="line" pane-style="display: none">
        <NTabPane v-for="kind in kinds" :key="kind.value" :name="kind.value" :tab="kind.label" />
      </NTabs>
      <NText v-if="activeKind === 'LAWFUL_EXIT'" depth="3">此账册由已完成法定退出的藏品自动生成，并保留原总登记号及退出档案关联。</NText>
    </div>
    <NDataTable
      class="auxiliary-ledger-table"
      flex-height
      :scroll-x="1550"
      :key="activeKind"
      :columns="columns"
      :data="visibleRows"
      :loading="loading"
      :pagination="visibleRows.length ? { pageSize: 20 } : false"
      :theme-overrides="{ emptyPadding: '0' }"
    />

    <CollectionEvidenceForm
      :key="`registration-${formEpoch}`"
      ref="formRef"
      :title="registrationTitle"
      :fields="fields"
      :save="registrationSave"
      @saved="load"
    />
    <HPageForm content-only :enable-cancel="false" v-model:show="detailShow" title="辅助账册登记原件">
      <NText v-if="detailLoading">正在核对辅助账册原件……</NText>
      <NAlert v-if="detailError" type="error">{{ detailError }}</NAlert>
      <CollectionArchiveIssues :issues="evidenceIssue ? [evidenceIssue] : []" />
      <NDescriptions v-if="selected" bordered :column="1">
        <NDescriptionsItem label="辅助账编号">{{ selected.recordNo }}</NDescriptionsItem>
        <NDescriptionsItem label="名称及类别"
          >{{ selected.name }} / {{ kinds.find((k) => k.value === selected?.kind)?.label }}</NDescriptionsItem
        >
        <NDescriptionsItem label="数量与原账记录位置"
          >{{ selected.quantity }} {{ selected.unit }} / {{ selected.location }}</NDescriptionsItem
        >
        <NDescriptionsItem label="登记日期">{{ formatLocalDateValue(selected.registeredOn) }}</NDescriptionsItem>
        <NDescriptionsItem label="登记依据" content-style="white-space: pre-wrap; overflow-wrap: anywhere;">{{ selected.reason }}</NDescriptionsItem>
        <NDescriptionsItem label="原始凭证"
          ><NSpace vertical><HFileList :files="originalFiles" :original-url="(fileId: string) => originalUrl(fileId)" :revision="[selected?.id, archiveId]" show-download-btn /></NSpace></NDescriptionsItem>
        <NDescriptionsItem v-if="selected.originTotalRegNo" label="退出前总登记号">{{
          selected.originTotalRegNo
        }}</NDescriptionsItem>
        <NDescriptionsItem v-if="selected.exitId" label="法定退出档案"
          ><NButton text type="primary" @click="exitRef?.open(selected?.exitId)"
            >查阅退出办理及归档材料</NButton
          ></NDescriptionsItem
        >
        <NDescriptionsItem v-if="selected.institutionHandover" label="实际承接记录">
          {{ handoverText(selected) }}
          <NButton
            v-permission="'COLLECTION_ADMIN,COLLECTION_STORAGE,COLLECTION_CROSS_ORG,COLLECTION_LEDGER_CORRECT'"
            text
            type="primary"
            @click="institutionRef?.open(selected.institutionHandover.caseId)"
            >查看机构承接案卷</NButton
          >
        </NDescriptionsItem>
      </NDescriptions>
    </HPageForm>
    <DeregistrationCaseDrawer :key="`deregistration-${detailEpoch}`" ref="exitRef" />
    <InstitutionSuccessionCaseDrawer :key="`institution-${detailEpoch}`" ref="institutionRef" @saved="load" />
  </NCard>
</template>
<script setup lang="ts">
import CollectionArchiveIssues from '../components/CollectionArchiveIssues.vue';
import { computed, h, onMounted, onBeforeUnmount, ref, watch } from 'vue';
import { NButton, useMessage } from 'naive-ui';
import { formatLocalDateValue } from '@/utils';
import { orgApi } from '../api/org';
import { auxiliaryApi } from '../api/auxiliary';
import type { EvidenceField } from '../utils/evidenceFields';
import CollectionEvidenceForm from '../components/CollectionEvidenceForm.vue';
import DeregistrationCaseDrawer from '../components/DeregistrationCaseDrawer.vue';
import InstitutionSuccessionCaseDrawer from '../components/InstitutionSuccessionCaseDrawer.vue';
const institutionRef = ref<any>();
function handoverText(row: any) {
  const r = row.institutionHandover;
  if (!r) return '未登记机构承接实交';
  return `${r.state === 'DISCREPANCY' ? '交接差异或后续问题待处理；' : r.investigationState === 'CONFIRMED' ? '差异处理已复核，原不一致事实保留；' : ''}已记录交出 ${r.handedQuantity} ${row.unit}，未交 ${r.remainingQuantity} ${row.unit}`;
}
const message = useMessage();
const formEpoch = ref(0),
  detailEpoch = ref(0);
const registrationSave = ref<(value: Record<string, any>) => Promise<any>>(async () => {
  throw new Error('请重新打开登记表');
});
const readError = ref(''),
  registrationTitle = ref('登记辅助账册原始记录'),
  detailError = ref(''),
  detailLoading = ref(false),
  evidenceIssue = ref(''),
  archiveId = ref(''),
  originalFiles = ref<{ fileId: string; metadata?: string | Record<string, unknown> }[]>([]);
let registrationVersion = 0;
let readVersion = 0,
  detailVersion = 0,
  active = true,
  registrationOrg = '';
const activeKind = ref('REPLICA');
const originalUrl = (fileId: string) =>
  `/collection/auxiliary/${selected.value?.orgId}/${selected.value?.id}/original/${archiveId.value}/${fileId}`;
function openRegistration() {
  if (!active || !orgId.value || loading.value || readError.value || activeKind.value === 'LAWFUL_EXIT') return;
  registrationOrg = orgId.value;
  const kind = activeKind.value;
  registrationTitle.value = `登记${kinds.find((item) => item.value === kind)?.label || '辅助账'} · ${orgOptions.value.find((o) => o.value === registrationOrg)?.label || registrationOrg}`;
  const epoch = ++registrationVersion,
    target = registrationOrg;
  registrationSave.value = (value) => register(value, target, kind, epoch);
  formRef.value?.open();
}
const orgId = ref<string>(),
  orgOptions = ref<{ label: string; value: string }[]>([]),
  rows = ref<Record<string, any>[]>([]),
  loading = ref(false);
const formRef = ref<any>(),
  exitRef = ref<any>(),
  selected = ref<Record<string, any>>(),
  detailShow = ref(false);
const kinds = [
  { label: '复仿制品', value: 'REPLICA' },
  { label: '模型', value: 'MODEL' },
  { label: '非文物教学标本', value: 'TEACHING_SPECIMEN' },
  { label: '当代图书资料', value: 'CONTEMPORARY_BOOK' },
  { label: '其他不符合入藏标准资料', value: 'OTHER_NON_COLLECTION' },
  { label: '依法退出馆藏', value: 'LAWFUL_EXIT' },
];
const activeKindLabel = computed(() => kinds.find((kind) => kind.value === activeKind.value)?.label || '辅助账资料');
const visibleRows = computed(() => rows.value.filter((row) => row.kind === activeKind.value));
const fields: EvidenceField[] = [
  { key: 'recordNo', label: '辅助账编号', required: true },
  { key: 'name', label: '名称', required: true },
  { key: 'quantity', label: '数量（正整数）', required: true },
  { key: 'unit', label: '计量单位', required: true },
  { key: 'location', label: '保管位置', required: true },
  { key: 'registeredOn', label: '实际登记日期', type: 'date', required: true },
  { key: 'reason', label: '类别判定及登记依据', type: 'textarea', required: true },
  { key: 'evidenceFileIds', label: '核收及判定原件', type: 'file', required: true },
];
const columns = [
  { ellipsis: { tooltip: true }, resizable: true, width: 160, title: '辅助账编号', key: 'recordNo' },
  { ellipsis: { tooltip: true }, resizable: true, width: 170, title: '类别', key: 'kind', render: (r: any) => kinds.find((k) => k.value === r.kind)?.label || r.kind },
  { ellipsis: { tooltip: true }, resizable: true, width: 200, title: '名称', key: 'name' },
  { width: 90, title: '数量', key: 'quantity' },
  { ellipsis: { tooltip: true }, resizable: true, width: 200, title: '原账记录位置', key: 'location' },
  { ellipsis: { tooltip: true }, resizable: true, width: 300, title: '机构承接实交记录', key: 'institutionHandover', render: (r: any) => handoverText(r) },
  { ellipsis: { tooltip: true }, resizable: true, width: 160, title: '原总登记号', key: 'originTotalRegNo' },
  { width: 140, title: '登记日期', key: 'registeredOn', render: (r: any) => formatLocalDateValue(r.registeredOn) },
  { width: 130,
    title: '原始记录',
    key: 'actions',
    render: (r: any) =>
      h(
        NButton,
        {
          text: true,
          type: 'primary',
          onClick: () => void openDetail(r),
        },
        () => '查看原件',
      ),
  },
];
function clearDetail() {
  ++detailVersion;
  selected.value = undefined;
  archiveId.value = '';
  evidenceIssue.value = '';
  originalFiles.value = [];
  detailError.value = '';
  detailLoading.value = false;
  detailShow.value = false;
  ++detailEpoch.value;
}
async function openDetail(row: any) {
  if (!active || loading.value || readError.value || !rows.value.includes(row)) return;
  const token = ++detailVersion;
  selected.value = undefined;
  archiveId.value = '';
  originalFiles.value = [];
  detailError.value = '';
  detailLoading.value = true;
  detailShow.value = true;
  try {
    const response: any = await auxiliaryApi.detail(row.orgId, row.id);
    if (token !== detailVersion || !detailShow.value || orgId.value !== row.orgId) return;
    const data = response.data;
    if (!data?.record || !data?.archiveId || !Array.isArray(data.files)) throw new Error('辅助账原件目录缺失');
    selected.value = data.record;
    archiveId.value = data.archiveId;
    evidenceIssue.value = data.evidenceIssue || "";
    originalFiles.value = data.files.filter((file: any) => typeof file?.fileId === 'string' && file.fileId);
  } catch (e) {
    if (token === detailVersion && detailShow.value)
      detailError.value = e instanceof Error ? e.message : '辅助账原件读取失败，请核对当前权限后重试';
  } finally {
    if (token === detailVersion) detailLoading.value = false;
  }
}
watch(detailShow, (value) => {
  if (!value) clearDetail();
});
function invalidateRegistration() {
  registrationOrg = '';
  ++registrationVersion;
  ++formEpoch.value;
}
async function load() {
  if (!active) return;
  clearDetail();
  const token = ++readVersion,
    target = orgId.value;
  rows.value = [];
  readError.value = '';
  loading.value = false;
  if (!target) {
    invalidateRegistration();
    return;
  }
  loading.value = true;
  try {
    const response: any = await auxiliaryApi.list(target);
    if (active && token === readVersion && target === orgId.value) rows.value = response.data || [];
  } catch (e) {
    if (active && token === readVersion) {
      readError.value = e instanceof Error ? e.message : '辅助账及实际承接记录读取失败';
      invalidateRegistration();
    }
  } finally {
    if (token === readVersion) loading.value = false;
  }
}
watch(orgId, () => {
  selected.value = undefined;
  detailShow.value = false;
  void load();
});
watch(activeKind, () => {
  clearDetail();
  invalidateRegistration();
});
onBeforeUnmount(() => {
  active = false;
  ++readVersion;
  invalidateRegistration();
  clearDetail();
});
onMounted(async () => {
  try {
    const response: any = await orgApi.page({ pageSize: 0 });
    if (!active) return;
    orgOptions.value = (response.data?.list || response.data || []).map((r: any) => ({ label: r.name, value: r.id }));
    if (orgOptions.value.length === 1) orgId.value = orgOptions.value[0].value;
  } catch (e) {
    if (!active) return;
    orgOptions.value = [];
    rows.value = [];
    readError.value = e instanceof Error ? e.message : '收藏单位读取失败';
    invalidateRegistration();
    clearDetail();
  }
});
async function register(value: Record<string, any>, target: string, kind: string, epoch: number) {
  if (!active || !registrationOrg || epoch !== registrationVersion || loading.value || readError.value)
    throw new Error('登记状态已失效，请刷新后重新打开登记表');
  const quantity = Number(value.quantity);
  if (!Number.isSafeInteger(quantity) || quantity < 1) {
    message.warning('数量须为正整数');
    return false;
  }
  await auxiliaryApi.register({ ...value, quantity, orgId: target, kind });
  if (!active || epoch !== registrationVersion) throw new Error('登记请求已返回，请重新查询实际登记结果');
  activeKind.value = kind;
  message.success('辅助账册已登记，原始凭证已归档');
}
</script>

<style scoped>
.auxiliary-ledger-page {
  height: 100%;
  min-height: 0;
}
.auxiliary-ledger-toolbar {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: 18px;
}
.auxiliary-ledger-table {
  flex: 1;
  min-height: 0;
}
</style>
