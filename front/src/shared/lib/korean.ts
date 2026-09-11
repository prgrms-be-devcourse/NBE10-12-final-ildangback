/**
 * 목적격 조사. 받침이 있으면 "을", 없으면 "를".
 *
 * 아이템 이름처럼 값이 바뀌는 말 뒤에 조사를 붙일 때 쓴다.
 * 하나로 박아 두면 "검정 비니을" 같은 문장이 나온다.
 */
export function objectParticle(word: string): "을" | "를" {
  const last = word.charCodeAt(word.length - 1);
  // 한글 음절이 아니면(숫자 · 영문 · 이모지) 판단할 수 없으니 "를" 로 둔다.
  if (Number.isNaN(last) || last < 0xac00 || last > 0xd7a3) return "를";
  // 한글 음절 = 초성 + 중성 + 종성. 종성 인덱스가 0 이면 받침이 없다.
  return (last - 0xac00) % 28 === 0 ? "를" : "을";
}
