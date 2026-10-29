<template>
  <n-card
    :segmented="{
      content: 'soft',
      footer: 'soft',
    }"
    v-bind="$attrs"
    :size="size"
    class="wh-full flex flex-col pageIterator"
    content-style="flex: 1;display: flex;flex-direction: column;margin: 0;padding: 15px;overflow: auto;"
    :bordered="bordered"
  >
    <template v-if="showHeader" #header>
      <n-space align="center">
        <n-icon size="22px" style="display: flex; justify-self: center">
          <IconMdiViewCarousel />
        </n-icon>
        <span>
          {{ title || $route.query.title }}
        </span>
      </n-space>
    </template>
    <template v-if="showHeader && $slots['header-toolbar']" #header-extra>
      <n-button-group>
        <slot name="header-toolbar"></slot>
      </n-button-group>
    </template>
    <AdvanceQueryForm
      :size="size"
      :show-label="true"
      inline
      label-placement="left"
      :show-feedback="false"
      v-if="$slots.queryForm"
    >
      <slot name="queryForm"></slot>
      <template #queryFormOperate>
        <n-form-item>
          <n-button type="primary" @click="handleQuery">
            <template #icon>
              <n-icon size="20px">
                <IconMdiSearch />
              </n-icon>
            </template>
            查询
          </n-button>
        </n-form-item>
        <n-form-item>
          <n-button type="error" @click="handleResetForm">
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
    <div class="iterator-content">
      <slot :list="tableData"></slot>
    </div>
    <template v-if="showFooter" #footer>
      <n-flex justify="space-between">
        <span>共{{ cPagination.itemCount }}条</span>
        <n-pagination
          v-model:page="cPagination.page"
          :onUpdate:pageSize="handlePageSizeChange"
          :onUpdate:page="handlePageChange"
          :page-sizes="cPagination.pageSizes"
          :page-size="cPagination.pageSize"
          show-size-picker
          :page-count="cPagination.pageCount"
        />
      </n-flex>
    </template>
    <template v-if="$slots.other">
      <slot name="other"></slot>
    </template>
    <n-spin v-if="tableLoading" size="large" style="height: 100%; width: 100%" />
  </n-card>
</template>
<script lang="ts" setup>
import { onMounted, PropType, reactive, ref, triggerRef, computed, onUnmounted } from 'vue';
import { NButton, NIcon, NSpace, PaginationInfo, PaginationProps } from 'naive-ui';
import { cloneDeep, debounce, isArray } from 'lodash-es';
import { useAuthStore } from '@/store';
import AdvanceQueryForm from '@/components/form/form/AdvanceQueryForm.vue';
import { useRoute } from 'vue-router';

const $route = useRoute();

const authStore = useAuthStore();

//缓存默认参数用于表单重置
let defaultParams: any = null;

/**
 * 导出函数
 * */
defineExpose({
  handleQuery,
  handleRefresh,
  updateTableData,
});

/**
 * 数据更新事件
 * */
const emits = defineEmits(['loadData', 'update:data']);

/**
 * 表格容器
 * */
const refPageTable = ref();

const expandedRowKeys = ref<string[]>([]);

/**
 * 默认分页对象
 * */
const iteratorPagination = reactive({
  page: 1,
  pageCount: 1,
  itemCount: 0,
  pageSize: 30,
  pageSizes: [10, 30, 50, 150],
  showQuickJumper: true,
  showSizePicker: true,
  prefix(info: PaginationInfo) {
    return `共计${info.itemCount} `;
  },
});

/**
 * 分页计算信息
 * */
const cPagination = computed<any>(() => {
  if (false === props.pagination) {
    return props.pagination;
  } else {
    Object.assign(iteratorPagination, props.pagination);
    return iteratorPagination;
  }
});

/**
 * prop
 * */
const props = defineProps({
  rowKeyField: { type: String, default: 'id' },
  size: {
    type: String as PropType<'small' | 'medium' | 'large'>,
    default: 'medium',
  },
  striped: { type: Boolean, default: true },
  //页面标题
  title: { type: String, default: '数据迭代' },
  //显示边框
  bordered: { type: Boolean, default: true },
  //是否显示顶部
  showHeader: { type: Boolean, default: true },
  //是否显示底部
  showFooter: { type: Boolean, default: true },
  //是否显示工具栏
  showToolbar: { type: Boolean, default: true },
  //初始化查询
  initQuery: { type: Boolean, default: true },
  pagination: { type: [Object, Boolean], default: true },
  //查询参数
  params: { type: Object },
  //自动刷新
  autoRefresh: { type: Boolean, default: false },
  //刷新间隔时间
  autoRefreshIntervalTime: { type: Number, default: 5000 },
  //获取数据异步函数
  fetchData: { type: Function },
});

/**
 * 排序相关数据
 * */
const sortInfo = new Map();

/**
 * 加载状态
 * */
const tableLoading = ref(false);

/**
 * 查询表单
 * */
const refQueryForm = ref();

/**
 * 定时器应用
 * */
const refreshTime = ref();

/**
 * 表格数据对象
 * */
// const tableData = shallowRef<any[]>([])
const tableData = ref<any[]>([]);

/**
 * 点击查询，此时会重置分页
 * */
function handleQuery() {
  tableData.value = [];
  dataFetchData(true);
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
  dataFetchData(true);
}

/**
 * 分页条
 * */
function handlePageChange(page: number) {
  iteratorPagination.page = page;
  dataFetchData();
}

/**
 * 分页大小
 * */
function handlePageSizeChange(pageSize: number) {
  iteratorPagination.pageSize = pageSize;
  dataFetchData();
}

/**
 * 刷新,适合修改删除数据的情况下使用
 * */
function handleRefresh() {
  tableData.value = [];
  dataFetchData();
}


const dataFetchData = debounce(sourceTableFetchData, 100);

/**
 * 加载数据
 * @param reset 从第一页开始查询
 * */

function sourceTableFetchData(reset: boolean = false) {
  const { fetchData, autoRefresh, autoRefreshIntervalTime } = props;
  if (refreshTime.value) {
    clearTimeout(refreshTime.value);
  }
  if (!fetchData) {
    // triggerRef(tableData)
    return;
  }
  let params = {
    pageSize: iteratorPagination.pageSize,
    pageNum: reset ? 1 : iteratorPagination.page,
    ...props.params,
  };

  //@ts-ignore
  if (!params.sortBy) {
    if (sortInfo.size > 0) {
      //@ts-ignore
      params['sortBy'] = Array.from(sortInfo.keys());
      //@ts-ignore
      params['sortDesc'] = Array.from(sortInfo.values());
    }
  }

  tableLoading.value = true;
  fetchData(params)
    .then((resp: any) => {
      if (resp == null) return;

      const data = resp.data ? (resp.data.data ? resp.data.data : resp.data) : resp;
      const list = data.list || (isArray(data) ? data : []);

      tableData.value = list;
      // triggerRef(tableData)

      emits('update:data', tableData.value);
      emits('loadData', resp);

      iteratorPagination.pageCount = data.pages;

      iteratorPagination.itemCount = data.total || list.length;
      tableLoading.value = false;
    })
    .catch((e: any) => {
      console.error('获取表格数据错误', e);
      tableLoading.value = false;
    })
    .finally(() => {
      if (autoRefresh) {
        refreshTime.value = setTimeout(() => {
          dataFetchData();
        }, autoRefreshIntervalTime);
      }
    });
}

/**
 * 更新表格数据
 * */
function updateTableData() {
  triggerRef(tableData);
}

/**
 * 初始化拉一次数据
 * */
onMounted(() => {
  //缓存参数为默认参数
  defaultParams = cloneDeep(props.params);
  if (props.initQuery) {
    dataFetchData();
  }
});

onUnmounted(() => {
  if (refreshTime.value) {
    clearTimeout(refreshTime.value);
  }
});
</script>
<style lang="scss" scoped>
.iterator-content {
  position: relative;
  overflow: auto;
  flex: 1;
}
</style>
