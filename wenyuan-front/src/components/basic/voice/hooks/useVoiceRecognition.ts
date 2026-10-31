import { useWebSocket } from './useWebSocket';
import { useVoice, UseVoiceOptions } from './useVoice';

// 建立ws链接, 做语音识别
export function useVoiceRecognition(options: UseVoiceOptions) {
  const { result } = useWebSocket({ destinationPath: '', subscribePath: '/topic/voice' });
  useVoice(result, options);

  return {
    result,
  };
}
