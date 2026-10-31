<template>
  <n-card :segmented="{ content: true, footer: 'soft' }">
    <template #header>
      <n-flex justify="center">
        <n-input-group>
          <n-input v-model:value="form.keywords" placeholder="请输入关键字进行搜索"></n-input>
          <n-button type="primary" @click="handleQuery">
            <template #icon>
              <n-icon size="24">
                <IconMdiMagnify></IconMdiMagnify>
              </n-icon>
            </template>
          </n-button>
        </n-input-group>
      </n-flex>
    </template>
    <template v-if="resultList && resultList.length > 0">
      <n-list bordered hoverable clickable>
        <n-list-item v-for="item in resultList" @click="handleTo(item)">
          <template v-if="item.prefix" #prefix>
            {{ item.prefix }}
          </template>
          <div>
            {{ item.title }}
            <span v-if="item.description">{{ item.description }}</span>
          </div>
          <template v-if="item.suffix" #suffix>
            {{ item.suffix }}
          </template>
        </n-list-item>
      </n-list>
    </template>
  </n-card>
</template>
<script lang="ts" setup>
import { ref } from 'vue';
import request from '@/utils/request.ts';
import { useRouter } from 'vue-router';

/**
 * 搜索数据格式
 * */
interface PortalSearchData {
  title: string;
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

const resultList = ref<PortalSearchData[]>();

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

function handleQuery() {
  const { url } = props;
  fetchData(url as string, form.value);
}
</script>
