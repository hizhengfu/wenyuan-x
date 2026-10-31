import type { ProtectionPolicyVo } from '../typings';

export interface ProtectionPolicyContext {
  orgId: string;
  locationIds: string[];
  metricCodes: string[];
  policyId: string;
}
function values(value: unknown): string[] {
  const items = Array.isArray(value) ? value : [value];
  return [
    ...new Set(
      items
        .filter((item): item is string => typeof item === 'string')
        .map((item) => item.trim())
        .filter(Boolean),
    ),
  ];
}
export function readProtectionPolicyContext(query: Record<string, unknown> = {}): ProtectionPolicyContext {
  return {
    orgId: values(query.orgId)[0] || '',
    locationIds: values(query.locationId),
    metricCodes: values(query.metricCode).map((value) => value.toUpperCase()),
    policyId: values(query.policyId)[0] || '',
  };
}
export function hasProtectionPolicyContext(context: ProtectionPolicyContext): boolean {
  return Boolean(context.orgId || context.locationIds.length || context.metricCodes.length || context.policyId);
}
export function matchesProtectionPolicyContext(row: ProtectionPolicyVo, context: ProtectionPolicyContext): boolean {
  return (
    (!context.orgId || row.orgId === context.orgId) &&
    (!context.policyId || row.id === context.policyId) &&
    (!context.locationIds.length ||
      row.targetScope === 'ALL_LOCATIONS' ||
      row.locationIds.some((id) => context.locationIds.includes(id))) &&
    (!context.metricCodes.length ||
      row.items.some((item) => context.metricCodes.includes((item.metricCode || '').toUpperCase())))
  );
}
