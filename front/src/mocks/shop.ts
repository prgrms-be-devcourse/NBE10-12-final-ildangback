// 목 데이터. 실 API 가 붙으면 이 폴더는 통째로 지운다.
//
// 기능정의서 SHP-02, SHP-05, CHR-04, CHR-05 의 화면 값이다.
//
// 모자 슬롯은 시안 그림(itemArt.ts)을 쓰고, 나머지 슬롯은 아직 그림이 없어 emoji 로 둔다.
// 슬롯은 백엔드 ItemSlot 을 따른다.

export const SLOTS = ["HEAD", "TOP", "BOTTOM", "SHOES"] as const;
export type Slot = (typeof SLOTS)[number];

export const SLOT_LABEL: Record<Slot, string> = {
  HEAD: "모자",
  TOP: "상의",
  BOTTOM: "하의",
  SHOES: "신발",
};

export const OWNERSHIP_FILTERS = ["ALL", "OWNED", "NOT_OWNED"] as const;
export type OwnershipFilter = (typeof OWNERSHIP_FILTERS)[number];

export const OWNERSHIP_LABEL: Record<OwnershipFilter, string> = {
  ALL: "전체",
  OWNED: "보유 중",
  NOT_OWNED: "미보유",
};

export const SORTS = ["POPULAR", "CHEAP", "EXPENSIVE"] as const;
export type Sort = (typeof SORTS)[number];

export const SORT_LABEL: Record<Sort, string> = {
  POPULAR: "인기순",
  CHEAP: "낮은 가격순",
  EXPENSIVE: "높은 가격순",
};

import type { ShopItem } from "./types";

export const SHOP_ITEMS: ShopItem[] = [
  // 모자
  {
    id: 1,
    slot: "HEAD",
    name: "초록 헤어밴드",
    art: "greenHeadband",
    price: 120,
    popularity: 98,
    owned: true,
  },
  {
    id: 2,
    slot: "HEAD",
    name: "검정 비니",
    art: "blackBeanie",
    price: 150,
    popularity: 92,
    owned: false,
  },
  {
    id: 3,
    slot: "HEAD",
    name: "운동 캡",
    art: "sportCap",
    price: 200,
    popularity: 88,
    owned: false,
  },
  {
    id: 4,
    slot: "HEAD",
    name: "보라 버킷햇",
    art: "purpleBucket",
    price: 250,
    popularity: 81,
    owned: false,
  },
  {
    id: 5,
    slot: "HEAD",
    name: "토끼 모자",
    art: "rabbitHat",
    price: 300,
    popularity: 76,
    owned: false,
  },
  {
    id: 6,
    slot: "HEAD",
    name: "왕관",
    art: "crown",
    price: 500,
    popularity: 70,
    owned: false,
  },
  {
    id: 7,
    slot: "HEAD",
    name: "별 머리띠",
    art: "starBand",
    price: 200,
    popularity: 64,
    owned: true,
  },
  {
    id: 8,
    slot: "HEAD",
    name: "수면 모자",
    art: "sleepCap",
    price: 250,
    popularity: 58,
    owned: false,
  },
  {
    id: 9,
    slot: "HEAD",
    name: "리본",
    art: "ribbon",
    price: 200,
    popularity: 52,
    owned: false,
  },

  // 상의
  {
    id: 10,
    slot: "TOP",
    name: "기본 티셔츠",
    art: "👕",
    price: 100,
    popularity: 95,
    owned: true,
  },
  {
    id: 11,
    slot: "TOP",
    name: "후드 집업",
    art: "🧥",
    price: 280,
    popularity: 89,
    owned: false,
  },
  {
    id: 12,
    slot: "TOP",
    name: "니트 스웨터",
    art: "🧶",
    price: 320,
    popularity: 74,
    owned: false,
  },
  {
    id: 13,
    slot: "TOP",
    name: "정장 재킷",
    art: "🤵",
    price: 480,
    popularity: 61,
    owned: false,
  },
  {
    id: 14,
    slot: "TOP",
    name: "운동 유니폼",
    art: "🎽",
    price: 220,
    popularity: 55,
    owned: false,
  },

  // 하의
  {
    id: 15,
    slot: "BOTTOM",
    name: "기본 반바지",
    art: "🩳",
    price: 100,
    popularity: 93,
    owned: true,
  },
  {
    id: 16,
    slot: "BOTTOM",
    name: "청바지",
    art: "👖",
    price: 240,
    popularity: 87,
    owned: false,
  },
  {
    id: 17,
    slot: "BOTTOM",
    name: "트레이닝 팬츠",
    art: "🏃",
    price: 200,
    popularity: 72,
    owned: false,
  },
  {
    id: 18,
    slot: "BOTTOM",
    name: "체크 스커트",
    art: "🩱",
    price: 300,
    popularity: 60,
    owned: false,
  },

  // 신발
  {
    id: 19,
    slot: "SHOES",
    name: "기본 운동화",
    art: "👟",
    price: 100,
    popularity: 96,
    owned: true,
  },
  {
    id: 20,
    slot: "SHOES",
    name: "러닝화",
    art: "🏃",
    price: 260,
    popularity: 84,
    owned: false,
  },
  {
    id: 21,
    slot: "SHOES",
    name: "구두",
    art: "👞",
    price: 380,
    popularity: 66,
    owned: false,
  },
  {
    id: 22,
    slot: "SHOES",
    name: "장화",
    art: "🥾",
    price: 300,
    popularity: 49,
    owned: false,
  },
];
