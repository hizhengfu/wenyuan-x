<template>
  <div v-if="shellEnabled" class="demo-shell">
    <div class="demo-shell__device">
      <iframe class="demo-shell__frame" :src="iframeSrc" title="yyjxwbH5 demo shell" />
    </div>
  </div>
  <router-view v-else />
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';

import { buildDemoShellIframeSrc, isDemoShellEnabled } from './demoShell.shared.ts';

const route = useRoute();

const shellEnabled = computed(() => isDemoShellEnabled(APP_TARGET, route.query));

const iframeSrc = computed(() => {
  if (typeof window === 'undefined') {
    return '';
  }

  return buildDemoShellIframeSrc(window.location.href, {
    path: route.path,
    query: route.query,
    hash: route.hash,
  });
});
</script>

<style scoped>
.demo-shell {
  min-height: 100vh;
  padding: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: auto;
  background:
    radial-gradient(circle at top, rgba(79, 128, 207, 0.22), transparent 36%),
    linear-gradient(180deg, #eef4ff 0%, #dfe8f8 100%);
}

.demo-shell__device {
  width: 390px;
  height: 844px;
  flex: 0 0 auto;
  padding: 12px;
  border-radius: 36px;
  background: linear-gradient(180deg, #1d2a44 0%, #0f172a 100%);
  box-shadow:
    0 28px 64px rgba(15, 23, 42, 0.2),
    inset 0 0 0 1px rgba(255, 255, 255, 0.08);
}

.demo-shell__frame {
  width: 100%;
  height: 100%;
  border: 0;
  border-radius: 28px;
  overflow: hidden;
  background: #f4f6fa;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.06);
}

@media (max-width: 440px) {
  .demo-shell {
    padding: 12px;
  }

  .demo-shell__device {
    width: min(390px, calc(100vw - 24px));
    height: calc(100vh - 24px);
    border-radius: 28px;
  }

  .demo-shell__frame {
    border-radius: 20px;
  }
}
</style>
