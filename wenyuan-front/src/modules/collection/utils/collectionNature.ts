export type CollectionNature = 'RELIC' | 'OTHER' | 'SCIENTIFIC_FOSSIL';
export const collectionNatureOptions = [
  { value: 'RELIC', label: '文物藏品' },
  { value: 'SCIENTIFIC_FOSSIL', label: '科学价值古脊椎动物或古人类化石（按文物规定管理）' },
  { value: 'OTHER', label: '其他藏品（不含上述两类）' },
];
export const collectionNatureHint =
  '依据真实鉴定和行政材料核实第61条类别；不能将所有化石或自然资源重点保护等级直接认作本类别、文物等级。已正式登记的性质更正须办理信息变更。';
export function usesRelicRules(nature: unknown): boolean {
  return nature === 'RELIC' || nature === 'SCIENTIFIC_FOSSIL';
}
export function collectionNatureLabel(nature: unknown): string {
  return collectionNatureOptions.find((x) => x.value === nature)?.label ?? '待核实';
}
