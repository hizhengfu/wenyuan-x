import PdfWorker from 'pdfjs-dist/build/pdf.worker.min.mjs?worker';
import type { PDFDocumentLoadingTask, PDFWorker as PdfWorkerHandle } from 'pdfjs-dist';

/** 仅读取调用方已经核对的本地 PDF；Worker 和静态字体资源随应用提供。 */
export async function openCurrentPdf(source: string, signal: AbortSignal) {
  if (!source.startsWith('blob:') || new URL(source).origin !== window.location.origin)
    throw new Error('PDF 阅读器只接受当前页面的本地文件');
  const pdfjs = await import('pdfjs-dist');
  signal.throwIfAborted();
  let worker: Worker | undefined;
  let handle: PdfWorkerHandle | undefined;
  let task: PDFDocumentLoadingTask | undefined;
  let closing: Promise<void> | undefined;
  const close = () => {
    if (closing) return closing;
    signal.removeEventListener('abort', abort);
    // 优先让 PDF.js 结束传输；损坏或失联的 Worker 也必须在有限时间内释放。
    let timeout: ReturnType<typeof setTimeout>;
    const deadline = new Promise<void>((resolve) => {
      timeout = setTimeout(resolve, 1000);
    });
    closing = Promise.race([
      Promise.resolve()
        .then(() => task?.destroy())
        .catch(() => {}),
      deadline,
    ])
      .then(() => {
        clearTimeout(timeout);
        try {
          handle?.destroy();
        } finally {
          worker?.terminate();
        }
      })
      .catch(() => {});
    return closing;
  };
  const abort = () => {
    void close();
  };
  try {
    worker = new PdfWorker();
    handle = new pdfjs.PDFWorker({ port: worker as any });
    const assets = new URL(`${import.meta.env.BASE_URL || './'}pdfjs/`, window.location.href.split('#')[0]).href;
    task = pdfjs.getDocument({
      url: source,
      worker: handle,
      withCredentials: false,
      cMapUrl: assets + 'cmaps/',
      cMapPacked: true,
      standardFontDataUrl: assets + 'standard_fonts/',
      wasmUrl: assets + 'wasm/',
      iccUrl: assets + 'iccs/',
      isEvalSupported: false,
      enableXfa: false,
    });
    // 调用方可能在引擎返回前离开；加载失败仍需有人接收。
    void task.promise.catch(() => {});
    signal.addEventListener('abort', abort, { once: true });
    if (signal.aborted) abort();
    return { promise: task.promise, close };
  } catch (error) {
    await close();
    throw error;
  }
}
