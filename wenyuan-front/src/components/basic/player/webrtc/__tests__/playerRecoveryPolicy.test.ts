import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  classifyInboundVideoCodec,
  confirmH264Fallback,
  forgetH264Fallback,
  isExplicitCodecTerminalFailure,
  isH265RuntimeUnusable,
  probeH265Capability,
  requestH264Fallback,
  resetH265RuntimeUnusableForTests,
  resolveH265DecodeFailureAction,
  resolveH265Usable,
  resolveInitialPlaybackProfile,
  resolveSuccessfulPlaybackAction,
  shouldSwitchToH264AfterCodecTerminals,
  shouldFallbackH265FirstFrame,
  type H265PlaybackMode,
  type InboundVideoDecodeStats,
} from '../h265Capability';

function inboundStats(codecMimeType: string): InboundVideoDecodeStats {
  return {
    codecMimeType,
    bytesReceived: 64_000,
    packetsReceived: 480,
    packetsLost: 0,
    framesReceived: 0,
    framesDecoded: 0,
    framesRendered: 0,
  };
}

describe('H265 runtime evidence', () => {
  it('starts decode fallback only for stats-confirmed H265, never for H264 or unknown codec', () => {
    const h265 = inboundStats('video/H265');
    const h264 = inboundStats('video/H264');

    expect(classifyInboundVideoCodec(h265)).toBe('h265');
    expect(shouldFallbackH265FirstFrame(h265, true, true)).toBe(true);
    expect(shouldFallbackH265FirstFrame(h264, true, true)).toBe(false);
    expect(shouldFallbackH265FirstFrame({ ...h265, codecMimeType: '' }, true, true)).toBe(false);
    expect(shouldFallbackH265FirstFrame(h265, true, false)).toBe(false);
  });

  it('allows only one ORIGINAL to H264 switch and never cycles back', () => {
    expect(resolveH265DecodeFailureAction('ORIGINAL', false)).toBe('switch-to-h264');
    expect(resolveH265DecodeFailureAction('ORIGINAL', true)).toBe('stop');
    expect(resolveH265DecodeFailureAction('H264', true)).toBe('stop');
  });

  it('confirms only a real H264 first frame and rejects a fake H264 variant', () => {
    expect(resolveSuccessfulPlaybackAction('ORIGINAL', 'H.265')).toBe('clear-h264-fallback');
    expect(resolveSuccessfulPlaybackAction('ORIGINAL', 'H.264')).toBe('clear-h264-fallback');
    expect(resolveSuccessfulPlaybackAction('H264', 'H.264')).toBe('confirm-h264-fallback');
    expect(resolveSuccessfulPlaybackAction('H264', 'H.265')).toBe('h264-codec-mismatch');
    expect(resolveSuccessfulPlaybackAction('H264', 'UNKNOWN')).toBe('none');
  });

  it('uses explicit terminal codec evidence from every enabled mode regardless of capability preflight', () => {
    const modes: H265PlaybackMode[] = ['webrtc', 'mse'];
    const terminals = new Set<H265PlaybackMode | 'unknown'>(['webrtc']);
    const codecFailure = { type: 'CODEC_MISMATCH', code: 'UNSUPPORTED_CODEC', mode: 'webrtc' as const };
    const networkFailure = { type: 'SOURCE_CONNECTION', code: 'CONNECTION_TIMEOUT', mode: 'webrtc' as const };

    expect(isExplicitCodecTerminalFailure(codecFailure)).toBe(true);
    expect(isExplicitCodecTerminalFailure(networkFailure)).toBe(false);
    expect(shouldSwitchToH264AfterCodecTerminals('ORIGINAL', false, modes, terminals)).toBe(false);
    expect(
      shouldSwitchToH264AfterCodecTerminals(
        'ORIGINAL',
        false,
        modes,
        new Set<H265PlaybackMode | 'unknown'>(['unknown']),
      ),
    ).toBe(false);
    terminals.add('mse');
    expect(shouldSwitchToH264AfterCodecTerminals('ORIGINAL', false, modes, terminals)).toBe(true);
    expect(shouldSwitchToH264AfterCodecTerminals('H264', true, modes, terminals)).toBe(false);
  });
});

describe('30-day H264 fallback cache', () => {
  beforeEach(() => resetH265RuntimeUnusableForTests());

  it('does not persist until H264 is confirmed by a first frame', () => {
    requestH264Fallback('camera-1');
    expect(isH265RuntimeUnusable('camera-1')).toBe(false);

    confirmH264Fallback('camera-1');
    expect(isH265RuntimeUnusable('camera-1')).toBe(true);
  });

  it('uses a confirmed result on the next start and otherwise probes ORIGINAL', () => {
    expect(resolveInitialPlaybackProfile('camera-1')).toBe('ORIGINAL');
    requestH264Fallback('camera-1');
    expect(resolveInitialPlaybackProfile('camera-1')).toBe('ORIGINAL');
    confirmH264Fallback('camera-1');
    expect(resolveInitialPlaybackProfile('camera-1')).toBe('H264');
  });

  it('expires after exactly 30 days', () => {
    vi.useFakeTimers();
    try {
      const startedAt = new Date('2026-08-12T00:00:00Z');
      vi.setSystemTime(startedAt);
      requestH264Fallback('camera-1');
      confirmH264Fallback('camera-1');

      vi.setSystemTime(startedAt.getTime() + 30 * 24 * 60 * 60 * 1_000 - 1);
      expect(resolveInitialPlaybackProfile('camera-1')).toBe('H264');
      vi.setSystemTime(startedAt.getTime() + 30 * 24 * 60 * 60 * 1_000);
      expect(resolveInitialPlaybackProfile('camera-1')).toBe('ORIGINAL');
    } finally {
      vi.useRealTimers();
    }
  });

  it('isolates confirmed fallback by stream type while keeping legacy camera-only reads compatible', () => {
    requestH264Fallback('camera-1', 'L0');
    confirmH264Fallback('camera-1', 'L0');

    expect(resolveInitialPlaybackProfile('camera-1', 'L0')).toBe('H264');
    expect(resolveInitialPlaybackProfile('camera-1', 'L1')).toBe('ORIGINAL');
    expect(resolveInitialPlaybackProfile('camera-1')).toBe('H264');

    forgetH264Fallback('camera-1', 'L0');
    expect(resolveInitialPlaybackProfile('camera-1', 'L0')).toBe('ORIGINAL');
  });
});

describe('shared H265 capability snapshot', () => {
  beforeEach(() => resetH265RuntimeUnusableForTests());

  it('probes once per page but resolves usability for each player mode independently', async () => {
    const getCapabilities = vi.fn(() => ({ codecs: [{ mimeType: 'video/H265' }] }));
    const isTypeSupported = vi.fn(() => false);
    vi.stubGlobal('RTCRtpReceiver', { getCapabilities });
    vi.stubGlobal('MediaSource', { isTypeSupported });
    vi.stubGlobal('navigator', {});
    try {
      const [left, right] = await Promise.all([probeH265Capability(), probeH265Capability()]);
      expect(left).toBe(right);
      expect(getCapabilities).toHaveBeenCalledTimes(1);
      expect(isTypeSupported).toHaveBeenCalledTimes(1);
      expect(resolveH265Usable(left, ['webrtc'])).toBe(true);
      expect(resolveH265Usable(left, ['mse'])).toBe(false);
    } finally {
      vi.unstubAllGlobals();
      resetH265RuntimeUnusableForTests();
    }
  });
});
