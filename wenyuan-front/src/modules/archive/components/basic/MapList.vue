<template>
  <div class="list">
    <div class="title">筛选结果</div>
    <n-infinite-scroll style="height: 211px" :distance="10" @load="handleLoad">
      <div v-for="(item, index) in markerData" :key="index" class="item" @click="handleClick(item)">
        {{ item.publishName }} (
        <h-dict-text v-model:value="item.publishLevel" type="third_cultural_relic_level"></h-dict-text>
        )
      </div>
    </n-infinite-scroll>
  </div>
</template>
<script lang="ts" setup>
import { ref, watch } from 'vue';

const props = defineProps({
  markerData: {
    type: Array as any,
    default: () => [],
  },
  dataFlag: {
    type: Boolean,
  },
});
const pageNum = ref(1);
const emit = defineEmits(['clickItem', 'loadMore']);

function handleLoad() {
  console.log('------');
  const { markerData, dataFlag } = props;
  if (markerData.length > 0 && !dataFlag) {
    emit('loadMore', ++pageNum.value);
  }
}

function handleClick(item: any) {
  emit('clickItem', item);
}

watch(
  () => props.markerData,
  (newValue, oldValue) => {
    console.log(newValue, '点位数据');
  },
  { deep: true, immediate: true },
);
</script>
<style lang="scss" scoped>
.item {
  display: flex;
  align-items: center;
  height: 46px;
  justify-content: left;
  margin-bottom: 10px;
  padding-left: 10px;
  border-bottom: 1px solid #d9d9d9;
  cursor: pointer;
}

.item:last-child {
  margin-bottom: 0;
}

.list {
  padding: 10px 10px;
  box-shadow: 0px 0px 3px 2px rgba(0, 0, 0, 0.16);
  border-radius: 4px;
}

.title {
  font-size: 18px;
}

.title::before {
  content: '';
  display: inline-block;
  width: 4px;
  height: 14px;
  background-color: #a51a1a;
  border-radius: 7px;
  margin-right: 5px;
}
</style>
