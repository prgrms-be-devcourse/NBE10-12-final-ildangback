package com.gommit.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 인프라(nginx / Docker HEALTHCHECK / 배포 스크립트 / GitHub Actions)가 전부
 * {@code GET /actuator/health} 를 인증 없이 호출한다. 이 계약이 깨지면 배포가 통째로 막힌다.
 * 동시에 그 외 actuator 엔드포인트는 절대 공개되면 안 된다(env·beans·configprops 로 시크릿 노출).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActuatorSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /actuator/health 는 인증 없이 200")
    void healthIsPublic() throws Exception {
        // 막는 사고: SecurityConfig 의 MONITORING_ENDPOINTS permitAll 이 사라지거나 health 노출이 꺼지면
        //           → nginx healthcheck·Docker HEALTHCHECK·deploy.sh 헬스 대기·deploy.yml 최종 확인이
        //             전부 실패하고 배포가 롤백 루프에 빠진다.
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("health 외 actuator 엔드포인트는 인증 없이 접근 불가")
    void otherActuatorEndpointsAreNotPublic() throws Exception {
        // 막는 사고: 누가 management.endpoints.web.exposure.include 를 "*" 로 바꾸거나
        //           MONITORING_ENDPOINTS 매처를 "/actuator/**" 로 넓히면
        //           → env(환경변수·시크릿), configprops, beans, heapdump 가 무인증 공개된다.
        mockMvc.perform(get("/actuator")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/beans")).andExpect(status().isUnauthorized());
    }
}
