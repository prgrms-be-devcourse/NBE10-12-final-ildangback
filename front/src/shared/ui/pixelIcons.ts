/**
 * 디자인 산출물에서 가져온 픽셀아트 아이콘.
 *
 * **지금 화면에 쓰는 것만 여기 등록한다.** 여기 올리면 쓰지 않아도 번들에 들어간다
 * (객체 프로퍼티는 트리셰이킹이 안 된다). 파일은 24개 전부 assets/icons 에 있으니
 * 통계 · 업적 화면을 만들 때 그때 추가한다 — 아직 안 쓰는 것:
 * total-verifications-trophy · longest-streak-flame · monthly-success-target ·
 * joined-challenges-calendar · verification-visibility-eye
 *
 * 주의: 원본 폴더의 이름이 그림과 다른 것이 하나 있었다.
 * `23_notification_bell_2` 는 종이 아니라 말풍선이라 chatNotification 으로 뒀다.
 */
import accountManagement from "../../assets/icons/account-management.webp";
import appInformation from "../../assets/icons/app-information.webp";
import appVersion from "../../assets/icons/app-version-cube.webp";
import chatNotification from "../../assets/icons/chat-notification-bubble.webp";
import badgeAchievement from "../../assets/icons/badge-achievement.webp";
import characterWink from "../../assets/icons/character-wink.webp";
import logout from "../../assets/icons/logout.webp";
import monthlyMergeArchive from "../../assets/icons/monthly-merge-archive.webp";
import monthlyMergeNotification from "../../assets/icons/monthly-merge-notification-calendar.webp";
import notificationBell from "../../assets/icons/notification-bell.webp";
import personalStats from "../../assets/icons/personal-stats-chart.webp";
import pointHistory from "../../assets/icons/point-history-coin.webp";
import pokeNotification from "../../assets/icons/poke-notification-hand.webp";
import privacyLock from "../../assets/icons/privacy-lock.webp";
import privacyPolicy from "../../assets/icons/privacy-policy-shield.webp";
import profileEdit from "../../assets/icons/profile-edit-pencil.webp";
import profileVisibility from "../../assets/icons/profile-visibility-users.webp";
import settingsGear from "../../assets/icons/settings-gear.webp";
import termsDocument from "../../assets/icons/terms-document.webp";
import verificationNotification from "../../assets/icons/verification-notification-checkbox.webp";

export const pixelIcons = {
  accountManagement,
  appInformation,
  appVersion,
  chatNotification,
  badgeAchievement,
  characterWink,
  logout,
  monthlyMergeArchive,
  monthlyMergeNotification,
  notificationBell,
  personalStats,
  pointHistory,
  pokeNotification,
  privacyLock,
  privacyPolicy,
  profileEdit,
  profileVisibility,
  settingsGear,
  termsDocument,
  verificationNotification,
} as const;
