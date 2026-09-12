package com.gommit.domain.background.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.gommit.domain.background.dto.response.GroupBackgroundResponse;
import com.gommit.domain.background.dto.response.ShopBackgroundResponse;
import com.gommit.domain.background.entity.Background;
import com.gommit.domain.background.entity.BackgroundPurchaseRequest;
import com.gommit.domain.background.entity.GroupBackground;
import com.gommit.domain.background.entity.GroupBackgroundStatus;
import com.gommit.domain.background.entity.PurchaseRequestStatus;
import com.gommit.domain.background.repository.BackgroundPurchaseRequestRepository;
import com.gommit.domain.background.repository.BackgroundRepository;
import com.gommit.domain.background.repository.GroupBackgroundRepository;
import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.entity.GroupCategory;
import com.gommit.domain.group.entity.GroupMember;
import com.gommit.domain.group.entity.MapType;
import com.gommit.domain.group.entity.Visibility;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.group.repository.GroupMemberRepository;
import com.gommit.domain.media.service.StorageService;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("BackgroundService")
class BackgroundServiceTest {

    private static final Long GROUP_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final Long MEMBER_ID = 11L;

    @Mock
    private BackgroundRepository backgroundRepository;

    @Mock
    private GroupBackgroundRepository groupBackgroundRepository;

    @Mock
    private BackgroundPurchaseRequestRepository purchaseRequestRepository;

    @Mock
    private ChallengeGroupRepository challengeGroupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private BackgroundService backgroundService;

    private ChallengeGroup group;

    @BeforeEach
    void setUp() {
        group = ChallengeGroup.builder()
                .name("운동 그룹")
                .description("설명")
                .category(GroupCategory.EXERCISE)
                .mapType(MapType.GYM)
                .visibility(Visibility.PUBLIC)
                .maxMembers(6)
                .ownerId(OWNER_ID)
                .build();
        ReflectionTestUtils.setField(group, "id", GROUP_ID);
    }

    private Background background(Long id, String name, int price) {
        Background background = Background.builder()
                .mapType(MapType.GYM)
                .name(name)
                .imageKey("backgrounds/" + name + ".png")
                .price(price)
                .build();
        ReflectionTestUtils.setField(background, "id", id);
        return background;
    }

    private GroupBackground owned(Background background, GroupBackgroundStatus status) {
        GroupBackground groupBackground = GroupBackground.builder()
                .groupId(GROUP_ID)
                .background(background)
                .build();
        if (status == GroupBackgroundStatus.ACTIVE) {
            groupBackground.activate();
        }
        return groupBackground;
    }

    private void mockActiveMember(Long userId) {
        GroupMember member = GroupMember.builder().group(group).userId(userId).build();
        when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, userId)).thenReturn(Optional.of(member));
    }

    private void mockGroupFound() {
        when(challengeGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
    }

    @Nested
    @DisplayName("getShopBackgrounds - 상점 조회")
    class GetShopBackgrounds {

        @Test
        @DisplayName("보유 여부와 적용 여부와 투표 여부를 각 배경에 표시한다")
        void marksOwnedActiveAndVotingFlags() {
            Background applied = background(100L, "헬스장", 100);
            Background justOwned = background(101L, "요가원", 200);
            Background onVote = background(102L, "복싱장", 300);
            Background plain = background(103L, "수영장", 400);

            mockGroupFound();
            mockActiveMember(MEMBER_ID);
            when(backgroundRepository.findByMapTypeAndIdGreaterThanOrderByIdAsc(
                            eq(MapType.GYM), eq(0L), any(Pageable.class)))
                    .thenReturn(List.of(applied, justOwned, onVote, plain));
            when(groupBackgroundRepository.findAllByGroupId(GROUP_ID))
                    .thenReturn(List.of(
                            owned(applied, GroupBackgroundStatus.ACTIVE),
                            owned(justOwned, GroupBackgroundStatus.INACTIVE)));

            BackgroundPurchaseRequest request = BackgroundPurchaseRequest.builder()
                    .groupId(GROUP_ID)
                    .background(onVote)
                    .requestedBy(MEMBER_ID)
                    .expiresAt(LocalDateTime.now().plusDays(3))
                    .build();
            when(purchaseRequestRepository.findByGroupIdAndStatus(GROUP_ID, PurchaseRequestStatus.VOTING))
                    .thenReturn(Optional.of(request));
            when(storageService.publicUrl(any())).thenReturn("https://cdn/x.png");

            SliceResponse<ShopBackgroundResponse> response =
                    backgroundService.getShopBackgrounds(GROUP_ID, MEMBER_ID, null, null, 20);

            assertThat(response.content()).hasSize(4);
            assertThat(response.content().get(0).owned()).isTrue();
            assertThat(response.content().get(0).active()).isTrue();
            assertThat(response.content().get(1).owned()).isTrue();
            assertThat(response.content().get(1).active()).isFalse();
            assertThat(response.content().get(2).voting()).isTrue();
            assertThat(response.content().get(3).owned()).isFalse();
            assertThat(response.content().get(3).voting()).isFalse();
        }

        @Test
        @DisplayName("투표 중인 제안이 없으면 어떤 배경도 투표 중으로 표시하지 않는다")
        void noVotingFlagWhenNoRequest() {
            Background only = background(100L, "헬스장", 100);

            mockGroupFound();
            mockActiveMember(MEMBER_ID);
            when(backgroundRepository.findByMapTypeAndIdGreaterThanOrderByIdAsc(
                            eq(MapType.GYM), eq(5L), any(Pageable.class)))
                    .thenReturn(List.of(only));
            when(groupBackgroundRepository.findAllByGroupId(GROUP_ID)).thenReturn(List.of());
            when(purchaseRequestRepository.findByGroupIdAndStatus(GROUP_ID, PurchaseRequestStatus.VOTING))
                    .thenReturn(Optional.empty());
            when(storageService.publicUrl(any())).thenReturn("https://cdn/x.png");

            SliceResponse<ShopBackgroundResponse> response =
                    backgroundService.getShopBackgrounds(GROUP_ID, MEMBER_ID, null, 5L, 20);

            assertThat(response.content().get(0).voting()).isFalse();
        }

        @Test
        @DisplayName("탈퇴한 멤버는 상점을 볼 수 없다")
        void leftMemberIsRejected() {
            GroupMember left =
                    GroupMember.builder().group(group).userId(MEMBER_ID).build();
            left.leave();

            mockGroupFound();
            when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, MEMBER_ID))
                    .thenReturn(Optional.of(left));

            assertThatThrownBy(() -> backgroundService.getShopBackgrounds(GROUP_ID, MEMBER_ID, null, null, 20))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_GROUP_MEMBER);
        }

        @Test
        @DisplayName("그룹이 없으면 GROUP_NOT_FOUND 다")
        void missingGroupIsRejected() {
            when(challengeGroupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> backgroundService.getShopBackgrounds(GROUP_ID, MEMBER_ID, null, null, 20))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getActiveBackground - 그룹 배경 조회")
    class GetActiveBackground {

        @Test
        @DisplayName("적용 중인 배경이 있으면 그 URL 을 준다")
        void returnsAppliedBackground() {
            Background applied = background(100L, "헬스장", 100);

            mockGroupFound();
            when(groupBackgroundRepository.findByGroupIdAndStatus(GROUP_ID, GroupBackgroundStatus.ACTIVE))
                    .thenReturn(Optional.of(owned(applied, GroupBackgroundStatus.ACTIVE)));
            when(storageService.publicUrl("backgrounds/헬스장.png")).thenReturn("https://cdn/헬스장.png");

            GroupBackgroundResponse response = backgroundService.getActiveBackground(GROUP_ID);

            assertThat(response.backgroundId()).isEqualTo(100L);
            assertThat(response.imageUrl()).isEqualTo("https://cdn/헬스장.png");
            assertThat(response.mapType()).isEqualTo(MapType.GYM);
        }

        @Test
        @DisplayName("적용 중인 배경이 없으면 mapType 만 준다")
        void fallsBackToMapType() {
            mockGroupFound();
            when(groupBackgroundRepository.findByGroupIdAndStatus(GROUP_ID, GroupBackgroundStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            GroupBackgroundResponse response = backgroundService.getActiveBackground(GROUP_ID);

            assertThat(response.backgroundId()).isNull();
            assertThat(response.imageUrl()).isNull();
            assertThat(response.mapType()).isEqualTo(MapType.GYM);
        }
    }

    @Nested
    @DisplayName("applyBackground - 그룹 배경 적용")
    class ApplyBackground {

        @Test
        @DisplayName("적용하면 이전 배경이 INACTIVE 가 되고 대상이 ACTIVE 가 된다")
        void swapsActiveBackground() {
            Background previous = background(100L, "헬스장", 100);
            Background target = background(101L, "요가원", 200);
            GroupBackground previousOwned = owned(previous, GroupBackgroundStatus.ACTIVE);
            GroupBackground targetOwned = owned(target, GroupBackgroundStatus.INACTIVE);

            mockGroupFound();
            mockActiveMember(OWNER_ID);
            when(groupBackgroundRepository.findByGroupIdAndBackgroundId(GROUP_ID, 101L))
                    .thenReturn(Optional.of(targetOwned));
            when(groupBackgroundRepository.findByGroupIdAndStatus(GROUP_ID, GroupBackgroundStatus.ACTIVE))
                    .thenReturn(Optional.of(previousOwned));
            when(storageService.publicUrl("backgrounds/요가원.png")).thenReturn("https://cdn/요가원.png");

            GroupBackgroundResponse response = backgroundService.applyBackground(GROUP_ID, OWNER_ID, 101L);

            assertThat(previousOwned.getStatus()).isEqualTo(GroupBackgroundStatus.INACTIVE);
            assertThat(targetOwned.getStatus()).isEqualTo(GroupBackgroundStatus.ACTIVE);
            assertThat(response.backgroundId()).isEqualTo(101L);
        }

        @Test
        @DisplayName("적용 중인 배경이 없어도 대상만 ACTIVE 로 바꾼다")
        void activatesWhenNothingApplied() {
            Background target = background(101L, "요가원", 200);
            GroupBackground targetOwned = owned(target, GroupBackgroundStatus.INACTIVE);

            mockGroupFound();
            mockActiveMember(OWNER_ID);
            when(groupBackgroundRepository.findByGroupIdAndBackgroundId(GROUP_ID, 101L))
                    .thenReturn(Optional.of(targetOwned));
            when(groupBackgroundRepository.findByGroupIdAndStatus(GROUP_ID, GroupBackgroundStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(storageService.publicUrl("backgrounds/요가원.png")).thenReturn("https://cdn/요가원.png");

            backgroundService.applyBackground(GROUP_ID, OWNER_ID, 101L);

            assertThat(targetOwned.getStatus()).isEqualTo(GroupBackgroundStatus.ACTIVE);
        }

        @Test
        @DisplayName("OWNER 가 아니면 ACCESS_DENIED 다")
        void nonOwnerIsRejected() {
            mockGroupFound();
            mockActiveMember(MEMBER_ID);

            assertThatThrownBy(() -> backgroundService.applyBackground(GROUP_ID, MEMBER_ID, 101L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        }

        @Test
        @DisplayName("보유하지 않은 배경은 BACKGROUND_NOT_OWNED 다")
        void unownedBackgroundIsRejected() {
            mockGroupFound();
            mockActiveMember(OWNER_ID);
            lenient()
                    .when(groupBackgroundRepository.findByGroupIdAndBackgroundId(GROUP_ID, 999L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> backgroundService.applyBackground(GROUP_ID, OWNER_ID, 999L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BACKGROUND_NOT_OWNED);
        }
    }
}
