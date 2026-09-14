package com.gommit.domain.checkin.media;

import com.gommit.domain.checkin.entity.MediaType;

// CheckInMediaStore.store 결과. posterKey 는 영상일 때만(그리드 표시용 썸네일), 이미지는 null.
public record UploadedMedia(String storageKey, MediaType mediaType, String posterKey) {}
