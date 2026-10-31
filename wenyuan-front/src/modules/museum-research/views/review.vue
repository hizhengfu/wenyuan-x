<template>
  <HPageDataTable
    ref="tableRef"
    :columns="columns"
    :fetch-data="reviewApi.page"
    :params="queryParams"
    :is-edit="false"
    :is-delete="false"
    :is-add="mayInvite"
    add-button-text="邀请评审"
    row-key-field="id"
    @add="onInvite"
    @row-view="onView"
  >
    <template #queryForm>
      <NFormItem label="关键字">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="课题/成果 / 专家" style="width: 200px" />
      </NFormItem>
      <NFormItem label="评审环节">
        <HDictSelect v-model:value="queryParams.reviewType" type="research_review_type" clearable placeholder="环节" style="width: 150px" />
      </NFormItem>
      <NFormItem label="状态">
        <HDictSelect v-model:value="queryParams.reviewStatus" type="research_review_status" clearable placeholder="状态" style="width: 120px" />
      </NFormItem>
    </template>

    <!-- 邀请专家评审 -->
    <NModal v-model:show="inviteShow" preset="dialog" title="邀请专家评审" positive-text="发出邀请" negative-text="取消" style="width: 560px" @positive-click="submitInvite">
      <NForm label-placement="left" label-width="90">
        <NFormItem label="评审环节">
          <HDictSelect v-model:value="inviteForm.reviewType" type="research_review_type" style="width: 100%" @update:value="inviteForm.businessId = null" />
        </NFormItem>
        <NFormItem label="评审对象">
          <TopicSelect v-if="inviteForm.reviewType !== 'ACHIEVEMENT'" v-model:value="inviteForm.businessId" placeholder="选择课题" />
          <AchievementSelect v-else v-model:value="inviteForm.businessId" :passed-only="false" placeholder="选择成果" />
        </NFormItem>
        <NFormItem label="评审专家">
          <NSelect v-model:value="inviteForm.expertIds" :options="expertOptions" multiple filterable placeholder="可多选" />
        </NFormItem>
      </NForm>
    </NModal>

    <!-- 填写评审 -->
    <NModal v-model:show="fillShow" preset="dialog" title="填写评审意见" positive-text="提交" negative-text="取消" style="width: 560px" @positive-click="submitFill">
      <NDescriptions v-if="fillRow" label-placement="left" :column="1" size="small" style="margin-bottom: 10px">
        <NDescriptionsItem label="评审对象">{{ fillRow.businessName }}</NDescriptionsItem>
        <NDescriptionsItem label="专家">{{ fillRow.expertName }}</NDescriptionsItem>
      </NDescriptions>
      <NForm label-placement="left" label-width="90">
        <NFormItem label="评分">
          <NInputNumber v-model:value="fillForm.score" :min="0" :max="100" style="width: 100%" />
        </NFormItem>
        <NFormItem label="评审结论">
          <HDictSelect v-model:value="fillForm.conclusion" type="research_review_conclusion" style="width: 100%" />
        </NFormItem>
        <NFormItem label="评审意见">
          <NInput v-model:value="fillForm.opinion" type="textarea" :rows="4" placeholder="评审意见与反馈建议" />
        </NFormItem>
      </NForm>
    </NModal>

    <!-- 评审报告（汇总） -->
    <HPageView v-model:show="summaryShow" title="评审报告" width="700px">
      <template v-if="summary">
        <NDescriptions label-placement="left" :column="3" bordered size="small">
          <NDescriptionsItem label="邀请专家">{{ summary.total }}</NDescriptionsItem>
          <NDescriptionsItem label="已评审">{{ summary.done }}</NDescriptionsItem>
          <NDescriptionsItem label="平均分">{{ summary.avgScore ?? '--' }}</NDescriptionsItem>
        </NDescriptions>
        <NSpace style="margin: 12px 0">
          <NTag v-for="(cnt, key) in summary.conclusionCounts" :key="key" :bordered="false" type="info">
            <HDictText type="research_review_conclusion" :value="String(key)" />：{{ cnt }}
          </NTag>
        </NSpace>
        <NList size="small" bordered>
          <NListItem v-for="r in summary.reviews" :key="r.id">
            <div style="width: 100%">
              <NSpace justify="space-between">
                <span><strong>{{ r.expertName }}</strong></span>
                <span v-if="r.reviewStatus === 'DONE'">
                  {{ r.score }} 分 · <HDictText type="research_review_conclusion" :value="r.conclusion" />
                </span>
                <NTag v-else size="small" :bordered="false">待评审</NTag>
              </NSpace>
              <div v-if="r.opinion" style="margin-top: 4px; color: #555">{{ r.opinion }}</div>
            </div>
          </NListItem>
        </NList>
      </template>
    </HPageView>
  </HPageDataTable>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, resolveComponent } from 'vue';
import {
  NButton, NDescriptions, NDescriptionsItem, NForm, NFormItem, NInput, NInputNumber, NList, NListItem,
  NModal, NPopconfirm, NSelect, NSpace, NTag, useMessage,
} from 'naive-ui';
import { useAuthStore } from '@/store';
import { canFillReview } from '../utils/reviewIdentity';
import { reviewApi } from '../api/review';
import { expertApi } from '../api/expert';
import TopicSelect from '../components/TopicSelect.vue';
import AchievementSelect from '../components/AchievementSelect.vue';
import type { ResExpertVo, ResReviewSummaryVo, ResReviewVo } from '../typings';

const HDictText = resolveComponent('HDictText');
const message = useMessage();
const authStore = useAuthStore();
const mayInvite = computed(() => authStore.hasPermission('ADMIN,RESEARCH_ADMIN,RESEARCH_REVIEW_INVITE'));
const mayFill = (row: ResReviewVo) => canFillReview(row, authStore.userInfo?.id, authStore.hasPermission('ADMIN,RESEARCH_ADMIN,RESEARCH_REVIEW'));

const tableRef = ref<any>(null);

const queryParams = reactive({
  keyword: null as string | null,
  reviewType: null as string | null,
  reviewStatus: null as string | null,
});

const columns: any[] = [
  {
    title: '评审环节', key: 'reviewType', width: 120,
    render: (r: ResReviewVo) => h(HDictText, { type: 'research_review_type', value: r.reviewType }),
  },
  { title: '评审对象', key: 'businessName', minWidth: 200, columnType: 'title', ellipsis: { tooltip: true } },
  { title: '专家', key: 'expertName', width: 100 },
  {
    title: '状态', key: 'reviewStatus', width: 90,
    render: (r: ResReviewVo) => h(HDictText, { type: 'research_review_status', value: r.reviewStatus }),
  },
  { title: '评分', key: 'score', width: 70, render: (r: ResReviewVo) => r.score ?? '—' },
  {
    title: '结论', key: 'conclusion', width: 100,
    render: (r: ResReviewVo) => (r.conclusion ? h(HDictText, { type: 'research_review_conclusion', value: r.conclusion }) : '—'),
  },
  { title: '评审时间', key: 'reviewTime', width: 170, columnType: 'dateTime' },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 220,
    fixed: 'right' as const,
    render: (row: ResReviewVo) => {
      const buttons = [
        h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => onSummary(row) }, () => '评审报告'),
      ];
      if (row.reviewStatus === 'PENDING') {
        buttons.push(
          h(NButton, { text: true, type: 'info', size: 'small', disabled: !mayFill(row), style: 'margin-left:10px', onClick: () => onFill(row) }, () => row.reviewerUserId ? '本人填写评审' : '评审账号待核实'),
        );
        if (mayInvite.value) buttons.push(
          h(
            NPopconfirm,
            { onPositiveClick: () => onDelete(row) },
            {
              trigger: () => h(NButton, { text: true, type: 'error', size: 'small', style: 'margin-left:10px' }, () => '删除'),
              default: () => '确定删除该评审邀请？',
            },
          ),
        );
      }
      return buttons;
    },
  },
];

// ── 邀请 ──
const inviteShow = ref(false);
const inviteForm = reactive({
  reviewType: 'TOPIC_APPLY' as string,
  businessId: null as string | null,
  expertIds: [] as string[],
});
const expertOptions = ref<{ label: string; value: string; disabled: boolean }[]>([]);

onMounted(async () => {
  const resp: any = await expertApi.options();
  const list: ResExpertVo[] = resp?.data ?? [];
  expertOptions.value = list.map((e) => ({ label: `${e.name}（${e.orgName || '馆内'}）${e.userId ? '' : ' · 尚未绑定评审账号'}`, value: e.id!, disabled: !e.userId }));
});

function onInvite() {
  if (!mayInvite.value) { message.warning('当前账号不能邀请专家评审'); return; }
  inviteForm.businessId = null;
  inviteForm.expertIds = [];
  inviteShow.value = true;
}

async function submitInvite() {
  if (!mayInvite.value) { message.warning('当前账号不能邀请专家评审'); return false; }
  if (!inviteForm.businessId || !inviteForm.expertIds.length) {
    message.warning('请选择评审对象与专家');
    return false;
  }
  await reviewApi.invite({
    reviewType: inviteForm.reviewType,
    businessId: inviteForm.businessId,
    expertIds: inviteForm.expertIds,
  });
  message.success('已发出评审邀请');
  tableRef.value?.handleQuery?.();
}

// ── 填写 ──
const fillShow = ref(false);
const fillRow = ref<ResReviewVo | null>(null);
const fillForm = reactive({ score: 80 as number | null, conclusion: 'PASS' as string, opinion: '' });

function onFill(row: ResReviewVo) {
  if (!mayFill(row)) { message.warning('仅受邀专家本人可填写，且须有当前评审权限'); return; }
  fillRow.value = row;
  fillForm.score = 80;
  fillForm.conclusion = 'PASS';
  fillForm.opinion = '';
  fillShow.value = true;
}

function onView(row: ResReviewVo) {
  if (mayFill(row)) onFill(row);
  else onSummary(row);
}

async function submitFill() {
  if (!fillRow.value || !mayFill(fillRow.value)) { message.warning('当前账号不能提交该评审，请核对受邀专家身份'); return false; }
  if (!fillRow.value?.id || fillForm.score == null) {
    message.warning('请填写评分');
    return false;
  }
  await reviewApi.fill(fillRow.value.id, {
    score: fillForm.score,
    conclusion: fillForm.conclusion,
    opinion: fillForm.opinion || undefined,
  });
  message.success('评审已提交');
  tableRef.value?.handleQuery?.();
}

// ── 汇总 ──
const summaryShow = ref(false);
const summary = ref<ResReviewSummaryVo | null>(null);

async function onSummary(row: ResReviewVo) {
  const resp: any = await reviewApi.summary(row.reviewType!, row.businessId!);
  summary.value = resp?.data ?? null;
  summaryShow.value = true;
}

async function onDelete(row: ResReviewVo) {
  if (!mayInvite.value) { message.warning('当前账号不能删除评审邀请'); return; }
  await reviewApi.delete(row.id!);
  message.success('已删除');
  tableRef.value?.handleQuery?.();
}
</script>
