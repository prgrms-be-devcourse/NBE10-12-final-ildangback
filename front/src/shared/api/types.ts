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
