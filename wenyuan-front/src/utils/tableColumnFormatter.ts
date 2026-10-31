import { shallowReactive } from 'vue'
import { get as getByKey, isArray, isBoolean } from 'lodash-es'
import { type as fetchDict } from '@/modules/sys/api/dict'
import { formatDateTime, formatDateValue } from './datetime'

/**
 * 把列定义里的 `columnType`/`dictType` 等声明式配置，
 * 编译成原生 `<n-data-table>` 可识别的 `render` 函数。
 *
 * 行为与 `HPageDataTable` / `HDataTable` 一致：
 * - `columnType: 'date'`      → `YYYY-MM-DD`，空值 `--`
 * - `columnType: 'dateTime'`  → `YYYY-MM-DD HH:mm:ss`，空值 `--`
 * - `columnType: 'daterange'` → 数组 → `起~止`；单值 → `YYYY-MM-DD`；空值 `--`
 * - `dictType: 'XXX'`         → 异步加载字典并翻译为 label
 *     · 多选逗号串：`"A,B,C"` → `"标签A,标签B,标签C"`
 *     · boolean → 1/0 后再查表
 *     · 字典未到位先显示 `--`，加载完成后响应式自动重渲染
 *
 * 使用场景：详情面板 / Card 内嵌的 `<n-data-table>`，这些位置不适合接入
 * 完整的 `HPageDataTable`（依赖 fetchData + 分页 + 查询表单），但又想复用
 * 统一的渲染规范。
 *
 * **不包含**：`title`/`index`/`image`/`preview`/`path`/`tag` 等列类型。
 * 这些列依赖表格组件内部状态（分页起始行、行点击回调、文件 URL 解析等），
 * 应继续由 `HPageDataTable` 内部 `defaultRenderColumn` 处理。
 *
 * @example
 * const cols = applyColumnFormatters([
 *   { key: 'syncAt', title: '时间', width: 180, columnType: 'dateTime' },
 *   { key: 'status', title: '结果', width: 80, dictType: 'PROT_SYNC_STATUS' },
 *   { key: 'tags',   title: '标签', dictType: 'PROT_TAG' },  // 多选逗号串
 * ])
 */
export function applyColumnFormatters<T extends Record<string, any>>(columns: T[]): T[] {
  return columns.map((col) => {
    if (!col || typeof col !== 'object') return col
    if ((col as any).render) return col // 已有自定义 render，原样保留
    const { columnType, dictType, key } = col as any
    if (!key) return col

    if (columnType === 'date') {
      return { ...col, render: (row: any) => fmtDate(getByKey(row, key)) }
    }
    if (columnType === 'dateTime') {
      return { ...col, render: (row: any) => fmtDateTime(getByKey(row, key)) }
    }
    if (columnType === 'daterange') {
      return { ...col, render: (row: any) => fmtDateRange(getByKey(row, key)) }
    }
    if (dictType) {
      ensureDict(dictType)
      return { ...col, render: (row: any) => translateDict(getByKey(row, key), dictType) }
    }
    return col
  })
}

const DASH = '--'

/**
 * 模块级字典缓存：dictType → { value: label }。
 * 用 `shallowReactive`，加载完成后写入新 map 会触发依赖的 render 重新执行，
 * 避免"先空后填"的渲染竞态。
 *
 * 注：底层 `fetchDict()` 本身已有缓存（`dict.ts#dictCaches`），这里再加一层
 * 是为了把"字典数组 → value→label map"的转换结果也缓存，避免每次渲染重算。
 */
const dictMaps = shallowReactive<Record<string, Record<string, string>>>({})
const loadingDict = new Set<string>()

function ensureDict(type: string) {
  if (dictMaps[type] || loadingDict.has(type)) return
  loadingDict.add(type)
  fetchDict(type, true)
    .then((resp: any) => {
      const map: Record<string, string> = {}
      ;(resp?.data || []).forEach((o: any) => {
        if (o?.value != null) map[String(o.value)] = o.label
      })
      dictMaps[type] = map
    })
    .catch(() => {
      // 失败也写空 map，避免反复重试
      dictMaps[type] = {}
    })
    .finally(() => loadingDict.delete(type))
}

function translateDict(rawValue: any, dictType: string): string {
  const m = dictMaps[dictType]
  if (!m) return DASH // 字典尚未到位

  let v = rawValue
  if (isBoolean(v)) v = v ? 1 : 0
  if (v == null || v === '') return DASH

  // 多选逗号串：'A,B,C' → '标签A,标签B,标签C'
  const labels = String(v)
    .split(',')
    .map((k) => m[k] ?? k)
    .filter((s) => s !== '' && s != null)
  return labels.length ? labels.join(',') : DASH
}

function fmtDate(v: any): string {
  return formatDateValue(v)
}
function fmtDateTime(v: any): string {
  return formatDateTime(v)
}
function fmtDateRange(v: any): string {
  if (!v) return DASH
  if (isArray(v)) return v.map((it) => fmtDate(it)).join('~')
  return fmtDate(v)
}
