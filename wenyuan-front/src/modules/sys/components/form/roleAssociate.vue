<template>
  <HDialogForm
    v-model:show="dialogVisible"
    :title="title || '配置界面可见性'"
    width="920px"
    height="68vh"
    :disabled="loading"
  >
    <div class="visibility-toolbar">
      <n-form-item label="所属应用" label-placement="left" :show-feedback="false" required>
        <h-application-select
          :value="applicationId"
          class="w-260px"
          placeholder="请选择应用"
          @update:value="requestApplicationChange"
        />
      </n-form-item>
      <n-alert type="info" :bordered="false"> 菜单与 Portal 只控制界面入口是否可见，不授予后端接口权限。 </n-alert>
    </div>

    <n-tabs v-model:value="activeTab" type="line" animated>
      <n-tab-pane name="menus" tab="菜单可见性">
        <n-input v-model:value="menuPattern" class="mb-12px" clearable placeholder="按菜单名称搜索" />
        <n-spin :show="loading">
          <div class="resource-tree">
            <n-tree
              v-if="menus.length"
              :data="menus"
              :pattern="menuPattern"
              :show-irrelevant-nodes="false"
              :checked-keys="menuIds"
              key-field="id"
              label-field="name"
              checkable
              block-line
              @update:checked-keys="updateMenuIds"
            />
            <n-empty v-else description="当前应用没有可配置菜单" />
          </div>
        </n-spin>
      </n-tab-pane>

      <n-tab-pane name="portals" tab="Portal 可见性">
        <n-input v-model:value="portalPattern" class="mb-12px" clearable placeholder="按 Portal 名称搜索" />
        <n-spin :show="loading">
          <div class="resource-tree">
            <n-tree
              v-if="portals.length"
              :data="portals"
              :pattern="portalPattern"
              :show-irrelevant-nodes="false"
              :checked-keys="portalIds"
              key-field="id"
              label-field="name"
              checkable
              block-line
              @update:checked-keys="updatePortalIds"
            />
            <n-empty v-else description="当前应用没有匹配的 Portal" />
          </div>
        </n-spin>
      </n-tab-pane>
    </n-tabs>

    <template #form-action>
      <n-tag v-if="currentTabDirty" type="warning" :bordered="false">当前页有未保存修改</n-tag>
      <n-button type="primary" class="ml-2" :loading="saving" :disabled="!currentTabDirty" @click="saveCurrent">
        保存{{ activeTab === 'menus' ? '菜单' : 'Portal' }}配置
      </n-button>
    </template>
  </HDialogForm>
</template>

<script lang="ts" setup>
import { computed, ref, watch } from 'vue';
import { useDialog } from 'naive-ui';
import { list as portalList } from '@/modules/portal/api/portaldefine';
import HApplicationSelect from '@/modules/sys/components/form/field/ApplicationSelect.vue';
import {
  menu as loadMenuTree,
  roleMenus,
  rolePortals,
  updateRoleMenus,
  updateRolePortals,
  type RoleMenuAssociation,
  type RolePortalAssociation,
} from '@/modules/sys/api/role';

const props = defineProps({
  show: { type: Boolean, default: false },
  title: { type: String, default: '' },
  applicationId: { type: String, default: '1' },
  roleId: { type: String, required: true },
});

const emit = defineEmits(['update:show']);
const confirmDialog = useDialog();

const applicationId = ref('1');
const activeTab = ref<'menus' | 'portals'>('menus');
const menus = ref<any[]>([]);
const portals = ref<any[]>([]);
const menuIds = ref<string[]>([]);
const originalMenuIds = ref<string[]>([]);
const portalIds = ref<string[]>([]);
const originalPortalIds = ref<string[]>([]);
const menuVersion = ref('');
const portalVersion = ref('');
const menuPattern = ref('');
const portalPattern = ref('');
const loading = ref(false);
const saving = ref(false);
let loadSequence = 0;

const menuDirty = computed(() => !sameIds(menuIds.value, originalMenuIds.value));
const portalDirty = computed(() => !sameIds(portalIds.value, originalPortalIds.value));
const dirty = computed(() => menuDirty.value || portalDirty.value);
const currentTabDirty = computed(() => (activeTab.value === 'menus' ? menuDirty.value : portalDirty.value));
const dialogVisible = computed({
  get: () => props.show,
  set: (value: boolean) => {
    if (value) emit('update:show', true);
    else requestClose();
  },
});

function normalizeIds(ids: string[]) {
  return [...new Set(ids)].sort();
}

function sameIds(left: string[], right: string[]) {
  const normalizedLeft = normalizeIds(left);
  const normalizedRight = normalizeIds(right);
  return (
    normalizedLeft.length === normalizedRight.length &&
    normalizedLeft.every((id, index) => id === normalizedRight[index])
  );
}

function updateMenuIds(ids: Array<string | number>) {
  menuIds.value = ids.map(String);
}

function updatePortalIds(ids: Array<string | number>) {
  portalIds.value = ids.map(String);
}

async function load() {
  if (!props.roleId || !applicationId.value) return;
  const sequence = ++loadSequence;
  loading.value = true;
  menus.value = [];
  portals.value = [];
  menuIds.value = [];
  originalMenuIds.value = [];
  portalIds.value = [];
  originalPortalIds.value = [];
  menuVersion.value = '';
  portalVersion.value = '';
  try {
    const params = { applicationId: applicationId.value, enable: true };
    const [menuAssociationResponse, portalAssociationResponse, menuData, portalResponse] = await Promise.all([
      roleMenus(props.roleId, applicationId.value),
      rolePortals(props.roleId, applicationId.value),
      loadMenuTree(params),
      portalList({ ...params, pageSize: -1 }),
    ]);
    if (sequence !== loadSequence) return;

    const menuAssociation = menuAssociationResponse.data as RoleMenuAssociation;
    const portalAssociation = portalAssociationResponse.data as RolePortalAssociation;
    menuIds.value = normalizeIds(menuAssociation.menuIds || []);
    originalMenuIds.value = normalizeIds(menuAssociation.menuIds || []);
    portalIds.value = normalizeIds(portalAssociation.portalIds || []);
    originalPortalIds.value = normalizeIds(portalAssociation.portalIds || []);
    menuVersion.value = menuAssociation.version;
    portalVersion.value = portalAssociation.version;
    menus.value = menuData || [];
    portals.value = portalResponse.data?.list || [];
    menuPattern.value = '';
    portalPattern.value = '';
  } catch {
    // 请求层已经展示错误；空状态可避免把上一应用的关联误保存到当前应用。
  } finally {
    if (sequence === loadSequence) loading.value = false;
  }
}

async function saveCurrent() {
  saving.value = true;
  try {
    if (activeTab.value === 'menus') {
      const response = await updateRoleMenus(props.roleId, {
        applicationId: applicationId.value,
        menuIds: normalizeIds(menuIds.value),
        expectedVersion: menuVersion.value,
      });
      const association = response.data as RoleMenuAssociation;
      menuIds.value = normalizeIds(association.menuIds || []);
      originalMenuIds.value = normalizeIds(association.menuIds || []);
      menuVersion.value = association.version;
      return;
    }

    const response = await updateRolePortals(props.roleId, {
      applicationId: applicationId.value,
      portalIds: normalizeIds(portalIds.value),
      expectedVersion: portalVersion.value,
    });
    const association = response.data as RolePortalAssociation;
    portalIds.value = normalizeIds(association.portalIds || []);
    originalPortalIds.value = normalizeIds(association.portalIds || []);
    portalVersion.value = association.version;
  } finally {
    saving.value = false;
  }
}

function switchApplication(value: string) {
  applicationId.value = value;
  load();
}

function requestApplicationChange(value?: string | null) {
  if (!value || value === applicationId.value) return;
  if (!dirty.value) {
    switchApplication(value);
    return;
  }
  confirmDialog.warning({
    title: '切换应用？',
    content: '当前界面可见性配置尚未全部保存，切换应用会丢弃这些修改。',
    positiveText: '放弃修改并切换',
    negativeText: '继续编辑',
    onPositiveClick: () => switchApplication(value),
  });
}

function requestClose() {
  if (!dirty.value) {
    emit('update:show', false);
    return;
  }
  confirmDialog.warning({
    title: '放弃未保存的可见性修改？',
    content: '菜单与 Portal 中尚未保存的变化都会丢失。',
    positiveText: '放弃并关闭',
    negativeText: '继续编辑',
    onPositiveClick: () => emit('update:show', false),
  });
}

watch(
  () => [props.show, props.roleId, props.applicationId] as const,
  ([show]) => {
    if (!show) return;
    applicationId.value = props.applicationId || '1';
    activeTab.value = 'menus';
    load();
  },
  { immediate: true },
);
</script>

<style scoped>
.visibility-toolbar {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 16px;
  margin-bottom: 8px;
}

.visibility-toolbar :deep(.n-form-item) {
  margin-bottom: 0;
}

.resource-tree {
  min-height: 420px;
  max-height: calc(68vh - 180px);
  padding: 4px 8px;
  overflow: auto;
  border: 1px solid var(--n-border-color, #efeff5);
  border-radius: 6px;
}
</style>
