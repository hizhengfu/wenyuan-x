import { onActivated, onBeforeUnmount, onDeactivated, ref, shallowRef, watch, type Ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { errorText } from '../utils/restorationError';

type Identity = { id?: string; orgId?: string };
type Context<T> = { id?: string | null; orgId: string; orgName: string; revision: string; canOperate: boolean; issue?: string; record: T | null };
type Options<T> = { label: string; empty: T; context: (id?: string) => Promise<any>;
  save: (record: T, orgId: string, revision: string) => Promise<any>;
  remove: (id: string, orgId: string, revision: string) => Promise<any>; disable?: (id: string, orgId: string, revision: string) => Promise<any>; refresh: () => unknown };
const text = (v: unknown): v is string => typeof v === 'string' && !!v.trim();
function body(response: any) {
  if (!response || Array.isArray(response) || (response.status !== undefined && response.status !== 200) || response.data == null) {
    throw Error('资料读取或办理失败，请重新核实');
  }
  return response.data;
}

/** 同次读取的正文、馆别和版本共同归属一个窗口，迟到读取及旧提交不能转到新窗口。 */
export function useMasterDataWindow<T extends Identity>(options: Options<T>) {
  const formShow = ref(false), viewShow = ref(false), loading = ref(false), submitting = ref(false), error = ref(''), title = ref('');
  const vo = ref(cloneDeep(options.empty)) as Ref<T>;
  const viewRow = ref<T | null>(null) as Ref<T | null>;
  const mode = ref<'add' | 'edit' | 'delete' | 'disable' | 'view'>('add'), formKey = ref(0);
  const handleSubmit = shallowRef<(() => Promise<any>)>();
  const updateField = shallowRef<(field: keyof T, value: unknown) => void>();
  let active = true, epoch = 0, inFlight = false;
  const current = (token: number) => active && epoch === token && (formShow.value || viewShow.value);
  function invalidate() { epoch++; formKey.value++; handleSubmit.value = undefined; updateField.value = undefined; loading.value = false; inFlight = false; submitting.value = false; }
  function close() { invalidate(); formShow.value = false; viewShow.value = false; vo.value = cloneDeep(options.empty); viewRow.value = null; error.value = ''; }
  watch(formShow, show => { if (!show) { invalidate(); vo.value = cloneDeep(options.empty); } }, { flush: 'sync' });
  watch(viewShow, show => { if (!show) { invalidate(); viewRow.value = null; } }, { flush: 'sync' });
  onActivated(() => { active = true; });
  onDeactivated(() => { active = false; close(); });
  onBeforeUnmount(() => { active = false; close(); });

  function parse(value: any, id?: string): Context<T> {
    if (!value || Array.isArray(value) || (value.id ?? undefined) !== id || !text(value.orgId) || !text(value.orgName)
      || !text(value.revision) || typeof value.canOperate !== 'boolean') throw Error('核对资料不完整或编号不一致，请重新读取');
    if (id ? !value.record || value.record.id !== id || value.record.orgId !== value.orgId : value.record != null) {
      throw Error('资料与原馆或原记录不一致，请重新读取');
    }
    return cloneDeep(value);
  }
  async function open(kind: typeof mode.value, row?: T) {
    if (!active) return;
    close(); mode.value = kind;
    if (kind === 'view') viewShow.value = true; else formShow.value = true;
    const token = ++epoch;
    title.value = `${kind === 'add' ? '新增' : kind === 'delete' ? '确认删除' : kind === 'disable' ? '确认停用' : kind === 'view' ? '查看' : '编辑'}${options.label}`;
    loading.value = true;
    try {
      if (kind !== 'add' && (!text(row?.id) || !text(row?.orgId))) throw Error('原记录编号或所属馆缺失，请重新读取列表');
      if (kind === 'disable' && !options.disable) throw Error('该类资料没有独立停用入口');
      const id = row?.id;
      const response = await options.context(id);
      if (!current(token)) return;
      const c = parse(body(response), id);
      if (row && row.orgId !== c.orgId) throw Error('资料所属馆已变化，请重新读取列表');
      title.value += ` · ${c.orgName}（${c.orgId}）`;
      if (kind === 'view') { viewRow.value = cloneDeep(c.record); return; }
      vo.value = c.record ? cloneDeep(c.record) : { ...cloneDeep(options.empty), orgId: c.orgId };
      if (!c.canOperate) { error.value = c.issue || '当前仅可查阅，不能办理'; return; }
      const model = vo.value;
      updateField.value = (field, value) => {
        if (current(token) && formShow.value && !submitting.value && !error.value && vo.value === model && (kind === 'add' || kind === 'edit') && field !== 'id' && field !== 'orgId') {
          model[field] = value as T[keyof T];
        }
      };
      handleSubmit.value = async () => {
        if (!current(token) || !formShow.value || vo.value !== model || model.id !== id || model.orgId !== c.orgId) throw Error('窗口已关闭、切换或来源变化，请重新核对');
        if (inFlight) throw Error('正在提交，请勿重复办理');
        inFlight = true; submitting.value = true;
        try {
          const response = kind === 'delete' ? await options.remove(id!, c.orgId, c.revision) : kind === 'disable' ? await options.disable!(id!, c.orgId, c.revision) : await options.save(cloneDeep(model), c.orgId, c.revision);
          const result = body(response);
          if (!text(result) || (id && result !== id)) throw Error('未收到有效记录编号，请重新读取核实办理结果');
          if (!current(token) || !formShow.value) throw Error('窗口已关闭或切换，请回原记录核实结果');
          close(); options.refresh(); return response;
        } catch (e) {
          // 失败后须重新读取；已交给表单校验器的旧回调也要失效。
          if (current(token)) { invalidate(); error.value = errorText(e, '办理失败，请重新读取'); }
          throw e;
        } finally { if (token === epoch) { inFlight = false; submitting.value = false; } }
      };
    } catch (e) { if (current(token)) error.value = errorText(e, '读取资料失败'); }
    finally { if (token === epoch) loading.value = false; }
  }
  return { formShow, viewShow, loading, submitting, error, title, vo, viewRow, mode, formKey, handleSubmit, updateField,
    onAdd: () => open('add'), onEdit: (row: T) => open('edit', row), onDelete: (row: T) => open('delete', row), onDisable: (row: T) => open('disable', row), onView: (row: T) => open('view', row) };
}
