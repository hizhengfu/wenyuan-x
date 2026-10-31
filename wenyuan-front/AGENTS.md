# 前端开发约定

- 只使用 pnpm 管理依赖与执行脚本。
- Vue 3 + Vite 多平台结构，公共业务模块位于 `src/modules/`，平台位于 `src/platforms/`。
- 本项目平台为 `asa_wms` 和 `qxslib`，默认 `asa_wms`；通过 `WEB_PLATFORM` 选择，配置见 `config/config.js`。
- 页面路由与 `tsconfig.private.json` 由 `pnpm route` 生成；不要手动编辑 `views/route.ts`。
- 新增页面放在对应平台或模块的 `views/` 下，再生成路由。
- `@/` 指向 `src/`，`~/` 指向项目根目录。
- `pnpm build` 依次生成当前平台路由、运行类型检查、执行生产构建。
- 保留用户已有改动。迁移来源仅用于参考，不要修改源项目。
