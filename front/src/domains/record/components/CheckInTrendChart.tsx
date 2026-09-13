interface CheckInTrendChartProps {
  title: string;
  labels: string[];
  counts: number[];
}

const CHART_HEIGHT = 80;

// "주간/월별 인증 추이" 막대 그래프. labels/counts가 비어있으면(CheckIn 미연동 데이터)
// 아무것도 렌더링하지 않는다 - 부모가 렌더 여부를 신경 안 쓰게.
export function CheckInTrendChart({
  title,
  labels,
  counts,
}: CheckInTrendChartProps) {
  if (labels.length === 0 || counts.length === 0) return null;

  const max = Math.max(...counts, 1);

  return (
    <div>
      <p className="text-[13px] font-semibold text-gray-900">{title}</p>
      <div className="mt-3 flex items-end justify-between gap-2">
        {counts.map((count, index) => {
          const isPeak = count === max;
          const height = Math.max(4, Math.round((count / max) * CHART_HEIGHT));

          return (
            <div key={index} className="flex flex-1 flex-col items-center">
              <span
                className={`text-[13px] font-bold ${isPeak ? "text-purple-600" : "text-gray-400"}`}
              >
                {count}
              </span>
              <div
                className={`mt-1 w-full max-w-6 rounded-t-md ${isPeak ? "bg-purple-600" : "bg-purple-100"}`}
                style={{ height }}
              />
              <span className="mt-1.5 text-[11px] text-gray-400">
                {labels[index]}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
