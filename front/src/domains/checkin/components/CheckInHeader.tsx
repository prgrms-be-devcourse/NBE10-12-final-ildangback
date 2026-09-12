import { Logo } from "../../../shared/ui/Logo";
import { PixelSparkle } from "../../../shared/ui/PixelSparkle";

/**
 * 체크인 플로우 화면(인트로 · 확인 · 완료) 공통 상단 헤더.
 * 시안 2·3·4 의 "Go!mmit 픽셀 로고 + 양옆 반짝임" 조합이다. 세 화면이 같은 머리를
 * 쓰므로 한곳에 둔다.
 */
export function CheckInHeader({ className = "" }: { className?: string }) {
  return (
    <div className={`flex items-center justify-center gap-2 ${className}`}>
      <PixelSparkle className="-scale-x-100" />
      <Logo className="h-9" />
      <PixelSparkle />
    </div>
  );
}
