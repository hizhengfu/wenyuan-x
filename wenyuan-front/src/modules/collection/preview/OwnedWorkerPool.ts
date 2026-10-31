export const decodeCancelled = () => new DOMException('模型解码已取消', 'AbortError');
type Task = {
  message: unknown;
  transfer: Transferable[];
  resolve: (event: MessageEvent) => void;
  reject: (error: unknown) => void;
};
/** Each worker has one active job; closing rejects active and queued jobs before termination. */
export class OwnedWorkerPool {
  private closed = false;
  private workers: { worker: Worker; task?: Task }[] = [];
  private queue: Task[] = [];
  private creator?: () => Worker;
  private limit = 2;
  setWorkerCreator(creator: () => Worker) {
    this.creator = creator;
  }
  setWorkerLimit(limit: number) {
    this.limit = Math.max(1, Math.min(4, limit));
  }
  postMessage(message: unknown, transfer: Transferable[] = []): Promise<MessageEvent> {
    if (this.closed) return Promise.reject(decodeCancelled());
    return new Promise((resolve, reject) => {
      this.queue.push({ message, transfer, resolve, reject });
      this.pump();
    });
  }
  private pump() {
    while (!this.closed && this.queue.length) {
      let slot = this.workers.find((s) => !s.task);
      if (!slot) {
        if (this.workers.length >= this.limit) return;
        try {
          if (!this.creator) throw new Error('解码 Worker 尚未准备好。');
          slot = { worker: this.creator() };
          this.workers.push(slot);
          const target = slot;
          slot.worker.addEventListener('message', (event) => {
            if (this.closed || !target.task) return;
            const task = target.task;
            target.task = undefined;
            if (event.data?.type === 'error') task.reject(new Error(String(event.data.error || '模型解码失败')));
            else task.resolve(event);
            this.pump();
          });
          slot.worker.addEventListener('error', (event) => {
            event.preventDefault();
            target.task?.reject(new Error(event.message || '模型解码 Worker 失败'));
            target.task = undefined;
            target.worker.terminate();
            this.workers = this.workers.filter((s) => s !== target);
            this.pump();
          });
        } catch (error) {
          this.queue.shift()!.reject(error);
          continue;
        }
      }
      slot.task = this.queue.shift()!;
      try {
        slot.worker.postMessage(slot.task.message, slot.task.transfer);
      } catch (error) {
        slot.task.reject(error);
        slot.task = undefined;
      }
    }
  }
  dispose() {
    if (this.closed) return;
    this.closed = true;
    this.queue.splice(0).forEach((task) => task.reject(decodeCancelled()));
    this.workers.splice(0).forEach((slot) => {
      slot.task?.reject(decodeCancelled());
      slot.task = undefined;
      slot.worker.terminate();
    });
    this.creator = undefined;
  }
}
