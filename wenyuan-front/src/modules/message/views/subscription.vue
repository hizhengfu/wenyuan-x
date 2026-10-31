<template>
  <n-card title="订阅偏好" class="h-full">
    <n-alert type="info" :show-icon="false" style="margin-bottom: 16px">
      开关开启表示接收通知；“全部业务”关闭的通道会覆盖单项设置，系统级紧急通知除外。
    </n-alert>

    <div v-if="channels.length" class="subscription-matrix-wrap">
      <n-table :single-line="false" size="small" class="subscription-matrix">
        <thead>
          <tr>
            <th>业务类型</th>
            <th v-for="ch in channels" :key="ch.value">{{ ch.label }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="biz in bizTypes" :key="biz.value" :class="{ 'subscription-global-row': biz.value === '*' }">
            <td>
              <n-space align="center" :size="8">
                <n-text :strong="biz.value === '*'">{{ biz.label }}</n-text>
                <n-tag v-if="biz.value === '*'" size="small" type="info">全局</n-tag>
              </n-space>
            </td>
            <td v-for="ch in channels" :key="ch.value">
              <n-space align="center" :size="8" :wrap="false">
                <n-switch
                  :value="!channelClosed(biz.value, ch.value)"
                  :disabled="isInheritedChannel(biz.value, ch.value)"
                  :aria-label="`${biz.label}${ch.label}接收开关`"
                  @update:value="setChannelReceiving(biz.value, ch.value, $event as boolean)"
                />
                <n-text depth="3">{{ channelStatusLabel(biz.value, ch.value) }}</n-text>
              </n-space>
            </td>
          </tr>
        </tbody>
      </n-table>
    </div>
    <n-text v-else depth="3">暂无启用通道，请先在「系统参数 → 通知中心」启用通道。</n-text>

    <n-space justify="end" class="subscription-actions" :size="12">
      <n-button @click="reload">
        <template #icon>
          <IconMdiRestore />
        </template>
        重置
      </n-button>
      <n-button type="primary" :loading="saving" @click="save">
        <template #icon>
          <IconMdiCheck />
        </template>
        保存
      </n-button>
    </n-space>
  </n-card>
</template>

<script lang="ts" setup>
import { computed, onBeforeMount, ref } from 'vue';
import { useDict } from '@/utils/dict';
import { enabledChannelOptions } from '../api/notify';
import { get as getSubscription, update as updateSubscription } from '../api/subscription';
import type { EnumMessageNotifyChannel, MessageSubscriptionItem, MessageSubscriptionVo } from '../typings/message';

interface Option {
  label: string;
  value: string;
}

const channels = ref<Option[]>([]);
const disabledMap = ref<Record<string, string[]>>({});
const saving = ref(false);
const { options: bizTypeDict } = useDict('notify_biz_type');

const bizTypes = computed<Option[]>(() => {
  const map = new Map<string, string>([['*', '全部业务']]);
  (bizTypeDict.value || []).forEach((item: any) => {
    if (item?.value) map.set(String(item.value), item.label || String(item.value));
  });
  Object.keys(disabledMap.value).forEach((bizType) => {
    if (!map.has(bizType)) map.set(bizType, bizType);
  });
  return Array.from(map, ([value, label]) => ({ label, value }));
});

async function reload() {
  // 后端 enabled-channel-options 已合并字典 label + 启用配置；
  // 这里只展示启用通道，让用户"关闭"已启用的通道才有意义。
  const [chResp, subResp] = await Promise.all([
    enabledChannelOptions().catch(() => ({ data: [] })),
    getSubscription(),
  ]);
  channels.value = ((chResp as any)?.data || []) as Option[];
  const items: MessageSubscriptionItem[] = (subResp?.data as MessageSubscriptionVo)?.items || [];
  const map: Record<string, string[]> = {};
  for (const it of items) {
    map[it.bizType] = (it.disabledChannels as string[]) || [];
  }
  disabledMap.value = map;
}

function isInheritedChannel(bizType: string, channel: string) {
  return bizType !== '*' && (disabledMap.value['*'] || []).includes(channel);
}

function effectiveDisabledChannels(bizType: string) {
  return bizType === '*'
    ? disabledMap.value['*'] || []
    : [...new Set([...(disabledMap.value['*'] || []), ...(disabledMap.value[bizType] || [])])];
}

function channelClosed(bizType: string, channel: string) {
  return effectiveDisabledChannels(bizType).includes(channel);
}

function channelStatusLabel(bizType: string, channel: string) {
  if (isInheritedChannel(bizType, channel)) return '全局关闭';
  if (channelClosed(bizType, channel)) return bizType === '*' ? '全部关闭' : '已关闭';
  return bizType === '*' ? '默认接收' : '接收中';
}

function setChannelReceiving(bizType: string, channel: string, receiving: boolean) {
  if (isInheritedChannel(bizType, channel)) return;
  const direct = new Set(disabledMap.value[bizType] || []);
  if (receiving) direct.delete(channel);
  else direct.add(channel);
  disabledMap.value = { ...disabledMap.value, [bizType]: [...direct] };
}

async function save() {
  saving.value = true;
  try {
    const items: MessageSubscriptionItem[] = Object.entries(disabledMap.value).map(([bizType, ch]) => ({
      bizType,
      disabledChannels: ch as EnumMessageNotifyChannel[],
    }));
    await updateSubscription({ items });
    await reload();
  } finally {
    saving.value = false;
  }
}

onBeforeMount(reload);
</script>

<style scoped>
.subscription-matrix-wrap {
  max-width: 1100px;
  overflow-x: auto;
}

.subscription-matrix {
  min-width: 620px;
}

.subscription-matrix :deep(th),
.subscription-matrix :deep(td) {
  white-space: nowrap;
}

.subscription-matrix :deep(th:first-child),
.subscription-matrix :deep(td:first-child) {
  min-width: 220px;
}

.subscription-matrix :deep(th:not(:first-child)),
.subscription-matrix :deep(td:not(:first-child)) {
  min-width: 150px;
}

.subscription-global-row :deep(td) {
  background: rgba(124, 77, 255, 0.06);
}

.subscription-actions {
  width: 100%;
  max-width: 1100px;
  margin-top: 16px;
}
</style>
