/**
 * 文物修复模块各页面写操作按钮的权限码，一律以后端为准（反编译 module-collection-restoration）：
 * - 各 Controller 写接口（新增 / 修改 / 删除 / 停用等）@PreAuthorize("hasAnyAuthority(...)") 里列出的码；
 * - 服务层 RestorationComplianceAccess.actor() 会把业务码再并上 ADMIN、COLLECTION_ADMIN、RESTORATION_ADMIN，
 *   与 @PreAuthorize 里的 3 个 ADMIN 一致。
 * 前端统一「业务码在前、3 个 ADMIN 在后」，改动时只改这里；页面里用 *_CODES 做 auth.hasPermission / v-permission，
 * 用 *_PERMISSION（逗号分隔）传 HPageDataTable 的 add-permission / edit-permission。
 *
 * 注意：HPageDataTable 未传 permission 时 judgePermissionByCode 恒为 true，单传 add/edit-permission 拦不住，
 * 页面必须同时用 :is-add / :is-edit 绑定 auth.hasPermission(对应码)。
 */

/** actor() 恒并入的 3 个管理员码。 */
export const RESTORATION_ADMIN_CODES = Object.freeze(['ADMIN', 'COLLECTION_ADMIN', 'RESTORATION_ADMIN'] as const);

const withAdmins = <T extends string>(...codes: T[]) =>
  Object.freeze([...codes, ...RESTORATION_ADMIN_CODES] as const);

/**
 * 工序 / 修复日志写、删按钮的权限码：
 * RestorationProcessController.save / reorder / delete 的 @PreAuthorize 与 RestorationComplianceAccess.actor() 都认这 5 个码。
 * 工序页（views/process.vue）表格的新增 / 编辑 / 删除和修复日志页签（RestorationProcessLog.vue）共用。
 */
export const RESTORATION_PROCESS_WRITE_CODES = withAdmins('RESTORATION_PROCESS_MANAGE', 'COLLECTION_RESTORATION_PROCESS');
/** 逗号分隔形式，供 HPageDataTable 的 add-permission / edit-permission 等字符串属性使用。 */
export const RESTORATION_PROCESS_WRITE_PERMISSION = RESTORATION_PROCESS_WRITE_CODES.join(',');

/**
 * 修复项目（views/project.vue）新增：RestorationController.create / submit / cancel（及 start / complete 等）
 * 的 @PreAuthorize；actor() 未传 requiredPermissions 时写操作默认码也是 RESTORATION_PROJECT_MANAGE + COLLECTION_STORAGE。
 */
export const RESTORATION_PROJECT_WRITE_CODES = withAdmins('RESTORATION_PROJECT_MANAGE', 'COLLECTION_STORAGE');
export const RESTORATION_PROJECT_WRITE_PERMISSION = RESTORATION_PROJECT_WRITE_CODES.join(',');

/**
 * 修复合同（views/contract.vue）新增 / 编辑 / 删除：RestorationContractController.save / deleteById / deleteByRestoration；
 * RestorationContractService.writePermissions 同为 RESTORATION_CONTRACT_MANAGE、COLLECTION_RESTORATION_CONTRACT。
 */
export const RESTORATION_CONTRACT_WRITE_CODES = withAdmins('RESTORATION_CONTRACT_MANAGE', 'COLLECTION_RESTORATION_CONTRACT');
export const RESTORATION_CONTRACT_WRITE_PERMISSION = RESTORATION_CONTRACT_WRITE_CODES.join(',');

/**
 * 修复档案（views/archive.vue）与病害记录（views/damage.vue）新增 / 编辑 / 删除：
 * RestorationArchiveController.save / delete / approve / saveDamage / deleteDamage；
 * RestorationArchiveService.writePermissions 同为 RESTORATION_ARCHIVE、COLLECTION_RESTORATION_ARCHIVE。
 */
export const RESTORATION_ARCHIVE_WRITE_CODES = withAdmins('RESTORATION_ARCHIVE', 'COLLECTION_RESTORATION_ARCHIVE');
export const RESTORATION_ARCHIVE_WRITE_PERMISSION = RESTORATION_ARCHIVE_WRITE_CODES.join(',');

/**
 * 修复验收（views/acceptance.vue）新增 / 编辑（打开验收归档抽屉登记验收 / 备案）：
 * RestorationCloseoutController.acceptance / filing 与 RestorationArchiveController.saveAcceptance / deleteAcceptance；
 * RestorationCloseoutService.acceptancePermissions 同为 RESTORATION_ACCEPTANCE、COLLECTION_RESTORATION_ACCEPTANCE。
 */
export const RESTORATION_ACCEPTANCE_WRITE_CODES = withAdmins('RESTORATION_ACCEPTANCE', 'COLLECTION_RESTORATION_ACCEPTANCE');
export const RESTORATION_ACCEPTANCE_WRITE_PERMISSION = RESTORATION_ACCEPTANCE_WRITE_CODES.join(',');

/**
 * 修复方法（views/method.vue）新增 / 编辑 / 删除：RestorationMethodController.create / update / delete；
 * RestorationMethodService 的 RestorationMasterDataAccess 写码同为 RESTORATION_METHOD_MANAGE、COLLECTION_STORAGE。
 */
export const RESTORATION_METHOD_WRITE_CODES = withAdmins('RESTORATION_METHOD_MANAGE', 'COLLECTION_STORAGE');
export const RESTORATION_METHOD_WRITE_PERMISSION = RESTORATION_METHOD_WRITE_CODES.join(',');

/**
 * 修复机构（views/org.vue）新增 / 编辑 / 停用 / 删除：RestorationOrgController.save / disable / delete；
 * RestorationOrgService 的 RestorationMasterDataAccess 写码同为 RESTORATION_ORG_MANAGE。
 */
export const RESTORATION_ORG_WRITE_CODES = withAdmins('RESTORATION_ORG_MANAGE');
export const RESTORATION_ORG_WRITE_PERMISSION = RESTORATION_ORG_WRITE_CODES.join(',');

/**
 * 修复人员（views/restorer.vue）新增 / 编辑 / 停用：RestorerController.save / disable；
 * RestorerService 的 RestorationMasterDataAccess 写码同为 RESTORATION_RESTORER_MANAGE、COLLECTION_RESTORER_MANAGE。
 */
export const RESTORER_WRITE_CODES = withAdmins('RESTORATION_RESTORER_MANAGE', 'COLLECTION_RESTORER_MANAGE');
export const RESTORER_WRITE_PERMISSION = RESTORER_WRITE_CODES.join(',');
/** 修复人员删除：RestorerController.delete 的 @PreAuthorize 不含 COLLECTION_RESTORER_MANAGE，比保存 / 停用少一个码。 */
export const RESTORER_DELETE_CODES = withAdmins('RESTORATION_RESTORER_MANAGE');

/**
 * 资质证书（views/qualification-cert.vue）新增 / 编辑 / 删除：QualificationCertController.save / delete；
 * QualificationCertService 的 RestorationMasterDataAccess 写码同为 RESTORATION_RESTORER_MANAGE、COLLECTION_RESTORER_MANAGE。
 */
export const QUALIFICATION_CERT_WRITE_CODES = withAdmins('RESTORATION_RESTORER_MANAGE', 'COLLECTION_RESTORER_MANAGE');
export const QUALIFICATION_CERT_WRITE_PERMISSION = QUALIFICATION_CERT_WRITE_CODES.join(',');

/**
 * 藏品打印预览 / 下载（CollectionPrintController.render.pdf / render.docx 等）：
 * 后端仅认 ADMIN、COLLECTION_SELECT。修复项目 / 合同 / 档案的 print 数据接口权限更宽，
 * 但前端预览最终走 render.pdf，无此码会 403，故打印按钮按本码隐藏。
 */
export const COLLECTION_PRINT_RENDER_CODES = Object.freeze(['ADMIN', 'COLLECTION_SELECT'] as const);
export const COLLECTION_PRINT_RENDER_PERMISSION = COLLECTION_PRINT_RENDER_CODES.join(',');
