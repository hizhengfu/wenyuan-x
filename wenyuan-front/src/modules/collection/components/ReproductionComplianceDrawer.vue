<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="复制拓印方案、实施与产物保管">
    <NSpin :show="loading"
      ><NSpace vertical :size="16">
      <CollectionArchiveIssues :data="data" />
        <NAlert type="info"
          >依照实际原件填写方案、批文、检查及签署记录。馆内批准后，使用原件的活动还须办理实际提用点交或在库工作登记。实物归库在来源归还单办理。</NAlert
        >
        <NAlert v-if="readError" type="error">{{ readError }}</NAlert>
        <NAlert v-if="unitsError" type="warning">{{ unitsError }}</NAlert>
        <NAlert v-if="data.sourceIssue" type="warning">{{ data.sourceIssue }}</NAlert>
        <NText
          >批准用途：{{ data.purpose || '尚未填写' }}；行政批准要求：{{
            data.requiredAuthority === 'NATIONAL'
              ? '国务院文物行政部门'
              : data.requiredAuthority === 'PROVINCE'
                ? '省级文物行政部门'
                : '依藏品性质及依法确定的等级核实'
          }}</NText
        >
        <NSpace v-if="!loading && !readError && data.canOperate" v-permission="'COLLECTION_ADMIN,COLLECTION_STORAGE'">
          <NButton v-if="canPlan" @click="edit('plan')">{{ latest ? '订正实际方案' : '登记实际方案' }}</NButton>
          <NButton v-if="latest && canPlan" @click="edit('administrative')">登记真实行政批文</NButton>
          <NButton v-if="latest && sourceAvailable && !latest.started && !data.flowInstanceId" @click="submit"
            >提交馆内确认</NButton
          >
          <NButton
            v-if="latest && sourceAvailable && data.flowStatus === 'PASS' && !latest.started"
            @click="edit('start')"
            >登记实际开始</NButton
          >
          <NButton
            v-if="latest?.content.usesOriginal && sourceAvailable && data.flowStatus === 'PASS' && !latest.started"
            @click="requisition"
            >办理原件提用</NButton
          >
          <NButton v-if="latest?.started?.requisitionId" @click="sourceRequisition">查阅来源提用与归还</NButton>
          <template v-if="latest?.started && !latest.completed"
            ><NButton @click="edit('safety')">登记实际安全检查</NButton
            ><NButton v-if="sourceAvailable || latest.stopped" @click="edit('output')">{{
              latest.stopped ? '补登记停止前产物' : '登记复制品或拓片'
            }}</NButton
            ><NButton v-if="!latest.stopped && sourceAvailable" @click="edit('complete')">登记实际完成</NButton
            ><NButton v-if="!latest.stopped" @click="edit('stop')">登记实际停止</NButton
            ><NButton v-if="latest.stopped && latest.stopUnsafe" @click="incident">登记停止异常事故</NButton
            ><NButton v-if="latest.stopped && latest.stopUnsafe" @click="edit('clear-stop')"
              >核对停止异常处置</NButton
            ></template
          >
        </NSpace>
        <NText v-if="latest?.started"
          >实施进度：{{
            latest.stopped ? '已实际停止，原件保管及归还须继续核实' : latest.completed ? '已实际完成' : '实施中'
          }}<template v-if="!latest.completed && data.nextSafetyCheckAt"
            >；下次安全检查期限：{{ formatLocalDateValue(data.nextSafetyCheckAt, 'YYYY-MM-DD HH:mm:ss') }}</template
          ></NText
        >
        <NAlert v-if="latest?.stopped" type="warning"
          >停止不代表正常完成、已经归库或安全限制已解除。可补登记停止前已制作的产物，不能继续本批生产；原件归库仍在来源归还单办理。存在停止安全异常时，应登记真实事故报告与处置，再由保管负责人本人核对。</NAlert
        >
        <NCard v-for="plan in [...(data.plans || [])].reverse()" :key="plan.id" :title="`第 ${plan.version} 版方案`">
          <NTabs type="line" animated>
            <NTabPane name="plan" tab="方案与批文"
              ><CollectionEvidenceReadView :fields="reproductionFields.plan" :data="display(plan.content)" :archive-files="(plan.archives || []).filter((archive: any) => archive.phase === 'PLAN').flatMap((archive: any) => archive.files || [])" :original-url="originalFor(plan,'PLAN')" /><NText
                v-if="plan.qualification"
                >资质：{{ plan.qualification.unitName }} · {{ plan.qualification.certificateNo }} ·
                {{ plan.qualification.scope }}</NText
              ><CollectionEvidenceReadView
                v-for="a in plan.administrative"
                :key="a.id"
                :fields="reproductionFields.administrative"
                :data="a"
                :archive-files="(plan.archives || []).filter((archive: any) => archive.phase === `ADMIN_${a.id}`).flatMap((archive: any) => archive.files || [])" :original-url="originalFor(plan,`ADMIN_${a.id}`)"
            /></NTabPane>
            <NTabPane name="execution" tab="实际实施与检查"
              ><CollectionEvidenceReadView
                v-if="plan.started"
                :fields="reproductionFields.start"
                :data="display(plan.started)"
                :archive-files="(plan.archives || []).filter((archive: any) => archive.phase === 'STARTED').flatMap((archive: any) => archive.files || [])" :original-url="originalFor(plan,'STARTED')"
              /><NCard v-for="c in plan.safety" :key="c.id" :title="`检查记录 ${c.id}`"
                ><CollectionEvidenceReadView :fields="reproductionFields.safety" :data="display(c.content)" :archive-files="(plan.archives || []).filter((archive: any) => archive.phase === `SAFETY_${c.id}`).flatMap((archive: any) => archive.files || [])" :original-url="originalFor(plan,`SAFETY_${c.id}`)" /><NText
                  v-if="c.content.resolvesRecordIds?.length"
                  >本次处理的原问题：{{ c.content.resolvesRecordIds.join('、') }}</NText
                ></NCard
              ><CollectionEvidenceReadView
                v-if="plan.completed"
                :fields="reproductionFields.complete"
                :data="display(plan.completed)"
                :archive-files="(plan.archives || []).filter((archive: any) => archive.phase === 'COMPLETED').flatMap((archive: any) => archive.files || [])" :original-url="originalFor(plan,'COMPLETED')"
              /><CollectionEvidenceReadView
                v-if="plan.stopped"
                :fields="reproductionFields.stop"
                :data="display(plan.stopped)"
                :archive-files="(plan.archives || []).filter((archive: any) => archive.phase === 'STOPPED').flatMap((archive: any) => archive.files || [])" :original-url="originalFor(plan,'STOPPED')"
              />
              <CollectionEvidenceReadView
                v-if="plan.clearance"
                :fields="reproductionFields['clear-stop']"
                :data="display(plan.clearance.record)"
                :archive-files="(plan.archives || []).filter((archive: any) => archive.phase === `STOP_CLEARED_${plan.clearance.version}`).flatMap((archive: any) => archive.files || [])" :original-url="originalFor(plan,`STOP_CLEARED_${plan.clearance.version}`)"
              />
              <NButton v-if="plan.clearance" @click="incidentRef?.open(plan.clearance.record.incidentId)"
                >查阅关联事故</NButton
              >
            </NTabPane>
            <NTabPane name="outputs" tab="产物登记、保管与使用"
              ><NCard v-for="out in plan.outputs" :key="out.id" :title="out.content.number"
                ><CollectionEvidenceReadView :fields="reproductionFields.output" :data="display(out.content)" :archive-files="(plan.archives || []).filter((archive: any) => archive.phase === `OUTPUT_${out.id}`).flatMap((archive: any) => archive.files || [])" :original-url="originalFor(plan,`OUTPUT_${out.id}`)" />
                <template v-if="out.custody">
                  <NText>{{
                    out.custody.position?.inUse ? '当前记录为使用中，归还后再办理保管交接' : '最近保管记录'
                  }}</NText>
                  <CollectionEvidenceReadView
                    :fields="reproductionFields.custodyCurrent"
                    :data="out.custody.position"
                  />
                  <NAlert v-if="out.custody.issue" type="warning">{{ out.custody.issue }}</NAlert>
                  <NButton
                    v-if="
                      !loading &&
                      !readError &&
                      data.canOperate &&
                      plan.id === latest?.id &&
                      out.custody.canTransfer === true
                    "
                    v-permission="'COLLECTION_ADMIN,COLLECTION_STORAGE'"
                    @click="edit('custody', out.id)"
                    >登记保管交接</NButton
                  >
                  <NCard
                    v-for="handover in out.custody.history"
                    :key="`custody-${handover.id}`"
                    :title="`第 ${handover.version} 次保管交接`"
                  >
                    <CollectionEvidenceReadView
                      :fields="[...reproductionFields.custodySource, ...reproductionFields.custody]"
                      :data="handover.record"
                      :archive-files="(plan.archives || []).filter((archive: any) => archive.phase === `CUSTODY_${handover.id}`).flatMap((archive: any) => archive.files || [])" :original-url="originalFor(plan,`CUSTODY_${handover.id}`)"
                    />
                  </NCard>
                </template>
                <NText v-else>保管交接信息暂不可用，请核对服务版本及原件。</NText>
                <NButton
                  v-if="
                    !loading &&
                    !readError &&
                    data.canOperate &&
                    plan.id === latest?.id &&
                    plan.completed &&
                    !(out.uses || []).some((u: any) => !u.returned)
                  "
                  v-permission="'COLLECTION_ADMIN,COLLECTION_STORAGE'"
                  @click="edit('use', out.id)"
                  >登记实际使用</NButton
                ><NCard v-for="u in out.uses" :key="u.id" :title="u.returned ? '已实际归还' : '使用中，待实际归还'"
                  ><NAlert v-if="u.associationVerified === false" type="warning"
                    >旧原件未冻结使用记录与本件产物的关联，请核对原始凭证。</NAlert
                  ><CollectionEvidenceReadView
                    :fields="reproductionFields.use"
                    :data="u.content"
                    :archive-files="(plan.archives || []).filter((archive: any) => archive.phase === `USE_${u.id}`).flatMap((archive: any) => archive.files || [])" :original-url="originalFor(plan,`USE_${u.id}`)"
                  /><CollectionEvidenceReadView
                    v-if="u.returned"
                    :fields="reproductionFields.returned"
                    :data="u.returned"
                    :archive-files="(plan.archives || []).filter((archive: any) => archive.phase === `RETURN_${u.id}`).flatMap((archive: any) => archive.files || [])" :original-url="originalFor(plan,`RETURN_${u.id}`)"
                  /><NButton
                    v-else-if="!loading && !readError && data.canOperate"
                    v-permission="'COLLECTION_ADMIN,COLLECTION_STORAGE'"
                    @click="edit('returned', out.id, u.id)"
                    >登记实际归还</NButton
                  ></NCard
                ></NCard
              ></NTabPane
            >
            <NTabPane name="archives" tab="永久原件"
              ><NCollapse
                ><NCollapseItem v-for="a in plan.archives" :key="a.id" :name="a.id" :title="archivePhaseTitle(a.phase)"
                  ><HFileList :files="a.files" :original-url="(fileId: string) => archivedOriginalUrl(plan.id,a.id,fileId)" :revision="[a?.id, a?.digest]" show-download-btn /></NCollapseItem></NCollapse
            ></NTabPane>
          </NTabs>
        </NCard> </NSpace
    ></NSpin>
  </HPageForm>
  <CollectionEvidenceForm
    :key="`CollectionEvidenceForm-${formEpoch}`"
    ref="formRef"
    :title="reproductionTitles[kind] || ''"
    :fields="fields"
    :save="saveForCurrentForm"
    @saved="saved"
  >
    <template #context>
      <template v-if="kind === 'custody' && editing.custody">
        <NAlert type="info"
          >本次交接固定下列原保管记录。核实实物与双方签署后填写新保管情况；来源发生变化须重新打开核对。</NAlert
        >
        <CollectionEvidenceReadView :fields="reproductionFields.custodySource" :data="editing.custody" />
      </template>
    </template>
  </CollectionEvidenceForm>
  <CollectionRequisitionCaseDrawer
    :key="`CollectionRequisitionCaseDrawer-${formEpoch}`"
    ref="requisitionRef"
    @saved="saved"
  />
  <IncidentDrawer v-if="show && !readError" :key="`IncidentDrawer-${formEpoch}`" ref="incidentRef" @saved="saved" />
</template>
<script setup lang="ts">
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { cloneDeep } from 'lodash-es';
import { useAuthStore } from '@/store/modules/auth';
import { formatLocalDateValue } from '@/utils';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import IncidentDrawer from './IncidentDrawer.vue';
import CollectionRequisitionCaseDrawer from './CollectionRequisitionCaseDrawer.vue';
import { reproductionComplianceApi as api } from '../api/reproductionCompliance';
import { reproductionApi } from '../api/reproduction';
import { reproductionFields, reproductionTitles, reproductionValues } from '../utils/reproductionFields';
import type { EvidenceField } from '../utils/evidenceFields';
const auth = useAuthStore();
const emit = defineEmits<{ (e: 'saved'): void }>();
const show = ref(false),
  loading = ref(false),
  readError = ref(''),
  unitsError = ref(''),
  formEpoch = ref(0),
  data = ref<any>({}),
  fields = ref<EvidenceField[]>([]),
  kind = ref(''),
  formRef = ref<any>(),
  requisitionRef = ref<any>(),
  incidentRef = ref<any>();
const latest = computed(() => data.value.plans?.at(-1));
const sourceAvailable = computed(() => !!data.value.sourceRevision && !data.value.sourceIssue);
const canPlan = computed(
  () =>
    !!data.value.sourceRevision &&
    !data.value.sourceIssue &&
    !unitsError.value &&
    data.value.flowStatus === 'DRAFT' &&
    !data.value.flowInstanceId &&
    !latest.value?.started,
);
let id = '',
  request = 0,
  editing = {
    id: '',
    plan: '',
    source: '',
    output: '',
    use: '',
    kind: '',
    org: '',
    custody: null as null | Record<string, any>,
  },
  units: { value: string; label: string }[] = [];
const display = (v: any) => reproductionValues(v, true);
const archivedOriginalUrl=(planId:string,archiveId:string,fileId:string)=>
  `/collection/reproduction-compliance/${encodeURIComponent(data.value.id)}/plan/${encodeURIComponent(planId)}/original/${encodeURIComponent(archiveId)}/${encodeURIComponent(fileId)}`;
const originalFor=(plan:any,phase:string)=>(fileId:string)=>{
  const archive=(plan?.archives||[]).find((item:any)=>item.phase===phase && item.files?.some((file:any)=>file.fileId===fileId));
  return archive?archivedOriginalUrl(plan.id,archive.id,fileId):'';
};
const archivePhaseTitle=(phase:string)=>({PLAN:'实际方案',SUBMITTED:'馆内提交',STARTED:'实际开始',COMPLETED:'实际完成',STOPPED:'实际停止'} as Record<string,string>)[phase]
  || (phase.startsWith('ADMIN_')?'行政批文':phase.startsWith('APPROVED_')?'馆内确认':phase.startsWith('SAFETY_')?'安全检查':phase.startsWith('OUTPUT_')?'产物登记':phase.startsWith('CUSTODY_')?'产物保管交接':phase.startsWith('USE_')?'产物使用':phase.startsWith('RETURN_')?'产物归还':phase.startsWith('STOP_CLEARED_')?'停止异常处置核对':phase);
const saveForCurrentForm = computed(() => {
  const epoch = formEpoch.value, actor = auth.userInfo?.id;
  return (value: Record<string, any>) => (epoch === formEpoch.value && actor === auth.userInfo?.id ? save(value) : Promise.resolve(false));
});
async function open(value: string) {
  id = value;
  data.value = {};
  show.value = true;
  await reload();
}
function clearForms() {
  editing = { id: '', plan: '', source: '', output: '', use: '', kind: '', org: '', custody: null };
  kind.value = '';
  fields.value = [];
  formEpoch.value++;
}
function close() {
  request++;
  data.value = {};
  units = [];
  readError.value = '';
  unitsError.value = '';
  loading.value = false;
  clearForms();
}
watch(
  show,
  (visible) => {
    if (!visible) close();
  },
  { flush: 'sync' },
);
onBeforeUnmount(close);
watch(() => auth.userInfo?.id, () => {
  show.value = false;
  id = '';
  close();
}, { flush: 'sync' });
async function reload() {
  const token = ++request,
    target = id;
  loading.value = true;
  clearForms();
  data.value = {};
  units = [];
  readError.value = '';
  unitsError.value = '';
  try {
    const [detail, directory] = await Promise.allSettled([api.detail(target), api.units(target)]);
    if (token !== request || !show.value) return false;
    if (detail.status !== 'fulfilled' || !(detail.value as any)?.data) throw new Error('missing detail');
    data.value = (detail.value as any).data;
    if (directory.status === 'fulfilled' && Array.isArray((directory.value as any)?.data))
      units = (directory.value as any).data;
    else unitsError.value = '承担单位列表读取失败，原档案仍可查阅；登记新方案前请重新读取。';
    return true;
  } catch {
    if (token === request && show.value) {
      data.value = {};
      units = [];
      readError.value = '复制拓印资料读取失败，请核对当前授权和原件。';
      clearForms();
    }
    return false;
  } finally {
    if (token === request) loading.value = false;
  }
}
async function edit(action: string, output = '', use = '') {
  if (!show.value || loading.value || readError.value || !data.value.canOperate || !data.value.id) return;
  if (action === 'plan' && unitsError.value) return;
  if (
    !['use', 'returned', 'stop', 'safety', 'clear-stop', 'custody'].includes(action) &&
    !(action === 'output' && latest.value?.stopped) &&
    !sourceAvailable.value
  )
    return;
  if (latest.value?.stopped && ['start', 'complete', 'stop'].includes(action)) return;
  const selected = latest.value?.outputs?.find((out: any) => out.id === output);
  if (
    action === 'custody' &&
    (!data.value.orgId ||
      selected?.custody?.canTransfer !== true ||
      !selected.custody.revision ||
      !selected.custody.position?.custodianUserId ||
      !selected.custody.position?.location)
  )
    return;
  const token = request;
  formEpoch.value++;
  editing = {
    id,
    plan: latest.value?.id,
    source: data.value.sourceRevision,
    output,
    use,
    kind: action,
    org: data.value.orgId,
    custody:
      action === 'custody'
        ? {
            revision: selected.custody.revision,
            fromCustodianUserId: selected.custody.position.custodianUserId,
            fromLocation: selected.custody.position.location,
          }
        : null,
  };
  kind.value = action;
  fields.value = cloneDeep(reproductionFields[action]);
  if (action === 'plan')
    fields.value = fields.value.map((f) => (f.key === 'undertakingUnitId' ? { ...f, options: units } : f));
  if (action === 'safety')
    fields.value.push({
      key: 'resolvesRecordIds',
      label: '本次实际处理的原问题',
      type: 'select',
      multiple: true,
      options: (latest.value?.safety || [])
        .filter((c: any) => c.content.safe === false)
        .map((c: any) => ({ value: c.id, label: `${c.id} · ${c.content.findings}` })),
    });
  const target = editing;
  await nextTick();
  if (token !== request || target !== editing || !show.value) return;
  formRef.value?.open({});
}
async function save(value: Record<string, any>) {
  if (!show.value || loading.value || readError.value || !editing.id || !editing.kind) return false;
  const target = { ...editing };
  const v = reproductionValues(value);
  if (target.kind === 'plan') return api.record(target.id, 'plan', { ...v, sourceRevision: target.source });
  if (target.kind === 'administrative')
    return api.record(target.id, 'administrative', { planId: target.plan, evidence: { ...v, action: 'APPROVAL' } });
  if (['start', 'stop'].includes(target.kind)) return api.record(target.id, target.kind, { ...v, planId: target.plan });
  if (target.kind === 'safety')
    return api.record(target.id, 'safety', { ...v, resolvesRecordIds: v.resolvesRecordIds || [] });
  if (target.kind === 'custody') {
    if (!target.custody) return false;
    const epoch = formEpoch.value;
    const result: any = await api.custody(target.id, target.output, {
      ...v,
      expectedOrgId: target.org,
      expectedRevision: target.custody.revision,
      fromCustodianUserId: target.custody.fromCustodianUserId,
      fromLocation: target.custody.fromLocation,
    });
    if (epoch !== formEpoch.value || !show.value) return false;
    if (typeof result?.data !== 'string' || !result.data.trim())
      throw new Error('未取得有效保管交接编号，请回查原件核实登记结果');
    return result;
  }
  if (target.kind === 'use') return api.use(target.id, target.output, v);
  if (target.kind === 'returned') return api.returned(target.id, target.output, target.use, v);
  return api.record(target.id, target.kind, v);
}
function incident() {
  if (!show.value || loading.value || readError.value || !data.value.canOperate || !latest.value?.stopped) return;
  incidentRef.value?.create({
    source: { bizType: 'REPRODUCTION_COMPLIANCE_2026', bizId: id, recordId: latest.value.id },
    items: [{ collectionId: data.value.collectionId, damaged: latest.value.stopped.originalUndamaged === false }],
  });
}
async function submit() {
  if (
    !show.value ||
    loading.value ||
    readError.value ||
    !data.value.canOperate ||
    !data.value.sourceRevision ||
    data.value.sourceIssue
  )
    return;
  const target = id,
    token = request;
  await reproductionApi.submit(target);
  if (token !== request || target !== id || !show.value) return;
  if (await reload()) emit('saved');
}
async function sourceRequisition() {
  if (
    !show.value ||
    loading.value ||
    readError.value ||
    !data.value.canOperate ||
    !latest.value?.started?.requisitionId
  )
    return;
  await requisitionRef.value?.open(latest.value.started.requisitionId);
}
async function requisition() {
  if (
    !show.value ||
    loading.value ||
    readError.value ||
    !data.value.canOperate ||
    !data.value.sourceRevision ||
    data.value.sourceIssue
  )
    return;
  await requisitionRef.value?.open(undefined, {
    collectionIds: data.value.collectionId,
    purpose: undefined,
    sourceBizType: 'COLLECTION_REPRODUCTION',
    sourceBizId: id,
  });
}
async function saved() {
  if (!show.value || readError.value) return;
  if (await reload()) emit('saved');
}
defineExpose({ open });
</script>
