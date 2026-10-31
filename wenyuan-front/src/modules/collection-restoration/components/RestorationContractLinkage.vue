<template>
  <div style="margin-top: 12px">
    <NDivider title-placement="left">关联项目</NDivider>
    <NText v-if="!contract.restorationId" depth="3">未关联修复项目</NText>
    <NAlert v-if="loading" type="info">正在读取关联项目…</NAlert>
    <NAlert v-if="error" type="error">{{ error }} <NButton size="small" @click="load">重新读取</NButton></NAlert>
    <NDescriptions v-if="project" label-placement="left" :column="2" bordered size="small">
      <NDescriptionsItem label="项目状态">{{ stateText }}</NDescriptionsItem>
      <NDescriptionsItem label="修复机构">{{ project.restoreOrg || '--' }}</NDescriptionsItem>
      <NDescriptionsItem label="工序进度">
        <NProgress type="line" :percentage="progress.percent" :height="10" style="max-width: 220px; display: inline-block; vertical-align: middle" />
        <span style="margin-left: 8px">{{ progress.done }} / {{ progress.total }} 道</span>
      </NDescriptionsItem>
      <NDescriptionsItem label="工序用时合计">{{ hoursText(hours.total) }}</NDescriptionsItem>
      <NDescriptionsItem label="修复档案">{{ archive ? `${archive.archiveNo || archive.id}${archive.approvedTime ? ' · 已归档' : ' · 未归档'}` : '未建立' }}</NDescriptionsItem>
      <NDescriptionsItem label="档案病害数">{{ archive?.damageCount ?? '--' }}</NDescriptionsItem>
      <NDescriptionsItem label="计划工期">{{ formatDateValue(contract.plannedStart) }} ~ {{ formatDateValue(contract.plannedEnd) }}（{{ deviation.plannedDays ?? '--' }} 天）</NDescriptionsItem>
      <NDescriptionsItem label="实际工期">{{ actualPeriodText(period, formatDateValue) }}（{{ deviation.actualDays ?? '--' }} 天）</NDescriptionsItem>
      <NDescriptionsItem label="开工偏差">
        <NText :type="tone(deviation.startDelay)">{{ deviationText(deviation.startDelay) }}</NText>
      </NDescriptionsItem>
      <NDescriptionsItem label="完工偏差">
        <NText :type="tone(deviation.endDelay)">{{ deviationText(deviation.endDelay) }}</NText>
      </NDescriptionsItem>
      <NDescriptionsItem label="工期长度偏差" :span="2">
        <NText :type="tone(deviation.durationDelta)">{{ deviationText(deviation.durationDelta, '超出', '缩短') }}</NText>
      </NDescriptionsItem>
    </NDescriptions>
  </div>
</template>

<script setup lang="ts">
import { errorText } from '../utils/restorationError';
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { NAlert, NButton, NDescriptions, NDescriptionsItem, NDivider, NProgress, NText } from 'naive-ui';
import { restorationApi } from '../api/restoration';
import { restorationProcessApi } from '../api/process';
import { restorationArchiveApi } from '../api/archive';
import type { RestorationArchiveVo, RestorationContractVo, RestorationProcessVo, RestorationVo } from '../typings';
import { formatDateValue } from '@/utils/datetime';
import { useDictLabel } from '@/utils/dict';
import {
  actualPeriod, actualPeriodText, deviationText, hoursText, periodDeviation, processProgress, projectStateText, sumProcessHours,
} from '../utils/restorationReport';

const props = defineProps<{ contract: RestorationContractVo }>();
const project = ref<RestorationVo | null>(null);
const processes = ref<RestorationProcessVo[]>([]);
const archive = ref<RestorationArchiveVo | null>(null);
const loading = ref(false), error = ref('');
const { label: stateLabel } = useDictLabel('col_process_state', computed(() => project.value?.state) as any);
const stateText = computed(() => projectStateText(project.value, stateLabel.value));
const progress = computed(() => processProgress(processes.value));
const hours = computed(() => sumProcessHours(processes.value));
const period = computed(() => actualPeriod(project.value, processes.value));
const deviation = computed(() => periodDeviation(props.contract.plannedStart, props.contract.plannedEnd, period.value.start, period.value.end));
let token = 0, alive = true;
onBeforeUnmount(() => { alive = false; token++; });

function tone(days: number | null) {
  return days == null || days === 0 ? 'default' : days > 0 ? 'error' : 'success';
}

function ok(response: any, label: string) {
  if (!response || (response.status !== undefined && response.status !== 200)) throw Error(`${label}读取失败`);
  return response.data;
}

async function load() {
  const id = props.contract.restorationId, orgId = props.contract.orgId, current = ++token;
  project.value = null; processes.value = []; archive.value = null; error.value = '';
  if (!id) return;
  loading.value = true;
  try {
    const [p, list, a] = await Promise.all([restorationApi.get(id), restorationProcessApi.list(id), restorationArchiveApi.getByRestoration(id)]);
    if (!alive || current !== token) return;
    const pv = ok(p, '项目');
    if (!pv || pv.id !== id || (orgId && pv.orgId !== orgId)) throw Error('关联项目与合同不一致，请核对原始关联');
    const lv = ok(list, '工序') ?? [];
    if (!Array.isArray(lv) || lv.some((r: any) => !r || r.restorationId !== id)) throw Error('工序列表与项目不一致');
    const av = ok(a, '档案') ?? null;
    if (av && av.restorationId !== id) throw Error('档案与项目不一致');
    project.value = pv; processes.value = lv; archive.value = av;
  } catch (e) {
    if (alive && current === token) error.value = errorText(e, '关联项目读取失败');
  } finally {
    if (alive && current === token) loading.value = false;
  }
}
watch(() => [props.contract.id, props.contract.restorationId], () => { void load(); }, { immediate: true });
</script>
