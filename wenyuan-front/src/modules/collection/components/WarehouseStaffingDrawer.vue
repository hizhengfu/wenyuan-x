<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="实际库房管理岗位"
    ><NSpace vertical :size="16"
      >
      <CollectionArchiveIssues :data="data" /><NAlert v-if="canManage" type="info"
        >库房名称旁的历史保管责任人备注不能代替实际任职名单。请按真实岗位责任和签署原件登记；总登记账专管人员不得兼管库房。</NAlert
      ><NText v-else>正在查阅 {{ data.name || '所选库房' }} 的历次任职永久档案；请按签署时间核对来源案卷中的任职版本。</NText
      ><WarehouseTreeSelect v-if="!props.readOnly"
        :value="warehouseId"
        :disabled="busy"
        scene-type="STORAGE"
        :allowed-types="['WAREHOUSE', 'AREA']"
        @update:value="select" /><NAlert v-if="loadError" type="error">{{ loadError }}</NAlert
      ><NForm v-if="canManage && ready" label-width="200"
        ><WarehouseRecordFields v-model="form" :fields="warehouseStaffingFields" /></NForm
      ><NButton v-if="canManage" :disabled="!ready" :loading="busy" type="primary" @click="save"
        >核实任职原件并登记</NButton
      ><WarehouseStaffingHandoverPanel
        v-if="canReviewHandover && ready && warehouseId"
        :key="selection"
        :warehouse-id="warehouseId"
        :staffing="data.staffing"
        :allow-prepare-inventory="allowPrepareInventory"
        @prepare-inventory="prepare" /><NCollapse
        ><NCollapseItem
          v-for="a in data.archives || []"
          :key="a.id"
          :name="a.id"
          :title="`任职原件 · ${formatLocalDateValue(a.archivedAt, 'YYYY-MM-DD HH:mm:ss')}`"
          ><CollectionEvidenceReadView
            :fields="warehouseStaffingFields"
            :data="archiveSnapshot(a.snapshot)" :archive-files="a.files" :original-url="fileId => originalUrl(a, fileId)" /></NCollapseItem></NCollapse></NSpace
  ></HPageForm>
</template>
<script setup lang="ts">
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
import { computed, onActivated, onBeforeUnmount, onDeactivated, ref, watch } from 'vue';
import { cloneDeep } from 'lodash-es';
import { useAuthStore } from '@/store/modules/auth';
import { useMessage } from 'naive-ui';
import { formatLocalDateValue } from '@/utils';
import { warehouseApi } from '../api/warehouse';
import WarehouseStaffingHandoverPanel from './WarehouseStaffingHandoverPanel.vue';
import WarehouseTreeSelect from './WarehouseTreeSelect.vue';
import WarehouseRecordFields from './WarehouseRecordFields.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import { warehouseStaffingFields, warehouseFieldError } from '../utils/warehouseEvidenceFields';
import type { KeeperInventorySource } from '../utils/inventoryEvidenceFields';
const props = defineProps<{ allowPrepareInventory?: boolean; readOnly?: boolean }>();
const emit = defineEmits<{ (e: 'saved'): void; (e: 'prepareInventory', value: KeeperInventorySource): void }>(),
  message = useMessage();
const show = ref(false),
  warehouseId = ref<string>(),
  form = ref<any>({}),
  data = ref<any>({}),
  busy = ref(false),
  loading = ref(false),
  loadError = ref('');
const auth = useAuthStore(), selection = ref(0), readActor = ref<string>();
const canManage = computed(() => !props.readOnly && auth.hasPermission('ADMIN,COLLECTION_ADMIN,COLLECTION_WAREHOUSE_MANAGE'));
const canReviewHandover = computed(() => !props.readOnly && (canManage.value ||
  auth.hasRoleCode('COLLECTION_STORAGE_HEAD') || auth.hasRoleCode('COLLECTION_DIRECTOR')));
let alive = true, writeVersion = 0;
const active = ref(true);
const ready = computed(() => alive && active.value && show.value && !!warehouseId.value && !!readActor.value &&
  readActor.value === auth.userInfo?.id && !loading.value && !loadError.value);
const save = ref<() => Promise<boolean>>(async () => false);
function invalidate() {
  ++selection.value;
  ++writeVersion;
  readActor.value = undefined;
  warehouseId.value = undefined;
  data.value = {};
  form.value = {};
  busy.value = false;
  loading.value = false;
  loadError.value = '';
  save.value = async () => false;
}
function close() { show.value = false; invalidate(); }
watch(show, value => { if (!value) invalidate(); }, { flush: 'sync' });
watch(() => auth.userInfo?.id, close, { flush: 'sync' });
watch(() => [props.readOnly, canManage.value, canReviewHandover.value], close, { flush: 'sync' });
onBeforeUnmount(() => { alive = false; close(); });
onDeactivated(() => { active.value = false; close(); });
onActivated(() => { active.value = true; });
function originalUrl(archive: any, fileId: string): string {
  return ready.value && warehouseId.value && (data.value.archives || []).some((a: any) => a.id === archive?.id && a.files?.some((file: any) => file.fileId === fileId))
    ? `/collection/warehouse/${encodeURIComponent(warehouseId.value)}/staffing/original/${encodeURIComponent(archive.id)}/${encodeURIComponent(fileId)}`
    : '';
}
function archiveSnapshot(raw: string) {
  try {
    const value = JSON.parse(raw);
    return value && typeof value === 'object' && !Array.isArray(value)
      ? value
      : { replacementReason: '原件无法解析，请核对历史记录' };
  } catch {
    return { replacementReason: '原件无法解析，请核对历史记录' };
  }
}
function open(id?: string) {
  if (!alive || !active.value || !auth.userInfo?.id) return;
  invalidate();
  show.value = true;
  if (id) void select(id);
}
async function select(id?: string) {
  if (!alive || !active.value || !show.value || !auth.userInfo?.id) return false;
  invalidate();
  warehouseId.value = id;
  if (!id) return false;
  const token = selection.value, actor = auth.userInfo.id;
  const current = () => alive && active.value && show.value && token === selection.value &&
    warehouseId.value === id && actor === auth.userInfo?.id;
  loading.value = true;
  try {
    const result = ((await warehouseApi.staffing(id)) as any).data;
    if (!current()) return false;
    if (!result || typeof result !== 'object' || Array.isArray(result)) throw new Error('任职原件格式异常，请重新核对');
    data.value = result;
    form.value = { ...cloneDeep(result.staffing), replacementReason: undefined };
    readActor.value = actor;
    save.value = () => submit(token, actor, id);
    return true;
  } catch (error: any) {
    if (current()) loadError.value = error?.message || '任职原件读取失败，请重试';
    return false;
  } finally {
    if (current()) loading.value = false;
  }
}
async function submit(token: number, actor: string, id: string) {
  const current = () => ready.value && token === selection.value && actor === auth.userInfo?.id && warehouseId.value === id;
  if (!current() || !canManage.value || busy.value) return false;
  const payload = cloneDeep(form.value);
  const error = warehouseFieldError(warehouseStaffingFields, payload);
  if (error) { message.warning(error); return false; }
  const operation = ++writeVersion;
  busy.value = true;
  try {
    await warehouseApi.saveStaffing(id, payload);
    if (!current() || !canManage.value) return false;
    if (!await select(id)) return false;
    if (!ready.value || warehouseId.value !== id || actor !== auth.userInfo?.id || !canManage.value) return false;
    emit('saved');
    return true;
  } catch (error) { if (current() && canManage.value) throw error; return false; }
  finally { if (operation === writeVersion) busy.value = false; }
}
const prepare = computed(() => {
  const token = selection.value, actor = readActor.value, id = warehouseId.value;
  return (value: Omit<KeeperInventorySource, 'orgId'>) => {
    if (!ready.value || !canReviewHandover.value || !props.allowPrepareInventory || busy.value ||
      token !== selection.value || actor !== auth.userInfo?.id || id !== warehouseId.value ||
      value.warehouseId !== id || !data.value.orgId) return;
    emit('prepareInventory', { ...cloneDeep(value), orgId: data.value.orgId });
  };
});
defineExpose({ open, close });
</script>
