<template>
  <n-popover v-if="appStore.messageEnabled" v-model:show="popoverShow" :show-arrow="false" trigger="click">
    <template #trigger>
      <n-badge class="mr-3" processing type="error" :value="notifyCount" :max="99">
        <n-icon
          :class="{ flicker: notifyCount > 0 }"
          size="26px"
          :color="notifyCount > 0 ? '#ff0000' : '#333333'"
          v-bind="$attrs"
        >
          <HIcon :icon="notifyCount > 0 ? 'c:AlarmLight' : 'c:AlarmLightOff'"></HIcon>
        </n-icon>
      </n-badge>
    </template>
    <InboxBellList
      :items="notifyList"
      @item-click="handleItemClick"
      @read-all="handleReadAll"
    />
  </n-popover>

  <h-page-form
    v-model:show="dialogShow"
    :title="active?.title || '消息详情'"
    :show-footer="false"
    label-placement="top"
  >
    <n-space vertical :size="12">
      <n-text v-if="active?.createTime" depth="3" style="font-size: 12px">
        <n-time :time="toDate(active.createTime)" />
      </n-text>
      <div v-html="active?.content || active?.summary || ''" />
      <!-- 附件：按 notifyId 查 sys_file_content；该通知未带附件时 HFileList 自身渲染为空 -->
      <HFileList v-if="active?.notifyId" :content-id="active.notifyId" :show-download-btn="true" />
      <n-space v-if="active?.clickUrl" justify="end">
        <n-button type="primary" @click="handleJump(active!.clickUrl!)">
          <template #icon>
            <n-icon><IconMdiOpenInNew /></n-icon>
          </template>
          前往查看
        </n-button>
      </n-space>
    </n-space>
  </h-page-form>
</template>

<script lang="ts" setup>
import { onBeforeUnmount, ref, watch } from 'vue';
import { useAppStore } from '@/store';
import { findPage as findInboxPage, read, readAll, unreadCount } from '@/modules/message/api/inbox';
import InboxBellList from '@/modules/message/components/InboxBellList.vue';
import type { MessageInboxItem } from '@/modules/message/typings/message';

const props = defineProps({
  refreshInterval: { type: Number, default: 8000 },
});

const appStore = useAppStore();

const notifyList = ref<MessageInboxItem[]>([]);
const notifyCount = ref(0);
const popoverShow = ref(false);

const dialogShow = ref(false);
const active = ref<MessageInboxItem | null>(null);

let timerId: ReturnType<typeof setInterval> | null = null;

function startTimer() {
  if (timerId != null) return;
  timerId = setInterval(loadData, props.refreshInterval);
}
function stopTimer() {
  if (timerId != null) {
    clearInterval(timerId);
    timerId = null;
  }
}

/**
 * 加载未读数。顶部轮询只需要红点数字，不提前拉列表。
 */
async function loadData() {
  if (!appStore.messageEnabled) {
    notifyList.value = [];
    notifyCount.value = 0;
    return;
  }
  try {
    const resp = await unreadCount();
    notifyCount.value = Number(resp?.data) || 0;
    if (notifyCount.value <= 0) notifyList.value = [];
  } catch {
    notifyList.value = [];
    notifyCount.value = 0;
  }
}

/** 点开通知弹层时再加载未读列表。 */
async function loadUnreadList() {
  if (!appStore.messageEnabled) {
    notifyList.value = [];
    return;
  }
  try {
    const resp = await findInboxPage({ pageNum: 1, pageSize: 5, readStatus: 'UNREAD' });
    notifyList.value = (resp?.data?.list || []) as MessageInboxItem[];
    notifyCount.value = (resp?.data?.total as number) || 0;
  } catch {
    notifyList.value = [];
    notifyCount.value = 0;
  }
}

watch(popoverShow, (show) => {
  if (show) loadUnreadList();
});

async function handleItemClick(item: MessageInboxItem) {
  active.value = item;
  dialogShow.value = true;
  if (item.readStatus === 'UNREAD') {
    try {
      await read(item.id);
      // 同步本地状态：从未读列表移除 + 未读数 -1
      const idx = notifyList.value.findIndex((x) => x.id === item.id);
      if (idx >= 0) notifyList.value.splice(idx, 1);
      if (notifyCount.value > 0) notifyCount.value -= 1;
    } catch {
      // 静默忽略，下一轮 loadData 修正
    }
  }
}

async function handleReadAll() {
  await readAll();
  notifyList.value = [];
  notifyCount.value = 0;
}

function handleJump(url: string) {
  if (url.startsWith('http://') || url.startsWith('https://')) {
    window.open(url, '_blank');
  } else {
    window.location.href = url;
  }
}

function toDate(s: string): number {
  const t = new Date(s).getTime();
  return Number.isNaN(t) ? Date.now() : t;
}

// 模块启用时立即拉一次并启动轮询；禁用时停轮询并清空状态
watch(
  () => appStore.messageEnabled,
  (enabled) => {
    if (enabled) {
      loadData();
      startTimer();
    } else {
      stopTimer();
      notifyList.value = [];
      notifyCount.value = 0;
    }
  },
  { immediate: true },
);

watch(
  () => props.refreshInterval,
  () => {
    if (timerId == null) return;
    stopTimer();
    startTimer();
  },
);

onBeforeUnmount(stopTimer);
</script>

<style scoped>
.flicker {
  -webkit-animation-name: flicker;
  -webkit-animation-duration: 1s;
  -webkit-animation-iteration-count: infinite;
  -webkit-animation-direction: alternate;
  -webkit-animation-timing-function: ease;
  -webkit-animation-play-state: running;
}

@-webkit-keyframes flicker {
  0% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
  100% {
    opacity: 0;
  }
}
</style>
