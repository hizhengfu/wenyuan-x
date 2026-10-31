import type {
  IngestBuiltInConnectorDefinition,
  IngestConnectorType,
  IngestProtocolDefinition,
  IngestRouteVo,
  IngestTransport,
} from '../typings';

export const BUILT_IN_MQTT_CONNECTOR_CODE = 'MQTT_MAIN';

export type MqttConnectionTarget = 'DEVICE_ACCESS' | 'UPSTREAM_BROKER';

export type ConnectorType = IngestConnectorType | IngestBuiltInConnectorDefinition['type'];

export interface RoutePatternSuggestionUpdate {
  routePattern: string | undefined;
  trackedSuggestion: string | null;
}

export function connectorTypeLabel(type: ConnectorType | undefined) {
  switch (type) {
    case 'MQTT_CLIENT':
      return 'MQTT 客户端（连接消息服务器）';
    case 'HTTP_CLIENT':
      return 'HTTP 客户端（轮询接口）';
    case 'TCP_CLIENT':
      return 'TCP 客户端（连接设备）';
    case 'MQTT_SERVER':
      return 'MQTT 服务端（等待设备连接）';
    case 'HTTP_SERVER':
      return 'HTTP 服务端（接收请求）';
    case 'TCP_SERVER':
      return 'TCP 服务端（等待设备连接）';
    case 'UDP_SERVER':
      return 'UDP 服务端（监听报文）';
    case 'SERIAL_PORT':
      return '串口直连';
    case 'CAN_BUS':
      return 'CAN 总线直连';
    default:
      return type || '未知通道';
  }
}

export function connectorTransport(type: IngestConnectorType | undefined): IngestTransport | undefined {
  switch (type) {
    case 'MQTT_CLIENT':
      return 'MQTT';
    case 'HTTP_CLIENT':
      return 'HTTP';
    case 'TCP_CLIENT':
      return 'TCP';
    case 'UDP_SERVER':
      return 'UDP';
    case 'SERIAL_PORT':
      return 'SERIAL';
    case 'CAN_BUS':
      return 'CAN';
    default:
      return undefined;
  }
}

/**
 * MQTT 只表示传输协议，连接方向必须由路由所选通道决定。
 * 内置 Server 的设备参数还要求协议显式声明 mqttAccess，避免为外部 Broker 协议伪造发布参数。
 */
export function resolveMqttConnectionTarget(
  route: Pick<IngestRouteVo, 'connectorId' | 'transport'>,
  connectorType: ConnectorType | undefined,
  protocol: Pick<IngestProtocolDefinition, 'mqttAccess'> | undefined,
): MqttConnectionTarget | null {
  if (route.transport !== 'MQTT') return null;
  if (connectorType === 'MQTT_CLIENT') return 'UPSTREAM_BROKER';
  if (route.connectorId === BUILT_IN_MQTT_CONNECTOR_CODE && connectorType === 'MQTT_SERVER' && protocol?.mqttAccess) {
    return 'DEVICE_ACCESS';
  }
  return null;
}

export function protocolSupportsConnector(
  connectorId: string | undefined,
  protocol: Pick<IngestProtocolDefinition, 'mqttAccess'>,
) {
  return connectorId !== BUILT_IN_MQTT_CONNECTOR_CODE || Boolean(protocol.mqttAccess);
}

export function suggestedRoutePattern(
  connector: { type?: ConnectorType; topic?: string | null } | undefined,
  protocol: Pick<IngestProtocolDefinition, 'defaultRoutePattern'> | undefined,
) {
  const protocolPattern = protocol?.defaultRoutePattern?.trim();
  if (protocolPattern) return protocolPattern;
  if (connector?.type !== 'MQTT_CLIENT') return undefined;
  return connector.topic?.trim() || undefined;
}

/**
 * 自动建议只覆盖空值或上一次由系统带出的值，避免切换通道/协议时覆盖用户手工修改。
 */
export function updateRoutePatternSuggestion(
  currentValue: string | undefined,
  previousSuggestion: string | null,
  nextSuggestion: string | undefined,
): RoutePatternSuggestionUpdate {
  const normalizedSuggestion = nextSuggestion?.trim() || undefined;
  const canReplace = !currentValue?.trim() || (Boolean(previousSuggestion) && currentValue === previousSuggestion);
  if (!canReplace) return { routePattern: currentValue, trackedSuggestion: null };
  return {
    routePattern: normalizedSuggestion,
    trackedSuggestion: normalizedSuggestion || null,
  };
}
