import request from '@/utils/request';
import { transformToTreeFormat } from '@/utils';

export interface RolePermissionAssociation {
  applicationId: string;
  permissionIds: string[];
  version: string;
}

export interface RoleDataPermissionAssociation {
  applicationId: string;
  dataPermissionCodes: string[];
  version: string;
}

export interface RoleMenuAssociation {
  applicationId: string;
  menuIds: string[];
  version: string;
}

export interface RolePortalAssociation {
  applicationId: string;
  portalIds: string[];
  version: string;
}

export function list(params: object) {
  return request({
    url: 'role/list',
    params: params,
    method: 'get',
  });
}

export function associate(params: object) {
  return request({
    url: 'role/associate',
    params: params,
    method: 'get',
  });
}

export function associateUpdate(data: object) {
  return request({
    url: 'role/associate',
    data: data,
    method: 'post',
  });
}

export function rolePermissions(roleId: string, applicationId: string) {
  return request({
    url: `role/${roleId}/permissions`,
    params: { applicationId },
    method: 'get',
  });
}

export function updateRolePermissions(
  roleId: string,
  data: { applicationId: string; permissionIds: string[]; expectedVersion: string },
) {
  return request({
    url: `role/${roleId}/permissions`,
    data,
    method: 'post',
  });
}

export function roleDataPermissions(roleId: string, applicationId: string) {
  return request({
    url: `role/${roleId}/data-permissions`,
    params: { applicationId },
    method: 'get',
  });
}

export function updateRoleDataPermissions(
  roleId: string,
  data: { applicationId: string; dataPermissionCodes: string[]; expectedVersion: string },
) {
  return request({
    url: `role/${roleId}/data-permissions`,
    data,
    method: 'post',
  });
}

export function roleMenus(roleId: string, applicationId: string) {
  return request({
    url: `role/${roleId}/menus`,
    params: { applicationId },
    method: 'get',
  });
}

export function updateRoleMenus(
  roleId: string,
  data: { applicationId: string; menuIds: string[]; expectedVersion: string },
) {
  return request({
    url: `role/${roleId}/menus`,
    data,
    method: 'post',
  });
}

export function rolePortals(roleId: string, applicationId: string) {
  return request({
    url: `role/${roleId}/portals`,
    params: { applicationId },
    method: 'get',
  });
}

export function updateRolePortals(
  roleId: string,
  data: { applicationId: string; portalIds: string[]; expectedVersion: string },
) {
  return request({
    url: `role/${roleId}/portals`,
    data,
    method: 'post',
  });
}

export function tree() {
  return request({
    url: 'role/tree',
    method: 'get',
  });
}

export function menu(params: object) {
  return request({
    url: 'role/menu',
    params: params,
    method: 'get',
  }).then((res) => {
    return transformToTreeFormat(res.data, { idKey: 'id', pIdKey: 'parentId' });
  });
}

export function get(id: string) {
  return request({
    url: `role/${id}`,
    method: 'post',
  });
}

export function add(data: object) {
  return request({
    url: 'role/create',
    method: 'post',
    data,
  });
}

export function del(id: string) {
  return request({
    url: 'role/delete/' + id,
    method: 'post',
  });
}

export function edit(data: object) {
  return request({
    url: 'role/update',
    method: 'post',
    data,
  });
}

export function copy(data: object) {
  return request({
    url: 'role/copy',
    method: 'post',
    data,
  });
}
