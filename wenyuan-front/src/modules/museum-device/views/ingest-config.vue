<template>
  <div class="ingest-page">
    <NTabs
      v-model:value="activeTab"
      type="line"
      animated
      class="ingest-tabs"
      pane-wrapper-style="display: flex; flex: 1; min-height: 0; overflow: hidden;"
      pane-style="display: flex; flex: 1; min-height: 0; flex-direction: column; overflow: hidden;"
    >
      <NTabPane name="channels" tab="采集通道" display-directive="show">
        <HPageDataTable
          ref="channelTableRef"
          class="ingest-data-table"
          :columns="channelColumns"
          :fetch-data="fetchChannels"
          :scroll-x="1810"
          row-key-field="code"
          :is-edit="false"
          :is-delete="false"
          :show-operate="false"
          @add="openConnectorForm()"
          @row-view="openChannelView"
        />
      </NTabPane>

      <NTabPane name="routes" tab="采集路由" display-directive="show">
        <HPageDataTable
          ref="routeTableRef"
          class="ingest-data-table"
          :columns="routeColumns"
          :fetch-data="fetchRoutes"
          :params="routeQuery"
          :scroll-x="1450"
          row-key-field="id"
          :is-edit="false"
          :is-delete="false"
          :show-operate="false"
          @add="openRouteForm()"
          @row-view="openRouteView"
        >
          <template #queryForm>
            <NFormItem label="网关">
              <NSelect v-model:value="routeQuery.gatewayId" :options="gatewayOptions" filterable style="width: 260px" />
            </NFormItem>
          </template>
          <template #toolbar-extra>
            <NButton size="small" secondary :disabled="!allRoutes.length" @click="matchShow = true"
              >路由匹配测试</NButton
            >
          </template>
        </HPageDataTable>
      </NTabPane>

      <NTabPane name="history-sync" tab="历史同步" display-directive="show:lazy">
        <HPageDataTable
          ref="historySyncTableRef"
          class="ingest-data-table"
          :columns="historySyncColumns"
          :fetch-data="fetchHistorySyncTasks"
          :scroll-x="1700"
          row-key-field="id"
          :is-edit="false"
          :is-delete="false"
          @add="openHistorySyncForm"
          @row-view="openHistorySyncView"
        />
      </NTabPane>

      <NTabPane name="problems" tab="待识别 / 死信" display-directive="show">
        <HPageDataTable
          ref="problemTableRef"
          title="待识别 / 死信"
          class="ingest-data-table"
          :columns="problemColumns"
          :fetch-data="fetchProblems"
          :scroll-x="1380"
          row-key-field="id"
          :is-add="false"
          :is-edit="false"
          :is-copy="false"
          :is-delete="false"
          :is-export="false"
          :show-operate="false"
          @updateCheckedRowKeys="handleProblemSelection"
        >
          <template #activeBtn>
            <NButton
              size="small"
              type="error"
              secondary
              :disabled="!problemCount"
              :loading="problemDeleting"
              @click="confirmDeleteProblems()"
            >
              {{ checkedProblemIds.length ? `删除选中（${checkedProblemIds.length}）` : '一键清空' }}
            </NButton>
          </template>
          <template #toolbar-extra>
            <NButton size="small" @click="refreshProblems">刷新</NButton>
          </template>
        </HPageDataTable>
      </NTabPane>
    </NTabs>

    <HPageView v-model:show="channelViewShow" :title="channelViewTitle" width="60%" :show-action="false">
      <NDescriptions v-if="channelViewRow" label-placement="left" :column="2" bordered size="small">
        <NDescriptionsItem label="通道类型">{{ channelTypeText(channelViewRow) }}</NDescriptionsItem>
        <NDescriptionsItem label="通道名称">{{ channelViewRow.name || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="系统标识">
          <NSpace align="center" :wrap="false">
            <NText code>{{ channelViewRow.code || '--' }}</NText>
            <NButton v-if="channelViewRow.code" size="tiny" text type="primary" @click="copyChannelCode">复制</NButton>
          </NSpace>
        </NDescriptionsItem>
        <NDescriptionsItem label="传输方式">{{ channelViewRow.transport || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="采集方式">{{ acquisitionText(channelViewRow.acquisitionMode) }}</NDescriptionsItem>
        <NDescriptionsItem label="状态">{{ channelStatusText(channelViewRow) }}</NDescriptionsItem>
        <template v-if="channelViewRow.builtIn">
          <NDescriptionsItem label="监听配置" :span="2">{{ builtInBindEndpoint(channelViewRow) }}</NDescriptionsItem>
          <NDescriptionsItem label="网卡地址" :span="2">
            <NSpace v-if="channelViewRow.networkAddresses?.length" vertical size="small">
              <NText
                v-for="item in channelViewRow.networkAddresses"
                :key="`${item.interfaceName}-${item.address}`"
                code
              >
                {{ networkAddressLabel(channelViewRow, item) }}
              </NText>
            </NSpace>
            <span v-else>--</span>
          </NDescriptionsItem>
          <NDescriptionsItem label="最近注册时间" :span="2"
            ><span style="white-space: nowrap">{{ formatDateTime(channelViewRow.lastSeenAt) }}</span></NDescriptionsItem
          >
          <template v-if="channelViewRow.type === 'MQTT_SERVER'">
            <NDescriptionsItem label="支持 MQTT 版本">{{
              listText(channelViewRow.protocolVersions)
            }}</NDescriptionsItem>
          </template>
          <NDescriptionsItem v-else-if="channelViewRow.type === 'HTTP_SERVER'" label="接收路径" :span="2">{{
            channelViewRow.path || '--'
          }}</NDescriptionsItem>
          <NDescriptionsItem v-else label="支持传输版本" :span="2">{{
            listText(channelViewRow.protocolVersions)
          }}</NDescriptionsItem>
        </template>
        <template v-else-if="channelViewRow.type === 'MQTT_CLIENT'">
          <NDescriptionsItem label="消息服务器地址" :span="2">{{ channelViewRow.brokerUri || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="订阅 Topic" :span="2">{{ channelViewRow.topic || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="Client ID">{{ channelViewRow.clientId || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="QoS">{{ channelViewRow.qos ?? '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="用户名">{{ channelViewRow.username || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="密码">{{
            channelViewRow.passwordConfigured ? '已配置' : '未配置'
          }}</NDescriptionsItem>
        </template>
        <template v-else-if="channelViewRow.type === 'HTTP_CLIENT'">
          <NDescriptionsItem label="接口地址" :span="2">{{ channelViewRow.baseUrl || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="用户名">{{ channelViewRow.username || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="密码">{{
            channelViewRow.passwordConfigured ? '已配置' : '未配置'
          }}</NDescriptionsItem>
          <NDescriptionsItem label="分页大小">{{ channelViewRow.pageSize || 200 }}</NDescriptionsItem>
          <NDescriptionsItem label="指定场景">{{ listText(channelViewRow.sceneIds) }}</NDescriptionsItem>
          <NDescriptionsItem label="轮询周期">{{
            channelViewRow.pollIntervalSeconds ? `${channelViewRow.pollIntervalSeconds} 秒` : '--'
          }}</NDescriptionsItem>
          <NDescriptionsItem label="超时时间">{{
            channelViewRow.timeoutSeconds ? `${channelViewRow.timeoutSeconds} 秒` : '--'
          }}</NDescriptionsItem>
          <template v-if="isRfidCallbackChannel(channelViewRow)">
            <NDescriptionsItem label="回调自检">
              <NTag :type="callbackProbeTagType(channelViewRow.probeStatus)" size="small" :bordered="false">
                {{ callbackProbeStatusText(channelViewRow.probeStatus) }}
              </NTag>
            </NDescriptionsItem>
            <NDescriptionsItem label="完成时间">{{
              formatDateTime(channelViewRow.probeCompletedAt)
            }}</NDescriptionsItem>
            <NDescriptionsItem label="耗时">{{ callbackProbeDurationText(channelViewRow) }}</NDescriptionsItem>
            <NDescriptionsItem label="结果编码">{{ channelViewRow.probeResultCode || '--' }}</NDescriptionsItem>
            <NDescriptionsItem label="自检说明" :span="2">{{ channelViewRow.probeMessage || '--' }}</NDescriptionsItem>
          </template>
        </template>
        <template v-else-if="channelViewRow.type === 'TCP_CLIENT'">
          <NDescriptionsItem label="串口服务器地址">{{ channelViewRow.host || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="TCP 端口">{{ channelViewRow.port ?? '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="连接配置" :span="2">{{
            customConnectorEndpoint(channelViewRow)
          }}</NDescriptionsItem>
          <NDescriptionsItem label="连接超时">{{
            channelViewRow.timeoutSeconds ? `${channelViewRow.timeoutSeconds} 秒` : '--'
          }}</NDescriptionsItem>
        </template>
        <NDescriptionsItem v-if="!channelViewRow.builtIn" label="备注" :span="2">{{
          channelViewRow.remarks || '--'
        }}</NDescriptionsItem>
      </NDescriptions>
    </HPageView>

    <HPageView v-model:show="historySyncViewShow" title="历史同步任务详情" width="60%" :show-action="false">
      <NDescriptions v-if="historySyncViewRow" label-placement="left" :column="2" bordered size="small">
        <NDescriptionsItem label="采集通道">{{
          historySyncViewRow.connectorName || channelName(historySyncViewRow.connectorId)
        }}</NDescriptionsItem>
        <NDescriptionsItem label="所属网关">{{
          historySyncViewRow.gatewayName || gatewayName(historySyncViewRow.gatewayId)
        }}</NDescriptionsItem>
        <NDescriptionsItem label="同步协议">{{
          historySyncViewRow.protocolName || protocolName(historySyncViewRow.protocolCode)
        }}</NDescriptionsItem>
        <NDescriptionsItem label="接口路径">{{ historySyncViewRow.routePattern || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="状态">{{ historySyncStatusText(historySyncViewRow.taskStatus) }}</NDescriptionsItem>
        <NDescriptionsItem label="开始时间"
          ><span style="white-space: nowrap">{{ formatDateTime(historySyncViewRow.fromTime) }}</span></NDescriptionsItem
        >
        <NDescriptionsItem label="结束时间"
          ><span style="white-space: nowrap">{{ formatDateTime(historySyncViewRow.toTime) }}</span></NDescriptionsItem
        >
        <NDescriptionsItem label="设备 / 指标"
          >{{ historySyncViewRow.deviceCount || 0 }} / {{ historySyncViewRow.metricCount || 0 }}</NDescriptionsItem
        >
        <NDescriptionsItem label="上游记录">{{ historySyncViewRow.fetchedCount || 0 }}</NDescriptionsItem>
        <NDescriptionsItem label="新增 / 跳过">
          {{ historySyncViewRow.insertedCount || 0 }} / {{ historySyncViewRow.skippedCount || 0 }}
        </NDescriptionsItem>
        <NDescriptionsItem label="开始执行"
          ><span style="white-space: nowrap">{{
            formatDateTime(historySyncViewRow.startedAt)
          }}</span></NDescriptionsItem
        >
        <NDescriptionsItem label="完成时间"
          ><span style="white-space: nowrap">{{
            formatDateTime(historySyncViewRow.completedAt)
          }}</span></NDescriptionsItem
        >
        <NDescriptionsItem label="失败原因" :span="2">{{ historySyncViewRow.errorMessage || '--' }}</NDescriptionsItem>
      </NDescriptions>
    </HPageView>

    <HPageView v-model:show="routeViewShow" title="采集路由详情" width="60%" :show-action="false">
      <NDescriptions v-if="routeViewRow" label-placement="left" :column="2" bordered size="small">
        <NDescriptionsItem label="采集通道">{{ channelName(routeViewRow.connectorId) }}</NDescriptionsItem>
        <NDescriptionsItem label="关联网关">{{ gatewayName(routeViewRow.gatewayId) }}</NDescriptionsItem>
        <NDescriptionsItem label="传输方式">{{ routeViewRow.transport || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="优先级">{{ routeViewRow.priority ?? 0 }}</NDescriptionsItem>
        <NDescriptionsItem label="地址模式" :span="2">{{ routeViewRow.routePattern || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="业务协议" :span="2">{{ protocolName(routeViewRow.protocolCode) }}</NDescriptionsItem>
        <NDescriptionsItem label="辨识条件">{{ matchTypeText(routeViewRow.matchType) }}</NDescriptionsItem>
        <NDescriptionsItem label="辨识字段">{{ routeViewRow.matchField || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="辨识值" :span="2">{{ routeViewRow.matchValue || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="协议配置" :span="2">{{ routeViewRow.protocolConfig || '--' }}</NDescriptionsItem>
      </NDescriptions>
    </HPageView>

    <HPageView v-model:show="problemViewShow" title="待识别 / 死信详情" width="60%" :show-action="false">
      <NDescriptions
        v-if="problemViewRow"
        label-placement="left"
        label-style="width: 120px; white-space: normal; word-break: break-all;"
        :column="2"
        bordered
        size="small"
      >
        <NDescriptionsItem label="接收时间"
          ><span style="white-space: nowrap">{{ formatDateTime(problemViewRow.receivedAt) }}</span></NDescriptionsItem
        >
        <NDescriptionsItem label="状态">{{ problemViewRow.status || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="失败阶段">{{ problemViewRow.failureStage || 'ROUTE' }}</NDescriptionsItem>
        <NDescriptionsItem label="失败编码">{{ problemViewRow.failureCode || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="网关">{{
          problemViewRow.gatewayName || gatewayName(problemViewRow.gatewayId)
        }}</NDescriptionsItem>
        <NDescriptionsItem label="重试次数">{{ problemViewRow.attempts ?? 0 }}</NDescriptionsItem>
        <NDescriptionsItem label="业务协议" :span="2">{{
          protocolName(problemViewRow.protocolCode || undefined)
        }}</NDescriptionsItem>
        <NDescriptionsItem label="外部设备标识" :span="2">{{
          problemViewRow.externalDeviceId || '--'
        }}</NDescriptionsItem>
        <NDescriptionsItem label="Topic / 路径" :span="2">{{ problemViewRow.route || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="失败原因" :span="2">{{ problemViewRow.errorMessage || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="完整报文" :span="2">
          <NSpin :show="problemViewLoading" class="problem-payload">
            <NSpace v-if="problemViewDetail" vertical :size="8" style="width: 100%">
              <NSpace align="center" :size="8">
                <NTag size="small" :bordered="false">{{ problemViewDetail.payloadEncoding }}</NTag>
                <NText depth="3">{{ problemViewDetail.payloadSize }} 字节</NText>
                <NButton size="tiny" text type="primary" @click="copyProblemPayload">复制</NButton>
              </NSpace>
              <NInput
                :value="problemViewDetail.payload"
                type="textarea"
                readonly
                :autosize="{ minRows: 8, maxRows: 20 }"
              />
            </NSpace>
            <NText v-else-if="!problemViewLoading" depth="3">未读取到完整报文</NText>
          </NSpin>
        </NDescriptionsItem>
      </NDescriptions>
    </HPageView>

    <HPageForm
      v-model:show="connectorShow"
      :title="connectorForm.id ? '编辑采集通道' : '新增采集通道'"
      :model="connectorForm"
      :rules="connectorRules"
      :ref-table="channelTableRef"
      :handle-submit="saveConnector"
      label-placement="left"
      label-width="110"
      width="60%"
    >
      <NFormItem label="通道类型" path="type">
        <NSelect
          v-model:value="connectorForm.type"
          :options="connectorTypeOptions"
          :disabled="Boolean(connectorForm.id)"
        />
      </NFormItem>
      <NAlert type="info" :bordered="false">
        客户端/服务端均以采集平台为参照：客户端由平台发起连接；服务端由平台监听并等待设备连接。
      </NAlert>
      <NFormItem label="通道名称" path="name"
        ><NInput v-model:value="connectorForm.name" placeholder="如 一号库房三江消防串口服务器"
      /></NFormItem>
      <NFormItem label="系统标识" path="code">
        <NSpace vertical :size="4" style="width: 100%">
          <NInput
            v-model:value="connectorForm.code"
            :disabled="Boolean(connectorForm.id)"
            placeholder="可选；留空由系统自动生成"
          />
          <NText v-if="!connectorForm.id" depth="3">普通通道无需填写；仅固定集成或自动化脚本需要指定。</NText>
        </NSpace>
      </NFormItem>
      <NFormItem label="采集方式" path="acquisitionMode">
        <NSelect v-model:value="connectorForm.acquisitionMode" :options="acquisitionOptions" />
      </NFormItem>
      <template v-if="connectorForm.type === 'MQTT_CLIENT'">
        <NFormItem label="消息服务器地址" path="brokerUri"
          ><NInput v-model:value="connectorForm.brokerUri" placeholder="tcp://broker.example.com:1883"
        /></NFormItem>
        <NFormItem label="订阅 Topic" path="topic"
          ><NInput v-model:value="connectorForm.topic" placeholder="/gateway/+/uplink"
        /></NFormItem>
        <NFormItem label="Client ID"><NInput v-model:value="connectorForm.clientId" /></NFormItem>
        <NFormItem label="用户名"><NInput v-model:value="connectorForm.username" /></NFormItem>
        <NFormItem label="密码"
          ><NInput
            v-model:value="connectorForm.password"
            type="password"
            show-password-on="click"
            :placeholder="connectorForm.passwordConfigured ? '留空保持原密码' : '可选'"
        /></NFormItem>
        <NFormItem label="QoS"><NInputNumber v-model:value="connectorForm.qos" :min="0" :max="2" /></NFormItem>
        <NAlert type="info" :bordered="false">
          本系统作为 MQTT 客户端连接并订阅外部 MQTT 消息服务器（Broker）；若设备直接连接本系统，请使用系统内置 MQTT
          服务端通道。
        </NAlert>
      </template>
      <template v-else-if="connectorForm.type === 'HTTP_CLIENT'">
        <NFormItem label="接口地址" path="baseUrl"
          ><NInput v-model:value="connectorForm.baseUrl" placeholder="https://vendor.example.com/api/data"
        /></NFormItem>
        <NFormItem label="用户名" path="username"><NInput v-model:value="connectorForm.username" /></NFormItem>
        <NFormItem label="密码"
          ><NInput
            v-model:value="connectorForm.password"
            type="password"
            show-password-on="click"
            :placeholder="connectorForm.passwordConfigured ? '留空保持原密码' : '必填'"
        /></NFormItem>
        <NFormItem label="分页大小"
          ><NInputNumber v-model:value="connectorForm.pageSize" :min="1" :max="500"
        /></NFormItem>
        <NFormItem label="指定场景 ID">
          <NSelect
            v-model:value="connectorForm.sceneIds"
            multiple
            filterable
            tag
            :options="[]"
            placeholder="留空自动采集全部场景"
          />
        </NFormItem>
        <NFormItem label="轮询周期"
          ><NInputNumber v-model:value="connectorForm.pollIntervalSeconds" :min="5" /><NText depth="3">
            秒</NText
          ></NFormItem
        >
        <NFormItem label="超时时间"
          ><NInputNumber v-model:value="connectorForm.timeoutSeconds" :min="1" /><NText depth="3"> 秒</NText></NFormItem
        >
      </template>
      <template v-else-if="connectorForm.type === 'TCP_CLIENT'">
        <NFormItem label="串口服务器地址" path="host">
          <NInput v-model:value="connectorForm.host" placeholder="固定 IP 或采集服务器可解析的 DNS 名称" />
        </NFormItem>
        <NFormItem label="TCP 端口" path="port">
          <NInputNumber v-model:value="connectorForm.port" :min="1" :max="65535" :precision="0" style="width: 100%" />
        </NFormItem>
        <NFormItem v-if="connectorForm.acquisitionMode === 'POLL'" label="轮询周期">
          <NInputNumber v-model:value="connectorForm.pollIntervalSeconds" :min="5" />
          <NText depth="3"> 秒</NText>
        </NFormItem>
        <NFormItem label="连接超时">
          <NInputNumber v-model:value="connectorForm.timeoutSeconds" :min="1" :max="300" />
          <NText depth="3"> 秒</NText>
        </NFormItem>
        <NAlert type="info" :bordered="false">
          本系统作为 TCP 客户端连接串口服务器，串口服务器应配置为 TCP Server；“被动接收”表示建连后等待设备事件。
        </NAlert>
      </template>
      <NFormItem label="启用"><NSwitch v-model:value="connectorForm.enabled" /></NFormItem>
      <NFormItem label="备注"><NInput v-model:value="connectorForm.remarks" type="textarea" :rows="2" /></NFormItem>
    </HPageForm>

    <HPageForm
      v-model:show="historySyncShow"
      title="创建历史同步任务"
      :model="historySyncForm"
      :rules="historySyncRules"
      :ref-table="historySyncTableRef"
      :handle-submit="saveHistorySyncTask"
      label-placement="left"
      label-width="110"
      width="60%"
    >
      <NFormItem label="所属网关" path="gatewayId">
        <NSelect
          v-model:value="historySyncForm.gatewayId"
          :options="gatewayOptions"
          filterable
          @update:value="syncHistoryGateway"
        />
      </NFormItem>
      <NFormItem label="HTTP 采集通道" path="connectorId">
        <NSelect
          v-model:value="historySyncForm.connectorId"
          :options="historyConnectorOptions"
          filterable
          placeholder="请选择已配置历史同步路由的通道"
          @update:value="syncHistoryConnector"
        >
          <template #empty><NEmpty size="small" description="该网关暂无支持历史同步的 HTTP 通道" /></template>
        </NSelect>
      </NFormItem>
      <NFormItem label="同步协议" path="routeId">
        <NSelect
          v-model:value="historySyncForm.routeId"
          :options="historyRouteOptions"
          filterable
          placeholder="请选择需要回采的协议与接口"
        >
          <template #empty><NEmpty size="small" description="该通道暂无支持历史同步的协议路由" /></template>
        </NSelect>
      </NFormItem>
      <NFormItem label="同步时间范围">
        <NDatePicker v-model:value="historySyncRange" type="datetimerange" clearable style="width: 100%" />
      </NFormItem>
    </HPageForm>

    <HPageForm
      v-model:show="routeShow"
      :title="routeForm.id ? '编辑采集路由' : '新增采集路由'"
      :model="routeForm"
      :rules="routeRules"
      :ref-table="routeTableRef"
      :handle-submit="saveRoute"
      label-placement="left"
      label-width="110"
      width="60%"
    >
      <NFormItem label="关联网关" path="gatewayId">
        <NSelect v-model:value="routeForm.gatewayId" :options="gatewayOptions" filterable />
      </NFormItem>
      <NFormItem label="采集通道" path="connectorId">
        <NSelect
          v-model:value="routeForm.connectorId"
          :options="channelOptions"
          filterable
          @update:value="syncRouteTransport"
        />
      </NFormItem>
      <NFormItem label="业务协议" path="protocolCode"
        ><NSelect
          v-model:value="routeForm.protocolCode"
          :options="protocolOptions"
          filterable
          @update:value="applyProtocolDefaults"
      /></NFormItem>
      <NFormItem label="传输方式"><NInput :value="routeForm.transport || ''" disabled /></NFormItem>
      <NFormItem label="地址模式" path="routePattern"
        ><NInput v-model:value="routeForm.routePattern" placeholder="MQTT 如 /vendor/uplink/#；HTTP 如 /vendor/data"
      /></NFormItem>
      <NFormItem label="辨识条件"
        ><NSelect v-model:value="routeForm.matchType" :options="matchTypeOptions"
      /></NFormItem>
      <NFormItem v-if="routeForm.matchType && routeForm.matchType !== 'NONE'" label="辨识字段">
        <NInput
          v-model:value="routeForm.matchField"
          :placeholder="routeForm.matchType === 'JSON_FIELD_EQUALS' ? '如 data.model' : '元数据键；前缀条件可留空'"
        />
      </NFormItem>
      <NFormItem v-if="routeForm.matchType && routeForm.matchType !== 'NONE'" label="辨识值" path="matchValue"
        ><NInput v-model:value="routeForm.matchValue"
      /></NFormItem>
      <NFormItem label="优先级"><NInputNumber v-model:value="routeForm.priority" :min="-1000" :max="1000" /></NFormItem>
      <NFormItem label="协议配置"
        ><NInput
          v-model:value="routeForm.protocolConfig"
          type="textarea"
          :rows="3"
          :placeholder="`如 ${DEFAULT_PROTOCOL_TIME_CONFIG}`"
      /></NFormItem>
    </HPageForm>

    <NModal
      v-model:show="connectionShow"
      preset="card"
      title="设备接入参数"
      style="width: min(860px, calc(100vw - 32px))"
    >
      <NDescriptions
        v-if="mqttConnection"
        label-placement="left"
        label-style="width: 128px; min-width: 128px; white-space: nowrap"
        :column="1"
        bordered
        size="small"
      >
        <NDescriptionsItem label="Server URI 候选">
          <NSpace v-if="mqttConnection.serverUris.length" vertical size="small">
            <NText v-for="uri in mqttConnection.serverUris" :key="uri" code>{{ uri }}</NText>
          </NSpace>
          <NText v-else depth="3">未发现非回环、非链路本地的网卡地址</NText>
        </NDescriptionsItem>
        <NDescriptionsItem label="Client ID">{{ mqttConnection.clientId }}</NDescriptionsItem>
        <NDescriptionsItem label="Username">{{ mqttConnection.username }}</NDescriptionsItem>
        <NDescriptionsItem label="Password">
          <NSpace align="center" :wrap="false">
            <NInput
              :value="mqttConnection.password"
              :placeholder="mqttConnection.passwordConfigured ? '已配置，密码不回显' : '未配置'"
              readonly
              style="width: 360px"
            />
            <NButton size="tiny" secondary @click="openMqttPasswordForm">修改</NButton>
          </NSpace>
        </NDescriptionsItem>
        <NDescriptionsItem label="MQTT 版本支持">{{ mqttVersionSupportText(mqttConnection) }}</NDescriptionsItem>
        <NDescriptionsItem label="协议发布参数">
          <NDataTable
            :columns="mqttPublishProfileColumns"
            :data="mqttConnection.publishProfiles"
            :pagination="false"
            :scroll-x="620"
            size="small"
          />
        </NDescriptionsItem>
      </NDescriptions>
    </NModal>

    <NModal
      v-model:show="mqttPasswordShow"
      preset="card"
      title="修改 MQTT 密码"
      style="width: min(520px, calc(100vw - 32px))"
    >
      <NAlert type="warning" :bordered="false" style="margin-bottom: 16px">
        保存后旧 Password 将无法用于重新连接；请同步更新网关设备的 MQTT 配置并主动重连。
      </NAlert>
      <NForm label-placement="left" label-width="90">
        <NFormItem label="新密码">
          <NInput
            v-model:value="mqttPasswordForm.password"
            type="password"
            show-password-on="click"
            :maxlength="64"
            placeholder="请输入 8～64 个字符"
          />
        </NFormItem>
        <NFormItem label="确认密码">
          <NInput
            v-model:value="mqttPasswordForm.confirmPassword"
            type="password"
            show-password-on="click"
            :maxlength="64"
            placeholder="请再次输入新密码"
            @keyup.enter="saveMqttPassword"
          />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="mqttPasswordShow = false">取消</NButton>
          <NButton type="primary" :loading="mqttPasswordSaving" @click="saveMqttPassword">保存</NButton>
        </NSpace>
      </template>
    </NModal>

    <NModal v-model:show="matchShow" preset="card" title="路由匹配测试" style="width: 760px">
      <NForm label-placement="left" label-width="120">
        <NFormItem label="采集通道"
          ><NSelect
            v-model:value="matchForm.connectorId"
            :options="channelOptions"
            filterable
            @update:value="syncMatchTransport"
        /></NFormItem>
        <NFormItem label="实际地址"
          ><NInput v-model:value="matchForm.actualRoute" placeholder="实际 Topic 或 HTTP 路径"
        /></NFormItem>
        <NFormItem label="报文"><NInput v-model:value="matchForm.payload" type="textarea" :rows="5" /></NFormItem>
        <NFormItem label="报文编码"
          ><NSelect v-model:value="matchForm.payloadEncoding" :options="payloadEncodingOptions"
        /></NFormItem>
      </NForm>
      <NAlert v-if="matchResult" :type="matchResult.matched ? 'success' : 'warning'" :bordered="false">
        {{
          matchResult.matched
            ? `命中协议 ${matchResult.protocolCode}，网关 ${gatewayName(matchResult.gatewayId)}`
            : '没有路由命中'
        }}
      </NAlert>
      <template #footer
        ><NSpace justify="end"
          ><NButton @click="matchShow = false">关闭</NButton
          ><NButton type="primary" @click="testRoute">测试</NButton></NSpace
        ></template
      >
    </NModal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch, type VNodeChild } from 'vue';
import dayjs from 'dayjs';
import {
  NAlert,
  NButton,
  NDataTable,
  NDatePicker,
  NDescriptions,
  NDescriptionsItem,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NModal,
  NRadio,
  NRadioGroup,
  NSelect,
  NSpace,
  NSpin,
  NSwitch,
  NTabPane,
  NTabs,
  NTag,
  NText,
  type DataTableBaseColumn,
  type DataTableColumns,
  useDialog,
  useMessage,
} from 'naive-ui';
import { cloneDeep } from 'lodash-es';
import { formatDateTime } from '@/utils';
import { gatewayApi } from '../api/gateway';
import { ingestConfigApi } from '../api/ingestConfig';
import {
  connectorTransport,
  connectorTypeLabel,
  protocolSupportsConnector,
  resolveMqttConnectionTarget,
  suggestedRoutePattern,
  updateRoutePatternSuggestion,
} from '../utils/ingestConnection';
import type {
  GatewayVo,
  IngestBuiltInConnectorDefinition,
  IngestCatalog,
  IngestConnectorVo,
  IngestConnectorProbeStatus,
  IngestConnectorProbeVo,
  IngestInboxDetailVo,
  IngestInboxSummaryVo,
  IngestHistorySyncTaskVo,
  IngestRouteMatchResultVo,
  IngestRouteMatchTestVo,
  IngestRouteVo,
  IngestNetworkAddressDefinition,
  MqttClientConnectionVo,
} from '../typings';

type ChannelRow = Omit<IngestConnectorVo, 'type'> &
  Omit<Partial<IngestBuiltInConnectorDefinition>, 'type'> & {
    type?: IngestConnectorVo['type'] | IngestBuiltInConnectorDefinition['type'];
    builtIn?: boolean;
    transport?: string;
  };
type PageTableColumn<T> = DataTableBaseColumn<T> & { columnType?: string };
const emptyCatalog: IngestCatalog = {
  protocols: [],
  codecs: [],
  deviceModels: [],
  builtInConnectors: [],
  standardMetrics: [],
};
const activeTab = ref('channels');
const message = useMessage();
const dialog = useDialog();
const channelTableRef = ref<any>(null);
const routeTableRef = ref<any>(null);
const historySyncTableRef = ref<any>(null);
const catalog = ref<IngestCatalog>(emptyCatalog);
const gateways = ref<GatewayVo[]>([]);
const connectors = ref<IngestConnectorVo[]>([]);
const allRoutes = ref<IngestRouteVo[]>([]);
const routeQuery = reactive({ gatewayId: null as string | null });
const connectorShow = ref(false);
const channelViewShow = ref(false);
const channelViewTitle = ref('采集通道详情');
const channelViewRow = ref<ChannelRow | null>(null);
const connectorProbeTestingId = ref<string | null>(null);
const connectorDefaults: IngestConnectorVo = {
  type: 'MQTT_CLIENT',
  acquisitionMode: 'PUSH',
  enabled: true,
  qos: 1,
  pageSize: 200,
  sceneIds: [],
  pollIntervalSeconds: 60,
  timeoutSeconds: 15,
};
const connectorForm = ref<IngestConnectorVo>(cloneDeep(connectorDefaults));
const routeShow = ref(false);
const routeViewShow = ref(false);
const routeViewRow = ref<IngestRouteVo | null>(null);
const routeForm = ref<IngestRouteVo>({ matchType: 'NONE', priority: 0 });
const routePatternSuggestion = ref<string | null>(null);
const connectionShow = ref(false);
const mqttConnection = ref<MqttClientConnectionVo | null>(null);
const mqttPasswordShow = ref(false);
const mqttPasswordSaving = ref(false);
const mqttPasswordForm = reactive({ password: '', confirmPassword: '' });
const problemTableRef = ref<any>(null);
const problemCount = ref(0);
const problemDeleting = ref(false);
const checkedProblemIds = ref<Array<string | number>>([]);
const problemViewShow = ref(false);
const problemViewRow = ref<IngestInboxSummaryVo | null>(null);
const problemViewDetail = ref<IngestInboxDetailVo | null>(null);
const problemViewLoading = ref(false);
const historySyncShow = ref(false);
const historySyncViewShow = ref(false);
const historySyncViewRow = ref<IngestHistorySyncTaskVo | null>(null);
const historySyncDeletingId = ref<string | null>(null);
const historySyncForm = ref<IngestHistorySyncTaskVo>({});
const historySyncRange = ref<[number, number] | null>(null);
const DEFAULT_PROTOCOL_TIME_CONFIG = '{"sourceTimeZone":"Asia/Shanghai","numericTimestampMode":"INSTANT"}';
const historyRoutes = ref<IngestRouteVo[]>([]);
const matchShow = ref(false);
const matchForm = ref<IngestRouteMatchTestVo>({ transport: 'MQTT', payloadEncoding: 'TEXT', metadata: {} });
const matchResult = ref<IngestRouteMatchResultVo | null>(null);

function responseList<T>(response: any): T[] {
  const data = response?.data?.data ?? response?.data ?? response;
  return Array.isArray(data) ? data : (data?.list ?? []);
}
function pageResponse<T>(list: T[], params: any) {
  const size = Number(params.pageSize || 20);
  const page = Number(params.pageNum || 1);
  return {
    data: {
      list: list.slice((page - 1) * size, page * size),
      total: list.length,
      pages: Math.max(1, Math.ceil(list.length / size)),
    },
  };
}
const gatewayOptions = computed(() =>
  gateways.value.filter((item) => item.id).map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id! })),
);
const channels = computed<ChannelRow[]>(() => [
  ...catalog.value.builtInConnectors.map((item: IngestBuiltInConnectorDefinition) => ({
    ...item,
    acquisitionMode: 'PUSH' as const,
    builtIn: true,
  })),
  ...connectors.value.map((item) => ({
    ...item,
    transport: connectorTransport(item.type),
  })),
]);
const channelOptions = computed(() =>
  channels.value.map((item) => ({ label: item.name || item.code!, value: item.code! })),
);
const httpConnectorOptions = computed(() =>
  connectors.value
    .filter((item) => item.type === 'HTTP_CLIENT')
    .map((item) => ({ label: item.name || item.code!, value: item.code! })),
);
const historyProtocolCodes = computed(
  () => new Set(catalog.value.protocols.filter((item) => item.historySyncSupported).map((item) => item.code)),
);
const historyCompatibleRoutes = computed(() =>
  historyRoutes.value.filter(
    (route) =>
      route.id &&
      route.transport === 'HTTP' &&
      Boolean(route.protocolCode) &&
      historyProtocolCodes.value.has(route.protocolCode!),
  ),
);
const historyConnectorOptions = computed(() => {
  const available = new Set(historyCompatibleRoutes.value.map((route) => route.connectorId));
  return httpConnectorOptions.value.filter((item) => available.has(item.value));
});
const historyRouteOptions = computed(() =>
  historyCompatibleRoutes.value
    .filter((route) => route.connectorId === historySyncForm.value.connectorId)
    .map((route) => ({
      label: `${protocolName(route.protocolCode)}（${route.routePattern || '--'}）`,
      value: route.id!,
    })),
);
const protocolOptions = computed(() =>
  catalog.value.protocols
    .filter((item) => !routeForm.value.transport || item.transports.includes(routeForm.value.transport))
    .filter((item) => protocolSupportsConnector(routeForm.value.connectorId, item))
    .map((item) => ({ label: `${item.vendor} / ${item.name} / ${item.version}`, value: item.code })),
);
const connectorTypeOptions = [
  { label: connectorTypeLabel('MQTT_CLIENT'), value: 'MQTT_CLIENT' },
  { label: connectorTypeLabel('HTTP_CLIENT'), value: 'HTTP_CLIENT' },
  { label: connectorTypeLabel('TCP_CLIENT'), value: 'TCP_CLIENT' },
];
const acquisitionOptions = [
  { label: '被动接收', value: 'PUSH' },
  { label: '主动轮询', value: 'POLL' },
];
const matchTypeOptions = [
  { label: '仅按地址', value: 'NONE' },
  { label: 'JSON 字段等于', value: 'JSON_FIELD_EQUALS' },
  { label: '消息头 / 元数据等于', value: 'METADATA_EQUALS' },
  { label: '二进制魔数', value: 'PAYLOAD_PREFIX_HEX' },
  { label: '文本前缀', value: 'PAYLOAD_PREFIX_TEXT' },
];
const payloadEncodingOptions = ['TEXT', 'BASE64', 'HEX'].map((value) => ({ label: value, value }));
const connectorRules = {
  type: { required: true, message: '请选择通道类型', trigger: 'change' },
  name: { required: true, message: '请输入通道名称', trigger: ['input', 'blur'] },
  code: {
    validator: (_rule: unknown, value: unknown) =>
      typeof value !== 'string' || !value.trim() || /^[A-Za-z0-9_-]{3,64}$/.test(value.trim()),
    message: '系统标识只能包含字母、数字、下划线和中划线，长度为 3～64 位',
    trigger: ['input', 'blur'],
  },
  host: {
    validator: (_rule: unknown, value: unknown) =>
      connectorForm.value.type !== 'TCP_CLIENT' || (typeof value === 'string' && Boolean(value.trim())),
    message: '请输入串口服务器地址',
    trigger: ['input', 'blur'],
  },
  port: {
    validator: (_rule: unknown, value: unknown) =>
      connectorForm.value.type !== 'TCP_CLIENT' ||
      (typeof value === 'number' && Number.isInteger(value) && value >= 1 && value <= 65535),
    message: '请输入 1～65535 的 TCP 端口',
    trigger: ['input', 'blur'],
  },
};
const routeRules = {
  connectorId: { required: true, message: '请选择采集通道', trigger: 'change' },
  gatewayId: { required: true, message: '请选择网关', trigger: 'change' },
  routePattern: { required: true, message: '请输入地址模式', trigger: ['input', 'blur'] },
  protocolCode: { required: true, message: '请选择业务协议', trigger: 'change' },
};
const historySyncRules = {
  gatewayId: { required: true, message: '请选择历史数据所属网关', trigger: 'change' },
  connectorId: { required: true, message: '请选择 HTTP 采集通道', trigger: 'change' },
  routeId: { required: true, message: '请选择历史同步协议', trigger: 'change' },
};

function channelTypeText(row: ChannelRow) {
  return connectorTypeLabel(row.type);
}
function acquisitionText(mode?: string) {
  return mode === 'POLL' ? '主动轮询' : '被动接收';
}
function matchTypeText(type?: string | null) {
  return matchTypeOptions.find((item) => item.value === (type || 'NONE'))?.label || type || '仅按地址';
}
function channelStatusText(row: ChannelRow) {
  return row.builtIn ? (row.enabled ? '已注册' : '未启用') : row.enabled === false ? '停用' : '启用';
}
function isRfidCallbackChannel(row: ChannelRow) {
  return !row.builtIn && row.code === 'COLLECTION_RFID_CALLBACK';
}
function callbackProbeStatusText(status?: IngestConnectorProbeStatus | null) {
  return (
    ({ PENDING: '等待执行', RUNNING: '执行中', SUCCESS: '通过', FAILED: '失败' } as Record<string, string>)[
      status || ''
    ] || '未测试'
  );
}
function callbackProbeTagType(status?: IngestConnectorProbeStatus | null) {
  if (status === 'SUCCESS') return 'success' as const;
  if (status === 'FAILED') return 'error' as const;
  if (status === 'PENDING' || status === 'RUNNING') return 'warning' as const;
  return 'default' as const;
}
function callbackProbeDurationText(row: Pick<ChannelRow, 'probeDurationMillis'>) {
  return typeof row.probeDurationMillis === 'number' ? `${row.probeDurationMillis} 毫秒` : '--';
}
function historySyncStatusText(status?: string) {
  return (
    ({ PENDING: '等待执行', PROCESSING: '执行中', SUCCESS: '已完成', FAILED: '失败' } as Record<string, string>)[
      status || ''
    ] ||
    status ||
    '--'
  );
}
function listText(value?: string[]) {
  return value?.length ? value.join(' / ') : '--';
}
function mqttVersionSupportText(connection: MqttClientConnectionVo) {
  const versions = connection.supportedVersions?.filter(Boolean) || [];
  return versions.length ? `自动适配（${versions.join(' / ')}）` : '自动适配';
}
function builtInBindEndpoint(row: ChannelRow) {
  return row.bindHost && row.bindPort ? `${row.bindHost}:${row.bindPort}` : '--';
}
function socketHost(address: string) {
  return address.includes(':') ? `[${address}]` : address;
}
function customConnectorEndpoint(row: ChannelRow) {
  if (row.type !== 'TCP_CLIENT') return row.brokerUri || row.baseUrl || '--';
  return `${row.host ? socketHost(row.host) : '--'}:${row.port ?? '--'}`;
}
function networkAddressFlags(item: IngestNetworkAddressDefinition) {
  return [
    item.loopback ? '回环' : '',
    item.linkLocal ? '链路本地' : '',
    item.siteLocal ? '内网' : '',
    item.multicast ? '组播' : '',
    item.virtual ? '虚拟' : '',
    item.pointToPoint ? '点对点' : '',
  ]
    .filter(Boolean)
    .join(' / ');
}
function networkAddressLabel(row: ChannelRow, item: IngestNetworkAddressDefinition) {
  const endpoint = `${socketHost(item.address)}:${row.bindPort ?? '--'}`;
  const flags = networkAddressFlags(item);
  return `${item.interfaceName}（${item.interfaceDisplayName}） · ${item.family} · ${endpoint}${flags ? ` · ${flags}` : ''}`;
}
function builtInNetworkText(row: ChannelRow) {
  return (
    row.networkAddresses
      ?.map((item) => `${item.interfaceName} ${socketHost(item.address)}:${row.bindPort ?? '--'}`)
      .join('；') || '--'
  );
}
function channelByCode(code?: string) {
  return channels.value.find((item) => item.code === code);
}
function routeConnectionTarget(row: IngestRouteVo) {
  const protocol = catalog.value.protocols.find((item) => item.code === row.protocolCode);
  return resolveMqttConnectionTarget(row, channelByCode(row.connectorId)?.type, protocol);
}

const channelColumns = [
  { title: '通道名称', key: 'name', minWidth: 200, fixed: 'left', columnType: 'title' },
  { title: '通道类型', key: 'type', width: 260, render: (row: ChannelRow) => channelTypeText(row) },
  { title: '传输方式', key: 'transport', width: 100 },
  {
    title: '监听/连接配置',
    key: 'endpoint',
    minWidth: 260,
    ellipsis: { tooltip: true },
    render: (row: ChannelRow) => (row.builtIn ? builtInBindEndpoint(row) : customConnectorEndpoint(row)),
  },
  {
    title: '网卡地址',
    key: 'networkAddresses',
    minWidth: 360,
    ellipsis: { tooltip: true },
    render: (row: ChannelRow) => (row.builtIn ? builtInNetworkText(row) : '--'),
  },
  {
    title: '采集方式',
    key: 'acquisitionMode',
    width: 100,
    render: (row: ChannelRow) => acquisitionText(row.acquisitionMode),
  },
  {
    title: '状态',
    key: 'enabled',
    width: 90,
    render: (row: ChannelRow) =>
      h(
        NTag,
        { type: row.enabled ? 'success' : 'default', size: 'small', bordered: false },
        { default: () => channelStatusText(row) },
      ),
  },
  {
    title: '回调自检',
    key: 'probeStatus',
    width: 110,
    render: (row: ChannelRow) =>
      isRfidCallbackChannel(row)
        ? h(
            NTag,
            { type: callbackProbeTagType(row.probeStatus), size: 'small', bordered: false },
            { default: () => callbackProbeStatusText(row.probeStatus) },
          )
        : '--',
  },
  {
    title: '操作',
    key: 'actions',
    width: 360,
    fixed: 'right',
    render: (row: ChannelRow) => {
      const actions: VNodeChild[] = [];
      if (row.type === 'MQTT_CLIENT') {
        actions.push(
          h(
            NButton,
            { text: true, type: 'primary', size: 'small', onClick: () => openUpstreamBrokerConnection(row) },
            { default: () => 'MQTT 连接参数' },
          ),
        );
      }
      if (!row.builtIn) {
        if (isRfidCallbackChannel(row)) {
          actions.push(
            h(
              NButton,
              {
                text: true,
                type: 'primary',
                size: 'small',
                loading: connectorProbeTestingId.value === row.id,
                disabled: !row.id || connectorProbeTestingId.value !== null,
                onClick: () => testRfidCallback(row),
              },
              { default: () => '测试回调' },
            ),
          );
        }
        actions.push(
          h(NButton, { text: true, size: 'small', onClick: () => openConnectorForm(row) }, { default: () => '编辑' }),
          h(
            NButton,
            { text: true, type: 'error', size: 'small', onClick: () => removeConnector(row) },
            { default: () => '删除' },
          ),
        );
      }
      return actions.length ? h(NSpace, { size: 8, wrap: false }, () => actions) : '--';
    },
  },
];
const routeColumns = [
  {
    title: '采集通道',
    key: 'connectorId',
    minWidth: 180,
    columnType: 'title',
    render: (row: IngestRouteVo) => channelName(row.connectorId),
  },
  { title: '网关', key: 'gatewayId', minWidth: 160, render: (row: IngestRouteVo) => gatewayName(row.gatewayId) },
  { title: '传输', key: 'transport', width: 90 },
  { title: '地址模式', key: 'routePattern', minWidth: 210 },
  {
    title: '业务协议',
    key: 'protocolCode',
    minWidth: 220,
    render: (row: IngestRouteVo) => protocolName(row.protocolCode),
  },
  { title: '辨识条件', key: 'matchType', width: 130, render: (row: IngestRouteVo) => matchTypeText(row.matchType) },
  { title: '优先级', key: 'priority', width: 80 },
  {
    title: '操作',
    key: 'actions',
    width: 310,
    fixed: 'right',
    render: (row: IngestRouteVo) => {
      const actions: VNodeChild[] = [];
      const connectionTarget = routeConnectionTarget(row);
      if (connectionTarget) {
        actions.push(
          h(
            NButton,
            { text: true, type: 'primary', size: 'small', onClick: () => openRouteConnection(row) },
            { default: () => (connectionTarget === 'DEVICE_ACCESS' ? '设备接入参数' : 'MQTT 连接参数') },
          ),
        );
      }
      actions.push(
        h(NButton, { text: true, size: 'small', onClick: () => openRouteForm(row) }, { default: () => '编辑' }),
        h(
          NButton,
          { text: true, type: 'error', size: 'small', onClick: () => removeRoute(row) },
          { default: () => '删除' },
        ),
      );
      return h(NSpace, { size: 8, wrap: false }, () => actions);
    },
  },
];
const historySyncColumns: PageTableColumn<IngestHistorySyncTaskVo>[] = [
  { title: '创建时间', key: 'createTime', width: 190, columnType: 'dateTime', ellipsis: { tooltip: true } },
  {
    title: '所属网关',
    key: 'gatewayName',
    minWidth: 160,
    render: (row: IngestHistorySyncTaskVo) => row.gatewayName || gatewayName(row.gatewayId),
  },
  {
    title: '采集通道',
    key: 'connectorName',
    minWidth: 180,
    columnType: 'title',
    render: (row: IngestHistorySyncTaskVo) => row.connectorName || channelName(row.connectorId),
  },
  {
    title: '同步协议',
    key: 'protocolName',
    minWidth: 190,
    render: (row: IngestHistorySyncTaskVo) => row.protocolName || protocolName(row.protocolCode),
  },
  { title: '同步开始', key: 'fromTime', width: 190, columnType: 'dateTime', ellipsis: { tooltip: true } },
  { title: '同步结束', key: 'toTime', width: 190, columnType: 'dateTime', ellipsis: { tooltip: true } },
  {
    title: '状态',
    key: 'taskStatus',
    width: 110,
    render: (row: IngestHistorySyncTaskVo) =>
      h(
        NTag,
        {
          type:
            row.taskStatus === 'SUCCESS'
              ? 'success'
              : row.taskStatus === 'FAILED'
                ? 'error'
                : row.taskStatus === 'PROCESSING'
                  ? 'warning'
                  : 'default',
          size: 'small',
          bordered: false,
        },
        { default: () => historySyncStatusText(row.taskStatus) },
      ),
  },
  {
    title: '上游记录',
    key: 'fetchedCount',
    width: 100,
    render: (row: IngestHistorySyncTaskVo) => row.fetchedCount || 0,
  },
  { title: '新增', key: 'insertedCount', width: 80, render: (row: IngestHistorySyncTaskVo) => row.insertedCount || 0 },
  { title: '跳过', key: 'skippedCount', width: 80, render: (row: IngestHistorySyncTaskVo) => row.skippedCount || 0 },
  { title: '失败原因', key: 'errorMessage', minWidth: 220, ellipsis: { tooltip: true } },
  {
    title: '操作',
    key: 'actions',
    width: 90,
    fixed: 'right',
    render: (row) =>
      h(
        NButton,
        {
          text: true,
          type: 'error',
          size: 'small',
          loading: historySyncDeletingId.value === row.id,
          disabled: !row.id || row.taskStatus === 'PROCESSING',
          title: row.taskStatus === 'PROCESSING' ? '执行中的任务不能删除' : undefined,
          onClick: () => confirmDeleteHistorySyncTask(row),
        },
        { default: () => '删除' },
      ),
  },
];
const problemColumns: DataTableColumns<IngestInboxSummaryVo> = [
  { type: 'selection', width: 48, fixed: 'left' },
  {
    title: '接收时间',
    key: 'receivedAt',
    width: 190,
    ellipsis: { tooltip: true },
    render: (row) => formatDateTime(row.receivedAt),
  },
  { title: '阶段', key: 'failureStage', width: 100, render: (row) => row.failureStage || 'ROUTE' },
  { title: '网关', key: 'gatewayName', width: 150, render: (row) => row.gatewayName || row.gatewayId || '--' },
  { title: 'Topic / 路径', key: 'route', minWidth: 190, ellipsis: { tooltip: true } },
  {
    title: '报文摘要',
    key: 'payloadPreview',
    minWidth: 260,
    ellipsis: { tooltip: { contentStyle: { width: '30vw' } } },
  },
  { title: '失败原因', key: 'errorMessage', minWidth: 220, ellipsis: { tooltip: true } },
  {
    title: '操作',
    key: 'actions',
    width: 170,
    render: (row) =>
      h(NSpace, { size: 8 }, () => [
        h(
          NButton,
          { text: true, type: 'primary', size: 'small', onClick: () => openProblemView(row) },
          { default: () => '查看' },
        ),
        h(
          NButton,
          { text: true, type: 'warning', size: 'small', onClick: () => replay(row) },
          { default: () => '重放' },
        ),
        h(
          NButton,
          { text: true, type: 'error', size: 'small', onClick: () => confirmDeleteProblems(row) },
          { default: () => '删除' },
        ),
      ]),
  },
];
const mqttPublishProfileColumns: DataTableColumns<MqttClientConnectionVo['publishProfiles'][number]> = [
  { title: '业务协议', key: 'protocolName', minWidth: 180 },
  { title: '发布 Topic', key: 'topic', minWidth: 260, ellipsis: { tooltip: true } },
  { title: 'QoS', key: 'qos', width: 70 },
  { title: 'Keep Alive', key: 'keepAliveSeconds', width: 110, render: (row) => `${row.keepAliveSeconds} 秒` },
];

async function loadBase() {
  const [catalogResp, gatewayResp, connectorResp] = await Promise.all([
    ingestConfigApi.catalog(),
    gatewayApi.page({ pageSize: 0 }),
    ingestConfigApi.connectors(),
  ]);
  catalog.value = catalogResp?.data ?? emptyCatalog;
  gateways.value = responseList<GatewayVo>(gatewayResp);
  connectors.value = responseList<IngestConnectorVo>(connectorResp);
  if (!routeQuery.gatewayId) routeQuery.gatewayId = gateways.value[0]?.id || null;
}
async function fetchChannels(params: any) {
  await loadBase();
  return pageResponse(channels.value, params);
}
async function fetchRoutes(params: any) {
  if (!routeQuery.gatewayId) return pageResponse([], params);
  allRoutes.value = responseList(await ingestConfigApi.routes(routeQuery.gatewayId));
  return pageResponse(allRoutes.value, params);
}
async function fetchHistorySyncTasks(params: any) {
  return await ingestConfigApi.historySyncTasks(params);
}
function channelName(code?: string) {
  return channels.value.find((item) => item.code === code)?.name || code || '--';
}
function gatewayName(id?: string | null) {
  return gateways.value.find((item) => item.id === id)?.name || id || '--';
}
function protocolName(code?: string) {
  return catalog.value.protocols.find((item) => item.code === code)?.name || code || '--';
}

function openChannelView(row: ChannelRow) {
  channelViewTitle.value = '采集通道详情';
  channelViewRow.value = cloneDeep(row);
  channelViewShow.value = true;
}
async function copyChannelCode() {
  const code = channelViewRow.value?.code;
  if (!code) return;
  try {
    await navigator.clipboard.writeText(code);
    message.success('系统标识已复制');
  } catch {
    message.error('复制失败，请手动复制');
  }
}
function probeResponse(response: any): IngestConnectorProbeVo {
  return (response?.data?.data ?? response?.data ?? response) as IngestConnectorProbeVo;
}
function applyProbeResult(id: string, result: IngestConnectorProbeVo) {
  const patch: Partial<IngestConnectorVo> = {
    probeStatus: result.status,
    probeRequestedAt: result.requestedAt,
    probeStartedAt: result.startedAt,
    probeCompletedAt: result.completedAt,
    probeRuntimeInstanceId: result.runtimeInstanceId,
    probeResultCode: result.resultCode,
    probeMessage: result.message,
    probeDurationMillis: result.durationMillis,
  };
  const connector = connectors.value.find((item) => item.id === id);
  if (connector) Object.assign(connector, patch);
  if (channelViewRow.value?.id === id) Object.assign(channelViewRow.value, patch);
}
function waitForProbePoll() {
  return new Promise((resolve) => window.setTimeout(resolve, 500));
}
async function testRfidCallback(row: ChannelRow) {
  if (!row.id || connectorProbeTestingId.value) return;
  connectorProbeTestingId.value = row.id;
  const timeoutAt = Date.now() + Math.max(10_000, (row.timeoutSeconds || 15) * 1000 + 5_000);
  try {
    let result = probeResponse(await ingestConfigApi.requestConnectorProbe(row.id));
    applyProbeResult(row.id, result);
    while (result.status === 'PENDING' || result.status === 'RUNNING') {
      if (Date.now() >= timeoutAt) throw new Error('等待 museum-ingest 执行回调自检超时');
      await waitForProbePoll();
      result = probeResponse(await ingestConfigApi.connectorProbe(row.id));
      applyProbeResult(row.id, result);
    }
    if (result.status === 'SUCCESS') {
      message.success('回调自检通过，请在 5 分钟内执行部署检查');
    } else {
      message.error(result.message || '回调自检失败');
    }
    await channelTableRef.value?.handleRefresh?.();
  } catch (error: any) {
    message.error(error?.message || '回调自检失败');
  } finally {
    connectorProbeTestingId.value = null;
  }
}
function openUpstreamBrokerConnection(row: ChannelRow) {
  if (row.type !== 'MQTT_CLIENT') return;
  channelViewTitle.value = 'MQTT 连接参数';
  channelViewRow.value = cloneDeep(row);
  channelViewShow.value = true;
}
function openRouteView(row: IngestRouteVo) {
  routeViewRow.value = cloneDeep(row);
  routeViewShow.value = true;
}
async function openProblemView(row: IngestInboxSummaryVo) {
  const id = row.id;
  problemViewRow.value = cloneDeep(row);
  problemViewDetail.value = null;
  problemViewShow.value = true;
  problemViewLoading.value = true;
  try {
    const response = await ingestConfigApi.problemMessage(id);
    const detail = response?.data?.data ?? response?.data ?? response;
    if (problemViewRow.value?.id === id) problemViewDetail.value = detail as IngestInboxDetailVo;
  } catch {
    if (problemViewRow.value?.id === id) problemViewDetail.value = null;
  } finally {
    if (problemViewRow.value?.id === id) problemViewLoading.value = false;
  }
}
async function copyProblemPayload() {
  if (!problemViewDetail.value) return;
  try {
    await navigator.clipboard.writeText(problemViewDetail.value.payload);
    message.success('完整报文已复制');
  } catch {
    message.error('复制失败，请手动复制');
  }
}
function openHistorySyncView(row: IngestHistorySyncTaskVo) {
  historySyncViewRow.value = cloneDeep(row);
  historySyncViewShow.value = true;
}

function confirmDeleteHistorySyncTask(row: IngestHistorySyncTaskVo) {
  if (!row.id) return;
  if (row.taskStatus === 'PROCESSING') {
    message.warning('历史同步任务正在执行，请等待执行结束后再删除');
    return;
  }
  const pending = row.taskStatus === 'PENDING';
  dialog.warning({
    title: '删除历史同步任务',
    content: pending
      ? '确认删除这条等待执行的历史同步任务？删除后任务将不会再执行。'
      : '确认删除这条历史同步任务记录？已同步写入的采集数据不会被删除。',
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      historySyncDeletingId.value = row.id!;
      try {
        await ingestConfigApi.deleteHistorySyncTask(row.id!);
        if (historySyncViewRow.value?.id === row.id) {
          historySyncViewShow.value = false;
          historySyncViewRow.value = null;
        }
        message.success('历史同步任务已删除');
        await historySyncTableRef.value?.handleRefresh?.();
      } finally {
        historySyncDeletingId.value = null;
      }
    },
  });
}

async function openHistorySyncForm() {
  const now = dayjs();
  const gatewayId = routeQuery.gatewayId || gatewayOptions.value[0]?.value;
  historySyncForm.value = { gatewayId };
  await loadHistoryRoutes(gatewayId || null);
  historySyncForm.value.connectorId = historyConnectorOptions.value[0]?.value;
  historySyncForm.value.routeId = historyRouteOptions.value[0]?.value;
  historySyncRange.value = [now.subtract(1, 'day').valueOf(), now.valueOf()];
  historySyncShow.value = true;
}
async function loadHistoryRoutes(gatewayId: string | null) {
  historyRoutes.value = gatewayId ? responseList(await ingestConfigApi.routes(gatewayId)) : [];
}
async function syncHistoryGateway(gatewayId: string | null) {
  await loadHistoryRoutes(gatewayId);
  if (!historyConnectorOptions.value.some((item) => item.value === historySyncForm.value.connectorId)) {
    historySyncForm.value.connectorId = historyConnectorOptions.value[0]?.value;
  }
  syncHistoryConnector(historySyncForm.value.connectorId || null);
}
function syncHistoryConnector(connectorId: string | null) {
  historySyncForm.value.connectorId = connectorId || undefined;
  if (!historyRouteOptions.value.some((item) => item.value === historySyncForm.value.routeId)) {
    historySyncForm.value.routeId = historyRouteOptions.value[0]?.value;
  }
}

async function saveHistorySyncTask() {
  if (!historySyncRange.value) throw new Error('请选择同步时间范围');
  const [from, to] = historySyncRange.value;
  await ingestConfigApi.createHistorySyncTask({
    ...historySyncForm.value,
    fromTime: from,
    toTime: to,
  });
  message.success('历史同步任务已创建');
}

function openConnectorForm(row?: ChannelRow) {
  if (row?.builtIn) {
    message.info('内置通道无需业务参数');
    return;
  }
  connectorForm.value = row
    ? { ...cloneDeep(connectorDefaults), ...cloneDeep(row as IngestConnectorVo) }
    : cloneDeep(connectorDefaults);
  connectorShow.value = true;
}
async function saveConnector() {
  await ingestConfigApi.saveConnector({ ...connectorForm.value });
  await loadBase();
}
function removeConnector(row: ChannelRow) {
  if (!row.id) return;
  dialog.warning({
    title: '删除采集通道',
    content: `确认删除“${row.name}”？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      await ingestConfigApi.deleteConnector(row.id!);
      message.success('已删除');
      channelTableRef.value?.handleRefresh();
    },
  });
}

function openRouteForm(row?: IngestRouteVo) {
  routePatternSuggestion.value = null;
  if (row) {
    routeForm.value = cloneDeep(row);
  } else {
    routeForm.value = {
      gatewayId: routeQuery.gatewayId || undefined,
      matchType: 'NONE',
      priority: 0,
    };
    routeForm.value.connectorId = channelOptions.value[0]?.value;
  }
  syncRouteTransport(routeForm.value.connectorId || null);
  routeShow.value = true;
}
function syncRouteTransport(code: string | null) {
  const connector = channels.value.find((item) => item.code === code);
  routeForm.value.transport = connector?.transport as any;
  if (!protocolOptions.value.some((item) => item.value === routeForm.value.protocolCode))
    routeForm.value.protocolCode = undefined;
  applySuggestedRoutePattern(connector, routeForm.value.protocolCode);
}
function applyProtocolDefaults(code: string | null) {
  applySuggestedRoutePattern(channelByCode(routeForm.value.connectorId), code || undefined);
  if (!routeForm.value.protocolConfig?.trim()) {
    routeForm.value.protocolConfig = DEFAULT_PROTOCOL_TIME_CONFIG;
  }
}
function applySuggestedRoutePattern(connector: ChannelRow | undefined, protocolCode: string | undefined) {
  const protocol = catalog.value.protocols.find((item) => item.code === protocolCode);
  const update = updateRoutePatternSuggestion(
    routeForm.value.routePattern,
    routePatternSuggestion.value,
    suggestedRoutePattern(connector, protocol),
  );
  routeForm.value.routePattern = update.routePattern;
  routePatternSuggestion.value = update.trackedSuggestion;
}
async function saveRoute() {
  await ingestConfigApi.saveRoute({ ...routeForm.value });
  routeQuery.gatewayId = routeForm.value.gatewayId || routeQuery.gatewayId;
}
function removeRoute(row: IngestRouteVo) {
  if (!row.id) return;
  dialog.warning({
    title: '删除采集路由',
    content: `确认删除“${row.routePattern}”？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      await ingestConfigApi.deleteRoute(row.id!);
      message.success('已删除');
      routeTableRef.value?.handleRefresh();
    },
  });
}

function openRouteConnection(row: IngestRouteVo) {
  const target = routeConnectionTarget(row);
  if (target === 'UPSTREAM_BROKER') {
    const channel = channelByCode(row.connectorId);
    if (channel) openUpstreamBrokerConnection(channel);
    return;
  }
  if (target === 'DEVICE_ACCESS') void openMqttConnection(row.gatewayId);
}
async function openMqttConnection(gatewayId?: string) {
  if (!gatewayId) return;
  const response = await ingestConfigApi.mqttConnection(gatewayId);
  mqttConnection.value = response?.data || null;
  connectionShow.value = true;
}
function openMqttPasswordForm() {
  mqttPasswordForm.password = '';
  mqttPasswordForm.confirmPassword = '';
  mqttPasswordShow.value = true;
}
async function saveMqttPassword() {
  const connection = mqttConnection.value;
  if (!connection || mqttPasswordSaving.value) return;
  if (mqttPasswordForm.password.length < 8 || mqttPasswordForm.password.length > 64) {
    message.warning('MQTT Password 长度必须为 8～64 个字符');
    return;
  }
  if (mqttPasswordForm.password !== mqttPasswordForm.confirmPassword) {
    message.warning('两次输入的密码不一致');
    return;
  }
  const newPassword = mqttPasswordForm.password;
  mqttPasswordSaving.value = true;
  try {
    const response = await ingestConfigApi.updateMqttPassword(connection.gatewayId, newPassword);
    mqttConnection.value = response?.data || { ...connection, password: newPassword };
    mqttPasswordForm.password = '';
    mqttPasswordForm.confirmPassword = '';
    mqttPasswordShow.value = false;
    message.success('MQTT Password 已修改');
  } finally {
    mqttPasswordSaving.value = false;
  }
}
async function fetchProblems(params: Record<string, unknown>) {
  const response = await ingestConfigApi.problemMessages(params);
  const page = response?.data?.data ?? response?.data ?? response;
  const rows: IngestInboxSummaryVo[] = Array.isArray(page) ? page : (page?.list ?? []);
  problemCount.value = Number(page?.total ?? rows.length);
  const visibleIds = new Set(rows.map((item) => item.id));
  const visibleCheckedIds = checkedProblemIds.value.filter((id) => visibleIds.has(String(id)));
  if (visibleCheckedIds.length !== checkedProblemIds.value.length) {
    problemTableRef.value?.clearCheckedRows?.();
    checkedProblemIds.value = [];
  } else {
    checkedProblemIds.value = visibleCheckedIds;
  }
  return response;
}

function handleProblemSelection(keys?: Array<string | number>) {
  checkedProblemIds.value = Array.isArray(keys) ? keys : [];
}

function refreshProblems() {
  problemTableRef.value?.handleRefresh?.();
}
function confirmDeleteProblems(row?: IngestInboxSummaryVo) {
  const ids = row ? [row.id] : checkedProblemIds.value.map(String);
  const clearAll = !row && ids.length === 0;
  dialog.warning({
    title: clearAll ? '一键清空待识别 / 死信' : '删除待识别 / 死信',
    content: clearAll
      ? '确认删除当前场馆权限范围内的全部待识别和死信消息？原始报文删除后无法恢复。'
      : `确认删除选中的 ${ids.length} 条消息？原始报文删除后无法恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      problemDeleting.value = true;
      try {
        const response = await ingestConfigApi.deleteProblemMessages(clearAll ? { all: true } : { ids });
        const deletedCount = Number(response?.data ?? 0);
        checkedProblemIds.value = [];
        if (problemViewRow.value && (clearAll || ids.includes(problemViewRow.value.id))) {
          problemViewShow.value = false;
          problemViewRow.value = null;
          problemViewDetail.value = null;
        }
        message.success(`已删除 ${deletedCount} 条消息`);
        problemTableRef.value?.clearCheckedRows?.();
        refreshProblems();
      } finally {
        problemDeleting.value = false;
      }
    },
  });
}
async function replay(row: IngestInboxSummaryVo) {
  await ingestConfigApi.replayInbox(row.id);
  message.success('已提交重放');
  refreshProblems();
}
function syncMatchTransport(code: string | null) {
  matchForm.value.transport = (channels.value.find((item) => item.code === code)?.transport || 'MQTT') as any;
}
async function testRoute() {
  const response = await ingestConfigApi.matchRoute(matchForm.value);
  matchResult.value = response?.data || null;
}

watch(activeTab, (tab) => {
  if (tab === 'problems') refreshProblems();
  if (tab === 'history-sync') historySyncTableRef.value?.handleRefresh();
});

watch(
  () => routeQuery.gatewayId,
  () => routeTableRef.value?.handleRefresh(),
);
onMounted(loadBase);
</script>

<style scoped>
.ingest-page,
.ingest-tabs {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
}

.ingest-tabs :deep(.n-tabs-nav) {
  flex: none;
}

.ingest-tabs :deep(.n-tabs-pane-wrapper) {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
}

.ingest-tabs :deep(.n-tab-pane) {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
}

.ingest-tabs :deep(.n-tab-pane > .n-card),
.ingest-data-table {
  min-height: 0;
  flex: 1;
}

.ingest-data-table {
  width: 100%;
  height: 100%;
}

.problem-payload {
  width: 100%;
  min-width: 0;
  min-height: 120px;
}

.ingest-tabs :deep(.n-tab-pane > .n-card) {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
</style>
