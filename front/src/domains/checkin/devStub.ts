import type { DailyLogQuery, GalleryQuery, MyCheckInQuery } from "./api";
import type {
  ChallengeAlbumSummary,
  ChallengeMember,
  CheckIn,
  CheckInCursorResponse,
  CheckInResultResponse,
  CursorPageMeta,
  DailyLog,
  DailyLogCursorResponse,
  MyChallengeSummary,
  MyCheckIn,
  MyCheckInCursorResponse,
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

function pad(n: number): string {
  return String(n).padStart(2, "0");
}

/**
 * 최신 날짜부터 하루에 `perDay(id)` 건씩, `count` 건이 될 때까지 `{id, businessDate}` 를
 * 만든다. id 는 큰 게 최신이라 커서(id 내림차순)·날짜 그룹핑이 둘 다 자연스럽다.
 */
function datedIds(
  start: [number, number, number],
  count: number,
  perDay: (id: number) => number,
): { id: number; businessDate: string }[] {
  const out: { id: number; businessDate: string }[] = [];
  const d = new Date(start[0], start[1] - 1, start[2], 12);
  let id = count;
  while (out.length < count) {
    const ymd = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    const n = Math.max(1, perDay(id));
    for (let k = 0; k < n && out.length < count; k++) {
      out.push({ id: id--, businessDate: ymd });
    }
    d.setDate(d.getDate() - 1);
  }
  return out;
}

/** 실제 사진처럼 보이게 dev 에서만 쓰는 임의 이미지. */
function stubPhoto(seed: string | number): string {
  return `https://picsum.photos/seed/gommit-${seed}/500`;
}

// 갤러리 스텁 — 최신순, 2026-09 ~ 2026-08 걸침(9월 > 20건이라 무한스크롤이 실제로 돈다),
// 멤버 5명, 일부만 memo.
const STUB_GALLERY: CheckIn[] = datedIds(
  [2026, 9, 25],
  50,
  (id) => (id % 2) + 1,
).map(({ id, businessDate }) => {
  const member = STUB_MEMBERS[id % STUB_MEMBERS.length];
  return {
    id,
    userId: member.userId,
    nickname: member.nickname,
    businessDate,
    roundNo: 1,
    checkInType: "PHOTO" as const,
    mediaUrl: stubPhoto(id),
    mediaType: "IMAGE" as const,
    memo: STUB_MEMOS[id % STUB_MEMOS.length],
    createdAt: `${businessDate}T${pad(9 + (id % 8))}:${pad((id * 7) % 60)}:00`,
  };
});

/** id 내림차순으로 정렬된 rows 를 커서로 자른다. */
function stubCursorPage<T extends { id: number }>(
  rows: T[],
  cursor: number | undefined,
  size: number,
): { content: T[]; meta: CursorPageMeta } {
  const startIdx = cursor == null ? 0 : rows.findIndex((r) => r.id < cursor);
  const content = startIdx < 0 ? [] : rows.slice(startIdx, startIdx + size);
  const last = content.at(-1);
  const hasNext = last != null && rows.some((r) => r.id < last.id);
  return {
    content,
    meta: { nextCursor: hasNext ? last!.id : null, hasNext, size },
  };
}

export function stubGallery(query: GalleryQuery): CheckInCursorResponse {
  let rows = STUB_GALLERY;
  if (query.month) {
    rows = rows.filter((c) => c.businessDate.startsWith(query.month!));
  }
  if (query.userId != null) {
    rows = rows.filter((c) => c.userId === query.userId);
  }
  return stubCursorPage(rows, query.cursor, query.size ?? 20);
}

// ── 프로필 모아보기 스텁 ────────────────────────────────────────────────────

const STUB_MY_CHALLENGES: MyChallengeSummary[] = [
  { challengeId: 1, name: "오운완" },
  { challengeId: 2, name: "매일 독서 30분" },
  { challengeId: 3, name: "아침 6시 기상" },
];

export function stubMyChallenges(): MyChallengeSummary[] {
  return STUB_MY_CHALLENGES;
}

const STUB_ALBUM_CATEGORIES = ["운동", "독서", "생활습관"];

export function stubChallengeAlbumSummary(
  challengeId: number,
): ChallengeAlbumSummary {
  const found = STUB_MY_CHALLENGES.find((c) => c.challengeId === challengeId);
  return {
    challengeId,
    name: found?.name ?? "오운완",
    category: STUB_ALBUM_CATEGORIES[(challengeId - 1) % 3],
    active: true,
    startDate: "2026-08-20",
    endDate: "2027-02-15",
  };
}

// 내 인증 60건 — 최신순, 2026-09 ~ 2026-08 걸침, 하루 1~3건, 챌린지 3개 분산, 일부 memo.
const STUB_MY_CHECKINS: MyCheckIn[] = datedIds(
  [2026, 9, 26],
  60,
  (id) => (id % 3) + 1,
).map(({ id, businessDate }) => ({
  id,
  userId: 1,
  nickname: "나",
  businessDate,
  roundNo: 1,
  checkInType: "PHOTO" as const,
  mediaUrl: stubPhoto(`me-${id}`),
  mediaType: "IMAGE" as const,
  memo: id % 4 === 0 ? STUB_MEMOS[id % STUB_MEMOS.length] : null,
  createdAt: `${businessDate}T${pad(8 + (id % 10))}:${pad((id * 7) % 60)}:00`,
  challengeId: (id % 3) + 1,
}));

export function stubMyCheckIns(query: MyCheckInQuery): MyCheckInCursorResponse {
  let rows = STUB_MY_CHECKINS;
  if (query.challengeId != null) {
    rows = rows.filter((c) => c.challengeId === query.challengeId);
  }
  if (query.checkInType) {
    rows = rows.filter((c) => c.checkInType === query.checkInType);
  }
  // totalCount 는 month 를 무시한다(헤더 수치 고정).
  const totalCount = rows.length;
  if (query.month) {
    rows = rows.filter((c) => c.businessDate.startsWith(query.month!));
  }
  const { content, meta } = stubCursorPage(
    rows,
    query.cursor,
    query.size ?? 20,
  );
  return { content, meta: { ...meta, totalCount } };
}

// ── 일일 로그 스텁 ──────────────────────────────────────────────────────────

// 하루 1건, 2026-09 22건 / 2026-08 20건. videoUrl 은 항상 null(영상 생성 전).
// totalCount(참여 인원)를 1~6 으로 돌려서 placeholder 타일 배치를 눈으로 확인 가능.
const STUB_DAILY_LOGS: DailyLog[] = Array.from({ length: 42 }, (_, i) => {
  const id = 42 - i;
  const inSeptember = id >= 21;
  const day = inSeptember ? id - 20 : id;
  const month = inSeptember ? "09" : "08";
  const totalCount = (id % 6) + 1;
  // 7일마다 한 번은 아무도 인증 안 한 날(completedCount 0) — 빈 문구 확인용.
  const completedCount = id % 7 === 0 ? 0 : Math.max(1, totalCount - (id % 3));
  return {
    id,
    businessDate: `2026-${month}-${String(day).padStart(2, "0")}`,
    videoUrl: null,
    completedCount,
    totalCount,
  };
});

export function stubDailyLogs(query: DailyLogQuery): DailyLogCursorResponse {
  const monthRows = query.month
    ? STUB_DAILY_LOGS.filter((d) => d.businessDate.startsWith(query.month!))
    : STUB_DAILY_LOGS;
  const { content, meta } = stubCursorPage(
    monthRows,
    query.cursor,
    query.size ?? 20,
  );
  const recordDays = monthRows.length;
  const avgRate =
    recordDays === 0
      ? 0
      : Math.round(
          monthRows.reduce(
            (sum, d) => sum + (d.completedCount / d.totalCount) * 100,
            0,
          ) / recordDays,
        );
  return { content, meta: { ...meta, recordDays, avgRate } };
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
