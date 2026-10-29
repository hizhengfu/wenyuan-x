# AGENTS.md

本文件为 Codex（Codex.ai/code）提供操作本仓库的指导说明。

## 构建与运行

```bash
# 打包所有平台（生产环境，自动切换 active: prod）
./gradlew bootJar

# 打包特定平台
./gradlew :platform-fx:bootJar

# 运行测试
./gradlew test

# 构建所有模块（不打可执行包）
./gradlew build
```

运行特定平台时，在 IDE 中直接运行对应 `platform/{name}/src/main/kotlin/.../Application.kt` 的 `main` 函数。

**打包说明：** bootJar 自动排除 `*-dev.yml`、`*-prod.yml`、`sample.yml`、`templates/`，并将 `application.yml` 中的 `active: dev` 替换为 `active: prod`。

## 项目结构

`settings.gradle` 自动扫描根目录下所有含 `build.gradle` 的 `module/` 和 `platform/` 子目录，生成 Gradle 子项目，命名规则为 `:module-{name}` / `:platform-{name}`。

- **`module/`** — 共享库模块（不独立运行）：`build.gradle` 中必须设置 `jar { enabled = true }`
- **`platform/`** — 可独立部署的 Spring Boot 应用，通过 `implementation(project(":module-xxx"))` 引用模块
- 包名前缀统一：`io.hizhengfu.cloud`

现有模块：`ai`, `analyse`, `apidoc`, `archive`, `cms`, `common`, `control`, `corpus`, `data_center`, `dingtalk`, `flow`, `flowable`, `gis`, `jwt`, `knowledge`, `license`, `media`, `message`, `monitor`, `mybatis`, `oauth2`, `pay`, `print`, `quartz`, `resource`, `social`, `spider`, `sys`, `tools`, `video`, `visual`, `wx`

现有平台：`fx`, `corpus`, `flowable`, `auth-server`, `cms`, `visual`, `central_control` 等

所有版本号统一在根目录 `gradle.properties` 中管理。

## 技术栈

| 组件 | 版本/选型 |
|------|----------|
| Spring Boot | 4.0.2 |
| Kotlin | 2.2.21，目标 JVM 17 |
| ORM | Jetbrains Exposed 1.2.0（DSL 风格，**非 JPA**） |
| 缓存 | Caffeine 3.2.2（Spring Cache 抽象） |
| 数据库 | MySQL 9 / PostgreSQL / OpenGauss / SQLite（多数据库支持） |
| 连接池 | HikariCP 7.0.2 |
| JSON | Jackson 3.0.4 + Kotlin 模块 |
| 工作流 | 自研 `module/flow` + Flowable 7.0.1 |

## DAO 层表基类体系（Exposed ORM）

所有基类位于 `module/common/.../io/hizhengfu/cloud/autoconfigure/exposed/`：

| 基类 | 说明 |
|------|------|
| `UIDTable` | 最基础，仅 id 字段（雪花 ID）|
| `ContentAuditUIDTable` | UIDTable + status + 审计字段（createTime/createBy/updateTime/updateBy）|
| `TreeUIDTable` | UIDTable + parentId/parentIds/isLeaf（树形结构）|
| `ContentUIDTable` | UIDTable + title/content/lang/image 等富内容字段 + 审计字段 |
| `ContentAuditTreeUIDTable` | 内容 + 审计 + 树形 组合基类 |
| `FlowUIDTable` | UIDTable + flowStatus/flowInstanceId + 审计字段（集成工作流）|

**EntityHook 自动填充**：继承 `ContentUIDEntityClass` 时，`EntityHook` 会在 Created/Updated 时自动写入 `createBy`/`updateBy`/`updateTime`（从 `SecurityUtils.getUserId()` 获取），无需手动处理。`FlowUIDTable` 的审计字段需业务层手动写入。

```kotlin
// 表定义
object XxxTable : ContentAuditUIDTable("table_name") {
    val name = varchar("name", 100).comment("名称")
    val status = enumeration<XxxStatus>("status")
    val content = json("content", { JsonUtils.toJson(it) }, { JsonUtils.fromJson(it, Xxx::class.java) })
}

// 实体类
class XxxDao(id: EntityID<String>) : UIDEntity(id) {
    companion object : UIDEntityClass<XxxDao>(XxxTable)
    var name by XxxTable.name
}
```

- ID 由 `IdGenerate.nextId()` 生成（雪花算法，String 类型），**不使用数据库自增**
- 索引字段长度不超过 100 字符（多数据库兼容要求）

## Service 层基类体系

| 基类 | 用途 |
|------|------|
| `CrudService<T>` | 通用 CRUD，提供 findById/find/findList/findPage/new/delete/count 等 |
| `TreeCrudService<T>` | 继承 CrudService，额外提供 buildParentIds/findTree 等树操作 |
| `FlowService<T>` | 继承 CrudService，集成工作流（submit/saveDraft/cancel + 回调钩子）|

```kotlin
@Component
@Transactional
@CacheConfig(cacheNames = ["MODULE_NAME"], keyGenerator = "keyGenerator")
class XxxService(private val otherService: OtherService) : CrudService<XxxDao>(XxxDao, "MODULE_NAME") {

    @CacheEvict(allEntries = true)
    fun create(vo: XxxVo): String {
        val id = IdGenerate.nextId()
        val user = SecurityUtils.getUserDetail()
        // ...
        return id
    }

    @Cacheable
    fun findPage(page: GenericPage<XxxVo>): GenericPage<XxxVo> { ... }
}
```

- 获取当前用户：`SecurityUtils.getUserDetail()` 返回 `SecurityUser`（含 `.id`、`.name`、`.groupId`、`.groupIds`、`.roles` 等）
- 业务异常抛 `BadRequestException("message")`
- **`pageSize = 0` 时查询全部数据**（不分页，total 返回实际数量）

## Controller 层

```kotlin
@RestController
@RequestMapping("\${apiPath}/module/resource")
class XxxController(private val xxxService: XxxService) {

    @Log("创建XXX")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'XXX_CREATE')")
    @PostMapping
    fun create(@RequestBody @Validated vo: XxxVo) = ApiData(data = xxxService.create(vo))
}
```

- 响应类：`ApiData`（`io.hizhengfu.cloud.common.domain.ApiData`）
- 路径前缀使用配置项 `${apiPath}`（通常为 `/api`）
- 审计日志：`@Log("操作描述")`
- 权限注解：`@PreAuthorize("hasAnyAuthority('ADMIN', '...')")`
- 可实现 `CrudController<T>` 接口（定义 create/update/delete/get 四方法签名）

## 工作流模块（module/flow）

自研轻量级流程引擎，**非 Flowable**（Flowable 封装在 `module/flowable`）。

业务服务集成流程有两种模式：

**模式一（一体化）**：业务 Service 继承 `FlowService<E>`，同一个类处理业务逻辑和流程事件。

```kotlin
class PurchaseApplyService(
    flowInstanceService: FlowInstanceService
) : FlowService<PurchaseApplyDao>(PurchaseApplyDao, flowInstanceService) {

    override fun businessType() = "PURCHASE_APPLY"
    override fun definitionKey() = "purchase_apply"
    override fun buildTitle(entity: PurchaseApplyDao) = "${entity.applicantName}的采购申请"
    override fun buildFormData(entity: PurchaseApplyDao) = mapOf("amount" to entity.amount)

    // 可选覆写：流程完成/驳回/撤销回调
    override fun onFlowCompleted(entity: PurchaseApplyDao, event: FlowCompletedEvent) {
        super.onFlowCompleted(entity, event)  // 默认设为 PASS
        entity.approvedAt = LocalDateTime.now()
    }
}
```

**模式二（解耦）**：继承 `FlowBusinessHandler`，流程事件处理与业务服务分离。

业务表继承 `FlowUIDTable`，流程状态枚举为 `EnumFlowStatus`（DRAFT/AUDIT/PASS/REJECT/CANCEL）。

## 新模块结构

```
module/{name}/
├── build.gradle                          # 必须包含 jar { enabled = true }
└── src/main/kotlin/io/hizhengfu/cloud/{name}/
    ├── {Name}AutoConfiguration.kt
    ├── dao/
    ├── domain/                           # VO/DTO/枚举
    ├── service/
    └── api/
    resources/META-INF/spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

模块通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册自动配置类（Spring Boot 4.0+ 标准方式）。

## 多数据库支持

`ExposedAutoConfiguration` 注册了自定义方言：OpenGauss、DM（达梦，映射为 OracleDialect）、PostgreSQL（映射为 OpenGaussDialect）。当 JDBC 驱动无法自动识别数据库类型时，可在运行时调用 `REAL_DATABASE.set("opengauss")` 强制指定方言。

平台可通过 `DatabaseConfiguration.kt` 配置多数据源（最多 4 个）：`spring.datasource`（主）、`spring.second-datasource`、`spring.three-datasource`、`spring.four-datasource`，各自独立的 `SpringTransactionManager`（named "primary", "second", "three", "four"）。
