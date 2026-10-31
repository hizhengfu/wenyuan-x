import { ref } from 'vue';
import { useCurrentImageLifecycle } from '@/utils/currentImage';
import { loanApi } from '../api/loan';
import { requisitionApi } from '../api/requisition';
import { readonlyFileIds, type CollectionReadonlyFile } from './readonlyFiles';

/** 归还来源原件只使用现时授权读取的来源单 OUT_EXECUTED 档案。 */
export function useReturnSourceOriginals(source: () => Record<string, any>, enabled: () => boolean = () => true) {
  const archive = ref<{ id: string; digest?: string; files: CollectionReadonlyFile[] }>();
  const error = ref('');
  const loading = ref(false);
  let epoch = 0;
  let abort: AbortController | undefined;
  function clear() {
    epoch++;
    abort?.abort();
    abort = undefined;
    archive.value = undefined;
    error.value = '';
    loading.value = false;
  }
  const { eligible } = useCurrentImageLifecycle(
    () => [source().sourceBizType, source().sourceBizId, source().sourceSnapshot, enabled()],
    clear,
    () => { void read(); },
  );
  async function read() {
    const current = source();
    const id = current.sourceBizId;
    const type = current.sourceBizType;
    const client = type === 'COLLECTION_LOAN' ? loanApi : type === 'COLLECTION_REQUISITION' ? requisitionApi : undefined;
    if (!eligible.value || !enabled() || !id || !current.sourceSnapshot || !client) return;
    const ticket = epoch;
    abort = new AbortController();
    loading.value = true;
    try {
      const result: any = await client.get(id, abort.signal);
      if (ticket !== epoch || !eligible.value || !enabled()) return;
      const detail = result.data;
      if (detail?.id !== id || !Array.isArray(detail.archives)) throw new Error('来源案卷不匹配');
      const stages = detail.archives.filter((stage: any) => stage.phase === 'OUT_EXECUTED');
      if (stages.length !== 1) throw new Error('来源出库档案不唯一');
      const selected = stages[0];
      const snapshot = typeof selected.snapshot === 'string' ? JSON.parse(selected.snapshot) : selected.snapshot;
      if (!selected.id || snapshot?.request?.id !== id || !Array.isArray(selected.files)) throw new Error('来源出库档案不匹配');
      const requested = [current.sourceSnapshot.agreementFileIds, current.sourceSnapshot.outSignedFileIds,
        ...(current.sourceSnapshot.items || []).map((item: any) => item.outboundCondition?.imageFileIds)].flatMap(readonlyFileIds);
      if (requested.some(fileId => !selected.files.some((file: CollectionReadonlyFile) => (file.fileId || file.id) === fileId)))
        throw new Error('来源出库档案未包含所需原件');
      archive.value = { id: selected.id, digest: selected.digest, files: selected.files };
    } catch {
      if (ticket === epoch) error.value = '来源出库原件读取失败，请核对当前授权或从来源案卷查看。';
    } finally {
      if (ticket === epoch) loading.value = false;
    }
  }
  function files(value: unknown): CollectionReadonlyFile[] {
    const ids = readonlyFileIds(value);
    return (archive.value?.files || []).filter(file => ids.includes(String(file.fileId || file.id || '')));
  }
  function originalUrl(fileId: string): string | undefined {
    const current = source();
    const selected = archive.value;
    const kind = current.sourceBizType === 'COLLECTION_LOAN' ? 'loan' : current.sourceBizType === 'COLLECTION_REQUISITION' ? 'requisition' : undefined;
    if (!selected || !kind || !selected.files.some(file => (file.fileId || file.id) === fileId)) return undefined;
    return `/collection/${kind}/${encodeURIComponent(current.sourceBizId)}/original/${encodeURIComponent(selected.id)}/${encodeURIComponent(fileId)}`;
  }
  return { archive, error, loading, files, originalUrl };
}
