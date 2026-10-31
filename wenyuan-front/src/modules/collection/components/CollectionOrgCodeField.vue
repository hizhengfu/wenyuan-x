<template>
  <NFormItem ref="field" path="code" :show-label="!readonly" :show-feedback="!readonly" :feedback="feedback">
    <template #label>
      <CollectionFieldLabel label="收藏单位代码" help="用于本系统识别收藏单位，与规范交换代码、一普编码分别管理。尚未使用的单位可直接修改；已有业务记录须办理留痕更正。历史代码继续归属本单位，不可供其他单位使用。" />
    </template>
    <div class="org-code-field">
      <span v-if="readonly" class="org-code-value">{{ data?.currentCode || value || '--' }}</span>
      <NInput v-else :value="value" :readonly="locked" :maxlength="32" placeholder="请输入收藏单位代码"
        @update:value="updateValue" />
      <NSpace v-if="orgId" :size="8" align="center">
        <NText v-if="session.busy.value" depth="3">{{ session.operation.value === 'load' ? '正在核对代码…' : '正在保存…' }}</NText>
        <NButton v-if="!readonly && data?.canEdit && data.used" text type="primary" :disabled="session.busy.value" @click="openCorrection">留痕更正</NButton>
        <NButton v-if="data" text :disabled="session.busy.value" @click="historyShow = true">修改记录（{{ data.history.length }}）</NButton>
        <NButton v-if="error" text type="primary" :disabled="session.busy.value" @click="load">重新加载</NButton>
      </NSpace>
      <NText v-if="error" type="error" role="alert">{{ error }}</NText>
    </div>
  </NFormItem>

  <NModal :show="correctionShow" preset="card" title="收藏单位代码留痕更正"
    style="width: min(480px, calc(100vw - 32px)); max-height: calc(100dvh - 32px)"
    :content-style="{ minHeight: '0', overflow: 'auto' }"
    :mask-closable="false" :close-on-esc="!session.busy.value" :closable="!session.busy.value"
    @update:show="correctionShow = $event">
    <div class="org-code-modal-body">
      <NForm label-placement="top">
        <NFormItem label="当前代码"><NText>{{ data?.currentCode || value }}</NText></NFormItem>
        <NFormItem label="新代码" required :validation-status="codeError ? 'error' : undefined" :feedback="codeError || undefined">
          <NInput v-model:value="newCode" :maxlength="32" :disabled="session.busy.value" placeholder="填写核实后的收藏单位代码" />
        </NFormItem>
        <NFormItem label="更正原因" required :validation-status="reasonError ? 'error' : undefined" :feedback="reasonError || '保存时记录操作人、时间和更正前后的代码'">
          <NInput v-model:value="reason" type="textarea" :maxlength="500" show-count :autosize="{ minRows: 3, maxRows: 6 }"
            :disabled="session.busy.value" placeholder="说明原代码错误及核实依据" />
        </NFormItem>
      </NForm>
      <NText depth="3">此操作立即保存代码更正，单位资料表单中的其他修改仍需另行保存。</NText>
      <NText v-if="error" type="error" role="alert" class="org-code-error">{{ error }}</NText>
    </div>
    <template #footer>
      <NSpace justify="end">
        <NButton :disabled="session.busy.value" @click="correctionShow = false">取消</NButton>
        <NButton v-if="!data" :disabled="session.busy.value" @click="load">重新加载</NButton>
        <NButton type="primary" :loading="session.operation.value === 'correct'" :disabled="!data?.canEdit || session.busy.value" @click="save">确认更正</NButton>
      </NSpace>
    </template>
  </NModal>

  <NModal v-model:show="historyShow" preset="card" title="收藏单位代码修改记录"
    style="width: min(580px, calc(100vw - 32px)); max-height: calc(100dvh - 32px)"
    :content-style="{ minHeight: '0', overflow: 'auto' }">
    <div class="org-code-modal-body">
      <NEmpty v-if="!data?.history.length" description="暂无代码修改记录" />
      <NTimeline v-else>
        <NTimelineItem v-for="row in data.history" :key="row.id" :title="row.correction ? '留痕更正' : '使用前修改'"
          :time="dayjs(row.operatedAt).format('YYYY-MM-DD HH:mm:ss')">
          <div class="org-code-transition"><span>{{ row.oldCode }}</span><span aria-label="变更为">→</span><strong>{{ row.newCode }}</strong></div>
          <p class="org-code-reason">{{ row.reason }}</p>
          <NText depth="3">操作人：<UserNameText :id="row.operatorId" /></NText>
        </NTimelineItem>
      </NTimeline>
    </div>
    <template #footer><NButton @click="historyShow = false">关闭</NButton></template>
  </NModal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { NButton, NEmpty, NForm, NFormItem, NInput, NModal, NSpace, NText, NTimeline, NTimelineItem, useMessage, type FormItemInst } from 'naive-ui';
import dayjs from 'dayjs';
import { useAuthStore } from '@/store/modules/auth';
import { orgApi } from '../api/org';
import { useCatalogOperation } from '../composables/useCatalogOperation';
import { readOrgCodeContext, type OrgCodeContext } from '../utils/orgCode';
import CollectionFieldLabel from './CollectionFieldLabel.vue';
import UserNameText from './UserNameText.vue';
const props = defineProps<{ orgId?: string; value?: string; active: boolean; readonly?: boolean }>();
const emit = defineEmits<{
  'update:value': [value: string];
  'update:stateHash': [value: string | undefined];
  changed: [value: OrgCodeContext];
}>();
const auth = useAuthStore();
const message = useMessage();
const data = ref<OrgCodeContext | null>(null);
const error = ref('');
const correctionShow = ref(false);
const historyShow = ref(false);
const newCode = ref('');
const reason = ref('');
const codeError = ref('');
const reasonError = ref('');
const session = useCatalogOperation(() => {
  data.value = null; error.value = ''; correctionShow.value = false; historyShow.value = false;
  newCode.value = ''; reason.value = ''; codeError.value = ''; reasonError.value = '';
  emit('update:stateHash', undefined);
});
const locked = computed(() => !!props.readonly || session.busy.value || !!props.orgId && (!data.value?.canEdit || data.value.used));
// Short status uses feedback; the full policy is in the label tooltip. NFormItem validation takes priority.
const field = ref<(FormItemInst & { mergedValidationStatus?: string }) | null>(null);
const feedback = computed(() => !['error', 'warning'].includes(field.value?.mergedValidationStatus || '') && data.value?.used ? '已有业务记录，请办理留痕更正' : undefined);
function updateValue(value: string) { if (!locked.value) emit('update:value', value); }
function accept(value: OrgCodeContext) {
  data.value = value; emit('update:value', value.currentCode); emit('update:stateHash', value.stateHash);
}
async function load() {
  if (!props.orgId || !props.active || auth.identitySyncing) return;
  const ticket = session.begin('load'); if (ticket === null) return;
  const id = props.orgId;
  data.value = null; error.value = ''; emit('update:stateHash', undefined);
  try {
    const response = await orgApi.codeContext(id);
    if (session.current(ticket)) accept(readOrgCodeContext(response, id));
  } catch (e) {
    if (session.current(ticket)) error.value = e instanceof Error ? e.message : '代码状态加载失败，请重试';
  } finally { session.finish(ticket); }
}
function openCorrection() {
  if (!data.value?.canEdit || props.readonly || session.busy.value) return;
  newCode.value = ''; reason.value = ''; codeError.value = ''; reasonError.value = ''; error.value = ''; correctionShow.value = true;
}
async function save() {
  if (!correctionShow.value || !data.value?.canEdit || props.readonly || !props.active || session.busy.value) return;
  const code = newCode.value.trim(); const why = reason.value.trim();
  codeError.value = !code || code.length > 32 || /[\u0000-\u001f\u007f-\u009f]/.test(code) ? '请填写不超过32个字符的有效代码' : code === data.value.currentCode ? '新代码与当前代码相同' : '';
  reasonError.value = !why || why.length > 500 ? '请填写更正原因，且不超过500字' : '';
  if (codeError.value || reasonError.value) return;
  const id = props.orgId!; const hash = data.value.stateHash;
  const ticket = session.begin('correct'); if (ticket === null) return;
  error.value = '';
  try {
    const response = await orgApi.correctCode(id, { newCode: code, reason: why }, hash);
    if (!session.current(ticket)) return;
    const saved = readOrgCodeContext(response, id);
    if (saved.currentCode !== code || saved.stateHash === hash) throw new Error('无法确认代码更正结果，请重新加载核对');
    accept(saved); correctionShow.value = false; emit('changed', saved); message.success('收藏单位代码已留痕更正');
  } catch (e) {
    if (session.current(ticket)) {
      data.value = null; emit('update:stateHash', undefined);
      error.value = e instanceof Error ? e.message : '代码更正未完成，请重新加载核对';
    }
  } finally { session.finish(ticket); }
}
watch(() => [props.orgId, props.active, auth.userInfo?.id, auth.identityRevision, auth.identitySyncing, session.active.value], () => {
  session.reset(); if (props.active && session.active.value && !auth.identitySyncing) void load();
}, { immediate: true });
</script>

<style scoped>
.org-code-field { display: flex; flex-direction: column; gap: 6px; width: 100%; min-width: 0; }
.org-code-value, .org-code-reason { overflow-wrap: anywhere; white-space: pre-wrap; }
.org-code-transition { display: flex; gap: 10px; flex-wrap: wrap; overflow-wrap: anywhere; }
.org-code-reason { margin: 6px 0; }
.org-code-error { display: block; margin-top: 12px; }
.org-code-modal-body { min-height: 0; }
</style>
