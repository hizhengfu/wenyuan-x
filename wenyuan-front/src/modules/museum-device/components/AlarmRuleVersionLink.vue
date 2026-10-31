<template>
  <template v-if="versionId">
    <NButton text type="primary" :title="versionId" @click="visible = true">{{ label }}</NButton>
    <AlarmRuleVersionDetail
      v-model:show="visible"
      :version-id="versionId"
      :rule-id="ruleId"
      :org-id="orgId"
      :branch-id="branchId"
    />
  </template>
  <span v-else>{{ placeholder }}</span>
</template>
<script setup lang="ts">
import { onDeactivated, computed, ref, watch } from 'vue';
import AlarmRuleVersionDetail from './AlarmRuleVersionDetail.vue';
const props = withDefaults(
  defineProps<{
    versionId?: string | null;
    ruleId?: string | null;
    orgId?: string | null;
    branchId?: string | null;
    ruleName?: string | null;
    versionNo?: number | null;
    placeholder?: string;
  }>(),
  { placeholder: '--' },
);
const visible = ref(false);
const label = computed(() =>
  props.ruleName
    ? `${props.ruleName}${props.versionNo != null ? ` · V${props.versionNo}` : ''}`
    : props.versionNo != null
      ? `V${props.versionNo}`
      : '查看规则版本',
);
watch(
  () => [props.versionId, props.ruleId, props.orgId],
  () => {
    visible.value = false;
  },
);
onDeactivated(() => {
  visible.value = false;
});
</script>
