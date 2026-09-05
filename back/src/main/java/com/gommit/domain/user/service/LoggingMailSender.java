package com.gommit.domain.user.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// dev 는 메일을 보내지 않고 본문을 콘솔에 찍는다. 토큰 원본이 남으므로 dev 에만 둔다
@Slf4j
@Component
@Profile("dev")
public class LoggingMailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String body) {
        log.info("[메일] {} / {}{}{}", to, subject, System.lineSeparator(), body);
    }
}
