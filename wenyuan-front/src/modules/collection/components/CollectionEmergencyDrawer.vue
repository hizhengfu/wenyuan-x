<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="藏品安全应急预案与演练">
    <NSpin :show="loading"
      ><NSpace vertical :size="16">
      <CollectionArchiveIssues :data="data" />
        <NAlert type="info"
          >依据实际印发原件管理预案版本，按各情景风险确定演练周期。模拟报警、报告不是真实报案；演练记录不授权提取原件，也不解除事故或实物限制。</NAlert
        >
        <NAlert v-if="error" type="error">{{ error }}</NAlert
        ><NText>{{ data.name }}</NText>
        <NSpace size="small" align="center" v-if="!loading && !error">
          <CollectionActionMenu label="预案与演练">
            <NButton quaternary @click="edit('plan')">{{ latest ? '登记实际修订预案' : '登记实际印发预案' }}</NButton>
            <NButton quaternary v-if="data.plans?.length" @click="edit('drill')">登记实际演练</NButton>
          </CollectionActionMenu>
          <NButton v-permission="'COLLECTION_ADMIN,COLLECTION_INCIDENT_MANAGE'" @click="incidentRef?.create()"
            >发生真实事故：另行首报</NButton
          >
          <NButton @click="reload">刷新办理状态</NButton>
        </NSpace>
        <NCard v-for="row in data.due || []" :key="row.rowKey" :title="scenarios[row.scenario]">
          <NText>{{ statuses[row.status] }} · 下次演练：{{ time(row.nextDueAt) }}</NText>
          <NText tag="p"
            >全馆待评估 {{ row.pendingEvaluationCount }} 次 · 演练问题待整改 {{ row.pendingIssueCount }} 项 ·
            关联真实事故办理中 {{ row.openIncidentCount }} 件 · 待交接或承接 {{ row.pendingDutyCount || 0 }} 项</NText
          >
        </NCard>
        <NTabs type="line">
          <NTabPane name="drills" tab="真实演练、评估与整改">
            <NEmpty v-if="!data.drills?.length" description="尚无实际演练记录" />
            <NCard v-for="drill in [...(data.drills || [])].reverse()" :key="drill.id" :title="drill.content.title">
              <NAlert :type="drill.counted ? 'info' : 'warning'">{{
                drill.counted
                  ? '实际演练已核对，按真实结束时间计算相关情景周期'
                  : '预案原件或实际演练评估待本人核对，尚未更新周期'
              }}</NAlert>
              <NAlert v-if="drill.basisMatches === false" type="warning"
                >后续原件表明本轮所用预案与当时生效版本不一致，须核实实际依据并按有效预案重新落实演练。原实际记录和签署继续保留，重复签署不会更新期限。</NAlert
              >
              <NText>当前为第 {{ drill.reportVersion || 1 }} 版报告；旧报告与旧签署按版本保留。</NText>
              <CollectionEvidenceReadView :fields="readFields('drill')" :data="drill.content" :archive-files="metadataFiles(drill.reportId, reportPhase(drill))" :original-url="originalUrl(drill.reportId, reportPhase(drill))" />
              <NButton @click="edit('revision', drill.id)">订正本次报告或追加新发现</NButton>
              <NCollapse v-if="drill.reports?.length">
                <NCollapseItem title="本次演练报告与签署历史" :name="drill.id">
                  <NCard
                    v-for="report in [...drill.reports].reverse()"
                    :key="report.id"
                    :title="`第 ${report.version} 版报告`"
                  >
                    <CollectionEvidenceReadView
                      v-if="report.revision"
                      :fields="revisionHistoryFields"
                      :data="revisionHistory(report.revision)"
                      :archive-files="metadataFiles(report.id, 'DRILL_REVISION')" :original-url="originalUrl(report.id, 'DRILL_REVISION')"
                    />
                    <CollectionEvidenceReadView :fields="readFields('drill')" :data="report.content" :archive-files="metadataFiles(report.id, reportPhase(report))" :original-url="originalUrl(report.id, reportPhase(report))" />
                    <NText>本版评估核对</NText>
                    <CollectionEvidenceReadView
                      v-if="report.evaluator"
                      :fields="signatureHistoryFields"
                      :data="signatureHistory(report.evaluator)"
                      :archive-files="metadataFiles(report.id, 'EVALUATOR')" :original-url="originalUrl(report.id, 'EVALUATOR')"
                    />
                    <NText v-else>待本人核对</NText>
                    <NText>本版主要负责人核对</NText>
                    <CollectionEvidenceReadView
                      v-if="report.director"
                      :fields="signatureHistoryFields"
                      :data="signatureHistory(report.director)"
                      :archive-files="metadataFiles(report.id, 'DIRECTOR')" :original-url="originalUrl(report.id, 'DIRECTOR')"
                    />
                    <NText v-else>待本人核对</NText>
                  </NCard>
                </NCollapseItem>
              </NCollapse>
              <NSpace
                ><NButton v-if="!drill.evaluator" @click="edit('signDrill', drill.id, 'EVALUATOR')">{{
                  duty('DRILL', drill.id, 'EVALUATOR')?.activeDutyId ? '接任人员核对演练原件' : '实际评估人员本人核对'
                }}</NButton
                ><NButton v-if="drill.evaluator && !drill.director" @click="edit('signDrill', drill.id, 'DIRECTOR')">{{
                  duty('DRILL', drill.id, 'DIRECTOR')?.activeDutyId
                    ? '接任主要负责人核对演练原件'
                    : '主要负责人本人核对演练'
                }}</NButton></NSpace
              >
              <CollectionEvidenceReadView
                v-if="drill.evaluator"
                :fields="fieldsByKind.sign"
                :data="drill.evaluator.record"
                :archive-files="metadataFiles(drill.reportId, 'EVALUATOR')" :original-url="originalUrl(drill.reportId, 'EVALUATOR')"
              /><CollectionEvidenceReadView
                v-if="drill.director"
                :fields="fieldsByKind.sign"
                :data="drill.director.record"
                :archive-files="metadataFiles(drill.reportId, 'DIRECTOR')" :original-url="originalUrl(drill.reportId, 'DIRECTOR')"
              />
              <NCard
                v-for="issue in drill.issues"
                :key="issue.number"
                :title="`问题 ${issue.number}：${issue.content.title}`"
              >
                <NAlert :type="issue.closed ? 'info' : issue.overdue ? 'error' : 'warning'"
                  >{{
                    issue.closed ? '本项整改已独立复核' : issue.overdue ? '本项整改已逾期' : '本项整改或复核待办理'
                  }}；真实事故仍独立跟踪。</NAlert
                >
                <CollectionEvidenceReadView
                  :fields="fieldsByKind.drill.find((f) => f.key === 'issues')?.children || []"
                  :data="issue.content"
                  :archive-files="metadataFiles(drill.reportId, reportPhase(drill))" :original-url="originalUrl(drill.reportId, reportPhase(drill))"
                />
                <NButton v-if="!issue.closed" @click="edit('improvement', drill.id, '', issue.number)">{{
                  duty('ISSUE', drill.id, 'OWNER', issue.number)?.activeDutyId
                    ? '接任责任人登记整改或核对结果'
                    : '本项责任人登记实际整改'
                }}</NButton>
                <NCard v-for="record in issue.records" :key="record.id" :title="`第 ${record.version} 次整改`"
                  ><NAlert v-if="record.basisMatches === false" type="warning"
                    >报告相关依据已变化，此前整改及复核保留，须追加本次实际核对结果。</NAlert
                  ><CollectionEvidenceReadView
                    :fields="readFields('improvement')"
                    :data="record.content"
                    :archive-files="metadataFiles(record.id, 'IMPROVEMENT')" :original-url="originalUrl(record.id, 'IMPROVEMENT')"
                  /><CollectionEvidenceReadView
                    v-if="record.review"
                    :fields="fieldsByKind.review"
                    :data="reviewValue(record.review.record)"
                    :archive-files="metadataFiles(record.id, 'REVIEW')" :original-url="originalUrl(record.id, 'REVIEW')"
                  /><NButton
                    v-else-if="record.id === issue.records.at(-1)?.id && record.basisMatches !== false"
                    @click="edit('review', drill.id, '', issue.number, record.id)"
                    >{{
                      duty('IMPROVEMENT', record.id, 'REVIEWER')?.activeDutyId
                        ? '接任人员本人独立复核'
                        : '指定人员本人独立复核'
                    }}</NButton
                  ></NCard
                >
              </NCard>
              <NCard v-for="incident in drill.actualIncidents" :key="incident.id" :title="`真实事故：${incident.title}`"
                ><NText>{{
                  incident.closed ? '本版事故已共同签署归档，实物限制按原业务核验' : '实际事故仍在办理中'
                }}</NText
                ><NButton
                  v-permission="'COLLECTION_ADMIN,COLLECTION_INCIDENT_MANAGE'"
                  @click="incidentRef?.open(incident.id)"
                  >查阅实际事故报告与处置</NButton
                ></NCard
              >
            </NCard>
          </NTabPane>
          <NTabPane name="duties" tab="人员变动与责任承接">
            <CollectionEmergencyDuties :archive-files="data.archiveFiles"
              v-if="!loading && !error"
              :org-id="data.orgId"
              :targets="data.duties || []"
              @saved="reload"
            />
          </NTabPane>
          <NTabPane name="plans" tab="实际预案版本">
            <NCard
              v-for="plan in [...(data.plans || [])].reverse()"
              :key="plan.id"
              :title="`第 ${plan.version} 版 · ${plan.content.title}`"
            >
              <NText
                >{{ plan.id === data.activePlanId ? '当前已生效原件' : '历史或尚未生效原件' }} ·
                {{ plan.confirmed ? '本人核对已完成' : '待本人核对' }}</NText
              >
              <NAlert v-if="plan.missingReportScenarios?.length" type="warning"
                >尚缺以下情景的实际演练安排：{{
                  plan.missingReportScenarios.map((s: string) => scenarios[s]).join('、')
                }}</NAlert
              >
              <CollectionEvidenceReadView :fields="fieldsByKind.plan" :data="plan.content" :archive-files="metadataFiles(plan.id, 'ISSUED')" :original-url="originalUrl(plan.id, 'ISSUED')" />
              <div v-for="sign in plan.signatures" :key="sign.role">
                <NText
                  >{{ roles[sign.role] }} ·
                  {{ sign.signature ? time(sign.signature.confirmedAt) : '待本人核对' }}</NText
                ><CollectionEvidenceReadView
                  v-if="sign.signature"
                  :fields="fieldsByKind.sign"
                  :data="sign.signature.record"
                  :archive-files="metadataFiles(plan.id, sign.role)" :original-url="originalUrl(plan.id, sign.role)"
                /><NButton v-else @click="edit('signPlan', plan.id, sign.role)"
                  >{{ roles[sign.role]
                  }}{{ duty('PLAN', plan.id, sign.role)?.activeDutyId ? '接任后核对预案' : '本人核对预案' }}</NButton
                >
              </div>
            </NCard>
          </NTabPane>
        </NTabs>
      </NSpace></NSpin
    >
  </HPageForm>
  <CollectionEvidenceForm ref="formRef" :title="titles[kind]" :fields="fields" :save="save" @saved="saved">
    <template #context>
      <template v-if="kind === 'revision'">
        <NAlert type="info"
          >正在订正同一次演练。下列实际组织人员和原问题继续保留，新发现填写在追加栏。接任人员以本人身份续办，系统保留实际登记人和承接依据。本版报告须重新核对，旧签署不会移入本版。</NAlert
        >
        <CollectionEvidenceReadView :fields="retainedFields" :data="revisionContext" :archive-files="metadataFiles(editing.reportId, editing.reportId === editing.record ? 'DRILL' : 'DRILL_REVISION')" :original-url="originalUrl(editing.reportId, editing.reportId === editing.record ? 'DRILL' : 'DRILL_REVISION')" />
      </template>
    </template>
  </CollectionEvidenceForm>
  <IncidentDrawer ref="incidentRef" @saved="reload" />
</template>
<script setup lang="ts">
import CollectionActionMenu from './CollectionActionMenu.vue';
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
const metadataFiles = (recordId: string, phase: string) => data.value.archiveFiles?.[recordId]?.[phase] || [];
import { computed, defineAsyncComponent, nextTick, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { formatDateTime } from '@/utils';
import { emergencyApi as api } from '../api/emergency';
import {
  emergencyFields as fieldsByKind,
  emergencyScenarios as scenarios,
  emergencyStatuses as statuses,
  emergencyRoles as roles,
  emergencyTitles as titles,
  emergencyValues,
  emergencyReportInitial,
  emergencyRevisionFields,
} from '../utils/emergencyFields';
import type { EvidenceField } from '../utils/evidenceFields';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import CollectionEmergencyDuties from './CollectionEmergencyDuties.vue';
const IncidentDrawer = defineAsyncComponent(() => import('./IncidentDrawer.vue'));
const emit = defineEmits<{ (e: 'saved'): void }>();
const show = ref(false),
  loading = ref(false),
  error = ref(''),
  data = ref<any>({}),
  kind = ref(''),
  fields = ref<EvidenceField[]>([]),
  formRef = ref<any>(),
  incidentRef = ref<any>();
const revisionContext = ref<Record<string, any>>({});
const retainedFields = fieldsByKind.drill.filter((f) => ['conductorUserId', 'issues'].includes(f.key));
const signatureHistoryFields: EvidenceField[] = [
  { key: 'actorUserId', label: '实际核对人员', type: 'user' },
  { key: 'confirmedAt', label: '本版系统核对时间', type: 'datetime' },
  ...fieldsByKind.sign,
];
const revisionHistoryFields: EvidenceField[] = [
  { key: 'submittedBy', label: '订正登记人员', type: 'user' },
  { key: 'submittedAt', label: '系统登记时间', type: 'datetime' },
  { key: 'reason', label: '订正原因', type: 'textarea' },
  { key: 'fileIds', label: '订正依据原件', type: 'file' },
];
const signatureHistory = (v: any) => ({ ...v.record, actorUserId: v.actorUserId, confirmedAt: v.confirmedAt });
const revisionHistory = (v: any) => ({ ...v.request, submittedBy: v.submittedBy, submittedAt: v.submittedAt });
const duty = (type: string, targetId: string, role: string, issueNumber = 0) =>
  data.value.duties?.find(
    (d: any) =>
      d.target.type === type &&
      d.target.targetId === targetId &&
      d.target.role === role &&
      d.target.issueNumber === issueNumber,
  );
const latest = computed(() => data.value.plans?.at(-1));
const time = (v: any) => (v ? formatDateTime(v) : '尚无有效安排');
const reviewValue = (v: any) => ({ ...v, accepted: v.accepted ? 'YES' : 'NO' });
const reportPhase = (report: any) => (report.revision || (report.reportId && report.reportId !== report.id) ? 'DRILL_REVISION' : 'DRILL');
const originalUrl = (recordId: string, phase: string) => (fileId: string) =>
  api.originalUrl(data.value.orgId, recordId, phase, fileId);
function readFields(action: string) {
  return fieldsByKind[action].map((f) =>
    ['planId', 'revisedPlanId'].includes(f.key)
      ? {
          ...f,
          options: (data.value.plans || []).map((p: any) => ({
            value: p.id,
            label: `第 ${p.version} 版 ${p.content.title}`,
          })),
        }
      : f.key === 'requisitionIds'
        ? { ...f, options: data.value.requisitionOptions || [] }
        : f.key === 'actualIncidentIds'
          ? { ...f, options: data.value.incidentOptions || [] }
          : f,
  );
}
let id = '',
  request = 0,
  editing = {
    id: '',
    record: '',
    role: '',
    issueNumber: 0,
    improvementId: '',
    previousId: '',
    previousPlanId: '',
    reportId: '',
    reportContent: {} as Record<string, any>,
    kind: '',
  };
async function open(value: string) {
  id = value;
  data.value = {};
  error.value = '';
  show.value = true;
  await reload();
}
async function reload() {
  const token = ++request,
    target = id;
  loading.value = true;
  error.value = '';
  try {
    const response: any = await api.detail(target);
    if (token === request) data.value = response.data;
  } catch (e: any) {
    if (token === request) {
      data.value = {};
      error.value = e?.message || '应急原件读取失败，请核查后重试';
    }
  } finally {
    if (token === request) loading.value = false;
  }
}
async function edit(action: string, record = '', role = '', issueNumber = 0, improvementId = '') {
  const drill = data.value.drills?.find((d: any) => d.id === record);
  const issue = drill?.issues?.find((i: any) => i.number === issueNumber);
  editing = {
    id,
    record,
    role,
    issueNumber,
    improvementId,
    previousId: issue?.records?.at(-1)?.id || '',
    previousPlanId: latest.value?.id || '',
    reportId: drill?.reportId || record,
    reportContent: cloneDeep(drill?.content || {}),
    kind: action,
  };
  kind.value = action;
  fields.value =
    action === 'revision'
      ? [
          ...readFields('drill').filter((f) => !['conductorUserId', 'issues', 'hasIssues'].includes(f.key)),
          ...emergencyRevisionFields,
        ]
      : readFields(['signPlan', 'signDrill'].includes(action) ? 'sign' : action);
  revisionContext.value = cloneDeep(editing.reportContent);
  const p = latest.value?.content;
  const initial =
    action === 'revision'
      ? emergencyReportInitial(cloneDeep(editing.reportContent))
      : action === 'plan' && p
        ? { keeperUserId: p.keeperUserId, securityUserId: p.securityUserId, directorUserId: p.directorUserId }
        : action === 'improvement'
          ? { ownerUserId: duty('ISSUE', record, 'OWNER', issueNumber)?.currentUserId || issue?.content.ownerUserId }
          : {};
  await nextTick();
  formRef.value?.open(initial);
}
async function save(value: Record<string, any>) {
  const t = { ...editing },
    v = emergencyValues(value);
  if (t.kind === 'plan') return api.record(t.id, 'plan', { ...v, previousPlanId: t.previousPlanId || null });
  if (t.kind === 'signPlan') return api.record(t.id, `plan/${t.record}/${t.role}`, v);
  if (t.kind === 'drill')
    return api.record(t.id, 'drill', {
      ...v,
      issues: v.issues || [],
      requisitionIds: v.requisitionIds || [],
      actualIncidentIds: v.actualIncidentIds || [],
    });
  if (t.kind === 'revision') {
    const { additionalIssues, revisionReason, revisionFileIds, ...report } = v;
    const issues = [...(t.reportContent.issues || []), ...(additionalIssues || [])];
    return api.record(t.id, `drill/${t.record}/revision`, {
      expectedReportId: t.reportId,
      reason: revisionReason,
      fileIds: revisionFileIds,
      report: {
        ...report,
        conductorUserId: t.reportContent.conductorUserId,
        issues,
        hasIssues: issues.length > 0,
        requisitionIds: report.requisitionIds || [],
        actualIncidentIds: report.actualIncidentIds || [],
      },
    });
  }
  if (t.kind === 'signDrill') return api.record(t.id, `drill/${t.record}/revision/${t.reportId}/sign/${t.role}`, v);
  if (t.kind === 'improvement')
    return api.record(t.id, `drill/${t.record}/revision/${t.reportId}/improvement`, {
      ...v,
      issueNumber: t.issueNumber,
      previousId: t.previousId || null,
    });
  return api.record(t.id, `drill/${t.record}/revision/${t.reportId}/improvement/${t.improvementId}/review`, v);
}
async function saved() {
  emit('saved');
  await reload();
}
defineExpose({ open });
</script>
