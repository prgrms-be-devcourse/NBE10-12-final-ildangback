import defaultImage from "../../../assets/icons/character_default.webp";
import gymSuccess from "../../../assets/icons/char_gym_success.webp";
import gymFail from "../../../assets/icons/char_gym_fail.webp";
import studySuccess from "../../../assets/icons/char_study_success.webp";
import studyFail from "../../../assets/icons/char_study_fail.webp";
import type { CharacterPose, CharacterSlots, ItemSlot } from "../types";

const baseImages: Record<CharacterPose, string> = {
  DEFAULT: defaultImage,
  GYM_SUCCESS: gymSuccess,
  GYM_FAIL: gymFail,
  STUDY_SUCCESS: studySuccess,
  STUDY_FAIL: studyFail,
};
const layers: ItemSlot[] = ["SHOES", "BOTTOM", "TOP", "HEAD"];

export function CharacterRenderer({
  pose,
  slots,
  label,
  className = "h-10 w-10",
}: {
  pose: CharacterPose;
  slots: CharacterSlots;
  label: string;
  className?: string;
}) {
  return (
    <span
      role="img"
      aria-label={label}
      className={`relative inline-block shrink-0 ${className}`}
    >
      <img
        src={baseImages[pose]}
        alt=""
        className="absolute inset-0 h-full w-full object-contain"
      />
      {layers.map((slot) =>
        slots[slot] ? (
          <img
            key={slot}
            src={slots[slot]}
            alt=""
            className="absolute inset-0 h-full w-full object-contain"
          />
        ) : null,
      )}
    </span>
  );
}
