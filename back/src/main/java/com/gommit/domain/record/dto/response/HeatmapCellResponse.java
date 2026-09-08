package com.gommit.domain.record.dto.response;

// "개인 전체 통계" 요약 탭의 "누적 인증 잔디" 한 칸. 하루 단위 체크인 로그가 없어서
// 실제 GitHub 잔디처럼 일 단위는 못 만들고, 그 달의 평균 완주율을 0~4단계 진하기로
// 근사한다(month는 "yyyy-MM").
public record HeatmapCellResponse(String month, int level) {}
