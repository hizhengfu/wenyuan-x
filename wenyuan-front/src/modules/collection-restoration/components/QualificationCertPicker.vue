<template>
  <NSelect
    :value="value"
    :options="options"
    :loading="loading"
    filterable
    remote
    clearable
    :disabled="disabled || !holderId"
    :status="error ? 'error' : undefined"
    :placeholder="error || (holderId ? (placeholder || '选择资质证书') : '请先选择持有方')"
    @search="onSearch"
    @update:value="selectValue"
  />
</template>

<script setup lang="ts">
import { errorText } from '../utils/restorationError';
import { onActivated, onBeforeUnmount, onDeactivated, ref, shallowRef, watch } from 'vue';
import { NSelect } from 'naive-ui';
import { qualificationCertApi } from '../api/qualificationCert';
import type { QualificationCertVo } from '../typings';
import { qualificationLevelLabel } from '../utils/qualificationLevels';
import { formatDateValue } from '@/utils/datetime';

const props = withDefaults(defineProps<{
  value?: string | null;
  holderType?: 'PERSON' | 'ORG';
  holderId?: string | null;
  disabled?: boolean;
  placeholder?: string;
  onlyEnabled?: boolean;
}>(), { onlyEnabled: true });

const emit = defineEmits<{ (e: 'update:value', value: string | null): void }>();
const loading = ref(false);
const options = ref<Array<{ label: string; value: string; disabled?: boolean }>>([]);

const error = ref('');
const selectValue = shallowRef<(value: string | null) => void>(() => {});
let active = true, generation = 0;
let timer: ReturnType<typeof setTimeout> | undefined;
function invalidate() {
  generation++;
  if (timer !== undefined) clearTimeout(timer);
  timer = undefined;
  options.value = [];
  loading.value = false;
  error.value = '';
  selectValue.value = () => {};
}
async function fetchList(keyword?: string) {
  invalidate();
  const token = generation, holderId = props.holderId, holderType = props.holderType || 'ORG';
  if (!active || !holderId || props.disabled) return;
  loading.value = true;
  try {
    const resp: any = await qualificationCertApi.byHolder(holderType, holderId);
    if (!active || token !== generation) return;
    const list: QualificationCertVo[] = resp?.data;
    if ((resp?.status !== undefined && resp.status !== 200) || !Array.isArray(list)
      || list.some(item => !item || typeof item.id !== 'string' || !item.id.trim() || item.holderId !== holderId || item.holderType !== holderType)) {
      throw Error('证书资料来源不一致，请重新选择持有方');
    }
    const normalizedKeyword = keyword?.trim().toLowerCase();
    options.value = list.filter(item => {
      if (props.onlyEnabled !== false && !item.enabled && item.id !== props.value) return false;
      return !normalizedKeyword || `${item.certNo || ''} ${item.issuer || ''}`.toLowerCase().includes(normalizedKeyword);
    }).map(item => ({
      value: item.id!,
      label: `${item.certNo || item.id} · ${qualificationLevelLabel(item.certLevel)} · ${formatDateValue(item.expireDate)}`,
      disabled: props.onlyEnabled !== false && !item.enabled,
    }));
    const allowed = new Set(options.value.filter(item => !item.disabled).map(item => item.value));
    selectValue.value = value => {
      if (active && token === generation && !props.disabled && !loading.value && !error.value && (value === null || allowed.has(value))) {
        emit('update:value', value);
      }
    };
  } catch (e) {
    if (active && token === generation) error.value = errorText(e, '读取证书失败，请重新选择持有方');
  } finally {
    if (token === generation) loading.value = false;
  }
}
function onSearch(keyword: string) {
  invalidate();
  if (!active || !props.holderId || props.disabled) return;
  loading.value = true;
  timer = setTimeout(() => { timer = undefined; void fetchList(keyword); }, 250);
}
watch(() => [props.holderId, props.holderType, props.value, props.onlyEnabled, props.disabled], () => { void fetchList(); }, { immediate: true, flush: 'sync' });
onDeactivated(() => { active = false; invalidate(); });
onActivated(() => { if (!active) { active = true; void fetchList(); } });
onBeforeUnmount(() => { active = false; invalidate(); });
</script>
