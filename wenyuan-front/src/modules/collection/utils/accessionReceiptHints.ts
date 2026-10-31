import type { CollectionCatalogVo } from '../typings/collection';

/** 台账新增、复制新建、导入登记表保存成功后的提示。 */
export const REGISTRATION_RECEIPT_HINT = '正式登账需先完成入馆凭证逐件核收；历史藏品请用迁移补录';

/**
 * Word 导入提交成功后是否弹登账提示：仅登记表且确实新建了藏品（createdCollection === true）。
 * 选「更正已有藏品」只更新（updatedCollection）、或动态跟踪记录导入时都不提示。
 */
export function wordImportCreatedRegistration(
  documentType: string | undefined,
  data?: { createdCollection?: unknown } | null,
) {
  return documentType === 'REGISTRATION' && data?.createdCollection === true;
}

/** 库房/保管员字段标签旁的问号提示：库房与保管员在入馆核收时确定，没有独立的藏品入库步骤。 */
export const WAREHOUSE_FIXED_AT_RECEIPT_HINT = '库房与保管员已在核收时确定，无需另行办理藏品入库';

/** 编目审核通过被入馆凭证逐件核收前置条件拦截时的提示。 */
export const CATALOG_RECEIPT_REQUIRED_MESSAGE =
  '该藏品没有入馆凭证的逐件核收记录，无法审核通过；历史藏品请走「已登账藏品迁移」补录';

/** 后端 requireReceived 返回的 400 文案片段。 */
const BACKEND_RECEIPT_MARKERS = ['逐件核收及入馆凭证原件', '历史纸质资料请按补录程序核实'];

/**
 * 仅在能从已有字段确定时返回 true：非迁移藏品，且编目内容明确带有 receiptCompletedAt 字段并为空。
 * 字段缺失（接口未下发）时无法判断，不拦截，交由后端校验。
 */
export function catalogLacksAccessionReceipt(catalog?: CollectionCatalogVo | null) {
  if (!catalog || catalog.historicalMigration || catalog.migrationVerificationRequired) return false;
  const content = catalog.content as Record<string, unknown> | undefined;
  if (!content || !Object.prototype.hasOwnProperty.call(content, 'receiptCompletedAt')) return false;
  return (
    content.receiptCompletedAt === null || content.receiptCompletedAt === undefined || content.receiptCompletedAt === ''
  );
}

export function requestErrorMessage(error: any) {
  return String(error?.response?.data?.message || error?.message || '');
}

export function isAccessionReceiptRequiredError(error: any) {
  const text = requestErrorMessage(error);
  return BACKEND_RECEIPT_MARKERS.some((marker) => text.includes(marker));
}
