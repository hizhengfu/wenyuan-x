<template>
  <HDialogForm
    v-model:show="dialogVisible"
    :title="title || '配置功能权限'"
    width="80%"
    size="small"
    height="calc(100vh - 20vh)"
    :handle-submit="save"
    :disabled="!loaded || loading"
    submit-text="保存功能权限"
  >
    <div class="permission-catalog">
      <div class="catalog-toolbar">
        <n-form-item label="所属应用" label-placement="left" :show-feedback="false" required>
          <h-application-select
            :value="applicationId"
            class="w-240px"
            placeholder="请选择应用"
            @update:value="requestApplicationChange"
          />
        </n-form-item>
        <n-select v-model:value="mappingFilter" class="w-150px" :options="mappingOptions" />
        <n-select v-model:value="usageFilter" class="w-130px" :options="usageOptions" />
        <n-tag :bordered="false" type="info">已选 {{ selectedIds.size }} 项</n-tag>
        <span class="catalog-toolbar__spacer" />
        <n-popover v-if="adminPermission" trigger="hover" placement="bottom-end" :width="380">
          <template #trigger>
            <div
              class="admin-control"
              :class="{ 'admin-control--active': selectedIds.has(adminPermission.id) }"
            >
              <n-switch
                size="small"
                :value="selectedIds.has(adminPermission.id)"
                @update:value="(checked: boolean) => toggleAdminPermission(checked)"
              />
              <span>全局管理员</span>
              <n-tag size="small" type="error" :bordered="false">ADMIN</n-tag>
            </div>
          </template>
          <div>
            ADMIN 覆盖当前平台全部受保护入口，与业务权限互斥。历史角色已配置 ADMIN 时会保留 ADMIN，并清理冗余业务权限。
          </div>
        </n-popover>
        <n-popover
          v-if="hasDiagnostics"
          v-model:show="diagnosticsVisible"
          trigger="click"
          placement="bottom-end"
          :width="560"
        >
          <template #trigger>
            <n-button size="small" secondary type="warning">
              目录诊断 {{ diagnosticCategoryCount }} 类
            </n-button>
          </template>
          <div class="diagnostic-popover">
            <div class="diagnostic-popover__header">
              <strong>权限目录诊断</strong>
              <n-button text size="small" @click="diagnosticsVisible = false">关闭</n-button>
            </div>
            <n-alert type="warning" :bordered="false">
              <div v-if="catalog.diagnostics.codeNotDeclared.length">
                当前运行平台有 {{ catalog.diagnostics.codeNotDeclared.length }} 个 Controller
                权限码尚未写入当前应用的权限目录。请前往「系统服务」点击「同步权限」；同步后仍存在的项目需检查权限
                code 和应用归属。
              </div>
              <div v-if="catalog.unmappedEndpoints.length">
                {{ catalog.unmappedEndpoints.length }} 个入口无法自动关联到原子权限，这类入口不会由权限同步自动处理。
              </div>
              <div v-if="catalog.diagnostics.multiApplicationCodes.length">
                {{ catalog.diagnostics.multiApplicationCodes.length }} 个权限码跨应用重复，请检查应用归属。
              </div>
            </n-alert>
          </div>
        </n-popover>
      </div>
      <div class="catalog-body">
        <aside class="module-panel">
          <div class="module-panel__title">权限模块</div>
          <n-input
            v-model:value="moduleKeyword"
            class="module-search"
            size="small"
            clearable
            placeholder="搜索模块名称 / code"
          >
            <template #prefix>
              <IconMdiMagnify />
            </template>
          </n-input>
          <n-scrollbar style="max-height: calc(78vh - 190px)">
            <button
              type="button"
              class="module-item"
              :class="{ 'module-item--active': activeModule === '' }"
              @click="activeModule = ''"
            >
              <span>
                <strong>全部权限</strong>
                <small>所有模块</small>
              </span>
              <span class="module-count">{{ businessEffectiveCount }}/{{ businessPermissions.length }}</span>
            </button>
            <button
              v-for="module in filteredBusinessModules"
              :key="module.code"
              type="button"
              class="module-item"
              :class="{ 'module-item--active': activeModule === module.code }"
              @click="activeModule = module.code"
            >
              <span>
                <strong>{{ module.label }}</strong>
                <small>{{ module.code }}</small>
              </span>
              <span class="module-count"
                >{{ moduleSelectedCount(module.code) }}/{{ modulePermissionCount(module.code) }}</span
              >
            </button>
            <n-empty v-if="!filteredBusinessModules.length" size="small" description="没有匹配的权限模块" />
          </n-scrollbar>
        </aside>

        <section class="permission-panel">
          <div class="permission-panel__summary">
            <n-input
              v-model:value="keyword"
              class="permission-search"
              clearable
              placeholder="搜索权限名称/code、HTTP 方法、API 路径或 Controller"
            >
              <template #prefix>
                <IconMdiMagnify />
              </template>
            </n-input>
            <span class="permission-match-count">匹配 {{ visiblePermissions.length }} 项</span>
            <n-space>
              <n-button size="small" secondary @click="selectVisible">选择当前结果</n-button>
              <n-button size="small" secondary @click="clearVisible">清除当前结果</n-button>
            </n-space>
          </div>
          <n-data-table
            :columns="columns"
            :data="visiblePermissionTree"
            :loading="loading"
            :pagination="false"
            :row-key="(row: PermissionCatalogItem) => row.id"
            v-model:expanded-row-keys="expandedRowKeys"
            :indent="24"
            :scroll-x="1180"
            :max-height="tableMaxHeight"
            striped
            :single-line="false"
          />
        </section>
      </div>
    </div>
  </HDialogForm>

  <n-drawer v-model:show="apiDrawerVisible" :width="860">
    <n-drawer-content :title="apiDrawerTitle" closable>
      <n-alert v-if="apiPermission?.globalBypass" class="mb-12px" type="warning" :bordered="false">
        ADMIN 是全局放行权限，覆盖当前平台所有受保护入口。为避免重复传输数千条
        API，目录只展示覆盖数量；请通过对应原子权限追溯具体入口。
      </n-alert>
      <template v-else>
        <div v-if="apiPermission?.endpoints.length" class="endpoint-toolbar">
          <n-input v-model:value="apiKeyword" clearable placeholder="搜索方法、路径、Controller、处理方法或权限 code">
            <template #prefix>
              <IconMdiMagnify />
            </template>
          </n-input>
          <n-select v-model:value="apiMethodFilter" class="endpoint-method-filter" :options="apiMethodOptions" />
          <n-tag :bordered="false" type="info"
            >{{ visibleApiEndpoints.length }}/{{ apiPermission.endpoints.length }}</n-tag
          >
        </div>
        <n-empty v-if="!apiPermission?.endpoints.length" description="该权限没有可自动反查的入口 API" />
        <n-empty v-else-if="!visibleApiEndpoints.length" description="没有匹配的入口 API" />
        <n-list v-else bordered>
          <n-list-item v-for="endpoint in visibleApiEndpoints" :key="endpoint.key">
            <div class="endpoint-row">
              <div class="endpoint-main">
                <n-space align="center">
                  <n-tag size="small" :type="methodTagType(endpoint.httpMethod)">
                    {{ endpoint.httpMethod }}
                  </n-tag>
                  <code>{{ endpoint.path }}</code>
                </n-space>
                <div class="endpoint-summary">
                  {{ endpoint.summary || `${endpoint.controller}.${endpoint.handler}` }}
                </div>
                <div class="endpoint-handler">{{ endpoint.controller }}.{{ endpoint.handler }}</div>
              </div>
              <n-tag size="small" :type="statusTagType(endpoint.mappingStatus)">
                {{ statusLabel(endpoint.mappingStatus) }}
              </n-tag>
            </div>
            <template #suffix>
              <n-tooltip v-if="endpoint.rawExpression">
                <template #trigger>
                  <n-button text type="primary">查看策略</n-button>
                </template>
                <code>{{ endpoint.rawExpression }}</code>
              </n-tooltip>
            </template>
          </n-list-item>
        </n-list>
      </template>
    </n-drawer-content>
  </n-drawer>
</template>

<script lang="ts" setup>
import { computed, h, ref, watch } from 'vue';
import { NButton, NCheckbox, NSpace, NTag, type DataTableColumns, type SelectOption, useDialog } from 'naive-ui';
import HApplicationSelect from '@/modules/sys/components/form/field/ApplicationSelect.vue';
import {
  catalog as loadPermissionCatalog,
  type PermissionCatalogItem,
  type PermissionCatalogSnapshot,
  type PermissionEndpointMappingStatus,
} from '@/modules/sys/api/permission';
import { rolePermissions, updateRolePermissions, type RolePermissionAssociation } from '@/modules/sys/api/role';
import {
  createEmptyPermissionCatalog,
  aggregatePermissionDescendants,
  buildPermissionCatalogTree,
  effectivePermissionSelection,
  filterPermissionCatalog,
  filterPermissionEndpoints,
  filterPermissionModules,
  isAggregatePermission,
  isGlobalBypassPermission,
  normalizePermissionCatalog,
  normalizePermissionSelection,
  sameStringSet,
  type PermissionCatalogTreeItem,
  updatePermissionSelection,
} from './permissionCatalog';

const props = defineProps({
  show: { type: Boolean, default: false },
  title: { type: String, default: '' },
  applicationId: { type: String, default: '1' },
  roleId: { type: String, required: true },
});

const emit = defineEmits(['update:show']);
const confirmDialog = useDialog();

const catalog = ref<PermissionCatalogSnapshot>(createEmptyPermissionCatalog());
const applicationId = ref('1');
const selectedIds = ref<Set<string>>(new Set());
const originalIds = ref<Set<string>>(new Set());
const version = ref('');
const loading = ref(false);
const loaded = ref(false);
const moduleKeyword = ref('');
const keyword = ref('');
const activeModule = ref('');
const mappingFilter = ref('ALL');
const usageFilter = ref('ALL');
const apiDrawerVisible = ref(false);
const apiPermission = ref<PermissionCatalogItem>();
const apiKeyword = ref('');
const apiMethodFilter = ref('ALL');
const diagnosticsVisible = ref(false);
const expandedRowKeys = ref<Array<string | number>>([]);
let loadSequence = 0;

const mappingOptions: SelectOption[] = [
  { label: '全部映射状态', value: 'ALL' },
  { label: '有入口 API', value: 'MAPPED' },
  { label: '无直接 API', value: 'UNMAPPED' },
];

const usageOptions: SelectOption[] = [
  { label: '全部权限', value: 'ALL' },
  { label: '有入口 API 引用', value: 'USED' },
  { label: '无入口 API 引用', value: 'UNUSED' },
];

const dirty = computed(() => !sameStringSet(selectedIds.value, originalIds.value));
const tableMaxHeight = computed(() => 'calc(78vh - 250px)');
const apiDrawerTitle = computed(() =>
  apiPermission.value ? `${apiPermission.value.name} · ${apiPermission.value.code}` : '入口 API',
);

const adminPermission = computed(() => catalog.value.permissions.find(isGlobalBypassPermission));
const diagnosticCategoryCount = computed(
  () =>
    Number(catalog.value.diagnostics.codeNotDeclared.length > 0) +
    Number(catalog.value.unmappedEndpoints.length > 0) +
    Number(catalog.value.diagnostics.multiApplicationCodes.length > 0),
);
const hasDiagnostics = computed(() => diagnosticCategoryCount.value > 0);
const businessPermissions = computed(() =>
  catalog.value.permissions.filter((permission) => !isGlobalBypassPermission(permission)),
);
const businessModules = computed(() =>
  catalog.value.modules.filter((module) => modulePermissionCount(module.code) > 0),
);
const filteredBusinessModules = computed(() => filterPermissionModules(businessModules.value, moduleKeyword.value));
const apiMethodOptions = computed<SelectOption[]>(() => [
  { label: '全部方法', value: 'ALL' },
  ...[...new Set(apiPermission.value?.endpoints.map((endpoint) => endpoint.httpMethod) ?? [])]
    .sort()
    .map((method) => ({ label: method, value: method })),
]);
const visibleApiEndpoints = computed(() =>
  filterPermissionEndpoints(apiPermission.value?.endpoints ?? [], apiKeyword.value, apiMethodFilter.value),
);

const visiblePermissions = computed(() => {
  return filterPermissionCatalog(businessPermissions.value, {
    moduleCode: activeModule.value,
    keyword: keyword.value,
    mapping: mappingFilter.value as 'ALL' | 'MAPPED' | 'UNMAPPED' | 'GLOBAL',
    usage: usageFilter.value as 'ALL' | 'USED' | 'UNUSED',
  });
});

const visiblePermissionTree = computed(() =>
  buildPermissionCatalogTree(
    businessPermissions.value,
    new Set(visiblePermissions.value.map((permission) => permission.id)),
  ),
);

const aggregateDescendants = computed(() => aggregatePermissionDescendants(businessPermissions.value));
const effectiveSelectedIds = computed(() => effectivePermissionSelection(catalog.value.permissions, selectedIds.value));
const businessEffectiveCount = computed(
  () => businessPermissions.value.filter((permission) => effectiveSelectedIds.value.has(permission.id)).length,
);
const aggregateCoverage = computed(() => {
  const coverage = new Map<string, PermissionCatalogItem>();
  const admin = adminPermission.value;
  if (admin && selectedIds.value.has(admin.id)) {
    businessPermissions.value.forEach((permission) => coverage.set(permission.id, admin));
    return coverage;
  }
  selectedIds.value.forEach((id) => {
    const aggregate = businessPermissions.value.find((permission) => permission.id === id);
    if (!aggregate || !isAggregatePermission(aggregate)) return;
    aggregateDescendants.value.get(id)?.forEach((descendantId) => coverage.set(descendantId, aggregate));
  });
  return coverage;
});

function permissionCheckbox(row: PermissionCatalogTreeItem) {
  const descendantIds = aggregateDescendants.value.get(row.id) ?? [];
  return h(NCheckbox, {
    checked: selectedIds.value.has(row.id),
    indeterminate:
      !selectedIds.value.has(row.id) && descendantIds.some((descendantId) => selectedIds.value.has(descendantId)),
    disabled: aggregateCoverage.value.has(row.id),
    title: aggregateCoverage.value.has(row.id)
      ? `已由 ${aggregateCoverage.value.get(row.id)!.code} 覆盖；如需单独配置，请先取消全权限`
      : undefined,
    'onUpdate:checked': (checked: boolean) => togglePermission(row.id, checked),
  });
}

const columns: DataTableColumns<PermissionCatalogTreeItem> = [
  {
    key: 'selected',
    width: 48,
    fixed: 'left',
    title: () =>
      h(NCheckbox, {
        checked:
          visiblePermissions.value.length > 0 &&
          visiblePermissions.value.every((item) => effectiveSelectedIds.value.has(item.id)),
        indeterminate:
          visiblePermissions.value.some((item) => effectiveSelectedIds.value.has(item.id)) &&
          !visiblePermissions.value.every((item) => effectiveSelectedIds.value.has(item.id)),
        'onUpdate:checked': (checked: boolean) => (checked ? selectVisible() : clearVisible()),
      }),
    render: (row) => permissionCheckbox(row),
  },
  {
    key: 'name',
    tree: true,
    title: '权限名称',
    width: 320,
    fixed: 'left',
    render: (row) =>
      h('span', { class: 'permission-name-row' }, [
        h('div', { class: 'permission-name' }, [
          h('div', { class: 'permission-name__label' }, [
            row.name,
            row.globalBypass
              ? h(NTag, { size: 'small', type: 'warning', bordered: false }, { default: () => '全局放行' })
              : null,
            isAggregatePermission(row) && aggregateDescendants.value.has(row.id)
              ? h(NTag, { size: 'small', type: 'info', bordered: false }, { default: () => '全权限' })
              : null,
            aggregateCoverage.value.has(row.id)
              ? h(
                  NTag,
                  { size: 'small', type: 'success', bordered: false },
                  { default: () => `由 ${aggregateCoverage.value.get(row.id)!.code} 覆盖` },
                )
              : null,
            row.applicationAmbiguous
              ? h(
                  NTag,
                  {
                    size: 'small',
                    type: 'error',
                    bordered: false,
                    title: `同一权限码还存在于应用：${row.applicationIds.join(', ')}`,
                  },
                  { default: () => '跨应用同码' },
                )
              : null,
          ]),
          h('code', { class: 'permission-code' }, row.code),
        ]),
      ]),
  },
  {
    key: 'moduleLabel',
    title: '模块',
    width: 190,
    render: (row) => h('div', { class: 'module-cell' }, [h('span', row.moduleLabel), h('code', row.moduleCode)]),
  },
  {
    key: 'endpoints',
    title: '入口 API',
    minWidth: 370,
    render: (row) => {
      if (row.globalBypass) {
        return h(
          NButton,
          { text: true, type: 'warning', onClick: () => showApis(row) },
          { default: () => `覆盖所有受保护入口（${row.endpointCount}）` },
        );
      }
      if (!row.endpointCount) {
        return h(NTag, { size: 'small', type: 'default', bordered: false }, { default: () => '无直接 API' });
      }
      const first = row.endpoints[0];
      return h(
        NSpace,
        { align: 'center', wrap: false },
        {
          default: () => [
            h(NTag, { size: 'small', type: methodTagType(first.httpMethod) }, { default: () => first.httpMethod }),
            h('code', { class: 'endpoint-path' }, first.path),
            h(
              NButton,
              { text: true, type: 'primary', onClick: () => showApis(row) },
              { default: () => `查看 ${row.endpointCount} 个` },
            ),
          ],
        },
      );
    },
  },
  {
    key: 'used',
    title: '状态',
    width: 100,
    render: (row) =>
      h(
        NTag,
        { size: 'small', type: row.used ? 'success' : 'default', bordered: false },
        { default: () => (row.used ? '有入口引用' : '未发现入口') },
      ),
  },
];

const dialogVisible = computed({
  get: () => props.show,
  set: (value: boolean) => {
    if (value) {
      emit('update:show', true);
    } else {
      requestClose();
    }
  },
});

function togglePermission(id: string, checked: boolean) {
  selectedIds.value = updatePermissionSelection(catalog.value.permissions, selectedIds.value, [id], checked);
}

function toggleAdminPermission(checked: boolean) {
  const admin = adminPermission.value;
  if (!admin) return;
  selectedIds.value = updatePermissionSelection(catalog.value.permissions, selectedIds.value, [admin.id], checked);
}

function selectVisible() {
  const targetIds = visiblePermissions.value
    .filter((item) => !effectiveSelectedIds.value.has(item.id))
    .map((item) => item.id);
  if (!targetIds.length) return;
  selectedIds.value = updatePermissionSelection(catalog.value.permissions, selectedIds.value, targetIds, true);
}

function clearVisible() {
  selectedIds.value = updatePermissionSelection(
    catalog.value.permissions,
    selectedIds.value,
    visiblePermissions.value.map((item) => item.id),
    false,
  );
}

function moduleSelectedCount(moduleCode: string) {
  return businessPermissions.value.filter(
    (permission) => permission.moduleCode === moduleCode && effectiveSelectedIds.value.has(permission.id),
  ).length;
}

function modulePermissionCount(moduleCode: string) {
  return businessPermissions.value.filter((permission) => permission.moduleCode === moduleCode).length;
}

function showApis(permission: PermissionCatalogItem) {
  apiPermission.value = permission;
  apiKeyword.value = '';
  apiMethodFilter.value = 'ALL';
  apiDrawerVisible.value = true;
}

function methodTagType(method: string): 'default' | 'success' | 'info' | 'warning' | 'error' {
  const types: Record<string, 'default' | 'success' | 'info' | 'warning' | 'error'> = {
    GET: 'success',
    POST: 'info',
    PUT: 'warning',
    PATCH: 'warning',
    DELETE: 'error',
  };
  return types[method] || 'default';
}

function statusTagType(status: PermissionEndpointMappingStatus): 'success' | 'info' | 'warning' | 'default' {
  if (status === 'EXACT') return 'success';
  if (status === 'AUTHENTICATED_ONLY') return 'info';
  if (status === 'DYNAMIC_POLICY' || status === 'UNSUPPORTED') return 'warning';
  return 'default';
}

function statusLabel(status: PermissionEndpointMappingStatus) {
  const labels: Record<PermissionEndpointMappingStatus, string> = {
    EXACT: '精确映射',
    AUTHENTICATED_ONLY: '仅需登录',
    DYNAMIC_POLICY: '动态策略',
    NO_METHOD_POLICY: '无入口策略',
    UNSUPPORTED: '无法自动解析',
  };
  return labels[status];
}

async function load() {
  if (!props.roleId || !applicationId.value) return;
  const sequence = ++loadSequence;
  loading.value = true;
  loaded.value = false;
  catalog.value = createEmptyPermissionCatalog();
  diagnosticsVisible.value = false;
  selectedIds.value = new Set();
  originalIds.value = new Set();
  expandedRowKeys.value = [];
  version.value = '';
  try {
    const [catalogResponse, associationResponse] = await Promise.all([
      loadPermissionCatalog({ applicationId: applicationId.value }),
      rolePermissions(props.roleId, applicationId.value),
    ]);
    if (sequence !== loadSequence) return;

    catalog.value = normalizePermissionCatalog(catalogResponse.data);
    diagnosticsVisible.value = hasDiagnostics.value;
    const parentIds = new Set(catalog.value.permissions.map((permission) => permission.parentId).filter(Boolean));
    expandedRowKeys.value = catalog.value.permissions
      .filter((permission) => parentIds.has(permission.id))
      .map((permission) => permission.id);
    const association = associationResponse.data as RolePermissionAssociation;
    const normalizedIds = normalizePermissionSelection(catalog.value.permissions, association.permissionIds || []);
    selectedIds.value = normalizedIds;
    originalIds.value = new Set(normalizedIds);
    version.value = association.version;
    activeModule.value = '';
    moduleKeyword.value = '';
    keyword.value = '';
    apiKeyword.value = '';
    apiMethodFilter.value = 'ALL';
    mappingFilter.value = 'ALL';
    usageFilter.value = 'ALL';
    loaded.value = true;
  } catch {
    // 请求层已经展示错误；保持禁用和空状态，防止把上一应用的选择误保存到当前应用。
  } finally {
    if (sequence === loadSequence) loading.value = false;
  }
}

async function save() {
  if (!loaded.value) throw new Error('权限目录尚未加载完成');
  const response = await updateRolePermissions(props.roleId, {
    applicationId: applicationId.value,
    permissionIds: [...selectedIds.value].sort(),
    expectedVersion: version.value,
  });
  const association = response.data as RolePermissionAssociation;
  const normalizedIds = normalizePermissionSelection(catalog.value.permissions, association.permissionIds || []);
  selectedIds.value = normalizedIds;
  originalIds.value = new Set(normalizedIds);
  version.value = association.version;
  return response;
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
    content: '当前功能权限尚未保存，切换应用会丢弃这些修改。',
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
    title: '放弃未保存的权限修改？',
    content: '关闭后，本次勾选变化不会保存。',
    positiveText: '放弃并关闭',
    negativeText: '继续编辑',
    onPositiveClick: () => emit('update:show', false),
  });
}

watch(moduleKeyword, () => {
  if (activeModule.value && !filteredBusinessModules.value.some((module) => module.code === activeModule.value)) {
    activeModule.value = '';
  }
});

watch(
  () => [props.show, props.roleId, props.applicationId] as const,
  ([show]) => {
    if (!show) return;
    applicationId.value = props.applicationId || '1';
    load();
  },
  { immediate: true },
);
</script>

<style scoped>
.permission-catalog {
  min-width: 980px;
}

.catalog-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;
}

.catalog-toolbar :deep(.n-form-item) {
  margin-bottom: 0;
}

.catalog-toolbar__spacer {
  flex: 1;
}

.admin-control {
  display: flex;
  min-height: 28px;
  padding: 2px 8px;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--n-border-color, #efeff5);
  border-radius: 4px;
  cursor: pointer;
}

.admin-control--active {
  color: var(--error-color, #d03050);
  background: rgba(208, 48, 80, 0.08);
  border-color: rgba(208, 48, 80, 0.35);
}

.diagnostic-popover__header {
  display: flex;
  margin-bottom: 8px;
  align-items: center;
  justify-content: space-between;
}

.diagnostic-popover :deep(.n-alert-body__content > div + div) {
  margin-top: 8px;
}

.catalog-body {
  display: grid;
  grid-template-columns: 238px minmax(0, 1fr);
  overflow: hidden;
  border: 1px solid var(--n-border-color, #efeff5);
  border-radius: 6px;
}

.module-panel {
  border-right: 1px solid var(--n-border-color, #efeff5);
  background: rgba(128, 128, 128, 0.035);
}

.module-panel__title {
  padding: 12px 14px;
  font-weight: 600;
  border-bottom: 1px solid var(--n-border-color, #efeff5);
}

.module-search {
  width: calc(100% - 20px);
  margin: 10px;
}

.module-item {
  display: flex;
  width: 100%;
  padding: 10px 12px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: inherit;
  text-align: left;
  background: transparent;
  border: 0;
  border-bottom: 1px solid rgba(128, 128, 128, 0.1);
  cursor: pointer;
}

.module-item:hover,
.module-item--active {
  color: var(--primary-color, #18a058);
  background: rgba(24, 160, 88, 0.08);
}

.module-item span:first-child {
  min-width: 0;
}

.module-item strong,
.module-item small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.module-item small {
  margin-top: 3px;
  color: var(--n-text-color-3);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.module-count {
  flex: none;
  font-variant-numeric: tabular-nums;
}

.permission-panel {
  min-width: 0;
}

.permission-panel__summary {
  display: flex;
  min-height: 48px;
  padding: 8px 12px;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--n-border-color, #efeff5);
  gap: 12px;
}

.permission-search {
  flex: 1;
  min-width: 300px;
  max-width: 680px;
}

.permission-match-count {
  flex: none;
  color: var(--n-text-color-3);
}

:deep(.permission-name__label) {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

:deep(.permission-name-row) {
  display: inline-flex;
  max-width: 100%;
  min-width: 0;
  align-items: center;
  vertical-align: middle;
}

:deep(.permission-name-row .permission-name) {
  min-width: 0;
}

:deep(.permission-code),
:deep(.module-cell code),
:deep(.endpoint-path),
.endpoint-main code,
.endpoint-handler {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

:deep(.permission-code),
:deep(.module-cell code),
.endpoint-handler {
  display: block;
  margin-top: 4px;
  color: var(--n-text-color-3);
  font-size: 12px;
}

:deep(.endpoint-path) {
  display: inline-block;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.endpoint-row {
  display: flex;
  width: 100%;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.endpoint-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.endpoint-method-filter {
  width: 132px;
  flex: none;
}

.endpoint-main {
  min-width: 0;
}

.endpoint-summary {
  margin-top: 8px;
  font-weight: 600;
}
</style>
