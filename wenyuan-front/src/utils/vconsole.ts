// 移动端调试工具 vConsole
import VConsole from 'vconsole';

let vConsole: VConsole | null = null;

function shouldEnableVConsole() {
  if (typeof window === 'undefined') {
    return false;
  }

  const searchParams = new URLSearchParams(window.location.search);
  const queryValue = (searchParams.get('vconsole') || '').toLowerCase();
  return ['1', 'true', 'on', 'yes'].includes(queryValue);
}

// 初始化 vConsole
export function initVConsole() {
  const enableVConsole = shouldEnableVConsole();

  if (enableVConsole && !vConsole) {
    // 不指定 defaultPlugins，让所有插件（包括 console）都默认启用
    vConsole = new VConsole({
      theme: 'dark',
      maxLogNumber: 1000,
    });
    
    // 测试各种日志级别
    console.log('✅ vConsole 已启动 - console.log 测试');
    console.info('✅ vConsole 已启动 - console.info 测试');
    console.warn('✅ vConsole 已启动 - console.warn 测试');
    console.error('✅ vConsole 已启动 - console.error 测试');
    console.debug('✅ vConsole 已启动 - console.debug 测试');
    console.table({
      href: window.location.href,
      userAgent: navigator.userAgent,
      queryVConsole: new URLSearchParams(window.location.search).get('vconsole') || '',
      storageVConsole: localStorage.getItem('enableVConsole') || '',
    });
  }
}

// 销毁 vConsole
export function destroyVConsole() {
  if (vConsole) {
    vConsole.destroy();
    vConsole = null;
    console.log('vConsole 已销毁');
  }
}

// 切换 vConsole
export function toggleVConsole() {
  if (vConsole) {
    destroyVConsole();
    localStorage.setItem('enableVConsole', 'false');
  } else {
    localStorage.setItem('enableVConsole', 'true');
    initVConsole();
  }
}
