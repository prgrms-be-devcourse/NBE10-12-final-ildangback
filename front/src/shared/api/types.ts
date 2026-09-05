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
  /**
   * 미인증이어도 로그인과 이용은 막지 않는다. 막는 것은 비밀번호 재설정뿐이다.
   * 소셜 가입자는 인증 메일을 받은 적이 없어 계속 false 다.
   */
  emailVerified: boolean;
  /**
   * 비밀번호가 설정된 계정인지. 소셜로만 가입하면 false 다.
   *
   * "소셜 가입자인가" 가 아니라 "비밀번호가 있는가" 이다. 지금은 자동 연결이 없어
   * 둘이 같은 뜻이지만, 계정 연결이 생기면 갈라진다.
   */
  hasPassword: boolean;
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
  /**
   * 이번 요청으로 계정이 새로 만들어졌는지. 소셜은 가입과 로그인이 같은 엔드포인트라
   * 응답만으로는 구분할 수 없어서 서버가 알려준다. 비밀번호 로그인은 항상 false 다.
   */
  newUser: boolean;
  user: UserProfileResponse;
}

export interface AvailabilityResponse {
  available: boolean;
}

/** 재설정 링크가 어느 계정 것인지 사용자가 확인할 수 있게, 가린 이메일만 준다. */
export interface PasswordResetTargetResponse {
  email: string;
}
