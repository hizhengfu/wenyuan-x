<template>
  <div class="verification-panel">
    <NSpin :show="session.busy.value && !data">
      <NAlert v-if="error" type="error" title="登记核验资料加载失败"><span>{{ error }}</span><NButton text @click="load">重试</NButton></NAlert>
      <NSpace v-else-if="data" align="center" justify="space-between">
        <div>
          <NTag :type="data.source.verification === 'CONFIRMED' ? 'success' : 'warning'">{{ data.record ? verificationStatuses[data.record.status] : data.source.verification === 'CONFIRMED' ? '原登记已核实' : '登记依据待核实' }}</NTag>
          <NText depth="3" class="status-note">{{ data.blockReason || '补充原登记材料，由另一名审核人员复核后恢复原登记事实。' }}</NText>
        </div>
        <NButton :disabled="session.busy.value" type="primary" secondary @click="open">{{ data.canReview ? '复核登记依据' : data.canEdit ? '补充登记依据' : '查看核验记录' }}</NButton>
      </NSpace>
    </NSpin>
    <NModal v-model:show="visible" preset="card" title="历史迁入登记核验" style="width: min(880px, calc(100vw - 32px)); max-height: calc(100dvh - 32px)" :mask-closable="!session.busy.value" :closable="!session.busy.value">
      <div v-if="data" class="verification-body">
        <NAlert type="info" :show-icon="false">通过复核后确认原有登记事实。原登记日期、登记人或分类号确实无法查明时，保留空值及缺项说明；编目可另行完善，不重新登账。</NAlert>
        <NDescriptions bordered :column="2" size="small">
          <NDescriptionsItem label="总登记号">{{ data.totalRegNo }}</NDescriptionsItem>
          <NDescriptionsItem label="来源系统">{{ data.source.sourceSystem }}</NDescriptionsItem>
          <NDescriptionsItem label="提交人"><UserNameText v-if="data.record?.submittedBy" :id="data.record.submittedBy" /><span v-else>尚未提交</span></NDescriptionsItem>
          <NDescriptionsItem label="提交时间">{{ timestamp(data.record?.submittedAt) }}</NDescriptionsItem>
        </NDescriptions>
        <NAlert v-if="data.missingFields?.length" type="warning" :show-icon="false">资料待完善：{{ data.missingFields.join('、') }}。缺项说明与原账依据随复核记录留存。</NAlert>
        <NAlert v-if="data.record?.opinion" :type="data.record.status === 'APPROVED' ? 'success' : 'warning'" title="上次复核意见">{{ data.record.opinion }}</NAlert>
        <NForm ref="formRef" :model="form" :rules="rules" label-placement="top" :disabled="session.busy.value || !data.canEdit">
          <div class="verification-grid">
            <NFormItem label="原登记日期" path="originalRegisteredAt"><NDatePicker v-model:value="form.originalRegisteredAt" type="datetime" clearable style="width: 100%" /></NFormItem>
            <NFormItem label="原登记人" path="originalRegisteredBy"><NInput v-model:value="form.originalRegisteredBy" maxlength="100" placeholder="填写原账记载的登记人" /></NFormItem>
          </div>
          <NFormItem v-if="form.originalRegisteredAt == null" label="原登记日期缺项说明" path="originalDateMissingReason">
            <template #label><CollectionFieldLabel label="原登记日期缺项说明" help="写明原账未载或暂无法查明的情况、已查阅材料及位置。不得用取得日期、迁移时间或估计日期代替。" /></template>
            <NInput v-model:value="form.originalDateMissingReason" type="textarea" :rows="2" maxlength="500" />
          </NFormItem>
          <NFormItem v-if="!form.originalRegisteredBy?.trim()" label="原登记人缺项说明" path="originalRegistrarMissingReason"><NInput v-model:value="form.originalRegistrarMissingReason" type="textarea" :rows="2" maxlength="500" placeholder="说明原账未载或查考情况，不得以当前操作人代替" /></NFormItem>
          <NFormItem v-if="data.missingFields?.includes('分类号')" label="分类号缺项说明" path="classificationMissingReason"><NInput v-model:value="form.classificationMissingReason" type="textarea" :rows="2" maxlength="500" placeholder="说明原账未编分类号或查考情况；不得以文物类别、总登记号代填" /></NFormItem>
          <NFormItem label="原账或登记依据" path="registrationEvidence">
            <HUploadInput v-if="data.canEdit" v-model:value="form.registrationEvidence" clearable />
            <HFileList v-else-if="form.registrationEvidence" :value="form.registrationEvidence" show-download-btn />
            <NText v-else depth="3">未关联依据文件</NText>
          </NFormItem>
          <NFormItem label="核验说明" path="reason"><NInput v-model:value="form.reason" type="textarea" :rows="3" maxlength="500" show-count placeholder="说明原账位置、核对内容及材料依据" /></NFormItem>
          <NFormItem label="历史增减核对"><template #label><CollectionFieldLabel label="历史增减核对" help="仅当原登记以来的馆藏归属、数量及单位均未变化，且已经核对原账时选是。发生过变化或材料不足时选否，保留年度统计的待核实提示。" /></template><NSwitch v-model:value="form.historyVerified"><template #checked>已核对且未变化</template><template #unchecked>待核实或有变化</template></NSwitch></NFormItem>
          <NFormItem label="原账一致性" path="matchesOriginalLedger"><template #label><CollectionFieldLabel label="原账一致性" help="核对本件身份、所属馆、总登记号、藏品性质、等级、数量及单位。性质和等级以真实鉴定、行政及原账材料为依据；发现主档差异时先核实处理，不能用确认勾选代替订正。" /></template><NCheckbox v-model:checked="form.matchesOriginalLedger">已核对本件与原账记载一致</NCheckbox></NFormItem>
        </NForm>
        <NCollapse v-if="data.record">
          <NCollapseItem title="本次材料对应的藏品快照" name="snapshot"><CatalogDetail :catalog="snapshotCatalog(data.record)" historical /></NCollapseItem>
        </NCollapse>
        <NForm v-if="data.canReview" label-placement="top">
          <NFormItem label="复核意见" required><NInput v-model:value="opinion" type="textarea" :rows="3" maxlength="500" :disabled="session.busy.value" placeholder="写明核对的原账依据、结论或需要补充的问题" /></NFormItem>
          <NCheckbox v-model:checked="reviewConfirmed" :disabled="session.busy.value">已查阅原件并核对提交快照，确认恢复原登记事实</NCheckbox>
        </NForm>
        <NCollapse v-if="data.history.length">
          <NCollapseItem title="历次核验记录" name="history">
            <NTimeline>
              <NTimelineItem v-for="record in data.history" :key="record.id" :title="verificationStatuses[record.status]" :time="timestamp(record.recordedAt)">
                <div>操作人：<UserNameText :id="record.recordedBy" /></div>
                <div>原登记日期：{{ timestamp(record.content.originalRegisteredAt) }} · 原登记人：{{ record.content.originalRegisteredBy || '原账未载／待考' }}</div>
                <div v-if="record.content.originalDateMissingReason">日期缺项：{{ record.content.originalDateMissingReason }}</div>
                <div v-if="record.content.originalRegistrarMissingReason">登记人缺项：{{ record.content.originalRegistrarMissingReason }}</div>
                <div v-if="record.content.classificationMissingReason">分类号缺项：{{ record.content.classificationMissingReason }}</div>
                <div class="record-note">{{ record.opinion || record.content.reason || '材料草稿' }}</div>
                <HFileList v-if="record.content.registrationEvidence" :value="record.content.registrationEvidence" show-download-btn />
              </NTimelineItem>
            </NTimeline>
          </NCollapseItem>
        </NCollapse>
      </div>
      <template #footer><NSpace justify="end">
        <NText v-if="session.busy.value" depth="3" role="status">{{ session.operation.value === 'load' ? '正在加载…' : '正在办理，请稍候…' }}</NText>
        <NButton :disabled="session.busy.value" @click="visible = false">关闭</NButton>
        <NButton :disabled="session.busy.value" @click="load">重新加载</NButton>
        <NButton v-if="data?.canWithdraw" :disabled="session.busy.value" @click="withdraw">撤回补充</NButton>
        <NButton v-if="data?.canEdit" :disabled="session.busy.value" @click="save(false)">保存草稿</NButton>
        <NButton v-if="data?.canEdit" type="primary" :disabled="session.busy.value" @click="save(true)">提交复核</NButton>
        <NButton v-if="data?.canReview" type="warning" :disabled="session.busy.value || !opinion.trim()" @click="review(false)">退回补充</NButton>
        <NButton v-if="data?.canReview" type="primary" :disabled="session.busy.value || !opinion.trim() || !reviewConfirmed" @click="review(true)">复核通过</NButton>
      </NSpace></template>
    </NModal>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import dayjs from 'dayjs';
import { useMessage } from 'naive-ui';
import { useAuthStore } from '@/store/modules/auth';
import { collectionApi } from '../api/collection';
import { useCatalogOperation } from '../composables/useCatalogOperation';
import { readMigrationVerification, verificationForm, verificationStatuses } from '../utils/migrationVerification';
import type { MigrationVerification, MigrationVerificationContent, MigrationVerificationRecord } from '../utils/migrationVerification';
import type { CollectionCatalogVo } from '../typings/collection';
import CollectionFieldLabel from './CollectionFieldLabel.vue';
import UserNameText from './UserNameText.vue';
import CatalogDetail from './CatalogDetail.vue';
const props = defineProps<{ collectionId: string; active: boolean }>();
const emit = defineEmits<{ changed: [id: string] }>();
const auth = useAuthStore();
const message = useMessage();
const data = ref<MigrationVerification | null>(null), visible = ref(false), error = ref(''), opinion = ref(''), reviewConfirmed = ref(false);
const formRef = ref<any>();
const form = ref<MigrationVerificationContent>({ originalRegisteredAt: null, originalRegisteredBy: '', registrationEvidence: null, reason: '', historyVerified: false, matchesOriginalLedger: false });
const session = useCatalogOperation(() => { data.value = null; visible.value = false; error.value = ''; });
const rules = computed(() => ({
  originalDateMissingReason: { required: form.value.originalRegisteredAt == null, message: '请说明原登记日期缺项及查考依据', trigger: 'blur' },
  originalRegistrarMissingReason: { required: !form.value.originalRegisteredBy?.trim(), message: '请说明原登记人缺项及查考依据', trigger: 'blur' },
  classificationMissingReason: { required: data.value?.missingFields?.includes('分类号'), message: '请说明分类号缺项及查考依据', trigger: 'blur' },
  registrationEvidence: { required: true, message: '请上传原账或登记依据', trigger: 'change' },
  reason: { required: true, message: '请填写核验说明', trigger: 'blur' },
  matchesOriginalLedger: { validator: (_: unknown, value: boolean) => value || new Error('请先核对原账一致性'), trigger: 'change' },
}));
watch(() => form.value.originalRegisteredAt, value => { if (value != null) form.value.originalDateMissingReason = ''; });
watch(() => form.value.originalRegisteredBy, value => { if (value?.trim()) form.value.originalRegistrarMissingReason = ''; });
// New API dates are epoch milliseconds, including dates before 1970.
const timestamp = (value?: number | null) => value == null ? '--' : dayjs(value).format('YYYY-MM-DD HH:mm:ss');
function snapshotCatalog(record: MigrationVerificationRecord): CollectionCatalogVo {
  return { collectionId: props.collectionId, totalRegNo: data.value?.totalRegNo, content: record.collectionSnapshot,
    completeness: { percent: 0, complete: false, missingFields: [] } };
}
function apply(response: unknown, id: string) {
  data.value = readMigrationVerification(response, id); form.value = verificationForm(data.value);
  opinion.value = ''; reviewConfirmed.value = false;
}
async function load() {
  if (!props.active || auth.identitySyncing) return;
  const ticket = session.begin('load'); if (ticket === null) return;
  const id = props.collectionId; error.value = '';
  try { const response = await collectionApi.migrationVerification(id); if (session.current(ticket)) apply(response, id); }
  catch (e: any) { if (session.current(ticket)) error.value = e?.message || '请重试'; }
  finally { session.finish(ticket); }
}
function open() { if (!data.value) return; form.value = verificationForm(data.value); opinion.value = ''; reviewConfirmed.value = false; visible.value = true; }
async function mutate(kind: string, action: (id: string, hash: string) => Promise<unknown>, success: string) {
  if (!props.active || !data.value) return;
  const ticket = session.begin(kind); if (ticket === null) return;
  const id = props.collectionId, hash = data.value.stateHash;
  try { const result = await action(id, hash); if (!session.current(ticket)) return; apply(result, id); message.success(success); emit('changed', id); }
  catch (e: any) { if (session.current(ticket)) { message.error(e?.message || '操作失败，请重试'); } }
  finally { session.finish(ticket); }
}
async function save(submit: boolean) {
  if (!data.value?.canEdit || session.busy.value) return;
  const id = props.collectionId, hash = data.value.stateHash;
  if (submit) { try { await formRef.value?.validate(); } catch { return; } }
  if (!props.active || id !== props.collectionId || hash !== data.value?.stateHash) return;
  const content = { ...form.value };
  await mutate('save', (id, expected) => collectionApi.saveMigrationVerification(id, content, expected, submit), submit ? '已提交，请由另一名审核人员复核' : '材料草稿已保存');
}
async function withdraw() { if (data.value?.canWithdraw) await mutate('withdraw', (id, hash) => collectionApi.withdrawMigrationVerification(id, hash), '已撤回，可继续补充材料'); }
async function review(approved: boolean) {
  if (!data.value?.canReview || !opinion.value.trim() || (approved && !reviewConfirmed.value)) return;
  const payload = { approved, opinion: opinion.value.trim() };
  await mutate('review', (id, hash) => collectionApi.reviewMigrationVerification(id, payload, hash), approved ? '原登记已核实，可继续编目补录' : '已退回补充材料');
}
watch(() => [props.collectionId, props.active, auth.userInfo?.id, auth.identityRevision, auth.identitySyncing, session.active.value], () => {
  session.reset(); if (props.active && session.active.value && !auth.identitySyncing) void load();
}, { immediate: true, flush: 'sync' });
</script>
<style scoped>
.verification-panel { margin-bottom: 16px; }
.status-note { display: inline-block; margin-left: 10px; }
.verification-modal { width: min(960px, calc(100vw - 32px)); max-height: calc(100dvh - 32px); }
.verification-body { display: grid; gap: 16px; max-height: calc(100dvh - 200px); overflow: auto; padding-right: 8px; }
.verification-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.record-note { white-space: pre-wrap; overflow-wrap: anywhere; }
@media (max-width: 640px) { .verification-grid { grid-template-columns: 1fr; gap: 0; } .status-note { display: block; margin: 8px 0; } }
</style>
