<template>
  <v-chart
    ref="refChart"
    :option="option"
    v-bind="$attrs"
    :loading="loading"
    :autoresize="autoresize"
    style="min-height: 1px; min-width: 1px;"
  />
</template>

<script lang="ts" setup>
//@ts-nocheck
import dark from './dark.json';
import { use, registerTheme } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { UniversalTransition, LabelLayout } from 'echarts/features';
import {
  PieChart,
  BarChart,
  LineChart,
  GaugeChart,
  ScatterChart,
  GraphChart,
  LineSeriesOption,
  FunnelChart,
  PictorialBarChart,
  RadarChart,
  SunburstChart,
} from 'echarts/charts';
import { SurfaceChart, Bar3DChart, Line3DChart, Scatter3DChart } from 'echarts-gl/charts';
import { Grid3DComponent } from 'echarts-gl/components';

import {
  DatasetComponent,
  AxisPointerComponent,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponentOption,
  ToolboxComponent,
  ToolboxComponentOption,
  TooltipComponentOption,
  GridComponent,
  GraphicComponent,
  GraphicComponentOption,
  LegendComponentOption,
  PolarComponent,
  DataZoomComponent,
  VisualMapComponent,
  MarkLineComponent,
  MarkPointComponent,
} from 'echarts/components';
import VChart, { LOADING_OPTIONS_KEY, THEME_KEY } from 'vue-echarts';
import { ref, provide, watch, watchEffect, onMounted, onUnmounted } from 'vue';

use([
  CanvasRenderer,
  DatasetComponent,
  AxisPointerComponent,
  GraphicComponent,
  GraphChart,
  PieChart,
  BarChart,
  Bar3DChart,
  LineChart,
  ScatterChart,
  GaugeChart,
  FunnelChart,
  RadarChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  ToolboxComponent,
  GridComponent,
  UniversalTransition,
  LabelLayout,
  SurfaceChart,
  Grid3DComponent,
  Line3DChart,
  Scatter3DChart,
  PictorialBarChart,
  PolarComponent,
  SunburstChart,
  DataZoomComponent,
  VisualMapComponent,
  MarkLineComponent,
  MarkPointComponent,
]);
registerTheme('dark', dark);

const props = defineProps({
  option: {
    type: Object,
    default() {
      return undefined;
    },
  },
  params: {
    type: Object,
    default() {
      return {};
    },
  },
  autoresize: {
    type: Boolean,
    default: true,
  },
  autoAction: {
    //自动显示数据
    type: Boolean,
    default: false,
  },
  fetchData: { type: Function }, //数据获取api
});

const loading = ref(true);
const refChart = ref();
const loadingInfo = ref({ text: '加载中' });
provide(LOADING_OPTIONS_KEY, loadingInfo);

watchEffect(() => {
  if (!props.option) {
    loadingInfo.value = { text: '暂无数据' };
  } else {
    loadingInfo.value = { text: '加载完成' };
    loading.value = false;
    if (props.autoAction) {
      startActions();
    }
  }
});

let timer = null;

onMounted(() => {
  if (props.autoAction) {
    startActions();
  }
});
onUnmounted(() => {
  stopActions();
});

/**
 * 开始启动定时器，用于切换类似饼图的点击效果
 * */
function startActions() {
  const { option } = props;
  let dataIndex = -1;

  const dataLen = option?.series?.[0]?.data?.length || 0;
  if (!refChart.value || dataLen === 0) {
    return;
  }

  clearInterval(timer);

  timer = setInterval(() => {
    if (!refChart.value) {
      clearInterval(timer);
      return;
    }

    refChart.value.dispatchAction({
      type: 'downplay',
      seriesIndex: 0,
      dataIndex,
    });
    dataIndex = (dataIndex + 1) % dataLen;
    refChart.value.dispatchAction({
      type: 'highlight',
      seriesIndex: 0,
      dataIndex,
    });
    // refChart.value.dispatchAction({
    //   type: 'showTip',
    //   seriesIndex: 0,
    //   dataIndex
    // });
  }, 1000);
}

/**
 *  停止互动响应
 * */
function stopActions() {
  if (timer) {
    clearInterval(timer);
  }
}

function resize() {
  refChart.value?.resize?.();
}

defineExpose({ resize });
</script>
