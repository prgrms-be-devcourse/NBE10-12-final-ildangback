import type {
  ChallengeSettings,
  ChallengeStatus,
  ChallengeSummary,
  FrequencyType,
  DaysOfWeek,
} from "../challenge/types";

export type GroupCategory =
  "DEV" | "READING" | "JOB" | "STUDY" | "EXERCISE" | "HEALTH" | "LIFE" | "ETC";
export type GroupSort = "LATEST" | "POPULAR" | "START_SOON";
export type GroupStatus = "READY" | "ACTIVE" | "ENDED";
export type MapType = "STUDY_ROOM" | "GYM";
export type Visibility = "PUBLIC" | "CODE_ONLY";
export interface GroupJoinRequest {
  inviteCode: string;
}
export interface InviteCodeResponse {
  inviteCode: string;
}
export interface PublicGroupQuery {
  keyword?: string;
  category?: GroupCategory;
  sort: GroupSort;
  size?: number;
}
export interface MyGroupQuery {
  status?: GroupStatus;
  size?: number;
}
export interface SliceResponse<T> {
  content: T[];
  hasNext: boolean;
  nextCursor: number | null;
}
export interface GroupSummary {
  id: number;
  name: string;
  description: string | null;
  category: GroupCategory;
  currentMembers: number;
  maxMembers: number;
  status: GroupStatus;
  challengeId: number;
  startDate: string;
  endDate: string;
  frequencyType: FrequencyType;
  frequencyValue: number | null;
  weekdays: DaysOfWeek[];
  dailyCheckInCount: number;
}
export interface MyGroupSummary {
  groupId: number;
  challengeId: number;
  name: string;
  category: GroupCategory;
  groupStatus: GroupStatus;
  challengeStatus: ChallengeStatus;
  participantCount: number;
  currentDay: number;
  totalDays: number;
  periodProgressRate: number;
  todayCheckInCount: number;
  dailyCheckInCount: number;
  todayCompleted: boolean;
}
export interface GroupResponse {
  id: number;
  name: string;
  description: string | null;
  category: GroupCategory;
  mapType: MapType;
  visibility: Visibility;
  maxMembers: number;
  currentMembers: number;
  ownerId: number;
  status: GroupStatus;
  createdAt: string;
}
export interface GroupMember {
  id: number;
  groupId: number;
  userId: number;
  nickname: string;
  status: "ACTIVE" | "LEFT" | "KICKED";
  leftAt: string | null;
  joinedAt: string;
}
export interface GroupDetailResponse {
  group: GroupResponse;
  currentChallenge: ChallengeSummary | null;
  members: GroupMember[];
}
export interface GroupJoinResponse {
  groupMember: GroupMember;
  challengeId: number;
  challengeMemberId: number;
}
export interface GroupCreateRequest {
  name: string;
  description?: string | null;
  category: GroupCategory;
  mapType: MapType;
  visibility: Visibility;
  maxMembers: number;
  challenge: Omit<ChallengeSettings, "allowedTypes"> & {
    // LIVE 는 별도 서브시스템(아직 없음) — 생성/설정 화면은 PHOTO/VIDEO 중 하나만 고른다.
    allowedTypes: ("PHOTO" | "VIDEO")[];
  };
}

/** GET /api/groups/{groupId}/challenges returns a plain array of these items. */
export interface SeasonSummary {
  id: number;
  seqNo: number;
  status: ChallengeStatus;
}

export type KickVoteChoice = "NONE" | "AGREE" | "DISAGREE";

export interface KickVoteStatusResponse {
  groupId: number;
  targetOwnerId: number;
  inProgress: boolean;
  agreeCount: number;
  disagreeCount: number;
  eligibleVoters: number;
  myChoice: KickVoteChoice;
  startedAt: string | null;
  expiresAt: string | null;
}
