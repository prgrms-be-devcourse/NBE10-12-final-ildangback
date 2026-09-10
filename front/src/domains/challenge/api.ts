import { apiFetch } from "../../shared/api/client";
import type {
  ChallengeStatusResponse,
  ChallengeCharacterResponse,
  MemberTodayStatusResponse,
  ChallengeUpdateRequest,
  ChallengeUpdateResponse,
  OwnerDelegationRequest,
  OwnerDelegationResponse,
  ExtensionChoiceRequest,
  ExtensionChoiceResponse,
} from "./types";

export function getChallenge(id: number) {
  return apiFetch<ChallengeStatusResponse>(`/api/challenges/${id}`);
}
export function getChallengeMembers(id: number) {
  return apiFetch<MemberTodayStatusResponse[]>(`/api/challenges/${id}/members`);
}
export function updateChallenge(id: number, body: ChallengeUpdateRequest) {
  return apiFetch<ChallengeUpdateResponse>(`/api/challenges/${id}`, {
    method: "PATCH",
    body,
  });
}
export function delegateOwner(id: number, body: OwnerDelegationRequest) {
  return apiFetch<OwnerDelegationResponse>(`/api/challenges/${id}/owner`, {
    method: "PATCH",
    body,
  });
}
export function chooseExtension(id: number, body: ExtensionChoiceRequest) {
  return apiFetch<ExtensionChoiceResponse>(
    `/api/challenges/${id}/extension/choice`,
    { method: "PUT", body },
  );
}

export function getChallengeCharacters(id: number) {
  return apiFetch<ChallengeCharacterResponse[]>(
    `/api/challenges/${id}/characters`,
  );
}
