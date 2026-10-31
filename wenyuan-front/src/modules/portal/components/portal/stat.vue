<template>
  <n-grid x-gap="12" :cols="data?.length">
    <n-gi v-for="item in data" class="stat-card">
      <n-card hoverable @click="handleClick(item)">
        <n-statistic :label="item.title" tabular-nums>
          <template #prefix v-if="item.valuePrefix || item.image">
            <img v-if="item.image" :src="item.image" width="50px" />
            <span v-if="item.valuePrefix">{{ item.valuePrefix }}</span>
          </template>
          {{ item.value }}
          <template #suffix v-if="item.valueSuffix || item.unit">
            {{ item.valueSuffix || '' }}
            {{ item.unit || '' }}
          </template>
        </n-statistic>
        <n-flex v-if="item.compareTitle || item.compareValue" justify="space-between">
          <div v-if="item.compareTitle || item.compareValue">
            <span v-if="item.compareTitle">{{ item.compareTitle }}</span>
            <span v-if="item.compareValue" :style="{ color: getColor(item.compareType) }">{{ item.compareValue }}</span>
          </div>
          <div v-if="item.compareType != undefined" :style="{ color: getColor(item.compareType) }">
            <n-icon :color="getColor(item.compareType)">
              <IconMdiArrowUpRight v-if="item.compareType === 2"></IconMdiArrowUpRight>
              <IconMdiArrowDownRight v-else-if="item.compareType === 0"></IconMdiArrowDownRight>
              <IconMdiEqual v-else></IconMdiEqual>
            </n-icon>
          </div>
        </n-flex>
        <div v-if="item.description">{{ item.description }}</div>
      </n-card>
    </n-gi>
  </n-grid>
</template>
<script lang="ts" setup>
import request from '@/utils/request.ts';
import { ref, watchEffect } from 'vue';
import { useRouter } from 'vue-router';

const router = useRouter();
/**
 *
 * */
const props = defineProps({
  url: { type: String },
  method: { type: [String, Number] },
  params: { type: Object },
});

const data = ref<any[]>([]);

function getColor(compareType: number | undefined) {
  if (compareType === 2) {
    return 'red';
  } else if (compareType === 0) {
    return 'green';
  }
  return 'black';
}

function fetchData(url: string, params: object | undefined) {
  const { method } = props;
  if (url) {
    request({
      url: url,
      params: Object.assign({}, params),
      method: method as string,
    })
      .then((resp) => {
        data.value = resp.data;
      })
      .catch(() => {
        data.value = [];
      });
  } else {
    data.value = [];
  }
}

function handleClick(item: any) {
  if (item.path) {
    router.push(item.path);
  }
}

watchEffect(() => {
  const { url, params } = props;
  fetchData(url as string, params);
});
</script>
<style lang="scss" scoped>
.stat-card {

}

:deep(.n-statistic-value) {
  justify-content: center;
  display: flex;
}

:deep(.n-statistic-value__content) {
  font-weight: bold;
}
</style>
