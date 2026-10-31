import type { FormRules } from 'naive-ui';
import type { CollectionCatalogContentVo } from '../typings/collection';
import { hasLocalDateValue } from '@/utils/datetime';
import { usesRelicRules } from './collectionNature';

export function requiresActualQuantity(content: CollectionCatalogContentVo): boolean {
  return content.unit?.trim() === '套' || (content.registrationDetails?.parts?.length || 0) > 1;
}

/** 草稿只核对已填的数量组合；提交时成套藏品须记录实际件数。 */
export function actualQuantityRules(content: CollectionCatalogContentVo, required = false): FormRules {
  return {
    actualQuantity: { required, trigger: ['change', 'blur'], validator: (_rule, value) =>
      (value == null ? !required && !content.actualQuantityUnit?.trim() : Number.isInteger(value) && value > 0) || new Error('请填写正整数实际数量') },
    actualQuantityUnit: { required: required || content.actualQuantity != null, trigger: ['input', 'blur'], validator: (_rule, value) =>
      (content.actualQuantity == null && !required && !value || typeof value === 'string' && !!value.trim() && value.trim() !== '套') || new Error('请填写件、只、册等实际数量单位，不能使用套') },
  };
}

/** 与后端编目策略一致：历史迁入补录、文物标准著录和共同登记字段分别校验。 */
export function catalogEntryRules(content: CollectionCatalogContentVo, historical = false): FormRules {
  const required = (message: string) => ({ required: true, message, trigger: ['change', 'blur', 'input'] });
  const rules: FormRules = {
    formalName: required('请输入正式定名'),
    categoryId: required('请选择藏品分类'),
    era: required('请输入具体年代'),
    texture: required('请选择或填写具体质地'),
    completeness: required('请选择完残程度'),
    source: required('请选择来源方式'),
    acquireDate: { trigger: ['change', 'blur', 'input'], validator: () =>
      Boolean(hasLocalDateValue(content.acquireDate) || content.acquireDateRange?.trim()) || new Error('请选择取得日期或填写经核实的时间范围') },
    ...actualQuantityRules(content, !historical && requiresActualQuantity(content)),
  };
  if (usesRelicRules(content.nature) || !content.nature) rules.level = required('请选择文物等级');
  if (historical) {
    delete rules.era; delete rules.texture; delete rules.completeness;
  }
  if (!historical) {
    rules.nature = required('请依据鉴定和行政材料核实藏品性质');
    rules.quantity = { ...required('请输入登记件套数'), type: 'number' };
    rules.unit = required('请输入登记数量单位');
    rules.classificationNo = required('请输入分类号');
    rules.entryDate = { ...required('请选择实收入馆日期'), type: 'number' };
    rules.preserveState = required('请选择保存状态');
    rules.completeStatus = required('请描述完残状况');
    rules.feature = required('请填写基本特征');
    rules.warehouseId = required('请选择存放位置');
    rules.coverImage = required('请上传正面照片');
    if (content.nature !== 'OTHER') {
      rules['registrationDetails.eraMeaning'] = required('请选择年代类型');
      rules['registrationDetails.eraNotation'] = required('请选择年代表示方式');
      rules.textureCategory1 = required('请选择质地类别');
      rules.textureCategory2 = required('请选择质地细类');
    }
  }
  return rules;
}
