<template>
  <n-spin :show="loadingLatest">
    <div class="deployment-diagnostics">
      <n-flex justify="space-between" align="center" :wrap="true">
        <n-text depth="3" class="description">
          只执行只读检查和可清理的临时探针，不会自动安装软件、修改配置或变更数据库结构。
        </n-text>
        <n-flex align="center">
          <n-tag v-if="snapshot.lifecycle !== 'NEVER_RUN'" :type="lifecycleTagType">
            {{ lifecycleLabel }}
          </n-tag>
          <n-button type="primary" :loading="starting || scanning" @click="handleStartScan">
            {{ report ? '重新扫描' : '开始扫描' }}
          </n-button>
        </n-flex>
      </n-flex>

      <n-alert v-if="snapshot.lifecycle === 'NEVER_RUN'" type="info" :show-icon="true">
        尚未执行部署扫描。建议每次发布、迁移主机或修改运行环境后重新扫描。
      </n-alert>
      <n-alert v-else-if="scanning" type="info" :show-icon="true">
        扫描正在后台执行{{ report ? '，下方暂时显示上一次完成结果' : '' }}。
      </n-alert>
      <n-alert v-else-if="snapshot.stale" type="warning" :show-icon="true">
        当前结果已经过期，请重新扫描后再作为部署核对依据。
      </n-alert>

      <template v-if="report">
        <n-flex class="report-header" align="center" :wrap="true">
          <n-tag :type="overallTagType" size="large">{{ overallLabel }}</n-tag>
          <n-text>完成时间：{{ formatTime(report.finishedAt) }}</n-text>
          <n-text depth="3">耗时：{{ formatDuration(report.durationMillis) }}</n-text>
          <n-text depth="3">
            {{ report.runtime.applicationName }} {{ report.runtime.applicationVersion }} · Java
            {{ report.runtime.javaVersion }} · {{ report.runtime.operatingSystem }}/{{ report.runtime.architecture }}
          </n-text>
        </n-flex>

        <n-grid cols="2 s:3 m:6" responsive="screen" :x-gap="12" :y-gap="12" class="summary-grid">
          <n-gi><n-statistic label="检查项" :value="report.summary.total" /></n-gi>
          <n-gi><n-statistic label="通过" :value="report.summary.passed" /></n-gi>
          <n-gi><n-statistic label="警告" :value="report.summary.warnings" /></n-gi>
          <n-gi><n-statistic label="严重失败" :value="report.summary.failed" /></n-gi>
          <n-gi><n-statistic label="执行异常" :value="report.summary.errors" /></n-gi>
          <n-gi><n-statistic label="超时/跳过" :value="report.summary.timeouts + report.summary.skipped" /></n-gi>
        </n-grid>

        <n-collapse class="check-list">
          <n-collapse-item v-for="item in report.items" :key="item.id" :name="item.id">
            <template #header>
              <n-flex align="center" :wrap="true" class="check-header">
                <n-tag size="small" :type="stateTagType(item.state)">{{ stateLabel(item.state) }}</n-tag>
                <n-tag v-if="item.severity === 'CRITICAL'" size="small" type="error">关键</n-tag>
                <n-text strong>{{ item.feature }} / {{ item.name }}</n-text>
                <n-text depth="3">{{ item.summary }}</n-text>
              </n-flex>
            </template>

            <n-descriptions label-placement="left" :column="2" size="small" bordered>
              <n-descriptions-item label="检查编号">{{ item.id }}</n-descriptions-item>
              <n-descriptions-item label="耗时">{{ formatDuration(item.durationMillis) }}</n-descriptions-item>
              <n-descriptions-item v-if="item.reasonCode" label="原因码">{{ item.reasonCode }}</n-descriptions-item>
              <n-descriptions-item v-if="item.diagnosticRef" label="诊断编号">
                {{ item.diagnosticRef }}
              </n-descriptions-item>
              <n-descriptions-item
                v-for="(fact, factIndex) in item.safeFacts || []"
                :key="`${item.id}-${fact.name}-${factIndex}`"
                :label="fact.name"
              >
                <span class="fact-value">{{ fact.value }}</span>
              </n-descriptions-item>
            </n-descriptions>

            <div v-if="item.remediations?.length" class="remediations">
              <n-alert
                v-for="(remediation, remediationIndex) in item.remediations || []"
                :key="`${item.id}-remediation-${remediationIndex}`"
                type="warning"
                :show-icon="true"
                class="remediation"
              >
                <template #header>
                  {{ remediation.summary }}
                  <n-tag v-if="remediation.restartRequired" size="small" type="warning">修复后需重启</n-tag>
                </template>
                <ol v-if="remediation.steps?.length" class="remediation-steps">
                  <li v-for="step in remediation.steps || []" :key="step">{{ step }}</li>
                </ol>
                <n-text depth="3">验证方式：{{ remediation.verification }}</n-text>
              </n-alert>
            </div>
          </n-collapse-item>
        </n-collapse>
      </template>
    </div>
  </n-spin>
</template>

<script lang="ts" setup>
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref } from 'vue';
import {
  latestDeploymentDiagnostics,
  startDeploymentDiagnostics,
  type DeploymentCheckState,
  type DeploymentScanSnapshot,
} from '../api/deploymentDiagnostic';

type TagType = 'default' | 'error' | 'info' | 'primary' | 'success' | 'warning';

const snapshot = ref<DeploymentScanSnapshot>({
  lifecycle: 'NEVER_RUN',
  stale: false,
});
const loadingLatest = ref(false);
const starting = ref(false);
const report = computed(() => snapshot.value.report);
const scanning = computed(() => snapshot.value.lifecycle === 'RUNNING');
let pollTimer: ReturnType<typeof setTimeout> | null = null;
let destroyed = false;
let inactive = false;

const lifecycleLabel = computed(() => {
  if (snapshot.value.lifecycle === 'RUNNING') return '扫描中';
  return snapshot.value.stale ? '结果已过期' : '扫描完成';
});
const lifecycleTagType = computed<TagType>(() => {
  if (snapshot.value.lifecycle === 'RUNNING') return 'info';
  return snapshot.value.stale ? 'warning' : 'success';
});
const overallLabel = computed(() => {
  switch (report.value?.overallStatus) {
    case 'READY':
      return '部署就绪';
    case 'DEGRADED':
      return '部分功能不可用';
    case 'NOT_READY':
      return '部署未就绪';
    default:
      return '尚无结论';
  }
});
const overallTagType = computed<TagType>(() => {
  switch (report.value?.overallStatus) {
    case 'READY':
      return 'success';
    case 'DEGRADED':
      return 'warning';
    case 'NOT_READY':
      return 'error';
    default:
      return 'default';
  }
});

function normalizeSnapshot(value: DeploymentScanSnapshot): DeploymentScanSnapshot {
  if (!value.report) return value;

  return {
    ...value,
    report: {
      ...value.report,
      runtime: {
        ...value.report.runtime,
        activeProfiles: value.report.runtime.activeProfiles ?? [],
      },
      items: (value.report.items ?? []).map((item) => ({
        ...item,
        safeFacts: item.safeFacts ?? [],
        remediations: (item.remediations ?? []).map((remediation) => ({
          ...remediation,
          steps: remediation.steps ?? [],
          restartRequired: remediation.restartRequired ?? false,
          verification: remediation.verification ?? '修复后重新执行部署扫描',
        })),
      })),
    },
  };
}

function stopPolling() {
  if (pollTimer) clearTimeout(pollTimer);
  pollTimer = null;
}

function schedulePolling() {
  stopPolling();
  if (destroyed || inactive || !scanning.value) return;
  pollTimer = setTimeout(() => {
    pollTimer = null;
    void refreshSnapshot(true);
  }, 1000);
}

async function refreshSnapshot(silent = false) {
  if (!silent) loadingLatest.value = true;
  let loaded = false;
  try {
    const response = await latestDeploymentDiagnostics();
    snapshot.value = normalizeSnapshot(response.data);
    loaded = true;
  } catch {
    stopPolling();
  } finally {
    if (!silent) loadingLatest.value = false;
    if (loaded) schedulePolling();
  }
}

async function handleStartScan() {
  starting.value = true;
  try {
    const response = await startDeploymentDiagnostics();
    snapshot.value = normalizeSnapshot(response.data);
  } catch {
    stopPolling();
  } finally {
    starting.value = false;
    schedulePolling();
  }
}

function stateLabel(state: DeploymentCheckState) {
  return {
    PASS: '通过',
    FAIL: '失败',
    SKIPPED: '已跳过',
    ERROR: '执行异常',
    TIMEOUT: '超时',
  }[state];
}

function stateTagType(state: DeploymentCheckState): TagType {
  return {
    PASS: 'success',
    FAIL: 'error',
    SKIPPED: 'default',
    ERROR: 'error',
    TIMEOUT: 'warning',
  }[state] as TagType;
}

function formatTime(value: number) {
  return new Date(value).toLocaleString();
}

function formatDuration(value: number) {
  if (value < 1000) return `${value} ms`;
  return `${(value / 1000).toFixed(1)} s`;
}

onMounted(() => {
  void refreshSnapshot();
});

onDeactivated(() => {
  inactive = true;
  stopPolling();
});

onActivated(() => {
  if (!inactive) return;
  inactive = false;
  void refreshSnapshot(true);
});

onBeforeUnmount(() => {
  destroyed = true;
  stopPolling();
});
</script>

<style scoped>
.deployment-diagnostics {
  min-width: 0;
}

.description {
  margin: 4px 0 12px;
}

.report-header,
.summary-grid,
.check-list {
  margin-top: 16px;
}

.check-header {
  min-width: 0;
}

.fact-value {
  overflow-wrap: anywhere;
}

.remediations {
  margin-top: 12px;
}

.remediation + .remediation {
  margin-top: 8px;
}

.remediation-steps {
  margin: 0 0 8px;
  padding-left: 20px;
}
</style>
