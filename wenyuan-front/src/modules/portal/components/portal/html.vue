<template>
  <template v-if="html">
    <div>
      <template v-if="isArray(html)">
        <div v-for="item in html" v-html="item.content"></div>
      </template>
      <div v-else-if="isObject(html)" v-html="html.content"></div>
      <div v-else v-html="html"></div>
    </div>
  </template>
  <n-empty v-else class="wh-full flex-center" description="暂无数据"></n-empty>
</template>
<script lang="ts" setup>
import { ref, watchEffect } from 'vue';
import request from '@/utils/request.ts';
import { isArray, isObject } from 'lodash-es';

/**
 * html数据格式
 * */
interface PortalHtmlData {
  content?: string;
}

const props = defineProps({
  url: { type: String },
  method: { type: [String, Number] },
  params: { type: Object },
});

const html = ref<PortalHtmlData | undefined | PortalHtmlData[]>();

function fetchData(url: string, params: object | undefined) {
  const { method } = props;
  if (url) {
    request({
      url: url,
      params: Object.assign({}, params),
      method: method as string,
    })
      .then((resp) => {
        html.value = resp.data;
      })
      .catch(() => {
        html.value = undefined;
      });
  } else {
    html.value = undefined;
  }
}

watchEffect(() => {
  const { url, params } = props;
  fetchData(url as string, params);
});
</script>
