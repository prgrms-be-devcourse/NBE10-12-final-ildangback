import { CameraIcon, ClockIcon, ShareNetworkIcon } from "@phosphor-icons/react";
import type { ReactNode } from "react";
import { Button } from "../../../shared/ui/Button";
import { CheckInHeader } from "./CheckInHeader";

interface Props {
  onStart: () => void;
}

/** 와이어프레임 2번 — 사진 인증 안내 + 촬영 시작. */
export function CheckInIntro({ onStart }: Props) {
  return (
    <div className="flex flex-1 flex-col items-center px-8 pt-6 pb-8 text-center">
      <CheckInHeader />

      <CameraIcon size={72} weight="fill" className="mt-10 text-purple-400" />

      <h1 className="mt-6 text-[22px] font-bold text-gray-900">
        오늘의 인증을 <span className="text-purple-500">사진</span>으로 기록해요
      </h1>
      <p className="mt-3 text-[14px] leading-relaxed text-gray-500">
        운동, 공부, 독서, 휴식 등{"\n"}오늘의 활동을 찍어주세요!
      </p>

      <ul className="mt-8 w-full space-y-4 text-left">
        <Hint icon={<CameraIcon size={20} />} title="즉석 촬영만 가능해요">
          앨범에서 사진을 불러올 수 없어요
        </Hint>
        <Hint
          icon={<ClockIcon size={20} />}
          title="하루 여러 번 인증할 수 있어요"
        >
          인증 횟수는 그룹 설정을 따릅니다
        </Hint>
        <Hint
          icon={<ShareNetworkIcon size={20} />}
          title="인증은 그룹원과 공유돼요"
        >
          갤러리에 저장되어 함께 볼 수 있어요
        </Hint>
      </ul>

      <Button className="mt-10" onClick={onStart}>
        사진 촬영하기
      </Button>
    </div>
  );
}

function Hint({
  icon,
  title,
  children,
}: {
  icon: ReactNode;
  title: string;
  children: ReactNode;
}) {
  return (
    <li className="flex gap-3">
      <span className="mt-0.5 shrink-0 text-purple-400">{icon}</span>
      <span>
        <span className="block text-[14px] font-semibold text-gray-900">
          {title}
        </span>
        <span className="block text-[13px] text-gray-500">{children}</span>
      </span>
    </li>
  );
}
