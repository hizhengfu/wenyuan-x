import {
  CanvasTexture,
  ClampToEdgeWrapping,
  LinearFilter,
  Texture,
} from 'three';

import type { Map3DBounds, Map3DTopTextureEffect } from './types';

const DEFAULT_TIANDITU_TOKEN = 'c93ea43b61c76df411540db24f55bfd1';
const DEFAULT_TIANDITU_TILE_URL = 'https://t{s}.tianditu.gov.cn/img_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=img&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILECOL={x}&TILEROW={y}&TILEMATRIX={z}&tk={key}';
const DEFAULT_SUBDOMAINS = ['0', '1', '2', '3', '4', '5', '6', '7'];
const TILE_SIZE = 256;
const MAX_LATITUDE = 85.05112878;

interface PixelCoordinate {
  x: number;
  y: number;
}

interface TileCoordinate {
  x: number;
  y: number;
  z: number;
}

export function isTiledImageryTopTexture(effect?: Map3DTopTextureEffect) {
  return !!effect?.enabled && effect.mode === 'tianditu';
}

export function isImageTopTexture(effect?: Map3DTopTextureEffect) {
  return !!effect?.enabled
    && effect.mode === 'image'
    && typeof effect.imageUrl === 'string'
    && effect.imageUrl.trim().length > 0;
}

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

function normalizeGeoBounds(bounds: Map3DBounds): Map3DBounds {
  return {
    minX: clamp(Math.min(bounds.minX, bounds.maxX), -180, 180),
    maxX: clamp(Math.max(bounds.minX, bounds.maxX), -180, 180),
    minY: clamp(Math.min(bounds.minY, bounds.maxY), -MAX_LATITUDE, MAX_LATITUDE),
    maxY: clamp(Math.max(bounds.minY, bounds.maxY), -MAX_LATITUDE, MAX_LATITUDE),
  };
}

function lonLatToWorldPixel(lng: number, lat: number, zoom: number): PixelCoordinate {
  const sinLat = Math.sin((clamp(lat, -MAX_LATITUDE, MAX_LATITUDE) * Math.PI) / 180);
  const scale = TILE_SIZE * 2 ** zoom;

  return {
    x: ((lng + 180) / 360) * scale,
    y: (0.5 - Math.log((1 + sinLat) / (1 - sinLat)) / (4 * Math.PI)) * scale,
  };
}

function resolveTileUrl(template: string, tile: TileCoordinate, token: string, subdomain: string) {
  return template
    .replace(/\{s\}/g, subdomain)
    .replace(/\{x\}/g, String(tile.x))
    .replace(/\{y\}/g, String(tile.y))
    .replace(/\{z\}/g, String(tile.z))
    .replace(/\{key\}/g, token)
    .replace(/\{tk\}/g, token)
    .replace(/\{token\}/g, token);
}

function resolveCanvasFilter(effect: Map3DTopTextureEffect) {
  const brightness = clamp(effect.brightness ?? 1, 0.2, 3);
  const contrast = clamp(effect.contrast ?? 1, 0.2, 3);
  const saturation = clamp(effect.saturation ?? 1, 0, 4);

  return `brightness(${brightness}) contrast(${contrast}) saturate(${saturation})`;
}

function applyToneOverlay(
  context: CanvasRenderingContext2D,
  width: number,
  height: number,
  effect: Map3DTopTextureEffect,
) {
  const tintOpacity = clamp(effect.tintOpacity ?? 0, 0, 1);
  const screenOpacity = clamp(effect.screenOpacity ?? 0, 0, 1);

  if (tintOpacity > 0 && effect.tintColor) {
    context.save();
    context.globalCompositeOperation = 'overlay';
    context.globalAlpha = tintOpacity;
    context.fillStyle = effect.tintColor;
    context.fillRect(0, 0, width, height);
    context.restore();
  }

  if (screenOpacity > 0 && effect.screenColor) {
    context.save();
    context.globalCompositeOperation = 'screen';
    context.globalAlpha = screenOpacity;

    const gradient = context.createRadialGradient(
      width * 0.48,
      height * 0.36,
      0,
      width * 0.48,
      height * 0.36,
      Math.max(width, height) * 0.68,
    );
    gradient.addColorStop(0, effect.screenColor);
    gradient.addColorStop(1, 'rgba(255, 255, 255, 0)');
    context.fillStyle = gradient;
    context.fillRect(0, 0, width, height);
    context.restore();
  }
}

function loadTileImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.crossOrigin = 'anonymous';
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error(`Map tile load failed: ${url}`));
    image.src = url;
  });
}

function loadTextureImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.crossOrigin = 'anonymous';
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error(`Map texture image load failed: ${url}`));
    image.src = url;
  });
}

function createCanvasTextureFromCanvas(canvas: HTMLCanvasElement) {
  const texture = new CanvasTexture(canvas);
  texture.wrapS = ClampToEdgeWrapping;
  texture.wrapT = ClampToEdgeWrapping;
  texture.minFilter = LinearFilter;
  texture.magFilter = LinearFilter;
  texture.generateMipmaps = true;

  return texture;
}

export async function createImageTopTexture(effect: Map3DTopTextureEffect): Promise<Texture | null> {
  if (typeof document === 'undefined' || !effect.imageUrl) {
    return null;
  }

  const image = await loadTextureImage(effect.imageUrl);
  const maxCanvasSize = Math.max(512, effect.maxCanvasSize ?? 1600);
  const sourceWidth = Math.max(1, image.naturalWidth || image.width);
  const sourceHeight = Math.max(1, image.naturalHeight || image.height);
  const canvasScale = Math.min(1, maxCanvasSize / Math.max(sourceWidth, sourceHeight));
  const canvas = document.createElement('canvas');
  canvas.width = Math.max(1, Math.round(sourceWidth * canvasScale));
  canvas.height = Math.max(1, Math.round(sourceHeight * canvasScale));

  const context = canvas.getContext('2d');
  if (!context) {
    return null;
  }

  const previousFilter = context.filter;
  context.filter = resolveCanvasFilter(effect);
  context.drawImage(image, 0, 0, canvas.width, canvas.height);
  context.filter = previousFilter;
  applyToneOverlay(context, canvas.width, canvas.height, effect);

  return createCanvasTextureFromCanvas(canvas);
}

export async function createTiledImageryTexture(
  geoBounds: Map3DBounds,
  effect: Map3DTopTextureEffect,
): Promise<Texture | null> {
  if (typeof document === 'undefined') {
    return null;
  }

  const bounds = normalizeGeoBounds(geoBounds);
  if (bounds.maxX <= bounds.minX || bounds.maxY <= bounds.minY) {
    return null;
  }

  const zoom = clamp(Math.round(effect.zoom ?? 8), 1, 18);
  const northwest = lonLatToWorldPixel(bounds.minX, bounds.maxY, zoom);
  const southeast = lonLatToWorldPixel(bounds.maxX, bounds.minY, zoom);
  const sourceWidth = Math.max(1, southeast.x - northwest.x);
  const sourceHeight = Math.max(1, southeast.y - northwest.y);
  const maxCanvasSize = Math.max(512, effect.maxCanvasSize ?? 1600);
  const canvasScale = Math.min(1, maxCanvasSize / Math.max(sourceWidth, sourceHeight));
  const canvas = document.createElement('canvas');
  canvas.width = Math.max(1, Math.round(sourceWidth * canvasScale));
  canvas.height = Math.max(1, Math.round(sourceHeight * canvasScale));

  const context = canvas.getContext('2d');
  if (!context) {
    return null;
  }

  const startTileX = Math.floor(northwest.x / TILE_SIZE);
  const endTileX = Math.floor((southeast.x - 1) / TILE_SIZE);
  const startTileY = Math.floor(northwest.y / TILE_SIZE);
  const endTileY = Math.floor((southeast.y - 1) / TILE_SIZE);
  const tileUrl = effect.tileUrl ?? DEFAULT_TIANDITU_TILE_URL;
  const token = effect.token ?? DEFAULT_TIANDITU_TOKEN;
  const subdomains = effect.subdomains?.length ? effect.subdomains : DEFAULT_SUBDOMAINS;
  const tileRequests: Array<Promise<{ image: HTMLImageElement; tile: TileCoordinate }>> = [];

  for (let x = startTileX; x <= endTileX; x += 1) {
    for (let y = startTileY; y <= endTileY; y += 1) {
      const tile = { x, y, z: zoom };
      const subdomain = subdomains[Math.abs(x + y + zoom) % subdomains.length];
      const url = resolveTileUrl(tileUrl, tile, token, subdomain);
      tileRequests.push(loadTileImage(url).then((image) => ({ image, tile })));
    }
  }

  const loadedTiles = await Promise.allSettled(tileRequests);
  let drawnTiles = 0;
  const previousFilter = context.filter;
  context.filter = resolveCanvasFilter(effect);

  loadedTiles.forEach((result) => {
    if (result.status !== 'fulfilled') {
      return;
    }

    const { image, tile } = result.value;
    const dx = (tile.x * TILE_SIZE - northwest.x) * canvasScale;
    const dy = (tile.y * TILE_SIZE - northwest.y) * canvasScale;
    const size = TILE_SIZE * canvasScale;
    context.drawImage(image, dx, dy, size, size);
    drawnTiles += 1;
  });
  context.filter = previousFilter;

  if (!drawnTiles) {
    throw new Error('No map imagery tiles were loaded.');
  }

  applyToneOverlay(context, canvas.width, canvas.height, effect);

  return createCanvasTextureFromCanvas(canvas);
}
