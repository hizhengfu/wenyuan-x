<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="日常检查与养护">
    <NSpin :show="loading"
      ><NSpace vertical :size="16">
      <CollectionArchiveIssues :data="data" />
        <NSpace justify="end"><NButton :loading="loading" @click="refreshCurrent">重新读取养护资料</NButton></NSpace>
        <NAlert type="info"
          >按本件实际风险制定周期和操作方法。检查应记录保存状况、病害和环境；养护开始须核实实际提用点交或在库办理依据。发现异常可先据实登记，真实紧急报告仍应及时办理。</NAlert
        >
        <NAlert v-if="readError" type="error">{{ readError }}</NAlert>
        <NAlert v-if="historical" type="info"
          >当前为指定收藏单位的养护留存历史，仅供查阅；不读取接收馆当前资料，也不办理新的养护事项。</NAlert
        >
        <NText v-if="historical">{{ data.name || '养护留存历史' }}</NText>
        <NText v-else
          >{{ data.name }} · 下次检查：{{ time(data.nextInspectionAt) }} · 下次养护：{{ time(data.nextCareAt) }}</NText
        >
        <NAlert v-if="hasUnresolved" type="error"
          >本件检查或养护仍有异常待处置。后续正常检查不覆盖历史异常，须关联实际事故处置并核对；普通提用和养护开始仍受限制。</NAlert
        >
        <NSpace size="small" align="center" v-if="canOperate">
          <NButton @click="edit('check')">记录实际检查</NButton>
          <NButton
            v-if="latest?.keeper && !running && !hasUnresolved && lastCheck && !unsafeCheck(lastCheck)"
            @click="edit('start')"
            >记录实际养护开始</NButton
          >
          <NButton v-if="running" @click="edit('end', running.id)">记录实际完成或停止</NButton>
          <NButton v-if="canSignPlan" @click="edit('sign')">本人确认方案</NButton>
          <CollectionActionMenu label="养护方案" v-if="!running">
            <NButton quaternary v-if="!running" @click="edit('plan')">{{
              latest ? '制定新版方案' : '制定检查养护方案'
            }}</NButton>

            <NButton quaternary v-if="latest?.keeper && !running && !hasUnresolved" @click="requisition"
              >办理养护原件提用</NButton
            >
          </CollectionActionMenu>
        </NSpace>
        <NTabs type="line">
          <NTabPane name="checks" tab="检查与病害"
            ><NCard v-for="r in [...(data.checks || [])].reverse()" :key="r.id" :title="time(r.content.checkedAt)">
              <CollectionEvidenceReadView :fields="careFields.check" :data="r.content" :archive-files="(r.archives || []).filter((archive: any) => archive.phase === 'CHECKED').flatMap((archive: any) => archive.files || [])" :original-url="originalUrl(r, 'CHECKED')" />
              <NSpace v-if="unsafeCheck(r)"
                ><NButton
                  v-if="canOperate"
                  v-permission="'COLLECTION_ADMIN,COLLECTION_INCIDENT_MANAGE'"
                  @click="incident(r.id, r.content.damaged)"
                  >登记事故报告与处置</NButton
                ><NButton v-if="canOperate" @click="edit('clear', r.id)">{{
                  r.clearance ? '重新核对事故处置' : '依据事故处置核对异常'
                }}</NButton
                ><NButton v-if="r.clearance" @click="incidentRef?.open(r.clearance.record.incidentId)"
                  >查阅关联事故</NButton
                ></NSpace
              >
              <NCard
                v-for="v in r.clearances || (r.clearance ? [r.clearance] : [])"
                :key="v.version"
                :title="`第 ${v.version || 1} 次事故处置核对`"
              >
                <CollectionEvidenceReadView :fields="careFields.clear" :data="v.record" :archive-files="(r.archives || []).filter((archive: any) => archive.phase === `CLEARED_${v.version}`).flatMap((archive: any) => archive.files || [])" :original-url="originalUrl(r, `CLEARED_${v.version}`)" />
              </NCard> </NCard
          ></NTabPane>
          <NTabPane name="works" tab="实际养护"
            ><NCard
              v-for="r in [...(data.works || [])].reverse()"
              :key="r.id"
              :title="r.ended ? (r.ended.completed ? '已记录正常完成' : '已记录实际停止') : '已开始，待记录实际结束'"
            >
              <CollectionEvidenceReadView :fields="careFields.start" :data="r.started" :archive-files="(r.archives || []).filter((archive: any) => archive.phase === 'STARTED').flatMap((archive: any) => archive.files || [])" :original-url="originalUrl(r, 'STARTED')" /><CollectionEvidenceReadView
                v-if="r.ended"
                :fields="careFields.end"
                :data="r.ended"
                :archive-files="(r.archives || []).filter((archive: any) => archive.phase === 'ENDED').flatMap((archive: any) => archive.files || [])" :original-url="originalUrl(r, 'ENDED')"
              />
              <NSpace v-if="r.ended && (!r.ended.safeToRelease || r.ended.unexpectedDamage)"
                ><NButton
                  v-if="canOperate"
                  v-permission="'COLLECTION_ADMIN,COLLECTION_INCIDENT_MANAGE'"
                  @click="incident(r.id, r.ended.unexpectedDamage)"
                  >登记事故报告与处置</NButton
                ><NButton v-if="canOperate" @click="edit('clear', r.id)">{{
                  r.clearance ? '重新核对事故处置' : '依据事故处置核对异常'
                }}</NButton
                ><NButton v-if="r.clearance" @click="incidentRef?.open(r.clearance.record.incidentId)"
                  >查阅关联事故</NButton
                ></NSpace
              >
              <NCard
                v-for="v in r.clearances || (r.clearance ? [r.clearance] : [])"
                :key="v.version"
                :title="`第 ${v.version || 1} 次事故处置核对`"
              >
                <CollectionEvidenceReadView :fields="careFields.clear" :data="v.record" :archive-files="(r.archives || []).filter((archive: any) => archive.phase === `CLEARED_${v.version}`).flatMap((archive: any) => archive.files || [])" :original-url="originalUrl(r, `CLEARED_${v.version}`)" />
              </NCard> </NCard
          ></NTabPane>
          <NTabPane name="plans" tab="方案及本人确认"
            ><NCard v-for="p in [...(data.plans || [])].reverse()" :key="p.id" :title="`第 ${p.version} 版方案`">
              <CollectionEvidenceReadView :fields="careFields.plan" :data="p.content" :archive-files="(p.archives || []).filter((archive: any) => archive.phase === 'PLAN').flatMap((archive: any) => archive.files || [])" :original-url="originalUrl(p, 'PLAN')" />
              <NText
                >保护修复确认：{{ p.conservation?.actorUserId || '待本人确认' }}；保管确认：{{
                  p.keeper?.actorUserId || '待本人确认'
                }}</NText
              >
              <CollectionEvidenceReadView
                v-if="p.conservation"
                :fields="careFields.sign"
                :data="p.conservation.record"
                :archive-files="(p.archives || []).filter((archive: any) => archive.phase === 'CONSERVATION').flatMap((archive: any) => archive.files || [])" :original-url="originalUrl(p, 'CONSERVATION')"
              /><CollectionEvidenceReadView v-if="p.keeper" :fields="careFields.sign" :data="p.keeper.record" :archive-files="(p.archives || []).filter((archive: any) => archive.phase === 'KEEPER').flatMap((archive: any) => archive.files || [])" :original-url="originalUrl(p, 'KEEPER')" /> </NCard
          ></NTabPane>
        </NTabs> </NSpace
    ></NSpin>
  </HPageForm>
  <CollectionEvidenceForm
    v-if="!readError && show"
    :key="`CollectionEvidenceForm-${formEpoch}`"
    ref="formRef"
    :title="careTitles[kind]"
    :fields="fields"
    :save="save"
    @saved="formSaved"
  />
  <IncidentDrawer v-if="!readError && show" :key="`IncidentDrawer-${formEpoch}`" ref="incidentRef" @saved="incidentSaved" />
  <CollectionRequisitionCaseDrawer v-if="!readError && show" :key="`CollectionRequisitionCaseDrawer-${formEpoch}`" ref="requisitionRef" @saved="requisitionSaved" />
</template>
<script setup lang="ts">
import CollectionActionMenu from './CollectionActionMenu.vue';
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { formatDateTime } from '@/utils';
import { useAuthStore } from '@/store/modules/auth';
import { careApi as api } from '../api/care';
import { careFields, careTitles, careValues } from '../utils/careFields';
import type { EvidenceField } from '../utils/evidenceFields';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import IncidentDrawer from './IncidentDrawer.vue';
import CollectionRequisitionCaseDrawer from './CollectionRequisitionCaseDrawer.vue';
const auth = useAuthStore();
const emit = defineEmits<{ (e: 'saved'): void }>();
const show = ref(false),
  loading = ref(false),
  data = ref<any>({}),
  kind = ref(''),
  readError = ref(''),
  historyOrg = ref<string | null>(null),
  formEpoch = ref(0),
  fields = ref<EvidenceField[]>([]);
const formSaved = ref<() => void>(() => {}), incidentSaved = ref<() => void>(() => {}), requisitionSaved = ref<() => void>(() => {});
const formRef = ref<any>(),
  incidentRef = ref<any>(),
  requisitionRef = ref<any>();
const historical = computed(() => historyOrg.value !== null || data.value.historical === true);
const canOperate = computed(
  () => show.value && !historical.value && !loading.value && !readError.value && data.value.canOperate === true,
);
const latest = computed(() => data.value.plans?.at(-1));
const canSignPlan = computed(() => {
  const actor = auth.userInfo?.id, plan = latest.value;
  if (!canOperate.value || !actor || !plan) return false;
  if (actor === plan.content?.conservationUserId) return !plan.conservation;
  return actor === plan.content?.keeperUserId && !!plan.conservation && !plan.keeper;
});
const lastCheck = computed(() => data.value.checks?.at(-1));
const hasUnresolved = computed(
  () =>
    data.value.checks?.some((r: any) => unsafeCheck(r) && !r.clearance) ||
    data.value.works?.some((r: any) => r.ended && (!r.ended.safeToRelease || r.ended.unexpectedDamage) && !r.clearance),
);
const running = computed(() => data.value.works?.find((w: any) => !w.ended));
let id = '', request = 0;
type CareTarget = { id: string; plan: string; record: string; check: string; revision: string; kind: string };
const unavailableSave = async (_value: Record<string, any>): Promise<any> => { throw new Error('请重新读取当前养护资料后办理'); };
const save = ref<(value: Record<string, any>) => Promise<any>>(unavailableSave);
const time = (v: any) => (v ? formatDateTime(v) : '尚未形成依据');
const unsafeCheck = (r: any) => r.content.damaged || !r.content.safeForRoutineCare || !r.content.physicallyPresent;
function originalUrl(record: any, phase: string) {
  return (fileId: string) => {
    const archive = record.archives?.find((row: any) => row.phase === phase);
    return archive?.files?.some((file: any) => file.fileId === fileId)
      ? api.originalUrl(id, data.value.orgId, record.id, phase, fileId)
      : '';
  };
}
async function open(value: string) {
  clearForm();
  historyOrg.value = null;
  id = value;
  data.value = {};
  readError.value = '';
  show.value = true;
  await reload();
}
async function openHistory(value: { collectionId: string; orgId: string }) {
  clearForm();
  id = value.collectionId;
  historyOrg.value = value.orgId;
  data.value = {};
  readError.value = '';
  show.value = true;
  await reload();
}
function clearForm() {
  formSaved.value = () => {}; incidentSaved.value = () => {}; requisitionSaved.value = () => {};
  save.value = unavailableSave;
  kind.value = '';
  fields.value = [];
  formEpoch.value++;
}
watch(
  show,
  (value) => {
    if (!value) {
      request++;
      data.value = {};
      readError.value = '';
      loading.value = false;
      clearForm();
    }
  },
  { flush: 'sync' },
);
onBeforeUnmount(() => {
  request++;
  clearForm();
});
watch(() => auth.userInfo?.id, () => {
  request++;
  show.value = false;
  id = '';
  data.value = {};
  readError.value = '';
  loading.value = false;
  historyOrg.value = null;
  clearForm();
}, { flush: 'sync' });
async function refreshCurrent() {
  clearForm();
  await reload();
}
async function reload() {
  if (!show.value || !id) return false;
  const token = ++request,
    target = id,
    targetOrg = historyOrg.value;
  loading.value = true;
  readError.value = '';
  try {
    const r: any = targetOrg !== null ? await api.history(target, targetOrg) : await api.detail(target);
    if (token !== request || !show.value) return false;
    if (!r?.data) throw new Error('养护资料缺失');
    data.value = r.data;
    return true;
  } catch {
    if (token === request && show.value) {
      data.value = {};
      readError.value = '养护资料读取失败，请核对当前授权和原件后重新读取。';
      clearForm();
    }
    return false;
  } finally {
    if (token === request) loading.value = false;
  }
}
async function edit(action: string, record = '') {
  if (!canOperate.value || !data.value.collectionId || (action === 'sign' && !canSignPlan.value)) return;
  clearForm();
  const target: CareTarget = {
    id,
    plan: latest.value?.id || '',
    record,
    check: lastCheck.value?.id || '',
    revision: data.value.sourceRevision || '',
    kind: action,
  };
  const epoch = formEpoch.value;
  formSaved.value = childReceipt();
  save.value = async (value) => {
    if (epoch !== formEpoch.value || !canOperate.value || id !== target.id) throw new Error('请重新读取当前养护资料后办理');
    return submit(target, value);
  };
  kind.value = action;
  fields.value = careFields[action];
  await nextTick();
  if (epoch === formEpoch.value && canOperate.value) formRef.value?.open({});
}
async function submit(target: CareTarget, value: Record<string, any>) {
  const v = careValues(value);
  if (target.kind === 'plan')
    return api.record(target.id, 'plan', {
      ...v,
      previousPlanId: target.plan || null,
      sourceRevision: target.revision,
      methods: v.methods || [],
    });
  if (target.kind === 'sign') return api.record(target.id, `plan/${target.plan}/sign`, v);
  if (target.kind === 'check') return api.record(target.id, 'check', { ...v, planId: target.plan || null }, target.revision);
  if (target.kind === 'start')
    return api.record(target.id, 'start', {
      ...v,
      planId: target.plan,
      checkId: target.check,
      methods: v.methods || [],
    });
  if (target.kind === 'end')
    return api.record(target.id, `work/${target.record}/end`, { ...v, actualMethods: v.actualMethods || [] });
  return api.record(target.id, `record/${target.record}/clear`, v);
}
function childReceipt() {
  const target = id, generation = request, epoch = formEpoch.value, actor = auth.userInfo?.id;
  let handled = false;
  return () => {
    if (handled || !actor || actor !== auth.userInfo?.id || !canOperate.value ||
        target !== id || generation !== request || epoch !== formEpoch.value) return;
    handled = true;
    void reload();
    emit('saved');
  };
}
function incident(recordId: string, damaged: boolean) {
  if (!canOperate.value) return;
  incidentSaved.value = childReceipt();
  incidentRef.value?.create({
    source: { bizType: 'COLLECTION_CARE', bizId: id, recordId },
    items: [{ collectionId: id, damaged }],
  });
}
function requisition() {
  if (!canOperate.value) return;
  requisitionSaved.value = childReceipt();
  requisitionRef.value?.open(undefined, { collectionIds: id, sourceBizType: 'COLLECTION_CARE', sourceBizId: id });
}
defineExpose({ open, openHistory });
</script>
