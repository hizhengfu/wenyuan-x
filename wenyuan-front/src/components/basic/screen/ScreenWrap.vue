<template>
  <div ref="screenWrapRef" class="screen-wrap" :style="{ visibility: loading ? 'hidden' : 'visible' }">
    <slot></slot>
  </div>
</template>
<script lang="ts" setup>
import { computed, onMounted, onUnmounted, PropType, provide, ref } from 'vue';
import { off, on, removeStyle, setStyle, addClass, removeClass } from '@/utils';
import { useEventBus, useTitle, useWindowSize } from '@vueuse/core';
import { useRoute } from 'vue-router';
import { createScreenRefreshScheduler, SCREEN_REFRESH_SCHEDULER_KEY } from './refresh/refreshScheduler';

const route = useRoute();

const { width: windowWidth, height: windowHeight } = useWindowSize();
/**
 * 大屏容器引用
 * */
const screenWrapRef = ref();

const isInit = ref(false);
//大屏容器是否存在
const screenInitProvide = provide('SCREEN-PROVIDE-INIT', isInit);
//大屏数据共享
const pageContainerProvide = provide('PAGE-CONTAINER-PROVIDE', screenWrapRef);

//大屏数据共享
const pageProvide = provide('PAGE-PROVIDE', {});
/**
 * 大屏缩放比例
 * */
const scaleRef = ref(1);

const pageSCALE = provide('PAGE-SCALE', scaleRef);

// 每个 ScreenWrap 持有独立调度器，组件可按自己的周期注册任务，避免全局事件串扰。
const refreshScheduler = createScreenRefreshScheduler();
provide(SCREEN_REFRESH_SCHEDULER_KEY, refreshScheduler);

const pageTitle = useTitle();

const props = defineProps({
  title: { type: String }, //页面title标题
  width: { type: Number, default: 1920 },
  height: { type: Number, default: 1080 },
  bgimage: { type: String },
  bgcolor: { type: String, default: '#100c2a' },
  zoomMode: { type: String as PropType<'auto' | 'width' | 'height' | 'full' | 'relative'>, default: 'auto' }, //"auto|width|height|full" 缩放模式
  intervalTime: { type: Number, default: 60000 },
});

const loading = ref(false);
/**
 * 根据宽高比例自适应
 * */
const resizeAuto = (w: number, h: number) => {
  const ratioX = windowWidth.value / w;
  const ratioY = windowHeight.value / h;
  if (ratioX < ratioY) {
    resizeWidth(w, h);
  } else {
    resizeHeight(w, h);
  }
};

/**
 * 宽度自适应
 * */
const resizeWidth = (w: number, h: number) => {
  const ratio = windowWidth.value / w;
  scaleRef.value = ratio;
  // setStyle(document.body, {
  setStyle(screenWrapRef.value, {
    width: `${w}px`,
    height: `${h}px`,
    transform: `scale(${ratio})`,
    transformOrigin: 'left top',
    backgroundSize: '100%',
    backgroundPosition: 'top',
    marginLeft: '0',
  } as CSSStyleDeclaration);
};
/**
 * 高度自适应
 * */
const resizeHeight = (w: number, h: number) => {
  const ratio = windowHeight.value / h;
  const gap = (windowWidth.value - w * ratio) / 2;
  scaleRef.value = ratio;
  // setStyle(document.body, {
  setStyle(screenWrapRef.value, {
    width: `${w}px`,
    height: `${h}px`,
    transform: `scale(${ratio})`,
    transformOrigin: 'left top',
    backgroundSize: `${(w / windowWidth.value) * ratio * 100}% 100%`,
    backgroundPosition: `${gap.toFixed(3)}px top`,
    marginLeft: `${gap.toFixed(3)}px`,
  } as CSSStyleDeclaration);
};
/**
 * 全屏拉伸
 * */
const resizeFull = (w: number, h: number) => {
  const ratioX = windowWidth.value / w;
  const ratioY = windowHeight.value / h;
  //setStyle(document.body, {
  setStyle(screenWrapRef.value, {
    width: `${w}px`,
    height: `${h}px`,
    transform: `scale(${ratioX}, ${ratioY})`,
    transformOrigin: 'left top',
    backgroundSize: '100% 100%',
    backgroundPosition: 'top',
    marginLeft: '0',
  } as CSSStyleDeclaration);
};

/**
 * 相对上下左右相对位置
 * */
const resizeRelative = (w: number, h: number) => {
  // setStyle(document.body, {
  setStyle(screenWrapRef.value.$el, {
    minWidth: '1920px',
    minHeight: '1086px',
    overflow: 'auto',
    position: 'relative',
  } as CSSStyleDeclaration);
};

/**
 * 原样显示
 * */
const resizeNone = () => {
  // setStyle(document.body, {
  setStyle(screenWrapRef.value.$el, {
    overflow: 'hidden',
    position: 'relative',
  } as CSSStyleDeclaration);
};

/**
 * 缩放方法
 * */
const resize = () => {
  const { zoomMode, width, height } = props;

  const _m = (route.query.m || zoomMode) as string;
  const _w = (route.query.w || width) as number;
  const _h = (route.query.h || height) as number;

  switch (_m) {
    case 'auto':
      resizeAuto(_w, _h);
      break;
    case 'width':
      resizeWidth(_w, _h);
      break;
    case 'height':
      resizeHeight(_w, _h);
      break;
    case 'full':
      resizeFull(_w, _h);
      break;
    case 'relative':
      resizeRelative(_w, _h);
      break;
    default:
      resizeNone();
      break;
  }
};
/**
 * 初始化样式
 * */
const initStyle = () => {
  const { zoomMode, width } = props;
  /**
   * html样式，隐藏横行滚动条
   * */
  let htmlStyle = {
    overflowX: 'hidden',
    overflowY: 'visible',
  };
  setStyle(document.documentElement, htmlStyle as CSSStyleDeclaration);

  //设置背景色
  let bodyStyle = {
    backgroundColor: props.bgcolor,
  };

  //设置背景
  if (props.bgimage) {
    let bgImage = props.bgimage;
    if (!/https?/.test(bgImage)) {
      bgImage = APP_BASE_URL + '/userfiles' + bgImage;
    }
    // @ts-ignore
    bodyStyle['backgroundImage'] = `url(${bgImage})`;
  }

  setStyle(document.body, bodyStyle as CSSStyleDeclaration);

  resize();

  addClass(document.body, 'screen-body-wrap');
  if (props.title) {
    pageTitle.value = props.title;
  }
};

/**
 * 重置样式
 * */
function resetStyle() {
  let htmlStyle = {
    overflowX: 'hidden',
    overflowY: 'visible',
  };
  removeStyle(document.documentElement, htmlStyle as CSSStyleDeclaration);
  let bodyStyle = {
    backgroundColor: '',
    backgroundImage: '',
  };
  removeStyle(document.body, bodyStyle as CSSStyleDeclaration);

  removeClass(document.body, 'screen-body-wrap');
}

const intervalRef = ref();

/**
 * 定时器事件分发
 * */
const screenIntervalBus = useEventBus<Date>('SCREEN-INTERVAL');

/**
 * 定时器，用timeout执行防止阻塞
 * */
function screenInterval() {
  intervalRef.value = setTimeout(() => {
    // notification({type: 'info', title: '数据更新', content: '后台数据自动更新'})
    screenIntervalBus.emit(new Date());
    screenInterval();
  }, props.intervalTime);
}

onMounted(async () => {
  isInit.value = true;
  initStyle();
  on(window, 'resize', () => {
    resize();
  });
  refreshScheduler.start();
  screenInterval();
});

onUnmounted(() => {
  isInit.value = false;
  off(window, 'resize', () => {});
  if (intervalRef.value) {
    clearTimeout(intervalRef.value);
  }
  refreshScheduler.dispose();
  resetStyle();
});
</script>
<style lang="scss">
.screen-body-wrap,
.screen-wrap {
  ::-webkit-scrollbar {
    /*水平滚动条*/
    max-width: 3px; /* 指定滚动条宽度 */
    /*垂直滚动条*/
    max-height: 3px; /* 指定滚动条宽度 */
  }

  /* 指定滚动条颜色 */

  ::-webkit-scrollbar-thumb {
    background-color: #888; /* 滚动条的颜色 */
  }

  /* 指定滚动条轨道颜色 */

  ::-webkit-scrollbar-track {
    background-color: #f1f1f1; /* 滚动条轨道的颜色 */
  }
}

.screen-wrap {
  overflow: hidden;
  display: block;
  height: 100%;

  //canvas {
  //  transform: translate3d(0, 0, 0);
  //}

  //video {
  //  transform: translate3d(0, 0, 0);
  //}

  //svg {
  //  will-change: transform;
  //}

  > * {
    user-select: none;
    touch-action: manipulation;
  }

  > div {
    position: absolute;
    box-sizing: border-box;
  }
}
</style>
