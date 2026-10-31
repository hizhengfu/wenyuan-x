import PlatformConfig from '../config/index.js';
import { existsSync, readFileSync } from 'node:fs';

import { createRoutes } from './route.utils.js';
import { buildTsConfig } from './tsconfig.utils.js';

console.log(`生成「${PlatformConfig.title}」路由信息`);
const requiredDependenciesPath = `src/platforms/${PlatformConfig.target}/required-dependencies.json`;
const requiredDependencies = existsSync(requiredDependenciesPath)
  ? JSON.parse(readFileSync(requiredDependenciesPath, 'utf8'))
  : [];
const dependencies = [...new Set([...(PlatformConfig.dependencies || []), ...requiredDependencies])];
//生成包含文件，防止全量编译
buildTsConfig(`src/platforms/${PlatformConfig.target}`, dependencies);
//生成相应目录下的路由文件
createRoutes(`./src/platforms/${PlatformConfig.target}`, dependencies);
