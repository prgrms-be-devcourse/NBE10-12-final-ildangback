package com.gommit.domain.checkin.dto.response;

import com.gommit.global.dto.SliceResponse;
import java.util.List;

// 공통 SliceResponse 의 커서 페이지 형태(content/hasNext/nextCursor)에 프로필 헤더용 totalCount 만 얹은 응답.
// totalCount 는 challengeId·checkInType 필터는 반영하되 month 는 무시한다(월을 바꿔도 헤더 수치가 흔들리지 않게).
public record MyCheckInSliceResponse(
        List<MyCheckInResponse> content, boolean hasNext, Long nextCursor, long totalCount) {

    public static MyCheckInSliceResponse of(SliceResponse<MyCheckInResponse> page, long totalCount) {
        return new MyCheckInSliceResponse(page.content(), page.hasNext(), page.nextCursor(), totalCount);
    }
}
