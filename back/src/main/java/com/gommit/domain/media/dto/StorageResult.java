package com.gommit.domain.media.dto;

// 파일 저장 결과.
// storageKey = load/delete 용 스토리지 핸들: 로컬 = baseDir 기준 상대경로, Cloudinary = publicId.
// 공개 URL 은 저장하지 않고 조회 시 provider 별로 파생한다 (로컬 = base-url + key, Cloudinary = SDK 가 publicId 로 생성).
public record StorageResult(String storageKey) {}
