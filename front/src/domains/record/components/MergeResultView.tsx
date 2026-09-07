import {
  CalendarBlankIcon,
  CheckSquareIcon,
  CrownSimpleIcon,
  DownloadSimpleIcon,
  DropIcon,
} from "@phosphor-icons/react";
import { toPng } from "html-to-image";
import { useRef, useState } from "react";
import { CheckInTrendChart } from "./CheckInTrendChart";
import { Button } from "../../../shared/ui/Button";
import { useToast } from "../../../shared/lib/useToast";
import type { MergeParticipantResponse } from "../../../shared/api/types";

/** LocalDate("YYYY-MM-DD")를 "YYYY.MM.DD"로. */
function formatDateDot(localDate: string): string {
  return localDate.replaceAll("-", ".");
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = reject;
    image.src = src;
  });
}

interface MergeResultViewProps {
  /** 챌린지/그룹 이름. 카드 맨 위에 픽셀 폰트로 표시된다. */
  groupName: string;
  /** "CHALLENGE MERGE #42" / "MONTHLY MERGE #03" 처럼 헤더에 쓸 라벨. */
  badgeLabel: string;
  periodStart: string;
  periodEnd: string;
  totalDays: number;
  totalCheckInCount: number;
  averageCompletionRate: number;
  participants: MergeParticipantResponse[];
  currentUserId: number;
  /** "30일의 운동 기록을 하나로 합쳤어요" 처럼 MERGED 배지 아래 설명 문구. */
  summaryDescription: string;
  /** "나의 기록" 섹션 제목 ("나의 기여 기록" / "나의 월간 기록"). */
  myRecordSectionTitle: string;
  /** "내가 운동한 날" 처럼 진행률 바 라벨. */
  myProgressLabel: string;
  /** "주간 인증 추이" / "월별 인증 추이" - 그래프 제목. */
  trendChartTitle: string;
  /** "월간 결과 저장하기" 처럼 결과 카드 이미지 저장 버튼 문구. */
  saveButtonLabel: string;
  /** "N일 함께함" 통계 표시 여부. 월간 머지는 주기가 항상 고정 30일이라 의미가
   * 없어서 숨긴다 - 최종 머지는 챌린지 전체 기간이라 의미 있어서 보여준다. */
  showDaysTogetherStat?: boolean;
}

export function MergeResultView({
  groupName,
  badgeLabel,
  periodStart,
  periodEnd,
  totalDays,
  totalCheckInCount,
  averageCompletionRate,
  participants,
  currentUserId,
  summaryDescription,
  myRecordSectionTitle,
  myProgressLabel,
  trendChartTitle,
  saveButtonLabel,
  showDaysTogetherStat = true,
}: MergeResultViewProps) {
  const { showToast } = useToast();
  const sorted = [...participants].sort((a, b) => a.ranking - b.ranking);
  const me = participants.find((p) => p.userId === currentUserId);
  const resultCardRef = useRef<HTMLDivElement | null>(null);
  const [saving, setSaving] = useState(false);

  const handleSaveImage = async () => {
    if (!resultCardRef.current || saving) return;
    setSaving(true);
    try {
      // html-to-image는 toPng()의 style 옵션으로 padding을 주더라도 캔버스
      // 크기는 원본 노드 크기 그대로 잡아서, 늘어난 여백만큼 오른쪽/아래가
      // 잘린다. 그래서 원본을 먼저 그대로 캡처한 뒤, 여백이 있는 더 큰
      // 캔버스에 중앙 배치하듯 그려 넣는 방식으로 우회한다.
      const pixelRatio = 2;
      const padding = 20 * pixelRatio;
      const rawDataUrl = await toPng(resultCardRef.current, {
        pixelRatio,
        backgroundColor: "#ffffff",
      });
      const rawImage = await loadImage(rawDataUrl);

      const canvas = document.createElement("canvas");
      canvas.width = rawImage.width + padding * 2;
      canvas.height = rawImage.height + padding * 2;
      const ctx = canvas.getContext("2d");
      if (!ctx) throw new Error("canvas context 생성 실패");
      ctx.fillStyle = "#ffffff";
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(rawImage, padding, padding);

      const link = document.createElement("a");
      link.download =
        `${groupName}_${badgeLabel}`.replaceAll(" ", "_") + ".png";
      link.href = canvas.toDataURL("image/png");
      link.click();
    } catch {
      showToast("이미지 저장에 실패했어요. 다시 시도해 주세요.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="px-5 pt-3 pb-10">
      {/* 저장 버튼을 누르면 이 wrapper 전체(그룹 결과 + 나의 기록 카드)가 이미지로
          저장된다 - 위 섹션만 캡처되지 않도록 ref를 여기(바깥)에 둔다. */}
      <div ref={resultCardRef} className="bg-white">
        <section className="rounded-2xl border border-purple-200 bg-white px-5 py-6">
          <div className="flex items-center justify-between">
            <span className="text-[11px] font-bold tracking-wide text-purple-600">
              {badgeLabel}
            </span>
            <span className="text-[12px] text-gray-500">
              {formatDateDot(periodStart)} - {formatDateDot(periodEnd)}
            </span>
          </div>

          <div className="mt-4 flex items-center gap-2">
            <PixelDots />
            <div className="min-w-0 flex-1">
              <p
                className="truncate text-center text-[15px] font-bold text-purple-500"
                style={{ fontFamily: "'NeoDunggeunmo', monospace" }}
              >
                {groupName}
              </p>
              <div className="mt-2 h-px bg-purple-200" />
            </div>
            <PixelDots />
          </div>

          <div className="mt-5 flex items-center justify-center">
            <span
              className="rounded-none border-2 border-purple-500 px-2.5 py-1 text-[26px] whitespace-nowrap text-purple-600 shadow-[3px_3px_0_0_#b9a2e0]"
              style={{ fontFamily: "'Press Start 2P', monospace" }}
            >
              MERGED
            </span>
          </div>
          <p className="mt-3 text-center text-[13px] text-gray-500">
            {summaryDescription}
          </p>

          <ul className="mt-6 flex justify-center gap-4 overflow-x-auto">
            {sorted.map((p, index) => (
              <li
                key={p.userId}
                className="flex w-14 shrink-0 flex-col items-center"
              >
                <div className="relative">
                  {index === 0 && (
                    <CrownSimpleIcon
                      size={16}
                      weight="fill"
                      className="absolute -top-4 left-1/2 -translate-x-1/2 text-amber-400"
                      aria-hidden
                    />
                  )}
                  {/* TODO(Record): 캐릭터 일러스트는 User/Item 도메인 연결되면 교체 - 지금은 이니셜 원형. */}
                  <div className="flex h-12 w-12 items-center justify-center rounded-full bg-purple-100 text-[15px] font-bold text-purple-600">
                    {p.nickname.slice(0, 1)}
                  </div>
                </div>
                <p className="mt-1.5 max-w-full truncate text-[12px] font-medium text-gray-700">
                  {p.nickname}
                </p>
                <p className="text-[13px] font-bold text-purple-600">
                  {p.completionRate}%
                </p>
              </li>
            ))}
          </ul>

          <div className="mt-6 flex items-center justify-around border-t border-purple-100 pt-4 text-[12px] text-gray-500">
            {showDaysTogetherStat && (
              <SummaryStat icon={<CalendarBlankIcon size={16} weight="bold" />}>
                <span className="font-bold text-purple-600">{totalDays}일</span>{" "}
                함께함
              </SummaryStat>
            )}
            <SummaryStat icon={<CheckSquareIcon size={16} weight="bold" />}>
              총{" "}
              <span className="font-bold text-purple-600">
                {totalCheckInCount}회
              </span>{" "}
              인증
            </SummaryStat>
            <SummaryStat icon={<DropIcon size={16} weight="bold" />}>
              평균 완주율{" "}
              <span className="font-bold text-purple-600">
                {averageCompletionRate}%
              </span>
            </SummaryStat>
          </div>
        </section>

        <div className="mt-6 flex items-center justify-between">
          <h2 className="text-[15px] font-bold text-gray-900">
            {myRecordSectionTitle}
          </h2>
        </div>

        {me ? (
          <MyRecordCard
            me={me}
            totalDays={totalDays}
            groupTotalCheckInCount={totalCheckInCount}
            myProgressLabel={myProgressLabel}
            trendChartTitle={trendChartTitle}
          />
        ) : (
          <p className="mt-3 rounded-2xl border border-purple-200 bg-white py-8 text-center text-[13px] text-gray-500">
            이 머지에 참여 기록이 없어요.
          </p>
        )}
      </div>

      <div className="mt-6">
        <Button
          onClick={handleSaveImage}
          disabled={saving}
          className="flex h-12 w-full items-center justify-center gap-2 disabled:opacity-60"
        >
          <DownloadSimpleIcon size={18} weight="bold" />
          {saving ? "저장 중…" : saveButtonLabel}
        </Button>
      </div>
    </div>
  );
}

function SummaryStat({
  icon,
  children,
}: {
  icon: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <p className="flex items-center gap-1.5">
      <span className="text-purple-400">{icon}</span>
      <span>{children}</span>
    </p>
  );
}

function MyRecordCard({
  me,
  totalDays,
  groupTotalCheckInCount,
  myProgressLabel,
  trendChartTitle,
}: {
  me: MergeParticipantResponse;
  totalDays: number;
  groupTotalCheckInCount: number;
  myProgressLabel: string;
  trendChartTitle: string;
}) {
  return (
    <div className="mt-3 rounded-2xl border border-purple-200 bg-white p-5">
      <div className="flex items-center gap-3">
        {/* TODO(Record): 캐릭터 일러스트는 User/Item 도메인 연결되면 교체 - 지금은 이니셜 원형. */}
        <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-full bg-purple-100 text-[20px] font-bold text-purple-600">
          {me.nickname.slice(0, 1)}
        </div>
        <div className="min-w-0 flex-1">
          <p className="truncate text-[16px] font-bold text-gray-900">
            {me.nickname}
          </p>
          {/* TODO(Record): User 도메인에 handle/아이디 필드 생기면 그걸로 교체 - 지금은 닉네임으로 대체. */}
          <p className="truncate text-[12px] text-gray-400">@{me.nickname}</p>
          <p className="mt-1 text-[24px] font-extrabold text-gray-900">
            {me.completedDayCount}
            <span className="text-[16px] font-bold"> / {totalDays}일</span>
          </p>
        </div>
      </div>

      <div className="mt-4">
        <div className="flex items-center justify-between">
          <p className="text-[12px] text-gray-500">{myProgressLabel}</p>
          <span className="text-[15px] font-bold text-purple-600">
            {me.completionRate}%
          </span>
        </div>
        <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-purple-50">
          <div
            className="h-full rounded-full bg-purple-500"
            style={{ width: `${Math.min(100, me.completionRate)}%` }}
          />
        </div>
      </div>

      <div className="mt-4 flex items-center rounded-xl border border-purple-100 py-2">
        <StatTile
          icon={<CheckSquareIcon size={16} weight="bold" />}
          label="총인증"
          value={`${me.totalCheckInCount}회`}
        />
        <div className="h-8 w-px bg-purple-100" aria-hidden />
        <StatTile
          icon={<CalendarBlankIcon size={16} weight="bold" />}
          label="최장연속"
          value={`${me.bestStreakInPeriod}일`}
        />
      </div>

      <div className="mt-4 border-t border-dashed border-purple-200 pt-4">
        <CheckInTrendChart
          title={trendChartTitle}
          labels={me.checkInTrendLabels}
          counts={me.checkInTrendCounts}
        />
      </div>

      <div className="mt-4 flex items-center rounded-2xl border border-purple-200 bg-purple-50/40 px-4 py-3">
        <p className="shrink-0 pr-4 text-[20px] font-extrabold text-purple-600">
          {me.contributionRate}%
        </p>
        <div className="flex-1 border-l border-purple-200 pl-4 text-[12px] text-gray-600">
          <p>
            그룹 전체 인증{" "}
            <span className="font-bold text-purple-600">
              {groupTotalCheckInCount}
            </span>
            건 중{" "}
            <span className="font-bold text-purple-600">
              {me.totalCheckInCount}
            </span>
            건을 채웠어요
          </p>
          <p className="mt-0.5 text-[11px] text-gray-400">
            기여도 {me.contributionRate}%
          </p>
        </div>
        <PixelSparkle />
      </div>
    </div>
  );
}

function StatTile({
  icon,
  label,
  value,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
}) {
  return (
    <div className="flex flex-1 items-center justify-center gap-1.5 px-2">
      <span className="text-purple-400">{icon}</span>
      <span>
        <span className="text-[12px] text-gray-500">{label}</span>
        <span className="ml-1 text-[15px] font-bold text-purple-600">
          {value}
        </span>
      </span>
    </div>
  );
}

/** 구분선 양 끝에 붙는 2x2 보라색 픽셀 도트 장식. */
function PixelDots() {
  return (
    <div className="grid shrink-0 grid-cols-2 gap-1" aria-hidden>
      <span className="h-1 w-1 bg-purple-300" />
      <span className="h-1 w-1 bg-purple-300" />
      <span className="h-1 w-1 bg-purple-300" />
      <span className="h-1 w-1 bg-purple-300" />
    </div>
  );
}

/** 기여도 박스 오른쪽에 붙는 보라색 픽셀 반짝임(다이아몬드 도트) 장식. */
function PixelSparkle() {
  return (
    <div
      className="ml-3 grid shrink-0 grid-cols-3 grid-rows-3 gap-0.5"
      aria-hidden
    >
      <span />
      <span className="h-1 w-1 bg-purple-300" />
      <span />
      <span className="h-1 w-1 bg-purple-300" />
      <span />
      <span className="h-1 w-1 bg-purple-300" />
      <span />
      <span className="h-1 w-1 bg-purple-300" />
      <span />
    </div>
  );
}
