<template>
  <div class="ingest-runtime">
    <NCard title="采集服务运行状态" size="small" :bordered="false">
      <template #header-extra>
        <NButton :loading="capabilitiesLoading" @click="loadCapabilities">刷新</NButton>
      </template>

      <NAlert v-if="runtimeError" type="error" show-icon :bordered="false">
        {{ runtimeError }}
      </NAlert>

      <NDescriptions
        v-if="capabilities"
        label-placement="left"
        :column="4"
        bordered
        size="small"
        class="status-summary"
      >
        <NDescriptionsItem label="运行态">
          <NTag :type="runtimeVerified ? 'success' : 'warning'" :bordered="false" size="small">
            {{ runtimeVerified ? '已验证' : '未验证' }}
          </NTag>
        </NDescriptionsItem>
        <NDescriptionsItem label="协议驱动">{{ capabilities.protocols?.length || 0 }}</NDescriptionsItem>
        <NDescriptionsItem label="设备 Codec">{{ capabilities.codecs?.length || 0 }}</NDescriptionsItem>
        <NDescriptionsItem label="设备型号">{{ capabilities.deviceModels?.length || 0 }}</NDescriptionsItem>
        <NDescriptionsItem label="MQTT 模式">{{ mqttRows.length }}</NDescriptionsItem>
      </NDescriptions>

      <NDataTable
        v-if="mqttRows.length"
        :columns="mqttColumns"
        :data="mqttRows"
        :pagination="false"
        :bordered="false"
        size="small"
        class="mqtt-table"
      />
      <NEmpty
        v-else-if="capabilities && runtimeVerified"
        description="采集服务未启用 MQTT Server 或外部 Broker 消费端"
      />
    </NCard>

    <NCard title="已注册协议目录" size="small" :bordered="false">
      <template #header-extra>
        <NButton :loading="catalogLoading" @click="loadCatalog">刷新</NButton>
      </template>
      <NAlert v-if="catalogError" type="error" show-icon :bordered="false" class="catalog-tip">
        {{ catalogError }}
      </NAlert>
      <NTabs v-model:value="catalogTab" type="line" pane-style="display: none">
        <NTabPane name="protocols" :tab="`协议驱动（${catalog.protocols.length}）`" />
        <NTabPane name="codecs" :tab="`设备 Codec（${catalog.codecs.length}）`" />
      </NTabs>
      <div class="catalog-filter">
        <NInput
          v-if="catalogTab === 'protocols'"
          v-model:value="protocolKeyword"
          clearable
          placeholder="筛选协议驱动：名称 / 编码 / 厂商 / 传输方式"
          aria-label="筛选协议驱动"
        />
        <NInput
          v-else
          v-model:value="codecKeyword"
          clearable
          placeholder="筛选设备 Codec：名称 / 编码 / 适用协议 / 设备型号 / 输出指标"
          aria-label="筛选设备 Codec"
        />
        <NText depth="3" class="catalog-filter__count"
          >显示 {{ activeCatalogCount }} / {{ activeCatalogTotal }} 项</NText
        >
      </div>
      <NDataTable
        v-if="catalogTab === 'protocols'"
        key="protocols"
        :columns="protocolColumns"
        :data="filteredProtocols"
        :loading="catalogLoading"
        :pagination="false"
        :bordered="false"
        :scroll-x="900"
        size="small"
      />
      <NDataTable
        v-else
        key="codecs"
        :columns="codecColumns"
        :data="filteredCodecs"
        :loading="catalogLoading"
        :pagination="false"
        :bordered="false"
        :scroll-x="950"
        size="small"
      />
    </NCard>

    <HPageView v-model:show="catalogViewShow" :title="catalogViewTitle" width="700px" :show-action="false">
      <NDescriptions v-if="catalogViewRow" label-placement="left" :column="2" bordered size="small">
        <NDescriptionsItem label="名称">{{ catalogViewRow.name || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="编码">{{ catalogViewRow.code || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="厂商">{{ catalogViewRow.vendor || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="版本">{{ catalogViewRow.version || '--' }}</NDescriptionsItem>
        <template v-if="catalogViewKind === 'protocol'">
          <NDescriptionsItem label="传输方式" :span="2">{{
            formatList(catalogViewRow.transports, ' / ')
          }}</NDescriptionsItem>
          <NDescriptionsItem label="默认路由" :span="2">{{
            catalogViewRow.defaultRoutePattern || '--'
          }}</NDescriptionsItem>
          <NDescriptionsItem label="默认 Codec" :span="2">{{
            catalogViewRow.defaultCodecCode || '--'
          }}</NDescriptionsItem>
          <template v-if="catalogViewRow.mqttAccess">
            <NDescriptionsItem label="发布 Topic" :span="2">{{
              catalogViewRow.mqttAccess.topicTemplate
            }}</NDescriptionsItem>
            <NDescriptionsItem label="允许 Topic" :span="2">{{
              formatList(catalogViewRow.mqttAccess.allowedTopicPatterns)
            }}</NDescriptionsItem>
            <NDescriptionsItem label="推荐 QoS">{{ catalogViewRow.mqttAccess.defaultQos }}</NDescriptionsItem>
            <NDescriptionsItem label="Keep Alive"
              >{{ catalogViewRow.mqttAccess.keepAliveSeconds }} 秒</NDescriptionsItem
            >
          </template>
          <NDescriptionsItem label="说明" :span="2">{{ catalogViewRow.description || '--' }}</NDescriptionsItem>
        </template>
        <template v-else>
          <NDescriptionsItem label="适用协议" :span="2">{{
            formatList(catalogViewRow.protocolCodes)
          }}</NDescriptionsItem>
          <NDescriptionsItem label="设备型号" :span="2">{{ formatList(catalogViewRow.models) }}</NDescriptionsItem>
          <NDescriptionsItem label="输出指标" :span="2">
            <div v-if="catalogViewMetrics.length" class="codec-metric-list">
              <div v-for="metric in catalogViewMetrics" :key="metric.key" class="codec-metric-list__item">
                <span class="codec-metric-list__name">{{ metric.name }}</span>
                <code class="codec-metric-list__code" :title="metric.key">{{ metric.key }}</code>
              </div>
            </div>
            <span v-else>{{ codecMetricEmptyText(catalogViewRow as IngestCodecDefinition) }}</span>
          </NDescriptionsItem>
        </template>
      </NDescriptions>
    </HPageView>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue';
import {
  NAlert,
  NButton,
  NCard,
  NDataTable,
  NDescriptions,
  NDescriptionsItem,
  NEmpty,
  NInput,
  NTabPane,
  NTabs,
  NTag,
  NText,
  type DataTableColumns,
} from 'naive-ui';
import { ingestConfigApi } from '../api/ingestConfig';
import { ingestApi } from '../api/ingest';
import type { IngestCatalog, IngestCodecDefinition, IngestProtocolDefinition } from '../typings';

interface IngestCapabilities {
  protocols?: string[];
  codecs?: string[];
  deviceModels?: string[];
  legacyRawProtocols?: string[];
  mqtt?: Record<string, Record<string, unknown>>;
  source?: string;
  runtimeVerified?: boolean;
}

interface MqttModeRow {
  mode: string;
  active: boolean;
  endpoint: string;
  networkAddresses: string;
  topics: string;
  detail: string;
}

interface CodecMetricDisplay {
  key: string;
  name: string;
  metricCodes: string[];
  units: string[];
}

const capabilities = ref<IngestCapabilities | null>(null);
const capabilitiesLoading = ref(false);
const runtimeError = ref('');
const emptyCatalog: IngestCatalog = {
  protocols: [],
  codecs: [],
  deviceModels: [],
  builtInConnectors: [],
  standardMetrics: [],
};
const catalog = ref<IngestCatalog>(emptyCatalog);
const catalogTab = ref<'protocols' | 'codecs'>('protocols');
const protocolKeyword = ref('');
const codecKeyword = ref('');
const catalogLoading = ref(false);
const catalogError = ref('');
const catalogViewShow = ref(false);
const catalogViewKind = ref<'protocol' | 'codec'>('protocol');
const catalogViewRow = ref<Record<string, any> | null>(null);
const catalogViewTitle = computed(
  () => ({ protocol: '协议驱动详情', codec: '设备 Codec 详情' })[catalogViewKind.value],
);
const catalogViewMetrics = computed(() =>
  catalogViewKind.value === 'codec' && catalogViewRow.value
    ? codecMetricDisplays(catalogViewRow.value as IngestCodecDefinition)
    : [],
);

const filteredProtocols = computed(() =>
  catalog.value.protocols.filter((item) =>
    matchesCatalogKeyword(
      [
        item.name,
        item.code,
        item.vendor,
        item.version,
        item.transports,
        item.description,
        item.defaultRoutePattern,
        item.defaultCodecCode,
      ],
      protocolKeyword.value,
    ),
  ),
);
const filteredCodecs = computed(() =>
  catalog.value.codecs.filter((item) =>
    matchesCatalogKeyword(
      [
        item.name,
        item.code,
        item.vendor,
        item.version,
        item.protocolCodes,
        item.models,
        codecMetricDisplays(item).flatMap((metric) => [metric.name, metric.key, metric.metricCodes, metric.units]),
      ],
      codecKeyword.value,
    ),
  ),
);
const activeCatalogCount = computed(() =>
  catalogTab.value === 'protocols' ? filteredProtocols.value.length : filteredCodecs.value.length,
);
const activeCatalogTotal = computed(() =>
  catalogTab.value === 'protocols' ? catalog.value.protocols.length : catalog.value.codecs.length,
);

const runtimeVerified = computed(() => {
  if (!capabilities.value) return false;
  return capabilities.value.runtimeVerified !== false && capabilities.value.source !== 'web-management-catalog';
});

const mqttRows = computed<MqttModeRow[]>(() =>
  Object.entries(capabilities.value?.mqtt || {}).map(([mode, status]) => {
    const server = mode.toLowerCase() === 'server';
    const active = server ? status.running === true : status.connected === true;
    const endpoint = server
      ? `${String(status.host || '--')}:${String(status.port ?? '--')}`
      : String(status.brokerUri || '--');
    const networkAddresses = server ? runtimeNetworkAddresses(status) : '--';
    const topicPolicy = status.topicPolicy === 'PROTOCOL_ROUTE' ? '按网关路由 / 协议驱动' : '--';
    const topics = server ? topicPolicy : String(status.topic || '--');
    const versions = Array.isArray(status.protocolVersions) ? status.protocolVersions.join(' / ') : '--';
    const detail = server
      ? `MQTT ${versions} · TLS ${status.tls === true ? '开启' : '关闭'} · ${String(status.connectedClients ?? 0)} 连接 / ${String(status.sessions ?? 0)} 会话 / ${String(status.subscriptions ?? 0)} 订阅 / ${String(status.retainedMessages ?? 0)} 保留消息`
      : `Client ID ${String(status.clientId || '--')} · 持久会话`;
    return { mode, active, endpoint, networkAddresses, topics, detail };
  }),
);

const mqttColumns: DataTableColumns<MqttModeRow> = [
  {
    title: '模式',
    key: 'mode',
    width: 110,
    render: (row) => (row.mode.toLowerCase() === 'server' ? '内置 Server' : '外部 Broker 消费'),
  },
  {
    title: '状态',
    key: 'active',
    width: 100,
    render: (row) =>
      h(
        NTag,
        { type: row.active ? 'success' : 'warning', bordered: false, size: 'small' },
        { default: () => (row.active ? '运行中' : '未连接') },
      ),
  },
  { title: '监听/连接地址', key: 'endpoint', minWidth: 210, ellipsis: { tooltip: true } },
  { title: '网卡地址', key: 'networkAddresses', minWidth: 320, ellipsis: { tooltip: true } },
  { title: 'Topic 策略', key: 'topics', minWidth: 220, ellipsis: { tooltip: true } },
  { title: '详情', key: 'detail', minWidth: 360, ellipsis: { tooltip: true } },
];

function runtimeNetworkAddresses(status: Record<string, unknown>): string {
  if (!Array.isArray(status.networkAddresses)) return '--';
  const port = String(status.port ?? '--');
  const rows = status.networkAddresses as Array<Record<string, unknown>>;
  return (
    rows
      .map((item) => {
        const address = String(item.address || '--');
        const host = address.includes(':') ? `[${address}]` : address;
        return `${String(item.interfaceName || '--')} ${host}:${port}`;
      })
      .join('；') || '--'
  );
}

const protocolColumns: DataTableColumns<IngestProtocolDefinition> = [
  { title: '名称', key: 'name', minWidth: 210 },
  { title: '编码', key: 'code', minWidth: 230, ellipsis: { tooltip: true } },
  { title: '厂商', key: 'vendor', width: 120 },
  { title: '版本', key: 'version', width: 80 },
  { title: '传输方式', key: 'transports', width: 150, render: (row) => formatList(row.transports, ' / ') },
  { title: '说明', key: 'description', minWidth: 300, ellipsis: { tooltip: true } },
  {
    title: '操作',
    key: 'actions',
    width: 80,
    fixed: 'right',
    render: (row) => viewButton(() => openCatalogView('protocol', row)),
  },
];

const codecColumns: DataTableColumns<IngestCodecDefinition> = [
  { title: '名称', key: 'name', minWidth: 190 },
  { title: '编码', key: 'code', minWidth: 220, ellipsis: { tooltip: true } },
  {
    title: '适用协议',
    key: 'protocolCodes',
    minWidth: 220,
    ellipsis: { tooltip: true },
    render: (row) => formatList(row.protocolCodes),
  },
  { title: '设备型号', key: 'models', width: 160, render: (row) => formatList(row.models) },
  { title: '输出指标', key: 'metricKeys', minWidth: 360, render: (row) => renderCodecMetricSummary(row) },
  {
    title: '操作',
    key: 'actions',
    width: 80,
    fixed: 'right',
    render: (row) => viewButton(() => openCatalogView('codec', row)),
  },
];

function formatList(value: unknown, separator = ', '): string {
  return Array.isArray(value) && value.length ? value.join(separator) : '--';
}

function codecMetricDisplays(codec: IngestCodecDefinition): CodecMetricDisplay[] {
  const modelMetrics = catalog.value.deviceModels
    .filter((model) => model.codecCode === codec.code)
    .flatMap((model) => model.metrics || []);
  const keys = [...new Set([...(codec.metricKeys || []), ...modelMetrics.map((metric) => metric.externalMetricKey)])];
  return keys.map((key) => {
    const definitions = modelMetrics.filter((metric) => metric.externalMetricKey === key);
    const names = [...new Set(definitions.map((metric) => metric.name).filter(Boolean))];
    return {
      key,
      name: names.join(' / ') || '未配置名称',
      metricCodes: [...new Set(definitions.map((metric) => metric.metricCode).filter(Boolean))],
      units: [...new Set(definitions.map((metric) => metric.unit).filter((unit): unit is string => Boolean(unit)))],
    };
  });
}

function renderCodecMetricSummary(codec: IngestCodecDefinition) {
  const metrics = codecMetricDisplays(codec);
  if (!metrics.length) return codecMetricEmptyText(codec);
  const visibleMetrics = metrics.slice(0, 3);
  return h('div', { class: 'codec-metric-summary' }, [
    ...visibleMetrics.map((metric) =>
      h('span', { class: 'codec-metric-summary__item', title: `${metric.name}（${metric.key}）` }, [
        h('span', { class: 'codec-metric-summary__name' }, metric.name),
        h('code', { class: 'codec-metric-summary__code' }, metric.key),
      ]),
    ),
    ...(metrics.length > visibleMetrics.length
      ? [h(NTag, { size: 'small', bordered: false }, { default: () => `+${metrics.length - visibleMetrics.length}` })]
      : []),
  ]);
}

function codecMetricEmptyText(codec: IngestCodecDefinition): string {
  const models = catalog.value.deviceModels.filter((model) => model.codecCode === codec.code);
  return models.some((model) => model.dynamicMetrics) ? '运行时动态指标' : '无数值型指标';
}

function matchesCatalogKeyword(values: unknown[], keyword: string): boolean {
  const normalizedKeyword = keyword.trim().toLocaleLowerCase();
  if (!normalizedKeyword) return true;
  return values.some((value) => {
    const texts = Array.isArray(value) ? value : [value];
    return texts.some((text) =>
      String(text ?? '')
        .toLocaleLowerCase()
        .includes(normalizedKeyword),
    );
  });
}

function viewButton(onClick: () => void) {
  return h(NButton, { text: true, type: 'primary', size: 'small', onClick }, { default: () => '查看' });
}

function openCatalogView(kind: 'protocol' | 'codec', row: Record<string, any>) {
  catalogViewKind.value = kind;
  catalogViewRow.value = { ...row };
  catalogViewShow.value = true;
}

async function loadCapabilities() {
  capabilitiesLoading.value = true;
  runtimeError.value = '';
  try {
    const response: any = await ingestApi.capabilities();
    capabilities.value = response?.data || null;
  } catch (error: any) {
    capabilities.value = null;
    runtimeError.value = error?.response?.data?.message || error?.message || '无法获取采集服务运行状态';
  } finally {
    capabilitiesLoading.value = false;
  }
}

async function loadCatalog() {
  catalogLoading.value = true;
  catalogError.value = '';
  try {
    const response: any = await ingestConfigApi.catalog();
    catalog.value = response?.data || emptyCatalog;
  } catch (error: any) {
    catalog.value = emptyCatalog;
    catalogError.value = error?.response?.data?.message || error?.message || '无法获取已注册协议目录';
  } finally {
    catalogLoading.value = false;
  }
}

onMounted(() => {
  loadCapabilities();
  loadCatalog();
});
</script>

<style scoped>
.ingest-runtime {
  display: flex;
  min-height: 0;
  flex-direction: column;
  gap: 12px;
}

.status-summary,
.mqtt-table,
.catalog-tip {
  margin-top: 12px;
}

.catalog-filter {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.catalog-filter :deep(.n-input) {
  max-width: 520px;
}

.catalog-filter__count {
  flex: none;
  white-space: nowrap;
}

.codec-metric-summary {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
  overflow: hidden;
}

.codec-metric-summary__item {
  display: inline-flex;
  min-width: 0;
  max-width: 180px;
  align-items: baseline;
  gap: 4px;
  padding: 2px 7px;
  border-radius: 4px;
  background: var(--n-merged-color);
  white-space: nowrap;
}

.codec-metric-summary__name {
  flex: none;
}

.codec-metric-summary__code {
  overflow: hidden;
  color: var(--n-text-color-3);
  font-size: 12px;
  text-overflow: ellipsis;
}

.codec-metric-list {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 8px;
}

.codec-metric-list__item {
  display: flex;
  min-width: 0;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 9px;
  border: 1px solid var(--n-border-color);
  border-radius: 4px;
  background: var(--n-color-embedded);
}

.codec-metric-list__name {
  flex: none;
  color: var(--n-text-color);
}

.codec-metric-list__code {
  color: var(--n-text-color-3);
  font-size: 12px;
  overflow-wrap: anywhere;
  text-align: right;
}

@media (max-width: 640px) {
  .catalog-filter {
    align-items: stretch;
    flex-direction: column;
    gap: 6px;
  }
}
</style>
