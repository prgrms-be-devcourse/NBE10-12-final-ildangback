package com.gommit.domain.point.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// application.yml 의 point.* 를 바인딩. 키가 없어도 @DefaultValue 로 뜬다(yml 값이 있으면 override).
// 모두 [임시값] — 기획 확정 시 yml 수정 + 재시작. (EC2 18:00 정지/재기동 사이클이 있어 재시작 부담 작음)
//   checkInReward        : 개인 인증 1건
//   groupDailyAllComplete: 그룹 ACTIVE 전원이 그날 목표 달성
//   mergeBonus / backgroundPurchase : GroupPointReason 자리만. 0 = 미사용(해당 기능 붙일 때 값 지정)
@ConfigurationProperties(prefix = "point")
public record PointProperties(
        @DefaultValue("10") int checkInReward,
        @DefaultValue("5") int groupDailyAllComplete,
        @DefaultValue("0") int mergeBonus,
        @DefaultValue("0") int backgroundPurchase) {}
