<template>
  <HPageDataTable auto-scroll-x
    class="collection-data-page"
    ref="tableRef"
    :title="typeof route.query.title === 'string' && route.query.title.trim() ? route.query.title : (classificationLedger ? '藏品分类账' : '藏品总登记账')"
    :columns="columns"
    local-sorter
    :fetch-data="collectionApi.page"
    :params="queryParams"
    :is-add="false"
    :is-edit="false"
    :is-delete="false"
    :is-export="false"
    row-key-field="id"
    @rowView="onDetail"
    :query-configurable="true"
    :query-config-options="queryConfigOptions"
    :always-visible-query-keys="routeVisibleQueryKeys"
    :column-config-key="COLUMN_FIELD_STORAGE_KEY"
    :column-config-options="columnFieldOptions"
    :default-visible-column-keys="defaultColumnKeys"
    :transform-visible-columns="transformVisibleLedgerColumns"
  >
    <CollectionNatureReviewDrawer ref="natureReviewRef" @view="viewNatureCollection" />
    <template #toolbar-extra>
      <div class="collection-toolbar-group">
        <NButton v-if="fromCollectionSearch" size="small" secondary @click="returnToCollectionSearch">返回检索结果</NButton>
        <NTag v-if="queryParams.orgId" type="info" size="small" :bordered="false">
          收藏单位：<CollectionOrgName :org-id="queryParams.orgId" />
        </NTag>
        <span v-permission="'COLLECTION_ADMIN,COLLECTION_LEDGER_CREATE'" class="collection-create-entry">
          <NButtonGroup>
            <NButton size="small" type="primary" @click="onAdd">新增藏品</NButton>
            <NDropdown
              trigger="click"
              placement="bottom-end"
              :options="collectionCreateOptions"
              @select="handleCreateEntrySelect"
            >
              <NButton size="small" type="primary" aria-label="选择新增藏品类型">
                <template #icon>
                  <NIcon><IconMdiMenuDown /></NIcon>
                </template>
              </NButton>
            </NDropdown>
          </NButtonGroup>
        </span>
        <span v-if="canUseCollectionImport" class="collection-import-entry">
          <NButtonGroup>
            <NButton size="small" type="primary" secondary @click="openDefaultCollectionImport">
              {{ canImportRegistrationWord ? '导入登记表' : '导入动态跟踪记录汇总表' }}
            </NButton>
            <NDropdown
              v-if="collectionImportOptions.length"
              trigger="click"
              placement="bottom-end"
              :options="collectionImportOptions"
              @select="handleCollectionImportSelect"
            >
              <NButton size="small" type="primary" secondary aria-label="选择其他导入方式">
                <template #icon>
                  <NIcon><IconMdiMenuDown /></NIcon>
                </template>
              </NButton>
            </NDropdown>
          </NButtonGroup>
        </span>
        <NButton v-if="classificationLedger" size="small" type="primary" secondary :loading="classificationLedgerChecking" @click="printClassificationLedger">打印分类账</NButton>
        <NButtonGroup>
          <NButton size="small" secondary :loading="tableExportLoading" @click="exportCurrentExcel">导出明细（Excel）</NButton>
          <NDropdown
            trigger="click"
            placement="bottom-end"
            :options="collectionExportOptions"
            @select="handleCollectionExportSelect"
          >
            <NButton
              size="small"
              secondary
              :disabled="tableExportLoading || exporting"
              aria-label="选择其他导出或打印方式"
            >
              <template #icon>
                <NIcon><IconMdiMenuDown /></NIcon>
              </template>
            </NButton>
          </NDropdown>
        </NButtonGroup>
      </div>
      <div class="collection-toolbar-group collection-toolbar-secondary">
        <OriginalHistoryButton>
          <template #trigger="{ open }">
            <NDropdown
              trigger="click"
              placement="bottom-end"
              :options="ledgerMoreOptions"
              @select="(key) => handleLedgerMoreSelect(key, open)"
            >
              <NButton size="small" secondary>
                更多<NIcon style="margin-left: 4px"><IconMdiMenuDown /></NIcon>
              </NButton>
            </NDropdown>
          </template>
        </OriginalHistoryButton>
        <CollectionFieldLabel label="登记规则" help="普通新增、复制及导入形成待登记资料；完成实际入馆核收后，在“生成待登记记录”中关联已有台账。旧系统已登账资料请走迁移。" />
      </div>
    </template>

    <template #queryForm>
      <NFormItem path="keyword" label="检索内容">
        <NInput v-model:value="queryParams.keyword" placeholder="名称 / 总登记号 / RFID标签" clearable />
      </NFormItem>
      <NFormItem path="totalRegNo" label="总登记号">
        <NInput v-model:value="queryParams.totalRegNo" placeholder="总登记号" clearable />
      </NFormItem>
      <NFormItem path="classNo" label="分类号">
        <NInput v-model:value="queryParams.classNo" placeholder="分类号" clearable />
      </NFormItem>
      <NFormItem path="categoryId" label="文物分类">
        <NSelect
          v-model:value="queryParams.categoryId"
          :options="categoryOptions"
          filterable
          clearable
          placeholder="分类"
        />
      </NFormItem>
      <NFormItem path="era" label="藏品年代">
        <NInput v-model:value="queryParams.era" placeholder="年代" clearable />
      </NFormItem>
      <NFormItem path="level" label="藏品级别">
        <HDictSelect v-model:value="queryParams.level" type="col_relic_level" placeholder="级别" clearable />
      </NFormItem>
      <NFormItem path="source" label="来源方式">
        <HDictSelect v-model:value="queryParams.source" type="col_source" placeholder="来源" clearable />
      </NFormItem>
      <NFormItem path="nature" label="藏品性质"><NSelect v-model:value="queryParams.nature" :options="[{label: '文物藏品', value: 'RELIC'}, {label: '其他藏品', value: 'OTHER'}, {label: '科学化石', value: 'SCIENTIFIC_FOSSIL'}]" clearable /></NFormItem>
      <NFormItem path="relicLevelScope" label="文物级别范围"><NSelect v-model:value="queryParams.relicLevelScope" :options="[{label: '未定级文物（含待登记）', value: 'UNGRADED'}, {label: '珍贵文物', value: 'PRECIOUS'}]" clearable /></NFormItem>
      <NFormItem path="registrationScope" label="登记范围">
        <NSelect v-model:value="queryParams.registrationScope" :options="registrationOptions" placeholder="全部登记范围" clearable />
      </NFormItem>
      <NFormItem path="lifeStatus" label="藏品状态">
        <HDictSelect v-model:value="queryParams.lifeStatus" type="col_life_status" placeholder="状态" clearable />
      </NFormItem>
      <NFormItem path="catalogStatus" label="编目状态">
        <HDictSelect
          v-model:value="queryParams.catalogStatus"
          type="col_catalog_status"
          placeholder="编目状态"
          clearable
        />
      </NFormItem>
      <NFormItem path="texture" label="藏品质地">
        <HDictSelect v-model:value="queryParams.texture" type="col_texture" placeholder="质地" tag clearable />
      </NFormItem>
      <NFormItem path="completeness" label="完残情况">
        <HDictSelect v-model:value="queryParams.completeness" type="col_completeness" placeholder="完残" clearable />
      </NFormItem>
      <NFormItem path="warehouseId" label="库房库位">
        <WarehouseTreeSelect v-model:value="queryParams.warehouseId" placeholder="库位" />
      </NFormItem>
      <NFormItem path="acquireDate" label="取得日期">
        <NDatePicker v-model:value="acquireDateRange" type="daterange" clearable @update:value="syncAcquireDateRange" />
      </NFormItem>
      <NFormItem path="registeredDate" label="正式登记日期">
        <NDatePicker v-model:value="registeredDateRange" type="daterange" clearable @update:value="syncRegisteredDateRange" />
      </NFormItem>
      <NFormItem path="createTime" label="创建时间">
        <NDatePicker v-model:value="createTimeRange" type="daterange" clearable @update:value="syncCreateTimeRange" />
      </NFormItem>
      <NFormItem path="hasImage" label="藏品影像">
        <HDictSelect v-model:value="queryParams.hasImage" type="col_presence" placeholder="是否有图像" clearable />
      </NFormItem>
      <NFormItem path="hasAttachment" label="档案附件">
        <HDictSelect v-model:value="queryParams.hasAttachment" type="col_presence" placeholder="是否有附件" clearable />
      </NFormItem>
    </template>

    <!-- 登记 / 编辑表单 -->
    <RegisteredMigrationModal ref="migrationRef" @saved="handleRefresh" />
    <MigrationBatchVerificationModal ref="migrationBatchRef" @changed="handleRefresh" @view="viewNatureCollection" />
    <CollectionFormModal ref="formRef" @saved="handleRefresh" />
    <CollectionSetEntryModal ref="setEntryRef" @saved="handleRefresh" />
    <CollectionWordImportModal ref="wordImportRef" @committed="handleWordImportCommitted" />

    <!-- 完整档案：引用现有资料，不复制文件 -->
    <HPageForm v-model:show="detailVisible" :title="current?.name ? `藏品档案 · ${current.name}` : '藏品档案'" content-only :enable-cancel="false">
      <template #header>
        <div class="dossier-preview-header">
          <span>{{ current?.name ? `藏品档案 · ${current.name}` : '藏品档案' }}</span>
          <NButton v-if="fromCollectionSearch" size="small" secondary @click="returnToCollectionSearch">返回检索结果</NButton>
        </div>
      </template>
      <NAlert v-if="detailAuxErrors.length" type="warning" title="部分档案资料加载失败" style="margin-bottom: 12px">
        <NSpace justify="space-between" align="center">
          <span>{{ detailAuxErrors.join('、') }}暂不可用，基本信息仍可正常查看。</span>
          <NButton size="small" :loading="detailAuxLoading" @click="retryDetailAuxData">重试</NButton>
        </NSpace>
      </NAlert>
      <div v-if="detailVisible && current" ref="detailRoot" class="collection-dossier" :style="detailTheme">
        <header class="detail-header">
          <div class="detail-identity">
            <div class="detail-no"><span>总登记号</span><strong>{{ current.totalRegNo || '未填写' }}</strong></div>
            <h2>{{ current.name || '未填写名称' }}</h2>
            <div class="dossier-identity">
              <span>收藏单位：<CollectionOrgName :org-id="current.orgId" /></span>
              <span>{{ current.categoryName || '分类待补充' }} · {{ current.era || '年代待补充' }}</span>
            </div>
            <NSpace size="small" class="detail-statuses">
              <NTag size="small" :bordered="false" :type="isFormalCollection(current) ? 'success' : 'info'">{{ registrationLabel(current) }}</NTag>
              <NTag v-if="current.lifeStatus" size="small" :bordered="false"><HDictText type="col_life_status" :value="current.lifeStatus" /></NTag>
              <NTag v-if="current.catalogStatus" size="small" :bordered="false">编目 · <HDictText type="col_catalog_status" :value="current.catalogStatus" /></NTag>
              <NTag v-if="current.inventoryHold" type="warning" size="small" :bordered="false">清点异常 · 暂停调取</NTag>
            </NSpace>
          </div>
          <div class="detail-actions">
            <NButton type="primary" @click="printArchive">打印登记表</NButton>
            <NButton secondary @click="onLedger(current)">总账留痕</NButton>
            <NDropdown trigger="click" :options="detailActionOptions" @select="handleDetailAction">
              <NButton secondary>更多操作<template #icon><NIcon :component="ChevronDownOutline" /></template></NButton>
            </NDropdown>
          </div>
        </header>
        <div ref="detailTabsAnchor" class="detail-tab-content" tabindex="-1">
          <div v-if="detailWidth <= 640" class="detail-section-picker">
            <span>查阅内容</span>
            <NSelect v-model:value="detailTab" :options="detailSections" aria-label="选择档案内容" />
          </div>
          <NTabs v-model:value="detailTab" type="line" animated class="detail-tabs">
            <NTabPane name="overview" tab="档案概览">
              <div class="collection-detail-layout">
                <aside class="collection-detail-media">
                  <div class="detail-gallery-header">
                    <span>藏品影像</span>
                    <span>{{ mediaImages.length ? `${selectedMediaIndex + 1} / ${mediaImages.length}` : (detailAuxLoading ? '读取中' : detailAuxErrors.includes('影像和附件') ? '读取失败' : '0 张') }}</span>
                  </div>
                  <div class="detail-cover" :class="{ 'detail-cover-empty': !mediaImages.length && !fallbackCoverImageSrc }">
                    <template v-if="mediaImages.length > 1">
                      <NCarousel
                        v-model:current-index="selectedMediaIndex"
                        class="detail-carousel"
                        show-arrow
                        :show-dots="false"
                        :loop="false"
                        draggable
                        keyboard
                      >
                        <NCarouselItem v-for="item in mediaImages" :key="mediaKey(item)">
                          <CurrentFileImage v-if="selectedMediaKey === mediaKey(item) || (!selectedMediaKey && item === mediaImages[0])"
                            :src="fileUrl(item.fileId)" :alt="item.title || current.name || '藏品影像'"
                            :width="960" :height="540" padding :revision="mediaReadEpoch" preview-disabled group-preview @activate="openMediaPreview" />
                        </NCarouselItem>
                      </NCarousel>
                    </template>
                    <CurrentFileImage v-else-if="mediaImages.length" :src="fileUrl(mediaImages[0].fileId)"
                      :alt="mediaImages[0].title || current.name || '藏品影像'" :width="960" :height="540" padding :revision="mediaReadEpoch" preview-disabled group-preview @activate="openMediaPreview" />
                    <CurrentFileImage v-else-if="fallbackCoverImageSrc" :src="fallbackCoverImageSrc"
                      :alt="current.name || '藏品影像'" :width="960" :height="540" padding :revision="mediaReadEpoch" />
                    <NEmpty v-else :description="detailAuxLoading ? '正在读取影像' : detailAuxErrors.includes('影像和附件') ? '影像暂不可用' : '暂无可查看的影像'" size="small" />
                  </div>
                  <div v-if="mediaImages.length > 1" class="detail-thumb-list" aria-label="藏品影像缩略图">
                    <button
                      v-for="(item, index) in mediaImages"
                      :key="mediaKey(item)"
                      type="button"
                      class="detail-thumb"
                      :class="{ active: selectedMediaKey === mediaKey(item) }"
                      :aria-label="`查看第 ${index + 1} 张影像${item.title ? `：${item.title}` : ''}`"
                      :aria-pressed="selectedMediaKey === mediaKey(item)"
                      @click="selectMedia(item)"
                    >
                      <CurrentFileImage :src="fileUrl(item.fileId)" :alt="item.title || current.name || '藏品影像'" :width="120" :revision="mediaReadEpoch" preview-disabled hide-reload />
                    </button>
                  </div>
                  <CurrentImagePreviewGroup v-model:show="mediaPreviewShow" v-model:current="selectedMediaIndex" :items="mediaPreviewItems" :revision="current.id" @denied="mediaReadEpoch++" />
                  <p v-if="mediaImages.length || fallbackCoverImageSrc" class="detail-image-caption">点击影像查看大图</p>
                  <nav class="detail-metrics" aria-label="藏品资料快捷入口">
                    <button v-for="item in detailResources" :key="item.label" type="button" @click="goDetailTab(item.tab)">
                      <strong>{{ detailResourceCount(item.count, item.error) }}</strong>
                      <span>{{ item.label }}<NIcon :component="ChevronForwardOutline" /></span>
                    </button>
                  </nav>
                </aside>
                <section class="collection-detail-main" aria-label="藏品概况">
                  <div class="detail-facts">
                    <div><span>藏品级别</span><strong><HDictText type="col_relic_level" :value="current.level" /></strong></div>
                    <div><span>质地</span><strong><span v-if="current.texture">{{ current.texture }}</span><span v-else>未记录</span></strong></div>
                    <div><span>登记件套数</span><strong>{{ current.quantity ?? '未记录' }}<small>{{ current.unit || '' }}</small></strong></div>
                    <div><span>实际数量</span><strong>{{ current.actualQuantity ?? '待核实' }}<small>{{ current.actualQuantityUnit || '' }}</small></strong></div>
                  </div>
                  <section class="detail-summary-section">
                    <div class="detail-section-heading"><h3>登记与保管</h3><NButton text type="primary" size="small" @click="goDetailTab('base')">查看全部信息<NIcon :component="ChevronForwardOutline" /></NButton></div>
                    <dl class="detail-summary-fields">
                      <div><dt><CollectionFieldLabel label="当前库位" :help="WAREHOUSE_FIXED_AT_RECEIPT_HINT" /></dt><dd>{{ current.warehouseName || '未记录' }}</dd></div>
                      <div><dt>来源方式</dt><dd><HDictText type="col_source" :value="current.source" /></dd></div>
                      <div><dt>取得日期</dt><dd>{{ current.acquireDate ? formatLocalDateValue(current.acquireDate) : current.acquireDateRange || '未记录' }}</dd></div>
                      <div><dt>实收入馆日期</dt><dd>{{ formatLocalDateValue(current.entryDate) }}</dd></div>
                      <div><dt>{{ current.historicalConfirmedAt != null ? '原登记日期' : '正式登记时间' }}</dt><dd>{{ current.registeredAt ? formatDateTime(current.registeredAt) : registrationDateFallback(current) }}</dd></div>
                      <div><dt>保存状态</dt><dd><HDictText type="col_preserve" :value="current.preserveState" /></dd></div>
                    </dl>
                  </section>
                  <section class="detail-summary-section">
                    <div class="detail-section-heading"><h3>形制与特征</h3><NButton text type="primary" size="small" @click="goDetailTab('description')">查阅完整描述<NIcon :component="ChevronForwardOutline" /></NButton></div>
                    <p class="detail-feature">{{ detailFeature }}</p>
                    <dl class="detail-summary-fields">
                      <div><dt>完残程度</dt><dd><HDictText type="col_completeness" :value="current.completeness" /></dd></div>
                      <div><dt>质量</dt><dd>{{ current.weight ? `${current.weight} ${current.weightUnit || '（单位未记录）'}` : current.registrationDetails?.weightUnknownReason || '未记录' }}</dd></div>
                      <div class="detail-field-wide"><dt>尺寸</dt><dd>{{ dimensionText(current.dimensions) }}<span v-if="current.dimensionUnit">（单位：{{ current.dimensionUnit }}）</span></dd></div>
                    </dl>
                  </section>
                  <section class="detail-reference-section">
                    <div><h3>档案依据</h3><p>{{ migrationSnapshot ? `迁移来源：${migrationSnapshot.sourceSystem}` : '登记依据、原始材料与编目版本' }}</p></div>
                    <NSpace size="small">
                      <OriginalHistoryButton business-type="COLLECTION_MAIN_FILES" :business-id="current.id" label="查阅历史材料" />
                      <NButton secondary size="small" @click="goDetailTab('catalog')">编目与版本</NButton>
                    </NSpace>
                  </section>
                </section>
              </div>
              <p class="detail-footnote">本页汇集藏品资料与业务记录；实际建档、行政备案情况以对应业务及签署原件为准。</p>
            </NTabPane>
            <NTabPane name="catalog" tab="编目与版本">
              <CatalogDetail v-if="detailCatalog" :catalog="detailCatalog" />
              <NEmpty v-else :description="detailAuxLoading ? '正在读取编目资料' : '编目资料暂不可用，请查看上方加载提示并重试'" />
            </NTabPane>
            <NTabPane name="base" tab="基本信息">
              <h3 class="detail-section-title">登记标识</h3>
              <NDescriptions label-placement="left" :column="detailColumns" bordered size="small">
                <NDescriptionsItem label="总登记号">{{ current.totalRegNo || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="分类号">{{ current.classNo || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="登记状态">{{ registrationLabel(current) }}</NDescriptionsItem>
                <NDescriptionsItem v-if="current.historicalConfirmedAt != null" label="登记事实">已登账·历史迁入</NDescriptionsItem>
                <NDescriptionsItem v-if="current.historicalConfirmedAt != null" label="历史登记复核时间">{{ formatDateTime(current.historicalConfirmedAt) }}</NDescriptionsItem>
                <NDescriptionsItem :label="current.historicalConfirmedAt != null ? '原登记日期' : '登记时间'">{{ current.registeredAt ? formatDateTime(current.registeredAt) : registrationDateFallback(current) }}</NDescriptionsItem>
                <NDescriptionsItem label="入馆凭证号">{{ current.entryVoucherNo || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="退出凭证号">{{ current.deregVoucherNo || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="辅助编号">{{ current.auxNo || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="原名">{{ current.originalName || '--' }}</NDescriptionsItem>
              </NDescriptions>
              <h3 class="detail-section-title">分类与著录</h3>
              <NDescriptions label-placement="left" :column="detailColumns" bordered size="small">
                <NDescriptionsItem label="藏品性质">{{ collectionNatureLabel(current.nature) }}</NDescriptionsItem>
                <NDescriptionsItem label="文物分类">{{ current.categoryName || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="年代类型">{{ eraMeaningLabel(current.registrationDetails?.eraMeaning) }}</NDescriptionsItem>
                <NDescriptionsItem label="年代表示方式">{{ eraNotationLabel(current.registrationDetails?.eraNotation) }}</NDescriptionsItem>
                <NDescriptionsItem label="具体年代">{{ current.era || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="质地类别">{{ current.textureCategory1 || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="质地细类">{{ current.textureCategory2 || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="质地"
                  >{{ current.texture || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="级别"
                  ><HDictText type="col_relic_level" :value="current.level"
                /></NDescriptionsItem>
                <NDescriptionsItem label="数量"
                  >{{ current.quantity || '--' }} {{ current.unit || '' }}</NDescriptionsItem
                >
                <NDescriptionsItem label="实际数量">{{ current.actualQuantity ?? '待核实' }} {{ current.actualQuantityUnit || '' }}</NDescriptionsItem>
              </NDescriptions>
              <h3 class="detail-section-title">来源与保管</h3>
              <NDescriptions label-placement="left" :column="detailColumns" bordered size="small">
                <NDescriptionsItem label="来源"
                  ><HDictText type="col_source" :value="current.source"
                /></NDescriptionsItem>
                <NDescriptionsItem label="取得时间范围">{{ current.acquireDateRange || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="取得年度">{{ current.acquireYear || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="取得日期">{{ formatLocalDateValue(current.acquireDate) }}</NDescriptionsItem>
                <NDescriptionsItem label="实收入馆日期">{{ formatLocalDateValue(current.entryDate) }}</NDescriptionsItem>
                <NDescriptionsItem label="核收完成时间">{{ current.receiptCompletedAt ? formatDateTime(current.receiptCompletedAt) : '未记录' }}</NDescriptionsItem>
                <NDescriptionsItem label="完残程度"
                  ><HDictText type="col_completeness" :value="current.completeness"
                /></NDescriptionsItem>
                <NDescriptionsItem label="保存状态"
                  ><HDictText type="col_preserve" :value="current.preserveState"
                /></NDescriptionsItem>
                <NDescriptionsItem><template #label><CollectionFieldLabel label="当前库位" :help="WAREHOUSE_FIXED_AT_RECEIPT_HINT" /></template>{{ current.warehouseName || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="业务状态"
                  ><HDictText type="col_life_status" :value="current.lifeStatus"
                /></NDescriptionsItem>
                <NDescriptionsItem v-if="current.inventoryHold" label="清点异常"
                  ><NTag type="warning">待核实处理，暂停调取利用</NTag></NDescriptionsItem
                >
                <NDescriptionsItem label="CMS供稿历史">
                  <NTag size="small" :bordered="false" :type="publishStatus(current).type as any">{{
                    publishStatus(current).text
                  }}</NTag>
                  <span v-if="current.publishedAt" style="margin-left: 8px">{{
                    formatDateTime(current.publishedAt)
                  }}</span>
                </NDescriptionsItem>
              </NDescriptions>
            </NTabPane>

            <NTabPane v-if="migrationSnapshot" name="migration" tab="旧系统迁移">
              <MigrationVerificationPanel v-if="current?.id" :collection-id="current.id" :active="detailVisible" @changed="refreshMigrationDetail" />
              <NDescriptions bordered :column="1" label-placement="left" size="small">
                <NDescriptionsItem label="迁移批次">{{ migrationSnapshot.batchId }}</NDescriptionsItem>
                <NDescriptionsItem label="来源系统">{{ migrationSnapshot.sourceSystem }}</NDescriptionsItem>
                <NDescriptionsItem label="原记录编号">{{ migrationSnapshot.sourceRecordId }}</NDescriptionsItem>
                <NDescriptionsItem label="原登记馆代码">{{ migrationSnapshot.originalOrgCode }}</NDescriptionsItem>
                <NDescriptionsItem label="原登记日期">{{ migrationSnapshot.originalRegisteredAt || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="原登记人">{{ migrationSnapshot.originalRegisteredBy || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="原账与登记依据"><HFileList v-if="migrationSnapshot.registrationEvidence" :value="migrationSnapshot.registrationEvidence" show-download-btn /><span v-else>未关联登记依据</span></NDescriptionsItem>
                <NDescriptionsItem label="迁移核验结果">{{ migrationSnapshot.verification === 'CONFIRMED' ? '已核实登账' : '登记依据待核实' }}</NDescriptionsItem>
                <NDescriptionsItem label="历史增减已核对">{{ migrationSnapshot.historyVerified ? '是' : '否，年度统计须核对历史缺口' }}</NDescriptionsItem>
                <NDescriptionsItem label="迁移时间">{{ migrationSnapshot.migratedAt }}</NDescriptionsItem>
                <NDescriptionsItem label="迁移操作人">{{ migrationSnapshot.migratedBy }}</NDescriptionsItem>
                <NDescriptionsItem label="核验说明" content-style="white-space: pre-wrap; overflow-wrap: anywhere;"><span class="detail-note-text">{{ migrationSnapshot.reason }}</span></NDescriptionsItem>
              </NDescriptions>
            </NTabPane>
            <NTabPane name="description" tab="档案描述">
              <NDescriptions label-placement="left" :column="detailColumns" bordered size="small">
                <NDescriptionsItem label="尺寸" :span="detailColumns">{{ dimensionText(current.dimensions) }}<span v-if="current.dimensionUnit">（单位：{{ current.dimensionUnit }}）</span></NDescriptionsItem>
                <NDescriptionsItem label="质量范围">{{ current.weightRange || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="具体质量">{{ current.weight || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="质量单位">{{ current.weightUnit || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="估价">{{ valuationText(current.valuation) }}</NDescriptionsItem>
                <NDescriptionsItem label="来源说明" :span="detailColumns" content-style="white-space: pre-wrap; overflow-wrap: anywhere;">{{ current.sourceDesc || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="完残状况" :span="detailColumns" content-style="white-space: pre-wrap; overflow-wrap: anywhere;">{{ current.completeStatus || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="铭文款识" :span="detailColumns" content-style="white-space: pre-wrap; overflow-wrap: anywhere;">{{ current.inscription || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="特征描述" :span="detailColumns" content-style="white-space: pre-wrap; overflow-wrap: anywhere;">{{ current.feature || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="著者">{{ current.author || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="版本">{{ current.edition || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="存卷" :span="detailColumns">{{ current.volumeNo || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="备注" :span="detailColumns" content-style="white-space: pre-wrap; overflow-wrap: anywhere;"><span class="detail-note-text">{{ current.remark || '--' }}</span></NDescriptionsItem>
              </NDescriptions>
            </NTabPane>

            <NTabPane name="media" tab="影像与附件">
              <div class="detail-section-toolbar">
                <span>影像资料</span>
                <NSpace size="small"
                  ><OriginalHistoryButton
                    business-type="COLLECTION_MAIN_FILES"
                    :business-id="current.id"
                    label="本藏品历史材料"
                  />
                  <NButton size="small" secondary @click="openMediaFolder">管理目录</NButton
                  ><NButton size="small" type="primary" secondary @click="openMedia">添加影像</NButton></NSpace
                >
              </div>
              <NDataTable :scroll-x="1154" :columns="mediaColumns" :data="mediaList" size="small" :bordered="false" />
              <div class="detail-section-toolbar">
                <span>附件材料</span>
                <NButton size="small" type="primary" secondary @click="openAttachment">添加附件</NButton>
              </div>
              <NDataTable :scroll-x="1070"
                :columns="attachmentColumns"
                :data="attachmentList"
                :loading="detailAuxLoading"
                size="small"
                :bordered="false"
              />
            </NTabPane>

            <NTabPane name="set" tab="套件分件">
              <div class="detail-section-toolbar">
                <span>套件/分件关系</span>
                <NSpace
                  ><NButton size="small" :disabled="detailAuxLoading || !current?.id" @click="requestSetAmendment"
                    >申请套件订正</NButton
                  >
                  <NButton
                    size="small"
                    type="primary"
                    secondary
                    v-if="!isRegistered(current) && current?.catalogStatus !== 'APPROVED'"
                    @click="() => openSet()"
                    >添加待登记关系</NButton
                  ></NSpace
                >
              </div>
              <NDataTable :scroll-x="810"
                :columns="setColumns"
                :data="setList"
                :loading="detailAuxLoading"
                size="small"
                :bordered="false"
              />
            </NTabPane>

            <NTabPane name="movement" tab="出入库">
              <NDataTable :scroll-x="960"
                :columns="movementColumns"
                :data="movementList"
                :loading="detailAuxLoading"
                size="small"
                :bordered="false"
              />
            </NTabPane>
            <NTabPane name="tracking" tab="管理记录">
              <div class="detail-section-toolbar">
                <span>藏品管理记录（{{ trackingImports.length }} 份导入文件）</span>
                <NButton
                  v-permission="'COLLECTION_ADMIN,COLLECTION_STORAGE'"
                  size="small"
                  type="primary"
                  secondary
                  @click="openTrackingWordImport"
                  >导入动态跟踪记录汇总表</NButton
                >
              </div>
              <NDataTable
                :columns="trackingImportColumns"
                :data="trackingImports"
                :loading="detailAuxLoading"
                size="small"
                :bordered="false"
                :max-height="220"
                :scroll-x="790"
                virtual-scroll
              />
              <div class="detail-section-toolbar">
                <span>管理事项（{{ trackingEvents.length }} 条）</span>
              </div>
              <NDataTable
                :columns="trackingEventColumns"
                :data="trackingEvents"
                :loading="detailAuxLoading"
                size="small"
                :bordered="false"
                :max-height="360"
                :scroll-x="860"
                virtual-scroll
              />
            </NTabPane>
          </NTabs>
        </div>
      </div>
    </HPageForm>

    <HPageForm v-model:show="fileSourceVisible" content-only :enable-cancel="false" title="来源业务详情">
      <template v-if="selectedSourceRecord">
        <NDescriptions bordered :column="1" label-placement="left" size="small">
          <NDescriptionsItem label="来源业务">{{ selectedSourceRecord.documentType === 'REGISTRATION' ? '文物藏品登记表导入' : '动态跟踪记录汇总表导入' }}</NDescriptionsItem>
          <NDescriptionsItem label="藏品">{{ current?.totalRegNo || '--' }} · {{ current?.name || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="原文件">{{ selectedSourceRecord.sourceFileName || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="导入时间">{{ selectedSourceRecord.createTime ? formatDateTime(selectedSourceRecord.createTime) : '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="记录年度">{{ selectedSourceRecord.startYear || '--' }} — {{ selectedSourceRecord.endYear || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="原件"><HFileList :value="selectedSourceRecord.sourceFileId" show-download-btn download-only /></NDescriptionsItem>
        </NDescriptions>
        <NAlert type="info" :bordered="false" style="margin: 12px 0">此处显示导入原件及其管理记录。导入记录不替代借用、修复等正式审批单据。</NAlert>
        <NDescriptions v-for="event in selectedSourceEvents" :key="event.id" bordered :column="1" label-placement="left" size="small" style="margin-bottom: 12px">
          <NDescriptionsItem label="事项">{{ trackingEventTypeText(event.eventType) }} · {{ event.title }}</NDescriptionsItem>
          <NDescriptionsItem label="发生时间">{{ event.occurredAt ? formatDateTime(event.occurredAt) : '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="记录内容" content-style="white-space: pre-wrap; overflow-wrap: anywhere;">{{ event.content || '--' }}</NDescriptionsItem>
        </NDescriptions>
        <NEmpty v-if="!selectedSourceEvents.length" description="此原件未导入管理事项" style="margin-top: 12px" />
      </template>
    </HPageForm>

    <NModal v-model:show="mediaVisible" preset="card" :title="mediaForm.id ? '补充影像采集信息' : '添加藏品影像'" style="width: 560px">
      <NForm ref="mediaFormRef" :model="mediaForm" :rules="mediaFormRules" label-placement="left" label-width="90">
        <NFormItem label="影像文件" path="fileId"
          ><HUploadInput v-model:value="mediaForm.fileId" @upload-finish="mediaForm.fileId = $event.id" clearable
        /></NFormItem>
        <NFormItem label="影像类型"
          ><HDictSelect v-model:value="mediaForm.mediaType" type="col_media_type" clearable
        /></NFormItem>
        <NFormItem label="影像目录"
          ><NTreeSelect
            v-model:value="mediaForm.folderId"
            :options="mediaFolderTree"
            key-field="key"
            label-field="label"
            default-expand-all
            clearable
            placeholder="未归档"
        /></NFormItem>
        <NFormItem label="标题"><NInput v-model:value="mediaForm.title" :maxlength="200" clearable /></NFormItem>
        <NFormItem label="设为封面"><NSwitch v-model:value="mediaForm.isCover" /></NFormItem>
        <template v-if="mediaForm.capture">
          <NFormItem label="标准视图"><NSelect v-model:value="mediaForm.capture.view" :options="photoViewOptions" clearable @update:value="setPhotoView" /></NFormItem>
          <NFormItem label="图片顺序号"><NInputNumber v-model:value="mediaForm.capture.sequence" :min="1" :precision="0" /></NFormItem>
          <NFormItem label="对应分件号"><NInput v-model:value="mediaForm.capture.partNo" placeholder="非分件留空；同一视图下顺序号不能重复" /></NFormItem>
          <NFormItem label="拍摄单位"><NInput v-model:value="mediaForm.capture.institution" /></NFormItem>
          <NFormItem label="拍摄地点"><NInput v-model:value="mediaForm.capture.location" /></NFormItem>
          <NFormItem label="拍摄日期"><NDatePicker v-model:value="mediaForm.capture.photographedOn" type="date" clearable /></NFormItem>
          <NFormItem label="拍摄人"><NInput v-model:value="mediaForm.capture.photographer" /></NFormItem>
          <NFormItem label="比例尺说明"><NInput v-model:value="mediaForm.capture.scaleDescription" /></NFormItem>
          <NFormItem label="附件同框"><NCheckbox v-model:checked="mediaForm.capture.accessoriesIncluded">照片包括对应附件</NCheckbox></NFormItem>
        </template>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="mediaVisible = false">取消</NButton>
          <NButton type="primary" @click="saveMedia">保存</NButton>
        </NSpace>
      </template>
    </NModal>

    <NModal
      v-model:show="batchFileVisible"
      preset="card"
      title="批量关联一普图片与附件"
      style="width: 980px; max-width: calc(100vw - 32px)"
    >
      <NAlert type="info" :bordered="false" style="margin-bottom: 12px">
        支持规范命名“14位机构代码-总登记号-A至F视图-序号.扩展名”，须先核实本馆交换代码；兼容“总登记号__类型__序号.扩展名”，例如 000123__正面__01.jpg；也可上传后手工修正总登记号和类型。
      </NAlert>
      <HUpload v-if="batchFileVisible" :key="batchWindow" multiple :disabled="batchBusy" @upload-finish="batchUploadHandler" @change="batchUploadChangeHandler">
        <NButton type="primary" secondary :disabled="batchBusy">选择多个文件上传</NButton>
      </HUpload>
      <NDataTable :scroll-x="832"
        :columns="batchFileColumns"
        :data="batchFileItems"
        :bordered="false"
        size="small"
        :max-height="360"
        style="margin-top: 12px"
      />
      <NDataTable :scroll-x="480"
        v-if="batchFileErrors.length"
        :columns="importErrorColumns"
        :data="batchFileErrors"
        :bordered="false"
        size="small"
        :max-height="160"
        style="margin-top: 12px"
      />
      <NAlert v-if="batchUploading" type="info" style="margin-top: 12px">文件仍在上传，请完成后再预校验。</NAlert>
      <NAlert v-if="batchConfirmed?.validation.confirmation" type="success" style="margin-top: 12px">
        本次收藏单位：{{ batchConfirmed.validation.confirmation.orgName }}（{{ batchConfirmed.validation.confirmation.orgId }}）。
        请核对下方每个文件对应的藏品与关联方式；修改输入后须重新预校验。
      </NAlert>
      <NDataTable :scroll-x="690" v-if="batchConfirmed" :columns="batchMatchColumns" :data="batchConfirmed.validation.matches" size="small" :bordered="false" :max-height="220" style="margin-top: 12px" />
      <template #footer>
        <NSpace justify="end">
          <NButton @click="batchFileVisible = false">取消</NButton>
          <NButton :loading="batchSession.operation.value === 'validate'" :disabled="batchBusy || batchUploading || !batchFileItems.length" @click="validateBatchFiles">预校验</NButton>
          <NButton type="primary" :loading="batchSession.operation.value === 'commit'" :disabled="batchBusy || batchUploading || !batchConfirmed" @click="commitBatchFiles">确认关联</NButton>
        </NSpace>
      </template>
    </NModal>

    <NModal v-model:show="mediaFolderVisible" preset="card" title="影像目录管理" style="width: 720px">
      <NInputGroup
        ><NTreeSelect
          v-model:value="mediaFolderParentId"
          :options="mediaFolderTree"
          key-field="key"
          label-field="label"
          :render-label="renderMediaFolderSelectLabel"
          default-expand-all
          clearable
          filterable
          placeholder="上级目录（顶级留空）"
          style="width: 320px; min-width: 280px; flex: 1 1 280px"
        /><NInput v-model:value="mediaFolderName" :maxlength="100" show-count placeholder="新目录名称" /><NButton
          type="primary"
          @click="saveMediaFolder"
          >新增目录</NButton
        ></NInputGroup
      >
      <NDataTable :scroll-x="420"
        :columns="mediaFolderColumns"
        :data="mediaFolderTree"
        :row-key="mediaFolderRowKey"
        v-model:expanded-row-keys="mediaFolderExpandedKeys"
        size="small"
        :bordered="false"
        style="margin-top: 14px"
      />
    </NModal>

    <NModal v-model:show="attachmentVisible" preset="card" title="添加藏品附件" style="width: 560px">
      <NForm
        ref="attachmentFormRef"
        :model="attachmentForm"
        :rules="attachmentFormRules"
        label-placement="left"
        label-width="90"
      >
        <NFormItem label="附件文件" path="fileId"
          ><HUploadInput
            v-model:value="attachmentForm.fileId"
            @upload-finish="attachmentForm.fileId = $event.id"
            clearable
        /></NFormItem>
        <NFormItem label="附件类型"
          ><HDictSelect v-model:value="attachmentForm.attachmentType" type="col_attachment_type" clearable
        /></NFormItem>
        <NFormItem label="名称"><NInput v-model:value="attachmentForm.name" clearable /></NFormItem>
        <NFormItem label="备注"><NInput v-model:value="attachmentForm.remark" type="textarea" :rows="2" /></NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="attachmentVisible = false">取消</NButton>
          <NButton type="primary" @click="saveAttachment">保存</NButton>
        </NSpace>
      </template>
    </NModal>

    <NModal v-model:show="setVisible" preset="card" title="添加套件/分件关系" style="width: 620px">
      <NForm ref="setFormRef" :model="setForm" :rules="setFormRules" label-placement="left" label-width="90">
        <NFormItem label="关系类型" path="relation"
          ><HDictSelect v-model:value="setForm.relation" type="col_set_relation" clearable
        /></NFormItem>
        <NFormItem label="关联藏品"><CollectionSelect v-model:value="setForm.childCollectionId" clearable /></NFormItem>
        <NFormItem label="分件号"><NInput v-model:value="setForm.partNo" clearable /></NFormItem>
        <NFormItem label="分件名称"><NInput v-model:value="setForm.partName" clearable /></NFormItem>
        <NFormItem label="数量"
          ><NInputNumber v-model:value="setForm.quantity" :min="1" style="width: 100%"
        /></NFormItem>
        <NFormItem label="单位"><NInput v-model:value="setForm.unit" clearable /></NFormItem>
        <NFormItem label="排序"><NInputNumber v-model:value="setForm.sort" :min="0" style="width: 100%" /></NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="setVisible = false">取消</NButton>
          <NButton type="primary" @click="saveSet">保存</NButton>
        </NSpace>
      </template>
    </NModal>

    <!-- 总账留痕 -->
    <NModal v-model:show="ledgerVisible" preset="card" title="总账留痕" style="width: 1200px; max-width: calc(100vw - 32px)">
      <NDataTable :scroll-x="1400" max-height="calc(100vh - 200px)" :columns="ledgerColumns" :data="ledgerLogs" size="small" :bordered="false" />
    </NModal>

    <NModal v-model:show="correctVisible" preset="card" title="发起信息变更" style="width: 560px">
      <NForm
        ref="correctFormRef"
        :model="correctForm"
        :rules="correctFormRules"
        label-placement="left"
        label-width="92"
      >
        <NFormItem label="藏品">
          {{ correctTarget?.name || '--' }}（{{ correctTarget?.totalRegNo || '--' }}）
        </NFormItem>
        <NFormItem label="更正字段" path="field">
          <NSelect
            v-model:value="correctForm.field"
            :options="correctionFieldOptions"
            @update:value="onCorrectFieldChange"
          />
        </NFormItem>
        <NFormItem label="更正值" path="value">
          <HDictSelect
            v-if="correctForm.field === 'level'"
            v-model:value="correctStringValue"
            type="col_relic_level"
            clearable
            placeholder="选择藏品级别"
          />
          <HDictSelect
            v-else-if="correctForm.field === 'source'"
            v-model:value="correctStringValue"
            type="col_source"
            clearable
            placeholder="选择来源方式"
          />
          <NDatePicker
            v-else-if="correctForm.field === 'acquireDate'"
            v-model:value="correctDateValue"
            type="date"
            clearable
            style="width: 100%"
          />
          <NInput v-else v-model:value="correctStringValue" clearable placeholder="填写更正后的值" />
        </NFormItem>
        <NFormItem label="更正事由" path="reason">
          <NInput
            v-model:value="correctForm.reason"
            type="textarea"
            :rows="3"
            placeholder="填写审批依据、档案依据或更正原因"
          />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="correctVisible = false">取消</NButton>
          <NButton type="primary" @click="submitCorrect">建立变更申请</NButton>
        </NSpace>
      </template>
    </NModal>

    <NModal
      v-model:show="importVisible"
      preset="card"
      title="批量导入藏品"
      style="width: 920px; max-width: calc(100vw - 32px)"
    >
      <div class="import-modal-body">
        <NText depth="3">文物分类、库位填写名称；重名时填写完整路径（上级名称/名称）。封面填写已上传文件的唯一文件名。旧 ID 模板仍可导入。</NText>
        <NForm label-placement="left" label-width="96">
          <NFormItem label="导入模式">
            <NRadioGroup v-model:value="importMode">
              <NRadioButton value="CREATE_ONLY">仅新增</NRadioButton>
              <NRadioButton value="UPSERT">新增并更新</NRadioButton>
            </NRadioGroup>
          </NFormItem>
          <NFormItem v-if="importMode === 'UPSERT'" label="更正原因" required>
            <NInput
              v-model:value="importReason"
              type="textarea"
              :rows="2"
              placeholder="说明本次批量更新依据；将写入每件藏品的总账更正记录"
            />
          </NFormItem>
        </NForm>
        <div class="import-upload-bar">
          <NSpace align="center" size="small">
            <NButton type="primary" secondary :disabled="importBusy || !importTemplate.length" @click="openImportFile"
              >选择 Excel/CSV</NButton
            >
            <NButton secondary :disabled="!importTemplate.length" @click="downloadImportTemplate">下载模板</NButton>
          </NSpace>
          <div class="import-file-state">
            <strong>{{ importFileName || '未选择文件' }}</strong>
            <span>{{
              importRows.length ? `已解析 ${importRows.length} 条待导入数据` : '请选择模板文件后再预校验'
            }}</span>
          </div>
        </div>
        <input
          ref="importFileInput"
          type="file"
          accept=".xlsx,.xls,.csv"
          style="display: none"
          @change="onImportFileChange"
        />

        <div class="import-section import-template-section">
          <div class="import-section-title">
            <span>导入列说明</span>
            <NText depth="3">按模板列名匹配，必填列需完整填写</NText>
          </div>
          <div class="import-table-wrap">
            <NDataTable :scroll-x="620"
              :columns="templateColumns"
              :data="importTemplate"
              size="small"
              :bordered="false"
              :max-height="220"
            />
          </div>
        </div>

        <div v-if="importFileName" class="import-section import-result-section">
          <div class="import-section-title">
            <span>校验结果</span>
            <NText depth="3">{{
              importErrors.length
                ? `${importErrors.length} 条错误`
                : importRows.length
                  ? `已解析 ${importRows.length} 条`
                  : '未解析到数据'
            }}</NText>
          </div>
          <NDataTable :scroll-x="480"
            v-if="importErrors.length"
            :columns="importErrorColumns"
            :data="importErrors"
            size="small"
            :bordered="false"
            :max-height="160"
            class="import-error-table"
          />
          <NText v-else-if="importConfirmed?.validation.confirmation" type="success"
            >目标馆：{{ importConfirmed.validation.confirmation.orgName }}（{{
              importConfirmed.validation.confirmation.orgId
            }}），新增 {{ importConfirmed.validation.createCount }} 条、更正
            {{ importConfirmed.validation.updateCount }} 条。请核对后确认。</NText
          >
          <NText v-else-if="importRows.length">文件已解析，请先完成服务端预校验。</NText>
          <NText v-else type="warning">未解析到可导入数据，请检查模板列名和文件内容。</NText>
        </div>
      </div>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="importVisible = false">取消</NButton>
          <NButton
            :disabled="!importRows.length || importBusy"
            :loading="importSession.operation.value === 'validate'"
            @click="validateImport"
            >预校验</NButton
          >
          <NButton
            type="primary"
            :disabled="!importConfirmed || importBusy"
            :loading="importSession.operation.value === 'commit'"
            @click="commitImport"
            >确认导入</NButton
          >
        </NSpace>
      </template>
    </NModal>

    <CollectionPrintPreviewModal
      v-model:show="printVisible"
      :data="printData"
      :type="printType"
      :id="printId"
      :document-no="printDocumentNo"
      :title="printTitle"
    />

    <NModal
      :show="!!classificationLedgerPreview"
      preset="card"
      :title="classificationLedgerPreview?.title || '藏品分类账预览'"
      style="width: 1080px; max-width: calc(100vw - 32px)"
      @update:show="handleClassificationLedgerPreviewShow"
    >
      <p>请核对收藏单位、分类、记录数和每件藏品信息后打印。</p>
      <iframe
        v-if="classificationLedgerPreview"
        :srcdoc="classificationLedgerPreviewDocument"
        title="藏品分类账预览"
        sandbox=""
        style="width: 100%; height: 62vh; border: 1px solid #dcdfe6"
      />
      <template #footer>
        <NSpace justify="end">
          <NButton @click="classificationLedgerPreview = null">关闭</NButton>
          <NButton type="primary" :loading="classificationLedgerChecking" @click="confirmClassificationLedgerPrint">打印</NButton>
        </NSpace>
      </template>
    </NModal>

    <!-- 提用（出入库流程） -->
    <HPageForm
      v-model:show="reqVisible"
      title="藏品提用"
      :model="reqForm"
      :rules="reqFormRules"
      :ref-table="tableRef"
      :handle-submit="doRequisition"
      label-placement="left"
      label-width="90"
      submit-text="提交审批"
    >
      <NFormItem label="藏品">{{ reqTarget?.name }}（{{ reqTarget?.totalRegNo }}）</NFormItem>
      <NFormItem label="提用单号"
        ><NInput v-model:value="reqForm.documentNo" placeholder="留空由系统自动生成"
      /></NFormItem>
      <NFormItem label="提用事由" path="purpose"
        ><NSelect v-model:value="reqForm.purpose" :options="requisitionPurposeOptions" clearable placeholder="提用事由"
      /></NFormItem>
      <NFormItem label="提用依据" path="basis"
        ><NInput v-model:value="reqForm.basis" placeholder="审批单号 / 展览任务 / 修复方案等"
      /></NFormItem>
      <NFormItem label="提用部门" path="department"
        ><NInput v-model:value="reqForm.department" placeholder="提用部门"
      /></NFormItem>
      <NFormItem label="经办人" path="operator"
        ><UserSelect v-model:value="reqForm.operator" placeholder="经办人（系统用户）"
      /></NFormItem>
      <NFormItem label="复核人"
        ><UserSelect v-model:value="reqForm.reviewer" placeholder="复核人（高等级藏品必填，且≠经办人）"
      /></NFormItem>
      <NFormItem label="保管员"><UserSelect v-model:value="reqForm.custodian" placeholder="保管员" /></NFormItem>
      <NFormItem label="交接人"><UserSelect v-model:value="reqForm.handoverUser" placeholder="交接人" /></NFormItem>
      <NFormItem label="预计归还">
        <NDatePicker v-model:value="reqForm.expectReturnDate" type="date" clearable style="width: 100%" />
      </NFormItem>
      <NFormItem label="回库库位">
        <WarehouseTreeSelect v-model:value="reqForm.toWarehouseId" placeholder="归还时入库库位" />
      </NFormItem>
      <NFormItem label="附件"
        ><HUploadInput
          v-model:value="reqForm.attachmentIds"
          @upload-finish="reqForm.attachmentIds = $event.id"
          placeholder="提用依据附件"
          clearable
      /></NFormItem>
      <NFormItem label="审核意见"><NInput v-model:value="reqForm.reviewOpinion" type="textarea" :rows="2" /></NFormItem>
    </HPageForm>

    <!-- 申请退出馆藏 -->
    <HPageForm
      v-model:show="deregVisible"
      title="申请退出馆藏"
      :model="deregForm"
      :rules="deregFormRules"
      :ref-table="tableRef"
      :handle-submit="doDereg"
      label-placement="left"
      label-width="90"
      submit-text="提交申请"
    >
      <NFormItem label="藏品">{{ deregTarget?.name }}（{{ deregTarget?.totalRegNo }}）</NFormItem>
      <NFormItem label="退出馆藏原因" path="reason"
        ><HDictSelect v-model:value="deregForm.reason" type="col_dereg_reason" clearable placeholder="原因"
      /></NFormItem>
      <NFormItem label="审批层级"
        ><HDictSelect v-model:value="deregForm.approveLevel" type="col_approve_level" clearable placeholder="层级"
      /></NFormItem>
      <NFormItem label="批文号"
        ><NInput v-model:value="deregForm.approvalDocNo" placeholder="审批批文号 / 处理决定编号"
      /></NFormItem>
      <NFormItem label="专家意见" path="expertOpinion"
        ><NInput v-model:value="deregForm.expertOpinion" type="textarea" :rows="2" placeholder="专家论证意见及结论"
      /></NFormItem>
      <NFormItem label="退出馆藏后去向" path="destination"
        ><NInput
          v-model:value="deregForm.destination"
          type="textarea"
          :rows="2"
          placeholder="销毁、移交、退还等处置去向"
      /></NFormItem>
      <NFormItem label="说明" path="detail"
        ><NInput v-model:value="deregForm.detail" type="textarea" :rows="2" placeholder="退出馆藏说明"
      /></NFormItem>
      <NFormItem label="附件"
        ><HUploadInput
          v-model:value="deregForm.attachmentIds"
          @upload-finish="deregForm.attachmentIds = $event.id"
          placeholder="上传专家论证、审批批文、处置凭证等"
          clearable
      /></NFormItem>
    </HPageForm>
    <CollectionExchangeModal ref="exchangeRef" />
    <PaperLedgerDrawer ref="paperLedgerRef" />
    <CollectionArchivePacketDrawer ref="archivePacketRef" />
    <CollectionDisclosureDrawer ref="disclosureRef" />
  </HPageDataTable>
</template>

<script setup lang="ts">
const props = withDefaults(defineProps<{ classificationLedger?: boolean }>(), { classificationLedger: false });
import '../styles/collection-page.css';
import { validateFormSafely } from '../utils/formValidation';
import { isRegistered, registrationDateFallback } from '../utils/registrationStatus';
import CollectionLedgerLogValue from '../components/CollectionLedgerLogValue.vue';
import MigrationVerificationPanel from '../components/MigrationVerificationPanel.vue';
import { ledgerFieldLabel } from '../utils/ledgerLogDisplay';
import { eraMeaningLabel, eraNotationLabel } from '../utils/collectionDescription';
import OriginalHistoryButton from '@/modules/collection/components/OriginalHistoryButton.vue';
import CollectionNatureReviewDrawer from '../components/CollectionNatureReviewDrawer.vue';
import CollectionOrgName from '../components/CollectionOrgName.vue';
import { computed, h, nextTick, onMounted, onActivated, onDeactivated, onBeforeUnmount, reactive, ref, watch } from 'vue';
import CurrentFileImage from '@/components/basic/CurrentFileImage.vue';
import CurrentImagePreviewGroup from '@/components/basic/CurrentImagePreviewGroup.vue';
import { useRoute, useRouter } from 'vue-router';
import { useElementSize } from '@vueuse/core';
import { ChevronDownOutline, ChevronForwardOutline, VideocamOutline } from '@vicons/ionicons5';
import { buildMediaFolderTree, isVideoMedia, mediaFolderPath, mediaFolderExpandableKeys } from '../utils/collectionMedia';
import { collectionNatureLabel } from '../utils/collectionNature';
import {
  NButton,
  NDropdown,
  NIcon,
  NInput,
  NInputNumber,
  NSelect,
  NSpace,
  NTag,
  NTooltip,
  useDialog,
  useMessage,
  useThemeVars,
  type FormInst,
  type FormRules,
  type UploadFileInfo,
} from 'naive-ui';
import * as XLSX from 'xlsx';
import { categoryApi } from '../api/category';
import CatalogDetail from '../components/CatalogDetail.vue';
import HFileList from '@/components/form/field/upload/fileList.vue';
import { catalogApi } from '../api/catalog';
import { readCatalog } from '../utils/catalogResponses';
import type { CollectionCatalogVo } from '../typings/collection';
import { fileBusinessSource, fileBusinessSourceLabel } from '../utils/fileBusinessSource';
import { collectionApi } from '../api/collection';
import { useCollectionImportSession } from '../composables/useCollectionImportSession';
import { ledgerValidation, ledgerCommit } from '../utils/importResponses';
import { batchFileValidation, batchFileCommit } from '../utils/batchFileResponses';
import { cloneDeep } from 'lodash-es';
import type { CollectionImportValidateResultVo, CollectionFileImportResultVo } from '../typings/collection';
import { movementApi } from '../api/movement';
import { requisitionApi } from '../api/requisition';
import { deregistrationApi } from '../api/deregistration';
import { type as fetchDictType, frontType as fetchPrintDictType } from '@/modules/sys/api/dict';
import { orgApi } from '../api/org';
import { requisitionPurposeOptions } from '../utils/requisitionPurpose';
import { useAuthStore } from '@/store/modules/auth';
import { htmlPrint } from '@/components/basic/print/PrintUtils';
import { formatDateTime, formatLocalDateValue } from '@/utils';
import { fileUrl } from '@/utils';
import RegisteredMigrationModal from '../components/RegisteredMigrationModal.vue';
import MigrationBatchVerificationModal from '../components/MigrationBatchVerificationModal.vue';
import CollectionFormModal from '../components/CollectionFormModal.vue';
import CollectionSetEntryModal from '../components/CollectionSetEntryModal.vue';
import CollectionWordImportModal from '../components/CollectionWordImportModal.vue';
import { REGISTRATION_RECEIPT_HINT, WAREHOUSE_FIXED_AT_RECEIPT_HINT } from '../utils/accessionReceiptHints';
import CollectionFieldLabel from '../components/CollectionFieldLabel.vue';
import CollectionSelect from '../components/CollectionSelect.vue';
import CollectionPrintPreviewModal from '../components/CollectionPrintPreviewModal.vue';
import WarehouseTreeSelect from '../components/WarehouseTreeSelect.vue';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import UserNameText from '../components/UserNameText.vue';
import { dictText } from '../utils/dictRender';
import { COLLECTION_ARCHIVE_PRINT_TYPE } from '../utils/printDocumentMeta';
import { type CollectionFieldOption } from '../utils/collectionFieldSettings';
import { registrationOptions, registrationLabel, collectionSearchReturnTarget } from '../utils/collectionEntitySearch';
import { applyCollectionRouteQuery, type CollectionRouteQuery } from '../utils/collectionRouteQuery';
import {
  correctionFieldOptions,
  initialCorrectionValue,
  normalizeCorrectionValue,
  type CollectionCorrectionField,
  type CollectionCorrectionValue,
} from '../utils/collectionCorrection';
import { canManageDisclosure, publishActionText, publishStatus } from '../utils/collectionPublish';
import { formatImportDictOptions, formatImportSample } from '../utils/importTemplateDisplay';
import { formatYuan, parseImportAmount, yuanImportColumn } from '../utils/moneyYuan';
import { standardDimensions, standardMass } from '../utils/registrationMeasurements';
import { downloadSurveyCsv, type CollectionSurveyExportVo } from '../utils/surveyExport';
import {
  exportCollectionAppraisalForm,
  loadCollectionAppraisalFormData,
  printCollectionAppraisalForm,
} from '../utils/collectionAppraisalForm';
import { isFormalCollection } from '../utils/statAnalysis';
import CollectionExchangeModal from '../components/CollectionExchangeModal.vue';
const exchangeRef=ref<InstanceType<typeof CollectionExchangeModal>>();
import PaperLedgerDrawer from '../components/PaperLedgerDrawer.vue';
import CollectionArchivePacketDrawer from '../components/CollectionArchivePacketDrawer.vue';
import CollectionDisclosureDrawer from '../components/CollectionDisclosureDrawer.vue';
const disclosureRef = ref<any>();
const archivePacketRef = ref<any>();
const paperLedgerRef = ref<any>();
import type {
  CollectionAttachmentVo,
  CollectionImportErrorVo,
  CollectionImportMode,
  CollectionFileImportItemVo,
  CollectionImportRowVo,
  CollectionImportTemplateColumnVo,
  CollectionMediaVo,
  CollectionMediaFolderVo,
  CollectionPrintVo,
  CollectionSetVo,
  CollectionTrackingEventVo,
  CollectionTrackingHistoryVo,
  CollectionWordImportRecordVo,
  CollectionVo,
  LedgerLogVo,
  MovementVo,
} from '../typings/collection';

const natureReviewRef = ref<InstanceType<typeof CollectionNatureReviewDrawer>>();
const message = useMessage();
const dialog = useDialog();
const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const canReviewMigration = computed(() =>
  authStore.hasPermission('COLLECTION_ADMIN,COLLECTION_LEDGER_CREATE,COLLECTION_CATALOG_AUDIT'),
);
const ledgerMoreOptions = computed(() => [
  { label: '法定性质核查', key: 'nature-review' },
  ...(canReviewMigration.value ? [{ label: '迁移批次复核', key: 'migration-review' }] : []),
  { label: '历史材料', key: 'original-history' },
]);
function handleLedgerMoreSelect(key: string, openHistory: () => void) {
  if (key === 'nature-review') natureReviewRef.value?.open();
  if (key === 'migration-review' && canReviewMigration.value) migrationBatchRef.value?.open();
  if (key === 'original-history') openHistory();
}
const canImportRegistrationWord = computed(() =>
  authStore.hasPermission('COLLECTION_ADMIN,COLLECTION_LEDGER_CREATE,COLLECTION_LEDGER_CORRECT'),
);
const canImportDynamicTrackingWord = computed(() => authStore.hasPermission('COLLECTION_ADMIN,COLLECTION_STORAGE'));
const canUseCollectionImport = computed(() => canImportRegistrationWord.value || canImportDynamicTrackingWord.value);
const canBatchImportCollection = computed(() => authStore.hasPermission('COLLECTION_ADMIN,COLLECTION_LEDGER_CREATE'));
const collectionImportOptions = computed(() => {
  const options: Array<{ label: string; key: string }> = [];
  if (canImportRegistrationWord.value && canImportDynamicTrackingWord.value) {
    options.push({ label: '导入动态跟踪记录汇总表（Word）', key: 'dynamic-tracking-word' });
  }
  if (canBatchImportCollection.value) {
    options.push(
      { label: '批量导入藏品（待登记）', key: 'batch-collection' },
      { label: '已登账藏品迁移（旧系统）', key: 'registered-migration' },
      { label: '批量导入图片/附件', key: 'batch-file' },
    );
  }
  return options;
});
const collectionExportOptions = [
  { label: '普查数据（CSV）', key: 'survey' },
  { label: '规范交换检查与照片包', key: 'exchange' },
  { label: '分类账（打印）', key: 'classification-ledger' },
];

const tableRef = ref<any>(null);
const tableExportLoading = computed(() => Boolean(tableRef.value?.exportLoading));
const formRef = ref<any>(null);
const setEntryRef = ref<any>(null);
const wordImportRef = ref<InstanceType<typeof CollectionWordImportModal> | null>(null);
const mediaFormRef = ref<FormInst | null>(null);
const attachmentFormRef = ref<FormInst | null>(null);
const setFormRef = ref<FormInst | null>(null);
const correctFormRef = ref<FormInst | null>(null);

const queryParams = reactive({
  collectionId: null as string | null,
  orgId: null as string | null,
  keyword: null as string | null,
  totalRegNo: null as string | null,
  classNo: null as string | null,
  categoryId: null as string | null,
  era: null as string | null,
  level: null as string | null,
  source: null as string | null,
  registrationScope: null as string | null,
  nature: null as string | null,
  relicLevelScope: null as string | null,
  registeredDateStart: null as number | null,
  registeredDateEnd: null as number | null,
  lifeStatus: null as string | null,
  catalogStatus: null as string | null,
  texture: null as string | null,
  completeness: null as string | null,
  warehouseId: null as string | null,
  acquireDateStart: null as number | null,
  acquireDateEnd: null as number | null,
  createTimeStart: null as number | null,
  createTimeEnd: null as number | null,
  hasImage: null as string | null,
  hasAttachment: null as string | null,
});
const fromCollectionSearch = computed(() => route.query.searchOrigin === 'collection-search');
function returnToCollectionSearch() {
  void router.push(collectionSearchReturnTarget(route.query));
}
const acquireDateRange = ref<[number, number] | null>(null);
const createTimeRange = ref<[number, number] | null>(null);
const registeredDateRange = ref<[number, number] | null>(null);
const queryConfigOptions = [
  {
    value: 'registeredDate',
    clearPaths: ['registeredDateStart', 'registeredDateEnd'],
    onClear: () => {
      const changed = registeredDateRange.value !== null;
      registeredDateRange.value = null;
      return changed;
    },
  },
  {
    value: 'acquireDate',
    clearPaths: ['acquireDateStart', 'acquireDateEnd'],
    onClear: () => {
      const changed = acquireDateRange.value !== null;
      acquireDateRange.value = null;
      return changed;
    },
  },
  {
    value: 'createTime',
    clearPaths: ['createTimeStart', 'createTimeEnd'],
    onClear: () => {
      const changed = createTimeRange.value !== null;
      createTimeRange.value = null;
      return changed;
    },
  },
];
const routeQueryFieldAliases: Record<string, string> = {
  registeredDateStart: 'registeredDate',
  registeredDateEnd: 'registeredDate',
  acquireDateStart: 'acquireDate',
  acquireDateEnd: 'acquireDate',
  createTimeStart: 'createTime',
  createTimeEnd: 'createTime',
};
const routeVisibleQueryKeys = computed(() => {
  const keys = new Set<string>(props.classificationLedger ? ['categoryId', 'classNo'] : []);
  Object.entries(route.query).forEach(([key, value]) => {
    const raw = Array.isArray(value) ? value[0] : value;
    if (typeof raw === 'string' && raw.trim()) {
      keys.add(routeQueryFieldAliases[key] || key);
    }
  });
  return Array.from(keys);
});
const categoryOptions = ref<{ label: string; value: string }[]>([]);
const correctVisible = ref(false);
const correctTarget = ref<CollectionVo | null>(null);
const correctForm = reactive<{ field: CollectionCorrectionField; value: CollectionCorrectionValue; reason: string }>({
  field: 'name',
  value: '',
  reason: '',
});
const correctStringValue = computed<string | null>({
  get: () => (typeof correctForm.value === 'string' ? correctForm.value : null),
  set: (value) => {
    correctForm.value = value ?? '';
  },
});
const correctDateValue = computed<number | null>({
  get: () => (typeof correctForm.value === 'number' ? correctForm.value : null),
  set: (value) => {
    correctForm.value = value ?? null;
  },
});
const mediaFormRules: FormRules = {
  fileId: { required: true, message: '请选择影像文件', trigger: ['change', 'blur'] },
};
const attachmentFormRules: FormRules = {
  fileId: { required: true, message: '请选择附件文件', trigger: ['change', 'blur'] },
};
const setFormRules: FormRules = {
  relation: { required: true, message: '请选择关系类型', trigger: ['change', 'blur'] },
  partNo: { required: true, message: '请输入分件号', trigger: ['input', 'blur'] },
  partName: { required: true, message: '请输入分件名称', trigger: ['input', 'blur'] },
  quantity: { required: true, type: 'number', message: '请输入数量', trigger: ['change', 'blur'] },
  unit: { required: true, message: '请输入单位', trigger: ['input', 'blur'] },
};
const correctFormRules: FormRules = {
  field: { required: true, message: '请选择更正字段', trigger: ['change', 'blur'] },
  value: {
    validator: () => Boolean(normalizeCorrectionValue(correctForm.field, correctForm.value)),
    message: '请填写更正值',
    trigger: ['change', 'blur', 'input'],
  },
  reason: {
    validator: () => Boolean(correctForm.reason?.trim()),
    message: '请填写更正事由',
    trigger: ['blur', 'input'],
  },
};
const reqFormRules: FormRules = {
  purpose: { required: true, message: '请选择提用事由', trigger: ['change', 'blur'] },
  basis: { required: true, message: '请输入提用依据', trigger: ['blur', 'input'] },
  department: { required: true, message: '请输入提用部门', trigger: ['blur', 'input'] },
  operator: { required: true, message: '请选择经办人', trigger: ['change', 'blur'] },
};
const deregFormRules: FormRules = {
  reason: { required: true, message: '请选择退出馆藏原因', trigger: ['change', 'blur'] },
  detail: { required: true, message: '请输入退出馆藏说明', trigger: ['blur', 'input'] },
  expertOpinion: { required: true, message: '请输入专家意见', trigger: ['blur', 'input'] },
  destination: { required: true, message: '请输入退出馆藏后去向', trigger: ['blur', 'input'] },
};

function syncAcquireDateRange(value: [number, number] | null) {
  queryParams.acquireDateStart = value?.[0] ?? null;
  queryParams.acquireDateEnd = value?.[1] ?? null;
}

function syncRegisteredDateRange(value: [number, number] | null) {
  queryParams.registeredDateStart = value?.[0] ?? null;
  queryParams.registeredDateEnd = value?.[1] ?? null;
}

function syncCreateTimeRange(value: [number, number] | null) {
  queryParams.createTimeStart = value?.[0] ?? null;
  queryParams.createTimeEnd = value?.[1] ?? null;
}

async function loadQueryOptions() {
  const categoryResp: any = await categoryApi.list();
  const categories: any[] = categoryResp?.data?.list ?? categoryResp?.data ?? [];
  categoryOptions.value = categories.map((c) => ({ label: c.name, value: c.id }));
}
onMounted(loadQueryOptions);

const detailVisible = ref(false);
const detailRoot = ref<HTMLElement>();
const detailTabsAnchor = ref<HTMLElement>();
const detailTab = ref('overview');
const detailSections = computed(() => [
  { label: '档案概览', value: 'overview' },
  { label: '编目与版本', value: 'catalog' },
  { label: '基本信息', value: 'base' },
  ...(migrationSnapshot.value ? [{ label: '旧系统迁移', value: 'migration' }] : []),
  { label: '档案描述', value: 'description' },
  { label: '影像与附件', value: 'media' },
  { label: '套件分件', value: 'set' },
  { label: '出入库', value: 'movement' },
  { label: '管理记录', value: 'tracking' },
]);
const { width: detailWidth } = useElementSize(detailRoot);
const detailColumns = computed(() => detailWidth.value > 640 ? 2 : 1);
const detailThemeVars = useThemeVars();
const detailTheme = computed(() => ({
  '--detail-accent': detailThemeVars.value.primaryColor,
  '--detail-text': detailThemeVars.value.textColor1,
  '--detail-muted': detailThemeVars.value.textColor3,
  '--detail-border': detailThemeVars.value.borderColor,
  '--detail-surface': detailThemeVars.value.cardColor,
  '--detail-fill': detailThemeVars.value.tableHeaderColor,
}));
function goDetailTab(name: string) {
  detailTab.value = name;
  void nextTick(() => {
    detailTabsAnchor.value?.focus({ preventScroll: true });
    detailTabsAnchor.value?.scrollIntoView({ block: 'start' });
  });
}
const detailActionOptions = computed(() => [
  { label: current.value ? publishActionText(current.value) : '公开核查与实发记录', key: 'publish', disabled: !current.value || !canManageDisclosure(current.value) },
  { label: '关键字段更正', key: 'correct' },
  { label: '本馆纸账与财务核对', key: 'paper', disabled: !current.value?.orgId },
  ...(authStore.hasPermission('COLLECTION_ADMIN,COLLECTION_ARCHIVE_MANAGE') ? [{ label: '实际建档与行政备案', key: 'archive', disabled: !current.value?.orgId }] : []),
]);
function handleDetailAction(key: string) {
  const row = current.value;
  if (!row) return;
  if (key === 'publish' && canManageDisclosure(row)) onPublish(row);
  if (key === 'correct') onCorrect(row);
  if (key === 'paper' && row.orgId) paperLedgerRef.value?.open({ id: row.orgId });
  if (key === 'archive' && row.orgId && authStore.hasPermission('COLLECTION_ADMIN,COLLECTION_ARCHIVE_MANAGE')) archivePacketRef.value?.open({ orgId: row.orgId, collectionId: row.id });
}
const detailFeature = computed(() => {
  const value = current.value?.feature?.trim();
  return value ? (value.length > 160 ? `${value.slice(0, 160)}…` : value) : '尚未记录特征描述，可在档案描述中查阅其他著录信息。';
});
const detailResources = computed(() => [
  { label: '影像', count: mediaList.value.length, tab: 'media', error: '影像和附件' },
  { label: '附件', count: attachmentList.value.length, tab: 'media', error: '影像和附件' },
  { label: '出入库', count: movementList.value.length, tab: 'movement', error: '出入库记录' },
  { label: '管理记录', count: trackingEvents.value.length, tab: 'tracking', error: '管理记录' },
]);
function detailResourceCount(count: number, error: string) {
  return detailAuxLoading.value ? '…' : detailAuxErrors.value.includes(error) ? '—' : count;
}
const ledgerVisible = ref(false);
const current = ref<CollectionVo | null>(null);
const detailAuxLoading = ref(false);
const detailAuxErrors = ref<string[]>([]);
let detailRequestVersion = 0;
const detailCatalog = ref<CollectionCatalogVo | null>(null);
const ledgerLogs = ref<LedgerLogVo[]>([]);
const migrationSnapshot = computed(() => {
  const log = ledgerLogs.value.find(item => item.field === 'legacyMigration');
  if (!log?.after) return null;
  try { const value = JSON.parse(log.after); return value && typeof value === 'object' && typeof value.batchId === 'string' ? value : null; }
  catch { return null; }
});
const mediaList = ref<CollectionMediaVo[]>([]);
const mediaFolderList = ref<CollectionMediaFolderVo[]>([]);
const mediaFolderVisible = ref(false);
const mediaFolderName = ref('');
const mediaFolderParentId = ref<string | null>(null);
// 影像目录按 parentId 分层显示（选择框、目录管理表格共用）。
const mediaFolderTree = computed(() => buildMediaFolderTree(mediaFolderList.value));
const mediaFolderExpandedKeys = ref<Array<string | number>>([]);
const mediaFolderRowKey = (row: { key: string }) => row.key;
function syncMediaFolderExpandedKeys(prefer?: string | null) {
  const all = mediaFolderExpandableKeys(mediaFolderTree.value);
  const preferId = prefer ? String(prefer) : '';
  const prev = new Set(mediaFolderExpandedKeys.value.map(String));
  if (preferId) prev.add(preferId);
  // 首次打开（无已展开）或新建后：保留原展开并强制展开当前上级；若仍为空则全展开。
  let next = all.filter((k) => prev.has(k));
  if (!next.length) next = all.slice();
  if (preferId && all.includes(preferId) && !next.includes(preferId)) next = [...next, preferId];
  mediaFolderExpandedKeys.value = next;
}
function renderMediaFolderSelectLabel({ option }: { option: { label?: string; depth?: number } }) {
  const label = String(option?.label ?? '');
  const pad = '　'.repeat(Math.max(0, Number(option?.depth) || 0));
  return h(
    NTooltip,
    { trigger: 'hover', disabled: label.length < 18 },
    {
      trigger: () =>
        h(
          'span',
          {
            style:
              'display:inline-block;max-width:280px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;vertical-align:bottom',
          },
          pad + label,
        ),
      default: () => label,
    },
  );
}
const mediaFolderColumns = [
  { ellipsis: { tooltip: true }, resizable: true, width: 200, title: '目录名称', key: 'name', minWidth: 200 },
  {
    title: '操作',
    key: 'action',
    width: 90,
    render: (row: CollectionMediaFolderVo) =>
      h(NButton, { text: true, type: 'error', size: 'small', onClick: () => deleteMediaFolder(row) }, () => '删除'),
  },
];
const attachmentList = ref<CollectionAttachmentVo[]>([]);
const setList = ref<CollectionSetVo[]>([]);
const movementList = ref<MovementVo[]>([]);
const trackingHistory = ref<CollectionTrackingHistoryVo>({ imports: [], events: [] });
const trackingImports = computed(() =>
  Array.isArray(trackingHistory.value?.imports) ? trackingHistory.value.imports : [],
);
const trackingEvents = computed(() =>
  Array.isArray(trackingHistory.value?.events) ? trackingHistory.value.events : [],
);
const selectedMediaKey = ref<string | null>(null);
const mediaPreviewShow = ref(false), mediaReadEpoch = ref(0);
const mediaPreviewItems = computed(() => mediaImages.value.map(item => ({id: mediaKey(item), src: fileUrl(item.fileId)})));
function openMediaPreview() { mediaPreviewShow.value = true; }
const exporting = ref(false);

const dimensionLabels: Record<string, string> = {
  height: '通高',
  mouth: '口径',
  bottom: '底径',
  belly: '腹径',
  length: '长',
  width: '宽',
  diameter: '直径',
};

const COLUMN_FIELD_STORAGE_KEY = props.classificationLedger ? 'collection.classificationLedger.dailyColumns.v1' : 'collection.ledger.dailyColumns.v2';

const columnFieldOptions: CollectionFieldOption[] = [
  { label: '序号', value: 'sequence' },
  { label: '总登记号', value: 'totalRegNo' },
  { label: '名称', value: 'name' },
  { label: '登记状态', value: 'registrationScope' },
  { label: '年代', value: 'eraSummary' },
  { label: '类别', value: 'categoryName' },
  { label: '质地', value: 'texture' },
  { label: '数量', value: 'actualQuantity' },
  { label: '级别', value: 'level' },
  { label: '藏品状态', value: 'lifeStatus' },
  { label: '库房库位', value: 'warehouseName' },
  { label: '来源', value: 'source' },
  { label: '正式登记时间', value: 'registeredAt' },
  { label: '原名', value: 'originalName' },
  { label: '具体尺寸', value: 'specificDimensions' },
  { label: '质量范围', value: 'weightRange' },
  { label: '具体质量', value: 'weightValue' },
  { label: '质量单位', value: 'weightUnit' },
  { label: '完残程度', value: 'completeness' },
  { label: '完残状况', value: 'completeStatus' },
  { label: '保存状态', value: 'preserveState' },
  { label: '取得时间范围', value: 'acquireDateRange' },
  { label: '取得年度', value: 'acquireYear' },
  { label: '著者', value: 'author' },
  { label: '版本', value: 'edition' },
  { label: '存卷', value: 'volumeNo' },
  { label: '分类号', value: 'classNo' },
];

const defaultColumnKeys = [
  ...(props.classificationLedger ? ['classNo'] : []),
  'sequence', 'totalRegNo', 'name', 'registrationScope', 'eraSummary', 'categoryName',
  'texture', 'actualQuantity', 'level', 'lifeStatus', 'warehouseName', 'source', 'registeredAt',
];

function syncRouteQueryFilters() {
  const routeQuery = route.query as CollectionRouteQuery;
  return applyCollectionRouteQuery(routeQuery, queryParams, acquireDateRange, createTimeRange, registeredDateRange);
}

syncRouteQueryFilters();
let ledgerActive = true;
let routeRefreshPending = false;
onDeactivated(() => { ledgerActive = false; });
onActivated(() => {
  ledgerActive = true;
  if (syncRouteQueryFilters()) routeRefreshPending = true;
  if (routeRefreshPending) {
    routeRefreshPending = false;
    void nextTick(() => tableRef.value?.handleRefresh?.());
  }
});
watch(
  () => route.query,
  () => {
    if (route.path !== (props.classificationLedger ? '/collection/classification-ledger' : '/collection/ledger') || !syncRouteQueryFilters()) return;
    if (ledgerActive) void nextTick(() => tableRef.value?.handleRefresh?.());
    else routeRefreshPending = true;
  },
  { flush: 'post' },
);

function mediaKey(item: CollectionMediaVo) {
  return item.id || item.fileId || `${item.collectionId || 'media'}-${item.title || item.mediaType || 'item'}`;
}

function selectMedia(item: CollectionMediaVo) {
  selectedMediaKey.value = mediaKey(item);
}

const mediaImages = computed(() =>
  mediaList.value.filter(
    (item): item is CollectionMediaVo & { fileId: string } => Boolean(item.fileId) && !isVideoMedia(item),
  ),
);

const selectedMediaIndex = computed({
  get() {
    const index = mediaImages.value.findIndex((item) => mediaKey(item) === selectedMediaKey.value);
    return index >= 0 ? index : 0;
  },
  set(index: number) {
    const item = mediaImages.value[index];
    if (item) selectMedia(item);
  },
});

const fallbackCoverImageSrc = computed(() => {
  if (mediaImages.value.length || !current.value?.coverImage) return '';
  return fileUrl(current.value.coverImage);
});

function dimensionText(dimensions?: Record<string, any> | null) {
  const parts = Object.entries(dimensions ?? {})
    .filter(([key, value]) => key !== 'extra' && value !== undefined && value !== null && String(value).trim() !== '')
    .map(([key, value]) => `${dimensionLabels[key] || key}：${value}`);
  Object.entries(dimensions?.extra ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim()) {
      parts.push(key === 'description' ? String(value) : `${key}：${value}`);
    }
  });
  return parts.join('；') || '--';
}

function valuationText(value?: number | null) {
  return formatYuan(value);
}

function ledgerValue(value: unknown) {
  if (value === undefined || value === null || String(value).trim() === '') return '--';
  return String(value);
}

function quantityText(row: CollectionVo) {
  const parts = [row.quantity, row.unit]
    .filter((value) => value !== undefined && value !== null && String(value).trim() !== '')
    .map(String);
  return parts.length ? parts.join('') : '--';
}

function weightParts(weight?: string, explicitUnit?: string) {
  const clean = weight?.trim() ?? '';
  const match = clean.match(/^([+-]?\d+(?:\.\d+)?)\s*([a-zA-Zµμ\u4e00-\u9fff]+)?$/);
  return {
    value: match?.[1] || clean,
    unit: explicitUnit?.trim() || match?.[2] || '',
  };
}

function dictLedgerText(type: string, value: unknown) {
  return value === undefined || value === null || String(value).trim() === '' ? '--' : dictText(type, value);
}

function ledgerEraText(row: CollectionVo) {
  const values = [row.era]
    .map((value) => value?.trim())
    .filter((value): value is string => !!value);
  return Array.from(new Set(values)).join(' · ') || '--';
}

const ledgerSortCollator = new Intl.Collator('zh-CN', { numeric: true, sensitivity: 'base' });

const dataColumns: any[] = [
  { title: '序号', key: 'sequence', width: 64, fixed: 'left', columnType: 'index' },
  { title: '总登记号', key: 'totalRegNo', sorter: (a: CollectionVo, b: CollectionVo) => ledgerSortCollator.compare(a.totalRegNo || '', b.totalRegNo || ''), width: 145, fixed: 'left', resizable: true, ellipsis: { tooltip: true }, render: (row: CollectionVo) => ledgerValue(row.totalRegNo) },
  { title: '名称', key: 'name', sorter: (a: CollectionVo, b: CollectionVo) => ledgerSortCollator.compare(a.name || '', b.name || ''), width: 190, minWidth: 190, fixed: 'left', resizable: true, columnType: 'title', ellipsis: { tooltip: true } },
  { title: '登记状态', key: 'registrationScope', width: 120, resizable: true, render: registrationLabel },
  { title: '年代', key: 'eraSummary', width: 180, resizable: true, ellipsis: { tooltip: true }, render: ledgerEraText },
  { title: '类别', key: 'categoryName', width: 130, resizable: true, ellipsis: { tooltip: true }, render: (row: CollectionVo) => ledgerValue(row.categoryName) },
  { title: '质地', key: 'texture', width: 100, resizable: true, ellipsis: { tooltip: true }, render: (row: CollectionVo) => ledgerValue(row.texture) },
  { title: '数量', key: 'actualQuantity', width: 95, render: (row: CollectionVo) => quantityText(row) },
  { title: '级别', key: 'level', width: 110, resizable: true, render: (row: CollectionVo) => dictLedgerText('col_relic_level', row.level) },
  { title: '藏品状态', key: 'lifeStatus', width: 110, resizable: true, render: (row: CollectionVo) => dictLedgerText('col_life_status', row.lifeStatus) },
  { title: '库房库位', key: 'warehouseName', width: 180, resizable: true, ellipsis: { tooltip: true }, render: (row: CollectionVo) => ledgerValue(row.warehouseName) },
  { title: '来源', key: 'source', width: 110, resizable: true, render: (row: CollectionVo) => dictLedgerText('col_source', row.source) },
  { title: '正式登记时间', key: 'registeredAt', width: 180, resizable: true, render: (row: CollectionVo) => row.registeredAt ? formatDateTime(row.registeredAt) : registrationDateFallback(row) },
  { title: '原名', key: 'originalName', width: 160, resizable: true, ellipsis: { tooltip: true }, render: (row: CollectionVo) => ledgerValue(row.originalName) },
  { resizable: true,
    title: '具体尺寸',
    key: 'specificDimensions',
    width: 190,
    ellipsis: { tooltip: true },
    render: (row: CollectionVo) => dimensionText(row.dimensions),
  },
  { title: '质量范围', key: 'weightRange', width: 120, resizable: true, render: (row: CollectionVo) => ledgerValue(row.weightRange) },
  { title: '具体质量', key: 'weightValue', width: 100, render: (row: CollectionVo) => ledgerValue(weightParts(row.weight, row.weightUnit).value) },
  { title: '质量单位', key: 'weightUnit', width: 80, render: (row: CollectionVo) => ledgerValue(weightParts(row.weight, row.weightUnit).unit) },
  { ellipsis: { tooltip: true }, resizable: true,
    title: '完残程度',
    key: 'completeness',
    width: 110,
    render: (row: CollectionVo) => dictLedgerText('col_completeness', row.completeness),
  },
  { resizable: true,
    title: '完残状况',
    key: 'completeStatus',
    width: 180,
    ellipsis: { tooltip: true },
    render: (row: CollectionVo) => ledgerValue(row.completeStatus),
  },
  { ellipsis: { tooltip: true }, resizable: true,
    title: '保存状态',
    key: 'preserveState',
    width: 120,
    render: (row: CollectionVo) => dictLedgerText('col_preserve', row.preserveState),
  },
  { ellipsis: { tooltip: true }, resizable: true,
    title: '取得时间范围',
    key: 'acquireDateRange',
    width: 145,
    render: (row: CollectionVo) =>
      ledgerValue(
        row.acquireDateRange || (row.acquireDate != null ? formatLocalDateValue(row.acquireDate) : undefined),
      ),
  },
  {
    title: '取得年度',
    key: 'acquireYear',
    width: 100,
    render: (row: CollectionVo) =>
      ledgerValue(
        row.acquireYear || (row.acquireDate != null ? formatLocalDateValue(row.acquireDate, 'YYYY') : undefined),
      ),
  },
  { ellipsis: { tooltip: true }, resizable: true, title: '著者', key: 'author', width: 110, render: (row: CollectionVo) => ledgerValue(row.author) },
  { ellipsis: { tooltip: true }, resizable: true, title: '版本', key: 'edition', width: 110, render: (row: CollectionVo) => ledgerValue(row.edition) },
  { ellipsis: { tooltip: true }, resizable: true, title: '存卷', key: 'volumeNo', width: 100, render: (row: CollectionVo) => ledgerValue(row.volumeNo) },
  { resizable: true,
    title: '分类号',
    key: 'classNo',
    width: 130,
    ellipsis: { tooltip: true },
    render: (row: CollectionVo) => ledgerValue(row.classNo),
  },
];

const actionColumn = {
  title: '操作',
  key: 'operate',
  type: 'operate',
  width: 245,
  fixed: 'right' as const,
  render: (row: CollectionVo) => {
    const buttons = [
      h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => onDetail(row) }, () => '详情'),
    ];
    if (canEditCollection(row)) {
      buttons.push(
        h(
          NButton,
          { text: true, type: 'primary', size: 'small', style: 'margin-left:10px', onClick: () => onEdit(row) },
          () => '编辑',
        ),
      );
    }
    if (authStore.hasPermission('ADMIN,COLLECTION_ADMIN,COLLECTION_LEDGER_CORRECT,COLLECTION_LEDGER_CREATE')) {
      buttons.push(h(NButton, { text: true, size: 'small', style: 'margin-left:10px', onClick: () => onRfid(row) },
        () => row.rfidTag ? 'RFID解绑' : '绑定RFID'));
    }
    buttons.push(
      h(
        NDropdown,
        {
          trigger: 'click',
          placement: 'bottom-end',
          options: collectionActionOptions(row),
          onSelect: (key: string) => handleCollectionAction(key, row),
        },
        () => h(NButton, { text: true, size: 'small', style: 'margin-left:10px' }, () => '更多'),
      ),
    );
    return buttons;
  },
};

/** 参考表格的标题区统一居中，数据单元格仍按各列自身规则对齐。 */
function centerLedgerHeaderTitles(items: any[]): any[] {
  return items.map((column) => ({
    ...column,
    titleAlign: 'center',
    ...(column.children?.length ? { children: centerLedgerHeaderTitles(column.children) } : {}),
  }));
}

function transformVisibleLedgerColumns(items: any[]) {
  return centerLedgerHeaderTitles(items);
}

const columns = [...dataColumns, actionColumn];

const flowedLifeStatuses = new Set([
  'PRE_OUT',
  'IN_USE',
  'RESTORING',
  'ON_LOAN',
  'MISSING',
  'DEREGISTERED',
  'RETURN_HOLD',
]);

function canEditCollection(row: CollectionVo) {
  return Boolean(
    row.id &&
      !row.published &&
      row.catalogStatus !== 'APPROVED' &&
      !flowedLifeStatuses.has(String(row.lifeStatus || '')),
  );
}

function collectionActionOptions(row: CollectionVo) {
  return [
    { label: '复制新建', key: 'copy' },
    { label: '打印评审鉴定表', key: 'appraisal-print' },
    { label: '导出评审鉴定表', key: 'appraisal-export' },
    { label: '关键字段更正', key: 'correct' },
    { label: '提用', key: 'requisition', disabled: row.lifeStatus !== 'IN_STORE' || row.inventoryHold },
    { label: publishActionText(row), key: 'publish', disabled: !canManageDisclosure(row) },
    { label: '总账留痕', key: 'ledger' },
    { label: '申请退出馆藏', key: 'dereg', disabled: row.lifeStatus === 'DEREGISTERED' },
  ];
}

function handleCollectionAction(key: string, row: CollectionVo) {
  switch (key) {
    case 'copy':
      return onCopy(row);
    case 'appraisal-print':
      return onAppraisalForm(row, 'print');
    case 'appraisal-export':
      return onAppraisalForm(row, 'export');
    case 'rfid':
      return onRfid(row);
    case 'correct':
      return onCorrect(row);
    case 'requisition':
      return onRequisition(row);
    case 'publish':
      return onPublish(row);
    case 'ledger':
      return onLedger(row);
    case 'dereg':
      return onDereg(row);
  }
}

/** 复制新建：以母本预填登记表单（清编号/RFID/审核与流程状态），加快同批相近藏品录入。 */
function onCopy(row: CollectionVo) {
  const seed: CollectionVo = {
    ...row,
    id: undefined,
    totalRegNo: undefined,
    rfidTag: undefined,
    entryVoucherNo: undefined,
    receiptCompletedAt: undefined,
    deregVoucherNo: undefined,
    catalogStatus: undefined,
    catalogAuditOpinion: undefined,
    lifeStatus: undefined,
    registeredAt: undefined, historicalConfirmedAt: undefined, registered: false,
    published: undefined,
    publishedAt: undefined,
    createTime: undefined,
    createBy: undefined,
  };
  formRef.value?.open(seed);
}

/** 评审鉴定表：按详情接口取完整字段（列表行可能缺登记细项），不含估价。 */
const appraisalFormLoading = ref(false);
async function onAppraisalForm(row: CollectionVo, mode: 'print' | 'export') {
  if (!row.id || appraisalFormLoading.value) return;
  appraisalFormLoading.value = true;
  try {
    const resp: any = await collectionApi.get(row.id);
    const detail: CollectionVo | undefined = resp?.data;
    if (!detail || detail.id !== row.id) {
      message.warning('藏品详情读取结果不一致，请刷新后重试');
      return;
    }
    const data = await loadCollectionAppraisalFormData(detail);
    if (mode === 'print') printCollectionAppraisalForm(data);
    else exportCollectionAppraisalForm(data);
  } catch {
    message.warning('评审鉴定表生成失败，请稍后重试');
  } finally {
    appraisalFormLoading.value = false;
  }
}

function onRfid(row: CollectionVo) {
  if (row.rfidTag) {
    dialog.warning({
      title: '解绑RFID标签',
      content: `确认解绑「${row.name}」的标签 ${row.rfidTag}？解绑将写入总账留痕。`,
      positiveText: '解绑',
      negativeText: '取消',
      onPositiveClick: async () => {
        await collectionApi.unbindRfid(row.id!);
        message.success('已解绑');
        handleRefresh();
      },
    });
    return;
  }
  const tag = ref('');
  dialog.create({
    title: `绑定RFID标签：${row.name || ''}`,
    content: () =>
      h(NInput, {
        value: tag.value,
        placeholder: '扫描或输入标签号（馆内唯一）',
        'onUpdate:value': (v: string) => (tag.value = v),
      }),
    positiveText: '绑定',
    negativeText: '取消',
    onPositiveClick: async () => {
      if (!tag.value.trim()) {
        message.warning('请输入标签号');
        return false;
      }
      await collectionApi.bindRfid(row.id!, tag.value.trim());
      message.success('已绑定，已写入总账留痕');
      handleRefresh();
      return true;
    },
  });
}

const selectedFileSource = ref<{ bizId?: string; fileId?: string } | null>(null);
const selectedSourceRecord = computed(() => selectedFileSource.value
  ? fileBusinessSource(selectedFileSource.value, trackingImports.value) : null);
const selectedSourceEvents = computed(() => selectedSourceRecord.value
  ? trackingEvents.value.filter(event => event.importId === selectedSourceRecord.value?.id) : []);
const fileSourceVisible = computed({
  get: () => !!selectedSourceRecord.value,
  set: (show: boolean) => { if (!show) selectedFileSource.value = null; },
});
function renderFileBusinessSource(row: { bizId?: string; fileId?: string }) {
  if (!row.bizId) return '藏品资料维护';
  if (detailAuxLoading.value) return '正在核对来源';
  if (detailAuxErrors.value.includes('管理记录')) return '来源暂不可用，请重试';
  const source = fileBusinessSource(row, trackingImports.value);
  if (!source) return '历史业务待核实';
  return h(NButton, {
    text: true, type: 'primary', size: 'small', title: fileBusinessSourceLabel(source),
    style: { maxWidth: '100%', whiteSpace: 'normal', textAlign: 'left', height: 'auto' },
    onClick: () => { selectedFileSource.value = { bizId: row.bizId, fileId: row.fileId }; },
  }, () => fileBusinessSourceLabel(source));
}

const mediaColumns = [
  { ellipsis: { tooltip: true }, resizable: true,
    title: '预览',
    key: 'preview',
    width: 74,
    render: (r: CollectionMediaVo) =>
      r.fileId && isVideoMedia(r)
        ? // 视频不能按图片取缩略图（会一直转圈），显示视频图标，原件可在「文件」列下载。
          h(NIcon, { size: 28, depth: 3, title: '视频', 'aria-label': '视频' }, { default: () => h(VideocamOutline) })
        : r.fileId
        ? h(CurrentFileImage, {
            src: fileUrl(r.fileId), width: 52, height: 52, revision: mediaReadEpoch.value,
            alt: r.title || current.value?.name || '藏品影像', hideReload: true,
            style: { width: '52px' },
          })
        : '',
  },
  { ellipsis: { tooltip: true }, resizable: true,
    title: '类型',
    key: 'mediaType',
    width: 110,
    render: (r: CollectionMediaVo) => dictText('col_media_type', r.mediaType),
  },
  { ellipsis: { tooltip: true }, resizable: true,
    title: '目录',
    key: 'folderId',
    width: 120,
    render: (r: CollectionMediaVo) => mediaFolderPath(mediaFolderList.value, r.folderId) || '未归档',
  },
  { resizable: true, width: 160, title: '标题', key: 'title', minWidth: 160, ellipsis: { tooltip: true } },
  { resizable: true, width: 110, title: '文件', key: 'fileId', render: (row: { fileId?: string }) => row.fileId ? h(HFileList, { value: row.fileId, showDownloadBtn: true, downloadOnly: true }) : '未关联文件' },
  { title: '来源业务', key: 'bizId', width: 240, ellipsis: { tooltip: true }, render: renderFileBusinessSource },
  { title: '关联时间', key: 'createTime', width: 170, render: (row: { createTime?: string }) => row.createTime ? formatDateTime(row.createTime) : '--' },
  { title: '采集记录', key: 'capture', width: 220, render: (r: CollectionMediaVo) => [r.capture?.view, r.capture?.sequence, r.capture?.photographer, r.capture?.photographedOn ? formatLocalDateValue(r.capture.photographedOn) : '拍摄日期待核实'].filter(v => v != null && v !== '').join(' · ') },
  { title: '封面', key: 'isCover', width: 70, render: (r: CollectionMediaVo) => dictText('col_presence', r.isCover) },
  {
    title: '操作',
    key: 'action',
    width: 80,
    render: (row: CollectionMediaVo) =>
      h(NSpace, { size: 8 }, () => [h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => editMedia(row) }, () => '补充信息'), h(NButton, { text: true, type: 'error', size: 'small', onClick: () => deleteMedia(row) }, () => '删除')]),
  },
];

const attachmentColumns = [
  { ellipsis: { tooltip: true }, resizable: true,
    title: '类型',
    key: 'attachmentType',
    width: 130,
    render: (r: CollectionAttachmentVo) => dictText('col_attachment_type', r.attachmentType),
  },
  { resizable: true, width: 160, title: '名称', key: 'name', minWidth: 160, ellipsis: { tooltip: true } },
  { resizable: true, width: 110, title: '文件', key: 'fileId', render: (row: { fileId?: string }) => row.fileId ? h(HFileList, { value: row.fileId, showDownloadBtn: true, downloadOnly: true }) : '未关联文件' },
  { title: '来源业务', key: 'bizId', width: 240, ellipsis: { tooltip: true }, render: renderFileBusinessSource },
  { title: '关联时间', key: 'createTime', width: 170, render: (row: { createTime?: string }) => row.createTime ? formatDateTime(row.createTime) : '--' },
  { resizable: true, width: 160, title: '备注', key: 'remark', minWidth: 160, ellipsis: { tooltip: true } },
  {
    title: '操作',
    key: 'action',
    width: 80,
    render: (row: CollectionAttachmentVo) =>
      h(NButton, { text: true, type: 'error', size: 'small', onClick: () => deleteAttachment(row) }, () => '删除'),
  },
];

const setColumns = [
  { ellipsis: { tooltip: true }, resizable: true,
    title: '关系',
    key: 'relation',
    width: 90,
    render: (r: CollectionSetVo) => dictText('col_set_relation', r.relation),
  },
  { resizable: true, title: '分件号', key: 'partNo', width: 110, ellipsis: { tooltip: true } },
  { resizable: true, width: 150, title: '分件名称', key: 'partName', minWidth: 150, ellipsis: { tooltip: true } },
  { title: '数量', key: 'quantity', width: 80 },
  { title: '单位', key: 'unit', width: 70 },
  { resizable: true, width: 180, title: '关联藏品ID', key: 'childCollectionId', minWidth: 180, ellipsis: { tooltip: true } },
  {
    title: '操作',
    key: 'action',
    width: 130,
    render: (row: CollectionSetVo) =>
      h(NSpace, { size: 8 }, () => [
        h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => openSet(row) }, () => '编辑'),
        h(NButton, { text: true, type: 'error', size: 'small', onClick: () => deleteSet(row) }, () => '删除'),
      ]),
  },
];

const movementColumns = [
  { resizable: true, title: '单号', key: 'documentNo', width: 130, ellipsis: { tooltip: true } },
  { ellipsis: { tooltip: true }, resizable: true, title: '类型', key: 'type', width: 80, render: (r: MovementVo) => dictText('col_move_type', r.type) },
  { ellipsis: { tooltip: true }, resizable: true, title: '事由', key: 'reason', width: 110, render: (r: MovementVo) => dictText('col_move_reason', r.reason) },
  { resizable: true, title: '部门', key: 'department', width: 120, ellipsis: { tooltip: true } },
  { ellipsis: { tooltip: true }, resizable: true,
    title: '经办人',
    key: 'operator',
    width: 120,
    render: (r: MovementVo) => h(UserNameText, { id: r.operator, name: r.operatorName }),
  },
  { ellipsis: { tooltip: true }, resizable: true,
    title: '复核人',
    key: 'reviewer',
    width: 120,
    render: (r: MovementVo) => h(UserNameText, { id: r.reviewer, name: r.reviewerName }),
  },
  {
    title: '时间',
    key: 'operatedAt',
    width: 200,
    render: (r: MovementVo) => (r.operatedAt ? formatDateTime(r.operatedAt) : ''),
  },
  {
    title: '操作',
    key: 'action',
    width: 80,
    render: (row: MovementVo) =>
      h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => onMovementPrint(row) }, () => '打印'),
  },
];

const trackingImportColumns = [
  { ellipsis: { tooltip: true }, resizable: true,
    title: '文档类型',
    key: 'documentType',
    width: 190,
    render: (row: CollectionWordImportRecordVo) =>
      row.documentType === 'REGISTRATION' ? '文物藏品登记表' : '动态跟踪记录汇总表',
  },
  { resizable: true, width: 220, title: '原文件', key: 'sourceFileName', minWidth: 220, ellipsis: { tooltip: true } },
  { title: '起始年度', key: 'startYear', width: 100 },
  { title: '结束年度', key: 'endYear', width: 100 },
  {
    title: '导入时间',
    key: 'createTime',
    width: 180,
    render: (row: CollectionWordImportRecordVo) => (row.createTime ? formatDateTime(row.createTime) : '--'),
  },
];

const trackingEventColumns = [
  { ellipsis: { tooltip: true }, resizable: true,
    title: '类别',
    key: 'eventType',
    width: 160,
    render: (row: CollectionTrackingEventVo) => trackingEventTypeText(row.eventType),
  },
  { ellipsis: { tooltip: true }, resizable: true, title: '栏目', key: 'title', width: 160 },
  { resizable: true, width: 360, title: '内容', key: 'content', minWidth: 360, ellipsis: { tooltip: true } },
  {
    title: '发生时间',
    key: 'occurredAt',
    width: 180,
    render: (row: CollectionTrackingEventVo) => (row.occurredAt ? formatDateTime(row.occurredAt) : '--'),
  },
];

function trackingEventTypeText(type?: string) {
  return (
    (
      {
        EXTRACT_RETURN: '提取退还',
        OBSERVATION: '观察监测',
        RESTORATION: '保护修复',
        DIGITIZATION: '数字化',
        APPRAISAL_CHANGE: '鉴定变更',
        RESEARCH_USE: '研究利用',
        INCIDENT: '事故记录',
        DEREGISTRATION: '退出馆藏记录',
        NOTE: '备注',
        REGISTRATION_DETAIL: '登记扩展',
      } as Record<string, string>
    )[type || ''] ||
    type ||
    '--'
  );
}

const ledgerColumns = [
  { ellipsis: { tooltip: true }, resizable: true, title: '动作', key: 'action', width: 100, render: (r: LedgerLogVo) => dictText('col_ledger_action', r.action) },
  { ellipsis: { tooltip: true }, resizable: true, title: '字段', key: 'field', width: 120, render: (r: LedgerLogVo) => ledgerFieldLabel(r.field) },
  { resizable: true, width: 260, title: '原值', key: 'before', render: (r: LedgerLogVo) => h(CollectionLedgerLogValue, { log: r, side: 'before', compact: true }) },
  { resizable: true, width: 260, title: '新值', key: 'after', render: (r: LedgerLogVo) => h(CollectionLedgerLogValue, { log: r, side: 'after', compact: true }) },
  { ellipsis: { tooltip: true }, resizable: true, title: '事由', key: 'reason', width: 160 },
  { resizable: true, title: '关联业务单ID', key: 'refBizId', width: 160, ellipsis: { tooltip: true } },
  { ellipsis: { tooltip: true }, resizable: true,
    title: '操作人',
    key: 'createBy',
    width: 120,
    render: (r: LedgerLogVo) => h(UserNameText, { id: r.createBy, name: r.createByName }),
  },
  {
    title: '时间',
    key: 'createTime',
    width: 200,
    render: (r: LedgerLogVo) => (r.createTime ? formatDateTime(r.createTime) : ''),
  },
];

function handleRefresh() {
  tableRef.value?.handleRefresh();
}

function openRegistrationWordImport() {
  wordImportRef.value?.open({ expectedDocumentType: 'REGISTRATION' });
}

function openDynamicTrackingWordImport() {
  wordImportRef.value?.open({ expectedDocumentType: 'DYNAMIC_TRACKING' });
}

function openDefaultCollectionImport() {
  if (canImportRegistrationWord.value) {
    openRegistrationWordImport();
    return;
  }
  openDynamicTrackingWordImport();
}

function handleCollectionImportSelect(key: string) {
  if (key === 'registered-migration') { migrationRef.value?.open(); return; }
  if (key === 'dynamic-tracking-word') {
    openDynamicTrackingWordImport();
    return;
  }
  if (key === 'batch-collection') {
    void openImport();
    return;
  }
  if (key === 'batch-file') openBatchFileImport();
}

function handleCollectionExportSelect(key: string) {
  if (key === 'exchange') { exchangeRef.value?.open({ ...queryParams }); return; }
  if (key === 'survey') {
    void exportSurvey();
    return;
  }
  if (key === 'classification-ledger') printClassificationLedger();
}

function exportCurrentExcel() {
  tableRef.value?.handleExport();
}

function openTrackingWordImport() {
  if (!current.value?.id) return;
  wordImportRef.value?.open({
    expectedCollectionId: current.value.id,
    expectedDocumentType: 'DYNAMIC_TRACKING',
  });
}

async function handleWordImportCommitted(collectionId?: string) {
  handleRefresh();
  if (!collectionId || current.value?.id !== collectionId) return;
  const version = detailRequestVersion;
  await Promise.all([
    loadTrackingHistory(collectionId, version),
    loadMovements(collectionId, version),
    loadFiles(collectionId, version),
  ]);
}

async function exportSurvey() {
  exporting.value = true;
  try {
    const status: any = await collectionApi.surveyStatus({ ...queryParams });
    if (status.data?.excluded?.length)
      message.warning(`已排除 ${status.data.excluded.length} 条待登记、已退出或历史待核实记录；原账继续保留`);
    const resp: any = await collectionApi.surveyExport({ ...queryParams });
    const rows: CollectionSurveyExportVo[] = resp?.data?.list ?? resp?.data ?? [];
    await downloadSurveyCsv(rows);
    message.success(`已导出 ${rows.length} 条`);
  } finally {
    exporting.value = false;
  }
}

type ClassificationLedgerSnapshot = { html: string; title: string; style: string };
const classificationLedgerPreview = ref<ClassificationLedgerSnapshot | null>(null);
const classificationLedgerChecking = ref(false);
const classificationLedgerPreviewDocument = computed(() => {
  const snapshot = classificationLedgerPreview.value;
  if (!snapshot) return '';
  return `<html lang="zh-CN"><head><meta charset="utf-8"><title>${escapeLedger(snapshot.title)}</title><style>${snapshot.style}@media screen{body{background:#f5f5f5;margin:0;padding:16px}.ledger-print{background:white;max-width:820px;margin:0 auto 18px;box-shadow:0 1px 6px #ddd}}</style></head><body>${snapshot.html}</body></html>`;
});
function handleClassificationLedgerPreviewShow(show: boolean) {
  if (!show) classificationLedgerPreview.value = null;
}
async function confirmClassificationLedgerPrint() {
  const reviewed = classificationLedgerPreview.value;
  if (!reviewed || classificationLedgerChecking.value) return;
  classificationLedgerChecking.value = true;
  try {
    const current = await buildClassificationLedgerSnapshot(false);
    if (classificationLedgerPreview.value !== reviewed) return;
    if (!current) {
      classificationLedgerPreview.value = null;
      return;
    }
    if (current.html !== reviewed.html || current.title !== reviewed.title || current.style !== reviewed.style) {
      classificationLedgerPreview.value = current;
      message.warning('分类账数据已变化，请核对更新后的预览再打印');
      return;
    }
    htmlPrint(reviewed.html, reviewed.title, reviewed.style);
  } catch {
    if (classificationLedgerPreview.value === reviewed)
      message.warning('分类账重新核对失败，请稍后重试');
  } finally {
    classificationLedgerChecking.value = false;
  }
}

async function printClassificationLedger() {
  try {
    const snapshot = await buildClassificationLedgerSnapshot(true);
    if (snapshot) classificationLedgerPreview.value = snapshot;
  } catch {
    message.warning('分类账读取失败，请稍后重试');
  }
}

async function buildClassificationLedgerSnapshot(showExclusionWarnings: boolean): Promise<ClassificationLedgerSnapshot | null> {
  const requestedCategory = categoryOptions.value.find((item) => item.value === queryParams.categoryId)?.label || '全部分类';
  const resp: any = await collectionApi.page({ ...queryParams, pageNum: 1, pageSize: 0 });
  const records: CollectionVo[] = resp?.data?.list ?? resp?.data ?? [];
  const rows = records.filter(isFormalCollection);
  if (showExclusionWarnings && rows.length !== records.length)
    message.warning(
      `分类账仅打印正式在册记录，已排除 ${records.length - rows.length} 条待登记、已退出或历史待核实记录；原账继续保留`,
    );
  if (!rows.length) {
    message.warning('当前筛选范围没有可打印的正式在册记录');
    return null;
  }
  const missingClassNo = rows.filter((row) => !row.classNo?.trim()).length;
  if (showExclusionWarnings && missingClassNo)
    message.warning(`有 ${missingClassNo} 条正式记录缺少分类号；账页将标为待核稿，核实订正后再作为正式分类账使用`);
  const groups = new Map<string, Map<string, CollectionVo[]>>();
  for (const row of rows) {
    if (!row.orgId?.trim()) {
      message.warning('记录缺少收藏单位，请核对后再打印');
      return null;
    }
    const museum = groups.get(row.orgId) || new Map<string, CollectionVo[]>();
    const categoryId = row.categoryId?.trim() || '';
    const group = museum.get(categoryId) || [];
    group.push(row);
    museum.set(categoryId, group);
    groups.set(row.orgId, museum);
  }
  const orgNames = new Map<string, string>();
  try {
    await Promise.all([...groups.keys()].map(async (id) => {
      const response = await orgApi.get(id);
      const org = response?.data;
      if (org?.id !== id || typeof org?.name !== 'string' || !org.name.trim())
        throw new Error('Invalid collection organization');
      orgNames.set(id, org.name);
    }));
  } catch {
    message.warning('收藏单位读取失败，请重新核对后打印');
    return null;
  }
  const dictionaries = await Promise.all([
    fetchPrintDictType('col_texture', true),
    fetchPrintDictType('col_relic_level', true),
    fetchPrintDictType('col_source', true),
    fetchPrintDictType('col_completeness', true),
    fetchPrintDictType('col_preserve', true),
  ]).catch(() => []);
  if (dictionaries.length !== 5 || dictionaries.some((response) => !Array.isArray(response?.data))) {
    message.warning('打印字典读取失败，请重试');
    return null;
  }
  const printDictLabel = (index: number, value: unknown) => {
    if (value === undefined || value === null || String(value).trim() === '') return '--';
    const options = dictionaries[index].data as { value: unknown; label: unknown }[];
    return String(value).split(',').map((code) => {
      const option = options.find((item) => String(item.value) === code);
      return option?.label == null ? code : String(option.label);
    }).join(',');
  };
  const pages = [...groups].flatMap(([orgId, categories]) => [...categories].map(([categoryId, categoryRows]) => {
    const categoryName = !categoryId ? '未分类（待核）' :
      categoryOptions.value.find((item) => item.value === categoryId)?.label || categoryRows[0].categoryName?.trim() || categoryId;
    const pendingNumbers = categoryRows.filter((row) => !row.classNo?.trim()).length;
    const body = categoryRows
    .map(
      (row, index) =>
        `<tbody><tr><td>${index + 1}</td><td>${escapeLedger(row.registeredAt ? formatDateTime(row.registeredAt).slice(0,10) : registrationDateFallback(row))}</td><td>${escapeLedger(row.totalRegNo)}</td><td>${escapeLedger(row.classNo?.trim() || '待核')}</td><td>${escapeLedger(row.name)}</td><td>${escapeLedger(row.era)}</td><td>${escapeLedger(printDictLabel(0, row.texture))}</td><td>${escapeLedger(printDictLabel(1, row.level))}</td><td>${escapeLedger(row.quantity)} ${escapeLedger(row.unit)}</td><td>${escapeLedger(row.warehouseName || row.warehouseId)}</td></tr>
        <tr><th>著录信息</th><td colspan="9">尺寸：${escapeLedger(standardDimensions(row))}　质量：${escapeLedger(standardMass(row.weight,row.weightUnit) || row.registrationDetails?.weightUnknownReason || '待核实')}（附件质量口径：${row.registrationDetails?.weightExcludesAccessories === true ? '已排除' : '待核实'}）　实际数量：${escapeLedger(row.actualQuantity ?? '待核实')} ${escapeLedger(row.actualQuantityUnit || '')}　来源：${escapeLedger(printDictLabel(2,row.source))}　完残：${escapeLedger(printDictLabel(3,row.completeness))} ${escapeLedger(row.completeStatus || '待核实')}　保存状态：${escapeLedger(printDictLabel(4,row.preserveState))}</td></tr>
        <tr><th>保管及备注</th><td colspan="9">库房库位标识：${escapeLedger(row.warehouseId)}　入馆凭证：${escapeLedger(row.entryVoucherNo)}　备注：${escapeLedger(row.remark)}</td></tr></tbody>`,
    )
    .join('');
    return `<article class="ledger-print"><h1>藏品分类账${pendingNumbers ? '（待核稿）' : ''}</h1><p>收藏单位：${escapeLedger(orgNames.get(orgId))}（${escapeLedger(orgId)}）</p><p>分类：${escapeLedger(categoryName)}（${escapeLedger(categoryId || '--')}）　记录数：${categoryRows.length}</p>${pendingNumbers ? `<p class="ledger-warning">${pendingNumbers} 条记录缺少分类号，核实订正后方可作为正式分类账使用。</p>` : ''}<table><thead><tr><th>序号</th><th>登记日期</th><th>总登记号</th><th>分类号</th><th>名称</th><th>年代</th><th>质地</th><th>级别</th><th>数量</th><th>库位</th></tr></thead>${body}</table><div class="signatures">制表人：________　复核人：________　日期：________</div></article>`;
  })).join('');
  return {
    html: pages,
    title: `${requestedCategory}藏品分类账`,
    style: `@page{size:A4 landscape;margin:12mm}.ledger-print{padding:18px;font-family:serif}
.ledger-print + .ledger-print{break-before:page;page-break-before:always}
.ledger-print h1{text-align:center}
.ledger-print .ledger-warning{font-weight:bold}
.ledger-print table{width:100%;border-collapse:collapse}
.ledger-print th,.ledger-print td{border:1px solid #333;padding:6px;font-size:11px;overflow-wrap:anywhere}
.signatures{margin-top:30px;text-align:right}
@media print{
  html,body{overflow:visible;break-inside:auto;page-break-inside:auto;-webkit-region-break-inside:auto}
  .ledger-print,.ledger-print table{break-inside:auto;page-break-inside:auto}
  .ledger-print thead{display:table-header-group}
  .ledger-print tbody,.ledger-print tr{break-inside:avoid;page-break-inside:avoid}
}`,
  };
}

function escapeLedger(value: unknown) {
  return String(value ?? '--').replace(
    /[&<>"']/g,
    (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char] || char,
  );
}

function onAdd() {
  formRef.value?.open();
}

const collectionCreateOptions = [
  { label: '单件藏品', key: 'single' },
  { label: '成套藏品（含分件）', key: 'set' },
];

function handleCreateEntrySelect(key: string) {
  if (key === 'set') {
    openSetEntry();
    return;
  }
  onAdd();
}

function openSetEntry() {
  setEntryRef.value?.open();
}
function onEdit(row: CollectionVo) {
  if (!canEditCollection(row)) {
    message.warning('该藏品已完成编目审批或已进入业务流转，请通过关键字段更正维护。');
    return;
  }
  formRef.value?.open({ ...row });
}
async function onDetail(row: CollectionVo) {
  if (!row.id) return;
  detailVisible.value = false;
  const id = row.id;
  const version = ++detailRequestVersion;
  const identity = `${authStore.userInfo?.id}:${authStore.identityRevision}`;
  try {
    const resp: any = await collectionApi.get(id);
    if (version !== detailRequestVersion || identity !== `${authStore.userInfo?.id}:${authStore.identityRevision}`) return;
    resetDetailAuxData();
    current.value = resp?.data ?? row;
    detailTab.value = 'overview';
    detailVisible.value = true;
    void loadDetailAuxData(id, version);
  } catch {
    if (version === detailRequestVersion) message.error('藏品基本信息加载失败，请重试');
  }
}

watch(() => route.query.collectionId, value => { const id = Array.isArray(value) ? value[0] : value; if (typeof id === 'string' && id.trim()) void onDetail({ id: id.trim() }); }, { immediate: true });
watch(detailVisible, show => { if (!show) { selectedFileSource.value = null; detailRequestVersion++; mediaPreviewShow.value = false; mediaReadEpoch.value++; } }, { flush: 'sync' });
function leaveDetailImages() { detailRequestVersion++; detailVisible.value = false; mediaPreviewShow.value = false; }
onDeactivated(leaveDetailImages); onBeforeUnmount(leaveDetailImages);
onActivated(() => { const id = route.query.collectionId; if (typeof id === 'string' && id && !detailVisible.value) void onDetail({ id }); });
watch(() => `${authStore.userInfo?.id}:${authStore.identityRevision}:${authStore.identitySyncing}`, () => { leaveDetailImages(); resetDetailAuxData(); current.value = null; }, { flush: 'sync' });

function resetDetailAuxData() {
  selectedFileSource.value = null;
  detailCatalog.value = null;
  ledgerLogs.value = [];
  mediaPreviewShow.value = false; mediaReadEpoch.value++;
  mediaList.value = [];
  attachmentList.value = [];
  mediaFolderList.value = [];
  setList.value = [];
  movementList.value = [];
  trackingHistory.value = { imports: [], events: [] };
  selectedMediaKey.value = null;
  detailAuxErrors.value = [];
}

async function loadDetailAuxData(id: string, version = detailRequestVersion) {
  detailAuxLoading.value = true;
  detailAuxErrors.value = [];
  const tasks = [
    { label: '总账与迁移依据', run: async () => {
      const response: any = await collectionApi.ledgerLogs(id);
      const rows = response?.data?.list ?? response?.data;
      if (!Array.isArray(rows)) throw new Error('总账记录格式不完整');
      if (version === detailRequestVersion && current.value?.id === id) ledgerLogs.value = rows;
    } },
    { label: '编目正文与版本入口', run: async () => {
      const row = readCatalog(await catalogApi.get(id), id);
      if (version === detailRequestVersion && current.value?.id === id) detailCatalog.value = row;
    } },
    { label: '影像和附件', run: () => loadFiles(id, version) },
    { label: '套件分件', run: () => loadSets(id, version) },
    { label: '出入库记录', run: () => loadMovements(id, version) },
    { label: '管理记录', run: () => loadTrackingHistory(id, version) },
  ];
  const results = await Promise.allSettled(tasks.map((task) => task.run()));
  if (version !== detailRequestVersion) return;
  detailAuxErrors.value = results.flatMap((result, index) =>
    result.status === 'rejected' ? [tasks[index].label] : [],
  );
  detailAuxLoading.value = false;
}

async function refreshMigrationDetail(id: string) {
  const version = detailRequestVersion;
  try {
    const response: any = await collectionApi.get(id);
    if (version !== detailRequestVersion || current.value?.id !== id) return;
    current.value = response.data;
    await loadDetailAuxData(id, version);
    tableRef.value?.handleRefresh();
  } catch { if (version === detailRequestVersion) message.warning('核验操作已完成，详情刷新失败，请重新打开详情'); }
}
function retryDetailAuxData() {
  if (current.value?.id) void loadDetailAuxData(current.value.id);
}
async function onLedger(row: CollectionVo) {
  const resp: any = await collectionApi.ledgerLogs(row.id!);
  ledgerLogs.value = resp?.data?.list ?? resp?.data ?? [];
  ledgerVisible.value = true;
}

async function viewNatureCollection(id: string) {
  const response: any = await collectionApi.get(id);
  if (response?.data) onDetail(response.data);
}
function onCorrect(row: CollectionVo) {
  correctTarget.value = row;
  correctForm.field = 'name';
  correctForm.value = initialCorrectionValue(row, correctForm.field);
  correctForm.reason = '';
  correctVisible.value = true;
}

function onCorrectFieldChange(field: CollectionCorrectionField) {
  correctForm.field = field;
  correctForm.value = initialCorrectionValue(correctTarget.value, field);
}

async function submitCorrect() {
  const target = correctTarget.value;
  if (!target?.id) return;
  if (!(await validateFormSafely(correctFormRef.value))) return;
  const value = normalizeCorrectionValue(correctForm.field, correctForm.value);
  const reason = correctForm.reason.trim();
  if (!value) {
    message.warning('请填写更正值');
    return;
  }
  if (!reason) {
    message.warning('请填写更正事由');
    return;
  }
  const result: any = await collectionApi.correct(target.id, correctForm.field, value, reason);
  correctVisible.value = false;
  message.success('已建立信息变更草稿，请补充责任人和依据后提交审批');
  await router.push({ path: '/collection/change', query: { changeId: result.data } });
  tableRef.value?.handleRefresh();
  if (current.value?.id === target.id) {
    const resp: any = await collectionApi.get(target.id);
    current.value = resp?.data ?? current.value;
  }
}

async function onPublish(row: CollectionVo) {
  if (!row.id || !row.orgId) return;
  await disclosureRef.value?.open({ orgId: row.orgId, subject: 'RESOURCE', subjectId: row.id });
}

async function loadFiles(id: string, version = detailRequestVersion) {
  const [mediaResp, attachmentResp, folderResp]: any[] = await Promise.all([
    collectionApi.media(id),
    collectionApi.attachments(id),
    collectionApi.mediaFolders(id),
  ]);
  if (version !== detailRequestVersion) return;
  mediaList.value = mediaResp?.data?.list ?? mediaResp?.data ?? [];
  attachmentList.value = attachmentResp?.data?.list ?? attachmentResp?.data ?? [];
  mediaFolderList.value = folderResp?.data?.list ?? folderResp?.data ?? [];
  const selectedExists = mediaList.value.some((item) => mediaKey(item) === selectedMediaKey.value);
  if (!selectedExists) {
    const cover =
      mediaList.value.find((item) => item.isCover && item.fileId) ?? mediaList.value.find((item) => item.fileId);
    selectedMediaKey.value = cover ? mediaKey(cover) : null;
  }
}

async function loadSets(id: string, version = detailRequestVersion) {
  const resp: any = await collectionApi.sets(id);
  if (version !== detailRequestVersion) return;
  setList.value = resp?.data?.list ?? resp?.data ?? [];
}

async function loadMovements(id: string, version = detailRequestVersion) {
  const resp: any = await movementApi.byCollection(id);
  if (version !== detailRequestVersion) return;
  movementList.value = resp?.data?.list ?? resp?.data ?? [];
}

async function loadTrackingHistory(id: string, version = detailRequestVersion) {
  const resp: any = await collectionApi.wordImportHistory(id);
  if (version !== detailRequestVersion) return;
  const data = resp?.data;
  trackingHistory.value = {
    imports: Array.isArray(data?.imports) ? data.imports : [],
    events: Array.isArray(data?.events) ? data.events : [],
  };
}

const printVisible = ref(false);
const printData = ref<CollectionPrintVo | null>(null);
const printType = ref<string | undefined>();
const printId = ref<string | undefined>();
const printDocumentNo = ref<string | undefined>();
const printTitle = computed(() => (printType.value === COLLECTION_ARCHIVE_PRINT_TYPE ? '藏品登记表' : undefined));
async function onMovementPrint(row: MovementVo) {
  if (!row.id) return;
  const resp: any = await movementApi.print(row.id);
  printData.value = resp?.data ?? null;
  printType.value = undefined;
  printId.value = undefined;
  printDocumentNo.value = undefined;
  printVisible.value = true;
}

function printArchive() {
  if (!current.value?.id) return;
  printData.value = null;
  printType.value = COLLECTION_ARCHIVE_PRINT_TYPE;
  printId.value = current.value.id;
  printDocumentNo.value = current.value.totalRegNo || current.value.id;
  printVisible.value = true;
}

const photoViewOptions = [{ value: 'A', label: 'A 正视图' }, { value: 'B', label: 'B 俯视图' }, { value: 'C', label: 'C 侧视图' }, { value: 'D', label: 'D 全景图' }, { value: 'E', label: 'E 局部图' }, { value: 'F', label: 'F 底部图' }];
function setPhotoView(view: string | null) {
  if (view) mediaForm.mediaType = ({ A: 'FRONT', B: 'TOP', C: 'SIDE', D: 'PANORAMIC', E: 'DETAIL', F: 'BOTTOM' } as Record<string, string>)[view];
}
const mediaVisible = ref(false);
const mediaForm = reactive<CollectionMediaVo>({});
function editMedia(row: CollectionMediaVo) {
  Object.keys(mediaForm).forEach(key => delete (mediaForm as any)[key]);
  Object.assign(mediaForm, cloneDeep(row), { capture: cloneDeep(row.capture || {}) });
  mediaVisible.value = true;
}
function openMedia() {
  Object.assign(mediaForm, {
    id: undefined,
    fileId: undefined,
    folderId: undefined,
    mediaType: 'FRONT',
    capture: {},
    title: undefined,
    isCover: false,
  });
  mediaVisible.value = true;
}
function openMediaFolder() {
  mediaFolderName.value = '';
  mediaFolderParentId.value = null;
  mediaFolderVisible.value = true;
  syncMediaFolderExpandedKeys();
}
async function saveMediaFolder() {
  if (!current.value?.id || !mediaFolderName.value.trim()) {
    message.warning('请填写目录名称');
    return;
  }
  const parentId = mediaFolderParentId.value || undefined;
  await collectionApi.saveMediaFolder(current.value.id, {
    name: mediaFolderName.value.trim(),
    parentId,
    sort: mediaFolderList.value.length,
  });
  mediaFolderName.value = '';
  await loadFiles(current.value.id);
  // 新增子目录后保持上级展开，避免父行自动收起看不到新行
  syncMediaFolderExpandedKeys(parentId || null);
}
async function deleteMediaFolder(row: CollectionMediaFolderVo) {
  if (!current.value?.id || !row.id) return;
  await collectionApi.deleteMediaFolder(current.value.id, row.id);
  message.success('影像目录已删除');
  await loadFiles(current.value.id);
}
async function saveMedia() {
  if (!(await validateFormSafely(mediaFormRef.value))) return;
  if (!current.value?.id || !mediaForm.fileId) {
    message.warning('请先选择影像文件');
    return;
  }
  await collectionApi.saveMedia(current.value.id, { ...mediaForm });
  message.success('影像已保存');
  mediaVisible.value = false;
  await loadFiles(current.value.id);
  handleRefresh();
}
async function deleteMedia(row: CollectionMediaVo) {
  if (!current.value?.id || !row.id) return;
  await collectionApi.deleteMedia(current.value.id, row.id);
  message.success('影像已删除');
  await loadFiles(current.value.id);
  handleRefresh();
}

const attachmentVisible = ref(false);
const attachmentForm = reactive<CollectionAttachmentVo>({});
function openAttachment() {
  Object.assign(attachmentForm, {
    id: undefined,
    fileId: undefined,
    attachmentType: 'OTHER',
    name: undefined,
    remark: undefined,
  });
  attachmentVisible.value = true;
}
async function saveAttachment() {
  if (!(await validateFormSafely(attachmentFormRef.value))) return;
  if (!current.value?.id || !attachmentForm.fileId) {
    message.warning('请先选择附件文件');
    return;
  }
  await collectionApi.saveAttachment(current.value.id, { ...attachmentForm });
  message.success('附件已保存');
  attachmentVisible.value = false;
  await loadFiles(current.value.id);
  handleRefresh();
}
async function deleteAttachment(row: CollectionAttachmentVo) {
  if (!current.value?.id || !row.id) return;
  await collectionApi.deleteAttachment(current.value.id, row.id);
  message.success('附件已删除');
  await loadFiles(current.value.id);
  handleRefresh();
}

const setVisible = ref(false);
const setForm = reactive<CollectionSetVo>({});
function requestSetAmendment() {
  const id = current.value?.id;
  if (!id || detailAuxLoading.value) return;
  void router.push({ path: '/collection/change', query: { collectionId: id, setAmendment: '1' } });
}
function openSet(row?: CollectionSetVo) {
  if (isRegistered(current.value) || current.value?.catalogStatus === 'APPROVED') return requestSetAmendment();
  Object.assign(setForm, {
    id: row?.id,
    relation: row?.relation || 'PART',
    childCollectionId: row?.childCollectionId,
    partNo: row?.partNo,
    partName: row?.partName,
    quantity: row?.quantity ?? 1,
    unit: row?.unit || '件',
    sort: row?.sort ?? 0,
  });
  setVisible.value = true;
}
async function saveSet() {
  if (!(await validateFormSafely(setFormRef.value))) return;
  if (!current.value?.id || !setForm.relation) {
    message.warning('请先选择套件关系');
    return;
  }
  await collectionApi.saveSet(current.value.id, { ...setForm });
  message.success('套件关系已保存');
  setVisible.value = false;
  await loadSets(current.value.id);
}
async function deleteSet(row: CollectionSetVo) {
  if (isRegistered(current.value) || current.value?.catalogStatus === 'APPROVED') return requestSetAmendment();
  if (!current.value?.id || !row.id) return;
  await collectionApi.deleteSet(current.value.id, row.id);
  message.success('套件关系已删除');
  await loadSets(current.value.id);
}

const migrationRef = ref<InstanceType<typeof RegisteredMigrationModal> | null>(null);
const migrationBatchRef = ref<InstanceType<typeof MigrationBatchVerificationModal> | null>(null);
const importVisible = ref(false);
const importMode = ref<CollectionImportMode>('CREATE_ONLY');
const importReason = ref('');
const importTemplate = ref<CollectionImportTemplateColumnVo[]>([]);
const importRows = ref<CollectionImportRowVo[]>([]);
const importErrors = ref<CollectionImportErrorVo[]>([]);
const importDictOptions = ref<Record<string, { label: string; value: string }[]>>({});
const importFileInput = ref<HTMLInputElement | null>(null);
const importFileName = ref('');
const importConfirmed = ref<{
  validation: CollectionImportValidateResultVo;
  rows: CollectionImportRowVo[];
  mode: CollectionImportMode;
  reason?: string;
} | null>(null);
const importSession = useCollectionImportSession(() => {
  importVisible.value = false;
  clearImportInput();
});
const importBusy = importSession.busy;
function clearImportInput() {
  importRows.value = [];
  importErrors.value = [];
  importFileName.value = '';
  importConfirmed.value = null;
  if (importFileInput.value) importFileInput.value.value = '';
}
watch(
  [importRows, importMode, importReason],
  () => {
    importSession.invalidate();
    importConfirmed.value = null;
  },
  { deep: true, flush: 'sync' },
);
watch(
  importVisible,
  (show) => {
    if (!show) {
      importSession.invalidate();
      clearImportInput();
      importReason.value = '';
    }
  },
  { flush: 'sync' },
);

const templateColumns = [
  { ellipsis: { tooltip: true }, resizable: true, title: '导入列', key: 'title', width: 150 },
  {
    title: '必填',
    key: 'required',
    width: 70,
    render: (r: CollectionImportTemplateColumnVo) => dictText('col_required', r.required),
  },
  { ellipsis: { tooltip: true }, resizable: true, width: 260,
    title: '可选值',
    key: 'dictType',
    minWidth: 260,
    render: (r: CollectionImportTemplateColumnVo) => dictOptionText(r.dictType),
  },
  { ellipsis: { tooltip: true }, resizable: true, width: 140, title: '示例', key: 'sample', minWidth: 140, render: (r: CollectionImportTemplateColumnVo) => importSampleText(r) },
];
const importErrorColumns = [
  { title: '行号', key: 'rowNo', width: 80 },
  { ellipsis: { tooltip: true }, resizable: true, title: '字段', key: 'field', width: 140 },
  { ellipsis: { tooltip: true }, resizable: true, width: 260, title: '错误', key: 'message', minWidth: 260 },
];

const batchFileVisible = ref(false);
const batchFileItems = ref<CollectionFileImportItemVo[]>([]);
const batchFileErrors = ref<CollectionImportErrorVo[]>([]);
const batchConfirmed = ref<{ items: CollectionFileImportItemVo[]; validation: CollectionFileImportResultVo } | null>(null);
const batchWindow = ref(0);
const batchUploading = ref(false);
const batchUploadHandler = ref<(file: UploadFileInfo) => void>(() => {});
const batchUploadChangeHandler = ref<(data: { fileList: UploadFileInfo[] }) => void>(() => {});
const batchSession = useCollectionImportSession(clearBatchFiles);
const batchBusy = batchSession.busy;
function clearBatchFiles() {
  batchWindow.value++; batchFileVisible.value = false; batchFileItems.value = []; batchFileErrors.value = [];
  batchConfirmed.value = null; batchUploading.value = false; batchUploadHandler.value = () => {}; batchUploadChangeHandler.value = () => {};
}
watch(batchFileItems, () => { batchSession.invalidate(); batchConfirmed.value = null; batchFileErrors.value = []; }, { deep: true, flush: 'sync' });
watch(batchFileVisible, show => { if (!show) { batchSession.invalidate(); clearBatchFiles(); } }, { flush: 'sync' });
watch(queryParams, () => { batchSession.invalidate(); clearBatchFiles(); }, { deep: true, flush: 'sync' });
const batchMatchColumns = [
  { ellipsis: { tooltip: true }, resizable: true, width: 180, title: '文件', key: 'fileName', minWidth: 180 }, { ellipsis: { tooltip: true }, resizable: true, title: '总登记号', key: 'totalRegNo', width: 130 },
  { ellipsis: { tooltip: true }, resizable: true, width: 170, title: '匹配藏品', key: 'collectionName', minWidth: 170 },
  { title: '关联方式', key: 'existingId', width: 100, render: (r: any) => r.existingId ? '已有，跳过' : '新关联' },
  { ellipsis: { tooltip: true }, resizable: true, title: '归档类型', key: 'kind', width: 110, render: (r: any) => r.kind === 'MEDIA' ? '影像/音视频' : '业务附件' },
];
const batchFileKindOptions = [
  { label: '影像/音视频/三维', value: 'MEDIA' },
  { label: '业务附件/文档', value: 'ATTACHMENT' },
];
const batchFileColumns = [
  { resizable: true, width: 220, title: '文件名', key: 'fileName', minWidth: 220, ellipsis: { tooltip: true } },
  {
    title: '总登记号',
    key: 'totalRegNo',
    width: 180,
    render: (row: CollectionFileImportItemVo) =>
      h(NInput, {
        value: row.totalRegNo, disabled: batchBusy.value,
        placeholder: '从文件名自动识别',
        onUpdateValue: (value) => (row.totalRegNo = value),
      }),
  },
  {
    title: '归档类型',
    key: 'kind',
    width: 190,
    render: (row: CollectionFileImportItemVo) =>
      h(NSelect, {
        value: row.kind, disabled: batchBusy.value,
        options: batchFileKindOptions,
        onUpdateValue: (value) => (row.kind = value),
      }),
  },
  {
    title: '具体类型',
    key: 'type',
    width: 170,
    render: (row: CollectionFileImportItemVo) =>
      h(NInput, {
        value: row.kind === 'ATTACHMENT' ? row.attachmentType : row.mediaType, disabled: batchBusy.value,
        placeholder: '可由文件名识别',
        onUpdateValue: (value) => {
          if (row.kind === 'ATTACHMENT') row.attachmentType = value || undefined;
          else row.mediaType = value || undefined;
        },
      }),
  },
  {
    title: '操作',
    key: 'actions',
    width: 72,
    render: (row: CollectionFileImportItemVo) =>
      h(
        NButton,
        {
          text: true,
          type: 'error', disabled: batchBusy.value,
          onClick: () => (batchFileItems.value = batchFileItems.value.filter((it) => it !== row)),
        },
        { default: () => '移除' },
      ),
  },
];

function openBatchFileImport() {
  batchSession.invalidate(); clearBatchFiles();
  const ticket = batchSession.begin('open'); if (ticket === null) return;
  batchFileVisible.value = true;
  const window = batchWindow.value;
  batchUploadHandler.value = file => { if (batchFileVisible.value && batchWindow.value === window) onBatchFileUploaded(file); };
  batchUploadChangeHandler.value = data => {
    if (!batchFileVisible.value || batchWindow.value !== window) return;
    batchUploading.value = data.fileList.some(f => f.status === 'pending' || f.status === 'uploading');
    batchSession.invalidate(); batchConfirmed.value = null;
  };
  batchSession.finish(ticket);
}

function onBatchFileUploaded(file: UploadFileInfo) {
  if (!file.id) {
    message.error(`文件 ${file.name || ''} 上传完成但未返回文件ID`);
    return;
  }
  const name = file.name || '';
  const stem = name.replace(/\.[^.]+$/, '');
  const standard = /^([0-9]{14})-([!-~]+)-([A-F])-([0-9]+)$/.exec(stem);
  const [totalRegNo, token] = standard ? [standard[2], ({ A: 'FRONT', B: 'TOP', C: 'SIDE', D: 'PANORAMIC', E: 'DETAIL', F: 'BOTTOM' } as Record<string, string>)[standard[3]]] : stem.split('__').map((value) => value.trim());
  const extension = name.split('.').pop()?.toLowerCase() || '';
  const mediaExtensions = new Set([
    'jpg',
    'jpeg',
    'png',
    'gif',
    'bmp',
    'webp',
    'tif',
    'tiff',
    'mp4',
    'mov',
    'avi',
    'mp3',
    'wav',
    'glb',
    'gltf',
    'obj',
    'fbx',
  ]);
  const kind: 'MEDIA' | 'ATTACHMENT' = mediaExtensions.has(extension) ? 'MEDIA' : 'ATTACHMENT';
  batchFileItems.value.push({
    rowNo: batchFileItems.value.length + 1,
    totalRegNo: totalRegNo || undefined,
    fileId: file.id,
    fileName: name,
    kind,
    ...(kind === 'MEDIA' ? { mediaType: token || undefined } : { attachmentType: token || undefined }),
  });
  batchFileErrors.value = [];
}

async function validateBatchFiles() {
  if (!batchFileVisible.value || batchBusy.value || batchUploading.value || !batchFileItems.value.length) return;
  batchConfirmed.value = null; batchFileErrors.value = [];
  const items = cloneDeep(batchFileItems.value).map((r,i) => ({ ...r, rowNo: i + 1 }));
  const ticket = batchSession.begin('validate'); if (ticket === null) return;
  try {
    const resp = await collectionApi.validateFileImport(items);
    if (!batchSession.current(ticket) || !batchFileVisible.value) return;
    const validation = batchFileValidation(resp, items); batchFileErrors.value = validation.errors;
    if (validation.valid) { batchConfirmed.value = { items, validation }; message.success(`已匹配 ${validation.matchedCount} 个文件，请核对收藏单位和对应藏品后确认`); }
    else message.warning('存在未匹配或无效文件');
  } catch (error: any) {
    if (batchSession.current(ticket)) { batchConfirmed.value = null; message.error(error?.message || '文件预校验失败，请重新核对'); }
  } finally { batchSession.finish(ticket); }
}
async function commitBatchFiles() {
  if (!batchFileVisible.value || batchBusy.value || batchUploading.value || !batchConfirmed.value) return;
  const confirmed = cloneDeep(batchConfirmed.value);
  const ticket = batchSession.begin('commit'); if (ticket === null) return;
  try {
    const resp = await collectionApi.commitFileImport(confirmed.items, confirmed.validation.confirmation!);
    if (!batchSession.current(ticket) || !batchFileVisible.value) return;
    const data = batchFileCommit(resp, confirmed.validation); batchFileErrors.value = data.errors;
    batchConfirmed.value = null;
    if (!data.valid) { message.warning('本次文件关联未通过校验，请重新核对'); return; }
    message.success(`已关联 ${data.createdIds.length} 个文件，跳过重复 ${data.unchangedIds.length} 个`);
    batchFileVisible.value = false; handleRefresh();
  } catch (error: any) {
    if (batchSession.current(ticket)) { batchConfirmed.value = null; message.error(error?.message || '文件关联失败，请核对实际结果后再办理'); }
  } finally { batchSession.finish(ticket); }
}

async function openImport() {
  importSession.invalidate();
  clearImportInput();
  importMode.value = 'CREATE_ONLY';
  importReason.value = '';
  importVisible.value = true;
  const ticket = importSession.begin('load');
  if (ticket === null) return;
  try {
    const resp: any = await collectionApi.importTemplate();
    if (!importSession.current(ticket) || !importVisible.value) return;
    const columns = resp?.data?.list ?? resp?.data;
    if (
      !Array.isArray(columns) ||
      !columns.length ||
      columns.some((v: any) => !v || typeof v.field !== 'string' || typeof v.title !== 'string')
    )
      throw new Error('导入模板未完整加载，请重新打开');
    importTemplate.value = columns.map((col: CollectionImportTemplateColumnVo) =>
      yuanImportColumn(col, new Set(['valuation'])),
    );
    await loadImportDictOptions(ticket);
  } catch (error: any) {
    if (importSession.current(ticket)) {
      importTemplate.value = [];
      importErrors.value = [];
      message.error(error?.message || '导入模板读取失败');
    }
  } finally {
    importSession.finish(ticket);
  }
}

async function loadImportDictOptions(ticket: number) {
  const dictTypes = Array.from(new Set(importTemplate.value.map((col) => col.dictType).filter(Boolean))) as string[];
  const unloaded = dictTypes.filter((type) => !importDictOptions.value[type]);
  if (!unloaded.length) return;
  const entries = await Promise.all(
    unloaded.map(async (type) => {
      const resp: any = await fetchDictType(type, true);
      const options = (resp?.data ?? []).map((item: any) => ({
        label: String(item.label ?? item.name ?? item.value ?? ''),
        value: String(item.value ?? ''),
      }));
      return [type, options] as const;
    }),
  );
  if (importSession.current(ticket) && importVisible.value)
    importDictOptions.value = { ...importDictOptions.value, ...Object.fromEntries(entries) };
}

function dictOptionText(dictType?: string | null) {
  return formatImportDictOptions(dictType, importDictOptions.value);
}

function importSampleText(row: CollectionImportTemplateColumnVo) {
  return formatImportSample(row, importDictOptions.value);
}

function openImportFile() {
  if (!importVisible.value || importBusy.value || !importTemplate.value.length) return;
  importFileInput.value?.click();
}

function downloadImportTemplate() {
  const headers = importTemplate.value.map((col) => col.title || col.field);
  const samples = importTemplate.value.map((col) => col.sample || '');
  const sheet = XLSX.utils.aoa_to_sheet([headers, samples]);
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, sheet, '藏品导入模板');
  XLSX.writeFile(workbook, '藏品批量导入模板.xlsx');
}

function normalizeImportValue(field: string, value: any, header: string) {
  const text = typeof value === 'string' ? value.trim() : value;
  if (text === '') return undefined;
  if (field === 'quantity' || field === 'valuation') {
    const n = Number(text);
    return field === 'valuation' ? parseImportAmount(text, header) : (Number.isFinite(n) ? n : text);
  }
  return text;
}

async function onImportFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file || !importVisible.value) return;
  importSession.invalidate();
  clearImportInput();
  importFileName.value = file.name;
  const ticket = importSession.begin('parse');
  if (ticket === null) return;
  try {
    const buffer = await file.arrayBuffer();
    if (!importSession.current(ticket) || !importVisible.value) return;
    const workbook = XLSX.read(buffer, { type: 'array' });
    const sheetName = workbook.SheetNames[0];
    if (!sheetName) {
      message.warning('导入文件没有可读取的工作表');
      return;
    }
    const sheet = workbook.Sheets[sheetName];
    const rawRows = XLSX.utils.sheet_to_json<Record<string, any>>(sheet, { defval: '', raw: false });
    const fieldByHeader = new Map<string, string>();
    importTemplate.value.forEach((col) => {
      fieldByHeader.set(col.field, col.field);
      fieldByHeader.set(col.title, col.field);
      if (col.field === 'valuation') fieldByHeader.set(col.title.replace('（元）', '（分）'), col.field);
    });
    // 兼容已下载的旧 ID 模板；名称字段由后端按当前收藏单位重新解析。
    Object.entries({ categoryId: 'categoryId', 文物分类ID: 'categoryId', 文物分类: 'categoryName',
      warehouseId: 'warehouseId', 库位ID: 'warehouseId', 库位: 'warehouseName',
      coverImage: 'coverImage', '文物影像/封面fileId': 'coverImage' }).forEach(([header, field]) => fieldByHeader.set(header, field));
    const rows = rawRows
      .map((raw, index) => {
        const row: Record<string, any> = { rowNo: index + 2 };
        Object.entries(raw).forEach(([header, value]) => {
          const field = fieldByHeader.get(header.trim());
          if (!field) return;
          const normalized = normalizeImportValue(field, value, header);
          if (normalized !== undefined) row[field] = normalized;
        });
        return row as CollectionImportRowVo;
      })
      .filter((row) => Object.keys(row).some((key) => key !== 'rowNo'));
    importRows.value = rows;
    importErrors.value = [];
    input.value = '';
    message.success(`已解析 ${rows.length} 条导入数据`);
  } catch (error: any) {
    if (importSession.current(ticket)) {
      clearImportInput();
      message.error(error?.message || '导入文件解析失败');
    }
  } finally {
    importSession.finish(ticket);
  }
}

function parseImportRows(): CollectionImportRowVo[] | null {
  if (!importRows.value.length) {
    message.warning('请先选择 Excel/CSV 文件');
    return null;
  }
  return importRows.value;
}
async function validateImport() {
  const rows = parseImportRows();
  if (!rows || !importVisible.value || importBusy.value) return;
  const mode = importMode.value;
  const reason = importReason.value.trim() || undefined;
  if (mode === 'UPSERT' && !reason) {
    message.warning('批量更新已有藏品时必须填写更正原因');
    return;
  }
  const frozenRows = JSON.parse(JSON.stringify(rows)) as CollectionImportRowVo[];
  importConfirmed.value = null;
  importErrors.value = [];
  const ticket = importSession.begin('validate');
  if (ticket === null) return;
  try {
    const response = await collectionApi.validateImport(frozenRows, mode, reason);
    if (!importSession.current(ticket) || !importVisible.value) return;
    const result = ledgerValidation(response, frozenRows.length);
    importErrors.value = result.errors;
    if (result.valid) {
      importConfirmed.value = { validation: result, rows: frozenRows, mode, reason };
      message.success('预校验通过，请核对目标馆和新增、更正数量后确认');
    } else message.warning('存在校验错误，未导入');
  } catch (error: any) {
    if (importSession.current(ticket)) {
      importConfirmed.value = null;
      importErrors.value = [];
      message.error(error?.message || '导入预校验失败');
    }
  } finally {
    importSession.finish(ticket);
  }
}
async function commitImport() {
  const confirmed = importConfirmed.value;
  if (!confirmed || !confirmed.validation.confirmation || !importVisible.value || importBusy.value) return;
  const ticket = importSession.begin('commit');
  if (ticket === null) return;
  try {
    const response = await collectionApi.commitImport(
      JSON.parse(JSON.stringify(confirmed.rows)),
      confirmed.mode,
      confirmed.reason,
      confirmed.validation.confirmation,
    );
    if (!importSession.current(ticket) || !importVisible.value) return;
    const data = ledgerCommit(response, confirmed.validation);
    importErrors.value = data.errors;
    if (!data.valid) {
      importConfirmed.value = null;
      message.warning('重新校验未通过，未导入');
      return;
    }
    message.success(
      '导入完成：新增 ' +
        data.createdIds.length +
        ' 条，更新 ' +
        data.updatedIds.length +
        ' 条，未变化 ' +
        data.unchangedIds.length +
        ' 条',
    );
    if (data.createdIds.length) message.info(REGISTRATION_RECEIPT_HINT, { duration: 6000 });
    importVisible.value = false;
    handleRefresh();
  } catch (error: any) {
    if (importSession.current(ticket)) {
      importConfirmed.value = null;
      importErrors.value = [];
      message.error(error?.message || '导入提交失败，请核对处理结果');
    }
  } finally {
    importSession.finish(ticket);
  }
}

// ── 提用（出入库流程）──
const reqVisible = ref(false);
const reqSaving = ref(false);
const reqTarget = ref<CollectionVo | null>(null);
const reqForm = reactive<any>({});

function onRequisition(row: CollectionVo) {
  if (row.lifeStatus !== 'IN_STORE' || row.inventoryHold) {
    message.warning('只有在库藏品可以办理提用');
    return;
  }
  reqTarget.value = row;
  Object.assign(reqForm, {
    documentNo: null,
    purpose: null,
    basis: null,
    department: null,
    operator: null,
    reviewer: null,
    custodian: null,
    handoverUser: null,
    expectReturnDate: null,
    toWarehouseId: null,
    attachmentIds: null,
    reviewOpinion: null,
  });
  reqVisible.value = true;
}
async function doRequisition() {
  const resp: any = await requisitionApi.save({ ...reqForm, collectionIds: reqTarget.value?.id });
  const id = resp?.data;
  if (id) await requisitionApi.submit(id);
  message.success('提用单已提交审批');
}

// ── 申请退出馆藏 ──
const deregVisible = ref(false);
const deregSaving = ref(false);
const deregTarget = ref<CollectionVo | null>(null);
const deregForm = reactive<any>({});

function onDereg(row: CollectionVo) {
  if (row.lifeStatus === 'DEREGISTERED') {
    message.warning('该藏品已退出馆藏，不能重复发起申请');
    return;
  }
  deregTarget.value = row;
  Object.assign(deregForm, {
    reason: null,
    approveLevel: null,
    approvalDocNo: null,
    expertOpinion: null,
    destination: null,
    detail: null,
    attachmentIds: null,
  });
  deregVisible.value = true;
}
async function doDereg() {
  const resp: any = await deregistrationApi.save({ ...deregForm, collectionId: deregTarget.value?.id });
  const id = resp?.data;
  if (id) await deregistrationApi.submit(id);
  message.success('退出馆藏申请已提交审批');
}
</script>

<style scoped>
.dossier-preview-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.dossier-identity {
  margin-top: 8px;
}
.import-modal-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: min(620px, calc(100vh - 220px));
  overflow-y: auto;
  padding-right: 6px;
}

.import-upload-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 12px;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 6px;
  background: var(--body-color, #f8fafc);
}

.import-file-state {
  min-width: 0;
  text-align: right;
}

.import-file-state strong,
.import-file-state span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.import-file-state strong {
  font-weight: 600;
}

.import-file-state span {
  margin-top: 3px;
  color: var(--text-color-3, #6b7280);
  font-size: 12px;
}

.import-section {
  min-height: 0;
  padding: 12px;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 6px;
  background: var(--card-color, #fff);
}

.import-template-section,
.import-result-section {
  flex: 0 0 auto;
}

.import-result-section {
  min-height: 112px;
}

.import-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.import-section-title > span {
  font-weight: 600;
}

.import-table-wrap,
.import-error-table {
  max-height: 220px;
  overflow: auto;
}

@media (max-width: 640px) {
  .import-upload-bar,
  .import-section-title {
    align-items: flex-start;
    flex-direction: column;
  }

  .import-file-state {
    width: 100%;
    text-align: left;
  }
}

/* Keep the dossier responsive to its drawer/content container, not the browser width. */
.collection-dossier {
  container-type: inline-size;
  color: var(--detail-text);
}
.detail-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px 24px;
  padding: 4px 0 24px;
}
.detail-identity { flex: 1 1 340px; min-width: 0; }
.detail-no { display: flex; flex-wrap: wrap; align-items: baseline; gap: 10px; color: var(--detail-muted); font-size: 12px; }
.detail-no strong { color: var(--detail-accent); font-size: 16px; font-variant-numeric: tabular-nums; letter-spacing: .04em; overflow-wrap: anywhere; }
.detail-header h2 { margin: 8px 0 10px; font-size: 26px; line-height: 1.45; font-weight: 650; overflow-wrap: anywhere; }
.detail-header .dossier-identity { display: flex; flex-wrap: wrap; gap: 6px 20px; color: var(--detail-muted); font-size: 13px; }
.detail-statuses { margin-top: 12px; }
.detail-actions { display: flex; flex-wrap: wrap; gap: 8px; padding-top: 4px; }
.detail-tab-content { outline: none; scroll-margin-top: 16px; }
.detail-tabs { min-width: 0; }
.detail-section-picker { display: flex; align-items: center; gap: 12px; margin: 2px 0 16px; }
.detail-section-picker > span { flex: none; color: var(--detail-muted); font-size: 13px; }
.detail-tabs > :deep(.n-tabs-nav) { margin-bottom: 18px; }
.collection-detail-layout { display: grid; grid-template-columns: 280px minmax(0, 1fr); align-items: start; gap: 28px; }
.collection-detail-media { display: flex; flex-direction: column; min-width: 0; padding: 14px; border: 1px solid var(--detail-border); border-radius: 10px; background: var(--detail-surface); }
.detail-gallery-header { display: flex; align-items: center; justify-content: space-between; padding: 0 0 12px; font-size: 13px; font-weight: 600; }
.detail-gallery-header span:last-child { color: var(--detail-muted); font-size: 12px; font-weight: 400; }
.detail-cover { display: flex; align-items: center; justify-content: center; aspect-ratio: 4 / 3; min-height: 0; overflow: hidden; border-radius: 6px; background: var(--detail-fill); }
.detail-cover-empty { aspect-ratio: auto; min-height: 160px; }
.detail-cover :deep(.current-file-image), .detail-cover :deep(.n-image) { width: 100%; height: 100%; }
.detail-cover :deep(img) { width: 100%; height: 100%; object-fit: contain; }
.detail-thumb :deep(.current-file-image), .detail-thumb :deep(.n-image) { height: 100%; }
.detail-carousel { width: 100%; height: 100%; }
.detail-thumb-list { display: flex; gap: 8px; overflow-x: auto; overflow-y: hidden; padding: 10px 2px 4px; scroll-snap-type: x proximity; }
.detail-thumb { flex: 0 0 52px; height: 52px; padding: 0; overflow: hidden; cursor: pointer; border: 1px solid var(--detail-border); border-radius: 5px; background: var(--detail-fill); scroll-snap-align: start; }
.detail-thumb.active { border-color: var(--detail-accent); box-shadow: 0 0 0 1px var(--detail-accent); }
.detail-thumb:focus-visible, .detail-metrics button:focus-visible { outline: 2px solid var(--detail-accent); outline-offset: 2px; }
.detail-thumb :deep(img) { width: 100%; height: 100%; object-fit: cover; }
.detail-image-caption { color: var(--detail-muted); text-align: center; margin: 10px 0 0; font-size: 12px; }
.detail-metrics { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; margin-top: 14px; padding-top: 14px; border-top: 1px solid var(--detail-border); }
.detail-metrics button { min-width: 0; padding: 10px 12px; text-align: left; cursor: pointer; color: inherit; font: inherit; border: 1px solid transparent; border-radius: 6px; background: var(--detail-fill); transition: border-color .15s; }
.detail-metrics button:hover { border-color: var(--detail-accent); }
.detail-metrics strong { display: block; font-size: 21px; line-height: 1.4; font-variant-numeric: tabular-nums; }
.detail-metrics span { display: flex; justify-content: space-between; align-items: center; color: var(--detail-muted); font-size: 12px; }
.collection-detail-main { min-width: 0; }
.detail-facts { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); border: 1px solid var(--detail-border); border-radius: 8px; overflow: hidden; }
.detail-facts > div { min-width: 0; padding: 14px 16px; background: var(--detail-fill); }
.detail-facts > div + div { border-left: 1px solid var(--detail-border); }
.detail-facts > div > span { display: block; color: var(--detail-muted); font-size: 12px; margin-bottom: 6px; }
.detail-facts strong { font-size: 18px; line-height: 1.5; font-weight: 600; overflow-wrap: anywhere; }
.detail-facts small { margin-left: 5px; font-size: 12px; font-weight: 400; color: var(--detail-muted); }
.detail-summary-section { padding: 20px 0; border-bottom: 1px solid var(--detail-border); }
.detail-section-heading { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 8px; margin-bottom: 14px; }
.detail-section-heading h3, .detail-reference-section h3 { margin: 0; font-size: 15px; font-weight: 600; }
.detail-summary-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px 24px; margin: 0; }
.detail-summary-fields > div { display: grid; grid-template-columns: 88px minmax(0, 1fr); gap: 12px; font-size: 13px; line-height: 1.7; }
.detail-summary-fields dt { color: var(--detail-muted); }
.detail-summary-fields dd { margin: 0; overflow-wrap: anywhere; white-space: pre-wrap; }
.detail-field-wide { grid-column: 1 / -1; }
.detail-feature { margin: 0 0 16px; line-height: 1.9; font-size: 14px; white-space: pre-wrap; overflow-wrap: anywhere; }
.detail-reference-section { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; padding: 20px 0 0; }
.detail-reference-section p { color: var(--detail-muted); font-size: 12px; margin: 6px 0 0; overflow-wrap: anywhere; }
.detail-footnote { margin: 24px 0 0; padding-top: 14px; border-top: 1px solid var(--detail-border); color: var(--detail-muted); font-size: 12px; line-height: 1.8; }
.detail-section-title { margin: 22px 0 12px; font-size: 15px; font-weight: 600; }
.detail-section-title:first-child { margin-top: 0; }
.detail-section-toolbar { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; justify-content: space-between; margin: 8px 0 16px; font-weight: 600; }
.collection-dossier :deep(.n-descriptions-table-content) { overflow-wrap: anywhere; }
@container (max-width: 880px) {
  .collection-detail-layout { grid-template-columns: 240px minmax(0, 1fr); gap: 20px; }
  .detail-facts { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .detail-facts > div:nth-child(3) { border-left: 0; }
  .detail-facts > div:nth-child(n+3) { border-top: 1px solid var(--detail-border); }
  .detail-summary-fields { grid-template-columns: minmax(0, 1fr); }
}
@container (max-width: 640px) {
  .detail-tabs > :deep(.n-tabs-nav) { display: none; }
  .detail-header { gap: 16px; padding-bottom: 16px; }
  .detail-header h2 { font-size: 22px; }
  .detail-identity { flex-basis: 100%; }
  .detail-actions { padding-top: 0; }
  .collection-detail-layout { grid-template-columns: minmax(0, 1fr); gap: 20px; }
  .detail-cover { max-height: 280px; aspect-ratio: 16 / 9; }
  .detail-cover-empty { aspect-ratio: auto; min-height: 104px; }
  .detail-metrics { grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 4px; }
  .detail-metrics button { padding: 8px 6px; }
  .detail-metrics span .n-icon { display: none; }
  .detail-summary-fields { grid-template-columns: minmax(0, 1fr); }
  .detail-reference-section { align-items: flex-start; }
}
</style>

<style scoped>
.detail-note-text { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
