/** 图片占用独立的并发预算，避免一屏大量缩略图挤占列表和详情请求。 */
const limit = 6;
let active = 0;
const waiting: (() => void)[] = [];

export function scheduleImageRead<T>(signal: AbortSignal, read: () => Promise<T>): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const cancel = () => {
      const index = waiting.indexOf(start);
      if (index >= 0) waiting.splice(index, 1);
      reject(new DOMException('Image read aborted', 'AbortError'));
    };
    const start = () => {
      signal.removeEventListener('abort', cancel);
      if (signal.aborted) { cancel(); return; }
      active++;
      let task: Promise<T>;
      try { task = read(); } catch (error) { task = Promise.reject(error); }
      task.then(resolve, reject).finally(() => {
        active--;
        waiting.shift()?.();
      });
    };
    if (signal.aborted) { cancel(); return; }
    signal.addEventListener('abort', cancel, { once: true });
    if (active < limit) start();
    else waiting.push(start);
  });
}
