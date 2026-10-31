<template>
  <n-descriptions label-placement="top" :column="2" bordered style="margin: 15px; flex: 1">
    <n-descriptions-item label="部署就绪检查" :span="2">
      <n-flex justify="space-between" align="center" :wrap="true">
        <n-text depth="3">扫描字体、文档预览、数据库、文件存储和运行环境，核对当前部署是否完整。</n-text>
        <n-button type="primary" @click="deploymentDiagnosticsDialog = true">查看部署扫描</n-button>
      </n-flex>
      <n-modal
        v-model:show="deploymentDiagnosticsDialog"
        preset="card"
        title="部署环境就绪扫描"
        style="width: min(1200px, 92vw)"
      >
        <n-scrollbar style="max-height: 78vh; padding-right: 12px">
          <DeploymentDiagnostics v-if="deploymentDiagnosticsDialog" />
        </n-scrollbar>
      </n-modal>
    </n-descriptions-item>
    <n-descriptions-item label="系统初始化" :span="2">
      <p>可在此同步代码声明或重置系统数据。权限同步会保留现有角色授权；生产系统执行重置操作前请谨慎确认。</p>
      <n-flex>
        <n-button type="error" @click="handleReset" :loading="loadingReset">重置系统</n-button>
        <n-button type="warning" @click="handleResetDict" :loading="loadingResetDict">重置字典</n-button>
        <n-button type="warning" @click="handleResetMenu" :loading="loadingResetMenu">重置菜单</n-button>
        <n-button type="success" @click="handleSyncPermission" :loading="loadingSyncPermission">同步权限</n-button>
        <n-button type="warning" @click="handleResetPermission" :loading="loadingResetPermission">重置权限</n-button>
        <n-button type="warning" @click="handleUpdateData" :loading="loadingUpdate">更新数据</n-button>
        <n-button-group>
          <n-button type="error" @click="handleStressTester('start')">启动资源平衡</n-button>
          <n-button type="success" @click="handleStressTester('stop')">停止资源平衡</n-button>
        </n-button-group>
      </n-flex>
    </n-descriptions-item>
    <n-descriptions-item label="行政区划数据采集">
      <p>
        点击「重置地区」按钮重新从国家统计局获取地区代码。<a
          href="https://www.mca.gov.cn/mzsj/xzqh/2025/202401xzqh.html"
          target="_blank"
          >查看来源</a
        >
      </p>
      <n-button type="success" @click="handleResetArea" :loading="loadingResetArea">重置地区</n-button>
    </n-descriptions-item>
    <n-descriptions-item label="重置对应地区街道信息">
      <p>
        点击「重置对应街道信息」按钮重新从国家统计局获取街道信息。<a href="http://www.stats.gov.cn/sj/" target="_blank"
          >查看编码</a
        >
      </p>
      <n-form inline label-placement="left" :rules="rules" :model="form">
        <n-form-item label="市县代码" path="areaCode">
          <n-input v-model:value="form.areaCode" placeholder="请输入市县代码"></n-input>
        </n-form-item>
        <n-button type="success" @click="handleResetStreetArea" :loading="loadingInitStreet">重置对应街道信息</n-button>
      </n-form>
    </n-descriptions-item>
    <n-descriptions-item label="系统部署">
      <n-flex>
        <n-button type="error" @click="frontDeployDialog = true">前端部署</n-button>
        <n-button disabled type="error" @click="backendDeployDialog = true">后端部署</n-button>
      </n-flex>
      <n-modal size="small" title="前端部署" style="width: 60%" preset="card" v-model:show="frontDeployDialog">
        <FrontDeploy></FrontDeploy>
      </n-modal>
      <n-modal size="small" title="后端部署" style="width: 60%" preset="card" v-model:show="backendDeployDialog">
        <BackendDeploy></BackendDeploy>
      </n-modal>
    </n-descriptions-item>
    <n-descriptions-item label="修改日志等级">
      <p>点击「修改日志等级」按钮执行系统日志等级修改操作,修改在重启后变更为原始设置级别。</p>
      <n-form inline label-placement="left" :rules="rulesLog" :model="formLog">
        <n-form-item label="日志等级" path="level">
          <h-dict-select type="sys_log_level" v-model:value="formLog.level" placeholder="请选择类型"></h-dict-select>
        </n-form-item>
        <n-button type="warning" @click="handleChangeLevel">修改日志等级</n-button>
      </n-form>
    </n-descriptions-item>
    <n-descriptions-item label="清除缓存">
      <p>点击「清除缓存」按钮执行模块下数据缓存更新操作。</p>
      <n-button type="warning" @click="handleCacheClear">清除缓存</n-button>
    </n-descriptions-item>
  </n-descriptions>
</template>
<script lang="ts" setup>
import {
  initStreet,
  reset,
  resetArea,
  resetDict,
  resetMenu,
  resetPermission,
  syncPermission,
  update,
} from '../api/install';
import { clear } from '../api/cache';
import { changeLogLevel, startStressTester, stopStressTester } from '../api/system';
import { confirm } from '@/utils';
import { ref } from 'vue';
import FrontDeploy from '../components/deploy.vue';
import BackendDeploy from '../components/backendDeploy.vue';
import DeploymentDiagnostics from '../components/DeploymentDiagnostics.vue';

const frontDeployDialog = ref(false);
const backendDeployDialog = ref(false);
const deploymentDiagnosticsDialog = ref(false);

const areaCode = ref('310120');

const form = ref({ areaCode: '310120' });
const rules = {
  areaCode: {
    required: true,
    message: '请输入地区代码',
  },
};

const formLog = ref({ level: 'INFO' });
const rulesLog = {
  level: {
    required: true,
    message: '请选择日志等级',
  },
};

const loadingReset = ref(false);

function handleReset() {
  confirm({
    content:
      '即将重置系统自动创建的字典、菜单、权限、初始化默认系统数据，一般不需要操作。注意，重置会导致用户角色权限需要重新配置。',
    onPositiveClick() {
      loadingReset.value = true;
      reset({}).finally(() => {
        loadingReset.value = false;
      });
    },
  });
}

const loadingResetDict = ref(false);

function handleResetDict() {
  confirm({
    content: '即将重置字典，本操作仅重置系统创建字典，请确认您的操作。',
    onPositiveClick() {
      loadingResetDict.value = true;
      resetDict().finally(() => {
        loadingResetDict.value = false;
      });
    },
  });
}

const loadingResetMenu = ref(false);

function handleResetMenu() {
  confirm({
    content: '即将重置菜单，本操作仅重置系统创建菜单，但是可能导致菜单顺序与预期不一致，请确认您的操作。',
    onPositiveClick() {
      loadingResetMenu.value = true;
      resetMenu().finally(() => {
        loadingResetMenu.value = false;
      });
    },
  });
}

const loadingResetPermission = ref(false);

const loadingSyncPermission = ref(false);

function handleSyncPermission() {
  confirm({
    content: '将按当前平台的代码声明补齐并更新系统权限，同时保留现有角色授权。是否继续？',
    onPositiveClick() {
      loadingSyncPermission.value = true;
      syncPermission().finally(() => {
        loadingSyncPermission.value = false;
      });
    },
  });
}

function handleResetPermission() {
  confirm({
    content: '即将重置权限，本操作仅重置系统创建权限，请确认您的操作。',
    onPositiveClick() {
      loadingResetPermission.value = true;
      resetPermission().finally(() => {
        loadingResetPermission.value = false;
      });
    },
  });
}

const loadingResetArea = ref(false);

function handleResetArea() {
  confirm({
    content: '即将重置地区，本操作从国家统计局获取地区代码，请确认您的操作。',
    onPositiveClick() {
      loadingResetArea.value = true;
      resetArea().finally(() => {
        loadingResetArea.value = false;
      });
    },
  });
}

const loadingInitStreet = ref(false);

function handleResetStreetArea() {
  confirm({
    content: '即将重置地区下街道，本操作从国家统计局获取地区街道代码，请确认您的操作。',
    onPositiveClick() {
      loadingInitStreet.value = true;
      initStreet({ areaCode: form.value.areaCode }).finally(() => {
        loadingInitStreet.value = false;
      });
    },
  });
}

const loadingUpdate = ref(false);

function handleUpdateData() {
  confirm({
    content: '即将重置数据，本操作根据模块规则处理数据更新逻辑，请确认您的操作。',
    onPositiveClick() {
      loadingUpdate.value = true;
      update({}).finally(() => {
        loadingUpdate.value = false;
      });
    },
  });
}

function handleCacheClear() {
  confirm({
    content: '即将清除缓存，请确认您的操作。',
    onPositiveClick() {
      clear().then(() => {
        location.reload();
      });
    },
  });
}

function handleChangeLevel() {
  confirm({
    content: '即将清除修改日志等级，请确认您的操作。',
    onPositiveClick() {
      changeLogLevel(formLog.value);
    },
  });
}

function handleStressTester(flag: string) {
  confirm({
    content: `即将${flag == 'start' ? '启动' : '停止'}系统资源平衡，请确认您的操作。`,
    onPositiveClick() {
      if (flag === 'start') {
        startStressTester();
      } else {
        stopStressTester();
      }
    },
  });
}
</script>
<style scoped>
:deep(.n-descriptions-table) {
  table-layout: fixed;
}
</style>
