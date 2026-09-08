import { CaretDownIcon, CaretRightIcon } from "@phosphor-icons/react";
import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { getMyBalance, getMyHistories } from "../api";
import { dateKey, formatMonthDay, formatTime } from "../../../shared/lib/date";
import { PixelIcon } from "../../../shared/ui/PixelIcon";
import { pixelIcons } from "../../../shared/ui/pixelIcons";
import { TopBar } from "../../../shared/ui/TopBar";
import {
  CHANGE_TYPE_LABEL,
  PERIOD_LABEL,
  REASON_ICON,
  reasonIconTone,
  REASON_TITLE,
} from "../lib/reason";
import type {
  PeriodFilter,
  PointBalanceResponse,
  PointChangeType,
  UserPointHistoryResponse,
} from "../../../shared/api/types";

const PAGE_SIZE = 20;

export function PointHistoryPage() {
  const navigate = useNavigate();

  const [balance, setBalance] = useState<PointBalanceResponse | null>(null);
  const [period, setPeriod] = useState<PeriodFilter>("THIS_MONTH");
  const [type, setType] = useState<PointChangeType>("ALL");
  // 직접설정(period === "CUSTOM")일 때만 쓰는 날짜 범위.
  const [customFrom, setCustomFrom] = useState("");
  const [customTo, setCustomTo] = useState("");

  const [items, setItems] = useState<UserPointHistoryResponse[]>([]);
  const [cursor, setCursor] = useState<number | null>(null);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState(false);

  const sentinelRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    getMyBalance()
      .then(setBalance)
      .catch(() => setBalance(null));
  }, []);

  // 필터가 바뀌면 처음부터 다시 불러온다.
  useEffect(() => {
    let cancelled = false;

    // 직접설정인데 아직 날짜를 다 안 골랐으면 요청을 보내지 않는다.
    if (period === "CUSTOM" && (!customFrom || !customTo)) return;

    async function run() {
      setLoading(true);
      setError(false);
      try {
        const page = await getMyHistories({
          period,
          type,
          from: period === "CUSTOM" ? customFrom : undefined,
          to: period === "CUSTOM" ? customTo : undefined,
          size: PAGE_SIZE,
        });
        if (cancelled) return;
        setItems(page.content);
        setCursor(page.nextCursor);
        setHasNext(page.hasNext);
      } catch {
        if (!cancelled) setError(true);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    run();
    return () => {
      cancelled = true;
    };
  }, [period, type, customFrom, customTo]);

  const loadMore = useCallback(async () => {
    if (loadingMore || cursor == null) return;
    setLoadingMore(true);
    try {
      const page = await getMyHistories({
        period,
        type,
        from: period === "CUSTOM" ? customFrom : undefined,
        to: period === "CUSTOM" ? customTo : undefined,
        cursor,
        size: PAGE_SIZE,
      });
      setItems((prev) => [...prev, ...page.content]);
      setCursor(page.nextCursor);
      setHasNext(page.hasNext);
    } catch {
      // 다음 페이지 실패는 조용히 넘어간다 — 이미 보이는 목록은 그대로 유지한다.
    } finally {
      setLoadingMore(false);
    }
  }, [cursor, loadingMore, period, type, customFrom, customTo]);

  // 스크롤이 바닥 근처에 닿으면 다음 페이지를 이어붙인다.
  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!sentinel || !hasNext || loading) return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) {
          loadMore();
        }
      },
      { rootMargin: "200px" },
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [hasNext, loading, loadMore]);

  const groups = groupByDate(items);

  return (
    <>
      <TopBar title="포인트 내역" />

      <div className="px-5 pt-3 pb-10">
        <section
          className="rounded-[7px] px-6 py-5"
          style={{
            background:
              "linear-gradient(154.26deg, #F6F1FC 23.84%, #FFFFFF 77.65%)",
            boxShadow:
              "5px 15px 10px rgba(0,0,0,0.01), 2px 7px 7px rgba(0,0,0,0.02), 1px 2px 4px rgba(0,0,0,0.03)",
          }}
        >
          <div className="flex items-center gap-4">
            <PixelIcon src={pixelIcons.pointHistory} size={68} />
            <div>
              <p className="text-[13px] leading-none font-semibold text-[#535353]">
                사용 가능한 포인트
              </p>
              <p className="mt-1.5 text-[34px] leading-none font-semibold text-[#784CBE]">
                {(balance?.balance ?? 0).toLocaleString()}
                <span className="text-[19px]">P</span>
              </p>
            </div>
          </div>

          <div className="mt-5 grid grid-cols-3 gap-2 border-t border-[#D9CDED] pt-4 text-center">
            <Stat
              label="이번 달 적립"
              value={balance?.monthlyEarned ?? 0}
              highlight
            />
            <Stat label="이번달 사용" value={balance?.monthlySpent ?? 0} />
            <Stat label="누적 적립" value={balance?.totalEarned ?? 0} />
          </div>
        </section>

        <div className="mt-4 flex divide-x divide-[#D9CDED] rounded-[7px] border-[0.7px] border-[#D9CDED] bg-[#FEFEFE]">
          <PeriodSelect
            period={period}
            customFrom={customFrom}
            customTo={customTo}
            onApply={(nextPeriod, from, to) => {
              setPeriod(nextPeriod);
              setCustomFrom(from);
              setCustomTo(to);
            }}
          />
          <FilterSelect
            label="내역 구분"
            value={type}
            onChange={(v) => setType(v as PointChangeType)}
            options={CHANGE_TYPE_LABEL}
          />
        </div>

        <div className="mt-4">
          {loading && (
            <p className="py-10 text-center text-[13px] text-gray-500">
              불러오는 중…
            </p>
          )}

          {!loading && error && (
            <p className="py-10 text-center text-[13px] text-gray-500">
              내역을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
            </p>
          )}

          {!loading && !error && groups.length === 0 && (
            <p className="py-10 text-center text-[13px] text-gray-500">
              아직 포인트 내역이 없어요.
            </p>
          )}

          {!loading &&
            !error &&
            groups.map((group) => (
              <div
                key={group.key}
                className="mt-6 overflow-hidden rounded-2xl border border-purple-200 first:mt-0"
              >
                <p className="border-b border-purple-100 bg-purple-50/60 px-4 py-2.5 text-[14px] font-semibold text-gray-500">
                  {formatMonthDay(group.items[0].createdAt)}
                </p>
                {group.items.map((item) => (
                  <HistoryRow
                    key={item.id}
                    item={item}
                    onClick={() => navigate(`/profile/points/${item.id}`)}
                  />
                ))}
              </div>
            ))}

          <div ref={sentinelRef} />

          {loadingMore && (
            <p className="py-4 text-center text-[12px] text-gray-400">
              더 불러오는 중…
            </p>
          )}
        </div>
      </div>
    </>
  );
}

function Stat({
  label,
  value,
  highlight = false,
}: {
  label: string;
  value: number;
  highlight?: boolean;
}) {
  return (
    <div className="px-2">
      <p className="text-[13px] font-medium text-[#535353]">{label}</p>
      <p
        className={`mt-1.5 text-[20px] font-semibold ${highlight ? "text-[#784CBE]" : "text-[#292929]"}`}
      >
        {highlight && "+"}
        {value.toLocaleString()}
        <span className="text-[14px]">P</span>
      </p>
    </div>
  );
}

function FilterSelect<T extends string>({
  label,
  value,
  onChange,
  options,
}: {
  label: string;
  value: T;
  onChange: (value: T) => void;
  options: Record<T, string>;
}) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement | null>(null);

  // 바깥을 누르면 닫는다.
  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: PointerEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, [open]);

  return (
    <div ref={rootRef} className="relative flex-1">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex w-full flex-col items-start px-4 py-3"
      >
        <span className="text-[11px] font-medium text-[#535353]">{label}</span>
        <span className="mt-1 flex w-full items-center justify-between gap-1">
          <span className="text-[14px] font-semibold text-[#4A4A4A]">
            {options[value]}
          </span>
          <CaretDownIcon
            size={18}
            weight="light"
            className={`shrink-0 text-[#4A4A4A] transition-transform ${open ? "rotate-180" : ""}`}
            aria-hidden
          />
        </span>
      </button>

      {open && (
        <ul className="absolute top-full left-0 z-10 mt-1.5 w-max min-w-full overflow-hidden rounded-[10px] border border-[#D9CDED] bg-white py-1 shadow-[0_8px_20px_rgba(0,0,0,0.08)]">
          {Object.entries(options).map(([key, text]) => (
            <li key={key}>
              <button
                type="button"
                onClick={() => {
                  onChange(key as T);
                  setOpen(false);
                }}
                className={`w-full px-4 py-2.5 text-left text-[14px] whitespace-nowrap ${
                  key === value
                    ? "font-semibold text-[#784CBE]"
                    : "text-[#4A4A4A] hover:bg-purple-50"
                }`}
              >
                {text as string}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

const PERIOD_KEYS = ["THIS_MONTH", "LAST_MONTH", "ALL", "CUSTOM"] as const;

// "조회 기간" 전용 드롭다운. 목록은 FilterSelect와 같지만, "직접설정"을 고르면
// 닫히는 대신 같은 자리에서 날짜 두 개를 입력하는 화면으로 바뀐다.
function PeriodSelect({
  period,
  customFrom,
  customTo,
  onApply,
}: {
  period: PeriodFilter;
  customFrom: string;
  customTo: string;
  onApply: (period: PeriodFilter, from: string, to: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const [showCustom, setShowCustom] = useState(false);
  const [draftFrom, setDraftFrom] = useState(customFrom);
  const [draftTo, setDraftTo] = useState(customTo);
  const rootRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: PointerEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) {
        setOpen(false);
        setShowCustom(false);
      }
    };
    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, [open]);

  const displayLabel =
    period === "CUSTOM" && customFrom && customTo
      ? `${customFrom.slice(5)} ~ ${customTo.slice(5)}`
      : PERIOD_LABEL[period];

  return (
    <div ref={rootRef} className="relative flex-1">
      <button
        type="button"
        onClick={() => {
          setDraftFrom(customFrom);
          setDraftTo(customTo);
          setShowCustom(false);
          setOpen((v) => !v);
        }}
        className="flex w-full flex-col items-start px-4 py-3"
      >
        <span className="text-[11px] font-medium text-[#535353]">
          조회 기간
        </span>
        <span className="mt-1 flex w-full items-center justify-between gap-1">
          <span className="truncate text-[14px] font-semibold text-[#4A4A4A]">
            {displayLabel}
          </span>
          <CaretDownIcon
            size={18}
            weight="light"
            className={`shrink-0 text-[#4A4A4A] transition-transform ${open ? "rotate-180" : ""}`}
            aria-hidden
          />
        </span>
      </button>

      {open && !showCustom && (
        <ul className="absolute top-full left-0 z-10 mt-1.5 w-max min-w-full overflow-hidden rounded-[10px] border border-[#D9CDED] bg-white py-1 shadow-[0_8px_20px_rgba(0,0,0,0.08)]">
          {PERIOD_KEYS.map((key) => (
            <li key={key}>
              <button
                type="button"
                onClick={() => {
                  if (key === "CUSTOM") {
                    setShowCustom(true);
                    return;
                  }
                  onApply(key, "", "");
                  setOpen(false);
                }}
                className={`w-full px-4 py-2.5 text-left text-[14px] whitespace-nowrap ${
                  key === period
                    ? "font-semibold text-[#784CBE]"
                    : "text-[#4A4A4A] hover:bg-purple-50"
                }`}
              >
                {PERIOD_LABEL[key]}
              </button>
            </li>
          ))}
        </ul>
      )}

      {open && showCustom && (
        <div className="absolute top-full left-0 z-10 mt-1.5 w-56 rounded-[10px] border border-[#D9CDED] bg-white p-3 shadow-[0_8px_20px_rgba(0,0,0,0.08)]">
          <label className="block text-[11px] text-[#535353]">
            시작일
            <input
              type="date"
              value={draftFrom}
              max={draftTo || undefined}
              onChange={(e) => setDraftFrom(e.target.value)}
              className="mt-1 block w-full rounded-lg border border-[#D9CDED] px-2 py-1.5 text-[13px] text-[#4A4A4A]"
            />
          </label>
          <label className="mt-2 block text-[11px] text-[#535353]">
            종료일
            <input
              type="date"
              value={draftTo}
              min={draftFrom || undefined}
              onChange={(e) => setDraftTo(e.target.value)}
              className="mt-1 block w-full rounded-lg border border-[#D9CDED] px-2 py-1.5 text-[13px] text-[#4A4A4A]"
            />
          </label>
          <button
            type="button"
            disabled={!draftFrom || !draftTo}
            onClick={() => {
              onApply("CUSTOM", draftFrom, draftTo);
              setOpen(false);
              setShowCustom(false);
            }}
            className="mt-3 w-full rounded-lg bg-[#784CBE] py-2 text-[13px] font-semibold text-white disabled:opacity-40"
          >
            적용
          </button>
        </div>
      )}
    </div>
  );
}

function HistoryRow({
  item,
  onClick,
}: {
  item: UserPointHistoryResponse;
  onClick(): void;
}) {
  const isEarn = item.amount > 0;
  const Icon = REASON_ICON[item.reason];
  const tone = reasonIconTone(item.amount);

  return (
    <button
      type="button"
      onClick={onClick}
      className="flex w-full items-center gap-3.5 border-b border-purple-100 px-4 py-4 text-left transition-colors last:border-b-0 hover:bg-purple-50/50"
    >
      <span
        className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full ${tone.bg} ${tone.fg}`}
      >
        <Icon size={20} weight="bold" />
      </span>

      <span className="min-w-0 flex-1">
        <span className="block truncate text-[14px] font-semibold text-[#4A4A4A]">
          {REASON_TITLE[item.reason]}
        </span>
        <span className="block text-[12px] text-gray-500">
          {item.sourceName} · {formatTime(item.createdAt)}
        </span>
      </span>

      <span className="shrink-0 text-right">
        <span
          className={`block text-[15px] font-semibold ${isEarn ? "text-purple-600" : "text-[#4A4A4A]"}`}
        >
          {isEarn ? "+" : ""}
          {item.amount.toLocaleString()}P
        </span>
        <span className="block text-[11px] text-gray-400">
          잔액 {item.balanceAfter.toLocaleString()}P
        </span>
      </span>

      <CaretRightIcon
        size={16}
        className="shrink-0 text-gray-300"
        aria-hidden
      />
    </button>
  );
}

function groupByDate(items: UserPointHistoryResponse[]) {
  const groups: { key: string; items: UserPointHistoryResponse[] }[] = [];
  for (const item of items) {
    const key = dateKey(item.createdAt);
    const last = groups[groups.length - 1];
    if (last?.key === key) {
      last.items.push(item);
    } else {
      groups.push({ key, items: [item] });
    }
  }
  return groups;
}
