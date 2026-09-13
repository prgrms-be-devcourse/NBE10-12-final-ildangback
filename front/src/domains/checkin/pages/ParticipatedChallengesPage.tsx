import { CaretRightIcon, ImagesIcon } from "@phosphor-icons/react";
import { Link } from "react-router";
import { AuthedImage } from "../../../shared/ui/AuthedImage";
import { TopBar } from "../../../shared/ui/TopBar";
import { getMyChallengeCovers } from "../api";
import { useFetchOnce } from "../lib/useFetchOnce";
import { useMyChallenges } from "../lib/useMyChallenges";
import type { MyChallengeSummary } from "../types";

/**
 * 프로필 > 참여했던 챌린지. 인증 데이터를 직접 보여주는 화면이 아니라 갈래를 나누는
 * 중간 페이지다.
 *   - 위: "전체 인증 모아보기" → 모든 챌린지 통합 (MyCheckInsPage)
 *   - 아래: "챌린지 앨범" → 챌린지별 2열 그리드, 각 카드 → 그 챌린지 앨범
 */
export function ParticipatedChallengesPage() {
  const challenges = useMyChallenges();

  return (
    <>
      <TopBar title="참여했던 챌린지" />

      <div className="flex-1 px-5 pb-10">
        <Link
          to="/profile/check-ins"
          className="mt-2 flex items-center gap-3 rounded-2xl border border-purple-200 p-4"
        >
          <ImagesIcon size={28} weight="fill" className="text-purple-400" />
          <span className="flex-1">
            <span className="block text-[15px] font-bold text-gray-900">
              전체 인증 모아보기
            </span>
            <span className="block text-[13px] text-gray-500">
              모든 챌린지의 내 인증을 한 번에
            </span>
          </span>
          <CaretRightIcon size={18} className="text-gray-400" aria-hidden />
        </Link>

        <h2 className="mt-7 mb-3 text-[15px] font-bold text-gray-900">
          챌린지 앨범
        </h2>

        {challenges.length === 0 ? (
          <p className="py-12 text-center text-[14px] text-gray-400">
            참여한 챌린지가 없어요
          </p>
        ) : (
          <div className="grid grid-cols-2 gap-3">
            {challenges.map((c) => (
              <AlbumCard key={c.challengeId} challenge={c} />
            ))}
          </div>
        )}
      </div>
    </>
  );
}

function AlbumCard({ challenge }: { challenge: MyChallengeSummary }) {
  // 카드마다 따로 받는다. 그리드는 먼저 그려지고 커버는 도착하는 대로 채워진다.
  const covers = useFetchOnce<string[]>(
    () => getMyChallengeCovers(challenge.challengeId),
    [challenge.challengeId],
    [],
  );

  return (
    <Link to={`/profile/challenges/${challenge.challengeId}/album`}>
      <div className="grid aspect-square grid-cols-2 grid-rows-2 overflow-hidden rounded-xl bg-gray-100">
        {Array.from({ length: 4 }, (_, i) =>
          covers[i] ? (
            <AuthedImage
              key={i}
              src={covers[i]}
              // 지연 로딩을 끈다. 커버는 이 화면의 본문이고 화면 위쪽에 다 들어오는데,
              // 커버 목록을 받은 뒤에야 img 가 붙어서 그때 IntersectionObserver 를
              // 새로 달게 된다. 탭이 백그라운드거나 렌더가 throttle 되면 그 콜백이
              // 아예 안 와서 커버가 영영 안 뜬다.
              lazy={false}
              alt=""
              aria-hidden
              className="size-full object-cover"
            />
          ) : (
            <div key={i} className="size-full bg-purple-50" />
          ),
        )}
      </div>
      <p className="mt-2 truncate text-[13px] font-semibold text-gray-900">
        {challenge.name}
      </p>
    </Link>
  );
}
