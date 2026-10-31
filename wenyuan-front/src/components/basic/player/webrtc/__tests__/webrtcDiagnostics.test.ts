import { describe, expect, it } from 'vitest';
import { classifyInboundVideoCodec } from '../h265Capability';
import {
  buildHevcLevelCompatibilityDiagnostics,
  collectWebrtcRuntimeDiagnostics,
  parseH265SdpLevel,
  parseHevcCodecLevel,
} from '../webrtcDiagnostics';

function statsReport(...items: Record<string, unknown>[]): RTCStatsReport {
  return {
    forEach(callback: (value: unknown, key: string, parent: RTCStatsReport) => void) {
      for (const item of items) callback(item, String(item.id ?? ''), this as RTCStatsReport);
    },
  } as unknown as RTCStatsReport;
}

describe('WebRTC runtime diagnostics', () => {
  it('collects the actual inbound codec, media counters and selected ICE candidate pair', () => {
    const diagnostics = collectWebrtcRuntimeDiagnostics(
      statsReport(
        {
          id: 'codec-h265',
          type: 'codec',
          mimeType: 'video/H265',
          payloadType: 49,
          sdpFmtpLine: 'profile-id=1;level-id=93',
        },
        { id: 'codec-rtx', type: 'codec', mimeType: 'video/rtx', payloadType: 50 },
        {
          id: 'inbound-video',
          type: 'inbound-rtp',
          kind: 'video',
          codecId: 'codec-h265',
          bytesReceived: 125_000,
          packetsReceived: 900,
          packetsLost: 3,
          framesReceived: 240,
          framesDecoded: 238,
          framesRendered: 237,
          frameWidth: 1920,
          frameHeight: 1080,
          framesPerSecond: 24,
          totalDecodeTime: 1.75,
          totalFreezesDuration: 0.4,
          decoderImplementation: 'VideoToolbox',
          powerEfficientDecoder: false,
          jitterBufferDelay: 2.5,
          jitterBufferEmittedCount: 240,
        },
        {
          id: 'inbound-rtx',
          type: 'inbound-rtp',
          kind: 'video',
          codecId: 'codec-rtx',
          bytesReceived: 5_000,
          packetsReceived: 40,
          totalDecodeTime: 99,
        },
        { id: 'transport-1', type: 'transport', selectedCandidatePairId: 'pair-1' },
        {
          id: 'pair-1',
          type: 'candidate-pair',
          state: 'succeeded',
          nominated: true,
          bytesSent: 800,
          bytesReceived: 130_000,
          currentRoundTripTime: 0.012,
          localCandidateId: 'local-1',
          remoteCandidateId: 'remote-1',
        },
        {
          id: 'local-1',
          type: 'local-candidate',
          candidateType: 'host',
          protocol: 'udp',
          address: '192.168.0.10',
          port: 51000,
          networkType: 'ethernet',
        },
        {
          id: 'remote-1',
          type: 'remote-candidate',
          candidateType: 'host',
          protocol: 'udp',
          address: '192.168.0.20',
          port: 8555,
        },
      ),
    );

    expect(diagnostics.inboundVideo).toMatchObject({
      codecMimeType: 'video/H265',
      codecPayloadType: 49,
      codecSdpFmtpLine: 'profile-id=1;level-id=93',
      bytesReceived: 125_000,
      packetsReceived: 900,
      framesDecoded: 238,
      frameWidth: 1920,
      frameHeight: 1080,
      framesPerSecond: 24,
      totalDecodeTime: 1.75,
      totalFreezesDuration: 0.4,
      decoderImplementation: 'VideoToolbox',
      powerEfficientDecoder: false,
      jitterBufferDelay: 2.5,
      jitterBufferEmittedCount: 240,
    });
    expect(classifyInboundVideoCodec(diagnostics.inboundVideo)).toBe('h265');
    expect(diagnostics.selectedCandidatePair).toMatchObject({
      id: 'pair-1',
      state: 'succeeded',
      selected: true,
      local: { candidateType: 'host', protocol: 'udp', address: '192.168.0.10' },
      remote: { candidateType: 'host', protocol: 'udp', address: '192.168.0.20' },
    });
  });

  it('leaves optional decoder diagnostics undefined when the browser does not expose them', () => {
    const diagnostics = collectWebrtcRuntimeDiagnostics(
      statsReport(
        { id: 'codec-h264', type: 'codec', mimeType: 'video/H264' },
        {
          id: 'inbound-video',
          type: 'inbound-rtp',
          kind: 'video',
          codecId: 'codec-h264',
          bytesReceived: 100,
          packetsReceived: 1,
        },
      ),
    );

    expect(diagnostics.inboundVideo?.frameWidth).toBeUndefined();
    expect(diagnostics.inboundVideo?.totalDecodeTime).toBeUndefined();
    expect(diagnostics.inboundVideo?.decoderImplementation).toBeUndefined();
    expect(diagnostics.inboundVideo?.powerEfficientDecoder).toBeUndefined();
    expect(diagnostics.inboundVideo?.jitterBufferDelay).toBeUndefined();
  });
});

describe('HEVC level diagnostics', () => {
  const h265Level31Sdp = [
    'm=video 9 UDP/TLS/RTP/SAVPF 48 49 96',
    'a=rtpmap:48 H265/90000',
    'a=fmtp:48 profile-id=1;level-id=153;max-recv-level-id=153',
    'a=rtpmap:49 H265/90000',
    'a=fmtp:49 profile-id=1;level-id=93;max-recv-level-id=93',
    'a=rtpmap:96 H264/90000',
  ].join('\r\n');

  it('parses the actual fMP4 HEVC level and the H265 SDP receive level', () => {
    expect(parseHevcCodecLevel('video/mp4; codecs="hvc1.1.6.L153.B0,flac"')).toEqual({
      codec: 'hvc1.1.6.L153.B0',
      levelId: 153,
      level: '5.1',
    });
    expect(parseH265SdpLevel(h265Level31Sdp, 49)).toMatchObject({
      payloadType: 49,
      levelId: 93,
      maxReceiveLevelId: 93,
      effectiveReceiveLevelId: 93,
      level: '3.1',
    });
  });

  it('flags a Level 5.1 source when the WebRTC path only receives Level 3.1', () => {
    expect(
      buildHevcLevelCompatibilityDiagnostics(
        'video/mp4; codecs="hvc1.1.6.L153.B0,flac"',
        h265Level31Sdp,
        h265Level31Sdp,
        49,
      ),
    ).toMatchObject({
      sourceLevelId: 153,
      sourceLevel: '5.1',
      negotiatedReceiveLevelId: 93,
      negotiatedReceiveLevel: '3.1',
      sourceExceedsNegotiatedLevel: true,
      recommendation: 'LOWER_CAMERA_HEVC_LEVEL_OR_USE_H264',
    });
  });
});
