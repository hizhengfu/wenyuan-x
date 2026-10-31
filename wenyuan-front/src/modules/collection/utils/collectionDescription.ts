import type { CollectionRegistrationDetailsVo } from '../typings/collection';
export const eraMeanings = [
  { value: 'MANUFACTURED', label: '制造年代' }, { value: 'USED', label: '使用年代' },
  { value: 'FORMED', label: '形成年代' }, { value: 'LIVED', label: '生存年代' },
];
export const eraNotations = [
  { value: 'GREGORIAN', label: '公历纪年' }, { value: 'GEOLOGICAL', label: '地质年代' },
  { value: 'ARCHAEOLOGICAL', label: '考古学年代' }, { value: 'CHINESE_HISTORY', label: '中国历史学年代' },
  { value: 'REGNAL', label: '帝王纪年' }, { value: 'OTHER', label: '其他纪年' },
];
export const materialCategories: Record<string, string[]> = {
  '单一质地': ['无机质/非金属材料', '无机质/金属材料', '有机质/植物质材料', '有机质/动物质材料', '有机质/其他有机质材料'],
  '复合或组合质地': ['无机复合或组合质地', '有机复合或组合质地', '有机无机复合或组合质地'],
};
export const materialSuggestions = ['瓷','陶','铜','金','银','铁','锡','铅','铝','玉','宝石','石','砖瓦','玻璃','珐琅','木','竹','漆','纸张','棉','麻','丝','毛','皮革','骨','角','牙','贝壳','树脂','橡胶','塑料','其他'];
export const eraMeaningLabel = (value?: string) => eraMeanings.find(x => x.value === value)?.label || '未记录';
export const eraNotationLabel = (value?: string) => eraNotations.find(x => x.value === value)?.label || '未记录';
export function eraText(value: { era?: string; registrationDetails?: CollectionRegistrationDetailsVo }) {
  return [value.registrationDetails?.eraMeaning && eraMeaningLabel(value.registrationDetails.eraMeaning),
    value.registrationDetails?.eraNotation && eraNotationLabel(value.registrationDetails.eraNotation), value.era].filter(Boolean).join(' · ') || '未记录';
}
