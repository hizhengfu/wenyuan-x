<template>
  <n-card title="站点内容插件" size="small">
    <n-text depth="3">启用状态立即生效。</n-text>
    <n-alert v-if="error" type="error">{{ error }}</n-alert>
    <n-data-table :columns="columns" :data="plugins" :loading="loading" :row-key="row => row.code" />
    <n-modal v-model:show="configOpen" preset="card" :title="`${selected?.label || ''}配置`" style="width: min(540px, 90vw)">
      <n-form v-if="selected" label-placement="left" label-width="auto">
        <n-form-item v-for="field in selected.configFields" :key="field.key" :label="field.label">
          <n-switch v-if="field.type === 'BOOLEAN'" v-model:value="draft[field.key]" :disabled="!canEdit" />
          <n-input-number v-else-if="field.type === 'NUMBER'" v-model:value="draft[field.key]" :disabled="!canEdit" />
          <n-input v-else v-model:value="draft[field.key]" :disabled="!canEdit" />
        </n-form-item>
      </n-form>
      <n-button v-if="canEdit" type="primary" :loading="saving" @click="saveConfig">保存配置</n-button>
    </n-modal>
  </n-card>
</template>
<script setup lang="ts">
import { computed, ref, watch, h } from 'vue';
import { NSwitch, NButton, type DataTableColumns } from 'naive-ui';
import { useAuthStore } from '@/store';
import { sitePlugins, saveSitePlugin, type CmsSitePlugin } from '../api/plugins';
const props = defineProps<{ siteId: string; disabled?: boolean }>();
const auth = useAuthStore();
const canEdit = computed(() => !props.disabled && auth.hasPermissionByCode('EDIT', 'SITE'));
const plugins = ref<CmsSitePlugin[]>([]); const loading = ref(false); const saving = ref(false); const error = ref('');
const configOpen = ref(false); const selected = ref<CmsSitePlugin>(); const draft = ref<Record<string, any>>({});
const pending = ref<string>(); let requestId = 0;
/** 站点切换后丢弃旧请求结果和错误信息，防止上一站点的配置覆盖当前面板。 */
async function load() {
  const id = ++requestId; plugins.value = []; error.value = ''; loading.value = !!props.siteId; if (!props.siteId) return;
  try { const response = await sitePlugins(props.siteId); if (id === requestId) plugins.value = response.data; }
  catch { if (id === requestId) error.value = '站点插件加载失败或无查看权限'; }
  finally { if (id === requestId) loading.value = false; }
}
async function toggle(row: CmsSitePlugin, enabled: boolean) {
  if (!canEdit.value || pending.value) return;
  // 捕获操作发起时的站点；等待保存期间即使切换站点，也不改变请求的目标。
  const siteId = props.siteId; pending.value = row.code; error.value = '';
  try { await saveSitePlugin(siteId, row.code, { enabled, config: { ...row.config } }); row.enabled = enabled; }
  catch { if (props.siteId === siteId) error.value = '启用状态保存失败，请重试'; }
  finally { pending.value = undefined; }
}
/** 显式保存的 null 也是覆盖值；只有字段未保存时才使用插件默认值。 */
function configure(row: CmsSitePlugin) {
  selected.value = row; draft.value = Object.fromEntries(row.configFields.map(field => [field.key, Object.prototype.hasOwnProperty.call(row.config, field.key) ? row.config[field.key] : field.defaultValue ?? (field.type === 'BOOLEAN' ? false : field.type === 'NUMBER' ? null : '')])); configOpen.value = true;
}
async function saveConfig() {
  if (!selected.value || !canEdit.value || saving.value) return;
  // 固定本次提交的行、站点和配置快照；完成后仅关闭仍指向同一行的弹窗。
  const row = selected.value; const siteId = props.siteId; const config = { ...draft.value };
  saving.value = true; error.value = '';
  try {
    await saveSitePlugin(siteId, row.code, { enabled: row.enabled, config }); row.config = config;
    if (props.siteId === siteId && selected.value === row) configOpen.value = false;
  }
  catch { if (props.siteId === siteId && selected.value === row) error.value = '插件配置保存失败，请核对后重试'; }
  finally { saving.value = false; }
}
const accessNames = { PUBLIC: '公开查看', LOGIN: '登录后查看', PERMISSION: '按权限查看' };
const columns: DataTableColumns<CmsSitePlugin> = [
  { key: 'label', title: '内容类型' },
  { key: 'access', title: '查看条件', render: row => accessNames[row.access] },
  { key: 'enabled', title: '本站启用', render: row => h(NSwitch, { value: row.enabled, loading: pending.value === row.code, disabled: !canEdit.value || !!pending.value, onUpdateValue: (value: boolean) => toggle(row, value) }) },
  { key: 'config', title: '配置', render: row => row.configFields.length ? h(NButton, { size: 'small', onClick: () => configure(row) }, () => canEdit.value ? '设置' : '查看') : '无配置项' },
];
watch(() => props.siteId, () => { configOpen.value = false; selected.value = undefined; void load(); }, { immediate: true });
</script>
