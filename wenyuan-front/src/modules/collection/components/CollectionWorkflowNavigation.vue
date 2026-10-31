<template>
  <details v-if="visibleGuides.length" class="workflow-navigation" aria-labelledby="collection-workflow-title">
    <summary class="workflow-summary">
      <span id="collection-workflow-title" class="workflow-summary-title">办理流程与入口</span>
    </summary>
    <div class="workflow-grid">
      <article v-for="guide in visibleGuides" :key="guide.title" class="workflow-card">
        <h3>{{ guide.title }}</h3>
        <ol class="workflow-sequence" :aria-label="`${guide.title}办理顺序`">
          <li v-for="step in guide.sequence.split(' → ')" :key="step">{{ step }}</li>
        </ol>
        <div class="workflow-links">
          <RouterLink v-for="menu in guide.menus" :key="menu.id" :to="menu.to">{{ menu.name }}</RouterLink>
        </div>
        <p class="workflow-note">{{ guide.note }}</p>
      </article>
    </div>
  </details>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { RouterLink } from 'vue-router';
import { useAuthStore } from '@/store';

type NavigationMenu = { id: string; name: string; path?: string; children?: NavigationMenu[] };
const auth = useAuthStore();
const guides = [
  { title: '征集入藏', sequence: '征集前鉴定评估 → 申请审批 → 实际核收 → 文物定级 → 签署登账',
    paths: ['/collection/acquisition-plan', '/collection/acquisition', '/collection/entry-voucher', '/collection/appraisal'],
    note: '征集前鉴定在征集申请中办理，行政定级在鉴定与定级入口办理。核收、定级依据和签署齐备后才可正式登账。' },
  { title: '账册与编目', sequence: '查总账 → 编目录入 → 审核 → 正式卡片；变更另行办理',
    paths: ['/collection/ledger', '/collection/catalog-entry', '/collection/catalog-audit', '/collection/catalog-card', '/collection/change'],
    note: '总账详情集中查阅一物一档及资源公开；工作台可直接办理纸账与财务核对、日常检查养护。编目草稿与审核通过版本分别管理。' },
  { title: '库房保管', sequence: '日常：位置与日记；人员：申请 → 核验；清点：实盘 → 复核',
    paths: ['/collection/warehouse', '/collection/warehouse-entry', '/collection/inventory'],
    note: '库房页办理日志及库管任职交接；人员入口办理申请、核验和实际进出登记；清点入口分别办理离任移交及年度清点报送。' },
  { title: '提用、借用与归库', sequence: '选择馆内提用或馆外借用 → 审批与点交 → 归库验收',
    paths: ['/collection/requisition', '/collection/loan', '/collection/return', '/collection/movement'],
    note: '馆外借用包括展览、科研等用途；批准后办理实际点交，返还后核验、签署并归库。' },
  { title: '展示与利用', sequence: '选择展览、复制拓印、数字采集或取样 → 批准 → 实施 → 归档',
    paths: ['/collection/exhibition', '/collection/reproduction', '/collection/sampling'],
    note: '摄影和三维扫描在“复制拓印与数字采集”中按类型办理；有损取样使用独立方案和实施记录。' },
  { title: '调拨与退出', sequence: '选择产权变动事项 → 方案与审批 → 签署执行 → 留账及备案',
    paths: ['/collection/transfer', '/collection/deregistration', '/collection/retained-ledger'],
    note: '交换、调拨和机构终止合并承接在同一入口按事项办理；退出馆藏另行履行鉴定、审批和公告。' },
  { title: '保护与修复', sequence: '预防保护：方案 → 监测 → 预警；修复：病害 → 项目 → 工序 → 验收 → 归档',
    paths: ['/collection-preservation/protection-policy', '/collection-preservation/environment', '/collection-preservation/alarm', '/collection-restoration/damage', '/collection-restoration/project', '/collection-restoration/acceptance'],
    note: '预防性保护、保护修复和科学研究保留独立菜单；修复原件提用、归库仍需办理点交验收。' },
  { title: '安全与报送', sequence: '安全：检查 → 问题处置 → 复核；年度：账实核对 → 统计签署 → 报送',
    paths: ['/collection/safety-task', '/collection/joint-safety', '/collection/incident', '/collection/emergency', '/collection/annual-report'],
    note: '安全待办汇集检查及处理缺口；场馆设施安全从“场馆安全”办理，藏品事故从“事故报告与处置”办理。' },
];

function leaves(nodes: NavigationMenu[]): NavigationMenu[] {
  return nodes.flatMap(menu => menu.children?.length ? leaves(menu.children) : menu.path ? [menu] : []);
}

// 以当前账号的后端菜单为准：不显示未授权入口，链接保留菜单 ID、标题及原有查询参数。
const visibleGuides = computed(() => {
  const byPath = new Map(leaves((auth.menus || []) as NavigationMenu[]).map(menu => [menu.path!.split('?')[0], menu]));
  return guides.map(guide => ({
    ...guide,
    menus: guide.paths.flatMap(path => {
      const menu = byPath.get(path);
      if (!menu?.path) return [];
      const [pathname, search = ''] = menu.path.split('?');
      return [{ ...menu, to: { path: pathname, query: { ...Object.fromEntries(new URLSearchParams(search)), menuId: menu.id, title: menu.name } } }];
    }),
  })).filter(guide => guide.menus.length);
});
</script>

<style scoped>
.workflow-navigation { padding: 16px; border: 1px solid #e3e8ef; border-radius: 10px; background: #fff; }
.workflow-summary { cursor: pointer; line-height: 1.6; }
.workflow-summary::marker { color: #08756f; }
.workflow-summary-title { font-size: 14px; font-weight: 700; color: #172033; }
.workflow-summary:focus-visible { outline: 2px solid #175cd3; outline-offset: 4px; border-radius: 2px; }
.workflow-navigation[open] > .workflow-summary { margin-bottom: 12px; }
.workflow-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.workflow-card { min-width: 0; padding: 12px; border-radius: 8px; background: #f7f9fc; }
.workflow-card h3 { margin: 0 0 6px; font-size: 14px; color: #172033; }
.workflow-sequence { display: flex; flex-wrap: wrap; gap: 6px 8px; margin: 0 0 12px; padding: 0; list-style: none; font-size: 12px; line-height: 1.7; color: #344054; }
.workflow-sequence li { display: flex; align-items: baseline; gap: 8px; }
.workflow-sequence li:not(:last-child)::after { content: '›'; color: #98a2b3; }
.workflow-links { display: flex; flex-wrap: wrap; gap: 6px 12px; }
.workflow-links a { color: #175cd3; font-size: 13px; text-decoration: underline; text-underline-offset: 3px; }
.workflow-links a:focus-visible { outline: 2px solid #175cd3; outline-offset: 3px; border-radius: 2px; }
.workflow-note { margin: 8px 0 0; font-size: 12px; line-height: 1.7; color: #667085; }
@container collection-workbench (max-width: 760px) { .workflow-grid { grid-template-columns: minmax(0, 1fr); } }
</style>
