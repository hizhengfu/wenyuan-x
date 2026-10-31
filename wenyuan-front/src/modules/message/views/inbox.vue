<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="findPage"
    :params="paramsForm"
    :columns="columns"
    :isEdit="false"
    :isAdd="false"
    @rowView="handleRead"
    v-bind="$attrs"
  >
    <template #queryForm>
      <n-form-item label="标题" path="title">
        <n-input v-model:value="paramsForm.title" placeholder="模糊匹配" clearable />
      </n-form-item>
      <n-form-item label="业务类型" path="bizType">
        <HDictSelect v-model:value="paramsForm.bizType" type="notify_biz_type" clearable />
      </n-form-item>
      <n-form-item label="状态" path="readStatus">
        <HDictSelect type="notify_read_status" v-model:value="paramsForm.readStatus" clearable></HDictSelect>
      </n-form-item>
    </template>

    <template #toolbar-extra>
      <n-button type="primary" @click="handleReadAll">
        <template #icon>
          <n-icon><IconMdiCheckAll /></n-icon>
        </template>
        全部已读
      </n-button>
    </template>

    <h-page-form
      v-model:show="detailShow"
      :title="active?.title || '消息详情'"
      :show-footer="false"
      label-placement="top"
    >
      <n-space vertical :size="12">
        <n-space align="center" :size="8">
          <HDictText v-if="active?.level" type="notify_level" :value="active.level" />
          <n-text depth="3" style="font-size: 12px">
            <n-time :time="toDate(active?.createTime)" />
          </n-text>
        </n-space>
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
  </h-page-data-table>
</template>

<script lang="ts" setup>
import { ref } from 'vue';
import { confirm } from '@/utils';
import { findPage, read, readAll } from '../api/inbox';
import type { MessageInboxItem } from '../typings/message';

const refPageTable = ref();

const paramsForm = ref<{
  title: string;
  bizType: string;
  readStatus: 'UNREAD' | 'READ' | '';
}>({
  title: '',
  bizType: '',
  readStatus: '',
});

const columns = [
  { key: 'title', title: '标题', minWidth: 240, columnType: 'title', ellipsis: true },
  { key: 'level', title: '级别', width: 80, align: 'center', dictType: 'notify_level' },
  { key: 'summary', title: '摘要', minWidth: 200, ellipsis: true },
  { key: 'bizType', title: '类型', width: 120, dictType: 'notify_biz_type' },
  { key: 'readStatus', title: '状态', width: 100, dictType: 'notify_read_status', align: 'center' },
  {
    key: 'createTime',
    title: '收到时间',
    width: 220,
    columnType: 'dateTime',
  },
];

const detailShow = ref(false);
const active = ref<MessageInboxItem | null>(null);

/**
 * 行点击：先标已读，再统一弹详情对话框；带 clickUrl 的消息由详情里的"前往查看"按钮触发跳转。
 */
async function handleRead(row: MessageInboxItem) {
  if (row.readStatus === 'UNREAD') {
    await read(row.id);
  }
  active.value = row;
  detailShow.value = true;
  refPageTable.value?.handleRefresh?.();
}

function handleJump(url: string) {
  if (url.startsWith('http://') || url.startsWith('https://')) {
    window.open(url, '_blank');
  } else {
    window.location.href = url;
  }
}

function handleReadAll() {
  confirm({
    title: '确认全部已读？',
    content: '将把当前账号下所有未读消息标记为已读，操作无法撤销。',
    onPositiveClick: async () => {
      await readAll();
      refPageTable.value?.handleRefresh?.();
    },
  });
}

function toDate(s: string | undefined): number {
  if (!s) return Date.now();
  const t = new Date(s).getTime();
  return Number.isNaN(t) ? Date.now() : t;
}
</script>
