import { onActivated, onMounted, ref } from 'vue';
import { useCatalogOperation } from '@/modules/collection/composables/useCatalogOperation';
import { qualificationCertApi } from '../api/qualificationCert';
import { restorationApi } from '../api/restoration';
import type { QualificationCertVo, RestorationVo } from '../typings';
import { errorText } from '../utils/restorationError';

type Section = 'certificates' | 'completed' | 'emergency';
const labels: Record<Section, string> = { certificates: '资质到期预警', completed: '本月修复完成', emergency: '抢救性修复待办' };
const text = (value: unknown): value is string => typeof value === 'string' && !!value.trim();
const timestamp = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value) && !Number.isNaN(new Date(value).getTime());
function body(response: any) {
  if (!response || (response.status !== undefined && response.status !== 200) || response.data == null) throw Error('未收到有效数据');
  return response.data;
}
function rows(data: any): any[] {
  if (!Array.isArray(data) || data.some(row => !row || !text(row.id) || !text(row.orgId))
    || new Set(data.map(row => row.id)).size !== data.length) throw Error('记录编号、馆别或列表不完整');
  return data;
}
function projects(response: any): RestorationVo[] {
  const data = body(response), list = rows(data.list);
  // 请求全部记录时数量必须一致，不能把残缺页当成实际完成数或待办数。
  if (!Number.isSafeInteger(data.total) || data.total !== list.length || list.some(row => !text(row.collectionId)
    || !['DRAFT', 'ONGOING', 'DONE', 'CANCELLED'].includes(row.state))) throw Error('项目统计资料不完整');
  return list;
}
export function useRestorationDashboard() {
  const expiring = ref<QualificationCertVo[]>([]), certificatesReady = ref(false);
  const completedThisMonth = ref<number | null>(null), emergencyPending = ref<number | null>(null);
  const errors = ref<string[]>([]);
  const session = useCatalogOperation(() => {
    expiring.value = []; certificatesReady.value = false; completedThisMonth.value = null; emergencyPending.value = null; errors.value = [];
  });
  async function refresh() {
    if (!session.active.value) return;
    session.reset(); const ticket = session.begin('load'); if (ticket === null) return;
    const until = Date.now(), month = new Date(until); month.setDate(1); month.setHours(0, 0, 0, 0); const since = month.getTime();
    try {
      const results = await Promise.allSettled([
        Promise.resolve().then(() => qualificationCertApi.expiring(90)),
        Promise.resolve().then(() => restorationApi.page({ pageSize: 0, state: 'DONE', endDateStart: since, endDateEnd: until })),
        Promise.resolve().then(() => restorationApi.page({ pageSize: 0, urgencyLevel: 'EMERGENCY' })),
      ]);
      if (!session.current(ticket)) return;
      function apply(index: number, section: Section, accept: (response: any) => void) {
        try { const result = results[index]; if (result.status === 'rejected') throw result.reason; accept(result.value); }
        catch (e) { errors.value.push(`${labels[section]}读取失败：${errorText(e, '请重新核对')}`); }
      }
      apply(0, 'certificates', response => {
        const list = rows(body(response));
        if (list.some(row => !text(row.certNo) || !text(row.holderId) || !['ORG', 'PERSON'].includes(row.holderType)
          || row.enabled !== true || !timestamp(row.expireDate))) throw Error('证书正文不完整');
        expiring.value = list; certificatesReady.value = true;
      });
      apply(1, 'completed', response => {
        const list = projects(response);
        if (list.some(row => row.state !== 'DONE' || !timestamp(row.endDate) || row.endDate < since || row.endDate > until)) throw Error('完成时间或状态与本次统计范围不一致');
        completedThisMonth.value = list.length;
      });
      apply(2, 'emergency', response => {
        const list = projects(response);
        if (list.some(row => row.urgencyLevel !== 'EMERGENCY')) throw Error('项目不属于抢救性修复');
        emergencyPending.value = list.filter(row => !['DONE', 'CANCELLED'].includes(row.state!)).length;
      });
    } finally { session.finish(ticket); }
  }
  onMounted(refresh);
  let firstActivation = true;
  onActivated(() => { if (firstActivation) firstActivation = false; else void refresh(); });
  return { expiring, certificatesReady, completedThisMonth, emergencyPending, errors, loading: session.busy, active: session.active, refresh };
}
