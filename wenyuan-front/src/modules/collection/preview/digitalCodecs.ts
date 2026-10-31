import { DRACOLoader } from 'three/examples/jsm/loaders/DRACOLoader.js';
import { KTX2Loader } from 'three/examples/jsm/loaders/KTX2Loader.js';
import type { LoadingManager, WebGLRenderer } from 'three';
import dracoJS from 'three/examples/jsm/libs/draco/gltf/draco_wasm_wrapper.js?raw';
import dracoWasm from 'three/examples/jsm/libs/draco/gltf/draco_decoder.wasm?url';
import basisJS from 'three/examples/jsm/libs/basis/basis_transcoder.js?raw';
import basisWasm from 'three/examples/jsm/libs/basis/basis_transcoder.wasm?url';
import { OwnedWorkerPool, decodeCancelled } from './OwnedWorkerPool';

/** Adapter for the pinned Three.js r170 codec lifecycle. No decoder paths come from model content. */
export function createDigitalCodecs(manager: LoadingManager, renderer: WebGLRenderer, signal: AbortSignal) {
  let closed = false;
  const abort = new AbortController();
  const check = () => {
    if (closed || signal.aborted) throw decodeCancelled();
  };
  const readWasm = async (url: string) => {
    check();
    const response = await fetch(url, {
      signal: abort.signal,
      credentials: 'omit',
      cache: 'no-store',
      redirect: 'error',
    });
    check();
    if (!response.ok) throw new Error('模型解码资源读取失败。');
    const bytes = await response.arrayBuffer();
    check();
    return bytes;
  };
  const draco = new DRACOLoader(manager).setWorkerLimit(2);
  const d = draco as any;
  // GLTFLoader ignores preload's promise. Initialize only when a decode task can observe its failure.
  d.preload = () => {
    check();
    return draco;
  };
  const cleanupDraco = () => {
    for (const worker of d.workerPool) {
      Object.values(worker._callbacks || {}).forEach((callback: any) => callback.reject(decodeCancelled()));
      worker.onmessage = null;
      worker.terminate();
    }
    d.workerPool.length = 0;
    if (d.workerSourceURL) {
      URL.revokeObjectURL(d.workerSourceURL);
      d.workerSourceURL = '';
    }
    d.decoderConfig.wasmBinary = undefined;
  };
  d._loadLibrary = async (name: string) => {
    check();
    if (name === 'draco_wasm_wrapper.js') return dracoJS;
    if (name === 'draco_decoder.wasm') return readWasm(dracoWasm);
    throw new Error('当前压缩预览需要 WebAssembly。');
  };
  const initDraco = d._initDecoder.bind(d);
  d._initDecoder = () => {
    check();
    return initDraco().then(() => {
      if (closed) {
        cleanupDraco();
        throw decodeCancelled();
      }
    });
  };
  const getWorker = d._getWorker.bind(d);
  d._getWorker = (...args: any[]) => {
    check();
    return getWorker(...args).then((worker: any) => {
      if (closed) {
        cleanupDraco();
        throw decodeCancelled();
      }
      return worker;
    });
  };
  const geometry = d._createGeometry.bind(d);
  d._createGeometry = (...args: any[]) => {
    check();
    return geometry(...args);
  };
  d.dispose = () => {
    cleanupDraco();
    return draco;
  };

  const ktx2 = new KTX2Loader(manager).detectSupport(renderer),
    k = ktx2 as any;
  const basisPool = new OwnedWorkerPool();
  k.workerPool = basisPool;
  k.init = () => {
    check();
    if (!k.transcoderPending)
      k.transcoderPending = readWasm(basisWasm).then((binary) => {
        check();
        const Klass = KTX2Loader as any,
          fn = Klass.BasisWorker.toString();
        const constants = ['EngineFormat', 'EngineType', 'TranscoderFormat', 'BasisFormat']
          .map((name) => `let _${name} = ${JSON.stringify(Klass[name])};`)
          .join('\n');
        k.workerSourceURL = URL.createObjectURL(
          new Blob([constants, '\n', basisJS, '\n', fn.slice(fn.indexOf('{') + 1, fn.lastIndexOf('}'))]),
        );
        k.transcoderBinary = binary;
        basisPool.setWorkerCreator(() => {
          check();
          const worker = new Worker(k.workerSourceURL);
          const copy = binary.slice(0);
          try {
            worker.postMessage({ type: 'init', config: k.workerConfig, transcoderBinary: copy }, [copy]);
            return worker;
          } catch (error) {
            worker.terminate();
            throw error;
          }
        });
      });
    return k.transcoderPending;
  };
  k.dispose = () => {
    basisPool.dispose();
    if (k.workerSourceURL) {
      URL.revokeObjectURL(k.workerSourceURL);
      k.workerSourceURL = '';
    }
    k.transcoderBinary = null;
    return ktx2;
  };
  const createTexture = k._createTexture.bind(k);
  k._createTexture = (...args: any[]) => {
    check();
    return createTexture(...args).then((texture: any) => {
      if (closed) {
        texture.dispose();
        throw decodeCancelled();
      }
      return texture;
    });
  };
  const meshPool = new OwnedWorkerPool();
  meshPool.setWorkerCreator(() => {
    check();
    return new Worker(new URL('./meshopt.worker.ts', import.meta.url), { type: 'module' });
  });
  const meshopt = {
    supported: typeof WebAssembly === 'object',
    ready: Promise.resolve(),
    decodeGltfBufferAsync: (count: number, stride: number, source: Uint8Array, mode: string, filter: string) => {
      check();
      const copy = source.slice().buffer;
      return meshPool.postMessage({ count, stride, source: copy, mode, filter }, [copy]).then((event) => {
        check();
        return new Uint8Array(event.data.buffer);
      });
    },
  };
  function dispose() {
    if (closed) return;
    closed = true;
    abort.abort();
    cleanupDraco();
    ktx2.dispose();
    meshPool.dispose();
    signal.removeEventListener('abort', dispose);
  }
  signal.addEventListener('abort', dispose, { once: true });
  if (signal.aborted) dispose();
  return { draco, ktx2, meshopt, dispose };
}
