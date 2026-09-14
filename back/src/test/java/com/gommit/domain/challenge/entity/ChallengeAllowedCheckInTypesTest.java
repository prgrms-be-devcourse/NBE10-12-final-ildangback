package com.gommit.domain.challenge.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.gommit.domain.checkin.entity.CheckInType;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// allowPhoto + allowVideo(둘 다 boolean, 같은 모양) 조합이 allowedCheckInTypes() 로 어떻게 합쳐지는지.
// allowVideo 기본값(false)은 이전 동작(allowPhoto 만)과 완전히 같아야 한다 — Challenge.builder()...
// allowPhoto(...) 만 쓰는 기존 테스트들이 이 필드를 몰라도 깨지지 않게 하는 계약.
@DisplayName("Challenge.allowedCheckInTypes")
class ChallengeAllowedCheckInTypesTest {

    private Challenge.ChallengeBuilder base() {
        return Challenge.builder()
                .groupId(1L)
                .seqNo(1)
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 30))
                .frequencyType(FrequencyType.DAILY)
                .dailyCheckInCount(1)
                .requiredDayCount(30)
                .groupCurrentStreak(0)
                .groupBestStreak(0);
    }

    @Test
    @DisplayName("allowVideo 없이 allowPhoto=true 면 PHOTO 만 (기존 동작)")
    void allowPhotoOnly() {
        Challenge challenge = base().allowPhoto(true).build();

        assertThat(challenge.allowedCheckInTypes()).containsExactly(CheckInType.PHOTO);
    }

    @Test
    @DisplayName("allowPhoto=false 이고 allowVideo=false 면 빈 목록 (기존 동작)")
    void nothingAllowed() {
        Challenge challenge = base().allowPhoto(false).build();

        assertThat(challenge.allowedCheckInTypes()).isEmpty();
    }

    @Test
    @DisplayName("allowVideo=true 면 PHOTO 와 함께 반환된다")
    void allowsVideoAlongsidePhoto() {
        Challenge challenge = base().allowPhoto(true).allowVideo(true).build();

        assertThat(challenge.allowedCheckInTypes()).containsExactly(CheckInType.PHOTO, CheckInType.VIDEO);
    }

    @Test
    @DisplayName("allowPhoto=false 여도 allowVideo 만으로 VIDEO 를 허용할 수 있다")
    void videoOnlyWithoutPhoto() {
        Challenge challenge = base().allowPhoto(false).allowVideo(true).build();

        assertThat(challenge.allowedCheckInTypes()).containsExactly(CheckInType.VIDEO);
    }
}
