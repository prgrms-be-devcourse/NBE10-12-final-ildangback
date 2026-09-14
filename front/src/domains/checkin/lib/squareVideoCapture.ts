/**
 * 정사각 영상 체크인 캡처 상수 + `MediaRecorder` 관련 순수 로직.
 * 실제 프레임 드로잉/녹화 배선은 `CheckInVideoCamera` 가 한다(하드웨어 의존이라 여기 안 둔다).
 *
 * 크롭 계산 자체는 `squareCapture.ts` 의 `computeSquareCrop` 을 그대로 재사용한다 — 사진이든
 * 영상이든 "짧은 변 기준 정사각 센터 크롭" 원리는 동일하고, 영상은 그걸 매 프레임 반복 적용할
 * 뿐이다.
 */

/** 영상 인증 한 변의 상한. ADR: checkin-video-design.md — 서버가 항상 이 크기로 재인코딩. */
export const CHECKIN_VIDEO_MAX_EDGE = 1080;

/** 녹화 길이. 타이머로 정확히 이만큼 자동 정지한다(강제). */
export const CHECKIN_VIDEO_DURATION_MS = 2000;

/**
 * `MediaRecorder` 에 넘길 mimeType 후보. 브라우저가 실제로 뱉는 순서대로 나열한다.
 * Chrome/Firefox 는 webm(vp9 우선, 없으면 vp8), Safari 는 mp4 만 지원한다.
 */
const CANDIDATE_MIME_TYPES = [
  "video/webm;codecs=vp9",
  "video/webm;codecs=vp8",
  "video/webm",
  "video/mp4",
];

/**
 * 이 브라우저가 실제로 녹화 가능한 mimeType 을 우선순위대로 골라준다.
 * 지원하는 타입이 하나도 없으면(구형 브라우저) 던진다 — 호출부가 카메라를 에러 상태로 돌린다.
 *
 * `isSupported` 를 주입받는 이유는 테스트에서 `MediaRecorder.isTypeSupported` 없이(jsdom 엔
 * 없음) 순수하게 우선순위 로직만 검증하기 위해서다.
 */
export function pickRecorderMimeType(
  isSupported: (mimeType: string) => boolean = (mimeType) =>
    typeof MediaRecorder !== "undefined" &&
    MediaRecorder.isTypeSupported(mimeType),
): string {
  const found = CANDIDATE_MIME_TYPES.find(isSupported);
  if (!found) {
    throw new Error("이 브라우저에서는 영상 녹화를 지원하지 않습니다.");
  }
  return found;
}

/**
 * 업로드 파일명용 확장자. 서버는 확장자가 아니라 매직바이트로 형식을 검증하므로(§
 * MediaContentType) 여기 값이 틀려도 업로드가 깨지진 않는다 — 순전히 가독성용.
 * `MediaRecorder` 가 만드는 Blob.type 은 "video/webm;codecs=vp9" 처럼 코덱 파라미터가
 * 붙어 오므로 세미콜론 앞부분만 비교한다.
 */
export function extensionForMimeType(mimeType: string): string {
  const base = mimeType.split(";", 1)[0].trim();
  switch (base) {
    case "video/webm":
      return "webm";
    case "video/mp4":
      return "mp4";
    case "video/quicktime":
      return "mov";
    case "image/jpeg":
      return "jpg";
    default:
      return "jpg";
  }
}
