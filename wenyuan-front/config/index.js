/* jshint esversion: 9 */
import { configs } from './config.js';

const platform = process.env.WEB_PLATFORM || 'asa_wms';
// 兼容源项目中的 asawms 配置名称。
const target = platform === 'asawms' ? 'asa_wms' : platform;

if (!Object.hasOwn(configs, target)) {
  throw new Error(`未知平台「${platform}」，可选平台：${Object.keys(configs).join(', ')}`);
}

export default { ...configs[target], dependencies: [...configs[target].dependencies] };
