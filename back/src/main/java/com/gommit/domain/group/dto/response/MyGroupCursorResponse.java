package com.gommit.domain.group.dto.response;

import java.util.List;

public record MyGroupCursorResponse(
    List<MyGroupSummaryResponse> content,
    CursorMetaResponse meta
) {
}
