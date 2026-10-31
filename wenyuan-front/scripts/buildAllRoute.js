import { configs } from '../config/config.js';

import { createRoutes } from './route.utils.js';

for (let key in configs) {
  let config = configs[key];
  console.log(`生成「${config.title}」路由信息`);
  //生成相应目录下的路由文件
  createRoutes(`./src/platforms/${config.target}`, config.dependencies);
}
