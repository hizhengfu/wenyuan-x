export interface CesiumImageResource {
  supportsImageBitmapOptions(): Promise<boolean>;
}

const patchedResources = new WeakSet<CesiumImageResource>();

/** Cesium's native probe checks flipY but misses from-image, unsupported before Chromium 112. */
export function ensureImageBitmapCompatibility(resource: CesiumImageResource): void {
  if (patchedResources.has(resource)) return;
  patchedResources.add(resource);

  const supportsImageBitmapOptions = resource.supportsImageBitmapOptions.bind(resource);
  let supported: Promise<boolean> | undefined;
  resource.supportsImageBitmapOptions = () => {
    supported ??= (async () => {
      if (typeof createImageBitmap !== 'function') return false;
      try {
        const bitmap = await createImageBitmap(new ImageData(1, 1), { imageOrientation: 'from-image' });
        bitmap.close();
        return await supportsImageBitmapOptions();
      } catch {
        // Returning false lets Cesium decode textures with HTMLImageElement instead.
        return false;
      }
    })();
    return supported;
  };
}
