/**
 * 화면이 데이터를 얻는 자리. **여기가 교체 지점이다.**
 *
 * 실 API 가 나오면 이 파일 대신 `domains/record/api.ts`, `domains/item/api.ts`
 * 를 만들고 화면의 import 만 바꾸면 된다. 함수 이름과 반환 타입을 그대로 두면
 * 화면 코드는 손댈 필요가 없다. 여기 엔드포인트 경로를 적지 않은 이유는
 * 아직 팀이 정하지 않았기 때문이다 (`types.ts` 머리말 참고).
 *
 * 개발 중 상태 확인:
 *   ?mock=loading  계속 로딩
 *   ?mock=error    실패 화면
 *   ?mock=empty    빈 화면
 */
import { buildCheckInDays, daysAgoKey } from "./grass";
import type { Slot } from "./shop";
import { SHOP_ITEMS } from "./shop";
import { CATEGORY_STATS, MONTHLY, MONTHLY_HIGHLIGHT, SUMMARY } from "./stats";
import type {
  HomeData,
  PersonalStatsData,
  PurchaseResult,
  ShopData,
} from "./types";

const LATENCY = 350;

// 4개월.
const HOME_GRASS_WEEKS = 18;
const STATS_GRASS_WEEKS = 52;

function mockMode(): string | null {
  if (typeof window === "undefined") return null;
  return new URLSearchParams(window.location.search).get("mock");
}

/** 실제 왕복처럼 지연을 준다. 즉시 resolve 하면 로딩 화면을 볼 수 없다. */
function respond<T>(value: T): Promise<T> {
  const mode = mockMode();
  if (mode === "loading") return new Promise<T>(() => {});
  if (mode === "error") {
    return new Promise((_, reject) =>
      setTimeout(() => reject(new Error("mock error")), LATENCY),
    );
  }
  return new Promise((resolve) => setTimeout(() => resolve(value), LATENCY));
}

const EMPTY_HOME: HomeData = {
  point: 0,
  streakDays: 0,
  monthlyCheckIns: 0,
  monthlySuccessRate: 0,
  todayChallenges: [],
  grass: buildCheckInDays(HOME_GRASS_WEEKS).map((d) => ({ ...d, count: 0 })),
  recentActivities: [],
};

export function fetchHome(): Promise<HomeData> {
  if (mockMode() === "empty") return respond(EMPTY_HOME);

  return respond({
    point: 1240,
    streakDays: 12,
    monthlyCheckIns: 18,
    monthlySuccessRate: 82,
    todayChallenges: [
      { id: 1, name: "오운완", category: "EXERCISE", done: 1, goal: 3 },
      { id: 2, name: "알고리즘 매일 풀기", category: "DEV", done: 2, goal: 2 },
      { id: 3, name: "하루 30분 독서", category: "READING", done: 0, goal: 1 },
    ],
    grass: buildCheckInDays(HOME_GRASS_WEEKS, 12),
    recentActivities: [
      {
        id: 1,
        prefix: "feat",
        title: "알고리즘 1문제 풀이",
        date: daysAgoKey(0),
        point: 40,
      },
      {
        id: 2,
        prefix: "docs",
        title: "독서 30분 기록",
        date: daysAgoKey(1),
        point: 40,
      },
      {
        id: 3,
        prefix: "workout",
        title: "오운완 인증",
        date: daysAgoKey(2),
        point: 40,
      },
    ],
  } satisfies HomeData);
}

const EMPTY_STATS: PersonalStatsData = {
  totalCheckIns: 0,
  bestStreak: 0,
  successRate: 0,
  succeeded: 0,
  missed: 0,
  completedChallenges: 0,
  abandonedChallenges: 0,
  grass: buildCheckInDays(STATS_GRASS_WEEKS).map((d) => ({ ...d, count: 0 })),
  monthly: [],
  bestMonth: 0,
  bestMonthCheckIns: 0,
  worstMonth: 0,
  worstMonthMissed: 0,
  categories: [],
};

/**
 * 기간 선택이 바뀌면 다시 부른다. 목은 기간을 무시하지만 호출은 실제로 다시 나가서,
 * 실 API 로 바꿔도 화면 동작이 그대로다.
 */
export function fetchPersonalStats(period: string): Promise<PersonalStatsData> {
  void period;
  if (mockMode() === "empty") return respond(EMPTY_STATS);

  return respond({
    ...SUMMARY,
    grass: buildCheckInDays(STATS_GRASS_WEEKS, 12),
    monthly: MONTHLY,
    ...MONTHLY_HIGHLIGHT,
    categories: CATEGORY_STATS,
  } satisfies PersonalStatsData);
}

// 상점은 사는 순간 상태가 바뀐다. 화면을 오갔다 와도 산 게 남아 있어야
// 진짜처럼 눌러볼 수 있어서, 이 모듈이 세션 동안 상태를 들고 있는다.
// 새로고침하면 초기화된다.
const shopState = {
  point: 1240,
  owned: new Set(SHOP_ITEMS.filter((i) => i.owned).map((i) => i.id)),
  equipped: { HEAD: 1, TOP: 10, BOTTOM: 15, SHOES: 19 } as ShopData["equipped"],
};

function currentShop(): ShopData {
  return {
    point: shopState.point,
    items: SHOP_ITEMS.map((item) => ({
      ...item,
      owned: shopState.owned.has(item.id),
    })),
    equipped: { ...shopState.equipped },
  };
}

/**
 * 카탈로그를 통째로 준다. 부위 · 보유 · 정렬은 화면에서 거른다.
 * 서버가 페이징이나 서버 정렬을 택하면 이 함수의 인자와 반환이 바뀐다.
 */
export function fetchShop(): Promise<ShopData> {
  if (mockMode() === "empty") {
    return respond({ point: 0, items: [], equipped: {} });
  }
  return respond(currentShop());
}

/**
 * SHP-03 · SHP-04. 실패는 `Error` 로 던지고 화면은 `message` 를 그대로 띄운다 —
 * 실 API 의 `ApiError` 도 서버 문구를 그대로 쓰는 구조라 바꿔 끼워도 같다.
 */
export function purchaseItem(itemId: number): Promise<PurchaseResult> {
  const item = SHOP_ITEMS.find((i) => i.id === itemId);

  return new Promise((resolve, reject) =>
    setTimeout(() => {
      if (!item) return reject(new Error("없는 아이템이에요."));
      if (shopState.owned.has(itemId)) {
        return reject(new Error("이미 가지고 있는 아이템이에요."));
      }
      if (shopState.point < item.price) {
        return reject(new Error("포인트가 부족해요."));
      }
      // 차감과 지급은 함께 일어난다 (SHP-04).
      shopState.point -= item.price;
      shopState.owned.add(itemId);
      resolve({ item: { ...item, owned: true }, point: shopState.point });
    }, LATENCY),
  );
}

/** CHR-05 · CHR-06. itemId 가 null 이면 그 부위를 벗는다. */
export function equipItem(
  slot: Slot,
  itemId: number | null,
): Promise<ShopData["equipped"]> {
  return new Promise((resolve, reject) =>
    setTimeout(() => {
      if (itemId !== null && !shopState.owned.has(itemId)) {
        return reject(new Error("아직 가지고 있지 않은 아이템이에요."));
      }
      if (itemId === null) delete shopState.equipped[slot];
      else shopState.equipped[slot] = itemId;
      resolve({ ...shopState.equipped });
    }, LATENCY),
  );
}
