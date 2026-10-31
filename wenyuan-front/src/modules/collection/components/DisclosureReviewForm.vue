<template>
  <NModal v-model:show="show" preset="card" title="登记实际公开稿核查及依据" style="width: 1000px; max-width: 96vw">
    <NSpace vertical :size="14">
      <NAlert type="info"
        >逐项核实公开范围，并填写经过核查的公开文字和图片地址。内部估价、库位、RFID、协议及个人资料不会从藏品档案自动带入。保存核查原件不代表已经公开。</NAlert
      >
      <NForm label-placement="top"
        ><WarehouseRecordFields v-model="model" :fields="fields" />
        <NCard v-for="[key, label] in disclosureRestrictions" :key="key" :title="label" style="margin-bottom: 12px"
          ><NFormItem label="实际核查处理" required
            ><NSelect v-model:value="restrictions[key].handling" :options="disclosureHandlings" /></NFormItem
          ><NFormItem label="实际判断及脱敏依据" required
            ><NInput v-model:value="restrictions[key].explanation" type="textarea" /></NFormItem
        ></NCard>
        <template v-if="model.decision === 'PUBLIC'">
          <WarehouseRecordFields v-model="model" :fields="disclosurePublicFields" />
          <NAlert v-if="subject === 'ACQUISITION'" type="info"
            >征集公开稿须说明管理和使用情况；尚未利用的，按真实现状说明。</NAlert
          >
          <NFormItem v-for="[key, label] in publicFacts" :key="key" :label="`${label}（公开值，留空不输出）`"
            ><NInput v-model:value="facts[key]" type="textarea"
          /></NFormItem>
          <template v-if="subject === 'EXIT_CONSULTATION'">
            <NAlert type="info"
              >逐一核对每份独立意见，填写允许公开的专家标识、该名专家意见及真实结论。不得用汇总意见替代逐名公开。</NAlert
            >
            <NCard
              v-for="(opinion, index) in expertOpinions"
              :key="opinion.sourceOpinionId"
              :title="`独立意见 ${index + 1}`"
            >
              <NText>对应原意见编号：{{ opinion.sourceOpinionId }}</NText>
              <NFormItem label="公开专家标识" required><NInput v-model:value="opinion.publicExpertLabel" /></NFormItem>
              <NFormItem label="该名专家的公开意见" required
                ><NInput v-model:value="opinion.opinion" type="textarea"
              /></NFormItem>
              <NFormItem label="原意见的真实结论" required
                ><NSelect
                  v-model:value="opinion.verdict"
                  :options="[
                    { value: 'AGREE', label: '同意退出' },
                    { value: 'DISAGREE', label: '不同意退出' },
                  ]"
              /></NFormItem>
            </NCard>
          </template>
        </template>
        <NFormItem v-if="model.decision === 'WITHHELD'" label="整体不公开的实际依据" required
          ><NInput v-model:value="model.withholdingReason" type="textarea"
        /></NFormItem>
      </NForm>
      <NAlert v-if="error" type="error">{{ error }}</NAlert>
    </NSpace>
    <template #footer
      ><NSpace justify="end"
        ><NButton @click="show = false">取消</NButton
        ><NButton type="primary" :loading="saving" @click="submit">留存本次核查及公开稿原件</NButton></NSpace
      ></template
    >
  </NModal>
</template>
<script setup lang="ts">
import { computed, ref, watch, onActivated, onDeactivated, onBeforeUnmount } from 'vue';
import { cloneDeep } from 'lodash-es';
import WarehouseRecordFields from './WarehouseRecordFields.vue';
import {
  disclosureReviewFields,
  disclosurePublicFields,
  disclosureRestrictions,
  disclosureHandlings,
  publicFacts,
} from '../utils/disclosureFields';
const props = defineProps<{ save: (org: string, subject: string, id: string, value: any) => Promise<any> }>();
const emit = defineEmits<{ (e: 'saved'): void }>();
const show = ref(false),
  saving = ref(false),
  model = ref<any>({}),
  restrictions = ref<Record<string, any>>({}),
  facts = ref<Record<string, string>>({}),
  revision = ref(false),
  subject = ref('RESOURCE'),
  error = ref('');
let org = '',
  id = '',
  sourceRevision = '';
const expertOpinions = ref<any[]>([]);
const fields = computed(() => disclosureReviewFields(revision.value));
let generation = 0,
  active = true;
function invalidate() {
  ++generation;
  saving.value = false;
  error.value = '';
  model.value = {};
  facts.value = {};
  restrictions.value = {};
  expertOpinions.value = [];
  revision.value = false;
  org = '';
  id = '';
  sourceRevision = '';
}
function close() {
  show.value = false;
  invalidate();
}
watch(
  show,
  (value) => {
    if (!value) invalidate();
  },
  { flush: 'sync' },
);
onActivated(() => {
  active = true;
});
onDeactivated(() => {
  active = false;
  close();
});
onBeforeUnmount(() => {
  active = false;
  close();
});
function open(value: {
  orgId: string;
  subject: string;
  subjectId: string;
  sourceRevision: string;
  revision: boolean;
  expertOpinionIds?: string[];
}) {
  if (!active) return;
  ++generation;
  saving.value = false;
  org = value.orgId;
  id = value.subjectId;
  subject.value = value.subject;
  sourceRevision = value.sourceRevision;
  revision.value = value.revision;
  model.value = {};
  restrictions.value = Object.fromEntries(disclosureRestrictions.map(([key]) => [key, {}]));
  facts.value = {};
  expertOpinions.value = (value.expertOpinionIds || []).map((sourceOpinionId) => ({ sourceOpinionId }));
  error.value = '';
  show.value = true;
}
async function submit() {
  if (!active || !show.value || saving.value) return;
  const required = [...fields.value, ...(model.value.decision === 'PUBLIC' ? disclosurePublicFields : [])];
  const missing = required.find(
    (f) => f.required && (model.value[f.key] === undefined || model.value[f.key] === null || model.value[f.key] === ''),
  );
  if (missing) {
    error.value = `请填写${missing.label}`;
    return;
  }
  if (
    disclosureRestrictions.some(
      ([key]) => !restrictions.value[key].handling || !restrictions.value[key].explanation?.trim(),
    )
  ) {
    error.value = '请逐项填写四类限制公开信息的实际核查依据';
    return;
  }
  if (model.value.decision === 'WITHHELD' && !model.value.withholdingReason?.trim()) {
    error.value = '请填写整体不公开的实际依据';
    return;
  }
  if (
    model.value.decision === 'PUBLIC' &&
    subject.value === 'ACQUISITION' &&
    (!facts.value.MANAGEMENT?.trim() || !facts.value.USE?.trim())
  ) {
    error.value = '请按实际情况填写征集管理和使用情况';
    return;
  }
  if (
    model.value.decision === 'PUBLIC' &&
    ['EXIT_CONSULTATION', 'EXIT_RESULT', 'TRANSFER_RESULT'].includes(subject.value) &&
    !facts.value.NAME?.trim()
  ) {
    error.value = '请填写藏品基本情况中的公开名称';
    return;
  }
  if (
    model.value.decision === 'PUBLIC' &&
    subject.value === 'EXIT_CONSULTATION' &&
    (!facts.value.DISPOSITION_PLAN?.trim() ||
      expertOpinions.value.length < 5 ||
      expertOpinions.value.some((o) => !o.publicExpertLabel?.trim() || !o.opinion?.trim() || !o.verdict))
  ) {
    error.value = '请填写退出处置方案及每一名专家的公开意见和真实结论';
    return;
  }
  if (
    model.value.decision === 'PUBLIC' &&
    ['EXIT_RESULT', 'TRANSFER_RESULT'].includes(subject.value) &&
    (!facts.value.ACCOUNT_DISPOSITION?.trim() || !facts.value.OBJECT_DISPOSITION?.trim())
  ) {
    error.value = '请分别填写账目及实物处置情况';
    return;
  }
  const m = model.value;
  const content =
    m.decision === 'PUBLIC'
      ? {
          title: m.title,
          body: m.body,
          feedbackInstructions: m.feedbackInstructions,
          imageUrls: (m.imageUrlsText || '')
            .split('\n')
            .map((s: string) => s.trim())
            .filter(Boolean),
          ...(subject.value === 'EXIT_CONSULTATION'
            ? {
                expertOpinions: expertOpinions.value.map(({ verdict, ...opinion }) => ({
                  ...opinion,
                  agree: verdict === 'AGREE',
                })),
              }
            : {}),
          facts: Object.fromEntries(Object.entries(facts.value).filter(([, value]) => value?.trim())),
        }
      : null;
  const value = cloneDeep({
    sourceRevision,
    decision: m.decision,
    reviewedAt: m.reviewedAt,
    reviewerUserId: m.reviewerUserId,
    signedReviewFileIds: m.signedReviewFileIds,
    replacementReason: m.replacementReason,
    replacementFileIds: m.replacementFileIds,
    restrictions: restrictions.value,
    content,
    publicContentFileIds: m.decision === 'PUBLIC' ? m.publicContentFileIds : null,
    withholdingReason: m.decision === 'WITHHELD' ? m.withholdingReason : null,
  });
  const token = generation,
    save = props.save;
  error.value = '';
  saving.value = true;
  try {
    const result: any = await save(org, subject.value, id, value);
    if (!active || token !== generation || !show.value || result === false) return;
    if (
      (result?.status !== undefined && result.status !== 200) ||
      typeof result?.data !== 'string' ||
      !result.data.trim()
    )
      throw Error('登记回执无法确认，请回原记录核对实际结果');
    show.value = false;
    emit('saved');
  } catch (e) {
    if (active && token === generation && show.value)
      error.value = e instanceof Error ? e.message : '登记失败，请核对原记录';
  } finally {
    if (token === generation) saving.value = false;
  }
}
defineExpose({ open, close, isOpen: () => show.value });
</script>
