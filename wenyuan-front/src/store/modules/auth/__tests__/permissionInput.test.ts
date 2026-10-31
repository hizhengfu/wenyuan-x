// @vitest-environment happy-dom
import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useAuthStore } from '../index';

describe('permission input in rendered action columns', () => {
  beforeEach(() => setActivePinia(createPinia()));

  it('accepts comma-separated codes and directive arrays with the same authorization result', () => {
    const auth = useAuthStore();
    auth.permissions = ['COLLECTION_STORAGE'];
    expect(auth.hasPermission('RESTORATION_ARCHIVE,COLLECTION_STORAGE')).toBe(true);
    expect(auth.hasPermission(['RESTORATION_ARCHIVE', 'COLLECTION_STORAGE'])).toBe(true);
    expect(auth.hasPermission(['RESTORATION_ARCHIVE'])).toBe(false);
  });

  it('denies empty and malformed values even for an administrator', () => {
    const auth = useAuthStore();
    auth.permissions = ['ADMIN'];
    expect(auth.hasPermission(['RESTORATION_ARCHIVE'])).toBe(true);
    expect(auth.hasPermission([])).toBe(false);
    expect(auth.hasPermission({ code: 'RESTORATION_ARCHIVE' } as never)).toBe(false);
  });
});
