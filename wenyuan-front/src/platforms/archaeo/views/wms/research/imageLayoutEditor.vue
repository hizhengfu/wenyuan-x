<template>
  <n-card
    class="wh-full flex flex-col"
    bordered
    content-style="flex:1;display:flex;overflow:hidden;padding:0;"
  >
    <template #header>
      <n-space align="center" :wrap="false">
        <n-tooltip trigger="hover">
          <template #trigger>
            <n-button quaternary circle size="small" @click="$router.back()">
              <template #icon><n-icon><IconMdiArrowLeft /></n-icon></template>
            </n-button>
          </template>
          返回列表
        </n-tooltip>
        <n-input v-model:value="form.title" placeholder="图名" style="width: 200px" size="small" />
        <n-tag :bordered="false" type="success" size="small">
          <template #icon><span style="color:#18a058">●</span></template>
          引擎就绪
        </n-tag>
      </n-space>
    </template>
    <template #header-extra>
      <n-space size="small" :wrap="false">
        <n-popconfirm @positive-click="clearAll">
          <template #trigger>
            <n-tooltip trigger="hover">
              <template #trigger>
                <n-button quaternary circle size="small" type="error">
                  <template #icon><n-icon><IconMdiTrashCanOutline /></n-icon></template>
                </n-button>
              </template>
              一键清空
            </n-tooltip>
          </template>
          确认清空当前画板（图片、连线、图注全部移除）？
        </n-popconfirm>
        <n-tooltip trigger="hover">
          <template #trigger>
            <n-button quaternary circle size="small" :loading="saving" @click="handleSave">
              <template #icon><n-icon><IconMdiContentSave /></n-icon></template>
            </n-button>
          </template>
          保存（自动生成缩略图）
        </n-tooltip>
        <n-tooltip trigger="hover">
          <template #trigger>
            <n-button quaternary circle size="small" @click="downloadCaptions">
              <template #icon><n-icon><IconMdiFileDocumentOutline /></n-icon></template>
            </n-button>
          </template>
          导出图注 .txt
        </n-tooltip>
        <n-button size="small" type="primary" @click="exportShow = true">
          <template #icon><n-icon><IconMdiDownload /></n-icon></template>
          导出图像
        </n-button>
      </n-space>
    </template>

    <div class="layout-container">
      <!-- 左栏：数据与排版规则 -->
      <div class="layout-tools left-pane">
        <div class="pane-title">
          <n-icon size="16"><IconMdiTableLargePlus /></n-icon>
          <span>数据与排版规则</span>
          <n-tag size="tiny" :bordered="false" type="info" class="pane-title-extra">
            {{ imageCount }} 张图
          </n-tag>
        </div>

        <!-- 导入 -->
        <div class="tool-section">
          <h-upload
            :show-file-list="false"
            :multiple="true"
            module="wms"
            :contentId="`imageLayout_${id}`"
            listType="text"
            accept="image/*"
            @uploadFinish="onImageUploaded"
          >
            <n-button block type="primary" :loading="appStore.uploading">
              <template #icon><n-icon><IconMdiImagePlus /></n-icon></template>
              {{ appStore.uploading ? '上传中…' : '导入线图 / 图片' }}
            </n-button>
          </h-upload>
          <div class="tip">文件名按"单位_编号_器名"命名可自动解析（如 <code>H37_2_尖底瓶.png</code>）。</div>
        </div>

        <!-- 排版逻辑（分组 + 编号方向） -->
        <div class="tool-section">
          <h4>排版逻辑</h4>
          <n-tabs v-model:value="groupBy" type="segment" size="small">
            <n-tab-pane name="unit" tab="按单位" />
            <n-tab-pane name="category" tab="按器类" />
            <n-tab-pane name="none" tab="混排模式" />
          </n-tabs>
          <n-form-item label="编号方向" label-placement="left" style="margin-top: 8px;">
            <n-radio-group v-model:value="numberingDir" size="small">
              <n-radio-button value="h">横向</n-radio-button>
              <n-radio-button value="v">纵向</n-radio-button>
            </n-radio-group>
          </n-form-item>
        </div>

        <!-- 常用比例 -->
        <div class="tool-section">
          <h4>常用缩小（纸面 ≤ 实物）</h4>
          <n-space size="small" style="margin-top:4px">
            <n-button size="tiny" @click="applyScalePreset(1)">1:1</n-button>
            <n-button size="tiny" @click="applyScalePreset(2)">1:2</n-button>
            <n-button size="tiny" @click="applyScalePreset(3)">1:3</n-button>
            <n-button size="tiny" @click="applyScalePreset(5)">1:5</n-button>
          </n-space>
          <h4 style="margin-top:10px">常用放大（纸面 ≥ 实物）</h4>
          <n-space size="small" style="margin-top:4px">
            <n-button size="tiny" @click="applyScalePreset(1 / 2)">2:1</n-button>
            <n-button size="tiny" @click="applyScalePreset(1 / 3)">3:1</n-button>
            <n-button size="tiny" @click="applyScalePreset(1 / 5)">5:1</n-button>
          </n-space>
          <div class="tip">选中图→单张；未选中→全部。</div>
        </div>

        <!-- 数据管理（选中图的解析字段就地编辑） -->
        <div class="tool-section" v-if="selectedItem?.type === 'image'">
          <h4>数据管理（选中图）</h4>
          <n-form-item label="单位" label-placement="left">
            <n-input v-model:value="selectedItem.unit" size="small" @blur="refreshLabels" />
          </n-form-item>
          <n-form-item label="编号" label-placement="left">
            <n-input v-model:value="selectedItem.code" size="small" @blur="refreshLabels" />
          </n-form-item>
          <n-form-item label="器名" label-placement="left">
            <n-input v-model:value="selectedItem.artifactName" size="small" @blur="refreshLabels" />
          </n-form-item>
          <n-form-item label="实际尺寸 cm" label-placement="left">
            <n-input-number v-model:value="selectedItem.realCm" :step="0.5" size="small" @update:value="updateScaleByReal" />
          </n-form-item>
          <div class="tip">序号：{{ selectedItem.seq ?? '尚未排版' }} · 比例尺 {{ ratioText(selectedItem) }}</div>
        </div>

        <!-- 选中其他类型 (label/scale) 的简易属性 -->
        <div class="tool-section" v-else-if="selectedItem">
          <h4>属性</h4>
          <n-form-item label="X" label-placement="left">
            <n-input-number v-model:value="selectedItem.x" size="small" />
          </n-form-item>
          <n-form-item label="Y" label-placement="left">
            <n-input-number v-model:value="selectedItem.y" size="small" />
          </n-form-item>
          <n-form-item v-if="selectedItem.type === 'scale'" label="刻度 cm" label-placement="left">
            <n-input-number v-model:value="selectedItem.scaleCm" :min="1" :step="1" size="small" />
          </n-form-item>
          <n-form-item v-if="selectedItem.type === 'label'" label="文字" label-placement="left">
            <n-input v-model:value="selectedItem.text" size="small" />
          </n-form-item>
        </div>
      </div>

      <!-- 中部：排版预览（顶部缩放/画板操作工具栏 + 画布） -->
      <div class="center-pane">
        <div class="preview-toolbar">
          <n-space size="small" align="center" :wrap="false">
            <n-tooltip trigger="hover">
              <template #trigger>
                <n-button size="small" quaternary circle @click="zoomOut">
                  <template #icon><n-icon><IconMdiMagnifyMinusOutline /></n-icon></template>
                </n-button>
              </template>
              缩小
            </n-tooltip>
            <span class="zoom-pct" @click="resetZoom" title="点击重置 100%">{{ Math.round(stageScale * 100) }}%</span>
            <n-tooltip trigger="hover">
              <template #trigger>
                <n-button size="small" quaternary circle @click="zoomIn">
                  <template #icon><n-icon><IconMdiMagnifyPlusOutline /></n-icon></template>
                </n-button>
              </template>
              放大
            </n-tooltip>
            <n-tooltip trigger="hover">
              <template #trigger>
                <n-button size="small" quaternary circle @click="fitToScreen">
                  <template #icon><n-icon><IconMdiFitToPageOutline /></n-icon></template>
                </n-button>
              </template>
              适应窗口
            </n-tooltip>
          </n-space>
          <n-space size="small" align="center" :wrap="false">
            <n-tooltip trigger="hover">
              <template #trigger>
                <n-button size="small" quaternary circle :type="showGrid ? 'primary' : 'default'" @click="toggleGrid">
                  <template #icon><n-icon><IconMdiGrid /></n-icon></template>
                </n-button>
              </template>
              {{ showGrid ? '关闭网格' : '开启网格' }}
            </n-tooltip>
            <n-tooltip trigger="hover">
              <template #trigger>
                <n-button size="small" quaternary circle :type="connectMode ? 'primary' : 'default'" @click="toggleConnect">
                  <template #icon><n-icon><IconMdiVectorPolyline /></n-icon></template>
                </n-button>
              </template>
              {{ connectMode ? '取消连接' : '连接线（点两张图）' }}
            </n-tooltip>
            <n-tooltip trigger="hover">
              <template #trigger>
                <n-button size="small" quaternary circle @click="addScaleBar">
                  <template #icon><n-icon><IconMdiRuler /></n-icon></template>
                </n-button>
              </template>
              添加比例尺
            </n-tooltip>
            <n-tooltip trigger="hover">
              <template #trigger>
                <n-button size="small" quaternary circle :disabled="!isImageSelected" @click="openCrop">
                  <template #icon><n-icon><IconMdiCrop /></n-icon></template>
                </n-button>
              </template>
              裁切选中图（智能去白边 / 手动）
            </n-tooltip>
            <n-dropdown trigger="click" :options="guideOptions" @select="(k: string) => addGuide(k === 'gh' ? 'h' : 'v')">
              <n-tooltip trigger="hover">
                <template #trigger>
                  <n-button size="small" quaternary circle>
                    <template #icon><n-icon><IconMdiRulerSquare /></n-icon></template>
                  </n-button>
                </template>
                参考线（双击线可删除）
              </n-tooltip>
            </n-dropdown>
            <n-tooltip trigger="hover">
              <template #trigger>
                <n-button size="small" quaternary circle :disabled="!isImageSelected" @click="addLabel">
                  <template #icon><n-icon><IconMdiNumeric /></n-icon></template>
                </n-button>
              </template>
              为选中图加图号
            </n-tooltip>
            <n-tooltip trigger="hover">
              <template #trigger>
                <n-button size="small" quaternary circle type="error" :disabled="!selectedId" @click="deleteSelected">
                  <template #icon><n-icon><IconMdiTrashCanOutline /></n-icon></template>
                </n-button>
              </template>
              删除选中
            </n-tooltip>
            <n-divider vertical />
            <n-tooltip trigger="hover">
              <template #trigger>
                <n-button size="small" circle type="primary" :disabled="layoutMode === 'custom'" @click="autoLayout">
                  <template #icon><n-icon><IconMdiAutoFix /></n-icon></template>
                </n-button>
              </template>
              {{ layoutMode === 'custom' ? '自定义模式：手动摆放' : '重新排版（按当前列数/分组）' }}
            </n-tooltip>
          </n-space>
        </div>
        <div class="canvas-wrap" ref="canvasWrap" @wheel="onWheel">
        <v-stage
          ref="stageRef"
          :config="{
            width: (form.width || 794) * stageScale,
            height: (form.height || 1123) * stageScale,
            scaleX: stageScale,
            scaleY: stageScale,
          }"
          @mousedown="onStageMouseDown"
          @mousemove="onStageMouseMove"
          @mouseup="onStageMouseUp"
        >
          <v-layer>
            <v-rect
              :config="{ __bg: true, x: 0, y: 0, width: form.width || 794, height: form.height || 1123, fill: '#fff', stroke: '#888' }"
            />
            <v-line
              v-for="(g, i) in gridLines"
              :key="'g' + i"
              :config="{ points: g, stroke: '#eee', strokeWidth: 1, listening: false }"
            />
            <!-- 参考线（不计入导出） -->
            <v-line
              v-for="g in (exportingFlag ? [] : guides)"
              :key="g.id"
              :config="{
                x: g.dir === 'v' ? g.pos : 0,
                y: g.dir === 'h' ? g.pos : 0,
                points: guidePoints(g),
                stroke: '#1890ff',
                strokeWidth: 1,
                dash: [4, 4],
                draggable: true,
                dragBoundFunc: guideDragBound(g),
              }"
              @dragend="(e: any) => onGuideDragEnd(g, e)"
              @dblclick="removeGuide(g)"
            />
            <!-- 连接线（同件文物不同角度） -->
            <v-line
              v-for="c in connectors"
              :key="c.id"
              :config="{
                points: connectorPoints(c),
                stroke: '#666',
                strokeWidth: 2,
                dash: [8, 6],
                listening: true,
              }"
              @click="onSelect(c.id)"
            />
            <template v-for="(it, idx) in items" :key="it.id">
              <v-image
                v-if="it.type === 'image' && it.image"
                :config="{
                  id: it.id,
                  x: it.x,
                  y: it.y,
                  image: it.image,
                  scaleX: it.scale,
                  scaleY: it.scale,
                  draggable: true,
                  stroke: pendingConnectId === it.id ? '#18a058' : undefined,
                  strokeWidth: pendingConnectId === it.id ? 3 / (it.scale || 1) : 0,
                }"
                @click="(e: any) => onImageClick(it, e)"
                @dragend="(e: any) => onImageDragEnd(it, e)"
                @transformend="(e: any) => onImageTransformEnd(it, e)"
              />
              <v-text
                v-else-if="it.type === 'label'"
                :config="{
                  id: it.id,
                  x: it.x,
                  y: it.y,
                  text: it.text,
                  fontSize: 14,
                  fill: '#333',
                  draggable: true,
                }"
                @click="(e: any) => onSelect(it.id, !!e?.evt?.shiftKey)"
                @dragend="(e: any) => onDragEnd(it, e)"
              />
              <v-line
                v-else-if="it.type === 'scale'"
                :config="{
                  id: it.id,
                  x: it.x,
                  y: it.y,
                  points: scaleBarPoints(it),
                  stroke: '#000',
                  strokeWidth: 3,
                  draggable: true,
                }"
                @click="(e: any) => onSelect(it.id, !!e?.evt?.shiftKey)"
                @dragend="(e: any) => onDragEnd(it, e)"
              />
              <v-text
                v-if="it.type === 'scale'"
                :config="{
                  x: it.x,
                  y: it.y + 8,
                  text: `0${'             '}${it.scaleCm || 5}cm`,
                  fontSize: 13,
                  fill: '#000',
                  listening: false,
                }"
              />
            </template>
            <!-- 框选橡皮筋 -->
            <v-rect
              v-if="selecting"
              :config="{
                x: selRect.x,
                y: selRect.y,
                width: selRect.w,
                height: selRect.h,
                fill: 'rgba(61,174,255,0.12)',
                stroke: '#3DAEFF',
                strokeWidth: 1,
                dash: [4, 4],
                listening: false,
              }"
            />
            <!-- 选中图片时显示的缩放手柄（按对角等比例缩放） -->
            <v-transformer
              ref="transformerRef"
              :config="{
                rotateEnabled: false,
                keepRatio: true,
                enabledAnchors: ['top-left', 'top-right', 'bottom-left', 'bottom-right'],
                anchorSize: 10,
                borderStroke: '#3DAEFF',
                anchorStroke: '#3DAEFF',
              }"
            />
            <!-- 底部图注块：同名器合并，实时跟随编号/属性更新 -->
            <v-text
              v-if="showCaptionBlock && captionLines.length"
              :config="{
                x: padding,
                y: (form.height || 1123) - 100,
                width: (form.width || 794) - padding * 2,
                text: captionLines.join('   '),
                fontSize: captionFontSize,
                fill: '#222',
                listening: false,
                lineHeight: 1.4,
              }"
            />
          </v-layer>
        </v-stage>
        </div>
      </div>

      <!-- 右栏：排版与输出细节 -->
      <div class="layout-tools right-pane">
        <div class="pane-title">
          <n-icon size="16"><IconMdiTune /></n-icon>
          <span>排版与输出细节</span>
        </div>

        <n-collapse :default-expanded-names="['layout', 'size', 'scale', 'caption']" arrow-placement="right">
          <!-- 排版布局 -->
          <n-collapse-item name="layout" title="排版布局">
            <n-form :show-feedback="false" label-placement="left" size="small">
              <n-form-item label="模式">
                <n-radio-group v-model:value="layoutMode" size="small">
                  <n-radio-button value="standard">标准</n-radio-button>
                  <n-radio-button value="custom">自定义</n-radio-button>
                </n-radio-group>
              </n-form-item>
              <n-form-item label="列数" style="margin-top:8px;">
                <n-input-number v-model:value="cols" :min="1" :max="12" size="small" :disabled="layoutMode === 'custom'" />
              </n-form-item>
              <n-form-item label="编号方向" style="margin-top:8px;">
                <n-radio-group v-model:value="numberingDir" size="small">
                  <n-radio-button value="h">横向</n-radio-button>
                  <n-radio-button value="v">纵向</n-radio-button>
                </n-radio-group>
              </n-form-item>
            </n-form>
          </n-collapse-item>

          <!-- 尺寸与留白（2×2 紧凑网格） -->
          <n-collapse-item name="size" title="尺寸与留白">
            <div class="compact-grid">
              <div class="compact-cell">
                <label>画板宽</label>
                <n-input-number v-model:value="form.width" :min="100" :step="20" size="small" :show-button="false" />
              </div>
              <div class="compact-cell">
                <label>画板高</label>
                <n-input-number v-model:value="form.height" :min="100" :step="20" size="small" :show-button="false" />
              </div>
              <div class="compact-cell">
                <label>版面留白</label>
                <n-input-number v-model:value="padding" :min="0" :step="4" size="small" :show-button="false" />
              </div>
              <div class="compact-cell">
                <label>器物间距</label>
                <n-input-number v-model:value="gap" :min="0" :step="4" size="small" :show-button="false" />
              </div>
            </div>
          </n-collapse-item>

          <!-- 比例尺设置 -->
          <n-collapse-item name="scale" title="比例尺设置">
            <n-form :show-feedback="false" label-placement="left" size="small">
              <n-form-item label="每cm像素">
                <n-input-number v-model:value="pxPerCm" :min="1" :step="1" size="small" />
              </n-form-item>
            </n-form>
            <n-button block size="small" type="primary" style="margin-top:8px" @click="applyUnifiedScale">按实际尺寸统一缩放</n-button>
            <n-button block size="small" style="margin-top:6px" @click="alignScaleBars">对齐所有比例尺（左对齐）</n-button>
            <div class="tip">每张图填"实际尺寸 cm"后，按同一基准缩放。多个比例尺可一键左对齐。</div>
          </n-collapse-item>

          <!-- 图注及序号 -->
          <n-collapse-item name="caption" title="图注与序号">
            <n-form :show-feedback="false" label-placement="left" size="small">
              <n-form-item label="格式">
                <n-select
                  v-model:value="captionFormat"
                  size="small"
                  @update:value="refreshLabels"
                  :options="[
                    { label: '经典  1. 名 (H37:2)', value: 'classic' },
                    { label: '圆括号  1. 名（H37:2）', value: 'paren' },
                    { label: '线图下  名 / H37:2', value: 'underDraw' },
                    { label: '自定义模板', value: 'custom' },
                  ]"
                />
              </n-form-item>
              <n-form-item v-if="captionFormat === 'custom'" label="模板" label-placement="top" style="margin-top:6px">
                <n-input v-model:value="captionTemplate" type="textarea" :autosize="{ minRows: 2 }" size="small" @blur="refreshLabels" />
              </n-form-item>
              <div v-if="captionFormat === 'custom'" class="tip">
                可用占位符：{seq}/{nums}/{name}/{code}/{codes}/{unit}
              </div>
              <n-form-item label="字号" style="margin-top:6px">
                <n-input-number v-model:value="captionFontSize" :min="8" :max="48" size="small" />
              </n-form-item>
              <n-form-item label="底部图注" style="margin-top:6px">
                <n-switch v-model:value="showCaptionBlock" size="small" />
              </n-form-item>
            </n-form>
            <n-button block size="small" style="margin-top:8px" @click="refreshLabels">刷新图注文字</n-button>
            <n-button block size="small" style="margin-top:6px" @click="copyCaptions">复制图注归总</n-button>
          </n-collapse-item>
        </n-collapse>
      </div>
    </div>

    <!-- 导出图像 -->
    <n-modal v-model:show="exportShow" preset="card" style="width: 460px" title="导出图像">
      <n-form label-placement="left" label-width="80px" size="small">
        <n-form-item label="分辨率">
          <n-radio-group v-model:value="exportDpi">
            <n-radio-button :value="150">150 DPI</n-radio-button>
            <n-radio-button :value="300">300 DPI</n-radio-button>
            <n-radio-button :value="600">600 DPI</n-radio-button>
          </n-radio-group>
        </n-form-item>
        <n-form-item label="格式">
          <n-radio-group v-model:value="exportFormat">
            <n-radio-button value="png">PNG（无损）</n-radio-button>
            <n-radio-button value="jpg">JPG（小体积）</n-radio-button>
            <n-radio-button value="pdf">PDF（单页）</n-radio-button>
          </n-radio-group>
        </n-form-item>
        <div class="tip">
          PDF 按画板等比生成单页。分层 PSD 需安装 <code>ag-psd</code> 依赖后启用，当前可先导出 PNG 再用 Photoshop 转换。
        </div>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="exportShow = false">取消</n-button>
          <n-button type="primary" @click="doExport">导出</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 裁切图片 -->
    <n-modal v-model:show="cropShow" preset="card" style="width: 560px" title="裁切图片">
      <div v-if="cropTarget" class="crop-stage">
        <div class="crop-frame">
          <img :src="resolveSrc(cropTarget.src)" class="crop-img" />
          <div class="crop-window" :style="cropWindowStyle"></div>
        </div>
      </div>
      <n-form label-placement="left" label-width="48" size="small" style="margin-top: 12px">
        <n-form-item label="上"><n-slider v-model:value="crop.top" :max="49" :step="1" /></n-form-item>
        <n-form-item label="下"><n-slider v-model:value="crop.bottom" :max="49" :step="1" /></n-form-item>
        <n-form-item label="左"><n-slider v-model:value="crop.left" :max="49" :step="1" /></n-form-item>
        <n-form-item label="右"><n-slider v-model:value="crop.right" :max="49" :step="1" /></n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="space-between">
          <n-button @click="cropSmartBox">智能定位（去白边）</n-button>
          <n-space>
            <n-button @click="cropShow = false">取消</n-button>
            <n-button type="primary" @click="applyManualCrop">应用裁切</n-button>
          </n-space>
        </n-space>
      </template>
    </n-modal>
  </n-card>
</template>

<script lang="ts" setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useMessage } from 'naive-ui';
import {
  Stage as VStage,
  Layer as VLayer,
  Rect as VRect,
  Image as VImage,
  Text as VText,
  Line as VLine,
  Transformer as VTransformer,
} from 'vue-konva';
import { get, update } from '../../../api/imageLayout';
import { fileUrl } from '@/utils/path';
import { useAppStore } from '@/store';
import request from '@/utils/request';
// @ts-ignore html2pdf.js 无类型声明
import html2pdf from 'html2pdf.js';

/** 全局文件上传中标志（由 h-upload 自身维护，true 表示有文件正在上传） */
const appStore = useAppStore();

const route = useRoute();
const router = useRouter();
const message = useMessage();

/** 路径/URL → 可显示地址；base64（历史数据）原样返回 */
function resolveSrc(src?: string): string {
  if (!src) return '';
  if (src.startsWith('data:') || /^https?:/.test(src) || /^blob:/.test(src)) return src;
  return fileUrl(src);
}

/** 把图片路径加载成 HTMLImageElement 供 konva 使用 */
function loadHtmlImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => resolve(img);
    img.onerror = reject;
    img.src = src;
  });
}
const id = (route.query.id as string) || '';
if (!id) {
  message.warning('缺少 ID');
  router.replace('/wms/research/imageLayout');
}

interface CanvasItem {
  id: string;
  type: 'image' | 'label' | 'scale';
  x: number;
  y: number;
  scale?: number;
  realCm?: number;
  scaleCm?: number; // scale bar 刻度长度（cm）
  natW?: number;
  natH?: number;
  text?: string;
  src?: string;
  image?: HTMLImageElement;
  artifactId?: string;
  /** 指向所属 image item.id —— image+label 组合为一个组：同移同删 */
  parentId?: string;
  /** 上传时的原始文件名（去后缀），用于"+图号"默认值 */
  name?: string;
  /** label 子项相对父图片左边的横向偏移（px，画布坐标） */
  relX?: number;
  /** label 子项相对父图片下沿的纵向偏移（px，向下为正） */
  relYFromBottom?: number;

  /** ===== 文件名解析（单位_编号_器名） ===== */
  unit?: string;          // 出土单位，如 H37
  code?: string;          // 编号（在该单位下），如 2
  artifactName?: string;  // 器名，如 尖底瓶
  /** ===== 自动排版后分配的序号（1-based） ===== */
  seq?: number;
}

interface Connector {
  id: string;
  fromId: string;
  toId: string;
}

const form = ref<any>({ width: 794, height: 1123 });
const items = ref<CanvasItem[]>([]);
const connectors = ref<Connector[]>([]);
const selectedId = ref<string | null>(null);
const selectedItem = computed<CanvasItem | undefined>(() =>
  items.value.find((i) => i.id === selectedId.value),
);
const saving = ref(false);
const showGrid = ref(true);
const stageRef = ref();
const transformerRef = ref();
const canvasWrap = ref();

/** 当前选中是否为图片 —— 用于禁用"+图号"按钮（只有图片才能配图号） */
const isImageSelected = computed(() => selectedItem.value?.type === 'image');

/** 左栏标题角标：图片数量 */
const imageCount = computed(() => items.value.filter((i) => i.type === 'image').length);

// 比例尺
const pxPerCm = ref(10);

// 画板缩放（仅影响显示；导出/保存基于原始尺寸）
const stageScale = ref(1);
const ZOOM_MIN = 0.1;
const ZOOM_MAX = 4;
function zoomIn() { stageScale.value = Math.min(ZOOM_MAX, +(stageScale.value * 1.2).toFixed(3)); }
function zoomOut() { stageScale.value = Math.max(ZOOM_MIN, +(stageScale.value / 1.2).toFixed(3)); }
function resetZoom() { stageScale.value = 1; }
/** 按容器尺寸适应窗口 */
function fitToScreen() {
  const wrap = canvasWrap.value as HTMLElement | undefined;
  if (!wrap) return;
  const padPx = 32;
  const sx = (wrap.clientWidth - padPx) / (form.value.width || 794);
  const sy = (wrap.clientHeight - padPx) / (form.value.height || 1123);
  stageScale.value = +Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, Math.min(sx, sy))).toFixed(3);
}
/** Ctrl + 滚轮缩放 */
function onWheel(e: WheelEvent) {
  if (!(e.ctrlKey || e.metaKey)) return;
  e.preventDefault();
  e.deltaY < 0 ? zoomIn() : zoomOut();
}

// 连接模式
const connectMode = ref(false);
const pendingConnectId = ref<string | null>(null);

// ===== 自动排版 / 编号 / 图注（参考"考古器物线图排版工具"视频） =====
type GroupBy = 'none' | 'unit' | 'category';
type Direction = 'h' | 'v';
type CaptionFormat = 'classic' | 'paren' | 'underDraw' | 'custom';
const cols = ref(3);
const gap = ref(40);
const padding = ref(40);
const groupBy = ref<GroupBy>('unit');
const numberingDir = ref<Direction>('h');
const captionFormat = ref<CaptionFormat>('classic');
/** 自定义图注模板：{seq} 序号 {name} 器名 {code} 编号 {unit} 单位 {nums} 合并序号 {codes} 合并编号 */
const captionTemplate = ref('{seq}. {name}（{unit}:{code}）');
const captionFontSize = ref(14);
const showCaptionBlock = ref(true);

// 导出 modal
const exportShow = ref(false);
const exportDpi = ref<150 | 300 | 600>(300);
const exportFormat = ref<'jpg' | 'png' | 'pdf'>('png');

// 排版模式：标准（按行列自动排版）/ 自定义（手动摆放）
const layoutMode = ref<'standard' | 'custom'>('standard');

// 多选 / 框选
const selectedIds = ref<string[]>([]);
const selecting = ref(false);
const selRect = ref({ x: 0, y: 0, w: 0, h: 0 });
let selStart = { x: 0, y: 0 };

// 参考线（不计入导出）
interface Guide {
  id: string;
  dir: 'h' | 'v';
  pos: number;
}
const guides = ref<Guide[]>([]);
const guideOptions = [
  { label: '加横参考线', key: 'gh' },
  { label: '加竖参考线', key: 'gv' },
];

// 导出时隐藏网格/参考线等辅助元素
const exportingFlag = ref(false);

// 裁切
const cropShow = ref(false);
const cropTarget = ref<CanvasItem | null>(null);
const crop = ref({ top: 0, right: 0, bottom: 0, left: 0 });

/**
 * 文件名解析：默认按 "单位_编号_器名.png" / "H37-2-尖底瓶.jpg" / "H37 2 尖底瓶.png" 拆。
 * 三段以上：第一段=unit，第二段=code，剩余拼回器名。
 * 单段：全部当成 artifactName。
 */
function parseFilename(name?: string): { unit?: string; code?: string; artifactName?: string } {
  if (!name) return {};
  const base = stripExt(name).trim();
  const parts = base.split(/[_\-\s]+/).filter(Boolean);
  if (parts.length >= 3) {
    return { unit: parts[0], code: parts[1], artifactName: parts.slice(2).join('') };
  }
  if (parts.length === 2) return { unit: parts[0], code: parts[1] };
  return { artifactName: base };
}

const gridLines = computed(() => {
  if (!showGrid.value || exportingFlag.value) return [];
  const w = form.value.width || 794;
  const h = form.value.height || 1123;
  const step = 40;
  const lines: number[][] = [];
  for (let x = step; x < w; x += step) lines.push([x, 0, x, h]);
  for (let y = step; y < h; y += step) lines.push([0, y, w, y]);
  return lines;
});

function toggleGrid() {
  showGrid.value = !showGrid.value;
}

function makeId() {
  return 'i_' + Math.random().toString(36).slice(2, 9);
}

/** 去掉文件名后缀 */
function stripExt(name?: string): string {
  if (!name) return '';
  const i = name.lastIndexOf('.');
  return i > 0 ? name.slice(0, i) : name;
}

/**
 * h-upload 上传完成回调：file.fullPath 是文件服务返回的相对路径，content 只存这个路径。
 * 自动给图片附一个"图号"label 作为子项（parentId 链接），默认文字 = 文件名去后缀。
 */
async function onImageUploaded(file: any) {
  try {
    const path: string = file?.fullPath || file?.url;
    if (!path) {
      message.error('上传失败：未返回文件路径');
      return;
    }
    const img = await loadHtmlImage(resolveSrc(path));
    const imgScale = Math.min(1, 200 / Math.max(img.width, img.height));
    const parsed = parseFilename(file?.name);
    const imgItem: CanvasItem = {
      id: makeId(),
      type: 'image',
      x: 40,
      y: 40,
      scale: imgScale,
      natW: img.width,
      natH: img.height,
      src: path,
      image: img,
      name: stripExt(file?.name),
      ...parsed,
    };
    items.value.push(imgItem);

    // 自动附一个图号 label（image + label = 一个组）
    const dispH = img.height * imgScale;
    items.value.push({
      id: makeId(),
      type: 'label',
      parentId: imgItem.id,
      x: imgItem.x,
      y: imgItem.y + dispH + 6,
      text: imgItem.name || '图号',
      relX: 0,
      relYFromBottom: 6,
    });
  } catch (e: any) {
    message.error(e?.message || '加载图片失败');
  }
}

/** ===== 连接线 ===== */
function toggleConnect() {
  connectMode.value = !connectMode.value;
  pendingConnectId.value = null;
}

function onImageClick(it: CanvasItem, e?: any) {
  if (!connectMode.value) {
    onSelect(it.id, !!e?.evt?.shiftKey);
    return;
  }
  if (!pendingConnectId.value) {
    pendingConnectId.value = it.id;
  } else if (pendingConnectId.value !== it.id) {
    connectors.value.push({ id: makeId(), fromId: pendingConnectId.value, toId: it.id });
    pendingConnectId.value = null;
    connectMode.value = false;
  }
}

function imageRect(item: CanvasItem) {
  const w = (item.natW || 100) * (item.scale || 1);
  const h = (item.natH || 100) * (item.scale || 1);
  return { x: item.x, y: item.y, w, h, cx: item.x + w / 2, cy: item.y + h / 2 };
}

/**
 * 连接线锚点：取两图"相对的边缘中点"（不是中心），并在两端各留 GAP 空隙，
 * 使连线不与图片完全相连（符合考古排版"短划线连接"惯例）。
 */
const CONNECT_GAP = 6;
function connectorPoints(c: Connector): number[] {
  const from = items.value.find((i) => i.id === c.fromId);
  const to = items.value.find((i) => i.id === c.toId);
  if (!from || !to) return [0, 0, 0, 0];
  const a = imageRect(from);
  const b = imageRect(to);
  const dx = b.cx - a.cx;
  const dy = b.cy - a.cy;
  let p1: [number, number];
  let p2: [number, number];
  if (Math.abs(dx) >= Math.abs(dy)) {
    // 主要为横向关系：连接左右相对边的中点
    const s = dx >= 0 ? 1 : -1;
    p1 = [s > 0 ? a.x + a.w : a.x, a.cy];
    p2 = [s > 0 ? b.x : b.x + b.w, b.cy];
    p1[0] += s * CONNECT_GAP;
    p2[0] -= s * CONNECT_GAP;
  } else {
    // 主要为纵向关系：连接上下相对边的中点
    const s = dy >= 0 ? 1 : -1;
    p1 = [a.cx, s > 0 ? a.y + a.h : a.y];
    p2 = [b.cx, s > 0 ? b.y : b.y + b.h];
    p1[1] += s * CONNECT_GAP;
    p2[1] -= s * CONNECT_GAP;
  }
  return [p1[0], p1[1], p2[0], p2[1]];
}

/** ===== 比例尺 ===== */
function scaleBarPoints(it: CanvasItem): number[] {
  const cm = it.scaleCm || 5;
  return [0, 0, cm * pxPerCm.value, 0];
}

/** 当前图实际比例尺文字，如 "1:2.5" */
function ratioText(it: CanvasItem): string {
  if (!it.realCm || !it.natW) return '--';
  const displayLongestPx = Math.max(it.natW, it.natH || 0) * (it.scale || 1);
  const displayLongestCm = displayLongestPx / pxPerCm.value; // 画面上的厘米数
  if (displayLongestCm <= 0) return '--';
  const ratio = it.realCm / displayLongestCm; // 实物 : 画面
  return ratio >= 1 ? `${ratio.toFixed(1)}:1` : `1:${(1 / ratio).toFixed(1)}`;
}

/** 按实际尺寸把所有图统一到同一比例（pxPerCm） */
function applyUnifiedScale() {
  let applied = 0;
  items.value.forEach((it) => {
    if (it.type !== 'image' || !it.realCm || !it.natW) return;
    const longestNat = Math.max(it.natW, it.natH || 0);
    // 实物 realCm → 画面 realCm*pxPerCm 像素
    it.scale = (it.realCm * pxPerCm.value) / longestNat;
    applied++;
  });
  // 同步标尺刻度（若存在）
  if (applied) message.success(`已按比例统一缩放 ${applied} 张图`);
  else message.warning('请先为图片填写"实际尺寸(cm)"');
}

/**
 * "+图号"：给选中的图片添加 / 重新选中其图号 label。
 * 默认文字 = 上传时记录的文件名（去后缀），允许在属性面板编辑。
 */
function addLabel() {
  const target = selectedItem.value;
  if (!target || target.type !== 'image') return;
  const existing = items.value.find((i) => i.type === 'label' && i.parentId === target.id);
  if (existing) {
    selectedId.value = existing.id;
    return;
  }
  const dispH = (target.natH || 100) * (target.scale || 1);
  items.value.push({
    id: makeId(),
    type: 'label',
    parentId: target.id,
    x: target.x,
    y: target.y + dispH + 6,
    text: target.name || '图号',
    relX: 0,
    relYFromBottom: 6,
  });
}

function addScaleBar() {
  items.value.push({ id: makeId(), type: 'scale', x: 40, y: (form.value.height || 1123) - 60, scaleCm: 5 });
}

function onSelect(id: string, additive = false) {
  if (additive) {
    const i = selectedIds.value.indexOf(id);
    if (i >= 0) selectedIds.value.splice(i, 1);
    else selectedIds.value.push(id);
    selectedId.value = selectedIds.value[selectedIds.value.length - 1] ?? null;
  } else {
    selectedIds.value = [id];
    selectedId.value = id;
  }
}

/** 屏幕指针 → 画布逻辑坐标（考虑画板缩放） */
function pointerLogical(stage: any): { x: number; y: number } {
  const pos = stage.getPointerPosition();
  if (!pos) return { x: 0, y: 0 };
  const t = stage.getAbsoluteTransform().copy().invert();
  return t.point(pos);
}

/** 画板按下：点空白处取消选中并开始框选；点节点交由各节点处理 */
function onStageMouseDown(e: any) {
  const stage = e.target?.getStage?.();
  if (!stage) return;
  const onEmpty = e.target === stage || e.target?.attrs?.__bg === true;
  if (connectMode.value) {
    if (onEmpty) pendingConnectId.value = null;
    return;
  }
  if (!onEmpty) return;
  const p = pointerLogical(stage);
  selStart = p;
  selecting.value = true;
  selRect.value = { x: p.x, y: p.y, w: 0, h: 0 };
  if (!e.evt?.shiftKey) {
    selectedIds.value = [];
    selectedId.value = null;
  }
}
function onStageMouseMove(e: any) {
  if (!selecting.value) return;
  const stage = e.target?.getStage?.();
  if (!stage) return;
  const p = pointerLogical(stage);
  selRect.value = {
    x: Math.min(selStart.x, p.x),
    y: Math.min(selStart.y, p.y),
    w: Math.abs(p.x - selStart.x),
    h: Math.abs(p.y - selStart.y),
  };
}
function onStageMouseUp() {
  if (!selecting.value) return;
  selecting.value = false;
  const r = selRect.value;
  if (r.w < 4 && r.h < 4) return; // 视为单击空白
  const hit = items.value.filter((it) => {
    if (it.type !== 'image') return false;
    const ir = imageRect(it);
    return ir.x < r.x + r.w && ir.x + ir.w > r.x && ir.y < r.y + r.h && ir.y + ir.h > r.y;
  });
  selectedIds.value = Array.from(new Set([...selectedIds.value, ...hit.map((i) => i.id)]));
  selectedId.value = selectedIds.value[selectedIds.value.length - 1] ?? null;
}

/**
 * 把子项（图号 label）按"相对父图片左下角"的偏移重新定位。
 * 这样无论父图片是被拖动还是被缩放（拖手柄改变 scale 但 x/y 可能不变），
 * 子 label 都能跟着图片的实际显示边界走。
 */
function syncChildrenToImage(image: CanvasItem) {
  const dispH = (image.natH || 0) * (image.scale || 1);
  items.value.forEach((c) => {
    if (c.parentId !== image.id) return;
    // 旧数据可能没有 rel*，回退到当前差值（保留用户已有位置）并就地补齐
    const relX = c.relX ?? c.x - image.x;
    const relYFromBottom = c.relYFromBottom ?? c.y - (image.y + dispH);
    c.relX = relX;
    c.relYFromBottom = relYFromBottom;
    c.x = image.x + relX;
    c.y = image.y + dispH + relYFromBottom;
  });
}

/** 普通 dragend（label / scale）。
 *  如果是 label 子项被独立拖动，更新它的 rel* 偏移，使新位置成为"相对父图片"的新锚点。 */
function onDragEnd(item: CanvasItem, e: any) {
  item.x = e.target.x();
  item.y = e.target.y();
  if (item.type === 'label' && item.parentId) {
    const parent = items.value.find((i) => i.id === item.parentId);
    if (parent && parent.type === 'image') {
      const dispH = (parent.natH || 0) * (parent.scale || 1);
      item.relX = item.x - parent.x;
      item.relYFromBottom = item.y - (parent.y + dispH);
    }
  }
}

/** 图片拖动：把子项随父图片平移到新位置（基于 rel* 锚点重算） */
function onImageDragEnd(item: CanvasItem, e: any) {
  item.x = e.target.x();
  item.y = e.target.y();
  syncChildrenToImage(item);
}

/**
 * Transformer 拖手柄结束：把 konva 的 scaleX/Y 同步回 item.scale。
 * keepRatio=true 时 scaleX === scaleY。
 * 缩放后图片底部位置变化，子 label 通过 rel* 锚点自动跟随到新底沿。
 */
function onImageTransformEnd(item: CanvasItem, e: any) {
  const node = e.target;
  item.scale = node.scaleX();
  item.x = node.x();
  item.y = node.y();
  syncChildrenToImage(item);
}

function deleteSelected() {
  const ids = selectedIds.value.length ? [...selectedIds.value] : selectedId.value ? [selectedId.value] : [];
  if (!ids.length) return;
  const idset = new Set(ids);
  // 同时删除关联连接线 + 子项（图号 label 等）
  connectors.value = connectors.value.filter((c) => !idset.has(c.id) && !idset.has(c.fromId) && !idset.has(c.toId));
  items.value = items.value.filter((i) => !idset.has(i.id) && !(i.parentId && idset.has(i.parentId)));
  selectedIds.value = [];
  selectedId.value = null;
}

/** 一键清空画板（保留 form 配置） */
function clearAll() {
  items.value = [];
  connectors.value = [];
  selectedId.value = null;
  message.success('已清空');
}

/** 选中变化 → 把 transformer 挂到所有选中的图片节点（支持多选同时缩放） */
watch(
  selectedIds,
  async () => {
    await nextTick();
    const stage = stageRef.value?.getStage?.();
    const transformer = transformerRef.value?.getNode?.();
    if (!stage || !transformer) return;
    const nodes = selectedIds.value
      .map((sid) => items.value.find((i) => i.id === sid))
      .filter((it): it is CanvasItem => !!it && it.type === 'image')
      .map((it) => stage.findOne('#' + it.id))
      .filter(Boolean);
    transformer.nodes(nodes as any);
    transformer.getLayer()?.batchDraw();
  },
  { deep: true },
);
function updateScaleByReal() {
  const it = selectedItem.value;
  if (!it || it.type !== 'image' || !it.realCm || !it.image) return;
  // 按统一比例 pxPerCm 计算：实物 realCm → 画面 realCm*pxPerCm 像素
  const targetPx = it.realCm * pxPerCm.value;
  it.scale = targetPx / Math.max(it.image.width, it.image.height, 1);
}

/** ===================================================================
 *  自动排版：把所有 image 按 cols 网格摆放，可按 unit / artifactName 分组。
 *  顺序：分组键升序 → 组内按当前 items 顺序（保留导入顺序）。
 *  每件器物占用一格，格宽=组内最宽缩放后宽度，避免重叠；
 *  组与组之间空一行（用 2*gap 间距）。
 * =================================================================== */
function imageItems(): CanvasItem[] {
  return items.value.filter((i) => i.type === 'image');
}

function groupKey(it: CanvasItem): string {
  if (groupBy.value === 'unit') return it.unit || '其他';
  if (groupBy.value === 'category') return it.artifactName || '其他';
  return '_';
}

function dispSize(it: CanvasItem): [number, number] {
  return [(it.natW || 100) * (it.scale || 1), (it.natH || 100) * (it.scale || 1)];
}

function autoLayout() {
  const imgs = imageItems();
  if (!imgs.length) {
    message.warning('请先导入图片');
    return;
  }
  // 分组（保持组内导入顺序）
  const groupsMap = new Map<string, CanvasItem[]>();
  imgs.forEach((it) => {
    const k = groupKey(it);
    if (!groupsMap.has(k)) groupsMap.set(k, []);
    groupsMap.get(k)!.push(it);
  });
  const groups = Array.from(groupsMap.entries()).sort(([a], [b]) => a.localeCompare(b));

  const c = Math.max(1, cols.value);
  let y = padding.value;
  let maxReqW = 0; // 各组实际占用宽度的最大值，用于自动扩宽
  groups.forEach(([_k, list]) => {
    // 组内：每行最高的图决定行高，cell 宽 = 整组里最宽的；序号由 recomputeSeq 后赋
    const cellW = Math.max(...list.map((it) => dispSize(it)[0])) || 100;
    const colsInThisGroup = Math.min(c, list.length); // 不足一行时按实际数算占宽
    const reqW = padding.value * 2 + colsInThisGroup * cellW + Math.max(0, colsInThisGroup - 1) * gap.value;
    if (reqW > maxReqW) maxReqW = reqW;
    let row = 0;
    let rowMaxH = 0;
    list.forEach((it, idx) => {
      const col = idx % c;
      if (col === 0 && idx !== 0) {
        y += rowMaxH + gap.value;
        rowMaxH = 0;
        row++;
      }
      const [w, h] = dispSize(it);
      it.x = padding.value + col * (cellW + gap.value) + (cellW - w) / 2; // 居中
      it.y = y;
      rowMaxH = Math.max(rowMaxH, h);
      syncChildrenToImage(it);
    });
    y += rowMaxH + gap.value * 2; // 组间空一行
  });
  // 自动扩宽：宽度不够时把画板扩到能装下最宽一组
  if (maxReqW > (form.value.width || 0)) form.value.width = Math.ceil(maxReqW);
  // 自动扩高（留 80px 给底部图注块）
  const targetH = Math.ceil(y + 80);
  if (targetH > (form.value.height || 0)) form.value.height = targetH;
  // 排版完成后自动适应窗口，避免用户对着空白滚动找
  nextTick(fitToScreen);

  recomputeSeq();
  refreshLabels();
  message.success(`已按 ${cols.value} 列排版 ${imgs.length} 件`);
}

/** 按"横向优先 / 纵向优先"对图片重新编号（基于当前画布位置）。 */
function recomputeSeq() {
  const imgs = imageItems();
  // 按行（top→bottom），行内按 left→right —— 这就是"横向"；
  // 纵向则按列（left→right），列内 top→bottom
  const rowTol = 40; // 同行容差
  imgs.sort((a, b) => {
    if (numberingDir.value === 'h') {
      const dy = a.y - b.y;
      if (Math.abs(dy) > rowTol) return dy;
      return a.x - b.x;
    } else {
      const dx = a.x - b.x;
      if (Math.abs(dx) > rowTol) return dx;
      return a.y - b.y;
    }
  });
  imgs.forEach((it, idx) => {
    it.seq = idx + 1;
  });
}

/** 用模板渲染单张图的下方文字（"格式三 / 自定义"才在线图下显示器名） */
function renderImageLabel(it: CanvasItem): string {
  const f = captionFormat.value;
  if (f === 'classic' || f === 'paren') return String(it.seq ?? '');
  const tpl = f === 'underDraw' ? '{name}\n{unit}:{code}' : captionTemplate.value;
  return tpl
    .replace(/\{seq\}/g, String(it.seq ?? ''))
    .replace(/\{name\}/g, it.artifactName || it.name || '')
    .replace(/\{code\}/g, it.code || '')
    .replace(/\{unit\}/g, it.unit || '');
}

/**
 * 自动刷新所有 image 子 label 的 text（不动用户手动定位）。
 * 先按当前 x/y 重新分配序号（这样未排过版的图也有 1,2,3...），再按当前格式渲染。
 * 若用户希望只换格式不换序号，autoLayout 内部仍会全量 recompute。
 */
function refreshLabels() {
  recomputeSeq();
  const imgs = imageItems();
  imgs.forEach((it) => {
    let label = items.value.find((c) => c.type === 'label' && c.parentId === it.id);
    // 若图片之前没有 label（旧数据 / 用户删过），自动补一个挂在底沿
    if (!label) {
      const dispH = (it.natH || 100) * (it.scale || 1);
      label = {
        id: makeId(),
        type: 'label',
        parentId: it.id,
        x: it.x,
        y: it.y + dispH + 6,
        text: '',
        relX: 0,
        relYFromBottom: 6,
      };
      items.value.push(label);
    }
    label.text = renderImageLabel(it);
  });
  message.success(`已刷新 ${imgs.length} 个图注`);
}

/**
 * 同名器合并：按 artifactName 分组，组内按 seq 升序拼成 "1、6、10. 尖底瓶 (H37:2,11,10)"
 * 没有 artifactName 的单列。
 */
const captionLines = computed<string[]>(() => {
  if (!showCaptionBlock.value) return [];
  const imgs = imageItems().filter((i) => typeof i.seq === 'number');
  if (!imgs.length) return [];
  const sorted = [...imgs].sort((a, b) => (a.seq! - b.seq!));
  // 按器名+单位（保留单位差异，避免不同单位同名被合并）分组
  const buckets = new Map<string, CanvasItem[]>();
  sorted.forEach((it) => {
    const k = `${it.artifactName || '未命名'}@@${it.unit || ''}`;
    if (!buckets.has(k)) buckets.set(k, []);
    buckets.get(k)!.push(it);
  });
  return Array.from(buckets.entries()).map(([k, list]) => {
    const [name] = k.split('@@');
    const nums = list.map((i) => i.seq).join('、');
    const codes = list.map((i) => i.code).filter(Boolean).join(',');
    const unit = list[0].unit;
    if (captionFormat.value === 'paren') {
      return codes ? `${nums}. ${name}（${unit ? unit + ':' : ''}${codes}）` : `${nums}. ${name}`;
    }
    if (captionFormat.value === 'custom') {
      return captionTemplate.value
        .replace(/\{seq\}/g, '')
        .replace(/\{nums\}/g, nums)
        .replace(/\{name\}/g, name)
        .replace(/\{code\}/g, list[0].code || '')
        .replace(/\{codes\}/g, codes)
        .replace(/\{unit\}/g, unit || '');
    }
    // classic / underDraw 都用经典格式
    return codes ? `${nums}. ${name} (${unit ? unit + ':' : ''}${codes})` : `${nums}. ${name}`;
  });
});

/** ===== 比例尺预设 ===== */
function applyScalePreset(ratio: number) {
  // ratio = 实物长度cm / 画面长度cm —— >1 表示缩小（如 1:2），<1 表示放大（如 2:1 → 0.5）
  const sel = items.value.filter((i) => i.type === 'image' && selectedIds.value.includes(i.id));
  const targets = sel.length ? sel : imageItems();
  let n = 0;
  targets.forEach((it) => {
    if (!it.realCm || !it.natW) return;
    // 画面期望显示长度 = realCm / ratio （cm） → px = realCm/ratio * pxPerCm
    const longestNat = Math.max(it.natW, it.natH || 0);
    it.scale = (it.realCm / ratio) * pxPerCm.value / longestNat;
    syncChildrenToImage(it);
    n++;
  });
  if (n === 0) message.warning('请先为图片填写"实际尺寸 cm"');
  else message.success(`已应用比例 ${ratio >= 1 ? `1:${ratio}` : `${1 / ratio}:1`} (${n} 张)`);
}

/** ===== 导出图注为 txt ===== */
function downloadCaptions() {
  const lines = captionLines.value;
  if (!lines.length) {
    message.warning('当前没有可导出的图注');
    return;
  }
  const blob = new Blob([lines.join('\n')], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${form.value.title || '图注'}.txt`;
  a.click();
  URL.revokeObjectURL(url);
}

/** ===== 复制图注到剪贴板 ===== */
async function copyCaptions() {
  const lines = captionLines.value;
  if (!lines.length) {
    message.warning('当前没有可复制的图注');
    return;
  }
  try {
    await navigator.clipboard.writeText(lines.join('\n'));
    message.success('图注已复制到剪贴板');
  } catch {
    message.error('复制失败，请改用导出 .txt');
  }
}

/** ===== 比例尺左对齐 ===== */
function alignScaleBars() {
  const bars = items.value.filter((i) => i.type === 'scale');
  if (bars.length < 2) {
    message.info('至少需要两个比例尺才能对齐');
    return;
  }
  const minX = Math.min(...bars.map((b) => b.x));
  bars.forEach((b) => (b.x = minX));
  message.success(`已左对齐 ${bars.length} 个比例尺`);
}

/** ===== 参考线 ===== */
function addGuide(dir: 'h' | 'v') {
  const w = form.value.width || 794;
  const h = form.value.height || 1123;
  guides.value.push({ id: makeId(), dir, pos: dir === 'h' ? Math.round(h / 2) : Math.round(w / 2) });
}
function guidePoints(g: Guide): number[] {
  const w = form.value.width || 794;
  const h = form.value.height || 1123;
  return g.dir === 'h' ? [0, 0, w, 0] : [0, 0, 0, h];
}
function guideDragBound(g: Guide) {
  return (pos: { x: number; y: number }) => (g.dir === 'h' ? { x: 0, y: pos.y } : { x: pos.x, y: 0 });
}
function onGuideDragEnd(g: Guide, e: any) {
  g.pos = g.dir === 'h' ? Math.round(e.target.y()) : Math.round(e.target.x());
}
function removeGuide(g: Guide) {
  guides.value = guides.value.filter((x) => x.id !== g.id);
}

/** ===== 裁切 ===== */
const cropWindowStyle = computed(() => ({
  top: crop.value.top + '%',
  right: crop.value.right + '%',
  bottom: crop.value.bottom + '%',
  left: crop.value.left + '%',
}));

/** 扫描像素，求"非空白(透明/纯白)"内容的外接框 */
function computeTrimBox(img: HTMLImageElement, threshold = 245) {
  const w = img.naturalWidth || img.width;
  const h = img.naturalHeight || img.height;
  if (!w || !h) return null;
  const c = document.createElement('canvas');
  c.width = w;
  c.height = h;
  const ctx = c.getContext('2d');
  if (!ctx) return null;
  ctx.drawImage(img, 0, 0);
  let data: Uint8ClampedArray;
  try {
    data = ctx.getImageData(0, 0, w, h).data;
  } catch {
    return null; // 跨域污染
  }
  let minX = w;
  let minY = h;
  let maxX = 0;
  let maxY = 0;
  let found = false;
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const i = (y * w + x) * 4;
      const blank = data[i + 3] < 10 || (data[i] > threshold && data[i + 1] > threshold && data[i + 2] > threshold);
      if (!blank) {
        found = true;
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
      }
    }
  }
  if (!found) return null;
  const pad = 2;
  minX = Math.max(0, minX - pad);
  minY = Math.max(0, minY - pad);
  maxX = Math.min(w - 1, maxX + pad);
  maxY = Math.min(h - 1, maxY + pad);
  return { x: minX, y: minY, w: maxX - minX + 1, h: maxY - minY + 1 };
}

/** 把裁切框（自然像素坐标）应用到图片：生成新的位图替换 image/src/natW/natH */
function applyCropToItem(it: CanvasItem, box: { x: number; y: number; w: number; h: number }) {
  if (!it.image) return;
  const c = document.createElement('canvas');
  c.width = box.w;
  c.height = box.h;
  const ctx = c.getContext('2d');
  if (!ctx) return;
  ctx.drawImage(it.image, box.x, box.y, box.w, box.h, 0, 0, box.w, box.h);
  const dataUrl = c.toDataURL('image/png');
  const newImg = new Image();
  newImg.crossOrigin = 'anonymous';
  newImg.onload = () => {
    it.image = newImg;
    it.src = dataUrl;
    it.natW = newImg.width;
    it.natH = newImg.height;
    syncChildrenToImage(it);
  };
  newImg.src = dataUrl;
}

function openCrop() {
  const it = selectedItem.value;
  if (!it || it.type !== 'image') {
    message.warning('请先选中一张图片');
    return;
  }
  cropTarget.value = it;
  crop.value = { top: 0, right: 0, bottom: 0, left: 0 };
  cropShow.value = true;
}
/** 智能定位裁框：自动去除上下左右空白 */
function cropSmartBox() {
  const it = cropTarget.value;
  if (!it?.image) return;
  const box = computeTrimBox(it.image);
  if (!box) {
    message.info('未检测到明显空白边（或图片跨域无法分析）');
    return;
  }
  const W = it.image.naturalWidth || it.image.width;
  const H = it.image.naturalHeight || it.image.height;
  crop.value = {
    left: +((box.x / W) * 100).toFixed(2),
    top: +((box.y / H) * 100).toFixed(2),
    right: +(((W - (box.x + box.w)) / W) * 100).toFixed(2),
    bottom: +(((H - (box.y + box.h)) / H) * 100).toFixed(2),
  };
}
function applyManualCrop() {
  const it = cropTarget.value;
  if (!it?.image) {
    cropShow.value = false;
    return;
  }
  const W = it.image.naturalWidth || it.image.width;
  const H = it.image.naturalHeight || it.image.height;
  const x = Math.round((W * crop.value.left) / 100);
  const y = Math.round((H * crop.value.top) / 100);
  const w = Math.round(W * (1 - crop.value.left / 100 - crop.value.right / 100));
  const h = Math.round(H * (1 - crop.value.top / 100 - crop.value.bottom / 100));
  if (w <= 0 || h <= 0) {
    message.warning('裁切区域无效');
    return;
  }
  applyCropToItem(it, { x, y, w, h });
  cropShow.value = false;
  message.success('已裁切');
}

/** ===== 通用导出 =====
 *  PNG/JPG：DPI 150/300/600 → pixelRatio = DPI / 96（屏幕基准）
 *  PDF：把整幅位图放进与画板等比的单页 PDF
 *  PSD（分层）：需安装 ag-psd，暂未内置
 */
function doExport() {
  if (exportFormat.value === 'pdf') {
    exportPdf();
    return;
  }
  const stage = stageRef.value?.getStage?.();
  if (!stage) return;
  selectedIds.value = [];
  selectedId.value = null; // 不带选中框
  exportingFlag.value = true; // 隐藏网格/参考线
  nextTick(() => {
    const pixelRatio = Math.max(1, exportDpi.value / 96);
    const mime = exportFormat.value === 'jpg' ? 'image/jpeg' : 'image/png';
    const url = stage.toDataURL({ pixelRatio, mimeType: mime, quality: exportFormat.value === 'jpg' ? 0.95 : 1 });
    exportingFlag.value = false;
    const a = document.createElement('a');
    a.href = url;
    a.download = `${form.value.title || '图片排版'}_${exportDpi.value}dpi.${exportFormat.value}`;
    a.click();
    exportShow.value = false;
  });
}

async function exportPdf() {
  const stage = stageRef.value?.getStage?.();
  if (!stage) return;
  selectedIds.value = [];
  selectedId.value = null;
  const prevZoom = stageScale.value;
  exportingFlag.value = true;
  stageScale.value = 1;
  await nextTick();
  const pixelRatio = Math.max(1, exportDpi.value / 96);
  const w = form.value.width || 794;
  const h = form.value.height || 1123;
  const dataUrl = stage.toDataURL({ pixelRatio, mimeType: 'image/jpeg', quality: 0.95 });
  exportingFlag.value = false;
  stageScale.value = prevZoom;
  const container = document.createElement('div');
  container.style.cssText = 'position:fixed;left:-99999px;top:0;';
  const img = document.createElement('img');
  img.src = dataUrl;
  img.style.cssText = `width:${w}px;height:${h}px;display:block;`;
  container.appendChild(img);
  document.body.appendChild(container);
  await new Promise<void>((r) => {
    if (img.complete) r();
    else img.onload = () => r();
  });
  try {
    await (html2pdf as any)()
      .set({
        margin: 0,
        filename: `${form.value.title || '图片排版'}.pdf`,
        image: { type: 'jpeg', quality: 0.95 },
        html2canvas: { scale: pixelRatio, useCORS: true, backgroundColor: '#fff' },
        jsPDF: { unit: 'px', format: [w, h], orientation: w > h ? 'landscape' : 'portrait' },
      })
      .from(container)
      .save();
  } catch (e: any) {
    message.error(e?.message || '导出 PDF 失败');
  } finally {
    document.body.removeChild(container);
    exportShow.value = false;
  }
}

/**
 * 把当前画板渲染成缩略图，上传到文件服务，返回相对路径。
 * 复用 /file/upload 接口（与 h-upload 同一通道），列表页 columnType=image 会自动用 fileUrl() 显示。
 * 失败不阻塞保存。
 */
async function generateThumbnail(): Promise<string | null> {
  const stage = stageRef.value?.getStage?.();
  if (!stage) return null;
  const prevSel = selectedId.value;
  const prevZoom = stageScale.value;
  selectedId.value = null; // 不带选中手柄
  stageScale.value = 1;   // 缩略图按 1:1 渲染，避免受当前画板缩放影响
  exportingFlag.value = true; // 隐藏网格/参考线
  await nextTick();
  try {
    // 240px 短边的小图足够列表预览
    const w = form.value.width || 794;
    const h = form.value.height || 1123;
    const targetShortest = 240;
    const pr = targetShortest / Math.min(w, h);
    const dataUrl: string = stage.toDataURL({
      pixelRatio: pr,
      mimeType: 'image/jpeg',
      quality: 0.78,
    });
    const blob = await (await fetch(dataUrl)).blob();
    const fd = new FormData();
    fd.append('file', new File([blob], `thumb_${id}.jpg`, { type: 'image/jpeg' }));
    fd.append('module', 'wms');
    fd.append('contentId', `imageLayoutThumb_${id}`);
    const resp: any = await request({
      url: 'file/upload',
      method: 'post',
      data: fd,
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return resp?.data?.path || null;
  } catch (e) {
    console.warn('缩略图生成失败', e);
    return null;
  } finally {
    selectedId.value = prevSel;
    stageScale.value = prevZoom;
    exportingFlag.value = false;
  }
}

async function handleSave() {
  saving.value = true;
  try {
    // 1) 先生成缩略图（失败不阻塞）
    const thumb = await generateThumbnail();
    if (thumb) form.value.thumbnailUrl = thumb;

    const serial = items.value.map(({ image, ...rest }) => rest); // 去掉 HTMLImageElement
    await update({
      id,
      title: form.value.title,
      thumbnailUrl: form.value.thumbnailUrl,
      content: JSON.stringify({
        items: serial,
        connectors: connectors.value,
        guides: guides.value,
        pxPerCm: pxPerCm.value,
        width: form.value.width,
        height: form.value.height,
        // 排版/编号/图注配置
        layoutCfg: {
          cols: cols.value,
          gap: gap.value,
          padding: padding.value,
          groupBy: groupBy.value,
          numberingDir: numberingDir.value,
          layoutMode: layoutMode.value,
          captionFormat: captionFormat.value,
          captionTemplate: captionTemplate.value,
          captionFontSize: captionFontSize.value,
          showCaptionBlock: showCaptionBlock.value,
        },
      }),
    });
    message.success('保存成功');
  } catch (e: any) {
    message.error(e?.message || '保存失败');
  } finally {
    saving.value = false;
  }
}

function loadImages(serial: any[]) {
  serial.forEach((s) => {
    if (s.type === 'image' && s.src) {
      const img = new Image();
      img.crossOrigin = 'anonymous';
      img.onload = () => {
        items.value.push({ ...s, image: img, natW: s.natW || img.width, natH: s.natH || img.height });
      };
      img.src = resolveSrc(s.src); // 路径 → 可访问 URL（兼容历史 base64）
    } else {
      items.value.push(s);
    }
  });
}

onMounted(async () => {
  try {
    const resp: any = await get(id);
    form.value = resp.data;
    if (form.value.content) {
      const parsed = JSON.parse(form.value.content);
      if (parsed.width) form.value.width = parsed.width;
      if (parsed.height) form.value.height = parsed.height;
      if (parsed.pxPerCm) pxPerCm.value = parsed.pxPerCm;
      if (Array.isArray(parsed.connectors)) connectors.value = parsed.connectors;
      if (Array.isArray(parsed.guides)) guides.value = parsed.guides;
      const cfg = parsed.layoutCfg;
      if (cfg) {
        cols.value = cfg.cols ?? cols.value;
        gap.value = cfg.gap ?? gap.value;
        padding.value = cfg.padding ?? padding.value;
        groupBy.value = cfg.groupBy ?? groupBy.value;
        numberingDir.value = cfg.numberingDir ?? numberingDir.value;
        layoutMode.value = cfg.layoutMode ?? layoutMode.value;
        captionFormat.value = cfg.captionFormat ?? captionFormat.value;
        captionTemplate.value = cfg.captionTemplate ?? captionTemplate.value;
        captionFontSize.value = cfg.captionFontSize ?? captionFontSize.value;
        showCaptionBlock.value = cfg.showCaptionBlock ?? showCaptionBlock.value;
      }
      loadImages(parsed.items || []);
    }
  } catch (e: any) {
    message.error(e?.message || '加载失败');
  }
});
</script>

<style scoped>
.layout-container {
  flex: 1;
  display: flex;
  overflow: hidden;
}
.layout-tools {
  width: 240px;
  padding: 8px 12px 12px;
  overflow: auto;
}
.left-pane {
  border-right: 1px solid #eee;
}
.right-pane {
  width: 280px;
  border-left: 1px solid #eee;
}
.pane-title {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 0 10px;
  border-bottom: 1px solid #eee;
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 500;
  color: #444;
}
.pane-title-extra {
  margin-left: auto;
  color: #999;
  font-weight: normal;
}
.tool-section {
  margin-bottom: 14px;
}
.tool-section h4 {
  margin: 4px 0 8px;
  font-size: 12px;
  color: #666;
  font-weight: 500;
}
/** 2×2 紧凑网格：label 在上、输入框在下，省一半垂直空间 */
.compact-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 10px;
}
.compact-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.compact-cell > label {
  font-size: 11px;
  color: #888;
  line-height: 1.2;
}
.compact-cell :deep(.n-input-number) {
  width: 100%;
}
.tip {
  font-size: 12px;
  color: #999;
  margin-top: 6px;
  line-height: 1.4;
}
.center-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}
.preview-toolbar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 14px;
  border-bottom: 1px solid #eee;
  background: #fafbfc;
}
.preview-title {
  font-size: 13px;
  color: #444;
  letter-spacing: 1px;
}
.zoom-pct {
  display: inline-block;
  min-width: 48px;
  text-align: center;
  font-size: 13px;
  cursor: pointer;
  color: #3DAEFF;
  user-select: none;
}
.canvas-wrap {
  flex: 1;
  overflow: auto;
  background: #f4f4f4;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 20px;
}
/* 裁切预览 */
.crop-stage {
  display: flex;
  justify-content: center;
  background: #f4f4f4;
  padding: 8px;
}
.crop-frame {
  position: relative;
  line-height: 0;
}
.crop-img {
  max-width: 100%;
  max-height: 320px;
  display: block;
}
.crop-window {
  position: absolute;
  border: 1px solid #18a058;
  box-shadow: 0 0 0 9999px rgba(0, 0, 0, 0.45);
  pointer-events: none;
}
</style>
