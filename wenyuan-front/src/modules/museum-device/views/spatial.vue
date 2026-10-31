<template>
  <div class="spatial-page">
    <NCard :bordered="false" class="spatial-card" content-style="padding: 0; min-height: 0; flex: 1;">
      <template #header>
        <div class="spatial-header">
          <div>
            <div class="spatial-header__title">空间设备图</div>
            <div class="spatial-header__path">
              {{ selectedLocation?.path || selectedLocation?.name || '请选择位置' }}
            </div>
          </div>
          <NSpace align="center" wrap>
            <VenueSelect
              v-model:value="orgId"
              clearable
              placeholder="全部可见场馆"
              style="width: 240px"
              @update:value="loadLocations"
            />
            <NSelect
              v-if="plans.length"
              v-model:value="selectedPlanId"
              :options="planOptions"
              style="width: 220px"
              @update:value="loadPlanView"
            />
            <NSelect
              v-model:value="deviceStateFilter"
              :options="deviceStateOptions"
              clearable
              placeholder="全部设备状态"
              style="width: 150px"
            />
            <NButton v-if="previewVenueId || displayView" secondary @click="previewShow = true">
              {{ editing ? '预览当前编辑' : '预览分布' }}
            </NButton>
            <NButton :loading="loading" @click="refresh">刷新</NButton>
            <NButton v-if="selectedLocation" secondary type="primary" @click="openPlanForm()">
              {{ activePlan ? '编辑底图' : '上传底图' }}
            </NButton>
            <NButton v-if="activePlan && !editing" type="primary" @click="startEditing">编辑锚点</NButton>
            <template v-if="editing">
              <NButton @click="cancelEditing">取消</NButton>
              <NButton type="primary" :loading="savingAnchors" @click="saveAnchors">保存锚点</NButton>
            </template>
            <NDropdown v-if="activePlan" :options="moreOptions" @select="handleMoreAction">
              <NButton quaternary>更多</NButton>
            </NDropdown>
          </NSpace>
        </div>
      </template>

      <NLayout has-sider class="spatial-layout">
        <NLayoutSider bordered :width="286" content-style="padding: 12px; overflow: auto;">
          <NInput v-model:value="locationKeyword" clearable placeholder="搜索场馆、楼层或展厅" />
          <NTree
            block-line
            expand-on-click
            default-expand-all
            :data="locationTree"
            :selected-keys="selectedLocationId ? [selectedLocationId] : []"
            :pattern="locationKeyword"
            :render-label="renderLocationLabel"
            @update:selected-keys="onLocationSelected"
          />
        </NLayoutSider>

        <NLayoutContent class="canvas-pane" content-style="height: 100%; min-height: 0;">
          <NSpin :show="loading" class="canvas-spin">
            <SpatialPlanCanvas
              v-if="displayView"
              :view="displayView"
              :editing="editing"
              :pending-target="pendingTarget"
              :dragging-target="draggingTarget"
              @place="placeTarget"
              @move="moveAnchor"
              @remove="removeAnchor"
              @open-location="openLocation"
            />
            <div v-else class="empty-plan">
              <NEmpty :description="selectedLocation ? '当前位置尚未上传平面图' : '请先选择一个位置'">
                <template #extra>
                  <NButton v-if="selectedLocation" type="primary" @click="openPlanForm()">上传平面图</NButton>
                </template>
              </NEmpty>
            </div>
          </NSpin>
        </NLayoutContent>

        <NLayoutSider
          v-if="editing && planView"
          bordered
          collapse-mode="width"
          :width="330"
          content-style="padding: 12px; overflow: auto;"
        >
          <NAlert type="info" :bordered="false" class="editor-alert">
            将设备或下级位置拖到图面完成布置；也可点击“布置”后再点击图面。已布置锚点可拖动或删除。
          </NAlert>
          <NTabs type="line" animated>
            <NTabPane name="devices" :tab="`设备 ${planView.devices.length}`">
              <NInput v-model:value="targetKeyword" clearable placeholder="搜索设备" />
              <NList hoverable clickable class="target-list">
                <NListItem
                  v-for="device in filteredDevices"
                  :key="device.id"
                  class="target-item"
                  draggable="true"
                  @dragstart="startTargetDrag($event, 'DEVICE', device.id, device.name)"
                  @dragend="endTargetDrag"
                >
                  <NThing :title="device.name" :description="device.locationPath || device.code">
                    <template #header-extra>
                      <NButton
                        v-if="isPlaced('DEVICE', device.id)"
                        text
                        type="error"
                        size="small"
                        @click="removeTarget('DEVICE', device.id)"
                        >移除</NButton
                      >
                      <NButton
                        v-else
                        text
                        type="primary"
                        size="small"
                        @click="selectTarget('DEVICE', device.id, device.name)"
                      >
                        布置
                      </NButton>
                    </template>
                  </NThing>
                </NListItem>
              </NList>
            </NTabPane>
            <NTabPane name="locations" :tab="`下级位置 ${planView.childLocations.length}`">
              <NList hoverable clickable class="target-list">
                <NListItem
                  v-for="location in planView.childLocations"
                  :key="location.id"
                  class="target-item"
                  draggable="true"
                  @dragstart="startTargetDrag($event, 'LOCATION', location.id, location.name)"
                  @dragend="endTargetDrag"
                >
                  <NThing :title="location.name" :description="location.path || location.code">
                    <template #header-extra>
                      <NButton
                        v-if="isPlaced('LOCATION', location.id)"
                        text
                        type="error"
                        size="small"
                        @click="removeTarget('LOCATION', location.id)"
                        >移除</NButton
                      >
                      <NButton
                        v-else
                        text
                        type="primary"
                        size="small"
                        @click="selectTarget('LOCATION', location.id, location.name)"
                      >
                        布置
                      </NButton>
                    </template>
                  </NThing>
                </NListItem>
              </NList>
            </NTabPane>
          </NTabs>
        </NLayoutSider>
      </NLayout>
    </NCard>

    <NModal
      v-model:show="planFormShow"
      preset="card"
      :title="planForm.id ? '编辑位置图' : '上传位置图'"
      class="plan-modal"
    >
      <NForm ref="planFormRef" :model="planForm" :rules="planRules" label-placement="left" label-width="92">
        <NFormItem label="所属位置">{{ selectedLocation?.path || selectedLocation?.name }}</NFormItem>
        <NFormItem label="图纸名称" path="name"
          ><NInput v-model:value="planForm.name" placeholder="如二层平面图"
        /></NFormItem>
        <NFormItem label="图纸类型" path="planType">
          <NSelect v-model:value="planForm.planType" :options="planTypeOptions" />
        </NFormItem>
        <NFormItem label="底图文件" path="fileId">
          <NSpace vertical style="width: 100%">
            <NImage v-if="planPreview" :src="planPreview" object-fit="contain" height="150" width="100%" />
            <HUpload
              :key="planUploadKey"
              :multiple="false"
              accept=".png,.jpg,.jpeg,.webp,image/png,image/jpeg,image/webp"
              label="选择平面图"
              @upload-finish="onPlanUploadFinished"
            />
          </NSpace>
        </NFormItem>
        <NFormItem label="默认图纸"><NSwitch v-model:value="planForm.defaultPlan" /></NFormItem>
        <NFormItem v-if="planForm.id && planFileChanged" label="保留锚点">
          <NSwitch v-model:value="planForm.preserveAnchors" />
          <span class="form-hint">底图布局发生变化时建议关闭并重新布置</span>
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="planFormShow = false">取消</NButton>
          <NButton type="primary" :loading="savingPlan" @click="savePlan">保存</NButton>
        </NSpace>
      </template>
    </NModal>

    <NModal
      v-model:show="previewShow"
      preset="card"
      :title="editing ? '当前编辑效果预览' : '场馆设备分布预览'"
      style="width: 80%"
      class="spatial-preview-modal"
      content-style="padding: 12px;"
    >
      <SpatialDistributionPreview
        v-if="previewShow && (previewVenueId || displayView)"
        :venue-id="editing ? null : previewVenueId"
        :location-id="selectedLocationId"
        :preview-view="editing ? displayView : null"
        :show-controls="!editing"
        height="min(72vh, 760px)"
      />
    </NModal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import type { FormInst, TreeOption, UploadFileInfo } from 'naive-ui';
import {
  NAlert,
  NButton,
  NCard,
  NDropdown,
  NEmpty,
  NForm,
  NFormItem,
  NImage,
  NInput,
  NLayout,
  NLayoutContent,
  NLayoutSider,
  NList,
  NListItem,
  NModal,
  NSelect,
  NSpace,
  NSpin,
  NSwitch,
  NTabPane,
  NTabs,
  NTag,
  NThing,
  NTree,
  useDialog,
  useMessage,
} from 'naive-ui';
import HUpload from '@/components/form/field/upload/upload.vue';
import VenueSelect from '../components/VenueSelect.vue';
import SpatialDistributionPreview from '../components/SpatialDistributionPreview.vue';
import SpatialPlanCanvas from '../components/SpatialPlanCanvas.vue';
import { ingestApi } from '../api/ingest';
import type {
  MuseumLocationPlanSaveVo,
  MuseumLocationPlanViewVo,
  MuseumLocationPlanVo,
  MuseumSpatialAnchorVo,
  MuseumSpatialLocationVo,
  SpatialAnchorTargetType,
} from '../typings';

const message = useMessage();
const dialog = useDialog();
const loading = ref(false);
const orgId = ref<string | null>(null);
const locations = ref<MuseumSpatialLocationVo[]>([]);
const selectedLocationId = ref<string | null>(null);
const plans = ref<MuseumLocationPlanVo[]>([]);
const selectedPlanId = ref<string | null>(null);
const planView = ref<MuseumLocationPlanViewVo | null>(null);
const locationKeyword = ref('');
const targetKeyword = ref('');
const deviceStateFilter = ref<string | null>(null);
const editing = ref(false);
const savingAnchors = ref(false);
const pendingTarget = ref<{ type: SpatialAnchorTargetType; id: string; name: string } | null>(null);
const draggingTarget = ref(false);
const previewShow = ref(false);

const selectedLocation = computed(() => locations.value.find((item) => item.id === selectedLocationId.value));
const previewVenueId = computed(() => selectedLocation.value?.orgId || orgId.value);
const activePlan = computed(() => plans.value.find((item) => item.id === selectedPlanId.value) || null);
const planOptions = computed(() => plans.value.map((plan) => ({ label: plan.name, value: plan.id })));
const locationTree = computed<TreeOption[]>(() => buildLocationTree(locations.value));
const displayedAnchors = computed(() => {
  if (!planView.value || editing.value || !deviceStateFilter.value) return planView.value?.anchors || [];
  const devices = new Map((planView.value.devices || []).map((device) => [device.id, device]));
  return (planView.value.anchors || []).filter((anchor) => {
    if (anchor.targetType === 'LOCATION') return true;
    return devices.get(anchor.targetId)?.state === deviceStateFilter.value;
  });
});
const displayView = computed<MuseumLocationPlanViewVo | null>(() =>
  planView.value ? { ...planView.value, anchors: displayedAnchors.value } : null,
);
const filteredDevices = computed(() => {
  const keyword = targetKeyword.value.trim().toLowerCase();
  if (!keyword) return planView.value?.devices || [];
  return (planView.value?.devices || []).filter((item) =>
    [item.name, item.code, item.locationPath].some((value) => value?.toLowerCase().includes(keyword)),
  );
});

const deviceStateOptions = [
  { label: '正常', value: 'NORMAL' },
  { label: '告警', value: 'ALARM' },
  { label: '离线', value: 'OFFLINE' },
  { label: '无数据', value: 'NO_DATA' },
];
const planTypeOptions = [
  { label: '楼层平面图', value: 'FLOOR_PLAN' },
  { label: '展厅平面图', value: 'SPACE_PLAN' },
  { label: '吊顶图', value: 'CEILING' },
  { label: '设备线路图', value: 'EQUIPMENT' },
];
const moreOptions = [
  { label: '新增另一张图', key: 'add' },
  { label: '删除当前图', key: 'delete' },
];

function responsePayload(response: any): any {
  let payload = response;
  for (let depth = 0; depth < 5; depth += 1) {
    if (Array.isArray(payload) || payload === null || typeof payload !== 'object' || !('data' in payload))
      return payload;
    payload = payload.data;
  }
  return payload;
}

function responseList<T>(response: any): T[] {
  const payload = responsePayload(response);
  if (Array.isArray(payload)) return payload;
  return Array.isArray(payload?.list) ? payload.list : [];
}

function normalizePlanView(payload: any): MuseumLocationPlanViewVo | null {
  if (!payload?.plan) return null;
  return {
    ...payload,
    anchors: Array.isArray(payload.anchors) ? payload.anchors : [],
    devices: Array.isArray(payload.devices) ? payload.devices : [],
    childLocations: Array.isArray(payload.childLocations) ? payload.childLocations : [],
  };
}

function buildLocationTree(source: MuseumSpatialLocationVo[]): TreeOption[] {
  const ids = new Set(source.map((item) => item.id));
  const children = new Map<string | null, MuseumSpatialLocationVo[]>();
  source.forEach((item) => {
    const parent = item.parentId && ids.has(item.parentId) ? item.parentId : null;
    children.set(parent, [...(children.get(parent) || []), item]);
  });
  const build = (parent: string | null): TreeOption[] =>
    (children.get(parent) || []).map((item) => ({
      key: item.id,
      label: item.name,
      raw: item,
      children: build(item.id),
    }));
  return build(null);
}

function renderLocationLabel(info: { option: TreeOption }) {
  const row = info.option.raw as MuseumSpatialLocationVo;
  return h('div', { class: 'location-tree-label' }, [
    h('span', row.name),
    row.planCount > 0
      ? h(NTag, { size: 'tiny', type: 'info', bordered: false }, { default: () => `${row.planCount} 图` })
      : null,
    row.deviceCount > 0 ? h('small', `${row.deviceCount} 台`) : null,
  ]);
}

async function loadLocations() {
  loading.value = true;
  try {
    locations.value = responseList<MuseumSpatialLocationVo>(
      await ingestApi.spatialLocations({ orgId: orgId.value || undefined }),
    );
    if (!locations.value.some((item) => item.id === selectedLocationId.value)) {
      const first = locations.value.find((item) => item.planCount > 0) || locations.value[0];
      selectedLocationId.value = first?.id || null;
    }
    await loadPlans();
  } finally {
    loading.value = false;
  }
}

async function onLocationSelected(keys: Array<string | number>) {
  selectedLocationId.value = keys.length ? String(keys[0]) : null;
  editing.value = false;
  pendingTarget.value = null;
  await loadPlans();
}

async function loadPlans(preferredPlanId?: string) {
  if (!selectedLocationId.value) {
    plans.value = [];
    planView.value = null;
    return;
  }
  plans.value = responseList<MuseumLocationPlanVo>(await ingestApi.locationPlans(selectedLocationId.value));
  selectedPlanId.value =
    plans.value.find((plan) => plan.id === preferredPlanId)?.id ||
    plans.value.find((plan) => plan.defaultPlan)?.id ||
    plans.value[0]?.id ||
    null;
  await loadPlanView();
}

async function loadPlanView() {
  if (!selectedPlanId.value) {
    planView.value = null;
    return;
  }
  planView.value = normalizePlanView(responsePayload(await ingestApi.locationPlanView(selectedPlanId.value)));
  const index = plans.value.findIndex((item) => item.id === planView.value?.plan.id);
  if (index >= 0 && planView.value) plans.value[index] = planView.value.plan;
}

async function refresh() {
  if (editing.value) return;
  loading.value = true;
  try {
    await loadLocations();
  } finally {
    loading.value = false;
  }
}

function startEditing() {
  editing.value = true;
  pendingTarget.value = null;
}

async function cancelEditing() {
  editing.value = false;
  pendingTarget.value = null;
  await loadPlanView();
}

function selectTarget(type: SpatialAnchorTargetType, id: string, name: string) {
  pendingTarget.value = { type, id, name };
}

function startTargetDrag(event: DragEvent, type: SpatialAnchorTargetType, id: string, name: string) {
  pendingTarget.value = { type, id, name };
  draggingTarget.value = true;
  event.dataTransfer?.setData('text/plain', `${type}:${id}`);
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move';
}

function endTargetDrag() {
  draggingTarget.value = false;
  pendingTarget.value = null;
}

function placeTarget(point: { xRatio: number; yRatio: number }) {
  if (!planView.value || !pendingTarget.value) return;
  const target = pendingTarget.value;
  if (!Array.isArray(planView.value.anchors)) planView.value.anchors = [];
  const existing = planView.value.anchors.find(
    (anchor) => anchor.targetType === target.type && anchor.targetId === target.id,
  );
  if (existing) {
    existing.xRatio = point.xRatio;
    existing.yRatio = point.yRatio;
    pendingTarget.value = null;
    draggingTarget.value = false;
    return;
  }
  planView.value.anchors.push({
    id: `local-${target.type}-${target.id}`,
    targetType: target.type,
    targetId: target.id,
    targetName: target.name,
    xRatio: point.xRatio,
    yRatio: point.yRatio,
    labelOffsetX: 0,
    labelOffsetY: 0,
    zIndex: 0,
  });
  pendingTarget.value = null;
  draggingTarget.value = false;
}

function moveAnchor(payload: { anchor: MuseumSpatialAnchorVo; xRatio: number; yRatio: number }) {
  payload.anchor.xRatio = payload.xRatio;
  payload.anchor.yRatio = payload.yRatio;
}

function removeAnchor(anchor: MuseumSpatialAnchorVo) {
  removeTarget(anchor.targetType, anchor.targetId);
}

function isPlaced(type: SpatialAnchorTargetType, id: string) {
  return (planView.value?.anchors || []).some((anchor) => anchor.targetType === type && anchor.targetId === id);
}

function removeTarget(type: SpatialAnchorTargetType, id: string) {
  if (!planView.value) return;
  planView.value.anchors = (planView.value.anchors || []).filter(
    (anchor) => anchor.targetType !== type || anchor.targetId !== id,
  );
  if (pendingTarget.value?.type === type && pendingTarget.value.id === id) pendingTarget.value = null;
}

async function saveAnchors() {
  if (!planView.value) return;
  savingAnchors.value = true;
  try {
    const result = responsePayload(
      await ingestApi.saveLocationPlanAnchors(planView.value.plan.id, {
        revision: planView.value.plan.revision,
        anchors: (planView.value.anchors || []).map((anchor) => ({
          targetType: anchor.targetType,
          targetId: anchor.targetId,
          xRatio: anchor.xRatio,
          yRatio: anchor.yRatio,
          displayName: anchor.displayName,
          labelOffsetX: anchor.labelOffsetX,
          labelOffsetY: anchor.labelOffsetY,
          zIndex: anchor.zIndex,
        })),
      }),
    );
    planView.value.plan.revision = result.revision;
    editing.value = false;
    pendingTarget.value = null;
    message.success('设备锚点已保存');
    await loadPlanView();
  } finally {
    savingAnchors.value = false;
  }
}

async function openLocation(locationId: string) {
  const target = locations.value.find((item) => item.id === locationId);
  if (!target) return;
  selectedLocationId.value = target.id;
  editing.value = false;
  pendingTarget.value = null;
  await loadPlans();
  if (!plans.value.length) message.info(`${target.name}尚未上传位置图`);
}

const planFormShow = ref(false);
const planFormRef = ref<FormInst | null>(null);
const savingPlan = ref(false);
const originalFileId = ref<string | null>(null);
const planPreview = ref<string | null>(null);
const planUploadKey = ref(0);
const planForm = reactive<MuseumLocationPlanSaveVo>({
  planType: 'FLOOR_PLAN',
  defaultPlan: true,
  preserveAnchors: true,
});
const planFileChanged = computed(() =>
  Boolean(planForm.id && originalFileId.value && planForm.fileId !== originalFileId.value),
);
const planRules = {
  name: { required: true, message: '请输入图纸名称', trigger: ['input', 'blur'] },
  fileId: { required: true, message: '请上传底图', trigger: 'change' },
};

function resetPlanForm() {
  Object.assign(planForm, {
    id: undefined,
    locationId: selectedLocationId.value || undefined,
    name: `${selectedLocation.value?.name || ''}平面图`,
    planType: selectedLocation.value?.type === 'SPACE' ? 'SPACE_PLAN' : 'FLOOR_PLAN',
    fileId: undefined,
    imageWidth: undefined,
    imageHeight: undefined,
    defaultPlan: plans.value.length === 0,
    sort: plans.value.length,
    revision: undefined,
    preserveAnchors: true,
  });
  originalFileId.value = null;
  planPreview.value = null;
  planUploadKey.value += 1;
}

function openPlanForm(plan: MuseumLocationPlanVo | null = activePlan.value) {
  resetPlanForm();
  if (plan) {
    Object.assign(planForm, {
      id: plan.id,
      locationId: plan.locationId,
      name: plan.name,
      planType: plan.planType,
      fileId: plan.fileId,
      imageWidth: plan.imageWidth,
      imageHeight: plan.imageHeight,
      defaultPlan: plan.defaultPlan,
      sort: plan.sort,
      revision: plan.revision,
      preserveAnchors: true,
    });
    originalFileId.value = plan.fileId;
    planPreview.value = plan.filePath || null;
  }
  planFormShow.value = true;
}

async function onPlanUploadFinished(file: UploadFileInfo) {
  const response = (file as any).response || {};
  planForm.fileId = file.id || response.id;
  planPreview.value = file.url || null;
  const dimensions = await imageDimensions(file.url || response.path);
  planForm.imageWidth = dimensions.width;
  planForm.imageHeight = dimensions.height;
  planUploadKey.value += 1;
}

function imageDimensions(url?: string | null): Promise<{ width: number; height: number }> {
  return new Promise((resolve, reject) => {
    if (!url) return reject(new Error('无法读取图片地址'));
    const image = new Image();
    image.onload = () => resolve({ width: image.naturalWidth, height: image.naturalHeight });
    image.onerror = () => reject(new Error('无法读取图片尺寸'));
    image.src = url;
  });
}

async function savePlan() {
  await planFormRef.value?.validate();
  savingPlan.value = true;
  try {
    const result = responsePayload(
      await ingestApi.saveLocationPlan({ ...planForm, locationId: selectedLocationId.value || undefined }),
    );
    message.success('位置图已保存');
    planFormShow.value = false;
    await loadLocations();
    if (selectedLocationId.value === planForm.locationId) await loadPlans(result.id);
  } finally {
    savingPlan.value = false;
  }
}

function handleMoreAction(key: string) {
  if (key === 'add') openPlanForm(null);
  if (key === 'delete') confirmDeletePlan();
}

function confirmDeletePlan() {
  if (!activePlan.value) return;
  dialog.warning({
    title: '删除位置图',
    content: `确认删除“${activePlan.value.name}”及其全部锚点？底图文件不会被删除。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      await ingestApi.deleteLocationPlan(activePlan.value!.id);
      message.success('位置图已删除');
      await loadLocations();
    },
  });
}

let refreshTimer: ReturnType<typeof setInterval> | undefined;
onMounted(async () => {
  await loadLocations();
  refreshTimer = setInterval(() => {
    if (!editing.value && selectedPlanId.value) loadPlanView();
  }, 30_000);
});
onBeforeUnmount(() => refreshTimer && clearInterval(refreshTimer));
</script>

<style scoped>
.spatial-page,
.spatial-card {
  display: flex;
  height: 100%;
  min-height: 620px;
  flex-direction: column;
}

.spatial-card :deep(.n-card__content) {
  display: flex;
  min-height: 0;
  flex: 1;
}

.spatial-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.spatial-header__title {
  font-size: 18px;
  font-weight: 600;
}
.spatial-header__path {
  margin-top: 3px;
  color: #888;
  font-size: 12px;
}
.spatial-layout {
  width: 100%;
  height: 100%;
  min-height: 0;
}
.canvas-pane {
  height: 100%;
  min-width: 0;
}
.canvas-spin {
  width: 100%;
  height: 100%;
}
.canvas-spin :deep(.n-spin-content) {
  height: 100%;
}
.empty-plan {
  display: flex;
  height: 100%;
  min-height: 480px;
  align-items: center;
  justify-content: center;
}
.editor-alert {
  margin-bottom: 10px;
}
.target-list {
  margin-top: 8px;
}
.target-item {
  cursor: grab;
}
.target-item:active {
  cursor: grabbing;
}
.plan-modal {
  width: min(560px, calc(100vw - 32px));
}
.spatial-preview-modal {
  width: min(1280px, calc(100vw - 32px));
}
.form-hint {
  margin-left: 10px;
  color: #888;
  font-size: 12px;
}

:deep(.location-tree-label) {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 6px;
}

:deep(.location-tree-label small) {
  margin-left: auto;
  color: #999;
}
</style>
