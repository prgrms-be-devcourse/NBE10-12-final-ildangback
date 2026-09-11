package com.gommit.domain.checkin.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gommit.domain.media.dto.StorageResult;
import com.gommit.domain.media.entity.MediaRole;
import com.gommit.domain.media.service.StorageService;
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

    private CheckInMediaStore adapter;

    @BeforeEach
    void setUp() {
        adapter = new CheckInMediaStore(storageService, mediaValidator);
    }

    @Test
    @DisplayName("store: validate 를 먼저 통과시킨 뒤에만 storageService.store 를 호출한다")
    void storeValidatesBeforeStoring() {
        MultipartFile file = new MockMultipartFile("media", "a.png", "image/png", new byte[] {1, 2, 3});
        when(storageService.store(file, MediaRole.CHECKIN)).thenReturn(new StorageResult("check-ins/2026/09/a.png"));

        String storageKey = adapter.store(file);

        assertThat(storageKey).isEqualTo("check-ins/2026/09/a.png");
        InOrder order = inOrder(mediaValidator, storageService);
        order.verify(mediaValidator).validate(file, MediaRole.CHECKIN);
        order.verify(storageService).store(file, MediaRole.CHECKIN);
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
    @DisplayName("delete: MediaRole.CHECKIN 을 고정해서 storageService.delete 에 위임한다")
    void deleteDelegatesWithCheckInRole() {
        adapter.delete("check-ins/2026/09/a.png");

        verify(storageService).delete("check-ins/2026/09/a.png", MediaRole.CHECKIN);
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
