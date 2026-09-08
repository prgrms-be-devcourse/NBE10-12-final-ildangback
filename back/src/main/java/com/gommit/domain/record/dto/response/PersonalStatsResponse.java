package com.gommit.domain.record.dto.response;

import java.util.List;

// "개인 전체 통계" 화면(GET /users/me/stats) 응답. 전부 이미 발행된 머지 결과(스냅샷)를
// 집계한 값이라 CheckIn 도메인 없이도 계산할 수 있다. "패턴"(요일별/시간대별 인증 패턴)
// 탭은 체크인 원본 날짜/시간이 있어야 해서 여기 포함하지 않는다 - CheckIn 도메인의
// 로그 조회 API가 생기면 별도 엔드포인트로 추가한다.
public record PersonalStatsResponse(
        SummaryStatResponse summary,
        List<MonthlyTrendItemResponse> monthlyTrend,
        List<CategoryStatResponse> categoryBreakdown,
        List<HeatmapCellResponse> heatmap) {}
