import { useNavigate } from "react-router";
import { MobileShell } from "../app/MobileShell";
import { Button } from "../shared/ui/Button";

export function NotFoundPage() {
  const navigate = useNavigate();

  return (
    <MobileShell>
      <div className="flex flex-1 flex-col items-center justify-center px-6">
        <p className="text-[40px] font-extrabold text-purple-200">404</p>
        <p className="mt-3 text-[15px] text-gray-500">
          찾을 수 없는 페이지예요
        </p>
        <Button
          onClick={() => navigate("/", { replace: true })}
          className="mt-8"
        >
          홈으로
        </Button>
      </div>
    </MobileShell>
  );
}
