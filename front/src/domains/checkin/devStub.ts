import type { GalleryQuery } from "./api";
import type {
  ChallengeMember,
  CheckIn,
  CheckInCursorResponse,
  CheckInResultResponse,
  TodayCheckInStatus,
} from "./types";

/**
 * 임시 스텁 — 백엔드에 checkin 엔드포인트가 아직 없다(feat/13 브랜치 기준).
 *
 * 백엔드 스펙(front/docs/checkin-api-spec.yml)이 실제 구현되어 머지되면:
 *   1. 이 파일을 지운다
 *   2. api.ts 의 `isCheckInStubEnabled()` 분기와 import 를 지운다
 * 그러면 남는 건 `apiFetch` 호출뿐이다.
 */

export function isCheckInStubEnabled(): boolean {
  // dev 서버에서만. `VITE_CHECKIN_STUB=off` 로 끄고 실제 백엔드를 붙일 수도 있다.
  return import.meta.env.DEV && import.meta.env.VITE_CHECKIN_STUB !== "off";
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

export function stubTodayStatus(): TodayCheckInStatus {
  return {
    businessDate: today(),
    isCheckInDay: true,
    currentCount: 0,
    targetCount: 1,
    completed: false,
    allowedTypes: ["PHOTO"],
  };
}

const STUB_MEMBERS: ChallengeMember[] = [
  { userId: 1, nickname: "Ari", todayCheckInCount: 1 },
  { userId: 2, nickname: "Noah", todayCheckInCount: 0 },
  { userId: 3, nickname: "Mia", todayCheckInCount: 1 },
  { userId: 4, nickname: "Leo", todayCheckInCount: 0 },
  { userId: 5, nickname: "Zoe", todayCheckInCount: 2 },
];

export function stubChallengeMembers(): ChallengeMember[] {
  return STUB_MEMBERS;
}

// 갤러리 스텁 데이터 — id 내림차순(최신순). 2026-09 33건 / 2026-08 12건, 멤버 5명,
// 일부만 memo. 9월은 size=20 이면 1페이지 20 + 2페이지 13 이라 무한스크롤이 실제로 돈다.
const STUB_MEMOS: (string | null)[] = [
  "오늘도 완료 💪",
  null,
  "비 와서 실내 러닝 30분",
  null,
  null,
  "PT 마지막 세트까지 꽉 채움",
  null,
  "스트레칭만 겨우 했다",
  null,
  "친구랑 같이 운동",
];

const STUB_GALLERY: CheckIn[] = Array.from({ length: 45 }, (_, i) => {
  const id = 45 - i;
  const inSeptember = id >= 13;
  const day = ((id * 7) % 27) + 1;
  const month = inSeptember ? "09" : "08";
  const businessDate = `2026-${month}-${String(day).padStart(2, "0")}`;
  const member = STUB_MEMBERS[id % STUB_MEMBERS.length];
  return {
    id,
    userId: member.userId,
    nickname: member.nickname,
    businessDate,
    roundNo: 1,
    checkInType: "PHOTO",
    mediaUrl: `https://placehold.co/600x600/8058c4/fff?text=${month}-${String(day).padStart(2, "0")}`,
    mediaType: "IMAGE",
    memo: STUB_MEMOS[id % STUB_MEMOS.length],
    createdAt: `${businessDate}T09:0${id % 6}:00`,
  };
});

export function stubGallery(query: GalleryQuery): CheckInCursorResponse {
  const size = query.size ?? 20;

  let rows = STUB_GALLERY;
  if (query.month) {
    rows = rows.filter((c) => c.businessDate.startsWith(query.month!));
  }
  if (query.userId != null) {
    rows = rows.filter((c) => c.userId === query.userId);
  }

  const startIdx =
    query.cursor == null ? 0 : rows.findIndex((c) => c.id < query.cursor!);
  const page = startIdx < 0 ? [] : rows.slice(startIdx, startIdx + size);
  const last = page.at(-1);
  const hasNext = last != null && rows.some((c) => c.id < last.id);

  return {
    content: page,
    meta: { nextCursor: hasNext ? last!.id : null, hasNext, size },
  };
}

export function stubSubmitResult(input: {
  memo?: string;
}): CheckInResultResponse {
  return {
    checkIn: {
      id: 1,
      userId: 1,
      nickname: "나",
      businessDate: today(),
      roundNo: 1,
      checkInType: "PHOTO",
      mediaUrl: "https://placehold.co/600x600/8058c4/fff?text=CHECK-IN",
      mediaType: "IMAGE",
      memo: input.memo ?? null,
      createdAt: new Date().toISOString(),
    },
    currentCount: 1,
    targetCount: 1,
    dailyCompleted: true,
    earnedUserPoints: 10,
    currentStreak: 12,
    groupCompletedCount: 4,
    groupTotalCount: 5,
  };
}
