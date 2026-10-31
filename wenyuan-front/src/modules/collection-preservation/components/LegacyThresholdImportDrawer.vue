<template>
  <NDrawer v-model:show="show" :width="'90%'">
    <NDrawerContent title="旧阈值原资料与保护方案转换" closable>
      <NSpace vertical>
        <CollectionArchiveIssues :data="data" />
        <NAlert type="info" :bordered="false"
          >读取本馆旧阈值和共享材质原资料，先保留快照并创建未启用草稿。逐项核对候选规则与实际单位、对象及范围后，办理共同评估和参与人员本人签署。</NAlert
        >
        <NButton :loading="loading" :disabled="creating" @click="reload">重新读取本馆原资料</NButton>
        <NAlert v-if="error" type="error">{{ error }}</NAlert>
        <template v-if="data && !loading">
          <NAlert v-if="data.sourceError" type="error">{{ data.sourceError }}</NAlert>
          <LegacyThresholdSource :source="data.source" :conversion="data.conversion" />
          <NAlert v-if="data.canCreate !== true" type="info">当前仅可查阅原资料，不能在本馆创建转换草稿。</NAlert>
          <NInput
            v-model:value="name"
            placeholder="本次保护方案名称"
            :maxlength="100"
            :disabled="creating || data.canCreate !== true"
          />
          <NButton type="primary" :disabled="!canCreate" :loading="creating" @click="create"
            >保留原资料并创建草稿</NButton
          >
          <NCard v-for="record in data.imports" :key="record.id" size="small" :title="record.name">
            <NSpace vertical>
              <NText>{{ stateLabels[record.state] || record.state }}</NText>
              <NText v-if="!record.sourceMatches" type="warning"
                >当前旧资料已变化，本次原快照继续保留，不能视为已覆盖现有资料。</NText
              >
              <NButton @click="openPolicy(record.policyId)">核对方案与共同评估</NButton>
              <NCollapse
                ><NCollapseItem title="查看本次永久保留的原资料及去向">
                  <LegacyThresholdSource
                    :source="record.original.source"
                    :conversion="record.original.conversion"
                  /> </NCollapseItem
              ></NCollapse>
            </NSpace>
          </NCard>
          <NText depth="3"
            >发布状态只表示这一份原快照已随实际评估发布；各馆全量覆盖、旧预警与报告历史核对及部署扫描的整体结论仍需分别核实。</NText
          >
        </template>
      </NSpace>
    </NDrawerContent>
  </NDrawer>
</template>
<script setup lang="ts">
import CollectionArchiveIssues from '@/modules/collection/components/CollectionArchiveIssues.vue';
import { computed, onBeforeUnmount, onActivated, onDeactivated, ref, watch } from 'vue';
import { useMessage } from 'naive-ui';
import { legacyThresholdApi } from '../api/legacyThreshold';
import LegacyThresholdSource from './LegacyThresholdSource.vue';
const emit = defineEmits<{ saved: []; 'open-policy': [id: string] }>();
const show = ref(false),
  loading = ref(false),
  creating = ref(false);
const data = ref<any>(null),
  error = ref(''),
  name = ref('旧阈值转换核对方案');
const message = useMessage();
let generation = 0;
let disposed = false;
const active = ref(true);
const isCurrent = (current: number) => !disposed && active.value && show.value && current === generation;
const stateLabels: Record<string, string> = {
  CURRENT_SOURCE_UNAVAILABLE: '当前旧资料不可读取，原快照继续保留',
  SOURCE_CHANGED: '原资料已变化，需重新核对',
  PENDING_ACTIVATION: '草稿或暂停状态，尚未启用',
  MISSING_ASSESSMENT: '缺少本版共同评估',
  ASSESSMENT_CHANGED: '方案或评估待核查',
  PUBLISHED_WITH_SOURCE_EVIDENCE: '本次原快照已纳入共同评估并发布',
};
const canCreate = computed(
  () =>
    active.value &&
    !disposed &&
    show.value &&
    data.value?.canCreate === true &&
    !!data.value?.sourceHash &&
    !loading.value &&
    !creating.value &&
    !error.value &&
    !!name.value.trim() &&
    !data.value.conversion.issues.length &&
    !!data.value.conversion.items.length &&
    !data.value.imports.some((r: any) => r.sourceMatches),
);
async function reload() {
  if (!show.value || disposed || !active.value || creating.value) return;
  const current = ++generation;
  loading.value = true;
  data.value = null;
  error.value = '';
  try {
    const result = await legacyThresholdApi.preview();
    if (!isCurrent(current)) return;
    const value = result.data;
    if (
      !value?.orgId ||
      !Array.isArray(value.imports) ||
      (value.sourceHash && (!Array.isArray(value.conversion?.items) || !Array.isArray(value.conversion?.issues)))
    )
      throw new Error('旧阈值原资料不完整，请重新读取');
    data.value = value;
  } catch (e: any) {
    if (isCurrent(current)) error.value = e?.message || '读取原资料失败，请处理后重新读取';
  } finally {
    if (isCurrent(current)) loading.value = false;
  }
}
async function create() {
  if (!canCreate.value) return;
  const payload = { sourceHash: data.value.sourceHash, name: name.value.trim() };
  const current = generation;
  creating.value = true;
  try {
    await legacyThresholdApi.create(payload);
    if (!isCurrent(current)) return;
    message.success('原资料已保留，已创建未启用草稿');
    emit('saved');
    if (!isCurrent(current)) return;
    creating.value = false;
    await reload();
  } catch (e: any) {
    if (isCurrent(current)) {
      data.value = null;
      error.value = e?.message || '创建失败，请重新核对原资料';
    }
  } finally {
    if (isCurrent(current)) creating.value = false;
  }
}
function openPolicy(id: string) {
  if (
    !isCurrent(generation) ||
    loading.value ||
    creating.value ||
    !data.value?.imports?.some((r: any) => r.policyId === id)
  )
    return;
  close();
  emit('open-policy', id);
}
async function open() {
  if (disposed || !active.value) return;
  creating.value = false;
  show.value = true;
  name.value = '旧阈值转换核对方案';
  await reload();
}
function invalidate() {
  generation++;
  data.value = null;
  error.value = '';
  loading.value = false;
  creating.value = false;
}
watch(
  show,
  (value) => {
    if (!value) invalidate();
  },
  { flush: 'sync' },
);
function close() {
  show.value = false;
  invalidate();
  name.value = '旧阈值转换核对方案';
}
onDeactivated(() => {
  active.value = false;
  close();
});
onActivated(() => {
  if (!disposed) active.value = true;
});
onBeforeUnmount(() => {
  disposed = true;
  active.value = false;
  close();
});
defineExpose({ open, close });
</script>
