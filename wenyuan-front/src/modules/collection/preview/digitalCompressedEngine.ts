import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import { createDigitalCodecs } from './digitalCodecs';
import { decodeCancelled } from './OwnedWorkerPool';

/** Only accepts the already verified temporary model produced by digitalPackagePreview. */
export function createDigitalCompressedEngine(
  host: HTMLElement,
  url: string,
  callbacks: { loaded: () => void; failed: (error: unknown) => void },
) {
  const abort = new AbortController();
  let closed = false,
    frame = 0;
  let renderer: THREE.WebGLRenderer | undefined,
    controls: OrbitControls | undefined,
    observer: ResizeObserver | undefined;
  let codecs: ReturnType<typeof createDigitalCodecs> | undefined, mixer: THREE.AnimationMixer | undefined;
  const tracked = new Set<any>(),
    disposed = new Set<any>();
  function release(value: any) {
    if (!value || disposed.has(value)) return;
    disposed.add(value);
    if (value.isObject3D)
      value.traverse((node: any) => {
        release(node.geometry);
        release(node.skeleton);
        if (node.isInstancedMesh) node.dispose();
        (Array.isArray(node.material) ? node.material : [node.material]).forEach(release);
      });
    else if (value.isMaterial) {
      Object.values(value).forEach((v) => {
        if ((v as any)?.isTexture) release(v);
      });
      value.dispose();
    } else if (value.isTexture) {
      value.dispose();
      const image = value.source?.data;
      (Array.isArray(image) ? image : [image]).forEach((v) => {
        if (v && typeof v.close === 'function' && !disposed.has(v)) {
          disposed.add(v);
          v.close();
        }
      });
    } else if (value.isBufferGeometry || value instanceof THREE.Skeleton) value.dispose();
  }
  const contextLost = (event: Event) => {
    event.preventDefault();
    fail(new Error('图形上下文已丢失，请重新打开预览。'));
  };
  function dispose() {
    if (closed) return;
    closed = true;
    abort.abort();
    cancelAnimationFrame(frame);
    observer?.disconnect();
    mixer?.stopAllAction();
    if (mixer) mixer.uncacheRoot(mixer.getRoot());
    controls?.dispose();
    codecs?.dispose();
    tracked.forEach(release);
    tracked.clear();
    if (renderer) {
      renderer.domElement.removeEventListener('webglcontextlost', contextLost);
      renderer.dispose();
      renderer.forceContextLoss();
      renderer.domElement.remove();
    }
  }
  function fail(error: unknown) {
    if (!closed) {
      dispose();
      callbacks.failed(error);
    }
  }
  const ready = (async () => {
    try {
      if (!url.startsWith('blob:')) throw new Error('压缩模型入口必须是已经核验的本地预览地址。');
      renderer = new THREE.WebGLRenderer({ antialias: true });
      renderer.setPixelRatio(Math.min(devicePixelRatio || 1, 2));
      renderer.setClearColor(0xf3f5f7);
      renderer.domElement.addEventListener('webglcontextlost', contextLost);
      host.appendChild(renderer.domElement);
      const scene = new THREE.Scene(),
        camera = new THREE.PerspectiveCamera(45, 1, 0.01, 10000);
      controls = new OrbitControls(camera, renderer.domElement);
      controls.enableDamping = true;
      const manager = new THREE.LoadingManager();
      manager.setURLModifier((value) => {
        if (closed) throw decodeCancelled();
        if (!/^(blob:|data:)/.test(value)) throw new Error('拒绝未核验的模型依赖地址。');
        return value;
      });
      codecs = createDigitalCodecs(manager, renderer, abort.signal);
      const loader = new GLTFLoader(manager)
        .setDRACOLoader(codecs.draco)
        .setKTX2Loader(codecs.ktx2)
        .setMeshoptDecoder(codecs.meshopt as any);
      loader.register((parser) => {
        const get = parser.getDependency.bind(parser);
        const geometries = parser.loadGeometries.bind(parser);
        parser.loadGeometries = (primitives) =>
          Promise.all(
            primitives.map((primitive) =>
              geometries([primitive]).then((values) => {
                values.forEach((value) => {
                  if (closed) release(value);
                  else tracked.add(value);
                });
                if (closed) throw decodeCancelled();
                return values;
              }),
            ),
          ).then((values) => values.flat());
        parser.getDependency = (type: string, index: number) =>
          get(type, index).then((value: any) => {
            if (type === 'texture' && !value) throw new Error('模型纹理解码失败，请检查原件或重新打开预览。');
            if (['texture', 'material', 'mesh', 'scene'].includes(type)) {
              if (closed) release(value);
              else tracked.add(value);
            }
            if (closed) throw decodeCancelled();
            return value;
          });
        return { name: 'COLLECTION_RESOURCE_OWNERSHIP' };
      });
      const response = await fetch(url, { signal: abort.signal, cache: 'no-store', redirect: 'error' });
      if (!response.ok) throw new Error('模型预览数据读取失败。');
      const data = await response.arrayBuffer();
      if (closed) return;
      const gltf = await loader.parseAsync(data, '');
      if (closed) {
        gltf.scenes.forEach(release);
        return;
      }
      gltf.scenes.forEach((value) => tracked.add(value));
      scene.add(gltf.scene);
      const box = new THREE.Box3().setFromObject(gltf.scene),
        center = box.getCenter(new THREE.Vector3()),
        size = box.getSize(new THREE.Vector3());
      if (box.isEmpty() || !Number.isFinite(size.length())) throw new Error('模型缺少可显示的有效几何。');
      const radius = Math.max(size.length() / 2, 0.001);
      controls.target.copy(center);
      const resize = () => {
        if (closed || !renderer) return;
        const width = Math.max(host.clientWidth, 1),
          height = Math.max(host.clientHeight, 1);
        renderer.setSize(width, height);
        camera.aspect = width / height;
        camera.updateProjectionMatrix();
      };
      resize();
      const angle = Math.min(
        (camera.fov * Math.PI) / 360,
        Math.atan(Math.tan((camera.fov * Math.PI) / 360) * camera.aspect),
      );
      const distance = (radius / Math.sin(angle)) * 1.1;
      camera.position.copy(center).add(new THREE.Vector3(1, 0.65, 1).normalize().multiplyScalar(distance));
      camera.near = Math.max(radius / 1000, 0.00001);
      camera.far = Math.max(distance + radius * 20, 10);
      camera.updateProjectionMatrix();
      scene.add(new THREE.HemisphereLight(0xffffff, 0x59616b, 2));
      const light = new THREE.DirectionalLight(0xffffff, 3);
      light.position.set(2, 3, 4);
      scene.add(light);
      mixer = new THREE.AnimationMixer(gltf.scene);
      gltf.animations.forEach((clip) => mixer!.clipAction(clip).play());
      observer = new ResizeObserver(resize);
      observer.observe(host);
      let previous = performance.now();
      const draw = (now: number) => {
        if (closed) return;
        try {
          mixer!.update(Math.min((now - previous) / 1000, 0.1));
          previous = now;
          controls!.update();
          renderer!.render(scene, camera);
          frame = requestAnimationFrame(draw);
        } catch (error) {
          fail(error);
        }
      };
      draw(previous);
      if (!closed) callbacks.loaded();
    } catch (error) {
      fail(error);
    }
  })();
  return { dispose, ready };
}
