import { Button } from "../../../shared/ui/Button";

interface Props {
  title: string;
  description: string;
  /** 없으면(예: 기기 미지원) 재시도해도 소용없는 상태 — 버튼을 안 그린다. */
  onRetry?: () => void;
}

/** CheckInCamera/CheckInVideoCamera 가 공유하는 카메라 권한거부·에러 화면. */
export function CameraPermissionError({ title, description, onRetry }: Props) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center px-8 text-center">
      <p className="text-[15px] font-semibold text-gray-900">{title}</p>
      <p className="mt-2 text-[13px] text-gray-500">{description}</p>
      {onRetry && (
        <Button variant="secondary" className="mt-6" onClick={onRetry}>
          다시 시도
        </Button>
      )}
    </div>
  );
}
