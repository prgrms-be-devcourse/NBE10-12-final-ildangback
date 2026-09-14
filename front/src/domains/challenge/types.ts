import type { CharacterPose, CharacterSlots } from "../user/types";

export type ChallengeStatus = "READY" | "ACTIVE" | "ENDED";
export type FrequencyType = "DAILY" | "EVERY_N_DAYS" | "DAYS_OF_WEEK";
export type DaysOfWeek = "MON" | "TUE" | "WED" | "THU" | "FRI" | "SAT" | "SUN";
export type CheckInType = "PHOTO" | "VIDEO" | "LIVE";
// LIVE 는 별도 서브시스템(아직 없음) — 제출/선택 UI 는 이 둘만 다룬다.
export type SupportedCheckInType = "PHOTO" | "VIDEO";
export function isSupportedCheckInType(
  type: CheckInType,
): type is SupportedCheckInType {
  return type === "PHOTO" || type === "VIDEO";
}
/** 인증 방식 선택 카드를 토글한다 — 라디오가 아니라 사진/영상 동시 선택을 허용한다. */
export function toggleAllowedType(
  current: SupportedCheckInType[],
  value: SupportedCheckInType,
): SupportedCheckInType[] {
  return current.includes(value)
    ? current.filter((t) => t !== value)
    : [...current, value];
}
/**
 * LIVE 를 걸러낸 뒤 PHOTO/VIDEO 를 그대로 돌려준다(동시 허용 가능). 걸러낸 결과가 비면
 * (기존 값에 LIVE 만 있던 이상 상태) PHOTO 로 되돌린다.
 */
export function toSupportedTypesOrDefault(
  allowedTypes: CheckInType[],
): SupportedCheckInType[] {
  const supported = allowedTypes.filter(isSupportedCheckInType);
  return supported.length ? supported : ["PHOTO"];
}
export type ChallengeMemberRole = "OWNER" | "MEMBER";
export type ExtensionChoice = "PENDING" | "EXTEND" | "DECLINE";
export interface ChallengeSettings {
  startDate: string;
  endDate: string;
  frequencyType: FrequencyType;
  frequencyValue: number | null;
  daysOfWeek: DaysOfWeek[] | null;
  dailyCheckInCount: number;
  // This release can only submit PHOTO/VIDEO. LIVE is a separate subsystem, not yet built.
  allowedTypes: CheckInType[];
}
export interface ChallengeSummary extends ChallengeSettings {
  id: number;
  seqNo: number;
  status: ChallengeStatus;
}
export interface ChallengeDetail extends ChallengeSummary {
  groupId: number;
  requiredDayCount: number;
  groupCompletedDayCount: number;
  groupCurrentStreak: number;
  groupBestStreak: number;
  ownerId: number;
}
export interface ChallengeStatusResponse {
  challenge: ChallengeDetail;
  currentDay: number;
  totalDays: number;
  participantCount: number;
  periodProgressRate: number;
  isCheckInDay: boolean;
  myCurrentCount: number;
  myCompleted: boolean;
  extensionAvailable: boolean;
}
export interface MemberTodayStatusResponse {
  userId: number;
  nickname: string;
  todayCheckInCount: number;
  extensionChoice: ExtensionChoice;
}
export interface ChallengeUpdateRequest {
  startDate?: string | null;
  endDate?: string | null;
  frequencyType?: FrequencyType | null;
  frequencyValue?: number | null;
  daysOfWeek?: DaysOfWeek[] | null;
  dailyCheckInCount?: number | null;
  allowedTypes?: ("PHOTO" | "VIDEO")[] | null;
}
export interface ChallengeUpdateResponse extends ChallengeSettings {
  id: number;
}
export interface OwnerDelegationRequest {
  targetUserId: number;
}
export interface OwnerDelegationResponse {
  challengeId: number;
  previousOwnerId: number;
  newOwnerId: number;
}
export interface ExtensionChoiceRequest {
  choice: Exclude<ExtensionChoice, "PENDING">;
}
export interface ExtensionChoiceResponse {
  challengeId: number;
  userId: number;
  choice: ExtensionChoice;
  pendingCount: number;
  extendCount: number;
  declineCount: number;
}

export interface ChallengeCharacterResponse {
  userId: number;
  nickname: string;
  pose: CharacterPose;
  slots: CharacterSlots;
}
