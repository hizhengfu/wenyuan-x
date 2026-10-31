const HIDE_INITIAL_LOADING_TARGETS = new Set([
  'yunyoujx',
  'yyjxwbH5',
]);

export function shouldHidePlatformInitialLoading(target: string): boolean {
  return HIDE_INITIAL_LOADING_TARGETS.has(target);
}
