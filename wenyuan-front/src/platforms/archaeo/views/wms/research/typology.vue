<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="page"
    :columns="columns"
    :params="paramsForm"
    :isAdd="false"
    :isEdit="false"
    @rowDelete="(row:any) => del(row.id)"
  >
    <template #queryForm>
      <n-form-item label="遗址">
        <WmsProjectSelect v-model:value="paramsForm.projectId" clearable filterable />
      </n-form-item>
      <n-form-item label="质地">
        <HDictSelect v-model:value="paramsForm.material" type="wms_artifact_material_type" clearable filterable />
      </n-form-item>
      <n-form-item label="发掘年度">
        <n-input-number
          :value="paramsForm.year ?? null"
          :show-button="false"
          :min="1900"
          :max="2100"
          placeholder="如 2023"
          clearable
          style="width: 120px"
          @update:value="(v: number | null) => (paramsForm.year = v ?? undefined)"
        />
      </n-form-item>
    </template>
    <template #toolbar-extra>
      <n-button @click="handleView">
        <template #icon><n-icon><IconMdiEyeOutline /></n-icon></template>
        查看
      </n-button>
      <n-button type="primary" @click="handleNew">
        <template #icon><n-icon><IconMdiPlus /></n-icon></template>
        新建型式分期
      </n-button>
    </template>

    <n-modal v-model:show="newShow" preset="card" class="typology-new-modal" style="width: 50%" title="新建型式分期">
      <template v-if="newStep === 0">
        <n-radio-group :value="newForm.source" class="mode-grid" @update:value="handleModeUpdate">
          <div class="mode-option" :class="{ active: newForm.source === 'AUTO' }" @click="chooseMode('AUTO')">
            <n-radio value="AUTO" class="mode-radio" />
            <div>
              <div class="mode-title">智能分期</div>
              <div class="mode-desc">基于文物卡片中的期段、器型、型、亚型、式和线图自动生成表格。</div>
            </div>
            <div v-if="newForm.source === 'AUTO'" class="mode-check">
              <n-icon><IconMdiCheckCircle /></n-icon>
            </div>
          </div>
          <div class="mode-option" :class="{ active: newForm.source === 'CUSTOM' }" @click="chooseMode('CUSTOM')">
            <n-radio value="CUSTOM" class="mode-radio" />
            <div>
              <div class="mode-title">自定义分期</div>
              <div class="mode-desc">使用空白模板，自定义表头、期段、图片、图名和单元格合并。</div>
            </div>
            <div v-if="newForm.source === 'CUSTOM'" class="mode-check">
              <n-icon><IconMdiCheckCircle /></n-icon>
            </div>
          </div>
        </n-radio-group>
      </template>

      <template v-else>
        <n-form label-placement="left" label-width="100px">
          <n-form-item label="遗址" required>
            <WmsProjectSelect
              v-model:value="newForm.projectId"
              clearable
              filterable
              @update:value="handleNewProjectChange"
            />
          </n-form-item>
          <n-form-item label="器物质地">
            <n-select
              v-if="newForm.source === 'AUTO'"
              v-model:value="newForm.material"
              clearable
              filterable
              :loading="summaryLoading"
              :disabled="!newForm.projectId"
              :options="materialOptions"
              placeholder="选择该遗址已录入的质地"
              @update:value="handleNewMaterialChange"
            />
            <HDictSelect
              v-else
              v-model:value="newForm.material"
              type="wms_artifact_material_type"
              clearable
              filterable
            />
          </n-form-item>
          <n-form-item v-if="newForm.source === 'AUTO'" label="文物器型" required>
            <n-select
              v-model:value="newForm.artifactTypes"
              multiple
              clearable
              filterable
              :loading="summaryLoading"
              :disabled="!newForm.projectId"
              :options="artifactTypeOptions"
              placeholder="选择要生成的器型列"
            />
          </n-form-item>
          <n-form-item label="标题">
            <n-input v-model:value="newForm.title" placeholder="缺省自动生成" />
          </n-form-item>
          <n-form-item label="发掘时间">
            <n-date-picker
              :value="newForm.excavateDate ?? null"
              type="date"
              clearable
              style="width: 100%"
              @update:value="(v: number | null) => (newForm.excavateDate = v ?? undefined)"
            />
          </n-form-item>

          <n-form-item v-if="newForm.source === 'AUTO'" label="分期预览">
            <div class="summary-box">
              <div class="summary-line">
                <n-tag size="small" :bordered="false">期段 {{ newSummary.periodRows?.length || 0 }}</n-tag>
                <n-tag size="small" :bordered="false">器型 {{ artifactTypeOptions.length }}</n-tag>
                <n-button size="small" :loading="summaryLoading" :disabled="!newForm.projectId" @click="loadNewSummary">
                  刷新预览
                </n-button>
              </div>
              <n-data-table
                v-if="statRows.length"
                size="small"
                style="margin-top: 8px"
                :columns="statColumns"
                :data="statRows"
                :pagination="false"
                :max-height="220"
              />
              <n-empty v-else size="small" description="选择遗址后预览该遗址的分期统计" />
            </div>
          </n-form-item>
        </n-form>
      </template>

      <template #footer>
        <n-space justify="end">
          <n-button @click="newShow = false">取消</n-button>
          <n-button v-if="newStep > 0" @click="newStep = 0">上一步</n-button>
          <n-button
            v-if="newStep === 0"
            type="primary"
            :disabled="!newForm.source"
            @click="newStep = 1"
          >
            下一步
          </n-button>
          <n-button v-else type="primary" :loading="newLoading" @click="doNew">创建并进入编辑</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 查看：选遗址 → 弹出该遗址各器类的分期表 → 点击打开 -->
    <n-modal v-model:show="viewShow" preset="card" style="width: 600px" title="查看型式分期表">
      <n-form label-placement="left" label-width="80px">
        <n-form-item label="遗址" required>
          <WmsProjectSelect
            v-model:value="viewProjectId"
            clearable
            filterable
            @update:value="loadViewTables"
          />
        </n-form-item>
      </n-form>
      <n-spin :show="viewLoading">
        <div v-if="!viewProjectId" class="view-hint">请选择遗址，查看其各器类的型式分期表。</div>
        <n-empty v-else-if="!viewTables.length" description="该遗址暂无已保存的型式分期表" />
        <n-grid v-else :cols="2" x-gap="10" y-gap="10">
          <n-grid-item v-for="t in viewTables" :key="t.id">
            <n-card size="small" hoverable embedded style="cursor: pointer" @click="openTable(t.id)">
              <div class="vt-title">{{ t.title || '型式分期表' }}</div>
              <div class="vt-meta">
                <n-tag size="tiny" :bordered="false" type="info">
                  <HDictText :value="t.material" type="wms_artifact_material_type" />
                </n-tag>
                <n-tag size="tiny" :bordered="false">{{ t.source === 'AUTO' ? '智能' : '自定义' }}</n-tag>
              </div>
            </n-card>
          </n-grid-item>
        </n-grid>
      </n-spin>
    </n-modal>
  </h-page-data-table>
</template>

<script lang="ts" setup>
import { h, ref } from 'vue';
import { useMessage, NButton } from 'naive-ui';
import { useRouter } from 'vue-router';
import { WmsProjectSelect } from '../../../components/form/field';
import { page, create, del, autoBuild, summary } from '../../../api/typology';
import { frontType } from '@/modules/sys/api/dict';

const message = useMessage();
const router = useRouter();
const refPageTable = ref();

const paramsForm = ref<{ projectId?: string; material?: string; year?: number }>({
  projectId: undefined,
  material: undefined,
  year: undefined,
});

const columns = [
  { key: 'seq', title: '序号', width: 60, align: 'center', render: (_r: any, i: number) => i + 1 },
  { key: 'title', title: '标题', width: 220, ellipsis: true },
  { key: 'projectName', title: '遗址名称', width: 150, ellipsis: true },
  { key: 'material', title: '器物质地', width: 100, dictType: 'wms_artifact_material_type' },
  { key: 'categoryName', title: '器物大类', width: 100 },
  { key: 'source', title: '来源', width: 80, render: (r: any) => (r.source === 'AUTO' ? '智能' : '自定义') },
  { key: 'excavateDate', title: '发掘时间', columnType: 'dateTime', width: 150 },
  { key: 'createTime', title: '生成时间', columnType: 'dateTime', width: 150 },
  {
    key: 'operate',
    type: 'operate',
    fixed: 'right',
    title: '操作',
    align: 'center',
    width: 180,
    render(row: any) {
      return [
        h(NButton, { size: 'tiny', type: 'primary', onClick: () => goEditor(row.id) }, { default: () => '编辑' }),
        h(NButton, { size: 'tiny', type: 'error', style: 'margin-left:6px', onClick: () => handleDelete(row.id) }, { default: () => '删除' }),
      ];
    },
  },
];

const newShow = ref(false);
const newStep = ref(0);
const newLoading = ref(false);
const newForm = ref<{
  source?: 'AUTO' | 'CUSTOM';
  projectId?: string;
  projectName?: string;
  material?: string;
  title?: string;
  excavateDate?: number;
  artifactTypes?: string[];
}>({});

const summaryLoading = ref(false);
const newSummary = ref<any>({});
const statRows = ref<any[]>([]);
const materialOptions = ref<any[]>([]);
const artifactTypeOptions = ref<any[]>([]);
const statColumns = [
  { key: 'artifactType', title: '器型', width: 110 },
  { key: 'total', title: '数量', width: 70 },
  { key: 'styleCount', title: '型数', width: 70 },
  { key: 'stageCount', title: '式数', width: 70 },
  { key: 'phaseCount', title: '期数', width: 70 },
  { key: 'sectionCount', title: '段数', width: 70 },
];

const materialDict = ref<Record<string, string>>({});

frontType('wms_artifact_material_type', true).then((resp: any) => {
  const dict: Record<string, string> = {};
  (resp.data || []).forEach((item: any) => {
    dict[item.value] = item.label;
  });
  materialDict.value = dict;
});

function materialLabel(value: string) {
  return materialDict.value[value] || value;
}

function handleNew() {
  newForm.value = {};
  newStep.value = 0;
  newSummary.value = {};
  statRows.value = [];
  materialOptions.value = [];
  artifactTypeOptions.value = [];
  newShow.value = true;
}

function chooseMode(source: 'AUTO' | 'CUSTOM') {
  newForm.value = { source };
}

function handleModeUpdate(value: string | number | boolean | null) {
  if (value === 'AUTO' || value === 'CUSTOM') {
    chooseMode(value);
  }
}

async function handleNewProjectChange(projectId?: string) {
  newForm.value.material = undefined;
  newForm.value.artifactTypes = [];
  newSummary.value = {};
  statRows.value = [];
  materialOptions.value = [];
  artifactTypeOptions.value = [];
  if (projectId && newForm.value.source === 'AUTO') {
    await loadNewSummary();
  }
}

async function handleNewMaterialChange() {
  newForm.value.artifactTypes = [];
  if (newForm.value.projectId) {
    await loadNewSummary();
  }
}

async function loadNewSummary() {
  if (!newForm.value.projectId) return;
  summaryLoading.value = true;
  statRows.value = [];
  try {
    const resp: any = await summary({ projectId: newForm.value.projectId, material: newForm.value.material });
    newSummary.value = resp.data || {};
    materialOptions.value = (newSummary.value.materials || []).map((item: any) => ({
      label: `${materialLabel(item.value)}（${item.count || 0}）`,
      value: item.value,
    }));
    artifactTypeOptions.value = (newSummary.value.artifactTypes || []).map((item: any) => ({
      label: `${item.label || item.value}（${item.count || 0}）`,
      value: item.value,
    }));
    statRows.value = newSummary.value.stats || [];
    if (!newForm.value.artifactTypes?.length) {
      newForm.value.artifactTypes = artifactTypeOptions.value.map((item: any) => item.value);
    }
  } catch (e: any) {
    message.error(e?.message || '加载分期概要失败');
  } finally {
    summaryLoading.value = false;
  }
}

function buildCustomContent() {
  const cols = [{ artifactType: '' }, { artifactType: '' }, { artifactType: '' }];
  const rows = [
    {
      phase: '',
      section: '',
      cells: cols.map(() => ({ text: '', items: [{ text: '', imgWidth: 120 }], rowspan: 1, colspan: 1 })),
    },
    {
      phase: '',
      section: '',
      cells: cols.map(() => ({ text: '', items: [{ text: '', imgWidth: 120 }], rowspan: 1, colspan: 1 })),
    },
  ];
  return JSON.stringify({ cols, rows, layout: 'A4_LANDSCAPE' });
}

async function doNew() {
  if (!newForm.value.projectId) {
    message.warning('请先选择遗址');
    return;
  }
  if (newForm.value.source === 'AUTO' && !newForm.value.artifactTypes?.length) {
    message.warning('请至少选择一个文物器型');
    return;
  }
  newLoading.value = true;
  try {
    let content: string | undefined;
    if (newForm.value.source === 'AUTO') {
      const resp: any = await autoBuild({
        projectId: newForm.value.projectId!,
        projectName: newForm.value.projectName,
        material: newForm.value.material,
        artifactTypes: newForm.value.artifactTypes,
      });
      content = resp.data;
    } else {
      content = buildCustomContent();
    }
    const resp: any = await create({
      title: newForm.value.title,
      projectId: newForm.value.projectId,
      projectName: newForm.value.projectName,
      material: newForm.value.material,
      source: newForm.value.source,
      excavateDate: newForm.value.excavateDate,
      content,
    });
    newShow.value = false;
    router.push({ path: '/wms/research/typologyEditor', query: { id: resp.data } });
  } catch (e: any) {
    message.error(e?.message || '创建失败');
  } finally {
    newLoading.value = false;
  }
}

function goEditor(id: string) {
  router.push({ path: '/wms/research/typologyEditor', query: { id } });
}
async function handleDelete(id: string) {
  await del(id);
  message.success('已删除');
  refPageTable.value?.handleRefresh();
}

/** ===== 查看：选遗址 → 弹出器类分期表 → 打开 ===== */
const viewShow = ref(false);
const viewProjectId = ref<string | undefined>(undefined);
const viewTables = ref<any[]>([]);
const viewLoading = ref(false);

function handleView() {
  viewProjectId.value = undefined;
  viewTables.value = [];
  viewShow.value = true;
}
async function loadViewTables(projectId?: string) {
  if (!projectId) {
    viewTables.value = [];
    return;
  }
  viewLoading.value = true;
  try {
    const resp: any = await page({ projectId, pageSize: 0 });
    viewTables.value = resp.data?.list || [];
  } catch (e: any) {
    message.error(e?.message || '加载失败');
  } finally {
    viewLoading.value = false;
  }
}
function openTable(id: string) {
  viewShow.value = false;
  router.push({ path: '/wms/research/typologyEditor', query: { id } });
}
</script>

<style scoped>
.mode-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
}
.mode-option {
  position: relative;
  display: flex;
  gap: 12px;
  min-height: 112px;
  padding: 18px 42px 18px 16px;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.18s ease, background-color 0.18s ease, box-shadow 0.18s ease;
}
.mode-option:hover {
  border-color: #18a058;
  box-shadow: 0 4px 14px rgba(24, 160, 88, 0.12);
}
.mode-option.active {
  border-color: #18a058;
  background: rgba(24, 160, 88, 0.08);
  box-shadow: 0 0 0 2px rgba(24, 160, 88, 0.18);
}
.mode-radio {
  flex: 0 0 auto;
  margin-top: 2px;
}
.mode-title {
  color: #1f2937;
  font-size: 15px;
  font-weight: 600;
  line-height: 22px;
}
.mode-desc {
  margin-top: 8px;
  color: #666;
  font-size: 13px;
  line-height: 20px;
}
.mode-option.active .mode-title {
  color: #18a058;
}
.mode-check {
  position: absolute;
  top: 14px;
  right: 14px;
  color: #18a058;
  font-size: 20px;
  line-height: 1;
}
.view-hint {
  color: #999;
  font-size: 13px;
  padding: 12px 0;
  text-align: center;
}
.vt-title {
  font-weight: 500;
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.vt-meta {
  display: flex;
  gap: 6px;
  align-items: center;
}
</style>
