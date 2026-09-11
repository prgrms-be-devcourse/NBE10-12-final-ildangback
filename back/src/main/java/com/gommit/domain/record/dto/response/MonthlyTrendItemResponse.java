package com.gommit.domain.record.dto.response;

// "개인 전체 통계" 월별 탭의 막대/꺾은선 그래프용 1개월치 데이터. month는 "yyyy-MM"
// (달력 월 기준으로 묶은 것 - 챌린지의 30일 롤링 주기가 아니다).
public record MonthlyTrendItemResponse(String month, int checkInCount, int completionRate) {}
