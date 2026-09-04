import { Button } from "../shared/ui/Button";

/**
 * ⚠️ 임시 스텁 — 챌린지 상세 상단 카드.
 *
 * 실제 챌린지 도메인이 들어오면 이 파일을 지우고, 그쪽 상세 응답
 * (`GET /challenges/{id}`)으로 채운 카드로 교체한다. 여기 값은 전부 하드코딩이다.
 * 체크인 쪽이 이 카드에서 필요로 하는 건 "오늘 인증하기" 버튼 하나뿐이다.
 */
interface Props {
  /** "오늘 인증하기" — 방법 선택 시트를 연다. */
  onCheckIn: () => void;
  /** 오늘 인증 상태를 아직 부르는 중이거나 인증 불가일 때 버튼을 잠근다. */
  checkInDisabled: boolean;
  /** 버튼이 잠긴 이유(완료 / 인증일 아님). null 이면 문구를 숨긴다. */
  blockedReason: string | null;
}

export function StubChallengeHeaderCard({
  onCheckIn,
  checkInDisabled,
  blockedReason,
}: Props) {
  return (
    <div className="mx-6 mt-4 rounded-2xl border border-purple-100 px-5 py-4">
      <p className="text-[13px] font-semibold text-purple-500">오운완</p>
      <h1 className="mt-1 text-[20px] font-bold text-gray-900">
        매일 2시간 운동하기
      </h1>
      <p className="mt-2 text-[13px] text-gray-500">Day 110 / 180 · 4명 참여</p>

      <div className="mt-3 flex items-center gap-2">
        <div className="h-2 flex-1 overflow-hidden rounded-full bg-purple-100">
          <div className="h-full w-[61%] rounded-full bg-purple-500" />
        </div>
        <span className="text-[12px] font-semibold text-purple-500">61%</span>
      </div>
      <p className="mt-1 text-[12px] text-gray-400">완주까지 70일</p>

      <Button className="mt-4" onClick={onCheckIn} disabled={checkInDisabled}>
        오늘 인증하기
      </Button>
      {blockedReason && (
        <p className="mt-2 text-center text-[13px] text-gray-400">
          {blockedReason}
        </p>
      )}
    </div>
  );
}
