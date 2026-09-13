import { CaretRightIcon } from "@phosphor-icons/react";
import { useNavigate } from "react-router";
import { formatDate } from "../../../shared/lib/date";
import { useAuth } from "../../../shared/lib/useAuth";
import { pixelIcons } from "../../../shared/ui/pixelIcons";
import { PixelIcon } from "../../../shared/ui/PixelIcon";
import { TopBar } from "../../../shared/ui/TopBar";
import { EmailVerificationNotice } from "../../auth/components/EmailVerificationNotice";

/**
 * 설정 > 계정 관리. 비밀번호 변경과 탈퇴가 여기 모인다.
 * 이 화면은 시안이 없어서 설정 화면의 행 모양을 그대로 따랐다.
 */
export function AccountPage() {
  const { user } = useAuth();
  const navigate = useNavigate();

  return (
    <>
      <TopBar title="계정 관리" />

      <div className="px-5 pb-10">
        <section className="mt-3 flex items-center gap-3 rounded-2xl bg-purple-50 px-5 py-4">
          <PixelIcon src={pixelIcons.accountManagement} size={34} />
          <div>
            <p className="text-[16px] font-bold text-gray-900">
              {user?.nickname}
            </p>
            <p className="mt-0.5 text-[13px] text-gray-500">{user?.email}</p>
            {user && (
              <p className="mt-2 text-[12px] text-gray-500">
                {formatDate(user.createdAt)}에 가입
              </p>
            )}
          </div>
        </section>

        <EmailVerificationNotice />

        <nav className="mt-5 overflow-hidden rounded-2xl bg-purple-50 [&>*+*]:border-t [&>*+*]:border-purple-100">
          {/* 비밀번호가 없는 계정에는 바꿀 것이 없다. 눌러도 서버가 401 이다. */}
          {user?.hasPassword && (
            <button
              type="button"
              onClick={() => navigate("/profile/password")}
              className="flex w-full items-center gap-3 px-4 py-3 text-left"
            >
              <PixelIcon src={pixelIcons.privacyLock} />
              <span className="flex-1 text-[14px] text-gray-900">
                비밀번호 변경
              </span>
              <CaretRightIcon size={16} className="text-gray-500" aria-hidden />
            </button>
          )}

          <button
            type="button"
            onClick={() => navigate("/profile/delete")}
            className="flex w-full items-center gap-3 px-4 py-3 text-left"
          >
            <PixelIcon src={pixelIcons.logout} />
            <span className="flex-1 text-[14px] text-red-600">회원 탈퇴</span>
            <CaretRightIcon size={16} className="text-gray-500" aria-hidden />
          </button>
        </nav>
      </div>
    </>
  );
}
