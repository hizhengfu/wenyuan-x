import request from '@/utils/request';
export interface CmsPluginDefinition {
  code: string; label: string; permissionPrefix: string; templateNamespace: string; editorKey: string;
  access: 'PUBLIC' | 'LOGIN' | 'PERMISSION'; readPermission?: string; capabilities: string[]; configFields: { key: string; label: string; type: 'STRING' | 'NUMBER' | 'BOOLEAN'; defaultValue?: unknown }[];
}
let metadata: Promise<CmsPluginDefinition[]> | undefined;
/** 缓存当前应用的插件元数据请求，多个表单共用；失败时清空 Promise，后续进入页面可重试。 */
export function cmsPlugins(): Promise<CmsPluginDefinition[]> {
  if (!metadata) metadata = (request({ url: 'cms/plugins' }) as unknown as Promise<{ data: CmsPluginDefinition[] }>).then(result => result.data).catch(error => { metadata = undefined; throw error; });
  return metadata;
}

export interface CmsSitePlugin extends CmsPluginDefinition { enabled: boolean; config: Record<string, any> }
export function sitePlugins(siteId: string): Promise<{ data: CmsSitePlugin[] }> {
  return request({ url: `cms/plugins/sites/${siteId}` }) as unknown as Promise<{ data: CmsSitePlugin[] }>;
}
export function saveSitePlugin(siteId: string, module: string, data: { enabled: boolean; config: Record<string, any> }) {
  return request({ url: `cms/plugins/sites/${siteId}/${module}`, method: 'put', data });
}
