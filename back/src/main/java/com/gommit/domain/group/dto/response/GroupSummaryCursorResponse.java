package com.gommit.domain.group.dto.response;

import java.util.List;

public record GroupSummaryCursorResponse(List<GroupSummaryResponse> content, CursorMetaResponse meta) {}
