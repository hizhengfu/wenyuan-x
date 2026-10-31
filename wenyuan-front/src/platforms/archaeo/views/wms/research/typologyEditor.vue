<template>
  <n-card
    class="typology-editor wh-full flex flex-col"
    bordered
    content-style="flex:1;display:flex;flex-direction:column;overflow:hidden;"
  >
    <template #header>
      <div class="editor-header-main">
        <n-button secondary @click="$router.back()">
          <template #icon><n-icon><IconMdiArrowLeft /></n-icon></template>
          返回列表
        </n-button>
        <n-input v-model:value="form.title" class="title-input" placeholder="表标题" />
        <n-tag :bordered="false" type="info">{{ form.source === 'AUTO' ? '智能' : '自定义' }}</n-tag>
        <n-tag v-if="form.projectName || form.material" :bordered="false" class="meta-tag">
          {{ form.projectName || '' }} · {{ form.material || '' }}
        </n-tag>
      </div>
    </template>
    <template #header-extra>
      <div class="editor-actions">
        <n-select
          v-model:value="body.layout"
          size="small"
          class="layout-select"
          :options="layoutOptions"
          @update:value="dirty = true"
        />
        <n-button secondary @click="handleAddRow">
          <template #icon><n-icon><IconMdiPlus /></n-icon></template>
          期/段行
        </n-button>
        <n-button secondary @click="handleAddCol">
          <template #icon><n-icon><IconMdiPlus /></n-icon></template>
          器型列
        </n-button>
        <n-button secondary @click="previewShow = true">
          <template #icon><n-icon><IconMdiPrinter /></n-icon></template>
          预览/导出
        </n-button>
        <n-button type="primary" :loading="saving" @click="handleSave">保存</n-button>
      </div>
    </template>

    <div class="canvas-wrap" ref="refCanvas">
      <div class="editor-sheet">
        <table class="typology-table" :style="tableStyle">
          <colgroup>
            <col class="phase-col" />
            <col class="section-col" />
            <col v-for="(_col, ci) in body.cols" :key="'col' + ci" class="artifact-col" />
          </colgroup>
          <thead>
            <tr>
              <th class="period-head period-group-head" colspan="2">期别/段别</th>
              <th
                v-for="(col, ci) in body.cols"
                :key="'type' + ci"
                class="artifact-head"
              >
                <div class="artifact-head-inner">
                  <n-select
                    v-model:value="col.artifactType"
                    placeholder="文物器型"
                    size="small"
                    filterable
                    tag
                    :options="artifactTypeOptions"
                    :bordered="false"
                    @update:value="dirty = true"
                  />
                  <n-button
                    quaternary
                    circle
                    size="tiny"
                    type="error"
                    title="删除器型列"
                    @click="removeCol(ci)"
                  >
                    <template #icon><n-icon><IconMdiClose /></n-icon></template>
                  </n-button>
                </div>
              </th>
            </tr>
            <tr>
              <th class="period-head">期</th>
              <th class="period-head">段</th>
              <th v-for="(_col, ci) in body.cols" :key="'style' + ci" class="artifact-sub-head">型式 / 标型器</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, ri) in body.rows" :key="ri">
              <th v-if="phaseCells[ri]?.show" class="period-cell" :rowspan="phaseCells[ri].rowspan">
                <n-input
                  :value="row.phase"
                  class="table-input"
                  placeholder="期"
                  size="small"
                  :bordered="false"
                  @update:value="(v: string) => updatePhase(ri, v)"
                />
              </th>
              <th class="section-cell">
                <div class="section-editor">
                  <n-input
                    v-model:value="row.section"
                    class="table-input"
                    placeholder="段"
                    size="small"
                    :bordered="false"
                    @blur="dirty = true"
                  />
                  <n-button
                    quaternary
                    circle
                    size="tiny"
                    type="error"
                    title="删除期/段行"
                    @click="removeRow(ri)"
                  >
                    <template #icon><n-icon><IconMdiClose /></n-icon></template>
                  </n-button>
                </div>
              </th>
              <template v-for="(_col, li) in body.cols" :key="li">
                <td
                  v-if="!coverage[ri]?.[li]"
                  :rowspan="row.cells[li]?.rowspan || 1"
                  :colspan="row.cells[li]?.colspan || 1"
                >
                  <div v-if="row.cells[li].items?.length" class="style-list">
                    <div v-for="(item, ii) in row.cells[li].items" :key="ii" class="style-item">
                      <div class="style-line">
                        <n-input
                          v-model:value="item.artifactStyle"
                          size="tiny"
                          placeholder="型"
                          :bordered="false"
                          @blur="syncCellText(row.cells[li])"
                        />
                        <n-input
                          v-model:value="item.artifactSubStyle"
                          size="tiny"
                          placeholder="亚型"
                          :bordered="false"
                          @blur="syncCellText(row.cells[li])"
                        />
                        <n-input
                          v-model:value="item.artifactStage"
                          size="tiny"
                          placeholder="式"
                          :bordered="false"
                          @blur="syncCellText(row.cells[li])"
                        />
                        <n-button quaternary circle size="tiny" type="error" title="删除该式" @click="removeCellItem(row.cells[li], ii)">
                          <template #icon><n-icon><IconMdiClose /></n-icon></template>
                        </n-button>
                      </div>
                      <div v-if="item.imageUrl" class="cell-img">
                        <img :src="resolveImg(item.imageUrl)" :style="cellImgStyle(item)" />
                        <div v-if="item.figureName" class="fig-name">{{ item.figureName }}</div>
                      </div>
                      <n-space class="item-actions" size="small" :wrap="true">
                        <n-button tertiary size="tiny" type="primary" @click="openPicker(row, li, item)">标型器</n-button>
                        <h-upload
                          :show-file-list="false"
                          :multiple="false"
                          module="wms"
                          listType="text"
                          accept="image/*"
                          @uploadFinish="(file:any) => onCellImage(file, item)"
                        >
                          <n-button tertiary size="tiny">{{ item.imageUrl ? '换图' : '插图' }}</n-button>
                        </h-upload>
                        <template v-if="item.imageUrl">
                          <n-button tertiary size="tiny" @click="resizeImg(item, 20)">放大</n-button>
                          <n-button tertiary size="tiny" @click="resizeImg(item, -20)">缩小</n-button>
                        </template>
                      </n-space>
                    </div>
                  </div>
                  <template v-else>
                    <n-input
                      v-model:value="row.cells[li].text"
                      class="cell-textarea"
                      placeholder="点击编辑"
                      type="textarea"
                      :autosize="{ minRows: 2 }"
                      size="small"
                      :bordered="false"
                      @blur="dirty = true"
                    />
                    <div v-if="row.cells[li].imageUrl" class="cell-img">
                      <img :src="resolveImg(row.cells[li].imageUrl)" :style="cellImgStyle(row.cells[li])" />
                      <div v-if="row.cells[li].figureName" class="fig-name">{{ row.cells[li].figureName }}</div>
                    </div>
                  </template>
                  <n-space class="cell-actions" size="small" :wrap="true">
                    <n-button tertiary size="tiny" type="primary" @click="addCellItem(row.cells[li])">+ 式</n-button>
                    <template v-if="!row.cells[li].items?.length">
                      <n-button tertiary size="tiny" type="primary" @click="openPicker(row, li)">标型器</n-button>
                      <h-upload
                        :show-file-list="false"
                        :multiple="false"
                        module="wms"
                        listType="text"
                        accept="image/*"
                        @uploadFinish="(file:any) => onCellImage(file, row.cells[li])"
                      >
                        <n-button tertiary size="tiny">{{ row.cells[li].imageUrl ? '换图' : '插图' }}</n-button>
                      </h-upload>
                      <template v-if="row.cells[li].imageUrl">
                        <n-button tertiary size="tiny" @click="resizeImg(row.cells[li], 20)">放大</n-button>
                        <n-button tertiary size="tiny" @click="resizeImg(row.cells[li], -20)">缩小</n-button>
                      </template>
                    </template>
                    <n-button tertiary size="tiny" title="向右合并" @click="mergeRight(ri, li)">右合</n-button>
                    <n-button tertiary size="tiny" title="向下合并" @click="mergeDown(ri, li)">下合</n-button>
                    <n-button
                      v-if="(row.cells[li].rowspan || 1) > 1 || (row.cells[li].colspan || 1) > 1"
                      tertiary
                      size="tiny"
                      type="warning"
                      title="拆分"
                      @click="splitCell(ri, li)"
                      >拆分</n-button
                    >
                  </n-space>
                </td>
              </template>
            </tr>
          </tbody>
        </table>
        <div class="tip">提示：调整行/列/段会重置已有的单元格合并；合并建议在表格结构确定后进行。</div>
      </div>
    </div>

    <!-- 标型器选择 -->
    <n-modal v-model:show="pickerShow" preset="card" style="width: 70%; max-width: 900px" :title="pickerTitle">
      <n-spin :show="pickerLoading">
        <n-empty v-if="!pickerList.length" description="该器型暂无带线图的文物" />
        <n-grid v-else :cols="4" x-gap="12" y-gap="12">
          <n-grid-item v-for="art in pickerList" :key="art.artifactId">
            <n-card
              size="small"
              hoverable
              :title="art.figureName || art.code || art.name"
              @click="chooseTypical(art)"
              style="cursor: pointer"
            >
              <div class="picker-img">
                <img v-if="art.lineImages?.length" :src="resolveImg(art.lineImages[0])" />
                <n-empty v-else size="small" description="无线图" />
              </div>
              <div class="picker-meta">
                <span>{{ art.name }}</span>
                <span class="muted">{{ art.artifactStyle }} {{ art.artifactStage }}</span>
              </div>
            </n-card>
          </n-grid-item>
        </n-grid>
      </n-spin>
    </n-modal>

    <!-- 预览 / 打印 / 导出 -->
    <n-modal
      v-model:show="previewShow"
      preset="card"
      style="width: 95%; max-width: 1240px"
      title="预览 / 打印 / 导出"
    >
      <template #header-extra>
        <n-space>
          <n-button size="small" @click="doPrint">打印</n-button>
          <n-button size="small" :loading="exporting" @click="exportPng">导出 PNG</n-button>
          <n-button size="small" :loading="exporting" @click="exportPdf">导出 PDF</n-button>
          <n-button size="small" @click="exportExcel">导出 Excel</n-button>
        </n-space>
      </template>
      <div class="preview-scroll">
        <div ref="refPreview" class="a4-page" :class="body.layout === 'A4_PORTRAIT' ? 'portrait' : 'landscape'">
          <h3 class="ty-title">{{ form.title || '型式分期表' }}</h3>
          <table id="ty-preview-print" class="ty-print">
            <thead>
              <tr>
                <th colspan="2">期别/段别</th>
                <th v-for="(col, ci) in body.cols" :key="'pp' + ci">{{ col.artifactType }}</th>
              </tr>
              <tr>
                <th>期</th>
                <th>段</th>
                <th v-for="(_col, ci) in body.cols" :key="'ps' + ci">型式 / 标型器</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, ri) in body.rows" :key="'pr' + ri">
                <th v-if="phaseCells[ri]?.show" :rowspan="phaseCells[ri].rowspan">{{ row.phase }}</th>
                <th>{{ row.section }}</th>
                <template v-for="(_col, li) in body.cols" :key="li">
                  <td
                    v-if="!coverage[ri]?.[li]"
                    :rowspan="row.cells[li]?.rowspan || 1"
                    :colspan="row.cells[li]?.colspan || 1"
                  >
                    <template v-if="row.cells[li].items?.length">
                      <div v-for="(item, ii) in row.cells[li].items" :key="ii" class="pv-style-item">
                        <div v-if="itemText(item)" class="pv-text">{{ itemText(item) }}</div>
                        <img
                          v-if="item.imageUrl"
                          :src="resolveImg(item.imageUrl)"
                          :style="cellImgStyle(item)"
                          crossorigin="anonymous"
                        />
                        <div v-if="item.figureName" class="pv-fig">{{ item.figureName }}</div>
                      </div>
                    </template>
                    <template v-else>
                      <div v-if="row.cells[li].text" class="pv-text">{{ row.cells[li].text }}</div>
                      <img
                        v-if="row.cells[li].imageUrl"
                        :src="resolveImg(row.cells[li].imageUrl)"
                        :style="cellImgStyle(row.cells[li])"
                        crossorigin="anonymous"
                      />
                      <div v-if="row.cells[li].figureName" class="pv-fig">{{ row.cells[li].figureName }}</div>
                    </template>
                  </td>
                </template>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </n-modal>
  </n-card>
</template>

<script lang="ts" setup>
import { ref, onMounted, reactive, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useMessage } from 'naive-ui';
import { get, update, typicalArtifacts, summary } from '../../../api/typology';
import { fileUrl } from '@/utils/path';
import html2canvas from 'html2canvas';
// @ts-ignore html2pdf.js 无类型声明
import html2pdf from 'html2pdf.js';
// @ts-ignore xlsx-js-style 无类型声明，API 同 SheetJS
import * as XLSX from 'xlsx-js-style';
// @ts-ignore print-js 无类型声明
import printJS from 'print-js';

/** 解析图片地址：base64 直接用，文件路径走 fileUrl */
function resolveImg(src?: string): string {
  if (!src) return '';
  if (src.startsWith('data:') || /^https?:/.test(src)) return src;
  return fileUrl(src);
}

const route = useRoute();
const router = useRouter();
const message = useMessage();

const id = (route.query.id as string) || '';
if (!id) {
  message.warning('缺少分期表 ID');
  router.replace('/wms/research/typology');
}

const form = ref<any>({});
const saving = ref(false);
const dirty = ref(false);
const refCanvas = ref<HTMLElement>();
const refPreview = ref<HTMLElement>();
const previewShow = ref(false);
const exporting = ref(false);

const layoutOptions = [
  { label: 'A4 横向', value: 'A4_LANDSCAPE' },
  { label: 'A4 纵向', value: 'A4_PORTRAIT' },
];

interface CellItem {
  text: string;
  artifactStyle?: string;
  artifactSubStyle?: string;
  artifactStage?: string;
  imageUrl?: string;
  artifactCode?: string;
  artifactId?: string;
  figureName?: string;
  imgWidth?: number;
}

interface Cell {
  text: string;
  items?: CellItem[];
  imageUrl?: string;
  artifactCode?: string;
  artifactId?: string;
  figureName?: string;
  rowspan?: number;
  colspan?: number;
  imgWidth?: number;
}
interface Row {
  phase: string;
  section: string;
  cells: Cell[];
}
interface Col {
  artifactType: string;
}
const body = reactive<{ cols: Col[]; rows: Row[]; layout: string }>({
  cols: [],
  rows: [],
  layout: 'A4_LANDSCAPE',
});

const phaseColWidth = 150;
const sectionColWidth = 150;
const artifactColWidth = 340;

const tableStyle = computed(() => {
  const width = phaseColWidth + sectionColWidth + Math.max(body.cols.length, 1) * artifactColWidth;
  return {
    width: `${width}px`,
    minWidth: `${width}px`,
  };
});

const artifactTypeOptions = ref<Array<{ label: string; value: string }>>([]);

function mergeArtifactTypeOptions(remoteOptions: Array<{ label?: string; value?: string }> = []) {
  const map = new Map<string, { label: string; value: string }>();
  remoteOptions.forEach((item) => {
    const value = item.value?.trim();
    if (!value) return;
    map.set(value, { label: item.label || value, value });
  });
  body.cols.forEach((col) => {
    const value = col.artifactType?.trim();
    if (!value || map.has(value)) return;
    map.set(value, { label: value, value });
  });
  artifactTypeOptions.value = Array.from(map.values());
}

/** 左侧「期」列按连续相同值纵向合并，形成期 / 段列表格。 */
const phaseCells = computed(() => {
  return body.rows.map((row, index) => {
    const phase = row.phase || '';
    if (!phase) return { show: true, rowspan: 1 };
    if (index > 0 && (body.rows[index - 1]?.phase || '') === phase) {
      return { show: false, rowspan: 0 };
    }
    let rowspan = 1;
    for (let i = index + 1; i < body.rows.length; i++) {
      if ((body.rows[i]?.phase || '') !== phase) break;
      rowspan++;
    }
    return { show: true, rowspan };
  });
});

/** 合并覆盖矩阵：被某个跨行/跨列单元格盖住的位置不再单独渲染 */
const coverage = computed(() => {
  const R = body.rows.length;
  const C = body.cols.length;
  const hid: boolean[][] = Array.from({ length: R }, () => new Array(C).fill(false));
  for (let r = 0; r < R; r++) {
    const cells = body.rows[r]?.cells || [];
    for (let c = 0; c < C; c++) {
      const cell = cells[c];
      if (!cell) continue;
      const rs = cell.rowspan || 1;
      const cs = cell.colspan || 1;
      if (rs > 1 || cs > 1) {
        for (let dr = 0; dr < rs; dr++) {
          for (let dc = 0; dc < cs; dc++) {
            if (dr === 0 && dc === 0) continue;
            if (r + dr < R && c + dc < C) hid[r + dr][c + dc] = true;
          }
        }
      }
    }
  }
  return hid;
});

function cellImgStyle(cell: Cell | CellItem) {
  const w = cell.imgWidth || 120;
  return { maxWidth: w + 'px', maxHeight: w + 'px' };
}
function resizeImg(cell: Cell | CellItem, delta: number) {
  cell.imgWidth = Math.min(400, Math.max(60, (cell.imgWidth || 120) + delta));
  dirty.value = true;
}

function itemText(item: CellItem) {
  return [item.artifactStyle, item.artifactSubStyle, item.artifactStage].filter(Boolean).join(' ') || item.text || '';
}

function syncCellText(cell: Cell) {
  if (cell.items?.length) {
    cell.text = cell.items.map(itemText).filter(Boolean).join('\n');
  }
  dirty.value = true;
}

function addCellItem(cell: Cell) {
  if (!cell.items?.length) {
    cell.items = normalizeCellItems(cell);
    cell.imageUrl = undefined;
    cell.figureName = undefined;
    cell.artifactId = undefined;
    cell.artifactCode = undefined;
  }
  cell.items.push({ text: '', imgWidth: 120 });
  syncCellText(cell);
}

function removeCellItem(cell: Cell, index: number) {
  cell.items?.splice(index, 1);
  if (!cell.items?.length) {
    cell.items = undefined;
  }
  syncCellText(cell);
}

function ensureCellsAligned() {
  const n = body.cols.length;
  body.rows.forEach((r) => {
    while (r.cells.length < n) r.cells.push({ text: '', rowspan: 1, colspan: 1 });
    if (r.cells.length > n) r.cells.length = n;
    r.cells.forEach((c) => {
      if (!c.rowspan) c.rowspan = 1;
      if (!c.colspan) c.colspan = 1;
    });
  });
}
function resetAllSpans() {
  body.rows.forEach((r) => r.cells.forEach((c) => {
    c.rowspan = 1;
    c.colspan = 1;
  }));
}

function handleAddRow() {
  const last = body.rows[body.rows.length - 1];
  body.rows.push({
    phase: last?.phase || '',
    section: '',
    cells: body.cols.map(() => ({ text: '', rowspan: 1, colspan: 1 })),
  });
  dirty.value = true;
}
function handleAddCol() {
  body.cols.push({ artifactType: '' });
  body.rows.forEach((r) => r.cells.push({ text: '', rowspan: 1, colspan: 1 }));
  dirty.value = true;
}
function removeRow(i: number) {
  body.rows.splice(i, 1);
  resetAllSpans();
  dirty.value = true;
}
function removeCol(i: number) {
  body.cols.splice(i, 1);
  body.rows.forEach((r) => r.cells.splice(i, 1));
  resetAllSpans();
  ensureCellsAligned();
  dirty.value = true;
}

function updatePhase(ri: number, value: string) {
  const oldPhase = body.rows[ri]?.phase || '';
  const start = findPhaseGroupStart(ri, oldPhase);
  const end = findPhaseGroupEnd(ri, oldPhase);
  for (let i = start; i <= end; i++) {
    body.rows[i].phase = value;
  }
  dirty.value = true;
}

function findPhaseGroupStart(index: number, phase: string) {
  if (!phase) return index;
  let i = index;
  while (i > 0 && (body.rows[i - 1]?.phase || '') === phase) i--;
  return i;
}

function findPhaseGroupEnd(index: number, phase: string) {
  if (!phase) return index;
  let i = index;
  while (i + 1 < body.rows.length && (body.rows[i + 1]?.phase || '') === phase) i++;
  return i;
}

/** ===== 单元格合并 ===== */
function mergeRight(r: number, c: number) {
  const cell = body.rows[r].cells[c];
  const cs = cell.colspan || 1;
  const target = c + cs;
  if (target >= body.cols.length) {
    message.warning('右侧没有可合并的列');
    return;
  }
  if (coverage.value[r]?.[target]) {
    message.warning('目标列已被其它合并占用，请先拆分');
    return;
  }
  cell.colspan = cs + 1;
  dirty.value = true;
}
function mergeDown(r: number, c: number) {
  const cell = body.rows[r].cells[c];
  const rs = cell.rowspan || 1;
  const target = r + rs;
  if (target >= body.rows.length) {
    message.warning('下方没有可合并的行');
    return;
  }
  if (coverage.value[target]?.[c]) {
    message.warning('目标行已被其它合并占用，请先拆分');
    return;
  }
  cell.rowspan = rs + 1;
  dirty.value = true;
}
function splitCell(r: number, c: number) {
  const cell = body.rows[r].cells[c];
  cell.rowspan = 1;
  cell.colspan = 1;
  dirty.value = true;
}

/** h-upload 上传完成：只把文件服务返回的相对路径写进单元格 */
function onCellImage(file: any, cell: Cell | CellItem) {
  const path: string = file?.fullPath || file?.url;
  if (!path) {
    message.error('上传失败：未返回文件路径');
    return;
  }
  cell.imageUrl = path;
  dirty.value = true;
}

/** ===== 标型器选择 ===== */
const pickerShow = ref(false);
const pickerLoading = ref(false);
const pickerTitle = ref('');
const pickerList = ref<any[]>([]);
let pickerTarget: Cell | CellItem | null = null;
let pickerTargetIsItem = false;

async function openPicker(row: Row, ci: number, item?: CellItem) {
  const artifactType = body.cols[ci]?.artifactType;
  if (!artifactType) {
    message.warning('请先填写该列的器型');
    return;
  }
  pickerTarget = item || row.cells[ci];
  pickerTargetIsItem = !!item;
  pickerTitle.value = `选择标型器（${artifactType}）`;
  pickerShow.value = true;
  pickerLoading.value = true;
  pickerList.value = [];
  try {
    const resp: any = await typicalArtifacts({
      projectId: form.value.projectId,
      artifactType,
      material: form.value.material,
      stage: item?.artifactStage,
      artifactStyle: item?.artifactStyle,
      artifactSubStyle: item?.artifactSubStyle,
      phaseName: row.phase,
      sectionName: row.section,
    });
    pickerList.value = resp.data || [];
  } catch (e: any) {
    message.error(e?.message || '加载标型器失败');
  } finally {
    pickerLoading.value = false;
  }
}

async function loadArtifactTypeOptions() {
  if (!form.value.projectId) {
    mergeArtifactTypeOptions();
    return;
  }
  try {
    const resp: any = await summary({ projectId: form.value.projectId, material: form.value.material });
    mergeArtifactTypeOptions((resp.data?.artifactTypes || []).map((item: any) => ({
      label: item.label || item.value,
      value: item.value,
    })));
  } catch (e) {
    mergeArtifactTypeOptions();
  }
}

function chooseTypical(art: any) {
  if (!pickerTarget) return;
  pickerTarget.imageUrl = art.lineImages?.[0] || '';
  pickerTarget.figureName = art.figureName || art.code || '';
  pickerTarget.artifactId = art.artifactId;
  pickerTarget.artifactCode = art.code;
  if (pickerTargetIsItem) {
    const target = pickerTarget as CellItem;
    target.artifactStyle = target.artifactStyle || art.artifactStyle || '';
    target.artifactStage = target.artifactStage || art.artifactStage || '';
    target.text = itemText(target);
  } else if (art.figureName && !pickerTarget.text.includes(art.figureName)) {
    pickerTarget.text = [pickerTarget.text, art.figureName].filter(Boolean).join(' ');
  }
  dirty.value = true;
  pickerShow.value = false;
}

async function handleSave() {
  saving.value = true;
  try {
    const content = JSON.stringify({ cols: body.cols, rows: body.rows, layout: body.layout });
    await update({ id, title: form.value.title, layout: body.layout, content });
    dirty.value = false;
    message.success('保存成功');
  } catch (e: any) {
    message.error(e?.message || '保存失败');
  } finally {
    saving.value = false;
  }
}

/** ===== 导出 / 打印 ===== */
async function exportPng() {
  if (!refPreview.value) return;
  exporting.value = true;
  try {
    const canvas = await html2canvas(refPreview.value, { scale: 2, useCORS: true, backgroundColor: '#fff' });
    const link = document.createElement('a');
    link.href = canvas.toDataURL('image/png');
    link.download = `${form.value.title || '型式分期表'}.png`;
    link.click();
  } catch (e: any) {
    message.error(e?.message || '导出 PNG 失败');
  } finally {
    exporting.value = false;
  }
}

async function exportPdf() {
  if (!refPreview.value) return;
  exporting.value = true;
  try {
    const landscape = body.layout === 'A4_LANDSCAPE';
    await (html2pdf as any)()
      .set({
        margin: 6,
        filename: `${form.value.title || '型式分期表'}.pdf`,
        image: { type: 'jpeg', quality: 0.95 },
        html2canvas: { scale: 2, useCORS: true, backgroundColor: '#fff' },
        jsPDF: { unit: 'mm', format: 'a4', orientation: landscape ? 'landscape' : 'portrait' },
        pagebreak: { mode: ['css', 'legacy'] },
      })
      .from(refPreview.value)
      .save();
  } catch (e: any) {
    message.error(e?.message || '导出 PDF 失败');
  } finally {
    exporting.value = false;
  }
}

function doPrint() {
  const landscape = body.layout === 'A4_LANDSCAPE';
  printJS({
    printable: 'ty-preview-print',
    type: 'html',
    targetStyles: ['*'],
    header: form.value.title || '型式分期表',
    headerStyle: 'text-align:center;font-size:16px;font-weight:600;margin-bottom:8px;',
    style: `@page{size:A4 ${landscape ? 'landscape' : 'portrait'};margin:8mm}
      table{border-collapse:collapse;width:100%}
      th,td{border:1px solid #333;padding:4px;font-size:12px;text-align:center;vertical-align:top}
      img{max-width:120px;max-height:120px}`,
  });
}

function exportExcel() {
  const XL: any = XLSX;
  const headerRows = 2;
  const totalCols = 2 + body.cols.length;
  const totalRows = headerRows + body.rows.length;
  const aoa: any[][] = Array.from({ length: totalRows }, () => new Array(totalCols).fill(''));
  const merges: any[] = [];
  aoa[0][0] = '期别/段别';
  merges.push({ s: { r: 0, c: 0 }, e: { r: 0, c: 1 } });
  aoa[1][0] = '期';
  aoa[1][1] = '段';
  body.cols.forEach((col, ci) => {
    aoa[0][2 + ci] = col.artifactType || '';
    aoa[1][2 + ci] = '型式 / 标型器';
  });
  body.rows.forEach((row, ri) => {
    const gr = headerRows + ri;
    aoa[gr][0] = row.phase || '';
    aoa[gr][1] = row.section || '';
    const phaseCell = phaseCells.value[ri];
    if (phaseCell?.show && phaseCell.rowspan > 1) {
      merges.push({ s: { r: gr, c: 0 }, e: { r: gr + phaseCell.rowspan - 1, c: 0 } });
    }
    body.cols.forEach((_col, li) => {
      if (coverage.value[ri]?.[li]) return;
      const cell = row.cells[li];
      if (!cell) return;
      const itemTexts = cell.items?.length
        ? cell.items.map((item) => [itemText(item), item.figureName].filter(Boolean).join(' ')).filter(Boolean)
        : [];
      aoa[gr][2 + li] = itemTexts.length ? itemTexts.join('\n') : [cell.text, cell.figureName].filter(Boolean).join('\n');
      const rs = cell.rowspan || 1;
      const cs = cell.colspan || 1;
      if (rs > 1 || cs > 1) {
        merges.push({ s: { r: gr, c: 2 + li }, e: { r: gr + rs - 1, c: 2 + li + cs - 1 } });
      }
    });
  });
  const ws = XL.utils.aoa_to_sheet(aoa);
  ws['!merges'] = merges;
  // 简单加边框 + 居中
  const range = XL.utils.decode_range(ws['!ref']);
  for (let r = range.s.r; r <= range.e.r; r++) {
    for (let c = range.s.c; c <= range.e.c; c++) {
      const ref = XL.utils.encode_cell({ r, c });
      if (!ws[ref]) ws[ref] = { t: 's', v: '' };
      ws[ref].s = {
        alignment: { horizontal: 'center', vertical: 'center', wrapText: true },
        border: {
          top: { style: 'thin', color: { rgb: '999999' } },
          bottom: { style: 'thin', color: { rgb: '999999' } },
          left: { style: 'thin', color: { rgb: '999999' } },
          right: { style: 'thin', color: { rgb: '999999' } },
        },
      };
    }
  }
  ws['!cols'] = new Array(totalCols).fill({ wch: 16 });
  const wb = XL.utils.book_new();
  XL.utils.book_append_sheet(wb, ws, '型式分期');
  XL.writeFile(wb, `${form.value.title || '型式分期表'}.xlsx`);
  message.info('Excel 已导出文字与图名；线图请使用 PNG/PDF 导出。');
}

function emptyCell(): Cell {
  return { text: '', rowspan: 1, colspan: 1 };
}

function normalizeCellItem(item?: Partial<CellItem>): CellItem {
  return {
    text: item?.text || [item?.artifactStyle, item?.artifactSubStyle, item?.artifactStage].filter(Boolean).join(' '),
    artifactStyle: item?.artifactStyle,
    artifactSubStyle: item?.artifactSubStyle,
    artifactStage: item?.artifactStage,
    imageUrl: item?.imageUrl,
    artifactCode: item?.artifactCode,
    artifactId: item?.artifactId,
    figureName: item?.figureName,
    imgWidth: item?.imgWidth,
  };
}

function normalizeCellItems(cell?: Partial<Cell>): CellItem[] {
  if (Array.isArray(cell?.items)) return cell.items.map(normalizeCellItem);
  if (cell?.imageUrl || cell?.figureName || cell?.artifactId) {
    return [
      normalizeCellItem({
        text: cell.text,
        imageUrl: cell.imageUrl,
        artifactCode: cell.artifactCode,
        artifactId: cell.artifactId,
        figureName: cell.figureName,
        imgWidth: cell.imgWidth,
      }),
    ];
  }
  return [];
}

function normalizeCell(cell?: Partial<Cell>): Cell {
  const items = normalizeCellItems(cell);
  return {
    text: cell?.text || items.map(itemText).filter(Boolean).join('\n'),
    items: items.length ? items : undefined,
    imageUrl: cell?.imageUrl,
    artifactCode: cell?.artifactCode,
    artifactId: cell?.artifactId,
    figureName: cell?.figureName,
    rowspan: cell?.rowspan || 1,
    colspan: cell?.colspan || 1,
    imgWidth: cell?.imgWidth,
  };
}

function normalizeContent(parsed: any) {
  if (parsed.layout) body.layout = parsed.layout;
  if (isPeriodRowContent(parsed)) {
    body.cols = (parsed.cols || []).map((c: any) => ({ artifactType: c.artifactType || c.name || '' }));
    body.rows = (parsed.rows || []).map((r: any) => ({
      phase: r.phase || r.period || r.phaseName || '',
      section: r.section || r.sectionName || '',
      cells: Array.isArray(r.cells) ? r.cells.map(normalizeCell) : [],
    }));
    return;
  }

  const legacyRows = (parsed.rows || []).map((r: any) => normalizeLegacyArtifactRow(r, parsed.cols || []));
  const legacyLeaves = flattenLegacyPeriodCols(parsed.cols || []);
  body.cols = legacyRows.map((r: any) => ({ artifactType: r.artifactType || '' }));
  body.rows = (legacyLeaves.length ? legacyLeaves : [{ phase: '', section: '' }]).map((leaf, index) => ({
    phase: leaf.phase,
    section: leaf.section,
    cells: legacyRows.map((row: any) => normalizeCell(row.cells[index] || emptyCell())),
  }));
}

function isPeriodRowContent(parsed: any) {
  return (parsed.cols || []).some((c: any) => 'artifactType' in c)
    || (parsed.rows || []).some((r: any) => 'phase' in r || 'section' in r || 'phaseName' in r || 'sectionName' in r);
}

function flattenLegacyPeriodCols(cols: any[]) {
  const leaves: Array<{ phase: string; section: string }> = [];
  cols.forEach((col) => {
    const sections = col.sections || [];
    if (sections.length) {
      sections.forEach((section: string) => leaves.push({ phase: col.period || '', section: section || '' }));
    } else {
      leaves.push({ phase: col.period || '', section: '' });
    }
  });
  return leaves;
}

function normalizeLegacyArtifactRow(row: any, legacyCols: any[]) {
  if (Array.isArray(row.cells)) {
    return {
      artifactType: row.artifactType || '',
      cells: row.cells.map(normalizeCell),
    };
  }
  const leaves = flattenLegacyPeriodCols(legacyCols);
  const cells: Cell[] = leaves.map(emptyCell);
  (row.styles || []).forEach((style: any, index: number) => {
    if (index >= cells.length) return;
    const firstStage = (style.stages || [])[0];
    const stagesText = (style.stages || []).map((stage: any) => stage.name).filter(Boolean).join(' / ');
    cells[index] = {
      text: `${style.name || ''} ${stagesText}`.trim(),
      imageUrl: firstStage?.imageUrl,
      artifactCode: firstStage?.code,
      artifactId: firstStage?.artifactId,
      figureName: firstStage?.figureName,
      rowspan: 1,
      colspan: 1,
    };
  });
  return {
    artifactType: row.artifactType || '',
    cells,
  };
}

onMounted(async () => {
  try {
    const resp: any = await get(id);
    form.value = resp.data;
    if (form.value.layout) body.layout = form.value.layout;
    if (form.value.content) {
      try {
        const parsed = JSON.parse(form.value.content);
        normalizeContent(parsed);
      } catch (e) {
        console.warn('content parse fail', e);
      }
    }
    if (!body.cols.length) body.cols = [{ artifactType: '' }];
    if (!body.rows.length) body.rows = [{ phase: '', section: '', cells: [emptyCell()] }];
    ensureCellsAligned();
    await loadArtifactTypeOptions();
  } catch (e: any) {
    message.error(e?.message || '加载失败');
  }
});
</script>

<style scoped>
.typology-editor :deep(.n-card-header) {
  padding: 14px 18px;
  border-bottom: 1px solid #edf1f6;
}

.typology-editor :deep(.n-card-header__main) {
  min-width: 0;
}

.editor-header-main,
.editor-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.editor-header-main {
  flex-wrap: wrap;
}

.editor-actions {
  justify-content: flex-end;
}

.title-input {
  width: 320px;
  max-width: 36vw;
}

.layout-select {
  width: 130px;
}

.meta-tag {
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.canvas-wrap {
  flex: 1;
  overflow: auto;
  padding: 22px;
  background: #f4f7fb;
}

.editor-sheet {
  width: max-content;
  min-width: 760px;
  margin: 0 auto;
  padding: 20px;
  background: #fff;
  border: 1px solid #e3eaf2;
  border-radius: 8px;
  box-shadow: 0 8px 22px rgba(24, 39, 75, 0.06);
}

.typology-table {
  --phase-col-width: 150px;
  --section-col-width: 150px;
  --artifact-col-width: 340px;
  margin: 0 auto;
  background: #fff;
  border-collapse: separate;
  border-spacing: 0;
  table-layout: fixed;
  width: max-content;
  min-width: 760px;
  border: 1px solid #d8e1eb;
  border-right: 0;
  border-bottom: 0;
  border-radius: 6px;
  overflow: hidden;
}

.phase-col {
  width: var(--phase-col-width);
}

.section-col {
  width: var(--section-col-width);
}

.artifact-col {
  width: var(--artifact-col-width);
}

.typology-table th,
.typology-table td {
  border-right: 1px solid #d8e1eb;
  border-bottom: 1px solid #d8e1eb;
  padding: 0;
  vertical-align: top;
  position: relative;
}

.typology-table th {
  color: #2f3a4a;
}

.typology-table td {
  padding: 10px;
  background: #fff;
}

.typology-table tbody tr:hover td,
.typology-table tbody tr:hover .period-cell,
.typology-table tbody tr:hover .section-cell {
  background: #fbfdff;
}

.period-head,
.period-cell,
.section-cell {
  background: #f5f8fc;
  font-weight: 600;
}

.period-head {
  padding: 12px;
  text-align: center;
}

.period-group-head {
  font-size: 15px;
}

.artifact-head,
.artifact-sub-head {
  background: #f8fafd;
  font-weight: 600;
}

.artifact-head {
  padding: 8px 10px;
}

.artifact-head-inner {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
}

.artifact-sub-head {
  padding: 10px 12px;
  color: #5a6676;
  font-size: 13px;
  text-align: center;
}

.period-cell,
.section-cell {
  position: relative;
  z-index: 1;
  box-sizing: border-box;
}

.period-cell {
  width: var(--phase-col-width);
  min-width: var(--phase-col-width);
  max-width: var(--phase-col-width);
  padding: 10px;
  text-align: center;
  vertical-align: middle;
}

.section-cell {
  width: var(--section-col-width);
  min-width: var(--section-col-width);
  max-width: var(--section-col-width);
  padding: 10px;
  vertical-align: middle;
}

.section-editor {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-width: 0;
}

.section-editor .table-input {
  flex: 1 1 auto;
  min-width: 0;
}

.section-editor :deep(.n-button) {
  flex: 0 0 auto;
}

.table-input,
.cell-textarea {
  width: 100%;
}

.typology-table :deep(.n-input) {
  --n-box-shadow-focus: 0 0 0 2px rgba(45, 140, 240, 0.14);
}

.table-input :deep(.n-input-wrapper),
.cell-textarea :deep(.n-input-wrapper),
.table-input :deep(.n-base-selection) {
  min-height: 32px;
  background: #fff;
  border-radius: 5px;
  box-shadow: inset 0 0 0 1px #9fb4cf;
}

.table-input :deep(.n-input-wrapper:hover),
.cell-textarea :deep(.n-input-wrapper:hover),
.table-input :deep(.n-base-selection:hover) {
  box-shadow: inset 0 0 0 1px #5c94d6;
}

.table-input :deep(.n-input-wrapper:focus-within),
.cell-textarea :deep(.n-input-wrapper:focus-within),
.table-input :deep(.n-base-selection--active) {
  box-shadow:
    inset 0 0 0 1px #2d8cf0,
    0 0 0 2px rgba(45, 140, 240, 0.14);
}

.table-input :deep(.n-input__input-el) {
  text-align: center;
  font-weight: 500;
}

.artifact-head .table-input :deep(.n-input-wrapper) {
  background: #fff;
}

.cell-textarea :deep(.n-input-wrapper) {
  align-items: flex-start;
  padding: 6px 8px;
}

.cell-textarea :deep(.n-input__textarea-el) {
  min-height: 54px;
  line-height: 1.5;
}

.style-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.style-item {
  padding: 8px;
  background: #fbfcfe;
  border: 1px solid #e7edf4;
  border-radius: 4px;
}

.style-line {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) minmax(0, 1fr) 24px;
  gap: 6px;
  align-items: center;
}

.style-line :deep(.n-input-wrapper) {
  min-height: 30px;
  background: #f4f8ff;
  border-radius: 5px;
  box-shadow: inset 0 0 0 1px #b9cce4;
}

.style-line :deep(.n-input:hover .n-input-wrapper) {
  background: #fff;
  box-shadow: inset 0 0 0 1px #7aa7d9;
}

.style-line :deep(.n-input--focus .n-input-wrapper) {
  background: #fff;
  box-shadow:
    inset 0 0 0 1px #2d8cf0,
    0 0 0 2px rgba(45, 140, 240, 0.12);
}

.style-line :deep(.n-input__input-el) {
  color: #243447;
  font-weight: 500;
  text-align: center;
}

.style-line :deep(.n-input__placeholder) {
  color: #8394a8;
}

.cell-img {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  margin-top: 10px;
  text-align: center;
}

.cell-img img {
  max-width: 140px;
  max-height: 140px;
  padding: 6px;
  object-fit: contain;
  background: #fff;
  border: 1px solid #e5eaf1;
  border-radius: 4px;
}

.fig-name {
  font-size: 12px;
  color: #667085;
}

.cell-actions {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #e5eaf1;
}

.item-actions {
  margin-top: 6px;
}

.tip {
  width: fit-content;
  margin: 14px auto 0;
  padding: 8px 14px;
  color: #7b8794;
  font-size: 12px;
  text-align: center;
  background: #f7f9fc;
  border: 1px solid #edf1f6;
  border-radius: 4px;
}

.picker-img {
  height: 140px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fafafa;
  border-radius: 4px;
  overflow: hidden;
}
.picker-img img {
  max-width: 100%;
  max-height: 140px;
}
.picker-meta {
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  font-size: 12px;
}
.picker-meta .muted {
  color: #999;
}
/* 预览 / 打印 */
.preview-scroll {
  max-height: 70vh;
  overflow: auto;
  background: #e9e9e9;
  padding: 16px;
  display: flex;
  justify-content: center;
}
.a4-page {
  background: #fff;
  padding: 12mm;
  box-shadow: 0 0 6px rgba(0, 0, 0, 0.2);
}
.a4-page.portrait {
  width: 794px;
}
.a4-page.landscape {
  width: 1123px;
}
.ty-title {
  text-align: center;
  margin: 0 0 10px;
}
.ty-print {
  border-collapse: collapse;
  width: 100%;
}
.ty-print th,
.ty-print td {
  border: 1px solid #333;
  padding: 4px;
  font-size: 12px;
  text-align: center;
  vertical-align: top;
}
.ty-print img {
  max-width: 120px;
  max-height: 120px;
}
.pv-text {
  white-space: pre-wrap;
}
.pv-fig {
  font-size: 12px;
  color: #555;
  margin-top: 2px;
}
.pv-style-item + .pv-style-item {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #ddd;
}
</style>
