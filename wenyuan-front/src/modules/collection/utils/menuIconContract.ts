export interface SvgAssetEntry {
  name: string;
  content: string;
}

export function extractMenuIconNames(source: string): string[] {
  // Menu installers use both named icon arguments and page/group helper calls.
  const iconDeclaration = /(?:\bicon\s*=\s*|\b(?:page|group)\(\s*(?:(?:"(?:\\.|[^"\\])*"|[A-Za-z_]\w*|\d+)\s*,\s*)+)"m:([^"]+)"/g;
  const names = Array.from(source.matchAll(iconDeclaration), (match) => match[1]?.trim())
    .filter((name): name is string => Boolean(name));
  return [...new Set(names)].sort((a, b) => a.localeCompare(b));
}

export function missingMenuIconAssets(menuIconNames: string[], assetFileNames: string[]): string[] {
  const assetNames = new Set(assetFileNames.map((fileName) => fileName.replace(/\.svg$/i, '')));
  return menuIconNames.filter((iconName) => !assetNames.has(iconName));
}

export function invalidSvgAssets(entries: SvgAssetEntry[]): string[] {
  return entries
    .filter(({ content }) => {
      const normalized = content.trim();
      return !normalized.startsWith('<svg') || !/\sviewBox\s*=/.test(normalized);
    })
    .map(({ name }) => name)
    .sort((a, b) => a.localeCompare(b));
}
