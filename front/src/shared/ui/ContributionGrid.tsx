import { useEffect, useRef, useState } from "react";
import { formatDate } from "../lib/date";

// 꼬밋 잔디. 홈과 개인 통계가 같이 쓴다.
//
// 깃허브 컨트리뷰션 그래프의 인상을 따른다. 그 인상은 셋에서 나온다 —
// 빽빽하고, 명암이 5단계고, 대부분이 빈칸이다.
//
// 칸 크기를 px 로 박지 않는다. 박으면 제일 좁은 기기에 맞춘 크기가 넓은
// 화면에서도 그대로라 오른쪽이 남는다. 열을 1fr 로 두고 칸을 정사각으로
// 만들면 폭을 항상 꽉 채우고, 화면이 넓을수록 칸도 같이 커진다.

/**
 * 서버가 주는 최소 단위. 오늘인지 미래인지는 이 컴포넌트가 판단한다 —
 * "오늘"은 보는 사람의 시계에 달린 값이라 응답에 담을 것이 아니다.
 */
export interface GrassDay {
  /** YYYY-MM-DD. 타임존이 없는 값이라 문자열 그대로 비교한다. */
  date: string;
  count: number;
}

function todayKey(): string {
  const now = new Date();
  const month = `${now.getMonth() + 1}`.padStart(2, "0");
  const day = `${now.getDate()}`.padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
}

// 월 라벨 글자 높이 + 아래 여백
const MONTH_ROW = 20;

// 빈칸 + 4단계. 시안의 #AC89E6 과 #794CC7 을 가운데에 두고 위아래를 늘렸다.
const LEVEL_BG = ["#EDEAF4", "#D9CDED", "#AC89E6", "#794CC7", "#5A3E89"];
const DEEP = "#794CC7";
// 칸 크기가 화면 폭 따라 달라지므로 반지름도 비율로 준다.
const RADIUS = "30%";

function level(count: number): number {
  return Math.min(count, LEVEL_BG.length - 1);
}

// 깃허브처럼 건너뛴 줄에만 붙인다. 7줄 전부 붙이면 글자가 겹친다.
const DAY_LABELS = ["", "월", "", "수", "", "금", ""];

interface ContributionGridProps {
  /** 일요일부터 시작해 7의 배수로 채운 배열. */
  days: GrassDay[];
  showDayLabels?: boolean;
  showLegend?: boolean;
  showMonthLabels?: boolean;
  /** 칸 사이 간격(px). 칸 크기는 남는 폭이 정한다. */
  gap?: number;
  /** 칸 크기를 px 로 고정한다. 넘치면 좌우로 스크롤한다. */
  cell?: number;
  /** 칸을 눌러 그 날 기록을 볼 수 있게 한다. 홈만 쓴다. */
  interactive?: boolean;
}

export function ContributionGrid({
  days,
  showDayLabels = false,
  showLegend = false,
  showMonthLabels = true,
  gap = 4,
  cell,
  interactive = false,
}: ContributionGridProps) {
  const scroller = useRef<HTMLDivElement>(null);
  // 누른 칸. 폰에서는 마우스 올리기가 없어서 눌러야 그 날을 볼 수 있다.
  const [picked, setPicked] = useState<string | null>(null);

  const today = todayKey();
  const columns = Math.ceil(days.length / 7);
  const total = days.reduce((sum, day) => sum + day.count, 0);

  // 요일 라벨과 월 라벨이 각각 첫 열, 첫 줄을 차지한다.
  // 좌우로 스크롤할 때는 요일 라벨을 스크롤 밖에 세워서 열을 쓰지 않는다.
  const scrolling = cell !== undefined;
  const labelsInGrid = showDayLabels && !scrolling;
  const colOffset = labelsInGrid ? 2 : 1;
  const rowOffset = showMonthLabels ? 1 : 0;
  const pickedDay = picked ? days.find((d) => d.date === picked) : null;

  // 오른쪽 끝이 오늘이다. 열어서 바로 보이는 쪽이 최근이어야 한다.
  // 폰트가 늦게 오면 폭이 나중에 늘어나서, 한 번 더 밀어 준다.
  useEffect(() => {
    const el = scroller.current;
    if (!el) return;
    const toEnd = () => {
      el.scrollLeft = el.scrollWidth;
    };
    toEnd();
    const frame = requestAnimationFrame(toEnd);
    return () => cancelAnimationFrame(frame);
  }, [days]);

  return (
    <div>
      <div className={scrolling ? "flex" : undefined}>
        {showDayLabels && scrolling && (
          <div
            className="grid shrink-0 pr-[6px]"
            style={{
              gridTemplateRows: `${showMonthLabels ? `${MONTH_ROW}px ` : ""}repeat(7, ${cell}px)`,
              rowGap: gap,
            }}
            aria-hidden
          >
            {DAY_LABELS.map(
              (label, row) =>
                label && (
                  <span
                    key={row}
                    className="self-center text-[10px] leading-none text-gray-500"
                    style={{ gridRow: row + 1 + rowOffset }}
                  >
                    {label}
                  </span>
                ),
            )}
          </div>
        )}
        <div
          ref={scroller}
          className={scrolling ? "overflow-x-auto" : undefined}
        >
          <div
            role="group"
            aria-label={`최근 ${columns}주 인증 잔디, 총 ${total}회`}
            className={scrolling ? "grid w-max" : "grid w-full"}
            style={{
              // minmax(0,1fr) 이라야 월 라벨이 칸보다 넓어도 열이 안 늘어난다.
              gridTemplateColumns: `${labelsInGrid ? "auto " : ""}repeat(${columns}, ${cell ? `${cell}px` : "minmax(0, 1fr)"})`,
              gridTemplateRows: `${showMonthLabels ? `${MONTH_ROW}px ` : ""}repeat(7, auto)`,
              columnGap: gap,
              rowGap: gap,
            }}
          >
            {labelsInGrid &&
              DAY_LABELS.map(
                (label, row) =>
                  label && (
                    <span
                      key={row}
                      className="self-center pr-[4px] text-[10px] leading-none text-gray-500"
                      style={{ gridColumn: 1, gridRow: row + 1 + rowOffset }}
                      aria-hidden
                    >
                      {label}
                    </span>
                  ),
              )}

            {showMonthLabels &&
              monthLabels(days).map(({ column, label }) => (
                <span
                  key={column}
                  className="self-start text-[10px] leading-none whitespace-nowrap text-gray-500"
                  style={{ gridColumn: column + colOffset, gridRow: 1 }}
                  aria-hidden
                >
                  {label}
                </span>
              ))}

            {days.map((day, index) => {
              const past = day.date <= today;
              const outlined = day.date === today || day.date === picked;
              return (
                <span
                  key={day.date}
                  className="relative aspect-square"
                  style={{
                    gridColumn: Math.floor(index / 7) + colOffset,
                    gridRow: (index % 7) + 1 + rowOffset,
                  }}
                >
                  {past &&
                    (interactive ? (
                      <button
                        type="button"
                        onClick={() =>
                          setPicked(picked === day.date ? null : day.date)
                        }
                        aria-label={`${formatDate(day.date)} 인증 ${day.count}회`}
                        aria-pressed={day.date === picked}
                        // 손가락이 칸 사이 빈틈을 눌러도 잡히도록 버튼을 간격의
                        // 절반까지 넓힌다. 이웃끼리 딱 맞닿아 겹치지 않는다.
                        // 색은 안쪽으로 되돌린 칸에만 칠해서 보이는 크기는 그대로다.
                        className="absolute focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none"
                        style={{ inset: -gap / 2, borderRadius: RADIUS }}
                      >
                        <span
                          className="absolute"
                          style={{
                            inset: gap / 2,
                            borderRadius: RADIUS,
                            backgroundColor: LEVEL_BG[level(day.count)],
                          }}
                        />
                      </button>
                    ) : (
                      <span
                        title={`${formatDate(day.date)} 인증 ${day.count}회`}
                        className="block h-full w-full"
                        style={{
                          borderRadius: RADIUS,
                          backgroundColor: LEVEL_BG[level(day.count)],
                        }}
                      />
                    ))}
                  {outlined && (
                    <span
                      className="pointer-events-none absolute -inset-[2px] border"
                      style={{ borderColor: DEEP, borderRadius: RADIUS }}
                    />
                  )}
                </span>
              );
            })}
          </div>
        </div>
      </div>

      {/* 누른 날. 자리를 늘 잡아 둬서 눌러도 아래 내용이 밀리지 않는다. */}
      {interactive && (
        <p
          className="mt-[10px] text-center text-[11px] leading-none"
          aria-live="polite"
        >
          {pickedDay ? (
            <span className="text-gray-900">
              {formatDate(pickedDay.date)}{" "}
              <span className="font-bold" style={{ color: DEEP }}>
                인증 {pickedDay.count}회
              </span>
            </span>
          ) : (
            <span className="text-gray-500">
              날짜를 누르면 그 날 기록이 보여요
            </span>
          )}
        </p>
      )}

      {showLegend && (
        <div className="mt-[10px] flex items-center justify-center gap-1.5 text-[10px] text-gray-500">
          <span>적음</span>
          {LEVEL_BG.map((bg) => (
            <span
              key={bg}
              className="h-[10px] w-[10px]"
              style={{ borderRadius: RADIUS, backgroundColor: bg }}
            />
          ))}
          <span>많음</span>
        </div>
      )}
    </div>
  );
}

/**
 * 월 라벨 규칙
 *
 * 1. **그 달 1일이 들어 있는 주** 위에 그 달을 적는다. 한 열은 일요일부터
 *    토요일까지라, 1일이 목요일이면 그 열 위에 붙는다.
 * 2. 맨 앞의 잘린 달은 적지 않는다. 첫 열은 그 달의 며칠부터인지 알 수 없어
 *    라벨을 달면 그 달 전체가 보이는 것처럼 읽힌다. 대신 칸을 누르면 날짜가 뜬다.
 *
 * 일요일이 속한 달로 판단하던 방식은 라벨이 한 주 밀린다 — 1일이 주 중간에
 * 있으면 그 주의 일요일은 아직 지난달이기 때문이다.
 */
function monthLabels(days: GrassDay[]) {
  const labels: { column: number; label: string }[] = [];

  for (let column = 0; column * 7 < days.length; column += 1) {
    const week = days.slice(column * 7, column * 7 + 7);
    const firstOfMonth = week.find((day) => day.date.slice(8, 10) === "01");
    if (firstOfMonth) {
      labels.push({
        column,
        label: `${Number(firstOfMonth.date.slice(5, 7))}월`,
      });
    }
  }
  return labels;
}
