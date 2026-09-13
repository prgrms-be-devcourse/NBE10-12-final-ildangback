package com.gommit.global.security;

import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.security.Principal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

public final class StompPrincipals {

    private StompPrincipals() {}

    // 인증된 사용자 번호 추출
    public static Long resolveUserId(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken authentication
                && authentication.getPrincipal() instanceof SecurityUser user) {
            return user.getId();
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
