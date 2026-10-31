<template>
  <div>
    <template v-if="resultList && resultList.length > 0">
      <n-list bordered hoverable clickable>
        <n-list-item v-for="item in resultList" v-download="{url:item.path,fileName:item.title}">
          <template v-if="item.prefix" #prefix>
            {{ item.prefix }}
          </template>
          <div>
            {{ item.title }}
            <span v-if="item.description">{{ item.description }}</span>
          </div>
          <template #suffix>
            <span v-if="item.suffix">{{ item.suffix }}</span>
            <n-icon size='16'>
              <IconMdiDownload></IconMdiDownload>
            </n-icon>
          </template>
        </n-list-item>
      </n-list>
    </template>
    <n-empty v-else class="wh-full flex-center" description="暂无数据"></n-empty>
  </div>
</template>
<script lang="ts" setup>
import { ref, watchEffect } from 'vue';
import request from '@/utils/request.ts';
import { useRouter } from 'vue-router';

/**
 * list数据格式
 * */
interface PortalListData {
  title: string;
  image?: string;
  prefix?: string;
  suffix?: string;
  description?: string;
  path?: string;
}

const router = useRouter();
const props = defineProps({
  url: { type: String },
  method: { type: [String, Number] },
  params: { type: Object },
});

const form = ref({
  keywords: '',
});

const resultList = ref<PortalListData[] | undefined>();

function fetchData(url: string, params: object | undefined) {
  const { method } = props;
  if (url) {
    request({
      url: url,
      params: Object.assign({}, params),
      method: method as string,
    })
      .then((resp) => {
        resultList.value = resp.data;
      })
      .catch(() => {
        resultList.value = undefined;
      });
  } else {
    resultList.value = undefined;
  }
}

function handleTo(item: any) {
  if (item.path) {
    if (/^https?/.test(item.path)) {
      window.open(item.path);
    } else {
      router.push(item.path);
    }
  }
}

watchEffect(() => {
  const { url, params } = props;
  fetchData(url as string, params);
});
</script>
