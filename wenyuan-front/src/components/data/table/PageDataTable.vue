<template>
  <n-card
    ref="refPageTableContainer"
    class="wh-full flex flex-col pageTable flex-auto"
    :segmented="segmented"
    size="small"
    :bordered="bordered"
    content-style="flex: 1;display: flex;flex-direction: column;margin: 0;padding: 15px;overflow: auto;"
  >
    <template v-if="showHeader" #header>
      <slot name="header">
        <n-space align="center">
          <n-icon size="22px" style="display: flex; justify-self: center">
            <IconMdiViewCarousel />
          </n-icon>
          <span>
            {{ title || $route.query.title }}
          </span>
        </n-space>
      </slot>
    </template>
    <template v-if="showHeader && showToolbar" #header-extra>
      <div class="page-table-header-toolbar">
        <div class="page-table-toolbar-actions">
          <div class="page-table-toolbar-slot">
            <slot name="activeBtn" :checked-table-data="checkedTableData" :checked-row-keys="checkedRowKeys"></slot>
          </div>
          <div class="page-table-toolbar-slot">
            <slot name="toolbar-extra"></slot>
            <n-tooltip
              v-if="
                isAdd &&
                ((addPermission && judgePermission(addPermission)) || judgePermissionByCode('CREATE', permission))
              "
              trigger="hover"
            >
              <template #trigger>
                <n-button aria-label="新增记录" @click="handleAdd">
                  <n-icon size="20px">
                    <IconMdiAdd />
                  </n-icon>
                </n-button>
              </template>
              点击添加
            </n-tooltip>
            <n-tooltip
              v-if="
                isExport &&
                ((exportPermission && judgePermission(exportPermission)) || judgePermissionByCode('EXPORT', permission))
              "
              trigger="hover"
            >
              <template #trigger>
                <n-button :loading="exportLoading" :aria-label="exportLabel || '导出 Excel'" @click="handleExport">
                  <n-icon size="20px">
                    <IconMdiMicrosoftExcel />
                  </n-icon>
                  <span v-if="exportLabel" style="margin-left: 6px">{{ exportLabel }}</span>
                </n-button>
              </template>
              导出当前筛选结果（Excel）
            </n-tooltip>
            <n-button-group>
              <HTemplatePrint
                v-if="printTemplate"
                :template="printTemplate"
                :data="checkedTableData"
                :columns="columns"
                :title="title || ($route.query.title as string)"
              ></HTemplatePrint>
              <PageDataTableColumnSettings
                v-if="columnConfigurable && columnSettingOptions.length"
                :options="columnSettingOptions"
                :value="internalVisibleColumnKeys"
                @update:value="updateVisibleColumnKeys"
                @reset="resetColumnSettings"
              />
              <h-full-screen :target="refPageTableContainer" aria-label="切换表格全屏"></h-full-screen>
            </n-button-group>
          </div>
        </div>
      </div>
    </template>

    <div v-if="!showHeader && showToolbar" class="page-table-toolbar">
      <Teleport :to="toToolbar" :disabled="!toToolbar">
        <div class="page-table-toolbar-actions">
          <div class="page-table-toolbar-slot">
            <slot name="activeBtn" :checked-table-data="checkedTableData" :checked-row-keys="checkedRowKeys"></slot>
          </div>
          <div class="page-table-toolbar-slot">
            <slot name="toolbar-extra"></slot>
            <n-tooltip
              v-if="
                isAdd &&
                ((addPermission && judgePermission(addPermission)) || judgePermissionByCode('CREATE', permission))
              "
              trigger="hover"
            >
              <template #trigger>
                <n-button aria-label="新增记录" @click="handleAdd">
                  <n-icon size="20px">
                    <IconMdiAdd />
                  </n-icon>
                </n-button>
              </template>
              点击添加
            </n-tooltip>
            <n-tooltip
              v-if="
                isExport &&
                ((exportPermission && judgePermission(exportPermission)) || judgePermissionByCode('EXPORT', permission))
              "
              trigger="hover"
            >
              <template #trigger>
                <n-button :loading="exportLoading" :aria-label="exportLabel || '导出 Excel'" @click="handleExport">
                  <n-icon size="20px">
                    <IconMdiMicrosoftExcel />
                  </n-icon>
                  <span v-if="exportLabel" style="margin-left: 6px">{{ exportLabel }}</span>
                </n-button>
              </template>
              导出当前筛选结果（Excel）
            </n-tooltip>
            <n-button-group>
              <PageDataTableColumnSettings
                v-if="columnConfigurable && columnSettingOptions.length"
                :options="columnSettingOptions"
                :value="internalVisibleColumnKeys"
                @update:value="updateVisibleColumnKeys"
                @reset="resetColumnSettings"
              />
              <h-full-screen :target="refPageTableContainer" aria-label="切换表格全屏"></h-full-screen>
            </n-button-group>
          </div>
        </div>
      </Teleport>
    </div>

    <AdvanceQueryForm
      v-if="$slots.queryForm"
      ref="refQueryForm"
      :storage-key="queryFormStorageKey"
      :size="size"
      inline
      :label-width="labelWidth"
      label-placement="left"
      :show-feedback="false"
      :query-configurable="queryConfigurable"
      :query-config-key="queryConfigurable ? resolvedQueryConfigKey : undefined"
      :query-config-options="queryConfigOptions"
      :default-visible-query-keys="defaultVisibleQueryKeys"
      :always-visible-query-keys="alwaysVisibleQueryKeys"
      @query-field-visibility-change="handleQueryFieldVisibilityChange"
    >
      <slot name="queryForm"></slot>
      <template #queryFormOperate>
        <n-form-item>
          <n-button :type="queryButtonType" @click="handleQuery">
            <template #icon>
              <n-icon size="20px">
                <IconMdiSearch />
              </n-icon>
            </template>
            查询
          </n-button>
        </n-form-item>
        <n-form-item v-if="showQueryResetButton">
          <n-button :type="resetButtonType" @click="handleResetForm">
            <template #icon>
              <n-icon size="20px">
                <IconMdiRestore />
              </n-icon>
            </template>
            重置
          </n-button>
        </n-form-item>
        <slot name="queryFormAppend"></slot>
      </template>
    </AdvanceQueryForm>
    <n-divider v-if="$slots.queryForm" style="margin: 0 0 15px 0"></n-divider>
    <slot name="data-view" :data="displayTableData" :loading="tableLoading" :pagination="cPagination" :on-page-change="handlePageChange" :on-page-size-change="handlePageSizeChange">
    <n-data-table
      v-bind="$attrs"
      ref="refPageTable"
      v-model:expanded-row-keys="expandedRowKeys"
      v-model:checked-row-keys="checkedRowKeys"
      class="flex-auto"
      remote
      striped
      :size="tableSize"
      min-height="160px"
      :flex-height="true"
      :single-line="false"
      :data="displayTableData"
      :loading="tableLoading"
      :pagination="cPagination"
      :row-key="rowKey"
      :columns="tableColumns"
      :scroll-x="resolvedScrollX"
      @update:sorter="handleSorterChange"
      @update:filters="handleFiltersChange"
      @update:page="handlePageChange"
      @update:page-size="handlePageSizeChange"
      @update:expanded-row-keys="expandedRowKey"
    >
      <template v-if="$slots.empty" #empty><slot name="empty"></slot></template>
    </n-data-table>
    </slot>
    <slot></slot>
    <HFileDialogPreview
      v-model:show="dialogFilePreview"
      :title="filePreviewTitle"
      :path="filePreviewPath"
      :preview-type="filePreviewType"
    ></HFileDialogPreview>
  </n-card>
</template>
<script lang="ts" setup>
import Sortable from 'sortablejs';
import { tableColumnsWidth } from './tableLayout';
import { computed, h, nextTick, onMounted, onActivated, onBeforeUnmount, onDeactivated, PropType, reactive, ref, triggerRef, watch, inject } from 'vue';
import {
  DataTableColumn,
  DataTableFilterState,
  DataTableSortState,
  NA,
  NButton,
  NIcon,
  NImage,
  NSpace,
  NTag,
  PaginationInfo,
  PaginationProps,
} from 'naive-ui';
import { cloneDeep, debounce, floor, get, isArray, isBoolean, omitBy, isNil, set } from 'lodash-es';
import { type } from '@/modules/sys/api/dict';
import dayjs from 'dayjs';
import {
  confirm,
  exportNaive2Excel,
  fileUrl,
  formatDateTime,
  formatDateValue,
  transformToArrayFormat,
  transformToTreeFormat,
} from '@/utils';
import Icon from '@/components/basic/DynamicIcon.vue';
import { useAppStore, useAuthStore } from '@/store';
import AdvanceQueryForm from '../../form/form/AdvanceQueryForm.vue';
import request from '@/utils/request.ts';
import HTemplatePrint from '@/components/basic/print/TemplatePrint.vue';
import { useRoute } from 'vue-router';
import PageDataTableColumnSettings from './PageDataTableColumnSettings.vue';
import {
  collectColumnSettingOptions,
  filterVisibleTableColumns,
  normalizeVisibleColumnKeys,
  type PageDataTableColumnSettingOption,
} from './pageDataTableColumnSettings';
import type {
  AdvanceQueryFormConfiguredOption,
  AdvanceQueryFormSettingOption,
  AdvanceQueryFormVisibilityChange,
} from '../../form/form/advanceQueryFormSettings';

const appStore = useAppStore();
const authStore = useAuthStore();
const $route = useRoute();

//缓存默认参数用于表单重置
let defaultParams: any = null;

/**
 * 数据更新事件
 * */
const emits = defineEmits([
  'add',
  'loadData',
  'update:data',
  'update:value',
  'rowEdit',
  'rowCopy',
  'rowView',
  'rowDelete',
  'updateSort',
  'rowKeyField',
  'expandedRowKeys',
  'updateCheckedRowKeys',
]);

/**
 * 表格容器
 * */
const refPageTableContainer = ref();
const refPageTable = ref();

const expandedRowKeys = ref<string[]>([]);
/**
 * 选择的行
 * */
const checkedRowKeys = ref<string[]>([]);

function clearCheckedRows() {
  checkedRowKeys.value = [];
}

/**
 * prop
 * */
const props = defineProps({
  rowKeyField: { type: String, default: 'id' },
  size: {
    type: String as PropType<'small' | 'medium' | 'large'>,
  },
  striped: { type: Boolean, default: true },
  bordered: { type: Boolean, default: true },
  segmented: {
    type: Object,
    default() {
      return {
        content: 'soft',
      };
    },
  },
  //页面标题
  title: { type: String },
  labelWidth: { type: String, default: '70' },
  //增加权限 可以为逗号分割的字符串
  addPermission: { type: String },
  //修改权限
  editPermission: { type: String },
  //删除权限
  deletePermission: { type: String },
  //导出权限
  exportPermission: { type: String },
  // 需要明显导出入口的页面可设置按钮文字，其他页面保留原布局
  exportLabel: { type: String, default: '' },
  //权限编码
  permission: { type: String },
  //是否显示顶部
  showHeader: { type: Boolean, default: true },
  //是否显示查询重置按钮
  showQueryResetButton: { type: Boolean, default: false },
  // 同一路由中的嵌套查询表单可使用独立的展开状态
  queryFormStorageKey: { type: String },
  //查询按钮类型
  queryButtonType: {
    type: String as PropType<'default' | 'tertiary' | 'primary' | 'info' | 'success' | 'warning' | 'error'>,
    default: 'primary',
  },
  //重置按钮类型
  resetButtonType: {
    type: String as PropType<'default' | 'tertiary' | 'primary' | 'info' | 'success' | 'warning' | 'error'>,
    default: 'error',
  },
  //是否显示工具栏
  showToolbar: { type: Boolean, default: true },
  //是否允许添加
  isAdd: { type: Boolean, default: true },
  //是否允许编辑
  isEdit: { type: Boolean, default: true },
  //是否允许复制
  isCopy: { type: Boolean, default: true },
  //是否允许删除
  isDelete: { type: Boolean, default: true },
  //是否允许导出
  isExport: { type: Boolean, default: true },
  //删除数据函数
  funApiDelete: { type: Function },
  // 批量删除
  funApiDeleteIds: { type: Function },
  // 选择列下拉菜单扩展项，onSelect(ids, rows, pageData)
  selectionOptions: {
    type: Array as PropType<Array<Record<string, any>>>,
    default: () => [],
  },
  enableSelection: { type: Boolean, default: false },
  clearSelectionOnFetch: { type: Boolean, default: false },
  //复制数据函数
  funApiCopy: { type: Function },
  //工具栏
  toToolbar: { type: String },
  printTemplate: { type: String }, //打印模版
  //展示操作列，当编辑或删除有启用才有效
  showOperate: { type: Boolean, default: true },
  //是否允许添加
  isTree: { type: Boolean, default: false },
  // 对当前已加载页在前端排序，分页仍由服务端处理。
  localSorter: { type: Boolean, default: false },
  //是否允许排序
  enableDrag: { type: Boolean, default: true },
  //初始化查询
  initQuery: { type: Boolean, default: true },
  pagination: { type: [Object, Boolean], default: true },
  initialPage: { type: Number, default: 1 },
  initialPageSize: { type: Number, default: 30 },
  resetPageOnSizeChange: { type: Boolean, default: false },
  autoScrollX: { type: Boolean, default: false },
  scrollX: { type: [Number, String] },
  //树字段配置
  treeConfig: {
    type: Object,
    default() {
      return { idKey: 'id', pIdKey: 'parentId' };
    },
  },
  //列数据
  columns: {
    type: Array,
    default() {
      return [];
    },
  },
  // 默认开启；页面可显式传 false 关闭列表列的显示/隐藏设置
  columnConfigurable: { type: Boolean, default: true },
  // 可选的本地存储键；不传时仅在当前组件生命周期内保存
  columnConfigKey: { type: String },
  // 可覆盖从 columns 自动提取的列名称，适用于动态标题或合并表头
  columnConfigOptions: {
    type: Array as PropType<PageDataTableColumnSettingOption[]>,
    default: () => [],
  },
  defaultVisibleColumnKeys: {
    type: Array as PropType<Array<string | number>>,
    default: () => [],
  },
  alwaysVisibleColumnKeys: {
    type: Array as PropType<Array<string | number>>,
    default: () => [],
  },
  // 业务表格可在通用筛选后修正合并表头等展示属性
  transformVisibleColumns: {
    type: Function as PropType<(columns: DataTableColumn[], visibleKeys: string[]) => DataTableColumn[]>,
  },
  // 默认关闭；显式开启后从查询表单项的 path 和 label 自动生成筛选项设置
  queryConfigurable: { type: Boolean, default: false },
  // 可选覆盖查询项缓存键；默认按当前路由 path 自动生成并持久化
  queryConfigKey: { type: String },
  // 普通查询项无需配置，仅用于日期范围等复合字段覆盖清理行为
  queryConfigOptions: {
    type: Array as PropType<AdvanceQueryFormConfiguredOption[]>,
    default: () => [],
  },
  defaultVisibleQueryKeys: {
    type: Array as PropType<Array<string | number>>,
    default: () => [],
  },
  alwaysVisibleQueryKeys: {
    type: Array as PropType<Array<string | number>>,
    default: () => [],
  },
  //查询参数
  params: { type: Object },
  //默认关闭；启用后表格只保留一个排序列，并按无排序、正序、倒序循环
  singleSorter: { type: Boolean, default: false },
  //数据
  data: { type: Array as PropType<any[] | undefined> },
  //获取数据异步函数
  fetchData: { type: Function },
});

const QUERY_CONFIG_STORAGE_PREFIX = '__APP_PAGE_DATA_TABLE_QUERY_FIELDS__';
const resolvedQueryConfigKey = computed(() => {
  const configuredKey = props.queryConfigKey?.trim();
  if (configuredKey) return configuredKey;
  const routePath = typeof $route.path === 'string' && $route.path ? $route.path : 'default';
  return `${QUERY_CONFIG_STORAGE_PREFIX}:${routePath}`;
});

/**
 * 加载状态
 * */
const tableLoading = ref(false);
/**
 * 导出状态
 * */
const exportLoading = ref(false);

/** 供业务页复用列表内置操作，可将默认工具栏入口合并到业务操作组。 */
defineExpose({
  handleQuery,
  handleRefresh,
  handlePageChange,
  handleExport,
  exportLoading,
  updateTableData,
  clearCheckedRows,
  updateVisibleColumnKeys,
  resetColumnSettings,
  updateVisibleQueryFieldKeys,
  resetQuerySettings,
});

const tableSize = computed(() => {
  return props.size ?? appStore.tableSize;
});

/**
 * 查询表单
 * */
const refQueryForm = ref();
let hiddenQueryFieldOptions: AdvanceQueryFormSettingOption[] = [];

function isActiveQueryValue(value: unknown) {
  if (value === null || value === undefined || value === '') return false;
  return !Array.isArray(value) || value.length > 0;
}

function clearQueryFieldValues(options: AdvanceQueryFormSettingOption[]) {
  let changed = false;
  options.forEach((option) => {
    option.clearPaths?.forEach((path) => {
      if (!props.params) return;
      if (isActiveQueryValue(get(props.params, path))) changed = true;
      set(props.params, path, null);
    });
    if (option.onClear?.({ params: props.params }) === true) changed = true;
  });
  return changed;
}

function handleQueryFieldVisibilityChange(payload: AdvanceQueryFormVisibilityChange) {
  const visible = new Set(payload.visibleKeys);
  hiddenQueryFieldOptions = payload.options.filter((option) => !visible.has(option.value));
  const hidden = new Set(payload.hiddenKeys);
  const changed = clearQueryFieldValues(payload.options.filter((option) => hidden.has(option.value)));
  if (payload.reason === 'user' && changed) {
    handleQuery();
  }
}

function updateVisibleQueryFieldKeys(value: Array<string | number>) {
  refQueryForm.value?.updateVisibleQueryFieldKeys?.(value);
}

function resetQuerySettings() {
  refQueryForm.value?.resetQuerySettings?.();
}
/**
 * 表头工具栏
 * */
const refHeaderToolbar = ref();

/**
 * 表格数据对象
 * */
// const tableData = shallowRef<any[]>([])
const tableData = ref<any[]>([]);
const localSortState = ref<DataTableSortState | null>(null);
const displayTableData = computed(() => {
  const state = localSortState.value;
  if (!props.localSorter || !state?.order) return tableData.value;
  const findColumn = (items: any[]): any => {
    for (const column of items) {
      if (column.key === state.columnKey) return column;
      if (column.children) {
        const match = findColumn(column.children);
        if (match) return match;
      }
    }
  };
  const column = findColumn(tableColumns.value || []);
  if (typeof column?.sorter !== 'function') return tableData.value;
  const direction = state.order === 'descend' ? -1 : 1;
  return [...tableData.value].sort((left, right) => direction * column.sorter(left, right));
});

/**
 * 选择的数据
 * */
const checkedTableData = computed<any[]>(() => {
  let list = tableData.value.filter((item) => checkedRowKeys.value?.includes(item[props.rowKeyField]));
  emits('update:value', list);
  return list;
});

/**
 * 列数组
 * */
const tableColumns = ref<DataTableColumn[]>();
const resolvedScrollX = computed(() =>
  props.autoScrollX ? tableColumnsWidth(tableColumns.value || [], props.scrollX) : props.scrollX,
);
const exportTableColumns = ref<DataTableColumn[]>();
const processedTableColumns = ref<DataTableColumn[]>([]);

const internalVisibleColumnKeys = ref<string[]>([]);
const columnSettingOptions = computed<PageDataTableColumnSettingOption[]>(() => {
  const derivedOptions = collectColumnSettingOptions(props.columns as any[]);
  const derivedKeys = new Set(derivedOptions.map((option) => option.value));
  const configuredOptions = props.columnConfigOptions
    .map((option) => ({ ...option, value: String(option.value) }))
    .filter((option) => derivedKeys.has(option.value));
  const options = configuredOptions.length ? configuredOptions : derivedOptions;
  const alwaysVisible = new Set(props.alwaysVisibleColumnKeys.map(String));
  return options.map((option) => ({ ...option, disabled: option.disabled || alwaysVisible.has(option.value) }));
});

const normalizedDefaultVisibleColumnKeys = computed(() => {
  const configuredDefaults = props.defaultVisibleColumnKeys.length
    ? props.defaultVisibleColumnKeys
    : columnSettingOptions.value.map((option) => option.value);
  return normalizeVisibleColumnKeys(
    [...configuredDefaults, ...props.alwaysVisibleColumnKeys],
    columnSettingOptions.value,
    configuredDefaults,
  );
});

function readStoredVisibleColumnKeys() {
  const defaults = normalizedDefaultVisibleColumnKeys.value;
  if (!props.columnConfigKey || typeof localStorage === 'undefined') return defaults;
  try {
    const stored = JSON.parse(localStorage.getItem(props.columnConfigKey) || 'null');
    return Array.isArray(stored)
      ? normalizeVisibleColumnKeys([...stored, ...props.alwaysVisibleColumnKeys], columnSettingOptions.value, defaults)
      : defaults;
  } catch {
    return defaults;
  }
}

function syncVisibleTableColumns() {
  const options = columnSettingOptions.value;
  const configurableKeys = new Set(options.map((option) => option.value));
  const visibleKeys = new Set(internalVisibleColumnKeys.value);
  const alwaysVisibleKeys = new Set(props.alwaysVisibleColumnKeys.map(String));
  const filteredColumns = filterVisibleTableColumns(
    processedTableColumns.value as any[],
    props.columnConfigurable ? configurableKeys : new Set<string>(),
    visibleKeys,
    alwaysVisibleKeys,
  ) as DataTableColumn[];
  const visibleColumns = props.transformVisibleColumns
    ? props.transformVisibleColumns(filteredColumns, internalVisibleColumnKeys.value)
    : filteredColumns;
  tableColumns.value = props.singleSorter
    ? (visibleColumns.map((column) => ({ ...column })) as DataTableColumn[])
    : visibleColumns;
  syncSingleSorterColumnState();
}

function updateVisibleColumnKeys(value: Array<string | number>) {
  const next = normalizeVisibleColumnKeys(
    [...value, ...props.alwaysVisibleColumnKeys],
    columnSettingOptions.value,
    normalizedDefaultVisibleColumnKeys.value,
  );
  internalVisibleColumnKeys.value = next;
  if (props.columnConfigKey && typeof localStorage !== 'undefined') {
    localStorage.setItem(props.columnConfigKey, JSON.stringify(next));
  }
  syncVisibleTableColumns();
}

function resetColumnSettings() {
  updateVisibleColumnKeys(normalizedDefaultVisibleColumnKeys.value);
}

/**
 * 默认分页对象
 * */
const tablePagination = reactive({
  page: Math.max(1, props.initialPage),
  pageCount: 1,
  itemCount: 0,
  extend: '',
  startRow: 0,
  pageSize: Math.max(1, props.initialPageSize),
  pageSizes: [10, 30, 50, 150],
  pageSlot: 6,
  showQuickJumper: true,
  showSizePicker: true,
  prefix(info: PaginationInfo) {
    if (tablePagination.extend) {
      return `${tablePagination.extend}`;
    } else {
      return `共计${info.itemCount} `;
    }
  },
});

/**
 * 表单是否换行
 * */
const queryOverflow = ref(false);

/**
 * 排序相关数据
 * */
const sortInfo = new Map();
type SingleSortOrder = 'ascend' | 'descend' | false;
const singleSorterState = reactive<{ columnKey: string | number | null; order: SingleSortOrder }>({
  columnKey: null,
  order: false,
});

function syncSingleSorterColumnState() {
  if (!props.singleSorter) return;
  tableColumns.value?.forEach((column: any) => {
    if (column.sorter) {
      column.sortOrder = column.key === singleSorterState.columnKey ? singleSorterState.order : false;
    }
  });
}

function syncSortInfoFromParams(params: any) {
  if (!params?.sortBy || sortInfo.size > 0) {
    return;
  }

  const sortByList = isArray(params.sortBy) ? params.sortBy : [params.sortBy];
  const sortDescList = isArray(params.sortDesc) ? params.sortDesc : [params.sortDesc];

  sortByList.forEach((columnKey: any, index: number) => {
    if (isNil(columnKey) || columnKey === '') {
      return;
    }

    const sortDesc = sortDescList[index] ?? sortDescList[0] ?? false;
    sortInfo.set(columnKey, Boolean(sortDesc));
  });
  if (props.singleSorter && sortInfo.size > 0) {
    const [columnKey, sortDesc] = Array.from(sortInfo.entries()).slice(-1)[0];
    singleSorterState.columnKey = columnKey;
    singleSorterState.order = sortDesc ? 'descend' : 'ascend';
    syncSingleSorterColumnState();
  }
}

/**
 * 分页计算信息
 * */
const cPagination = computed<false | PaginationProps | undefined>(() => {
  if (false === props.pagination) {
    return props.pagination;
  } else {
    Object.assign(tablePagination, props.pagination);
    return tablePagination;
  }
});

/**
 * 行数据key
 * */
function rowKey(rowData: any) {
  return rowData[props.rowKeyField];
}

/**
 * 权限判断
 * 当为设置权限的情况下允许操作
 * */
function judgePermission(permission?: string): boolean {
  if (!permission) {
    return true;
  }
  return authStore.hasPermission(permission);
}

/**
 * 根据编码权限判断
 * 当为设置权限的情况下允许操作
 * */
function judgePermissionByCode(code: string, permission?: string): boolean {
  if (!permission) {
    return true;
  }
  return authStore.hasPermission(`${permission}_ALL,${permission}_${code}`);
}

/**
 * 标题列
 * */
const columnTitle = computed<string>(() => {
  let column: any = props.columns?.find((item: any) => {
    return item.columnType === 'title';
  });
  return column?.key || 'name' || 'title' || props.rowKeyField;
});

/**
 * 递归处理初始数据
 * */
function processColumns(columns: any[], promises: any[]) {
  return columns.map((column, index) => {
    // 处理 dictType
    if (column.dictType) {
      promises.push({ column: column, promise: type(column.dictType) });
    }

    // 处理嵌套列（递归处理 children）
    if (column.children && column.children.length > 0) {
      column.children = processColumns(column.children, promises);
    } else {
      // Naive UI 按 children 属性是否存在识别分组，叶子列不能保留空 children。
      delete column.children;
    }

    // 默认列渲染
    defaultRenderColumn(column);

    return column;
  });
}

/**
 * 初始化列，加载使用到的字典数据
 *
 * */
async function initColumn() {
  let promises: any[] = [];
  const {
    funApiDeleteIds,
    showOperate,
    isDelete,
    isEdit,
    isCopy,
    funApiDelete,
    funApiCopy,
    selectionOptions,
    enableSelection,
  } = props;

  let columns = processColumns(props.columns, promises);

  const { addPermission, editPermission, deletePermission, permission } = props;

  const rowSelectionOptions: Array<Record<string, any>> = [
    ...(selectionOptions || []).map((option: Record<string, any>) => ({
      ...option,
      onSelect: (pageData: any[]) => option.onSelect?.(checkedRowKeys.value, checkedTableData.value, pageData),
    })),
  ];

  if (funApiDeleteIds) {
    rowSelectionOptions.push({
      label: '删除选中项',
      key: 'delSelect',
      onSelect: () => {
        let ids = checkedRowKeys.value;
        confirm({
          title: '即将删除！',
          content: `即将删除选择的「${ids.length}」条数据，请确认。`,
          onPositiveClick() {
            //有删除函数的情况下执行删除函数
            funApiDeleteIds(ids).then(() => {
              handleRefresh();
            });
          },
        });
      },
    });
  }

  if (enableSelection || funApiDeleteIds || rowSelectionOptions.length) {
    columns?.unshift({
      type: 'selection',
      fixed: 'left',
      width: 48,
      ...(rowSelectionOptions.length ? { options: rowSelectionOptions } : {}),
    });
  }

  //@ts-ignore
  if (showOperate && (isDelete || isEdit)) {
    //获取配置的自定义操作列
    //@ts-ignore
    let columnOperate = columns.find((column) => column.key === 'operate' && column.type === 'operate');

    //@ts-ignore
    let columnOperateRender = null;
    if (!columnOperate) {
      columnOperate = {
        key: 'operate',
        type: 'operate',
        title: '操作',
        resizable: true,
        align: 'center',
        fixed: 'right',
        width: '160px',
      };
      columns?.push(columnOperate);
    } else {
      //@ts-ignore
      columnOperateRender = columnOperate.render;
    }

    //防止重复添加内容到操作列
    //@ts-ignore
    if (!columnOperate._init) {
      //合并自定义操作列
      let len = 0;
      if (columnOperateRender) {
        len++;
      }
      if (
        isCopy &&
        ((editPermission && judgePermission(editPermission)) || judgePermissionByCode('EDIT', permission)) &&
        funApiCopy
      ) {
        len++;
      }
      if (
        isEdit &&
        ((editPermission && judgePermission(editPermission)) || judgePermissionByCode('EDIT', permission))
      ) {
        len++;
      }
      if (
        isDelete &&
        ((deletePermission && judgePermission(deletePermission)) || judgePermissionByCode('DELETE', permission)) &&
        funApiDelete
      ) {
        len++;
      }

      //@ts-ignore
      columnOperate.render = (row: any, index: number) => {
        let renderList = [];
        //本身的渲染函数
        //@ts-ignore
        if (columnOperateRender) {
          renderList.push(columnOperateRender(row, index));
        }
        if (
          isCopy &&
          ((addPermission && judgePermission(addPermission)) || judgePermissionByCode('CREATE', permission)) &&
          funApiCopy
        ) {
          renderList.push(
            h(
              NButton,
              {
                size: 'tiny',
                type: 'success',
                class: 'mr-3',
                onClick: () => handleRowCopy(row, index),
              },
              {
                default: () => '复制',
              },
            ),
          );
        }

        if (
          isEdit &&
          ((editPermission && judgePermission(editPermission)) || judgePermissionByCode('EDIT', permission))
        ) {
          renderList.push(
            h(
              NButton,
              {
                size: 'tiny',
                type: 'warning',
                class: 'mr-3',
                onClick: () => handleRowEdit(row, index),
              },
              {
                default: () => '编辑',
              },
            ),
          );
        }
        if (
          isDelete &&
          ((deletePermission && judgePermission(deletePermission)) || judgePermissionByCode('DELETE', permission)) &&
          funApiDelete
        ) {
          renderList.push(
            h(
              NButton,
              {
                size: 'tiny',
                type: 'error',
                onClick: () => handleRowDelete(row, index),
              },
              {
                default: () => '删除',
              },
            ),
          );
        }
        return renderList;
      };

      if (len > 0) {
        //@ts-ignore
        if (!columnOperate.width) {
          columnOperate.width = `${20 + len * 36}px`;
        }
      } else {
        //当无操作信息的时候隐藏列
        columnOperate.show = false;
      }

      //@ts-ignore
      columnOperate._init = true;
    }
  }

  //获取字典值
  let promiseDict = await Promise.all(
    promises.map((item: any) => item.promise.then((resp: any) => ({ column: item.column, data: resp.data }))),
  );
  promiseDict.forEach((item, index) => {
    item.column.dictOptions = item.data;
  });

  exportTableColumns.value = columns as DataTableColumn[];
  processedTableColumns.value = columns as DataTableColumn[];
  syncVisibleTableColumns();

  return promiseDict;
}

/**
 * 列默认渲染函数
 * */
function defaultRenderColumn(column: any) {
  const { enableDrag } = props;
  //有渲染函数的优先自定义渲染函数
  if (!(column.columnType || column.dictType || column.rowDrag)) return;

  column.render = function (row: any, index: any) {
    let value = get(row, column.key);
    let columnType = column.columnType;
    let render = [];

    //启用行排序
    if (enableDrag && column.rowDrag) {
      render.push(
        h(NIcon, { class: 'mr-1 sortHandle', style: { cursor: 'move' } }, () => {
          return h(Icon, { icon: 'c:DragVertical' });
        }),
      );
    }
    if (columnType == 'date') {
      render.push(formatDateValue(value));
    } else if (columnType == 'dateTime') {
      render.push(formatDateTime(value));
    } else if (columnType == 'daterange') {
      //时间
      if (value) {
        if (isArray(value)) {
          let t = value.map((it) => {
            return formatDateValue(it);
          });
          render.push(t.join('~'));
        } else {
          render.push(formatDateValue(value));
        }
      } else {
        render.push('--');
      }
    } else if (columnType == 'index') {
      column.align = 'center';
      //索引列,根据起始列递增
      render.push(tablePagination.startRow + index + 1);
    } else if (columnType == 'precision') {
      //数据精度
      if (value && column.precision) {
        render.push(floor(value, 4));
      } else {
        render.push(value || '--');
      }
    } else if (columnType == 'title') {
      //标题栏
      if (!column.ellipsis) {
        Object.assign(column, {
          ellipsis: {
            tooltip: { width: 600 },
          },
        });
      }
      // render.push(value || '--')
      // render.push(
      //     h(NButton,
      //         {
      //           text: true,
      //           tag: 'a',
      //           type: 'primary',
      //           onClick: () => handleRowView(row)
      //         },
      //         () => {
      //           // return h(NEllipsis, {style: {'max-width': '100%'}}, () => value)
      //           return value || '--'
      //         }
      //     )
      // )
      render.push(
        h(
          'a',
          {
            class: 'data-table-link',
            style: { cursor: 'pointer' },
            onClick: () => handleRowView(row, index),
          },
          value || '--',
        ),
      );
      render.push(
        h(NIcon, { size: 'small', class: 'mr-1', style: { cursor: 'pointer', verticalAlign: 'sub' } }, () => {
          return h(Icon, { icon: 'c:OpenInNew' });
        }),
      );
    } else if (columnType == 'icon') {
      //缩略图
      render.push(h(NIcon, null, { default: () => h(Icon, { icon: value || 'c:TimerSandEmpty' }) }));
    } else if (columnType == 'path') {
      //文件
      if (value) {
        let src = value;
        if (!/https?/.test(value)) {
          src = fileUrl(value);
        }
        render.push(h(NA, { href: src, type: 'default', target: '_blank' }, { default: () => '查看链接' }));
      } else {
        render.push(h('span', null, '--'));
      }
    } else if (columnType == 'preview') {
      //预览
      if (value) {
        let previewType = get(row, column.previewType);
        let previewTitle = get(row, column.previewTitle);
        render.push(
          h(
            NA,
            {
              type: 'default',
              onClick: () => {
                handleFilePreview(value, previewType, previewTitle);
              },
            },
            { default: () => '内容预览' },
          ),
        );
      } else {
        render.push(h('span', null, '--'));
      }
    } else if (columnType === 'image') {
      //缩略图
      column.align = 'center';
      if (value) {
        render.push(h(NImage, { src: fileUrl(value) + '?w=22', previewSrc: fileUrl(value), width: '22' }));
      } else {
        render.push(h('span', null, ''));
      }
    } else if (columnType == 'tag') {
      //tag标签
      value.split(',').map((tagKey: string) => {
        render.push(
          h(
            NTag,
            {
              style: {
                marginRight: '6px',
              },
              type: 'info',
            },
            {
              default: () => tagKey,
            },
          ),
        );
      });
    } else if (columnType == 'dict' || column.dictType || column.dictOptions) {
      //字典
      //布尔类型的数据
      if (isBoolean(value)) {
        value = value ? 1 : 0;
      }
      if (column.dictOptions) {
        let l: string[] = [];
        let temp = value?.toString()?.split(',') || [];
        if (column.isPrint === true) {
          for (const [key, value] of Object.entries(column.dictOptions)) {
            if (column.isRadio) {
              //单选
              if (temp.includes(key)) {
                l.push('●' + value);
              } else {
                l.push('○' + value);
              }
            } else {
              //多选
              if (temp.includes(key)) {
                l.push('☑' + value);
              } else {
                l.push('□' + value);
              }
            }
          }
          render.push(l.join(' '));
        } else {
          temp.forEach((t: any) => {
            l.push(column.dictOptions[t]);
          });
          render.push(l.join(','));
        }
      } else {
        render.push(value);
      }
    } else {
      render.push(value);
    }
    return render;
  };
}

/**
 * 排序事件
 * */
function handleSorterChange(options: DataTableSortState | DataTableSortState[] | null) {
  if (props.localSorter) {
    localSortState.value = (isArray(options) ? options.slice(-1)[0] : options) || null;
    return;
  }
  if (props.singleSorter) {
    const option = isArray(options) ? options.slice(-1)[0] : options;
    if (!option) {
      singleSorterState.columnKey = null;
      singleSorterState.order = false;
      sortInfo.clear();
      syncSingleSorterColumnState();
      handleQuery();
      return;
    }

    const columnKey = option.columnKey;
    const nextOrder: SingleSortOrder =
      singleSorterState.columnKey !== columnKey
        ? 'ascend'
        : singleSorterState.order === false
          ? 'ascend'
          : singleSorterState.order === 'ascend'
            ? 'descend'
            : false;

    singleSorterState.columnKey = nextOrder === false ? null : columnKey;
    singleSorterState.order = nextOrder;
    sortInfo.clear();
    if (nextOrder !== false) {
      sortInfo.set(columnKey, nextOrder === 'descend');
    }
    syncSingleSorterColumnState();
    handleQuery();
    return;
  }

  if (!options) {
    sortInfo.clear();
    handleQuery();
    return;
  }
  if (isArray(options)) {
    options.forEach((option) => {
      //@ts-ignore
      let order = option.order;
      //@ts-ignore
      let columnKey = option.columnKey;
      if (order === false) {
        sortInfo.delete(columnKey);
      } else {
        sortInfo.set(columnKey, order === 'descend');
      }
    });
  } else {
    //@ts-ignore
    let order = options.order;
    //@ts-ignore
    let columnKey = options.columnKey;
    if (order === false) {
      sortInfo.delete(columnKey);
    } else {
      sortInfo.set(columnKey, order === 'descend');
    }
  }
  handleQuery();
}

/**
 * 初始化排序
 * */
function initSorter(column: any) {
  if (column.sorter) {
    let columnKey = column.key;
    sortInfo.set(columnKey, true);
  }
}

/**
 * 过滤事件
 * */
function handleFiltersChange(filters: DataTableFilterState, initiatorColumn: DataTableColumn) {}

/**
 * 分页事件
 * */
function handlePageChange(page: number) {
  tablePagination.page = page;
  tableFetchData();
}

/**
 * 分页大小
 * */
function handlePageSizeChange(pageSize: number) {
  tablePagination.pageSize = pageSize;
  if (props.resetPageOnSizeChange) tablePagination.page = 1;
  tableFetchData();
}

/**
 * 点击展开行返回数据
 */
function expandedRowKey(array: any) {
  emits('expandedRowKeys', array);
}

function handleOnLoad(row: Record<string, unknown>) {
  const { fetchData, isTree, treeConfig } = props;
  if (!fetchData) {
    return new Promise<void>((resolve) => {
      resolve();
    });
  }

  return fetchData({ parentId: row.id, pageSize: -1 }).then((resp: any) => {
    if (resp.data && resp.data.list) {
      if (isTree) {
        let treeList = transformToTreeFormat(resp.data.list, treeConfig);
        row.children = treeList;
      } else {
        row.children = resp.data.list;
      }
    }
  });
}

/**
 * 点击查询，此时会重置分页
 * */
function handleQuery() {
  tableData.value = [];
  tableFetchData(true);
}

/**
 * 点击重置，对params进行清空
 * */
function handleResetForm() {
  const { params } = props;
  //清空参数对象
  params &&
    Object.keys(params).forEach((key) => {
      if (defaultParams) {
        params[key] = defaultParams[key];
      } else {
        params[key] = null;
      }
    });
  clearQueryFieldValues(hiddenQueryFieldOptions);
  tableFetchData(true);
}

/**
 * 刷新,适合修改删除数据的情况下使用
 * */
function handleRefresh() {
  tableData.value = [];
  //缓存参数为默认参数
  defaultParams = cloneDeep(props.params);
  tableFetchData();
}

let tableQueryGeneration = 0;
let tableQueryActive = true;
let tableQueryDisposed = false;
let tableQueryPending = false;
let tableQueryPendingReset = false;
let resumeTableQuery = false;
const debouncedTableFetchData = debounce(sourceTableFetchData, 100);

function currentTableQuery(generation: number) {
  return !tableQueryDisposed && tableQueryActive && generation === tableQueryGeneration;
}
function invalidateTableQuery() {
  tableQueryGeneration++;
  debouncedTableFetchData.cancel();
  tableQueryPending = false;
  tableLoading.value = false;
}
onDeactivated(() => {
  resumeTableQuery = tableQueryPending;
  tableQueryActive = false;
  invalidateTableQuery();
});
onActivated(() => {
  if (tableQueryDisposed) return;
  tableQueryActive = true;
  if (resumeTableQuery) {
    resumeTableQuery = false;
    tableFetchData(tableQueryPendingReset);
  }
});
onBeforeUnmount(() => {
  tableQueryDisposed = true; tableQueryActive = false; resumeTableQuery = false;
  invalidateTableQuery();
});

function tableFetchData(reset: boolean = false) {
  if (!tableQueryActive || tableQueryDisposed) return;
  // Invalidate the previous request immediately, including this debounce interval.
  const generation = ++tableQueryGeneration;
  tableQueryPending = true;
  tableQueryPendingReset = reset;
  if (props.clearSelectionOnFetch) {
    clearCheckedRows();
    tableData.value = [];
  }
  debouncedTableFetchData(reset, generation);
}

/**
 * 加载数据
 * @param reset 从第一页开始查询
 * */

function sourceTableFetchData(reset: boolean = false, generation: number = tableQueryGeneration) {
  if (!currentTableQuery(generation)) return;
  const { fetchData, isTree, treeConfig } = props;
  if (!fetchData) {
    tableQueryPending = false;
    // triggerRef(tableData)
    return;
  }
  if (reset) {
    tablePagination.page = 1;
  }

  let tempParams = omitBy(cloneDeep(props.params), (v) => isNil(v) || v === '');

  let params = {
    pageSize: tablePagination.pageSize,
    pageNum: tablePagination.page,
    ...tempParams,
  };

  syncSortInfoFromParams(params);

  if (sortInfo.size > 0) {
    //@ts-ignore
    params['sortBy'] = Array.from(sortInfo.keys());
    //@ts-ignore
    params['sortDesc'] = Array.from(sortInfo.values());
  }

  tableLoading.value = true;
  fetchData(params)
    .then((resp: any) => {
      if (!currentTableQuery(generation)) return;
      tableQueryPending = false;
      emits('loadData', resp);
      if (!currentTableQuery(generation)) return;
      emits('update:data', []);
      if (!currentTableQuery(generation)) return;
      if (resp == null) {
        tableLoading.value = false;
        return;
      }

      const data = resp.data ? (resp.data.data ? resp.data.data : resp.data) : resp;
      const list = data.list || (isArray(data) ? data : []);

      //是否为树形表格
      if (isTree) {
        let treeList = transformToTreeFormat(list, treeConfig);
        //默认展开第一级别
        if (treeList) {
          treeList.forEach((item: any) => {
            if (item.children) {
              expandedRowKeys.value = [item.id];
              return;
            }
          });
        }
        emits('update:data', treeList);
        tableData.value = treeList;
      } else {
        emits('update:data', list);
        tableData.value = list;
      }
      // triggerRef(tableData)

      tablePagination.pageCount = data.pages;
      tablePagination.extend = data.extend || '';

      tablePagination.itemCount = data.total || list.length;
      tablePagination.startRow = data.startRow || 0;
      tableLoading.value = false;

      initSortable();
    })
    .catch((e: any) => {
      if (!currentTableQuery(generation)) return;
      tableQueryPending = false;
      console.error('获取表格数据错误', e);
      tableLoading.value = false;
    });
}

/**
 * 更新表格数据
 * */
function updateTableData() {
  triggerRef(tableData);
}

/**
 * 删除行
 * */
function handleRowDelete(row: any, index: number) {
  const { rowKeyField, funApiDelete } = props;
  confirm({
    title: '即将删除！',
    content: `即将删除「${get(row, columnTitle.value)}」，请确认。`,
    to: appStore.fullscreen ? '.pageTable' : 'body',
    onPositiveClick() {
      //有删除函数的情况下执行删除函数
      if (funApiDelete) {
        funApiDelete(row[rowKeyField]).then(() => {
          emits('rowDelete', row, index);
          handleRefresh();
        });
      } else {
        emits('rowDelete', row, index);
      }
    },
  });
}

/**
 * 添加行
 * */
function handleAdd() {
  emits('add');
}

/**
 * 编辑行
 * */
function handleRowEdit(row: any, index: number) {
  emits('rowEdit', row, index);
}

/**
 * 复制行
 * */
function handleRowCopy(row: any, index: number) {
  const { rowKeyField, funApiCopy } = props;

  confirm({
    title: '即将复制！',
    content: `即将复制「${row[columnTitle.value]}」，请确认。`,
    to: appStore.fullscreen ? '.pageTable' : 'body',
    onPositiveClick() {
      //有删除函数的情况下执行删除函数
      if (funApiCopy) {
        funApiCopy(row[rowKeyField]).then(() => {
          emits('rowCopy', row);
          handleRefresh();
        });
      } else {
        emits('rowCopy', row);
      }
    },
  });
}

/**
 * 查看行
 * */
function handleRowView(row: any, index: number) {
  emits('rowView', row, index);
}

/**
 * 文件预览控制
 * */
const dialogFilePreview = ref(false);
const filePreviewTitle = ref();
const filePreviewPath = ref();
const filePreviewType = ref();

function handleFilePreview(value: string, previewType: string, previewTitle: string = '文件预览') {
  console.log(previewType, '预览类型');

  dialogFilePreview.value = true;
  filePreviewTitle.value = previewTitle;
  filePreviewPath.value = value;
  filePreviewType.value = previewType;
}

/**
 * 导出数据事件
 * */
function handleExport() {
  //有效的导出列
  let exportColumn = exportTableColumns.value?.filter((item: any) => {
    return item.excel !== false && !['operate', 'selection'].includes(item.type);
  });

  // 处理多级表头
  const headers = processHeaders(exportColumn);

  exportNaive2Excel(async (excel: any) => {
    excel.exportToExcel({
      headers: headers,
      data: await fetchExportData(headers),
      fileName: (props.title || 'excel-' + dayjs().format('YYYYMMDDHHssmm')) + '.xlsx',
    });
  });
}

/**
 * 递归处理多级别表头
 * */
function processHeaders(columns: DataTableColumn[] | undefined, level = 0, startColumn = 0) {
  let headers: any[] = [];
  let currentColumn = startColumn;
  if (!columns) return [];

  columns.forEach((column: any) => {
    if (column.children) {
      const childHeaders = processHeaders(column.children, level + 1, currentColumn);
      headers.push({
        ...column,
        level: level,
        startColumn: currentColumn,
        colspanHeader: childHeaders.length,
        rowspanHeader: 1,
      });
      headers = headers.concat(childHeaders);
      currentColumn += childHeaders.length;
    } else {
      headers.push({
        ...column,
        level: level,
        startColumn: currentColumn,
        colspanHeader: 1,
        rowspanHeader: 1,
      });
      currentColumn += 1;
    }
  });

  return headers;
}

/**
 * 格式化导出表格显示数据
 *
 * */
async function fetchExportData(columns: any) {
  const { fetchData } = props;
  let data = tableData.value;

  if (fetchData) {
    let params = {
      pageSize: -1,
      ...props.params,
    };

    exportLoading.value = true;

    await fetchData(params).then((resp: any) => {
      if (resp == null) return;
      const respData = resp.data ? (resp.data.data ? resp.data.data : resp.data) : resp;
      const list = respData.list || (isArray(respData) ? respData : []);
      data = list;
      exportLoading.value = false;

      //导出时记录日志
      request({
        url: 'log/save',
        method: 'post',
        data: {
          url: resp.url || location.href,
          data: JSON.stringify(params),
          description: `${props.title || ''}数据导出`,
        },
      });
    });
  } else {
    if (props.isTree) {
      data = transformToArrayFormat(tableData, props.treeConfig);
    }
  }
  return data;
}

/**
 * 初始化排序
 * */
function initSortable() {
  const { isTree, treeConfig } = props;
  nextTick(() => {
    if (!(refPageTable.value && refPageTable.value.$el)) return;
    const sortableElement = refPageTable.value.$el.querySelector('.n-data-table-tbody');
    const options = {
      handle: '.sortHandle',
      animation: 150,
      onUpdate: function (event: any) {
        if (isTree) {
          let treeList = transformToArrayFormat(tableData.value, treeConfig);
          const row = treeList.splice(event.oldIndex, 1)[0];
          if (row) {
            treeList.splice(event.newIndex, 0, row);
          }
          emits('updateSort', treeList);
        } else {
          const row = tableData.value.splice(event.oldIndex, 1)[0];
          if (row) {
            tableData.value.splice(event.newIndex, 0, row);
          }
          emits('updateSort', tableData.value);
        }
      },
    };
    if (sortableElement) {
      Sortable.create(sortableElement, options);
    }
  });
}

/**
 * 初始化拉一次数据
 * */
onMounted(() => {
  //缓存参数为默认参数
  defaultParams = cloneDeep(props.params);
  initColumn().then(() => {
    if (props.initQuery) {
      tableFetchData();
    }
  });
});

/**
 * 直接监听数据的变化
 * */
watch(
  () => props.data,
  (d) => {
    tableData.value = d || [];
  },
  {
    immediate: true,
    deep: true,
  },
);
/**
 * 监听是否允许拖动
 * */
watch(
  () => props.enableDrag,
  (c) => {
    initColumn().then(() => {
      if (props.initQuery) {
        tableFetchData();
      }
    });
  },
);

/**
 * 监听列变化
 * */
watch(
  () => [
    props.columnConfigurable,
    props.columnConfigKey,
    props.columnConfigOptions,
    props.defaultVisibleColumnKeys,
    props.alwaysVisibleColumnKeys,
  ],
  () => {
    internalVisibleColumnKeys.value = readStoredVisibleColumnKeys();
    syncVisibleTableColumns();
  },
  { immediate: true, deep: true },
);
watch(
  () => props.columns,
  () => {
    internalVisibleColumnKeys.value = readStoredVisibleColumnKeys();
    initColumn().then(() => {
      if (props.initQuery) {
        tableFetchData();
      }
    });
  },
  {
    immediate: true,
    deep: true,
  },
);
watch(
  () => checkedRowKeys.value,
  (newValue) => {
    emits('updateCheckedRowKeys', newValue);
  },
  { deep: true, immediate: true },
);
const selectedData = inject('selectedData', ref<any[]>([])) as any;
watch(
  () => selectedData.value,
  (newValue) => {
    checkedRowKeys.value = Array.isArray(newValue) ? newValue : [];
  },
  { deep: true, immediate: true },
);
</script>
<style>
.pageTable td a {
  text-decoration: underline;
}

.pageTable .n-data-table-td {
  align-items: center;
  /* vertical-align: text-top; */
}

.pageTable .n-data-table-base-table-body .n-data-table-table {
  border-bottom: 1px solid var(--n-border-color);
}

.pageTable .page-table-toolbar {
  position: fixed;
  right: 40px;
  top: 82px;
}

.pageTable .page-table-toolbar-actions,
.page-table-toolbar-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  min-width: 0;
  max-width: 100%;
  gap: 8px;
}

.pageTable .page-table-toolbar-slot,
.page-table-toolbar-slot {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  min-width: 0;
  max-width: 100%;
  gap: 8px;
}
.pageTable .page-table-header-toolbar {
  min-width: 0;
  max-width: 100%;
}

@media (max-width: 767px) {
  /* Keep all columns scrollable while leaving the operation column reachable. */
  .pageTable .n-data-table .n-data-table-th.n-data-table-th--fixed-left,
  .pageTable .n-data-table .n-data-table-td.n-data-table-td--fixed-left {
    position: static;
    z-index: auto;
  }

  .pageTable .n-data-table .n-data-table-th.n-data-table-th--fixed-left::after,
  .pageTable .n-data-table .n-data-table-td.n-data-table-td--fixed-left::after {
    display: none;
  }

  .pageTable > .n-card-header {
    flex-wrap: wrap;
    gap: 8px;
  }

  .pageTable > .n-card-header > .n-card-header__main,
  .pageTable > .n-card-header > .n-card-header__extra {
    flex: 0 0 100%;
    min-width: 0;
  }
}
</style>
