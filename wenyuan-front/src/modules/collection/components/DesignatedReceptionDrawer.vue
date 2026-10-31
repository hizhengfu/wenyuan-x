<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="行政指定接收与另行承接">
    <NSpace vertical :size="16">
      <CollectionArchiveIssues :data="record" />
      <NAlert type="info"
        >依据实际行政指定和专业鉴定评估办理。符合本馆标准应接收，不符合的须及时报请原指定部门另行指定；这里的登记不代替真实报请、行政决定或实物交接。</NAlert
      >
      <NAlert v-if="error" type="error">{{ error }} <NButton @click="retry">重新读取</NButton></NAlert>
      <NSpace
        ><NButton @click="edit('notice')" :disabled="loading || !canCreate">登记行政指定原件</NButton
        ><NInput v-model:value="keyword" placeholder="对象名称、原编号或文号" clearable style="width: 330px" /><NButton
          @click="search"
          >查询记录</NButton
        ></NSpace
      >
      <NSpin :show="loading">
        <NDataTable :columns="columns" :data="items" :row-key="(r: any) => r.id" :scroll-x="1060" />
        <NPagination :page="page" :page-count="Math.max(1, Math.ceil(total / 20))" @update:page="changePage" />
      </NSpin>
      <NCard v-if="record" :title="record.notice.item.name">
        <NText
          >原对象编号：{{ record.notice.item.sourceItemNo }} ·
          {{ designatedStates[record.state] || record.state }}</NText
        >
        <NAlert v-if="record.state === 'MUST_REFER'" type="warning"
          >已确认不符合本馆入藏标准。请及时向原指定部门报请另行指定，并保存实际送达凭据。</NAlert
        >
        <NAlert v-if="record.state === 'READY_FOR_ACCESSION'" type="info"
          >接收准备已核对。待入馆对象编号：{{
            record.acquisitionId
          }}。还须通过入馆凭证逐件核收及后续登记，不能将行政指定视为已经登账。</NAlert
        >
        <NAlert v-if="!canOperate" type="info">当前仅可查阅本案，办理须核对本馆实际职责。</NAlert>
        <NSpace v-if="canOperate">
          <NButton
            v-if="['AWAIT_ASSESSMENT', 'MUST_RECEIVE', 'MUST_REFER'].includes(record.state)"
            @click="edit('assessment')"
            >{{ record.assessmentId ? '追加评估订正' : '登记专业鉴定评估' }}</NButton
          >
          <NButton v-if="!closed" @click="edit('custody')">{{
            record.custodyId ? '补充实际保管原件' : '登记实际保管原件'
          }}</NButton>
          <NButton v-if="record.state === 'MUST_RECEIVE'" @click="edit('acceptance')">本人核对接收及实际取得</NButton>
          <NButton v-if="record.state === 'MUST_REFER'" @click="edit('referral')">登记已实际报请另行指定</NButton>
          <NButton v-if="record.state === 'AWAIT_AUTHORITY'" @click="edit('reassignment')"
            >登记收到的另行指定决定</NButton
          >
          <NButton v-if="record.state === 'AWAIT_HANDOVER'" @click="edit('settlement')">登记实际承接结果</NButton>
        </NSpace>
        <CollectionEvidenceReadView :fields="designatedFields('notice', context)" :data="record.notice" :archive-files="record.archiveFiles?.[record.id]?.NOTICE" :original-url="originalUrl" />
        <NCard
          v-for="r in record.records"
          :key="r.id"
          :title="`${designatedTitles[r.kind.toLowerCase()]} · ${formatDateTime(r.recordedAt)}`"
        >
          <CollectionEvidenceReadView
            :fields="designatedFields(r.kind.toLowerCase(), context, record.orgId)"
            :data="r.content"
            :archive-files="record.archiveFiles?.[r.id]?.[r.kind]" :original-url="originalUrl"
          />
        </NCard>
      </NCard>
    </NSpace>
  </HPageForm>
  <CollectionEvidenceForm v-if="show" ref="formRef" :title="title" :fields="fields" :save="save" @saved="saved" />
</template>
<script setup lang="ts">
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
import { computed, h, nextTick, onActivated, onDeactivated, onBeforeUnmount, ref, watch } from 'vue';
import { NButton } from 'naive-ui';
import { cloneDeep } from 'lodash-es';
import { formatDateTime } from '@/utils';
import { designatedReceptionApi as api } from '../api/designatedReception';
import {
  designatedFields,
  designatedStates,
  designatedTitles,
  designatedValues,
} from '../utils/designatedReceptionFields';
import type { EvidenceField } from '../utils/evidenceFields';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
const emit = defineEmits(['saved']);
const active = ref(true);
const show = ref(false),
  loading = ref(false),
  contextReady = ref(false),
  record = ref<any>(),
  items = ref<any[]>([]),
  context = ref<any>({}),
  page = ref(1),
  total = ref(0),
  keyword = ref(''),
  error = ref(''),
  fields = ref<EvidenceField[]>([]),
  title = ref(''),
  formRef = ref<any>(),
  save = ref<(value: Record<string, any>) => Promise<unknown>>(async () => false);
const canCreate = computed(
  () => contextReady.value && Array.isArray(context.value.orgs) && context.value.orgs.length > 0,
);
const canOperate = computed(() => contextReady.value && record.value?.canOperate === true);
function originalUrl(fileId: string) {
  return `/collection/designated-reception/${encodeURIComponent(record.value?.id || '')}/original/${encodeURIComponent(fileId)}`;
}
let completion: { view: number; token: number; id: string } | undefined;
let editorTargetId = '';
const closed = computed(() => ['READY_FOR_ACCESSION', 'REASSIGNED_RECEIVED'].includes(record.value?.state));
let session = 0,
  request = 0,
  detailRequest = 0,
  editRequest = 0,
  selected = '';
const columns = [
  { title: '指定对象', key: 'notice.item.name', width: 200, minWidth: 140, resizable: true, ellipsis: { tooltip: true }, render: (r: any) => r.notice.item.name },
  { title: '原移交物编号', key: 'notice.item.sourceItemNo', width: 180, minWidth: 130, resizable: true, ellipsis: { tooltip: true }, render: (r: any) => r.notice.item.sourceItemNo },
  { title: '指定文号', key: 'notice.documentNo', width: 200, minWidth: 140, resizable: true, ellipsis: { tooltip: true }, render: (r: any) => r.notice.documentNo },
  { title: '办理状态', key: 'state', width: 180, minWidth: 120, resizable: true, ellipsis: { tooltip: true }, render: (r: any) => designatedStates[r.state] || r.state },
  { title: '评估或通知时间', key: 'pendingSince', width: 190, minWidth: 160, resizable: true, ellipsis: { tooltip: true }, render: (r: any) => formatDateTime(r.pendingSince) },
  {
    title: '操作',
    key: 'actions',
    width: 110,
    render: (r: any) => h(NButton, { text: true, onClick: () => select(r.id) }, () => '查看办理'),
  },
];
async function reload() {
  const token = ++request;
  loading.value = true;
  error.value = '';
  items.value = [];
  total.value = 0;
  try {
    const r: any = await api.page(page.value, keyword.value);
    if (token !== request || !show.value) return;
    if (
      !Array.isArray(r?.data?.list) ||
      !Number.isInteger(Number(r.data.total)) ||
      Number(r.data.total) < r.data.list.length
    )
      throw new Error('案卷列表回执不完整，请重新读取');
    items.value = r.data.list;
    total.value = Number(r.data.total);
  } catch (e) {
    if (token === request) error.value = e instanceof Error ? e.message : '指定接收记录读取失败';
  } finally {
    if (token === request) loading.value = false;
  }
}
async function select(id: string) {
  if (editorTargetId === id) {
    ++editRequest;
    completion = undefined;
    formRef.value?.close?.();
  }
  selected = id;
  const token = ++detailRequest;
  record.value = undefined;
  error.value = '';
  try {
    const r: any = await api.detail(id);
    if (token === detailRequest && show.value) {
      if (
        r?.data?.id !== id ||
        typeof r.data.orgId !== 'string' ||
        !r.data.orgId.trim() ||
        !r.data.notice ||
        !Array.isArray(r.data.records)
      )
        throw new Error('案卷详情与当前对象不一致，请重新读取');
      record.value = r.data;
    }
  } catch (e) {
    if (token === detailRequest) error.value = e instanceof Error ? e.message : '指定接收原件读取失败';
  }
}
async function open(id?: string) {
  if (!active.value) return;
  invalidate();
  const view = session;
  ++request;
  ++detailRequest;
  ++editRequest;
  show.value = true;
  record.value = undefined;
  selected = id || '';
  page.value = 1;
  keyword.value = '';
  context.value = {};
  contextReady.value = false;
  loading.value = true;
  error.value = '';
  const token = request;
  try {
    const r: any = await api.context();
    if (token !== request || !show.value) return;
    if (!Array.isArray(r?.data?.orgs)) throw new Error('办理范围回执不完整，请重新读取');
    context.value = r.data;
    contextReady.value = true;
    await reload();
    if (id && view === session && show.value) await select(id);
  } catch (e) {
    if (token === request) error.value = e instanceof Error ? e.message : '本馆办理范围读取失败';
  } finally {
    if (token === request) loading.value = false;
  }
}
async function retry() {
  await open(selected || undefined);
}
function search() {
  page.value = 1;
  void reload();
}
function changePage(v: number) {
  page.value = v;
  void reload();
}
async function edit(kind: string) {
  if (
    !active.value ||
    !show.value ||
    !contextReady.value ||
    loading.value ||
    (kind === 'notice' ? !canCreate.value : !canOperate.value)
  )
    return;
  const token = ++editRequest;
  const view = session;
  const target = cloneDeep({
    id: record.value?.id || '',
    orgId: record.value?.orgId,
    allowedOrgs: cloneDeep(context.value.orgs || []),
    kind,
    selected,
    previousId:
      kind === 'assessment' ? record.value?.assessmentId : kind === 'custody' ? record.value?.custodyId : undefined,
  });
  completion = undefined;
  editorTargetId = target.id;
  let inFlight = false,
    finished = false;
  const live = () =>
    active.value && view === session && token === editRequest && show.value && formRef.value?.isOpen?.() !== false;
  save.value = async (value) => {
    if (!live() || inFlight || finished) return false;
    const v = designatedValues(cloneDeep(value));
    if (target.previousId) v.previousId = target.previousId;
    const orgId = kind === 'notice' ? v.orgId : target.orgId;
    if (
      typeof orgId !== 'string' ||
      !orgId.trim() ||
      (kind === 'notice' && !target.allowedOrgs.some((o: any) => o.value === orgId))
    )
      throw new Error('请重新核对本次办理的收藏单位');
    inFlight = true;
    try {
      const result: any = kind === 'notice' ? await api.create(v, orgId) : await api.record(target.id, kind, v, orgId);
      if (!live()) return false;
      if (typeof result?.data !== 'string' || !result.data.trim()) {
        finished = true;
        throw new Error('办理回执不完整，请返回原案卷核实实际状态');
      }
      finished = true;
      if (kind === 'notice' && selected === target.selected) selected = result.data;
      completion = { view, token, id: kind === 'notice' ? result.data : target.id };
      emit('saved');
      return live();
    } catch (e) {
      if (!live()) return false;
      throw e;
    } finally {
      inFlight = false;
    }
  };
  title.value = `${designatedTitles[kind]}${kind === 'notice' ? '' : ` · ${record.value.notice.item.name}`}`;
  if (kind !== 'notice') title.value += ` · ${target.orgId}`;
  fields.value = designatedFields(kind, cloneDeep(context.value), target.orgId);
  await nextTick();
  if (!active.value || view !== session || token !== editRequest || !show.value) return;
  formRef.value?.open(kind === 'notice' ? { orgId: context.value.currentOrgId } : {});
}
async function saved() {
  const done = completion;
  completion = undefined;
  if (
    !done ||
    !active.value ||
    !show.value ||
    done.view !== session ||
    done.token !== editRequest ||
    done.id !== selected
  )
    return;
  await open(done.id);
}
function invalidate() {
  editorTargetId = '';
  completion = undefined;
  formRef.value?.close?.();
  contextReady.value = false;
  context.value = {};
  total.value = 0;
  ++session;
  ++request;
  ++detailRequest;
  ++editRequest;
  loading.value = false;
  record.value = undefined;
  items.value = [];
}
watch(
  show,
  (v) => {
    if (!v) invalidate();
  },
  { flush: 'sync' },
);
onActivated(() => {
  active.value = true;
});
onDeactivated(() => {
  active.value = false;
  show.value = false;
  invalidate();
});
onBeforeUnmount(invalidate);
defineExpose({ open });
</script>
