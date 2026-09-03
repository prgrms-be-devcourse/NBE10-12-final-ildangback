package com.gommit.support;

import com.gommit.domain.user.service.EmailSender;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// 테스트는 메일을 보내지 않는다. 본문을 받아두고 토큰만 꺼내 쓴다
@Component
@Profile("test")
public class RecordingMailSender implements EmailSender {

    // 본문 뒤에 안내 문구가 붙으므로 토큰 문자만 끊어서 읽는다
    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    private String recipient;
    private String subject;
    private String body;
    private boolean failOnSend;

    @Override
    public void send(String to, String subject, String body) {
        if (failOnSend) {
            throw new IllegalStateException("메일 서버 장애 흉내");
        }
        this.recipient = to;
        this.subject = subject;
        this.body = body;
    }

    public void clear() {
        this.recipient = null;
        this.subject = null;
        this.body = null;
        this.failOnSend = false;
    }

    public void failOnSend() {
        this.failOnSend = true;
    }

    public String lastSubject() {
        return subject;
    }

    public String lastBody() {
        return body;
    }

    public String lastRecipient() {
        return recipient;
    }

    public String lastToken() {
        if (body == null) {
            return null;
        }
        Matcher matcher = TOKEN.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }
}
