import { CaretRightIcon } from "@phosphor-icons/react";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
import { getRecentCheckIns } from "../api";
import type { RecentCheckIn } from "../types";

// 시안은 세 줄이다. 나머지는 갤러리에서 본다.
const LIMIT = 3;

// 서버가 earnedUserPoints 를 아직 null 로 준다. 인증 1건당 지급액은
// application.yml 의 point.check-in-reward 고정값이고 조건 분기가 없어서
// 모든 줄이 같은 값이다. 서버가 채우면 서버 값이 이기므로, 그때 이 상수와
// 아래 ?? 만 지우면 된다.
const CHECK_IN_REWARD = 10;

/** LocalDateTime 문자열에서 "HH:MM" 만 꺼낸다. 타임존이 없는 값이라 그대로 자른다. */
function timeOf(createdAt: string): string {
  return createdAt.slice(11, 16);
}

/**
 * 챌린지 현황의 인증 로그. 커밋 로그처럼 한 줄씩 쌓인다.
 *
 * text 는 서버가 "닉네임_메모" 로 만들어 준다. 메모가 없으면 그룹명이 들어간다.
 */
export function RecentCheckInLog({
  challengeId,
  onSeeAll,
}: {
  challengeId: number;
  onSeeAll(): void;
}) {
  const navigate = useNavigate();
  const [items, setItems] = useState<RecentCheckIn[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    getRecentCheckIns(challengeId, LIMIT)
      .then((rows) => {
        if (!cancelled) setItems(rows);
      })
      .catch(() => {
        // 로그를 못 받아도 현황 화면의 나머지는 보여준다.
        if (!cancelled) setItems([]);
      });

    return () => {
      cancelled = true;
    };
  }, [challengeId]);

  if (items === null) return null;

  return (
    <section className="rounded-2xl border border-purple-200 bg-white p-3.5">
      <div className="flex items-center justify-between">
        <h3 className="text-[13px] font-bold text-gray-900">인증 로그</h3>
        <button
          type="button"
          onClick={onSeeAll}
          className="flex items-center gap-0.5 rounded text-[11px] font-semibold text-purple-600 focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none"
        >
          전체보기
          <CaretRightIcon size={12} weight="bold" aria-hidden />
        </button>
      </div>

      {items.length === 0 ? (
        <p className="py-6 text-center text-[12px] text-gray-500">
          아직 인증 기록이 없어요.
        </p>
      ) : (
        <ul className="mt-2 overflow-hidden rounded-lg border border-purple-200">
          {items.map((item) => (
            <li
              key={item.checkInId}
              className="border-b border-purple-100 last:border-b-0"
            >
              <button
                type="button"
                onClick={() => navigate(`/challenges/${challengeId}/check-in`)}
                className="flex w-full items-center gap-1.5 px-2.5 py-1.5 text-left focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none"
              >
                <span className="shrink-0 text-[10px] font-semibold text-blue-500">
                  feat:
                </span>
                <span className="min-w-0 flex-1 truncate text-[14px] text-gray-900">
                  {item.text}
                </span>
                <span className="shrink-0 text-[10px] font-semibold text-purple-600 tabular-nums">
                  +{item.earnedUserPoints ?? CHECK_IN_REWARD}P
                </span>
                <span className="shrink-0 text-[10px] text-gray-500 tabular-nums">
                  {timeOf(item.createdAt)}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
