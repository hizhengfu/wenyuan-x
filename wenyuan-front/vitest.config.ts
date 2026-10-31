import { fileURLToPath } from 'node:url';
import vue from '@vitejs/plugin-vue';
import vueJsx from '@vitejs/plugin-vue-jsx';
import { templateCompilerOptions } from '@tresjs/core';
import { VantResolver } from '@vant/auto-import-resolver';
import Icons from 'unplugin-icons/vite';
import IconsResolver from 'unplugin-icons/resolver';
import Components from 'unplugin-vue-components/vite';
import { NaiveUiResolver, TDesignResolver } from 'unplugin-vue-components/resolvers';
import { defineConfig } from 'vitest/config';

const fromProjectRoot = (path: string) => fileURLToPath(new URL(path, import.meta.url));
const unitTestPatterns = ['src/**/*.test.ts', 'tests/**/*.test.ts'];
const domTestPatterns = ['**/wwyzt/**/*.test.ts'];

export default defineConfig({
  define: {
    PROJECT_BUILD_TIME: JSON.stringify('test'),
    APP_BUILD_TIME: JSON.stringify('test'),
    APP_TARGET: JSON.stringify('test'),
    APP_TITLE: JSON.stringify('test'),
    APP_ADMIN_THEME: JSON.stringify('auto'),
    APP_ABBR_TITLE: JSON.stringify('test'),
    APP_DESCRIPTION: JSON.stringify('test'),
    APP_COPYRIGHT: JSON.stringify('test'),
    APP_MOBILE_UI: false,
    APP_HOME_URL: JSON.stringify('.'),
    APP_BASE_URL: JSON.stringify('.'),
    APP_API_PATH: JSON.stringify('/api'),
    APP_API_FRONT_PATH: JSON.stringify('/api/front'),
    APP_API_PROXY: false,
    ENABLE_HKPLAYER: false,
    ENABLE_MARS3D: false,
    ENABLE_CESIUM: false,
    ENABLE_DRACO: false,
    ENABLE_MARMOSET: false,
    ENABLE_UPDATE_VERSION: false,
  },
  plugins: [
    vue({ ...templateCompilerOptions }),
    vueJsx(),
    Icons({ compiler: 'vue3' }),
    Components({
      dts: false,
      resolvers: [
        IconsResolver({ prefix: 'icon' }),
        TDesignResolver({ library: 'mobile-vue' }),
        NaiveUiResolver(),
        VantResolver(),
      ],
    }),
  ],
  resolve: {
    alias: [
      { find: '@', replacement: fromProjectRoot('./src') },
      { find: '~', replacement: fromProjectRoot('./') },
    ],
  },
  test: {
    setupFiles: [fromProjectRoot('./tests/setup/auth.ts')],
    projects: [
      {
        extends: true,
        test: {
          name: 'unit',
          environment: 'node',
          globals: false,
          include: unitTestPatterns,
          exclude: domTestPatterns,
        },
      },
      {
        extends: true,
        test: {
          name: 'unit-dom',
          environment: 'happy-dom',
          globals: false,
          include: domTestPatterns,
        },
      },
    ],
  },
});
