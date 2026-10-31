export function collectPreviewDocument(content: string) {
  return `<!doctype html><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src https://mmbiz.qpic.cn https://mmbiz.qlogo.cn https://mp.weixin.qq.com; style-src 'unsafe-inline'"><style>body{font:16px/1.8 sans-serif;padding:16px;overflow-wrap:anywhere}img{max-width:100%;height:auto}table{max-width:100%}</style>${content}`;
}
