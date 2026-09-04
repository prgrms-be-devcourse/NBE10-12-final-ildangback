import { useState } from "react";
import { Button } from "../../../shared/ui/Button";
import { TextField } from "../../../shared/ui/TextField";
import type { CapturedPhoto } from "../types";
import { CheckInHeader } from "./CheckInHeader";

const MEMO_MAX = 100;

interface Props {
  photo: CapturedPhoto;
  submitting: boolean;
  onRetake: () => void;
  onSubmit: (memo: string) => void;
}

/** 와이어프레임 3번 — 촬영한 사진 확인 + 메모(선택) + 제출. */
export function CheckInConfirm({
  photo,
  submitting,
  onRetake,
  onSubmit,
}: Props) {
  const [memo, setMemo] = useState("");
  // 촬영 직후 이 화면이 뜨므로 마운트 시각을 촬영 시각으로 얼린다.
  // 매 렌더(메모 입력 등)마다 new Date() 를 다시 찍으면 표시가 흐른다.
  const [takenAt] = useState(() =>
    new Intl.DateTimeFormat("ko-KR", {
      dateStyle: "medium",
      timeStyle: "short",
    }).format(new Date()),
  );

  return (
    <div className="flex flex-1 flex-col px-6 pt-4 pb-8">
      <CheckInHeader />

      <img
        src={photo.previewUrl}
        alt="촬영한 인증 사진"
        className="mt-6 aspect-square w-full rounded-3xl object-cover"
      />
      <p className="mt-2 text-center text-[13px] text-gray-400">{takenAt}</p>

      <TextField
        label="오늘의 기록"
        hideLabel
        className="mt-4"
        placeholder="오늘의 기록을 한 줄로 남겨보세요 (선택)"
        maxLength={MEMO_MAX}
        counter={`${memo.length}/${MEMO_MAX}`}
        value={memo}
        onChange={(e) => setMemo(e.target.value)}
      />

      <div className="mt-6 grid grid-cols-2 gap-3">
        <Button variant="secondary" onClick={onRetake} disabled={submitting}>
          다시 찍기
        </Button>
        <Button onClick={() => onSubmit(memo.trim())} loading={submitting}>
          이 사진 사용
        </Button>
      </div>
    </div>
  );
}
