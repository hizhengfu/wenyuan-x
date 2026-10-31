<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="材质脆弱性与使用保护"
    ><NSpin :show="loading"
      ><NSpace vertical :size="16">
      <CollectionArchiveIssues :data="data" />
        <NAlert type="info"
          >限值按本件实际评估填写，没有统一默认天数或次数。出库点交、实际展出和实际运输分别记录；已确认但未补齐实际记录的安排继续占用频次。未知材质状态须先核实。</NAlert
        >
        <NAlert v-if="readError" type="error">{{ readError }} <NButton @click="reload()">重新读取</NButton></NAlert>
        <NText v-if="data.collectionId && !readError"
          >{{ data.name }} · 当前已确认结论：{{
            active ? (active.content.fragileMaterial ? '材质脆弱，执行逐件使用限制' : '已核实非脆弱材质') : '尚未核实'
          }}</NText
        >
        <NAlert v-if="automaticError" type="warning">{{ automaticError }}</NAlert>
        <NSpace v-if="canOperate"
          ><NButton @click="edit('assessment')">{{ latest ? '追加评估版本' : '登记实际脆弱性评估' }}</NButton
          ><NButton @click="edit('history')">补录既有展出或运输事实</NButton
          ><NButton @click="openCare">登记实际现状检查</NButton></NSpace
        >
        <NCard v-if="canOperate && active?.content.fragileMaterial" title="核对本次具体使用来源">
          <NSpace
            ><NSelect v-model:value="sourceType" :options="sourceOptions" style="width: 180px" /><NInput
              v-model:value="sourceId"
              placeholder="已保存完整方案的提用或借用单编号"
              style="width: 340px"
            /><NButton :loading="checkingSource" @click="loadSource">核对使用来源</NButton></NSpace
          >
          <NSpace v-if="sourceData"
            ><NText
              >核对范围：{{ time(sourceData.expectedOutAt) }} 至 {{ time(sourceData.expectedReturnAt) }}；{{
                sourceData.display ? '展览用途' : '其他使用用途'
              }}</NText
            ><NButton v-if="sourceData.editable" @click="edit('plan')">制定本次使用保护方案</NButton></NSpace
          >
        </NCard>
        <NTabs v-if="data.collectionId && !readError" type="line">
          <NTabPane name="records" tab="实际发生与现状复核"
            ><NCard
              v-for="r in [...(data.records || [])].reverse()"
              :key="r.id"
              :title="r.history ? '既有事实补录（不产生当时授权）' : r.ended ? '已登记实际结束' : '已登记实际开始'"
            >
              <NText>记录编号：{{ r.id }}</NText>
              <NText v-if="r.authorizationAdjustmentId">本次依据的变更原件：{{ r.authorizationAdjustmentId }}</NText>
              <NAlert v-if="r.started && !r.ended && r.plannedEnd && Number(r.plannedEnd) < nowTime" type="warning"
                >本段计划结束时间已过，请及时核实、采取措施并如实记录实际结束；系统不会自动填写已经结束。</NAlert
              >
              <CollectionEvidenceReadView
                v-if="r.history"
                :fields="fragilityFields.history"
                :data="r.history"
                :archive-files="metadataFiles(r.id, 'HISTORY')" :original-url="originalUrl(r.id, 'HISTORY')"
              /><CollectionEvidenceReadView
                v-if="r.started"
                :fields="fragilityFields.start"
                :data="{ ...r.started, segmentIndex: Number(r.started.segmentIndex) + 1 }"
                :archive-files="[...metadataFiles(r.id, 'STARTED'), ...metadataFiles(r.id, 'ENVIRONMENT_VERIFIED')]" :original-url="(fileId: string, fieldKey: string) => originalUrl(r.id, fieldKey === 'environmentReadings.signedFileIds' ? 'ENVIRONMENT_VERIFIED' : 'STARTED')(fileId)"
              />
              <CollectionEvidenceReadView
                v-if="r.environmentVerification"
                :fields="fragileEnvironmentVerificationFields"
                :data="r.environmentVerification"
                :archive-files="[...metadataFiles(r.planId, 'USE_PLAN'), ...metadataFiles(r.id, 'ENVIRONMENT_VERIFIED')]" :original-url="(fileId: string, fieldKey: string) => originalUrl(fieldKey === 'metrics.source.qualificationFileIds' ? r.planId : r.id, fieldKey === 'metrics.source.qualificationFileIds' ? 'USE_PLAN' : 'ENVIRONMENT_VERIFIED')(fileId)"
              />
              <NText v-else-if="r.started" type="warning"
                >旧实际记录未留存本次新增的数值环境核验，不据此推定当时符合要求。</NText
              ><CollectionEvidenceReadView
                v-if="r.ended"
                :fields="fragilityFields.end"
                :data="r.ended"
                :archive-files="metadataFiles(r.id, 'ENDED')" :original-url="originalUrl(r.id, 'ENDED')"
              /><CollectionEvidenceReadView v-if="r.review" :fields="fragilityFields.review" :data="r.review" :archive-files="metadataFiles(r.id, 'REVIEWED')" :original-url="originalUrl(r.id, 'REVIEWED')" />
              <FragileAutomaticMonitoring :archive-files="data.archiveFiles"
                v-if="r.started"
                :collection-id="data.collectionId"
                :org-id="data.orgId"
                :use-id="r.id"
                :status="r.automaticMonitoring"
                :clearance-available="!!monitorClearRecord(r.id)"
                :can-operate="canOperate"
                v-on="automaticActions(r.id)"
              />
              <NButton v-if="canOperate && r.started" @click="edit('monitor', r.id)">登记使用期间环境核验</NButton>
              <NCard
                v-for="m in (data.monitoringReports || []).filter((m: any) => m.useRecordId === r.id)"
                :key="m.id"
                :title="`监测核验 · ${time(m.request.checkedAt)}`"
              >
                <NAlert v-if="m.evidence.needsHandling" :type="m.clearance ? 'info' : 'warning'" :show-icon="false">
                  {{
                    m.clearance
                      ? '原异常已关联处置核对，原始证据仍完整保留。'
                      : '本次存在监测证据缺口、超限或实物异常，后续正常读数不能解除普通使用限制。请核实并及时采取实际措施；软件不会自动停止实物使用。'
                  }}
                </NAlert>
                <CollectionEvidenceReadView :fields="fragilityFields.monitor" :data="m.request" :archive-files="metadataFiles(m.id, 'MONITORED')" :original-url="originalUrl(m.id, 'MONITORED')" />
                <CollectionEvidenceReadView :fields="fragileMonitoringEvidenceFields" :data="m.evidence" />
                <FragileMonitoringSamples :archive-files="data.archiveFiles"
                  :collection-id="data.collectionId"
                  :org-id="data.orgId"
                  :monitor-id="m.id"
                  :sample-count="
                    (m.evidence.metrics || []).reduce((n: number, x: any) => n + Number(x.sampleCount || 0), 0)
                  "
                />
                <NButton v-if="canOperate && m.evidence.needsHandling" @click="monitorIncident(r.id)">关联本次使用异常处置</NButton>
                <NButton v-if="canOperate && m.evidence.needsHandling" @click="edit('monitorClear', m.id)"
                  >核对本次使用监测异常处置</NButton
                >
                <NCard
                  v-for="v in m.clearances || (m.clearance ? [m.clearance] : [])"
                  :key="v.version"
                  :title="`第 ${v.version} 版处置核对 · ${v.version === m.clearance?.version ? '当前版本' : '历史版本'}`"
                >
                  <CollectionEvidenceReadView :fields="careFields.clear" :data="v.record" :archive-files="metadataFiles(m.id, `MONITOR_CLEARED_${v.version}`)" :original-url="originalUrl(m.id, `MONITOR_CLEARED_${v.version}`)" />
                  <NButton @click="openIncident(v.record.incidentId)">查看本版关联事故</NButton>
                </NCard>
              </NCard>
              <NButton v-if="canOperate && r.started && !r.ended" @click="edit('end', r.id)">登记实际结束</NButton
              ><NButton v-if="canOperate && r.ended && !r.review" @click="edit('review', r.id)">核对使用后实际检查</NButton>
            </NCard></NTabPane
          >
          <NTabPane name="plans" tab="具体使用保护方案"
            ><NCard
              v-for="p in [...(data.plans || [])].reverse()"
              :key="p.id"
              :title="`${p.content.sourceBizType === 'COLLECTION_LOAN' ? '借用' : '提用'}：${p.content.sourceBizId}`"
            >
              <CollectionEvidenceReadView :fields="fragilityFields.plan" :data="p.content" :archive-files="metadataFiles(p.id, 'USE_PLAN')" :original-url="originalUrl(p.id, 'USE_PLAN')" /><NText
                >保护修复确认：{{ p.conservation?.actorUserId || '待本人确认' }}；保管确认：{{
                  p.keeper?.actorUserId || '待本人确认'
                }}</NText
              ><CollectionEvidenceReadView
                v-if="p.conservation"
                :fields="fragilityFields.sign"
                :data="p.conservation.record"
                :archive-files="metadataFiles(p.id, 'CONSERVATION')" :original-url="originalUrl(p.id, 'CONSERVATION')"
              />
              <CollectionEvidenceReadView v-if="p.keeper" :fields="fragilityFields.sign" :data="p.keeper.record" :archive-files="metadataFiles(p.id, 'KEEPER')" :original-url="originalUrl(p.id, 'KEEPER')" />
              <NSpace
                ><NButton v-if="canOperate && !p.keeper && p.current && p.sourceEditable" @click="edit('sign', p.id)"
                  >本人确认本次使用方案</NButton
                ><NButton
                  v-if="
                    canOperate && p.keeper &&
                    p.current &&
                    p.sourceOutExecuted &&
                    !p.adjustmentPending &&
                    availableSegments(p).length &&
                    !(data.records || []).some((r: any) => r.started && (!r.ended || !r.review))
                  "
                  @click="edit('start', p.id)"
                  >登记实际展出或运输开始</NButton
                ><NButton
                  v-if="canOperate && p.keeper && p.sourceHasOutbound && availableSegments(p).length"
                  @click="edit('adjustment', p.id)"
                >
                  核实未实施段及使用变更
                </NButton></NSpace
              >
              <NAlert v-if="p.adjustmentPending" type="warning"
                >最新变更尚待共同确认，新展出或运输暂停办理；已发生的结束与现状检查仍应如实记录。</NAlert
              >
              <NText
                v-for="segment in p.effectiveSegments || []"
                :key="`${segment.kind}:${segment.segmentIndex}`"
                style="display: block"
              >
                {{ segmentLabel(segment) }}：{{
                  segment.cancelled ? '已核实未实施并取消' : `${time(segment.from)} 至 ${time(segment.to)}`
                }}
              </NText>
              <NCard
                v-for="a in [...(p.adjustments || [])].reverse()"
                :key="a.id"
                :title="
                  a.withdrawal
                    ? '已撤回的变更原件'
                    : a.effective
                      ? '已共同确认的变更原件'
                      : a.latest
                        ? '待共同确认的变更核查'
                        : '已由新版本承接的核查原件'
                "
              >
                <CollectionEvidenceReadView
                  :fields="fragilityFields.adjustment"
                  :data="{
                    ...a.content,
                    adjustments: a.content.adjustments.map((v: any) => ({ ...v, segmentIndex: v.segmentIndex + 1 })),
                  }"
                  :archive-files="metadataFiles(a.id, 'ADJUSTMENT')" :original-url="originalUrl(a.id, 'ADJUSTMENT')"
                />
                <NCollapse>
                  <NCollapseItem title="核查时的实际现状与使用事实" name="facts">
                    <CollectionEvidenceReadView
                      :fields="careFields.check"
                      :data="a.facts?.checks?.find((c: any) => c.id === a.content.checkId)?.content || {}"
                      :archive-files="metadataFiles(a.content.checkId, 'CHECKED')" :original-url="(fileId: string) => careApi.originalUrl(data.collectionId, data.orgId, a.content.checkId, 'CHECKED', fileId)"
                    />
                    <NCard
                      v-for="r in a.facts?.records || []"
                      :key="r.id"
                      :title="r.history ? '既有事实原件' : '已发生的实际使用'"
                    >
                      <CollectionEvidenceReadView
                        v-if="r.history"
                        :fields="fragilityFields.history"
                        :data="r.history"
                        :archive-files="metadataFiles(r.id, 'HISTORY')" :original-url="originalUrl(r.id, 'HISTORY')"
                      />
                      <CollectionEvidenceReadView
                        v-if="r.started"
                        :fields="fragilityFields.start"
                        :data="{ ...r.started, segmentIndex: r.started.segmentIndex + 1 }"
                        :archive-files="[...metadataFiles(r.id, 'STARTED'), ...metadataFiles(r.id, 'ENVIRONMENT_VERIFIED')]" :original-url="(fileId: string, fieldKey: string) => originalUrl(r.id, fieldKey === 'environmentReadings.signedFileIds' ? 'ENVIRONMENT_VERIFIED' : 'STARTED')(fileId)"
                      />
                      <CollectionEvidenceReadView v-if="r.ended" :fields="fragilityFields.end" :data="r.ended" :archive-files="metadataFiles(r.id, 'ENDED')" :original-url="originalUrl(r.id, 'ENDED')" />
                      <CollectionEvidenceReadView v-if="r.review" :fields="fragilityFields.review" :data="r.review" :archive-files="metadataFiles(r.id, 'REVIEWED')" :original-url="originalUrl(r.id, 'REVIEWED')" />
                    </NCard>
                    <NCard v-for="r in a.facts?.loanMonitoring || []" :key="r.id" title="核查时的借用监管原件">
                      <CollectionEvidenceReadView
                        :fields="loanMonitoringFields(a.facts.loanMonitoring)"
                        :data="r.content"
                        :archive-files="metadataFiles(r.id, `MONITORING_${r.id}`)" :original-url="(fileId: string) => api.loanMonitoringOriginalUrl(data.collectionId, data.orgId, a.id, r.id, fileId)"
                      />
                    </NCard>
                  </NCollapseItem>
                </NCollapse>
                <NSpace v-for="sign in a.signatures || []" :key="sign.role" vertical>
                  <NText
                    >{{ roleLabels[sign.role] }}：{{ sign.actorUserId }} ·
                    {{ sign.signature ? time(sign.signature.confirmedAt) : '待本人确认' }}</NText
                  >
                  <CollectionEvidenceReadView
                    v-if="sign.signature"
                    :fields="fragilityFields.adjustmentSign"
                    :data="sign.signature.record"
                    :archive-files="metadataFiles(a.id, `ADJUSTMENT_${sign.role}`)" :original-url="originalUrl(a.id, `ADJUSTMENT_${sign.role}`)"
                  />
                </NSpace>
                <NSpace v-if="canOperate && a.latest && !a.effective && !a.withdrawal">
                  <NButton
                    v-for="sign in (a.signatures || []).filter((v: any) => !v.signature)"
                    :key="sign.role"
                    @click="edit('adjustmentSign', a.id, sign.role)"
                    >{{ roleLabels[sign.role] }}本人确认变更</NButton
                  >
                  <NButton @click="edit('adjustmentWithdraw', a.id)">登记人撤回本次变更</NButton>
                </NSpace>
                <CollectionEvidenceReadView
                  v-if="a.withdrawal"
                  :fields="fragilityFields.adjustmentWithdraw"
                  :data="a.withdrawal"
                  :archive-files="metadataFiles(a.id, 'ADJUSTMENT_WITHDRAWN')" :original-url="originalUrl(a.id, 'ADJUSTMENT_WITHDRAWN')"
                />
              </NCard> </NCard
          ></NTabPane>
          <NTabPane name="assessments" tab="材质评估与限值"
            ><NCard v-for="a in [...(data.assessments || [])].reverse()" :key="a.id" :title="`第 ${a.version} 版评估`">
              <CollectionEvidenceReadView
                :fields="fragilityFields.assessment"
                :data="{ ...a.content, ...a.content.limits }"
                :archive-files="metadataFiles(a.id, 'ASSESSMENT')" :original-url="originalUrl(a.id, 'ASSESSMENT')"
              /><NText
                >保护修复确认：{{ a.conservation?.actorUserId || '待本人确认' }}；保管确认：{{
                  a.keeper?.actorUserId || '待本人确认'
                }}</NText
              ><NButton v-if="canOperate && a.id === latest?.id && !a.keeper" @click="edit('sign', a.id)">本人确认材质评估</NButton
              ><CollectionEvidenceReadView
                v-if="a.conservation"
                :fields="fragilityFields.sign"
                :data="a.conservation.record"
                :archive-files="metadataFiles(a.id, 'CONSERVATION')" :original-url="originalUrl(a.id, 'CONSERVATION')"
              /><CollectionEvidenceReadView
                v-if="a.keeper"
                :fields="fragilityFields.sign"
                :data="a.keeper.record"
                :archive-files="metadataFiles(a.id, 'KEEPER')" :original-url="originalUrl(a.id, 'KEEPER')"
              /> </NCard
          ></NTabPane>
        </NTabs> </NSpace></NSpin
  ></HPageForm>
  <CollectionEvidenceForm v-if="show && !readError" :key="`CollectionEvidenceForm-${formEpoch}`"
    ref="formRef"
    :title="fragilityTitles[kind]"
    :fields="fields"
    :save="save"
    @saved="formSaved"
    @change="changeForm"
  >
    <template #context
      ><NText>本次藏品：{{ formTargetName }}</NText><NAlert v-if="['assessment', 'plan', 'start', 'monitor'].includes(kind)" type="info" :show-icon="false">
        限值、单位、有效期由本件专业评估确定。联网监测只用于方案指定的固定展出位置；运输须核对包装或运输环境的现场签署测量。
        核验仅覆盖本次开始前采样，展出和运输期间仍须持续监测、控制环境并处置异常。
      </NAlert></template
    >
  </CollectionEvidenceForm>
  <CollectionCareDrawer v-if="show && !readError" :key="`CollectionCareDrawer-${formEpoch}`" ref="careRef" @saved="careSaved" />
  <IncidentDrawer v-if="show && !readError" :key="`IncidentDrawer-${formEpoch}`" ref="incidentRef" @saved="incidentSaved" />
</template>
<script setup lang="ts">
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
const metadataFiles = (recordId: string, phase: string) => data.value.archiveFiles?.[recordId]?.[phase] || [];
import { computed, defineAsyncComponent, nextTick, onActivated, onDeactivated, onBeforeUnmount, ref, watch } from 'vue';
import { formatDateTime } from '@/utils';
import { cloneDeep } from 'lodash-es';
import { fragilityApi as api } from '../api/fragility';
import { careApi } from '../api/care';
import { careFields } from '../utils/careFields';
import { loanMonitoringFields } from '../utils/loanEvidenceFields';
import {
  fragilityFields,
  fragilityTitles,
  fragilityValues,
  limitKeys,
  fragileEnvironmentVerificationFields,
  fragileMonitoringEvidenceFields,
} from '../utils/fragilityFields';
import type { EvidenceField } from '../utils/evidenceFields';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import IncidentDrawer from './IncidentDrawer.vue';
import FragileMonitoringSamples from './FragileMonitoringSamples.vue';
import FragileAutomaticMonitoring from './FragileAutomaticMonitoring.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
const emit = defineEmits<{ (e: 'saved'): void }>();
const CollectionCareDrawer = defineAsyncComponent(() => import('./CollectionCareDrawer.vue'));
const show = ref(false),
  loading = ref(false),
  checkingSource = ref(false),
  data = ref<any>({}),
  sourceData = ref<any>(),
  kind = ref(''),
  fields = ref<EvidenceField[]>([]);
const readError = ref(''), formEpoch = ref(0), pageActive = ref(true), formTargetName = ref('');
const canOperate = computed(() => pageActive.value && show.value && !loading.value && !readError.value && data.value.canOperate === true);
const invalidForm = async () => { throw new Error('本次表单已失效，请重新读取当前资料后办理'); };
const save = ref<(value: Record<string, any>) => Promise<any>>(invalidForm);
const changeForm = ref<(value: Record<string, any>) => void>(() => {});
const formSaved = ref<() => void>(() => {});
const careSaved = ref<() => void>(() => {}), incidentSaved = ref<() => void>(() => {});
const sourceType = ref('COLLECTION_REQUISITION'),
  sourceId = ref(''),
  formRef = ref<any>(),
  careRef = ref<any>(),
  incidentRef = ref<any>();
const sourceOptions = [
  { label: '馆内提用', value: 'COLLECTION_REQUISITION' },
  { label: '馆外借用', value: 'COLLECTION_LOAN' },
];
const latest = computed(() => data.value.assessments?.at(-1));
const originalUrl = (recordId: string, phase: string) => (fileId: string) =>
  api.originalUrl(data.value.collectionId, data.value.orgId, recordId, phase, fileId);
const active = computed(() => [...(data.value.assessments || [])].reverse().find((a: any) => a.keeper));
const time = (v: any) => (v ? formatDateTime(v) : '尚未形成依据');
const automaticError = ref('');
let automaticRequest = 0,
  automaticLoading = false;
async function refreshAutomatic() {
  if (automaticLoading || !show.value || !pageActive.value || loading.value || readError.value) return;
  const token = ++automaticRequest,
    collectionId = id;
  automaticLoading = true;
  try {
    const r: any = await api.automaticStatus(collectionId);
    if (token !== automaticRequest || id !== collectionId || !show.value) return;
    if (!Array.isArray(r.data) || r.data.some((row: any) => !row || typeof row.useRecordId !== 'string' || !row.status || typeof row.status !== 'object')) throw new Error('自动监测状态响应不完整');
    for (const use of data.value.records || []) {
      const saved = r.data.find((v: any) => v.useRecordId === use.id);
      if (saved) use.automaticMonitoring = saved.status;
    }
    automaticError.value = '';
  } catch {
    if (token === automaticRequest) failRead('自动监测状态刷新失败，请重新读取当前资料。');
  } finally {
    if (token === automaticRequest) automaticLoading = false;
  }
}
const monitorClearRecord = (useId: string) =>
  (data.value.monitoringReports || []).filter((m: any) => m.useRecordId === useId).at(-1)?.id || '';
const nowTime = ref(Date.now());
let clockTimer: ReturnType<typeof setInterval> | undefined;
watch(show, (value) => {
  ++automaticRequest;
  automaticLoading = false;
  automaticError.value = '';
  if (clockTimer) clearInterval(clockTimer);
  clockTimer = undefined;
  if (!value) clearPage();
  if (value) {
    nowTime.value = Date.now();
    clockTimer = setInterval(() => {
      nowTime.value = Date.now();
      void refreshAutomatic();
    }, 30000);
  }
}, { flush: 'sync' });
function clearForm() {
  formEpoch.value++; editRequest++; kind.value = ''; fields.value = []; formTargetName.value = '';
  save.value = invalidForm; changeForm.value = () => {}; formSaved.value = () => {};
  careSaved.value = () => {}; incidentSaved.value = () => {}; editing = cloneDeep(emptyEditing); environmentSegment = "";
}
function clearPage() {
  request++; sourceRequest++; automaticRequest++; loading.value = false; checkingSource.value = false;
  data.value = {}; sourceData.value = undefined; verified = { type: '', biz: '', revision: '' }; readError.value = '';
  clearForm();
}
function failRead(error: any) {
  clearPage(); readError.value = typeof error === 'string' ? error : error?.message || '读取使用保护资料失败，请重新读取';
}
function leavePage() { pageActive.value = false; show.value = false; clearPage(); if (clockTimer) clearInterval(clockTimer); }
onDeactivated(leavePage); onBeforeUnmount(leavePage); onActivated(() => { pageActive.value = true; });
const roleLabels: Record<string, string> = {
  CONSERVATION: '保护修复责任人员',
  DEPARTMENT: '提用部门负责人',
  KEEPER: '保管部门负责人',
  DIRECTOR: '主要负责人',
};
const segmentLabel = (s: any) => (s.kind === 'DISPLAY' ? '展出' : `第 ${s.segmentIndex + 1} 段运输：${s.route || ''}`);
function availableSegments(plan: any) {
  const done = (kind: string, index: number) =>
    (data.value.records || []).some(
      (r: any) => r.planId === plan.id && r.started?.kind === kind && r.started?.segmentIndex === index,
    );
  const segments = plan.effectiveSegments || [
    ...(plan.content.displayFrom != null
      ? [{ kind: 'DISPLAY', segmentIndex: 0, from: plan.content.displayFrom, to: plan.content.displayTo }]
      : []),
    ...(plan.content.transports || []).map((t: any, index: number) => ({
      kind: 'TRANSPORT',
      segmentIndex: index,
      from: t.departsAt,
      to: t.arrivesAt,
      route: t.route,
    })),
  ];
  return segments
    .filter((s: any) => !s.cancelled && !done(s.kind, s.segmentIndex))
    .map((s: any) => ({
      value: `${s.kind}:${s.segmentIndex}`,
      label:
        s.kind === 'DISPLAY'
          ? `展出：${time(s.from)} 至 ${time(s.to)}`
          : `${segmentLabel(s)}（${time(s.from)} 至 ${time(s.to)}）`,
    }));
}
let editRequest = 0;
let id = '',
  request = 0,
  sourceRequest = 0,
  verified = { type: '', biz: '', revision: '' };
let editing = {
  id: '', orgId: '',
  record: '',
  assessment: '',
  previous: '',
  revision: '',
  kind: '',
  type: '',
  biz: '',
  sourceRevision: '',
  segments: [] as { value: string; label: string }[],
  role: '',
  adjustmentPrevious: '',
  factsRevision: '',
  criteria: [] as any[],
  environmentSources: [] as any[],
  deviceOptions: [] as any[],
  plannedSegments: [] as { value: string; label: string }[],
};
const emptyEditing = cloneDeep(editing);
let environmentSegment = '';
watch([sourceType, sourceId], () => {
  checkingSource.value = false;
  sourceData.value = undefined; verified = { type: '', biz: '', revision: '' };
  ++sourceRequest;
});
async function open(value: string, type = 'COLLECTION_REQUISITION', biz = '') {
  if (!pageActive.value) return;
  if (!show.value || id === value) clearForm();
  ++automaticRequest;
  automaticLoading = false;
  automaticError.value = '';
  id = value;
  ++sourceRequest;
  ++editRequest;
  checkingSource.value = false;
  data.value = {};
  sourceData.value = undefined;
  sourceType.value = type;
  sourceId.value = biz;
  show.value = true;
  await reload(true);
}
async function reload(preserveForm = false) {
  if (!show.value || !pageActive.value || !id) return;
  const token = ++request, target = id;
  sourceRequest++; editRequest++; automaticRequest++; automaticLoading = false;
  data.value = {}; sourceData.value = undefined; verified = { type: '', biz: '', revision: '' };
  readError.value = ''; automaticError.value = ''; loading.value = true;
  if (!preserveForm) clearForm();
  try {
    const r: any = await api.detail(target), d = r?.data;
    if (token !== request || !show.value || !pageActive.value) return;
    if (!d || d.collectionId !== target || typeof d.orgId !== 'string' || !d.orgId.trim() || typeof d.canOperate !== 'boolean' ||
        typeof d.sourceRevision !== 'string' || !d.sourceRevision || !['assessments','plans','records','monitoringReports'].every(key => Array.isArray(d[key]))) throw new Error('使用保护资料或当前办理资格不完整，请重新读取');
    data.value = d;
  } catch (error) { if (token === request) failRead(error); }
  finally { if (token === request) loading.value = false; }
}
async function loadSource() {
  if (!canOperate.value || checkingSource.value) return;
  const token = ++sourceRequest,
    target = id,
    type = sourceType.value,
    biz = sourceId.value;
  checkingSource.value = true;
  sourceData.value = undefined;
  try {
    const r: any = await api.source(target, type, biz);
    if (token === sourceRequest && target === id && canOperate.value) {
      if (!r?.data || typeof r.data.sourceRevision !== 'string' || !r.data.sourceRevision || typeof r.data.editable !== 'boolean') throw new Error('使用来源响应不完整');
      sourceData.value = r.data;
      verified = { type, biz, revision: r.data.sourceRevision };
    }
  } catch (error) { if (token === sourceRequest) failRead(error); } finally {
    if (token === sourceRequest) checkingSource.value = false;
  }
}
async function edit(action: string, record = '', role = '') {
  if (!canOperate.value) return;
  clearForm(); const token = ++editRequest;
  try {
  const monitoringRecord = action === 'monitor' ? data.value.records?.find((r: any) => r.id === record) : undefined;
  const plan = monitoringRecord
    ? data.value.plans?.find((p: any) => p.id === monitoringRecord.planId)
    : ['start', 'adjustment'].includes(action)
      ? data.value.plans.find((p: any) => p.id === record)
      : undefined;
  editing = {
    id, orgId: data.value.orgId,
    record,
    assessment: active.value?.id,
    previous: latest.value?.id,
    revision: data.value.sourceRevision,
    kind: action,
    type: verified.type,
    biz: verified.biz,
    sourceRevision: verified.revision,
    segments: plan ? availableSegments(plan) : [],
    role,
    adjustmentPrevious: plan?.adjustments?.at(-1)?.id,
    factsRevision: plan?.adjustmentFactsRevision,
    criteria:
      (plan ? data.value.assessments?.find((a: any) => a.id === plan.content.assessmentId) : active.value)?.content
        .environmentCriteria || [],
    environmentSources: plan?.content.environmentSources || [],
    deviceOptions: [],
    plannedSegments: [],
  };
  const target = { ...editing };
  environmentSegment = '';
  kind.value = action;
  fields.value = action === 'monitorClear' ? careFields.clear : fragilityFields[action];
  if (action === 'monitor') {
    const sources = target.environmentSources.filter(
      (r) =>
        r.type === 'SIGNED_ON_SITE' &&
        r.kind === monitoringRecord?.started?.kind &&
        r.segmentIndex === monitoringRecord?.started?.segmentIndex,
    );
    const options = (key: string) =>
      [...new Set(sources.map((r) => String(r[key])))].map((v) => ({ value: v, label: v }));
    fields.value = fragilityFields.monitor.map((f) =>
      f.key === 'readings'
        ? {
            ...f,
            children: f.children!.map((c) =>
              ['metricCode', 'instrumentAndMethod', 'context'].includes(c.key) ? { ...c, options: options(c.key) } : c,
            ),
          }
        : f,
    );
  }
  if (action === 'plan') {
    const response: any = await api.environmentSources(target.id);
    if (token !== editRequest || target.id !== id || !canOperate.value) return;
    if (!Array.isArray(response?.data)) throw new Error("联网监测来源响应不完整");
    editing.deviceOptions = response.data;
    fields.value = fragilityFields.plan.map((f) =>
      f.key === 'environmentSources'
        ? {
            ...f,
            children: [
              { key: 'segment', label: '本次计划的使用段', type: 'select', required: true, options: [] },
              ...f
                .children!.filter((c) => !['kind', 'segmentIndex', 'deviceId', 'locationId'].includes(c.key))
                .map((c) =>
                  c.key === 'metricCode'
                    ? {
                        ...c,
                        options: target.criteria.map((r) => ({
                          value: r.metricCode,
                          label: `${r.metricName} · ${r.unit} · ${r.kind === 'DISPLAY' ? '展出' : '运输'}`,
                        })),
                      }
                    : c,
                ),
              {
                key: 'registeredSource',
                label: '本馆联网监测来源（仅固定展出时选择）',
                type: 'select',
                required: false,
                options: editing.deviceOptions.map((r: any) => ({
                  value: JSON.stringify([r.deviceId, r.locationId, r.metricCode]),
                  label: `${r.locationName} · ${r.deviceName} · ${r.metricName} (${r.unit})`,
                })),
              },
            ],
          }
        : f,
    );
  }
  if (action === 'start') {
    fields.value = [
      { key: 'segment', label: '本次实际开始的已确认安排', type: 'select', required: true, options: target.segments },
      ...fragilityFields.start.filter((f) => !['kind', 'segmentIndex'].includes(f.key)),
    ];
  }
  if (action === 'adjustment') {
    const response: any = await careApi.detail(target.id);
    if (token !== editRequest || target.id !== id || !canOperate.value) return;
    if (!Array.isArray(response?.data?.checks)) throw new Error("实际现状检查响应不完整");
    const checks = [...response.data.checks].sort(
      (a: any, b: any) => Number(a.content.checkedAt) - Number(b.content.checkedAt),
    );
    fields.value = fragilityFields.adjustment.map((f) =>
      f.key === 'checkId'
        ? {
            ...f,
            options: checks
              .slice(-1)
              .map((r: any) => ({ value: r.id, label: `${time(r.content.checkedAt)} · ${r.content.condition}` })),
          }
        : f.key === 'adjustments'
          ? {
              ...f,
              children: [
                {
                  key: 'segment',
                  label: '已确认但从未开始的原安排',
                  type: 'select',
                  required: true,
                  options: target.segments,
                },
                ...f.children!.filter((c) => !['kind', 'segmentIndex'].includes(c.key)),
              ],
            }
          : f,
    );
  }
  if (action === 'review') {
    const endedAt = data.value.records?.find((r: any) => r.id === record)?.ended?.endedAt;
    const response: any = await careApi.detail(target.id);
    if (token !== editRequest || target.id !== id || !canOperate.value) return;
    if (!Array.isArray(response?.data?.checks)) throw new Error("实际现状检查响应不完整");
    fields.value = fragilityFields.review.map((f) =>
      f.key === 'checkId'
        ? {
            ...f,
            type: 'select',
            options: (response.data.checks || [])
              .filter((r: any) => Number(r.content.checkedAt) >= Number(endedAt))
              .map((r: any) => ({ value: r.id, label: `${time(r.content.checkedAt)} · ${r.content.condition}` })),
          }
        : f,
    );
  }
  const owned = cloneDeep(editing), epoch = formEpoch.value;
  formTargetName.value = `${data.value.name || owned.id}（${owned.id}） · 收藏单位：${data.value.orgName || owned.orgId}（${owned.orgId}）`;
  const current = () => epoch === formEpoch.value && pageActive.value && show.value && !readError.value;
  let submitting = false;
  save.value = async value => {
    if (!current() || submitting) return invalidForm();
    submitting = true;
    try { return await saveFor(owned, cloneDeep(value)); } finally { submitting = false; }
  };
  changeForm.value = value => { if (current()) formChanged(owned, value); };
  formSaved.value = () => { if (!current()) return; emit('saved'); if (id === owned.id) void reload(); else clearForm(); };
  await nextTick();
  if (token === editRequest && canOperate.value) formRef.value?.open({});
  } catch (error) { if (token === editRequest) failRead(error); }
}
function formChanged(owned: typeof editing, value: Record<string, any>) {
  if (owned.kind === 'plan') {
    owned.plannedSegments = [
      ...(value.displayFrom != null ? [{ value: 'DISPLAY:0', label: '本次展出' }] : []),
      ...(value.transports || []).map((r: any, i: number) => ({
        value: `TRANSPORT:${i}`,
        label: `第${i + 1}段运输 · ${r.route || '待填路线'}`,
      })),
    ];
    fields.value = fields.value.map((f) =>
      f.key === 'environmentSources'
        ? {
            ...f,
            children: f.children!.map((c) => (c.key === 'segment' ? { ...c, options: owned.plannedSegments } : c)),
          }
        : f,
    );
    return;
  }
  if (owned.kind !== 'start' || value.segment === environmentSegment) return;
  environmentSegment = value.segment || '';
  const [segmentKind, index] = environmentSegment.split(':');
  const sources = owned.environmentSources.filter((r) => r.kind === segmentKind && r.segmentIndex === Number(index));
  const manual = sources.filter((r) => r.type === 'SIGNED_ON_SITE');
  const choices = (values: string[]) => [...new Set(values)].map((v) => ({ value: v, label: v }));
  fields.value = fields.value.map((f) =>
    f.key === 'actualLocationOrRoute'
      ? { ...f, type: 'select', options: choices(sources.map((r) => r.context)) }
      : f.key === 'environmentReadings'
        ? {
            ...f,
            required: manual.length > 0,
            children: f.children!.map((c) =>
              c.key === 'metricCode'
                ? { ...c, type: 'select', options: choices(manual.map((r) => r.metricCode)) }
                : c.key === 'instrumentAndMethod' || c.key === 'context'
                  ? { ...c, type: 'select', options: choices(manual.map((r) => r[c.key])) }
                  : c,
            ),
          }
        : f,
  );
  formRef.value?.open({
    ...value,
    actualLocationOrRoute: undefined,
    environmentReadings: manual.map((r) => ({
      metricCode: r.metricCode,
      unit: owned.criteria.find((c) => c.kind === segmentKind && c.metricCode === r.metricCode)?.unit,
    })),
  });
}
async function saveFor(t: typeof editing, value: Record<string, any>) {
  if (!t.orgId) throw new Error("缺少本次确认的收藏单位，请重新读取资料后办理");
  const record = (id: string, path: string, data: Record<string, any>) => api.record(id, path, data, t.orgId);
  const v = fragilityValues(value);
  if (t.kind === 'assessment') {
    const limits = v.fragileMaterial ? Object.fromEntries(limitKeys.map((k) => [k, v[k]])) : null;
    limitKeys.forEach((k) => delete v[k]);
    return record(t.id, 'assessment', {
      ...v,
      sourceRevision: t.revision,
      previousAssessmentId: t.previous || null,
      limits,
    });
  }
  if (t.kind === 'plan') {
    if (v.environmentSources)
      v.environmentSources = v.environmentSources.map((row: any) => {
        const { registeredSource, segment, ...rest } = row;
        if (!t.plannedSegments.some((s) => s.value === segment))
          throw new Error('请先填写实际展出或运输安排，并为来源选择对应使用段');
        const [kind, index] = segment.split(':');
        rest.kind = kind;
        rest.segmentIndex = Number(index);
        if (rest.type !== 'REGISTERED_DEVICE') return rest;
        const source = t.deviceOptions.find(
          (r) => JSON.stringify([r.deviceId, r.locationId, r.metricCode]) === registeredSource,
        );
        if (!source || source.metricCode !== rest.metricCode) throw new Error('请选择本馆对应指标的有效联网监测来源');
        return { ...rest, deviceId: source.deviceId, locationId: source.locationId };
      });
    return record(t.id, 'plan', {
      ...v,
      assessmentId: t.assessment,
      sourceBizType: t.type,
      sourceBizId: t.biz,
      sourceRevision: t.sourceRevision,
      transports: v.transports || [],
    });
  }
  if (t.kind === 'adjustment') {
    const adjustments = (v.adjustments || []).map((item: any) => {
      if (!t.segments.some((s) => s.value === item.segment)) throw new Error('请选择本次核查时尚未开始的原安排');
      const [kind, index] = item.segment.split(':');
      const { segment, ...values } = item;
      if (values.action === 'CANCEL') ['from', 'to', 'route', 'protection'].forEach((key) => delete values[key]);
      return { ...values, kind, segmentIndex: Number(index) };
    });
    return record(t.id, `plan/${t.record}/adjustment`, {
      ...v,
      adjustments,
      previousAdjustmentId: t.adjustmentPrevious || null,
      factsRevision: t.factsRevision,
    });
  }
  if (t.kind === 'adjustmentSign') return record(t.id, `adjustment/${t.record}/sign/${t.role}`, v);
  if (t.kind === 'adjustmentWithdraw') return record(t.id, `adjustment/${t.record}/withdraw`, v);
  if (t.kind === 'monitor') return record(t.id, `record/${t.record}/monitor`, v);
  if (t.kind === 'monitorClear') return record(t.id, `monitor/${t.record}/clear`, v);
  if (t.kind === 'sign') return record(t.id, `${t.record}/sign`, v);
  if (t.kind === 'start') {
    if (!t.segments.some((s) => s.value === v.segment)) throw new Error('请选择本次已确认且尚未开始的安排');
    const [kind, index] = v.segment.split(':');
    delete v.segment;
    return record(t.id, `plan/${t.record}/start`, { ...v, kind, segmentIndex: Number(index) });
  }
  if (t.kind === 'end' || t.kind === 'review') return record(t.id, `record/${t.record}/${t.kind}`, v);
  return record(t.id, 'history', v);
}
function automaticActions(useId: string) {
  const target = id, epoch = request;
  const current = () => target === id && epoch === request && canOperate.value;
  return { incident: () => { if (current()) monitorIncident(useId); }, clear: () => { if (current()) void edit('monitorClear', monitorClearRecord(useId)); }, openIncident: (incidentId: string) => { if (target === id && epoch === request) openIncident(incidentId); } };
}
function childReceipt() {
  const target = id, generation = request, epoch = formEpoch.value;
  return () => { if (pageActive.value && show.value && !readError.value && target === id && generation === request && epoch === formEpoch.value) { emit('saved'); void reload(); } };
}
function openCare() { if (canOperate.value) { careSaved.value = childReceipt(); careRef.value?.open(id); } }
function openIncident(incidentId: string) { if (show.value && pageActive.value && !readError.value) { incidentSaved.value = childReceipt(); incidentRef.value?.open(incidentId); } }
function monitorIncident(useRecordId: string) {
  if (!canOperate.value) return;
  const reports = (data.value.monitoringReports || []).filter(
    (r: any) => r.useRecordId === useRecordId && r.evidence.needsHandling,
  );
  incidentSaved.value = childReceipt();
  incidentRef.value?.create({
    source: { bizType: 'COLLECTION_FRAGILITY', bizId: id, recordId: useRecordId },
    items: [{ collectionId: id, damaged: reports.some((r: any) => r.request.damaged) }],
  });
}
defineExpose({ open });
</script>
