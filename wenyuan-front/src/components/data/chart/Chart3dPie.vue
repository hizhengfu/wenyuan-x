<template>
  <EChart :option="options"
          @click="handleClick"
          @globalout="handlGlobalout"
          @legendselectchanged="handleLegendselectchanged"
          @mouseover="handleMouseover"></EChart>
</template>
<script setup>
import EChart from './EChart.vue'
import {watchEffect, ref} from 'vue';

/**
 * 参考实现 https://www.makeapie.cn/echarts_content/xojSDhHAMm.html
 *         https://www.makeapie.cn/echarts_content/xX9xcKXRjq.html
 * */
const props = defineProps({
  radius: {//圆环的半径，为0时表示是饼图
    type: Number, default: 0.5
  },
  autoRotate: {//是否自动旋转
    type: Boolean, default: true
  },
  items: {//数据
    type: Array,
    default() {
      return [
        {
          name: 'a', value: 10,
          label: {
            show: true,
          }
        },
        {
          name: 'b', value: 30,
          label: {
            show: true,
          }
        },
        {
          name: 'c', value: 20,
          label: {
            show: true,
          }
        },
        {
          name: 'd', value: 12,
          label: {
            show: true,
          }
        },
      ]
    }
  }
})

const options = ref()
/**
 * 饼图值的最大值
 * */
let maxValue = 0

watchEffect(() => {
  if (props.items) {
    options.value = initData(props.items)
  }
})

function initData(data) {
  let color = ['#3356d2', '#6a43f3', '#f3a722', '#5de8ec'];
  data.forEach((item, index) => {
    maxValue = Math.max(item.value, maxValue)
    if (!item.itemStyle) {
      item.itemStyle = {
        color: color[index],
        opacity: 1,
      };
    }
  });

  return getPie3D(data, props.radius);
}


/**
 * 生成扇形的曲面参数方程，用于 series-surface.parametricEquation
 * */
function getParametricEquation(startRatio, endRatio, isSelected, isHovered, k, h) {
  // 计算
  let midRatio = (startRatio + endRatio) / 2;

  let startRadian = startRatio * Math.PI * 2;
  let endRadian = endRatio * Math.PI * 2;
  let midRadian = midRatio * Math.PI * 2;

  // 如果只有一个扇形，则不实现选中效果。
  if (startRatio === 0 && endRatio === 1) {
    isSelected = false;
  }

  // 通过扇形内径/外径的值，换算出辅助参数 k（默认值 1/3）
  k = typeof k !== 'undefined' ? k : 1 / 3;

  // 计算选中效果分别在 x 轴、y 轴方向上的位移（未选中，则位移均为 0）
  let offsetX = isSelected ? Math.cos(midRadian) * 0.1 : 0;
  let offsetY = isSelected ? Math.sin(midRadian) * 0.1 : 0;

  // 计算高亮效果的放大比例（未高亮，则比例为 1）
  let hoverRate = isHovered ? 1.05 : 1;

  // 返回曲面参数方程
  return {
    u: {
      min: -Math.PI,
      max: Math.PI * 3,
      step: Math.PI / 32,
    },

    v: {
      min: 0,
      max: Math.PI * 2,
      step: Math.PI / 20,
    },

    x: function (u, v) {
      if (u < startRadian) {
        return offsetX + Math.cos(startRadian) * (1 + Math.cos(v) * k) * hoverRate;
      }
      if (u > endRadian) {
        return offsetX + Math.cos(endRadian) * (1 + Math.cos(v) * k) * hoverRate;
      }
      return offsetX + Math.cos(u) * (1 + Math.cos(v) * k) * hoverRate;
    },

    y: function (u, v) {
      if (u < startRadian) {
        return offsetY + Math.sin(startRadian) * (1 + Math.cos(v) * k) * hoverRate;
      }
      if (u > endRadian) {
        return offsetY + Math.sin(endRadian) * (1 + Math.cos(v) * k) * hoverRate;
      }
      return offsetY + Math.sin(u) * (1 + Math.cos(v) * k) * hoverRate;
    },

    z: function (u, v) {
      if (u < -Math.PI * 0.5) {
        return Math.sin(u);
      }
      if (u > Math.PI * 2.5) {
        return Math.sin(u) * (2 * h / maxValue);
      }
      return Math.sin(v) > 0 ? (2 * h / maxValue) : -1;
    },
  };
}


/**
 * 生成模拟 3D 饼图的配置项
 * @param internalDiameterRatio 0标识饼图
 * */
function getPie3D(pieData, internalDiameterRatio) {
  let series = [];
  let sumValue = 0;
  let startValue = 0;
  let endValue = 0;
  let legendData = [];
  let k =
    typeof internalDiameterRatio !== 'undefined'
      ? (1 - internalDiameterRatio) / (1 + internalDiameterRatio)
      : 1 / 3;

  // 新增标签 series @20210613
  let labelSeries = {
    id: 'labelSeries',
    type: 'bar3D',
    //zlevel:-9,
    barSize: [0.1, 0.1],
    data: [],
    label: {
      show: true,
      fontSize: 20,
      color: '#000',
      formatter: function (params) {
        return `${params.name}:${params.value[3]}`;
      },
    },
  };

  // 为每一个饼图数据，生成一个 series-surface 配置
  for (let i = 0; i < pieData.length; i++) {
    sumValue += pieData[i].value;

    let seriesItem = {
      name: typeof pieData[i].name === 'undefined' ? `series${i}` : pieData[i].name,
      type: 'surface',
      parametric: true,
      wireframe: {
        show: false,
      },
      pieData: pieData[i],
      pieStatus: {
        selected: false,
        hovered: false,
        k: k,
      },
    };

    if (typeof pieData[i].itemStyle != 'undefined') {
      let itemStyle = {};

      typeof pieData[i].itemStyle.color != 'undefined' ? (itemStyle.color = pieData[i].itemStyle.color) : null;
      typeof pieData[i].itemStyle.opacity != 'undefined'
        ? (itemStyle.opacity = pieData[i].itemStyle.opacity)
        : null;

      seriesItem.itemStyle = itemStyle;
    }
    series.push(seriesItem);
  }

  // 使用上一次遍历时，计算出的数据和 sumValue，调用 getParametricEquation 函数，
  // 向每个 series-surface 传入不同的参数方程 series-surface.parametricEquation，也就是实现每一个扇形。
  for (let i = 0; i < series.length; i++) {
    endValue = startValue + series[i].pieData.value;

    series[i].pieData.startRatio = startValue / sumValue;
    series[i].pieData.endRatio = endValue / sumValue;
    series[i].parametricEquation = getParametricEquation(
      series[i].pieData.startRatio,
      series[i].pieData.endRatio,
      false,
      false,
      k,
      series[i].pieData.value
    );

    startValue = endValue;

    legendData.push(series[i].name);

    // 判断增加 label 效果 @20210613
    if (pieData[i].label && pieData[i].label.show) {
      let labelRadian = (series[i].pieData.startRatio + series[i].pieData.endRatio) * Math.PI;
      labelSeries.data.push({
        name: series[i].name,
        value: [Math.cos(labelRadian), Math.sin(labelRadian), 3, series[i].pieData.value],
        itemStyle: {
          color: '#000',
          opacity: 1,
        },
      });
    }
  }

  // 补充一个透明的圆环，用于支撑高亮功能的近似实现。
  series.push({
    name: 'mouseoutSeries',
    type: 'surface',
    parametric: true,
    wireframe: {
      show: false,
    },
    itemStyle: {
      opacity: 0,
    },
    parametricEquation: {
      u: {
        min: 0,
        max: Math.PI * 2,
        step: Math.PI / 20,
      },
      v: {
        min: 0,
        max: Math.PI,
        step: Math.PI / 20,
      },
      x: function (u, v) {
        return Math.sin(v) * Math.sin(u) + Math.sin(u);
      },
      y: function (u, v) {
        return Math.sin(v) * Math.cos(u) + Math.cos(u);
      },
      z: function (u, v) {
        return Math.cos(v) > 0 ? 0.1 : -0.1;
      },
    },
  });

  // 将 labelSeries 添加进去 @20210613
  series.push(labelSeries);

  // 准备待返回的配置项，把准备好的 legendData、series 传入。
  let option = {
    //animation: false,
    legend: {
      data: legendData,
    },
    tooltip: {
      formatter: (params) => {
        if (params.seriesName !== 'mouseoutSeries') {
          return `${
            params.seriesName
          }<br/><span style="display:inline-block;margin-right:5px;border-radius:10px;width:10px;height:10px;background-color:${
            params.color
          };"></span>${option.series[params.seriesIndex].pieData?.value}`;
        }
      },
    },
    xAxis3D: {
      min: -1,
      max: 1,
    },
    yAxis3D: {
      min: -1,
      max: 1,
    },
    zAxis3D: {
      min: -1,
      max: 1,
    },
    grid3D: {
      show: false,
      boxHeight: 10,
      //top: '30%',
      bottom: '50%',
      viewControl: {
        autoRotate: props.autoRotate//自动旋转
      }
    },
    series: series,
  };
  return option;
}

// 监听鼠标事件，实现饼图选中效果（单选），近似实现高亮（放大）效果。
let selectedIndex = '';
let hoveredIndex = '';

/**
 * 监听点击事件，实现选中效果（单选）
 * */
function handleClick(params) {
  let option = options.value
  // 从 option.series 中读取重新渲染扇形所需的参数，将是否选中取反。
  let isSelected = !option.series[params.seriesIndex].pieStatus?.selected;
  let isHovered = option.series[params.seriesIndex].pieStatus?.hovered;
  let k = option.series[params.seriesIndex].pieStatus.k;
  let startRatio = option.series[params.seriesIndex].pieData?.startRatio;
  let endRatio = option.series[params.seriesIndex].pieData?.endRatio;
  let h = option.series[params.seriesIndex].pieData?.value;

  // 如果之前选中过其他扇形，将其取消选中（对 option 更新）
  if (selectedIndex !== '' && selectedIndex !== params.seriesIndex) {
    option.series[selectedIndex].parametricEquation = getParametricEquation(
      option.series[selectedIndex].pieData.startRatio,
      option.series[selectedIndex].pieData.endRatio,
      false,
      false,
      k,
      option.series[selectedIndex].pieData.value
    );
    option.series[selectedIndex].pieStatus.selected = false;
  }

  // 对当前点击的扇形，执行选中/取消选中操作（对 option 更新）
  option.series[params.seriesIndex].parametricEquation = getParametricEquation(
    startRatio,
    endRatio,
    isSelected,
    isHovered,
    k,
    h
  );
  option.series[params.seriesIndex].pieStatus.selected = isSelected;

  // 如果本次是选中操作，记录上次选中的扇形对应的系列号 seriesIndex
  isSelected ? (selectedIndex = params.seriesIndex) : null;
}

/**
 * 监听 mouseover，近似实现高亮（放大）效果
 * */
function handleMouseover(params) {
  let option = options.value
  // 准备重新渲染扇形所需的参数
  let isSelected;
  let isHovered;
  let startRatio;
  let endRatio;
  let k;
  let h;

  // 如果触发 mouseover 的扇形当前已高亮，则不做操作
  if (hoveredIndex === params.seriesIndex) {
    return;

    // 否则进行高亮及必要的取消高亮操作
  } else {
    // 如果当前有高亮的扇形，取消其高亮状态（对 option 更新）
    if (hoveredIndex !== '') {
      // 从 option.series 中读取重新渲染扇形所需的参数，将是否高亮设置为 false。
      isSelected = option.series[hoveredIndex].pieStatus.selected;
      isHovered = false;
      startRatio = option.series[hoveredIndex].pieData.startRatio;
      endRatio = option.series[hoveredIndex].pieData.endRatio;
      k = option.series[hoveredIndex].pieStatus.k;
      h = option.series[hoveredIndex].pieData.value;

      // 对当前点击的扇形，执行取消高亮操作（对 option 更新）
      option.series[hoveredIndex].parametricEquation = getParametricEquation(
        startRatio,
        endRatio,
        isSelected,
        isHovered,
        k,
        h
      );
      option.series[hoveredIndex].pieStatus.hovered = isHovered;

      // 将此前记录的上次选中的扇形对应的系列号 seriesIndex 清空
      hoveredIndex = '';
    }

    // 如果触发 mouseover 的扇形不是透明圆环，将其高亮（对 option 更新）
    if (params.seriesName !== 'mouseoutSeries') {
      // 从 option.series 中读取重新渲染扇形所需的参数，将是否高亮设置为 true。
      isSelected = option.series[params.seriesIndex].pieStatus?.selected;
      isHovered = true;
      startRatio = option.series[params.seriesIndex].pieData.startRatio;
      endRatio = option.series[params.seriesIndex].pieData.endRatio;
      k = option.series[params.seriesIndex].pieStatus.k;
      h = option.series[params.seriesIndex].pieData.value;

      // 对当前点击的扇形，执行高亮操作（对 option 更新）
      option.series[params.seriesIndex].parametricEquation = getParametricEquation(
        startRatio,
        endRatio,
        isSelected,
        isHovered,
        k,
        h
      );
      option.series[params.seriesIndex].pieStatus.hovered = isHovered;

      // 记录上次高亮的扇形对应的系列号 seriesIndex
      hoveredIndex = params.seriesIndex;
    }
  }
}


/**
 * 修正取消高亮失败的 bug
 * */
function handlGlobalout() {
  let option = options.value
  if (hoveredIndex !== '') {
    // 从 option.series 中读取重新渲染扇形所需的参数，将是否高亮设置为 true。
    let isSelected = option.series[hoveredIndex].pieStatus.selected;
    let isHovered = false;
    let k = option.series[hoveredIndex].pieStatus.k;
    let startRatio = option.series[hoveredIndex].pieData.startRatio;
    let endRatio = option.series[hoveredIndex].pieData.endRatio;
    let h = option.series[hoveredIndex].pieData.value;

    // 对当前点击的扇形，执行取消高亮操作（对 option 更新）
    option.series[hoveredIndex].parametricEquation = getParametricEquation(
      startRatio,
      endRatio,
      isSelected,
      isHovered,
      k,
      h
    );
    option.series[hoveredIndex].pieStatus.hovered = isHovered;

    // 将此前记录的上次选中的扇形对应的系列号 seriesIndex 清空
    hoveredIndex = '';
  }

}

/**
 * 新增监听图例事件，同步显示隐藏对应
 * */
function handleLegendselectchanged(params) {
  let option = options.value
  let seriesLength = option.series.length;
  for (let i = 0; i < option.series[seriesLength - 1].data.length; i++) {
    if (option.series[seriesLength - 1].data[i].name == params.name) {
      params.selected[params.name]
        ? (option.series[seriesLength - 1].data[i].itemStyle.opacity = 1)
        : (option.series[seriesLength - 1].data[i].itemStyle.opacity = 0);
    }
  }
}
</script>
