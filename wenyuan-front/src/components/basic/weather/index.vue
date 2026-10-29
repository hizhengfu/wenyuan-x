<template>
  <div class="weather">
    <n-space class="weather-inner" align="center" @click="showModal = true">
      <WeatherIcon
        v-if="showIcon"
        determine="typeName"
        :type-name="weatherData.info"
        :color="weatherIconColor"
        size="32"
      />
      <div class="weather-info">
        <span>{{ weatherData.info }}</span>
      </div>
      <div class="weather-trend" v-if="showWind">{{ weatherData.wind }}</div>
      <div class="weather-temperature">
        <span v-if="showTemperatureText">当前温度：</span>
        <span>{{ weatherData.temperature }}</span>
        <span>{{ weatherData.unit }}</span>
      </div>
    </n-space>
    <n-modal v-model:show="showModal" class="weather-modal">
      <n-card style="width: 1024px" :bordered="false" size="small" role="dialog" aria-modal="true">
        <template #header>
          <div class="modal-header">
            <!-- <span class="title">天气预报详情</span> -->
            <n-icon class="icon-close" size="30" color="#fff" @click="showModal = false">
              <IconMdiClose class="icon-inner"></IconMdiClose>
            </n-icon>
          </div>
        </template>
        <template #default>
          <div class="modal-main flex">
            <div class="brief">
              <n-flex justify="space-between" class="refresh">
                <n-flex class="refresh-left">
                  <n-icon size="22px" color="#fff">
                    <IconMdiClockTimeFiveOutline />
                  </n-icon>
                  <span>{{ dayjs(weatherData.publishTime).format('HH:mm') }} 更新</span>
                </n-flex>
                <div class="refresh-right">
                  <img
                    v-if="weatherData.warnImgUrl"
                    :src="weatherData.warnImgUrl"
                    class="alarm-img"
                    @click="openPage(weatherData.warnUrl)"
                  />
                </div>
              </n-flex>
              <div class="sun">
                <img src="./images/icon_rise.png" class="sun-icon" />
                <span class="sun-city">{{ weatherData.city }}</span>
                <span class="sun-temp">{{ weatherData.temperature + weatherData.unit }}</span>
              </div>
              <div class="real">
                <n-grid x-gap="4" y-gap="6" cols="4">
                  <n-gi class="real-gi">
                    <n-flex justify="center" align="center" vertical class="real-item">
                      <img src="./images/icon_rain.png" class="real-icon icon-rain" />
                      <span>降水量</span>
                      <span>{{ weatherData.rain }}</span>
                    </n-flex>
                  </n-gi>
                  <n-gi class="real-gi">
                    <n-flex justify="center" align="center" vertical class="real-item">
                      <img src="./images/icon_wind.png" class="real-icon icon-rain" />
                      <span>{{ weatherData.wind }}</span>
                      <span>{{ weatherData.speed }}</span>
                    </n-flex>
                  </n-gi>
                  <n-gi class="real-gi">
                    <n-flex justify="center" align="center" vertical class="real-item">
                      <img src="./images/icon_sd.png" class="real-icon icon-rain" />
                      <span>相对湿度</span>
                      <span>{{ weatherData.humidity }}</span>
                    </n-flex>
                  </n-gi>
                  <n-gi class="real-gi">
                    <n-flex justify="center" align="center" vertical class="real-item">
                      <img src="./images/icon_tgtemp.png" class="real-icon icon-rain" />
                      <span>体感温度</span>
                      <span>{{ weatherData.feel }}</span>
                    </n-flex>
                  </n-gi>
                  <n-gi class="real-gi" span="4">
                    <n-flex class="real-item" vertical>
                      <div class="real-air flex">
                        <img src="./images/icon_air.png" class="real-icon icon-air" />
                        <span>空气质量：</span>
                        <span>{{ weatherData.air }}</span>
                      </div>
                      <div class="real-comfort flex" align="center">
                        <img src="./images/icon_sds.png" class="real-icon icon-comfort" />
                        <span>舒适度：</span>
                        <span>{{ dealComfort(weatherData.comfort) }}</span>
                      </div>
                    </n-flex>
                  </n-gi>
                </n-grid>
              </div>
            </div>
            <div class="detail">
              <n-flex class="publish" justify="end">
                <span>发布时间：{{ dayjs(weatherData.publishTime).format('MM-DD') }}</span>
              </n-flex>
              <n-flex class="day-wrap">
                <n-flex
                  v-for="(e, i) in predictData"
                  :key="i"
                  class="day-col"
                  :class="daySelected === i ? 'day-selected' : ''"
                  vertical
                  justify="center"
                  @click="handleDaySelect(i)"
                >
                  <div class="day-desc flex-col-center">
                    <span>{{ dayjs(e.date).format('MM/DD') }}</span>
                    <span>{{ dayjs(e.date).format('周dd') }}</span>
                  </div>
                  <div class="day flex-col-center">
                    <WeatherIcon
                      determine="typeName"
                      :type-name="e.day.weather.info"
                      :color="modalIconColor"
                      size="32"
                    />
                    <span>{{ e.day.weather.info }}</span>
                    <span>{{ e.day.wind.direct }}</span>
                    <span>{{ e.day.wind.power }}</span>
                    <span class="day-temp" :class="handleTempClass(e.day.weather.temperature)"
                      >{{ e.day.weather.temperature }}℃</span
                    >
                  </div>
                  <div class="night flex-col-center">
                    <span class="day-temp" :class="handleTempClass(e.night.weather.temperature)">
                      {{ e.night.weather.temperature }}℃
                    </span>
                    <WeatherIcon
                      determine="typeName"
                      :type-name="e.night.weather.info"
                      :color="modalIconColor"
                      size="32"
                    />
                    <span>{{ e.night.weather.info }}</span>
                    <span>{{ e.night.wind.direct }}</span>
                    <span>{{ e.night.wind.power }}</span>
                  </div>
                </n-flex>
              </n-flex>
            </div>
          </div>
        </template>
      </n-card>
    </n-modal>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive, computed, watch } from 'vue';
import dayjs from 'dayjs';
import { weather } from './api/weather';
import WeatherIcon from './WeatherIcon.vue';
import { useWeatherStore } from '@/store/index.ts';
const weatherStore = useWeatherStore();


const props = defineProps({
  weatherId: { type: String, default: '', required: true }, // 地区id
  iconColor: { type: String, default: '#0095FF' }, // 图标颜色
  modalIconColor: { type: String, default: '#fff' }, // 模态框图标颜色
  showIcon: { type: Boolean, default: true }, //是否展示天气图标
  showWind: { type: Boolean, default: true }, //是否展示风向
  showTemperatureText: { type: Boolean, default: true }, // 是否展示温度文本
});

const weatherIconColor = computed(() => props.iconColor);
const modalIconColor = computed(() => props.modalIconColor);
const weatherData = reactive({
  city: '', // 城市
  air: '', // 空气质量
  temperature: '', // 实时温度
  info: '', // 实时天气状况
  rain: '', // 降水量
  wind: '', // 风向
  speed: '', // 风向等级
  humidity: '', // 相对湿度
  feel: '', // 体感温度
  comfort: '', // 舒适度
  unit: '℃', // 温度单位
  publishTime: '', // 发布时间
  warnImgUrl: '', // 预警图片来源
  warnUrl: '', // 预警图片跳转
});

const predictData = ref<any[]>([]); // 每周天气预报详情
const showModal = ref<boolean>(false); // 显示模态框
const daySelected = ref<number>(0); // 当前每周天气预报的选择

// 获取天气数据
function loadWeatherData(id: string) {
  weather(id).then((resp: any) => {
    const data = resp.data || {};
    weatherData.city = dealData(data?.real?.station.city);
    weatherData.air = dealData(data?.air?.text);
    weatherData.temperature = dealData(data?.real?.weather?.temperature);
    weatherData.info = dealData(data?.real?.weather?.info);
  
    weatherData.rain = dealData(data?.real?.weather?.rain);
    weatherData.wind = dealData(data?.real?.wind?.direct);
    weatherData.speed = dealData(data?.real?.weather?.speed);
    weatherData.humidity = dealData(data?.real?.weather?.humidity);
    weatherData.feel = dealData(data?.real?.weather?.feelst);
    weatherData.comfort = dealData(data?.real?.weather?.icomfort);
    weatherData.publishTime = dealData(data?.real?.publish_time);
    weatherData.warnImgUrl = dealData(data?.real?.warn?.pic, '');
    weatherData.warnUrl = dealImgUrl(data?.real?.warn?.url, 'http://www.nmc.cn');
    data?.predict?.detail.forEach((e: any) => {
      e.day.weather.info = dealData(e?.day?.weather?.info, '');
      e.day.wind.direct = dealData(e?.day?.wind?.direct, '');
      e.day.wind.power = dealData(e?.day?.wind?.power, '');
      e.day.weather.temperature = dealData(e?.day?.weather?.temperature);
      e.night.weather.info = dealData(e?.night?.weather?.info, '');
      e.night.wind.direct = dealData(e?.night?.wind?.direct, '');
      e.night.wind.power = dealData(e?.night?.wind?.power, '');
      e.night.weather.temperature = dealData(e?.night?.weather?.temperature);
    });
    predictData.value = data.predict?.detail || [];
    weatherStore.updateWeatherInfo(id,weatherData.info)
  });
}

// 每周天气预报点击
function handleDaySelect(index: number) {
  daySelected.value = index;
}

// 打开指定页面
function openPage(url: string) {
  url && window.open(url);
}

// 天气预报无数据处理, 预报数据接口无数据会返回9999
function dealData(data: any, emptyStr: string = '--') {
  return !data || data == '9999' ? emptyStr : data;
}

// 处理天气图片路径
function dealImgUrl(url: string, prefix: string = '') {
  return !url || url == '9999' ? '' : prefix + url;
}

// 舒适度
function dealComfort(comfort: number | string) {
  comfort = Number(comfort);
  switch (comfort) {
    case 0:
      return '舒适，最可接受';
    case 1:
      return '温暖，较舒适';
    case 2:
      return '暖，不舒适';
    default:
      return '舒适，最可接受';
  }
}

// 温度颜色样式
function handleTempClass(t: number) {
  switch (true) {
    case t > 35:
      return 'red';
    case 30 < t && t <= 35:
      return 'orange';
    case 25 < t && t <= 30:
      return 'orangeLight';
    case 20 < t && t <= 25:
      return 'yellow';
    case 15 < t && t <= 20:
      return 'greenLight';
    case 10 < t && t <= 25:
      return 'green';
    case 5 < t && t <= 10:
      return 'blueLight';
    case 0 < t && t <= 5:
      return 'blue';
    case t <= 0:
      return 'blueDark';
    default:
      return 'yellow';
  }
}

 watch(
   () => props.weatherId,
   (v) => {
     v && loadWeatherData(v);
   },
   { immediate: true },
 );
</script>

<style lang="scss" scoped>
.weather {
  .weather-inner {
    cursor: pointer;
  }
}
.weather-modal {
  background-image: url('./images/weather_bg.png');
  background-size: 100% 100%;
  .modal-header {
    position: relative;
    display: flex;
    justify-content: center;
    text-align: center;
    .title {
      font-size: 24px;
    }
    .icon-close {
      position: absolute;
      top: 0;
      right: 10px;
      cursor: pointer;
    }
  }
  .modal-main {
    height: 520px;
    .brief {
      display: flex;
      flex-direction: column;
      width: 400px;
      height: 100%;
      margin-right: 20px;
      .refresh {
        padding-top: 8px;
        .refresh-left {
          gap: 0 !important;
        }
        .refresh-right {
          height: 32px;
          .alarm-img {
            width: 42px;
            height: 100%;
            cursor: pointer;
          }
        }
      }
      .sun {
        position: relative;
        width: 100%;
        height: 217px;
        margin: 20px 0 25px 0;
        background-image: url('./images/icon_sun.png');
        background-size: 100% 100%;
        .sun-city {
          position: absolute;
          top: 20%;
          left: 50%;
          transform: translateX(-50%);
          font-size: 24px;
        }
        .sun-temp {
          position: absolute;
          bottom: 5%;
          left: 50%;
          transform: translateX(-50%);
          font-size: 48px;
        }
        .sun-icon {
          position: absolute;
          top: 60px;
          right: 50px;
        }
      }
      .real {
        .real-gi {
          padding: 14px 10px 10px 10px;
          background-color: #55a8e0;
        }
        .real-icon {
          width: 28px;
          height: 28px;
          margin: 0 4px;
        }
        .icon-air,
        .icon-comfort {
          transform: translateY(-4px);
        }
      }
    }
    .detail {
      flex: 1;
      .publish {
        padding: 6px;
      }
      .day-wrap {
        gap: 0 !important;
        .day-col {
          flex: 1;
          text-align: center;
          background: linear-gradient(#105b97, #3c79aa);
          padding: 10px 0;
          gap: 0 !important;
          cursor: pointer;
          &.day-selected {
            background: #104a84;
          }
          .day-temp {
            width: 100%;
            box-sizing: border-box;
            &.red {
              background-color: #d76e3c;
            }
            &.orange {
              background-color: #db9148;
            }
            &.orangeLight {
              background-color: #e4ad54;
            }
            &.yellow {
              background-color: #efdb64;
            }
            &.greenLight {
              background-color: #dff3bb;
            }
            &.green {
              background-color: #c5f576;
            }
            &.blueLight {
              background-color: #61afe3;
            }
            &.blue {
              background-color: #1f88d0;
            }
            &.blueDark {
              background-color: #081197;
            }
          }
          .night {
            .weather-icon {
              margin-top: 6px;
            }
          }
          span {
            display: inline-block;
            padding: 8px;
          }
        }
      }
    }
  }
  .n-card-header {
    padding: 20px 10px;
  }
  .n-card__content {
    font-size: 14px;
    padding: 20px;
  }
}
</style>
