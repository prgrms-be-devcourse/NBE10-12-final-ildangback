/**
 * 이 화면들(홈 · 개인 상점 · 개인 통계)에서만 쓰는 그림.
 *
 * 공용 `pixelIcons.ts` 에 올리지 않는 이유는 그 파일이 팀 공용 등록부라서다.
 * 여기 아이콘은 이 세 화면 밖에서 쓰이지 않으므로 따로 둔다.
 * 원본 webp 는 `assets/icons` · `assets/design` 에 그대로 있다.
 *
 * 시안 프레임에서 그대로 꺼낸 픽셀아트.
 *
 * Figma 의 `Copy as SVG` 결과에 원본이 base64 로 박혀 있어, pattern 변환이
 * 가리키는 영역만 잘라 냈다. 그래서 크기와 여백이 시안과 같다.
 */
import statLongestStreak from "../../assets/icons/longest-streak-flame.webp";
import statMonthlySuccess from "../../assets/icons/monthly-success-target.webp";
import statTotalVerifications from "../../assets/icons/total-verifications-trophy.webp";
import bestCategory from "../../assets/design/best-category.webp";
import categoryDev from "../../assets/design/category-dev.webp";
import categoryExercise from "../../assets/design/category-exercise.webp";
import categoryReading from "../../assets/design/category-reading.webp";
import characterHome from "../../assets/design/character-home.webp";
import notificationBell from "../../assets/design/notification-bell.webp";
import shopCharacter from "../../assets/design/shop-character.webp";
import statMonthly from "../../assets/design/stat-monthly.webp";
import statRate from "../../assets/design/stat-rate.webp";
import statStreak from "../../assets/design/stat-streak.webp";
import shopRoom from "../../assets/design/shop-room.webp";
import worstCategory from "../../assets/design/worst-category.webp";

export const designArt = {
  // 개인 통계 지표 타일. 통계 화면은 시안이 픽셀아트를 쓴다.
  statTotalVerifications,
  statLongestStreak,
  statMonthlySuccess,
  characterHome,
  notificationBell,
  statStreak,
  statMonthly,
  statRate,
  shopRoom,
  shopCharacter,
  categoryExercise,
  categoryDev,
  categoryReading,
  bestCategory,
  worstCategory,
} as const;
