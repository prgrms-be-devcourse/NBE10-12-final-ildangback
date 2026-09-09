package com.gommit.domain.profile.service;

import com.gommit.domain.item.dto.response.CharacterResponse;
import com.gommit.domain.item.service.UserItemService;
import com.gommit.domain.profile.dto.response.ProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {
    private final UserItemService userItemService;

    // 프로필 메인 조회
    public ProfileResponse getMyProfile(Long userId) {
        CharacterResponse character = userItemService.getMyCharacter(userId, null);
        return new ProfileResponse(character);
    }
}
