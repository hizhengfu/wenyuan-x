import type { Ref } from 'vue-demi';
import { isRef, ref, watch, unref } from 'vue-demi';
import { Voice } from '../lib/voice';

export interface UseVoiceOptions {
  action: Record<any, any>;
  map: Map<any, any>;
}

// 语音识别
export function useVoice(target: any, options: UseVoiceOptions) {
  const { action, map } = options;
  const _action = unref(action); // 解构后去ref响应式
  const _map = unref(map);
  const voice = new Voice(_action, _map); // 建立语音实例
  const speech: Ref<string> = ref(''); // 语音识别结果

  function execute() {
    const _target = unref(target);
    if (typeof _target === 'object') {
      speech.value = _target.result || '';
    } else if (typeof _target === 'string') {
      speech.value = _target;
    }
    // 语音识别有效, 则做相应处理
    speech.value && voice.process(speech.value);
  }

  // 响应式传参需要监听处理
  if (isRef(target) || typeof target === 'function') {
    watch(target, execute, { immediate: true });
  } else {
    execute();
  }

  return {
    speech,
    voice,
    execute,
  };
}
