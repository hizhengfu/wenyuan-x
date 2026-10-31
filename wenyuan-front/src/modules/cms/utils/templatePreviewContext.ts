import type { PreviewContext } from "../api/templateFiles";

/** 独立预览只携带目录和查询条件；不把渲染 HTML、资源凭证或登录凭据放进 URL。 */
export function templatePreviewQuery(context: PreviewContext, width: number) {
  const query: Record<string, string> = { width: String(width) };
  for (const key of [
    "siteId",
    "templateCode",
    "themeCode",
    "kind",
    "categoryId",
    "contentId",
    "module",
    "keywords",
    "pageNum",
    "pageSize",
  ] as const) {
    const value = context[key];
    if (value !== undefined) query[key] = String(value);
  }
  return query;
}

export function parseTemplatePreviewQuery(query: Record<string, unknown>) {
  function text(key: string, required = false): string | undefined {
    const value = query[key];
    if (value !== undefined && value !== null && typeof value !== "string")
      throw new Error(`无效预览参数：${key}`);
    if (required && (typeof value !== "string" || !value.trim()))
      throw new Error(`缺少预览参数：${key}`);
    return typeof value === "string" ? value : undefined;
  }
  function number(key: string, fallback: number, max: number, min = 0) {
    const value = text(key);
    if (value === undefined) return fallback;
    const parsed = Number(value);
    if (
      !/^\d+$/.test(value) ||
      !Number.isSafeInteger(parsed) ||
      parsed < min ||
      parsed > max
    )
      throw new Error(`无效预览参数：${key}`);
    return parsed;
  }
  const kind = text("kind", true)!;
  if (!["HOME", "CATEGORY", "CONTENT", "SEARCH"].includes(kind))
    throw new Error("无效预览页面类型");
  const context: PreviewContext = {
    siteId: text("siteId", true)!,
    templateCode: text("templateCode", true)!,
    themeCode: text("themeCode"),
    kind,
    categoryId: text("categoryId", kind === "CATEGORY" || kind === "CONTENT"),
    contentId: text("contentId", kind === "CONTENT"),
    module: text("module", kind === "SEARCH"),
    keywords: text("keywords"),
    pageNum: number("pageNum", 1, 10000000, 1),
    pageSize: number("pageSize", 20, 100000),
  };
  const width = number("width", 0, 768);
  if (![0, 390, 768].includes(width)) throw new Error("无效预览尺寸");
  return { context, width };
}
