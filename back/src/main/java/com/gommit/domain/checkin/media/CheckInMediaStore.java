package com.gommit.domain.checkin.media;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

// 인증 미디어 저장/조회 인터페이스.
// 저장은 2단계 — 파일 검증 + 키 확보(reserve) → 바이트 쓰기(write). 인증 row 를 먼저 저장(uk_check_ins)한 뒤에만
// write 를 호출해서, 중복/동시 제출로 insert 가 실패한 경우 디스크에 파일이 남지 않게 한다.
// media 공통 서비스(#8) 머지 후: 이 인터페이스 구현을 media.StorageService(file/key, MediaRole.CHECKIN) 위임 어댑터로 교체.
// 어댑터가 MediaRole.CHECKIN 을 채우므로 이 인터페이스에는 role 이 드러나지 않는다.
// 서빙 엔드포인트(CheckInMediaController)는 도메인별 경로 유지
public interface CheckInMediaStore {

    // 파일을 검증하고 저장할 스토리지 키를 확보한다. 바이트는 아직 쓰지 않는다.
    // 위반 시 BusinessException(EMPTY_FILE / FILE_TOO_LARGE / UNSUPPORTED_MEDIA_TYPE).
    String reserve(MultipartFile file);

    // reserve() 로 받은 키에 실제 바이트를 쓴다.
    // 실패 시 BusinessException(MEDIA_STORAGE_FAILED), 부분 파일은 best-effort 로 정리한다.
    void write(MultipartFile file, String storageKey);

    // 스토리지 키로 저장된 바이트를 읽는다.
    // 없으면 BusinessException(MEDIA_NOT_FOUND), 경로 탈출 시 BusinessException(ACCESS_DENIED).
    Resource load(String storageKey);
}
