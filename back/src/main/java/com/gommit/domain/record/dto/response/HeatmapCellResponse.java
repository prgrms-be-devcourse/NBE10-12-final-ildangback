package com.gommit.domain.record.dto.response;

// "누적 인증 잔디" 한 칸. 일 단위 로그가 없어서 그 달 평균 완주율을 0~4단계로 근사한다.
public record HeatmapCellResponse(String month, int level) {}
