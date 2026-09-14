package com.gommit.domain.checkin.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gommit.domain.checkin.entity.MediaType;
import com.gommit.domain.checkin.support.CheckInVideoTranscoder;
import com.gommit.domain.checkin.support.CheckInVideoTranscoder.Transcoded;
import com.gommit.domain.media.dto.StorageResult;
import com.gommit.domain.media.entity.MediaRole;
import com.gommit.domain.media.service.StorageService;
import com.gommit.domain.media.support.MediaContentType;
import com.gommit.domain.media.support.MediaValidator;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

// MediaValidator/StorageService 위임 순서를 고정한다. media.support.MediaValidator 는
// StorageService.store() 가 호출 전 검증 완료를 전제하는 계약이라, 이 클래스가 순서를 지키는지가
// 유일한 안전망이다 (계약이 타입 시그니처에 드러나지 않기 때문).
@ExtendWith(MockitoExtension.class)
@DisplayName("CheckInMediaStore")
class CheckInMediaStoreTest {

    @Mock
    private StorageService storageService;

    @Mock
    private MediaValidator mediaValidator;

    @Mock
    private CheckInVideoTranscoder videoTranscoder;

    private CheckInMediaStore adapter;

    @BeforeEach
    void setUp() {
        adapter = new CheckInMediaStore(storageService, mediaValidator, videoTranscoder);
    }

    @Test
    @DisplayName("store: validate 를 먼저 통과시킨 뒤에만 storageService.store 를 호출한다(이미지)")
    void storeValidatesBeforeStoring() {
        MultipartFile file = new MockMultipartFile("media", "a.png", "image/png", new byte[] {1, 2, 3});
        when(storageService.store(file, MediaRole.CHECKIN)).thenReturn(new StorageResult("check-ins/2026/09/a.png"));

        UploadedMedia uploaded = adapter.store(file);

        assertThat(uploaded.storageKey()).isEqualTo("check-ins/2026/09/a.png");
        assertThat(uploaded.mediaType()).isEqualTo(MediaType.IMAGE);
        assertThat(uploaded.posterKey()).isNull();
        InOrder order = inOrder(mediaValidator, storageService);
        order.verify(mediaValidator).validate(file, MediaRole.CHECKIN);
        order.verify(storageService).store(file, MediaRole.CHECKIN);
        verify(videoTranscoder, never()).transcode(any(), any());
    }

    @Test
    @DisplayName("store: 영상은 트랜스코드 후 영상·포스터를 각각 storageService 에 저장한다")
    void storeTranscodesVideoAndStoresPoster() {
        MultipartFile file = new MockMultipartFile("media", "a.webm", "video/webm", new byte[] {1, 2, 3});
        when(videoTranscoder.transcode(file, "webm")).thenReturn(new Transcoded(new byte[] {9, 9}, new byte[] {8, 8}));
        when(storageService.storeGenerated(new byte[] {9, 9}, MediaContentType.MP4, MediaRole.CHECKIN))
                .thenReturn(new StorageResult("check-ins/2026/09/clip.mp4"));
        when(storageService.storeGenerated(new byte[] {8, 8}, MediaContentType.JPEG, MediaRole.CHECKIN))
                .thenReturn(new StorageResult("check-ins/2026/09/clip-poster.jpg"));

        UploadedMedia uploaded = adapter.store(file);

        assertThat(uploaded.storageKey()).isEqualTo("check-ins/2026/09/clip.mp4");
        assertThat(uploaded.mediaType()).isEqualTo(MediaType.VIDEO);
        assertThat(uploaded.posterKey()).isEqualTo("check-ins/2026/09/clip-poster.jpg");
        verify(storageService, never()).store(any(), any());
    }

    @Test
    @DisplayName("store: 포스터 저장이 실패하면 방금 올린 영상을 정리하고 예외를 되던진다")
    void storeCleansUpVideoWhenPosterStorageFails() {
        MultipartFile file = new MockMultipartFile("media", "a.mp4", "video/mp4", new byte[] {1, 2, 3});
        when(videoTranscoder.transcode(file, "mp4")).thenReturn(new Transcoded(new byte[] {9}, new byte[] {8}));
        when(storageService.storeGenerated(new byte[] {9}, MediaContentType.MP4, MediaRole.CHECKIN))
                .thenReturn(new StorageResult("check-ins/2026/09/clip.mp4"));
        RuntimeException posterFailure = new BusinessException(ErrorCode.MEDIA_STORAGE_FAILED);
        when(storageService.storeGenerated(new byte[] {8}, MediaContentType.JPEG, MediaRole.CHECKIN))
                .thenThrow(posterFailure);

        assertThatThrownBy(() -> adapter.store(file)).isSameAs(posterFailure);

        verify(storageService).delete("check-ins/2026/09/clip.mp4", MediaRole.CHECKIN);
    }

    @Test
    @DisplayName("store: validate 가 실패하면 storageService.store 는 아예 호출되지 않는다")
    void storeSkipsStorageWhenValidationFails() {
        MultipartFile file = new MockMultipartFile("media", "a.txt", "text/plain", new byte[] {1, 2, 3});
        BusinessException validationFailure = new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        doThrowOnValidate(file, validationFailure);

        assertThatThrownBy(() -> adapter.store(file)).isSameAs(validationFailure);

        verify(storageService, never()).store(any(), any());
    }

    @Test
    @DisplayName("load: MediaRole.CHECKIN 을 고정해서 storageService.load 에 위임한다")
    void loadDelegatesWithCheckInRole() {
        Resource resource = new ByteArrayResource(new byte[] {1, 2, 3});
        when(storageService.load("check-ins/2026/09/a.png", MediaRole.CHECKIN)).thenReturn(resource);

        Resource result = adapter.load("check-ins/2026/09/a.png");

        assertThat(result).isSameAs(resource);
    }

    private void doThrowOnValidate(MultipartFile file, BusinessException exception) {
        doThrow(exception).when(mediaValidator).validate(file, MediaRole.CHECKIN);
    }
}
