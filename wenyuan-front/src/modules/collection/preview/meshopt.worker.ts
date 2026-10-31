import { MeshoptDecoder } from 'three/examples/jsm/libs/meshopt_decoder.module.js';
const scope = self as unknown as DedicatedWorkerGlobalScope;
scope.addEventListener('message', async (event) => {
  try {
    const { count, stride, source, mode, filter } = event.data;
    const length = count * stride;
    if (!Number.isSafeInteger(length) || length <= 0 || length > 256 * 1024 * 1024)
      throw new Error('Meshopt 解码输出大小无效或超过限额。');
    await MeshoptDecoder.ready;
    const target = new Uint8Array(length);
    MeshoptDecoder.decodeGltfBuffer(target, count, stride, new Uint8Array(source), mode, filter);
    scope.postMessage({ type: 'decoded', buffer: target.buffer }, [target.buffer]);
  } catch (error) {
    scope.postMessage({ type: 'error', error: error instanceof Error ? error.message : String(error) });
  }
});
