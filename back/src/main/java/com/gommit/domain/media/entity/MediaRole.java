package com.gommit.domain.media.entity;

// 미디어 용도 구분. 폴더/용량/공개여부 등 저장 정책은 domain.media.policy.StoragePolicy 담당.
public enum MediaRole {
    CHECKIN,
    DAILYLOG,
    ITEM
}
