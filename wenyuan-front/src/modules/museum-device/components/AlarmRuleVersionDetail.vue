<template>
  <HPageForm
    :show="show"
    title="规则版本详情"
    content-only
    cancel-text="关闭"
    :footer-style="{ paddingRight: '36px' }"
    @update:show="emit('update:show', $event)"
  >
    <NAlert v-if="error" type="error">{{ error }} <NButton text type="primary" @click="load">重试</NButton></NAlert>
    <NSpin :show="loading">
      <template v-if="detail">
        <NDescriptions :column="2" bordered label-placement="left">
          <NDescriptionsItem label="规则" :span="2">{{ detail.ruleName }} · V{{ detail.versionNo }}</NDescriptionsItem>
          <NDescriptionsItem label="规则编码">{{ detail.ruleCode }}</NDescriptionsItem>
          <NDescriptionsItem label="版本状态"
            ><HDictText type="museum_alarm_rule_version_status" :value="detail.versionStatus"
          /></NDescriptionsItem>
          <NDescriptionsItem label="维护来源">{{
            detail.managedBy === 'COLLECTION_PRESERVATION' ? '预防性保护方案' : '人工采集规则'
          }}</NDescriptionsItem>
          <NDescriptionsItem label="规则类型"
            ><HDictText type="museum_alarm_rule_type" :value="detail.ruleType"
          /></NDescriptionsItem>
          <NDescriptionsItem label="生效时间">{{ formatDateTime(detail.effectiveFrom) }}</NDescriptionsItem>
          <NDescriptionsItem label="失效时间">{{
            detail.effectiveTo ? formatDateTime(detail.effectiveTo) : '--'
          }}</NDescriptionsItem>
          <NDescriptionsItem label="连续触发">{{ detail.triggerCount }} 次</NDescriptionsItem>
          <NDescriptionsItem label="连续恢复">{{ detail.recoveryCount }} 次</NDescriptionsItem>
          <NDescriptionsItem label="历史回灌参与">{{ detail.includeBackfill ? '是' : '否' }}</NDescriptionsItem>
          <NDescriptionsItem label="依据标准" :span="2"
            ><div class="long-text">{{ detail.standardRef || '--' }}</div></NDescriptionsItem
          >
          <NDescriptionsItem v-if="detail.messageTemplate" label="消息模板" :span="2"
            ><div class="long-text">{{ detail.messageTemplate }}</div></NDescriptionsItem
          >
        </NDescriptions>
        <NText depth="3">以下条件属于所选版本。任一分支满足即触发，同一分支内的条件须全部满足。</NText>
        <NCard
          v-for="(variant, index) in detail.variants"
          :key="variant.id || index"
          size="small"
          :title="variant.name || variant.code || `适用分组 ${index + 1}`"
          class="version-section"
        >
          <div v-for="(branch, branchIndex) in variant.branches" :key="branch.id || branchIndex" class="branch">
            <NSpace align="center"
              ><strong>{{ branch.name || branch.code || `分支 ${branchIndex + 1}` }}</strong>
              <HDictText type="museum_alarm_severity" :value="branch.severity" /><NTag
                v-if="branch.id === branchId"
                size="small"
                type="info"
                >本事件命中分支</NTag
              ></NSpace
            >
            <div
              v-for="(condition, conditionIndex) in branch.conditions"
              :key="condition.id || conditionIndex"
              class="long-text"
            >
              {{ conditionText(condition, detail.ruleType) }}
            </div>
          </div>
        </NCard>
        <NEmpty v-if="!detail.variants.length" description="本版本没有条件分支" />
      </template>
    </NSpin>
  </HPageForm>
</template>
<script setup lang="ts">
import { onBeforeUnmount, onDeactivated, ref, watch } from 'vue';
import { ingestApi } from '../api/ingest';
import type { AlarmRuleVersionDetailVo } from '../typings';
import { formatDateTime } from '@/utils';
import { alarmConditionText as conditionText } from '../utils/alarmRulePresentation';
const props = defineProps<{
  show: boolean;
  versionId: string;
  ruleId?: string | null;
  orgId?: string | null;
  branchId?: string | null;
}>();
const emit = defineEmits(['update:show']);
const detail = ref<AlarmRuleVersionDetailVo | null>(null),
  loading = ref(false),
  error = ref('');
let generation = 0;
async function load() {
  if (!props.show || !props.versionId) return;
  const token = ++generation,
    id = props.versionId,
    ruleId = props.ruleId,
    orgId = props.orgId;
  detail.value = null;
  error.value = '';
  loading.value = true;
  try {
    const response: any = await ingestApi.alarmRuleVersion(id);
    if (token !== generation) return;
    const value = response?.data?.data ?? response?.data ?? response;
    if (value?.id !== id || (ruleId && value?.ruleId !== ruleId) || (orgId && value?.orgId !== orgId))
      throw new Error('规则版本与当前事件关联不一致，请重新读取');
    detail.value = value;
  } catch (cause: any) {
    if (token === generation) error.value = cause?.message || '规则版本读取失败';
  } finally {
    if (token === generation) loading.value = false;
  }
}
watch(
  () => [props.show, props.versionId, props.ruleId, props.orgId],
  () => {
    ++generation;
    detail.value = null;
    error.value = '';
    loading.value = false;
    if (props.show && props.versionId) void load();
  },
  { immediate: true },
);
onDeactivated(() => {
  ++generation;
  detail.value = null;
  loading.value = false;
  error.value = '';
  emit('update:show', false);
});
onBeforeUnmount(() => {
  ++generation;
});
</script>
<style scoped>
.version-section {
  margin-top: 16px;
}
.branch + .branch {
  margin-top: 14px;
}
.long-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
