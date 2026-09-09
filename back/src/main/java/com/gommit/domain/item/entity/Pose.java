package com.gommit.domain.item.entity;

import com.gommit.domain.group.entity.MapType;

public enum Pose {
    DEFAULT,
    GYM_SUCCESS,
    GYM_FAIL,
    STUDY_SUCCESS,
    STUDY_FAIL;

    public static Pose of(MapType mapType, boolean completed) {
        return switch (mapType) {
            case GYM -> completed ? GYM_SUCCESS : GYM_FAIL;
            case STUDY_ROOM -> completed ? STUDY_SUCCESS : STUDY_FAIL;
        };
    }
}
