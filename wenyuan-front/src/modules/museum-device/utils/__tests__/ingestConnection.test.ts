import { describe, expect, it } from 'vitest';
import {
  connectorTransport,
  connectorTypeLabel,
  protocolSupportsConnector,
  resolveMqttConnectionTarget,
  suggestedRoutePattern,
  updateRoutePatternSuggestion,
} from '../ingestConnection';

describe('museum ingest connector transport', () => {
  it('maps each custom connector type to its actual transport', () => {
    expect(connectorTransport('MQTT_CLIENT')).toBe('MQTT');
    expect(connectorTransport('HTTP_CLIENT')).toBe('HTTP');
    expect(connectorTransport('TCP_CLIENT')).toBe('TCP');
    expect(connectorTransport('UDP_SERVER')).toBe('UDP');
    expect(connectorTransport('SERIAL_PORT')).toBe('SERIAL');
    expect(connectorTransport('CAN_BUS')).toBe('CAN');
  });

  it('describes client and server types from the ingest platform perspective', () => {
    expect(connectorTypeLabel('MQTT_CLIENT')).toBe('MQTT 客户端（连接消息服务器）');
    expect(connectorTypeLabel('MQTT_SERVER')).toBe('MQTT 服务端（等待设备连接）');
    expect(connectorTypeLabel('TCP_CLIENT')).toBe('TCP 客户端（连接设备）');
    expect(connectorTypeLabel('TCP_SERVER')).toBe('TCP 服务端（等待设备连接）');
    expect(connectorTypeLabel('UDP_SERVER')).toBe('UDP 服务端（监听报文）');
  });
});

describe('museum ingest MQTT connection direction', () => {
  const deviceProtocol = {
    mqttAccess: {
      topicTemplate: '/device/{username}/data',
      allowedTopicPatterns: ['/device/{username}/#'],
      defaultQos: 1,
      keepAliveSeconds: 60,
    },
  };
  const externalProtocol = { mqttAccess: null };

  it('shows device access parameters only for an internal MQTT Server route with mqttAccess', () => {
    expect(
      resolveMqttConnectionTarget({ connectorId: 'MQTT_MAIN', transport: 'MQTT' }, 'MQTT_SERVER', deviceProtocol),
    ).toBe('DEVICE_ACCESS');
    expect(
      resolveMqttConnectionTarget({ connectorId: 'MQTT_MAIN', transport: 'MQTT' }, 'MQTT_SERVER', externalProtocol),
    ).toBeNull();
  });

  it('shows upstream Broker parameters for an external MQTT client route without mqttAccess', () => {
    expect(
      resolveMqttConnectionTarget(
        { connectorId: 'TAIYIHENG_MQTT', transport: 'MQTT' },
        'MQTT_CLIENT',
        externalProtocol,
      ),
    ).toBe('UPSTREAM_BROKER');
  });

  it('does not offer MQTT connection parameters for non-MQTT routes', () => {
    expect(
      resolveMqttConnectionTarget({ connectorId: 'HTTP_VENDOR', transport: 'HTTP' }, 'HTTP_CLIENT', externalProtocol),
    ).toBeNull();
  });

  it('excludes external-Broker-only protocols from the built-in MQTT Server', () => {
    expect(protocolSupportsConnector('MQTT_MAIN', deviceProtocol)).toBe(true);
    expect(protocolSupportsConnector('MQTT_MAIN', externalProtocol)).toBe(false);
    expect(protocolSupportsConnector('TAIYIHENG_MQTT', externalProtocol)).toBe(true);
  });
});

describe('museum ingest route pattern suggestion', () => {
  const mqttConnector = { type: 'MQTT_CLIENT' as const, topic: '/vendor/+/uplink' };

  it('uses the MQTT subscription topic until a protocol provides a more specific default', () => {
    expect(suggestedRoutePattern(mqttConnector, undefined)).toBe('/vendor/+/uplink');
    expect(suggestedRoutePattern(mqttConnector, { defaultRoutePattern: '/vendor/model-a/#' })).toBe(
      '/vendor/model-a/#',
    );
  });

  it('replaces an earlier automatic suggestion when the selected protocol changes', () => {
    expect(updateRoutePatternSuggestion('/vendor/+/uplink', '/vendor/+/uplink', '/vendor/model-a/#')).toEqual({
      routePattern: '/vendor/model-a/#',
      trackedSuggestion: '/vendor/model-a/#',
    });
  });

  it('does not overwrite a route pattern entered by the user', () => {
    expect(updateRoutePatternSuggestion('/custom/topic', '/vendor/+/uplink', '/vendor/model-a/#')).toEqual({
      routePattern: '/custom/topic',
      trackedSuggestion: null,
    });
  });
});
