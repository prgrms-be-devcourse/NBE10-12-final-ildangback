/**
 * 체크인 미디어 업로드 제약.
 *
 * 서버(checkin-api-spec.yml)는 50MB 까지 허용하지만 프론트는 40MB 로 더 빡빡하게 잡는다.
 * getUserMedia + canvas 로 우리가 직접 만든 jpeg 라 실제로는 이 값 근처도 안 가지만,
 * 예상 못 한 큰 출력이 나올 때 업로드 왕복 대신 재촬영을 유도하는 방어선이다.
 */
export const MAX_CHECKIN_FILE_BYTES = 40 * 1024 * 1024;

export function isWithinCheckInFileLimit(bytes: number): boolean {
  return bytes <= MAX_CHECKIN_FILE_BYTES;
}
