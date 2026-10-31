type Params = Record<string, unknown>;

/** 每个预警列表独立串行：相同查询共享在途请求，筛选变化只保留最后一次待执行查询。 */
export function createAlarmEventLoader<T>(request: (params: Params) => Promise<T>) {
  let running: { key: string; promise: Promise<T> } | undefined;
  let pending: {
    params: Params;
    promise: Promise<T>;
    resolve: (value: T) => void;
    reject: (reason: unknown) => void;
  } | undefined;
  const keyOf = (params: Params) => JSON.stringify(Object.entries(params).sort(([a], [b]) => a.localeCompare(b)));

  function start(params: Params): Promise<T> {
    const promise = Promise.resolve().then(() => request(params));
    running = { key: keyOf(params), promise };
    promise.then(finish, finish);
    return promise;
  }

  function finish() {
    running = undefined;
    const next = pending;
    pending = undefined;
    if (next) start(next.params).then(next.resolve, next.reject);
  }

  function load(params: Params): Promise<T> {
    const snapshot = { ...params };
    if (!running) return start(snapshot);
    if (!pending && running.key === keyOf(snapshot)) return running.promise;
    if (pending) {
      pending.params = snapshot;
      return pending.promise;
    }
    let resolve!: (value: T) => void;
    let reject!: (reason: unknown) => void;
    const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
    pending = { params: snapshot, promise, resolve, reject };
    return promise;
  }

  load.cancelPending = () => {
    const next = pending;
    pending = undefined;
    next?.reject(new Error('预警页面已离开，取消待执行查询'));
  };
  return load;
}
