import { Route, Routes } from "react-router";
import { PlainLayout } from "./app/PlainLayout";
import { RequireAuth } from "./app/RequireAuth";
import { RequireAdmin } from "./domains/admin/RequireAdmin";
import { AdminHomePage } from "./domains/admin/pages/AdminHomePage";
import { ItemAdminPage } from "./domains/admin/pages/ItemAdminPage";
import { TabLayout } from "./app/TabLayout";
import { ForgotPasswordPage } from "./domains/auth/pages/ForgotPasswordPage";
import { LoginPage } from "./domains/auth/pages/LoginPage";
import { OAuthCallbackPage } from "./domains/auth/pages/OAuthCallbackPage";
import { ResetPasswordPage } from "./domains/auth/pages/ResetPasswordPage";
import { SignUpPage } from "./domains/auth/pages/SignUpPage";
import { SocialOnboardingPage } from "./domains/auth/pages/SocialOnboardingPage";
import { VerifyResultPage } from "./domains/auth/pages/VerifyResultPage";
import { CheckInPage } from "./domains/checkin/pages/CheckInPage";
import { MyChallengeAlbumPage } from "./domains/checkin/pages/MyChallengeAlbumPage";
import { MyCheckInsPage } from "./domains/checkin/pages/MyCheckInsPage";
import { ParticipatedChallengesPage } from "./domains/checkin/pages/ParticipatedChallengesPage";
import { AccountPage } from "./domains/user/pages/AccountPage";
import { CharacterShopPage } from "./domains/item/pages/CharacterShopPage";
import { ChangePasswordPage } from "./domains/user/pages/ChangePasswordPage";
import { DeleteAccountPage } from "./domains/user/pages/DeleteAccountPage";
import { EditProfilePage } from "./domains/user/pages/EditProfilePage";
import { PointHistoryDetailPage } from "./domains/point/pages/PointHistoryDetailPage";
import { PersonalStatsPage } from "./domains/record/pages/PersonalStatsPage";
import { PointHistoryPage } from "./domains/point/pages/PointHistoryPage";
import { FinalMergeResultPage } from "./domains/record/pages/FinalMergeResultPage";
import { MergeListPage } from "./domains/record/pages/MergeListPage";
import { MonthlyMergeResultPage } from "./domains/record/pages/MonthlyMergeResultPage";
import { MyMonthlyMergeArchivePage } from "./domains/record/pages/MyMonthlyMergeArchivePage";
import { ProfilePage } from "./domains/user/pages/ProfilePage";
import { SettingsPage } from "./domains/user/pages/SettingsPage";
import { GroupHomePage } from "./domains/group/pages/GroupHomePage";
import { GroupPreviewPage } from "./domains/group/pages/GroupPreviewPage";
import { GroupCreatePage } from "./domains/group/pages/GroupCreatePage";
import { GroupJoinByCodePage } from "./domains/group/pages/GroupJoinByCodePage";
import { GroupInvitePreviewPage } from "./domains/group/pages/GroupInvitePreviewPage";
import { ChallengeStatusPage } from "./domains/challenge/pages/ChallengeStatusPage";
import { HomePage } from "./pages/HomePage";
import { NotFoundPage } from "./pages/NotFoundPage";
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
        <Route path="challenges" element={<GroupHomePage />} />
        <Route path="challenges/join" element={<GroupInvitePreviewPage />} />
        <Route element={<RequireAuth />}>
          <Route path="join-by-code" element={<GroupJoinByCodePage />} />
          <Route
            path="challenges/groups/join"
            element={<GroupJoinByCodePage />}
          />
          <Route path="challenges/groups/new" element={<GroupCreatePage />} />
          <Route
            path="challenges/groups/:groupId"
            element={<GroupPreviewPage />}
          />
          <Route
            path="challenges/:challengeId"
            element={<ChallengeStatusPage />}
          />
          <Route
            path="profile/challenges"
            element={<ParticipatedChallengesPage />}
          />
          <Route path="profile/check-ins" element={<MyCheckInsPage />} />
          <Route
            path="profile/challenges/:challengeId/album"
            element={<MyChallengeAlbumPage />}
          />
        </Route>
        <Route path="profile" element={<ProfilePage />} />

        {/* 뒤로가기가 있어도 하단바가 남는 화면들 (시안). */}
        <Route element={<RequireAuth />}>
          <Route path="profile/stats" element={<PersonalStatsPage />} />
          <Route path="profile/shop" element={<CharacterShopPage />} />
        </Route>
      </Route>

      <Route element={<PlainLayout />}>
        <Route path="login" element={<LoginPage />} />
        <Route path="signup" element={<SignUpPage />} />

        {/*
          경로가 백엔드에 박혀 있다. verify-result 는 EmailVerificationService 의 302 목적지,
          reset-password 는 재설정 메일 링크, oauth 콜백은 oauth.allowed-redirect-uris 다.
          바꾸려면 백엔드도 같이 고쳐야 한다.
        */}
        <Route path="verify-result" element={<VerifyResultPage />} />
        <Route path="reset-password" element={<ResetPasswordPage />} />
        <Route path="forgot-password" element={<ForgotPasswordPage />} />
        <Route
          path="oauth/:provider/callback"
          element={<OAuthCallbackPage />}
        />

        {/* 로그인해야만 열리는 화면들 */}
        <Route element={<RequireAuth />}>
          <Route path="welcome" element={<SocialOnboardingPage />} />
          <Route path="profile/settings" element={<SettingsPage />} />
          <Route path="profile/account" element={<AccountPage />} />
          <Route path="profile/edit" element={<EditProfilePage />} />
          <Route path="profile/password" element={<ChangePasswordPage />} />
          <Route path="profile/delete" element={<DeleteAccountPage />} />
          <Route path="profile/points" element={<PointHistoryPage />} />
          <Route
            path="profile/points/:historyId"
            element={<PointHistoryDetailPage />}
          />
          <Route
            path="profile/monthly-merges"
            element={<MyMonthlyMergeArchivePage />}
          />
          <Route
            path="challenges/:challengeId/merges"
            element={<MergeListPage />}
          />
          <Route
            path="challenges/:challengeId/monthly-merges/:seqNo"
            element={<MonthlyMergeResultPage />}
          />
          <Route
            path="challenges/:challengeId/final-merge"
            element={<FinalMergeResultPage />}
          />

          {/* 체크인 제출 플로우 — 카메라 중심의 집중 화면이라 하단바 없이 둔다. */}
          <Route
            path="challenges/:challengeId/check-in"
            element={<CheckInPage />}
          />

          {/* 관리자. 앱 어디에도 링크가 없고 /admin 을 직접 입력해 들어온다.
              가드는 화면을 안 그리는 것뿐이고 차단은 서버가 한다 (RequireAdmin 주석). */}
          <Route path="admin" element={<RequireAdmin />}>
            <Route index element={<AdminHomePage />} />
            <Route path="items" element={<ItemAdminPage />} />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
