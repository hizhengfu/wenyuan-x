import { ajaxGetDownload } from '@/utils/download';

/**
 * 下载库存操作单据（docx）
 *
 * 后端会根据 record.type / outPurpose 自动选择标题：
 *  - 入库 → 入库单
 *  - 出库 + outPurpose=T6(借展) → 借调申请单
 *  - 出库其他 → 出库单
 *  - 移库 → 移库单
 *  - 归库 → 归库单
 *
 * @param recordId  WmsOperateRecord.id
 * @param fileName  下载文件名，默认 "库存操作单.docx"（实际由 Content-Disposition 覆盖时使用真实名）
 */
export function downloadOperateDocx(recordId: string, fileName = '库存操作单.docx') {
  return ajaxGetDownload(`wms/document/operate/${recordId}.docx`, fileName);
}
