import { collectInboundVideoDecodeStats, type InboundVideoDecodeStats } from './h265Capability';

export interface IceCandidateDiagnostics {
  id: string;
  candidateType: string | null;
  protocol: string | null;
  address: string | null;
  port: number | null;
  networkType: string | null;
  relayProtocol: string | null;
  tcpType: string | null;
}

export interface SelectedCandidatePairDiagnostics {
  id: string;
  state: string | null;
  nominated: boolean;
  selected: boolean;
  bytesSent: number;
  bytesReceived: number;
  currentRoundTripTime: number | null;
  local: IceCandidateDiagnostics | null;
  remote: IceCandidateDiagnostics | null;
}

export interface WebrtcRuntimeDiagnostics {
  inboundVideo: InboundVideoDecodeStats | null;
  selectedCandidatePair: SelectedCandidatePairDiagnostics | null;
}

export interface HevcSdpLevelDiagnostics {
  payloadType: number;
  levelId: number | null;
  maxReceiveLevelId: number | null;
  effectiveReceiveLevelId: number | null;
  level: string | null;
}

export interface HevcLevelCompatibilityDiagnostics {
  sourceCodec: string | null;
  sourceLevelId: number | null;
  sourceLevel: string | null;
  browserOffer: HevcSdpLevelDiagnostics | null;
  serverAnswer: HevcSdpLevelDiagnostics | null;
  negotiatedReceiveLevelId: number | null;
  negotiatedReceiveLevel: string | null;
  sourceExceedsNegotiatedLevel: boolean | null;
  recommendation: 'LOWER_CAMERA_HEVC_LEVEL_OR_USE_H264' | null;
}

const HEVC_LEVELS = new Map<number, string>([
  [30, '1'],
  [60, '2'],
  [63, '2.1'],
  [90, '3'],
  [93, '3.1'],
  [120, '4'],
  [123, '4.1'],
  [150, '5'],
  [153, '5.1'],
  [156, '5.2'],
  [180, '6'],
  [183, '6.1'],
  [186, '6.2'],
]);

function finiteNumber(value: unknown, fallback = 0): number {
  const number = Number(value);
  return Number.isFinite(number) ? number : fallback;
}

function nullableNumber(value: unknown): number | null {
  if (value === null || value === undefined || value === '') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function nullableString(value: unknown): string | null {
  return typeof value === 'string' && value ? value : null;
}

function compactCandidate(item: any): IceCandidateDiagnostics | null {
  if (!item) return null;
  return {
    id: String(item.id ?? ''),
    candidateType: nullableString(item.candidateType),
    protocol: nullableString(item.protocol),
    address: nullableString(item.address ?? item.ip),
    port: nullableNumber(item.port),
    networkType: nullableString(item.networkType),
    relayProtocol: nullableString(item.relayProtocol),
    tcpType: nullableString(item.tcpType),
  };
}

/** 提取浏览器当前真正选中的 ICE candidate pair，而不是仅记录信令阶段出现过的候选。 */
export function collectSelectedCandidatePair(report: RTCStatsReport): SelectedCandidatePairDiagnostics | null {
  const items = new Map<string, any>();
  const pairs: any[] = [];
  let selectedPairId = '';

  report.forEach((item: any) => {
    if (typeof item.id === 'string') items.set(item.id, item);
    if (item.type === 'transport' && typeof item.selectedCandidatePairId === 'string') {
      selectedPairId = item.selectedCandidatePairId;
    } else if (item.type === 'candidate-pair') {
      pairs.push(item);
    }
  });

  const pair =
    (selectedPairId ? items.get(selectedPairId) : null) ??
    pairs.find((item) => item.selected === true) ??
    pairs.find((item) => item.nominated === true && item.state === 'succeeded') ??
    null;
  if (!pair) return null;

  return {
    id: String(pair.id ?? ''),
    state: nullableString(pair.state),
    nominated: pair.nominated === true,
    selected: pair.selected === true || pair.id === selectedPairId,
    bytesSent: finiteNumber(pair.bytesSent),
    bytesReceived: finiteNumber(pair.bytesReceived),
    currentRoundTripTime: nullableNumber(pair.currentRoundTripTime),
    local: compactCandidate(items.get(pair.localCandidateId)),
    remote: compactCandidate(items.get(pair.remoteCandidateId)),
  };
}

export function collectWebrtcRuntimeDiagnostics(report: RTCStatsReport): WebrtcRuntimeDiagnostics {
  return {
    inboundVideo: collectInboundVideoDecodeStats(report),
    selectedCandidatePair: collectSelectedCandidatePair(report),
  };
}

export async function readWebrtcRuntimeDiagnostics(
  peerConnection: RTCPeerConnection,
): Promise<WebrtcRuntimeDiagnostics | null> {
  try {
    return collectWebrtcRuntimeDiagnostics(await peerConnection.getStats());
  } catch {
    return null;
  }
}

export function formatHevcLevel(levelId: number | null): string | null {
  return levelId === null ? null : (HEVC_LEVELS.get(levelId) ?? `level-id-${levelId}`);
}

/** go2rtc 的 MSE MIME 来自实际 fMP4 视频轨，可用来核对源 HEVC profile/level。 */
export function parseHevcCodecLevel(codecs: string): { codec: string; levelId: number; level: string } | null {
  const codec = codecs.match(/(?:hvc1|hev1)\.[^,;"\s]+/i)?.[0] ?? null;
  if (!codec) return null;
  const levelId = Number(codec.match(/\.L(\d+)(?:\.|$)/i)?.[1]);
  if (!Number.isFinite(levelId)) return null;
  return { codec, levelId, level: formatHevcLevel(levelId)! };
}

/** 解析 SDP 中 H265 payload 对应的 level-id/max-recv-level-id。 */
export function parseH265SdpLevel(
  sdp: string | null | undefined,
  preferredPayloadType?: number,
): HevcSdpLevelDiagnostics | null {
  if (!sdp) return null;
  const lines = sdp.split(/\r?\n/);
  const rtpmaps = lines
    .map((line) => line.match(/^a=rtpmap:(\d+)\s+H265\/90000/i))
    .filter((match): match is RegExpMatchArray => Boolean(match));
  const rtpmap =
    rtpmaps.find((match) => preferredPayloadType !== undefined && Number(match[1]) === preferredPayloadType) ??
    rtpmaps[0];
  if (!rtpmap) return null;

  const payloadType = Number(rtpmap[1]);
  const fmtp = lines.find((line) => line.startsWith(`a=fmtp:${payloadType} `)) ?? '';
  const parameters = fmtp.slice(fmtp.indexOf(' ') + 1);
  const levelId = nullableNumber(parameters.match(/(?:^|;)\s*level-id=(\d+)/i)?.[1]);
  const maxReceiveLevelId = nullableNumber(parameters.match(/(?:^|;)\s*max-recv-level-id=(\d+)/i)?.[1]);
  const effectiveReceiveLevelId = maxReceiveLevelId ?? levelId;
  return {
    payloadType,
    levelId,
    maxReceiveLevelId,
    effectiveReceiveLevelId,
    level: formatHevcLevel(effectiveReceiveLevelId),
  };
}

export function buildHevcLevelCompatibilityDiagnostics(
  sourceCodecs: string,
  localSdp: string | null | undefined,
  remoteSdp: string | null | undefined,
  actualPayloadType?: number,
): HevcLevelCompatibilityDiagnostics {
  const source = parseHevcCodecLevel(sourceCodecs);
  const browserOffer = parseH265SdpLevel(localSdp, actualPayloadType);
  const serverAnswer = parseH265SdpLevel(remoteSdp, actualPayloadType);
  const negotiatedReceiveLevelId =
    serverAnswer?.effectiveReceiveLevelId ?? browserOffer?.effectiveReceiveLevelId ?? null;
  const sourceExceedsNegotiatedLevel =
    source && negotiatedReceiveLevelId !== null ? source.levelId > negotiatedReceiveLevelId : null;

  return {
    sourceCodec: source?.codec ?? null,
    sourceLevelId: source?.levelId ?? null,
    sourceLevel: source?.level ?? null,
    browserOffer,
    serverAnswer,
    negotiatedReceiveLevelId,
    negotiatedReceiveLevel: formatHevcLevel(negotiatedReceiveLevelId),
    sourceExceedsNegotiatedLevel,
    recommendation: sourceExceedsNegotiatedLevel ? 'LOWER_CAMERA_HEVC_LEVEL_OR_USE_H264' : null,
  };
}
