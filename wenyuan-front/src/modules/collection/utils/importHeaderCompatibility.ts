// 旧模板仅作为兼容输入，新模板继续由后端返回名称字段。
const legacyHeaders = {
  "acquisition": {
    "categoryId": "categoryId",
    "文物分类ID": "categoryId",
    "images": "images",
    "附件/影像 fileId": "images",
    "expertOpinionAttachmentIds": "expertOpinionAttachmentIds",
    "专家鉴定意见表附件 fileId": "expertOpinionAttachmentIds",
    "approvalAttachmentIds": "approvalAttachmentIds",
    "征集审批表/清单附件 fileId": "approvalAttachmentIds",
    "contractAttachmentIds": "contractAttachmentIds",
    "征集合同附件 fileId": "contractAttachmentIds",
    "invoiceAttachmentIds": "invoiceAttachmentIds",
    "发票附件 fileId": "invoiceAttachmentIds"
  },
  "warehouse": {
    "parentId": "parentId",
    "上级库位ID": "parentId",
    "thumbnail": "thumbnail",
    "缩略图 fileId/路径": "thumbnail"
  },
  "org": {
    "parentId": "parentId",
    "上级单位ID": "parentId",
    "areaId": "areaId",
    "行政区划ID": "areaId",
    "locationRootId": "locationRootId",
    "关联安装位置根ID": "locationRootId"
  },
  "inventory": {
    "attachmentIds": "attachmentIds",
    "附件": "attachmentIds"
  }
} as const;
export function applyLegacyImportHeaders(kind: keyof typeof legacyHeaders, headers: Map<string, string>) {
  Object.entries(legacyHeaders[kind]).forEach(([header, field]) => headers.set(header, field));
}
