<template>
  <div class="rfid-entry">
    <header class="entry-header">
      <div>
        <span class="entry-kicker">现场作业</span>
        <h1>RFID作业入口</h1>
        <p>选择工作站，在独立工作台验证身份后办理借还、提用、盘点或发标。</p>
      </div>
      <NSpace>
        <NButton :loading="loading" @click="loadStations">刷新</NButton>
        <NButton v-if="managementMenu" secondary type="primary" @click="openManagement">基础管理</NButton>
      </NSpace>
    </header>

    <div class="entry-steps" aria-label="RFID作业步骤">
      <span><b>1</b>选择工作站</span><span><b>2</b>本机绑定与身份验证</span><span><b>3</b>读取标签与业务确认</span>
    </div>

    <section class="station-section" aria-labelledby="station-list-title">
      <div class="station-toolbar">
        <div class="station-list-title"><h2 id="station-list-title">工作站</h2><span>{{ filteredStations.length }} / {{ stations.length }} 台</span></div>
        <div class="station-filters">
          <NInput v-model:value="keyword" clearable aria-label="搜索工作站" placeholder="工作站名称 / 编码" />
          <NSelect v-model:value="capability" clearable aria-label="作业能力" :options="entryCapabilities" placeholder="作业能力" />
          <NSelect v-model:value="availability" aria-label="启动状态" :options="availabilityOptions" />
        </div>
      </div>
      <p class="station-help">请在对应工作站电脑打开。启用状态不代表设备在线，进入工作台后再核验本机和外设。</p>

      <NAlert v-if="loadError" type="error" show-icon class="entry-error">{{ loadError }}</NAlert>
      <NSpin :show="loading">
        <div v-if="filteredStations.length" class="station-grid">
          <article v-for="station in filteredStations" :key="station.id" class="station-card">
            <div class="station-card-header">
              <HIcon icon="mdi:desktop-classic" class="station-icon" />
              <div class="station-title"><h3>{{ station.name }}</h3><span>{{ station.code }}</span></div>
              <NTag :type="station.enabled ? 'success' : 'default'" size="small" :bordered="false">{{ station.enabled ? '启用' : '停用' }}</NTag>
            </div>
            <div class="station-capabilities">
              <NTag v-for="item in station.capabilities" :key="item" size="small" :bordered="false">
                <HDictText type="col_rfid_workstation_capability" :value="item" />
              </NTag>
              <span v-if="!station.capabilities.length" class="station-muted">尚未配置作业能力</span>
            </div>
            <dl class="station-info">
              <div><dt>默认作业</dt><dd><HDictText v-if="station.defaultCapability" type="col_rfid_workstation_capability" :value="station.defaultCapability" /><span v-else>进入后选择</span></dd></div>
              <div><dt>外设绑定</dt><dd>{{ station.deviceBindings.filter(binding => binding.enabled).length }} 项</dd></div>
            </dl>
            <p v-if="unavailableReason(station)" class="station-unavailable">{{ unavailableReason(station) }}</p>
            <div class="station-actions">
              <NButton class="open-station" type="primary" :disabled="Boolean(unavailableReason(station))" @click="openStation(station)">进入作业</NButton>
              <NButton class="station-shortcut" :disabled="Boolean(unavailableReason(station))" @click="downloadShortcut(station)">桌面快捷方式</NButton>
              <NButton class="station-copy" text :disabled="Boolean(unavailableReason(station))" @click="copyAddress(station)">复制地址</NButton>
            </div>
          </article>
        </div>
        <NEmpty v-else-if="!loading && !loadError" :description="stations.length ? '没有符合筛选条件的工作站' : '当前可见范围内尚无工作站'" class="station-empty" />
      </NSpin>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onActivated, onDeactivated, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useMessage } from 'naive-ui';
import HIcon from '@/components/basic/DynamicIcon.vue';
import { useAuthStore } from '@/store';
import { useDict } from '@/utils/dict';
import { rfidApi } from '../api/rfid';
import type { RfidWorkstation, RfidWorkstationCapability } from '../typings/rfid';
import { canonicalRfidWorkstationUrl } from '../utils/rfidWorkstationUrl';

const router = useRouter();
const auth = useAuthStore();
const message = useMessage();
const stations = ref<RfidWorkstation[]>([]);
const loading = ref(false);
const loadError = ref('');
const keyword = ref('');
const capability = ref<RfidWorkstationCapability | null>(null);
const availability = ref('ALL');
const launchCapabilities = new Set<RfidWorkstationCapability>(['BORROW_RETURN', 'INVENTORY', 'TAG_ISSUE']);
const { options: capabilityOptions } = useDict('col_rfid_workstation_capability');
const entryCapabilities = computed(() => capabilityOptions.value.filter((item: { value: RfidWorkstationCapability }) => launchCapabilities.has(item.value)));
const availabilityOptions = [{ label: '全部工作站', value: 'ALL' }, { label: '可启动', value: 'READY' }, { label: '暂不可启动', value: 'UNAVAILABLE' }];
type NavigationMenu = { id: string; name: string; path?: string; children?: NavigationMenu[] };
function findManagementMenu(menus: NavigationMenu[]): NavigationMenu | undefined {
  for (const menu of menus) {
    if (menu.path?.split('?')[0] === '/collection/rfid-operation') return menu;
    const child = findManagementMenu(menu.children || []);
    if (child) return child;
  }
}
const managementMenu = computed(() => findManagementMenu((auth.menus || []) as NavigationMenu[]));
const filteredStations = computed(() => {
  const search = keyword.value.trim().toLocaleLowerCase();
  return stations.value.filter(station => {
    if (search && ![station.name, station.code].some(value => value.toLocaleLowerCase().includes(search))) return false;
    if (capability.value && !station.capabilities.includes(capability.value)) return false;
    const ready = !unavailableReason(station);
    return availability.value === 'ALL' || (availability.value === 'READY' ? ready : !ready);
  });
});

function unavailableReason(station: RfidWorkstation): string {
  if (!station.enabled) return '工作站已停用，请联系管理员确认配置。';
  if (!station.capabilities.length) return '尚未配置作业能力，请在基础管理中完成配置。';
  if (station.capabilities.some(item => item === 'TAG_PRINT' || item === 'DOCUMENT_PRINT')) return '打印运行链路尚未开放，请联系管理员调整工作站配置。';
  if (!station.capabilities.some(item => launchCapabilities.has(item))) return '通道专用工作站，请使用 RFID通行监控。';
  const bindings = station.deviceBindings.filter(binding => binding.enabled);
  if (station.capabilities.some(item => item === 'BORROW_RETURN' || item === 'INVENTORY') && !bindings.some(binding => binding.purpose === 'RFID_READ')) return '尚未绑定 RFID 读取设备，请在基础管理中完成配置。';
  if (station.capabilities.includes('TAG_ISSUE') && !bindings.some(binding => binding.purpose === 'RFID_WRITE')) return '尚未绑定 RFID 写入设备，请在基础管理中完成配置。';
  return '';
}

let active = true;
let requestVersion = 0;
async function loadStations() {
  if (!active) return;
  const version = ++requestVersion;
  stations.value = [];
  loadError.value = '';
  loading.value = true;
  try {
    const response: any = await rfidApi.workstations();
    const rows = Array.isArray(response?.data) ? response.data : response?.data?.list;
    if (!Array.isArray(rows)) throw new Error('Invalid workstation response');
    const result = rows.map((row: RfidWorkstation) => ({ ...row, capabilities: row.capabilities || [], deviceBindings: row.deviceBindings || [] }));
    if (active && version === requestVersion) stations.value = result;
  } catch {
    if (active && version === requestVersion) loadError.value = '工作站列表加载失败，请刷新重试。';
  } finally {
    if (active && version === requestVersion) loading.value = false;
  }
}
function clientUrl(station: RfidWorkstation) {
  const href = router.resolve({ path: '/rfid-kiosk', query: { stationCode: station.code } }).href;
  return canonicalRfidWorkstationUrl(href, window.location.href);
}
function openStation(station: RfidWorkstation) {
  if (!active || unavailableReason(station)) return;
  window.open(clientUrl(station), '_blank', 'noopener,noreferrer');
}
async function copyAddress(station: RfidWorkstation) {
  if (!active || unavailableReason(station)) return;
  try {
    await navigator.clipboard.writeText(clientUrl(station));
    message.success('工作台地址已复制，请在对应工作站电脑打开。');
  } catch {
    message.error('复制失败，请使用桌面快捷方式。');
  }
}
function downloadShortcut(station: RfidWorkstation) {
  if (!active || unavailableReason(station)) return;
  const url = clientUrl(station);
  const iconUrl = canonicalRfidWorkstationUrl('./favicon.ico', window.location.href.split('#')[0]);
  const content = `[InternetShortcut]\r\nURL=${url}\r\nIconFile=${iconUrl}\r\nIconIndex=0\r\n`;
  const blobUrl = URL.createObjectURL(new Blob([content], { type: 'text/plain;charset=utf-8' }));
  const anchor = document.createElement('a');
  anchor.href = blobUrl;
  anchor.download = `RFID-${station.name.replace(/[\\/:*?"<>|]/g, '-')}.url`;
  anchor.click();
  URL.revokeObjectURL(blobUrl);
  message.success('桌面快捷方式已生成，请放到工作站电脑桌面。');
}
function openManagement() {
  const menu = managementMenu.value;
  if (!active || !menu?.path) return;
  const [path, search = ''] = menu.path.split('?');
  router.push({ path, query: { ...Object.fromEntries(new URLSearchParams(search)), menuId: menu.id, title: menu.name } });
}
function deactivate() {
  active = false;
  requestVersion++;
  stations.value = [];
  loadError.value = '';
  loading.value = false;
}
onMounted(loadStations);
onDeactivated(deactivate);
onUnmounted(deactivate);
onActivated(() => {
  if (active) return;
  active = true;
  void loadStations();
});
</script>

<style scoped>
.rfid-entry { box-sizing: border-box; width: 100%; min-width: 0; padding: 16px; container-type: inline-size; container-name: rfid-entry; }
.entry-header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; }
.entry-kicker { color: #0f766e; font-size: 12px; }
.entry-header h1 { margin: 4px 0 8px; color: #172033; font-size: 22px; }
.entry-header p, .station-help { margin: 0; color: #667085; font-size: 13px; line-height: 1.7; }
.entry-steps { display: flex; flex-wrap: wrap; gap: 12px 32px; margin: 18px 0; padding: 12px 16px; border-radius: 8px; background: #f3f8fb; color: #536272; font-size: 13px; }
.entry-steps span { display: flex; align-items: center; gap: 8px; }
.entry-steps b { display: grid; place-items: center; width: 22px; height: 22px; border-radius: 50%; color: #0f766e; background: #e0f0ec; }
.station-section { padding: 16px; border: 1px solid #e3e8ef; border-radius: 8px; background: #fff; }
.station-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.station-list-title { display: flex; align-items: center; gap: 8px; }
.station-list-title h2 { margin: 0; font-size: 16px; }
.station-list-title span, .station-muted { color: #667085; font-size: 12px; }
.station-filters { display: grid; grid-template-columns: minmax(160px, 240px) repeat(2, 150px); gap: 10px; max-width: 100%; }
.station-help { margin: 12px 0 16px; font-size: 12px; }
.entry-error { margin-bottom: 12px; }
.station-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.station-card { display: flex; flex-direction: column; min-width: 0; padding: 16px; border: 1px solid #e0e7ef; border-radius: 8px; background: #fff; }
.station-card-header { display: flex; align-items: flex-start; gap: 10px; }
.station-icon { color: #0f766e; font-size: 28px; flex-shrink: 0; }
.station-title { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.station-title h3 { margin: 0 0 3px; color: #172033; font-size: 15px; line-height: 1.5; }
.station-title span { color: #667085; font-size: 12px; }
.station-capabilities { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; min-height: 24px; margin: 14px 0; }
.station-info { display: grid; gap: 8px; margin: 0 0 12px; font-size: 13px; }
.station-info > div { display: flex; justify-content: space-between; gap: 12px; }
.station-info dt { color: #667085; }
.station-info dd { margin: 0; color: #344054; text-align: right; }
.station-unavailable { margin: 0 0 12px; color: #8b651f; font-size: 12px; line-height: 1.6; }
.station-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 12px; margin-top: auto; padding-top: 12px; border-top: 1px solid #e8edf3; }
.station-empty { padding: 36px 0; }
@container rfid-entry (max-width: 1080px) { .station-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@container rfid-entry (max-width: 660px) { .station-grid { grid-template-columns: minmax(0, 1fr); } .station-filters { width: 100%; grid-template-columns: repeat(2, minmax(0, 1fr)); } .station-filters > :first-child { grid-column: 1 / -1; } }
</style>
