<template>
  <NSpace vertical :size="16">
      <CollectionArchiveIssues :data="archives" />
    <NCollapse v-if="experts?.length">
      <NCollapseItem
        v-for="expert in experts"
        :key="expert.id || expert.expertKey"
        :name="expert.id || expert.expertKey"
        :title="`${expert.expertName} · ${expert.agree ? '同意退出' : '不同意退出'} · ${expert.organization}`"
      >
        <NDescriptions bordered :column="1">
          <NDescriptionsItem label="专家职称及方向"
            >{{ expert.professionalTitle }} / {{ expert.specialty }}</NDescriptionsItem
          >
          <NDescriptionsItem label="实际评估日期">{{ formatLocalDateValue(expert.assessedAt) }}</NDescriptionsItem>
          <NDescriptionsItem label="逐件独立评估意见" content-style="white-space: pre-wrap; overflow-wrap: anywhere;">{{ expert.opinion }}</NDescriptionsItem>
          <NDescriptionsItem label="高级职称证明"
            ><CollectionCaseOriginalFiles :case-type="caseType" :case-id="caseId" :archive-files="stageFiles(`EXPERT_${expert.id}`)" :value="expert.seniorQualificationFileIds" /></NDescriptionsItem>
          <NDescriptionsItem label="馆外独立声明"
            ><CollectionCaseOriginalFiles :case-type="caseType" :case-id="caseId" :archive-files="stageFiles(`EXPERT_${expert.id}`)" :value="expert.independenceStatementFileIds" /></NDescriptionsItem>
          <NDescriptionsItem label="本人签署的书面意见"
            ><CollectionCaseOriginalFiles :case-type="caseType" :case-id="caseId" :archive-files="stageFiles(`EXPERT_${expert.id}`)" :value="expert.signedOpinionFileIds" /></NDescriptionsItem>
        </NDescriptions>
      </NCollapseItem>
    </NCollapse>
    <NText v-if="caseId && notices?.length" depth="3">旧版或撤下的公开稿可能需公开核查岗位查阅，案卷查阅权限不会自动开放该类原件。</NText>
    <NCollapse v-if="notices?.length">
      <NCollapseItem
        v-for="notice in notices"
        :key="notice.id"
        :name="notice.id"
        :title="`${notice.kind === 'RESULT' ? '退出结果公告' : '退出前公示'} · ${channels[notice.channel] || notice.channel} · ${formatLocalDateValue(notice.startedOn)}`"
      >
        <NDescriptions bordered :column="1">
          <NDescriptionsItem label="公示或公告期限"
            >{{ formatLocalDateValue(notice.startedOn) }} 至
            {{ notice.endedOn ? formatLocalDateValue(notice.endedOn) : '持续中' }}</NDescriptionsItem
          >
          <NDescriptionsItem label="实际发布网址或地点">{{ notice.urlOrLocation }}</NDescriptionsItem>
          <NDescriptionsItem label="公开稿核查版本">{{
            notice.disclosureReviewId || '旧记录尚无核查版本关联'
          }}</NDescriptionsItem>
          <NDescriptionsItem label="各渠道共用的公示内容"
            ><CollectionCaseOriginalFiles :case-type="caseType" :case-id="caseId" :archive-files="stageFiles(`NOTICE_${notice.id}`)" :value="notice.publicContentFileIds" /></NDescriptionsItem>
          <NDescriptionsItem label="实发凭据或现场照片"
            ><CollectionCaseOriginalFiles :case-type="caseType" :case-id="caseId" :archive-files="stageFiles(`NOTICE_${notice.id}`)" :value="notice.publicationEvidenceFileIds" /></NDescriptionsItem>
        </NDescriptions>
      </NCollapseItem>
    </NCollapse>
    <NCollapse v-if="administrativeEvidence?.length">
      <NCollapseItem
        v-for="document in administrativeEvidence"
        :key="document.id"
        :name="document.id"
        :title="`${document.authorityName} · ${document.documentNo}`"
      >
        <NDescriptions bordered :column="1">
          <NDescriptionsItem label="行政机关"
            >{{ levels[document.level] || document.level }} / {{ document.authorityName }}</NDescriptionsItem
          >
          <NDescriptionsItem label="行政文书"
            >{{ document.action === 'APPROVAL' ? '批准' : '备案' }} / {{ document.documentNo }} /
            {{ formatLocalDateValue(document.issuedAt) }}</NDescriptionsItem
          >
          <NDescriptionsItem label="原件"><CollectionCaseOriginalFiles :case-type="caseType" :case-id="caseId" :archive-files="stageFiles(`${administrativePhasePrefix}_${document.id}`)" :value="document.fileIds" /></NDescriptionsItem>
        </NDescriptions>
      </NCollapseItem>
    </NCollapse>
  </NSpace>
</template>
<script setup lang="ts">
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
import type { CollectionReadonlyFile } from '../utils/readonlyFiles';
import { formatLocalDateValue } from '@/utils';
import CollectionCaseOriginalFiles from './CollectionCaseOriginalFiles.vue';
const props = withDefaults(defineProps<{
  caseType?: 'deregistration' | 'transfer' | 'restoration' | 'restoration-restricted' | 'restoration-closeout';
  caseId?: string | null;
  experts?: Record<string, any>[];
  notices?: Record<string, any>[];
  administrativeEvidence?: Record<string, any>[];
  archives?: { id: string; phase: string; files?: CollectionReadonlyFile[] }[];
  administrativePhasePrefix?: 'ADMIN' | 'FILED';
}>(), { caseType: 'deregistration', administrativePhasePrefix: 'ADMIN' });
const stageFiles = (phase: string) => (props.archives || []).filter(archive => archive.phase === phase).flatMap(archive => archive.files || []);
const channels: Record<string, string> = {
  AUTHORITY_WEBSITE: '主管文物部门官网',
  MUSEUM_ONSITE: '博物馆现场',
  MUSEUM_WEBSITE: '博物馆官网',
};
const levels: Record<string, string> = {
  COMPETENT: '主管文物行政部门',
  PROVINCE: '省级文物行政部门',
  NATIONAL: '国务院文物行政部门',
};
</script>
