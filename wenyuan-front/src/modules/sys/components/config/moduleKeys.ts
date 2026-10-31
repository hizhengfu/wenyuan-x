/**
 * 配置行的前端类型与展示辅助。
 *
 * 每一行 `ConfigRow` 既是值的载体也是 schema 的载体，与后端 `io.hizhengfu.cloud.sys.system.dao.ConfigVo`
 * 保持一一对应。schema 列（fieldType / fieldGroup / module / minVal / maxVal / enumValues / pattern /
 * required / label / visibleWhen / enumLabels）在首启时由各模块的 `*Install` 写入 `sys_config` 表，后续代码升级通过 Install
 * 覆盖这些列，value 列（content / enable / remarks）则保留用户改动。
 */

export type FieldType = 'BOOLEAN' | 'INT' | 'LONG' | 'STRING' | 'ENUM';

export interface ConfigDisplayCondition {
  field: string;
  equals: string;
}

export interface ConfigRow {
  id?: string;
  name: string;
  content?: string | null;
  remarks?: string | null;
  isPrivate?: boolean | null;
  enable?: boolean | null;
  createTime?: string | null;

  // schema 列
  fieldType?: FieldType | null;
  module?: string | null;
  fieldGroup?: string | null;
  label?: string | null;
  minVal?: number | null;
  maxVal?: number | null;
  enumValues?: string[] | null;
  pattern?: string | null;
  required?: boolean | null;
  visibleWhen?: ConfigDisplayCondition | null;
  enumLabels?: Record<string, string> | null;
}

/**
 * 系统参数模块 tab：id 为模块技术码（如 notify），label 为展示名（如 通知中心）。
 * 与后端 `io.hizhengfu.cloud.sys.system.dao.ConfigModuleVo` 对齐；label 由后端 `config_module`
 * 字典提供（各模块在 *Install 中声明），前端不再硬编码"技术码→中文名"映射。
 */
export interface ConfigModule {
  id: string;
  label: string;
}

export interface FieldGroup {
  title: string;
  fields: ConfigRow[];
}

/** 与后端 ConfigConditions 使用同样的标量比较规则。 */
function normalized(value: unknown, type?: FieldType | null): string | undefined {
  if (value == null) return undefined;
  const text = String(value);
  if (type === 'BOOLEAN') {
    if (['TRUE', '1'].includes(text.trim().toUpperCase())) return 'TRUE';
    if (['FALSE', '0'].includes(text.trim().toUpperCase())) return 'FALSE';
    return undefined;
  }
  if (type === 'ENUM') return text.trim().toUpperCase();
  if (type === 'INT' || type === 'LONG') {
    if (!/^[+-]?\d+$/.test(text)) return undefined;
    const number = BigInt(text);
    return number >= -9223372036854775808n && number <= 9223372036854775807n ? String(number) : undefined;
  }
  return text;
}

/** 只解释后端条件；缺失依赖、隐藏的父字段及循环依赖都不会显示下级字段。 */
export function visibleConfigNames(rows: ConfigRow[], model: Record<string, unknown>): Set<string> {
  const byName = new Map(rows.map((row) => [row.name, row]));
  const cache = new Map<string, boolean>();
  function visible(name: string, visiting: Set<string>): boolean {
    if (cache.has(name)) return cache.get(name)!;
    const row = byName.get(name);
    if (!row || visiting.has(name)) return false;
    visiting.add(name);
    const condition = row.visibleWhen;
    let show = true;
    if (condition) {
      const dependency = byName.get(condition.field);
      const actual = normalized(model[condition.field], dependency?.fieldType);
      show = !!dependency && dependency.module === row.module && visible(condition.field, visiting)
        && actual !== undefined && actual === normalized(condition.equals, dependency.fieldType);
    }
    visiting.delete(name);
    cache.set(name, show);
    return show;
  }
  return new Set(rows.filter((row) => visible(row.name, new Set())).map((row) => row.name));
}

/** 按行出现顺序聚合到 group，同一 group 内保留原顺序。 */
export function groupRows(rows: ConfigRow[]): FieldGroup[] {
  const groups: FieldGroup[] = [];
  const indexByTitle = new Map<string, FieldGroup>();
  for (const row of rows) {
    const title = row.fieldGroup || '其他';
    let g = indexByTitle.get(title);
    if (!g) {
      g = { title, fields: [] };
      indexByTitle.set(title, g);
      groups.push(g);
    }
    g.fields.push(row);
  }
  return groups;
}
