export interface FieldError {
  field: string;
  reason: string;
}

export interface ErrorResponse {
  code: string;
  message: string;
  errors: FieldError[];
}

export interface UserSummaryResponse {
  id: number;
  email: string;
  nickname: string;
}

export interface UserProfileResponse {
  id: number;
  email: string;
  nickname: string;
  introduction: string | null;
  personalStreak: number;
  bestStreak: number;
  /** 인증 전에는 null 이다. */
  lastCheckedInDate: string | null;
  /**
   * 서버가 LocalDateTime 이라 타임존 오프셋이 없고 소수점 이하 초 자리수가 고정이 아니다.
   * 실측 "2026-08-31T15:52:54.06". new Date() 로 파싱하면 브라우저 로컬로 읽으니
   * 화면에 찍을 때 KST 기준임을 감안해야 한다.
   */
  createdAt: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
}

export interface LoginResponse extends TokenResponse {
  user: UserProfileResponse;
}

export interface AvailabilityResponse {
  available: boolean;
}

/** 백엔드 커서 페이지 공통 모양. meta 래핑 없음, size 없음. */
export interface SliceResponse<T> {
  content: T[];
  hasNext: boolean;
  nextCursor: number | null;
}

/** CUSTOM은 서버엔 없는 프론트 전용 값 — 선택되면 period 대신 from/to를 보낸다. */
export type PeriodFilter = "THIS_MONTH" | "LAST_MONTH" | "ALL" | "CUSTOM";
export type PointChangeType = "EARN" | "DEDUCT" | "ALL";

export type UserPointReason =
  | "CHECK_IN"
  | "CHALLENGE_BONUS"
  | "MONTHLY_MERGE_BONUS"
  | "ITEM_PURCHASE"
  | "WITHDRAWAL_PENALTY";

export interface PointBalanceResponse {
  balance: number;
  monthlyEarned: number;
  monthlySpent: number;
  totalEarned: number;
}

export interface UserPointHistoryResponse {
  id: number;
  userId: number;
  sourceName: string;
  amount: number;
  reason: UserPointReason;
  balanceAfter: number;
  /** LocalDateTime 문자열. date.ts 의 규칙과 동일하게 파싱 없이 슬라이스해서 쓴다. */
  createdAt: string;
}

// ===== record (머지) =====

export type MergeType = "MONTHLY" | "FINAL";

export type GroupCategory =
  "DEV" | "READING" | "JOB" | "STUDY" | "EXERCISE" | "HEALTH" | "LIFE" | "ETC";

/**
 * 챌린지 하나의 머지 진행 현황. currentSeqNo/currentCycleDay는 진행 중인(아직
 * 발행 안 된) 회차가 있을 때만 채워진다 - 챌린지 시작일 기준 달력 계산일 뿐이라
 * 체크인 완료 여부와는 무관하다(완료율 아님). totalMergeCount는 "월간 머지"만
 * 센다 - 마지막 사이클은 최종 머지로 발행되므로 포함하지 않는다.
 */
export interface ChallengeMergeOverviewResponse {
  challengeId: number;
  groupName: string;
  category: GroupCategory;
  periodStart: string;
  periodEnd: string;
  totalDays: number;
  cycleLengthDays: number;
  totalMergeCount: number;
  completedMergeCount: number;
  hasFinalMerge: boolean;
  currentSeqNo: number | null;
  currentCycleDay: number | null;
}

export interface MergeParticipantResponse {
  userId: number;
  nickname: string;
  ranking: number;
  completionRate: number;
  /** 며칠을 인증했는지 - "N/기간일수" 표시엔 이 값을 써야 한다(totalCheckInCount 아님). */
  completedDayCount: number;
  totalCheckInCount: number;
  bestStreakInPeriod: number;
  earnedPoints: number;
  contributionRate: number;
  /** "주간/월별 인증 추이" 그래프용. 값이 없으면(CheckIn 미연동) 빈 배열이다. */
  checkInTrendLabels: string[];
  checkInTrendCounts: number[];
}

/** 머지 목록 화면(월간+최종 함께)의 한 항목. seqNo는 FINAL이면 null이다. */
export interface MergeSummaryResponse {
  type: MergeType;
  mergeId: number;
  seqNo: number | null;
  /** LocalDate 문자열(YYYY-MM-DD). */
  periodStart: string;
  periodEnd: string;
  totalDays: number;
  totalCheckInCount: number;
  averageCompletionRate: number;
  publishedAt: string;
}

export interface MonthlyMergeDetailResponse {
  challengeId: number;
  seqNo: number;
  periodStart: string;
  periodEnd: string;
  totalDays: number;
  totalCheckInCount: number;
  averageCompletionRate: number;
  publishedAt: string;
  participants: MergeParticipantResponse[];
}

export interface FinalMergeDetailResponse {
  challengeId: number;
  periodStart: string;
  periodEnd: string;
  totalDays: number;
  totalCheckInCount: number;
  averageCompletionRate: number;
  publishedAt: string;
  participants: MergeParticipantResponse[];
}

export interface MyMonthlyMergeResponse {
  id: number;
  challengeId: number;
  seqNo: number;
  periodStart: string;
  periodEnd: string;
  myRanking: number;
  myCompletionRate: number;
  myEarnedPoints: number;
  groupAverageCompletionRate: number;
}

/**
 * "개인 통계" 화면(GET /users/me/stats) 응답. 전부 이미 발행된 머지 결과(스냅샷)를
 * 집계한 값이다 - 진행 중인 챌린지의 오늘 기록은 반영되지 않는다. "패턴"(요일별/
 * 시간대별) 탭은 체크인 원본 날짜/시간이 있어야 해서 아직 없다.
 */
export interface PersonalStatsResponse {
  summary: SummaryStatResponse;
  monthlyTrend: MonthlyTrendItemResponse[];
  categoryBreakdown: CategoryStatResponse[];
  heatmap: HeatmapCellResponse[];
}

/**
 * 요약 탭. completedDayCount/missedDayCount는 "인증 대상일 중 며칠을 채웠는지"
 * 기준이라 totalCheckInCount(하루 여러 번 인증 가능)와는 다르다.
 * completedChallengeCount는 최종 머지까지 발행된(끝까지 참여한) 챌린지 수,
 * inProgressChallengeCount는 아직 최종 머지가 없는(진행 중인) 챌린지 수다.
 */
export interface SummaryStatResponse {
  totalCheckInCount: number;
  completedDayCount: number;
  missedDayCount: number;
  bestStreakEver: number;
  averageCompletionRate: number;
  completedChallengeCount: number;
  inProgressChallengeCount: number;
}

/** 월별 탭의 막대/꺾은선 그래프용 1개월치 데이터. month는 "yyyy-MM"(달력 월 기준). */
export interface MonthlyTrendItemResponse {
  month: string;
  checkInCount: number;
  completionRate: number;
}

/** 카테고리 탭 1건. missedDayCount는 "카테고리별 놓친 인증 비율" 도넛 차트용. */
export interface CategoryStatResponse {
  category: GroupCategory;
  challengeCount: number;
  totalCheckInCount: number;
  averageCompletionRate: number;
  missedDayCount: number;
}

/**
 * 요약 탭의 "누적 인증 잔디" 한 칸. 일 단위 체크인 로그가 없어서 그 달의 평균
 * 완주율을 0~4단계 진하기로 근사한다(month는 "yyyy-MM").
 */
export interface HeatmapCellResponse {
  month: string;
  level: number;
// ===== item (상점, 캐릭터) =====

export type ItemSlot = "HEAD" | "TOP" | "BOTTOM" | "SHOES";

export interface ItemResponse {
  id: number;
  slot: ItemSlot;
  name: string;
  /** 등록된 이미지가 없으면 null 이다. */
  imageUrl: string | null;
  price: number;
}

export interface ShopItemResponse {
  item: ItemResponse;
  owned: boolean;
  equipped: boolean;
}

export interface UserItemResponse {
  /** userItemId 다. 착용과 해제는 itemId 가 아니라 이 값을 보낸다. */
  id: number;
  item: ItemResponse;
  /** 착용 중이 아니면 null. */
  equippedSlot: ItemSlot | null;
  /** LocalDateTime 문자열. */
  purchasedAt: string;
}

export interface CharacterResponse {
  /** 네 부위가 항상 다 온다. 안 낀 부위는 null 이다. */
  slots: Record<ItemSlot, string | null>;
}

export interface ItemPurchaseResponse {
  userItemId: number;
  itemId: number;
  purchasedAt: string;
  /** 차감이 끝난 뒤의 잔액. */
  balance: number;
  /** 서버가 구매와 함께 착용까지 끝낸다. 그때 채워진 부위다. */
  equippedSlot: ItemSlot | null;
}
