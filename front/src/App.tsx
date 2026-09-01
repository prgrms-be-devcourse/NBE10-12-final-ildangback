import { Route, Routes } from "react-router";
import { PlainLayout } from "./app/PlainLayout";
import { RequireAuth } from "./app/RequireAuth";
import { TabLayout } from "./app/TabLayout";
import { LoginPage } from "./domains/auth/pages/LoginPage";
import { SignUpPage } from "./domains/auth/pages/SignUpPage";
import { CheckInPage } from "./domains/checkin/pages/CheckInPage";
import { AccountPage } from "./domains/user/pages/AccountPage";
import { ChangePasswordPage } from "./domains/user/pages/ChangePasswordPage";
import { DeleteAccountPage } from "./domains/user/pages/DeleteAccountPage";
import { EditProfilePage } from "./domains/user/pages/EditProfilePage";
import { ProfilePage } from "./domains/user/pages/ProfilePage";
import { SettingsPage } from "./domains/user/pages/SettingsPage";
import { ChallengeTabPlaceholder } from "./pages/ChallengeTabPlaceholder";
import { HomePage } from "./pages/HomePage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { StubChallengeDetailPage } from "./pages/StubChallengeDetailPage";
import { useAuth } from "./shared/lib/useAuth";
import { LoadingScreen } from "./shared/ui/LoadingScreen";

export function App() {
  const { status } = useAuth();

  // RT 로 내 정보를 복구하는 동안. 이걸 안 막으면 로그인 상태인데도 한 프레임 동안
  // 비로그인 화면(가입 유도)이 번쩍인다.
  if (status === "loading") return <LoadingScreen />;

  return (
    <Routes>
      {/* 하단바가 붙는 탭 3개. 비로그인도 들어올 수 있고 안에서 빈 상태가 뜬다. */}
      <Route element={<TabLayout />}>
        <Route index element={<HomePage />} />
        <Route path="challenges" element={<ChallengeTabPlaceholder />} />
        <Route path="profile" element={<ProfilePage />} />
      </Route>

      <Route element={<PlainLayout />}>
        <Route path="login" element={<LoginPage />} />
        <Route path="signup" element={<SignUpPage />} />

        {/* 로그인해야만 열리는 화면들 */}
        <Route element={<RequireAuth />}>
          <Route path="profile/settings" element={<SettingsPage />} />
          <Route path="profile/account" element={<AccountPage />} />
          <Route path="profile/edit" element={<EditProfilePage />} />
          <Route path="profile/password" element={<ChangePasswordPage />} />
          <Route path="profile/delete" element={<DeleteAccountPage />} />

          {/* 체크인 플로우. challenges/:challengeId 스텁 상세는 실제 챌린지 상세 화면이
              들어오면 교체된다 (StubChallengeDetailPage 주석 참고). */}
          <Route
            path="challenges/:challengeId"
            element={<StubChallengeDetailPage />}
          />
          <Route
            path="challenges/:challengeId/check-in"
            element={<CheckInPage />}
          />
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
