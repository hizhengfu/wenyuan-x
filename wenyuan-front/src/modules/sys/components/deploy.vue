<template>
  <div>
    <n-card size="small" style="align-items: center">
      <h-upload
        module="front-deploy"
        :params="{ eventOnly: 'true' }"
        :enable-quick-upload="false"
        @uploadFinish="fetchHistory"
        :show-file-list="false"
        allowDragger
        accept=".zip,application/zip"
      >
        <div style="margin-bottom: 12px">
          <n-icon size="48" :depth="3">
            <IconMdiArchive />
          </n-icon>
        </div>
        <n-text style="font-size: 16px"> 点击或者拖动文件到该区域来上传 </n-text>
        <n-p depth="3" style="margin: 8px 0 0 0">
          仅支持ZIP格式文件，tinymce、cesium、draco、hik文件夹如不需更新可不重复上传。
        </n-p>
      </h-upload>
    </n-card>
    <n-hr></n-hr>
    <n-card title="部署历史" size="small" style="height: 300px; overflow: auto">
      <template #header-extra>
        <n-button @click="fetchHistory" :loading="loading" text>
          <template #icon>
            <n-icon size="20">
              <IconMdiSync></IconMdiSync>
            </n-icon>
          </template>
        </n-button>
      </template>
      <n-list>
        <n-list-item v-for="item in historyList">
          部署ID：{{ item.deployId }},部署时间:<n-time :time="item.timestamp" />
          <template #suffix>
            <n-button v-if="!item.isCurrent" type="warning" @click="handleSwitch(item.deployId)">切换版本</n-button>
            <n-button v-else type="info" text>当前版本</n-button>
          </template>
        </n-list-item>
      </n-list>
      <n-empty v-if="!historyList || historyList.length === 0" description="暂无数据">
        <template #extra>
          <n-button size="small" @click="fetchHistory"> 刷新 </n-button>
        </template>
      </n-empty>
    </n-card>
  </div>
</template>
<script lang="ts" setup>
import { history, switchDeploy } from '../api/deploy.ts';
import { ref } from 'vue';
import { confirm } from '@/utils';

const historyList = ref<any[]>([]);

const loading = ref(false);

/**
 * 切换发布版本
 * */
function handleSwitch(deployId: string) {
  confirm({
    type: 'error',
    title: '请注意',
    content: '点击确认后将使用此版本替换正在运行的版本',
    onPositiveClick: () => {
      switchDeploy(deployId).then(() => {
        fetchHistory();
      });
    },
  });
}

/**
 * 获取历史记录
 * */
function fetchHistory() {
  loading.value = true;
  history()
    .then((resp: any) => {
      historyList.value = resp.data || [];
    })
    .finally(() => {
      loading.value = false;
    });
}
fetchHistory();
</script>
