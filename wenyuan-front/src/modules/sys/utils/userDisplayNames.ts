import { displayNames } from '../api/user';

export interface UserDisplayName {
  id: string;
  name?: string;
  loginName?: string;
}
interface Entry {
  promise: Promise<UserDisplayName | null>;
  resolve: (value: UserDisplayName | null) => void;
  expiresAt: number;
}
export function createUserNameResolver(load: (ids: string[]) => Promise<UserDisplayName[]>) {
  const entries = new Map<string, Entry>();
  const queued = new Map<string, Entry>();
  let scheduled = false;
  function flush() {
    scheduled = false;
    const batch = [...queued];
    queued.clear();
    for (let offset = 0; offset < batch.length; offset += 100) {
      const items = batch.slice(offset, offset + 100);
      void load(items.map(([id]) => id))
        .then((rows) => {
          const found = new Map(rows.map((row) => [row.id, row]));
          for (const [id, entry] of items) {
            if (entries.get(id) !== entry) continue;
            const row = found.get(id) || null;
            entry.expiresAt = Date.now() + (row ? 300000 : 30000);
            entry.resolve(row);
          }
        })
        .catch(() => {
          for (const [id, entry] of items) {
            if (entries.get(id) !== entry) continue;
            entries.delete(id);
            entry.resolve(null);
          }
        });
    }
  }
  return {
    resolve(id: string): Promise<UserDisplayName | null> {
      const existing = entries.get(id);
      if (existing && existing.expiresAt > Date.now()) return existing.promise;
      let resolve!: Entry['resolve'];
      const promise = new Promise<UserDisplayName | null>((done) => (resolve = done));
      const entry = { promise, resolve, expiresAt: Number.POSITIVE_INFINITY };
      entries.set(id, entry);
      queued.set(id, entry);
      if (!scheduled) {
        scheduled = true;
        queueMicrotask(flush);
      }
      return promise;
    },
    clear() {
      entries.forEach((entry) => entry.resolve(null));
      entries.clear();
      queued.clear();
    },
  };
}

const scopedResolvers = new WeakMap<object, { scope: string; resolver: ReturnType<typeof createUserNameResolver> }>();
export function userNameResolver(owner: object, scope: string) {
  const cached = scopedResolvers.get(owner);
  if (cached?.scope === scope) return cached.resolver;
  cached?.resolver.clear();
  const resolver = createUserNameResolver(async (ids) => {
    const response: any = await displayNames(ids);
    const rows = response?.data?.data ?? response?.data ?? response;
    if (!Array.isArray(rows)) throw new Error('人员显示数据格式异常');
    // Cache only the display fields returned for the requested IDs.
    return rows
      .filter((row) => row && ids.includes(row.id))
      .map((row) => ({ id: row.id, name: row.name, loginName: row.loginName }));
  });
  scopedResolvers.set(owner, { scope, resolver });
  return resolver;
}
