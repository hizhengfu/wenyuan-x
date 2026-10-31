<template>
  <n-card
    class="wh-full flex flex-col"
    bordered
    content-style="flex:1;display:flex;flex-direction:column;overflow:hidden;"
  >
    <template #header>
      <n-space align="center">
        <n-icon size="22"><IconMdiFormatListBulleted /></n-icon>
        <span>出土器物登记表</span>
      </n-space>
    </template>
    <template #header-extra>
      <n-space>
        <n-button size="small" :disabled="!table.rows.length" @click="exportPng">导出图片</n-button>
        <n-button size="small" :disabled="!table.rows.length" @click="exportPdf">导出 PDF</n-button>
        <n-button size="small" :disabled="!table.rows.length" @click="exportExcel">导出 Excel</n-button>
      </n-space>
    </template>

    <n-form inline label-placement="left" label-width="80px" :show-feedback="false" size="small">
      <n-form-item label="遗址" required>
        <WmsProjectSelect v-model:value="paramsForm.projectId" clearable filterable style="width: 240px" />
      </n-form-item>
      <n-form-item>
        <n-button type="primary" :loading="loading" @click="fetchData">智能统计</n-button>
      </n-form-item>
      <n-form-item v-if="table.rows.length">
        <n-tag :bordered="false" type="info">共 {{ table.rows.length }} 个单位 · {{ table.total }} 件</n-tag>
      </n-form-item>
    </n-form>

    <n-divider style="margin: 8px 0" />

    <div class="register-wrap">
      <n-spin :show="loading" style="width: 100%">
        <n-empty v-if="!table.rows.length" description="选择遗址后点击「智能统计」" />
        <div v-else ref="refTable" class="reg-table-wrap">
          <h3 class="reg-title">{{ table.projectName ? table.projectName + ' · ' : '' }}出土器物登记表</h3>
          <table class="reg-table">
            <thead>
              <tr>
                <th class="col-type" rowspan="2">遗迹类型</th>
                <th class="col-position" rowspan="2">遗迹单位</th>
                <th :colspan="table.materials.length || 1">出土器物</th>
              </tr>
              <tr>
                <th v-for="m in table.materials" :key="m">{{ materialLabel(m) }}</th>
                <th v-if="!table.materials.length">--</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in table.rows" :key="row.cellName">
                <td v-if="row.showCellType" class="type-cell" :rowspan="row.cellTypeRowspan">
                  {{ row.cellType || '--' }}
                </td>
                <td class="position">{{ row.cellName }}</td>
                <td v-for="m in table.materials" :key="m" class="mat-cell">
                  <template v-for="(e, i) in row.cells[m] || []" :key="i">
                    <span v-if="i" class="sep">{{ entrySeparator }}</span>
                    <span
                      class="entry"
                      :title="`${e.name}${e.typeStyle} ${e.count}件（点击查看卡片）`"
                      @click="openEntry(e)"
                      >{{ e.label }}</span
                    >
                  </template>
                </td>
                <td v-if="!table.materials.length" class="mat-cell"></td>
              </tr>
            </tbody>
          </table>
          <div class="reg-tip">
            说明：按遗迹类型、位置和材质类型聚合；材质单元格按「名称 + 型式 + 数量」逗号分隔显示（如
            鼎（7），簋（5）），点击器名可打开对应文物卡片。
          </div>
        </div>
      </n-spin>
    </div>

    <n-modal
      v-model:show="cardShow"
      preset="card"
      class="artifact-card-modal"
      style="width: 80%; max-width: 1100px"
      :title="cardTitle"
    >
      <div class="artifact-detail-shell">
        <ArtifactBookDetail v-if="cardId" :id="cardId" />
      </div>
    </n-modal>
  </n-card>
</template>

<script lang="ts" setup>
import { ref, onMounted } from 'vue';
import { useMessage } from 'naive-ui';
import { registrationTable } from '../../../api/analysis';
import { frontType } from '@/modules/sys/api/dict';
import { WmsProjectSelect } from '../../../components/form/field';
import ArtifactBookDetail from '../../../components/ArtifactBookDetail.vue';
import html2canvas from 'html2canvas';
// @ts-ignore html2pdf.js 无类型声明
import html2pdf from 'html2pdf.js';
// @ts-ignore xlsx-js-style 无类型声明
import * as XLSX from 'xlsx-js-style';

interface RegEntry {
  name: string;
  typeStyle: string;
  count: number;
  label: string;
  artifactIds: string[];
}

interface RegRow {
  cellId?: string;
  cellName: string;
  cellType: string;
  showCellType: boolean;
  cellTypeRowspan: number;
  total: number;
  cells: Record<string, RegEntry[]>;
}

interface RegTable {
  projectName: string;
  materials: string[];
  rows: RegRow[];
  total: number;
}

const REGISTER_ENTRY_SEPARATOR = '，';
const EMPTY_TABLE: RegTable = { projectName: '', materials: [], rows: [], total: 0 };
const RITUAL_ORDER = [
  '鼎',
  '簋',
  '鬲',
  '甗',
  '簠',
  '盨',
  '敦',
  '豆',
  '盆',
  '钵',
  '罐',
  '瓮',
  '壶',
  '瓶',
  '盘',
  '匜',
  '盂',
  '爵',
  '角',
  '斝',
  '觚',
  '觯',
  '尊',
  '卣',
  '罍',
  '瓿',
  '盉',
  '钟',
  '镈',
];
const MATERIAL_LABEL_BY_ENUM_CODE: Record<string, string> = {
  SHIQI: '石器',
  YUQI: '玉器',
  TAOQI: '陶器',
  GUQI: '骨器',
  MUQI: '木器',
  TONGQI: '铜器',
  TIEQI: '铁器',
  JINYINQI: '金银器',
  QIAN: '铅',
  XINQI: '锌器',
  CIQI: '瓷器',
  XIQI: '漆器',
  BOLIQI: '玻璃器',
  FANGZHIPIN: '纺织品',
  ZHILEI: '纸类文物',
};
const MATERIAL_LABEL_BY_LEGACY_INDEX = Object.fromEntries(
  Object.values(MATERIAL_LABEL_BY_ENUM_CODE).map((label, index) => [String(index), label]),
);

const message = useMessage();
const loading = ref(false);
const paramsForm = ref<{ projectId?: string }>({});
const table = ref<RegTable>({ projectName: '', materials: [], rows: [], total: 0 });
const refTable = ref<HTMLElement>();
const entrySeparator = REGISTER_ENTRY_SEPARATOR;

// 材质字典：把材质 code 解析为标签；解析不到（"未填"/历史值/已是标签）时回退原值，避免表头为空
const materialDict = ref<Record<string, string>>({});
onMounted(() => {
  frontType('wms_artifact_material_type', true).then((resp: any) => {
    const map: Record<string, string> = {};
    (resp?.data || []).forEach((d: any) => {
      map[String(d.value)] = d.label;
    });
    materialDict.value = map;
  });
});
function materialLabel(m: string): string {
  return resolveMaterialLabel(m, materialDict.value);
}

function normalizeText(value: unknown): string {
  return String(value ?? '').trim();
}

function normalizeCount(value: unknown): number {
  const count = Number(value);
  return Number.isFinite(count) ? count : 0;
}

function buildEntryLabel(entry: Pick<RegEntry, 'name' | 'typeStyle' | 'count'>): string {
  return `${entry.name}${entry.typeStyle}（${entry.count}）`;
}

function ritualIndex(name: string): number {
  const index = RITUAL_ORDER.findIndex((item) => name.includes(item));
  return index >= 0 ? index : RITUAL_ORDER.length;
}

function entryKey(entry: Pick<RegEntry, 'name' | 'typeStyle'>): string {
  return `${entry.name}\u0000${entry.typeStyle}`;
}

function compareEntry(a: RegEntry, b: RegEntry): number {
  return (
    ritualIndex(a.name) - ritualIndex(b.name) ||
    a.name.localeCompare(b.name, 'zh-Hans-CN') ||
    a.typeStyle.localeCompare(b.typeStyle, 'zh-Hans-CN')
  );
}

function naturalPrefix(value: string): string {
  return /^\D*/.exec(value)?.[0] || value;
}

function naturalNum(value: string): number {
  return Number(/\d+/.exec(value)?.[0] || 0);
}

function compareRow(a: RegRow, b: RegRow): number {
  return (
    a.cellType.localeCompare(b.cellType, 'zh-Hans-CN') ||
    naturalPrefix(a.cellName).localeCompare(naturalPrefix(b.cellName), 'zh-Hans-CN') ||
    naturalNum(a.cellName) - naturalNum(b.cellName) ||
    a.cellName.localeCompare(b.cellName, 'zh-Hans-CN')
  );
}

function normalizeEntry(entry: Partial<RegEntry>): RegEntry {
  const normalized = {
    name: normalizeText(entry.name) || '未命名器物',
    typeStyle: normalizeText(entry.typeStyle),
    count: normalizeCount(entry.count),
    label: '',
    artifactIds: Array.isArray(entry.artifactIds) ? entry.artifactIds.map(String) : [],
  };
  normalized.label = buildEntryLabel(normalized);
  return normalized;
}

function collectMaterials(source: Partial<RegTable>, rows: RegRow[]): string[] {
  const materials = (Array.isArray(source.materials) ? source.materials : []).map(normalizeText).filter(Boolean);
  rows.forEach((row) => {
    Object.keys(row.cells).forEach((material) => {
      if (material && !materials.includes(material)) {
        materials.push(material);
      }
    });
  });
  return materials;
}

function buildGlobalEntryRanks(rows: RegRow[]): Map<string, number> {
  const allEntries = rows.flatMap((row) => Object.values(row.cells).flat());
  const sortedKeys = Array.from(new Map(allEntries.sort(compareEntry).map((entry) => [entryKey(entry), entry])).keys());
  return new Map(sortedKeys.map((key, index) => [key, index]));
}

function applyCellTypeRowspans(rows: RegRow[]): void {
  let index = 0;
  while (index < rows.length) {
    const start = index;
    const cellType = rows[index].cellType;
    while (index < rows.length && rows[index].cellType === cellType) {
      rows[index].showCellType = false;
      rows[index].cellTypeRowspan = 0;
      index++;
    }
    rows[start].showCellType = true;
    rows[start].cellTypeRowspan = index - start;
  }
}

function normalizeRegistrationTable(source?: Partial<RegTable> | null): RegTable {
  if (!source) return { ...EMPTY_TABLE };

  const rows = (Array.isArray(source.rows) ? source.rows : []).map((row) => {
    const cells: Record<string, RegEntry[]> = {};
    Object.entries(row.cells || {}).forEach(([material, entries]) => {
      const materialName = normalizeText(material) || '未填';
      cells[materialName] = (Array.isArray(entries) ? entries : []).map(normalizeEntry);
    });
    return {
      cellId: normalizeText(row.cellId) || undefined,
      cellName: normalizeText(row.cellName) || '未分单位',
      cellType: normalizeText(row.cellType),
      showCellType: true,
      cellTypeRowspan: 1,
      total: normalizeCount(row.total),
      cells,
    };
  });

  const entryRanks = buildGlobalEntryRanks(rows);
  rows.forEach((row) => {
    Object.keys(row.cells).forEach((material) => {
      row.cells[material] = [...row.cells[material]].sort((a, b) => {
        const rankDiff =
          (entryRanks.get(entryKey(a)) ?? Number.MAX_SAFE_INTEGER) -
          (entryRanks.get(entryKey(b)) ?? Number.MAX_SAFE_INTEGER);
        return rankDiff || compareEntry(a, b);
      });
    });
  });

  const sortedRows = rows.sort(compareRow);
  applyCellTypeRowspans(sortedRows);

  return {
    projectName: normalizeText(source.projectName),
    materials: collectMaterials(source, rows),
    rows: sortedRows,
    total: normalizeCount(source.total),
  };
}

function resolveMaterialLabel(value: string, dict: Record<string, string> = {}): string {
  const key = normalizeText(value);
  return dict[key] || MATERIAL_LABEL_BY_LEGACY_INDEX[key] || MATERIAL_LABEL_BY_ENUM_CODE[key] || key;
}

async function fetchData() {
  if (!paramsForm.value.projectId) {
    message.warning('请先选择遗址');
    return;
  }
  loading.value = true;
  try {
    const resp: any = await registrationTable({ projectId: paramsForm.value.projectId });
    table.value = normalizeRegistrationTable(resp.data);
    if (!table.value.rows.length) message.info('该遗址暂无文物数据');
  } catch (e: any) {
    message.error(e?.message || '统计失败');
  } finally {
    loading.value = false;
  }
}

const cardShow = ref(false);
const cardId = ref('');
const cardTitle = ref('文物卡片');
function openEntry(e: RegEntry) {
  if (e.artifactIds?.length) {
    cardId.value = e.artifactIds[0];
    cardTitle.value = `查看「${e.name}${e.typeStyle || ''}」`;
    cardShow.value = true;
  } else {
    message.info(`${e.name}：${e.count} 件`);
  }
}

async function exportPng() {
  if (!refTable.value || !table.value.rows.length) return;
  const canvas = await html2canvas(refTable.value, { scale: 2, useCORS: true, backgroundColor: '#fff' });
  const a = document.createElement('a');
  a.href = canvas.toDataURL('image/png');
  a.download = `${table.value.projectName || '出土器物登记表'}.png`;
  a.click();
}

async function exportPdf() {
  if (!refTable.value || !table.value.rows.length) return;
  await (html2pdf as any)()
    .set({
      margin: 6,
      filename: `${table.value.projectName || '出土器物登记表'}.pdf`,
      image: { type: 'jpeg', quality: 0.95 },
      html2canvas: { scale: 2, useCORS: true, backgroundColor: '#fff' },
      jsPDF: { unit: 'mm', format: 'a4', orientation: 'landscape' },
      pagebreak: { mode: ['css', 'legacy'] },
    })
    .from(refTable.value)
    .save();
}

function exportExcel() {
  const tableEl = refTable.value?.querySelector('table');
  if (!tableEl) return;
  const XL: any = XLSX;
  // table_to_sheet 只会转换 <table> 本身，表上方的标题会丢失；
  // 故先转为二维数组，在最前面补一行合并的标题，再生成工作表。
  const dataWs = XL.utils.table_to_sheet(tableEl);
  const aoa: any[][] = XL.utils.sheet_to_json(dataWs, { header: 1 });
  const title = `${table.value.projectName ? table.value.projectName + ' ' : ''}出土器物登记表`;
  aoa.unshift([title]);
  const ws = XL.utils.aoa_to_sheet(aoa);
  const colCount = (aoa[1]?.length as number) || 1;
  ws['!merges'] = [{ s: { r: 0, c: 0 }, e: { r: 0, c: Math.max(0, colCount - 1) } }];
  const wb = XL.utils.book_new();
  XL.utils.book_append_sheet(wb, ws, '出土器物登记表');
  XL.writeFile(wb, `${title}.xlsx`);
}
</script>

<style scoped>
.register-wrap {
  flex: 1;
  overflow: auto;
  padding: 4px;
}
.reg-table-wrap {
  background: #fff;
  padding: 12px;
}
.reg-title {
  text-align: center;
  margin: 0 0 12px;
}
.reg-table {
  border-collapse: collapse;
  width: 100%;
  font-size: 13px;
}
.reg-table th,
.reg-table td {
  border: 1px solid #333;
  padding: 5px 8px;
  vertical-align: top;
  text-align: left;
}
.reg-table th {
  background: #f4f8fb;
  text-align: center;
  white-space: nowrap;
}
.col-type {
  width: 90px;
}
.col-position {
  width: 100px;
}
.position {
  font-weight: 600;
  text-align: center;
}
.reg-table td.type-cell {
  text-align: center;
  vertical-align: middle;
}
.mat-cell {
  min-width: 120px;
}
.entry {
  cursor: pointer;
  color: #1a6dd0;
}
.entry:hover {
  text-decoration: underline;
}
.sep {
  color: #999;
}
.reg-tip {
  margin-top: 10px;
  font-size: 12px;
  color: #999;
}
.artifact-card-modal {
  height: 78vh;
}
.artifact-card-modal :deep(.n-card__content) {
  height: calc(78vh - 72px);
  overflow: hidden;
}
.artifact-detail-shell {
  height: 600px;
  min-height: 0;
  overflow: hidden;
  display: flex;
}
</style>
