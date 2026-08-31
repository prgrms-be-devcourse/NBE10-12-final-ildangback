import { CaretRightIcon } from "@phosphor-icons/react";
import { useState } from "react";
import { useNavigate } from "react-router";
import { useAuth } from "../../../shared/lib/useAuth";
import { pixelIcons } from "../../../shared/ui/pixelIcons";
import { PixelIcon } from "../../../shared/ui/PixelIcon";
import { TermsModal } from "../../../shared/ui/TermsModal";
import { useToast } from "../../../shared/lib/useToast";
import { Toggle } from "../../../shared/ui/Toggle";
import { TopBar } from "../../../shared/ui/TopBar";
import { TERMS_DOCUMENTS } from "../../auth/components/terms";

/**
 * 알림 토글은 아직 저장할 곳이 없다 — users 테이블에 컬럼이 없고 알림 API 도 없다.
 * 지금은 화면만 있고 값은 새로고침하면 사라진다. 알림 기능이 들어올 때 연결한다.
 */
const NOTIFICATIONS = [
  {
    key: "checkin",
    icon: pixelIcons.verificationNotification,
    label: "인증 알림",
    initial: true,
  },
  {
    key: "poke",
    icon: pixelIcons.pokeNotification,
    label: "콕 찌르기 알림",
    initial: true,
  },
  {
    key: "chat",
    icon: pixelIcons.chatNotification,
    label: "채팅 알림",
    initial: true,
  },
  {
    key: "merge",
    icon: pixelIcons.monthlyMergeNotification,
    label: "월간 머지 알림",
    initial: false,
  },
];

export function SettingsPage() {
  const { signOut } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const [notifications, setNotifications] = useState(() =>
    Object.fromEntries(NOTIFICATIONS.map((n) => [n.key, n.initial])),
  );
  const [modalDoc, setModalDoc] = useState<{
    title: string;
    content: string;
  } | null>(null);

  const handleSignOut = async () => {
    await signOut();
    navigate("/", { replace: true });
  };

  return (
    <>
      <TopBar title="설정" />

      <div className="px-5 pb-10">
        <Section icon={pixelIcons.profileVisibility} title="계정">
          <Row
            icon={pixelIcons.profileEdit}
            label="프로필 정보 수정"
            onClick={() => navigate("/profile/edit")}
          />
          <Row
            icon={pixelIcons.privacyLock}
            label="계정 관리"
            onClick={() => navigate("/profile/account")}
          />
          <Row
            icon={pixelIcons.logout}
            label="로그아웃"
            danger
            onClick={handleSignOut}
          />
        </Section>

        <Section icon={pixelIcons.notificationBell} title="알림">
          {NOTIFICATIONS.map(({ key, icon, label }) => (
            <div key={key} className="flex items-center gap-3 px-4 py-3">
              <PixelIcon src={icon} size={30} />
              <span className="flex-1 text-[15px] text-purple-700">
                {label}
              </span>
              <Toggle
                label={label}
                checked={notifications[key]}
                onChange={(next) =>
                  setNotifications((prev) => ({ ...prev, [key]: next }))
                }
              />
            </div>
          ))}
        </Section>

        <Section icon={pixelIcons.appInformation} title="앱 정보">
          <Row
            icon={pixelIcons.termsDocument}
            label="이용 약관"
            onClick={() => setModalDoc(TERMS_DOCUMENTS.service)}
          />
          <Row
            icon={pixelIcons.privacyPolicy}
            label="개인정보 처리방침"
            onClick={() => setModalDoc(TERMS_DOCUMENTS.privacy)}
          />
          {/*
            버전은 vite.config.ts 가 package.json 에서 넣어 준다 (__APP_VERSION__).
            "최신 버전입니다" 는 쓰지 않는다 — 최신인지 확인할 API 가 없다.
          */}
          <Row
            icon={pixelIcons.appVersion}
            label="버전"
            onClick={() => showToast(`꼬밋 v${__APP_VERSION__}`)}
          />
        </Section>
      </div>

      <TermsModal
        isOpen={Boolean(modalDoc)}
        onClose={() => setModalDoc(null)}
        title={modalDoc?.title ?? ""}
        content={modalDoc?.content ?? ""}
      />
    </>
  );
}

function Section({
  icon,
  title,
  children,
}: {
  icon: string;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <section className="mt-6 first:mt-3">
      <h2 className="mb-2.5 flex items-center gap-2.5 text-[16px] font-bold text-gray-900">
        <PixelIcon src={icon} size={32} />
        {title}
      </h2>
      <div className="overflow-hidden rounded-2xl bg-purple-50 [&>*+*]:border-t [&>*+*]:border-purple-100">
        {children}
      </div>
    </section>
  );
}

function Row({
  icon,
  label,
  onClick,
  danger = false,
}: {
  icon: string;
  label: string;
  onClick(): void;
  danger?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-purple-100/50"
    >
      <PixelIcon src={icon} size={30} />
      <span
        className={`flex-1 text-[15px] ${danger ? "text-red-600 font-medium" : "text-purple-700 font-medium"}`}
      >
        {label}
      </span>
      <CaretRightIcon size={16} className="text-gray-500" aria-hidden />
    </button>
  );
}
