import { beforeEach, describe, expect, it, vi } from 'vitest';

const { requestMock } = vi.hoisted(() => ({ requestMock: vi.fn() }));
vi.mock('@/utils/request', () => ({ default: requestMock }));

import { createExport, executeImport, inspectImport, schemas, tables } from '../dataExchange';

describe('data exchange api', () => {
  beforeEach(() => requestMock.mockReset());

  it('always scopes table discovery by data source and schema id', () => {
    tables({ dataSource: 'primary', schemaId: 'schema-1', direction: 'EXPORT' });
    expect(requestMock).toHaveBeenCalledWith({
      url: 'sys/data-exchange/tables',
      method: 'get',
      params: { pageSize: 500, dataSource: 'primary', schemaId: 'schema-1', direction: 'EXPORT' },
    });
    schemas('primary', 'IMPORT');
    expect(requestMock).toHaveBeenLastCalledWith({
      url: 'sys/data-exchange/schemas', method: 'get', params: { dataSource: 'primary', direction: 'IMPORT' },
    });
  });

  it('passes one-time grants only in the sensitive operation header', () => {
    createExport({ previewId: 'preview-1', requestDigest: 'digest-1' }, 'grant-export');
    executeImport('job-1', { inspectTaskId: 'inspect-1', mappings: [] }, 'grant-import');

    expect(requestMock.mock.calls[0][0].headers).toEqual({ 'X-Sensitive-Operation-Grant': 'grant-export' });
    expect(requestMock.mock.calls[1][0].headers).toEqual({ 'X-Sensitive-Operation-Grant': 'grant-import' });
  });

  it('can reinspect an existing import job with its uploaded file', () => {
    inspectImport('job-1', 'file-1');

    expect(requestMock).toHaveBeenCalledWith({
      url: 'sys/data-exchange/imports/job-1/inspect',
      method: 'post',
      data: { fileId: 'file-1' },
    });
  });
});
