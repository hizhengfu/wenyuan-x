<template>
  <div class="template-workspace" :style="workspaceTheme">
    <header class="workspace-header panel">
      <div class="root-group">
        <div class="workspace-mark">
          <n-icon :size="22"><CodeIcon /></n-icon>
        </div>
        <n-select
          :value="selectedRoot?.id"
          :options="rootOptions"
          :disabled="busy"
          filterable
          aria-label="文件根目录"
          placeholder="选择文件根目录"
          class="root-select"
          @update:value="openRoot"
        />
        <CmsTemplateActionButton
          label="新建模板"
          :icon="PlusIcon"
          :disabled="busy || !can('CREATE')"
          @click="run(openNewTemplate)"
        />
      </div>
      <div v-if="selectedRoot" class="preview-context">
        <SiteSelect
          auto-select-first
          enabled-only
          :disabled="busy"
          :value="context.siteId || undefined"
          placeholder="选择预览站点"
          class="preview-site"
          @update:value="selectPreviewSite"
        />
        <n-select
          :disabled="busy"
          v-model:value="context.templateCode"
          :options="templateSources.templates"
          class="preview-kind"
          placeholder="预览模板"
          aria-label="预览模板"
        />
        <n-select
          :disabled="busy"
          v-model:value="context.themeCode"
          :options="templateSources.themes"
          class="preview-kind"
          placeholder="预览皮肤"
          aria-label="预览皮肤"
        />
        <n-select
          :disabled="busy"
          v-model:value="context.kind"
          :options="kindOptions"
          class="preview-kind"
        />
        <n-popover
          v-if="context.kind !== 'HOME'"
          trigger="click"
          placement="bottom-start"
          :width="320"
          :show-arrow="false"
        >
          <template #trigger
            ><span class="action-group"
              ><CmsTemplateActionButton
                label="预览条件"
                tooltip="设置栏目、内容、搜索和分页条件"
                :icon="TuneIcon"
                :disabled="busy" /></span
          ></template>
          <div class="preview-conditions">
            <div class="preview-condition-title">{{ kindLabel }}预览条件</div>
            <n-form
              label-placement="left"
              :label-width="78"
              size="small"
              :show-feedback="false"
              class="preview-form"
            >
              <n-form-item
                v-if="context.kind === 'CATEGORY' || context.kind === 'CONTENT'"
                label="栏目"
              >
                <CategorySelect
                  :disabled="busy"
                  v-model:value="context.categoryId"
                  :site-id="context.siteId"
                  placeholder="选择栏目"
                />
              </n-form-item>
              <n-form-item v-if="context.kind === 'CONTENT'" label="内容">
                <n-input
                  :disabled="busy"
                  v-model:value="context.contentId"
                  placeholder="已发布内容 ID 或 code"
                />
              </n-form-item>
              <n-form-item v-if="context.kind === 'SEARCH'" label="内容类型">
                <n-select
                  :disabled="busy"
                  v-model:value="context.module"
                  :options="pluginOptions"
                  placeholder="内容类型"
                />
              </n-form-item>
              <n-form-item v-if="context.kind === 'SEARCH'" label="关键词">
                <n-input
                  :disabled="busy"
                  v-model:value="context.keywords"
                  placeholder="搜索关键词"
                />
              </n-form-item>
              <n-form-item v-if="context.kind !== 'CONTENT'" label="页码">
                <n-input-number
                  :disabled="busy"
                  v-model:value="context.pageNum"
                  :min="1"
                  :max="10000000"
                  aria-label="页码"
                />
              </n-form-item>
              <n-form-item v-if="context.kind !== 'CONTENT'" label="每页数量">
                <n-input-number
                  :disabled="busy"
                  v-model:value="context.pageSize"
                  :min="0"
                  :max="100000"
                  aria-label="每页数量"
                />
              </n-form-item>
            </n-form>
          </div>
        </n-popover>
      </div>
      <div v-if="selectedRoot" class="workspace-actions">
        <div class="action-group">
          <CmsTemplateActionButton
            label="弹窗预览"
            tooltip="保存当前文件并在弹窗中真实渲染"
            :icon="EyeIcon"
            :disabled="busy || !context.siteId"
            @click="openModalPreview"
          />
          <CmsTemplateActionButton
            label="新窗口预览"
            tooltip="保存当前文件并在独立窗口中预览"
            :icon="OpenIcon"
            :disabled="busy || !context.siteId"
            @click="openWindowPreview"
          />
          <CmsTemplateActionButton
            label="导入整包"
            tooltip="导入模板、JS、CSS 和资源文件"
            :icon="ImportIcon"
            :disabled="!can('EDIT') || busy"
            @click="importInput?.click()"
          />
          <CmsTemplateActionButton
            label="导出整包"
            tooltip="导出模板目录及关联静态资源"
            :icon="ExportIcon"
            :disabled="!can('EXPORT') || busy"
            @click="run(exportPackage)"
          />
        </div>
        <span class="toolbar-divider" />
        <CmsTemplateActionButton
          label="保存文件"
          tooltip="保存真实文件 · Ctrl / ⌘ + S"
          :icon="SaveIcon"
          :loading="busy"
          :disabled="!dirty || !can('EDIT') || !selectedRoot.writable"
          @click="run(save)"
        />
        <CmsTemplateActionButton
          label="应用站点"
          tooltip="设置站点使用的模板和皮肤"
          :icon="PublishIcon"
          type="primary"
          :disabled="busy || !can('PUBLISH') || !context.siteId"
          @click="run(prepareSiteBinding)"
          >应用站点</CmsTemplateActionButton
        >
      </div>
    </header>

    <div v-if="!selectedRoot" class="panel workspace-empty">
      <div class="empty-mark">
        <n-icon :size="36"><FolderIcon /></n-icon>
      </div>
      <h2>创建你的模板</h2>
      <p class="muted">管理 templates 与 static 的实际目录、文件及网站资源。</p>
      <CmsTemplateActionButton
        label="新建模板"
        :icon="PlusIcon"
        type="primary"
        :disabled="busy || !can('CREATE')"
        @click="run(openNewTemplate)"
        >新建模板</CmsTemplateActionButton
      >
    </div>
    <div v-if="selectedRoot" class="workspace-editor">
      <div class="workspace-grid">
        <aside class="panel file-panel">
          <div class="panel-title">
            <span class="panel-heading"
              ><n-icon :size="17"><FolderIcon /></n-icon>目录与文件</span
            >
            <div class="action-group">
              <CmsTemplateActionButton
                label="刷新文件树"
                size="small"
                :icon="RefreshIcon"
                :disabled="busy"
                @click="refreshWorkspace"
              />
              <CmsTemplateActionButton
                label="新增文件"
                size="small"
                :icon="FilePlusIcon"
                :disabled="busy || !can('EDIT') || !selectedRoot.writable"
                @click="openNewFile"
              />
              <CmsTemplateActionButton
                label="上传资源"
                size="small"
                :icon="UploadIcon"
                :disabled="busy || !can('EDIT') || !selectedRoot.writable"
                @click="assetInput?.click()"
              />
            </div>
          </div>
          <div class="file-tree-scroll">
            <n-tree
              :data="fileTree"
              :selected-keys="selectedNodePath ? [selectedNodePath] : []"
              :render-prefix="renderFileIcon"
              :render-suffix="renderFileStatus"
              :node-props="nodeProps"
              v-model:expanded-keys="expandedFileKeys"
              block-line
              @update:selected-keys="selectTreeNode"
            />
          </div>
          <div class="panel-footer file-footer">
            <span class="muted">{{ effectiveFiles.length }} 个文件</span>
            <CmsTemplateActionButton
              label="删除选中文件或目录"
              size="small"
              :icon="DeleteIcon"
              type="error"
              :disabled="
                busy ||
                !selectedNodePath.includes('/') ||
                !can('DELETE') ||
                !selectedRoot.writable
              "
              @click="deleteFile"
            />
          </div>
        </aside>
        <main class="source-panel panel">
          <div class="panel-title source-title">
            <div class="source-file" :title="selectedPath">
              <n-icon :size="18" :color="selectedPresentation.color"
                ><component :is="selectedPresentation.icon"
              /></n-icon>
              <div class="file-identity">
                <span class="file-name">{{ selectedName || "请选择文件" }}</span
                ><span class="file-path muted">{{
                  selectedDirectory || "源码编辑"
                }}</span>
              </div>
              <span
                v-if="changes[selectedPath]"
                class="change-dot"
                title="此文件有未保存修改"
              />
            </div>
            <div class="action-group">
              <CmsTemplateActionButton
                label="弹窗预览"
                :icon="EyeIcon"
                size="small"
                :disabled="busy || !context.siteId"
                @click="openModalPreview"
              />
            </div>
          </div>
          <CmsTemplateSourceEditor
            v-if="selectedFile?.editable"
            ref="editor"
            :model-value="source"
            :path="selectedPath"
            :disabled="busy || !can('EDIT') || !selectedRoot.writable"
            @update:model-value="setSource"
            @save="run(save)"
          />
          <n-empty
            v-else
            :description="
              selectedPath
                ? '资源文件随模板保存，可通过整包导出查看'
                : '选择文件开始编辑'
            "
            class="source-empty"
          />
          <div class="panel-footer source-footer">
            <span
              >{{ selectedPath ? selectedPresentation.label : "源码"
              }}<span v-if="selectedFile?.editable" class="caption-dot">·</span
              ><span v-if="selectedFile?.editable"
                >{{ sourceLines }} 行</span
              ></span
            ><span>Ctrl / ⌘ + S 保存文件</span>
          </div>
        </main>
        <aside class="panel tools-panel">
          <div class="panel-title">
            <span class="panel-heading"
              ><n-icon :size="17"><TuneIcon /></n-icon>编辑辅助</span
            ><span class="muted">{{
              editingTag ? "编辑现有标签" : "源码"
            }}</span>
          </div>
          <n-tabs type="line" animated class="tools-tabs">
            <n-tab-pane name="tags">
              <template #tab
                ><span class="tab-label"
                  ><n-icon :size="16"><TagIcon /></n-icon>标签参数</span
                ></template
              >
              <div class="tool-content">
                <n-select
                  v-model:value="tagName"
                  :options="tagOptions"
                  filterable
                  :disabled="!!editingTag"
                  placeholder="选择业务标签"
                  @update:value="resetTag"
                />
                <div class="tag-actions">
                  <CmsTemplateActionButton
                    label="读取光标标签"
                    :icon="CursorIcon"
                    size="small"
                    :disabled="!selectedFile?.editable"
                    @click="readTag"
                    >读取光标标签</CmsTemplateActionButton
                  >
                  <CmsTemplateActionButton
                    label="插入新标签"
                    :icon="PlusIcon"
                    size="small"
                    @click="editingTag = null"
                  />
                  <n-popover trigger="hover" style="max-width: 280px"
                    ><template #trigger
                      ><span
                        class="help-icon"
                        tabindex="0"
                        aria-label="参数填写说明"
                        ><n-icon :size="17"
                          ><HelpIcon /></n-icon></span></template
                    >参数使用 FreeMarker 表达式，如
                    "栏目ID"、category.id、10、true。字符串需加引号。</n-popover
                  >
                </div>
                <n-form
                  v-if="tag"
                  label-placement="top"
                  size="small"
                  class="tool-form"
                >
                  <n-form-item label="结果变量"
                    ><n-input v-model:value="alias"
                  /></n-form-item>
                  <n-form-item
                    v-for="parameter in tag.parameters"
                    :key="parameter.name"
                    :required="tag.required.includes(parameter.name)"
                  >
                    <template #label
                      >{{ parameter.name
                      }}<span class="parameter-type">{{
                        parameter.type
                      }}</span></template
                    >
                    <n-input
                      v-model:value="tagValues[parameter.name]"
                      :placeholder="parameter.purpose || '留空使用默认值'"
                    />
                  </n-form-item>
                </n-form>
                <n-empty
                  v-else
                  description="选择标签后配置参数"
                  class="tool-empty"
                  ><template #icon
                    ><n-icon><TagIcon /></n-icon></template
                ></n-empty>
                <n-button
                  block
                  type="primary"
                  :disabled="
                    busy ||
                    !tag ||
                    !selectedPath.startsWith('templates/') ||
                    !can('EDIT') ||
                    !selectedRoot.writable
                  "
                  @click="applyTag"
                  ><template #icon
                    ><n-icon><CodeIcon /></n-icon></template
                  >{{ editingTag ? "更新参数" : "插入标签" }}</n-button
                >
              </div>
            </n-tab-pane>
            <n-tab-pane name="style">
              <template #tab
                ><span class="tab-label"
                  ><n-icon :size="16"><PaletteIcon /></n-icon>可视化样式</span
                ></template
              >
              <div class="tool-content">
                <n-form label-placement="top" size="small" class="tool-form">
                  <n-form-item label="CSS 选择器"
                    ><n-input
                      v-model:value="styleForm.selector"
                      placeholder="body、.content、#header"
                  /></n-form-item>
                  <div class="form-section-title">文字与颜色</div>
                  <n-form-item label="字体大小"
                    ><n-input-number
                      v-model:value="styleForm.fontSize"
                      :min="8"
                      :max="120"
                      ><template #suffix>px</template></n-input-number
                    ></n-form-item
                  >
                  <div class="style-pair">
                    <n-form-item label="文字颜色"
                      ><n-color-picker
                        v-model:value="styleForm.color"
                        :modes="['hex']"
                    /></n-form-item>
                    <n-form-item label="背景颜色"
                      ><n-color-picker
                        v-model:value="styleForm.background"
                        :modes="['hex']"
                    /></n-form-item>
                  </div>
                  <div class="form-section-title">布局与间距</div>
                  <n-form-item label="内边距"
                    ><n-input-number
                      v-model:value="styleForm.padding"
                      :min="0"
                      :max="200"
                      ><template #suffix>px</template></n-input-number
                    ></n-form-item
                  >
                  <n-form-item label="最大宽度"
                    ><n-input-number
                      v-model:value="styleForm.maxWidth"
                      :min="200"
                      :max="3000"
                      clearable
                      ><template #suffix>px</template></n-input-number
                    ></n-form-item
                  >
                </n-form>
                <n-button
                  block
                  type="primary"
                  secondary
                  :disabled="
                    busy ||
                    !context.siteId ||
                    !can('EDIT') ||
                    !selectedRoot.writable
                  "
                  @click="run(applyStyle)"
                  ><template #icon
                    ><n-icon><CheckIcon /></n-icon></template
                  >应用并预览</n-button
                >
                <p class="tool-note muted">
                  {{
                    context.siteId
                      ? "样式保存到 editor.css，可在文件树中继续编辑。"
                      : "先在上方选择预览站点，再应用样式。"
                  }}
                </p>
              </div>
            </n-tab-pane>
          </n-tabs>
        </aside>
      </div>
      <footer class="workspace-status" aria-label="模板状态">
        <span class="status-item status-note"
          ><n-icon :size="12"><ShieldIcon /></n-icon
          ><span>{{
            selectedRoot.writable
              ? selectedRoot.path + " · 保存直接更新文件"
              : "内置资源只读，可右键复制到可写目录"
          }}</span></span
        >
        <span class="status-item" role="status"
          >{{ effectiveFiles.length }} 个文件<span class="caption-dot">·</span
          ><span :class="{ 'status-unsaved': dirty }">{{
            dirty ? "有未保存修改" : "文件已保存"
          }}</span></span
        >
      </footer>
    </div>
    <n-modal v-model:show="previewShow">
      <CmsTemplatePreview
        class="preview-dialog"
        :template-name="context.templateCode"
        :html="previewHtml"
        :entry="previewEntry"
        :error="previewError"
        :busy="busy"
        v-model:width="previewWidth"
        :rendered-at="previewRenderedAt"
        :stale="previewStale"
        closable
        allow-new-window
        @refresh="run(renderPreview)"
        @open-window="openWindowPreview"
        @close="previewShow = false"
      />
    </n-modal>
    <input
      ref="importInput"
      type="file"
      accept=".zip"
      hidden
      @change="importSelected"
    />
    <input
      ref="assetInput"
      type="file"
      multiple
      hidden
      @change="assetsSelected"
    />
    <n-modal
      v-model:show="newTemplateShow"
      preset="dialog"
      title="新建模板目录"
      positive-text="创建实际目录"
      style="width: 560px"
      @positive-click="createTemplate"
    >
      <n-form label-placement="left" :label-width="80" :show-feedback="false">
        <n-form-item label="目录名称"
          ><n-input
            v-model:value="newName"
            placeholder="例如 museum，只允许字母、数字、下划线和连字符"
        /></n-form-item>
        <n-form-item label="模板根目录"
          ><n-select v-model:value="newRootId" :options="templateRoots"
        /></n-form-item>
        <n-form-item label="静态根目录"
          ><n-select v-model:value="newStaticRootId" :options="staticRoots"
        /></n-form-item>
        <n-form-item label="复制模板"
          ><n-select
            v-model:value="newTemplateCode"
            :options="templateSources.templates"
            clearable
            placeholder="留空创建空模板"
        /></n-form-item>
        <n-form-item label="复制皮肤"
          ><n-select
            v-model:value="newThemeCode"
            :options="templateSources.themes"
            clearable
            placeholder="留空创建空皮肤"
        /></n-form-item>
      </n-form>
      <p class="muted">
        创建 templates/目录名称 与
        static/assets/theme/目录名称，保留原有目录和文件。
      </p>
    </n-modal>
    <n-modal
      v-model:show="fileShow"
      preset="dialog"
      :title="operationTitle"
      positive-text="确认"
      :positive-button-props="{ disabled: !newFileName.trim() }"
      @positive-click="createFile"
    >
      <n-form label-placement="left" :label-width="80" :show-feedback="false">
        <n-form-item v-if="operation === 'copy'" label="目标根目录"
          ><n-select
            :value="targetRootId"
            :options="writeRoots"
            @update:value="selectTargetRoot"
        /></n-form-item>
        <n-form-item label="新名称"
          ><n-input
            v-model:value="newFileName"
            autofocus
            placeholder="about.ftlh、custom.css 或 include/header.ftlh"
        /></n-form-item>
        <n-form-item label="创建目录"
          ><n-tree-select
            v-if="operation === 'copy'"
            v-model:value="newDirectory"
            :options="targetDirectoryTree"
            filterable
            placeholder="选择目标目录" /><n-input
            v-else
            v-model:value="newDirectory"
            placeholder="templates/default 或 static/assets/theme/default"
        /></n-form-item>
      </n-form>
      <p class="muted new-file-path">文件路径：{{ newFilePath }}</p>
    </n-modal>
    <n-modal
      v-model:show="siteBindingShow"
      preset="dialog"
      title="应用站点模板与皮肤"
      positive-text="应用"
      @positive-click="applySiteTemplates"
    >
      <p>将当前预览站点的配置更新为所选文件目录。</p>
      <n-space
        ><n-checkbox v-model:checked="bindTemplate"
          >前端模板：{{ context.templateCode }}</n-checkbox
        ><n-checkbox v-model:checked="bindTheme"
          >皮肤：{{ context.themeCode || "default" }}</n-checkbox
        ></n-space
      >
      <p class="muted">
        模板与资源已在真实目录中；此操作只更新站点选择，需要站点编辑权限。
      </p>
    </n-modal>
    <n-modal
      v-model:show="importShow"
      preset="dialog"
      title="导入完整模板包"
      positive-text="导入实际目录"
      style="width: 560px"
      @positive-click="importPackage"
    >
      <n-form label-placement="left" :label-width="80" :show-feedback="false">
        <n-form-item label="模板根目录"
          ><n-select v-model:value="importRootId" :options="templateRoots"
        /></n-form-item>
        <n-form-item label="静态根目录"
          ><n-select v-model:value="importStaticRootId" :options="staticRoots"
        /></n-form-item>
      </n-form>
      <n-checkbox v-model:checked="importOverwrite"
        >覆盖已存在的文件</n-checkbox
      >
      <p class="muted">
        按 ZIP 中的 templates、static
        路径写入；未包含的文件保留。覆盖真实文件前请确认已备份。
      </p>
    </n-modal>
    <n-dropdown
      trigger="manual"
      :show="contextMenu.show"
      :x="contextMenu.x"
      :y="contextMenu.y"
      :options="menuOptions"
      @select="handleContextAction"
      @clickoutside="contextMenu.show = false"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, onBeforeUnmount, reactive, ref } from "vue";
import { onBeforeRouteLeave, useRouter } from "vue-router";
import {
  NIcon,
  useDialog,
  useMessage,
  useThemeVars,
  type TreeOption,
} from "naive-ui";
import RefreshIcon from "~icons/mdi/refresh";
import CodeIcon from "~icons/mdi/code-tags";
import PlusIcon from "~icons/mdi/plus";
import FilePlusIcon from "~icons/mdi/file-plus-outline";
import ImportIcon from "~icons/mdi/tray-arrow-up";
import ExportIcon from "~icons/mdi/tray-arrow-down";
import SaveIcon from "~icons/mdi/content-save-outline";
import PublishIcon from "~icons/mdi/publish";
import ShieldIcon from "~icons/mdi/shield-check-outline";
import FolderIcon from "~icons/mdi/folder-outline";
import UploadIcon from "~icons/mdi/upload";
import DeleteIcon from "~icons/mdi/delete-outline";
import EyeIcon from "~icons/mdi/eye-outline";
import OpenIcon from "~icons/mdi/open-in-new";
import TuneIcon from "~icons/mdi/tune-variant";
import TagIcon from "~icons/mdi/tag-outline";
import CursorIcon from "~icons/mdi/cursor-text";
import HelpIcon from "~icons/mdi/help-circle-outline";
import PaletteIcon from "~icons/mdi/palette-outline";
import CheckIcon from "~icons/mdi/check";
import CssIcon from "~icons/mdi/language-css3";
import JsIcon from "~icons/mdi/language-javascript";
import FileIcon from "~icons/mdi/file-outline";
import ImageIcon from "~icons/mdi/image-outline";
import JsonIcon from "~icons/mdi/code-json";
import CmsTemplateActionButton from "../components/CmsTemplateActionButton.vue";
import { useAuthStore } from "@/store";
import { ajaxGetDownload } from "@/utils/download";
import { cmsPlugins } from "../api/plugins";
import {
  templateFiles as api,
  type TemplateRoot,
  type TemplateFile,
  type TemplateTag,
  type TemplateChange,
  type TemplateSources,
  type PreviewContext,
} from "../api/templateFiles";
import {
  directiveAt,
  directiveHeader,
  type TemplateDirective,
} from "../utils/templateDirectives";
import SiteSelect from "../components/form/field/SiteSelect.vue";
import CategorySelect from "../components/form/field/CategorySelect.vue";
import CmsTemplateSourceEditor from "../components/CmsTemplateSourceEditor.vue";
import CmsTemplatePreview from "../components/CmsTemplatePreview.vue";
import {
  templatePreviewQuery,
  parseTemplatePreviewQuery,
} from "../utils/templatePreviewContext";
const message = useMessage(),
  dialog = useDialog(),
  auth = useAuthStore();
const theme = useThemeVars();
const router = useRouter();
const previewShow = ref(false),
  previewRenderedAt = ref<number>(),
  previewSnapshot = ref("");
// 沿用后台的主题颜色，保证工作台在浅色、深色和不同平台主题中保持一致。
const workspaceTheme = computed(() => ({
  "--cms-primary": theme.value.primaryColor,
  "--cms-primary-soft": `color-mix(in srgb, ${theme.value.primaryColor} 9%, transparent)`,
  "--cms-warning": theme.value.warningColor,
  "--cms-text": theme.value.textColor1,
  "--cms-muted": theme.value.textColor3,
  "--cms-border": theme.value.borderColor,
  "--cms-surface": theme.value.cardColor,
  "--cms-header": theme.value.actionColor,
  "--cms-page": theme.value.bodyColor,
}));
const roots = ref<TemplateRoot[]>([]),
  selectedRoot = ref<TemplateRoot>(),
  files = ref<TemplateFile[]>([]),
  tags = ref<TemplateTag[]>([]);
const busy = ref(false),
  selectedPath = ref(""),
  selectedNodePath = ref(""),
  source = ref(""),
  changes = ref<Record<string, TemplateChange>>({});
// 展开状态独立于文件选择和加载，避免页面重渲染时重置目录。
const expandedFileKeys = ref<(string | number)[]>([]);
const digests = ref<Record<string, string>>({});
const editor = ref<InstanceType<typeof CmsTemplateSourceEditor>>();
const previewHtml = ref(""),
  previewEntry = ref(""),
  previewError = ref(""),
  previewWidth = ref(0);
const templateSources = ref<TemplateSources>({ templates: [], themes: [] });
const newTemplateCode = ref<string | null>("default"),
  newThemeCode = ref<string | null>("default"),
  bindTemplate = ref(true),
  bindTheme = ref(true);
const newRootId = ref(""),
  newStaticRootId = ref(""),
  targetRootId = ref("");
const newTemplateShow = ref(false),
  newName = ref(""),
  fileShow = ref(false),
  newDirectory = ref(""),
  newFileName = ref(""),
  siteBindingShow = ref(false);
const operation = ref<"file" | "directory" | "copy" | "rename">("file"),
  operationSource = ref("");
const importShow = ref(false),
  pendingImport = ref<File>(),
  importRootId = ref(""),
  importStaticRootId = ref(""),
  importOverwrite = ref(false);
const importInput = ref<HTMLInputElement>(),
  assetInput = ref<HTMLInputElement>();
const targetDirectories = ref<TemplateFile[]>([]);
const contextMenu = reactive({ show: false, x: 0, y: 0 });
const tagName = ref<string | null>(null),
  alias = ref("result"),
  tagValues = ref<Record<string, string>>({}),
  editingTag = ref<TemplateDirective | null>(null);
const context = reactive<PreviewContext>({
  siteId: "",
  templateCode: "default",
  themeCode: "default",
  kind: "HOME",
  module: "ARTICLE",
  pageNum: 1,
  pageSize: 20,
});
const pluginOptions = ref<{ label: string; value: string }[]>([]);
const styleForm = reactive({
  selector: "body",
  fontSize: 16,
  color: "#333333",
  background: "#ffffff",
  padding: 16,
  maxWidth: null as number | null,
});
const kindOptions = [
  { label: "首页", value: "HOME" },
  { label: "栏目列表", value: "CATEGORY" },
  { label: "内容详情", value: "CONTENT" },
  { label: "搜索页", value: "SEARCH" },
];
const kindLabel = computed(
  () => kindOptions.find((item) => item.value === context.kind)?.label,
);
const can = (operation: string) =>
  auth.hasPermission([
    "ADMIN",
    "CMS_TEMPLATE_ALL",
    `CMS_TEMPLATE_${operation}`,
  ]);
const dirty = computed(() => Object.keys(changes.value).length > 0);
const previewKey = () =>
  JSON.stringify([selectedRoot.value?.id, context, files.value]);
const previewStale = computed(
  () =>
    !!previewHtml.value &&
    (dirty.value || previewSnapshot.value !== previewKey()),
);
const selectedName = computed(() => selectedPath.value.split("/").pop());
const selectedDirectory = computed(() =>
  selectedPath.value.slice(0, selectedPath.value.lastIndexOf("/")),
);
const newFilePath = computed(
  () =>
    `${newDirectory.value.trim().replace(/\/+$/, "")}/${newFileName.value.trim()}`,
);
const sourceLines = computed(() => source.value.split("\n").length);
const operationTitle = computed(
  () =>
    ({
      file: "新增文件",
      directory: "新增目录",
      copy: "复制文件或目录",
      rename: "重命名",
    })[operation.value],
);
const writeRoots = computed(() =>
  roots.value
    .filter((item) => item.writable)
    .map((item) => ({ label: `${item.name} · ${item.path}`, value: item.id })),
);
const templateRoots = computed(() =>
  writeRoots.value.filter((item) => item.value.startsWith("templates-")),
);
const staticRoots = computed(() =>
  writeRoots.value.filter((item) => item.value.startsWith("static-")),
);
function filePresentation(path: string) {
  if (/\.css$/i.test(path))
    return { icon: CssIcon, label: "CSS", color: theme.value.infoColor };
  if (/\.m?js$/i.test(path))
    return {
      icon: JsIcon,
      label: "JavaScript",
      color: theme.value.warningColor,
    };
  if (/\.json$/i.test(path))
    return { icon: JsonIcon, label: "JSON", color: theme.value.warningColor };
  if (/\.(ftlh?|html)$/i.test(path))
    return {
      icon: CodeIcon,
      label: /\.ftlh?$/i.test(path) ? "FreeMarker" : "HTML",
      color: theme.value.primaryColor,
    };
  if (/\.(svg|png|jpe?g|gif|webp|ico)$/i.test(path))
    return {
      icon: ImageIcon,
      label: "图片资源",
      color: theme.value.successColor,
    };
  return { icon: FileIcon, label: "资源文件", color: theme.value.textColor3 };
}
const selectedPresentation = computed(() =>
  filePresentation(selectedPath.value),
);
function renderFileIcon({ option }: { option: TreeOption }) {
  const presentation = option.isLeaf
    ? filePresentation(String(option.key))
    : { icon: FolderIcon, color: theme.value.textColor3 };
  return h(
    NIcon,
    { size: 16, color: presentation.color },
    { default: () => h(presentation.icon) },
  );
}
function renderFileStatus({ option }: { option: TreeOption }) {
  return option.isLeaf && changes.value[String(option.key)]
    ? h("span", { class: "change-dot", title: "未保存修改" })
    : null;
}
const rootOptions = computed(() =>
  roots.value.map((item) => ({
    label: `${item.name} · ${item.path}`,
    value: item.id,
  })),
);
const tagOptions = computed(() =>
  tags.value.map((item) => ({ label: item.name, value: item.name })),
);
const tag = computed(() =>
  tags.value.find((item) => item.name === tagName.value),
);
const selectedFile = computed(() =>
  files.value.find(
    (item) => item.path === selectedPath.value && !item.directory,
  ),
);
const effectiveFiles = computed(() =>
  files.value.filter((item) => !item.directory),
);
const fileTree = computed<TreeOption[]>(() => {
  const root: TreeOption[] = [];
  files.value.forEach((file) => {
    let list = root;
    const parts = file.path.split("/");
    parts.forEach((part, index) => {
      const key = parts.slice(0, index + 1).join("/");
      let node = list.find((item) => item.key === key);
      if (!node) {
        node = {
          key,
          label: part,
          isLeaf: index === parts.length - 1 && !file.directory,
          selectable: true,
        };
        if (!node.isLeaf) node.children = [];
        list.push(node);
      }
      if (node.children) list = node.children;
    });
  });
  return root;
});
function nodeProps({ option }: { option: TreeOption }) {
  return {
    onContextmenu(event: MouseEvent) {
      event.preventDefault();
      contextMenu.show = false;
      selectedNodePath.value = String(option.key);
      contextMenu.x = event.clientX;
      contextMenu.y = event.clientY;
      contextMenu.show = true;
    },
  };
}
const targetDirectoryTree = computed<TreeOption[]>(() => {
  const root = roots.value.find((item) => item.id === targetRootId.value);
  if (!root) return [];
  const nodes: TreeOption[] = [
    { key: root.kind, label: root.kind, children: [] },
  ];
  targetDirectories.value.forEach((file) => {
    const parts = file.path.split("/");
    if (!file.directory) parts.pop();
    let list = nodes;
    parts.forEach((part, index) => {
      const key = parts.slice(0, index + 1).join("/");
      let node = list.find((item) => item.key === key);
      if (!node) {
        node = { key, label: part, children: [] };
        list.push(node);
      }
      list = node.children!;
    });
  });
  return nodes;
});
async function selectTargetRoot(value: string) {
  targetRootId.value = value;
  await run(async () => {
    targetDirectories.value = (await api.files(value)).data;
    const root = roots.value.find((item) => item.id === value)!;
    if (
      !newDirectory.value.startsWith(root.kind + "/") &&
      newDirectory.value !== root.kind
    )
      newDirectory.value = root.kind;
  });
}
const menuOptions = computed(() => [
  {
    key: "copy",
    label: "复制文件 / 整个目录",
    disabled: !can("EDIT") || !selectedNodePath.value.includes("/"),
  },
  {
    key: "rename",
    label: "重命名",
    disabled:
      !can("EDIT") ||
      !selectedRoot.value?.writable ||
      !selectedNodePath.value.includes("/"),
  },
  {
    key: "file",
    label: "新建文件",
    disabled: !can("EDIT") || !selectedRoot.value?.writable,
  },
  {
    key: "directory",
    label: "新建目录",
    disabled: !can("EDIT") || !selectedRoot.value?.writable,
  },
  {
    key: "delete",
    label: "删除文件 / 目录",
    disabled:
      !can("DELETE") ||
      !selectedRoot.value?.writable ||
      !selectedNodePath.value.includes("/"),
  },
]);
function handleContextAction(key: string) {
  contextMenu.show = false;
  if (key === "delete") deleteFile();
  else openPathOperation(key as typeof operation.value);
}
async function run(action: () => Promise<unknown>) {
  if (busy.value) return;
  busy.value = true;
  try {
    await action();
  } catch (error: any) {
    if (!error?.response) message.error(error?.message || "操作失败");
  } finally {
    busy.value = false;
  }
}
async function refreshRoots() {
  roots.value = (await api.list()).data.list;
  templateSources.value = (await api.sources()).data;
}
let fileRequest = 0;
function refreshWorkspace() {
  confirmDiscard(
    () =>
      void run(async () => {
        changes.value = {};
        await reloadFiles();
        await refreshRoots();
        await openFile(
          files.value.some((file) => file.path === selectedPath.value)
            ? selectedPath.value
            : "",
        );
      }),
  );
}
async function reloadFiles() {
  if (selectedRoot.value)
    files.value = (await api.files(selectedRoot.value.id)).data;
}
async function loadRoot(value: TemplateRoot) {
  selectedRoot.value = value;
  expandedFileKeys.value = [value.kind];
  changes.value = {};
  digests.value = {};
  editingTag.value = null;
  previewHtml.value = "";
  previewEntry.value = "";
  previewError.value = "";
  previewRenderedAt.value = undefined;
  previewSnapshot.value = "";
  previewShow.value = false;
  await reloadFiles();
  const first =
    effectiveFiles.value.find((file) => /\/home\.ftlh?$/.test(file.path)) ||
    effectiveFiles.value.find((file) => file.editable);
  selectedNodePath.value = "";
  await openFile(first?.path || "");
}
function confirmDiscard(action: () => void) {
  if (!dirty.value) action();
  else
    dialog.warning({
      title: "有未保存修改",
      content: "离开会丢弃编辑器中尚未保存的修改。",
      positiveText: "丢弃修改",
      negativeText: "继续编辑",
      onPositiveClick: action,
    });
}
function openRoot(id: string) {
  confirmDiscard(
    () =>
      void run(async () => {
        await loadRoot((await api.detail(id)).data);
      }),
  );
}
async function openFile(path: string) {
  editingTag.value = null;
  selectedPath.value = path;
  selectedNodePath.value = path;
  const ticket = ++fileRequest;
  inferContext(path);
  if (!selectedRoot.value || !selectedFile.value?.editable) {
    source.value = "";
    return;
  }
  if (changes.value[path]?.content !== undefined) {
    source.value = changes.value[path].content!;
    return;
  }
  const result = await api.file(selectedRoot.value.id, path);
  if (ticket === fileRequest) {
    source.value = result.data.content || "";
    digests.value[path] = result.data.digest;
  }
}
function inferContext(path: string) {
  if (path.startsWith("templates/"))
    context.templateCode = path.split("/")[1] || "default";
  if (path.startsWith("static/assets/theme/"))
    context.themeCode = path.split("/")[3] || "default";
}
function selectTreeNode(keys: (string | number)[]) {
  if (busy.value || !keys.length) return;
  const path = String(keys[0]);
  selectedNodePath.value = path;
  inferContext(path);
  if (files.value.some((file) => file.path === path && !file.directory))
    void run(() => openFile(path));
}
function openNewFile() {
  openPathOperation("file");
}
function openPathOperation(kind: typeof operation.value) {
  if (!selectedRoot.value || busy.value) return;
  operation.value = kind;
  operationSource.value = selectedNodePath.value || selectedPath.value;
  const current = files.value.find(
    (file) => file.path === operationSource.value,
  );
  newDirectory.value =
    kind === "copy" || kind === "rename" || (current && !current.directory)
      ? operationSource.value.substring(
          0,
          operationSource.value.lastIndexOf("/"),
        )
      : operationSource.value || selectedRoot.value.kind;
  const original = operationSource.value.split("/").pop() || "";
  newFileName.value =
    kind === "rename"
      ? original
      : kind === "copy"
        ? original.replace(/(\.[^.]+)?$/, "_copy$1")
        : "";
  targetRootId.value = selectedRoot.value.writable
    ? selectedRoot.value.id
    : roots.value.find(
        (item) => item.writable && item.kind === selectedRoot.value!.kind,
      )?.id || "";
  fileShow.value = true;
  if (kind === "copy") void selectTargetRoot(targetRootId.value);
}
function setSource(value: string) {
  source.value = value;
  const path = selectedPath.value;
  if (!path || !selectedRoot.value?.writable) return;
  changes.value[path] = {
    path,
    content: value,
    expectedDigest: changes.value[path]?.expectedDigest || digests.value[path],
  };
}
async function save() {
  if (!selectedRoot.value || !dirty.value) return;
  await api.save(selectedRoot.value.id, Object.values(changes.value));
  changes.value = {};
  await reloadFiles();
  await openFile(selectedPath.value);
}
async function createFile() {
  if (!selectedRoot.value) return false;
  await run(async () => {
    await save();
    const path = newFilePath.value;
    if (
      !newFileName.value.trim() ||
      path.split("/").some((part) => !part || part === "." || part === "..")
    )
      throw new Error("请输入有效的目录和名称");
    if (operation.value === "directory")
      await api.directory(selectedRoot.value!.id, path);
    else if (operation.value === "file")
      await api.save(selectedRoot.value!.id, [{ path, content: "" }]);
    else if (operation.value === "copy")
      await api.copy(selectedRoot.value!.id, {
        path: operationSource.value,
        targetPath: path,
        targetRootId: targetRootId.value,
      });
    else {
      const entry = (
        await api.file(selectedRoot.value!.id, operationSource.value)
      ).data;
      await api.rename(selectedRoot.value!.id, {
        path: operationSource.value,
        targetPath: path,
        expectedDigest: entry.digest,
      });
    }
    fileShow.value = false;
    await refreshRoots();
    const target =
      operation.value === "copy" ? targetRootId.value : selectedRoot.value!.id;
    if (target !== selectedRoot.value!.id)
      await loadRoot((await api.detail(target)).data);
    else await reloadFiles();
    selectedNodePath.value = path;
    if (files.value.some((file) => file.path === path && !file.directory))
      await openFile(path);
    else if (
      operation.value === "rename" &&
      selectedPath.value.startsWith(operationSource.value)
    )
      await openFile("");
    message.success("已写入真实文件目录");
  });
  return false;
}
function deleteFile() {
  if (!selectedRoot.value || !selectedNodePath.value.includes("/")) return;
  const path = selectedNodePath.value;
  dialog.warning({
    title: "删除文件或目录",
    content: `删除 ${path} 的真实文件${files.value.find((file) => file.path === path)?.directory ? "及所有子目录和文件" : ""}，请确认已有备份。`,
    positiveText: "删除",
    negativeText: "取消",
    onPositiveClick: () =>
      run(async () => {
        await save();
        const entry = (await api.file(selectedRoot.value!.id, path)).data;
        await api.remove(selectedRoot.value!.id, {
          path,
          expectedDigest: entry.digest,
        });
        await reloadFiles();
        if (
          selectedPath.value === path ||
          selectedPath.value.startsWith(path + "/")
        )
          await openFile("");
        selectedNodePath.value = "";
        await refreshRoots();
      }),
  });
}
async function openNewTemplate() {
  await refreshRoots();
  newRootId.value = templateRoots.value[0]?.value || "";
  newStaticRootId.value = staticRoots.value[0]?.value || "";
  newName.value = "";
  newTemplateShow.value = true;
}
async function createTemplate() {
  if (!/^[A-Za-z0-9_-]+$/.test(newName.value.trim())) {
    message.warning("请输入目录名称，只允许英文字母、数字、下划线和连字符");
    return false;
  }
  await run(async () => {
    await save();
    const result = (
      await api.create(
        newName.value.trim(),
        newTemplateCode.value,
        newThemeCode.value,
        newRootId.value,
        newStaticRootId.value,
      )
    ).data;
    await refreshRoots();
    await loadRoot((await api.detail(result.rootId)).data);
    context.templateCode = result.templateCode;
    context.themeCode = result.themeCode;
    selectedNodePath.value = result.path;
    await openFile(result.path + "/home.ftlh");
    newTemplateShow.value = false;
  });
  return false;
}
function openModalPreview() {
  if (!context.siteId || busy.value) return;
  previewShow.value = true;
  void run(renderPreview);
}
function openWindowPreview() {
  if (!selectedRoot.value || !context.siteId || busy.value) return;
  const query = templatePreviewQuery(context, previewWidth.value);
  try {
    parseTemplatePreviewQuery(query);
  } catch (error: any) {
    message.warning(error.message);
    return;
  }
  const target = window.open("about:blank", "_blank");
  if (!target) {
    message.warning("浏览器阻止了新窗口，请允许弹出窗口或使用弹窗预览");
    return;
  }
  target.opener = null;
  target.document.title = "正在准备模板预览";
  target.document.body.textContent = "正在保存文件并准备预览…";
  void run(async () => {
    try {
      await save();
      if (!target.closed)
        target.location.replace(
          new URL(
            router.resolve({ path: "/cms/template-preview", query }).href,
            window.location.href,
          ).href,
        );
    } catch (error) {
      target.close();
      throw error;
    }
  });
}
async function renderPreview() {
  if (!context.siteId) throw new Error("请选择预览站点");
  await save();
  if (!selectedRoot.value) return;
  previewError.value = "";
  const snapshot = previewKey();
  try {
    const result = (await api.preview(context)).data;
    previewHtml.value = result.html;
    previewEntry.value = result.entry;
    previewRenderedAt.value = Date.now();
    previewSnapshot.value = snapshot;
  } catch (error: any) {
    previewError.value =
      error?.response?.data?.message || error.message || "渲染失败";
    throw error;
  }
}
function selectPreviewSite(value: string) {
  context.siteId = value;
  context.categoryId = undefined;
  context.contentId = undefined;
  previewHtml.value = "";
}
async function prepareSiteBinding() {
  await save();
  await refreshRoots();
  siteBindingShow.value = true;
  bindTemplate.value = true;
  bindTheme.value = true;
}
async function applySiteTemplates() {
  if (!bindTemplate.value && !bindTheme.value) {
    message.warning("请选择应用模板或皮肤");
    return false;
  }
  await run(async () => {
    await api.apply(
      context.siteId,
      bindTemplate.value ? context.templateCode : undefined,
      bindTheme.value ? context.themeCode : undefined,
    );
    siteBindingShow.value = false;
    message.success("站点模板与皮肤已更新");
  });
  return false;
}
function packagePath() {
  if (!selectedRoot.value) throw new Error("请选择文件根目录");
  if (selectedRoot.value.kind === "templates")
    return `templates/${context.templateCode}`;
  const node = files.value.find((file) => file.path === selectedNodePath.value);
  const path =
    node && !node.directory
      ? selectedNodePath.value.substring(
          0,
          selectedNodePath.value.lastIndexOf("/"),
        )
      : selectedNodePath.value;
  if (!path.includes("/")) throw new Error("请选择要导出的静态资源目录");
  return path;
}
async function exportPackage() {
  await save();
  if (selectedRoot.value)
    await ajaxGetDownload(
      api.exportUrl(selectedRoot.value.id),
      "cms-template.zip",
      {
        path: packagePath(),
        themeCode: context.themeCode,
      },
    );
}
function importSelected(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  if (!file) return;
  pendingImport.value = file;
  importOverwrite.value = false;
  importRootId.value =
    selectedRoot.value?.kind === "templates" && selectedRoot.value.writable
      ? selectedRoot.value.id
      : templateRoots.value[0]?.value || "";
  importStaticRootId.value =
    selectedRoot.value?.kind === "static" && selectedRoot.value.writable
      ? selectedRoot.value.id
      : staticRoots.value[0]?.value || "";
  importShow.value = true;
}
async function importPackage() {
  if (!pendingImport.value) return false;
  await run(async () => {
    await save();
    await api.import(
      importRootId.value,
      importStaticRootId.value,
      pendingImport.value!,
      importOverwrite.value,
    );
    importShow.value = false;
    await reloadFiles();
    await refreshRoots();
    await openFile(
      files.value.some((file) => file.path === selectedPath.value)
        ? selectedPath.value
        : "",
    );
    message.success("模板和静态资源已导入真实目录");
  });
  return false;
}
async function assetsSelected(event: Event) {
  const input = event.target as HTMLInputElement;
  const selected = Array.from(input.files || []);
  input.value = "";
  await run(async () => {
    if (!selectedRoot.value) return;
    await save();
    const node = files.value.find(
      (file) => file.path === selectedNodePath.value,
    );
    let folder =
      node && !node.directory
        ? selectedNodePath.value.substring(
            0,
            selectedNodePath.value.lastIndexOf("/"),
          )
        : selectedNodePath.value;
    if (!folder)
      folder =
        selectedRoot.value.kind === "static"
          ? "static/assets/uploads"
          : `templates/${context.templateCode}`;
    const updates: TemplateChange[] = [];
    for (const file of selected) {
      if (file.size > 8 * 1024 * 1024) throw new Error("单文件不能超过 8MB");
      const path = `${folder}/${file.name}`;
      const existing = files.value.some((item) => item.path === path);
      if (existing)
        throw new Error(`文件已存在：${file.name}，请先改名或删除后上传`);
      const base64 = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result).split(",")[1]);
        reader.onerror = reject;
        reader.readAsDataURL(file);
      });
      updates.push({ path, base64 });
    }
    await api.save(selectedRoot.value.id, updates);
    await reloadFiles();
    message.success("资源文件已上传");
  });
}
function readTag() {
  const value = directiveAt(source.value, editor.value?.cursor() || 0);
  if (!value || !tags.value.some((item) => item.name === value.name)) {
    message.warning("将光标放在 CMS 标签的起始声明中再读取");
    return;
  }
  editingTag.value = value;
  tagName.value = value.name;
  alias.value = value.alias;
  tagValues.value = { ...value.parameters };
}
function resetTag() {
  editingTag.value = null;
  alias.value = "result";
  tagValues.value = {};
  tag.value?.required.forEach((name) => {
    tagValues.value[name] =
      name === "module"
        ? '"ARTICLE"'
        : name === "id"
          ? "content.id"
          : name === "categoryId"
            ? "category.id"
            : '""';
  });
}
function applyTag() {
  if (!tag.value) return;
  try {
    if (tag.value.required.some((name) => !tagValues.value[name]?.trim()))
      throw new Error("请填写必填参数");
    const header = directiveHeader(
      tag.value.name,
      alias.value,
      tagValues.value,
    );
    if (editingTag.value) {
      const current = directiveAt(source.value, editingTag.value.start + 2);
      if (!current || current.name !== editingTag.value.name)
        throw new Error("标签位置已变更，请重新读取");
      editor.value?.replace(current.start, current.end, header);
      editingTag.value = {
        ...current,
        end: current.start + header.length,
        alias: alias.value,
        parameters: { ...tagValues.value },
      };
    } else
      editor.value?.insert(
        `${header}\n${tag.value.body.replace(/\bresult\b/g, alias.value)}\n</@${tag.value.name}>`,
      );
  } catch (error: any) {
    message.error(error.message);
  }
}
async function textFile(path: string) {
  if (changes.value[path]?.content !== undefined)
    return changes.value[path].content!;
  if (
    !selectedRoot.value ||
    !files.value.some((file) => file.path === path && !file.directory)
  )
    return "";
  const data = (await api.file(selectedRoot.value.id, path)).data;
  digests.value[path] = data.digest;
  return data.content || "";
}
async function applyStyle() {
  if (!selectedRoot.value?.writable) throw new Error("请选择可写目录");
  if (!styleForm.selector.trim() || /[{}<>;]/.test(styleForm.selector))
    throw new Error("请输入有效的 CSS 选择器");
  const rule = `\n${styleForm.selector} { font-size: ${styleForm.fontSize}px; color: ${styleForm.color}; background-color: ${styleForm.background}; padding: ${styleForm.padding}px;${styleForm.maxWidth ? ` max-width: ${styleForm.maxWidth}px; margin-inline: auto;` : ""} }\n`;
  // 编辑 CSS 时直接写入当前样式文件；编辑模板时写入所选皮肤并加入原生 assetsTheme 引用。
  if (selectedPath.value.endsWith(".css")) {
    setSource(source.value + rule);
    await save();
  } else {
    let header = selectedPath.value;
    let html = await textFile(header);
    if (!/<\/head>/i.test(html)) {
      header = `templates/${context.templateCode}/include/header.ftlh`;
      html = await textFile(header);
    }
    if (!/<\/head>/i.test(html))
      throw new Error("当前模板中没有 </head>，请选中 HTML 或 CSS 文件后操作");
    const root = roots.value.find(
      (item) => item.kind === "static" && item.writable,
    );
    if (!root) throw new Error("没有可写的 static 目录");
    const path = `static/assets/theme/${context.themeCode || "default"}/editor.css`;
    const entries = (await api.files(root.id)).data;
    const current = entries.some((file) => file.path === path)
      ? (await api.file(root.id, path)).data
      : undefined;
    await api.save(root.id, [
      {
        path,
        content: (current?.content || "") + rule,
        expectedDigest: current?.digest,
      },
    ]);
    if (!html.includes("${assetsTheme}/editor.css"))
      html = html.replace(
        /<\/head>/i,
        '<link rel="stylesheet" href="${assetsTheme}/editor.css">\n</head>',
      );
    changes.value[header] = {
      path: header,
      content: html,
      expectedDigest: digests.value[header],
    };
    await save();
  }
  previewShow.value = true;
  await renderPreview();
}
function beforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value) {
    event.preventDefault();
    event.returnValue = "";
  }
}
onBeforeRouteLeave(
  () =>
    !dirty.value ||
    new Promise<boolean>((resolve) => {
      dialog.warning({
        title: "有未保存修改",
        content: "是否丢弃修改并离开？",
        positiveText: "丢弃并离开",
        negativeText: "继续编辑",
        onPositiveClick: () => resolve(true),
        onNegativeClick: () => resolve(false),
        onClose: () => resolve(false),
      });
    }),
);
onMounted(() => {
  window.addEventListener("beforeunload", beforeUnload);
  void run(async () => {
    const [list, catalog, plugins, sources] = await Promise.all([
      api.list(),
      api.tags(),
      cmsPlugins(),
      api.sources(),
    ]);
    roots.value = list.data.list;
    tags.value = catalog.data;
    templateSources.value = sources.data;
    pluginOptions.value = plugins.map((plugin) => ({
      label: plugin.label,
      value: plugin.code,
    }));
    if (roots.value.length) await loadRoot(roots.value[0]);
  });
});
onBeforeUnmount(() => {
  ++fileRequest;
  window.removeEventListener("beforeunload", beforeUnload);
});
</script>
<style scoped>
.template-workspace {
  height: 100%;
  min-height: 0;
  box-sizing: border-box;
  overflow: auto;
  padding: 12px 16px 4px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  color: var(--cms-text);
  background: var(--cms-page);
  container-type: inline-size;
}
.panel {
  border: 1px solid var(--cms-border);
  border-radius: 8px;
  background: var(--cms-surface);
  overflow: hidden;
}
.workspace-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
  padding: 8px 12px;
}
.root-group,
.workspace-actions,
.action-group,
.status-item,
.panel-heading,
.tab-label {
  display: flex;
  align-items: center;
}
.root-group {
  gap: 6px;
  flex-shrink: 0;
}
.workspace-mark,
.empty-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--cms-primary);
  background: var(--cms-primary-soft);
  border-radius: 10px;
}
.workspace-mark {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
}
.root-select {
  width: 190px;
}
.workspace-actions {
  gap: 6px;
  margin-left: auto;
  flex-shrink: 0;
}
.action-group {
  gap: 2px;
}
.toolbar-divider {
  width: 1px;
  height: 22px;
  margin: 0 4px;
  background: var(--cms-border);
}
/* 由路由内容区提供高度，工具栏换行后编辑区自动占用剩余空间。 */
.workspace-editor {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  min-height: 0;
}
.workspace-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 22px;
  flex-shrink: 0;
  gap: 8px;
  padding: 0 2px;
  color: var(--cms-muted);
  font-size: 11px;
  line-height: 22px;
}
.status-item {
  gap: 4px;
  white-space: nowrap;
  flex-shrink: 0;
}
.status-note {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}
.status-note > span {
  overflow: hidden;
  text-overflow: ellipsis;
}
.status-note > .n-icon {
  flex-shrink: 0;
}
.workspace-status .caption-dot {
  margin: 0 3px;
}
.status-unsaved {
  color: var(--cms-warning);
}
.caption-dot {
  margin: 0 7px;
  color: var(--cms-muted);
}
.workspace-grid {
  display: grid;
  flex: 1;
  min-height: 0;
  grid-template-columns: 220px minmax(0, 1fr) 284px;
  grid-template-rows: minmax(0, 1fr);
  gap: 12px;
}
.panel-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 12px;
  min-height: 48px;
  gap: 8px;
  border-bottom: 1px solid var(--cms-border);
  background: var(--cms-header);
}
.panel-heading {
  gap: 7px;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}
.panel-heading > .n-icon {
  color: var(--cms-muted);
}
.panel-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
  padding: 4px 12px;
  gap: 8px;
  font-size: 11px;
  color: var(--cms-muted);
  border-top: 1px solid var(--cms-border);
  background: var(--cms-header);
}
.file-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.file-tree-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 10px 6px;
}
.file-tree-scroll :deep(.n-tree-node-content) {
  font-size: 12px;
  border-radius: 5px;
}
.file-footer {
  min-height: 40px;
}
.source-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.source-title {
  min-height: 55px;
}
.source-file {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
}
.file-identity {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 2px;
}
.file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 500;
}
.file-path {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 10px;
}
.change-dot,
.file-tree-scroll :deep(.change-dot) {
  display: inline-block;
  width: 6px;
  height: 6px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--cms-warning);
}
.source-panel .cms-source-editor {
  flex: 1;
  min-height: 0;
}
.source-footer {
  flex-shrink: 0;
}
.source-empty {
  flex: 1;
  display: flex;
  justify-content: center;
  padding: 40px 16px;
}
.tools-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.tools-tabs {
  flex: 1;
  min-height: 0;
}
.tools-tabs :deep(.n-tabs-nav) {
  padding: 0 14px;
}
.tools-tabs :deep(.n-tabs-pane-wrapper) {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.tools-tabs :deep(.n-tab-pane) {
  padding: 0;
}
.tab-label {
  gap: 6px;
  font-size: 12px;
}
.tool-content {
  padding: 14px;
}
.tag-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin: 10px 0 16px;
}
.help-icon {
  display: inline-flex;
  align-items: center;
  color: var(--cms-muted);
  cursor: help;
  margin-left: auto;
}
.tool-form :deep(.n-form-item-label) {
  font-size: 12px;
  padding-bottom: 6px;
}
.tool-form :deep(.n-form-item-feedback-wrapper) {
  min-height: 14px;
}
.tool-form :deep(.n-input-number) {
  width: 100%;
}
.parameter-type {
  margin-left: 6px;
  color: var(--cms-muted);
  font-size: 10px;
  font-weight: 400;
}
.form-section-title {
  margin: 0 0 12px;
  padding-top: 12px;
  border-top: 1px solid var(--cms-border);
  color: var(--cms-muted);
  font-size: 11px;
  font-weight: 500;
}
.style-pair {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.style-pair :deep(.n-color-picker-trigger__value) {
  font-size: 10px;
}
.tool-note {
  line-height: 1.6;
  margin-bottom: 0;
}
.tool-empty {
  padding: 30px 0;
}
.muted {
  color: var(--cms-muted);
  font-size: 12px;
}
.new-file-path {
  overflow-wrap: anywhere;
}
.preview-dialog {
  width: min(1440px, calc(100vw - 48px));
  height: calc(100dvh - 64px);
}
.preview-context {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
}
.preview-site {
  width: 160px;
}
.preview-kind {
  width: 110px;
}
.preview-condition-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 12px;
}
.preview-conditions {
  padding: 4px 0;
}
.preview-form :deep(.n-input-number) {
  width: 100%;
}
.workspace-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}
.empty-mark {
  width: 64px;
  height: 64px;
}
.workspace-empty {
  flex: 1;
  min-height: 0;
  gap: 12px;
  padding: 24px;
}
.workspace-empty h2 {
  margin: 8px 0 0;
  font-size: 18px;
  font-weight: 500;
}
.workspace-empty p {
  margin: 0 0 8px;
}
@container (max-width: 1100px) {
  .workspace-grid {
    grid-template-columns: 195px minmax(0, 1fr) 260px;
    gap: 10px;
  }
  .workspace-mark {
    display: none;
  }
  .root-select {
    width: 170px;
  }
}
@container (max-width: 850px) {
  /* 窄屏分行后保留编辑高度，由工作区滚动展示全部面板。 */
  .workspace-editor {
    flex: 1 0 auto;
  }
  .workspace-grid {
    flex: none;
    grid-template-columns: 190px minmax(0, 1fr);
    grid-template-rows: auto;
    align-items: stretch;
  }
  .file-panel,
  .source-panel {
    height: 450px;
  }
  .tools-panel {
    grid-column: 1 / -1;
    max-height: 420px;
  }
  .preview-context {
    flex-basis: 100%;
    order: 1;
  }
}
@container (max-width: 550px) {
  .workspace-header {
    padding: 8px;
    gap: 8px;
  }
  .workspace-actions {
    margin-left: 0;
    flex-wrap: wrap;
    flex-shrink: 1;
  }
  .workspace-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .file-panel {
    height: 240px;
  }
  .tools-panel {
    grid-column: auto;
  }
  .source-footer {
    font-size: 10px;
  }
}
@media (max-width: 700px) {
  .template-workspace {
    padding: 10px 10px 4px;
  }
  .preview-dialog {
    width: calc(100vw - 20px);
    height: calc(100dvh - 32px);
  }
}
</style>
