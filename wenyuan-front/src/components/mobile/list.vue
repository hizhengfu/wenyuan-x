<template>
  <t-pull-down-refresh
    v-model="refreshing"
    :id="wrapId"
    :loading-bar-height="66"
    :loading-texts="['下拉刷新', '松开刷新', '正在刷新', '刷新完成']"
    @refresh="handleRefresh"
    @scrolltolower="handleScrolltolower"
  >
    <t-list :async-loading="asyncLoading" @scroll="handleLoad" style="overflow-y: auto; max-height: 100vh">
      <slot :list="refDataList"></slot>
    </t-list>

    <t-empty v-if="listPage.itemCount === 0" description="暂无数据，请下拉刷新" class="t-cell">
      <template #icon>
        <IconMdiInformationVariantCircleOutline class="t-icon"></IconMdiInformationVariantCircleOutline>
      </template>
    </t-empty>
    <t-footer class="my-3" v-if="listPage.itemCount > 0 && listPage.page === listPage.pageCount" :text="footerText" />
  </t-pull-down-refresh>
</template>
<script lang="ts" setup>
import { computed,  onMounted, ref, watch } from 'vue';
import { isArray } from 'lodash-es';
import { uuid } from '@/utils';
import { useDebounceFn } from '@vueuse/core';

const emit = defineEmits(['loadData']);
/**
 * 容器id
 * */
const wrapId = 'list-' + uuid();

const props = defineProps({
  pageSize: { type: Number, default: 10 },
  //查询参数
  params: { type: Object },
  //是否允许加载更多
  allowLoadMore: { type: Boolean, default: true },
  //获取数据异步函数
  fetchData: { type: Function, required: true },
});

/**
 * 导出函数
 * */
defineExpose({
  handleRefresh,
  handleLoadMore,
});

/**
 * 列表数据
 * */
const refDataList = ref<any[]>([]);

const asyncLoading = ref('');

/**
 * 列表分页数据
 * */
const listPage = ref({ page: 1, pageCount: 0, itemCount: 1 });

const footerText = computed(() => {
  return `数据已加载完成，合计加载${listPage.value.itemCount}条`;
});

/**
 * 防抖接口调用
 * */
const fetchListData = useDebounceFn(async () => {
  return await sourceTableFetchData();
}, 100);

/**
 * 数据查询函数
 * */
async function sourceTableFetchData() {
  const { fetchData, params, pageSize } = props;
  const { page } = listPage.value;
  const p = { pageNum: page, pageSize: pageSize, ...params };

  return fetchData(p).then((resp: any) => {
    const data = resp?.data?.data || resp?.data || resp;
    const list = data.list || (isArray(data) ? data : []);

    listPage.value.pageCount = data.pages || 1;
    listPage.value.itemCount = data.total || list.length;
    if (list.length > 0) {
      refDataList.value.push(...list);
    }
    emit('loadData', refDataList.value, data);
  });
}

/**
 * 刷新标记
 * */
const refreshing = ref(false);

/**
 * 刷新
 * */
function handleRefresh() {
  listPage.value.page = 1;
  listPage.value.pageCount = 0;
  listPage.value.itemCount = 1;

  refreshing.value = true;
  refDataList.value = [];
  asyncLoading.value = 'loading';
  fetchListData()
    ?.then(() => {
      refreshing.value = false;
    })
    ?.finally(() => {
      refreshing.value = false;
      asyncLoading.value = '';
    });
}

const loading = ref(false);

/**
 * 下拉加载
 * */
function handleLoad(scrollBottom: number) {
  if (!props.allowLoadMore) {
    asyncLoading.value = '';
    loading.value = false;
    return;
  }

  // 如果正在加载中，直接返回，避免重复加载
  if (loading.value) {
    return;
  }

  //到底部才加载
  if (scrollBottom > 50) return;
  const { page, pageCount } = listPage.value;
  //所有内容加载完成了
  if (page >= pageCount) {
    loading.value = false;
    asyncLoading.value = '';
    return;
  }
  listPage.value.page = page + 1;
  asyncLoading.value = 'load-more';
  loading.value = true;
  fetchListData()
    ?.then(() => {
      loading.value = false;
    })
    ?.finally(() => {
      loading.value = false;
      asyncLoading.value = '';
    });
}

function handleScrolltolower() {
  console.log('触底');
}

function handleLoadMore() {
  handleLoad(0);
}

onMounted(() => {
  handleRefresh();
});

watch(
  () => props.params,
  (nVal: any) => {
    handleRefresh();
  },
  {
    deep: true,
  },
);
</script>
