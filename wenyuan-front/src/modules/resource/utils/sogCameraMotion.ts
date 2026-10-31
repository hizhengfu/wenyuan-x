const DEFAULT_FLY_SPEED = 0.64;
const MAX_MOVE_SECONDS = 0.25;

/** World units per second, fixed for a scene regardless of its saved camera view. */
export function sogSceneFlySpeed(min?: readonly number[] | null, max?: readonly number[] | null): number {
  if (min?.length !== 3 || max?.length !== 3 || ![...min, ...max].every(Number.isFinite)) {
    return DEFAULT_FLY_SPEED;
  }
  const diagonal = Math.hypot(max[0] - min[0], max[1] - min[1], max[2] - min[2]);
  return diagonal > 0 && Number.isFinite(diagonal)
    ? Math.max(0.1, diagonal * 0.05)
    : DEFAULT_FLY_SPEED;
}

/** Keep movement time-based at low FPS while avoiding a jump after a long pause. */
export function sogMoveDeltaSeconds(now: number, previous: number): number {
  if (!Number.isFinite(now) || !Number.isFinite(previous) || previous <= 0) return 0;
  return Math.min(MAX_MOVE_SECONDS, Math.max(0, (now - previous) / 1000));
}
