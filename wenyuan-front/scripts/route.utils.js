import * as fs from 'fs';
import * as path from 'path';
import { find, isEmpty, isArray, findIndex, last } from 'lodash-es';

/**
 * 注意
 * 移动端路由算法不通用，不能直接替换覆盖
 * */

/**
 * 文件读取选项对象
 *
 * */
/*interface ReadFileOption {
    hidden?: boolean
    nonregular?: boolean
    ext?: string
}*/

/**
 * 文件原始对象
 * */
/*interface FileSourceBody {
    file: string,
    meta: object
}*/

/**
 * 路由节点对象
 * */

/*interface RouteNode {
    path: string,
    component?: string,
    meta?: object | undefined
    children?: RouteNode[] | undefined
}*/

/**
 * 根据目录名称递归获取所有的文件路径
 * @param dir 扫描的根路径
 * @param options 扫描选项
 * */
function recursiveFiles(dir, options) {
  let _r = [];

  if (options && options.ext && options.ext[0] !== '.') options.ext = '.' + options.ext;
  if (fs.existsSync(dir)) {
    const files = fs.readdirSync(dir);
    files.forEach((file) => {
      file = path.join(dir, file);
      const stats = fs.statSync(file);
      if (stats.isDirectory()) {
        _r = _r.concat(recursiveFiles(file, options));
      } else {
        const ext = path.extname(file);
        const hidden = path.basename(file)[0] === '.';
        const nonregular = !stats.isFile();

        if (
          (options.hidden || !hidden) &&
          (!options.ext || options.ext === ext) &&
          (options.nonregular || !nonregular)
        ) {
          const data = fs.readFileSync(file, 'utf8');

          let meta = null;
          if (/return[\S\s]*meta[\s]*:[\s]*({[^}]*})/.test(data)) {
            //转换字符串为对象,实际中写法可能非标不能直接用JSON.parse
            // eslint-disable-next-line no-eval
            // @ts-ignore
            meta = eval('(' + /return[\S\s]*meta[\s]*:[\s]*({[^}]*})/.exec(data)[1] + ')');
          }
          _r.push({
            file: file,
            meta: meta,
          });
        }
      }
    });
  }
  return _r;
}

function buildRoutes(files) {
  const routes = [];
  files.forEach(function (item) {
    let file = item.file;
    const meta = item.meta;
    file = file.replace(/[\\]+/g, '/');
    const module = file.match(/^.+\/modules\/([^/]*).+\.vue$/);
    // @ts-ignore
    const rule = file.match(/^.+views\/(.*)\.vue$/)[1];
    const _rule = rule.replace(/\/?index$/, '').split('/');
    //是否为通用目录
    if (/\/modules\//.test(file)) {
      file = '../../../modules/' + file.replace(/.+modules\//, '');
    } else {
      file = './' + rule + '.vue';
    }
    let root = _rule[0];
    if (module && module[1]) {
      //移除模块后嗯数字编号，方便依赖版本不同的情况下菜单路径不用修改
      const fixModule = module[1].replace(/\d+$/, '');
      root = fixModule;
      _rule.unshift(fixModule);
    }
    // console.log(file, _rule, rule)
    recursiveRoute(routes, file, meta, _rule, root, 1, routes);
  });
  return routes;
}

/**
 * 模块可通过 route.config.json 声明根路由行为，避免平台每次生成 route.ts 后
 * 丢失人工补充的 redirect 等配置。
 */
function applyModuleRouteConfigs(routes, dependencies) {
  dependencies.forEach((dependency) => {
    if (!dependency) return;
    const configPath = `./src/modules/${dependency}/route.config.json`;
    if (!fs.existsSync(configPath)) return;

    const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
    const rootPath = config.rootPath || `/${dependency.replace(/\d+$/, '')}`;
    const rootRoute = find(routes, { path: rootPath });
    if (!rootRoute) {
      throw new Error(`模块 ${dependency} 的根路由 ${rootPath} 不存在，无法应用 route.config.json`);
    }
    if (typeof config.redirect === 'string' && config.redirect.trim()) {
      rootRoute.redirect = config.redirect.trim();
    }
  });
}

/**
 * 递归生成路由表
 * @param route
 * @param file 文件路径
 * @param meta 文件中data中的meta内容
 * @param paths 路径数组
 * @param root 根目录
 * @param level 当前层级
 * @param routes 所有的路由
 * */
// function recursiveRoute(route: RouteNode[], file: string, meta: any, paths: string[], root: string, level: number, routes: RouteNode[]) {
function recursiveRoute(route, file, meta, paths, root, level, routes) {
  let _f = paths.shift();
  if (paths.length === 1) {
    //当模块下有对应界面时默认首页时用自身的界面不用公共布局
    if (paths[0] === '') {
      paths.shift();
    }
  }

  //自定义
  let _isCustomLayout = false;

  //如果有default表示为默认界面
  _f = _f === 'default' ? '' : _f;
  let node = { path: '' + _f };
  if (meta) {
    node.meta = meta;
  }
  if (level === 1) {
    //home文件夹中的内容作为首页
    if (_f === 'home') _f = '';
    node = {
      path: '/' + _f,
      component: 'APPLAYOUT',
    };
  }
  //vite 使用#有问题替换为@符号
  // @在文件结尾时使用自定义布局 处理下级内容中使用自有布局情况
  if (/@$/.test(_f)) {
    _isCustomLayout = true;
    _f = _f.replace(/@$/g, '');

    // node = { path: `/${root}/${_f}`, component: 'APPLAYOUT' }
    node = {
      path: `/${_f}`,
      component: 'APPLAYOUT',
    };
  }

  //支持命名路径 :test/:test 类似路径
  if (/[@_]/.test(_f)) {
    const normalizedPath = _f.replace(/@/g, ':').replace(/_/g, '/');
    node.path = _isCustomLayout ? `/${normalizedPath}` : normalizedPath;
  }
  let tempNode;
  if (_isCustomLayout) {
    tempNode = find(routes, { path: node.path });
  } else {
    tempNode = find(route, { path: node.path });
  }
  if (tempNode) {
    node = tempNode;
  } else {
    if (_isCustomLayout) {
      routes.unshift(node);
    } else {
      //命名路由后置到路由表末尾
      if (/:/.test(node.path)) {
        //命名路由在开始位置的放到最后
        if (/^:/.test(node.path)) {
          route.push(node);
        } else {
          //命名路由在非开始位置的放在第一个命名路由在开始位置的前面
          const index = findIndex(route, (item) => {
            return /^:/.test(item.path);
          });
          if (index === -1) {
            route.push(node);
          } else {
            route.splice(index, 0, node);
          }
        }
      } else {
        if (isEmpty(node.path)) {
          route.unshift(node);
        } else {
          const index = findIndex(route, (item) => {
            return !isEmpty(item.path);
          });

          if (index === -1) {
            route.unshift(node);
          } else {
            route.splice(index, 0, node);
          }
        }
      }
    }
  }

  // Custom-layout nodes are recreated above; retain metadata on the actual leaf.
  if (paths.length === 0 && meta) node.meta = { ...(node.meta || {}), ...meta };

  if (paths.length > 0) {
    if (!node.children) {
      node.children = [];
    }
    level++;
    recursiveRoute(node.children, file, meta, paths, root, level, routes);
  } else {
    if (!root) {
      root = 'home';
    } /*else if (/^#/.test(root)) {
            root = root.split("_")[0].replace(/^#/, '')
        }*/

    // node['name'] = _f
    // node['component'] = "SYNCLOAD:" + file;
    // node.name = `r-${last(node.path.split("/"))}`
    node.component = 'ASYNCLOAD:' + file + ':' + root;
  }
}

/**
 * @param dir 主项目路径
 * @param dependencies 依赖项目路径
 * 使用依赖项 可以更加细致的控制依赖粒度
 * */
export function createRoutes(dir, dependencies) {
  const viewDir = dir + '/views';
  const componentsDir = dir + '/components';
  console.log(`开始生成路径「${dir}」，依赖项${JSON.stringify(dependencies || [])}`);
  // console.log("开始生成路由表信息:", dir, JSON.stringify(dependencies));
  let files = [];

  if (!isArray(dependencies)) {
    dependencies = [];
  }
  //添加错误信息界面到依赖项
  dependencies.push('error');

  //加载依赖项数据
  dependencies.forEach((d) => {
    if (d) {
      files = files.concat(recursiveFiles(`./src/modules/${d}/views/`, { ext: 'vue' }));
    }
  });

  files = files.concat(recursiveFiles(viewDir, { ext: 'vue' }));

  const routes = buildRoutes(files);
  applyModuleRouteConfigs(routes, dependencies);
  let str = JSON.stringify(routes, null, 2);
  //生成加载函数
  // str = str.replace(/"ASYNCLOAD:([\S\\]+):(\S+)"/g, "r => require.ensure([], () => r(require('./$1.vue')), '$2')");
  // str = str.replace(/"SYNCLOAD:([\S\\]+)"/g, "require('./$1.vue')");
  //webpack2.4+ 新版的路由生成算法
  //vue route 路由算法
  // str = str.replace(/"ASYNCLOAD:([\S\\]+):(\S+)"/g, `() => import(/* webpackChunkName: "$2" */ '$1')`)
  str = str.replace(/"ASYNCLOAD:([\S\\]+):(\S+)"/g, "() => import('$1')");
  str = str.replace(/"SYNCLOAD:([\S\\]+)"/g, "() => import('$1')");
  str = str.replace(/"APPLAYOUT"/g, 'AppLayout');
  // 格式生成数据以便符合eslint提示
  str = str.replace(/"path"/g, 'path');
  str = str.replace(/"component"/g, 'component');
  str = str.replace(/"redirect"/g, 'redirect');
  str = str.replace(/"children"/g, 'children');
  str = str.replace(/"meta"/g, 'meta');
  str = str.replace(/"title"/g, 'title');
  str = str.replace(/"name"/g, 'name');
  str = str.replace(/"noAuthorize"/g, 'noAuthorize');

  if (fs.existsSync(componentsDir + '/AppLayout.vue')) {
    str = "import AppLayout from '../components/AppLayout.vue'\n\nexport default " + str;
  } else {
    str = "import AppLayout from '@/components/app/AppLayout.vue'\n\nexport default " + str;
  }

  str += '\n';

  //替换双引号为单引号
  str = str.replace(/"/g, "'");

  //有可能目录不存在，创建目录
  if (!fs.existsSync(viewDir)) {
    fs.mkdirSync(viewDir);
  }

  fs.writeFile(path.join(viewDir, './route.ts'), str, (err) => {
    if (err) throw err;
    console.log(`${viewDir}下的路由已经生成完毕！`);
  });
  // console.log("路由表信息已经生成完毕");
}
