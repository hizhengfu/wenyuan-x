<template>
  <div>
    <n-grid x-gap="15" y-gap="15" cols="24" item-responsive style="height: 300px">
      <n-grid-item span="24 800:16">
        <n-grid cols="2 800:4" x-gap="15" y-gap="15">
          <template v-for="item in stockStat">
            <n-grid-item>
              <n-card hoverable>
                <n-statistic :label="item.title">
                  <n-number-animation :from="0" :to="item.value"></n-number-animation>
                  <template #prefix>
                    <n-icon>
                      <h-icon :icon="item.image"></h-icon>
                    </n-icon>
                  </template>
                  <template #suffix>
                    {{ item.valueSuffix }}
                  </template>
                </n-statistic>
              </n-card>
            </n-grid-item>
          </template>
        </n-grid>
        <n-card
          style="margin-top: 15px"
          size="small"
          title="文物库存情况"
          bordered
          hoverable
          :segmented="{ content: 'soft' }"
          rounded
        >
          <template #header-extra>
            <div class="md:block hidden">
              <n-input-group>
                <n-input-group-label>分析方式</n-input-group-label>
                <HDictSelect v-model:value="form.stockChartType" type="wms_analysis_type"></HDictSelect>
              </n-input-group>
            </div>
          </template>
          <HChart :option="chartOptions" style="height: 300px; width: 100%"></HChart>
        </n-card>
        <n-card
          style="margin-top: 15px"
          size="small"
          title="文物登记情况"
          bordered
          hoverable
          :segmented="{ content: 'soft' }"
          rounded
        >
          <template #header-extra>
            <div class="md:block hidden">
              <n-input-group>
                <n-input-group-label>分析方式</n-input-group-label>
                <HDictSelect v-model:value="form.registerChartType" type="wms_register_analysis_type"></HDictSelect>
              </n-input-group>
            </div>
          </template>
          <HChart :option="chartRegisterOptions" style="height: 300px; width: 100%"></HChart>
        </n-card>
      </n-grid-item>
      <n-grid-item span="24 800:8">
        <n-card
          size="small"
          title="最新登记文物"
          bordered
          hoverable
          :segmented="{ content: 'soft' }"
          style="height: 445px"
          content-style="overflow-y: auto;overflow-x: hidden;"
        >
          <template #header-extra>
            <router-link to="/wms/artifact/register?menuId=1893285066424012801&title=文物登记">更多</router-link>
          </template>
          <Vue3Marquee vertical style="width: 100%">
            <n-list bordered hoverable style="width: 100%">
              <template v-for="item in registerList">
                <n-list-item>
                  <n-flex justify="space-between">
                    <n-ellipsis>
                      <!--                    <n-time :time="item.createTime" type="relative"></n-time>-->
                      {{ item.projectCellName }}
                      {{ item.name }}
                      (
                      <HDictText :value="item.material" type="wms_artifact_material_type"></HDictText>
                      )
                    </n-ellipsis>
                    <div>{{ item.quantity }}件</div>
                  </n-flex>
                </n-list-item>
              </template>
            </n-list>
          </Vue3Marquee>
        </n-card>
        <n-card
          size="small"
          title="最新入库文物"
          bordered
          hoverable
          :segmented="{ content: 'soft' }"
          style="height: 447px; margin-top: 15px"
          content-style="overflow-y: auto;overflow-x: hidden;"
        >
          <template #header-extra></template>
          <Vue3Marquee vertical style="width: 100%">
            <n-list bordered hoverable style="width: 100%">
              <template v-for="item in stockList">
                <n-list-item>
                  <n-flex justify="space-between">
                    <n-ellipsis>
                      <!--                    <n-time :time="item.createTime" type="relative"></n-time>-->
                      {{ item.projectCellName }}
                      {{ item.name }}
                      (
                      <HDictText :value="item.material" type="wms_artifact_material_type"></HDictText>
                      )
                    </n-ellipsis>
                    <div>{{ item.quantity }}件</div>
                  </n-flex>
                </n-list-item>
              </template>
            </n-list>
          </Vue3Marquee>
        </n-card>
      </n-grid-item>
    </n-grid>
  </div>
</template>
<script lang="ts" setup>
import { list } from '../../api/artifact.ts';
import { statStock, chart } from '../../api/analysis.ts';
import { Vue3Marquee } from 'vue3-marquee';
import { ref, watch } from 'vue';

const stockStat = ref<any[]>([]);

const form = ref({
  stockChartType: 3,
  registerChartType: 3,
});

/**
 * 库存数据分析
 * */
function fetchStockStat() {
  statStock({}).then((resp: any) => {
    stockStat.value = resp.data || {};
  });
}

const registerList = ref<any[]>([]);

/**
 * 获取注册文物的列表
 * */
function fetchRegisterList() {
  list({ pageSize: 15, type: 0 }).then((resp: any) => {
    registerList.value = resp.data?.list || [];
  });
}

const loading = ref();
const chartOptions = ref({});

/**
 * 库存情况
 * */
function fetchChartData() {
  loading.value = true;
  const { stockChartType } = form.value;
  chart({ type: stockChartType })
    .then((resp) => {
      if (resp && resp.data) {
        chartOptions.value = resp.data[0] || {};
      }
    })
    .finally(() => {
      loading.value = false;
    });
}

const chartRegisterOptions = ref({});

/**
 * 登记情况
 * */
function fetchRegisterChartData() {
  loading.value = true;
  const { registerChartType } = form.value;
  chart({ type: registerChartType, stock: false })
    .then((resp) => {
      if (resp && resp.data) {
        chartRegisterOptions.value = resp.data[0] || {};
      }
    })
    .finally(() => {
      loading.value = false;
    });
}

const stockList = ref<any[]>([]);

/**
 * 获取注册文物的列表
 * */
function fetchStockList() {
  list({ pageSize: 15, type: 1 }).then((resp: any) => {
    stockList.value = resp.data?.list || [];
  });
}

fetchStockStat();
fetchStockList();
fetchRegisterList();
fetchChartData();
fetchRegisterChartData();

watch(
  () => form.value.stockChartType,
  () => {
    fetchChartData();
  },
);
watch(
  () => form.value.registerChartType,
  () => {
    fetchRegisterChartData();
  },
);
</script>
