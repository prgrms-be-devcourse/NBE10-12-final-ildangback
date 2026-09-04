import { CaretRightIcon } from "@phosphor-icons/react";
import { useEffect, useState } from "react";
import { useParams } from "react-router";
import { getMyHistoryDetail } from "../api";
import { formatDateTimeDot } from "../../../shared/lib/date";
import { PixelIcon } from "../../../shared/ui/PixelIcon";
import { pixelIcons } from "../../../shared/ui/pixelIcons";
import { TopBar } from "../../../shared/ui/TopBar";
import { REASON_TYPE_LABEL, reasonSentence } from "../lib/reason";
import type { UserPointHistoryResponse } from "../../../shared/api/types";

export function PointHistoryDetailPage() {
  const { historyId } = useParams<{ historyId: string }>();
  const [history, setHistory] = useState<UserPointHistoryResponse | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!historyId) return;
    let cancelled = false;

    getMyHistoryDetail(Number(historyId))
      .then((detail) => {
        if (!cancelled) setHistory(detail);
      })
      .catch(() => {
        if (!cancelled) setError(true);
      });

    return () => {
      cancelled = true;
    };
  }, [historyId]);

  return (
    <>
      <TopBar title="포인트 상세" />

      <div className="px-6 pt-3 pb-10">
        {error && (
          <p className="py-10 text-center text-[13px] text-gray-500">
            존재하지 않는 포인트 내역이에요.
          </p>
        )}

        {!error && !history && (
          <p className="py-10 text-center text-[13px] text-gray-500">
            불러오는 중…
          </p>
        )}

        {history && <Detail history={history} />}
      </div>
    </>
  );
}

function Detail({ history }: { history: UserPointHistoryResponse }) {
  const isEarn = history.amount > 0;
  const beforeBalance = history.balanceAfter - history.amount;

  return (
    <>
      <div className="flex flex-col items-center pt-6 pb-9 text-center">
        <PixelIcon src={pixelIcons.characterWink} size={140} />
        <p className="mt-4 text-[16px] font-semibold text-purple-600">
          {isEarn ? "적립 완료" : "차감 완료"}
        </p>
        <p className="mt-2 text-[44px] font-semibold text-[#4A4A4A]">
          {isEarn ? "+" : ""}
          {history.amount.toLocaleString()}
          <span className="text-[26px]">P</span>
        </p>
        <p className="mt-1.5 text-[14px] text-gray-500">
          {reasonSentence(history.reason, history.sourceName)}
        </p>
      </div>

      <dl className="overflow-hidden rounded-2xl border border-purple-200 [&>*+*]:border-t [&>*+*]:border-purple-100">
        <InfoRow
          label="처리 일시"
          value={formatDateTimeDot(history.createdAt)}
        />
        <InfoRow label="내역 유형" value={REASON_TYPE_LABEL[history.reason]} />
        <InfoRow label="발생 경로" value={history.sourceName} />
      </dl>

      <div className="mt-5 flex items-center justify-between rounded-2xl border border-purple-200 px-4 py-5">
        <BalanceStep label="거래 전" value={beforeBalance} />
        <CaretRightIcon
          size={16}
          className="shrink-0 text-purple-300"
          aria-hidden
        />
        <BalanceStep
          label={isEarn ? "적립" : "차감"}
          value={history.amount}
          signed
        />
        <CaretRightIcon
          size={16}
          className="shrink-0 text-purple-300"
          aria-hidden
        />
        <BalanceStep label="거래 후" value={history.balanceAfter} />
      </div>
    </>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between px-4 py-4">
      <dt className="text-[14px] text-gray-500">{label}</dt>
      <dd className="text-[15px] font-medium text-[#4A4A4A]">{value}</dd>
    </div>
  );
}

function BalanceStep({
  label,
  value,
  signed = false,
}: {
  label: string;
  value: number;
  signed?: boolean;
}) {
  return (
    <div className="flex-1 text-center">
      <p className="text-[12px] text-gray-500">{label}</p>
      <p
        className={`mt-1.5 text-[16px] font-semibold ${signed ? "text-purple-600" : "text-[#4A4A4A]"}`}
      >
        {signed && value > 0 ? "+" : ""}
        {value.toLocaleString()}P
      </p>
    </div>
  );
}
