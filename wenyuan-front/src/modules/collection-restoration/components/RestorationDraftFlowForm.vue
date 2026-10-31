<template>
  <div>
    <template v-if="isDraft">
      <NAlert v-if="error" type="error">{{ error }}。请关闭后重新打开表单核对。</NAlert>
      <NSpin v-else-if="loading" show description="读取当前项目与藏品资料" />
      <template v-else>
        <NAlert v-if="context" type="info" style="margin-bottom: 12px">
          {{ context.orgName }} · {{ context.collection.name }}（{{ context.collection.totalRegNo || '暂无登记号' }}）
        </NAlert>
        <CollectionFlowForm :key="epoch" ref="formRef" v-bind="props" :form-data="body"
          :auto-load-detail="false" @update:form-data="updateOwned" />
      </template>
    </template>
    <CollectionFlowForm v-else ref="formRef" v-bind="props" @update:form-data="emit('update:formData', $event)" />
  </div>
</template>

<script setup lang="ts">
import { errorText } from '../utils/restorationError';
import { computed, onActivated, onBeforeUnmount, onDeactivated, ref, watch } from 'vue';
import { NAlert, NSpin } from 'naive-ui';
import { cloneDeep } from 'lodash-es';
import CollectionFlowForm from '@/modules/collection/forms/CollectionFlowForm.vue';
import type { FlowFormProps } from '@/modules/flow/typings/flow';
import { restorationApi, type RestorationDraftContext } from '../api/restoration';
import { mergeDraftBodyWithCollectionSnapshot } from '../utils/restorationCollectionSnapshot';

const props = defineProps<FlowFormProps>();
const emit = defineEmits<{ (e: 'update:formData', value: Record<string, any>): void }>();
const isDraft = computed(() => props.mode === 'handle' && !props.task);
const formRef = ref<any>();
const body = ref<Record<string, any>>({});
const context = ref<RestorationDraftContext | null>(null);
const epoch = ref(0);
const loading = ref(false);
const error = ref('');
let active = true;
let baseline: { orgId: string; revision: string } | null = null;
const current = (version: number) => active && epoch.value === version && isDraft.value;

function readContext(response: any, id: string | undefined, collectionId?: string): RestorationDraftContext {
  if (response?.status !== undefined && response.status !== 200) throw new Error('读取修复草稿失败');
  if (response?.data?.status !== undefined && response.data.status !== 200) throw new Error('读取修复草稿失败');
  const value = response?.data?.data ?? response?.data;
  const text = (v: unknown) => typeof v === 'string' && !!v.trim();
  if (!value || (value.id ?? undefined) !== id || !text(value.orgId) || !text(value.orgName)
      || !text(value.revision) || !text(value.collectionRevision) || !text(value.collectionId)
      || (collectionId && value.collectionId !== collectionId)
      || value.collection?.id !== value.collectionId || value.collection.orgId !== value.orgId
      || (id ? value.project?.id !== id || value.project?.orgId !== value.orgId : value.project != null)) {
    throw new Error('修复草稿与所选藏品资料不一致');
  }
  if (value.canEdit !== true) throw new Error(value.issue || '当前项目不能编辑草稿');
  return value;
}

async function load(collectionId?: string, replaceBody = false) {
  const version = ++epoch.value;
  const id = props.businessKey || undefined;
  context.value = null;
  error.value = '';
  loading.value = !!(collectionId || (replaceBody && id));
  if (!loading.value) return;
  try {
    const value = readContext(await restorationApi.draftContext(collectionId, id), id, collectionId);
    if (!current(version)) return;
    if (baseline && (baseline.orgId !== value.orgId || baseline.revision !== value.revision)) {
      throw new Error('改选藏品期间，原修复项目或原藏品资料已变化');
    }
    baseline = { orgId: value.orgId, revision: value.revision };
    context.value = cloneDeep(value);
    // project 常无 collectionName/totalRegNo；必须从 context.collection 写入，供 CollectionSelect.fallbackLabel。
    const project = replaceBody && value.project ? value.project : body.value;
    body.value = mergeDraftBodyWithCollectionSnapshot(project, value.collection, {
      id: id || undefined,
      collectionId: value.collectionId,
    });
    emit('update:formData', cloneDeep(body.value));
  } catch (e: any) {
    if (current(version)) error.value = errorText(e, '读取修复草稿失败');
  } finally {
    if (current(version)) loading.value = false;
  }
}

// 每个子表单持有自己的更新回调，旧上传完成不能写入后来打开的草稿。
const updateOwned = computed(() => {
  const version = epoch.value;
  return (value: Record<string, any>) => {
    if (!current(version) || loading.value || error.value) return;
    const selected = body.value.collectionId;
    body.value = cloneDeep(value);
    emit('update:formData', cloneDeep(body.value));
    if (selected !== body.value.collectionId) void load(body.value.collectionId || undefined);
  };
});

function reset() {
  epoch.value++;
  baseline = null;
  context.value = null;
  error.value = '';
  loading.value = false;
  body.value = cloneDeep(props.formData || {});
  if (active && isDraft.value) void load(props.businessKey ? undefined : body.value.collectionId, true);
}
watch(() => [props.businessKey, props.mode, props.task?.id] as const, reset, { immediate: true });
onDeactivated(() => { active = false; epoch.value++; context.value = null; body.value = {}; });
onBeforeUnmount(() => { active = false; epoch.value++; context.value = null; body.value = {}; });
onActivated(() => { if (!active) { active = true; reset(); } });

async function validate(options?: { submit?: boolean }) {
  if (!isDraft.value) return formRef.value?.validate(options);
  const version = epoch.value;
  const fixed = context.value;
  if (!current(version) || loading.value || error.value || !fixed || !formRef.value) {
    if (current(version) && !loading.value && !error.value && formRef.value) await formRef.value.validate(options);
    throw new Error(error.value || '请先选择藏品并读取当前资料');
  }
  const value = await formRef.value.validate(options);
  if (!current(version) || fixed !== context.value || value.collectionId !== fixed.collectionId) throw new Error('表单已变化，请重新核对');
  return { ...cloneDeep(value), id: fixed.id || undefined, __draftConfirmation: {
    id: fixed.id || undefined, collectionId: fixed.collectionId, expectedOrgId: fixed.orgId,
    expectedRevision: fixed.revision, expectedCollectionRevision: fixed.collectionRevision,
  } };
}
defineExpose({ validate });
</script>
