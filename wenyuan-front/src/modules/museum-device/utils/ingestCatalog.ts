import type { IngestBuiltInConnectorDefinition, IngestCatalog, IngestCodecDefinition, IngestDeviceModelDefinition, IngestProtocolDefinition, StandardMetricDefinition } from '../typings';

function records(value: unknown): Array<Record<string, unknown>> {
  return Array.isArray(value) ? value.filter((item): item is Record<string, unknown> => item !== null && typeof item === 'object') : [];
}
function strings(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];
}
export function normalizeIngestCatalog(value: unknown): IngestCatalog {
  const source = value !== null && typeof value === 'object' ? (value as Record<string, unknown>) : {};
  return {
    protocols: records(source.protocols).map((item) => ({ ...item, transports: strings(item.transports) }) as unknown as IngestProtocolDefinition),
    codecs: records(source.codecs).map((item) => ({ ...item, protocolCodes: strings(item.protocolCodes), models: strings(item.models), metricKeys: strings(item.metricKeys) }) as unknown as IngestCodecDefinition),
    deviceModels: records(source.deviceModels).map((item) => ({ ...item, protocolCodes: strings(item.protocolCodes), metrics: records(item.metrics), dynamicMetrics: item.dynamicMetrics === true }) as unknown as IngestDeviceModelDefinition),
    builtInConnectors: records(source.builtInConnectors).map((item) => ({ ...item, protocolVersions: strings(item.protocolVersions), networkAddresses: records(item.networkAddresses), enabled: item.enabled === true, tlsEnabled: item.tlsEnabled === true }) as unknown as IngestBuiltInConnectorDefinition),
    standardMetrics: records(source.standardMetrics).map((item) => ({
      ...item,
      aliases: strings(item.aliases),
      valueFields: records(item.valueFields),
    }) as unknown as StandardMetricDefinition),
  };
}
