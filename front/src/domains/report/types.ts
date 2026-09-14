/** 신고 대상. 시스템 메시지는 보낸 사람이 없어 서버가 404 로 막는다. */
export type ReportTargetType = "USER" | "CHECK_IN" | "CHAT_MESSAGE";

export type ReportReason = "ABUSE" | "SPAM" | "SEXUAL" | "FAKE" | "ETC";

export type ReportStatus = "PENDING" | "ACCEPTED" | "REJECTED";

export type PenaltyType =
  "WARNING" | "SUSPENSION" | "PERMANENT_BAN" | "POINT_FORFEIT";

/** 신고(ReportStatus)와 같은 말을 같은 단어로 쓴다. ACCEPTED 면 제재가 전부 해제된다. */
export type AppealStatus = "PENDING" | "ACCEPTED" | "REJECTED";

export interface SubmitReportRequest {
  targetType: ReportTargetType;
  targetId: number;
  reason: ReportReason;
  /** ETC 면 필수. 나머지 사유에서는 생략할 수 있다. */
  detail?: string | null;
}

/** 접수 응답. 목록 행과 달리 얇다 — 접수됐다는 사실만 알려준다. */
export interface ReportResponse {
  id: number;
  targetType: ReportTargetType;
  targetId: number;
  reason: ReportReason;
  status: ReportStatus;
  createdAt: string;
}

export interface PenaltyCommand {
  penaltyType: PenaltyType;
  /** SUSPENSION 만 쓴다. 1 이상 365 이하. */
  suspensionDays?: number | null;
  /** POINT_FORFEIT 만 쓴다. 1 이상. */
  amount?: number | null;
}

export interface PenaltyResponse {
  id: number;
  penaltyType: PenaltyType;
  /** 기간 정지만 채워진다. 영구 정지는 null 이다. */
  endsAt: string | null;
  /** 포인트 압수만 채워진다. */
  amount: number | null;
  /** 이의제기가 인용되면 채워진다. */
  revokedAt: string | null;
}

export interface ReportDetailResponse {
  id: number;
  reporterId: number;
  targetType: ReportTargetType;
  targetId: number;
  /** 제재를 받을 사람. 접수 시점에 확정된다. */
  targetUserId: number;
  /** 탈퇴했으면 null 이다. */
  targetUserNickname: string | null;
  reporterNickname: string | null;
  reason: ReportReason;
  /** 접수 시점 원본 스냅샷. 대상이 사라져도 이것으로 판정한다. */
  reportedContent: string | null;
  detail: string | null;
  status: ReportStatus;
  decidedBy: number | null;
  decidedAt: string | null;
  createdAt: string;
  /** 해제되지 않은 제재 건수. 운영 기준표의 차수를 고를 때 본다. */
  pastPenaltyCount: number;
  /** 목록 조회에서는 항상 빈 배열이고 판정 응답에서만 채워진다. */
  penalties: PenaltyResponse[];
}

export interface DecideReportRequest {
  accept: boolean;
  /** 승인이면 하나 이상 필요하다. 기각이면 무시된다. */
  penalties?: PenaltyCommand[];
}

export interface DirectPenaltyRequest {
  /** 운영자가 아는 값은 식별자가 아니라 이메일이다. */
  email: string;
  detail: string;
  penalties: PenaltyCommand[];
}

/** 내가 받은 제재 한 건. 판정 단위라 제재가 여러 개 묶여 온다. */
export interface MyPenaltyResponse {
  reportId: number;
  reason: ReportReason;
  /** 관리자가 남긴 사유. 신고 없이 건 제재는 반드시 있다. */
  detail: string | null;
  decidedAt: string | null;
  penalties: PenaltyResponse[];
  /** 아직 안 냈으면 null 이다. */
  appeal: AppealResponse | null;
  /** 이의제기를 낼 수 있는가. 신고 1건당 한 번이다. */
  appealable: boolean;
}

export interface SubmitAppealRequest {
  content: string;
}

export interface AppealResponse {
  id: number;
  reportId: number;
  content: string;
  status: AppealStatus;
  createdAt: string;
}

export interface AppealDetailResponse {
  id: number;
  reportId: number;
  appellantId: number;
  content: string;
  status: AppealStatus;
  decidedBy: number | null;
  decidedAt: string | null;
  createdAt: string;
  report: ReportDetailResponse;
}

export interface DecideAppealRequest {
  /** true 면 인용이고 그 신고에 달린 제재가 전부 해제된다. */
  accept: boolean;
}

export const REASON_LABEL: Record<ReportReason, string> = {
  ABUSE: "욕설, 비방, 괴롭힘",
  SPAM: "도배, 광고",
  SEXUAL: "선정성, 혐오 표현",
  FAKE: "허위 인증",
  ETC: "기타",
};

export const TARGET_LABEL: Record<ReportTargetType, string> = {
  USER: "사용자",
  CHECK_IN: "인증",
  CHAT_MESSAGE: "채팅",
};

export const STATUS_LABEL: Record<ReportStatus, string> = {
  PENDING: "대기",
  ACCEPTED: "승인",
  REJECTED: "기각",
};

export const PENALTY_LABEL: Record<PenaltyType, string> = {
  WARNING: "경고",
  SUSPENSION: "기간 정지",
  PERMANENT_BAN: "영구 정지",
  POINT_FORFEIT: "포인트 압수",
};

export const APPEAL_STATUS_LABEL: Record<AppealStatus, string> = {
  PENDING: "대기",
  ACCEPTED: "인용",
  REJECTED: "기각",
};
