<template>
  <SafetyPageGuard>
    <div class="operations-page">
      <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
      <NTabs
        v-model:value="activeTab"
        type="line"
        animated
        class="safety-tabs"
        pane-wrapper-style="display: flex; flex: 1; min-height: 0; overflow: hidden;"
        pane-style="display: flex; flex: 1; min-height: 0; flex-direction: column; overflow: hidden;"
      >
        <NTabPane name="personnel" tab="值班人员">
          <HPageDataTable
            ref="personnelTableRef"
            title="值班人员"
            class="safety-page-table"
            :columns="personnelColumns"
            :fetch-data="safetyApi.dutyPersonnelPage"
            :params="tableQuery"
            row-key-field="id"
            :is-add="false"
            :is-edit="false"
            :is-copy="false"
            :is-delete="false"
            :is-export="false"
            :show-operate="false"
          >
            <template #queryForm><VenueQuery /></template>
            <template #toolbar-extra
              ><NSpace>
                <NButton @click="exportPersonnel">导出 Excel</NButton>
                <NButton v-if="canDuty" type="primary" @click="() => openPersonnel()">新增人员</NButton>
              </NSpace></template
            >
          </HPageDataTable>
        </NTabPane>
        <NTabPane name="schedule" tab="值班排班">
          <HPageDataTable
            ref="scheduleTableRef"
            title="值班排班"
            class="safety-page-table"
            :columns="scheduleColumns"
            :fetch-data="safetyApi.dutySchedulesPage"
            :params="tableQuery"
            row-key-field="id"
            :is-add="false"
            :is-edit="false"
            :is-copy="false"
            :is-delete="false"
            :is-export="false"
            :show-operate="false"
          >
            <template #queryForm><VenueQuery /></template>
            <template #toolbar-extra
              ><NSpace>
                <NButton @click="exportSchedules">导出排班表</NButton>
                <NButton v-if="canDuty" type="primary" @click="openSchedule">新增排班</NButton>
              </NSpace></template
            >
          </HPageDataTable>
        </NTabPane>
        <NTabPane name="adjustment" tab="调班审批">
          <HPageDataTable
            ref="adjustmentTableRef"
            title="调班审批"
            class="safety-page-table"
            :columns="adjustmentColumns"
            :fetch-data="safetyApi.dutyAdjustmentsPage"
            :params="tableQuery"
            :scroll-x="1180"
            row-key-field="id"
            :is-add="false"
            :is-edit="false"
            :is-copy="false"
            :is-delete="false"
            :is-export="false"
            :show-operate="false"
          >
            <template #queryForm><VenueQuery /></template>
            <template #toolbar-extra>
              <NSpace>
                <NButton @click="exportAdjustments">导出调班台账</NButton>
                <NButton
                  type="primary"
                  :disabled="!canDuty"
                  :title="canDuty ? '' : '需要场馆安全值班管理权限才能申请调班'"
                  @click="openAdjustment()"
                  >申请调班</NButton
                >
              </NSpace>
            </template>
          </HPageDataTable>
        </NTabPane>
        <NTabPane name="duty" tab="值班记录">
          <HPageDataTable
            ref="dutyTableRef"
            title="值班记录"
            class="safety-page-table"
            :columns="dutyColumns"
            :fetch-data="safetyApi.dutyLogsPage"
            :params="tableQuery"
            :scroll-x="1100"
            row-key-field="id"
            :is-add="false"
            :is-edit="false"
            :is-copy="false"
            :is-delete="false"
            :is-export="false"
            :show-operate="false"
          >
            <template #queryForm><VenueQuery /></template>
            <template #toolbar-extra
              ><NButton v-if="canDuty" type="primary" :disabled="!schedules.length" @click="openDutyLog"
                >填报记录</NButton
              ></template
            >
          </HPageDataTable>
        </NTabPane>
        <NTabPane name="checkpoint" tab="巡检点档案">
          <HPageDataTable
            ref="checkpointTableRef"
            title="巡检点档案"
            class="safety-page-table"
            :columns="checkpointColumns"
            :fetch-data="safetyApi.inspectionCheckpointsPage"
            :params="tableQuery"
            row-key-field="id"
            :is-add="false"
            :is-edit="false"
            :is-copy="false"
            :is-delete="false"
            :is-export="false"
            :show-operate="false"
          >
            <template #queryForm><VenueQuery /></template>
            <template #toolbar-extra
              ><NButton v-if="canInspect" type="primary" @click="openCheckpoint()">新增巡检点</NButton></template
            >
          </HPageDataTable>
        </NTabPane>
        <NTabPane name="route" tab="巡检路线">
          <HPageDataTable
            ref="routeTableRef"
            title="可排序巡检路线"
            class="safety-page-table"
            :columns="routeColumns"
            :fetch-data="safetyApi.inspectionRoutesPage"
            :params="tableQuery"
            row-key-field="id"
            :is-add="false"
            :is-edit="false"
            :is-copy="false"
            :is-delete="false"
            :is-export="false"
            :show-operate="false"
          >
            <template #queryForm><VenueQuery /></template>
            <template #toolbar-extra
              ><NButton v-if="canInspect" type="primary" @click="openRoute()">新增路线</NButton></template
            >
          </HPageDataTable>
        </NTabPane>
        <NTabPane name="plan" tab="巡检计划">
          <HPageDataTable
            ref="inspectionPlanTableRef"
            title="巡检计划"
            class="safety-page-table"
            :columns="planColumns"
            :fetch-data="safetyApi.inspectionPlansPage"
            :params="tableQuery"
            row-key-field="id"
            :is-add="false"
            :is-edit="false"
            :is-copy="false"
            :is-delete="false"
            :is-export="false"
            :show-operate="false"
          >
            <template #queryForm><VenueQuery /></template>
            <template #toolbar-extra
              ><NButton v-if="canInspect" type="primary" @click="openInspectionPlan()">新增计划</NButton></template
            >
          </HPageDataTable>
        </NTabPane>
        <NTabPane name="task" tab="巡检任务">
          <HPageDataTable
            ref="inspectionTaskTableRef"
            title="巡检任务"
            class="safety-page-table"
            :columns="taskColumns"
            :fetch-data="safetyApi.inspectionTasksPage"
            :params="inspectionTaskQuery"
            row-key-field="id"
            :is-add="false"
            :is-edit="false"
            :is-copy="false"
            :is-delete="false"
            :is-export="false"
            :show-operate="false"
            ><template #queryForm><VenueQuery /></template
          ></HPageDataTable>
        </NTabPane>
        <NTabPane name="record" tab="巡检记录">
          <HPageDataTable
            ref="inspectionRecordTableRef"
            title="巡检记录"
            class="safety-page-table"
            :columns="recordColumns"
            :fetch-data="safetyApi.inspectionRecordsPage"
            :params="tableQuery"
            :scroll-x="1180"
            row-key-field="id"
            :is-add="false"
            :is-edit="false"
            :is-copy="false"
            :is-delete="false"
            :is-export="false"
            :show-operate="false"
          >
            <template #queryForm><VenueQuery /></template>
            <template #toolbar-extra
              ><NSpace>
                <NButton @click="exportInspections">导出巡检台账</NButton>
                <NButton v-if="canInspect" type="primary" @click="activeTab = 'task'">从任务登记</NButton>
              </NSpace></template
            >
          </HPageDataTable>
        </NTabPane>
        <NTabPane name="issue" tab="异常处置">
          <HPageDataTable
            ref="issueTableRef"
            title="异常处置"
            class="safety-page-table"
            :columns="issueColumns"
            :fetch-data="safetyApi.inspectionIssuesPage"
            :params="issueQuery"
            :scroll-x="1100"
            row-key-field="id"
            :is-add="false"
            :is-edit="false"
            :is-copy="false"
            :is-delete="false"
            :is-export="false"
            :show-operate="false"
          >
            <template #queryForm>
              <VenueQuery />
              <NFormItem label="处置状态"
                ><NSelect
                  v-model:value="issueStatus"
                  :options="issueStatusOptions"
                  clearable
                  placeholder="全部状态"
                  style="width: 150px"
              /></NFormItem>
            </template>
          </HPageDataTable>
        </NTabPane>
        <NTabPane name="attendance" tab="考勤台账">
          <HPageDataTable
            ref="attendanceTableRef"
            title="考勤台账"
            class="safety-page-table"
            :columns="attendanceColumns"
            :fetch-data="safetyApi.attendanceLedgerPage"
            :params="tableQuery"
            :scroll-x="1200"
            row-key-field="scheduleId"
            :is-add="false"
            :is-edit="false"
            :is-copy="false"
            :is-delete="false"
            :is-export="false"
            :show-operate="false"
          >
            <template #queryForm><VenueQuery /></template>
            <template #toolbar-extra><NButton @click="exportAttendance">导出考勤台账</NButton></template>
          </HPageDataTable>
        </NTabPane>
      </NTabs>

      <HPageForm
        v-model:show="personnelVisible"
        title="值班人员信息"
        :model="personnelForm"
        :auto-close="false"
        :handle-submit="savePersonnel"
        label-placement="left"
        label-width="110"
        submit-text="保存"
      >
        <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
        <NFormItem label="所属场馆" required
          ><VenueSelect
            v-model:value="personnelForm.orgId"
            placeholder="请选择场馆"
            @update:value="personnelForm.dutyAreaId = null"
        /></NFormItem>
        <NFormItem label="关联用户" required
          ><UserSelect
            v-model:value="personnelForm.userId"
            clearable
            placeholder="请选择系统用户"
            @update:value="personnelUserChanged"
        /></NFormItem>
        <NFormItem label="姓名" required
          ><NInput v-model:value="personnelForm.name" disabled placeholder="从系统用户读取"
        /></NFormItem>
        <NFormItem label="岗位" required
          ><HPostSelect v-model:value="personnelForm.postId" clearable filterable placeholder="请选择系统岗位"
        /></NFormItem>
        <NFormItem label="联系电话" required
          ><NInput v-model:value="personnelForm.phone" disabled placeholder="从系统用户读取"
        /></NFormItem>
        <NFormItem label="值班区域" required
          ><SpatialLocationSelect
            v-model:value="personnelForm.dutyAreaId"
            :org-id="personnelForm.orgId"
            placeholder="从场馆空间配置中选择"
        /></NFormItem>
        <NFormItem label="任务清单">
          <HDictSelect
            v-model:value="personnelForm.taskList"
            type="museum_safety_duty_task"
            multiple
            return-array
            clearable
            :fallback-options="dutyTaskOptions"
            placeholder="请选择值班任务"
          />
        </NFormItem>
        <NFormItem label="资质证书"><SafetyQualificationEditor v-model="personnelForm.qualifications" /></NFormItem>
        <NFormItem label="启用"><NSwitch v-model:value="personnelForm.active" /></NFormItem>
      </HPageForm>

      <HPageForm
        v-model:show="scheduleVisible"
        title="新增值班排班"
        :model="scheduleForm"
        :auto-close="false"
        :handle-submit="saveSchedule"
        label-placement="left"
        label-width="100"
        submit-text="保存"
      >
        <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
        <NAlert v-if="!personnelOptions.length" type="info" class="state-alert">
          {{ scheduleForm.orgId ? '该场馆暂无已启用的值班人员档案。' : '暂无已启用的值班人员档案。' }}
          请先登记值班人员并关联岗位和值班区域。
          <NButton text type="primary" @click="openPersonnelFromSchedule">登记值班人员</NButton>
        </NAlert>
        <NFormItem label="所属场馆" required
          ><VenueSelect
            v-model:value="scheduleForm.orgId"
            placeholder="请选择场馆"
            @update:value="scheduleForm.dutyPersonnelId = ''"
        /></NFormItem>
        <NFormItem label="排班周期" required
          ><NInput v-model:value="scheduleForm.cycleCode" placeholder="例如：2026-W39"
        /></NFormItem>
        <NFormItem label="班次"><NSelect v-model:value="scheduleForm.shiftType" :options="shiftOptions" /></NFormItem>
        <NFormItem label="值班人员" required
          ><NSelect v-model:value="scheduleForm.dutyPersonnelId" :options="personnelOptions" filterable
        /></NFormItem>
        <NFormItem v-if="selectedSchedulePersonnel" label="人员档案">
          <NDescriptions :column="1" bordered size="small" class="personnel-snapshot">
            <NDescriptionsItem label="岗位">{{ selectedSchedulePersonnel.position }}</NDescriptionsItem>
            <NDescriptionsItem label="值班区域">{{ selectedSchedulePersonnel.dutyArea }}</NDescriptionsItem>
            <NDescriptionsItem label="任务清单">{{
              taskLabels(selectedSchedulePersonnel.taskList) || '未配置'
            }}</NDescriptionsItem>
            <NDescriptionsItem label="资质证书">{{
              qualificationSummary(selectedSchedulePersonnel.qualifications)
            }}</NDescriptionsItem>
          </NDescriptions>
        </NFormItem>
        <NFormItem label="值班时段" required
          ><NDatePicker v-model:value="scheduleRange" type="datetimerange"
        /></NFormItem>
        <NFormItem label="休假标记"><NSwitch v-model:value="scheduleForm.onLeave" /></NFormItem>
      </HPageForm>

      <HPageForm
        v-model:show="dutyVisible"
        title="填报值班记录"
        :model="dutyForm"
        :auto-close="false"
        :handle-submit="saveDutyLog"
        label-placement="left"
        label-width="100"
        submit-text="保存"
      >
        <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
        <NFormItem label="排班" required
          ><NSelect v-model:value="dutyForm.scheduleId" :options="scheduleOptions" filterable
        /></NFormItem>
        <NFormItem label="事件类型"><NSelect v-model:value="dutyForm.eventType" :options="eventOptions" /></NFormItem>
        <NFormItem label="发生时间"><NDatePicker v-model:value="dutyForm.occurredAt" type="datetime" /></NFormItem>
        <NFormItem label="到离岗时间"
          ><NDatePicker v-model:value="dutyRange" type="datetimerange" clearable
        /></NFormItem>
        <NFormItem label="打卡经度"
          ><NInputNumber v-model:value="dutyForm.longitude" :min="-180" :max="180"
        /></NFormItem>
        <NFormItem label="打卡纬度"><NInputNumber v-model:value="dutyForm.latitude" :min="-90" :max="90" /></NFormItem>
        <NFormItem label="事件描述" required><NInput v-model:value="dutyForm.description" type="textarea" /></NFormItem>
        <NFormItem label="处理结果"><NInput v-model:value="dutyForm.result" type="textarea" /></NFormItem>
        <NFormItem label="交接人员"
          ><UserSelect v-model:value="dutyForm.handoverUserId" clearable placeholder="请选择交接人员"
        /></NFormItem>
        <NFormItem label="交接事项"><NInput v-model:value="dutyForm.handoverNotes" type="textarea" /></NFormItem>
        <NFormItem label="图片/语音"
          ><SafetyAttachmentUpload v-model="dutyForm.attachments" accept="image/*,audio/*"
        /></NFormItem>
        <NFormItem label="审核级数"
          ><NInputNumber v-model:value="dutyForm.requiredApprovals" :min="1" :max="10"
        /></NFormItem>
      </HPageForm>

      <HPageForm
        v-model:show="adjustmentVisible"
        title="申请调班"
        :model="adjustmentForm"
        :auto-close="false"
        :handle-submit="saveAdjustment"
        label-placement="left"
        label-width="90"
        submit-text="提交申请"
      >
        <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
        <NAlert v-if="!adjustableScheduleOptions.length" type="info" class="state-alert">
          当前没有可申请调班的待执行排班；已存在待审核调班申请的排班也不能重复申请。
          <NButton text type="primary" @click="openScheduleFromAdjustment">前往新增排班</NButton>
        </NAlert>
        <NFormItem label="原排班" required>
          <NSelect
            v-model:value="adjustmentForm.scheduleId"
            :options="adjustableScheduleOptions"
            filterable
            clearable
            placeholder="选择待执行的原排班"
            @update:value="adjustmentForm.targetUserId = ''"
          />
        </NFormItem>
        <NFormItem v-if="selectedSchedule" label="排班信息">
          <NInput :value="scheduleSummary(selectedSchedule)" disabled />
        </NFormItem>
        <NFormItem label="接班人员" required
          ><NSelect
            v-model:value="adjustmentForm.targetUserId"
            :options="adjustmentPersonnelOptions"
            :disabled="!selectedSchedule"
            filterable
            clearable
            placeholder="选择同场馆已启用的值班人员"
        /></NFormItem>
        <NFormItem label="调班原因" required
          ><NInput v-model:value="adjustmentForm.reason" type="textarea"
        /></NFormItem>
        <NAlert type="info" class="state-alert"
          >提交后由其他具有审批权限的人员审核；通过后原排班自动更新为接班人员。</NAlert
        >
      </HPageForm>

      <NModal v-model:show="adjustmentReviewVisible" preset="card" title="审核调班" class="small-dialog">
        <NForm label-placement="left" label-width="90">
          <NFormItem label="审核结果"
            ><NSwitch v-model:value="adjustmentReviewForm.approved"
              ><template #checked>通过</template><template #unchecked>驳回</template></NSwitch
            ></NFormItem
          >
          <NFormItem label="审核意见" required
            ><NInput v-model:value="adjustmentReviewForm.opinion" type="textarea"
          /></NFormItem>
          <FormActions :loading="submitting" @cancel="adjustmentReviewVisible = false" @save="reviewAdjustment" />
        </NForm>
      </NModal>

      <NModal v-model:show="reviewVisible" preset="card" title="审核值班记录" class="small-dialog">
        <NForm label-placement="left" label-width="90"
          ><NFormItem label="审核结果"
            ><NSwitch v-model:value="reviewForm.approved"
              ><template #checked>通过</template><template #unchecked>驳回</template></NSwitch
            ></NFormItem
          ><NFormItem label="审核意见" required><NInput v-model:value="reviewForm.opinion" type="textarea" /></NFormItem
          ><FormActions :loading="submitting" @cancel="reviewVisible = false" @save="reviewDutyLog"
        /></NForm>
      </NModal>

      <HPageForm
        v-model:show="checkpointVisible"
        :title="editingCheckpoint ? '编辑巡检点' : '新增巡检点'"
        :model="checkpointForm"
        :auto-close="false"
        :handle-submit="saveCheckpoint"
        label-placement="left"
        label-width="110"
        submit-text="保存"
      >
        <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
        <NFormItem label="所属场馆" required
          ><VenueSelect
            v-model:value="checkpointForm.orgId"
            :disabled="!!editingCheckpoint"
            @update:value="checkpointVenueChanged"
        /></NFormItem>
        <NFormItem label="点位编码"><NInput :value="editingCheckpoint?.code || '保存后自动生成'" disabled /></NFormItem>
        <NFormItem label="点位名称" required><NInput v-model:value="checkpointForm.name" /></NFormItem>
        <NFormItem label="采集空间" required
          ><NSelect
            v-model:value="checkpointForm.locationId"
            :options="spaceOptions"
            filterable
            placeholder="选择采集系统已有空间"
            @update:value="checkpointLocationChanged"
        /></NFormItem>
        <NFormItem label="关联设备"
          ><NSelect
            v-model:value="checkpointForm.deviceId"
            :options="checkpointDeviceOptions"
            filterable
            clearable
            placeholder="可选，来自采集系统"
        /></NFormItem>
        <NFormItem label="说明"><NInput v-model:value="checkpointForm.description" type="textarea" /></NFormItem>
        <NFormItem label="启用"><NSwitch v-model:value="checkpointForm.enabled" /></NFormItem>
      </HPageForm>

      <HPageForm
        v-model:show="routeVisible"
        :title="editingRoute ? '调整巡检路线' : '新增巡检路线'"
        :model="routeForm"
        :auto-close="false"
        :handle-submit="saveRoute"
        label-placement="left"
        label-width="110"
        submit-text="保存"
      >
        <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
        <NFormItem label="所属场馆" required
          ><VenueSelect v-model:value="routeForm.orgId" :disabled="!!editingRoute" @update:value="routeVenueChanged"
        /></NFormItem>
        <NFormItem label="路线编码"><NInput :value="editingRoute?.code || '保存后自动生成'" disabled /></NFormItem>
        <NFormItem label="路线名称" required><NInput v-model:value="routeForm.name" /></NFormItem>
        <NFormItem label="巡检区域"
          ><NSelect
            v-model:value="routeForm.areaLocationId"
            :options="locationOptions"
            clearable
            filterable
            @update:value="routeAreaChanged"
        /></NFormItem>
        <NFormItem label="添加巡检点"
          ><NSelect
            v-model:value="routePointToAdd"
            :options="routePointOptions"
            filterable
            clearable
            placeholder="选择已建巡检点"
            @update:value="appendRoutePoint"
        /></NFormItem>
        <NFormItem label="点位顺序" required>
          <div class="route-point-list">
            <div v-for="(entry, index) in routeForm.points" :key="entry.checkpointId" class="route-point-row">
              <span>{{ index + 1 }}. {{ checkpointLabel(entry.checkpointId) }}</span>
              <NSwitch v-model:value="entry.required" size="small" /><span>{{ entry.required ? '必检' : '可选' }}</span>
              <NButton size="small" :disabled="index === 0" @click="moveRoutePoint(index, -1)">上移</NButton>
              <NButton size="small" :disabled="index === routeForm.points.length - 1" @click="moveRoutePoint(index, 1)"
                >下移</NButton
              >
              <NButton size="small" tertiary type="error" @click="routeForm.points.splice(index, 1)">移除</NButton>
            </div>
          </div>
        </NFormItem>
      </HPageForm>

      <HPageForm
        v-model:show="taskVisible"
        :title="editingTask ? '指派巡检任务' : '生成巡检任务'"
        :model="taskForm"
        :auto-close="false"
        :handle-submit="saveTask"
        label-placement="left"
        label-width="110"
        submit-text="保存"
      >
        <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
        <NFormItem label="巡检计划"
          ><NInput :value="inspectionPlans.find((item) => item.id === taskForm.planId)?.name || ''" disabled
        /></NFormItem>
        <NFormItem v-if="!editingTask" label="任务开始"
          ><NDatePicker v-model:value="taskForm.scheduledAt" type="datetime"
        /></NFormItem>
        <NFormItem label="巡检人员" required
          ><NSelect
            v-model:value="taskForm.inspectorId"
            :options="taskInspectorOptions"
            filterable
            clearable
            placeholder="选择同场馆且资质有效的人员"
        /></NFormItem>
      </HPageForm>

      <HPageForm
        v-model:show="planVisible"
        :title="editingPlan ? '调整巡检计划' : '新增巡检计划'"
        :model="planForm"
        :auto-close="false"
        :handle-submit="saveInspectionPlan"
        label-placement="left"
        label-width="110"
        submit-text="保存"
      >
        <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
        <NFormItem label="所属场馆" required
          ><VenueSelect
            v-model:value="planForm.orgId"
            placeholder="请选择场馆"
            :disabled="!!editingPlan"
            @update:value="planVenueChanged"
        /></NFormItem>
        <NFormItem label="计划编码"><NInput :value="editingPlan?.code || '保存后自动生成'" disabled /></NFormItem
        ><NFormItem label="计划名称" required><NInput v-model:value="planForm.name" /></NFormItem>
        <NFormItem label="巡检路线" required
          ><NSelect
            v-model:value="planForm.routeId"
            :options="planRouteOptions"
            filterable
            placeholder="选择已配置顺序的路线"
        /></NFormItem>
        <NFormItem label="每日频次"
          ><NInputNumber v-model:value="planForm.frequencyPerDay" :min="1" :max="96"
        /></NFormItem>
        <NFormItem label="巡检周期"><NSelect v-model:value="planForm.cycle" :options="cycleOptions" /></NFormItem>
        <NFormItem label="首次开始"><NDatePicker v-model:value="planForm.firstStartsAt" type="datetime" /></NFormItem>
        <NFormItem label="人员资质"
          ><NSelect
            v-model:value="planForm.inspectorQualification"
            :options="qualificationOptions"
            clearable
            filterable
            placeholder="从已登记证书中选择"
        /></NFormItem>
      </HPageForm>

      <HPageForm
        v-model:show="recordVisible"
        title="登记巡检记录"
        :model="recordForm"
        :auto-close="false"
        :handle-submit="saveInspectionRecord"
        label-placement="left"
        label-width="110"
        submit-text="保存"
      >
        <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
        <NFormItem label="巡检任务"><NInput :value="recordForm.taskId" disabled /></NFormItem>
        <NFormItem label="巡检人员"
          ><NInput
            :value="personnel.find((item) => item.userId === recordForm.inspectorId)?.name || recordForm.inspectorId"
            disabled
        /></NFormItem>
        <NFormItem label="巡检时间"><NDatePicker v-model:value="recordForm.inspectedAt" type="datetime" /></NFormItem>
        <NFormItem label="巡检点" required
          ><NSelect
            v-model:value="recordForm.checkpointId"
            :options="recordPointOptions"
            filterable
            clearable
            placeholder="选择任务中的未完成巡检点"
            @update:value="recordCheckpointChanged"
        /></NFormItem>
        <NFormItem label="空间"
          ><NInput
            :value="checkpoints.find((item) => item.id === recordForm.checkpointId)?.locationName || '随巡检点自动确定'"
            disabled
        /></NFormItem>
        <NFormItem label="关联设备"
          ><NSelect
            v-model:value="recordForm.deviceId"
            :options="deviceOptions"
            clearable
            filterable
            :disabled="!!selectedRecordCheckpoint?.deviceId"
            @update:value="loadIngestRecords"
        /></NFormItem>
        <NFormItem label="数据来源"><NSelect v-model:value="recordForm.source" :options="sourceOptions" /></NFormItem>
        <NFormItem v-if="recordForm.source !== 'MANUAL'" label="采集事实" required
          ><NSelect
            v-model:value="recordForm.ingestRecordIds"
            :options="ingestRecordOptions"
            multiple
            filterable
            :loading="ingestLoading"
            placeholder="选择现有采集记录作为巡检证据"
        /></NFormItem>
        <NFormItem label="巡检结果"><NSelect v-model:value="recordForm.result" :options="resultOptions" /></NFormItem>
        <NFormItem label="说明" :required="recordForm.result === 'ABNORMAL'"
          ><NInput v-model:value="recordForm.description" type="textarea"
        /></NFormItem>
        <NFormItem label="观测数据"
          ><NInput v-model:value="observationText" placeholder='JSON，例如：{"pressure": 2.5}'
        /></NFormItem>
        <NFormItem label="现场附件"
          ><SafetyAttachmentUpload v-model="recordForm.attachments" accept="image/*,audio/*"
        /></NFormItem>
      </HPageForm>

      <HPageForm
        v-model:show="issueVisible"
        title="创建异常处置事项"
        :model="issueForm"
        :auto-close="false"
        :handle-submit="saveIssue"
        label-placement="left"
        label-width="100"
        submit-text="保存"
      >
        <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
        <NFormItem label="问题类型" required
          ><HDictSelect
            v-model:value="issueForm.issueType"
            type="museum_safety_issue_type"
            :fallback-options="issueTypeOptions"
            placeholder="请选择问题类型" /></NFormItem
        ><NFormItem label="问题描述" required
          ><NInput v-model:value="issueForm.description" type="textarea" /></NFormItem
        ><NFormItem label="责任人" required
          ><UserSelect v-model:value="issueForm.assigneeId" clearable placeholder="请选择责任人" /></NFormItem
        ><NFormItem label="处置期限"><NDatePicker v-model:value="issueForm.dueAt" type="datetime" /></NFormItem
        ><NFormItem label="故障图片"
          ><SafetyAttachmentUpload v-model="issueForm.attachments" accept="image/*"
        /></NFormItem>
      </HPageForm>

      <NModal v-model:show="actionVisible" preset="card" title="推进异常处置" class="small-dialog">
        <NForm label-placement="left" label-width="100"
          ><NFormItem label="目标状态"
            ><NSelect v-model:value="actionForm.status" :options="availableIssueActions" /></NFormItem
          ><NFormItem label="处置结果" required><NInput v-model:value="actionForm.result" type="textarea" /></NFormItem
          ><NFormItem v-if="actionForm.status === 'COMPLETED' || actionForm.status === 'VERIFIED'" label="完成时间"
            ><NDatePicker v-model:value="actionForm.completedAt" type="datetime" /></NFormItem
          ><NFormItem label="关联维护任务"
            ><NSelect
              v-model:value="actionForm.maintenanceTaskId"
              :options="issueTaskOptions"
              clearable
              filterable
              placeholder="选择此异常关联的维护任务" /></NFormItem
          ><FormActions :loading="submitting" @cancel="actionVisible = false" @save="saveIssueAction"
        /></NForm>
      </NModal>
    </div>
  </SafetyPageGuard>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router';
import { computed, defineComponent, h, onMounted, reactive, ref, watch } from 'vue';
import * as XLSX from 'xlsx';
import {
  NAlert,
  NButton,
  NDatePicker,
  NDescriptions,
  NDescriptionsItem,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NModal,
  NPopconfirm,
  NSelect,
  NSpace,
  NSwitch,
  NTabPane,
  NTabs,
  NTag,
  type DataTableColumns,
  useMessage,
} from 'naive-ui';
import { formatDateTime } from '@/utils';
import { useAuthStore } from '@/store/modules/auth';
import HPostSelect from '@/modules/sys/components/form/field/PostSelect.vue';
import HDictSelect from '@/components/form/field/dict/select.vue';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import { get as getUser } from '@/modules/sys/api/user';
import { ingestApi } from '../../museum-device/api/ingest';
import SpatialLocationSelect from '../../museum-device/components/SpatialLocationSelect.vue';
import VenueSelect from '../../museum-device/components/VenueSelect.vue';
import type { IngestRecordVo, RecordFilterOptionsVo } from '../../museum-device/typings';
import { safetyApi, unwrapResponse } from '../api';
import type {
  SafetyDutyAdjustmentStatus,
  SafetyDutyAdjustmentVo,
  SafetyDutyAttendanceVo,
  SafetyDutyEventType,
  SafetyDutyLogVo,
  SafetyDutyPersonnelVo,
  SafetyDutyScheduleVo,
  SafetyInspectionCycle,
  SafetyCheckpointVo,
  SafetyRouteVo,
  SafetyRoutePoint,
  SafetyInspectionTaskVo,
  SafetyInspectionIssueVo,
  SafetyInspectionPlanVo,
  SafetyInspectionRecordVo,
  SafetyInspectionSource,
  SafetyIssueStatus,
  SafetyMaintenanceTaskVo,
  SafetyReviewStatus,
  SafetyQualificationCertificate,
  SafetyShiftType,
} from '../typings';
import SafetyPageGuard from '../components/SafetyPageGuard.vue';
import SafetyAttachmentUpload from '../components/SafetyAttachmentUpload.vue';
import SafetyQualificationEditor from '../components/SafetyQualificationEditor.vue';

const FormActions = defineComponent({
  props: { loading: Boolean },
  emits: ['cancel', 'save'],
  setup(props, { emit }) {
    return () =>
      h('div', { class: 'form-actions' }, [
        h(NButton, { onClick: () => emit('cancel') }, { default: () => '取消' }),
        h(NButton, { type: 'primary', loading: props.loading, onClick: () => emit('save') }, { default: () => '保存' }),
      ]);
  },
});
const message = useMessage();
const authStore = useAuthStore();
const canDuty = computed(() => authStore.hasPermission('ADMIN,MUSEUM_SAFETY_DUTY'));
const canInspect = computed(() => authStore.hasPermission('ADMIN,MUSEUM_SAFETY_INSPECTION'));
const canApprove = computed(() => authStore.hasPermission('ADMIN,MUSEUM_SAFETY_APPROVE'));
const route = useRoute();
const orgId = ref<string | null>(typeof route.query.orgId === 'string' ? route.query.orgId : null);
const VenueQuery = defineComponent({
  setup() {
    return () =>
      h(
        NFormItem,
        { label: '所属场馆' },
        {
          default: () =>
            h(VenueSelect, {
              value: orgId.value,
              clearable: true,
              placeholder: '全部场馆',
              style: 'width: 240px',
              'onUpdate:value': (value: string | null) => {
                orgId.value = value;
                void reloadAll();
              },
            }),
        },
      );
  },
});
const activeTab = ref(typeof route.query.tab === 'string' ? route.query.tab : 'schedule');
const inspectionTaskQuery = computed(() => ({
  orgId: orgId.value || undefined,
  taskId: typeof route.query.taskId === 'string' ? route.query.taskId : undefined,
}));
const loading = ref(false);
const submitting = ref(false);
const ingestLoading = ref(false);
const error = ref('');
const personnel = ref<SafetyDutyPersonnelVo[]>([]);
const attendance = ref<SafetyDutyAttendanceVo[]>([]);
const schedules = ref<SafetyDutyScheduleVo[]>([]);
const adjustments = ref<SafetyDutyAdjustmentVo[]>([]);
const dutyLogs = ref<SafetyDutyLogVo[]>([]);
const inspectionPlans = ref<SafetyInspectionPlanVo[]>([]);
const checkpoints = ref<SafetyCheckpointVo[]>([]);
const routes = ref<SafetyRouteVo[]>([]);
const inspectionTasks = ref<SafetyInspectionTaskVo[]>([]);
const inspectionRecords = ref<SafetyInspectionRecordVo[]>([]);
const issues = ref<SafetyInspectionIssueVo[]>([]);
const maintenanceTasks = ref<SafetyMaintenanceTaskVo[]>([]);
const catalogs = ref<RecordFilterOptionsVo>({ devices: [], locations: [] });
const ingestRecords = ref<IngestRecordVo[]>([]);
const issueStatus = ref<SafetyIssueStatus | null>(null);
const selectedDuty = ref<SafetyDutyLogVo | null>(null);
const selectedAdjustment = ref<SafetyDutyAdjustmentVo | null>(null);
const selectedRecord = ref<SafetyInspectionRecordVo | null>(null);
const selectedIssue = ref<SafetyInspectionIssueVo | null>(null);
const editingPlan = ref<SafetyInspectionPlanVo | null>(null);
const editingCheckpoint = ref<SafetyCheckpointVo | null>(null);
const editingRoute = ref<SafetyRouteVo | null>(null);
const editingTask = ref<SafetyInspectionTaskVo | null>(null);
const personnelVisible = ref(false);
const scheduleVisible = ref(false);
const dutyVisible = ref(false);
const reviewVisible = ref(false);
const adjustmentVisible = ref(false);
const adjustmentReviewVisible = ref(false);
const planVisible = ref(false);
const checkpointVisible = ref(false);
const routeVisible = ref(false);
const taskVisible = ref(false);
const recordVisible = ref(false);
const issueVisible = ref(false);
const actionVisible = ref(false);
const personnelTableRef = ref<any>(null);
const scheduleTableRef = ref<any>(null);
const adjustmentTableRef = ref<any>(null);
const dutyTableRef = ref<any>(null);
const inspectionPlanTableRef = ref<any>(null);
const checkpointTableRef = ref<any>(null);
const routeTableRef = ref<any>(null);
const inspectionTaskTableRef = ref<any>(null);
const inspectionRecordTableRef = ref<any>(null);
const issueTableRef = ref<any>(null);
const attendanceTableRef = ref<any>(null);
const tableQuery = computed(() => ({ orgId: orgId.value || undefined }));
const issueQuery = computed(() => ({
  orgId: orgId.value || undefined,
  status: issueStatus.value || undefined,
}));
const shiftOptions = [
  { label: '早班', value: 'MORNING' },
  { label: '中班', value: 'MIDDLE' },
  { label: '晚班', value: 'NIGHT' },
  { label: '自定义', value: 'CUSTOM' },
];
const dutyTaskOptions = [
  { label: '安防巡查', value: 'SECURITY_PATROL' },
  { label: '消防巡查', value: 'FIRE_PATROL' },
  { label: '安全防护设备状态检查', value: 'DEVICE_STATUS_CHECK' },
  { label: '环境参数检查', value: 'ENVIRONMENT_CHECK' },
  { label: '文物展陈状态检查', value: 'EXHIBITION_CHECK' },
  { label: '交接班事项核对', value: 'HANDOVER_CHECK' },
  { label: '访客及异常事项登记', value: 'VISITOR_EXCEPTION_RECORD' },
];
const eventOptions = [
  { label: '日常', value: 'ROUTINE' },
  { label: '设备异常', value: 'DEVICE_ABNORMAL' },
  { label: '访客登记', value: 'VISITOR' },
  { label: '安防事件', value: 'SECURITY' },
  { label: '消防事件', value: 'FIRE' },
  { label: '交接班', value: 'HANDOVER' },
  { label: '其他', value: 'OTHER' },
];
const cycleOptions = [
  { label: '工作日', value: 'WORKDAY' },
  { label: '周末', value: 'WEEKEND' },
  { label: '每日', value: 'DAILY' },
  { label: '每周', value: 'WEEKLY' },
  { label: '每月', value: 'MONTHLY' },
  { label: '自定义', value: 'CUSTOM' },
];
const sourceOptions = [
  { label: '人工填报', value: 'MANUAL' },
  { label: '采集数据引用', value: 'INGEST_REFERENCE' },
  { label: '人工 + 采集', value: 'MIXED' },
];
const resultOptions = [
  { label: '正常', value: 'NORMAL' },
  { label: '异常', value: 'ABNORMAL' },
];
const issueStatusOptions = [
  { label: '待处理', value: 'PENDING' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已复核', value: 'VERIFIED' },
  { label: '已取消', value: 'CANCELLED' },
];
const issueTypeOptions = [
  { label: '设备故障', value: 'DEVICE_FAULT' },
  { label: '消防隐患', value: 'FIRE_HAZARD' },
  { label: '安防隐患', value: 'SECURITY_HAZARD' },
  { label: '防雷隐患', value: 'LIGHTNING_HAZARD' },
  { label: '环境异常', value: 'ENVIRONMENT_ABNORMAL' },
  { label: '其他', value: 'OTHER' },
];
const scheduleOptions = computed(() =>
  schedules.value.map((item) => ({
    label: `${formatDateTime(item.startsAt)} · ${item.position} · ${item.dutyUserId}`,
    value: item.id,
  })),
);
const adjustableScheduleOptions = computed(() =>
  schedules.value.filter(isAdjustableSchedule).map((item) => ({ label: scheduleSummary(item), value: item.id })),
);
const personnelOptions = computed(() =>
  personnel.value
    .filter((item) => item.active && (!scheduleForm.orgId || item.orgId === scheduleForm.orgId))
    .map((item) => ({
      label: `${item.name} · ${item.position} · ${item.dutyArea}`,
      value: item.id,
    })),
);
const selectedSchedulePersonnel = computed(
  () => personnel.value.find((item) => item.id === scheduleForm.dutyPersonnelId) || null,
);
const adjustmentPersonnelOptions = computed(() =>
  personnel.value
    .filter(
      (item) =>
        item.active &&
        item.orgId === selectedSchedule.value?.orgId &&
        item.userId !== selectedSchedule.value?.dutyUserId,
    )
    .map((item) => ({
      label: `${item.name} · ${item.position} · ${qualificationSummary(item.qualifications)}`,
      value: item.userId,
    })),
);
const locationOptions = computed(() =>
  catalogs.value.locations.map((item) => ({ label: item.path || item.name, value: item.id })),
);
const spaceOptions = computed(() =>
  catalogs.value.locations
    .filter((item) => item.type === 'SPACE')
    .map((item) => ({ label: item.path || item.name, value: item.id })),
);
const planRouteOptions = computed(() =>
  routes.value
    .filter((item) => item.orgId === planForm.orgId)
    .map((item) => ({ label: `${item.code} · ${item.name}`, value: item.id })),
);
const routePointOptions = computed(() => {
  const byId = new Map(catalogs.value.locations.map((location) => [location.id, location]));
  const inArea = (locationId: string) => {
    if (!routeForm.areaLocationId) return true;
    let current = byId.get(locationId);
    while (current) {
      if (current.id === routeForm.areaLocationId) return true;
      current = current.parentId ? byId.get(current.parentId) : undefined;
    }
    return false;
  };
  return checkpoints.value
    .filter(
      (item) =>
        item.orgId === routeForm.orgId &&
        item.enabled &&
        inArea(item.locationId) &&
        !routeForm.points.some((point) => point.checkpointId === item.id),
    )
    .map((item) => ({ label: `${item.name} · ${item.locationName}`, value: item.id }));
});
const qualificationOptions = computed(() =>
  [
    ...new Set(
      personnel.value
        .filter((item) => item.active && item.orgId === planForm.orgId)
        .flatMap((item) => item.qualifications)
        .filter((item) => !item.expiresAt || item.expiresAt >= Date.now())
        .map((item) => item.name),
    ),
  ].map((name) => ({ label: name, value: name })),
);
const taskInspectorOptions = computed(() => {
  const plan = inspectionPlans.value.find((item) => item.id === taskForm.planId);
  const qualification = plan?.inspectorQualification;
  return personnel.value
    .filter(
      (item) =>
        item.active &&
        item.orgId === plan?.orgId &&
        (!qualification ||
          item.qualifications.some(
            (certificate) =>
              certificate.name === qualification && (!certificate.expiresAt || certificate.expiresAt >= Date.now()),
          )),
    )
    .map((item) => ({ label: `${item.name} · ${item.position}`, value: item.userId }));
});
const issueTaskOptions = computed(() =>
  maintenanceTasks.value
    .filter((item) => item.issueId === selectedIssue.value?.id)
    .map((item) => ({
      label: `${item.id} · ${deviceOptions.value.find((device) => device.value === item.deviceId)?.label || item.deviceId}`,
      value: item.id,
    })),
);
const deviceOptions = computed(() =>
  catalogs.value.devices.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id })),
);
const checkpointDeviceOptions = computed(() =>
  catalogs.value.devices
    .filter((item) => !checkpointForm.locationId || !item.locationId || item.locationId === checkpointForm.locationId)
    .map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id })),
);
const selectedRecordCheckpoint = computed(() => checkpoints.value.find((item) => item.id === recordForm.checkpointId));
const ingestRecordOptions = computed(() =>
  ingestRecords.value.map((item) => ({
    label: `${formatDateTime(item.occurredAt)} · ${item.metricName || item.key} · ${item.numericValue ?? item.stringValue ?? JSON.stringify(item.values)}`,
    value: item.id,
  })),
);
const recordPointOptions = computed(() => {
  const task = inspectionTasks.value.find((item) => item.id === recordForm.taskId);
  return (task?.points || [])
    .filter((item) => !task?.completedPointIds.includes(item.checkpointId))
    .map((item) => ({ label: checkpointLabel(item.checkpointId), value: item.checkpointId }));
});
const availableIssueActions = computed(() => {
  const current = selectedIssue.value?.status;
  const map: Record<string, SafetyIssueStatus[]> = {
    PENDING: ['PROCESSING', 'CANCELLED'],
    PROCESSING: ['COMPLETED', 'CANCELLED'],
    COMPLETED: ['VERIFIED', 'PROCESSING'],
  };
  return (map[current || ''] || []).map((value) => ({ label: issueStatusLabel(value), value }));
});

const scheduleForm = reactive({
  orgId: null as string | null,
  cycleCode: '',
  shiftType: 'MORNING' as SafetyShiftType,
  dutyPersonnelId: '',
  onLeave: false,
});
const personnelForm = reactive({
  orgId: null as string | null,
  userId: '',
  name: '',
  postId: '',
  phone: '',
  qualifications: [] as SafetyQualificationCertificate[],
  dutyAreaId: null as string | null,
  taskList: [] as string[],
  active: true,
});
const scheduleRange = ref<[number, number] | null>(null);
const dutyForm = reactive({
  scheduleId: '',
  eventType: 'ROUTINE' as SafetyDutyEventType,
  occurredAt: Date.now(),
  description: '',
  result: '',
  handoverUserId: '',
  handoverNotes: '',
  longitude: null as number | null,
  latitude: null as number | null,
  attachments: [] as string[],
  requiredApprovals: 1,
});
const dutyRange = ref<[number, number] | null>(null);
const reviewForm = reactive({ approved: true, opinion: '' });
const adjustmentForm = reactive({ scheduleId: '', targetUserId: '', reason: '' });
const selectedSchedule = computed(() => schedules.value.find((item) => item.id === adjustmentForm.scheduleId) || null);
const adjustmentReviewForm = reactive({ approved: true, opinion: '' });
const planForm = reactive({
  orgId: null as string | null,
  name: '',
  routeId: '',
  frequencyPerDay: 1,
  cycle: 'DAILY' as SafetyInspectionCycle,
  firstStartsAt: Date.now(),
  inspectorQualification: '',
});
const checkpointForm = reactive({
  orgId: null as string | null,
  name: '',
  locationId: '',
  deviceId: null as string | null,
  description: '',
  enabled: true,
});
const routeForm = reactive({
  orgId: null as string | null,
  name: '',
  areaLocationId: null as string | null,
  points: [] as SafetyRoutePoint[],
});
const routePointToAdd = ref<string | null>(null);
const taskForm = reactive({ planId: '', scheduledAt: Date.now(), inspectorId: '' });
const recordForm = reactive({
  orgId: null as string | null,
  taskId: '',
  inspectorId: '',
  inspectedAt: Date.now(),
  checkpointId: '',
  deviceId: null as string | null,
  collectionCode: '',
  source: 'MANUAL' as SafetyInspectionSource,
  ingestRecordIds: [] as string[],
  result: 'NORMAL' as 'NORMAL' | 'ABNORMAL',
  description: '',
  attachments: [] as string[],
});
const observationText = ref('{}');
const issueForm = reactive({
  issueType: '',
  description: '',
  assigneeId: '',
  dueAt: Date.now() + 24 * 60 * 60 * 1000,
  attachments: [] as string[],
});
const actionForm = reactive({
  status: 'PROCESSING' as SafetyIssueStatus,
  result: '',
  completedAt: null as number | null,
  maintenanceTaskId: '',
});

const personnelColumns: DataTableColumns<SafetyDutyPersonnelVo> = [
  { title: '姓名', key: 'name', width: 120 },
  { title: '人员 ID', key: 'userId', width: 140 },
  { title: '岗位', key: 'position', width: 140 },
  { title: '联系电话', key: 'phone', width: 140 },
  {
    title: '资质证书',
    key: 'qualifications',
    minWidth: 190,
    ellipsis: { tooltip: true },
    render: (r) => qualificationSummary(r.qualifications),
  },
  { title: '值班区域', key: 'dutyArea', minWidth: 150 },
  {
    title: '任务清单',
    key: 'taskList',
    minWidth: 180,
    ellipsis: { tooltip: true },
    render: (r) => taskLabels(r.taskList) || '--',
  },
  {
    title: '状态',
    key: 'active',
    width: 90,
    render: (r) => tag(r.active ? '启用' : '停用', r.active ? 'success' : 'default'),
  },
  {
    title: '操作',
    key: 'actions',
    width: 140,
    render: (r) =>
      canDuty.value
        ? h(NSpace, null, {
            default: () => [
              h(NButton, { text: true, type: 'primary', onClick: () => openPersonnel(r) }, { default: () => '编辑' }),
              h(
                NPopconfirm,
                { onPositiveClick: () => archivePersonnel(r) },
                {
                  trigger: () => h(NButton, { text: true, type: 'error' }, { default: () => '停用' }),
                  default: () => '确认停用该值班人员？',
                },
              ),
            ],
          })
        : '--',
  },
];
const scheduleColumns: DataTableColumns<SafetyDutyScheduleVo> = [
  { title: '开始', key: 'startsAt', width: 180, render: (r) => formatDateTime(r.startsAt) },
  { title: '结束', key: 'endsAt', width: 180, render: (r) => formatDateTime(r.endsAt) },
  {
    title: '班次',
    key: 'shiftType',
    width: 90,
    render: (r) => shiftOptions.find((i) => i.value === r.shiftType)?.label || r.shiftType,
  },
  { title: '岗位', key: 'position' },
  { title: '人员', key: 'dutyUserId', render: (r) => dutyPersonnelName(r) },
  { title: '区域', key: 'dutyArea' },
  { title: '状态', key: 'status', width: 100 },
  {
    title: '操作',
    key: 'actions',
    width: 90,
    render: (r) =>
      canDuty.value && isAdjustableSchedule(r)
        ? h(NButton, { text: true, type: 'primary', onClick: () => openAdjustment(r) }, { default: () => '调班' })
        : adjustments.value.some((item) => item.scheduleId === r.id && item.status === 'PENDING')
          ? '待审批'
          : '--',
  },
];
const adjustmentColumns: DataTableColumns<SafetyDutyAdjustmentVo> = [
  { title: '申请时间', key: 'requestedAt', width: 180, render: (r) => formatDateTime(r.requestedAt) },
  { title: '排班', key: 'scheduleId', minWidth: 180, render: (r) => scheduleLabel(r.scheduleId) },
  { title: '原值班人', key: 'originalUserId', width: 130 },
  { title: '接班人', key: 'targetUserId', width: 130 },
  { title: '原因', key: 'reason', minWidth: 220, ellipsis: { tooltip: true } },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render: (r) =>
      tag(
        adjustmentStatusLabel(r.status),
        r.status === 'APPROVED' ? 'success' : r.status === 'REJECTED' ? 'error' : 'warning',
      ),
  },
  { title: '审核意见', key: 'reviewOpinion', minWidth: 180, ellipsis: { tooltip: true } },
  {
    title: '操作',
    key: 'actions',
    width: 90,
    fixed: 'right',
    render: (r) =>
      canApprove.value && r.status === 'PENDING'
        ? h(NButton, { text: true, type: 'primary', onClick: () => openAdjustmentReview(r) }, { default: () => '审核' })
        : '--',
  },
];
const attendanceColumns: DataTableColumns<SafetyDutyAttendanceVo> = [
  { title: '计划到岗', key: 'scheduledStartsAt', width: 180, render: (r) => formatDateTime(r.scheduledStartsAt) },
  { title: '计划离岗', key: 'scheduledEndsAt', width: 180, render: (r) => formatDateTime(r.scheduledEndsAt) },
  { title: '人员', key: 'dutyUserName', width: 130, render: (r) => r.dutyUserName || r.dutyUserId },
  { title: '岗位', key: 'position', width: 140 },
  { title: '到岗', key: 'arrivedAt', width: 180, render: (r) => (r.arrivedAt ? formatDateTime(r.arrivedAt) : '--') },
  { title: '离岗', key: 'departedAt', width: 180, render: (r) => (r.departedAt ? formatDateTime(r.departedAt) : '--') },
  {
    title: '定位',
    key: 'location',
    width: 180,
    render: (r) => (r.longitude != null && r.latitude != null ? `${r.longitude}, ${r.latitude}` : '--'),
  },
  { title: '考勤状态', key: 'status', width: 110, render: (r) => attendanceStatusLabel(r.status) },
];
const dutyColumns: DataTableColumns<SafetyDutyLogVo> = [
  { title: '发生时间', key: 'occurredAt', width: 180, render: (r) => formatDateTime(r.occurredAt) },
  { title: '值班人员', key: 'dutyUserId', width: 130 },
  { title: '类型', key: 'eventType', width: 110 },
  { title: '描述', key: 'description', minWidth: 220, ellipsis: { tooltip: true } },
  {
    title: '审核',
    key: 'reviewStatus',
    width: 100,
    render: (r) =>
      tag(
        `${reviewStatusLabel(r.reviewStatus)} ${r.approvalCount}/${r.requiredApprovals}`,
        r.reviewStatus === 'APPROVED' ? 'success' : r.reviewStatus === 'REJECTED' ? 'error' : 'warning',
      ),
  },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    render: (r) =>
      canApprove.value && r.reviewStatus === 'PENDING'
        ? h(NButton, { text: true, type: 'primary', onClick: () => openReview(r) }, { default: () => '审核' })
        : '--',
  },
];
const checkpointColumns: DataTableColumns<SafetyCheckpointVo> = [
  { title: '编码', key: 'code', width: 170 },
  { title: '巡检点', key: 'name' },
  { title: '采集空间', key: 'locationName' },
  {
    title: '关联设备',
    key: 'deviceId',
    render: (row) => deviceOptions.value.find((item) => item.value === row.deviceId)?.label || '--',
  },
  {
    title: '状态',
    key: 'enabled',
    width: 90,
    render: (row) => tag(row.enabled ? '启用' : '停用', row.enabled ? 'success' : 'default'),
  },
  {
    title: '操作',
    key: 'action',
    width: 90,
    render: (row) =>
      canInspect.value
        ? h(NButton, { text: true, type: 'primary', onClick: () => openCheckpoint(row) }, { default: () => '编辑' })
        : '--',
  },
];
const routeColumns: DataTableColumns<SafetyRouteVo> = [
  { title: '编码', key: 'code', width: 170 },
  { title: '名称', key: 'name' },
  {
    title: '有序点位',
    key: 'points',
    render: (row) =>
      row.points
        .map(
          (entry, index) => `${index + 1}. ${checkpointLabel(entry.checkpointId)}${entry.required ? '（必检）' : ''}`,
        )
        .join(' → '),
  },
  {
    title: '操作',
    key: 'action',
    width: 90,
    render: (row) =>
      canInspect.value
        ? h(NButton, { text: true, type: 'primary', onClick: () => openRoute(row) }, { default: () => '调整' })
        : '--',
  },
];
const planColumns: DataTableColumns<SafetyInspectionPlanVo> = [
  { title: '编码', key: 'code', width: 130 },
  { title: '名称', key: 'name' },
  { title: '路线', key: 'routeCode' },
  { title: '必检点', key: 'requiredPoints', render: (r) => r.requiredPoints.map((p) => p.name).join('、') },
  { title: '频次', key: 'frequencyPerDay', width: 100, render: (r) => `${r.frequencyPerDay} 次/日` },
  { title: '周期', key: 'cycle', width: 100 },
  {
    title: '操作',
    key: 'actions',
    width: 160,
    render: (r) =>
      canInspect.value
        ? h(NSpace, null, {
            default: () => [
              h(
                NButton,
                { text: true, type: 'primary', onClick: () => openInspectionPlan(r) },
                { default: () => '调整' },
              ),
              h(NButton, { text: true, type: 'primary', onClick: () => openTask(r) }, { default: () => '生成任务' }),
            ],
          })
        : '--',
  },
];
const taskColumns: DataTableColumns<SafetyInspectionTaskVo> = [
  {
    title: '计划',
    key: 'planId',
    render: (row) => inspectionPlans.value.find((item) => item.id === row.planId)?.name || row.planId,
  },
  { title: '开始', key: 'scheduledAt', width: 180, render: (row) => formatDateTime(row.scheduledAt) },
  { title: '截止', key: 'dueAt', width: 180, render: (row) => formatDateTime(row.dueAt) },
  {
    title: '巡检人员',
    key: 'inspectorId',
    width: 130,
    render: (row) =>
      personnel.value.find((item) => item.userId === row.inspectorId)?.name || row.inspectorId || '待指派',
  },
  { title: '进度', key: 'points', width: 110, render: (row) => `${row.completedPointIds.length}/${row.points.length}` },
  {
    title: '状态',
    key: 'status',
    width: 110,
    render: (row) =>
      tag(
        { PENDING: '待执行', IN_PROGRESS: '进行中', COMPLETED: '已完成', OVERDUE: '已逾期' }[row.status],
        row.status === 'COMPLETED' ? 'success' : row.status === 'OVERDUE' ? 'error' : 'warning',
      ),
  },
  {
    title: '操作',
    key: 'action',
    width: 150,
    render: (row) =>
      canInspect.value
        ? h(NSpace, null, {
            default: () => [
              h(
                NButton,
                {
                  text: true,
                  type: 'primary',
                  disabled: row.completedPointIds.length > 0,
                  onClick: () => openTask(row),
                },
                { default: () => '指派' },
              ),
              h(
                NButton,
                {
                  text: true,
                  type: 'primary',
                  disabled: !row.inspectorId || row.status === 'COMPLETED',
                  onClick: () => openInspectionRecord(row),
                },
                { default: () => '登记' },
              ),
            ],
          })
        : '--',
  },
];
const recordColumns: DataTableColumns<SafetyInspectionRecordVo> = [
  { title: '时间', key: 'inspectedAt', width: 180, render: (r) => formatDateTime(r.inspectedAt) },
  { title: '人员', key: 'inspectorId', width: 130 },
  { title: '巡检点', key: 'checkpointId', width: 160, render: (row) => checkpointLabel(row.checkpointId) },
  {
    title: '设备',
    key: 'deviceId',
    minWidth: 150,
    render: (r) => deviceOptions.value.find((i) => i.value === r.deviceId)?.label || r.deviceId || '--',
  },
  { title: '来源', key: 'source', width: 120 },
  {
    title: '结果',
    key: 'result',
    width: 90,
    render: (r) => tag(r.result === 'NORMAL' ? '正常' : '异常', r.result === 'NORMAL' ? 'success' : 'error'),
  },
  { title: '说明', key: 'description', minWidth: 200, ellipsis: { tooltip: true } },
  {
    title: '操作',
    key: 'actions',
    width: 120,
    render: (r) =>
      canInspect.value && r.result === 'ABNORMAL'
        ? h(NButton, { text: true, type: 'primary', onClick: () => openIssue(r) }, { default: () => '创建处置' })
        : '--',
  },
];
const issueColumns: DataTableColumns<SafetyInspectionIssueVo> = [
  { title: '问题类型', key: 'issueType', width: 140 },
  { title: '问题描述', key: 'description', minWidth: 240, ellipsis: { tooltip: true } },
  { title: '责任人', key: 'assigneeId', width: 130 },
  { title: '期限', key: 'dueAt', width: 180, render: (r) => formatDateTime(r.dueAt) },
  {
    title: '状态',
    key: 'status',
    width: 110,
    render: (r) =>
      tag(
        issueStatusLabel(r.status),
        r.status === 'VERIFIED'
          ? 'success'
          : r.status === 'CANCELLED'
            ? 'default'
            : r.dueAt < Date.now()
              ? 'error'
              : 'warning',
      ),
  },
  { title: '处置结果', key: 'result', minWidth: 180, ellipsis: { tooltip: true } },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    render: (r) =>
      canInspect.value && !['VERIFIED', 'CANCELLED'].includes(r.status)
        ? h(NButton, { text: true, type: 'primary', onClick: () => openAction(r) }, { default: () => '处置' })
        : '--',
  },
];
function tag(label: string, type: 'default' | 'success' | 'warning' | 'error') {
  return h(NTag, { type, bordered: false }, { default: () => label });
}
function reviewStatusLabel(v: SafetyReviewStatus) {
  return v === 'PENDING' ? '待审核' : v === 'APPROVED' ? '已通过' : '已驳回';
}
function adjustmentStatusLabel(value: SafetyDutyAdjustmentStatus) {
  return value === 'PENDING' ? '待审核' : value === 'APPROVED' ? '已通过' : '已驳回';
}
function scheduleSummary(row: SafetyDutyScheduleVo) {
  return `${formatDateTime(row.startsAt)} - ${formatDateTime(row.endsAt)} · ${row.position} · ${row.dutyUserId}`;
}
function isAdjustableSchedule(row: SafetyDutyScheduleVo) {
  return (
    row.status === 'SCHEDULED' &&
    !adjustments.value.some((item) => item.scheduleId === row.id && item.status === 'PENDING')
  );
}
function scheduleLabel(id: string) {
  const row = schedules.value.find((item) => item.id === id);
  return row ? scheduleSummary(row) : id;
}
function issueStatusLabel(v: SafetyIssueStatus) {
  return issueStatusOptions.find((i) => i.value === v)?.label || v;
}
function attendanceStatusLabel(value: SafetyDutyAttendanceVo['status']) {
  return {
    SCHEDULED: '未到班次',
    NORMAL: '正常',
    LATE: '迟到',
    ABSENT: '缺勤',
    ON_LEAVE: '休假',
  }[value];
}
function checkpointLabel(id: string) {
  return checkpoints.value.find((item) => item.id === id)?.name || id;
}
function safeJson(value: string) {
  try {
    return JSON.parse(value || '{}');
  } catch {
    throw new Error('观测数据必须是有效 JSON。');
  }
}

function qualificationSummary(certificates: SafetyQualificationCertificate[]) {
  if (!certificates.length) return '未登记证书';
  return certificates
    .map((item) => (item.certificateNo ? `${item.name}（${item.certificateNo}）` : item.name))
    .join('；');
}

function taskLabels(tasks: string[]) {
  return tasks.map((value) => dutyTaskOptions.find((option) => option.value === value)?.label || value).join('；');
}

function dutyPersonnelName(schedule: SafetyDutyScheduleVo) {
  return personnel.value.find((item) => item.id === schedule.dutyPersonnelId)?.name || schedule.dutyUserId;
}

function openPersonnel(row?: SafetyDutyPersonnelVo) {
  Object.assign(personnelForm, {
    orgId: row?.orgId || orgId.value,
    userId: row?.userId || '',
    name: row?.name || '',
    postId: row?.postId || '',
    phone: row?.phone || '',
    qualifications: row?.qualifications.map((item) => ({ ...item, attachments: [...item.attachments] })) || [],
    dutyAreaId: row?.dutyAreaId || null,
    taskList: [...(row?.taskList || [])],
    active: row?.active ?? true,
  });
  personnelVisible.value = true;
}
async function personnelUserChanged(userId: string | null) {
  personnelForm.name = '';
  personnelForm.phone = '';
  if (!userId) return;
  try {
    const user = unwrapResponse<{ id: string; name: string; phone?: string | null }>(await getUser(userId));
    if (personnelForm.userId !== userId) return;
    personnelForm.name = user.name || '';
    personnelForm.phone = user.phone || '';
  } catch (cause: any) {
    error.value = cause?.message || '系统用户信息读取失败。';
  }
}
function openSchedule() {
  Object.assign(scheduleForm, {
    orgId: orgId.value,
    cycleCode: '',
    shiftType: 'MORNING',
    dutyPersonnelId: '',
    onLeave: false,
  });
  scheduleRange.value = null;
  scheduleVisible.value = true;
}
function openPersonnelFromSchedule() {
  const selectedOrgId = scheduleForm.orgId;
  scheduleVisible.value = false;
  activeTab.value = 'personnel';
  openPersonnel();
  personnelForm.orgId = selectedOrgId;
}
function openDutyLog() {
  Object.assign(dutyForm, {
    scheduleId: schedules.value[0]?.id || '',
    eventType: 'ROUTINE',
    occurredAt: Date.now(),
    description: '',
    result: '',
    handoverUserId: '',
    handoverNotes: '',
    longitude: null,
    latitude: null,
    attachments: [],
    requiredApprovals: 1,
  });
  dutyRange.value = null;
  dutyVisible.value = true;
}
function openAdjustment(row?: SafetyDutyScheduleVo) {
  error.value = '';
  Object.assign(adjustmentForm, { scheduleId: row?.id || '', targetUserId: '', reason: '' });
  adjustmentVisible.value = true;
}
function openScheduleFromAdjustment() {
  adjustmentVisible.value = false;
  activeTab.value = 'schedule';
  openSchedule();
}
function openAdjustmentReview(row: SafetyDutyAdjustmentVo) {
  selectedAdjustment.value = row;
  Object.assign(adjustmentReviewForm, { approved: true, opinion: '' });
  adjustmentReviewVisible.value = true;
}
function openReview(row: SafetyDutyLogVo) {
  selectedDuty.value = row;
  Object.assign(reviewForm, { approved: true, opinion: '' });
  reviewVisible.value = true;
}
function openInspectionPlan(row?: SafetyInspectionPlanVo) {
  editingPlan.value = row || null;
  Object.assign(planForm, {
    orgId: row?.orgId || orgId.value,
    name: row?.name || '',
    routeId: row?.routeId || '',
    frequencyPerDay: row?.frequencyPerDay || 1,
    cycle: row?.cycle || 'DAILY',
    firstStartsAt: row?.firstStartsAt || Date.now(),
    inspectorQualification: row?.inspectorQualification || '',
  });
  planVisible.value = true;
}
function planVenueChanged() {
  planForm.routeId = '';
  planForm.inspectorQualification = '';
}
function openCheckpoint(row?: SafetyCheckpointVo) {
  editingCheckpoint.value = row || null;
  Object.assign(checkpointForm, {
    orgId: row?.orgId || orgId.value,
    name: row?.name || '',
    locationId: row?.locationId || '',
    deviceId: row?.deviceId || null,
    description: row?.description || '',
    enabled: row?.enabled ?? true,
  });
  void loadCatalogs(checkpointForm.orgId);
  checkpointVisible.value = true;
}
function checkpointVenueChanged() {
  checkpointForm.locationId = '';
  checkpointForm.deviceId = null;
  void loadCatalogs(checkpointForm.orgId);
}
function checkpointLocationChanged() {
  checkpointForm.deviceId = null;
}
function openRoute(row?: SafetyRouteVo) {
  editingRoute.value = row || null;
  Object.assign(routeForm, {
    orgId: row?.orgId || orgId.value,
    name: row?.name || '',
    areaLocationId: row?.areaLocationId || null,
    points: row?.points.map((entry) => ({ ...entry })) || [],
  });
  routePointToAdd.value = null;
  void loadCatalogs(routeForm.orgId);
  routeVisible.value = true;
}
function routeVenueChanged() {
  routeForm.areaLocationId = null;
  routeForm.points = [];
  void loadCatalogs(routeForm.orgId);
}
function routeAreaChanged() {
  routeForm.points = [];
}
function appendRoutePoint(id: string | null) {
  if (id && !routeForm.points.some((item) => item.checkpointId === id))
    routeForm.points.push({ checkpointId: id, required: true });
  routePointToAdd.value = null;
}
function moveRoutePoint(index: number, delta: number) {
  const target = index + delta;
  if (target < 0 || target >= routeForm.points.length) return;
  const [entry] = routeForm.points.splice(index, 1);
  routeForm.points.splice(target, 0, entry);
}
function openTask(row: SafetyInspectionPlanVo | SafetyInspectionTaskVo) {
  const isTask = 'scheduledAt' in row;
  editingTask.value = isTask ? row : null;
  taskForm.planId = isTask ? row.planId : row.id;
  taskForm.scheduledAt = isTask ? row.scheduledAt : Math.max(Date.now(), row.firstStartsAt);
  taskForm.inspectorId = isTask ? row.inspectorId || '' : '';
  taskVisible.value = true;
}
function openInspectionRecord(task: SafetyInspectionTaskVo) {
  Object.assign(recordForm, {
    orgId: task.orgId,
    taskId: task.id,
    inspectorId: task.inspectorId || '',
    inspectedAt: Date.now(),
    checkpointId: task.points.find((point) => !task.completedPointIds.includes(point.checkpointId))?.checkpointId || '',
    deviceId: null,
    collectionCode: '',
    source: 'MANUAL',
    ingestRecordIds: [],
    result: 'NORMAL',
    description: '',
    attachments: [],
  });
  observationText.value = '{}';
  ingestRecords.value = [];
  void loadCatalogs(task.orgId);
  recordCheckpointChanged();
  recordVisible.value = true;
}
function recordCheckpointChanged() {
  recordForm.deviceId = selectedRecordCheckpoint.value?.deviceId || null;
  recordForm.ingestRecordIds = [];
  void loadIngestRecords();
}
function openIssue(row: SafetyInspectionRecordVo) {
  selectedRecord.value = row;
  Object.assign(issueForm, {
    issueType: '',
    description: row.description || '',
    assigneeId: '',
    dueAt: Date.now() + 24 * 60 * 60 * 1000,
    attachments: [],
  });
  issueVisible.value = true;
}
function openAction(row: SafetyInspectionIssueVo) {
  selectedIssue.value = row;
  Object.assign(actionForm, {
    status: row.status === 'PENDING' ? 'PROCESSING' : row.status === 'PROCESSING' ? 'COMPLETED' : 'VERIFIED',
    result: '',
    completedAt: row.status === 'PROCESSING' || row.status === 'COMPLETED' ? Date.now() : null,
    maintenanceTaskId: row.maintenanceTaskId || '',
  });
  actionVisible.value = true;
}
async function loadCatalogs(value: string | null = orgId.value) {
  try {
    catalogs.value = value
      ? unwrapResponse<RecordFilterOptionsVo>(await ingestApi.recordFilterOptions({ orgId: value }))
      : { devices: [], locations: [] };
  } catch {
    catalogs.value = { devices: [], locations: [] };
  }
}
async function loadIngestRecords() {
  recordForm.ingestRecordIds = [];
  ingestRecords.value = [];
  if (!recordForm.deviceId) return;
  ingestLoading.value = true;
  try {
    const result: any = unwrapResponse<any>(
      await ingestApi.latestRecords({
        orgId: recordForm.orgId || undefined,
        deviceId: recordForm.deviceId,
        pageNum: 1,
        pageSize: 50,
      }),
    );
    ingestRecords.value = Array.isArray(result) ? result : result?.list || [];
  } catch (cause: any) {
    error.value = cause?.message || '采集记录加载失败。';
  } finally {
    ingestLoading.value = false;
  }
}

async function reloadAll() {
  loading.value = true;
  error.value = '';
  try {
    const params = { orgId: orgId.value || undefined };
    const [people, s, adjustmentResult, d, attendanceResult, p, r, i, m, c, routesResult, taskResult] =
      await Promise.all([
        safetyApi.dutyPersonnel(params),
        safetyApi.dutySchedules(params),
        safetyApi.dutyAdjustments(params),
        safetyApi.dutyLogs(params),
        safetyApi.attendanceLedger(params),
        safetyApi.inspectionPlans(params),
        safetyApi.inspectionRecords(params),
        safetyApi.inspectionIssues({ ...params, status: issueStatus.value || undefined }),
        safetyApi.maintenanceTasks(params),
        safetyApi.inspectionCheckpoints(params),
        safetyApi.inspectionRoutes(params),
        safetyApi.inspectionTasks(params),
      ]);
    personnel.value = unwrapResponse<SafetyDutyPersonnelVo[]>(people) || [];
    schedules.value = unwrapResponse<SafetyDutyScheduleVo[]>(s) || [];
    adjustments.value = unwrapResponse<SafetyDutyAdjustmentVo[]>(adjustmentResult) || [];
    dutyLogs.value = unwrapResponse<SafetyDutyLogVo[]>(d) || [];
    attendance.value = unwrapResponse<SafetyDutyAttendanceVo[]>(attendanceResult) || [];
    inspectionPlans.value = unwrapResponse<SafetyInspectionPlanVo[]>(p) || [];
    inspectionRecords.value = unwrapResponse<SafetyInspectionRecordVo[]>(r) || [];
    issues.value = unwrapResponse<SafetyInspectionIssueVo[]>(i) || [];
    maintenanceTasks.value = unwrapResponse<SafetyMaintenanceTaskVo[]>(m) || [];
    checkpoints.value = unwrapResponse<SafetyCheckpointVo[]>(c) || [];
    routes.value = unwrapResponse<SafetyRouteVo[]>(routesResult) || [];
    inspectionTasks.value = unwrapResponse<SafetyInspectionTaskVo[]>(taskResult) || [];
    await loadCatalogs();
    refreshTables();
  } catch (cause: any) {
    error.value = cause?.message || '值班巡检数据加载失败。';
  } finally {
    loading.value = false;
  }
}
function refreshTables() {
  personnelTableRef.value?.handleRefresh?.();
  scheduleTableRef.value?.handleRefresh?.();
  adjustmentTableRef.value?.handleRefresh?.();
  dutyTableRef.value?.handleRefresh?.();
  inspectionPlanTableRef.value?.handleRefresh?.();
  checkpointTableRef.value?.handleRefresh?.();
  routeTableRef.value?.handleRefresh?.();
  inspectionTaskTableRef.value?.handleRefresh?.();
  inspectionRecordTableRef.value?.handleRefresh?.();
  issueTableRef.value?.handleRefresh?.();
  attendanceTableRef.value?.handleRefresh?.();
}
function savePersonnel() {
  if (
    !personnelForm.orgId ||
    !personnelForm.userId.trim() ||
    !personnelForm.name.trim() ||
    !personnelForm.postId ||
    !personnelForm.phone.trim() ||
    !personnelForm.dutyAreaId
  ) {
    error.value = '请完整填写场馆、人员、姓名、岗位、电话和值班区域。';
    return;
  }
  if (personnelForm.qualifications.some((item) => !item.name.trim())) {
    error.value = '请填写每本资质证书的名称。';
    return;
  }
  return execute(
    () =>
      safetyApi.upsertDutyPersonnel({
        orgId: personnelForm.orgId,
        userId: personnelForm.userId,
        postId: personnelForm.postId,
        dutyAreaId: personnelForm.dutyAreaId!,
        qualifications: personnelForm.qualifications,
        taskList: personnelForm.taskList,
        active: personnelForm.active,
      }),
    '值班人员保存成功',
    () => (personnelVisible.value = false),
  );
}
function archivePersonnel(row: SafetyDutyPersonnelVo) {
  return execute(
    () => safetyApi.archiveDutyPersonnel(row.id),
    '值班人员已停用',
    () => undefined,
  );
}
function writeWorkbook(name: string, sheetName: string, values: Array<Record<string, unknown>>) {
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet(values), sheetName.slice(0, 31));
  XLSX.writeFile(workbook, `${name}.xlsx`);
}
function exportPersonnel() {
  writeWorkbook(
    '值班人员台账',
    '值班人员',
    personnel.value.map((row) => ({
      人员ID: row.userId,
      姓名: row.name,
      岗位: row.position,
      联系电话: row.phone,
      资质证书: qualificationSummary(row.qualifications),
      证书原件数: row.qualifications.reduce((total, item) => total + item.attachments.length, 0),
      值班区域: row.dutyArea,
      任务清单: taskLabels(row.taskList),
      状态: row.active ? '启用' : '停用',
    })),
  );
}
function exportSchedules() {
  writeWorkbook(
    '值班排班台账',
    '排班',
    schedules.value.map((row) => ({
      排班周期: row.cycleCode,
      班次: shiftOptions.find((item) => item.value === row.shiftType)?.label || row.shiftType,
      岗位: row.position,
      人员ID: row.dutyUserId,
      值班区域: row.dutyArea,
      开始时间: formatDateTime(row.startsAt),
      结束时间: formatDateTime(row.endsAt),
      资质快照: qualificationSummary(row.qualificationSnapshot),
      状态: row.status,
    })),
  );
}
function exportAdjustments() {
  writeWorkbook(
    '值班调班台账',
    '调班审批',
    adjustments.value.map((row) => ({
      排班ID: row.scheduleId,
      原值班人ID: row.originalUserId,
      接班人ID: row.targetUserId,
      调班原因: row.reason,
      申请人ID: row.requesterId,
      申请时间: formatDateTime(row.requestedAt),
      审批状态: adjustmentStatusLabel(row.status),
      审核人ID: row.reviewerId || '',
      审核意见: row.reviewOpinion || '',
      审核时间: row.reviewedAt ? formatDateTime(row.reviewedAt) : '',
    })),
  );
}
function exportAttendance() {
  writeWorkbook(
    '值班考勤台账',
    '考勤',
    attendance.value.map((row) => ({
      人员ID: row.dutyUserId,
      姓名: row.dutyUserName || '',
      岗位: row.position,
      计划到岗: formatDateTime(row.scheduledStartsAt),
      计划离岗: formatDateTime(row.scheduledEndsAt),
      实际到岗: row.arrivedAt ? formatDateTime(row.arrivedAt) : '',
      实际离岗: row.departedAt ? formatDateTime(row.departedAt) : '',
      打卡经度: row.longitude ?? '',
      打卡纬度: row.latitude ?? '',
      考勤状态: attendanceStatusLabel(row.status),
    })),
  );
}
function exportInspections() {
  writeWorkbook(
    '巡检台账',
    '巡检记录',
    inspectionRecords.value.map((row) => ({
      巡检时间: formatDateTime(row.inspectedAt),
      巡检人员: row.inspectorId,
      巡检任务: row.taskId,
      巡检点: checkpointLabel(row.checkpointId),
      设备ID: row.deviceId || '',
      数据来源: row.source,
      采集事实ID: row.ingestRecordIds.join('；'),
      检查结果: row.result === 'NORMAL' ? '正常' : '异常',
      问题描述: row.description || '',
      观测数据: JSON.stringify(row.observations || {}),
    })),
  );
}
async function loadIssues() {
  try {
    issues.value =
      unwrapResponse<SafetyInspectionIssueVo[]>(
        await safetyApi.inspectionIssues({ orgId: orgId.value || undefined, status: issueStatus.value || undefined }),
      ) || [];
    issueTableRef.value?.handleRefresh?.();
  } catch (cause: any) {
    error.value = cause?.message || '异常事项加载失败。';
  }
}
async function execute(task: () => Promise<unknown>, success: string, close: () => void) {
  submitting.value = true;
  error.value = '';
  try {
    await task();
    message.success(success);
    close();
    await reloadAll();
  } catch (cause: any) {
    error.value = cause?.message || `${success.replace('成功', '')}失败。`;
  } finally {
    submitting.value = false;
  }
}
function saveSchedule() {
  if (!scheduleForm.orgId || !scheduleForm.dutyPersonnelId || !scheduleRange.value) {
    error.value = '请选择场馆、值班人员和值班时段。';
    return;
  }
  return execute(
    () =>
      safetyApi.createDutySchedule({
        ...scheduleForm,
        startsAt: scheduleRange.value![0],
        endsAt: scheduleRange.value![1],
      }),
    '排班创建成功',
    () => (scheduleVisible.value = false),
  );
}
function saveDutyLog() {
  return execute(
    () =>
      safetyApi.createDutyLog({
        ...dutyForm,
        arrivedAt: dutyRange.value?.[0] || null,
        departedAt: dutyRange.value?.[1] || null,
      }),
    '值班记录提交成功',
    () => (dutyVisible.value = false),
  );
}
function saveAdjustment() {
  if (
    !selectedSchedule.value ||
    !isAdjustableSchedule(selectedSchedule.value) ||
    !adjustmentForm.targetUserId ||
    !adjustmentForm.reason.trim()
  ) {
    error.value = '请选择可调班的原排班、接班人员并填写原因。';
    return;
  }
  return execute(
    () =>
      safetyApi.createDutyAdjustment({
        scheduleId: adjustmentForm.scheduleId,
        targetUserId: adjustmentForm.targetUserId,
        reason: adjustmentForm.reason,
      }),
    '调班申请提交成功',
    () => (adjustmentVisible.value = false),
  );
}
function reviewAdjustment() {
  if (!selectedAdjustment.value || !adjustmentReviewForm.opinion.trim()) {
    error.value = '请填写调班审核意见。';
    return;
  }
  return execute(
    () => safetyApi.reviewDutyAdjustment(selectedAdjustment.value!.id, adjustmentReviewForm),
    '调班审核成功',
    () => (adjustmentReviewVisible.value = false),
  );
}
function reviewDutyLog() {
  if (!selectedDuty.value) return;
  return execute(
    () => safetyApi.reviewDutyLog(selectedDuty.value!.id, reviewForm),
    '审核成功',
    () => (reviewVisible.value = false),
  );
}
function saveInspectionPlan() {
  if (!planForm.orgId || !planForm.routeId || !planForm.name.trim()) {
    error.value = '请选择场馆、路线并填写计划名称。';
    return;
  }
  return execute(
    () =>
      editingPlan.value
        ? safetyApi.updateInspectionPlan(editingPlan.value.id, planForm)
        : safetyApi.createInspectionPlan(planForm),
    editingPlan.value ? '巡检计划调整成功' : '巡检计划创建成功',
    () => (planVisible.value = false),
  );
}
function saveCheckpoint() {
  if (!checkpointForm.orgId || !checkpointForm.name.trim() || !checkpointForm.locationId) {
    error.value = '请选择场馆、采集空间并填写巡检点名称。';
    return;
  }
  return execute(
    () =>
      editingCheckpoint.value
        ? safetyApi.updateInspectionCheckpoint(editingCheckpoint.value.id, checkpointForm)
        : safetyApi.createInspectionCheckpoint(checkpointForm),
    '巡检点保存成功',
    () => (checkpointVisible.value = false),
  );
}
function saveRoute() {
  if (!routeForm.orgId || !routeForm.name.trim() || !routeForm.points.some((point) => point.required)) {
    error.value = '请选择场馆、填写路线名称，并至少配置一个必检点。';
    return;
  }
  return execute(
    () =>
      editingRoute.value
        ? safetyApi.updateInspectionRoute(editingRoute.value.id, routeForm)
        : safetyApi.createInspectionRoute(routeForm),
    '巡检路线保存成功',
    () => (routeVisible.value = false),
  );
}
function saveTask() {
  if (!taskForm.inspectorId) {
    error.value = '请选择有有效资质的巡检人员。';
    return;
  }
  return execute(
    () =>
      editingTask.value
        ? safetyApi.assignInspectionTask(editingTask.value.id, taskForm.inspectorId)
        : safetyApi.createInspectionTask(taskForm.planId, {
            scheduledAt: taskForm.scheduledAt,
            inspectorId: taskForm.inspectorId,
          }),
    editingTask.value ? '任务指派成功' : '任务生成成功',
    () => (taskVisible.value = false),
  );
}
function saveInspectionRecord() {
  if (!recordForm.orgId || !recordForm.taskId || !recordForm.inspectorId || !recordForm.checkpointId) {
    error.value = '请选择已指派任务及其巡检点。';
    return;
  }
  if (recordForm.source !== 'MANUAL' && !recordForm.ingestRecordIds.length) {
    error.value = '请选择采集事实记录。';
    return;
  }
  let observations: Record<string, unknown>;
  try {
    observations = safeJson(observationText.value);
  } catch (cause: any) {
    error.value = cause.message;
    return;
  }
  return execute(
    () => safetyApi.createInspectionRecord({ ...recordForm, observations }),
    '巡检记录创建成功',
    () => (recordVisible.value = false),
  );
}
function saveIssue() {
  if (!selectedRecord.value) return;
  return execute(
    () => safetyApi.createInspectionIssue(selectedRecord.value!.id, issueForm),
    '处置事项创建成功',
    () => (issueVisible.value = false),
  );
}
function saveIssueAction() {
  if (!selectedIssue.value) return;
  return execute(
    () =>
      safetyApi.actOnInspectionIssue(selectedIssue.value!.id, {
        ...actionForm,
        maintenanceTaskId: actionForm.maintenanceTaskId || null,
      }),
    '处置状态更新成功',
    () => (actionVisible.value = false),
  );
}
watch(
  () => [route.query.orgId, route.query.tab, route.query.taskId],
  () => {
    if (typeof route.query.orgId === 'string') orgId.value = route.query.orgId;
    if (typeof route.query.tab === 'string') activeTab.value = route.query.tab;
    void reloadAll();
  },
);
onMounted(reloadAll);
</script>

<style scoped>
.operations-page {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  padding: 12px;
  box-sizing: border-box;
  overflow: hidden;
}
.safety-tabs {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
}
.safety-tabs :deep(.n-tabs-nav) {
  flex: none;
}
.safety-tabs :deep(.n-tabs-pane-wrapper),
.safety-tabs :deep(.n-tab-pane),
.safety-page-table {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
}
.state-alert {
  margin-bottom: 12px;
}
.small-dialog {
  width: min(560px, calc(100vw - 32px));
}
.personnel-snapshot {
  width: 100%;
}
.route-point-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.route-point-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border: 1px solid var(--n-border-color);
  border-radius: 6px;
}
.route-point-row > span:first-child {
  flex: 1;
  min-width: 160px;
}
:deep(.form-actions) {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
@media (max-width: 720px) {
  .operations-page {
    padding: 8px;
  }
  :deep(.n-form-item-blank) {
    min-width: 0;
  }
}
</style>
