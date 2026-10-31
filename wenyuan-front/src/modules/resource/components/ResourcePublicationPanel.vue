<template>
  <n-card title="渠道发布与展示布局" size="small">
    <n-alert type="info"
      >发布后内容进入所选渠道接口，可预览实际输出。门户网站或小程序需接入该接口后展示；下架、审核失效或关联资源停止公开时，渠道会自动隐藏内容。</n-alert
    >
    <n-space vertical class="mt-3"
      ><n-card
        v-for="row in rows"
        :key="row.channel"
        size="small"
        :title="row.channel === 'PORTAL' ? '门户网站' : '小程序'"
      >
        <n-space align="center"
          ><n-tag :type="row.id && row.published ? (row.effective ? 'success' : 'warning') : 'default'">{{
            row.id && row.published ? (row.effective ? '已发布到渠道接口' : '已暂停展示') : '未发布到渠道接口'
          }}</n-tag>
          <n-input v-model:value="row.section" placeholder="展示栏目" style="width: 150px" maxlength="100" />
          <n-select v-model:value="row.layout" :options="layouts" style="width: 140px" /><n-input-number
            v-model:value="row.sort"
            placeholder="排序"
            style="width: 110px"
          />
          <n-button :loading="busy" type="primary" @click="save(row, true)">{{
            row.id && row.published ? '更新布局' : '发布到渠道'
          }}</n-button>
          <n-button v-if="row.id && row.published" :loading="busy" @click="save(row, false)">从渠道下架</n-button>
          <n-button @click="preview(row.channel)">预览渠道</n-button> </n-space
        ><n-alert v-if="row.unavailableReason" type="warning" class="mt-3">{{ row.unavailableReason }}</n-alert></n-card
      >
      <n-space
        ><n-button @click="share">复制内容分享链接</n-button
        ><n-text depth="3">分享链接只展示当前公开内容。</n-text></n-space
      >
      <n-alert v-if="error" type="error">{{ error }}</n-alert>
    </n-space></n-card
  >
</template>
<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useMessage } from 'naive-ui';
import { publications, publishChannel } from '../api/business';
const props = defineProps<{ subjectId: string; subjectType: string }>();
const router = useRouter();
const message = useMessage();
const rows = ref<any[]>([]);
const busy = ref(false);
const error = ref('');
let generation = 0;
const layouts = [
  { label: '网格', value: 'GRID' },
  { label: '卡片', value: 'CARDS' },
  { label: '时间线', value: 'TIMELINE' },
];
watch(
  () => [props.subjectId, props.subjectType],
  async () => {
    const own = ++generation;
    error.value = '';
    rows.value = [];
    try {
      const r: any = await publications(props.subjectType, props.subjectId);
      if (own !== generation) return;
      rows.value = ['PORTAL', 'MINI_PROGRAM'].map((channel) => ({
        channel,
        subjectId: props.subjectId,
        subjectType: props.subjectType,
        section: '精选',
        layout: 'GRID',
        sort: 0,
        published: false,
        ...r.data?.find((v: any) => v.channel === channel),
      }));
    } catch {
      if (own === generation) error.value = '发布状态读取失败，请重新打开。';
    }
  },
  { immediate: true },
);
async function save(row: any, published: boolean) {
  busy.value = true;
  error.value = '';
  try {
    const r: any = await publishChannel({ ...row, published });
    Object.assign(row, r.data);
    message.success(published ? '渠道发布已保存' : '已从渠道下架');
  } catch (e: any) {
    error.value = e.message || '操作失败，请检查审核和公开状态';
  } finally {
    busy.value = false;
  }
}
function preview(channel: string) {
  window.open(router.resolve({ path: '/resource-channel', query: { channel } }).href, '_blank', 'noopener');
}
async function share() {
  const href = router.resolve({
    path: '/resource-channel',
    query: { type: props.subjectType, id: props.subjectId },
  }).href;
  await navigator.clipboard.writeText(new URL(href, window.location.href).href);
  message.success('分享链接已复制');
}
</script>
