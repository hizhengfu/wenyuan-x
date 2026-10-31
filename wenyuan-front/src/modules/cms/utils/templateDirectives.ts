/** 扫描标签头部时保留引号和括号内空白，参数仍是原生 FTL 表达式，不重写正文。 */
export interface TemplateDirective { start: number; end: number; name: string; alias: string; parameters: Record<string, string> }
function boundary(text: string, start: number, ends: string): number {
  let quote = '', depth = 0;
  for (let i = start; i < text.length; i++) {
    const char = text[i];
    if (quote) { if (char === '\\') i++; else if (char === quote) quote = ''; continue; }
    if (char === '"' || char === "'") { quote = char; continue; }
    if ('[({'.includes(char)) depth++;
    if ('])}'.includes(char)) depth--;
    if (!depth && ends.includes(char)) return i;
  }
  return text.length;
}
export function directiveAt(text: string, cursor: number): TemplateDirective | null {
  const starts = /<@([A-Za-z_]\w*)\b/g;
  let match: RegExpExecArray | null;
  while ((match = starts.exec(text))) {
    const end = boundary(text, starts.lastIndex, '>');
    if (cursor < match.index || cursor > end || end === text.length) continue;
    const raw = text.slice(starts.lastIndex, end);
    const separator = boundary(raw, 0, ';');
    const args = raw.slice(0, separator);
    const alias = raw.slice(separator + 1).trim();
    if (!/^[A-Za-z_]\w*$/.test(alias)) return null;
    const parameters: Record<string, string> = {};
    let at = 0;
    while (at < args.length) {
      while (/\s/.test(args[at] || '') && at < args.length) at++;
      if (at === args.length) break;
      const key = /^([A-Za-z_]\w*)\s*=\s*/.exec(args.slice(at));
      if (!key) return null;
      at += key[0].length;
      const stop = boundary(args, at, ' \t\n\r');
      parameters[key[1]] = args.slice(at, stop);
      at = stop;
    }
    return { start: match.index, end: end + 1, name: match[1], alias, parameters };
  }
  return null;
}
export function directiveHeader(name: string, alias: string, parameters: Record<string, string>): string {
  if (!/^[A-Za-z_]\w*$/.test(name) || !/^[A-Za-z_]\w*$/.test(alias)) throw new Error('标签名和结果变量必须是合法标识符');
  return `<@${name}${Object.entries(parameters).filter(([, value]) => value.trim()).map(([key, value]) => ` ${key}=${value.trim()}`).join('')}; ${alias}>`;
}
