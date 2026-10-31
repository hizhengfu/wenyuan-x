<template>
  <div class="exchange-page">
    <n-tabs
      v-model:value="activeTab"
      class="exchange-tabs"
      type="line"
      animated
      pane-wrapper-style="display: flex; flex: 1; min-height: 0;"
      pane-style="display: flex; flex: 1; min-height: 0; flex-direction: column; overflow: auto;"
    >
      <n-tab-pane name="export" tab="数据导出">
        <h-page-data-table
          ref="exportTableRef"
          title="指定源 Schema"
          :fetch-data="fetchExportTablePage"
          :params="exportForm"
          :columns="catalogColumns"
          :init-query="false"
          :is-add="false"
          :is-export="false"
          :show-operate="false"
          :enable-drag="false"
          @updateCheckedRowKeys="handleExportSelection"
        >
          <template #queryForm>
            <n-form-item label="源数据源" path="dataSource">
              <n-select
                v-model:value="exportForm.dataSource"
                :options="exportDataSourceOptions"
                placeholder="请选择数据源"
                @update:value="handleExportDataSourceChange"
              />
            </n-form-item>
            <n-form-item label="源 Schema" path="schemaId">
              <n-select
                v-model:value="exportForm.schemaId"
                :options="exportSchemaOptions"
                :disabled="!exportForm.dataSource"
                placeholder="请选择唯一源 Schema"
                @update:value="handleExportSchemaChange"
              />
            </n-form-item>
            <n-form-item label="一致性" path="consistencyMode">
              <h-dict-select
                v-model:value="exportForm.consistencyMode"
                type="sys_data_exchange_consistency_mode"
                @update:value="clearExportPreview"
              />
            </n-form-item>
            <n-form-item label="导出范围" path="selectionMode">
              <h-dict-select
                v-model:value="exportForm.selectionMode"
                type="sys_data_exchange_selection_mode"
                @update:value="handleExportSelectionModeChange"
              />
            </n-form-item>
            <n-form-item label="包含视图" path="includeViews">
              <n-switch v-model:value="exportForm.includeViews" @update:value="handleExportCatalogChanged" />
            </n-form-item>
            <n-form-item label="表名" path="keyword">
              <n-input v-model:value="exportForm.keyword" clearable placeholder="输入表名" />
            </n-form-item>
          </template>
          <template #activeBtn="{ checkedRowKeys = [] }">
            <n-text depth="3">
              {{
                exportForm.selectionMode === 'ALL_TABLES_IN_SCHEMA'
                  ? '将导出当前 Schema 的全部表'
                  : `已选择 ${checkedRowKeys?.length ?? 0} 张表`
              }}
            </n-text>
          </template>
          <template #toolbar-extra>
            <n-space>
              <n-button v-if="activeExportTask" secondary @click="exportTaskVisible = true"> 查看导出任务 </n-button>
              <n-button
                type="primary"
                :loading="exportPreviewLoading"
                :disabled="!canPreviewExport"
                @click="handlePreviewExport"
              >
                预检查
              </n-button>
            </n-space>
          </template>
        </h-page-data-table>
      </n-tab-pane>

      <n-tab-pane name="import" tab="数据导入">
        <n-card size="small" class="import-wizard">
          <n-steps :current="importStep" size="small">
            <n-step title="选择目标并上传" description="指定唯一目标 Schema，上传 HDE 包" />
            <n-step title="检查交换包" description="校验文件完整性和结构兼容性" />
            <n-step title="配置导入" description="确认表列映射与冲突策略" />
            <n-step title="认证并导入" description="二次认证后执行后台任务" />
          </n-steps>

          <n-divider />

          <div class="import-step-heading">
            <n-avatar round :size="36">{{ importStep }}</n-avatar>
            <div>
              <div class="import-step-heading__title">{{ importStepTitle }}</div>
              <n-text depth="3">{{ importStepDescription }}</n-text>
            </div>
          </div>

          <section v-if="importStep === 1" class="import-step-content">
            <n-form label-placement="left" inline>
              <n-form-item label="目标数据源">
                <n-select
                  v-model:value="importForm.dataSource"
                  :options="importDataSourceOptions"
                  placeholder="请选择目标数据源"
                  style="width: 220px"
                  @update:value="handleImportDataSourceChange"
                />
              </n-form-item>
              <n-form-item label="目标 Schema">
                <n-select
                  v-model:value="importForm.schemaId"
                  :options="importSchemaOptions"
                  :disabled="!importForm.dataSource"
                  style="width: 220px"
                  placeholder="请选择唯一目标 Schema"
                />
              </n-form-item>
            </n-form>

            <template v-if="importForm.schemaId">
              <n-alert type="info" class="mb-4">
                此时只上传文件，不会创建导入记录。点击“检查交换包”后才正式发起导入流程。
              </n-alert>
              <h-upload
                :content-id="importUploadContentId"
                module="sys-data-exchange"
                accept=".hde,application/vnd.hizhengfu.data-exchange+zip"
                :multiple="false"
                :enable-chunk="true"
                :enable-quick-upload="true"
                allow-dragger
                @upload-finish="handleUploadFinish"
              >
                <div class="upload-copy">
                  <div class="text-lg font-medium">拖入或选择 HDE 数据交换包</div>
                  <n-text depth="3">大文件自动分片，上传完成后进入交换包检查</n-text>
                </div>
              </h-upload>
              <n-space justify="end" class="mt-4">
                <n-button type="primary" :disabled="!uploadedFileId" :loading="inspectCreating" @click="handleInspect">
                  下一步：检查交换包
                </n-button>
              </n-space>
            </template>
          </section>

          <section v-else-if="importStep === 2" class="import-step-content">
            <n-alert
              v-if="activeImportTask?.status === 'FAILED'"
              type="error"
              title="交换包检查失败"
              class="mb-4"
            >
              {{ activeImportTask.errorMessage || activeImportTask.message || '未能完成交换包检查，可使用当前文件重新检查。' }}
            </n-alert>
            <n-alert v-else-if="activeImportTask?.status === 'CANCELLED'" type="warning" title="检查已取消" class="mb-4">
              交换包检查任务已取消，请重新选择交换包。
            </n-alert>
            <n-alert v-else type="info" class="mb-4">
              正在校验 HDE 清单、文件摘要、源 Schema 结构，并与目标 Schema 中的物理表进行兼容性比较。
            </n-alert>
            <task-progress-card v-if="activeImportTask" title="交换包检查进度" :task="activeImportTask" />
            <n-empty v-else description="检查任务尚未创建" />
            <n-space v-if="activeImportTask && isTerminalAsyncTask(activeImportTask.status)" justify="end" class="mt-4">
              <n-button
                v-if="activeImportTask.status === 'FAILED' && activeImportTask.taskType === 'DATA_EXCHANGE_INSPECT'"
                type="primary"
                :loading="inspectCreating"
                @click="handleReinspect"
              >
                使用当前文件重新检查
              </n-button>
              <n-button secondary @click="resetImport">重新选择交换包</n-button>
            </n-space>
          </section>

          <section v-else-if="importStep === 3 && inspection" class="import-step-content">
            <n-descriptions bordered label-placement="top" :column="3" class="mb-4">
              <n-descriptions-item label="源 Schema">
                {{ inspection.sourceSchema || inspection.sourceCatalog || 'default' }}
              </n-descriptions-item>
              <n-descriptions-item label="目标 Schema">{{ inspection.targetSchema }}</n-descriptions-item>
              <n-descriptions-item label="源表数量">{{ inspection.tables.length }}</n-descriptions-item>
            </n-descriptions>
            <n-alert :type="inspection.compatible ? 'success' : 'warning'" class="mb-4">
              源、目标 Schema 名称无需相同。默认只选择直接兼容和可转换的表，也可以手动选择部分表并调整映射。
            </n-alert>
            <n-alert v-if="importRetryError" type="warning" title="正在调整失败任务的导入参数" class="mb-4">
              上次失败信息：{{ importRetryError }}。已提交的批次不会回滚，请根据实际数据选择跳过或更新等冲突策略。
            </n-alert>
            <n-space justify="space-between" align="center" class="mb-3">
              <span>已选择 {{ selectedImportTables.length }} / {{ inspection.tables.length }} 张表</span>
              <n-space>
                <n-button secondary @click="selectCompatibleImportTables">仅选兼容表</n-button>
                <n-button secondary @click="selectAllImportTables">全选</n-button>
                <n-button secondary @click="clearImportTableSelection">清空</n-button>
              </n-space>
            </n-space>
            <n-table striped :single-line="false">
              <thead>
                <tr>
                  <th class="import-selection-column">
                    <n-checkbox
                      :checked="allImportTablesSelected"
                      :indeterminate="someImportTablesSelected"
                      @update:checked="handleAllImportTableSelection"
                    />
                  </th>
                  <th>源表</th>
                  <th>行数</th>
                  <th>兼容性</th>
                  <th>目标表</th>
                  <th>列映射</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="table in inspection.tables" :key="table.sourceTableId">
                  <td>
                    <n-checkbox
                      :checked="selectedImportTableIdSet.has(table.sourceTableId)"
                      @update:checked="(checked) => handleImportTableSelection(table.sourceTableId, checked)"
                    />
                  </td>
                  <td>{{ table.sourceTable }}</td>
                  <td>{{ formatNumber(table.rowCount) }}</td>
                  <td>
                    <n-tag :type="compatibilityTagType(table.compatibility)">
                      <h-dict-text :value="table.compatibility" type="sys_data_exchange_compatibility" />
                    </n-tag>
                  </td>
                  <td>
                    <n-select
                      v-model:value="importMappings[table.sourceTableId].targetTableCatalogId"
                      :options="targetTableOptions"
                      filterable
                      @update:value="() => handleTargetTableChange(table)"
                    />
                  </td>
                  <td><n-button text type="primary" @click="openColumnMapping(table)">配置列映射</n-button></td>
                </tr>
              </tbody>
            </n-table>
            <n-divider />
            <n-form label-placement="left">
              <n-form-item label="冲突策略">
                <h-dict-select v-model:value="importStrategy" type="sys_data_exchange_import_strategy" />
              </n-form-item>
            </n-form>
            <n-alert type="warning">
              只会处理已勾选的表；“清空后导入”也只清空已勾选表对应的目标表。导入按批次提交，取消或后续批次失败不会回滚已经提交的数据。
            </n-alert>
            <n-space justify="space-between" class="mt-4">
              <n-button secondary @click="resetImport">重新选择交换包</n-button>
              <n-button
                type="primary"
                :disabled="!canExecuteImport"
                :loading="importCreating"
                @click="handleExecuteImport"
              >
                下一步：二次认证并导入
              </n-button>
            </n-space>
          </section>

          <section v-else class="import-step-content">
            <n-alert
              v-if="activeImportTask?.status === 'FAILED'"
              type="error"
              title="数据导入失败"
              class="mb-4"
            >
              {{ activeImportTask.errorMessage || activeImportTask.message || '后台导入任务执行失败。' }}
            </n-alert>
            <n-alert v-else-if="activeImportTask?.status === 'CANCELLED'" type="warning" title="数据导入已取消" class="mb-4">
              已提交批次不会自动回滚，可以在表级错误详情中核对实际处理结果。
            </n-alert>
            <n-alert v-else-if="activeImportTask?.status === 'SUCCESS'" type="success" title="数据导入完成" class="mb-4">
              数据已经导入目标 Schema，可以在交换记录中查看各表处理数量。
            </n-alert>
            <n-alert v-else type="warning" class="mb-4">
              导入任务已进入后台执行。关闭页面不会中断任务，可以在“交换记录”中继续查看表级进度。
            </n-alert>
            <task-progress-card v-if="activeImportTask" title="正式导入进度" :task="activeImportTask" />
            <n-empty v-else description="导入任务尚未创建" />
            <n-space v-if="activeImportTask && isTerminalAsyncTask(activeImportTask.status)" justify="end" class="mt-4">
              <n-button
                v-if="activeImportTask.status === 'FAILED' && importJobId"
                secondary
                @click="showCurrentImportJobDetail"
              >
                查看表级错误
              </n-button>
              <n-button
                v-if="activeImportTask.status === 'FAILED' && importJobId"
                type="primary"
                @click="reviseImportParameters"
              >
                调整导入参数并重试
              </n-button>
              <n-button type="primary" @click="resetImport">开始新的导入</n-button>
            </n-space>
          </section>
        </n-card>
      </n-tab-pane>

      <n-tab-pane name="jobs" tab="交换记录">
        <h-page-data-table
          ref="jobTableRef"
          title="交换记录"
          :fetch-data="jobs"
          :params="jobParams"
          :columns="jobColumns"
          :is-add="false"
          :is-export="false"
          :show-operate="false"
          :enable-drag="false"
        >
          <template #queryForm>
            <n-form-item label="方向" path="direction">
              <h-dict-select v-model:value="jobParams.direction" type="sys_data_exchange_direction" clearable />
            </n-form-item>
            <n-form-item label="状态" path="status">
              <h-dict-select v-model:value="jobParams.status" type="sys_data_exchange_job_status" clearable />
            </n-form-item>
          </template>
        </h-page-data-table>
      </n-tab-pane>
    </n-tabs>

    <n-modal v-model:show="exportPreviewVisible" preset="card" style="width: 800px; height: 600px" title="导出预检查">
      <template v-if="exportPreview">
        <n-descriptions bordered :column="4" label-placement="top">
          <n-descriptions-item label="源 Schema">{{ exportPreview.sourceSchema.name }}</n-descriptions-item>
          <n-descriptions-item label="实际表数">{{ exportPreview.tables.length }}</n-descriptions-item>
          <n-descriptions-item label="预计行数">{{ formatNumber(exportPreview.estimatedRows) }}</n-descriptions-item>
          <n-descriptions-item label="风险等级">
            <n-tag :type="riskTagType(exportPreview.riskLevel)">
              <h-dict-text :value="exportPreview.riskLevel" type="sys_data_exchange_risk_level" />
            </n-tag>
          </n-descriptions-item>
        </n-descriptions>
        <n-alert v-if="exportPreview.stepUpRequired" type="warning" class="mt-3">
          本次导出需要密码、图形验证码二次认证，授权仅对当前表快照生效一次。
        </n-alert>
      </template>
      <template #action>
        <n-space justify="end">
          <n-button @click="exportPreviewVisible = false">取消</n-button>
          <n-button type="primary" :loading="exportCreating" :disabled="!exportPreview" @click="handleCreateExport">
            开始后台导出
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal v-model:show="exportTaskVisible" preset="card" style="width:800px" title="导出任务">
      <template v-if="activeExportTask">
        <n-alert v-if="activeExportTask.status === 'SUCCESS'" type="success" title="导出完成" class="mb-4">
          HDE 数据交换包已经生成，可以直接下载；文件也会保留在“交换记录”中。
        </n-alert>
        <n-alert
          v-else-if="activeExportTask.status === 'FAILED' || activeExportTask.status === 'CANCELLED'"
          type="error"
          :title="activeExportTask.status === 'FAILED' ? '导出失败' : '导出已取消'"
          class="mb-4"
        >
          {{ activeExportTask.errorMessage || '任务未生成可下载文件，请检查后重新导出。' }}
        </n-alert>
        <n-alert v-else type="info" title="正在后台导出" class="mb-4">
          关闭弹窗不会中断任务，可以通过“查看导出任务”再次打开。
        </n-alert>
        <task-progress-card title="导出进度" :task="activeExportTask" />
      </template>
      <template #action>
        <n-space justify="end">
          <n-button secondary @click="showJobsFromExportTask">查看交换记录</n-button>
          <n-button
            v-if="activeExportTask && isTerminalAsyncTask(activeExportTask.status)"
            secondary
            @click="resetExportResult"
          >
            继续导出
          </n-button>
          <n-button
            v-if="activeExportTask?.status === 'SUCCESS'"
            type="primary"
            :loading="downloadLoading"
            :disabled="!activeExportJobId"
            @click="downloadCurrentExport"
          >
            下载 HDE 文件
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal v-model:show="columnMappingVisible" preset="card" title="配置列映射" style="width: 720px">
      <n-table v-if="editingInspection" striped :single-line="false">
        <thead>
          <tr>
            <th>源列</th>
            <th>目标列</th>
            <th>检查状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="column in importMappings[editingInspection.sourceTableId]?.columns" :key="column.source">
            <td>{{ column.source }}</td>
            <td>
              <n-select v-model:value="column.target" clearable :options="editingTargetColumnOptions" />
            </td>
            <td>
              <h-dict-text
                :value="inspectionColumnStatus(editingInspection, column.source)"
                type="sys_data_exchange_compatibility"
              />
            </td>
          </tr>
        </tbody>
      </n-table>
    </n-modal>

    <n-modal
      v-model:show="authVisible"
      preset="card"
      title="高风险操作二次认证"
      style="width: 480px"
      :mask-closable="false"
    >
      <n-form label-placement="left" label-width='120px'>
        <n-form-item label="当前密码">
          <n-input v-model:value="authForm.password" type="password" show-password-on="mousedown" />
        </n-form-item>
        <n-form-item label="图形验证码">
          <h-field-captcha ref="captchaRef" v-model:value="authForm.captchaCode" :form="authForm" />
        </n-form-item>
      </n-form>
      <n-alert type="warning">授权有效期很短，仅能用于当前请求一次，不会保存到浏览器。</n-alert>
      <template #action>
        <n-space justify="end">
          <n-button @click="authVisible = false">取消</n-button>
          <n-button type="primary" :loading="authLoading" @click="submitAuth">验证并继续</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal v-model:show="jobDetailVisible" preset="card" title="表级执行详情" style="width: 1000px">
      <n-data-table :columns="jobTableColumns" :data="jobTableRows" :max-height="560" />
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, nextTick, onBeforeUnmount, onMounted, reactive, ref, resolveComponent } from 'vue';
import type { PropType } from 'vue';
import { NButton, NProgress, NSpace, NTag, useMessage } from 'naive-ui';
import type { DataTableColumns } from 'naive-ui';
import { saveAs } from 'file-saver';
import HFieldCaptcha from '../components/form/field/captcha/index.vue';
import type { AsyncTask } from '../api/asyncTask';
import { cancelAsyncTask } from '../api/asyncTask';
import { isTerminalAsyncTask, subscribeAsyncTaskEvents } from '../utils/asyncTask';
import type { AsyncTaskEventSubscription } from '../utils/asyncTask';
import { rsaEncrypt } from '@/utils/rsaEncrypt';
import {
  createExport,
  createImport,
  dataSources,
  downloadDigest,
  downloadExport,
  executeImport,
  getJob,
  inspectImport,
  jobTables,
  jobs,
  previewExport,
  previewImport,
  schemas,
  tables,
  verifySensitiveOperation,
} from '../api/dataExchange';
import type {
  DataSourceOption,
  ExportPreview,
  ImportStrategy,
  InspectionResult,
  PhysicalTable,
  SchemaOption,
  SelectionMode,
  TableInspection,
} from '../api/dataExchange';

const TaskProgressCard = defineComponent({
  props: { title: { type: String, required: true }, task: { type: Object as PropType<AsyncTask>, required: true } },
  setup(props) {
    return () =>
      h('div', { class: 'task-card' }, [
        h('div', { class: 'task-card__header' }, [
          h('strong', props.title),
          h(NTag, null, { default: () => props.task.status }),
        ]),
        h(NProgress, {
          percentage: props.task.progress || 0,
          processing: !isTerminalAsyncTask(props.task.status),
          indicatorPlacement: 'inside',
        }),
        h('div', { class: 'task-card__message' }, `${props.task.stage || ''} ${props.task.message || ''}`),
      ]);
  },
});

interface TableMappingState {
  targetTableCatalogId: string;
  columns: Array<{ source: string; target?: string | null }>;
}

const message = useMessage();
const HDictText = resolveComponent('HDictText');
const activeTab = ref('export');
const sourceRows = ref<DataSourceOption[]>([]);
const exportSchemas = ref<SchemaOption[]>([]);
const importSchemas = ref<SchemaOption[]>([]);
const targetTables = ref<PhysicalTable[]>([]);
const exportTableRef = ref<any>();
const jobTableRef = ref<any>();
const exportPreviewLoading = ref(false);
const exportCreating = ref(false);
const selectedExportTableIds = ref<Array<string | number>>([]);
const exportPreview = ref<ExportPreview | null>(null);
const exportPreviewVisible = ref(false);
const exportTaskVisible = ref(false);
const activeExportTask = ref<AsyncTask | null>(null);
const activeExportJobId = ref('');
const activeImportTask = ref<AsyncTask | null>(null);
const taskSubscriptions = new Map<string, AsyncTaskEventSubscription>();

const exportForm = reactive({
  dataSource: '',
  schemaId: '',
  selectionMode: 'SELECTED' as SelectionMode,
  includeViews: false,
  consistencyMode: 'CONSISTENT_SNAPSHOT',
  keyword: '',
});
const jobParams = reactive<{ direction?: string; status?: string }>({ direction: undefined, status: undefined });
const importForm = reactive({ dataSource: '', schemaId: '' });
const importJobId = ref('');
const importUploadContentId = ref(createImportUploadContentId());
const inspectionTaskId = ref('');
const uploadedFileId = ref('');
const inspectCreating = ref(false);
const importCreating = ref(false);
const resumingImportJobId = ref('');
const inspection = ref<InspectionResult | null>(null);
const importMappings = reactive<Record<string, TableMappingState>>({});
const selectedImportTableIds = ref<string[]>([]);
const importStrategy = ref<ImportStrategy>('FAIL_IF_EXISTS');
const importRetryError = ref('');
const columnMappingVisible = ref(false);
const editingInspection = ref<TableInspection | null>(null);
const jobDetailVisible = ref(false);
const jobTableRows = ref<any[]>([]);

const authVisible = ref(false);
const authLoading = ref(false);
const downloadLoading = ref(false);
const captchaRef = ref<any>();
const authForm = reactive({ password: '', captchaCode: '', captchaUuid: '', uuid: '' });
let pendingAuth: { action: string; digest: string; callback: (grant: string) => Promise<void> } | null = null;

const exportDataSourceOptions = computed(() => sourceRows.value.filter((i) => i.exportEnabled).map(toOption));
const importDataSourceOptions = computed(() => sourceRows.value.filter((i) => i.importEnabled).map(toOption));
const exportSchemaOptions = computed(() => exportSchemas.value.map((i) => ({ label: i.name, value: i.id })));
const importSchemaOptions = computed(() => importSchemas.value.map((i) => ({ label: i.name, value: i.id })));
const targetTableOptions = computed(() => targetTables.value.map((i) => ({ label: i.name, value: i.id })));
const canPreviewExport = computed(
  () =>
    Boolean(exportForm.dataSource && exportForm.schemaId) &&
    (exportForm.selectionMode === 'ALL_TABLES_IN_SCHEMA' || selectedExportTableIds.value.length > 0),
);
const importStep = computed(() => {
  if (activeImportTask.value?.taskType === 'DATA_EXCHANGE_IMPORT') return 4;
  if (inspection.value) return 3;
  if (activeImportTask.value) return 2;
  return 1;
});
const importStepContent = [
  { title: '选择目标并上传', description: '先锁定唯一目标 Schema，再上传需要导入的 HDE 数据交换包。' },
  { title: '检查交换包', description: '系统正在校验文件完整性、源 Schema 结构和目标表兼容性。' },
  { title: '配置导入', description: '选择需要导入的源表，并确认目标表、列映射以及数据冲突处理策略。' },
  { title: '认证并导入', description: '通过二次认证后在后台执行导入，并持续反馈表级处理进度。' },
];
const importStepTitle = computed(() => importStepContent[importStep.value - 1]?.title || '数据导入');
const importStepDescription = computed(() => importStepContent[importStep.value - 1]?.description || '');
const selectedImportTableIdSet = computed(() => new Set(selectedImportTableIds.value));
const selectedImportTables = computed(
  () => inspection.value?.tables.filter((table) => selectedImportTableIdSet.value.has(table.sourceTableId)) || [],
);
const allImportTablesSelected = computed(
  () => Boolean(inspection.value?.tables.length && selectedImportTables.value.length === inspection.value.tables.length),
);
const someImportTablesSelected = computed(
  () => selectedImportTables.value.length > 0 && !allImportTablesSelected.value,
);
const canExecuteImport = computed(() =>
  Boolean(
    selectedImportTables.value.length &&
      selectedImportTables.value.every((table) => importMappings[table.sourceTableId]?.targetTableCatalogId),
  ),
);
const editingTargetColumnOptions = computed(() => {
  const tableId = editingInspection.value
    ? importMappings[editingInspection.value.sourceTableId]?.targetTableCatalogId
    : '';
  return (
    targetTables.value
      .find((i) => i.id === tableId)
      ?.columns.filter((i) => !i.generated)
      .map((i) => ({ label: `${i.name} (${i.typeName})`, value: i.name })) || []
  );
});

const catalogColumns = computed<DataTableColumns<PhysicalTable>>(() => [
  {
    type: 'selection',
    multiple: exportForm.selectionMode !== 'SINGLE',
    disabled: () => exportForm.selectionMode === 'ALL_TABLES_IN_SCHEMA',
  },
  { title: '表名', key: 'name', minWidth: 220 },
  { title: '类型', key: 'type', width: 130, dictType: 'sys_data_exchange_object_type' },
  { title: '列数', key: 'columns', width: 90, render: (row: PhysicalTable) => row.columns?.length ?? 0 },
  {
    title: '主键',
    key: 'primaryKey',
    minWidth: 180,
    render: (row: PhysicalTable) => row.primaryKey?.join(', ') || '无',
  },
  { title: '备注', key: 'remarks', minWidth: 220, ellipsis: { tooltip: true } },
]);

const jobColumns = [
  { title: '方向', key: 'direction', width: 90, dictType: 'sys_data_exchange_direction' },
  {
    title: '源',
    key: 'source',
    minWidth: 170,
    render: (row: any) => [row.sourceDataSource, row.sourceSchema].filter(Boolean).join(' / ') || '-',
  },
  {
    title: '目标',
    key: 'target',
    minWidth: 170,
    render: (row: any) => [row.targetDataSource, row.targetSchema].filter(Boolean).join(' / ') || '-',
  },
  { title: '表数', key: 'tableCount', width: 80 },
  {
    title: '状态',
    key: 'status',
    width: 140,
    render: (row: any) =>
      row.direction === 'IMPORT' && row.status === 'READY'
        ? '检查完成，待执行'
        : h(HDictText, { type: 'sys_data_exchange_job_status', value: row.status }),
  },
  { title: '风险', key: 'riskLevel', width: 90, dictType: 'sys_data_exchange_risk_level' },
  { title: '创建时间', key: 'createTime', width: 180, columnType: 'dateTime' },
  {
    title: '操作',
    key: 'actions',
    width: 230,
    fixed: 'right' as const,
    render: (row: any) =>
      h(
        NSpace,
        {},
        {
          default: () => [
            h(NButton, { text: true, type: 'primary', onClick: () => showJobDetail(row) }, { default: () => '详情' }),
            row.direction === 'EXPORT' && row.status === 'SUCCESS'
              ? h(
                  NButton,
                  { text: true, type: 'primary', onClick: () => handleDownload(row) },
                  { default: () => '下载' },
                )
              : null,
            row.direction === 'IMPORT' && row.status === 'READY'
              ? h(
                  NButton,
                  {
                    text: true,
                    type: 'primary',
                    loading: resumingImportJobId.value === row.id,
                    onClick: () => resumeImport(row),
                  },
                  { default: () => '配置并执行' },
                )
              : null,
            row.currentTaskId && row.status === 'RUNNING'
              ? h(
                  NButton,
                  { text: true, type: 'warning', onClick: () => cancelAsyncTask(row.currentTaskId).then(refreshJobs) },
                  { default: () => '取消' },
                )
              : null,
          ],
        },
      ),
  },
];
const jobTableColumns = [
  { title: '源表', key: 'sourceTable' },
  { title: '目标表', key: 'targetTable' },
  { title: '状态', key: 'status', width: 100, dictType: 'sys_data_exchange_table_status' },
  { title: '处理', key: 'processedCount', width: 100 },
  { title: '插入', key: 'insertedCount', width: 100 },
  { title: '更新', key: 'updatedCount', width: 100 },
  { title: '跳过', key: 'skippedCount', width: 100 },
  { title: '失败原因', key: 'errorSummary', minWidth: 220, ellipsis: { tooltip: true } },
];

function toOption(item: DataSourceOption) {
  return { label: item.label, value: item.key };
}
function formatNumber(value?: number) {
  return Number(value || 0).toLocaleString();
}
function riskTagType(risk: string) {
  return risk === 'CRITICAL' ? 'error' : risk === 'HIGH' ? 'warning' : 'info';
}
function compatibilityTagType(value: string) {
  return value === 'DIRECT' ? 'success' : value === 'CONVERTIBLE' ? 'warning' : 'error';
}
function handleImportTableSelection(sourceTableId: string, checked: boolean) {
  if (checked && !selectedImportTableIdSet.value.has(sourceTableId)) {
    selectedImportTableIds.value = [...selectedImportTableIds.value, sourceTableId];
  } else if (!checked) {
    selectedImportTableIds.value = selectedImportTableIds.value.filter((id) => id !== sourceTableId);
  }
}
function handleAllImportTableSelection(checked: boolean) {
  if (checked) selectAllImportTables();
  else clearImportTableSelection();
}
function selectCompatibleImportTables() {
  selectedImportTableIds.value =
    inspection.value?.tables
      .filter((table) => table.compatibility === 'DIRECT' || table.compatibility === 'CONVERTIBLE')
      .map((table) => table.sourceTableId) || [];
}
function selectAllImportTables() {
  selectedImportTableIds.value = inspection.value?.tables.map((table) => table.sourceTableId) || [];
}
function clearImportTableSelection() {
  selectedImportTableIds.value = [];
}
function clearExportPreview() {
  exportPreview.value = null;
  exportPreviewVisible.value = false;
}

function clearExportSelection() {
  selectedExportTableIds.value = [];
  exportTableRef.value?.clearCheckedRows();
  clearExportPreview();
}

async function handleExportDataSourceChange(value: string) {
  exportForm.dataSource = value || '';
  exportForm.schemaId = '';
  exportSchemas.value = [];
  clearExportSelection();
  await nextTick();
  exportTableRef.value?.handleQuery();
  if (value) exportSchemas.value = ((await schemas(value, 'EXPORT')) as any).data || [];
}
async function handleExportSchemaChange(value: string) {
  exportForm.schemaId = value || '';
  clearExportSelection();
  if (value) {
    await nextTick();
    exportTableRef.value?.handleQuery();
  }
}

function handleExportSelectionModeChange() {
  clearExportSelection();
}

async function handleExportCatalogChanged() {
  clearExportSelection();
  if (exportForm.dataSource && exportForm.schemaId) {
    await nextTick();
    exportTableRef.value?.handleQuery();
  }
}

async function fetchExportTablePage(params: Record<string, any>) {
  if (!params.dataSource || !params.schemaId) {
    return { data: { list: [], total: 0, pages: 1, pageNum: 1, pageSize: params.pageSize || 30 } };
  }
  clearExportPreview();
  return tables({
    dataSource: params.dataSource,
    schemaId: params.schemaId,
    includeViews: params.includeViews,
    keyword: params.keyword,
    direction: 'EXPORT',
    pageNum: params.pageNum,
    pageSize: params.pageSize,
  });
}

function handleExportSelection(keys: Array<string | number>) {
  selectedExportTableIds.value = exportForm.selectionMode === 'SINGLE' ? keys.slice(-1) : keys;
  clearExportPreview();
}
async function handlePreviewExport() {
  exportPreviewLoading.value = true;
  try {
    const resp: any = await previewExport({
      sourceDataSource: exportForm.dataSource,
      sourceSchemaId: exportForm.schemaId,
      selectionMode: exportForm.selectionMode,
      tables: selectedExportTableIds.value.map((tableCatalogId) => ({ tableCatalogId })),
      includeViews: exportForm.includeViews,
      consistencyMode: exportForm.consistencyMode,
    });
    exportPreview.value = resp.data;
    exportPreviewVisible.value = Boolean(exportPreview.value);
  } finally {
    exportPreviewLoading.value = false;
  }
}
async function handleCreateExport() {
  const preview = exportPreview.value;
  if (!preview) return;
  const run = async (grant?: string) => {
    exportCreating.value = true;
    try {
      const resp: any = await createExport(
        { previewId: preview.previewId, requestDigest: preview.requestDigest },
        grant,
      );
      const task = resp.data?.task;
      activeExportJobId.value = resp.data?.jobId || '';
      if (task) watchTask(task, 'export');
      exportPreview.value = null;
      exportPreviewVisible.value = false;
      activeTab.value = 'export';
      refreshJobs();
    } finally {
      exportCreating.value = false;
    }
  };
  if (preview.stepUpRequired) openAuth('EXPORT', preview.requestDigest, (grant) => run(grant));
  else await run();
}

async function handleImportDataSourceChange(value: string) {
  importForm.dataSource = value || '';
  importForm.schemaId = '';
  importSchemas.value = [];
  uploadedFileId.value = '';
  importUploadContentId.value = createImportUploadContentId();
  if (value) importSchemas.value = ((await schemas(value, 'IMPORT')) as any).data || [];
}
function handleUploadFinish(file: any) {
  uploadedFileId.value = file?.id || file?.response?.id || '';
}
async function handleInspect() {
  if (!importForm.dataSource || !importForm.schemaId || !uploadedFileId.value) return;
  inspectCreating.value = true;
  try {
    const resp: any = await createImport({
      targetDataSource: importForm.dataSource,
      targetSchemaId: importForm.schemaId,
      fileId: uploadedFileId.value,
      uploadContentId: importUploadContentId.value,
    });
    importJobId.value = resp.data?.jobId || '';
    const task = resp.data?.task;
    if (task) {
      inspectionTaskId.value = task.id;
      watchTask(task, 'import');
    }
  } finally {
    inspectCreating.value = false;
  }
}
async function handleReinspect() {
  if (!importJobId.value || !uploadedFileId.value) return;
  inspectCreating.value = true;
  try {
    const resp: any = await inspectImport(importJobId.value, uploadedFileId.value);
    const task = resp.data?.task;
    if (task) {
      inspectionTaskId.value = task.id;
      inspection.value = null;
      importRetryError.value = '';
      watchTask(task, 'import');
    }
  } finally {
    inspectCreating.value = false;
  }
}
async function loadInspection() {
  if (!importJobId.value) return;
  const [resp] = (await Promise.all([getJob(importJobId.value), loadTargetTables()])) as [any, void];
  inspectionTaskId.value = resp.data?.inspectTaskId || inspectionTaskId.value;
  inspection.value = resp.data?.inspectResultJson ? JSON.parse(resp.data.inspectResultJson) : null;
  if (inspection.value) importRetryError.value = '';
  Object.keys(importMappings).forEach((key) => delete importMappings[key]);
  inspection.value?.tables.forEach((table) => {
    importMappings[table.sourceTableId] = {
      targetTableCatalogId: table.targetTableCatalogId || '',
      columns: table.columns.map((column) => ({ source: column.source, target: column.target || null })),
    };
  });
  selectCompatibleImportTables();
}
async function loadTargetTables() {
  if (!importForm.dataSource || !importForm.schemaId) return;
  const resp: any = await tables({
    dataSource: importForm.dataSource,
    schemaId: importForm.schemaId,
    direction: 'IMPORT',
  });
  targetTables.value = resp.data?.list || [];
}
function handleTargetTableChange(table: TableInspection) {
  const state = importMappings[table.sourceTableId];
  const target = targetTables.value.find((i) => i.id === state.targetTableCatalogId);
  state.columns = table.columns.map((source) => ({
    source: source.source,
    target:
      target?.columns.find((column) => column.name === source.source)?.name ||
      target?.columns.filter((column) => column.name.toLowerCase() === source.source.toLowerCase()).at(0)?.name ||
      null,
  }));
}
function openColumnMapping(table: TableInspection) {
  editingInspection.value = table;
  columnMappingVisible.value = true;
}
function inspectionColumnStatus(table: TableInspection, source: string) {
  return table.columns.find((i) => i.source === source)?.compatibility || '-';
}
function buildImportRequest() {
  return {
    inspectTaskId: inspectionTaskId.value,
    strategy: importStrategy.value,
    mappings: selectedImportTables.value.map((table) => ({
      sourceTableId: table.sourceTableId,
      ...importMappings[table.sourceTableId],
    })),
  };
}
async function handleExecuteImport() {
  const payload = buildImportRequest();
  if (!payload.inspectTaskId) {
    message.error('检查任务信息已失效，请重新检查');
    return;
  }
  importCreating.value = true;
  try {
    const previewResp: any = await previewImport(importJobId.value, payload);
    openAuth('IMPORT', previewResp.data.requestDigest, async (grant) => {
      const resp: any = await executeImport(importJobId.value, payload, grant);
      const task = resp.data?.task;
      if (task) {
        importRetryError.value = '';
        watchTask(task, 'import');
      }
      refreshJobs();
    });
  } finally {
    importCreating.value = false;
  }
}
async function reviseImportParameters() {
  const failedTask = activeImportTask.value;
  if (!inspection.value) await loadInspection();
  if (!inspection.value) {
    message.error('原检查结果已失效，请使用当前交换包重新检查');
    return;
  }
  importRetryError.value = failedTask?.errorMessage || failedTask?.message || '后台导入任务执行失败';
  activeImportTask.value = null;
}
async function resumeImport(row: any) {
  resumingImportJobId.value = row.id;
  try {
    const resp: any = await getJob(row.id);
    const job = resp.data;
    if (job?.direction !== 'IMPORT' || job?.status !== 'READY' || !job?.inspectResultJson) {
      message.error('该交换记录当前不能继续执行，请刷新后重试');
      return;
    }
    const dataSource = job.targetDataSource || '';
    if (!dataSource || !job.targetSchemaId) {
      message.error('交换记录的目标 Schema 信息不完整，请重新检查交换包');
      return;
    }

    resetImport();
    importJobId.value = job.id;
    uploadedFileId.value = job.sourceFileId || '';
    inspectionTaskId.value = job.inspectTaskId || '';
    importForm.dataSource = dataSource;
    importSchemas.value = ((await schemas(dataSource, 'IMPORT')) as any).data || [];
    const targetSchema = importSchemas.value.find((schema) => schema.id === job.targetSchemaId);
    if (!targetSchema) {
      message.error('原目标 Schema 已不存在或无权导入，请重新发起导入');
      return;
    }
    importForm.schemaId = targetSchema.id;
    importStrategy.value = job.importStrategy || 'FAIL_IF_EXISTS';
    await loadInspection();
    if (!inspection.value) {
      message.error('交换包检查结果已失效，请重新检查交换包');
      return;
    }
    activeTab.value = 'import';
    message.success('已恢复检查结果，请确认表映射后执行导入');
  } finally {
    resumingImportJobId.value = '';
  }
}
function resetImport() {
  importJobId.value = '';
  uploadedFileId.value = '';
  importUploadContentId.value = createImportUploadContentId();
  inspectionTaskId.value = '';
  inspection.value = null;
  importRetryError.value = '';
  activeImportTask.value = null;
  targetTables.value = [];
  selectedImportTableIds.value = [];
  Object.keys(importMappings).forEach((key) => delete importMappings[key]);
}

function createImportUploadContentId() {
  const randomId = globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `data-exchange-${randomId}`;
}

function watchTask(task: AsyncTask, scope: 'export' | 'import') {
  taskSubscriptions.get(task.id)?.close();
  if (scope === 'export') {
    activeExportTask.value = task;
    exportTaskVisible.value = true;
  } else activeImportTask.value = task;
  if (isTerminalAsyncTask(task.status)) return;
  const subscription = subscribeAsyncTaskEvents(task.id, {
    onTask: (updated) => {
      if (scope === 'export') activeExportTask.value = updated;
      else activeImportTask.value = updated;
    },
    onTerminal: async (terminalTask) => {
      taskSubscriptions.delete(task.id);
      refreshJobs();
      if (scope === 'import' && terminalTask?.status === 'FAILED') {
        const title = terminalTask.taskType === 'DATA_EXCHANGE_INSPECT' ? '交换包检查失败' : '数据导入失败';
        message.error(`${title}：${terminalTask.errorMessage || terminalTask.message || '请查看任务详情'}`);
      }
      if (scope === 'import' && task.taskType === 'DATA_EXCHANGE_INSPECT') await loadInspection();
    },
    extraEvents: {
      'exchange-table-progress': () => undefined,
      'exchange-table-finished': () => undefined,
      'exchange-package-verified': () => undefined,
    },
  });
  taskSubscriptions.set(task.id, subscription);
}

function openAuth(action: string, digest: string, callback: (grant: string) => Promise<void>) {
  authForm.password = '';
  authForm.captchaCode = '';
  authForm.captchaUuid = '';
  authForm.uuid = '';
  pendingAuth = { action, digest, callback };
  authVisible.value = true;
  captchaRef.value?.handleRefresh();
}
async function submitAuth() {
  if (!pendingAuth || !authForm.password || !authForm.captchaCode || !authForm.uuid) {
    message.warning('请输入密码和图形验证码');
    return;
  }
  authLoading.value = true;
  try {
    const encrypted = rsaEncrypt(authForm.password);
    if (!encrypted) throw new Error('密码加密失败');
    const resp: any = await verifySensitiveOperation({
      action: pendingAuth.action,
      resourceDigest: pendingAuth.digest,
      password: encrypted,
      captchaUuid: authForm.uuid,
      captchaCode: authForm.captchaCode,
    });
    const callback = pendingAuth.callback;
    pendingAuth = null;
    authVisible.value = false;
    await callback(resp.data.token);
  } catch (error) {
    captchaRef.value?.handleRefresh();
    authForm.captchaCode = '';
    throw error;
  } finally {
    authLoading.value = false;
  }
}

function refreshJobs() {
  jobTableRef.value?.handleRefresh();
}
function resetExportResult() {
  activeExportTask.value = null;
  activeExportJobId.value = '';
  exportTaskVisible.value = false;
}
function showJobsFromExportTask() {
  exportTaskVisible.value = false;
  activeTab.value = 'jobs';
}
function downloadCurrentExport() {
  if (activeExportJobId.value) handleDownload({ id: activeExportJobId.value });
}
async function showJobDetail(row: any) {
  const resp: any = await jobTables(row.id);
  jobTableRows.value = resp.data || [];
  jobDetailVisible.value = true;
}
function showCurrentImportJobDetail() {
  if (importJobId.value) showJobDetail({ id: importJobId.value });
}
async function handleDownload(row: any) {
  const digestResp: any = await downloadDigest(row.id);
  openAuth('DOWNLOAD', digestResp.data, async (grant) => {
    downloadLoading.value = true;
    try {
      const blob: any = await downloadExport(row.id, grant);
      saveAs(blob instanceof Blob ? blob : new Blob([blob]), `data-exchange-${row.id}.hde`);
    } finally {
      downloadLoading.value = false;
    }
  });
}

onMounted(async () => {
  sourceRows.value = ((await dataSources()) as any).data || [];
  const exportSources = sourceRows.value.filter((item) => item.exportEnabled);
  if (exportSources.length === 1) {
    await handleExportDataSourceChange(exportSources[0].key);
  }
});
onBeforeUnmount(() => {
  taskSubscriptions.forEach((item) => item.close());
  taskSubscriptions.clear();
});
</script>

<style scoped>
.exchange-page {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 16px;
}
.exchange-tabs {
  display: flex;
  flex: 1;
  min-height: 0;
}
.import-step-heading {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}
.import-step-heading__title {
  font-size: 18px;
  font-weight: 600;
}
.import-step-content {
  min-height: 0;
}
.import-selection-column {
  width: 44px;
}
.upload-copy {
  padding: 28px;
  text-align: center;
}
.task-card {
  padding: 16px;
  border: 1px solid var(--n-border-color);
  border-radius: 8px;
  background: var(--n-color);
}
.task-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.task-card__message {
  margin-top: 8px;
  color: var(--n-text-color-3);
}
.mt-3 {
  margin-top: 12px;
}
.mt-4 {
  margin-top: 16px;
}
.mb-3 {
  margin-bottom: 12px;
}
.mb-4 {
  margin-bottom: 16px;
}
</style>
