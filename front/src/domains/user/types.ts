export type ItemSlot = "HEAD" | "TOP" | "BOTTOM" | "SHOES";
export type CharacterSlots = Record<ItemSlot, string | null>;
export type CharacterPose =
  "DEFAULT" | "GYM_SUCCESS" | "GYM_FAIL" | "STUDY_SUCCESS" | "STUDY_FAIL";

// JSON object keys are user IDs encoded as strings, not group member IDs.
export type UserCharactersResponse = Record<string, CharacterSlots>;
