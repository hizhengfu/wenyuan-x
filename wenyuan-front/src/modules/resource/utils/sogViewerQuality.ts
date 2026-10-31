export const MOBILE_SOG_MAX_SPLATS = 2_000_000;

/** Keep the coarsest complete level renderable, then let the engine refine it. */
export function streamedSogBudget(mobile: boolean, coarseCount = 0): number {
  return Math.max(mobile ? 1_000_000 : 4_000_000, coarseCount);
}

export function canSelectSogLevel(mobile: boolean, count: number): boolean {
  return !mobile || count <= MOBILE_SOG_MAX_SPLATS;
}

/** An explicit choice caps spatial refinement; coarser levels remain as fallback. */
export function streamedSogLodRange(selected: number, lodLevels: number): { min: number; max: number } {
  if (!Number.isInteger(lodLevels) || lodLevels < 1 || !Number.isInteger(selected) ||
      selected < -1 || selected >= lodLevels) {
    throw new RangeError('Streamed SOG 精度级别无效');
  }
  return { min: Math.max(0, selected), max: lodLevels - 1 };
}
