package com.gommit.domain.checkin.dto.request;

import com.gommit.domain.checkin.entity.CheckInType;

// multipart/form-data 의 파일 외 필드. media(MultipartFile)는 컨트롤러에서 @RequestPart 로 따로 받는다.
// 메모 길이 등 검증은 CheckInService 가 api-spec 의 에러 코드로 수행한다.
public record SubmitCheckInForm(CheckInType checkInType, String memo) {}
