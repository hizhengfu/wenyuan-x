import { defineConfig, normalizePath } from 'vite';
import vue from '@vitejs/plugin-vue';
import { createHtmlPlugin } from 'vite-plugin-html'; // html插件(使用变量、压缩)
import vueJsx from '@vitejs/plugin-vue-jsx';
import { resolve } from 'path';
import { existsSync, readFileSync, writeFileSync } from 'fs';
// @ts-ignore
import dayjs from 'dayjs';
import PlatformConfig from './config';
import Components from 'unplugin-vue-components/vite';
import Inspect from 'vite-plugin-inspect';
import { NaiveUiResolver, TDesignResolver } from 'unplugin-vue-components/resolvers';
import { visualizer } from 'rollup-plugin-visualizer';
import unocss from 'unocss/vite';
import Icons from 'unplugin-icons/vite';
import IconsResolver from 'unplugin-icons/resolver';
import { createSvgIconsPlugin } from 'vite-plugin-svg-icons-ng';
import { FileSystemIconLoader } from 'unplugin-icons/loaders';
import VueI18nPlugin from '@intlify/unplugin-vue-i18n/vite';
import { viteStaticCopy } from 'vite-plugin-static-copy';
import { VitePWA } from 'vite-plugin-pwa';
// import legacy from '@vitejs/plugin-legacy'
// import { mars3dPlugin } from 'vite-plugin-mars3d';
import progress from 'vite-plugin-progress';
import { VantResolver } from '@vant/auto-import-resolver';
import { templateCompilerOptions } from '@tresjs/core';
import packageJson from './package.json';
import { createIsolatedTemplateCompiler } from './scripts/vue-template-compiler';

/** 项目构建时间 */
const PROJECT_BUILD_TIME = JSON.stringify(dayjs().format('YYYY-MM-DD HH:mm:ss'));
const rootPath = resolve(__dirname, '.');
const srcPath = `${rootPath}/src`;
const resourcePath = 'resource';
const requiredDependenciesPath = resolve(srcPath, `platforms/${PlatformConfig.target}/required-dependencies.json`);
const requiredDependencies = existsSync(requiredDependenciesPath)
  ? JSON.parse(readFileSync(requiredDependenciesPath, 'utf8'))
  : [];
const platformDependencies = [...new Set([...(PlatformConfig.dependencies || []), ...requiredDependencies])];

//自定义图标集合 c表示通用，h表示自定义
let customCollections: any = {
  c: FileSystemIconLoader(`${srcPath}/assets/icon`, (svg) => {
    return svg.replace(/^<svg/, '<svg fill="currentColor" ');
  }),
};
//默认通用图标
let iconDirs = [resolve(process.cwd(), `${srcPath}/assets/icon`)];
//处理项目自定义图标文件路径
let iconPath = resolve(srcPath, `platforms/${PlatformConfig.target}/assets/icon`);
if (existsSync(iconPath)) {
  customCollections['h'] = FileSystemIconLoader(iconPath, (svg) => {
    return svg.replace(/^<svg/, '<svg fill="currentColor" ');
  });
  iconDirs.push(iconPath);
}

const modulesIcon: any[] = [];
//处理模块中的图标
platformDependencies.forEach((depend: string) => {
  let iconPath = resolve(srcPath, `modules/${depend}/assets/icon`);
  if (existsSync(iconPath)) {
    modulesIcon.push(
      FileSystemIconLoader(iconPath, (svg) => {
        return svg.replace(/^<svg/, '<svg fill="currentColor" ');
      }),
    );

    iconDirs.push(iconPath);
  }
});

if (modulesIcon.length > 0) {
  customCollections['m'] = { ...modulesIcon };
}

/**
 * 生成打包文件版本号
 * */
const generateVersionFile = () => {
  return {
    name: 'vite-plugin-generate-version',
    // 在打包结束时执行
    closeBundle() {
      const currentVersion = packageJson.version;
      const timestamp = new Date().getTime();

      // 定义要写入的内容
      const versionInfo = {
        version: currentVersion,
        timestamp: timestamp,
      };

      const outputPath = resolve(__dirname, PlatformConfig.outputDir || 'dist', 'version.json');

      // 写入文件
      writeFileSync(outputPath, JSON.stringify(versionInfo, null, 2));

      console.log(`\n✅ Version file generated: ${currentVersion} - ${timestamp}`);
    },
  };
};

// https://vitejs.dev/config/
export default defineConfig(({ command, mode }) => {
  const plugins: any[] = [
    vue({
      ...templateCompilerOptions,
      compiler: createIsolatedTemplateCompiler(),
    }),
    vueJsx(),
    Inspect(),
    // DefineOptions(),
    Icons({
      autoInstall: true,
      compiler: 'vue3',
      customCollections: customCollections,
      scale: 1,
      defaultClass: 'inline-block',
    }),
    unocss(),
    Components({
      resolvers: [
        IconsResolver({
          customCollections: ['c', 'h', 'sensor', 'm'],
          prefix: 'icon',
          // enabledCollections: ['h', 'mdi'],
        }),
        TDesignResolver({ library: 'mobile-vue' }),
        NaiveUiResolver(),
        // PlatformConfig.mobile_ui ? TDesignResolver({ library: 'mobile-vue' }) : NaiveUiResolver(),
        VantResolver(),
      ],
    }),
    createSvgIconsPlugin({
      // 指定需要缓存的图标文件夹
      iconDirs: iconDirs,
      symbolId: 'icon-[dir]-[name]',
      inject: 'body-last',
      customDomId: '__svg__icons__dom__',
    }),
    createHtmlPlugin({
      minify: false,
      entry: `/src/platforms/${PlatformConfig.target}/main.ts`,
      template: '/index.html',
      inject: {
        data: {
          title: PlatformConfig.title,
          abbrTitle: PlatformConfig.abbrTitle,
          description: PlatformConfig.description,
        },
      },
    }),
  ];
  //编译时移除指令
  let dropList: any[] = [];
  if (command === 'build') {
    //浏览器兼容性支持,奉贤使用的Firefox版本大部份为70版本
    /**
     * 兼容性处理
     * @see https://browsersl.ist/
     * */
    // plugins.push(
    //   legacy({
    //     targets: ['>0.3%', 'Firefox > 70', 'last 2 versions', 'not dead']
    //   }),
    // )
    if (PlatformConfig.enable_update_version !== false) {
      plugins.push(generateVersionFile());
    }

    plugins.push(progress());
    plugins.push(
      visualizer({
        open: true,
        gzipSize: true,
        brotliSize: true,
      }),
    );
    dropList = ['console', 'debugger'];
  } else if (command === 'serve') {
    //测试增加跨域相关指令
    // plugins.push(crossOriginIsolation())
  }

  if (PlatformConfig.target === 'tk_verify') {
    plugins.push(
      VitePWA({
        registerType: 'autoUpdate',
        injectRegister: false,
        filename: 'sw.js',
        manifestFilename: 'manifest.webmanifest',
        manifest: {
          name: '票务核销',
          short_name: '核销',
          description: '票务移动端扫码核销终端',
          scope: '/verify/',
          start_url: '/verify/',
          display: 'standalone',
          orientation: 'portrait',
          background_color: '#0f172a',
          theme_color: '#18a058',
          icons: [
            {
              src: '/verify-icons/icon.svg',
              sizes: 'any',
              type: 'image/svg+xml',
              purpose: 'any maskable',
            },
          ],
        },
        workbox: {
          navigateFallback: '/verify/index.html',
          globPatterns: ['**/*.{js,css,html,svg,png,ico,json,webmanifest}'],
          globIgnores: ['**/FilePreview-*.js'],
        },
      }),
    );
  }

  /**
   * 待复制文件夹
   * */
  let copyTargets = [];
  //启用优立引擎时复制文件
  if (PlatformConfig.enable_udstream) {
    copyTargets.push({
      src: resolve(resourcePath, 'udstream/'),
      dest: '',
      rename: { stripBase: 2 },
    });
  }

  //启用海康播放器时复制文件
  if (PlatformConfig.enable_hkplayer) {
    copyTargets.push({
      src: resolve(resourcePath, 'hik/'),
      dest: 'hkplayer',
      rename: { stripBase: 2 },
    });
  }

  //启用draco
  if (PlatformConfig.enable_draco) {
    copyTargets.push({
      src: resolve(resourcePath, 'draco/'),
      dest: 'draco',
      rename: { stripBase: 2 },
    });
  }

  //启用 Marmoset Viewer 时复制离线运行时和 UI 资源
  if (PlatformConfig.enable_marmoset) {
    copyTargets.push({
      src: resolve(resourcePath, 'marmoset/'),
      dest: 'marmoset',
      rename: { stripBase: 2 },
    });
  }

  //启用全景时复制对应文件
  if (PlatformConfig.enable_krpano) {
    copyTargets.push({
      src: resolve(resourcePath, 'krpano/'),
      dest: 'krpano',
      rename: { stripBase: 2 },
    });
  }
  //cesium支持，原生文件处理
  /*if (PlatformConfig.enable_cesium) {
    const cesiumPath = 'node_modules/cesium/Build/Cesium/';
    copyTargets.push({
      src: resolve(cesiumPath, 'Assets/!*'),
      dest: 'cesium/Assets',
    });
    copyTargets.push({
      src: resolve(cesiumPath, 'ThirdParty/!*'),
      dest: 'cesium/ThirdParty',
    });
    copyTargets.push({
      src: resolve(cesiumPath, 'Workers/!*'),
      dest: 'cesium/Workers',
    });
    copyTargets.push({
      src: resolve(cesiumPath, 'Widgets/!*'),
      dest: 'cesium/Widgets',
    });
    copyTargets.push({
      src: resolve(cesiumPath, 'Cesium.js'),
      dest: 'cesium',
    });
  }*/

  if (PlatformConfig.enable_cesium) {
    copyTargets.push({
      src: resolve(resourcePath, 'cesium/'),
      dest: 'cesium',
      rename: { stripBase: 2 },
    });
  }

  //当启用监测模块时，复制监测模块的传感器图标文件
  if (PlatformConfig.dependencies.includes('monitor')) {
    customCollections['sensor'] = FileSystemIconLoader(`${srcPath}/modules/monitor/static/sensor`, (svg) => {
      return svg.replace(/^<svg/, '<svg fill="currentColor" ');
    });

    copyTargets.push({
      src: resolve('src', 'modules/monitor/static/'),
      dest: '',
      rename: { stripBase: 4 },
    });
  }

  //启用mars3d时复制文件，和viteStaticCopy有冲突,暂时只是打包的时候增加
  // if (PlatformConfig.enable_mars3d) {
  //   if (command === 'build') {
  //     plugins.push(mars3dPlugin({}));
  //   }
  // }

  //处理各个项目的静态文件
  let platFormStatic = resolve(srcPath, `platforms/${PlatformConfig.target}/static`);
  if (existsSync(platFormStatic)) {
    copyTargets.push({
      src: resolve('src', `platforms/${PlatformConfig.target}/static/`),
      dest: '',
      rename: { stripBase: 4 },
    });
  }

  //复制tinymce相关文件
  // copyTargets.push({
  //   src: resolve(resourcePath, 'tinymce/'),
  //   dest: 'tinymce',
  // });

  //复制全国行政区划geojson数据
  copyTargets.push({
    src: resolve(resourcePath, 'geojson'),
    dest: 'geojson',
    rename: { stripBase: 2 },
  });

  // PDF 阅读器所需资源随应用提供，版本与已安装的 pdfjs-dist 保持一致。
  for (const directory of ['cmaps', 'standard_fonts', 'wasm', 'iccs']) {
    copyTargets.push({
      src: resolve(rootPath, 'node_modules/pdfjs-dist', directory),
      dest: `pdfjs/${directory}`,
      rename: { stripBase: true },
    });
  }

  /**
   * 当待复制的文件非空时加入到插件队列
   * */
  if (copyTargets.length > 0) {
    plugins.push(
      viteStaticCopy({
        targets: copyTargets.map(({ src, dest, rename }) => {
          return {
            src: normalizePath(src),
            dest: dest,
            rename: rename,
          };
        }),
      }),
    );
  }

  /**
   * 多语言支持
   * */
  let localesPath = `./src/platforms/${PlatformConfig.target}/locales`;
  if (existsSync(localesPath)) {
    plugins.push(
      VueI18nPlugin({
        include: resolve(`${srcPath}/platforms/${PlatformConfig.target}`, './locales/**'),
      }),
    );
  }

  /**
   * 特殊路径映射处理
   * */
  let proxy: any = {};
  const proxyUrls = ['/userfiles', '/doc', '/logout', PlatformConfig.api_path, PlatformConfig.front_api_path];

  proxyUrls.map((url) => {
    proxy[url] = {
      target: PlatformConfig.dev_url,
      changeOrigin: true,
      secure: false,
    };
  });

  // go2rtc 代理：HTTP（WHEP）+ WebSocket（信令）
  // proxy['/stream'] = {
  //   target: 'http://127.0.0.1:1984',
  //   changeOrigin: true,
  //   ws: true,
  //   rewrite: (path) => path.replace(/^\/stream/, ''),
  //   configure: (proxy) => {
  //     proxy.on('error', (err: any) => {
  //       // go2rtc 发完 SDP answer 或 WebSocket 流结束后主动关闭连接，
  //       // Vite 代理还在写数据会触发 EPIPE / ECONNRESET，属正常现象，静默处理。
  //       if (err.code === 'EPIPE' || err.code === 'ECONNRESET') return;
  //       console.error('[stream proxy]', err.message);
  //     });
  //   },
  // };

  return {
    base: './',
    server: {
      host: '0.0.0.0',
      port: 3200,
      strictPort: false,
      open: false,
      cors: true,
      proxy: proxy,
      // headers: {
      //   'Cross-Origin-Embedder-Policy': 'require-corp',
      //   'Cross-Origin-Opener-Policy': 'same-origin'
      // }
    },
    resolve: {
      alias: {
        '~': rootPath,
        '@': srcPath,
      },
    },

    define: {
      PROJECT_BUILD_TIME,
      //编译时间
      APP_BUILD_TIME: JSON.stringify(dayjs().format('YYYY-MM-DD HH:mm:ss')),
      //编译项目
      APP_TARGET: JSON.stringify(PlatformConfig.target),
      //标题
      APP_TITLE: JSON.stringify(PlatformConfig.title),
      //短标题
      APP_ABBR_TITLE: JSON.stringify(PlatformConfig.abbrTitle),
      //描述
      APP_DESCRIPTION: JSON.stringify(PlatformConfig.description),
      //版权
      APP_COPYRIGHT: JSON.stringify(PlatformConfig.copyright),
      //移动端UI
      APP_MOBILE_UI: JSON.stringify(PlatformConfig.mobile_ui),
      //启用mars3d
      ENABLE_MARS3D: JSON.stringify(PlatformConfig.enable_mars3d),
      ENABLE_CESIUM: JSON.stringify(PlatformConfig.enable_cesium),
      ENABLE_DRACO: JSON.stringify(PlatformConfig.enable_draco),
      ENABLE_MARMOSET: JSON.stringify(PlatformConfig.enable_marmoset === true),
      //OAUTH认证方式
      ENABLE_OAUTH2: JSON.stringify(PlatformConfig.enable_oauth2 === true),
      ENABLE_UPDATE_VERSION: JSON.stringify(PlatformConfig.enable_update_version !== false),
      //启用海康播放器
      ENABLE_HKPLAYER: JSON.stringify(PlatformConfig.enable_hkplayer),
      //首页
      APP_HOME_URL: JSON.stringify(command === 'build' ? PlatformConfig.prod_home : PlatformConfig.dev_home),
      //API基路径
      APP_BASE_URL: JSON.stringify(command === 'build' ? PlatformConfig.prod_url : PlatformConfig.dev_url),
      // APP_BASE_URL: JSON.stringify(command === 'build' ? PlatformConfig.prod_url : ''),
      //后端api地址
      APP_API_PATH: JSON.stringify(PlatformConfig.api_path),
      //免授权api地址
      APP_API_FRONT_PATH: JSON.stringify(PlatformConfig.front_api_path),
      //代理API到其它路径
      APP_API_PROXY: JSON.stringify(PlatformConfig.enable_api_proxy),
    },
    plugins: plugins,
    optimizeDeps: {
      exclude: [
        'pdfjs-dist/legacy/build/pdf',
        'pdfjs-dist/legacy/build/pdf.worker.min.js',
        // Decoder source is loaded as text to build an owned Worker, never as a prebundled module.
        'three/examples/jsm/libs/draco/gltf/draco_wasm_wrapper.js?raw',
        'three/examples/jsm/libs/basis/basis_transcoder.js?raw',
      ],
      // disabled: false,
      // force: true
      // exclude: ['@form-create/utils']
    },
    esbuild: {
      drop: dropList,
    },
    build: {
      outDir: PlatformConfig.outputDir || 'dist',
      minify: 'esbuild',
      target: ['es2016'],
      //谨慎开启可能导致编译失败
      sourcemap: false,
      reportCompressedSize: false,
    },
  };
});
