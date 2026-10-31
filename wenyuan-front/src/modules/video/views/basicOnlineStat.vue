<template>
  <n-card class="h-full" content-style="padding:0">
    <n-layout class="h-full" has-sider>
      <n-layout-sider
        :collapsed-width="20"
        :show-collapsed-content="false"
        show-trigger="arrow-circle"
        width="230"
        bordered
      >
        <n-layout-header bordered style="padding: 10px">
          <div class="tree-header">行政区域</div>
        </n-layout-header>
        <n-layout-content style="padding: 10px">
          <HAreaTree ref="refAreaTree" v-model:value="areaIds" label-field="name" @click:item="handleSelect"></HAreaTree>
        </n-layout-content>
      </n-layout-sider>
      <n-layout-content>
        <h-page-data-table
          ref="refPageTable"
          :fetch-data="basicVideoOnlineStat"
          v-bind="$attrs"
          :scroll-x="1200"
          :params="paramsForm"
          :columns="columns"
          :isAdd="false"
          :showOperate="false"
        >
          <template #queryForm>
            <n-form-item label="名称" path="name">
              <n-input v-model:value="paramsForm.name" placeholder="请输入名称" clearable></n-input>
            </n-form-item>
            <n-form-item label="级别" path="level">
              <h-dict-select
                v-model:value="paramsForm.level"
                type="four_cultural_relic_level"
                placeholder="请选择级别"
                filterable
                clearable
              ></h-dict-select>
            </n-form-item>
            <n-form-item label="类别" path="category">
              <h-dict-select
                v-model:value="paramsForm.category"
                type="four_cultural_relic_type"
                placeholder="请选择类别"
                filterable
                clearable
              ></h-dict-select>
            </n-form-item>
            <n-form-item label="批次" path="batch">
              <h-dict-select
                v-model:value="paramsForm.batch"
                type="basic_public_batch"
                placeholder="请选择批次"
                filterable
                clearable
              ></h-dict-select>
            </n-form-item>
            <n-form-item label="在线率" path="onlineRate">
              <n-input-group>
                <n-input-number
                  v-model:value="paramsForm.minRate"
                  placeholder="最小值"
                  clearable
                  :min="0"
                  :max="100"
                  :precision="0"
                  :show-button="false"
                  style="width: 100px"
                >
                  <template #suffix>%</template>
                </n-input-number>
                <n-input-group-label>-</n-input-group-label>
                <n-input-number
                  v-model:value="paramsForm.maxRate"
                  placeholder="最大值"
                  clearable
                  :min="0"
                  :max="100"
                  :precision="0"
                  :show-button="false"
                  style="width: 100px"
                >
                  <template #suffix>%</template>
                </n-input-number>
              </n-input-group>
            </n-form-item>
          </template>
        </h-page-data-table>
      </n-layout-content>
    </n-layout>
  </n-card>
</template>

<script lang="ts" setup>
import { basicVideoOnlineStat } from '../api/onlineRate';
import { ref } from 'vue';
import HAreaTree from '../components/AreaTree.vue';

const columns = [
  {
    key: 'name',
    title: '名称',
    fixed: 'left',
    resizable: true,
    ellipsis: { tooltip: true },
    titleAlign: 'center',
    width: '180px',
    align: 'center',
  },
  {
    key: 'level',
    title: '级别',
    align: 'center',
    titleAlign: 'center',
    width: '180px',
    dictType: 'four_cultural_relic_level',
    sorter:{multiple: 1}
  },
  {
    key: 'category',
    title: '类别',
    align: 'center',
    titleAlign: 'center',
    width: '150px',
    sorter:{multiple: 2}
  },
  {
    key: 'batch',
    title: '批次',
    align: 'center',
    titleAlign: 'center',
    width: '100px',
    dictType: 'basic_public_batch',
    sorter:{multiple: 3}
  },
  {
    key: 'addr',
    title: '地址',
    resizable: true,
    ellipsis: { tooltip: true },
    titleAlign: 'center',
    width: '150px',
    align: 'center',
  },
  {
    key: 'videoCount',
    title: '接入视频路数',
    align: 'center',
    titleAlign: 'center',
    width: '140px',
    sorter:{multiple: 4}
  },
  {
    key: 'videoOnlineCount',
    title: '在线数',
    align: 'center',
    titleAlign: 'center',
    width: '100px',
    sorter:{multiple: 5}
  },
  {
    key: 'videoOfflineCount',
    title: '离线数',
    align: 'center',
    titleAlign: 'center',
    width: '100px',
    sorter:{multiple: 6}
  },
  {
    key: 'videoRate',
    title: '在线率',
    align: 'center',
    titleAlign: 'center',
    width: '100px',
    sorter:{multiple: 7},
    render: (row: any) => {
      return row.videoRate ? `${row.videoRate}%` : '0%';
    },
  },
];

const refPageTable = ref();
const areaIds = ref<string[]>([]);

/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  level: '',
  category: '',
  batch: '',
  minRate: 0,
  maxRate: 100,
  areaIds: [] as string[],
});

/**
 * 行政区域树选择事件
 * */
function handleSelect(items: any[]) {
  paramsForm.value.areaIds = Array.isArray(items) ? items.map((item) => String(item.id)) : [];
  refPageTable.value.handleQuery();
}
</script>

<style lang="scss" scoped>
.tree-header {
  width: 100%;
  text-align: center;
  font-weight: 500;
  font-size: 16px;
}
</style>
