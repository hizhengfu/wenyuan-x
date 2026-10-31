import type { CollectionTransferVo } from '../typings/collection';

type TransferTransportEvidence = Pick<CollectionTransferVo, 'executedAt' | 'dossier' | 'execution'>;

const hasText = (value: unknown) => typeof value === 'string' && value.trim().length > 0;

export function transferTransportEvidenceIncomplete(data: TransferTransportEvidence): boolean {
  if (!data.executedAt) return false;
  const dossier = data.dossier;
  const execution = data.execution;
  return !dossier || !execution ||
    [dossier.transportStandardReference, dossier.transportStandardApplicability, dossier.transportCarrierName,
      dossier.transportCarrierCapabilityFileIds, dossier.transportPlanFileIds,
      execution.appliedTransportStandardReference, execution.packingCheckFileIds].some((value) => !hasText(value)) ||
    execution.packingStandardConfirmed !== true;
}

export function transferHandoverProgress(data: CollectionTransferVo): string {
  const status = data.terminationClosed ? '已批准，终止执行'
    : data.terminationPending ? '已批准，终止办理中'
    : data.currentNoticeComplete ? '当前结果公告已留档'
    : data.executedAt ? '已交接，待公告'
    : data.flowStatus === 'PASS' ? '已批准，待交接'
    : '准备或审批中';
  return transferTransportEvidenceIncomplete(data) ? `${status} · 包装运输材料待核` : status;
}
