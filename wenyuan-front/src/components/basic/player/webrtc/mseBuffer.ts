/**
 * SourceBuffer 忙碌时使用的有界媒体块队列。
 * 队列不会静默淘汰任何 fMP4 块：容量不足时明确拒绝，由播放器重建 MSE 管线或切换备用模式。
 */
export class BoundedMediaQueue {
  private readonly chunks: ArrayBuffer[] = [];
  private pendingBytes = 0;

  constructor(private readonly maxBytes: number) {
    if (!Number.isFinite(maxBytes) || maxBytes <= 0) throw new Error('maxBytes must be positive');
  }

  get length(): number {
    return this.chunks.length;
  }

  get byteLength(): number {
    return this.pendingBytes;
  }

  enqueue(data: ArrayBuffer): boolean {
    if (data.byteLength > this.maxBytes) return false;
    if (this.pendingBytes + data.byteLength > this.maxBytes) return false;
    this.chunks.push(data);
    this.pendingBytes += data.byteLength;
    return true;
  }

  shift(): ArrayBuffer | undefined {
    const chunk = this.chunks.shift();
    if (chunk) this.pendingBytes -= chunk.byteLength;
    return chunk;
  }
}
