import { describe, expect, it } from 'vitest';
import {
  hasGo2rtcPlaybackProgress,
  resolveGo2rtcFirstFrameTimeoutAction,
  resolveGo2rtcMsePlaybackAdjustment,
  resolveGo2rtcMseTrimStart,
  resolveGo2rtcPrimaryVideoErrorAction,
} from '../go2rtcPlayerCore';
import type { InboundVideoDecodeStats, MseFirstFrameProgress } from '../h265Capability';

const emptyMse: MseFirstFrameProgress = {
  bytesReceived: 0,
  bytesAppended: 0,
  bufferedEnd: 0,
  successfulAppends: 0,
};

function inbound(codecMimeType: string): InboundVideoDecodeStats {
  return {
    codecMimeType,
    bytesReceived: 128_000,
    packetsReceived: 900,
    framesReceived: 0,
    framesDecoded: 0,
    framesRendered: 0,
  };
}

describe('go2rtc MSE live-edge policy', () => {
  it('never slows playback while it is 0.1 to 1 second behind live', () => {
    for (const lag of [0.11, 0.2, 0.5, 1]) {
      expect(resolveGo2rtcMsePlaybackAdjustment(10 - lag, 0, 10)).toEqual({
        playbackRate: 1.05,
        seekTo: null,
      });
    }
    expect(resolveGo2rtcMsePlaybackAdjustment(9.95, 0, 10)).toEqual({ playbackRate: 1, seekTo: null });
  });

  it('seeks safely near live and restores normal speed when lag is severe', () => {
    expect(resolveGo2rtcMsePlaybackAdjustment(6, 5, 10)).toEqual({ playbackRate: 1, seekTo: 9.5 });
    expect(resolveGo2rtcMsePlaybackAdjustment(8.5, 9, 10)).toEqual({ playbackRate: 1, seekTo: 9.5 });
    expect(resolveGo2rtcMsePlaybackAdjustment(8.5, 5, 10)).toEqual({ playbackRate: 1.05, seekTo: null });
  });

  it('trims from a high-water mark back to five seconds with a two-second minimum interval', () => {
    expect(resolveGo2rtcMseTrimStart(0, 7, null, 1_000)).toBeNull();
    expect(resolveGo2rtcMseTrimStart(0, 7.1, null, 1_000)).toBeCloseTo(2.1);
    expect(resolveGo2rtcMseTrimStart(0, 8, 1_000, 2_999)).toBeNull();
    expect(resolveGo2rtcMseTrimStart(0, 8, 1_000, 3_000)).toBe(3);
    expect(resolveGo2rtcMseTrimStart(3, 8, 1_000, 5_000)).toBeNull();
  });
});

describe('go2rtc first-frame timeout policy', () => {
  it('falls back only when WebRTC stats identify the actual inbound codec as H265', () => {
    expect(
      resolveGo2rtcFirstFrameTimeoutAction({
        inboundVideo: inbound('video/H265'),
        inboundProgressSamples: 2,
        mseCodecs: '',
        initialMse: emptyMse,
        currentMse: emptyMse,
      }),
    ).toBe('h265-decode-failed-webrtc');

    for (const codecMimeType of ['', 'video/H264']) {
      expect(
        resolveGo2rtcFirstFrameTimeoutAction({
          inboundVideo: inbound(codecMimeType),
          inboundProgressSamples: 2,
          mseCodecs: '',
          initialMse: emptyMse,
          currentMse: emptyMse,
        }),
      ).toBe('reconnect');
    }
  });

  it('does not mistake an H265 SDP offer or packets without progress for decode failure', () => {
    expect(
      resolveGo2rtcFirstFrameTimeoutAction({
        inboundVideo: inbound(''),
        inboundProgressSamples: 0,
        mseCodecs: '',
        initialMse: emptyMse,
        currentMse: emptyMse,
      }),
    ).toBe('reconnect');
    expect(
      resolveGo2rtcFirstFrameTimeoutAction({
        inboundVideo: inbound('video/H265'),
        inboundProgressSamples: 0,
        mseCodecs: '',
        initialMse: emptyMse,
        currentMse: emptyMse,
      }),
    ).toBe('reconnect');
    expect(
      resolveGo2rtcFirstFrameTimeoutAction({
        inboundVideo: { ...inbound('video/H265'), framesDecoded: 1 },
        inboundProgressSamples: 2,
        mseCodecs: 'video/mp4; codecs="hvc1.1.6.L153.B0"',
        initialMse: emptyMse,
        currentMse: {
          bytesReceived: 256_000,
          bytesAppended: 240_000,
          bufferedEnd: 2.4,
          successfulAppends: 4,
        },
      }),
    ).toBe('reconnect');
  });

  it('requires an actual HEVC MSE track plus append and buffered timeline progress', () => {
    const progressed = {
      bytesReceived: 256_000,
      bytesAppended: 240_000,
      bufferedEnd: 2.4,
      successfulAppends: 4,
    };
    expect(
      resolveGo2rtcFirstFrameTimeoutAction({
        inboundVideo: null,
        inboundProgressSamples: 0,
        mseProgressSamples: 2,
        mseCodecs: 'video/mp4; codecs="hvc1.1.6.L153.B0,mp4a.40.2"',
        initialMse: emptyMse,
        currentMse: progressed,
      }),
    ).toBe('h265-decode-failed-mse');
    expect(
      resolveGo2rtcFirstFrameTimeoutAction({
        inboundVideo: inbound('video/H264'),
        inboundProgressSamples: 2,
        mseProgressSamples: 2,
        mseCodecs: 'video/mp4; codecs="hvc1.1.6.L153.B0"',
        initialMse: emptyMse,
        currentMse: progressed,
      }),
    ).toBe('reconnect');
    expect(
      resolveGo2rtcFirstFrameTimeoutAction({
        inboundVideo: null,
        inboundProgressSamples: 0,
        mseProgressSamples: 2,
        mseCodecs: 'video/mp4; codecs="hvc1.1.6.L153.B0"',
        initialMse: emptyMse,
        currentMse: { ...progressed, bytesAppended: 0, bufferedEnd: 0, successfulAppends: 0 },
      }),
    ).toBe('reconnect');
    expect(
      resolveGo2rtcFirstFrameTimeoutAction({
        inboundVideo: null,
        inboundProgressSamples: 0,
        mseProgressSamples: 1,
        mseCodecs: 'video/mp4; codecs="hvc1.1.6.L153.B0"',
        initialMse: emptyMse,
        currentMse: progressed,
      }),
    ).toBe('reconnect');
  });

  it('does not treat one early H265 burst followed by a stall as a decode failure', () => {
    expect(
      resolveGo2rtcFirstFrameTimeoutAction({
        inboundVideo: inbound('video/H265'),
        inboundProgressSamples: 1,
        mseProgressSamples: 0,
        mseCodecs: '',
        initialMse: emptyMse,
        currentMse: emptyMse,
      }),
    ).toBe('reconnect');
  });
});

describe('go2rtc post-selection recovery policy', () => {
  it('ignores errors from an old source while the primary video is switching', () => {
    expect(
      resolveGo2rtcPrimaryVideoErrorAction({
        activeMode: 'webrtc',
        playingReported: false,
        mediaErrorCode: 3,
        mseCodecs: 'video/mp4; codecs="hvc1.1.6.L153.B0"',
        inboundVideo: null,
        primarySourceTransition: true,
      }),
    ).toBe('ignore');
  });

  it('ignores MEDIA_ERR_ABORTED because it represents an intentionally detached old source', () => {
    expect(
      resolveGo2rtcPrimaryVideoErrorAction({
        activeMode: 'mse',
        playingReported: false,
        mediaErrorCode: 1,
        mseCodecs: '',
        inboundVideo: null,
      }),
    ).toBe('ignore');
  });

  it('never lets residual MSE H265 codecs turn an active WebRTC error into H265 fallback', () => {
    expect(
      resolveGo2rtcPrimaryVideoErrorAction({
        activeMode: 'webrtc',
        playingReported: false,
        mediaErrorCode: 3,
        mseCodecs: 'video/mp4; codecs="hvc1.1.6.L153.B0"',
        inboundVideo: inbound('video/H264'),
      }),
    ).toBe('reconnect');

    expect(
      resolveGo2rtcPrimaryVideoErrorAction({
        activeMode: 'mse',
        playingReported: false,
        mediaErrorCode: 3,
        mseCodecs: 'video/mp4; codecs="hvc1.1.6.L153.B0"',
        inboundVideo: inbound('video/H264'),
      }),
    ).toBe('h265-decode-failed-mse');
  });

  it('always recovers in place after a first frame, even when the active codec is H265', () => {
    expect(
      resolveGo2rtcPrimaryVideoErrorAction({
        activeMode: 'mse',
        playingReported: true,
        mediaErrorCode: 3,
        mseCodecs: 'video/mp4; codecs="hvc1.1.6.L153.B0"',
        inboundVideo: null,
      }),
    ).toBe('reconnect');
  });

  it('treats timeline or decoded-frame advancement as healthy without inspecting codec', () => {
    const baseline = { currentTime: 10, framesDecoded: 100, framesRendered: 100 };
    expect(
      hasGo2rtcPlaybackProgress('mse', baseline, {
        currentTime: 10.05,
        framesDecoded: null,
        framesRendered: null,
      }),
    ).toBe(true);
    expect(
      hasGo2rtcPlaybackProgress('webrtc', baseline, {
        currentTime: 10,
        framesDecoded: 101,
        framesRendered: 100,
      }),
    ).toBe(true);
    expect(
      hasGo2rtcPlaybackProgress('webrtc', baseline, {
        currentTime: 11,
        framesDecoded: 100,
        framesRendered: 100,
      }),
    ).toBe(false);
    expect(
      hasGo2rtcPlaybackProgress(
        'webrtc',
        { currentTime: 10, framesDecoded: null, framesRendered: null },
        { currentTime: 11, framesDecoded: null, framesRendered: null },
      ),
    ).toBe(true);
    expect(hasGo2rtcPlaybackProgress('webrtc', baseline, baseline)).toBe(false);
  });
});
