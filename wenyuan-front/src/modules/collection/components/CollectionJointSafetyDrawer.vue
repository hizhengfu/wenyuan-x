<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="多部门定期安全检查">
    <NSpin :show="loading"
      ><NSpace vertical :size="16">
      <CollectionArchiveIssues :data="data" />
        <NAlert type="info"
          >保管、提用和安全保卫部门共同开展实际检查。每件只有关联本轮真实现状检查并完成三方本人确认，才更新检查期限；发现问题应及时报告主要负责人。报告和签署不解除现状检查或事故限制。</NAlert
        >
        <NAlert v-if="error" type="error">{{ error }} <NButton @click="reload()">重新读取</NButton></NAlert>
        <NText>{{ data.documentNo || '提用单' }}</NText>
        <NAlert v-if="!loading && !error && !data.source" type="warning"
          >请先在馆内提用单核实完整使用、安全方案及责任人员。</NAlert
        >
        <NSpace size="small" align="center" v-if="!loading && !error">
          <NButton v-if="canOperate && data.source?.outExecuted && confirmedPlans.length" @click="edit('round')"
            >登记实际共同检查</NButton
          >
          <CollectionActionMenu
            label="方案与责任"
            v-if="
              canOperate &&
              ((data.source && !data.source.returned && !data.source.cancelled) ||
                data.custodyAvailable ||
                data.recallAvailable)
            "
          >
            <NButton
              quaternary
              v-if="canOperate && data.source && !data.source.returned && !data.source.cancelled"
              @click="edit('plan')"
              >{{ latest ? '制定新版共同方案' : '制定共同检查方案' }}</NButton
            >
            <NButton quaternary v-if="canOperate && data.custodyAvailable" @click="edit('custody')">登记在用责任承接</NButton>
            <NButton quaternary v-if="canOperate && data.recallAvailable" @click="edit('recall')">登记提前归库要求</NButton>
          </CollectionActionMenu>
          <NButton @click="reload()">刷新检查状态</NButton>
        </NSpace>
        <NCard
          v-for="item in data.items || []"
          :key="item.collectionId"
          :title="`${item.totalRegNo || ''} ${item.name || item.collectionId}`"
        >
          <NSpace vertical
            ><NText>{{ statuses[item.status] || item.status }} · 下次检查：{{ time(item.nextDueAt) }}</NText>
            <NText
              >待三方确认：{{ item.pendingRoundCount || 0 }} 轮；待报告或主要负责人处理：{{
                item.pendingDirectorCount || 0
              }}
              轮</NText
            >
            <NText v-if="item.returnDueAt"
              >当前归库期限：{{ time(item.returnDueAt) }}{{ item.returnOverdue ? ' · 已到期未接收' : '' }}</NText
            >
            <NAlert v-if="item.recallDueAt" type="warning"
              >已要求提前归库：{{ time(item.recallDueAt) }}。实际接收状态以逐件点交验收为准。</NAlert
            >
            <NButton :onClick="careAction(item.collectionId)">逐件现状检查、事故及处置</NButton>
          </NSpace>
        </NCard>
        <NTabs v-if="!loading && !error" type="line">
          <NTabPane name="recalls" tab="提前归库记录">
            <NAlert type="info"
              >保管部门负责人据实登记因防护措施或场所条件不符合要求而发出的归库要求。多次通知按逐件最早期限监督，后续通知不会延长已生效期限；登记通知不代表实物已归库。</NAlert
            >
            <NEmpty v-if="!data.recalls?.length" description="尚无提前归库记录" />
            <NCard
              v-for="entry in [...(data.recalls || [])].reverse()"
              :key="entry.id"
              :title="`实际通知 ${time(entry.request.issuedAt)}`"
            >
              <CollectionEvidenceReadView :data="entry.request" :fields="recallReadFields" :archive-files="metadataFiles(data.requisitionId, `EARLY_RETURN_${entry.id}`)" :original-url="originalUrl(data.requisitionId, `EARLY_RETURN_${entry.id}`)" />
            </NCard>
          </NTabPane>
          <NTabPane name="rounds" tab="共同检查与报告">
            <NEmpty v-if="!data.rounds?.length" description="尚未登记实际共同检查" />
            <NCard
              v-for="round in [...(data.rounds || [])].reverse()"
              :key="round.id"
              :title="`实际检查 ${time(round.content.endedAt)}`"
            >
              <NAlert :type="round.unsafe ? 'warning' : 'info'"
                >{{ round.confirmed ? '三方已确认实际参与' : '三方实际参与待本人确认'
                }}{{ round.unsafe ? '；含逐件不安全因素，请核对实际报告与处置' : '' }}</NAlert
              >
              <CollectionEvidenceReadView :fields="readRoundFields" :data="round.content" :archive-files="metadataFiles(round.id, 'ROUND')" :original-url="originalUrl(round.id, 'ROUND')" />
              <NCollapse
                ><NCollapseItem
                  v-for="check in round.checks"
                  :key="check.id"
                  :name="check.id"
                  :title="checkTitle(check)"
                  ><CollectionEvidenceReadView :fields="careFields.check" :data="check.content" :archive-files="metadataFiles(check.id, 'CHECKED')" :original-url="originalUrl(check.id, 'CHECKED')" /><NButton
                    :onClick="careAction(check.collectionId)"
                    >查阅逐件检查及事故处置</NButton
                  ></NCollapseItem
                ></NCollapse
              >
              <NSpace vertical
                ><div v-for="signature in round.signatures" :key="signature.role">
                  <NText
                    >{{ roles[signature.role] }} · {{ signature.actorUserId }} ·
                    {{ signature.signature ? time(signature.signature.confirmedAt) : '待本人确认' }}</NText
                  >
                  <CollectionEvidenceReadView
                    v-if="signature.signature"
                    :fields="fieldsByKind.sign"
                    :data="signature.signature.record"
                    :archive-files="metadataFiles(round.id, signature.role)" :original-url="originalUrl(round.id, signature.role)"
                  />
                  <NButton v-else-if="canConfirm(signature)" @click="edit('signRound', round.id, signature.role)"
                    >{{ roles[signature.role] }}本人确认参与</NButton
                  >
                </div></NSpace
              >
              <NButton v-if="canOperate && round.unsafe && !round.report" @click="edit('report', round.id)"
                >登记向主要负责人实际报告</NButton
              >
              <NButton
                v-if="canOperate && round.unsafe && !round.report && round.roundDigest"
                @click="edit('reportCurrent', round.id)"
                >向现任主要负责人报告（职责变更）</NButton
              >
              <CollectionEvidenceReadView
                v-if="round.currentReportBasis"
                :fields="fieldsByKind.currentReportBasis"
                :data="round.currentReportBasis"
                :archive-files="metadataFiles(round.id, 'DIRECTOR_CURRENT_REPORT_BASIS')" :original-url="originalUrl(round.id, 'DIRECTOR_CURRENT_REPORT_BASIS')"
              />
              <CollectionEvidenceReadView v-if="round.report" :fields="fieldsByKind.report" :data="round.report" :archive-files="metadataFiles(round.id, 'DIRECTOR_REPORT')" :original-url="originalUrl(round.id, 'DIRECTOR_REPORT')" />
              <NButton
                v-if="canAcknowledge(round)"
                @click="edit('director', round.id)"
                >主要负责人本人收悉及处理</NButton
              >
              <NButton
                v-if="canOperate && round.report && !round.director && !round.reportHandled && round.reportDigest"
                @click="edit('reportHandover', round.id)"
                >登记问题报告移交</NButton
              >
              <NCard
                v-for="handover in round.reportHandovers || []"
                :key="handover.id"
                :title="`报告移交 ${time(handover.content.handedAt)}`"
              >
                <NText>{{
                  handover.complete ? '新任主要负责人已收悉并记录处理意见' : '报告移交及本人收悉待确认'
                }}</NText>
                <CollectionEvidenceReadView :fields="fieldsByKind.reportHandover" :data="handover.content" :archive-files="metadataFiles(round.id, `REPORT_HANDOVER_P_${handover.id}`)" :original-url="originalUrl(round.id, `REPORT_HANDOVER_P_${handover.id}`)" />
                <NSpace vertical>
                  <div v-for="signature in handover.signatures" :key="signature.role">
                    <NText>{{ roles[signature.role] }} · {{ signature.actorUserId }}</NText>
                    <CollectionEvidenceReadView
                      v-if="signature.signature"
                      :fields="fieldsByKind.sign"
                      :data="signature.signature.record"
                      :archive-files="metadataFiles(round.id, `REPORT_HANDOVER_${signature.role}_${handover.id}`)" :original-url="originalUrl(round.id, `REPORT_HANDOVER_${signature.role}_${handover.id}`)"
                    />
                    <NButton
                      v-else-if="canSignReportHandover(round, handover, signature)"
                      @click="edit('signReportHandover', round.id, signature.role, handover.id)"
                      >{{
                        signature.role === 'KEEPER' ? '保管负责人本人确认报告移交' : '新任主要负责人本人收悉处理'
                      }}</NButton
                    >
                  </div>
                </NSpace>
              </NCard>
              <CollectionEvidenceReadView
                v-if="round.director"
                :fields="fieldsByKind.sign"
                :data="round.director.record"
                :archive-files="metadataFiles(round.id, 'DIRECTOR')" :original-url="originalUrl(round.id, 'DIRECTOR')"
              />
            </NCard>
          </NTabPane>
          <NTabPane name="plans" tab="共同方案版本">
            <NCard
              v-for="plan in [...(data.plans || [])].reverse()"
              :key="plan.id"
              :title="`第 ${plan.version} 版方案`"
            >
              <NText
                >{{ plan.confirmed ? '三方已确认' : '三方待确认'
                }}{{ !plan.sourceCurrent ? ' · 提用依据已变化' : '' }}</NText
              >
              <CollectionEvidenceReadView :fields="fieldsByKind.plan" :data="plan.content" :archive-files="metadataFiles(plan.id, 'PLAN')" :original-url="originalUrl(plan.id, 'PLAN')" />
              <NSpace vertical
                ><div v-for="signature in plan.signatures" :key="signature.role">
                  <NText
                    >{{ roles[signature.role] }} · {{ signature.actorUserId }} ·
                    {{ signature.signature ? time(signature.signature.confirmedAt) : '待本人确认' }}</NText
                  >
                  <CollectionEvidenceReadView
                    v-if="signature.signature"
                    :fields="fieldsByKind.sign"
                    :data="signature.signature.record"
                    :archive-files="metadataFiles(plan.id, signature.role)" :original-url="originalUrl(plan.id, signature.role)"
                  />
                  <NButton
                    v-else-if="
                      canConfirm(signature) &&
                      plan.id === latest?.id &&
                      plan.sourceCurrent &&
                      !data.source?.returned &&
                      !data.source?.cancelled
                    "
                    @click="edit('signPlan', plan.id, signature.role)"
                    >{{ roles[signature.role] }}本人确认方案</NButton
                  >
                </div></NSpace
              >
            </NCard>
          </NTabPane>
          <NTabPane v-if="data.source?.outExecuted || data.custody?.length" name="custody" tab="在用责任承接">
            <NAlert type="info"
              >先逐件核对尚在用的全部藏品，登记实际交接及未结事项，再由新任保管、提用人员和主要负责人本人确认。确认后另行制定共同检查方案；原审批、点交、已发生检查和未完成报告仍保留。</NAlert
            >
            <NEmpty v-if="!data.custody?.length" description="尚无独立在用承接记录" />
            <NCard
              v-for="entry in [...(data.custody || [])].reverse()"
              :key="entry.id"
              :title="`承接 ${time(entry.content.handedAt)}`"
            >
              <NText
                >{{
                  entry.id === data.effectiveCustodyId
                    ? '当前责任依据'
                    : entry.effective
                      ? '历史已确认依据'
                      : '待本人确认'
                }}
                · {{ entry.effectiveAt ? time(entry.effectiveAt) : '尚未生效' }}</NText
              >
              <NText
                >承接范围：{{
                  (entry.content.collectionIds || [])
                    .map((cid: string) => data.items?.find((item: any) => item.collectionId === cid)?.name || cid)
                    .join('、')
                }}</NText
              >
              <CollectionEvidenceReadView :fields="fieldsByKind.custody" :data="entry.content" :archive-files="metadataFiles(data.requisitionId, `CUSTODY_P_${entry.id}`)" :original-url="originalUrl(data.requisitionId, `CUSTODY_P_${entry.id}`)" />
              <NSpace vertical>
                <div v-for="signature in entry.signatures" :key="signature.role">
                  <NText>{{ roles[signature.role] }} · {{ signature.actorUserId }}</NText>
                  <CollectionEvidenceReadView
                    v-if="signature.signature"
                    :fields="fieldsByKind.sign"
                    :data="signature.signature.record"
                    :archive-files="metadataFiles(data.requisitionId, `CUSTODY_${signature.role}_${entry.id}`)" :original-url="originalUrl(data.requisitionId, `CUSTODY_${signature.role}_${entry.id}`)"
                  />
                  <NButton
                    v-else-if="canSignCustody(entry, signature)"
                    @click="edit('signCustody', entry.id, signature.role)"
                    >{{ roles[signature.role] }}本人确认承接</NButton
                  >
                </div>
              </NSpace>
            </NCard>
          </NTabPane>
          <NTabPane name="checks" tab="可关联的逐件检查原件">
            <NEmpty
              v-if="!data.availableChecks?.length"
              description="请先逐件记录真实现状检查，已关联其他轮次的检查不会重复使用"
            />
            <NCollapse
              ><NCollapseItem
                v-for="check in data.availableChecks || []"
                :key="check.id"
                :name="check.id"
                :title="checkTitle(check)"
                ><CollectionEvidenceReadView :fields="careFields.check" :data="check.content" :archive-files="metadataFiles(check.id, 'CHECKED')" :original-url="originalUrl(check.id, 'CHECKED')" /></NCollapseItem
            ></NCollapse>
          </NTabPane>
        </NTabs> </NSpace
    ></NSpin>
  </HPageForm>
  <CollectionEvidenceForm
    v-if="kind"
    :key="formEpoch"
    ref="formRef"
    :title="`${titles[kind]} · ${formTargetName}`"
    :fields="fields"
    :save="formSave"
    @saved="formSaved"
  />
  <CollectionCareDrawer v-if="show" :key="careEpoch" ref="careRef" />
</template>
<script setup lang="ts">
import CollectionActionMenu from './CollectionActionMenu.vue';
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
const metadataFiles = (recordId: string, phase: string) => data.value.archiveFiles?.[recordId]?.[phase] || [];
import { computed, defineAsyncComponent, nextTick, ref, watch, onActivated, onDeactivated, onBeforeUnmount } from 'vue';
import { cloneDeep } from 'lodash-es';
import { useAuthStore } from '@/store/modules/auth';
import { formatDateTime } from '@/utils';
import { jointSafetyApi as api } from '../api/jointSafety';
import {
  jointSafetyFields as fieldsByKind,
  jointSafetyStatuses as statuses,
  jointSafetyRoles as roles,
  jointSafetyTitles as titles,
} from '../utils/jointSafetyFields';
import { careFields } from '../utils/careFields';
import type { EvidenceField } from '../utils/evidenceFields';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
const CollectionCareDrawer = defineAsyncComponent(() => import('./CollectionCareDrawer.vue'));
const auth = useAuthStore();
const currentUserId = computed(() => auth.userInfo?.id || '');
const emit = defineEmits<{ (e: 'saved'): void }>();
const show = ref(false),
  loading = ref(false),
  error = ref(''),
  data = ref<any>({}),
  kind = ref(''),
  fields = ref<EvidenceField[]>([]),
  formRef = ref<any>(),
  careRef = ref<any>();
const careEpoch = ref(0);
const active = ref(true),
  formEpoch = ref(0),
  formTargetName = ref('');
const formSave = ref<(v: Record<string, any>) => Promise<any>>(async () => {
  throw new Error('表单已失效，请重新打开');
});
const formSaved = ref<() => void>(() => {});
const canOperate = computed(
  () => active.value && show.value && !loading.value && !error.value && data.value.canOperate === true,
);
function clearForm() {
  formEpoch.value++;
  kind.value = '';
  fields.value = [];
  formTargetName.value = '';
  editing = emptyEditing();
}
function close() {
  request++;
  careEpoch.value++;
  id = '';
  loading.value = false;
  error.value = '';
  data.value = {};
  clearForm();
  show.value = false;
}
watch(
  show,
  (value) => {
    if (!value) close();
  },
  { flush: 'sync' },
);
watch(currentUserId, close, { flush: 'sync' });
onActivated(() => {
  active.value = true;
});
onDeactivated(() => {
  active.value = false;
  close();
});
onBeforeUnmount(() => {
  active.value = false;
  close();
});
const latest = computed(() => data.value.plans?.at(-1));
const confirmedPlans = computed(() => (data.value.plans || []).filter((p: any) => p.confirmed));
function isCurrentActor(actorUserId: unknown) {
  return Boolean(currentUserId.value && actorUserId === currentUserId.value);
}
function canConfirm(signature: any) {
  return canOperate.value && signature && !signature.signature && isCurrentActor(signature.actorUserId);
}
function canAcknowledge(round: any) {
  return canOperate.value && round?.report && !round.director && !round.reportHandled &&
    isCurrentActor(round.report.directorUserId);
}
function canSignCustody(entry: any, signature: any) {
  return canConfirm(signature) && data.value.custodyAvailable && entry.id === data.value.custody?.at(-1)?.id &&
    (signature.role !== 'DIRECTOR' || ['KEEPER', 'USE'].every(role =>
      entry.signatures.some((person: any) => person.role === role && person.signature)));
}
function canSignReportHandover(round: any, handover: any, signature: any) {
  return canConfirm(signature) && !round.reportHandled && !round.director &&
    handover.id === round.reportHandovers?.at(-1)?.id &&
    (signature.role !== 'DIRECTOR' || handover.signatures.some((person: any) => person.role === 'KEEPER' && person.signature));
}
function canOpenAction(action: string, record: string, role: string, handoverId: string) {
  if (action === 'signPlan') {
    const plan = data.value.plans?.find((item: any) => item.id === record);
    return plan && plan.id === latest.value?.id && plan.sourceCurrent && !data.value.source?.returned &&
      !data.value.source?.cancelled && canConfirm(plan.signatures?.find((person: any) => person.role === role));
  }
  if (action === 'signRound' || action === 'director' || action === 'signReportHandover') {
    const round = data.value.rounds?.find((item: any) => item.id === record);
    if (!round) return false;
    if (action === 'director') return canAcknowledge(round);
    if (action === 'signRound') return canConfirm(round.signatures?.find((person: any) => person.role === role));
    const handover = round.reportHandovers?.find((item: any) => item.id === handoverId);
    return handover && canSignReportHandover(round, handover, handover.signatures?.find((person: any) => person.role === role));
  }
  if (action === 'signCustody') {
    const entry = data.value.custody?.find((item: any) => item.id === record);
    return entry && canSignCustody(entry, entry.signatures?.find((person: any) => person.role === role));
  }
  return true;
}

const recallReadFields = computed(() =>
  fieldsByKind.recall.map((f) =>
    f.key === 'collectionIds'
      ? {
          ...f,
          options: (data.value.items || []).map((i: any) => ({
            value: i.collectionId,
            label: `${i.totalRegNo || ''} ${i.name || i.collectionId}`,
          })),
        }
      : f,
  ),
);
const readRoundFields = computed(() =>
  fieldsByKind.round.map((f) =>
    f.key === 'planId'
      ? {
          ...f,
          options: (data.value.plans || []).map((p: any) => ({ value: p.id, label: `第 ${p.version} 版方案` })),
        }
      : f.key === 'checkIds'
        ? {
            ...f,
            options: (data.value.rounds || []).flatMap((r: any) =>
              r.checks.map((c: any) => ({ value: c.id, label: checkTitle(c) })),
            ),
          }
        : f,
  ),
);
const time = (value: any) => (value ? formatDateTime(value) : '尚无有效安排或已接收');
function originalUrl(recordId: string, phase: string) {
  return (fileId: string) =>
    `/collection/joint-safety/${encodeURIComponent(data.value.requisitionId)}/original/${encodeURIComponent(recordId)}/${encodeURIComponent(phase)}/${encodeURIComponent(fileId)}`;
}
function checkTitle(check: any) {
  const item = data.value.items?.find((i: any) => i.collectionId === check.collectionId);
  const unsafe = check.content.damaged || !check.content.physicallyPresent || !check.content.safeForRoutineCare;
  return `${item?.totalRegNo || ''} ${item?.name || check.collectionId} · ${time(check.content.checkedAt)} · ${unsafe ? '有不安全因素' : '已记录现状'}`;
}
const emptyEditing = () => ({
  id: '',
  orgId: '',
  record: '',
  role: '',
  previousPlanId: '',
  revision: '',
  kind: '',
  recallPreviousId: '',
  custodyPreviousId: '',
  effectiveCustodyId: '',
  collectionIds: [] as string[],
  reportDigest: '',
  roundDigest: '',
  reportPreviousId: '',
  reportHandoverId: '',
});
let id = '',
  request = 0,
  editing = emptyEditing();
async function open(value: string) {
  if (!active.value) return;
  const preserve = show.value && value !== id;
  id = value;
  show.value = true;
  await reload(preserve);
}
async function reload(preserve = false) {
  if (!show.value || !active.value || !id) return;
  careEpoch.value++;
  if (!preserve) clearForm();
  const token = ++request,
    target = id;
  loading.value = true;
  error.value = '';
  data.value = {};
  try {
    const response: any = await api.detail(target);
    if (token !== request || !show.value || !active.value) return;
    const value = response?.data;
    if (
      value?.requisitionId !== target ||
      !value.orgId ||
      typeof value.canOperate !== 'boolean' ||
      !['plans', 'rounds', 'items', 'availableChecks'].every((key) => Array.isArray(value[key])) ||
      (value.source && typeof value.sourceRevision !== 'string')
    )
      throw new Error('共同检查资料不完整，请重新读取');
    data.value = value;
  } catch (e: any) {
    if (token === request && show.value && active.value) {
      data.value = {};
      clearForm();
      error.value = e?.message || '原件读取失败，请核查后重试';
    }
  } finally {
    if (token === request) loading.value = false;
  }
}
function careAction(collectionId: string) {
  const token = request,
    target = id;
  return () => {
    if (active.value && show.value && !loading.value && !error.value && token === request && target === id)
      careRef.value?.open(collectionId);
  };
}
async function edit(action: string, record = '', role = '', reportHandoverId = '') {
  if (!canOperate.value || !canOpenAction(action, record, role, reportHandoverId)) return;
  clearForm();
  editing = {
    id,
    orgId: data.value.orgId,
    record,
    role,
    previousPlanId: latest.value?.id || '',
    revision: data.value.sourceRevision,
    recallPreviousId: data.value.recalls?.at(-1)?.id || '',
    custodyPreviousId: data.value.custody?.at(-1)?.id || '',
    effectiveCustodyId: data.value.effectiveCustodyId || '',
    collectionIds: (data.value.items || [])
      .filter((item: any) => !item.actualReceived)
      .map((item: any) => item.collectionId),
    kind: action,
    reportDigest: data.value.rounds?.find((r: any) => r.id === record)?.reportDigest || '',
    roundDigest: data.value.rounds?.find((r: any) => r.id === record)?.roundDigest || '',
    reportPreviousId: data.value.rounds?.find((r: any) => r.id === record)?.reportHandovers?.at(-1)?.id || '',
    reportHandoverId,
  };
  const owned = cloneDeep(editing),
    epoch = formEpoch.value;
  const current = () => active.value && show.value && formEpoch.value === epoch && kind.value !== '';
  let submitting = false;
  formTargetName.value = `${data.value.documentNo || owned.id} · 收藏单位：${data.value.orgName || owned.orgId}（${owned.orgId}）`;
  formSave.value = async (value) => {
    if (!current()) throw new Error('表单已失效，请重新打开');
    if (submitting) throw new Error('本次办理正在提交，请勿重复提交');
    submitting = true;
    try {
      const result: any = await saveFor(owned, cloneDeep(value));
      if (typeof result?.data !== 'string' || !result.data.trim()) throw new Error('办理回执不完整，请回原提用单核实');
      return result;
    } finally {
      submitting = false;
    }
  };
  formSaved.value = () => {
    if (!current()) return;
    const same = id === owned.id;
    clearForm();
    if (same) {
      emit('saved');
      void reload();
    }
  };
  kind.value = action;
  const fieldKind = ['signPlan', 'signRound', 'director', 'signCustody', 'signReportHandover'].includes(action)
    ? 'sign'
    : action;
  fields.value = fieldsByKind[fieldKind].map((f) =>
    f.key === 'collectionIds'
      ? {
          ...f,
          options: (data.value.items || [])
            .filter((i: any) => !i.actualReceived)
            .map((i: any) => ({ value: i.collectionId, label: `${i.totalRegNo || ''} ${i.name || i.collectionId}` })),
        }
      : f.key === 'planId'
        ? {
            ...f,
            options: confirmedPlans.value.map((p: any) => ({
              value: p.id,
              label: `第 ${p.version} 版 · 实际开始时须已三方确认`,
            })),
          }
        : f.key === 'checkIds'
          ? {
              ...f,
              options: ((action === 'custody' ? data.value.custodyChecks : data.value.availableChecks) || []).map(
                (c: any) => ({ value: c.id, label: checkTitle(c) }),
              ),
            }
          : f,
  );
  const initial = ['plan', 'custody'].includes(action)
    ? { ...data.value.actors }
    : action === 'report'
      ? { directorUserId: data.value.rounds?.find((r: any) => r.id === record)?.directorUserId }
      : {};
  await nextTick();
  if (current()) formRef.value?.open(cloneDeep(initial));
}
async function saveFor(target: ReturnType<typeof emptyEditing>, value: Record<string, any>) {
  if (!target.orgId) throw new Error('缺少本次收藏单位确认，请重新读取');
  const record = (path: string, body: Record<string, any>) => api.record(target.id, path, body, target.orgId);
  if (target.kind === 'recall') {
    if (!value.collectionIds?.length || value.collectionIds.some((cid: string) => !target.collectionIds.includes(cid)))
      throw new Error('请选择本次尚未接收的藏品');
    return record('recall', { ...value, previousId: target.recallPreviousId || null, sourceRevision: target.revision });
  }
  if (target.kind === 'reportCurrent') {
    if (!target.roundDigest) throw new Error('缺少原检查核对依据，请重新读取');
    const report = Object.fromEntries(
      ['reportedAt', 'reporterUserId', 'directorUserId', 'content', 'immediateMeasures', 'signedFileIds'].map((key) => [
        key,
        value[key],
      ]),
    );
    return record(`round/${target.record}/report-current`, {
      expectedRoundDigest: target.roundDigest,
      appointmentFileIds: value.appointmentFileIds,
      changeReason: value.changeReason,
      report,
    });
  }
  if (target.kind === 'reportHandover') {
    if (!target.reportDigest) throw new Error('缺少原问题报告核对依据，请重新读取');
    return record(`round/${target.record}/report-handover`, {
      ...value,
      previousId: target.reportPreviousId || null,
      expectedReportDigest: target.reportDigest,
    });
  }
  if (target.kind === 'signReportHandover')
    return record(`round/${target.record}/report-handover/${target.reportHandoverId}/${target.role}`, value);
  if (target.kind === 'custody')
    return record('custody', {
      ...value,
      previousId: target.custodyPreviousId || null,
      expectedEffectiveId: target.effectiveCustodyId || null,
      sourceRevision: target.revision,
      collectionIds: target.collectionIds,
      checkIds: value.checkIds || [],
    });
  if (target.kind === 'signCustody') return record(`custody/${target.record}/${target.role}`, value);
  if (target.kind === 'plan')
    return record('plan', { ...value, previousPlanId: target.previousPlanId || null, sourceRevision: target.revision });
  if (target.kind === 'signPlan') return record(`plan/${target.record}/${target.role}`, value);
  if (target.kind === 'round') return record('round', { ...value, checkIds: value.checkIds || [] });
  if (target.kind === 'signRound') return record(`round/${target.record}/sign/${target.role}`, value);
  return record(`round/${target.record}/${target.kind}`, value);
}
defineExpose({ open, close });
</script>
