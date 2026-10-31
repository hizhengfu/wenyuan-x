# 文渊前端

从 `web-manage-v3` 本地工作目录复制，沿用 Vue 3、Vite、Pinia、pnpm 的项目结构。

## 目录

- `src/modules/`：源项目全部 42 个公共业务模块。
- `src/platforms/asa_wms/`：考古工地文物库房管理系统。
- `src/platforms/qxslib/`：钱学森图书馆智慧现场服务监控管理系统，包含完整全景切片及静态资源。
- `src/components/`、`src/assets/`、`src/store/`、`src/router/`、`src/plugins/`、`src/utils/` 等：公共前端基础代码。
- `config/`：两个平台的配置与入口选择。
- `scripts/`：路由生成、类型检查范围生成及测试脚本。
- `resource/`、`public/`：公共静态资源与第三方运行时。
- `tests/`：源项目测试文件，部分用例依赖本次未迁移的平台。

复制包含源目录中的本地改动和被 Git 忽略的平台文件；不包含源项目的 Git 历史、依赖安装目录、构建产物及编辑器缓存。

## 安装与运行

使用 pnpm，建议 Node.js 22.12 及以上版本。

```bash
pnpm install --frozen-lockfile

# 默认平台：asa_wms，开发端口：3201（占用时自动顺延）
pnpm dev
pnpm dev:asa-wms
pnpm dev:qxslib

# 为当前平台或全部两个平台生成路由
pnpm route
pnpm routeAll

# 类型检查通过后执行生产构建
pnpm build:asa-wms
pnpm build:qxslib
```

也可以通过 `WEB_PLATFORM=asa_wms` 或 `WEB_PLATFORM=qxslib` 选择平台；兼容源配置名 `asawms`。
输出目录分别为 `dist/asa_wms`、`dist/qxslib`。

两个平台的后端地址、API 前缀和模块依赖保留源配置，集中在 `config/config.js` 中。接入本地后端时在此调整 `dev_url`。
`views/route.ts` 和 `tsconfig.private.json` 均由 `pnpm route` 自动生成，不应手动维护。

## 模块边界

当前 `asa_wms` 启用 `sys`、`message`，`qxslib` 启用 `sys`、`module`；错误页由路由脚本统一加入。
其余模块按原内容保留。源项目中的 `archive`、`knowledge` 引用了 `jiangxi` 平台文件，`enforcement`、`protection` 引用了 `yunyoujx` 平台文件；这两个额外平台未迁移，后续启用这些模块时需一并整理相关依赖。

原测试集同样包含其他平台的用例，因此完整 `pnpm test` 不代表仅验证这两个平台；可按文件选择运行对应测试。
