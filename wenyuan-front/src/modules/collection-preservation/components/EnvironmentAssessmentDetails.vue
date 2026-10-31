<template>
  <section class="pv-assessment" aria-label="当前环境评价依据">
    <div class="pv-assessment__heading">
      <NText strong>当前评价依据</NText>
      <NButton v-if="canViewPolicies" text type="primary" size="small" @click="openPolicy()">查看保护方案</NButton>
    </div>
    <NAlert v-if="missingCount" type="info" :bordered="false">
      {{ missingCount }}
      项指标未匹配生效标准，暂无法判断是否达标。请核对保护方案的适用位置、指标及启用状态。
    </NAlert>
    <NText v-if="!canViewPolicies && missingCount" class="pv-assessment__help">
      请联系本馆保护标准维护人员核对保护方案。
    </NText>
    <NCollapse :default-expanded-names="[]">
      <NCollapseItem name="current" :title="`采集数据与评价说明（${metrics.length} 项）`">
        <div class="pv-assessment__table">
          <NTable size="small" :single-line="false">
            <thead>
              <tr>
                <th>指标 / 设备</th>
                <th>最近采集</th>
                <th>保护标准 / 来源</th>
                <th>环境评价</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(metric, index) in metrics"
                :key="`${metric.sensorPointId}-${metric.deviceId}-${metric.sensorId}-${index}`"
              >
                <td>
                  <strong>{{ metric.sensorName || metric.sensorId || '未知指标' }}</strong>
                  <div>
                    {{ metric.deviceName || metric.deviceId || '未关联设备' }}
                  </div>
                </td>
                <td>
                  <div>{{ formatTime(metric.dateTime) }}</div>
                  <div>数据质量：{{ qualityLabel(metric) }}</div>
                  <div>{{ sampleLifetime(metric) }}</div>
                </td>
                <td>
                  <div>{{ standardMatchLabel(metric) }}</div>
                  <div v-if="hasMatchedStandard(metric) === false" class="pv-assessment__explanation">
                    {{ standardMatchDescriptions[metric.standardMatch || ''] || sampleStatusDescriptions.UNCONFIGURED }}
                  </div>
                  <div v-if="hasMatchedStandard(metric) === true && !metric.thresholdConflict">
                    标准区间：{{ standardRange(metric) }}
                  </div>
                  <div
                    v-for="(source, sourceIndex) in metric.standardSources || []"
                    :key="sourceIndex"
                    class="pv-assessment__source"
                  >
                    <div>
                      {{ source.name }} ·
                      {{
                        source.policyVersion != null ? `方案 V${source.policyVersion}` : `规则 V${source.ruleVersion}`
                      }}
                    </div>
                    <div>规则生效：{{ formatTime(source.effectiveFrom) }}</div>
                    <div v-if="source.standardRef">依据：{{ source.standardRef }}</div>
                    <NButton
                      v-if="source.policyId && canViewPolicies"
                      text
                      type="primary"
                      size="tiny"
                      @click="openPolicy(source.policyId, metric)"
                      >查看来源方案</NButton
                    >
                  </div>
                </td>
                <td>
                  <NTag :type="evaluationType(metric)" size="small">{{ evaluationLabel(metric) }}</NTag>
                  <div
                    v-if="environmentCardStatus(metric, elapsedMs) !== 'UNCONFIGURED'"
                    class="pv-assessment__explanation"
                  >
                    {{ environmentStatusDescription(metric, elapsedMs) }}
                  </div>
                </td>
              </tr>
            </tbody>
          </NTable>
        </div>
      </NCollapseItem>
    </NCollapse>
    <NText class="pv-assessment__help">以上为当前监测评价；下方曲线与明细按所选历史时间范围查询。</NText>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NAlert, NButton, NCollapse, NCollapseItem, NTable, NTag, NText } from 'naive-ui';
import dayjs from 'dayjs';
import { useAuthStore } from '@/store';
import type { EnvironmentCardVo } from '../typings';
import {
  environmentCardStatus,
  environmentStatusDescription,
  hasMatchedStandard,
  missingStandardCount,
  sampleStatusDescriptions,
  standardMatchDescriptions,
  standardMatchLabel,
} from '../utils/environmentSample';

const props = withDefaults(defineProps<{ metrics: EnvironmentCardVo[]; elapsedMs?: number }>(), { elapsedMs: 0 });
const router = useRouter();
const route = useRoute();
const auth = useAuthStore();
const policyPath = '/collection-preservation/protection-policy';
function findPolicyMenu(menus: any[]): any | undefined {
  for (const menu of menus) {
    if (menu.path === policyPath) return menu;
    const child = findPolicyMenu(Array.isArray(menu.children) ? menu.children : []);
    if (child) return child;
  }
}
const policyMenu = computed(() => findPolicyMenu(auth.menus || []));
const canViewPolicies = computed(
  () =>
    Boolean(policyMenu.value) &&
    new Set(props.metrics.map((row) => row.orgId).filter(Boolean)).size === 1 &&
    auth.hasPermission(['ADMIN', 'PRESERVATION_ADMIN', 'PRESERVATION_SELECT', 'PRESERVATION_THRESHOLD_MANAGE']),
);
const missingCount = computed(() => missingStandardCount(props.metrics));
function openPolicy(policyId?: string, metric?: EnvironmentCardVo) {
  if (!canViewPolicies.value) return;
  const rows = metric ? [metric] : props.metrics;
  const ids = (values: Array<string | null | undefined>) => [
    ...new Set(values.filter((value): value is string => Boolean(value))),
  ];
  const orgIds = ids(rows.map((row) => row.orgId));
  if (orgIds.length !== 1) return;
  void router.push({
    path: policyPath,
    query: {
      menuId: policyMenu.value.id,
      title: policyMenu.value.name || '保护方案编制',
      portalId: route?.query.portalId,
      orgId: orgIds[0],
      locationId: ids(rows.map((row) => row.sensorPointId)),
      metricCode: ids(rows.map((row) => row.sensorId || row.sensorCode)),
      ...(policyId ? { policyId } : {}),
    },
  });
}
function formatTime(value?: number | null): string {
  return value != null && Number.isFinite(value) ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '尚未采集';
}
function qualityLabel(card: EnvironmentCardVo): string {
  if (card.dataValue == null || card.dateTime == null) return '暂无数据';
  if (!Number.isFinite(card.dataValue) || card.quality === 'INVALID') return '无效';
  return ({ NORMAL: '正常', SUSPECT: '可疑' } as Record<string, string>)[card.quality || ''] || '待核实';
}
function sampleLifetime(card: EnvironmentCardVo): string {
  const seconds = card.maximumSampleAgeSeconds;
  return seconds != null && Number.isFinite(seconds) && seconds > 0 ? `采样有效期：${seconds} 秒` : '数据时效：待评估';
}
function standardRange(card: EnvironmentCardVo): string {
  const min = card.safeMinValue,
    max = card.safeMaxValue;
  const unit = card.sensorUnit ? ` ${card.sensorUnit}` : '';
  if (min != null && max != null) return `${min} ～ ${max}${unit}`;
  if (min != null) return `≥ ${min}${unit}`;
  if (max != null) return `≤ ${max}${unit}`;
  return '待核实';
}
function evaluationLabel(card: EnvironmentCardVo): string {
  const status = environmentCardStatus(card, props.elapsedMs);
  return status === 'SAFE' ? '达标' : status === 'UNSAFE' ? '超限' : '暂无法判定';
}
function evaluationType(card: EnvironmentCardVo): 'success' | 'error' | 'info' {
  const status = environmentCardStatus(card, props.elapsedMs);
  return status === 'SAFE' ? 'success' : status === 'UNSAFE' ? 'error' : 'info';
}
</script>

<style scoped>
.pv-assessment {
  margin-top: 12px;
}
.pv-assessment__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
.pv-assessment__help {
  display: block;
  margin-top: 8px;
  color: var(--n-text-color-2);
  font-size: 12px;
}
.pv-assessment__table {
  overflow-x: auto;
}
.pv-assessment__table table {
  min-width: 720px;
  table-layout: fixed;
}
.pv-assessment__table th:nth-child(1) {
  width: 17%;
}
.pv-assessment__table th:nth-child(2) {
  width: 21%;
}
.pv-assessment__table th:nth-child(3) {
  width: 36%;
}
.pv-assessment__table td {
  vertical-align: top;
  overflow-wrap: anywhere;
}
.pv-assessment__explanation,
.pv-assessment__source {
  margin-top: 6px;
  color: var(--n-text-color-2);
  font-size: 12px;
}
.pv-assessment :deep(.n-collapse) {
  margin-top: 10px;
}
</style>
