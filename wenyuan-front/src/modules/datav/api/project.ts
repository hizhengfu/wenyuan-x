import request from '@/utils/request';
import type {
  DatavDocument,
  DatavDocumentMode,
  DatavProject,
  DatavProjectType,
  DatavProjectSummary,
  DatavRevision,
  DatavSubjectType,
} from '../core/types';

export function listDatavProjects(params?: {
  keyword?: string;
  projectType?: DatavProjectType;
  pageNum?: number;
  pageSize?: number;
}) {
  return request({ url: 'datav/project/list', method: 'get', params });
}

export function updateDatavProject(projectId: string, title: string, groupId: string) {
  return request<DatavProjectSummary>({
    url: `datav/project/${projectId}`,
    method: 'patch',
    data: { title, groupId },
  });
}

export function loadBoundDatavProject(subjectType: DatavSubjectType, subjectId: string, mode: DatavDocumentMode) {
  return request<DatavProject | null>({
    url: `datav/project/binding/${subjectType}/${subjectId}${mode === 'draft' ? '/draft' : ''}`,
    method: 'get',
  });
}

export function saveDatavDraft(
  subjectType: DatavSubjectType,
  subjectId: string,
  document: DatavDocument,
  version: number,
  title?: string,
) {
  return request<DatavProject>({
    url: `datav/project/binding/${subjectType}/${subjectId}/draft`,
    method: 'put',
    data: { version, title, purpose: 'MAIN_DASHBOARD', document },
  });
}

export function publishDatavProject(subjectType: DatavSubjectType, subjectId: string, version: number) {
  return request<DatavProject>({
    url: `datav/project/binding/${subjectType}/${subjectId}/publish`,
    method: 'post',
    data: { version, purpose: 'MAIN_DASHBOARD' },
  });
}

export function copyDatavProject(sourceProjectId: string, subjectType: DatavSubjectType, subjectId: string) {
  return request<DatavProject>({
    url: `datav/project/${sourceProjectId}/copy`,
    method: 'post',
    data: { subjectType, subjectId, purpose: 'MAIN_DASHBOARD' },
  });
}

export function createDatavTemplate(projectId: string, title: string) {
  return request<DatavProject>({
    url: `datav/project/${projectId}/template`,
    method: 'post',
    data: { title },
  });
}

export function listDatavRevisions(projectId: string) {
  return request<DatavRevision[]>({ url: `datav/project/${projectId}/revisions`, method: 'get' });
}

export function restoreDatavRevision(projectId: string, currentVersion: number, revisionVersion: number) {
  return request<DatavProject>({
    url: `datav/project/${projectId}/revisions/restore`,
    method: 'post',
    data: { currentVersion, revisionVersion },
  });
}

export function archiveDatavProject(projectId: string) {
  return request({ url: `datav/project/${projectId}`, method: 'delete' });
}
