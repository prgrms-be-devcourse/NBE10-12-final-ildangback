import { CaretRightIcon } from "@phosphor-icons/react";
import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router";
import { getChallengeMergeOverview, getMergeList } from "../api";
import { MergeStatusCircle } from "../components/MergeStatusCircle";
import { CATEGORY_LABEL } from "../lib/category";
import { TopBar } from "../../../shared/ui/TopBar";
import type {
  ChallengeMergeOverviewResponse,
  MergeSummaryResponse,
} from "../../../shared/api/types";

/** LocalDate("YYYY-MM-DD")를 "YYYY.MM.DD"로. */
function formatDateDot(localDate: string): string {
  return localDate.replaceAll("-", ".");
}

/**
 * LocalDate("YYYY-MM-DD")에 일수를 더한다. toISOString()은 UTC로 변환하는데,
 * KST(UTC+9)에서는 로컬 자정이 전날 UTC 15시라 하루가 밀린다 - 로컬 필드로
 * 직접 조립해서 이 문제를 피한다.
 */
function addDays(localDate: string, days: number): string {
  const date = new Date(`${localDate}T00:00:00`);
  date.setDate(date.getDate() + days);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

interface DisplayRow {
  key: string;
  label: string;
  statusLabel: string;
  periodStart: string;
  periodEnd: string;
  averageCompletionRate: number | null;
  clickable: boolean;
  status: "completed" | "current";
  onClick?: () => void;
}

export function MergeListPage() {
  const { challengeId } = useParams<{ challengeId: string }>();
  const navigate = useNavigate();
  const [overview, setOverview] =
    useState<ChallengeMergeOverviewResponse | null>(null);
  const [merges, setMerges] = useState<MergeSummaryResponse[] | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!challengeId) return;
    let cancelled = false;

    Promise.all([
      getChallengeMergeOverview(Number(challengeId)),
      getMergeList(Number(challengeId)),
    ])
      .then(([overviewData, list]) => {
        if (cancelled) return;
        setOverview(overviewData);
        setMerges(list);
      })
      .catch(() => {
        if (!cancelled) setError(true);
      });

    return () => {
      cancelled = true;
    };
  }, [challengeId]);

  const rows: DisplayRow[] = [];
  if (overview?.currentSeqNo != null) {
    const cycleStart = addDays(
      overview.periodStart,
      (overview.currentSeqNo - 1) * overview.cycleLengthDays,
    );
    rows.push({
      key: "current",
      label: `#${String(overview.currentSeqNo).padStart(2, "0")}`,
      statusLabel: "진행 중",
      periodStart: cycleStart,
      periodEnd: addDays(cycleStart, overview.cycleLengthDays - 1),
      averageCompletionRate: null,
      clickable: false,
      status: "current",
    });
  }
  for (const merge of merges ?? []) {
    rows.push({
      key: `${merge.type}-${merge.mergeId}`,
      label:
        merge.type === "FINAL"
          ? "최종 머지"
          : `#${String(merge.seqNo).padStart(2, "0")}`,
      statusLabel: "Merged",
      periodStart: merge.periodStart,
      periodEnd: merge.periodEnd,
      averageCompletionRate: merge.averageCompletionRate,
      clickable: true,
      status: "completed",
      onClick: () =>
        navigate(
          merge.type === "FINAL"
            ? `/challenges/${challengeId}/final-merge`
            : `/challenges/${challengeId}/monthly-merges/${merge.seqNo}`,
        ),
    });
  }

  return (
    <>
      <TopBar
        title={overview ? overview.groupName : `챌린지 #${challengeId}`}
      />

      <div className="px-5 pt-4 pb-10">
        {error && (
          <p className="py-10 text-center text-[13px] text-gray-500">
            머지 목록을 불러오지 못했어요.
          </p>
        )}

        {!error && (!overview || !merges) && (
          <p className="py-10 text-center text-[13px] text-gray-500">
            불러오는 중…
          </p>
        )}

        {overview && (
          <section className="rounded-2xl border border-purple-200 bg-white p-5">
            <div className="flex items-center gap-2 text-[13px] text-gray-500">
              <span className="rounded-full bg-purple-50 px-2 py-0.5 text-[11px] font-medium text-purple-600">
                {CATEGORY_LABEL[overview.category]}
              </span>
              <span>{overview.totalDays}일 챌린지</span>
              <span>
                {formatDateDot(overview.periodStart)} -{" "}
                {formatDateDot(overview.periodEnd)}
              </span>
            </div>
            <div className="mt-3 flex items-center gap-1.5 text-[15px] font-bold text-gray-900">
              월간 머지
              <span className="text-[20px] text-purple-600">
                {overview.completedMergeCount}
              </span>
              <span className="text-gray-400">
                / {overview.totalMergeCount}개
              </span>
            </div>
          </section>
        )}

        {rows.length > 0 && (
          <>
            <h2 className="mt-6 mb-3 text-[15px] font-bold text-gray-900">
              월간 머지 목록
            </h2>
            <ul>
              {rows.map((row, i) => {
                const isLast = i === rows.length - 1;
                return (
                  <li key={row.key} className="flex gap-3">
                    <div className="flex flex-col items-center self-stretch">
                      <div
                        className={`w-px flex-1 ${i === 0 ? "invisible" : "bg-[#D9CDED]"}`}
                        aria-hidden
                      />
                      <MergeStatusCircle status={row.status} size={26} />
                      <div
                        className={`w-px flex-1 ${isLast ? "invisible" : "bg-[#D9CDED]"}`}
                        aria-hidden
                      />
                    </div>

                    <div className={isLast ? "flex-1" : "flex-1 pb-3"}>
                      {row.clickable ? (
                        <button
                          type="button"
                          onClick={row.onClick}
                          className="flex w-full items-center justify-between rounded-2xl border border-purple-200 bg-white px-4 py-4 text-left transition-colors hover:bg-purple-50/50"
                        >
                          <MergeRowContent row={row} />
                        </button>
                      ) : (
                        <div className="flex w-full items-center justify-between rounded-2xl border border-dashed border-purple-200 bg-white px-4 py-4">
                          <MergeRowContent row={row} />
                        </div>
                      )}
                    </div>
                  </li>
                );
              })}
            </ul>
          </>
        )}

        {overview && rows.length === 0 && (
          <p className="py-10 text-center text-[13px] text-gray-500">
            아직 발행된 머지가 없어요. 챌린지가 진행되면 여기 쌓여요.
          </p>
        )}
      </div>
    </>
  );
}

function MergeRowContent({ row }: { row: DisplayRow }) {
  return (
    <>
      <div>
        <div className="flex items-center gap-3">
          <span
            className={`text-[17px] font-bold ${row.status === "current" ? "text-gray-900" : "text-[#7D7D7D]"}`}
          >
            {row.label}
          </span>
          <span
            className={`flex items-center gap-1 text-[12px] font-medium ${row.status === "current" ? "text-gray-400" : "text-purple-600"}`}
          >
            <span
              className={`h-1.5 w-1.5 rounded-full bg-[#8551C9] ${row.status === "current" ? "animate-pulse" : ""}`}
              aria-hidden
            />
            {row.statusLabel}
          </span>
        </div>
        <p className="mt-1 text-[12px] text-gray-500">
          {formatDateDot(row.periodStart)} - {formatDateDot(row.periodEnd)}
        </p>
      </div>

      <div className="flex shrink-0 items-center gap-2">
        {row.averageCompletionRate != null && (
          <div className="text-right">
            <p className="text-[11px] text-gray-500">그룹 평균 완주율</p>
            <p className="text-[18px] font-extrabold text-purple-600">
              {row.averageCompletionRate}%
            </p>
          </div>
        )}
        {row.clickable && (
          <CaretRightIcon size={18} className="text-gray-300" aria-hidden />
        )}
      </div>
    </>
  );
}
