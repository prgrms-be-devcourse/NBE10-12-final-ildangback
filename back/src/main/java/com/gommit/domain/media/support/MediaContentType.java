package com.gommit.domain.media.support;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

// 미디어에서 허용하는 콘텐츠 타입 + 매직바이트 서명.
// 확장자 결정(LocalStorageService)과 실제 바이트 검증(MediaValidator)에서 사용
// 새 포맷을 허용시 여기와 application.yml 의 allowed-content-types 수정.

// 영상 체크인: checkin 영상은 getUserMedia + MediaRecorder 로 Chrome/Firefox 에서 video/webm(VP8/VP9),
//   Safari 에서 video/mp4(H.264) 로 녹화된다. mov(video/quicktime) 는 아직 FE 경로에선 안 나오지만
//   추후 KMP 네이티브(iOS AVCaptureMovieFileOutput 기본 출력)를 대비해 미리 화이트리스트에 둔다.
// 이 셋(WEBM/MOV/MP4)은 전부 원본 업로드 컨테이너로만 쓰이고, 최종 저장은 CheckInVideoTranscoder 가
// 항상 MP4 로 재인코딩한다 — iOS Safari 는 WebM 재생이 안 되고, mov 도 브라우저 <video> 재생 지원이 빈약해서.

public enum MediaContentType {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
    WEBP("image/webp", "webp"),
    MP4("video/mp4", "mp4"),
    WEBM("video/webm", "webm"),
    MOV("video/quicktime", "mov");

    // 파일의 "ftyp" 부분만으로는 mp4와 혼동되는 파일 형식을 파일의 "brand" 부분으로 걸러내기 위한 목록.
    private static final Set<String> NON_MP4_BRANDS = Set.of("heic", "heix", "heim", "heis", "mif1", "qt  ");

    private final String mimeType;
    private final String extension;

    MediaContentType(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    // PRIVATE 미디어(checkin, dailylog)를 서버가 전달할 때 필요한 Content-Type 헤더값.
    // 서빙 시점에 storageKey 확장자 -> fromExtension() -> mimeType() 으로 되찾는다.
    public String mimeType() {
        return mimeType;
    }

    // 콘텐츠 자체로 이미지/영상을 구분한다. MediaRole 로 분기하지 않음:
    //   checkin 이 추후 영상을 허용해도(정책에 video/* 추가) 이 값이 자동으로 바뀌어 provider 구현 수정 불필요
    public boolean isVideo() {
        return this == MP4 || this == WEBM || this == MOV;
    }

    // 업로드 시점에 파일 형식 확인. MediaRecorder 가 만드는 Blob 의 Content-Type 은
    // "video/webm;codecs=vp9" 처럼 코덱 파라미터가 붙어 오므로 세미콜론 앞부분만 비교한다.
    public static Optional<MediaContentType> fromMimeType(String mimeType) {
        if (mimeType == null) {
            return Optional.empty();
        }
        String base = mimeType.split(";", 2)[0].trim();
        return Arrays.stream(values())
                .filter(type -> type.mimeType.equalsIgnoreCase(base))
                .findFirst();
    }

    // 조회 시점에 storageKey 의 확장자로 타입 확인
    public static Optional<MediaContentType> fromExtension(String extension) {
        if (extension == null) {
            return Optional.empty();
        }
        String normalized = extension.equalsIgnoreCase("jpeg") ? "jpg" : extension;
        return Arrays.stream(values())
                .filter(type -> type.extension.equalsIgnoreCase(normalized))
                .findFirst();
    }

    // 파일의 선두 바이트(최소 12바이트 권장)로 파일 형식 확인
    public boolean matchesSignature(byte[] header) {
        return switch (this) {
            case PNG -> startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case JPEG -> startsWith(header, 0xFF, 0xD8, 0xFF);
            case WEBP ->
                startsWith(header, 0x52, 0x49, 0x46, 0x46) // "RIFF"
                        && header.length >= 12
                        && header[8] == (byte) 0x57
                        && header[9] == (byte) 0x45
                        && header[10] == (byte) 0x42
                        && header[11] == (byte) 0x50; // "WEBP"
            // ISO base media: 앞 4바이트는 box 크기, 이어서 "ftyp", 그 다음 4바이트가 브랜드.
            // "ftyp" 는 mp4/mov/heic 공통이라 브랜드로 mp4 아닌 것을 걸러낸다.
            case MP4 -> isFtypBox(header) && !NON_MP4_BRANDS.contains(brand(header));
            // mov 도 같은 "ftyp" 박스 구조를 쓰고 브랜드가 "qt  "(QuickTime) — MP4 가 걸러내는 바로 그 브랜드.
            case MOV -> isFtypBox(header) && "qt  ".equals(brand(header));
            // WebM 은 EBML 컨테이너 — 선두 4바이트가 EBML 매직넘버로 고정.
            case WEBM -> startsWith(header, 0x1A, 0x45, 0xDF, 0xA3);
        };
    }

    // ISO base media box: header[4..7] == "ftyp".
    private static boolean isFtypBox(byte[] header) {
        return header.length >= 12
                && header[4] == (byte) 0x66
                && header[5] == (byte) 0x74
                && header[6] == (byte) 0x79
                && header[7] == (byte) 0x70;
    }

    // ftyp box 의 브랜드(header[8..11]). isFtypBox 로 길이를 먼저 확인한 뒤에만 호출한다.
    private static String brand(byte[] header) {
        return new String(header, 8, 4, StandardCharsets.ISO_8859_1);
    }

    private static boolean startsWith(byte[] data, int... prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != (byte) prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
