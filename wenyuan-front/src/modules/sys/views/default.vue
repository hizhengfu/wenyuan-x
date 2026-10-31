<template>
  <n-layout ref="systemInfoContainerRef" embedded content-style="padding:24px">
    <n-layout-header>
      <n-card size="small" hoverable>
        <n-flex justify="space-between">
          <n-button text>
            <template #icon>
              <n-icon size="18">
                <icon-mdi-information />
              </n-icon>
            </template>
            系统:
            {{ systemInfo?.sys?.os }}
          </n-button>
          <n-button text>
            <template #icon>
              <n-icon size="18">
                <icon-mdi-ip-network-outline />
              </n-icon>
            </template>
            IP:
            {{ systemInfo?.sys?.ip }}
          </n-button>
          <n-button text>
            <template #icon>
              <n-icon size="18">
                <icon-mdi-clock />
              </n-icon>
            </template>
            运行时间:{{ systemInfo?.sys?.day }}
          </n-button>
        </n-flex>
      </n-card>
    </n-layout-header>
    <n-layout-content style="margin: 8px 0">
      <n-grid cols="4" x-gap="8" y-gap="8" style="min-height: 196px">
        <n-grid-item>
          <n-card hoverable>
            <n-flex vertical align="center" v-if="systemInfo?.cpu">
              <span>CPU使用率</span>
              <n-progress type="dashboard" gap-position="bottom" :percentage="systemInfo?.cpu?.used"></n-progress>
              <n-tooltip trigger="hover">
                <template #trigger>
                  <n-button text> {{ systemInfo?.cpu?.core }}</n-button>
                </template>
                {{ systemInfo?.cpu?.package }}<br />
                {{ systemInfo?.cpu?.core }}<br />
                {{ systemInfo?.cpu?.logic }}
              </n-tooltip>
            </n-flex>
          </n-card>
        </n-grid-item>
        <n-grid-item>
          <n-card hoverable>
            <n-flex vertical align="center" v-if="systemInfo?.memory">
              <span>内存使用率</span>
              <n-progress
                type="dashboard"
                gap-position="bottom"
                :percentage="systemInfo?.memory?.usageRate"
              ></n-progress>
              <n-tooltip trigger="hover">
                <template #trigger>
                  <n-button text> {{ systemInfo?.memory?.used }}/{{ systemInfo?.memory?.total }}</n-button>
                </template>
                总：{{ systemInfo?.memory?.total }}<br />
                可：{{ systemInfo?.memory?.available }}<br />
                使：{{ systemInfo?.memory?.used }}
              </n-tooltip>
            </n-flex>
          </n-card>
        </n-grid-item>
        <n-grid-item>
          <n-card hoverable>
            <n-flex vertical align="center" v-if="systemInfo?.swap">
              <span>交换区使用率</span>
              <n-progress type="dashboard" gap-position="bottom" :percentage="systemInfo?.swap?.usageRate"></n-progress>
              <n-tooltip trigger="hover">
                <template #trigger>
                  <n-button text> {{ systemInfo?.swap?.used }}/{{ systemInfo?.swap?.total }}</n-button>
                </template>
                总：{{ systemInfo?.swap?.total }}<br />
                可：{{ systemInfo?.swap?.available }}<br />
                已：{{ systemInfo?.swap?.used }}
              </n-tooltip>
            </n-flex>
          </n-card>
        </n-grid-item>
        <n-grid-item>
          <n-card hoverable>
            <n-flex vertical align="center" v-if="systemInfo?.disk">
              <span>磁盘使用率</span>
              <n-progress type="dashboard" gap-position="bottom" :percentage="systemInfo?.disk?.usageRate"></n-progress>
              <n-tooltip trigger="hover">
                <template #trigger>
                  <n-button text> {{ systemInfo?.disk?.used }}/{{ systemInfo?.disk?.total }}</n-button>
                </template>
                总：{{ systemInfo?.disk?.total }}<br />
                可：{{ systemInfo?.disk?.available }}<br />
                已：{{ systemInfo?.disk?.used }}
              </n-tooltip>
            </n-flex>
          </n-card>
        </n-grid-item>
        <n-grid-item :span="4">
          <n-card size="small" hoverable>
            <n-collapse>
              <n-collapse-item title="网络信息" name="network" v-if="systemInfo?.network">
                <n-table bordered :single-line="false">
                  <thead>
                    <tr>
                      <th>名称</th>
                      <th class="w-160px">MAC</th>
                      <th>IP4/IP6</th>
                      <th>数据量发送/接收</th>
                      <th>数据包发送/接收</th>
                      <th>速率</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="network in systemInfo?.network" :key="network.mac">
                      <td>{{ network.name }}({{ network.displayName }})</td>
                      <td>{{ network.mac }}</td>
                      <td>{{ network.ipv4 || '无' }}/{{ network.ipv6 || '无' }}</td>
                      <td>{{ network.bytesSent }}/{{ network.bytesRecv }}</td>
                      <td>{{ network.packetsSent }}/{{ network.packetsRecv }}</td>
                      <td>{{ network.speed }}</td>
                    </tr>
                  </tbody>
                </n-table>
              </n-collapse-item>
              <n-collapse-item title="存储信息" name="disk" v-if="systemInfo?.disk?.partitions">
                <n-table bordered :single-line="false">
                  <thead>
                    <tr>
                      <th>名称</th>
                      <th>挂载点</th>
                      <th>类型</th>
                      <th>总容量</th>
                      <th>可用</th>
                      <th>已用</th>
                      <th>使用率</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="partition in systemInfo?.disk?.partitions" :key="partition.name">
                      <td>{{ partition.name }}</td>
                      <td>{{ partition.mount }}</td>
                      <td>{{ partition.type }}</td>
                      <td>{{ partition.total }}</td>
                      <td>{{ partition.available }}</td>
                      <td>{{ partition.used }}</td>
                      <td>{{ partition.usageRate }}%</td>
                    </tr>
                  </tbody>
                </n-table>
              </n-collapse-item>
            </n-collapse>
          </n-card>
        </n-grid-item>
      </n-grid>
    </n-layout-content>
    <n-layout-footer>
      <n-grid cols="2" x-gap="8" y-gap="8">
        <n-grid-item>
          <n-card
            title="CPU使用率监控"
            size="small"
            style="height: 380px"
            hoverable
            :segmented="{
              content: true,
              footer: 'soft',
            }"
          >
            <HEChart ref="chartCpuUse" :option="optionCpuUsage"></HEChart>
          </n-card>
        </n-grid-item>
        <n-grid-item>
          <n-card
            title="内存使用率监控"
            size="small"
            style="height: 380px"
            hoverable
            :segmented="{
              content: true,
              footer: 'soft',
            }"
          >
            <HEChart ref="charMemorytUse" :option="optionMemoryUsage"></HEChart>
          </n-card>
        </n-grid-item>
      </n-grid>
    </n-layout-footer>
    <HWindowPrint fileName="系统运行监测报告.pdf"></HWindowPrint>
  </n-layout>
</template>
<script lang="ts" setup>
import { onActivated, onBeforeUnmount, onDeactivated, onMounted, reactive, ref, shallowRef } from 'vue';
import HEChart from '@/components/data/chart/EChart.vue';
import HWindowPrint from '@/components/basic/print/WindowPrint.vue';
import { info } from '../api/system';
import { cloneDeep } from 'lodash-es';

const systemInfo = ref();
const chartCpuUsage = ref<any[]>([]);
const chartMemoryUsage = ref<any[]>([]);
const timer = ref(); //定时器索引

const systemInfoContainerRef = ref();

const componentActive = ref(false);

/**
 * 默认选项
 * */
const defaultOptions = {
  tooltip: {
    trigger: 'axis',
  },
  xAxis: {
    type: 'time',
  },
  yAxis: {
    type: 'value',
  },
  grid: {
    left: '5%',
    right: '5%',
    top: 10,
    bottom: 20,
  },
  series: [
    {
      data: [] as any[],
      type: 'line',
      showSymbol: false,
      smooth: true,
    },
  ],
};

//cpu使用率参数
const optionCpuUsage = ref(cloneDeep(defaultOptions));
//内存使用率参数
const optionMemoryUsage = ref(cloneDeep(defaultOptions));

function fetchSystemInfo() {
  info().then((resp: any) => {
    if (resp.data) {
      systemInfo.value = resp.data;
      let time = resp.timestamp;
      if (chartCpuUsage.value.length > 500) {
        chartCpuUsage.value.shift();
        chartMemoryUsage.value.shift();
      }

      chartCpuUsage.value.push([time, resp.data.cpu.used]);
      chartMemoryUsage.value.push([time, resp.data.memory.usageRate]);

      optionCpuUsage.value.series[0].data = chartCpuUsage.value;

      optionMemoryUsage.value.series[0].data = chartMemoryUsage.value;
      if (componentActive.value) {
        timer.value = setTimeout(fetchSystemInfo, 1000);
      }
    }
  });
}

/**
 * 停止自动刷新*/
function stopAutoRefresh() {
  if (timer.value) {
    clearTimeout(timer.value);
    timer.value = null;
  }
  componentActive.value = false;
}

onMounted(() => {
  componentActive.value = true;
  fetchSystemInfo();
});

onBeforeUnmount(() => {
  stopAutoRefresh();
});

onActivated(() => {
  componentActive.value = true;
  fetchSystemInfo();
});

onDeactivated(() => {
  stopAutoRefresh();
});
</script>
