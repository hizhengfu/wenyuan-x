import request from "@/utils/request";
export interface TemplateRoot {
  id: string;
  name: string;
  kind: "templates" | "static";
  path: string;
  writable: boolean;
}
export interface TemplateSources {
  templates: { label: string; value: string }[];
  themes: { label: string; value: string }[];
}
export interface TemplateFile {
  path: string;
  size: number;
  editable: boolean;
  directory: boolean;
}
export interface TemplateChange {
  path: string;
  content?: string;
  base64?: string;
  expectedDigest?: string;
}
export interface TemplateTag {
  name: string;
  parameters: {
    name: string;
    type: string;
    purpose?: string;
    default?: unknown;
  }[];
  required: string[];
  output: string;
  body: string;
}
export interface PreviewContext {
  siteId: string;
  templateCode: string;
  themeCode?: string;
  kind: string;
  categoryId?: string;
  contentId?: string;
  module?: string;
  keywords?: string;
  pageNum: number;
  pageSize: number;
}
export interface PathCommand {
  path: string;
  targetPath?: string;
  targetRootId?: string;
  expectedDigest?: string;
}
const root = "cms/template/workspace";
function call<T>(url: string, options: object = {}): Promise<{ data: T }> {
  return request({ url, ...options }) as unknown as Promise<{ data: T }>;
}
export const templateFiles = {
  list: () => call<{ list: TemplateRoot[] }>(root),
  detail: (id: string) => call<TemplateRoot>(`${root}/${id}`),
  sources: () => call<TemplateSources>(`${root}/sources`),
  create: (
    name: string,
    templateCode: string | null,
    themeCode: string | null,
    rootId: string,
    staticRootId: string,
  ) =>
    call<{
      rootId: string;
      path: string;
      templateCode: string;
      themeCode: string;
    }>(root, {
      method: "post",
      data: { name, templateCode, themeCode, rootId, staticRootId },
    }),
  tags: () => call<TemplateTag[]>(`${root}/tags`),
  files: (id: string) => call<TemplateFile[]>(`${root}/${id}/files`),
  file: (id: string, path: string) =>
    call<{ content?: string; digest: string; directory: boolean }>(
      `${root}/${id}/file`,
      { params: { path } },
    ),
  save: (id: string, changes: TemplateChange[]) =>
    call<void>(`${root}/${id}/files`, { method: "put", data: { changes } }),
  directory: (id: string, path: string) =>
    call<void>(`${root}/${id}/directory`, { method: "post", data: { path } }),
  copy: (id: string, data: PathCommand) =>
    call<void>(`${root}/${id}/copy`, { method: "post", data }),
  rename: (id: string, data: PathCommand) =>
    call<void>(`${root}/${id}/rename`, { method: "post", data }),
  remove: (id: string, data: PathCommand) =>
    call<void>(`${root}/${id}/file`, { method: "delete", data }),
  apply: (siteId: string, templateCode?: string, themeCode?: string) =>
    call<void>(`${root}/apply`, {
      method: "post",
      data: { siteId, templateCode, themeCode },
    }),
  preview: (context: PreviewContext) =>
    call<{ html: string; entry: string }>(`${root}/preview`, {
      method: "post",
      data: context,
    }),
  import: (
    templateRootId: string,
    staticRootId: string,
    file: File,
    overwrite: boolean,
  ) => {
    const data = new FormData();
    data.append("file", file);
    data.append("templateRootId", templateRootId);
    data.append("staticRootId", staticRootId);
    data.append("overwrite", String(overwrite));
    return call<void>(`${root}/import`, { method: "post", data });
  },
  exportUrl: (id: string) => `${root}/${id}/export`,
};
