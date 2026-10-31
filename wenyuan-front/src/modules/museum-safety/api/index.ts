import request from '@/utils/request';
import type {
  SafetyDeviceProfileCommand,
  SafetyDocumentAccessCommand,
  SafetyDocumentAccessScope,
  SafetyDocumentCommand,
  SafetyDocumentScopeAccessCommand,
  SafetyDocumentReviewCommand,
  SafetyDocumentVersionCommand,
  SafetyDomain,
  SafetyDutyAdjustmentCommand,
  SafetyDutyAdjustmentReviewCommand,
  SafetyDutyLogCommand,
  SafetyDutyReviewCommand,
  SafetyDutyScheduleCommand,
  SafetyDutyPersonnelCommand,
  SafetyInspectionIssueCommand,
  SafetyCheckpointCommand,
  SafetyRouteCommand,
  SafetyInspectionPlanCommand,
  SafetyInspectionRecordCommand,
  SafetyIssueActionCommand,
  SafetyLearningRecordCommand,
  SafetyMaintenanceCompleteCommand,
  SafetyMaintenancePlanCommand,
  SafetyMaintenanceTaskCommand,
  SafetyNoticeCommand,
  SafetyReportCommand,
  SafetyRiskActionCommand,
  SafetySystemCommand,
  SafetySystemDevicesCommand,
  SafetyTrainingPlanCommand,
} from '../typings';

const BASE = '/museum/safety';
const MANAGEMENT_BASE = `${BASE}/management`;

export const safetyApi = {
  portfolioOverview: (params: Record<string, unknown> = {}) =>
    request({ url: `${BASE}/portfolio-overview`, method: 'get', params }),
  overview: (params: Record<string, unknown> = {}) => request({ url: `${BASE}/overview`, method: 'get', params }),
  domain: (domain: SafetyDomain, params: Record<string, unknown> = {}) =>
    request({ url: `${BASE}/domain/${domain}`, method: 'get', params }),
  lightningOverview: (params: Record<string, unknown> = {}) =>
    request({ url: `${BASE}/lightning/overview`, method: 'get', params }),
  systems: (params: Record<string, unknown> = {}) => request({ url: `${BASE}/systems`, method: 'get', params }),
  system: (id: string) => request({ url: `${BASE}/systems/${id}`, method: 'get' }),
  createSystem: (data: SafetySystemCommand) => request({ url: `${BASE}/systems`, method: 'post', data }),
  updateSystem: (id: string, data: SafetySystemCommand) =>
    request({ url: `${BASE}/systems/${id}`, method: 'put', data }),
  deleteSystem: (id: string) => request({ url: `${BASE}/systems/${id}`, method: 'delete' }),
  systemDeviceCandidates: (id: string) => request({ url: `${BASE}/systems/${id}/device-candidates`, method: 'get' }),
  replaceSystemDevices: (id: string, data: SafetySystemDevicesCommand) =>
    request({ url: `${BASE}/systems/${id}/devices`, method: 'put', data }),
  risks: (params: Record<string, unknown>) => request({ url: `${BASE}/risks`, method: 'get', params }),
  risk: (id: string) => request({ url: `${BASE}/risks/${id}`, method: 'get' }),
  createRisk: (data: Record<string, unknown>) => request({ url: `${BASE}/risks`, method: 'post', data }),
  riskAction: (id: string, data: SafetyRiskActionCommand) =>
    request({ url: `${BASE}/risks/${id}/actions`, method: 'post', data }),
  createRiskLinkage: (id: string, data: { type: string; instruction: string; expectedTriggerLogId: string }) =>
    request({ url: `${BASE}/risks/${id}/linkages`, method: 'post', data }),
  riskLinkages: (id: string) => request({ url: `${BASE}/risks/${id}/linkages`, method: 'get' }),
  riskLinkageAction: (id: string, taskId: string, data: Record<string, unknown>) =>
    request({ url: `${BASE}/risks/${id}/linkages/${taskId}/actions`, method: 'post', data }),
  batchAssign: (riskIds: string[], assigneeId: string) =>
    request({ url: `${BASE}/risks/batch-assign`, method: 'post', data: { riskIds, assigneeId } }),
  reports: (params: Record<string, unknown>) => request({ url: `${BASE}/reports`, method: 'get', params }),
  report: (id: string) => request({ url: `${BASE}/reports/${id}`, method: 'get' }),
  createReport: (data: SafetyReportCommand) => request({ url: `${BASE}/reports`, method: 'post', data }),
  regenerateReport: (id: string) => request({ url: `${BASE}/reports/${id}/regenerate`, method: 'post' }),
  downloadReport: (id: string) =>
    request({ url: `${BASE}/reports/${id}/download`, method: 'get', responseType: 'blob' }),
  managementWorkbench: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/workbench`, method: 'get', params }),
  videoCameras: (params: { orgId: string }) =>
    request({ url: `${MANAGEMENT_BASE}/video/cameras`, method: 'get', params }),
  managementDocuments: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/documents`, method: 'get', params }),
  managementDocumentPage: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/documents/page`, method: 'get', params }),
  managementDocument: (id: string) => request({ url: `${MANAGEMENT_BASE}/documents/${id}`, method: 'get' }),
  downloadManagementDocument: (id: string) =>
    request({ url: `${MANAGEMENT_BASE}/documents/${id}/download`, method: 'get', responseType: 'blob' }),
  createManagementDocument: (data: SafetyDocumentCommand) =>
    request({ url: `${MANAGEMENT_BASE}/documents`, method: 'post', data }),
  createManagementDocuments: (documents: SafetyDocumentCommand[]) =>
    request({ url: `${MANAGEMENT_BASE}/documents/batch`, method: 'post', data: { documents } }),
  createManagementDocumentVersion: (id: string, data: SafetyDocumentVersionCommand) =>
    request({ url: `${MANAGEMENT_BASE}/documents/${id}/versions`, method: 'post', data }),
  submitManagementDocumentVersion: (versionId: string) =>
    request({ url: `${MANAGEMENT_BASE}/document-versions/${versionId}/submit`, method: 'post' }),
  reviewManagementDocumentVersion: (versionId: string, data: SafetyDocumentReviewCommand) =>
    request({ url: `${MANAGEMENT_BASE}/document-versions/${versionId}/reviews`, method: 'post', data }),
  replaceManagementDocumentAccess: (id: string, data: SafetyDocumentAccessCommand) =>
    request({ url: `${MANAGEMENT_BASE}/documents/${id}/access`, method: 'put', data }),
  managementDocumentAccess: (id: string) =>
    request({ url: `${MANAGEMENT_BASE}/documents/${id}/access`, method: 'get' }),
  managementDocumentAccessPolicies: (params: { orgId?: string; scopeType?: SafetyDocumentAccessScope } = {}) =>
    request({ url: `${MANAGEMENT_BASE}/document-access-policies`, method: 'get', params }),
  replaceManagementDocumentAccessPolicy: (data: SafetyDocumentScopeAccessCommand) =>
    request({ url: `${MANAGEMENT_BASE}/document-access-policies`, method: 'put', data }),
  archiveManagementDocument: (id: string) => request({ url: `${MANAGEMENT_BASE}/documents/${id}`, method: 'delete' }),
  managementNotices: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/notices`, method: 'get', params }),
  createManagementNotice: (data: SafetyNoticeCommand) =>
    request({ url: `${MANAGEMENT_BASE}/notices`, method: 'post', data }),
  updateManagementNotice: (id: string, data: SafetyNoticeCommand) =>
    request({ url: `${MANAGEMENT_BASE}/notices/${id}`, method: 'put', data }),
  archiveManagementNotice: (id: string) => request({ url: `${MANAGEMENT_BASE}/notices/${id}`, method: 'delete' }),
  dutyPersonnel: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/duty/personnel`, method: 'get', params }),
  dutyPersonnelPage: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/duty/personnel/page`, method: 'get', params }),
  upsertDutyPersonnel: (data: SafetyDutyPersonnelCommand) =>
    request({ url: `${MANAGEMENT_BASE}/duty/personnel`, method: 'post', data }),
  archiveDutyPersonnel: (id: string) => request({ url: `${MANAGEMENT_BASE}/duty/personnel/${id}`, method: 'delete' }),
  dutySchedules: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/duty/schedules`, method: 'get', params }),
  dutySchedulesPage: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/duty/schedules/page`, method: 'get', params }),
  createDutySchedule: (data: SafetyDutyScheduleCommand) =>
    request({ url: `${MANAGEMENT_BASE}/duty/schedules`, method: 'post', data }),
  dutyAdjustments: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/duty/adjustments`, method: 'get', params }),
  dutyAdjustmentsPage: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/duty/adjustments/page`, method: 'get', params }),
  createDutyAdjustment: (data: SafetyDutyAdjustmentCommand) =>
    request({ url: `${MANAGEMENT_BASE}/duty/adjustments`, method: 'post', data }),
  reviewDutyAdjustment: (id: string, data: SafetyDutyAdjustmentReviewCommand) =>
    request({ url: `${MANAGEMENT_BASE}/duty/adjustments/${id}/review`, method: 'post', data }),
  dutyLogs: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/duty/logs`, method: 'get', params }),
  dutyLogsPage: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/duty/logs/page`, method: 'get', params }),
  attendanceLedger: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/duty/attendance-ledger`, method: 'get', params }),
  attendanceLedgerPage: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/duty/attendance-ledger/page`, method: 'get', params }),
  createDutyLog: (data: SafetyDutyLogCommand) => request({ url: `${MANAGEMENT_BASE}/duty/logs`, method: 'post', data }),
  reviewDutyLog: (id: string, data: SafetyDutyReviewCommand) =>
    request({ url: `${MANAGEMENT_BASE}/duty/logs/${id}/review`, method: 'post', data }),
  inspectionPlans: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/inspections/plans`, method: 'get', params }),
  inspectionPlansPage: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/inspections/plans/page`, method: 'get', params }),
  createInspectionPlan: (data: SafetyInspectionPlanCommand) =>
    request({ url: `${MANAGEMENT_BASE}/inspections/plans`, method: 'post', data }),
  updateInspectionPlan: (id: string, data: SafetyInspectionPlanCommand) =>
    request({ url: `${MANAGEMENT_BASE}/inspections/plans/${id}`, method: 'put', data }),
  inspectionCheckpoints: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/inspections/checkpoints`, method: 'get', params }),
  inspectionCheckpointsPage: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/inspections/checkpoints/page`, method: 'get', params }),
  createInspectionCheckpoint: (data: SafetyCheckpointCommand) =>
    request({ url: `${MANAGEMENT_BASE}/inspections/checkpoints`, method: 'post', data }),
  updateInspectionCheckpoint: (id: string, data: SafetyCheckpointCommand) =>
    request({ url: `${MANAGEMENT_BASE}/inspections/checkpoints/${id}`, method: 'put', data }),
  inspectionRoutes: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/inspections/routes`, method: 'get', params }),
  inspectionRoutesPage: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/inspections/routes/page`, method: 'get', params }),
  createInspectionRoute: (data: SafetyRouteCommand) =>
    request({ url: `${MANAGEMENT_BASE}/inspections/routes`, method: 'post', data }),
  updateInspectionRoute: (id: string, data: SafetyRouteCommand) =>
    request({ url: `${MANAGEMENT_BASE}/inspections/routes/${id}`, method: 'put', data }),
  inspectionTasks: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/inspections/tasks`, method: 'get', params }),
  inspectionTasksPage: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/inspections/tasks/page`, method: 'get', params }),
  createInspectionTask: (planId: string, data: { scheduledAt?: number; inspectorId?: string }) =>
    request({ url: `${MANAGEMENT_BASE}/inspections/plans/${planId}/tasks`, method: 'post', data }),
  assignInspectionTask: (id: string, inspectorId: string) =>
    request({ url: `${MANAGEMENT_BASE}/inspections/tasks/${id}/inspector`, method: 'put', data: { inspectorId } }),
  inspectionRecords: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/inspections/records`, method: 'get', params }),
  inspectionRecordsPage: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/inspections/records/page`, method: 'get', params }),
  createInspectionRecord: (data: SafetyInspectionRecordCommand) =>
    request({ url: `${MANAGEMENT_BASE}/inspections/records`, method: 'post', data }),
  inspectionIssues: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/inspections/issues`, method: 'get', params }),
  inspectionIssuesPage: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/inspections/issues/page`, method: 'get', params }),
  createInspectionIssue: (recordId: string, data: SafetyInspectionIssueCommand) =>
    request({ url: `${MANAGEMENT_BASE}/inspections/records/${recordId}/issues`, method: 'post', data }),
  actOnInspectionIssue: (id: string, data: SafetyIssueActionCommand) =>
    request({ url: `${MANAGEMENT_BASE}/inspections/issues/${id}/actions`, method: 'post', data }),
  safetyDeviceProfiles: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/devices`, method: 'get', params }),
  safetyDeviceProfilePage: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/devices/page`, method: 'get', params }),
  upsertSafetyDeviceProfile: (deviceId: string, data: SafetyDeviceProfileCommand) =>
    request({ url: `${MANAGEMENT_BASE}/devices/${deviceId}`, method: 'put', data }),
  maintenancePlans: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/maintenance/plans`, method: 'get', params }),
  maintenancePlanPage: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/maintenance/plans/page`, method: 'get', params }),
  createMaintenancePlan: (data: SafetyMaintenancePlanCommand) =>
    request({ url: `${MANAGEMENT_BASE}/maintenance/plans`, method: 'post', data }),
  maintenanceTasks: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/maintenance/tasks`, method: 'get', params }),
  maintenanceTaskPage: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/maintenance/tasks/page`, method: 'get', params }),
  maintenanceRecords: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/maintenance/records`, method: 'get', params }),
  maintenanceRecordPage: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/maintenance/records/page`, method: 'get', params }),
  createMaintenanceTask: (data: SafetyMaintenanceTaskCommand) =>
    request({ url: `${MANAGEMENT_BASE}/maintenance/tasks`, method: 'post', data }),
  startMaintenanceTask: (id: string) =>
    request({ url: `${MANAGEMENT_BASE}/maintenance/tasks/${id}/start`, method: 'post' }),
  completeMaintenanceTask: (id: string, data: SafetyMaintenanceCompleteCommand) =>
    request({ url: `${MANAGEMENT_BASE}/maintenance/tasks/${id}/complete`, method: 'post', data }),
  trainingPlans: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/training/plans`, method: 'get', params }),
  trainingPlanPage: (params: Record<string, unknown> = {}) =>
    request({ url: `${MANAGEMENT_BASE}/training/plans/page`, method: 'get', params }),
  createTrainingPlan: (data: SafetyTrainingPlanCommand) =>
    request({ url: `${MANAGEMENT_BASE}/training/plans`, method: 'post', data }),
  learningRecords: (planId: string) =>
    request({ url: `${MANAGEMENT_BASE}/training/plans/${planId}/records`, method: 'get' }),
  recordLearning: (data: SafetyLearningRecordCommand) =>
    request({ url: `${MANAGEMENT_BASE}/training/records`, method: 'post', data }),
};

export function unwrapResponse<T>(response: any): T {
  let value = response;
  for (let index = 0; index < 5; index += 1) {
    if (value === null || typeof value !== 'object' || !('data' in value)) break;
    value = value.data;
  }
  return value as T;
}
