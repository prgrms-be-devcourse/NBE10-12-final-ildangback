/**
 * 약관 동의 상태.
 *
 * 필수 2개는 가입 버튼을 여는 게이트로만 쓰고 서버로 보내지 않는다 — SignUpRequest 는
 * email · password · nickname 3개가 전부고 users 테이블에 동의 컬럼이 없다.
 * 선택(이벤트 · 혜택 알림)도 지금은 저장할 자리가 없다. 알림 기능이 들어올 때 필드를 만든다.
 */
export interface TermsState {
  service: boolean;
  privacy: boolean;
  marketing: boolean;
}

export const EMPTY_TERMS: TermsState = {
  service: false,
  privacy: false,
  marketing: false,
};

export function requiredTermsAccepted(terms: TermsState): boolean {
  return terms.service && terms.privacy;
}

const PENDING = "출시 전 공개 예정입니다.";

/**
 * 약관 본문은 화면에 두지 않는다.
 *
 * 처음에 임시로 써 넣었던 본문이 구현과 어긋나는 곳이 있었고(탈퇴 「즉시 파기」는 실제로는
 * 소프트 딜리트, 쓰지도 않는 쿠키 · 기기 식별값 수집 등) 법정 기재사항도 빠져 있었다.
 * 틀린 본문을 그럴듯한 법률 문서 말투로 두면 아무도 다시 읽지 않는다.
 *
 * **걷어낸 본문과 출시 전 체크리스트는 `docs/약관초안.md` 에 있다.**
 * 동의 체크박스와 설정 화면의 행은 그대로 둔다 — 출시 직전에 본문만 채우면 된다.
 */
export const TERMS_DOCUMENTS = {
  service: { title: "이용 약관", content: PENDING },
  privacy: { title: "개인정보 처리방침", content: PENDING },
  marketing: { title: "이벤트 · 혜택 알림 동의", content: PENDING },
} as const;
