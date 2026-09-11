package com.gommit.domain.record.dto.response;

// 머지 목록 조회 시 항목이 월간/최종 중 어느 쪽인지 구분하는 표시용 값.
// DB에 저장되는 값이 아니라 MonthlyMerge/FinalMerge 중 어느 테이블에서 왔는지를 응답에 표시하기 위함.
public enum MergeType {
    MONTHLY,
    FINAL
}
