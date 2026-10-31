import { describe, expect, it } from "vitest";
import {
  parseTemplatePreviewQuery,
  templatePreviewQuery,
} from "../templatePreviewContext";

const base = {
  siteId: "site-1",
  templateCode: "default",
  themeCode: "blue",
  kind: "HOME",
};
describe("独立模板预览条件", () => {
  it("保留搜索条件和查询全部数据的 pageSize=0", () => {
    const context = {
      siteId: "site-1",
      templateCode: "default",
      themeCode: "blue",
      kind: "SEARCH",
      module: "ARTICLE",
      keywords: "历史 & 建筑",
      pageNum: 2,
      pageSize: 0,
    };
    const query = templatePreviewQuery(context, 390);
    expect(parseTemplatePreviewQuery(query)).toEqual({ width: 390, context });
    expect(query).not.toHaveProperty("revisionId");
    expect(query).not.toHaveProperty("projectId");
    expect(query).not.toHaveProperty("html");
  });
  it("拒绝缺少站点或内容范围的链接", () => {
    expect(() => parseTemplatePreviewQuery({ ...base, siteId: "" })).toThrow(
      "siteId",
    );
    expect(() =>
      parseTemplatePreviewQuery({
        ...base,
        kind: "CONTENT",
        contentId: "content-1",
      }),
    ).toThrow("categoryId");
    expect(() =>
      parseTemplatePreviewQuery({
        ...base,
        kind: "CONTENT",
        categoryId: "category-1",
      }),
    ).toThrow("contentId");
  });
  it("保留详情的栏目及内容 ID，不降级为首页", () => {
    const result = parseTemplatePreviewQuery({
      ...base,
      kind: "CONTENT",
      categoryId: "category-1",
      contentId: "content-1",
      width: "768",
    });
    expect(result.context).toMatchObject({
      kind: "CONTENT",
      categoryId: "category-1",
      contentId: "content-1",
    });
    expect(() =>
      parseTemplatePreviewQuery({ ...base, kind: "UNKNOWN" }),
    ).toThrow("无效预览页面类型");
  });
  it("拒绝重复值、非法分页和非标准预览宽度", () => {
    expect(() =>
      parseTemplatePreviewQuery({ ...base, siteId: ["site-1", "site-2"] }),
    ).toThrow("siteId");
    for (const pageNum of ["0", "1.5", "-1", "Infinity", "10000001"])
      expect(() => parseTemplatePreviewQuery({ ...base, pageNum })).toThrow(
        "pageNum",
      );
    expect(() => parseTemplatePreviewQuery({ ...base, width: "400" })).toThrow(
      "无效预览尺寸",
    );
  });
});
