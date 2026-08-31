package com.gommit.domain.user.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다.")
        @Pattern(regexp = "^[가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z0-9]+( [가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z0-9]+)*$", message = "닉네임은 한글·영문·숫자만 쓸 수 있습니다.")
        String nickname,

        @Size(max = 255, message = "한줄소개는 255자를 넘을 수 없습니다.") String introduction) {

    public boolean hasNoField() {
        return nickname == null && introduction == null;
    }
}
