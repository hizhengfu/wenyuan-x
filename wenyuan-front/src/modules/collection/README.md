# collection —— 藏品管理（馆藏可移动文物）

中国藏品账目化全生命周期管理前端模块，支持单馆与多馆。
设计基线：后端仓库 `docs/superpowers/specs/2026-06-21-collection-management-design.md`。

## 目录结构

```
collection/
├── api/
│   ├── collection.ts   # 藏品档案（总账/分类账/一物一档）
│   ├── org.ts          # 收藏单位（多馆）
│   ├── category.ts     # 文物分类（树）
│   ├── movement.ts     # 出入库流水
│   ├── exhibition.ts   # 藏品展览
│   ├── restorationMethod.ts # 修复方法库
│   └── requisition.ts  # 提用单
├── typings/collection.ts
├── components/CollectionFormModal.vue   # 藏品登记/编辑表单
├── views/collection/
│   ├── default.vue     # /collection         藏品工作台
│   ├── ledger.vue      # /collection/ledger  藏品台账
│   ├── org.vue         # /collection/org      收藏单位
│   └── category.vue    # /collection/category 文物分类
└── assets/icon/        # m:* 自定义菜单图标（collection / ledger / org / category / warehouse 等）
```

## 启用方式（关键）

模块按平台引入。要在某平台启用：

1. 在 `config/config-private.js` 中目标平台的 `dependencies` 数组里加入 `'collection'`。
2. 运行 `pnpm route`（或 `pnpm routeAll`）重新生成路由。
3. 菜单 / 标题来自后端 `CollectionInstall.initMenus()`（视图本身不声明 meta，与项目其它模块一致）。

## 后端接口契约（路径前缀 `${apiPath}/collection`）

| 前端 url | 方法 | 后端 |
|----------|------|------|
| `/collection/page` | GET | 分页（Service 按可见收藏单位过滤） |
| `/collection/{id}` | GET/PUT | 详情 / 编辑非关键字段 |
| `/collection` | POST | 登记 |
| `/collection/{id}/catalog-audit` | POST | 编目审核（通过入库 / 驳回待修改） |
| `/collection/{id}/correct` | POST | 关键字段更正（留痕） |
| `/collection/{id}/ledger-logs` | GET | 总账留痕 |
| `/collection/ledger-logs/page` | GET | 资料登记账 / 全局总账留痕 |
| `/collection/import/template` | GET | 批量导入模板 |
| `/collection/import/validate` | POST | 批量导入预校验 |
| `/collection/import/commit` | POST | 批量导入提交 |
| `/collection/{id}/media` | GET/POST | 藏品影像 |
| `/collection/{id}/attachment` | GET/POST | 藏品附件 |
| `/collection/{id}/set` | GET/POST | 套件/附件/分件关系 |
| `/collection/deregistration` | POST | 注销申请（逐级报批） |
| `/collection/{id}/publish-cms` | POST | 单向发布数字藏品 |
| `/collection/movement/collection/{id}` | GET | 藏品出入库流水 |
| `/collection/movement/page` | GET | 出入库流水全局查询 |
| `/collection/movement/{id}/print` | GET | 出入库规范打印数据 |
| `/collection/exhibition/**` | CRUD | 藏品展览项目 |
| `/collection/restoration-method/**` | CRUD | 修复方法库 |
| `/collection/requisition/{id}/print` | GET | 提用单规范打印数据 |
| `/collection/acquisition/{id}/print` | GET | 征集入藏审批登记规范打印数据 |
| `/collection/appraisal/{id}/print` | GET | 鉴定定级规范打印数据 |
| `/collection/restoration/{id}/print` | GET | 保护修复记录规范打印数据 |
| `/collection/reproduction/{id}/print` | GET | 复制拓印影像采集规范打印数据 |
| `/collection/loan/{id}/print` | GET | 借展出馆交接规范打印数据 |
| `/collection/transfer/{id}/print` | GET | 跨馆调拨审批交接规范打印数据 |
| `/collection/deregistration/{id}/print` | GET | 注销审批规范打印数据 |
| `/collection/inventory/{id}/print` | GET | 盘点清查规范打印数据 |
| `/collection/inventory/import/template` | GET | 盘点批量导入模板 |
| `/collection/inventory/import/validate` | POST | 盘点批量导入预校验 |
| `/collection/inventory/import/commit` | POST | 盘点批量导入提交 |
| `/collection/org/**` | CRUD | 收藏单位 |
| `/collection/category/**` | CRUD | 文物分类 |

> 红线：藏品总账**不提供物理删除**，台账 `is-delete=false`；总登记号编辑态只读，更正须走 `correct`。

## 字典契约（后端 `CollectionInstall.initDicts()` 必须登记同名 type）

| dict type | 含义 |
|-----------|------|
| `col_relic_level` | 藏品级别（一/二/三级·一般·未定级） |
| `col_source` | 来源方式（发掘/调拨/捐赠/收购…） |
| `col_texture` | 质地 |
| `col_completeness` | 完残程度 |
| `col_preserve` | 保存状态 |
| `col_life_status` | 藏品业务状态 |
| `col_catalog_status` | 编目审核状态 |
| `col_set_relation` | 套件关系（主件/附件/分件） |
| `col_media_type` | 藏品影像类型 |
| `col_attachment_type` | 藏品附件类型 |
| `col_print_type` | 规范打印单据类型 |
| `col_org_level` | 收藏单位级别 |
| `col_ownership` | 举办主体（国有/非国有） |
| `col_warehouse_type` | 库位类型（M2） |
| `col_dereg_reason` | 注销原因（M3） |

## 里程碑

当前已覆盖 **M1-M3 的基础闭环**：藏品台账 + 编目审核 + 资料登记账 + 收藏单位 + 文物分类 + 库房库位 + 套件/分件 + 提用/出入库 + 出入库流水 + 藏品展览 + 修复方法库 + 影像附件 + Excel/CSV 批量导入 + 规范打印数据预览 + 生命周期流程页。
后续：根据平台接入情况完善打印模板排版输出、多馆跨馆调拨审批细节、统计上报导出。
