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
