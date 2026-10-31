<template>
  <main class="channel-page">
    <header>
      <p>数字资源</p>
      <h1>{{ route.query.id ? '公开内容' : channel === 'MINI_PROGRAM' ? '小程序内容预览' : '门户内容预览' }}</h1>
      <n-space><n-button @click="load">刷新</n-button><n-button @click="share">分享本页</n-button></n-space>
    </header>
    <n-alert v-if="error" type="error">{{ error }}</n-alert
    ><n-spin :show="loading"
      ><n-empty v-if="!loading && !error && !items.length" description="暂无公开内容" />
      <section v-for="section in sections" :key="section.name">
        <h2>{{ section.name }}</h2>
        <div :class="['channel-items', section.layout.toLowerCase()]">
          <article v-for="entry in section.items" :key="entry.subject.id">
            <n-card :title="entry.subject.title"
              ><n-space vertical
                ><n-text>{{ entry.subject.description || entry.subject.data?.description }}</n-text
                ><n-text v-if="entry.subject.data?.capturedAt" depth="3"
                  >采集时间：{{ entry.subject.data.capturedAt }}</n-text
                ><n-text v-if="entry.subject.data?.annotation">{{ entry.subject.data.annotation }}</n-text
                ><n-space
                  ><n-button v-for="(id, index) in entry.subject.resourceIds" :key="id" @click="openResource(id)"
                    >查看资源 {{ Number(index) + 1 }}</n-button
                  ><template v-if="entry.subject.kind === 'PLAN'"
                    ><n-button
                      v-for="(id, index) in entry.subject.resourceIds"
                      :key="id"
                      type="primary"
                      @click="claim(entry.subject.id, id)"
                      >领取本件（资源 {{ Number(index) + 1 }}）</n-button
                    ></template
                  ><n-button @click="shareItem(entry.subject)">分享内容</n-button></n-space
                ><n-text v-if="entry.subject.kind === 'PLAN'" depth="3"
                  >已领取 {{ entry.subject.issuedCount }} / {{ entry.subject.data.capacity }} ·
                  {{ entry.subject.data.startsAt }} 至 {{ entry.subject.data.endsAt }}</n-text
                ></n-space
              ></n-card
            >
          </article>
        </div>
      </section>
      <n-pagination
        v-if="!route.query.id && total > 40"
        :page="page"
        :item-count="total"
        :page-size="40"
        @update:page="changePage"
      />
    </n-spin>
    <n-modal
      v-model:show="resourceShow"
      preset="card"
      :title="resource?.name || '公开资源'"
      style="width: min(1000px, calc(100vw - 32px))"
      ><n-spin :show="resourceLoading"
        ><n-alert v-if="resourceError" type="error">{{ resourceError }}</n-alert
        ><template v-if="resource"
          ><ResourcePanorama v-if="resource.resourceType === 'PANORAMA'" :src="resource.filePath" /><n-image
            v-else-if="resource.resourceType === 'IMAGE'"
            :src="resource.filePath"
            object-fit="contain"
            style="max-height: 65vh"
          /><video
            v-else-if="resource.resourceType === 'VIDEO'"
            :src="resource.filePath"
            controls
            style="width: 100%; max-height: 65vh"
          /><audio v-else-if="resource.resourceType === 'AUDIO'" :src="resource.filePath" controls /><n-text v-else
            >此格式可下载后使用相应软件查看。</n-text
          ><n-space class="mt-3"
            ><n-button tag="a" :href="resource.filePath" target="_blank" rel="noopener noreferrer"
              >打开公开原文件</n-button
            ></n-space
          ></template
        ></n-spin
      ></n-modal
    >
  </main>
</template>
<script lang="ts">
export default {
  data() {
    return { meta: { requiresAuth: false, title: '数字资源公开展示' } };
  },
};
</script>
<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useMessage } from 'naive-ui';
import { useAuthStore } from '@/store/modules/auth';
import { channelFeed, publicSubject, publicResource, businessAction } from '../api/business';
import ResourcePanorama from '../components/ResourcePanorama.vue';
const route = useRoute(),
  router = useRouter(),
  message = useMessage(),
  auth = useAuthStore();
const channel = computed(() => (route.query.channel === 'MINI_PROGRAM' ? 'MINI_PROGRAM' : 'PORTAL'));
const items = ref<any[]>([]),
  total = ref(0),
  page = ref(1),
  loading = ref(false),
  error = ref(''),
  resourceShow = ref(false),
  resource = ref<any>(),
  resourceLoading = ref(false),
  resourceError = ref('');
let generation = 0,
  resourceGeneration = 0;
const sections = computed(() => {
  const result = new Map<string, any>();
  for (const entry of items.value) {
    const p = entry.publication || { section: '内容', layout: 'CARDS' };
    const key = p.section + '_' + p.layout;
    if (!result.has(key)) result.set(key, { name: p.section, layout: p.layout, items: [] });
    result.get(key).items.push(entry);
  }
  return [...result.values()];
});
async function load() {
  const own = ++generation;
  loading.value = true;
  error.value = '';
  items.value = [];
  try {
    if (route.query.id) {
      const r: any = await publicSubject(String(route.query.type || 'BUSINESS'), String(route.query.id));
      if (own !== generation) return;
      items.value = [{ subject: r.data }];
    } else {
      const r: any = await channelFeed(channel.value, page.value);
      if (own !== generation) return;
      items.value = r.data?.list || [];
      total.value = r.data?.total || 0;
    }
  } catch {
    if (own === generation) error.value = '内容未公开、已下架或暂时无法读取';
  } finally {
    if (own === generation) loading.value = false;
  }
}
function changePage(n: number) {
  page.value = n;
  load();
}
async function share() {
  await navigator.clipboard.writeText(location.href);
  message.success('链接已复制');
}
async function shareItem(item: any) {
  const href = router.resolve({
    path: '/resource-channel',
    query: { type: item.kind === 'RESOURCE' ? 'RESOURCE' : 'BUSINESS', id: item.id },
  }).href;
  await navigator.clipboard.writeText(new URL(href, location.href).href);
  message.success('分享链接已复制');
}
async function claim(id: string, resourceId: string) {
  if (!auth.userInfo?.id) {
    message.info('请先登录后再领取数字收藏');
    return;
  }
  await businessAction(id, 'claim', { resourceIds: [resourceId] });
  message.success('领取成功，请在数字收藏管理的“我的证书”中查看');
  await load();
}
async function openResource(id: string) {
  const own = ++resourceGeneration;
  resource.value = undefined;
  resourceError.value = '';
  resourceShow.value = true;
  resourceLoading.value = true;
  try {
    const r: any = await publicResource(id);
    if (own === resourceGeneration) resource.value = r.data;
  } catch {
    if (own === resourceGeneration) resourceError.value = '资源已下架或无法访问';
  } finally {
    if (own === resourceGeneration) resourceLoading.value = false;
  }
}
watch(
  () => route.fullPath,
  () => {
    page.value = 1;
    load();
  },
  { immediate: true },
);
</script>
<style scoped>
.channel-page {
  max-width: 1200px;
  margin: auto;
  padding: 32px 20px;
  min-height: 100vh;
  background: #f6f8f7;
}
.channel-page header {
  padding: 28px 0;
  border-bottom: 1px solid #d9e4df;
}
.channel-page h1 {
  font-size: 32px;
  color: #234e43;
}
.channel-page section {
  margin: 28px 0;
}
.channel-items {
  display: grid;
  gap: 18px;
}
.channel-items.grid {
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
}
.channel-items.timeline {
  border-left: 3px solid #6d998d;
  padding-left: 20px;
}
.channel-items.cards {
  max-width: 900px;
}
</style>
