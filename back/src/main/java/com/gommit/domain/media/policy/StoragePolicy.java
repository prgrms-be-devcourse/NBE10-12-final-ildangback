package com.gommit.domain.media.policy;

import java.util.Set;
import org.springframework.util.unit.DataSize;

// 미디어 역할(MediaRole: CHECKIN/DAILYLOG/ITEM)별 저장 정책.
// application.yml 의 media.storage.policies.* 에서 MediaStorageProperties 로 바인딩
// 예) CHECKIN:
// { folder: check-ins, max-size: 5MB, visibility: PRIVATE, allowed-content-types: [image/png, image/jpeg, image/webp] }
public record StoragePolicy(String folder, DataSize maxSize, Visibility visibility, Set<String> allowedContentTypes) {

    public enum Visibility {
        PUBLIC,
        PRIVATE
    }

    public boolean allowsContentType(String contentType) {
        return allowedContentTypes.contains(contentType);
    }

    public boolean isPublic() {
        return visibility == Visibility.PUBLIC;
    }
}
