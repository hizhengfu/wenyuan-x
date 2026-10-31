import { useCurrentRecordPicker } from './useCurrentRecordPicker';
export type MasterPickerRow = { id?: string; orgId?: string; name?: string; enabled?: boolean };
type Props = { value?: string | string[] | null; multiple?: boolean; disabled?: boolean; onlyEnabled?: boolean; orgId?: string };
/** 基础资料的启用条件与名称校验；请求生命周期由通用记录选择器管理。 */
export function useMasterDataPicker<T extends MasterPickerRow>(props: Props, config: {
  page: (query: any) => Promise<any>; get: (id: string) => Promise<any>;
  query: () => Record<string, unknown>; matches: (row: T) => boolean; emit: (value: any) => void;
}) {
  return useCurrentRecordPicker<T>(props, {
    ...config,
    query: () => ({ ...config.query(), ...(props.onlyEnabled !== false ? { enabled: 'true' } : {}) }),
    validate: row => typeof row.name === 'string' && !!row.name.trim() && typeof row.enabled === 'boolean',
    label: row => row.name!,
    matches: row => (props.onlyEnabled === false || row.enabled === true) && config.matches(row),
  });
}
