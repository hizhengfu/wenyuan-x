/* jshint esversion: 9 */
const configs = {
  asa_wms: {
    target: 'archaeo',
    title: '考古工地文物库房管理系统',
    abbrTitle: '考古工地文物库房',
    description: '考古工地文物库房管理系统-v1.0.0',
    outputDir: 'dist/asa_wms',
    dev_url: 'http://127.0.0.1:8080',
    prod_url: '.',
    api_path: '/api/v1',
    front_api_path: '/front',
    dependencies: ['sys', 'message'],
  },
  qxslib: {
    target: 'qxslib',
    title: '钱学森图书馆智慧现场服务监控管理系统',
    abbrTitle: '钱学森图书馆智慧现场服务监控管理系统',
    description: '钱学森图书馆智慧现场服务监控管理系统-v1.0.0',
    outputDir: 'dist/qxslib',
    dev_url: 'http://dev.shyuandong.com/',
    prod_url: '',
    api_path: '/api/v1',
    front_api_path: '/front',
    dependencies: ['sys', 'module'],
  },
};

export { configs };
