import { describe, expect, it } from 'vitest';
import type { PermissionCatalogItem } from '@/modules/sys/api/permission';
import {
  buildPermissionCatalogTree,
  effectivePermissionSelection,
  filterPermissionEndpoints,
  filterPermissionCatalog,
  filterPermissionModules,
  normalizePermissionCatalog,
  normalizePermissionSelection,
  sameStringSet,
  updatePermissionSelection,
} from '../permissionCatalog';

const permissions: PermissionCatalogItem[] = [
  {
    id: 'p1',
    applicationId: '1',
    name: '查询角色',
    code: 'ROLES_SELECT',
    moduleCode: 'system-role',
    moduleLabel: '角色管理',
    isSys: true,
    source: 'io.example.SystemInstall',
    used: true,
    globalBypass: false,
    applicationAmbiguous: false,
    applicationIds: ['1'],
    endpointCount: 1,
    endpoints: [
      {
        key: 'GET /api/role/list',
        httpMethod: 'GET',
        path: '/api/role/list',
        controller: 'RoleController',
        handler: 'list',
        summary: '查询角色列表',
        rawExpression: "hasAnyAuthority('ADMIN','ROLES_SELECT')",
        policySource: 'METHOD',
        mappingStatus: 'EXACT',
        authorityCodes: ['ADMIN', 'ROLES_SELECT'],
        roleCodes: [],
      },
    ],
  },
  {
    id: 'p2',
    applicationId: '1',
    name: '历史权限',
    code: 'LEGACY_PERMISSION',
    moduleCode: '_UNCLASSIFIED',
    moduleLabel: '未归类（手工权限）',
    isSys: false,
    used: false,
    globalBypass: false,
    applicationAmbiguous: false,
    applicationIds: ['1'],
    endpointCount: 0,
    endpoints: [],
  },
];

describe('permission catalog filtering', () => {
  it('searches module, authority code, HTTP method and path with AND semantics', () => {
    expect(
      filterPermissionCatalog(permissions, {
        keyword: '角色 roles_select get /role/list',
        mapping: 'ALL',
        usage: 'ALL',
      }).map((item) => item.id),
    ).toEqual(['p1']);
  });

  it('filters modules independently by label and technical code', () => {
    const modules = [
      { code: 'system-role', label: '角色管理', order: 1, permissionCount: 2, usedPermissionCount: 1 },
      { code: 'system-area', label: '地区管理', order: 2, permissionCount: 3, usedPermissionCount: 3 },
    ];

    expect(filterPermissionModules(modules, '地区 system-area').map((module) => module.code)).toEqual(['system-area']);
  });

  it('filters API details by method path controller and authority code', () => {
    const endpoints = permissions[0].endpoints;

    expect(filterPermissionEndpoints(endpoints, '/role/list RoleController roles_select', 'GET')).toEqual(endpoints);
    expect(filterPermissionEndpoints(endpoints, '/role/list', 'POST')).toEqual([]);
  });

  it('keeps unclassified permissions visible and filterable without an API mapping', () => {
    expect(
      filterPermissionCatalog(permissions, {
        moduleCode: '_UNCLASSIFIED',
        mapping: 'UNMAPPED',
        usage: 'UNUSED',
      }).map((item) => item.id),
    ).toEqual(['p2']);
  });

  it('compares selected IDs independent of order', () => {
    expect(sameStringSet(new Set(['p1', 'p2']), new Set(['p2', 'p1']))).toBe(true);
    expect(sameStringSet(new Set(['p1']), new Set(['p1', 'p2']))).toBe(false);
  });

  it('normalizes diagnostics returned by an older backend', () => {
    const catalog = normalizePermissionCatalog({
      permissions: [
        {
          ...permissions[0],
          endpoints: [{ ...permissions[0].endpoints[0], roleCodes: undefined }],
        },
      ],
      diagnostics: {
        codeNotDeclared: [],
      },
    } as unknown as Parameters<typeof normalizePermissionCatalog>[0]);

    expect(catalog.diagnostics.multiApplicationCodes).toEqual([]);
    expect(catalog.modules).toEqual([]);
    expect(catalog.permissions[0].endpoints[0].roleCodes).toEqual([]);
    expect(() => filterPermissionCatalog(catalog.permissions, {
      keyword: 'roles_select',
      mapping: 'ALL',
      usage: 'ALL',
    })).not.toThrow();
    expect(catalog.totals.endpointCount).toBe(0);
  });

  it('restores permission hierarchy and keeps matching ancestors visible', () => {
    const hierarchy: PermissionCatalogItem[] = [
      { ...permissions[0], id: 'area-all', parentId: '0', code: 'AREA_ALL', name: '地区管理' },
      { ...permissions[0], id: 'area-select', parentId: 'area-all', code: 'AREA_SELECT', name: '地区查看' },
    ];

    const tree = buildPermissionCatalogTree(hierarchy, new Set(['area-select']));

    expect(tree.map((item) => item.id)).toEqual(['area-all']);
    expect(tree[0].children?.map((item) => item.id)).toEqual(['area-select']);
  });

  it('keeps an ALL permission mutually exclusive with its descendants', () => {
    const hierarchy: PermissionCatalogItem[] = [
      { ...permissions[0], id: 'area-all', parentId: '0', code: 'AREA_ALL', name: '地区管理' },
      { ...permissions[0], id: 'area-select', parentId: 'area-all', code: 'AREA_SELECT', name: '地区查看' },
      { ...permissions[0], id: 'area-edit', parentId: 'area-all', code: 'AREA_EDIT', name: '地区编辑' },
    ];

    expect([...normalizePermissionSelection(hierarchy, ['area-all', 'area-select'])]).toEqual(['area-all']);
    expect([...effectivePermissionSelection(hierarchy, ['area-all'])].sort()).toEqual([
      'area-all',
      'area-edit',
      'area-select',
    ]);

    const selectChild = updatePermissionSelection(
      hierarchy,
      new Set(['area-all']),
      ['area-select'],
      true,
    );
    expect([...selectChild]).toEqual(['area-select']);

    const selectAll = updatePermissionSelection(
      hierarchy,
      new Set(['area-select', 'area-edit']),
      ['area-all'],
      true,
    );
    expect([...selectAll]).toEqual(['area-all']);
  });

  it('does not cascade a structural parent without an ALL code', () => {
    const hierarchy: PermissionCatalogItem[] = [
      { ...permissions[0], id: 'area-group', parentId: '0', code: 'AREA_GROUP', name: '地区权限组' },
      { ...permissions[0], id: 'area-select', parentId: 'area-group', code: 'AREA_SELECT', name: '地区查看' },
    ];

    expect([...normalizePermissionSelection(hierarchy, ['area-group'])]).toEqual(['area-group']);
  });

  it('keeps ADMIN mutually exclusive with every business permission', () => {
    const hierarchy: PermissionCatalogItem[] = [
      { ...permissions[0], id: 'admin', parentId: '0', code: 'ADMIN', name: '超级管理员', globalBypass: true },
      { ...permissions[0], id: 'area-all', parentId: '0', code: 'AREA_ALL', name: '地区管理' },
      { ...permissions[0], id: 'area-select', parentId: 'area-all', code: 'AREA_SELECT', name: '地区查看' },
    ];

    expect([...normalizePermissionSelection(hierarchy, ['admin', 'area-select'])]).toEqual(['admin']);
    expect([...effectivePermissionSelection(hierarchy, ['admin'])].sort()).toEqual([
      'admin',
      'area-all',
      'area-select',
    ]);
    expect([...updatePermissionSelection(hierarchy, ['area-select'], ['admin'], true)]).toEqual(['admin']);
    expect([...updatePermissionSelection(hierarchy, ['admin'], ['area-select'], true)]).toEqual(['area-select']);
  });
});
