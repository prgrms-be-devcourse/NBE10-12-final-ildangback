import { Link, useSearchParams } from "react-router";
import { TopBar } from "../../../shared/ui/TopBar";

export function GroupInvitePreviewPage() {
  const [params] = useSearchParams();
  const code = params.get("code");
  const valid = code !== null && /^[A-Z0-9]{6}$/.test(code);

  // Connect the read-only preview request here after the backend contract is
  // confirmed. Never call joinByCode on mount or use it to fetch preview data.
  // A future preview confirmation should call joinByCode({ inviteCode: code })
  // only on explicit participation, then navigate to the returned challengeId.
  return (
    <>
      <TopBar title="그룹 초대" />
      <main className="space-y-5 px-6 pt-10 pb-10">
        <h1 className="text-2xl font-bold">초대받은 그룹</h1>
        <p
          role="status"
          className="rounded-2xl bg-purple-50 p-5 text-sm leading-relaxed text-purple-700"
        >
          {valid
            ? "초대 그룹 미리보기를 준비 중이에요. 아직 그룹에 참여하지 않았어요."
            : "초대 링크가 올바르지 않아요. 영문 대문자와 숫자로 된 6자리 코드가 포함된 링크를 확인해주세요."}
        </p>
        <Link
          to="/challenges"
          className="block py-3 text-center text-sm text-purple-500"
        >
          내 그룹으로
        </Link>
      </main>
    </>
  );
}
