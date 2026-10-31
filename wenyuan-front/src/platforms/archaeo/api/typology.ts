import request from '@/utils/request';

export function page(params: object) {
  return request({ url: 'wms/typology/page', params, method: 'get' });
}

export function get(id: string) {
  return request({ url: `wms/typology/${id}`, method: 'get' });
}

export function create(data: object) {
  return request({ url: 'wms/typology/create', method: 'post', data });
}

export function update(data: object) {
  return request({ url: 'wms/typology/update', method: 'post', data });
}

export function del(id: string) {
  return request({ url: `wms/typology/delete/${id}`, method: 'post' });
}

/**
 * 新建型式分期概要：按遗址联动材质、器型、期段和统计预览。
 */
export function summary(params: { projectId: string; material?: string }) {
  return request({ url: 'wms/typology/summary', params, method: 'get' });
}

/**
 * 智能拼装：返回 content JSON 字符串，前端反序列化进编辑器
 */
export function autoBuild(data: {
  projectId: string;
  projectName?: string;
  material?: string;
  categoryName?: string;
  artifactTypes?: string[];
}) {
  return request({ url: 'wms/typology/autoBuild', method: 'post', data });
}

/**
 * 自动分期统计预览（每类器物分几型/几式/几期/几段）
 */
export function autoStat(params: { projectId: string; material?: string }) {
  return request({ url: 'wms/typology/autoStat', params, method: 'get' });
}

/**
 * 标型器候选：编辑"式"单元格时列出可选文物（带线图、图名）
 */
export function typicalArtifacts(params: {
  projectId: string;
  artifactType: string;
  material?: string;
  stage?: string;
  artifactStyle?: string;
  artifactSubStyle?: string;
  phaseName?: string;
  sectionName?: string;
}) {
  return request({ url: 'wms/typology/typicalArtifacts', params, method: 'get' });
}
