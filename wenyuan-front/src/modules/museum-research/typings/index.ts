/** 科学研究管理模块类型定义（对应后端 module/research VO） */

export interface ResTopicVo {
  id?: string;
  topicNo?: string;
  name?: string;
  direction?: string;
  level?: string;
  stage?: string;
  leaderId?: string;
  leaderName?: string;
  orgName?: string;
  external?: boolean;
  members?: string;
  purpose?: string;
  content?: string;
  expectedResult?: string;
  budget?: number;
  startDate?: string;
  endDate?: string;
  attachmentIds?: string;
  concludeDate?: string;
  flowStatus?: string;
  flowInstanceId?: string;
  createTime?: string;
  createBy?: string;
  delayedPlanCount?: number;
}

export interface ResTopicPlanVo {
  id?: string;
  topicId?: string;
  name?: string;
  startDate?: string;
  endDate?: string;
  goal?: string;
  members?: string;
  progress?: number;
  planStatus?: string;
  actualDate?: string;
  remark?: string;
  createTime?: string;
  topicName?: string;
  topicNo?: string;
}

export interface ResTopicConclusionVo {
  id?: string;
  topicId?: string;
  process?: string;
  achievement?: string;
  experience?: string;
  attachmentIds?: string;
  conclusionStatus?: string;
  reviewOpinion?: string;
  reviewBy?: string;
  reviewTime?: string;
  createTime?: string;
  createBy?: string;
  topicName?: string;
  topicNo?: string;
  leaderName?: string;
}

export interface ResAchievementVo {
  orgId?: string | null;
  id?: string;
  achievementNo?: string;
  title?: string;
  achievementType?: string;
  direction?: string;
  topicId?: string;
  authors?: string;
  team?: string;
  publishOrg?: string;
  publishDate?: string;
  keywords?: string;
  summary?: string;
  attachmentIds?: string;
  watermark?: string;
  published?: boolean;
  avgScore?: number;
  evalCount?: number;
  flowStatus?: string;
  flowInstanceId?: string;
  createTime?: string;
  createBy?: string;
  topicName?: string;
  topicNo?: string;
}

export interface ResAchievementEvalVo {
  id?: string;
  achievementId?: string;
  evalType?: string;
  score?: number;
  opinion?: string;
  suggestion?: string;
  evaluatorId?: string;
  evaluatorName?: string;
  createTime?: string;
  achievementTitle?: string;
}

export interface ResTransformVo {
  id?: string;
  achievementId?: string;
  name?: string;
  transformType?: string;
  transformStatus?: string;
  description?: string;
  startDate?: string;
  endDate?: string;
  effect?: string;
  report?: string;
  approveOpinion?: string;
  approveBy?: string;
  createTime?: string;
  createBy?: string;
  achievementTitle?: string;
  achievementNo?: string;
}

export interface ResExpertVo {
  id?: string;
  orgId?: string;
  userId?: string | null;
  name?: string;
  sex?: string;
  orgName?: string;
  title?: string;
  field?: string;
  region?: string;
  telephone?: string;
  email?: string;
  achievements?: string;
  reviewExperience?: string;
  internal?: boolean;
  enabled?: boolean;
  remarks?: string;
  createTime?: string;
}

export interface ResReviewVo {
  id?: string;
  reviewerUserId?: string | null;
  reviewType?: string;
  businessId?: string;
  businessName?: string;
  expertId?: string;
  expertName?: string;
  reviewStatus?: string;
  score?: number;
  conclusion?: string;
  opinion?: string;
  reviewTime?: string;
  createTime?: string;
}

export interface ResReviewSummaryVo {
  total: number;
  done: number;
  avgScore?: number;
  conclusionCounts: Record<string, number>;
  reviews: ResReviewVo[];
}

export interface ResDataAccessVo {
  id?: string;
  applicant?: string;
  applicantOrg?: string;
  contact?: string;
  dataScope?: string;
  topicId?: string;
  achievementId?: string;
  purpose?: string;
  agreementFileId?: string;
  accessStatus?: string;
  validFrom?: string;
  validTo?: string;
  approveOpinion?: string;
  approveBy?: string;
  approveTime?: string;
  createTime?: string;
  createBy?: string;
  expired?: boolean;
}

export interface ResNameCountVo {
  name: string;
  count: number;
}

export interface ResearchStatVo {
  topicTotal: number;
  topicInResearch: number;
  topicArchived: number;
  topicAlert: number;
  topicByStage: ResNameCountVo[];
  topicByDirection: ResNameCountVo[];
  topicByLevel: ResNameCountVo[];
  topicByYear: ResNameCountVo[];
  achievementTotal: number;
  achievementPassed: number;
  achievementThisYear: number;
  achievementAvgScore?: number;
  achievementByYear: ResNameCountVo[];
  achievementByType: ResNameCountVo[];
  achievementByDirection: ResNameCountVo[];
  achievementByTeam: ResNameCountVo[];
  evalTotal: number;
  transformTotal: number;
  transformCompleted: number;
  transformByType: ResNameCountVo[];
  expertTotal: number;
  reviewTotal: number;
  dataAccessTotal: number;
  dataAccessApproved: number;
}
