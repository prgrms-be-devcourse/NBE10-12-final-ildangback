package com.gommit.support;

import com.gommit.domain.user.service.VerificationMailSender;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// 테스트는 메일을 보내지 않는다. 링크를 받아두고 토큰만 꺼내 쓴다
@Component
@Profile("test")
public class RecordingMailSender implements VerificationMailSender {

    private static final String TOKEN_PARAM = "?token=";

    private String recipient;
    private String link;
    private boolean failOnSend;

    @Override
    public void send(String email, String verificationLink) {
        if (failOnSend) {
            throw new IllegalStateException("메일 서버 장애 흉내");
        }
        this.recipient = email;
        this.link = verificationLink;
    }

    public void clear() {
        this.recipient = null;
        this.link = null;
        this.failOnSend = false;
    }

    public void failOnSend() {
        this.failOnSend = true;
    }

    public String lastLink() {
        return link;
    }

    public String lastRecipient() {
        return recipient;
    }

    public String lastToken() {
        return link == null ? null : link.substring(link.indexOf(TOKEN_PARAM) + TOKEN_PARAM.length());
    }
}
