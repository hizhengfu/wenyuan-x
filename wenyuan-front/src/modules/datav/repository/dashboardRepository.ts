import { loadBoundDatavProject, publishDatavProject, saveDatavDraft } from '../api/project';
import { cloneDatavDocument, normalizeDatavDocument, validateDatavDocument } from '../core/document';
import type { DatavDocument, DatavDocumentMode, DatavProject, DatavSubjectType } from '../core/types';

export class DatavDashboardRepository {
  /** 展示端只加载 published，设计端加载 draft；模式由调用方明确传入，禁止隐式混用。 */
  async load(subjectType: DatavSubjectType, subjectId: string, mode: DatavDocumentMode): Promise<DatavProject | null> {
    const response = await loadBoundDatavProject(subjectType, subjectId, mode);
    const project = response.data;
    if (!project) return null;
    const document = normalizeDatavDocument(project.document);
    if (!validateDatavDocument(document)) throw new Error('后端返回的大屏配置格式无效');
    return { ...project, document: cloneDatavDocument(document) };
  }

  async saveDraft(
    subjectType: DatavSubjectType,
    subjectId: string,
    document: DatavDocument,
    version: number,
    title?: string,
  ) {
    // 前端先阻止明显损坏的文档进入网络，后端仍会执行同等级别的可信校验。
    if (!validateDatavDocument(document)) throw new Error('大屏配置格式无效，不能保存');
    const response = await saveDatavDraft(subjectType, subjectId, document, version, title);
    return response.data;
  }

  async publish(subjectType: DatavSubjectType, subjectId: string, version: number) {
    const response = await publishDatavProject(subjectType, subjectId, version);
    return response.data;
  }
}

export const datavDashboardRepository = new DatavDashboardRepository();
