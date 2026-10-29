<template>
  <div style="padding: 16px; max-width: 1200px; margin: 0 auto">
    <!-- 过滤条件 -->
    <n-card title="选择范围" style="margin-bottom: 16px">
      <n-form label-width="80px" label-placement="left">
        <n-grid :cols="3" :x-gap="12">
          <n-gi>
            <n-form-item label="所属区域">
              <CascadeArea v-model:value="filter.areaId" clearable style="width: 100%" />
            </n-form-item>
          </n-gi>
          <n-gi>
            <n-form-item label="保护级别">
              <h-dict-select
                v-model:value="filter.publishLevel"
                type="four_cultural_relic_level"
                placeholder="请选择保护级别"
                clearable
                style="width: 100%"
              />
            </n-form-item>
          </n-gi>
          <n-gi>
            <n-form-item label="公布批次">
              <h-dict-select
                v-model:value="filter.publishBatch"
                type="basic_public_batch"
                placeholder="请选择公布批次"
                clearable
                style="width: 100%"
              />
            </n-form-item>
          </n-gi>
          <n-gi>
            <n-form-item label="主类别">
              <h-dict-select
                v-model:value="filter.mainCategory"
                type="four_cultural_relic_main_type"
                placeholder="请选择主类别"
                clearable
                style="width: 100%"
              />
            </n-form-item>
          </n-gi>
          <n-gi>
            <n-form-item label="时代">
              <n-input v-model:value="filter.publishYear" placeholder="请输入时代（如：明代）" clearable />
            </n-form-item>
          </n-gi>
          <n-gi>
            <n-form-item label=" ">
              <n-space>
                <n-button type="primary" :loading="previewing" @click="handlePreview">
                  <template #icon><HIcon icon="mdi:eye-outline" /></template>
                  预览匹配数量
                </n-button>
                <n-button @click="handleReset">重置</n-button>
              </n-space>
            </n-form-item>
          </n-gi>
        </n-grid>
      </n-form>
    </n-card>

    <!-- 预览结果 -->
    <n-card v-if="previewResult" title="预览结果" style="margin-bottom: 16px">
      <n-alert
        :type="previewResult.total > 0 ? 'info' : 'warning'"
        style="margin-bottom: 12px"
      >
        <template v-if="previewResult.total > 0">
          共匹配到 <strong>{{ previewResult.total }}</strong> 处不可移动文物，
          本次将提取前 <strong>100000</strong> 处生成知识图谱。
        </template>
        <template v-else>
          当前过滤条件没有匹配到任何文物记录，请调整条件后重试。
        </template>
      </n-alert>
      <n-data-table
        v-if="previewResult.samples?.length"
        :columns="previewColumns"
        :data="previewResult.samples"
        size="small"
        :pagination="false"
        max-height="320"
        style="margin-bottom: 4px"
      />
      <n-text depth="3" style="font-size: 12px">以上为前10条样本预览</n-text>
    </n-card>

    <!-- 生成设置 -->
    <n-card title="生成设置">
      <n-form label-width="120px" label-placement="left">
        <n-form-item label="知识图谱分类">
          <CategorySelect
            v-model:value="filter.categoryId"
            placeholder="选择节点默认分类（AI 提取的实体将归入此分类）"
            clearable
            style="width: 360px"
          />
        </n-form-item>
        <n-form-item label=" ">
          <n-space align="center">
            <n-button
              type="primary"
              size="large"
              :loading="generating"
              :disabled="!previewResult || previewResult.total === 0"
              @click="handleGenerate"
            >
              <template #icon><HIcon icon="mdi:auto-fix" /></template>
              AI 提取知识图谱
            </n-button>
            <n-text depth="3" style="font-size: 12px">
              提交后 AI 将在后台异步处理，可前往「知识图谱 → AI 生成」查看进度
            </n-text>
          </n-space>
        </n-form-item>
      </n-form>

      <!-- 生成成功提示 -->
      <n-alert v-if="draftResult" type="success" style="margin-top: 8px">
        <template #header>已成功提交 AI 提取任务</template>
        草稿名称：<strong>{{ draftResult.fileName }}</strong>，状态：{{ draftResult.status }}
        <br />
        <n-button text type="primary" style="margin-top: 6px" @click="goToGenerate">
          前往「知识图谱 → AI 生成」查看进度 →
        </n-button>
      </n-alert>
    </n-card>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive } from 'vue';
import { useRouter } from 'vue-router';
import { useMessage, NDataTable } from 'naive-ui';
import CascadeArea from '@/modules/sys/components/form/field/CascadeArea.vue';
import CategorySelect from '@/modules/knowledge/components/form/field/CategorySelect.vue';
import { previewKnowledgeFilter, generateKnowledgeBatch } from '../../api/basic/culturalRecordBasic.ts';

const router = useRouter();
const message = useMessage();

const filter = reactive({
  areaId: null as string | null,
  publishLevel: null as string | null,
  publishBatch: null as string | null,
  mainCategory: null as string | null,
  publishYear: null as string | null,
  categoryId: null as string | null,
});

const previewing = ref(false);
const generating = ref(false);
const previewResult = ref<{ total: number; samples: any[] } | null>(null);
const draftResult = ref<any>(null);

const previewColumns = [
  { key: 'publishName', title: '公布名称', ellipsis: { tooltip: true }, width: 200 },
  { key: 'publishLevel', title: '保护级别', width: 120 },
  { key: 'publishBatch', title: '公布批次', width: 100 },
  { key: 'publishYear', title: '时代', width: 100 },
  { key: 'mainCategory', title: '主类别', width: 120 },
  { key: 'publishAddress', title: '公布地址', ellipsis: { tooltip: true } },
];

function handleReset() {
  filter.areaId = null;
  filter.publishLevel = null;
  filter.publishBatch = null;
  filter.mainCategory = null;
  filter.publishYear = null;
  previewResult.value = null;
  draftResult.value = null;
}

async function handlePreview() {
  previewing.value = true;
  previewResult.value = null;
  draftResult.value = null;
  try {
    const resp = await previewKnowledgeFilter(filter);
    previewResult.value = resp.data;
  } catch {
    message.error('预览失败，请检查过滤条件');
  } finally {
    previewing.value = false;
  }
}

async function handleGenerate() {
  if (!previewResult.value || previewResult.value.total === 0) {
    message.warning('请先预览确认有匹配记录');
    return;
  }
  generating.value = true;
  draftResult.value = null;
  try {
    const resp = await generateKnowledgeBatch(filter);
    draftResult.value = resp.data;
    message.success('AI 提取任务已提交，请稍后在「知识图谱 → AI 生成」查看进度');
  } catch (e: any) {
    message.error(e?.response?.data?.message || '提交失败，请稍后重试');
  } finally {
    generating.value = false;
  }
}

function goToGenerate() {
  router.push('/knowledge/generate');
}
</script>
