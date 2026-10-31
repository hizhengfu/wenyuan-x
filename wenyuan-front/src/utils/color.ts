/**
 * 将十六进制颜色转换为RGB
 * */
export function hexToRgb(hex: string): number[] {
  const r = parseInt(hex.slice(0, 2), 16);
  const g = parseInt(hex.slice(2, 4), 16);
  const b = parseInt(hex.slice(4, 6), 16);
  return [r, g, b];
}

/**
 * 将RGB转换为十六进制颜色
 * */
export function rgbToHex(r: number, g: number, b: number): string {
  return [r, g, b]
    .map((x) => Math.round(x))
    .map((x) => x.toString(16).padStart(2, '0'))
    .join('');
}
