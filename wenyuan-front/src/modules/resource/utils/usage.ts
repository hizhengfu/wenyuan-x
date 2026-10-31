export const USAGE_ACTIONS = [
  { label: '检索', value: 'SEARCH' },
  { label: '浏览', value: 'VIEW' },
  { label: '预览', value: 'PREVIEW' },
  { label: '下载', value: 'DOWNLOAD' },
  { label: '导出', value: 'EXPORT' },
  { label: '接口调用', value: 'API_CALL' },
];
export const USAGE_SCENES = [
  { label: '学术研究', value: 'ACADEMIC_RESEARCH' },
  { label: '展览展示', value: 'EXHIBITION' },
  { label: '公众教育', value: 'PUBLIC_EDUCATION' },
  { label: '保护修复', value: 'CONSERVATION_REPAIR' },
  { label: '其他', value: 'OTHER' },
];
export function usageActionLabel(value?: string) {
  return USAGE_ACTIONS.find((item) => item.value === value)?.label || value || '--';
}
export function usageSceneLabel(value?: string) {
  return USAGE_SCENES.find((item) => item.value === value)?.label || value || '--';
}
export function usageResourceLabel(row: { resourceId?: string; resourceName?: string }) {
  return row.resourceName || (row.resourceId ? '资源已删除或无权查看' : '--');
}
