/**
 * 체크인 미디어 업로드 제약.
 *
 * 서버(checkin-video-design.md)는 사진 50MB / 영상 10MB 까지 허용하지만, 프론트는 더 빡빡하게
 * 잡는다. getUserMedia + canvas 로 우리가 직접 만든 사진/영상이라 실제로는 이 값 근처도 안
 * 가지만, 예상 못 한 큰 출력이 나올 때 업로드 왕복 대신 재촬영을 유도하는 방어선이다.
 */
export const MAX_CHECKIN_PHOTO_FILE_BYTES = 40 * 1024 * 1024;
/** 2초 클립은 해상도와 무관하게 데이터량이 작아(보통 1~4MB) 서버 상한(10MB)에 맞춰 잡는다. */
export const MAX_CHECKIN_VIDEO_FILE_BYTES = 10 * 1024 * 1024;

export function isWithinCheckInFileLimit(
  bytes: number,
  kind: "photo" | "video" = "photo",
): boolean {
  const max =
    kind === "video"
      ? MAX_CHECKIN_VIDEO_FILE_BYTES
      : MAX_CHECKIN_PHOTO_FILE_BYTES;
  return bytes <= max;
}
