<template>
  <div class="weather-icon">
    <n-icon v-bind="$attrs" class="icon flex-center">
      <HIcon :icon="icons[iconType]" class="icon-inner" />
    </n-icon>
  </div>
</template>

<script lang="ts" setup>
import { PropType, computed } from 'vue';

const props = defineProps({
  // 天气图标基准, type和typeName
  determine: {
    type: String as PropType<'type' | 'typeName'>,
    default: 'type',
  },
  type: {
    type: String as PropType<weatherType>,
    default: 'clear',
  },
  typeName: {
    type: String as PropType<weatherName>,
    default: '晴',
  },
});

const iconType = computed(() => {
  const { determine, type, typeName } = props;
  switch (determine) {
    case 'type':
      return type;
    case 'typeName':
      return getEnumKey(Weather, typeName) || '';
    default:
      return type;
  }
});

// 天气字典
enum Weather {
  clear = '晴',
  overcast = '阴',
  cloudy = '多云',
  rainy = '雨',
  lightRain = '小雨',
  moderateRain = '中雨',
  heavyRain = '大雨',
  shower = '阵雨',
  thunderShower = '雷阵雨',
  snowy = '雪',
  moderateSnow = '中雪',
  heavySnow = '大雪',
  sleet = '雨夹雪',
}

//天气映射类型
type weatherType = keyof typeof Weather;
type weatherName = `${Weather}` | string;

// 天气图标
const icons: Record<weatherType | string, string> = {
  // https://icones.js.org/collection/mdi?s=weather
  clear: 'mdi:weather-sunny',
  overcast: 'mdi:weather-cloudy',
  cloudy: 'mdi:weather-cloudy',
  rainy: 'mdi:weather-rainy',
  lightRain: 'mdi:weather-rainy',
  moderateRain: 'mdi:weather-rainy',
  heavyRain: 'mdi:weather-rainy',
  shower: 'mdi:weather-pouring',
  thunderShower: 'mdi:weather-pouring',
  snowy: 'mdi:weather-snowy',
  moderateSnow: 'mdi:weather-snowy',
  heavySnow: 'mdi:weather-snowy-heavy',
  sleet: 'mdi:weather-snowy-rainy',
};

// 枚举value转key
function getEnumKey(enumObj: any, enumVal: any): string | undefined {
  return Object.keys(enumObj)[Object.values(enumObj).indexOf(enumVal)];
}
</script>
