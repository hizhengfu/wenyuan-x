<template>
  <h-chart :option="option"></h-chart>
</template>
<script lang="ts" setup>
import { HChart } from '@/components/data/chart';
import { ref, watchEffect } from 'vue';
import request from '@/utils/request';

const props = defineProps({
  url: { type: String },
  method: { type: [String, Number] },
  params: { type: Object },
});

const option = ref();

/**
 * 加载数据
 * url需要考虑支持时间参数 $TODAY 以便动态获取数据
 * */
function fetchData(url: string, params: object | undefined) {
  const { method } = props;
  if (url) {
    request({
      url: url,
      params: Object.assign({}, params),
      method: method as string,
    })
      .then((resp) => {
        let temp = (resp && resp.data) || undefined;
        if (temp && !temp.toolbox) {
          temp.toolbox = {
            show: true,
            feature: {
              dataView: {
                readOnly: false,
              },
              saveAsImage: {},
            },
          };
        }
        option.value = temp;
      })
      .catch(() => {
        option.value = undefined;
      });
  } else {
    option.value = undefined;
  }
}

watchEffect(() => {
  const { url, params } = props;
  fetchData(url as string, params);
});
</script>
