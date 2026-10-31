import { onActivated, onBeforeUnmount, onDeactivated, ref, shallowRef, watch } from 'vue';
import { errorText } from '../utils/restorationError';
export type CurrentPickerRow = { id?: string; orgId?: string };
type Props = { value?: string | string[] | null; multiple?: boolean; disabled?: boolean; orgId?: string };
type Option<T> = { label: string; value: string; raw: T; disabled: boolean };
const text = (v: unknown): v is string => typeof v === 'string' && !!v.trim();
function body(response: any) {
  if (!response || (response.status !== undefined && response.status !== 200) || response.data == null) throw Error('读取当前资料失败，请重试');
  return response.data;
}
/** 每次检索只接受本次结果，已选记录补读失败不伪造可选项。 */
export function useCurrentRecordPicker<T extends CurrentPickerRow>(props: Props, config: {
  page: (query: any) => Promise<any>; get: (id: string) => Promise<any>;
  query: () => Record<string, unknown>; validate: (row: T) => boolean; label: (row: T) => string; matches: (row: T) => boolean; emit: (value: any) => void;
}) {
  const options = ref<Option<T>[]>([]), loading = ref(false), error = ref('');
  const selectValue = shallowRef<(value: any) => void>(() => {});
  let active = true, generation = 0, timer: ReturnType<typeof setTimeout> | undefined;
  function invalidate() {
    generation++; if (timer !== undefined) clearTimeout(timer); timer = undefined;
    options.value = []; loading.value = false; error.value = ''; selectValue.value = () => {};
  }
  function selectedIds(): string[] {
    if (props.value == null || props.value === '') return [];
    if (props.multiple) {
      if (!Array.isArray(props.value) || props.value.some(v => !text(v))) throw Error('已选编号格式不正确，请重新选择');
      return [...new Set(props.value)];
    }
    if (!text(props.value)) throw Error('已选编号格式不正确，请重新选择');
    return [props.value];
  }
  function validate(row: any, expectedId?: string): T {
    if (!row || !text(row.id) || !text(row.orgId) || !config.validate(row)
      || (expectedId !== undefined && row.id !== expectedId) || (props.orgId && row.orgId !== props.orgId)) throw Error('资料编号或所属馆不一致，请重新核对');
    return row;
  }
  function arm(token: number, selected: string[], allowed: Set<string> = new Set(), ready = false) {
    const previous = new Set(selected);
    selectValue.value = value => {
      if (!active || token !== generation || props.disabled) return;
      // 读取失败时仍允许明确清空或移除旧编号，但不能添加未经核对的记录。
      if (value === null) { config.emit(null); return; }
      if (props.multiple) {
        if (!Array.isArray(value) || value.some(v => !text(v)) || new Set(value).size !== value.length) return;
        if (value.length < previous.size && value.every(v => previous.has(v))) { config.emit(value); return; }
        if (ready && !loading.value && !error.value && value.every(v => allowed.has(v) || previous.has(v))) config.emit(value);
      } else if (ready && !loading.value && !error.value && text(value) && allowed.has(value)) config.emit(value);
    };
  }
  async function fetchList(keyword?: string) {
    invalidate(); if (!active) return;
    const token = generation;
    try {
      const selected = selectedIds(); arm(token, selected); loading.value = true;
      let rows: T[] = [];
      if (!props.disabled) {
        const response = await config.page({ pageSize: 30, ...config.query(), ...(props.orgId ? { orgId: props.orgId } : {}),
          ...(keyword?.trim() ? { keyword: keyword.trim() } : {}) });
        if (!active || token !== generation) return;
        const data = body(response), list = Array.isArray(data) ? data : data.list;
        if (!Array.isArray(list)) throw Error('列表响应不完整，请重新读取');
        rows = list.map(row => validate(row));
        if (new Set(rows.map(row => row.id)).size !== rows.length) throw Error('列表编号重复，请重新读取');
      }
      const byId = new Map(rows.map(row => [row.id!, row]));
      const missing = selected.filter(id => !byId.has(id));
      const results = await Promise.allSettled(missing.map(id => config.get(id)));
      if (!active || token !== generation) return;
      results.forEach((result, index) => {
        if (result.status === 'rejected') throw result.reason;
        const row = validate(body(result.value), missing[index]); byId.set(row.id!, row);
      });
      const eligible = config.matches;
      const combined = [...selected.map(id => byId.get(id)!), ...rows.filter(row => !selected.includes(row.id!))];
      options.value = combined.filter(row => selected.includes(row.id!) || eligible(row)).map(row => ({
        value: row.id!, label: config.label(row), raw: row, disabled: !eligible(row),
      })) as typeof options.value;
      arm(token, selected, new Set(combined.filter(eligible).map(row => row.id!)), true);
    } catch (e) {
      if (active && token === generation) error.value = errorText(e, '读取当前资料失败，请重试');
    } finally { if (token === generation) loading.value = false; }
  }
  function onSearch(keyword: string) {
    invalidate(); if (!active || props.disabled) return;
    loading.value = true;
    timer = setTimeout(() => { timer = undefined; void fetchList(keyword); }, 250);
  }
  function onShow(show: boolean) { if (show && active && !props.disabled) void fetchList(); }
  watch(() => [props.value, props.multiple, props.disabled, props.orgId, config.query()], () => { void fetchList(); }, { immediate: true, deep: true, flush: 'sync' });
  onDeactivated(() => { active = false; invalidate(); });
  onActivated(() => { if (!active) { active = true; void fetchList(); } });
  onBeforeUnmount(() => { active = false; invalidate(); });
  return { options, loading, error, selectValue, onSearch, onShow };
}
