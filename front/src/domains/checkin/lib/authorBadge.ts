/**
 * 인증 작성자 표시용 이니셜 뱃지.
 *
 * 실제 아바타 이미지는 안 쓴다 — `nickname` 앞 글자를 원 안에 넣고, 배경색은
 * `userId` 로 팔레트에서 고정 선택한다(같은 사람은 항상 같은 색).
 */

/** 뱃지에 넣을 글자. 그리드 셀은 1자, 라이트박스는 2자. */
export function nicknameInitial(nickname: string, length: 1 | 2 = 1): string {
  return [...nickname.trim()].slice(0, length).join("").toUpperCase();
}

// 보라 계열 위주 + 대비되는 색 몇 개. 어두운 톤이라 흰 글자가 잘 보인다.
const BADGE_COLORS = [
  "#8058c4",
  "#c4587f",
  "#5887c4",
  "#58a06a",
  "#b0863c",
  "#7d5bd0",
];

export function badgeColor(userId: number): string {
  return BADGE_COLORS[Math.abs(userId) % BADGE_COLORS.length];
}
