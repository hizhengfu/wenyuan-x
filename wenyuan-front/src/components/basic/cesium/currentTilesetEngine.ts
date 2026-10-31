import { loadPreviewCesiumRuntime, type PreviewCesiumRuntime } from './previewCesiumRuntime';
import type { Cesium3DTileset, Viewer } from 'cesium';
export interface CurrentTilesetOptions {
  container: HTMLElement;
  url: string;
  /** Only for a caller that owns a fully verified, rewritten package. */
  acceptPreparedBlob?: boolean;
  waitForVisibleTiles?: boolean;
  onLoad: () => void;
  /** 可选的业务装配回调；此时模型与相机已就绪，调用方负责释放自行添加的交互监听。 */
  onReady?: (viewer: Viewer, tileset: Cesium3DTileset) => void;
  onError: (reason: unknown) => void;
  onCleanupError?: (reason: unknown) => void;
  runtime?: () => Promise<PreviewCesiumRuntime>;
}
/** Own all resources before exposing them; late creations are destroyed without mounting. */
export function openCurrentTileset(options: CurrentTilesetOptions) {
  let active = true;
  let viewer: Viewer | undefined;
  let tileset: Cesium3DTileset | undefined;
  let resize: ResizeObserver | undefined;
  const removeListeners: (() => void)[] = [];
  let resolveClosed!: () => void;
  const closed = new Promise<void>((resolve) => {
    resolveClosed = resolve;
  });
  const cleanupError = options.onCleanupError || ((reason: unknown) => console.error('瓦片预览资源清理失败', reason));
  const attempt = (work: () => void) => {
    try {
      work();
    } catch (reason) {
      cleanupError(reason);
    }
  };
  const destroy = (resource: { isDestroyed(): boolean; destroy(): unknown } | undefined) => {
    if (resource && !resource.isDestroyed()) resource.destroy();
  };
  function close() {
    if (!active) return;
    active = false;
    resolveClosed();
    const ownedViewer = viewer,
      ownedTileset = tileset,
      ownedResize = resize;
    viewer = undefined;
    tileset = undefined;
    resize = undefined;
    attempt(() => ownedResize?.disconnect());
    for (const remove of removeListeners.splice(0)) attempt(remove);
    // Destroy the Viewer even if removing an individual primitive fails.
    attempt(() => {
      if (ownedViewer && !ownedViewer.isDestroyed() && ownedTileset) ownedViewer.scene.primitives.remove(ownedTileset);
    });
    attempt(() => destroy(ownedViewer));
    // Handles detached objects and collections configured not to destroy their children.
    attempt(() => destroy(ownedTileset));
  }
  function fail(reason: unknown) {
    if (!active) return;
    close();
    options.onError(reason);
  }
  // Cesium raises these during scene traversal: leave that stack before destroying the scene.
  const deferFailure = (reason: unknown) => queueMicrotask(() => fail(reason));
  const run = async () => {
    try {
      const url = new URL(options.url, document.baseURI);
      if (
        (!/^https?:$/.test(url.protocol) && !(options.acceptPreparedBlob && url.protocol === 'blob:')) ||
        url.username ||
        url.password
      )
        throw new Error('不支持的瓦片地址');
      const cesium = await (options.runtime || loadPreviewCesiumRuntime)();
      if (!active) return;
      viewer = new cesium.Viewer(options.container, {
        animation: false,
        timeline: false,
        baseLayerPicker: false,
        baseLayer: false,
        geocoder: false,
        homeButton: false,
        sceneModePicker: false,
        navigationHelpButton: false,
        fullscreenButton: false,
        infoBox: false,
        selectionIndicator: false,
        globe: false,
        skyBox: false,
        skyAtmosphere: false,
      });
      const currentViewer = viewer;
      currentViewer.scene.light.intensity = 4;
      removeListeners.push(currentViewer.scene.renderError.addEventListener((_scene, reason) => deferFailure(reason)));
      if (typeof ResizeObserver !== 'undefined') {
        resize = new ResizeObserver(() => {
          if (!active) return;
          try {
            currentViewer.resize();
            currentViewer.scene.requestRender();
          } catch (reason) {
            deferFailure(reason);
          }
        });
        resize.observe(options.container);
      }
      const created = await cesium.Cesium3DTileset.fromUrl(url.href);
      if (!active) {
        attempt(() => destroy(created));
        return;
      }
      tileset = created;
      removeListeners.push(created.tileFailed.addEventListener((reason) => deferFailure(reason)));
      currentViewer.scene.primitives.add(created);
      // An omitted offset in Cesium 1.133 uses the sphere radius as distance, clipping small objects.
      const positioned = await currentViewer.zoomTo(created, { heading: 0, pitch: -0.5, range: 0 });
      if (!active) return;
      if (!positioned) throw new Error('瓦片视角定位未完成');
      if (options.waitForVisibleTiles) {
        await Promise.race([
          new Promise<void>((resolve) => {
            let contentVisible = false;
            const removeVisible = created.tileVisible.addEventListener(() => {
              contentVisible = true;
            });
            const remove = currentViewer.scene.postRender.addEventListener(() => {
              if (created.tilesLoaded && contentVisible) {
                remove();
                removeVisible();
                resolve();
              }
              contentVisible = false;
            });
            removeListeners.push(remove, removeVisible);
            currentViewer.scene.requestRender();
          }),
          closed,
        ]);
        if (!active) return;
      }
      options.onReady?.(currentViewer, created);
      if (!active) return;
      options.onLoad();
    } catch (reason) {
      fail(reason);
    }
  };
  // Closing must settle caller readiness even if a native network or zoom promise never finishes.
  const ready = Promise.race([run(), closed]);
  return { close, ready };
}
