# collection 认证方式与 RFID 刷卡登录

`module-collection` 不依赖 `module-jwt` 或 `module-oauth2`。RFID 人员卡的校验、令牌签发和后续请求鉴权全部由 collection 完成，复用 `module-sys` 已有的 `JwtTokenUtil`、`SystemUserDetailsService` 以及公共 `SecurityUser`。

## 普通登录与刷卡登录

普通账号登录方式仍由平台选择：

```groovy
implementation(project(":module-collection"))
// implementation(project(":module-jwt2")) // 需要本地账号登录时按需引入
// implementation(project(":module-oauth2")) // 同时添加后自动启用混合认证，并需配置 SSO
```

认证能力由运行时实际依赖自动决定：仅 JWT2 提供本地登录，仅 OAuth2 提供 SSO，两者同时存在即混合认证。直接或传递依赖均可生效，collection 业务模块不强制引入二者；引入 OAuth2 时需提供认证中心配置。不要同时保留旧 `module-jwt` 依赖。迁移、密钥与双令牌契约见 `module/jwt2/doc/认证与迁移.md`。

藏品 App 继续复用 `${apiPath}/auth/login` 的账号、验证码和 MFA 流程，不增加专属登录接口。选择 OAuth2 或混合模式不影响 RFID 自有刷卡会话，刷卡无需调用认证中心，也无需平台再实现 RFID 签发器。

`CollectionRfidCardTokenIssuer` 位于 `module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/integration/`，随 collection 加载，不检查平台是否引入 JWT 或 OAuth2 模块。

## 刷卡认证过程

1. `RfidWorkstationService.cardLogin` 校验工作站、已登记浏览器凭证、人员卡状态与有效期、卡片能力和本地账号状态。
2. collection 的签发器核对用户当前的工作站权限和馆级写范围，签发短期 JWT。
3. 令牌使用 sys 已有的 `security.secret` 密钥能力，签名头带 `typ=collection-rfid+jwt`。接口仍返回 `token`、`expirationMillis`、用户和工作站信息，客户端仍使用 `Authorization: Bearer <token>`。
4. `RfidCardSecurityConfiguration` 优先接收带该标记的 API 请求，`RfidCardAuthenticationFilter` 验证完整签名、签发时间、过期时间、当前账号状态和密码重置时间，加载当前用户与权限。
5. 方法权限、馆别范围、工作站绑定、前台窗口租约及敏感操作二次认证继续由原有业务规则校验。

类型标记只用于选择过滤链，不是认证依据；被篡改、过期或缺少必要时间字段的令牌返回 401，不回退为其他用户身份。请求头中的显式操作员令牌优先于普通登录 Cookie。刷卡请求不读取或覆盖已有平台 Session。

刷卡令牌的有效期独立于普通登录默认值：

```yaml
collection:
  rfid:
    card-session-expiration-millis: 1800000 # 默认 30 分钟
```

刷卡过滤链覆盖这些令牌所访问的 `${apiPath}/**`，从而支持工作台的藏品业务、当前用户信息和菜单请求；具体功能权限仍来自本地用户。普通 JWT、OAuth2 令牌和其他平台登录请求继续交给平台安全链。没有其他安全链时，collection 的链兜底要求认证，避免其他请求意外匿名访问。

对外应用接口 `/app` 及其子路径使用 sys 提供的独立应用签名链，不读取用户 Bearer、Cookie 或 Session。JWT2、OAuth2、混合模式以及 collection 无用户登录模块时都保留原有应用验签。RFID 判断是否需要兜底保护时会排除这条仅覆盖应用路径的链，普通 API 不会因此变成匿名入口。

## 公开入口

collection 自行处理以下入口，不再依赖平台的 `ignorePath` 放行 RFID：

```text
${apiPath}/collection/rfid/public/**
${apiPath}/collection/rfid/runtime/**
```

这仅跳过用户登录认证。注册、刷卡和租约操作仍检查自己的工作站凭证；采集回调仍验证 `X-RFID-Ingest-Key`。公开入口忽略请求携带的失效刷卡令牌，以便重新刷卡登录。

保留现有前端刷卡接口和 Bearer 请求形式。带 `collection-rfid+jwt` 类型的 RFID 令牌继续由 collection 验证；旧通用 JWT 不会被 JWT2 兼容接受，迁移后需重新登录或重新刷卡。

## 验证

```bash
./gradlew :module-collection:test --tests 'io.hizhengfu.cloud.collection.rfid.*' \
  :module-jwt2:test :module-oauth2:test
```

模块测试类路径不包含 JWT/OAuth2 登录模块，覆盖真实刷卡 HTTP 请求、后续用户恢复、权限校验、令牌过期与篡改、账号停用和密码重置、Cookie/Session 身份隔离、无平台安全链时的鉴权兜底。JWT2 模块测试在不含 OAuth2 的真实类路径验证本地登录与刷卡共存；OAuth2 模块测试验证自动混合认证与刷卡共存、两种访问令牌、会话身份和普通 API 权限规则。

这些测试不代表真实 SSO 登录页面、现场 RFID 设备或部署环境已经联调。
