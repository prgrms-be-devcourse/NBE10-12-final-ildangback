package com.gommit.domain.record.dto.response;

import java.util.List;

// "개인 전체 통계" 화면(GET /users/me/stats) 응답. "패턴" 탭은 체크인 원본 데이터가
// 있어야 해서 CheckIn 도메인 연동 후 별도로 추가한다.
public record PersonalStatsResponse(
        SummaryStatResponse summary,
        List<MonthlyTrendItemResponse> monthlyTrend,
        List<CategoryStatResponse> categoryBreakdown,
        List<HeatmapCellResponse> heatmap) {}
