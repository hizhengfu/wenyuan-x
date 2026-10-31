import { describe, expect, it } from 'vitest';
import { directiveAt, directiveHeader } from '../templateDirectives';
describe('CMS 标签参数编辑', () => {
  it('保留数字、布尔列表、引号内分隔符和原生结果变量', () => {
    const text = '<@article_list_tag pageSize=10 sortDesc=[true, false] title="a > b; c"; articles>正文</@article_list_tag>';
    expect(directiveAt(text, 20)).toMatchObject({ name: 'article_list_tag', alias: 'articles', parameters: { pageSize: '10', sortDesc: '[true, false]', title: '"a > b; c"' } });
  });
  it('修改头部时保留循环和模板正文', () => {
    const text = '<@article_list_tag pageSize=10; list><#list list.list as item>${item.title}</#list></@article_list_tag>';
    const value = directiveAt(text, 15)!;
    const result = text.slice(0, value.start) + directiveHeader(value.name, value.alias, { pageSize: '5' }) + text.slice(value.end);
    expect(result).toContain('pageSize=5; list>');
    expect(result).toContain('<#list list.list as item>${item.title}</#list></@article_list_tag>');
  });
  it('支持带空白和比较运算的括号表达式', () => {
    const text = '<@article_tag id=(a > b)?string("one", "two"); article>body</@article_tag>';
    expect(directiveAt(text, 30)?.parameters.id).toBe('(a > b)?string("one", "two")');
  });
  it('跳过当前光标之前的其他标签', () => {
    const text = '<@site_tag siteId="s1"; site>s</@site_tag>\n<@article_tag id="a1"; article>a</@article_tag>';
    expect(directiveAt(text, text.indexOf('id="a1"'))?.name).toBe('article_tag');
  });
  it('复杂或不完整声明保留源码，不猜测重写', () => {
    expect(directiveAt('<@article_tag id="a"; a,b>', 10)).toBeNull();
    expect(directiveAt('<@article_tag id="a"', 10)).toBeNull();
    expect(directiveAt('plain text', 5)).toBeNull();
  });
  it('拒绝非法结果变量并省略空参数', () => {
    expect(() => directiveHeader('article_tag', 'bad alias', {})).toThrow();
    expect(directiveHeader('article_tag', 'article', { id: '"a1"', siteId: '' })).toBe('<@article_tag id="a1"; article>');
  });
});
