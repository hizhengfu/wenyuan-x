<template>
  <HPageView v-model:show="show" title="保护方案共同评估与原件" width="1180px">
    <NSpin :show="loading"
      ><NSpace vertical :size="16">
        <CollectionArchiveIssues :data="data" />
        <NAlert type="info"
          >先核对本版实际数值、单位和适用范围，再登记共同评估原件，由所列参与人员本人核对。本馆流程要求不同部门的保护修复与保管责任人员参加；具体限值须有实际专业依据。</NAlert
        >
        <NAlert v-if="error" type="error">{{ error }}</NAlert>
        <template v-if="!loading && !error && data.policyId">
          <NText>{{ data.name }}</NText>
          <NButton @click="reload">刷新办理状态</NButton>
          <NAlert v-if="!data.editable" type="warning">当前只可查阅原件，办理资格或归档状态请重新核对。</NAlert>
          <NSelect
            v-model:value="selectedVersion"
            :options="versionOptions"
            placeholder="选择待评估草稿或当前运行版本"
          />
          <template v-if="version">
            <NAlert :type="version.state === 'READY' ? 'info' : 'warning'">{{
              policyEvidenceStates[version.state] || '办理状态待核查'
            }}</NAlert>
            <NText v-if="version.state === 'LEGACY_REVIEW_REQUIRED'"
              >既有告警继续运行；补录原件不代表已完成共同评估，人工启用仍须完成本版核对。</NText
            >
            <NText v-for="q in version.qualificationErrors || []" :key="q.userId">{{ q.message }}</NText>
            <PreservationPolicyDefinition :definition="version.definition" />
            <NSpace v-if="data.editable">
              <NButton @click="editAssessment">登记本版实际共同评估</NButton>
              <NButton
                v-if="version.draft"
                :disabled="version.state !== 'READY'"
                :loading="publishing"
                type="primary"
                @click="activate"
                >启用已核对的本版方案</NButton
              >
              <NButton
                v-else-if="version.active"
                :disabled="version.state !== 'READY'"
                :loading="publishing"
                @click="activate"
                >恢复或确认启用当前版本</NButton
              >
            </NSpace>
          </template>
          <NTabs type="line">
            <NTabPane name="assessments" tab="评估、本人核对与历史原件">
              <NEmpty v-if="!data.records.length" description="尚无共同评估原件" />
              <NCard v-for="r in [...data.records].reverse()" :key="r.id" :title="`第 ${r.version} 次共同评估`">
                <NText>{{
                  r.currentDefinitionMatches ? '对应的方案定义与原件一致' : '对应方案定义已变化，保留本次历史原件'
                }}</NText>
                <CollectionEvidenceReadView :fields="recordFields" :data="recordValue(r)" :archive-files="stageFiles(r.archives, 'ASSESSED')" />
                <NCollapse
                  ><NCollapseItem :name="r.id" title="本次签署对应的数值、单位和适用范围"
                    ><PreservationPolicyDefinition :definition="r.definition" /></NCollapseItem
                ></NCollapse>
                <div v-for="(person, index) in participants(r)" :key="index">
                  <NText>{{ policyDuties[person.duty] }} · 第 {{ index + 1 }} 位实际参与人</NText>
                  <CollectionEvidenceReadView :fields="participantFields" :data="person" />
                  <CollectionEvidenceReadView
                    v-if="signature(r, index)"
                    :archive-files="stageFiles(r.archives, `PARTICIPANT_${index + 1}`)"
                    :fields="signatureHistoryFields"
                    :data="signature(r, index)"
                  />
                  <NButton v-else-if="canSign(r, index + 1)" @click="editSignature(r, index + 1)"
                    >{{ policyDuties[person.duty] }}本人核对本次原件</NButton
                  >
                  <NText v-else-if="canSignRecord(r) && person.userId !== auth.userInfo?.id">待该参与人员本人核对</NText>
                  <NText v-else>本次未签署，旧版原件不能继续补签</NText>
                </div>
              </NCard>
            </NTabPane>
          </NTabs>
        </template>
      </NSpace></NSpin
    >
  </HPageView>
  <CollectionEvidenceForm
    v-if="formSession"
    :key="formSession"
    ref="formRef"
    :title="formTitle"
    :fields="fields"
    :save="save"
    @saved="saved"
  >
    <template #context>
      <NAlert type="info"
        >{{ editingLabel }}。请核对下列本次实际依据；页面中的其他方案变化不会改换本表单所对应的原件。</NAlert
      >
      <PreservationPolicyDefinition v-if="editingDefinition" :definition="editingDefinition" />
    </template>
  </CollectionEvidenceForm>
</template>
<script setup lang="ts">
import CollectionArchiveIssues from '@/modules/collection/components/CollectionArchiveIssues.vue';
import { computed, nextTick, ref, watch, onBeforeUnmount, onActivated, onDeactivated } from 'vue';
import { cloneDeep } from 'lodash-es';
import { useAuthStore } from '@/store/modules/auth';
import { useMessage } from 'naive-ui';
import { protectionPolicyApi as api } from '../api/protectionPolicy';
import {
  assessmentFields,
  participantFields,
  policySignatureFields,
  policyEvidenceStates,
  policyDuties,
  environmentFactors,
} from '../utils/policyEvidenceFields';
import type { EvidenceField } from '@/modules/collection/utils/evidenceFields';
import CollectionEvidenceForm from '@/modules/collection/components/CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from '@/modules/collection/components/CollectionEvidenceReadView.vue';
import PreservationPolicyDefinition from './PreservationPolicyDefinition.vue';
const emit = defineEmits<{ (e: 'saved'): void }>();
const message = useMessage();
const auth = useAuthStore();
const show = ref(false),
  loading = ref(false),
  publishing = ref(false),
  error = ref('');
const data = ref<any>({}),
  selectedVersion = ref<string | null>(null),
  formRef = ref<any>();
const fields = ref<EvidenceField[]>([]),
  formTitle = ref(''),
  editingLabel = ref(''),
  editingDefinition = ref<any>();
const version = computed(() => data.value.versions?.find((v: any) => v.id === selectedVersion.value));
const versionOptions = computed(() =>
  (data.value.versions || []).map((v: any) => ({
    value: v.id,
    label: `${v.draft ? '待启用草稿' : '已发布版本'} · ${policyEvidenceStates[v.state] || '待核查'}`,
  })),
);
const recordFields: EvidenceField[] = [
  { key: 'recordedBy', label: '实际登记人员', type: 'user' },
  { key: 'recordedAt', label: '系统登记时间', type: 'datetime' },
  ...assessmentFields,
];
const signatureHistoryFields: EvidenceField[] = [
  { key: 'actorUserId', label: '本人核对人员', type: 'user' },
  { key: 'confirmedAt', label: '系统本人核对时间', type: 'datetime' },
  ...policySignatureFields,
];
function stageFiles(archives: any[] | undefined, phase: string): any[] {
  const matching = Array.isArray(archives) ? archives.filter((archive) => archive.phase === phase) : [];
  return matching.length === 1 && Array.isArray(matching[0].files) ? matching[0].files : [];
}
const participants = (r: any): any[] => r.record.request.participants;
const recordValue = (r: any) => ({
  ...r.record.request,
  recordedBy: r.record.recordedBy,
  recordedAt: r.record.recordedAt,
});
const signature = (r: any, index: number) => {
  const s = r.signatures?.find((s: any) => s.participantNumber === index + 1)?.signature;
  return s ? { ...s.record, actorUserId: s.actorUserId, confirmedAt: s.confirmedAt } : null;
};
const canSignRecord = (r: any) =>
  active &&
  !disposed &&
  show.value &&
  !loading.value &&
  !error.value &&
  data.value.editable === true &&
  r.currentDefinitionMatches &&
  data.value.versions.some((v: any) => v.id === r.policyVersionId && v.assessmentId === r.id);
const canSign = (r: any, participantNumber: number) =>
  Number.isInteger(participantNumber) &&
  participantNumber >= 1 && participantNumber <= participants(r).length &&
  !!auth.userInfo?.id && participants(r)[participantNumber - 1].userId === auth.userInfo.id &&
  auth.hasPermission('PRESERVATION_THRESHOLD_MANAGE,PRESERVATION_ADMIN') &&
  canSignRecord(r);
let requestId = '',
  sequence = 0,
  publishSequence = 0,
  serial = 0,
  active = true,
  disposed = false,
  editing: any;
const formSession = ref(0);
const save = ref<(value: Record<string, any>) => Promise<unknown>>(async () => false);
const saved = ref<() => Promise<void>>(async () => {});
const currentRead = (token: number, id: string) =>
  active && !disposed && show.value && token === sequence && requestId === id;
function invalidateForm() {
  editing = undefined;
  formSession.value = 0;
  fields.value = [];
  formTitle.value = '';
  editingLabel.value = '';
  editingDefinition.value = undefined;
  save.value = async () => false;
  saved.value = async () => {};
}
function close() {
  ++sequence;
  ++publishSequence;
  requestId = '';
  data.value = {};
  selectedVersion.value = null;
  loading.value = false;
  publishing.value = false;
  error.value = '';
  show.value = false;
  invalidateForm();
}
function bindForm(context: any) {
  const e = context,
    actorId = auth.userInfo?.id,
    session = ++serial;
  editing = e;
  formSession.value = session;
  const current = () => active && !disposed && show.value && editing === e && formSession.value === session &&
    actorId === auth.userInfo?.id && (e.kind !== 'sign' ||
      (e.participantUserId === actorId && auth.hasPermission('PRESERVATION_THRESHOLD_MANAGE,PRESERVATION_ADMIN')));
  let sending = false;
  save.value = async (value) => {
    if (!current() || sending) return false;
    sending = true;
    try {
      const body = cloneDeep(value);
      const response =
        e.kind === 'assessment'
          ? await api.registerAssessment(e.policyId, {
              ...body,
              policyVersionId: e.policyVersionId,
              definitionHash: e.definitionHash,
              previousId: e.previousId,
            })
          : await api.signAssessment(e.policyId, e.assessmentId, e.participantNumber, body);
      return current() ? response : false;
    } catch (failure: any) {
      if (current()) {
        invalidateForm();
        const reason = failure?.message || '办理失败，请重新读取当前资料';
        if (requestId === e.policyId) {
          error.value = reason;
          data.value = {};
          selectedVersion.value = null;
        } else message.error(`原表单对应方案办理失败：${reason}`);
      }
      return false;
    } finally {
      sending = false;
    }
  };
  saved.value = async () => {
    if (!current()) return;
    emit('saved');
    if (current() && requestId === e.policyId) await reload();
  };
  return current;
}
async function open(id: string) {
  if (!active || disposed) return;
  requestId = id;
  selectedVersion.value = null;
  show.value = true;
  await reload();
}
async function reload() {
  if (!active || disposed || !show.value || !requestId) return;
  ++publishSequence;
  publishing.value = false;
  if (editing?.policyId === requestId) invalidateForm();
  const current = ++sequence,
    id = requestId,
    selected = selectedVersion.value;
  data.value = {};
  error.value = '';
  loading.value = true;
  try {
    const response: any = await api.assessment(id),
      value = response?.data;
    if (!currentRead(current, id)) return;
    if (
      !value ||
      value.policyId !== id ||
      typeof value.editable !== 'boolean' ||
      !Array.isArray(value.versions ?? []) ||
      !Array.isArray(value.records ?? [])
    )
      throw new Error('共同评估资料不完整，请刷新核对');
    const versions = value.versions || [],
      records = value.records || [];
    if (
      versions.some(
        (v: any) => !v.id || !v.definition || !v.definitionHash || !v.state || (v.state === 'READY' && !v.assessmentId),
      ) ||
      records.some((r: any) => !r.id || !r.record?.request || !Array.isArray(r.record.request.participants))
    )
      throw new Error('共同评估资料不完整，请刷新核对');
    data.value = { ...value, versions, records };
    selectedVersion.value = versions.some((v: any) => v.id === selected) ? selected : versions[0]?.id || null;
  } catch (e: any) {
    if (currentRead(current, id)) {
      data.value = {};
      selectedVersion.value = null;
      error.value = e?.message || '共同评估读取失败，请刷新核对';
    }
  } finally {
    if (currentRead(current, id)) loading.value = false;
  }
}
async function editAssessment() {
  if (
    !active ||
    disposed ||
    !show.value ||
    !version.value ||
    data.value.editable !== true ||
    loading.value ||
    error.value ||
    publishing.value
  )
    return;
  const context = {
    kind: 'assessment',
    policyId: data.value.policyId,
    policyVersionId: version.value.id,
    definitionHash: version.value.definitionHash,
    previousId: data.value.previousId || null,
  };
  const current = bindForm(context);
  editingDefinition.value = cloneDeep(version.value.definition);
  editingLabel.value = `${data.value.name} · 本版实际共同评估`;
  const metricOptions = editingDefinition.value.metrics.map((metric: any) => ({
    value: metric.code,
    label: `${metric.name || metric.code}${metric.unit ? `（${metric.unit}）` : ''}`,
  }));
  fields.value = assessmentFields.map((f) => {
    if (f.key === 'replacementReason') return { ...f, required: !!editing.previousId };
    if (f.key === 'monitoringFreshness')
      return {
        ...f,
        children: f.children?.map((child) =>
          child.key === 'metricCode'
            ? { ...child, label: '本版指标', type: 'select' as const, options: metricOptions }
            : child,
        ),
      };
    return f;
  });
  formTitle.value = '登记本版实际共同评估';
  await nextTick();
  if (current())
    formRef.value?.open({
      environmentMeasures: Object.keys(environmentFactors).map((factor) => ({ factor })),
      monitoringFreshness: [...new Set(editingDefinition.value.items.map((item: any) => item.metricCode))].map(
        (metricCode) => ({ metricCode }),
      ),
    });
}
async function editSignature(r: any, participantNumber: number) {
  r = data.value.records?.find((item: any) => item.id === r.id);
  if (
    !r ||
    !canSign(r, participantNumber) ||
    publishing.value ||
    !Number.isInteger(participantNumber) ||
    participantNumber < 1 ||
    participantNumber > participants(r).length ||
    signature(r, participantNumber - 1)
  )
    return;
  const current = bindForm({ kind: 'sign', policyId: data.value.policyId, assessmentId: r.id, participantNumber,
    participantUserId: participants(r)[participantNumber - 1].userId });
  editingDefinition.value = cloneDeep(r.definition);
  editingLabel.value = `${data.value.name} · 第 ${r.version} 次评估 · 第 ${participantNumber} 位参与人员`;
  fields.value = policySignatureFields;
  formTitle.value = '参与人员本人核对原件';
  await nextTick();
  if (current()) formRef.value?.open({});
}
async function activate() {
  if (
    !active ||
    disposed ||
    !show.value ||
    loading.value ||
    error.value ||
    publishing.value ||
    data.value.editable !== true ||
    version.value?.state !== 'READY'
  )
    return;
  const id = data.value.policyId,
    v = cloneDeep(version.value),
    read = sequence,
    token = ++publishSequence;
  const current = () => currentRead(read, id) && token === publishSequence && selectedVersion.value === v.id;
  publishing.value = true;
  try {
    if (v.draft)
      await api.publish(id, { policyVersionId: v.id, definitionHash: v.definitionHash, assessmentId: v.assessmentId });
    else await api.setStatus(id, 'ACTIVE');
    if (!current()) return;
    message.success('已按完成核对的版本启用，执行情况请查阅方案列表与告警记录');
    emit('saved');
    if (current()) await reload();
  } catch (failure: any) {
    if (current()) {
      invalidateForm();
      data.value = {};
      selectedVersion.value = null;
      publishing.value = false;
      const businessReason = failure?.response?.data?.message;
      error.value = (typeof businessReason === 'string' && businessReason.trim() ? businessReason : failure?.message) ||
        '启用失败，请重新读取当前办理状态';
    }
  } finally {
    if (current()) publishing.value = false;
  }
}
watch(() => auth.userInfo?.id, close, { flush: 'sync' });
watch(
  show,
  (value) => {
    if (!value) close();
  },
  { flush: 'sync' },
);
watch(
  selectedVersion,
  () => {
    ++publishSequence;
    publishing.value = false;
  },
  { flush: 'sync' },
);
onDeactivated(() => {
  active = false;
  close();
});
onActivated(() => {
  if (!disposed) active = true;
});
onBeforeUnmount(() => {
  disposed = true;
  active = false;
  close();
});
defineExpose({ open, close });
</script>
