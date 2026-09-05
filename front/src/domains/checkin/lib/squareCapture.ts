/**
 * getUserMedia 로 잡은 영상 프레임을 정사각 인증 사진으로 만들 때의 크롭/스케일 계산과
 * 실제 canvas 추출.
 *
 * 원본을 짧은 변 기준 정사각으로 센터 크롭하고, 한 변이 maxEdge 를 넘으면 거기까지 줄인다.
 */
export interface SquareCropInput {
  width: number;
  height: number;
  maxEdge: number;
}

export interface SquareCrop {
  /** 원본에서 잘라낼 사각형의 좌상단 x */
  sx: number;
  /** 원본에서 잘라낼 사각형의 좌상단 y */
  sy: number;
  /** 잘라낼 정사각형의 한 변 (원본 픽셀 기준) */
  size: number;
  /** 출력 canvas 의 한 변 */
  outSize: number;
}

export function computeSquareCrop({
  width,
  height,
  maxEdge,
}: SquareCropInput): SquareCrop {
  const size = Math.min(width, height);
  return {
    sx: Math.floor((width - size) / 2),
    sy: Math.floor((height - size) / 2),
    size,
    outSize: Math.min(size, maxEdge),
  };
}

/** 인증 사진 한 변의 상한. ADR 0001 참고. */
export const CHECKIN_IMAGE_MAX_EDGE = 1440;
const JPEG_QUALITY = 0.9;

/** 재생 중인 <video> 의 현재 프레임을 정사각 jpeg Blob 으로 뽑는다. */
export async function captureSquareJpeg(
  video: HTMLVideoElement,
): Promise<Blob> {
  if (video.videoWidth === 0 || video.videoHeight === 0) {
    throw new Error("아직 카메라 프레임이 준비되지 않았습니다.");
  }

  const { sx, sy, size, outSize } = computeSquareCrop({
    width: video.videoWidth,
    height: video.videoHeight,
    maxEdge: CHECKIN_IMAGE_MAX_EDGE,
  });

  const canvas = document.createElement("canvas");
  canvas.width = outSize;
  canvas.height = outSize;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("canvas 2d context 를 얻지 못했습니다.");
  ctx.drawImage(video, sx, sy, size, size, 0, 0, outSize, outSize);

  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) =>
        blob ? resolve(blob) : reject(new Error("사진 생성에 실패했습니다.")),
      "image/jpeg",
      JPEG_QUALITY,
    );
  });
}
