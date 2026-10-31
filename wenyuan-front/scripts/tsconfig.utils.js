import fs from 'fs';
import path from 'path';

export function buildTsConfig(dir, dependencies) {
  const config = {
    include: [
      'config/*.js',
      'src/**/*.d.ts',
      'src/components/*.ts',
      'src/components/*.vue',
      'src/directives/*.ts',
      'src/plugins/*.ts',
      'src/router/*.ts',
      'src/utils/*.ts',
      'src/**/*.tsx',
      'node_modules/@types/geojson',
      'node_modules/vue-cesium/Cesium.d.ts',
      'node_modules/vue-cesium/global.d.ts',
      'node_modules/tdesign-mobile-vue/global.d.ts',
    ],
  };

  config.include.push(`${dir}/*.ts`);
  config.include.push(`${dir}/*.vue`);
  dependencies.forEach((d) => {
    if (d) {
      config.include.push(`src/modules/${d}/*.ts`);
      config.include.push(`src/modules/${d}/*.vue`);
    }
  });
  config.include = config.include.sort();
  const text = JSON.stringify(config, null, 2);

  fs.writeFile('./tsconfig.private.json', text, (err) => {
    if (err) throw err;
    console.log('tsconfig.private.json下的需要编译的文件已经生成完毕！');
  });
}
