<template>
  <div class="showcase-page">
    <NCard size="small" :bordered="false">
      <NSpace align="center">
        <NInput v-model:value="query.keyword" clearable placeholder="名称 / 作者 / 关键词" style="width: 240px" @keyup.enter="load" />
        <HDictSelect v-model:value="query.achievementType" type="research_achievement_type" clearable placeholder="成果类型" style="width: 140px" @update:value="load" />
        <HDictSelect v-model:value="query.direction" type="research_direction" clearable placeholder="研究方向" style="width: 140px" @update:value="load" />
        <NInputNumber v-model:value="query.year" clearable placeholder="年份" :show-button="false" style="width: 110px" />
        <NInput v-model:value="query.author" clearable placeholder="作者" style="width: 130px" @keyup.enter="load" />
        <NButton type="primary" secondary @click="load">查询</NButton>
      </NSpace>
    </NCard>

    <NSpin :show="loading">
      <NEmpty v-if="!loading && !list.length" description="暂无公开成果" style="padding: 80px 0" />
      <div v-else class="card-grid">
        <NCard v-for="item in list" :key="item.id" size="small" hoverable class="showcase-card" @click="onView(item)">
          <template #header>
            <div class="card-title">{{ item.title }}</div>
          </template>
          <template #header-extra>
            <NTag size="small" type="info" :bordered="false">
              <HDictText type="research_achievement_type" :value="item.achievementType" />
            </NTag>
          </template>
          <div class="card-meta">
            <span>{{ item.achievementNo }}</span>
            <span v-if="item.authors">{{ item.authors }}</span>
            <span v-if="item.publishDate">{{ item.publishDate }}</span>
          </div>
          <div class="card-summary">{{ item.summary }}</div>
          <div class="card-footer">
            <HDictText type="research_direction" :value="item.direction" />
            <span v-if="item.avgScore != null" class="score">评分 {{ item.avgScore }}（{{ item.evalCount }}条）</span>
          </div>
        </NCard>
      </div>
      <div class="pager" v-if="total > query.pageSize">
        <NPagination v-model:page="query.pageNum" :page-size="query.pageSize" :item-count="total" @update:page="load" />
      </div>
    </NSpin>

    <HPageView v-model:show="viewShow" title="成果详情" width="760px">
      <template v-if="viewRow">
        <NDescriptions label-placement="left" :column="2" bordered size="small">
          <NDescriptionsItem label="成果编号">{{ viewRow.achievementNo || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="数字水印">{{ viewRow.watermark || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="成果名称" :span="2">{{ viewRow.title }}</NDescriptionsItem>
          <NDescriptionsItem label="类型"><HDictText type="research_achievement_type" :value="viewRow.achievementType" /></NDescriptionsItem>
          <NDescriptionsItem label="方向"><HDictText type="research_direction" :value="viewRow.direction" /></NDescriptionsItem>
          <NDescriptionsItem label="作者">{{ viewRow.authors || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="团队">{{ viewRow.team || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="发表载体">{{ viewRow.publishOrg || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="发表时间">{{ viewRow.publishDate || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="关联课题" :span="2">{{ viewRow.topicNo }} {{ viewRow.topicName || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="关键词" :span="2">{{ viewRow.keywords || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="摘要" :span="2">{{ viewRow.summary || '--' }}</NDescriptionsItem>
        </NDescriptions>

        <NCard title="成果评价" size="small" :bordered="false" style="margin-top: 12px">
          <template #header-extra>
            <NButton size="small" type="primary" secondary @click="evalFormShow = true">我要评价</NButton>
          </template>
          <NEmpty v-if="!evals.length" description="暂无评价" size="small" />
          <NList v-else size="small">
            <NListItem v-for="ev in evals" :key="ev.id">
              <div class="eval-row">
                <div>
                  <NTag size="small" :bordered="false"><HDictText type="research_eval_type" :value="ev.evalType" /></NTag>
                  <strong style="margin-left: 8px">{{ ev.score }} 分</strong>
                  <span style="margin-left: 8px; color: #888">{{ ev.evaluatorName }}</span>
                </div>
                <div class="eval-opinion">{{ ev.opinion || '' }}</div>
                <div v-if="ev.suggestion" class="eval-suggestion">建议：{{ ev.suggestion }}</div>
              </div>
            </NListItem>
          </NList>
        </NCard>

        <NModal v-model:show="evalFormShow" preset="dialog" title="成果评价" positive-text="提交" negative-text="取消" @positive-click="submitEval">
          <NForm label-placement="left" label-width="80">
            <NFormItem label="评价方式">
              <HDictSelect v-model:value="evalForm.evalType" type="research_eval_type" style="width: 100%" />
            </NFormItem>
            <NFormItem label="评分">
              <NInputNumber v-model:value="evalForm.score" :min="0" :max="100" style="width: 100%" />
            </NFormItem>
            <NFormItem label="评价意见">
              <NInput v-model:value="evalForm.opinion" type="textarea" :rows="3" />
            </NFormItem>
            <NFormItem label="改进建议">
              <NInput v-model:value="evalForm.suggestion" type="textarea" :rows="2" />
            </NFormItem>
          </NForm>
        </NModal>
      </template>
    </HPageView>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import {
  NButton, NCard, NDescriptions, NDescriptionsItem, NEmpty, NForm, NFormItem, NInput, NInputNumber,
  NList, NListItem, NModal, NPagination, NSpace, NSpin, NTag, useMessage,
} from 'naive-ui';
import { achievementApi } from '../api/achievement';
import { evaluationApi } from '../api/evaluation';
import type { ResAchievementEvalVo, ResAchievementVo } from '../typings';

const message = useMessage();
const loading = ref(false);
const list = ref<ResAchievementVo[]>([]);
const total = ref(0);

const query = reactive({
  pageNum: 1,
  pageSize: 12,
  keyword: null as string | null,
  achievementType: null as string | null,
  direction: null as string | null,
  year: null as number | null,
  author: null as string | null,
});

onMounted(load);

async function load() {
  loading.value = true;
  try {
    const resp: any = await achievementApi.showcase({
      ...query,
      keyword: query.keyword || undefined,
      achievementType: query.achievementType || undefined,
      direction: query.direction || undefined,
      year: query.year || undefined,
      author: query.author || undefined,
    });
    list.value = resp?.data?.list ?? [];
    total.value = Number(resp?.data?.total ?? 0);
  } finally {
    loading.value = false;
  }
}

const viewShow = ref(false);
const viewRow = ref<ResAchievementVo | null>(null);
const evals = ref<ResAchievementEvalVo[]>([]);
const evalFormShow = ref(false);
const evalForm = reactive<ResAchievementEvalVo>({ evalType: 'USER', score: 80 });

async function onView(row: ResAchievementVo) {
  viewRow.value = row;
  viewShow.value = true;
  await loadEvals(row.id!);
}

async function loadEvals(achievementId: string) {
  const resp: any = await evaluationApi.byAchievement(achievementId);
  evals.value = resp?.data ?? [];
}

async function submitEval() {
  if (!viewRow.value?.id) return;
  if (evalForm.score == null) {
    message.warning('请填写评分');
    return false;
  }
  await evaluationApi.save({ ...evalForm, achievementId: viewRow.value.id });
  message.success('评价已提交');
  await loadEvals(viewRow.value.id);
  await load();
  evalForm.opinion = undefined;
  evalForm.suggestion = undefined;
}
</script>

<style scoped>
.showcase-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
}

.showcase-card {
  cursor: pointer;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: #888;
  font-size: 12px;
  margin-bottom: 6px;
}

.card-summary {
  color: #555;
  font-size: 13px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 58px;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
  font-size: 12px;
}

.score {
  color: #d48806;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.eval-row {
  width: 100%;
}

.eval-opinion {
  margin-top: 4px;
  color: #555;
}

.eval-suggestion {
  margin-top: 2px;
  color: #888;
  font-size: 12px;
}
</style>
