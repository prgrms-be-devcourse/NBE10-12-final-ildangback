export type ChallengeStatus = "READY" | "ACTIVE" | "ENDED";
export type FrequencyType = "DAILY" | "EVERY_N_DAYS" | "DAYS_OF_WEEK";
export type DaysOfWeek = "MON" | "TUE" | "WED" | "THU" | "FRI" | "SAT" | "SUN";
export type CheckInType = "PHOTO" | "VIDEO" | "LIVE";
export type ChallengeMemberRole = "OWNER" | "MEMBER";
export type ExtensionChoice = "PENDING" | "EXTEND" | "DECLINE";
export interface ChallengeSettings {
  startDate: string;
  endDate: string;
  frequencyType: FrequencyType;
  frequencyValue: number | null;
  daysOfWeek: DaysOfWeek[] | null;
  dailyCheckInCount: number;
  // This release can only submit PHOTO. Reads remain tolerant of older data.
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
  allowedTypes?: "PHOTO"[] | null;
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
