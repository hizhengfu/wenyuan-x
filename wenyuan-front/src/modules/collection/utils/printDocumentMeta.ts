export interface CollectionPrintMetaInput {
  type?: string | null;
  title?: string | null;
  template?: Partial<CollectionPrintMeta> | null;
}

export interface CollectionPrintMeta {
  title: string;
  documentLabel: string;
  collectionTitle: string;
  signatures: string[];
}

const defaultMeta: CollectionPrintMeta = {
  title: '藏品业务单据',
  documentLabel: '单号',
  collectionTitle: '藏品明细',
  signatures: ['经办人', '复核人', '保管员', '负责人', '日期'],
};

export const COLLECTION_ARCHIVE_PRINT_TYPE = 'COLLECTION_ARCHIVE';

const printTypeMeta: Record<string, CollectionPrintMeta> = {
  [COLLECTION_ARCHIVE_PRINT_TYPE]: {
    title: '藏品登记表',
    documentLabel: '总登记号',
    collectionTitle: '藏品信息',
    signatures: ['登记人', '复核人', '保管员', '负责人', '日期'],
  },
  MOVEMENT: {
    title: '藏品出入库凭证',
    documentLabel: '出入库单号',
    collectionTitle: '出入库藏品清单',
    signatures: ['经办人', '复核人', '保管员', '交接人', '日期'],
  },
  REQUISITION: {
    title: '藏品提用审批交接单',
    documentLabel: '提用单号',
    collectionTitle: '提用藏品清单',
    signatures: ['申请部门', '经办人', '复核人', '保管员', '日期'],
  },
  LOAN_HANDOVER: {
    title: '藏品借展出馆交接单',
    documentLabel: '协议/批文号',
    collectionTitle: '借展藏品清单',
    signatures: ['借出单位', '借入单位', '经办人', '复核人', '日期'],
  },
  DEREGISTRATION: {
    title: '藏品退出馆藏审批表',
    documentLabel: '审批批文号',
    collectionTitle: '退出馆藏藏品清单',
    signatures: ['申请人', '审核人', '批准人', '经办人', '日期'],
  },
  INVENTORY: {
    title: '藏品盘点清查记录表',
    documentLabel: '盘点批次/单号',
    collectionTitle: '盘点藏品清单',
    signatures: ['盘点人', '复核人', '保管员', '负责人', '日期'],
  },
  APPRAISAL: {
    title: '藏品鉴定定级表',
    documentLabel: '鉴定记录号',
    collectionTitle: '鉴定藏品信息',
    signatures: ['鉴定委员', '复核人', '保管员', '负责人', '日期'],
  },
  TRANSFER: {
    title: '藏品跨馆调拨审批交接表',
    documentLabel: '调拨批文号',
    collectionTitle: '调拨藏品清单',
    signatures: ['调出单位', '调入单位', '经办人', '复核人', '日期'],
  },
  RESTORATION: {
    title: '藏品保护修复记录表',
    documentLabel: '修复记录号',
    collectionTitle: '修复藏品信息',
    signatures: ['修复人员', '复核人', '保管员', '负责人', '日期'],
  },
  ACQUISITION: {
    title: '藏品征集入藏审批登记表',
    documentLabel: '征集申请号',
    collectionTitle: '入藏藏品信息',
    signatures: ['申请人', '评估人', '审核人', '批准人', '日期'],
  },
  REPRODUCTION: {
    title: '藏品复制拓印影像采集记录表',
    documentLabel: '复制拓印记录号',
    collectionTitle: '复制拓印藏品信息',
    signatures: ['采集人', '复核人', '保管员', '负责人', '日期'],
  },
  EXHIBITION: {
    title: '藏品展览记录表',
    documentLabel: '展览编号',
    collectionTitle: '参展藏品清单',
    signatures: ['策展人', '保管员', '复核人', '负责人', '日期'],
  },
};

function movementCollectionTitle(title: string, fallback: string) {
  if (title.includes('入库')) return '入库藏品清单';
  if (title.includes('出库')) return '出库藏品清单';
  return fallback;
}

export function resolveCollectionPrintMeta(data: CollectionPrintMetaInput): CollectionPrintMeta {
  const backendTemplate = data.template || null;
  const base = backendTemplate
    ? { ...defaultMeta, ...backendTemplate }
    : data.type
      ? printTypeMeta[data.type] || defaultMeta
      : defaultMeta;
  const title = String(backendTemplate?.title || data.title || '').trim() || base.title;
  return {
    ...base,
    title,
    collectionTitle: backendTemplate?.collectionTitle
      || (data.type === 'MOVEMENT' ? movementCollectionTitle(title, base.collectionTitle) : base.collectionTitle),
    signatures: backendTemplate?.signatures?.length ? backendTemplate.signatures : base.signatures,
  };
}
