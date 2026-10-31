import { describe, expect, it } from 'vitest';
import { BoundedMediaQueue } from '../mseBuffer';

describe('MSE bounded queue', () => {
  it('rejects overflow without silently evicting an earlier fMP4 block', () => {
    const queue = new BoundedMediaQueue(10);
    const first = new ArrayBuffer(6);

    expect(queue.enqueue(first)).toBe(true);
    expect(queue.enqueue(new ArrayBuffer(5))).toBe(false);
    expect(queue.length).toBe(1);
    expect(queue.byteLength).toBe(6);
    expect(queue.shift()).toBe(first);
  });
});
