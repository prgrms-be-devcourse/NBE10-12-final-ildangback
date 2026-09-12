import { CalendarBlankIcon, CaretDownIcon } from "@phosphor-icons/react";
import { useEffect, useMemo, useRef, useState } from "react";
import { getGrassWeeks } from "../../home/api";
import { getMyStats } from "../api";
import {
  STATS_PERIOD_LABEL,
  STATS_PERIODS,
  type StatsPeriod,
  toDateRange,
  toPersonalStatsData,
} from "../lib/personalStats";
import type { PersonalStatsResponse } from "../../../shared/api/types";
import type { GrassDay } from "../../../shared/ui/ContributionGrid";
import { TopBar } from "../../../shared/ui/TopBar";
import { CategoryTab } from "../components/CategoryTab";
import { MonthlyTab } from "../components/MonthlyTab";
import { DEEP, MUTED } from "../components/statsTokens";
import { SummaryTab } from "../components/SummaryTab";

// 통계 잔디는 1년치다. 서버가 366일까지 받는다.
const GRASS_WEEKS = 52;

const TABS = [
  { key: "SUMMARY", label: "요약" },
  { key: "MONTHLY", label: "월별" },
  { key: "CATEGORY", label: "카테고리" },
] as const;

type TabKey = (typeof TABS)[number]["key"];

// 탭을 바꾸면 그 탭에 어울리는 기간으로 맞춰 준다. 바꾸는 것은 막지 않는다.
const DEFAULT_PERIOD: Record<TabKey, StatsPeriod> = {
  SUMMARY: "ALL",
  MONTHLY: "SIX_MONTHS",
  CATEGORY: "ALL",
};

export function PersonalStatsPage() {
  const [tab, setTab] = useState<TabKey>("SUMMARY");
  const [period, setPeriod] = useState<StatsPeriod>("ALL");

  const [summary, setSummary] = useState<PersonalStatsResponse | null>(null);
  const [grass, setGrass] = useState<GrassDay[] | null>(null);
  const [pending, setPending] = useState(true);
  const [failed, setFailed] = useState(false);

  // 잔디는 기간 선택과 무관하게 1년 고정이라 처음 한 번만 받는다.
  useEffect(() => {
    let cancelled = false;
    getGrassWeeks(GRASS_WEEKS)
      .then((days) => {
        if (!cancelled) setGrass(days);
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  // 통계는 기간이 바뀌면 다시 받는다.
  useEffect(() => {
    let cancelled = false;

    async function run() {
      setPending(true);
      setFailed(false);
      try {
        const response = await getMyStats(toDateRange(period));
        if (!cancelled) setSummary(response);
      } catch {
        if (!cancelled) setFailed(true);
      } finally {
        if (!cancelled) setPending(false);
      }
    }

    run();
    return () => {
      cancelled = true;
    };
  }, [period]);

  const stats = useMemo(
    () => (summary && grass ? toPersonalStatsData(summary, grass) : null),
    [summary, grass],
  );

  // 둘 중 하나라도 아직이면 로딩이다.
  const loading = !failed && (pending || stats === null);

  return (
    <>
      <TopBar title="개인 통계" />

      <div className="px-[22px] pb-10">
        <div
          role="tablist"
          aria-label="개인 통계 보기 방식"
          className="mt-[17px] flex h-[34px] items-center rounded-[7.5px] border border-purple-200 bg-[#FEFEFE] p-[2.5px]"
        >
          {TABS.map(({ key, label }) => (
            <button
              key={key}
              type="button"
              role="tab"
              aria-selected={tab === key}
              onClick={() => {
                setTab(key);
                setPeriod(DEFAULT_PERIOD[key]);
              }}
              className="h-[29px] flex-1 rounded-[6px] text-[12px] font-semibold"
              style={
                tab === key
                  ? { backgroundColor: DEEP, color: "#fff" }
                  : { color: MUTED }
              }
            >
              {label}
            </button>
          ))}
        </div>

        <div className="mt-[16px]">
          <PeriodSelect value={period} onChange={setPeriod} />
        </div>

        <div className="mt-[15px]">
          {loading && (
            <p className="py-20 text-center text-[13px] text-gray-500">
              불러오는 중…
            </p>
          )}
          {!loading && (failed || !stats) && (
            <p className="py-20 text-center text-[13px] text-gray-500">
              통계를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
            </p>
          )}
          {!loading && !failed && stats && stats.totalCheckIns === 0 && (
            <p className="py-20 text-center text-[13px] text-gray-500">
              아직 쌓인 기록이 없어요. 오늘 첫 인증을 해보세요.
            </p>
          )}
          {!loading && !failed && stats && stats.totalCheckIns > 0 && (
            <>
              {tab === "SUMMARY" && <SummaryTab stats={stats} />}
              {tab === "MONTHLY" && <MonthlyTab stats={stats} />}
              {tab === "CATEGORY" && <CategoryTab stats={stats} />}
            </>
          )}
        </div>
      </div>
    </>
  );
}

function PeriodSelect({
  value,
  onChange,
}: {
  value: StatsPeriod;
  onChange(value: StatsPeriod): void;
}) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: PointerEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, [open]);

  return (
    <div ref={rootRef} className="relative inline-block">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex h-[31px] w-[121px] items-center gap-[6px] rounded-[6.65px] border-[0.7px] border-purple-200 bg-[#FEFEFE] pl-[11px] text-[11px] text-gray-900"
      >
        <CalendarBlankIcon size={15} style={{ color: DEEP }} aria-hidden />
        <span className="flex-1 text-left">{STATS_PERIOD_LABEL[value]}</span>
        <CaretDownIcon
          size={12}
          className={`mr-[9px] text-gray-500 ${open ? "rotate-180" : ""}`}
          aria-hidden
        />
      </button>

      {open && (
        <ul className="absolute top-full left-0 z-10 mt-1 w-full overflow-hidden rounded-[6.65px] border-[0.7px] border-purple-200 bg-white py-1 shadow-[0_8px_20px_rgba(0,0,0,0.08)]">
          {STATS_PERIODS.map((key) => (
            <li key={key}>
              <button
                type="button"
                onClick={() => {
                  onChange(key);
                  setOpen(false);
                }}
                className="w-full px-[11px] py-2 text-left text-[11px] whitespace-nowrap"
                style={{ color: key === value ? DEEP : undefined }}
              >
                {STATS_PERIOD_LABEL[key]}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
