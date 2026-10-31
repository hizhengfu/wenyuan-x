<template>
  <div class="wh-full">
    <EChart :option="options" @click="handleClick"></EChart>

    <div v-if="infoShow" class="info-wrap">
      <n-card
        :title="activeNode.name"
        size="small"
        :segmented="{
          content: true,
          action: true,
        }"
      >
        <template #header-extra>
          <n-button @click="infoShow = false">
            <n-icon>
              <IconMdiClose></IconMdiClose>
            </n-icon>
          </n-button>
        </template>
        <div class="info-content">
          <n-image
            v-if="activeNode.image"
            width="120"
            :src="fileUrl(activeNode.image) + '?w=300'"
            :preview-src="fileUrl(activeNode.image)"
          ></n-image>
          <div v-html="activeNode.content" style="margin-bottom: 8px"></div>

          <!-- 结构化属性 -->
          <template v-if="activeNodeAttrs.length > 0">
            <n-divider style="margin: 6px 0; font-size: 12px">属性</n-divider>
            <n-descriptions :column="1" size="small" bordered label-placement="left">
              <n-descriptions-item v-for="attr in activeNodeAttrs" :key="attr.id" :label="attr.attrKey">
                <n-tag
                  v-if="attr.valueType === 'BOOLEAN'"
                  :type="attr.attrValue === 'true' ? 'success' : 'default'"
                  size="small"
                  >{{ attr.attrValue }}</n-tag
                >
                <span v-else>{{ attr.attrValue }}</span>
              </n-descriptions-item>
            </n-descriptions>
          </template>
          <n-spin v-else-if="attrLoading" size="small" style="margin-top: 8px" />
        </div>
      </n-card>
    </div>
  </div>
</template>
<script lang="ts" setup>
import EChart from '@/components/data/chart/EChart.vue';
import { graphQuery } from '../api/graph.ts';
import { listByNode } from '../api/nodeAttr.ts';
import { ref, watchEffect } from 'vue';
import { fileUrl } from '@/utils';

const props = defineProps({
  nodeId: { type: String },
  sourceId: { type: String },
  showTitle: { type: Boolean, default: true },
  showLegend: { type: Boolean, default: true },
  zoom: { type: Number, default: 1 }, //默认缩放比例
  isCloseClick: { type: Boolean, default: false }, //是否关闭单击，默认不关闭false
  depth: { type: Number, default: 2 }, //图谱遍历深度，1~5
  previewNodes: { type: Array, default: null }, // 草稿预览：直接传入节点数组
  previewRelations: { type: Array, default: null }, // 草稿预览：直接传入关系数组
  showDownload: { type: Boolean, default: false }, // 是否显示下载全图按钮
});

const emits = defineEmits(['viewDetail']);

const infoShow = ref();
const options = ref();

function loadData(nodeId?: string, sourceId?: string) {
  const { showTitle, showLegend, showDownload, zoom, depth } = props;
  graphQuery({ nodeId: nodeId, sourceId: sourceId, depth: depth }).then((resp: any) => {
    let data = resp.data;
    if (!data) return;
    let option: any = {
      tooltip: {
        trigger: zoom === 1 ? 'item' : 'none',
      },
      ...(showDownload ? { toolbox: { feature: { saveAsImage: { title: '下载图片', pixelRatio: 2 } } } } : {}),
      series: [
        {
          type: 'graph',
          layout: 'force',
          symbolSize: 58,
          draggable: true,
          roam: true,
          zoom,
          emphasis: {
            focus: 'adjacency',
          },
          categories: data.categories,
          edgeSymbol: ['', 'arrow'],
          // edgeSymbolSize: [80, 10],
          edgeLabel: {
            show: true,
            fontSize: 14 * zoom,
            formatter(x: any) {
              return x.data.name;
            },
            width: 80 * zoom,
            overflow: 'truncate',
          },
          tooltip: {
            formatter(x: any) {
              if (x.dataType === 'edge') {
                return x.data.name;
              }
            },
          },
          label: {
            show: true,
            fontSize: zoom === 1 ? 12 : 12 * zoom * 3,
          },
          force: {
            repulsion: 2000,
            edgeLength: 120,
          },
          data: data.nodes.map((item: any) => {
            if (item.image) {
              item['symbol'] = 'image://' + fileUrl(item.image) + '?w=300&c';
              item['itemStyle'] = {
                borderCap: 'round',
                borderColor: '#04f2a7',
                borderWidth: 6,
                shadowBlur: 20,
                shadowColor: '#04f2a7',
                color: '#001c43',
              };
            } else {
              item['itemStyle'] = {
                borderCap: 'round',
                borderWidth: 6,
                shadowBlur: 20,
              };
            }
            return item;
          }),
          links: data.links,
        },
      ],
    };

    if (showTitle) {
      option['title'] = {
        text: '知识图谱',
      };
    }
    if (showLegend && data.categories) {
      option['legend'] = [
        {
          data: data.categories?.map((x: any) => x.name) || [],
        },
      ];
    }

    options.value = option;
  });
}

const activeNode = ref<any>();
const activeNodeAttrs = ref<any[]>([]);
const attrLoading = ref(false);

function handleClick(event: any) {
  if (props.isCloseClick) {
    emits('viewDetail');
    return;
  }
  let dataType = event.dataType;
  infoShow.value = false;
  activeNodeAttrs.value = [];
  //节点事件
  if (dataType === 'node') {
    activeNode.value = event.data;
    infoShow.value = true;
    // 加载节点结构化属性
    const nid = event.data.nodeId;
    if (nid) {
      attrLoading.value = true;
      listByNode(nid)
        .then((resp: any) => {
          activeNodeAttrs.value = resp.data || [];
        })
        .finally(() => {
          attrLoading.value = false;
        });
    }
  }
}

function buildPreviewOptions(nodes: any[], relations: any[]) {
  const { showLegend, zoom } = props;
  // 收集所有分类
  const catSet = new Set<string>();
  nodes.forEach((n) => {
    if (n.categoryId) catSet.add(n.categoryId);
  });
  const categories = Array.from(catSet).map((id) => ({ name: id }));
  const catIndex = (id?: string) => (id ? categories.findIndex((c) => c.name === id) : 0);

  const echartNodes = nodes.map((n) => ({
    name: n.name,
    content: n.content,
    category: Math.max(catIndex(n.categoryId), 0),
    symbolSize: Math.max(30, Math.min(70, (n.weight ?? 60) * 0.7)),
    itemStyle: { borderWidth: 4, shadowBlur: 12 },
  }));

  const echartLinks = relations.map((r) => ({
    source: r.sourceName,
    target: r.targetName,
    name: r.name,
    lineStyle: r.direction === 'TWOWAY' ? { type: 'dashed' } : {},
    symbol: r.direction === 'RIGHT2LEFT' ? ['arrow', ''] : ['', 'arrow'],
  }));

  const option: any = {
    tooltip: { trigger: 'item' },
    ...(props.showDownload ? { toolbox: { feature: { saveAsImage: { title: '下载图片', pixelRatio: 2 } } } } : {}),
    series: [
      {
        type: 'graph',
        layout: 'force',
        draggable: true,
        roam: true,
        zoom,
        emphasis: { focus: 'adjacency' },
        categories,
        edgeLabel: {
          show: true,
          fontSize: 12,
          formatter: (x: any) => x.data.name,
          overflow: 'truncate',
          width: 80,
        },
        label: { show: true, fontSize: 12 },
        force: { repulsion: 2000, edgeLength: 130 },
        data: echartNodes,
        links: echartLinks,
      },
    ],
  };
  if (showLegend && categories.length > 0) {
    option.legend = [{ data: categories.map((c) => c.name) }];
  }
  return option;
}

watchEffect(() => {
  const { nodeId, sourceId, previewNodes, previewRelations } = props;
  if (previewNodes && previewNodes.length > 0) {
    options.value = buildPreviewOptions(previewNodes as any[], (previewRelations ?? []) as any[]);
  } else if (nodeId || sourceId) {
    loadData(nodeId, sourceId);
  }
});
</script>
<style lang="scss" scoped>
.info-wrap {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 360px;

  .info-content {
    max-height: 300px;
    overflow: auto;
  }
}
</style>
